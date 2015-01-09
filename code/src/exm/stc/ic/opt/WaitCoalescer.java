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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.Location;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.MultiMap.LinkedListFactory;
import exm.stc.common.util.MultiMap.ListFactory;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.common.util.StackLite;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.OptUtil.InstOrCont;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.TargetLocation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
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
  // If true, merge continuations
  private final boolean doMerges;
  // If true, retain explicit waits even if removing them is valid
  private final boolean retainExplicit;

  public WaitCoalescer(boolean doMerges, boolean retainExplicit) {
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
      logger.trace("Wait coalescer entering function " + f.getName());
      rearrangeWaits(logger, prog, f, f.mainBlock(), ExecContext.control());
    }
  }

  public boolean rearrangeWaits(Logger logger, Program prog, Function fn,
                                  Block block, ExecContext currContext) {

    boolean merged = false;
    if (doMerges) {
      logger.trace("Merging Waits...");
      merged = mergeWaits(logger, fn, block, currContext);
    }

    logger.trace("Pushing down waits...");
    boolean pushedDown = pushDownWaits(logger, prog, fn, block, currContext);

    // Recurse on child blocks
    boolean recChanged = rearrangeWaitsRec(logger, prog, fn, block, currContext);
    return merged || pushedDown || recChanged;
  }

  private boolean rearrangeWaitsRec(Logger logger, Program prog,
                  Function fn, Block block, ExecContext currContext) {
    // List of waits to inline (to avoid modifying continuations while
    //          iterating over them)
    List<WaitStatement> toInline = new ArrayList<WaitStatement>();

    boolean changed = false;

    for (Continuation c: block.allComplexStatements()) {
      ExecContext newContext = c.childContext(currContext);

      for (Block childB: c.getBlocks()) {
        if (rearrangeWaits(logger, prog, fn, childB,
                           newContext)) {
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
   * @param innerContext
   * @param wait
   * @return true if it should be inlined
   */
  private boolean tryReduce(Logger logger,
      Function fn, ExecContext currContext,
      ExecContext innerContext, WaitStatement wait) {
    if ((currContext.equals(innerContext) &&
        ProgressOpcodes.isCheap(wait.getBlock())) ||
        (currContext.isAnyWorkContext() &&
         innerContext.isControlContext() &&
         canSwitchControlToWorker(logger, fn, wait))) {

      // Fix any waits inside that expect to be execute in CONTROL context
      if (currContext.isAnyWorkContext() &&
          innerContext.isControlContext()) {
        replaceLocalControl(wait.getBlock(), currContext);
      }

      if (wait.getWaitVars().isEmpty()) {
        // Can remove wait
        return true;
      } else {
        // Still have to wait but maybe can reduce overhead
        ExecTarget waitTarget = wait.getTarget();
        ExecContext waitTargetContext = waitTarget.targetContext();
        if (waitTarget.isDispatched() && waitTargetContext.isControlContext()) {
          if (currContext.isControlContext()) {
            // Don't load-balance: assume control work is cheap
            wait.setTarget(ExecTarget.nonDispatchedControl());
          } else {
            // Don't load-balance: assume control work can safely run in
            // current work context
            wait.setTarget(ExecTarget.nonDispatchedAny());
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
      TargetLocation location1, TargetLocation location2,
      Arg par1, Arg par2) {
    if (!c1.equals(c2)) {
      return false;
    }

    return compatibleLocPar(location1, location2, par1, par2);
  }

  /**
   * Check if two contexts are exactly equivalent.  This is
   * somewhat conservative and will reject cases where the
   * contexts don't match exactly.
   * @param location1
   * @param location2
   * @param par1
   * @param par2
   * @return
   */
  private boolean compatibleLocPar(TargetLocation location1,
      TargetLocation location2, Arg par1, Arg par2) {
    Logger logger = Logging.getSTCLogger();
    if (logger.isTraceEnabled()) {
      logger.trace("compatibleLocPar(" + location1 + " " + location2 +
                    " " + par1 + " " + par2 + ")");
    }
    boolean targeted1 = !Location.isAnyLocation(location1.location, true);
    boolean targeted2 = !Location.isAnyLocation(location2.location, true);
    if (targeted1 && targeted2) {
      if (!location1.location.equals(location2.location) ||
          !location1.softTarget.equals(location2.softTarget)) {
        return false;
      }
    } else if (targeted1 || targeted2) {
      // If only one location present
      return false;
    }

    if (par1 != null || par2 != null) {
      if (!par1.equals(par2)) {
        return false;
      }
    }
    return true;
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
    Block waitBlock = wait.getBlock();
    WaitStatement innerWait = null;
    // Can be 0..n sync continuations and 1 async wait
    for (Continuation c: waitBlock.getContinuations()) {
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

    return trySquash(logger, wait, waitContext, waitBlock, innerWait);
  }

  private boolean trySquash(Logger logger, WaitStatement wait,
          ExecContext waitContext, Block waitBlock, WaitStatement innerWait) {

    if (logger.isTraceEnabled()) {
      logger.trace("Attempting squash of  wait(" + wait.getWaitVars() + ") " +
                   wait.getTarget() + " " + wait.getMode() +
                    " with wait(" + innerWait.getWaitVars() + ") " +
                   innerWait.getTarget() + " " + innerWait.getMode());
    }
    ExecContext innerContext = innerWait.childContext(waitContext);

    // Check that locations are compatible
    if (!compatibleLocPar(wait.targetLocation(), innerWait.targetLocation(),
                             wait.parallelism(), innerWait.parallelism())) {
      logger.trace("Locations incompatible");
      return false;
    }

    // Check that recursiveness matches
    if (wait.isRecursive() != innerWait.isRecursive()) {
      logger.trace("Recursiveness incompatible");
      return false;
    }

    // Check that contexts are compatible
    ExecContext possibleMergedContext;
    if (innerContext.equals(waitContext)) {
      possibleMergedContext = waitContext;
    } else {
      if (waitContext.isAnyWorkContext()) {
        // Don't try to move work from worker context to another context
        logger.trace("Contexts incompatible (outer is " + waitContext +
                     " and inner is " + innerContext);
        return false;
      } else if (waitContext.isWildcardContext()) {
        logger.trace("Outer is wildcard: maybe change to worker");
        possibleMergedContext = innerContext;
      } else {
        assert(waitContext.isControlContext());
        assert(innerContext.isAnyWorkContext() ||
               innerContext.isWildcardContext());
        // Inner wait is on worker, try to see if we can
        // move context of outer wait to worker
        logger.trace("Outer is control: maybe change to worker");
        possibleMergedContext = innerContext;
      }
    }

    // Check that wait variables not defined in this block
    for (WaitVar waitVar: innerWait.getWaitVars()) {
      if (waitBlock.getVariables().contains(waitVar.var)) {
        logger.trace("Squash failed: wait var declared inside");
        return false;
      }
    }

    if (!ProgressOpcodes.isNonProgress(waitBlock, possibleMergedContext)) {
      // Progress might be deferred by squashing
      logger.trace("Squash failed: progress would be deferred");
      return false;
    }

    // Pull inner up
    if (logger.isTraceEnabled())
      logger.trace("Squash wait(" + innerWait.getWaitVars() + ")" +
                 " up into wait(" + wait.getWaitVars() + ")");
    wait.addWaitVars(innerWait.getWaitVars());

    if (innerWait.getMode() == WaitMode.TASK_DISPATCH ||
            wait.getMode() == WaitMode.TASK_DISPATCH) {
      // In either case, need to make sure tasks get dispatched
      wait.setMode(WaitMode.TASK_DISPATCH);
    }
    if (!possibleMergedContext.equals(waitContext)) {
      wait.setTarget(ExecTarget.dispatched(possibleMergedContext));
    }

    // Fixup any local waits in block
    fixupNonDispatched(innerWait, possibleMergedContext);
    fixupNonDispatched(wait, possibleMergedContext);

    if (logger.isTraceEnabled()) {
      logger.trace("Squash succeeded: wait(" + wait.getWaitVars() + ") "
                  + wait.getTarget() + " " + wait.getMode());
    }
    innerWait.inlineInto(waitBlock);
    return true;
  }

  private boolean mergeWaits(Logger logger, Function fn, Block block,
      ExecContext execCx) {
    boolean changed = false;
    boolean fin;
    do {
      if (logger.isTraceEnabled()) {
        logger.trace("Attempting to merge waits");
      }
      fin = true;
      MultiMap<Var, WaitStatement> waitMap = buildWaitMap(block);
      // Greedy approach: find most shared variable and
      //    merge wait based on that
      if (logger.isTraceEnabled()) {
        logger.trace("Wait keys: " + waitMap.keySet());
        for (Entry<Var, List<WaitStatement>> e: waitMap.entrySet()) {
          logger.trace("Waiting on : " + e.getKey() + ": "
                       + e.getValue().size());
        }
      }
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
            WaitMode.WAIT_ONLY, allRecursive, ExecTarget.nonDispatchedAny(),
            new TaskProps());

        // Put the old waits under the new one, remove redundant wait vars
        // Exception: don't eliminate task dispatch waits
        for (WaitStatement wait: waits) {
          wait.removeWaitVars(mergedWaitVars, allRecursive, retainExplicit);

          boolean compatible = compatibleContexts(execCx,
              wait.childContext(execCx), TargetLocation.ANY, wait.targetLocation(),
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
  private boolean pushDownWaits(Logger logger, Program prog, Function fn,
                                Block block, ExecContext currContext) {
    MultiMap<Var, InstOrCont> waitMap = buildWaiterMap(prog, block);

    if (logger.isTraceEnabled()) {
      logger.trace("waitMap keys: " + waitMap.keySet());
    }

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
          StackLite<Continuation> ancestors =
                                        new StackLite<Continuation>();
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
                StackLite<Continuation> ancestors, Block curr,
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

          // Relocate instructions which depend on output future of this instruction
          for (Var out: pushdownPoint.getOutputs()) {
            if (trackForPushdown(out)) {
              if (logger.isTraceEnabled()) {
                logger.trace("Check output for pushdown: " + out.name());
              }
              boolean success = tryPushdownClosedVar(logger, top, topContext, ancestors, curr,
                  currContext, waitMap, pushedDown, it, out);
              changed = changed || success;
            }
          }
          break;
        }
        case CONDITIONAL: {
          // Can't push down into conditional
          // TODO: maybe could push down to both branches in some cases?

          // Find futures assigned on all branches
          Set<Var> writtenFutures = findConditionalAssignedFutures(logger, stmt.conditional());

          for (Var writtenFuture: writtenFutures) {
            assert(Types.isFuture(writtenFuture));
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
    for (Var closedFirstBranch: Sets.intersectionIter(branchStates)) {
      initState.add(closedFirstBranch);
    }
  }

  public void addWrittenFuturesFromBlock(Logger logger, Block b, Set<Var> branchState) {
    for (Statement stmt: b.getStatements()) {
      if (stmt.type() == StatementType.INSTRUCTION) {
        for (Var out: stmt.instruction().getOutputs()) {
          if (Types.isFuture(out)) {
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
      ExecContext topContext, StackLite<Continuation> ancestors, Block curr,
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
        ExecTarget target = w.getTarget();
        if (target.isDispatched()) {
          return target.targetContext();
        } else {
          assert(target.canRunIn(curr));
          // Context doesn't change
          return curr;
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
      StackLite<Continuation> ancestors,
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
      StackLite<Continuation> ancestors, Block currBlock,
      ExecContext currContext, Set<Continuation> movedC, Continuation cont) {
    boolean canRelocate = true;
    // Check we're not relocating continuation into itself
    for (Continuation ancestor: ancestors) {
      if (cont == ancestor) {
        canRelocate = false;
        break;
      }
    }

    if (currContext.isAnyWorkContext()) {
      if (cont.getType() != ContinuationType.WAIT_STATEMENT) {
        canRelocate = false;
      } else {
        WaitStatement w = (WaitStatement)cont;
        // Make sure gets dispatched to right place
        if (w.getTarget().isAsync()) {
          canRelocate = true;

          if (!w.getTarget().isDispatched()) {
            fixupNonDispatched(w, currContext);
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

  /**
   * Replace local control waits recursively
   * TODO: do we want a more generic approach?
   * @param w
   */
  private void fixupNonDispatched(WaitStatement w, ExecContext currContext) {
    if (!w.getTarget().canRunIn(currContext)) {
      w.setMode(WaitMode.TASK_DISPATCH);
      ExecContext targetCx = w.getTarget().targetContext();
      w.setTarget(ExecTarget.dispatched(targetCx));
    }

    // Check if we need to recurse
    if (!w.getTarget().isDispatched()) {
      replaceLocalControl(w.getBlock(), currContext);
    }
  }

  private void replaceLocalControl(Block block, ExecContext currContext) {
    for (Continuation c: block.allComplexStatements()) {
      if (!c.isAsync()) {
        // Locate any inner waits that are sync
        for (Block inner: c.getBlocks()) {
          replaceLocalControl(inner, currContext);
        }
      }
      if (c.getType() == ContinuationType.WAIT_STATEMENT) {
        WaitStatement w = (WaitStatement) c;
        fixupNonDispatched(w, currContext);
      }
    }
  }

  private static boolean relocateInstruction(
      StackLite<Continuation> ancestors, ExecContext currContext,
      Block currBlock,
      ListIterator<Statement> currBlockIt,
      Set<Instruction> movedI, Instruction inst) {

    inst.setParent(currBlock);
    currBlockIt.add(inst);
    movedI.add(inst);

    return true;
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
  private static MultiMap<Var, InstOrCont> buildWaiterMap(Program prog,
                                                          Block block) {
    // Use linked list to support more efficient removal in middle of list
    MultiMap<Var, InstOrCont> waitMap =
                        new MultiMap<Var, InstOrCont>(LL_FACT);
    findRelocatableBlockingInstructions(prog, block, waitMap);
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

  private static void findRelocatableBlockingInstructions(Program prog,
          Block block, MultiMap<Var, InstOrCont> waitMap) {
    for (Statement stmt: block.getStatements()) {
      if (stmt.type() != StatementType.INSTRUCTION) {
        continue; // Only interested in instructions
      }

      Instruction inst = stmt.instruction();
      // check all outputs are non-alias futures - if not can't safely reorder
      boolean canMove = true;
      for (Var out: inst.getOutputs()) {
        if (!Types.isFuture(out) || out.storage() == Alloc.ALIAS) {
          canMove = false;
          break;
        }
      }
      if (canMove) {
        // Put in map based on which inputs will block execution of task
        List<Var> bi = inst.getBlockingInputs(prog);
        if (bi != null) {
          for (Var in: bi) {
            if (trackForPushdown(in)) {
              waitMap.put(in, new InstOrCont(inst));
            }
          }
        }
      }
    }
  }

  /**
   * Check whether we should track for pushing down waits
   * @param var
   * @return
   */
  private static boolean trackForPushdown(Var var) {
    return var.storage() != Alloc.LOCAL;
  }
}
