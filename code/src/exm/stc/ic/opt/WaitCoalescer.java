/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.ic.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.MultiMap.LinkedListFactory;
import exm.stc.common.util.MultiMap.ListFactory;
import exm.stc.common.util.Pair;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.OptUtil.InstOrCont;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * This optimization pass aims to rearrange task dependencies to:
 * - Reduce the number of tasks
 * - Reduce the number of subscriptions
 * - Enable further optimizations by, in some cases, putting instructions more
 *    in order of data dependencies
 * E.g. consider this code:
 * 
 * int x = f(); // f is a composite
 * int y = x * x + 1 - 2 * 3;
 * 
 * Without this pass, since we don't know when the return value of f() will be
 * available, we are forced to create a chain of dataflow operations to
 * evaluate the expression.  With this pass, we simply wait for x and then
 * do the arithmetic locally. 
 *
 * 
 * Stage 1: Op conversion
 * ----------------------
 * In block B.
 * Convert all ops f(v1, v2) with local op equivalent local_f(lv1, lv2) to:
 * wait (v1, v2) {
 *   retrieve lv1 v1
 *   retrieve lv1 v2
 *   call local_f [lv3] [lv1, lv2]
 *   store v3 lv2
 * }
 * 
 * This mainly is done to enable the subsequent stages, but does provide
 * benefits in the situation where some but not all of the arguments are
 * immediately available (e.g. suppose v1 is a constant) and we can eliminate 
 * some futures, or eliminate waits on the futures
 * 
 * Stage 2: Wait Merging
 * ---------------------
 * After the introduction of all the waits in the step above, there might
 * be a lot of redundancy: waits with overlapping argument lists.  This 
 * step finds wait statements in the block B with overlap in argument lists,
 * and then combines them, reducing redundant subscribe operations, and 
 * often enabling further optimisations within the blocks.  There are multiple
 * ways that waits can be combined and it's difficult to find the optimal one
 * since this is some kind of graph partitioning problem, and furthermore that
 * subsequent optimization passes may change the problem parameters.  
 * 
 * We use a greedy heuristic in an attempt to merge waits in the best way.  
 * Intuition is that this algorithm should do the 100% right thing most of 
 * the time, and most of  the rest of the time do something reasonable
 *
 * until converged:
 *    - look at all waits in block
 *       and build data structure: {
 *            v1: { wait_1 }
 *            v2: { wait_1 }
 *            v3: { wait_1, wait_2 }
 *        }
 *    - find variable v with most waiters
 *    - let [w1, w2, ..., wk] be the waiters, and VARS(w) be the set of
 *        variables w waits for
 *    - find intersection of VARS(w1), ..., VARS(wk)
 *    - merge together [w1, w2, ..., wk] as follows
 *            wait (intersection) {
 *                w1
 *                w2
 *                ...
 *                wk
 *            }
 *
 * Stage 3: Statement Pushdown
 * --------------------------
 * 
 * So by now we have a block B and some number of wait statements hanging off it.
 * There may be an instruction I1 or continuation C in block B that
 * logically cannot run until an instruction I2 buried in one of the waits 
 * executes.   So it would make sense to push I1 or C down into the wait statement
 * after I2, as this will at the very least defer the insertion of the task
 * until it has some chance of running, but in many cases will enable many further
 * optimizations.
 * 
 * There may be multiple such places where an instruction will be moved to, we
 * just do use a greedy heuristic and put it in the first place we find. 
 * Again, the intuition is that in many cases, this will do exactly the 
 * right thing, in others it will do something acceptable.  
 * 
 * In some cases as well, once we recurse down into the wait statements, we
 * will push the isntruction/continuation down even further
 * 
 * Pseudocode:
 *   - For many instructions or continuations, we know the set of blocking 
 *     input variables: variables which must be closed before anything can
 *     execute. 
 *   - look at all instructions and continuations, and based on the blocking
 *      inputs, build this map: {
 *           v1: {inst1, cont1, inst2}
 *           v2: {inst1, cont2, inst3}
 *           }
 *   - The above data structure should only include instructions which can be
 *      safely relocated: in particular we should avoid relocating anything which
 *      doesn't have exclusively futures as outputs 
 *   - Iterate recursively over all child blocks:
 *     * If a variable v1 is written here, stick the dependent instructions/
 *          continuations at the end of the block in which its written, 
 *          and then update parent block B and the map 
 * 
 * Stage 4: Recurse
 * ---------------
 * Go to all subblocks and do the same
 *
 */
public class WaitCoalescer implements OptimizerPass {
  private boolean doMerges;
  
  public WaitCoalescer(boolean doMerges) {
    this.doMerges = doMerges;
  }
  
  @Override
  public String getPassName() {
    return "Wait coalescing";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_WAIT_COALESCE;
  }

  @Override
  public void optimize(Logger logger, Program prog) {
    for (Function f: prog.getFunctions()) {
      boolean changed = rearrangeWaits(logger, f, f.getMainblock(),
                                       ExecContext.CONTROL);
      if (changed) {
        // This pass can mess up variable passing
        FixupVariables.fixupVariablePassing(logger, prog, f);
      }
    }
  }

  public boolean rearrangeWaits(Logger logger, Function fn, Block block,
                                       ExecContext currContext) {
    StringBuilder sb = new StringBuilder();
    boolean exploded = explodeFuncCalls(logger, fn, currContext, block);

    if (logger.isTraceEnabled()) {
      fn.prettyPrint(sb);
      logger.trace("After exploding " + fn.getName() +":\n" + sb.toString());
    }
    
    boolean merged = false;
    if (doMerges) { 
      merged = mergeWaits(logger, fn, block);
    }
    
    if (logger.isTraceEnabled()) {
      sb = new StringBuilder();
      fn.prettyPrint(sb);
      logger.trace("After merging " + fn.getName() +":\n" + sb.toString());
    }
    
    boolean pushedDown = pushDownWaits(logger, fn, block, currContext);
    
    // Recurse on child blocks
    boolean recChanged = rearrangeWaitsRec(logger, fn, block, currContext);
    return exploded || merged || pushedDown || recChanged;
  }

  private boolean rearrangeWaitsRec(Logger logger,
                  Function fn, Block block, ExecContext currContext) {
    boolean changed = false;
    for (Continuation c: block.getContinuations()) {
      ExecContext newContext = c.childContext(currContext);
      for (Block childB: c.getBlocks()) {
        if (rearrangeWaits(logger, fn, childB, newContext)) {
          changed = true;
        }
      }
    }
    return changed;
  }

  /**
   * Convert dataflow f() to local f_l() with wait around it
   * @param logger
   * @param block
   * @return
   */
  private static boolean explodeFuncCalls(Logger logger, Function fn,
        ExecContext execCx, Block block) {
    boolean changed = false;
    Set<String> empty = Collections.emptySet();
    ListIterator<Instruction> it = block.instructionIterator();
    while (it.hasNext()) {
      Instruction i = it.next();
      MakeImmRequest req = i.canMakeImmediate(empty, true);
      if (req != null && req.in.size() > 0) {
        List<Var> waitVars = ICUtil.filterBlockingOnly(req.in);
        
        WaitMode waitMode;
        if (req.mode == TaskMode.SYNC || req.mode == TaskMode.LOCAL ||
              (req.mode == TaskMode.LOCAL_CONTROL &&
                execCx == ExecContext.CONTROL)) {
          waitMode = WaitMode.DATA_ONLY;
        } else {
          waitMode = WaitMode.TASK_DISPATCH;
        }
        
        List<Var> outWriteRefcounted = RefCounting.filterWriteRefcount(
                    req.out == null ? Collections.<Var>emptyList() : req.out);
        
        WaitStatement wait = new WaitStatement(
                fn.getName() + "-" + i.shortOpName(),
                waitVars, req.in,
                outWriteRefcounted,
                i.getPriority(), waitMode, true, req.mode);
        block.addContinuation(wait);
        
        List<Instruction> instBuffer = new ArrayList<Instruction>();
        
        // Fetch the inputs
        List<Arg> inVals = OptUtil.fetchValuesOf(wait.getBlock(),
                                              instBuffer, req.in);
        
        // Create local instruction, copy out outputs
        List<Var> localOutputs = OptUtil.declareLocalOpOutputVars(
                                            wait.getBlock(), req.out);
        MakeImmChange change = i.makeImmediate(localOutputs, inVals);
        OptUtil.fixupImmChange(wait.getBlock(), change, instBuffer,
                                          localOutputs, req.out);
        
        // Remove old instruction, add new one inside wait block
        it.remove();
        wait.getBlock().addInstructions(instBuffer);
        changed = true;
      }
    }
    return changed;
  }

  private static boolean mergeWaits(Logger logger, Function fn, Block block) {
    boolean changed = false;
    boolean fin;
    do {
      fin = true;
      MultiMap<String, WaitStatement> waitMap = buildWaitMap(block);
      // Greedy approach: find most shared variable and
      //    merge wait based on that
      String winner = mostSharedVar(waitMap);
      if (winner != null) {
        // There is some shared variable between waits
        fin = false;
        changed = true;
        List<WaitStatement> waits = waitMap.get(winner);
        assert(waits != null && waits.size() >= 2);
        
        // If one of the waits is explicit, new one must be also
        boolean explicit = false; 
        
        // If all waits are recursive
        boolean allRecursive = true;
        
        // Find out which variables are in common with all waits
        Set<String> intersection = null;
        for (WaitStatement wait: waits) {
          Set<String> nameSet = Var.nameSet(wait.getWaitVars());
          if (intersection == null) {
            intersection = nameSet;
          } else {
            intersection.retainAll(nameSet);
          }
          explicit = explicit || wait.getMode() != WaitMode.DATA_ONLY;
          allRecursive = allRecursive && wait.isRecursive();
        }
        assert(intersection != null && !intersection.isEmpty());
        
        List<Var> intersectionVs = ICUtil.getVarsByName(intersection,
                                              waits.get(0).getWaitVars());
        
        // Create a new wait statement waiting on the intersection
        // of the above.
        WaitStatement newWait = new WaitStatement(fn.getName() + "-optmerged",
            intersectionVs, new ArrayList<Var>(0), new ArrayList<Var>(0), null,
            explicit ? WaitMode.EXPLICIT : WaitMode.DATA_ONLY, allRecursive,
            TaskMode.LOCAL);
        
        // List of variables that are kept open, or used
        ArrayList<Var> usedVars = new ArrayList<Var>();
        ArrayList<Var> keepOpen = new ArrayList<Var>();
        
        // Put the old waits under the new one, remove redundant wait vars
        // Exception: don't eliminate task dispatch waits
        for (WaitStatement wait: waits) {
          if (allRecursive) {
            wait.tryInline(Collections.<String>emptySet(), intersection);
          } else {
            wait.tryInline(intersection, Collections.<String>emptySet());
          }
          if (wait.getWaitVars().isEmpty() &&
              wait.getMode() != WaitMode.TASK_DISPATCH) {
            newWait.getBlock().insertInline(wait.getBlock());
          } else {
            wait.setParent(newWait.getBlock());
            newWait.getBlock().addContinuation(wait);
          }
          keepOpen.addAll(wait.getKeepOpenVars());
          usedVars.addAll(wait.getPassedInVars());
        }

        ICUtil.removeDuplicates(keepOpen);
        for (Var v: keepOpen) {
          newWait.addKeepOpenVar(v);
        }
        ICUtil.removeDuplicates(usedVars);
        newWait.addPassedInVars(usedVars);
        
        block.addContinuation(newWait);
        block.removeContinuations(waits);
      }
      changed = changed || !fin;
    } while (!fin);
    return changed;
  }

  /**
   * Build a map of <variable name> --> wait statements blocking on that value 
   * @param block
   * @return
   */
  public static  MultiMap<String, WaitStatement> buildWaitMap(Block block) {
    MultiMap<String, WaitStatement> waitMap =
                        new MultiMap<String, WaitStatement>(); 
    for (Continuation c: block.getContinuations()) {
      if (c.getType() == ContinuationType.WAIT_STATEMENT) {
        WaitStatement wait = (WaitStatement)c;
        for (Var v: wait.getWaitVars()) {
          waitMap.put(v.name(), wait);
        }
      }
    }
    return waitMap;
  }

  /**
   * Find the variable which appears the most times in a value of the
   * map
   * @param waitMap
   * @return
   */
  public static String mostSharedVar(
          MultiMap<String, WaitStatement> waitMap) {
    String winner = null;
    int winCount = 0;
    for (Entry<String, List<WaitStatement>> e: waitMap.entrySet()) {
      List<WaitStatement> waits = e.getValue();
      if (waits.size() > 1) {
        if (winner == null || waits.size() > winCount) {
          winner = e.getKey();
          winCount = waits.size();
        }
      }
    }
    return winner;
  }
  
  private static class AncestorContinuation {
    private AncestorContinuation(Continuation continuation, Block block) {
      super();
      this.continuation = continuation;
      this.block = block;
    }
    
    public final Continuation continuation;
    /** Block inside continuation */
    public final Block block;
  }
  
  private static class PushDownResult {
    public final boolean anyChanges;
    public final List<Continuation> relocated;
    
    public PushDownResult(boolean anyChanges, List<Continuation> relocated) {
      super();
      this.anyChanges = anyChanges;
      this.relocated = relocated;
    }
  }

  private static boolean
          pushDownWaits(Logger logger, Function fn, Block block,
                                      ExecContext currContext) {
    MultiMap<String, InstOrCont> waitMap = buildWaiterMap(block);
    if (waitMap.isDefinitelyEmpty()) {
      // If waitMap is empty, can't push anything down, so just
      // shortcircuit
      return false;
    }
    boolean changed = false;
    
    HashSet<Continuation> allPushedDown = new HashSet<Continuation>();
    ArrayList<Continuation> contCopy = 
                new ArrayList<Continuation>(block.getContinuations());
    for (Continuation c: contCopy) {
      if (allPushedDown.contains(c)) {
        // Was moved
        continue;
      }
      ExecContext newContext = canPushDownInto(c, currContext); 
      if (newContext != null) {
        for (Block innerBlock: c.getBlocks()) {
          ArrayDeque<AncestorContinuation> ancestors =
                                        new ArrayDeque<AncestorContinuation>();
          ancestors.push(new AncestorContinuation(c, innerBlock));
          PushDownResult pdRes = 
               pushDownWaitsRec(logger, fn, block, currContext, ancestors,
                                innerBlock, newContext, waitMap);
           changed = changed || pdRes.anyChanges;
          /* The list of continuations might be modified as continuations are
           * pushed down - track which ones are relocated */
          allPushedDown.addAll(pdRes.relocated);
        }
      }
    }
    return changed;
  }
  
  private static PushDownResult pushDownWaitsRec(
                Logger logger,  Function fn,
                Block top, ExecContext topContext,
                Deque<AncestorContinuation> ancestors, Block curr,
                ExecContext currContext,
                MultiMap<String, InstOrCont> waitMap) {
    boolean changed = false;
    ArrayList<Continuation> pushedDown = new ArrayList<Continuation>();
    /* Iterate over all instructions in this descendant block */
    ListIterator<Instruction> it = curr.instructionIterator();
    while (it.hasNext()) {
      Instruction i = it.next();
      if (logger.isTraceEnabled()) {
        logger.trace("Pushdown at: " + i.toString());
      }
      List<Var> writtenFutures = new ArrayList<Var>();
      for (Var outv: i.getOutputs()) {
        if (Types.isFuture(outv.type())) {
          writtenFutures.add(outv);
        }
      }
      
      // Relocate instructions which depend on output future of this instruction
      for (Var v: writtenFutures) {
        if (waitMap.containsKey(v.name())) {
          Pair<Boolean, Set<Continuation>> pdRes =
              relocateDependentInstructions(logger, top, topContext, ancestors, 
                                        curr, currContext, it,  waitMap, v);
          changed = changed || pdRes.val1;
          pushedDown.addAll(pdRes.val2);
        }
      }
    }
    
    // Update the stack with child continuations
    for (Continuation c: curr.getContinuations()) {
      ExecContext newContext = canPushDownInto(c, currContext);
      if (newContext != null) {
        for (Block innerBlock: c.getBlocks()) {
          ancestors.push(new AncestorContinuation(c, innerBlock));
          PushDownResult pdRes = pushDownWaitsRec(logger, fn, top, topContext,
                ancestors, innerBlock, newContext, waitMap);
          pushedDown.addAll(pdRes.relocated);
          changed = changed || pdRes.anyChanges;
          ancestors.pop();
        }
      }
    }
    return new PushDownResult(changed, pushedDown);
  }
  
  /**
   * 
   * @param c
   * @param curr
   * @return null if can't push down into
   */
  private static ExecContext canPushDownInto(Continuation c,
                                                 ExecContext curr) {
    /* Can push down into wait statements unless they are being dispatched
     *  to worker node */
    if (c.getType() == ContinuationType.WAIT_STATEMENT) {
      WaitStatement w = (WaitStatement)c;
      if (w.getMode() == WaitMode.TASK_DISPATCH) {
        if (w.getTarget() == TaskMode.LOCAL_CONTROL ||
            w.getTarget() == TaskMode.LOCAL) {
          if (curr == ExecContext.LEAF &&
                w.getTarget() == TaskMode.LOCAL_CONTROL) {
            throw new STCRuntimeError("Can't have local control wait in leaf"); 
          }
          // Context doesn't change
          return curr;
        } else if (w.getTarget() == TaskMode.CONTROL) {
          return ExecContext.CONTROL;
        } else {
          assert(w.getTarget() == TaskMode.LEAF);
          return ExecContext.LEAF;
        }
      } else {
        return curr;
      }
    }
    return null;
  }

  /**
   * 
   * @param logger
   * @param ancestorBlock the block the instructions are moved from
   * @param ancestors 
   * @param currBlock the block they are moved too (a descendant of the prior
   *              block)
   * @param currContext 
   * @param currBlockInstructions  all changes to instructions in curr block
   *    are made through this iterator, and it is rewound to the previous position
   *    before the function exits
   * @param waitMap map of variable names to instructions/continuations they block
   *                  on
   * @param writtenV
   * @return true if change made, list of moved continuations
   */
  private static Pair<Boolean, Set<Continuation>> relocateDependentInstructions(
      Logger logger,
      Block ancestorBlock, ExecContext ancestorContext,
      Deque<AncestorContinuation> ancestors,
      Block currBlock, ExecContext currContext, ListIterator<Instruction> currBlockInstructions,
      MultiMap<String, InstOrCont> waitMap, Var writtenV) {
    boolean changed = false;
    // Remove from outer block
    List<InstOrCont> waits = waitMap.get(writtenV.name());
    Set<Instruction> movedI = new HashSet<Instruction>();
    Set<Continuation> movedC = new HashSet<Continuation>();
    // Rely on later forward Dataflow pass to remove
    // unneeded wait vars
    
    /*
     * NOTE: instructions/ continuations retain the same relative
     * order they were in in the original block, this should help
     * optimization pass
     */
    for (InstOrCont ic: waits) {
      if (logger.isTraceEnabled())
        logger.trace("Pushing down: " + ic.toString());
      boolean relocated;
      switch (ic.type()) {
        case CONTINUATION: {
          relocated = relocateContinuation(ancestors, currBlock,
              currContext, movedC, ic.continuation());
          
          break;
        } 
        case INSTRUCTION:
          relocated = relocateInstruction(ancestors, currContext,
              currBlockInstructions, movedI, ic.instruction());
          break;
        default:
          throw new STCRuntimeError("how on earth did we get here...");
      }
      changed = changed || relocated;
    }
    // Remove instructions from old block
    ancestorBlock.removeContinuations(movedC);
    ancestorBlock.removeInstructions(movedI);
    
    // Rewind iterator so that next instruction returned
    // will be the first one added
    ICUtil.rewindIterator(currBlockInstructions, movedI.size());
    
    // Rebuild wait map to reflect changes
    updateWaiterMap(waitMap, movedC, movedI);
    return Pair.create(changed, movedC);
  }

  private static boolean relocateContinuation(
      Deque<AncestorContinuation> ancestors, Block currBlock,
      ExecContext currContext, Set<Continuation> movedC, Continuation cont) {
    boolean canRelocate = true;
    // Check we're not relocating continuation into itself
    for (AncestorContinuation ancestor: ancestors) {
      if (cont == ancestor.continuation) {
        canRelocate = false;
        break;
      }
    }
    
    if (currContext == ExecContext.LEAF) {
      if (cont.getType() != ContinuationType.WAIT_STATEMENT) {
        canRelocate = false;
      } else {
        WaitStatement w = (WaitStatement)cont;
        // Make sure gets dispatched to right place
        if (w.getTarget() == TaskMode.CONTROL ||
            w.getTarget() == TaskMode.LEAF ||
            w.getTarget() == TaskMode.LOCAL) {
          canRelocate = true;
        } else if (w.getTarget() == TaskMode.LOCAL_CONTROL) {
          w.setMode(WaitMode.TASK_DISPATCH);
          w.setTarget(TaskMode.CONTROL);
        } else {
          canRelocate = false;
        }
      }
    }
    
    if (canRelocate) {
      currBlock.addContinuation(cont);
      movedC.add(cont);
      // Doesn't make sense to push down synchronous continuations
      assert(cont.isAsync());
      updateAncestorKeepOpen(ancestors, cont.getKeepOpenVars());
    }
    return canRelocate;
  }

  private static boolean relocateInstruction(
      Deque<AncestorContinuation> ancestors, ExecContext currContext,
      ListIterator<Instruction> currBlockInstructions,
      Set<Instruction> movedI, Instruction inst) {
    boolean canRelocate = true;
    ArrayList<Var> keepOpenVars = new ArrayList<Var>();
    for (Var out: inst.getOutputs()) {
      if (Types.isArray(out.type())) {
        keepOpenVars.add(out);
      } else if (Types.isArrayRef(out.type())) {
        // Array ref might be from nested array, don't know yet
        // how to keep parent array open
        canRelocate = false;
      }
    }
    
    if (currContext == ExecContext.LEAF) {
      if (inst.getMode() != TaskMode.SYNC) {
        // Can't push down async tasks to leaf yet
        canRelocate = false;
      }
    }
    
    if (canRelocate) {
      currBlockInstructions.add(inst);
      movedI.add(inst);
      if (!keepOpenVars.isEmpty()) {
        updateAncestorKeepOpen(ancestors, keepOpenVars);
      }
    }
    return canRelocate;
  }

  private static void updateAncestorKeepOpen(
      Deque<AncestorContinuation> ancestors, Collection<Var> keepOpenVars) {
    Iterator<AncestorContinuation> it = ancestors.descendingIterator();
    ArrayList<Var> remainingVars = new ArrayList<Var>(keepOpenVars);
    while (it.hasNext()) {
      AncestorContinuation ancestor = it.next();
      
      // if variable was defined in this scope, doesn't exist above
      Set<String> defined = Var.nameSet(ancestor.block.getVariables());
      ListIterator<Var> vit = remainingVars.listIterator();
      while (vit.hasNext()) {
        Var v = vit.next();
        if (defined.contains(v.name())) {
          vit.remove();
        }
      }

      if (remainingVars.isEmpty()) {
        return;
      }
      
      // Add if missing
      Continuation cont = ancestor.continuation;
      if (cont.isAsync()) {
        ArrayList<Var> newKeepOpen =
                        new ArrayList<Var>(cont.getKeepOpenVars());
        newKeepOpen.addAll(remainingVars);
        ICUtil.removeDuplicates(newKeepOpen);
        cont.clearKeepOpenVars();
        cont.addKeepOpenVars(newKeepOpen);
      }
    }
  }

  /**
   * Update waiter map by removing continuations and instructions
   * based on object identity
   * @param waitMap
   * @param removedC
   * @param removedI
   */
  private static void updateWaiterMap(
          MultiMap<String, InstOrCont> waitMap, Set<Continuation> removedC,
      Set<Instruction> removedI) {
    List<String> keysToRemove = new ArrayList<String>();
    for (Entry<String, List<InstOrCont>> e: waitMap.entrySet()) {
      ListIterator<InstOrCont> it = e.getValue().listIterator();
      int count = 0;
      while (it.hasNext()) {
        InstOrCont ic = it.next();
        switch(ic.type()) {
        case CONTINUATION:
          if (removedC.contains(ic.continuation())) {
            it.remove();
          } else {
            count++;
          }
          break;
        case INSTRUCTION:
          if (removedI.contains(ic.instruction())) {
            it.remove();
          } else {
            count++;
          }
          break;
        default:
          throw new STCRuntimeError("shouldn't get here, unexpected enum " + 
                              ic.type());
        }
      }
      /* Remove variables no longer waited on */
      if (count == 0) {
        keysToRemove.add(e.getKey());
      }
    }
    // Explicitly remove key so that we cna tell if map is empty
    // Do this outside loop to avoid concurrently modifying it while
    // we are iterating over it
    for (String k: keysToRemove) {
      waitMap.remove(k);
    }
  }

  private static ListFactory<InstOrCont> LL_FACT = 
                    new LinkedListFactory<InstOrCont>();
  private static MultiMap<String, InstOrCont> buildWaiterMap(Block block) {
    // Use linked list to support more efficient removal in middle of list
    MultiMap<String, InstOrCont> waitMap =
                        new MultiMap<String, InstOrCont>(LL_FACT); 
    findRelocatableBlockingInstructions(block, waitMap);
    findBlockingContinuations(block, waitMap);
    return waitMap;
  }

  private static void findBlockingContinuations(Block block,
          MultiMap<String, InstOrCont> waitMap) {
    for (Continuation c: block.getContinuations()) {
      List<BlockingVar> blockingVars = c.blockingVars();
      if (blockingVars != null) {
        for (BlockingVar v: blockingVars) {
          waitMap.put(v.var.name(), new InstOrCont(c));
        }
      }
    }
  }

  private static void findRelocatableBlockingInstructions(Block block,
          MultiMap<String, InstOrCont> waitMap) {
    for (Instruction inst: block.getInstructions()) {
      // check all outputs are non-alias futures - if not can't safely reorder
      boolean canMove = true;
      for (Var out: inst.getOutputs()) {
        if (!Types.isFuture(out.type())
            || out.storage() == VarStorage.ALIAS) {
          canMove = false;
          break;
        }
      }
      if (canMove) {
        // Put in map based on which inputs will block execution of task
        List<Var> bi = inst.getBlockingInputs();
        if (bi != null) {
          for (Var in: bi) {
            if (Types.isFuture(in.type())) {
              waitMap.put(in.name(), new InstOrCont(inst));
            }
          }
        }
      }
    }
  }
}
