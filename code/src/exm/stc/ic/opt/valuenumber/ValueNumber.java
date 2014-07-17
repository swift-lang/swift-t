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
package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.aliases.Alias;
import exm.stc.ic.opt.ICOptimizer;
import exm.stc.ic.opt.InitVariables;
import exm.stc.ic.opt.InitVariables.InitState;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.opt.OptUtil.OptVarCreator;
import exm.stc.ic.opt.OptimizerPass;
import exm.stc.ic.opt.ProgressOpcodes;
import exm.stc.ic.opt.ProgressOpcodes.Category;
import exm.stc.ic.opt.Semantics;
import exm.stc.ic.opt.TreeWalk;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.opt.valuenumber.Congruences.OptUnsafeError;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ForeachLoops.ForeachLoop;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.Fetched;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmVar;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalConstants;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.Opcode;
import exm.stc.ic.tree.TurbineOp;

/**
 * This optimisation pass does a range of optimizations. The overarching idea is
 * to find congruences class in which different variables or constants are
 * somehow equal (either aliases for the same data, or having the same value).
 * We use this information to do constant folding/propagation, and remove
 * redundant computations and variables by consolidating variables in the same
 * congruence class.
 * 
 * The information about aliases is also very useful for finding out which
 * variables are closed at each point in the program, so we do a closed
 * variable analysis as part of this pass using the alias info.  This analysis
 * allows us to do strength reduction of operations, and inline wait statements.
 * 
 * Using the propagated constants also allows us to predict branches.  
 * 
 * This optimization pass doesn't remove any instructions: it simply modifies
 * the arguments to each instruction in a way that will hopefully lead a bunch
 * of dead code, which can be cleaned up in a pass of the dead code eliminator.
 * 
 */
public class ValueNumber implements OptimizerPass {

  private Logger logger;
  
  /**
   * True if this pass is allowed to reorder instructions. If false, guarantees
   * that future passes won't try reordering.
   */
  private boolean reorderingAllowed;
  
  /**
   * True if we should try to infer which variables are closed/finalized
   */
  private boolean finalizedVarEnabled;

  public ValueNumber(boolean reorderingAllowed) {
    this.reorderingAllowed = reorderingAllowed;

    try {
      finalizedVarEnabled = Settings.getBoolean(Settings.OPT_FINALIZED_VAR);
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }

  @Override
  public String getPassName() {
    return "Value numbering";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_VALUE_NUMBER;
  }

  @Override
  public void optimize(Logger logger, Program prog) throws UserException {
    this.logger = logger;
    for (Function f: prog.getFunctions()) {
      runPass(prog, f);
      liftWaitRec(logger, prog, f, f.mainBlock());
    }
  }

  private void runPass(Program prog, Function f) {
    logger.trace("Optimizing function @" + f.getName());
    try {
      // First pass finds all congruence classes and expands some instructions
      Map<Block, Congruences> congMap;
      congMap = findCongruences(prog, f, ExecContext.control());
  
      // Second pass replaces values based on congruence classes
      replaceVals(prog.constants(), f.getName(), f.mainBlock(), congMap,
                  InitState.enterFunction(f));
      
      // Third pass inlines continuations
      inlinePass(prog.constants(), f.mainBlock(), congMap);
    } catch (OptUnsafeError e) {
      logger.debug("Optimization cancelled for function " + f.getName());
    }
  }

  private Congruences initFuncState(Logger logger,
        GlobalConstants constants, Function f) throws OptUnsafeError {
    Congruences congruent = new Congruences(logger, constants,
                                            reorderingAllowed);
    for (Var v : constants.vars()) {
      // First, all constants can be treated as being set
      if (v.storage() == Alloc.GLOBAL_CONST) {
        Arg val = constants.lookupByVar(v);
        assert (val != null) : v.name();
        
        ValLoc assign = ComputedValue.assignValLoc(v, val,
                                        IsAssign.TO_LOCATION, false);
        int stmtIndex = -1;
        congruent.update(constants, f.getName(), assign, stmtIndex);
      }
    }
    
    if (finalizedVarEnabled) {
      for (WaitVar wv : f.blockingInputs()) {
        congruent.markClosedBlockStart(wv.var, false);
      }
    }
    return congruent;
  }

  private void liftWaitRec(Logger logger, Program prog, Function f,
                           Block block) {
    // First apply to this block
    liftWait(logger, prog, f, block);
    
    for (Continuation c: block.allComplexStatements()) {
      for (Block inner: c.getBlocks()) {
        liftWaitRec(logger, prog, f, inner);
      }
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
  private static void liftWait(Logger logger, Program program, Function f,
                               Block block) {
    // Check if we can attempt
    switch (block.getType()) {
      case MAIN_BLOCK: {
        // Can do this optimization if the function runs asynchronously: proceed
        assert(block == f.mainBlock());
        if (!f.isAsync()) {
          return;
        }
        break;
      }
      case LOOP_BODY:
        // Can do this optimization
        assert(block.getParentCont().getType() == ContinuationType.LOOP);
        break;
      default:
        // Doesn't apply
        return;
    }

    logger.trace("liftWait() on " + f.getName() + " " + block.getType());
    
    List<WaitVar> blockingVariables;
    blockingVariables = findBlockingVariables(logger, program, f, block);

    if (blockingVariables != null) {
      // Apply changes
      logger.trace("blockingVariables: " + blockingVariables);
      
      switch (block.getType()) {
        case MAIN_BLOCK: {
          List<Var> locals = f.getInputList();
          if (logger.isTraceEnabled()) {
            logger.trace("Blocking " + f.getName() + ": " + blockingVariables);
          }
          for (WaitVar wv : blockingVariables) {
            boolean isConst = (wv.var.defType() == DefType.GLOBAL_CONST);
            // Global constants are already set
            if (!isConst && locals.contains(wv.var)) {
              // Check if a non-arg
              f.addBlockingInput(wv);
            }
          }
          break;
        }
        case LOOP_BODY: {
          Loop loop = (Loop)block.getParentCont();
          List<Var> loopVars = loop.getLoopVars();
          for (WaitVar wv: blockingVariables) {
            boolean isConst = (wv.var.defType() == DefType.GLOBAL_CONST);
            // Global constants are already set
            if (!isConst && loopVars.contains(wv.var)) {
              loop.setBlockingInput(wv.var);
            }
          }
          break;
        }
        default:
          throw new STCRuntimeError("Unexpected: " + block.getType());
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
      Program prog, Function fn, Block block) {
    /*
     * TODO: could exploit the information we have in getBlockingInputs() to
     * explore dependencies between variables and work out which variables are
     * needed to make progress
     * 
     * TODO: this is necessary for optimizing some for loops, e.g. test 900
     */

    if (!ProgressOpcodes.blockProgress(block, Category.NON_PROGRESS)) {
      // An instruction in block may make progress without any waits
      return null;
    }

    // Find blocking variables in instructions and continuations
    BlockingVarFinder walker = new BlockingVarFinder(prog);
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

    private BlockingVarFinder(Program prog) {
      this.prog = prog;
    }

    private final Program prog;
    
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
      }
    }

    @Override
    public void visit(Instruction inst) {
      List<WaitVar> waitVars = WaitVar.asWaitVarList(
          inst.getBlockingInputs(prog), false);
      updateBlockingSet(waitVars);
    }

    private void updateBlockingSet(List<WaitVar> waitOn) {
      assert (waitOn != null);
      if (blockingVariables == null) {
        blockingVariables = new HashSet<WaitVar>(waitOn);
      } else {
        // Keep only those variables which block all wait statements
        blockingVariables.retainAll(waitOn);
      }
    }
  }

  /**
   * Build congruence map.  Also expand operations where input
   * values are closed.
   * @param program
   * @param f
   * @param execCx
   * @return a map from block to the congruence info for the block.
   * @throw OptUnsafeError if optimisation isn't safe
   *      for this function.
   */
  private Map<Block, Congruences> findCongruences(Program program,
          Function f, ExecContext execCx) throws OptUnsafeError {
    Map<Block, Congruences> result = new HashMap<Block, Congruences>();
    findCongruencesRec(program, f, f.mainBlock(), execCx,
          initFuncState(logger, program.constants(), f), result);
    return result;
  }
  
  private void findCongruencesRec(Program program, Function f, Block block,
      ExecContext execCx, Congruences state, Map<Block, Congruences> result)
          throws OptUnsafeError {
    result.put(block, state);
    
    ListIterator<Statement> stmts = block.statementIterator();
    while (stmts.hasNext()) {
      int stmtIndex = stmts.nextIndex();
      Statement stmt = stmts.next();
      
      if (stmt.type() == StatementType.INSTRUCTION) {
        /* First try to see if we can expand instruction sequence */

        Instruction inst = stmt.instruction();
        if (logger.isTraceEnabled() && inst.op != Opcode.COMMENT) {
          state.printTraceInfo(logger, program.constants());
          logger.trace("-----------------------------");
          logger.trace("At instruction: " + inst);
        }
        
        if (switchToImmediate(logger, f, execCx, block, state, inst, stmts,
                              stmtIndex)) {
          /*
           * We switched the instruction for a new sequence of instructions. 
           * Restart iteration and *don't* increment statement index to account.
           */
          continue;
        }
        findCongruencesInst(program, f, execCx, block, stmts, inst, stmtIndex,
                            state);
      } else {
        assert (stmt.type() == StatementType.CONDITIONAL);
        // handle situations like:
        // all branches assign future X a local values v1,v2,v3,etc.
        // in this case should try to create another local value outside of
        // conditional z which has the value from all branches stored
        UnifiedValues unified = findCongruencesContRec(program, f, execCx,
                            stmt.conditional(), stmtIndex, state, result);
        state.addUnifiedValues(program.constants(), f.getName(), stmtIndex, unified);
      }
    }
    
    int stmtCount = block.getStatements().size();
    for (Continuation cont: block.getContinuations()) {
      findCongruencesContRec(program, f, execCx, cont, stmtCount,
                             state, result);
    }
    
    validateState(program.constants(), state);
  }

  private void findCongruencesInst(Program prog, Function f,
      ExecContext execCx, Block block, ListIterator<Statement> stmts,
      Instruction inst, int stmtIndex, Congruences state) throws OptUnsafeError {
    
    /*
     * See if value is already computed somewhere and see if we should replace
     * variables going forward
     * NOTE: we don't delete any instructions on this pass, but rather rely on
     * dead code elim to later clean up unneeded instructions instead.
     */
    updateCongruent(logger, prog.constants(), f, inst, stmtIndex, state);

    
    if (finalizedVarEnabled) {
      updateTransitiveDeps(prog, inst, state);
  
      for (Var out: inst.getClosedOutputs()) {
        if (logger.isTraceEnabled()) {
          logger.trace("Output " + out.name() + " is closed");
        }
        state.markClosed(out, stmtIndex, false);
      }
    }
  }

  private UnifiedValues findCongruencesContRec(Program prog,
      Function fn, ExecContext execCx, Continuation cont,
      int stmtIndex, Congruences state, Map<Block, Congruences> result)
          throws OptUnsafeError {
    logger.trace("Recursing on continuation " + cont.getType());
    
    if (finalizedVarEnabled) {
      // TODO: prototype of this transformation
      // more elegant approach should be possible
      if (cont.getType() == ContinuationType.FOREACH_LOOP) {
        ForeachLoop foreach = (ForeachLoop)cont;
        Arg arrayVal = state.findRetrieveResult(foreach.getArrayVar(), false);
        logger.trace("CHECKING FOREACH: " + arrayVal);
        if (arrayVal != null) {
          foreach.switchToLocalForeach(arrayVal.getVar());
        }
      }
    }

    // additional variables may be close once we're inside continuation
    List<BlockingVar> contClosedVars = null;
    if (finalizedVarEnabled) {
      contClosedVars = cont.closedVars(state.getClosed(stmtIndex),
                                       state.getRecursivelyClosed(stmtIndex));
    }

    // For conditionals, find variables closed on all branches
    boolean unifyBranches = cont.isExhaustiveSyncConditional();
    List<Congruences> branchStates = unifyBranches ?
                      new ArrayList<Congruences>() : null;

    for (Block contBlock: cont.getBlocks()) {
      Congruences blockState = state.enterContBlock(
                    cont.inheritsParentVars(), stmtIndex);
      if (contClosedVars != null) {
        for (BlockingVar bv: contClosedVars) {
          blockState.markClosedBlockStart(bv.var, bv.recursive);
        }
      }
      findCongruencesRec(prog, fn, contBlock, cont.childContext(execCx),
                         blockState, result);

      if (unifyBranches) {
        branchStates.add(blockState);
      }
    }

    if (unifyBranches) {
      return UnifiedValues.unify(logger, prog.constants(), fn,
                    reorderingAllowed, stmtIndex, state, cont,
                              branchStates, cont.getBlocks());
    } else {
      return UnifiedValues.EMPTY;
    }
  }
  
  private void replaceVals(GlobalConstants consts, String function,
      Block block, Map<Block, Congruences> congruences, InitState init) {
    Congruences state = congruences.get(block);

    if (logger.isTraceEnabled()) {
      logger.trace("=======================================");
      logger.trace("Replacing on block " + System.identityHashCode(block)
                   + ": " + block.getType());
      state.printTraceInfo(logger, consts);
    }
    
    // TODO: ideally, use closed info when replacing to ensure correctness
    ListIterator<Statement> stmtIt = block.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      if (stmt.type() == StatementType.INSTRUCTION) {
        // Replace vars in instruction
        Instruction inst = stmt.instruction();
        replaceCongruent(function, inst, state, init);
        
        if (!inst.hasSideEffects() && inst.getOutputs().size() == 1) {
          Var output = inst.getOutput(0);
          if (!InitVariables.varMustBeInitialized(output, true) ||
              init.isInitialized(output, true)) {
            if (Types.isScalarFuture(output)) {
              // Replace a computation with future output with a store
              Arg val = state.findRetrieveResult(output, false);
              if (val != null && init.isInitialized(val, false)) {
                Instruction futureSet = TurbineOp.storePrim(output, val);
                stmtIt.set(futureSet);
                logger.trace("Replaced with " + futureSet);
              }
            } else if (Types.isScalarValue(output)) {
              Arg val = state.findValue(output);
              if (val != null && val.isConstant()) {
                Instruction valueSet = ICInstructions.valueSet(output, val);
                stmtIt.set(valueSet);
                logger.trace("Replaced with " + valueSet);
              }
            }
          }
        }
        
        // Update init state
        InitVariables.updateInitVars(logger, stmt, init, false);
      } else {
        assert(stmt.type() == StatementType.CONDITIONAL);
        /* Replace vars recursively in conditional.  Init state
         * is updated in this function */ 
        replaceCongruentNonRec(function, stmt.conditional(), state, init);
        replaceValsRec(consts, function, stmt.conditional(), congruences, init);
      }
    }
    
    // Replace in cleanups
    replaceCleanupCongruent(function, block, state, init);
    
    for (Continuation cont: block.getContinuations()) {
      replaceCongruentNonRec(function, cont, state, init);
      replaceValsRec(consts, function, cont, congruences, init);
    }
  }

  private void replaceValsRec(GlobalConstants consts, String function, 
          Continuation cont, Map<Block, Congruences> congruences,
          InitState init) {
    InitState contInit = init.enterContinuation(cont);
    List<InitState> branchInits = new ArrayList<InitState>();
    for (Block contBlock: cont.getBlocks()) {
      InitState branchInit = contInit.enterBlock(contBlock);
      replaceVals(consts, function, contBlock, congruences, branchInit);
      branchInits.add(branchInit);
    }
    
    if (InitState.canUnifyBranches(cont)) {
      init.unifyBranches(cont, branchInits);
    }
  }

  /**
   * Inline from bottom-up
   * @param mainBlock
   * @param cong
   */
  private void inlinePass(GlobalConstants consts, Block block,
                          Map<Block, Congruences> cong) {
    Congruences blockState = cong.get(block);
    assert(blockState != null);
    if (logger.isTraceEnabled()) {
      logger.trace("=======================================");
      logger.trace("Inlining statements on block " + 
                    System.identityHashCode(block) + ": " + block.getType());
      blockState.printTraceInfo(logger, consts);
    }
    
    // Use original statement count from when block was constructed
    int origStmtCount = block.getStatements().size();
    
    ListIterator<Statement> stmtIt = block.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      if (stmt.type() == StatementType.CONDITIONAL) {
        // First recurse
        tryInlineConditional(consts, block, stmtIt, stmt.conditional(), cong);
      } else {
        assert(stmt.type() == StatementType.INSTRUCTION);
        // Leave instructions alone
      }
    }

    // Block state will reflect closed vars as of end of block
    Set<Var> closedVars; 
    Set<Var> recClosedVars;
    
    if (finalizedVarEnabled) {
      recClosedVars = blockState.getRecursivelyClosed(origStmtCount);
      closedVars = blockState.getClosed(origStmtCount);
    } else {
      recClosedVars = Collections.emptySet();
      closedVars = Collections.emptySet();
    }
    
    if (logger.isTraceEnabled()) {
      logger.trace("=======================================");
      logger.trace("Inlining continuations on block " + 
                    System.identityHashCode(block) + ": " + block.getType());
      blockState.printTraceInfo(logger, consts);
    }
    ListIterator<Continuation> contIt = block.continuationIterator();
    while (contIt.hasNext()) {
      Continuation cont = contIt.next();
      // First recurse
      inlinePassRecurse(consts, cont, cong);
      
      if (logger.isTraceEnabled()) {
        logger.trace("Return to block " +  System.identityHashCode(block) + 
                    " checking " + cont.getType());
      }
      // Then try to inline
      if (cont.isNoop()) {
        logger.trace("Removed noop continuation " + cont.getType());
        contIt.remove();
      } else if (tryInlineContinuation(block, cont, contIt,
                                       closedVars, recClosedVars)) {
        // Success!  Will now iterate over rest
        logger.trace("Inlined continuation " + cont.getType());
      }
    }
  }

  private boolean tryInlineContinuation(Block block,
              Continuation cont,
              ListIterator<Continuation> contIt,
              Set<Var> closedVars, Set<Var> recClosedVars) {
    Block toInline = null;
    toInline = cont.tryInline(closedVars, recClosedVars, reorderingAllowed);
    if (toInline != null) {
      // Remove old and then add new
      contIt.remove();
      block.insertInline(toInline, contIt, block.statementEndIterator());
      logger.trace("Inlined continuation " + cont.getType());
    }
    return false;
  }

  private boolean tryInlineConditional(GlobalConstants consts, Block block,
      ListIterator<Statement> stmtIt, Conditional conditional,
      Map<Block, Congruences> cong) {
    inlinePassRecurse(consts, conditional, cong);
    
    // Then see if we can inline
    Block predicted = conditional.branchPredict();
    if (conditional.isNoop()) {
      stmtIt.remove();
      return true;
    }
    if (predicted != null) {
      /* Insert block inline.  This will put iterator past end of
       * inserted code (which is what we want!) */
      if (logger.isTraceEnabled()) {
        logger.trace("Inlining conditional at statement index " +
                     stmtIt.previousIndex());
      }
      stmtIt.remove();
      block.insertInline(predicted, stmtIt);
      return true;
    }
    return false;
  }

  private void inlinePassRecurse(GlobalConstants consts, Continuation cont,
                                 Map<Block, Congruences> cong) {
    for (Block inner: cont.getBlocks()) {
      inlinePass(consts, inner, cong);
    }
  }
  
  private void updateTransitiveDeps(Program prog, Instruction inst,
      Congruences state) {
    
    /*
     * Track transitive dependencies of variables. Future passes depend on
     * explicit dependencies and may not correctly handling transitive deps.
     * This is only safe once future reordering is disallowed.
     */
    if (!reorderingAllowed) {
      List<Var> in = inst.getBlockingInputs(prog);
      if (in != null) {
        for (Var ov : inst.getOutputs()) {
          if (!Types.isPrimValue(ov.type())) {
            state.setDependencies(ov, in);
          }
        }
      }
    }
  }

  private static final List<RenameMode> RENAME_MODES =
      Arrays.asList(RenameMode.VALUE, RenameMode.REFERENCE);
  
  private static void replaceCongruentNonRec(String function, Continuation cont,
                              Congruences congruent, InitState init) {
    for (RenameMode mode: RENAME_MODES) {
      cont.renameVars(function, congruent.replacements(mode, init), mode, false); 
    }
  }

  private void replaceCongruent(String function, Instruction inst,
                                Congruences congruent, InitState init) {
    if (logger.isTraceEnabled()) {
      logger.trace("Instruction before replacement: " + inst);
    }
    for (RenameMode mode: RENAME_MODES) {
      inst.renameVars(function, congruent.replacements(mode, init), mode);
    }
    if (logger.isTraceEnabled()) {
      logger.trace("Instruction after replacement: " + inst);
    }
  }
  
  private static void replaceCleanupCongruent(String function, Block block,
                            Congruences congruent, InitState init) {

    for (RenameMode mode: RENAME_MODES) {
      block.renameCleanupActions(function, congruent.replacements(mode, init),
                                 mode);
    }
  }

  private static void updateCongruent(Logger logger, GlobalConstants consts,
            Function function, Instruction inst, int stmtIndex,
            Congruences state) throws OptUnsafeError {
    List<ValLoc> resVals = inst.getResults();
    List<Alias> aliases = inst.getAliases();
    
    if (logger.isTraceEnabled()) {
      logger.trace("resVals: " + resVals);
      logger.trace("aliases: " + aliases);
    }
    state.update(consts, function.getName(), resVals, aliases, stmtIndex);
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
  private boolean switchToImmediate(Logger logger, Function fn,
      ExecContext execCx, Block block, Congruences state,
      Instruction inst, ListIterator<Statement> stmts, int stmtIndex) {
    if (!finalizedVarEnabled) {
      return false;
    }
    
    // First see if we can replace some futures with values
    MakeImmRequest req = inst.canMakeImmediate(state.getClosed(stmtIndex),
                               state.getClosedLocs(stmtIndex),
                               state.retrieveResultAvail(), false);

    if (req == null) {
      return false;
    }
    
    // Create replacement sequence
    Block insertContext;
    ListIterator<Statement> insertPoint;
    boolean waitRequired = req.mode.isDispatched() ||
             !req.mode.targetContextMatches(execCx);
    
    // First remove old instruction
    stmts.remove();
    
    if (!waitRequired) {
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
    List<Statement> alt = new ArrayList<Statement>();
    List<Fetched<Arg>> inVals = fetchInputsForSwitch(state, req,
                                    insertContext, !waitRequired, alt);
    if (logger.isTraceEnabled()) {
      logger.trace("Fetched " + inVals + " for " + inst
                 + " req.in: " + req.in);
    }

    // Need filenames for output file values
    Map<Var, Var> filenameVals = loadOutputFileNames(state, stmtIndex,
                req.out, insertContext, insertPoint);
    
    
    List<Var> outFetched = OptUtil.createLocalOpOutputVars(insertContext,
                insertPoint, req.out, filenameVals);

    MakeImmChange change;
    change = inst.makeImmediate(new OptVarCreator(block),
          Fetched.makeList(req.out, outFetched, true), inVals);
    OptUtil.fixupImmChange(fn.getName(), block, insertContext, inst, change, alt,
                           outFetched, req.out);

    if (logger.isTraceEnabled()) {
      logger.trace("Replacing instruction <" + inst + "> with sequence "
          + alt.toString());
    }

    // Add new instructions at insert point
    for (Statement newStmt : alt) {
      insertPoint.add(newStmt);
    }

    // Rewind argument iterator to instruction before replaced one
    if (stmts == insertPoint) {
      ICUtil.rewindIterator(stmts, alt.size());
    }
    return true;
  }

  private static List<Fetched<Arg>> fetchInputsForSwitch(
      Congruences state,
      MakeImmRequest req, Block insertContext, boolean noWaitRequired,
      List<Statement> alt) {
    List<Fetched<Arg>> inVals = new ArrayList<Fetched<Arg>>(req.in.size());

    // same var might appear multiple times
    HashMap<Var, Arg> alreadyFetched = new HashMap<Var, Arg>();
    for (MakeImmVar input: req.in) {
      if (!input.fetch) {
        continue;
      }
      Var toFetch = input.var;
      
      Arg maybeVal;
      boolean fetchedHere;
      if (alreadyFetched.containsKey(toFetch)) {
        maybeVal = alreadyFetched.get(toFetch);
        fetchedHere = true;
      } else {
        maybeVal = state.findRetrieveResult(toFetch, false);
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
        inVals.add(new Fetched<Arg>(toFetch, maybeVal));
        alreadyFetched.put(toFetch, maybeVal);
      } else {
        // Generate instruction to fetch val, append to alt
        Var fetchedV = OptUtil.fetchForLocalOp(insertContext, alt, toFetch,
                                  input.recursive, input.acquireWriteRefs);
        Arg fetched = Arg.createVar(fetchedV);
        inVals.add(new Fetched<Arg>(toFetch, fetched));
        alreadyFetched.put(toFetch, fetched);
      }
    }
    return inVals;
  }

  private static Map<Var, Var> loadOutputFileNames(Congruences state,
      int oldStmtIndex, List<MakeImmVar> outputs,
      Block insertContext, ListIterator<Statement> insertPoint) {
    Map<Var, Var> filenameVals = new HashMap<Var, Var>();
    for (MakeImmVar output: outputs) {
      Var outVar = output.var;
      if (Types.isFile(outVar) && output.preinitOutputMapping) {
        Var filenameVal = insertContext.declareUnmapped(Types.V_STRING,
            OptUtil.optFilenamePrefix(insertContext, outVar),
            Alloc.LOCAL, DefType.LOCAL_COMPILER,
            VarProvenance.filenameOf(outVar));

        if (outVar.isMapped() == Ternary.FALSE) {
          // Initialize unmapped var
          assert (outVar.type().fileKind().supportsTmpImmediate());
          WrapUtil.initTemporaryFileName(insertPoint, outVar, filenameVal);
        } else {
          // Load existing mapping
          // Should only get here if value of mapped var is available.
          insertPoint.add(TurbineOp.getFilenameVal(filenameVal, outVar));
        }
        filenameVals.put(outVar, filenameVal);
      }
    }
    return filenameVals;
  }

  /**
   * Do any validations of the state of things
   */
  private void validateState(GlobalConstants consts, Congruences state) {
    if (ICOptimizer.SUPER_DEBUG) {
      state.validate(consts);
    }
  }
}
