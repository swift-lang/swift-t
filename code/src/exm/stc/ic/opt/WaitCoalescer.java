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
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.MultiMap.LinkedListFactory;
import exm.stc.common.util.MultiMap.ListFactory;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.OptUtil.InstOrCont;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

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
  // If true, explode dataflow ops
  private final boolean doExplode;
  // If true, merge continuations
  private final boolean doMerges;
  // If true, retain explicit waits even if removing them is valid
  private final boolean retainExplicit;
  
  public WaitCoalescer(boolean doExplode, boolean doMerges,
                       boolean retainExplicit) {
    this.doExplode = doExplode;
    this.doMerges = doMerges;
    this.retainExplicit = retainExplicit;
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
      rearrangeWaits(logger, f, f.mainBlock(), ExecContext.CONTROL);
    }
  }

  public boolean rearrangeWaits(Logger logger, Function fn, Block block,
                                       ExecContext currContext) {
    boolean exploded = false;
    logger.trace("Entering function " + fn.getName());
    try {
      if (doExplode && Settings.getBoolean(Settings.OPT_EXPAND_DATAFLOW_OPS)) {
        logger.trace("Exploding Function Calls...");
        exploded = explodeFuncCalls(logger, fn, currContext, block);
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }

    if (logger.isTraceEnabled()) {
      //StringBuilder sb = new StringBuilder();
      //fn.prettyPrint(sb);
      //logger.trace("After exploding " + fn.getName() +":\n" + sb.toString());
    }
    
    boolean merged = false;
    if (doMerges) {
      logger.trace("Merging Waits...");
      merged = mergeWaits(logger, fn, block, currContext);
    }
    
    if (logger.isTraceEnabled()) {
      //sb = new StringBuilder();
      //fn.prettyPrint(sb);
      //logger.trace("After merging " + fn.getName() +":\n" + sb.toString());
    }
    
    logger.trace("Pushing down waits...");
    boolean pushedDown = pushDownWaits(logger, fn, block, currContext);
    
    // Recurse on child blocks
    boolean recChanged = rearrangeWaitsRec(logger, fn, block, currContext);
    return exploded || merged || pushedDown || recChanged;
  }

  private boolean rearrangeWaitsRec(Logger logger,
                  Function fn, Block block, ExecContext currContext) {
    // List of waits to inline (to avoid modifying continuations while
    //          iterating over them)
    List<WaitStatement> toInline = new ArrayList<WaitStatement>();
    
    boolean changed = false;
    
    for (Continuation c: block.allComplexStatements()) {
      ExecContext newContext = c.childContext(currContext);
      for (Block childB: c.getBlocks()) {
        if (rearrangeWaits(logger, fn, childB, newContext)) {
          changed = true;
        }
      }
      
      if (c.getType() == ContinuationType.WAIT_STATEMENT) {
        WaitStatement wait = (WaitStatement)c;
        if (tryReduce(logger, fn, currContext, newContext, wait)) {
          toInline.add(wait);
        } else if (squashWaits(logger, fn, wait, newContext)) {
          changed = true;
        }
      }
    }
    for (WaitStatement w: toInline) {
      w.inlineInto(block);
      changed = true;
    }
    return changed;
  }

  /**
   * try to reduce to a simpler form of wait
   * @param currContext
   * @param toInline
   * @param newContext
   * @param wait
   */
  private boolean tryReduce(Logger logger,
      Function fn, ExecContext currContext,
      ExecContext newContext, WaitStatement wait) {
    if ((currContext == newContext &&
        ProgressOpcodes.isCheap(wait.getBlock())) ||
        (currContext == ExecContext.WORKER && 
         newContext == ExecContext.CONTROL &&
         canSwitchControlToWorker(logger, fn, wait))) {
      if (wait.getWaitVars().isEmpty()) {
        return true;
      } else {
        // Still have to wait but maybe can reduce overhead
        if (wait.getTarget() == TaskMode.CONTROL) {
          // Don't load-balance
          if (currContext == ExecContext.CONTROL) {
            wait.setTarget(TaskMode.LOCAL_CONTROL);
          } else {
            wait.setTarget(TaskMode.LOCAL);
          }
        }
        if (wait.getMode() == WaitMode.TASK_DISPATCH) {
          wait.setMode(WaitMode.WAIT_ONLY);
        }
      }
    }
    return false;
  }

  /**
   * Return true if ok to run wait contents in worker context
   * @param wait
   * @return
   */
  private boolean canSwitchControlToWorker(Logger logger,
      Function fn, WaitStatement wait) {
    if (!ProgressOpcodes.isCheapWorker(wait.getBlock())) {
      return false;
    }
    
    // Hack to allow inner class to modify
    final boolean safe[] = new boolean[1];
    safe[0] = true;
    
    TreeWalker walker = new TreeWalker() {
      @Override
      protected void visit(Continuation cont) {
        if (!cont.isAsync())
          return;
        
        switch (cont.getType()) {
          case WAIT_STATEMENT:
          case LOOP:
            // Safe
            break;
          default:
            // Don't want to run foreach loops, etc on worker
            safe[0] = false;
        }
      }
      
    };
    TreeWalk.walkSyncChildren(logger, fn, wait, walker);
    return safe[0];
  }

  /**
   * Check to see if two separate waits can be merged together, i.e.
   * if their context is compatible
   * @param c1
   * @param c2
   * @param location1
   * @param location2
   * @param par1
   * @param par2
   * @return
   */
  private boolean compatibleContexts(ExecContext c1, ExecContext c2,
                        Arg location1, Arg location2, Arg par1, Arg par2) {
    if (!c1.equals(c2)) {
      return false;
    }
    
    if (location1 != null && location2 != null) {
      if (!location1.equals(location2)) {
        return false;
      }
    }
    
    if (par1 != null || par2 != null) {
      if (!par1.equals(par2)) {
        return false;
      }
    }
    return true;
  }
  
  private boolean compatibleContexts(WaitStatement w1, ExecContext c1,
                                     WaitStatement w2, ExecContext c2) {
    return compatibleContexts(c1, c2, w1.targetLocation(), w2.targetLocation(),
                              w1.parallelism(), w2.parallelism());
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
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      // Only handle instructions: don't recurse here
      if (stmt.type() == StatementType.INSTRUCTION) {
        if (tryExplode(logger, fn, execCx, block, it, stmt.instruction())) {
          changed = true;
        } 
      }
    }
    return changed;
  }
  
  /**
   * Attempt to explode individual instruction
   * @param logger
   * @param fn
   * @param execCx
   * @param block
   * @param it
   * @param inst
   * @return true if exploded
   */
  private static boolean tryExplode(Logger logger, Function fn,
        ExecContext execCx, Block block, ListIterator<Statement> it,
        Instruction inst) {
    
    MakeImmRequest req = inst.canMakeImmediate(
                            Collections.<Var>emptySet(),
                            Collections.<Var>emptySet(), true);
    if (req != null && req.in.size() > 0) {
      if (logger.isTraceEnabled()) {
        logger.trace("Exploding " + inst + " in function " + fn.getName());
      }
      List<Var> waitVars = ICUtil.filterBlockingOnly(req.in);
      
      WaitMode waitMode;
      if (req.mode == TaskMode.SYNC || req.mode == TaskMode.LOCAL ||
            (req.mode == TaskMode.LOCAL_CONTROL &&
              execCx == ExecContext.CONTROL)) {
        waitMode = WaitMode.WAIT_ONLY;
      } else {
        waitMode = WaitMode.TASK_DISPATCH;
      }
      
      TaskProps props = inst.getTaskProps();
      if (props == null) {
        props = new TaskProps();
      }
      
      WaitStatement wait = new WaitStatement(
              fn.getName() + "-" + inst.shortOpName(),
              WaitVar.makeList(waitVars, false), PassedVar.NONE, Var.NONE,
              waitMode, req.recursiveClose, req.mode, props);
      block.addContinuation(wait);
      
      List<Instruction> instBuffer = new ArrayList<Instruction>();
      
      // Fetch the inputs
      List<Arg> inVals = OptUtil.fetchValuesOf(wait.getBlock(),
                                            instBuffer, req.in);
      
      if (props.containsKey(TaskPropKey.PARALLELISM)) {
        //TODO: different output var conventions
        throw new STCRuntimeError("Don't know how to explode parallel " +
        		"instruction yet: " + inst);
      }
      // Create local instruction, copy out outputs
      List<Var> localOutputs = OptUtil.declareLocalOpOutputVars(
                                          wait.getBlock(), req.out);
      MakeImmChange change = inst.makeImmediate(localOutputs, inVals);
      OptUtil.fixupImmChange(block, wait.getBlock(), change, instBuffer,
                                        localOutputs, req.out);
      
      // Remove old instruction, add new one inside wait block
      it.remove();
      wait.getBlock().addInstructions(instBuffer);
      return true;
    }
    return false;
  }

  /**
   * Try to squash together waits, e.g.
   * wait (x) {
   *   < do some minor work that doesn't help with progress >
   *   wait (y) {
   *    < do some real work >
   *   }
   * }
   * 
   * gets changed to
   * wait (x, y) {
   *  ...
   * }
   * @param logger
   * @param fn
   * @param block
   */
  private boolean squashWaits(Logger logger, Function fn, WaitStatement wait,
      ExecContext waitContext) {
    Block block = wait.getBlock();
    WaitStatement innerWait = null;
    // Can be 0..n sync continuations and 1 async wait
    for (Continuation c: block.getContinuations()) {
      if (c.isAsync()) {
        if (c.getType() == ContinuationType.WAIT_STATEMENT) {
          if (innerWait != null) {
            // Can't have two waits
            return false;
          } else {
            innerWait = (WaitStatement)c;
          }
        } else {
          return false;
        }
      }
    }
    
    if (innerWait == null)
      return false;
    
    ExecContext innerContext = innerWait.childContext(waitContext);
    // Check that locations are compatible
    if (!compatibleContexts(wait, waitContext, innerWait, innerContext)) {
      return false;
    }
    
    // Check that wait variables not defined in this block
    for (WaitVar waitVar: innerWait.getWaitVars()) {
      if (block.getVariables().contains(waitVar.var)) {
        return false;
      }
    }
    
    if (!ProgressOpcodes.isNonProgress(block)) {
      // Progress might be deferred by squashing
      return false;
    }
    // Pull inner up
    if (logger.isTraceEnabled())
      logger.trace("Squash wait(" + innerWait.getWaitVars() + ")" +
                 " up into wait(" + wait.getWaitVars() + ")");
    wait.addWaitVars(innerWait.getWaitVars());
    innerWait.inlineInto(block);
    return true;
  }

  private boolean mergeWaits(Logger logger, Function fn, Block block,
      ExecContext execCx) {
    boolean changed = false;
    boolean fin;
    do {
      fin = true;
      MultiMap<Var, WaitStatement> waitMap = buildWaitMap(block);
      // Greedy approach: find most shared variable and
      //    merge wait based on that
      Var winner = mostSharedVar(waitMap);
      if (winner != null) {
        // There is some shared variable between waits
        fin = false;
        changed = true;
        List<WaitStatement> waits = waitMap.get(winner);
        assert(waits != null && waits.size() >= 2);
        logger.trace("Merging " + waits.size() + " Waits...");
        
        // If one of the waits is explicit, new one must be also
        boolean explicit = false; 
        
        // If all waits are recursive
        boolean allRecursive = true;
        
        // Find out which variables are in common with all waits
        Set<Var> explicitVars = new HashSet<Var>();
        Set<Var> notExplicitVars = new HashSet<Var>();
        Set<Var> intersection = null;
        for (WaitStatement wait: waits) {
          Set<Var> waitVars = new HashSet<Var>();
          for (WaitVar wv: wait.getWaitVars()) {
            waitVars.add(wv.var);
            if (wv.explicit) {
              explicitVars.add(wv.var);
            } else {
              notExplicitVars.add(wv.var);
            }
          }
          if (intersection == null) {
            intersection = waitVars;
          } else {
            intersection.retainAll(waitVars);
          }
          explicit = explicit || wait.getMode() != WaitMode.WAIT_ONLY;
          allRecursive = allRecursive && wait.isRecursive();
        }
        assert(intersection != null && !intersection.isEmpty());
        
        List<WaitVar> mergedWaitVars = new ArrayList<WaitVar>();
        for (Var v: intersection) {
          // Only should be explicit if both are, to avoid adding
          // extra ordering dependencies
          boolean mergedExplicit = explicitVars.contains(v) &&
                                   !notExplicitVars.contains(v);
          mergedWaitVars.add(new WaitVar(v, mergedExplicit));
        }
        // Create a new wait statement waiting on the intersection
        // of the above.
        WaitStatement newWait = new WaitStatement(fn.getName() + "-optmerged",
            mergedWaitVars, PassedVar.NONE, Var.NONE,
            WaitMode.WAIT_ONLY, allRecursive, TaskMode.LOCAL,
            new TaskProps());

        // Put the old waits under the new one, remove redundant wait vars
        // Exception: don't eliminate task dispatch waits
        for (WaitStatement wait: waits) {
          wait.removeWaitVars(mergedWaitVars, allRecursive, retainExplicit);
          
          boolean compatible = compatibleContexts(execCx,
              wait.childContext(execCx), null, wait.targetLocation(),
              null, wait.parallelism());
          if (compatible &&
              wait.getWaitVars().isEmpty() &&
              wait.getMode() != WaitMode.TASK_DISPATCH) {
            newWait.getBlock().insertInline(wait.getBlock());
          } else {
            wait.setParent(newWait.getBlock());
            newWait.getBlock().addContinuation(wait);
          }
        }
        
        block.addContinuation(newWait);
        block.removeContinuations(waits);
      }
      changed = changed || !fin;
    } while (!fin);
    return changed;
  }

  /**
   * Build a map of <variable> --> wait statements blocking on that value 
   * @param block
   * @return
   */
  public static  MultiMap<Var, WaitStatement> buildWaitMap(Block block) {
    MultiMap<Var, WaitStatement> waitMap = new MultiMap<Var, WaitStatement>(); 
    for (Continuation c: block.getContinuations()) {
      if (c.getType() == ContinuationType.WAIT_STATEMENT) {
        WaitStatement wait = (WaitStatement)c;
        
        // Defensively check for duplicates since duplicates cause issues here
        List<WaitVar> waitVars = new ArrayList<WaitVar>(wait.getWaitVars());
        WaitVar.removeDuplicates(waitVars);
        for (WaitVar wv: waitVars) {
          waitMap.put(wv.var, wait);
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
  public static Var mostSharedVar(
          MultiMap<Var, WaitStatement> waitMap) {
    Var winner = null;
    int winCount = 0;
    for (Entry<Var, List<WaitStatement>> e: waitMap.entrySet()) {
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
  
  private static class PushDownResult {
    public final boolean anyChanges;
    public final List<Continuation> relocated;
    
    public PushDownResult(boolean anyChanges, List<Continuation> relocated) {
      super();
      this.anyChanges = anyChanges;
      this.relocated = relocated;
    }
  }

  /**
   * Try to push down waits from current block into child blocks
   * @param logger
   * @param fn
   * @param block
   * @param currContext
   * @return
   */
  private boolean pushDownWaits(Logger logger, Function fn, Block block,
                                      ExecContext currContext) {
    MultiMap<Var, InstOrCont> waitMap = buildWaiterMap(block);
    
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
          ArrayDeque<Continuation> ancestors =
                                        new ArrayDeque<Continuation>();
          ancestors.push(c);
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
  
  private PushDownResult pushDownWaitsRec(
                Logger logger,  Function fn,
                Block top, ExecContext topContext,
                Deque<Continuation> ancestors, Block curr,
                ExecContext currContext,
                MultiMap<Var, InstOrCont> waitMap) {
    boolean changed = false;
    ArrayList<Continuation> pushedDown = new ArrayList<Continuation>();
    /* Iterate over all instructions in this descendant block */
    ListIterator<Statement> it = curr.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction pushdownPoint = stmt.instruction();
          if (logger.isTraceEnabled()) {
            logger.trace("Pushdown at: " + pushdownPoint.toString());
          }
          List<Var> writtenFutures = new ArrayList<Var>();
          for (Var outv: pushdownPoint.getOutputs()) {
            if (Types.isFuture(outv.type())) {
              writtenFutures.add(outv);
            }
          }
          
          // Relocate instructions which depend on output future of this instruction
          for (Var writtenFuture: writtenFutures) {
            boolean success = tryPushdownClosedVar(logger, top, topContext, ancestors, curr,
                currContext, waitMap, pushedDown, it, writtenFuture);
            changed = changed || success;
          }
          break;
        }
        case CONDITIONAL: {
          // Can't push down into conditional
          // TODO: maybe could push down to both branches in some cases?
          
          // Find futures assigned on all branches
          Set<Var> writtenFutures = findConditionalAssignedFutures(logger, stmt.conditional());

          for (Var writtenFuture: writtenFutures) {
            assert(Types.isFuture(writtenFuture.type()));
            boolean success = tryPushdownClosedVar(logger, top, topContext, ancestors, curr,
                currContext, waitMap, pushedDown, it, writtenFuture);
            changed = changed || success;
          }
          break;
        }
        default:
          throw new STCRuntimeError("unknown statement type " + stmt.type());
      }
      
    }
    
    // Update the stack with child continuations
    for (Continuation c: curr.getContinuations()) {
      ExecContext newContext = canPushDownInto(c, currContext);
      if (newContext != null) {
        for (Block innerBlock: c.getBlocks()) {
          ancestors.push(c);
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
   * @param logger
   * @param conditional
   * @return Any variables that are closed after the conditional finishes executing
   */
  private Set<Var> findConditionalAssignedFutures(Logger logger,
      Conditional conditional) {
    if (!conditional.isExhaustiveSyncConditional()) {
      return Collections.emptySet();
    }
    
    Set<Var> initState = new HashSet<Var>();
    findConditionalAssignedFutures(logger, conditional, initState);
    return initState;
  }

  private void findConditionalAssignedFutures(Logger logger,
      Conditional conditional, Set<Var> initState) {
    assert(conditional.isExhaustiveSyncConditional());
    
    List<Set<Var>> branchStates = new ArrayList<Set<Var>>();
    for (Block b: conditional.getBlocks()) {
      Set<Var> branchState = new HashSet<Var>();
      addWrittenFuturesFromBlock(logger, b, branchState);
      branchStates.add(branchState);
    }
    
    // unify states from different branches
    for (Var closedFirstBranch: Sets.intersection(branchStates)) {
      initState.add(closedFirstBranch);
    }
  }

  public void addWrittenFuturesFromBlock(Logger logger, Block b, Set<Var> branchState) {
    for (Statement stmt: b.getStatements()) {
      if (stmt.type() == StatementType.INSTRUCTION) {
        for (Var out: stmt.instruction().getOutputs()) {
          if (Types.isFuture(out.type())) {
            branchState.add(out);
          }
        }
      } else {
        assert(stmt.type() == StatementType.CONDITIONAL);
        Conditional cond = stmt.conditional();
        if (cond.isExhaustiveSyncConditional()) {
          // Recurse to update state with closed vars from conditional
          findConditionalAssignedFutures(logger, cond, branchState);
        }
      }
    }
  }

  public boolean tryPushdownClosedVar(Logger logger, Block top,
      ExecContext topContext, Deque<Continuation> ancestors, Block curr,
      ExecContext currContext, MultiMap<Var, InstOrCont> waitMap,
      ArrayList<Continuation> pushedDown, ListIterator<Statement> it,
      Var v) {
    boolean changed = false;
    if (waitMap.containsKey(v)) {
      Pair<Boolean, Set<Continuation>> pdRes =
          relocateDependentInstructions(logger, top, topContext, ancestors, 
                                    curr, currContext, it,  waitMap, v);
      changed = pdRes.val1;
      pushedDown.addAll(pdRes.val2);
    }
    return changed;
  }
  
  /**
   * 
   * @param c
   * @param curr
   * @return null if can't push down into
   */
  private static ExecContext canPushDownInto(Continuation c,
                                                 ExecContext curr) {
    /* Can push down into wait statements unless they have wrong
     * execution context or are parallel */
    if (c.getType() == ContinuationType.WAIT_STATEMENT) {
      WaitStatement w = (WaitStatement)c;
      if (w.isParallel()) {
        return null;
      }
      if (w.getMode() == WaitMode.TASK_DISPATCH) {
        if (w.getTarget() == TaskMode.LOCAL_CONTROL ||
            w.getTarget() == TaskMode.LOCAL) {
          if (curr == ExecContext.WORKER &&
                w.getTarget() == TaskMode.LOCAL_CONTROL) {
            throw new STCRuntimeError("Can't have local control wait in leaf"); 
          }
          // Context doesn't change
          return curr;
        } else if (w.getTarget() == TaskMode.CONTROL) {
          return ExecContext.CONTROL;
        } else {
          assert(w.getTarget() == TaskMode.WORKER);
          return ExecContext.WORKER;
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
   * @param currBlockIt  all changes to instructions in curr block
   *    are made through this iterator, and it is rewound to the previous position
   *    before the function exits
   * @param waitMap map of variable names to instructions/continuations they block
   *                  on
   * @param writtenV
   * @return true if change made, list of moved continuations
   */
  private Pair<Boolean, Set<Continuation>> relocateDependentInstructions(
      Logger logger,
      Block ancestorBlock, ExecContext ancestorContext,
      Deque<Continuation> ancestors,
      Block currBlock, ExecContext currContext, ListIterator<Statement> currBlockIt,
      MultiMap<Var, InstOrCont> waitMap, Var writtenV) {
    boolean changed = false;
    // Remove from outer block
    List<InstOrCont> waits = waitMap.get(writtenV);
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
          if (logger.isTraceEnabled())
            logger.trace("Relocating " + ic.continuation().getType());
          relocated = relocateContinuation(ancestors, currBlock,
              currContext, movedC, ic.continuation());
          
          break;
        } 
        case INSTRUCTION:
          if (logger.isTraceEnabled())
            logger.trace("Relocating " + ic.instruction());
          relocated = relocateInstruction(ancestors, currContext,
              currBlock, currBlockIt, movedI, ic.instruction());
          break;
        default:
          throw new STCRuntimeError("how on earth did we get here...");
      }
      changed = changed || relocated;
    }
    // Remove instructions from old block
    ancestorBlock.removeContinuations(movedC);
    ancestorBlock.removeStatements(movedI);
    
    // Rewind iterator so that next instruction returned
    // will be the first one added
    ICUtil.rewindIterator(currBlockIt, movedI.size());
    
    // Rebuild wait map to reflect changes
    updateWaiterMap(waitMap, movedC, movedI);
    return Pair.create(changed, movedC);
  }

  private boolean relocateContinuation(
      Deque<Continuation> ancestors, Block currBlock,
      ExecContext currContext, Set<Continuation> movedC, Continuation cont) {
    boolean canRelocate = true;
    // Check we're not relocating continuation into itself
    for (Continuation ancestor: ancestors) {
      if (cont == ancestor) {
        canRelocate = false;
        break;
      }
    }
    
    if (currContext == ExecContext.WORKER) {
      if (cont.getType() != ContinuationType.WAIT_STATEMENT) {
        canRelocate = false;
      } else {
        WaitStatement w = (WaitStatement)cont;
        // Make sure gets dispatched to right place
        if (w.getTarget() == TaskMode.CONTROL ||
            w.getTarget() == TaskMode.WORKER ||
            w.getTarget() == TaskMode.LOCAL) {
          canRelocate = true;
        } else if (w.getTarget() == TaskMode.LOCAL_CONTROL) {
          if (w.hasExplicit() && retainExplicit) {
            canRelocate = false;
          } else {
            w.setMode(WaitMode.TASK_DISPATCH);
            w.setTarget(TaskMode.CONTROL);
          }
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
    }
    return canRelocate;
  }

  private static boolean relocateInstruction(
      Deque<Continuation> ancestors, ExecContext currContext,
      Block currBlock,
      ListIterator<Statement> currBlockIt,
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
    
    if (currContext == ExecContext.WORKER) {
      if (inst.getMode() != TaskMode.SYNC) {
        // Can't push down async tasks to leaf yet
        canRelocate = false;
      }
    }
    
    if (canRelocate) {
      inst.setParent(currBlock);
      currBlockIt.add(inst);
      movedI.add(inst);
    }
    return canRelocate;
  }

  /**
   * Update waiter map by removing continuations and instructions
   * based on object identity
   * @param waitMap
   * @param removedC
   * @param removedI
   */
  private static void updateWaiterMap(
          MultiMap<Var, InstOrCont> waitMap, Set<Continuation> removedC,
      Set<Instruction> removedI) {
    List<Var> keysToRemove = new ArrayList<Var>();
    for (Entry<Var, List<InstOrCont>> e: waitMap.entrySet()) {
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
    for (Var k: keysToRemove) {
      waitMap.remove(k);
    }
  }

  private static ListFactory<InstOrCont> LL_FACT = 
                    new LinkedListFactory<InstOrCont>();
  private static MultiMap<Var, InstOrCont> buildWaiterMap(Block block) {
    // Use linked list to support more efficient removal in middle of list
    MultiMap<Var, InstOrCont> waitMap =
                        new MultiMap<Var, InstOrCont>(LL_FACT); 
    findRelocatableBlockingInstructions(block, waitMap);
    findBlockingContinuations(block, waitMap);
    return waitMap;
  }

  private static void findBlockingContinuations(Block block,
          MultiMap<Var, InstOrCont> waitMap) {
    for (Continuation c: block.getContinuations()) {
      List<BlockingVar> blockingVars = c.blockingVars(false);
      if (blockingVars != null) {
        for (BlockingVar v: blockingVars) {
          waitMap.put(v.var, new InstOrCont(c));
        }
      }
    }
  }

  private static void findRelocatableBlockingInstructions(Block block,
          MultiMap<Var, InstOrCont> waitMap) {
    for (Statement stmt: block.getStatements()) {
      if (stmt.type() != StatementType.INSTRUCTION) {
        continue; // Only interested in instructions
      }
      
      Instruction inst = stmt.instruction();
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
              waitMap.put(in, new InstOrCont(inst));
            }
          }
        }
      }
    }
  }
}
