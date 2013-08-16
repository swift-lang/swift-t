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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.opt.ProgressOpcodes.Category;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.opt.ValueTracker.UnifiedState;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

/**
 * This optimisation pass does a range of optimizations. The overarching idea is
 * that we move forward through the IC and keep track of, at each instruction,
 * which variables will be closed, and what values have already been computed.
 * There are several ways we exploit this:
 * 
 * - If a future is known to be closed, we can retrieve the value and perform
 * operations locally, or we can eliminate a wait - If the same value is
 * computed twice, we can reuse the earlier value - If a value has been inserted
 * into an array, and we retrieve a value from the same index, we can skip the
 * load. Same for struct loads and stores - etc.
 * 
 * This optimization pass doesn't remove any instructions: it simply modifies
 * the arguments to each instruction in a way that will hopefully lead a bunch
 * of dead code, which can be cleaned up in a pass of the dead code eliminator
 * 
 */
public class ForwardDataflow implements OptimizerPass {

  /**
   * True if this pass is allowed to reorder instructions. If false, guarantees
   * that future passes won't try reordering.
   */
  private boolean reorderingAllowed;

  public ForwardDataflow(boolean reorderingAllowed) {
    this.reorderingAllowed = reorderingAllowed;
  }

  @Override
  public String getPassName() {
    return "Forward dataflow";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_FORWARD_DATAFLOW;
  }

  private static void updateReplacements(Logger logger, Function function,
      Instruction inst, ValueTracker av,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) {
    List<ResultVal> irs = inst.getResults(av);
    
    if (irs != null) {
      if (logger.isTraceEnabled()) {
        logger.trace("irs: " + irs.toString());
      }
      for (ResultVal resVal : irs) {
        if (ComputedValue.isAlias(resVal)) {
          replaceAll
              .put(resVal.location().getVar(), resVal.value().getInput(0));
          continue;
        } else if (resVal.value().isCopy()) {
          // Copies are easy to handle: replace output of inst with input
          // going forward
          replaceInputs.put(resVal.location().getVar(), resVal.value()
              .getInput(0));
          continue;
        }
        Arg currLoc = resVal.location();
        if (!av.isAvailable(resVal.value())) {
          // Can't replace, track this value
          av.addComputedValue(resVal, Ternary.FALSE);
        } else if (currLoc.isConstant()) {
          Arg prevLoc = av.getLocation(resVal.value());
          if (prevLoc.isVar()) {
            assert (Types.isScalarValue(prevLoc.getVar().type()));
            // Constants are the best... might as well replace
            av.addComputedValue(resVal, Ternary.TRUE);
            // System.err.println("replace " + prevLoc + " with " + currLoc);
            replaceInputs.put(prevLoc.getVar(), currLoc);
          } else {
            // Should be same, otherwise bug (in user script or compiler)
            if (!currLoc.equals(prevLoc)) {
              Logging .uniqueWarn("Invalid code detected during optimization. "
                      + "Conflicting values for " + resVal + ": " + prevLoc + " != "
                      + currLoc + " in " + function.getName() + ".\n"
                      + "This may have been caused by a double-write to a variable. "
                      + "Please look at any previous warnings emitted by compiler. "
                      + "Otherwise this could indicate a stc bug");

              // Invalidate any computed values associated with instruction to
              // minimise chance of invalid optimization
              purgeValues(logger, av, irs);
              return;
            }
          }
        } else {
          final boolean usePrev;
          assert (currLoc.isVar());
          // See if we should replace
          Arg prevLoc = av.getLocation(resVal.value());
          if (prevLoc.isConstant()) {
            usePrev = true;
          } else {
            assert (prevLoc.isVar());
            boolean currClosed = av.isClosed(currLoc.getVar());
            boolean prevClosed = av.isClosed(prevLoc.getVar());
            if (resVal.equivType() == EquivalenceType.REFERENCE) {
              // The two locations are both references to same thing, so can
              // replace all references, including writes to currLoc
              replaceAll.put(currLoc.getVar(), prevLoc);
            }
            if (prevClosed || !currClosed) {
              // Use the prev value
              usePrev = true;
            } else {
              /*
               * The current variable is closed but the previous isn't. Its
               * probably better to use the closed one to enable further
               * optimisations
               */
              usePrev = false;
            }
          }

          // Now we've decided whether to use the current or previous
          // variable for the computed expression
          if (usePrev && resVal.isSubstitutable()) {
            // Do it
            if (logger.isTraceEnabled())
              logger.trace("replace " + currLoc + " with " + prevLoc);
            replaceInputs.put(currLoc.getVar(), prevLoc);
          } else {
            if (logger.isTraceEnabled())
              logger.trace("replace " + prevLoc + " with " + currLoc);
            replaceInputs.put(prevLoc.getVar(), currLoc);
          }
        }
      }
    } else {
      logger.trace("no icvs");
    }
  }

  private static void purgeValues(Logger logger, ValueTracker state, List<ResultVal> rvs) {
    for (ResultVal rv: rvs) {
      Logging.getSTCLogger().debug("Invalidating " + rv);
      state.invalidateComputedValue(rv.value());
    }
  }

  /**
   * Do a kind of dataflow analysis where we try to identify which futures are
   * closed at different points in the program. This allows us to switch to
   * lower-overhead versions of many operations.
   * 
   * @param logger
   * @param program
   * @throws InvalidOptionException
   * @throws InvalidWriteException
   */
  public void optimize(Logger logger, Program program)
      throws InvalidOptionException, InvalidWriteException {
    ValueTracker globalState = new ValueTracker(logger, reorderingAllowed);

    for (Var v : program.getGlobalVars()) {
      // First, all constants can be treated as being set
      if (v.storage() == VarStorage.GLOBAL_CONST) {
        Arg val = program.lookupGlobalConst(v.name());
        assert (val != null) : v.name();
        ResultVal compVal = ICInstructions.assignComputedVal(v, val);
        globalState.addComputedValue(compVal,
            Ternary.fromBool(globalState.isAvailable(compVal.value())));
      }
    }
    for (Function f : program.getFunctions()) {
      // Do repeated passes until converged
      boolean changes;
      int pass = 1;
      do {
        logger.trace("closed variable analysis on function " + f.getName()
            + " pass " + pass);
        changes = forwardDataflow(logger, program, f, ExecContext.CONTROL,
            f.mainBlock(), globalState.makeChild(false),
            new HierarchicalMap<Var, Arg>(), new HierarchicalMap<Var, Arg>());
        liftWait(logger, program, f);
        pass++;
      } while (changes);
    }
  }

  /**
   * If we have something of the below form, can just block on a as far of
   * function call protocol (..) f (a, b, c) { wait (a) {
   * 
   * } }
   * 
   * @param logger
   * @param program
   * @param f
   */
  private static void liftWait(Logger logger, Program program, Function f) {
    if (!f.isAsync()) {
      // Can only do this optimization if the function runs asynchronously
      return;
    }

    Block main = f.mainBlock();
    List<WaitVar> blockingVariables = findBlockingVariables(logger, f, main);

    if (blockingVariables != null) {
      List<Var> locals = f.getInputList();
      if (logger.isTraceEnabled()) {
        logger.trace("Blocking " + f.getName() + ": " + blockingVariables);
      }
      for (WaitVar wv : blockingVariables) {
        boolean isConst = program.lookupGlobalConst(wv.var.name()) != null;
        // Global constants are already set
        if (!isConst && locals.contains(wv.var)) {
          // Check if a non-arg
          f.addBlockingInput(wv);
        }
      }
    }
  }

  /**
   * Find the set of variables required to be closed (recursively or not) to
   * make progress in block.
   * 
   * @param block
   * @return
   */
  private static List<WaitVar> findBlockingVariables(Logger logger,
      Function fn, Block block) {
    /*
     * TODO: could exploit the information we have in getBlockingInputs() to
     * explore dependencies between variables and work out which variables are
     * needed to make progress
     */

    if (ProgressOpcodes.blockProgress(block, Category.NON_PROGRESS)) {
      // An instruction in block may make progress without any waits
      return null;
    }

    // Find blocking variables in instructions and continuations
    BlockingVarFinder walker = new BlockingVarFinder();
    TreeWalk.walkSyncChildren(logger, fn, block, true, walker);

    if (walker.blockingVariables == null) {
      return null;
    } else {
      ArrayList<WaitVar> res = new ArrayList<WaitVar>(walker.blockingVariables);
      WaitVar.removeDuplicates(res);
      return res;
    }
  }

  private static final class BlockingVarFinder extends TreeWalker {
    // Set of blocking variables. May contain duplicates for explicit/not
    // explicit -
    // we eliminate these at end
    HashSet<WaitVar> blockingVariables = null;

    @Override
    protected void visit(Continuation cont) {
      if (cont.isAsync()) {
        List<BlockingVar> waitOnVars = cont.blockingVars(false);
        List<WaitVar> waitOn;
        if (waitOnVars == null) {
          waitOn = WaitVar.NONE;
        } else {
          waitOn = new ArrayList<WaitVar>(waitOnVars.size());
          for (BlockingVar bv : waitOnVars) {
            waitOn.add(new WaitVar(bv.var, bv.explicit));
          }
        }

        updateBlockingSet(waitOn);
        // System.err.println("Blocking so far:" + blockingVariables);
      }
    }

    @Override
    public void visit(Instruction inst) {
      List<WaitVar> waitVars = WaitVar.asWaitVarList(inst.getBlockingInputs(),
          false);
      updateBlockingSet(waitVars);
    }

    private void updateBlockingSet(List<WaitVar> waitOn) {
      assert (waitOn != null);
      // System.err.println("waitOn: " + waitOn);
      if (blockingVariables == null) {
        blockingVariables = new HashSet<WaitVar>(waitOn);
      } else {
        // Keep only those variables which block all wait statements
        blockingVariables.retainAll(waitOn);
      }
    }
  }

  /**
   * 
   * @param execCx
   * @param block
   * @param cv
   *          copy of cv from outer scope, or null if it should be initialized
   * @param replaceInputs
   *          : a set of variable replaces to do from this point in IC onwards
   * @return true if this should be called again
   * @throws InvalidOptionException
   * @throws InvalidWriteException
   */
  private boolean forwardDataflow(Logger logger, Program program, Function f,
      ExecContext execCx, Block block, ValueTracker cv,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) throws InvalidOptionException,
      InvalidWriteException {
    if (block.getType() == BlockType.MAIN_BLOCK) {
      for (WaitVar wv : f.blockingInputs()) {
        cv.close(wv.var, false);
      }
      for (Var v : f.getInputList()) {
        if (Types.isScalarUpdateable(v.type())) {
          // Updateables always have a value
          cv.close(v, false);
        }
      }
    }
    for (Var v : block.getVariables()) {
      if (v.isMapped() && Types.isFile(v.type())) {
        ResultVal filenameVal = ICInstructions.filenameCV(
            Arg.createVar(v.mapping()), v);
        cv.addComputedValue(filenameVal, Ternary.FALSE);
      }
      if (Types.isMappable(v.type()) && !v.isMapped()
          && v.storage() != VarStorage.ALIAS) {
        // Var is definitely unmapped
        cv.setUnmapped(v);
      }
    }

    handleStatements(logger, program, f, execCx, block,
        block.statementIterator(), cv, replaceInputs, replaceAll);

    block.renameCleanupActions(replaceInputs, RenameMode.VALUE);
    block.renameCleanupActions(replaceAll, RenameMode.REFERENCE);

    boolean inlined = false;
    // might be able to eliminate wait statements or reduce the number
    // of vars they are blocking on
    for (int i = 0; i < block.getContinuations().size(); i++) {
      Continuation c = block.getContinuation(i);

      // Replace all variables in the continuation construct
      c.renameVars(replaceInputs, RenameMode.VALUE, false);
      c.renameVars(replaceAll, RenameMode.REFERENCE, false);

      Block toInline = c.tryInline(cv.getClosed(), cv.getRecursivelyClosed(),
          reorderingAllowed);
      if (logger.isTraceEnabled()) {
        logger.trace("Inlining continuation " + c.getType());
      }
      if (toInline != null) {

        prepareForInline(toInline, replaceInputs, replaceAll);
        c.inlineInto(block, toInline);
        i--; // compensate for removal of continuation
        inlined = true;
      }
    }

    if (inlined) {
      // Rebuild data structures for this block after inlining
      return true;
    }

    // Note: assume that continuations aren't added to rule engine until after
    // all code in block has run
    for (Continuation cont : block.getContinuations()) {
      recurseOnContinuation(logger, program, f, execCx, cont, cv,
          replaceInputs, replaceAll);
    }

    // Didn't inline everything, all changes should be propagated ok
    return false;
  }

  /**
   * 
   * @param logger
   * @param program
   * @param fn
   * @param execCx
   * @param cont
   * @param cv
   * @param replaceInputs
   * @param replaceAll
   * @return any variables that are guaranteed to be closed in current context
   *         after continuation is evaluated
   * @throws InvalidOptionException
   * @throws InvalidWriteException
   */
  private UnifiedState recurseOnContinuation(Logger logger, Program program,
      Function fn, ExecContext execCx, Continuation cont, ValueTracker cv,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) throws InvalidOptionException,
      InvalidWriteException {
    logger.trace("Recursing on continuation " + cont.getType());

    ValueTracker contCV = cv.makeChild(cont.inheritsParentVars());
    // additional variables may be close once we're inside continuation
    List<BlockingVar> contClosedVars = cont.blockingVars(true);
    if (contClosedVars != null) {
      for (BlockingVar bv : contClosedVars) {
        contCV.close(bv.var, bv.recursive);
      }
    }

    // For conditionals, find variables closed on all branches
    boolean unifyBranches = cont.isExhaustiveSyncConditional();
    List<ValueTracker> branchStates = unifyBranches ? new ArrayList<ValueTracker>() : null;

    List<Block> contBlocks = cont.getBlocks();
    for (int i = 0; i < contBlocks.size(); i++) {
      // Update based on whether values available within continuation
      HierarchicalMap<Var, Arg> contReplaceInputs;
      HierarchicalMap<Var, Arg> contReplaceAll;
      if (cont.inheritsParentVars()) {
        contReplaceInputs = replaceInputs;
        contReplaceAll = replaceAll;
      } else {
        contReplaceInputs = replaceInputs.makeChildMap();
        contReplaceAll = replaceAll.makeChildMap();
        purgeUnpassableVars(contReplaceInputs);
        purgeUnpassableVars(contReplaceAll);
      }

      ValueTracker blockCV;

      boolean again;
      int pass = 1;
      do {
        logger.debug("closed variable analysis on nested block pass " + pass);
        blockCV = contCV.makeChild(true);
        again = forwardDataflow(logger, program, fn, cont.childContext(execCx),
            contBlocks.get(i), blockCV, contReplaceInputs.makeChildMap(),
            contReplaceAll.makeChildMap());

        // changes within nested scope don't require another pass
        // over this scope
        pass++;
      } while (again);

      if (unifyBranches) {
        branchStates.add(blockCV);
      }
    }

    if (unifyBranches) {
      return UnifiedState.unify(reorderingAllowed, cv, cont.parent(),
                                branchStates, contBlocks);
    } else {
      return UnifiedState.EMPTY;
    }
  }
  
  /**
   * Need to apply pending updates to everything in block to be
   * inline aside from contents of nested continuations.
   * @param blockToInline
   * @param replaceInputs
   * @param replaceAll
   */
  private static void prepareForInline(Block blockToInline,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) {
    // Redo replacements for newly inserted instructions/continuations
    for (Statement stmt : blockToInline.getStatements()) {
      stmt.renameVars(replaceInputs, RenameMode.VALUE);
      stmt.renameVars(replaceAll, RenameMode.REFERENCE);
    }
    for (Continuation c : blockToInline.getContinuations()) {
      c.renameVars(replaceInputs, RenameMode.VALUE, false);
      c.renameVars(replaceAll, RenameMode.REFERENCE, false);
    }
  }

  /**
   * 
   * @param logger
   * @param f
   * @param execCx
   * @param block
   * @param stmts
   * @param cv
   * @param replaceInputs
   * @param replaceAll
   * @throws InvalidWriteException
   * @throws InvalidOptionException
   */
  private void handleStatements(Logger logger, Program program, Function f,
      ExecContext execCx, Block block, ListIterator<Statement> stmts, ValueTracker cv,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) throws InvalidWriteException,
      InvalidOptionException {
    logger.trace("STATEMENTS AT START:");
    StringBuilder sb = new StringBuilder();
    for (Statement stmt: block.getStatements()) {
      stmt.prettyPrint(sb, "    ");
    }
    logger.trace(sb);
    
    while (stmts.hasNext()) {
      Statement stmt = stmts.next();

      if (stmt.type() == StatementType.INSTRUCTION) {
        handleInstruction(logger, f, execCx, block, stmts, stmt.instruction(),
            cv, replaceInputs, replaceAll);
      } else {
        assert (stmt.type() == StatementType.CONDITIONAL);
        // handle situations like:
        // all branches assign future X a local values v1,v2,v3,etc.
        // in this case should try to create another local value outside of
        // conditional z which has the value from all branches stored
        UnifiedState condClosed = recurseOnContinuation(logger, program, f,
            execCx, stmt.conditional(), cv, replaceInputs, replaceAll);
        cv.addClosed(condClosed);
        cv.addComputedValues(condClosed.availableVals, Ternary.FALSE);
      }
    }
    logger.trace("STATEMENTS AT END: ");
    sb = new StringBuilder();
    for (Statement stmt: block.getStatements()) {
      stmt.prettyPrint(sb, "    ");
    }
    logger.trace(sb);
  }

  private void handleInstruction(Logger logger, Function f, ExecContext execCx,
      Block block, ListIterator<Statement> stmts, Instruction inst, ValueTracker cv,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) {
    if (logger.isTraceEnabled()) {
      logger.trace("Value renames in effect: " + replaceInputs);
      logger.trace("Reference renames in effect: " + replaceAll);
      logger.trace("Available values this block: " + cv.availableVals);
      logger.trace("Blacklist values this block: " + cv.blackList);
      ValueTracker ancestor = cv.parent;
      int up = 1;
      while (ancestor != null) {
        logger
            .trace("Available ancestor " + up + ": " + ancestor.availableVals);
        up++;
        ancestor = ancestor.parent;
      }
      logger.trace("Closed variables: " + cv.closed);
      logger.trace("-----------------------------");
      logger.trace("At instruction: " + inst);
    }

    // Immediately apply the variable renames
    inst.renameVars(replaceInputs, RenameMode.VALUE);
    inst.renameVars(replaceAll, RenameMode.REFERENCE);


    /*
     * See if value is already computed somewhere and see if we should replace
     * variables going forward NOTE: we don't delete any instructions on this
     * pass, but rather rely on dead code elim to later clean up unneeded
     * instructions instead
     */
    updateReplacements(logger, f, inst, cv, replaceInputs, replaceAll);

    if (logger.isTraceEnabled()) {
      logger.trace("Instruction after updates: " + inst);
    }
    // now try to see if we can change to the immediate version
    if (switchToImmediate(logger, f, execCx, block, cv, inst, stmts)) {
      // Continue pass at the start of the newly inserted sequence
      // as if it was always there
      return;
    }

    /*
     * Track transitive dependencies of variables. Future passes depend on
     * explicit dependencies and may not correctly handling transitive deps.
     * This is only safe once future reordering is disallowed.
     */
    if (!reorderingAllowed) {
      List<Var> in = inst.getBlockingInputs();
      if (in != null) {
        for (Var ov : inst.getOutputs()) {
          if (!Types.isScalarValue(ov.type())) {
            cv.setDependencies(ov, in);
          }
        }
      }
    }

    for (Var out : inst.getClosedOutputs()) {
      if (logger.isTraceEnabled()) {
        logger.trace("Output " + out.name() + " is closed");
      }
      cv.close(out, false);
    }
  }

  /**
   * 
   * @param logger
   * @param fn
   * @param block
   * @param cv
   * @param inst
   * @param insts
   *          if instructions inserted, leaves iterator pointing at previous
   *          instruction
   * @return
   */
  private static boolean switchToImmediate(Logger logger, Function fn,
      ExecContext execCx, Block block, ValueTracker cv, Instruction inst,
      ListIterator<Statement> stmts) {
    // First see if we can replace some futures with values
    MakeImmRequest req = inst.canMakeImmediate(cv.getClosed(),
        cv.getUnmapped(), false);

    if (req == null) {
      return false;
    }

    // Create replacement sequence
    Block insertContext;
    ListIterator<Statement> insertPoint;
    boolean noWaitRequired = req.mode == TaskMode.LOCAL
        || req.mode == TaskMode.SYNC
        || (req.mode == TaskMode.LOCAL_CONTROL && execCx == ExecContext.CONTROL);
    if (noWaitRequired) {
      insertContext = block;
      insertPoint = stmts;
    } else {
      WaitStatement wait = new WaitStatement(fn.getName() + "-"
          + inst.shortOpName(), WaitVar.NONE, PassedVar.NONE, Var.NONE,
          WaitMode.TASK_DISPATCH, false, req.mode, inst.getTaskProps());
      insertContext = wait.getBlock();
      block.addContinuation(wait);
      // Insert at start of block
      insertPoint = insertContext.statementIterator();
    }

    // Now load the values
    List<Instruction> alt = new ArrayList<Instruction>();
    List<Arg> inVals = new ArrayList<Arg>(req.in.size());

    // same var might appear multiple times
    HashMap<Var, Arg> alreadyFetched = new HashMap<Var, Arg>();
    for (Var v : req.in) {
      Arg maybeVal;
      boolean fetchedHere;
      if (alreadyFetched.containsKey(v)) {
        maybeVal = alreadyFetched.get(v);
        fetchedHere = true;
      } else {
        maybeVal = cv.findRetrieveResult(v);
        fetchedHere = false;
      }
      // Can only retrieve value of future or reference
      // If we inserted a wait, need to consider if local value can
      // be passed into new scope.
      if (maybeVal != null
          && (fetchedHere || noWaitRequired ||
              Semantics.canPassToChildTask(maybeVal.type()))) {
        /*
         * this variable might not actually be passed through continuations to
         * the current scope, so we might have temporarily made the IC invalid,
         * but we rely on fixupVariablePassing to fix this later
         */
        inVals.add(maybeVal);
        alreadyFetched.put(v, maybeVal);
      } else {
        // Generate instruction to fetch val, append to alt
        Var fetchedV = OptUtil.fetchForLocalOp(insertContext, alt, v);
        Arg fetched = Arg.createVar(fetchedV);
        inVals.add(fetched);
        alreadyFetched.put(v, fetched);
      }
    }
    
    // Need filenames for output file values
    Map<Var, Var> filenameVals = loadOutputFileNames(cv, req.out,
                                      insertContext, insertPoint);
    
    List<Var> outValVars = OptUtil.createLocalOpOutputVars(insertContext,
                                    insertPoint, req.out, filenameVals);
    MakeImmChange change = inst.makeImmediate(outValVars, inVals);
    OptUtil.fixupImmChange(block, insertContext, change, alt, outValVars,
                           req.out);

    if (logger.isTraceEnabled()) {
      logger.trace("Replacing instruction <" + inst + "> with sequence "
          + alt.toString());
    }

    // Remove existing instruction
    stmts.remove();

    // Add new instructions at insert point
    for (Instruction newInst : alt) {
      insertPoint.add(newInst);
    }

    // Rewind argument iterator to instruction before replaced one
    if (stmts == insertPoint) {
      ICUtil.rewindIterator(stmts, alt.size());
    }
    return true;
  }

  private static Map<Var, Var> loadOutputFileNames(ValueTracker cv,
      List<Var> outputs, Block insertContext,
      ListIterator<Statement> insertPoint) {
    if (outputs == null)
      outputs = Collections.emptyList();
    
    Map<Var, Var> filenameVals = new HashMap<Var, Var>();
    for (Var output: outputs) {
      if (Types.isFile(output)) {
        // Can only do this with unmapped vars, since otherwise we need
        // to wait for filename to be set
        assert(cv.isUnmapped(output));
        String varName = insertContext.uniqueVarName(
                          Var.FILENAME_OF_PREFIX + output.name());
        Var filenameVal = insertContext.declareVariable(
                        Types.V_STRING, varName, VarStorage.LOCAL,
                        DefType.LOCAL_COMPILER, null);
        for (Statement stmt: TurbineOp.initWithTmpFilename(
                                    filenameVal, null, output)) {
          insertPoint.add(stmt);
        }
        filenameVals.put(output, filenameVal);
      }
    }
    return filenameVals;
  }

  /**
   * Remove unpassable vars from map
   * 
   * @param replaceInputs
   */
  private static void purgeUnpassableVars(HierarchicalMap<Var, Arg> replacements) {
    ArrayList<Var> toPurge = new ArrayList<Var>();
    for (Entry<Var, Arg> e : replacements.entrySet()) {
      Arg val = e.getValue();
      if (val.isVar() && !Semantics.canPassToChildTask(val.getVar().type())) {
        toPurge.add(e.getKey());
      }
    }
    for (Var key : toPurge) {
      replacements.remove(key);
    }
  }

}
