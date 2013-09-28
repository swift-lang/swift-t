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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.opt.InitVariables.InitState;
import exm.stc.ic.opt.ProgressOpcodes.Category;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.opt.valuenumber.ComputedValue;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.opt.valuenumber.Congruences;
import exm.stc.ic.opt.valuenumber.UnifiedValues;
import exm.stc.ic.opt.valuenumber.ValLoc;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.Fetched;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalConstants;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
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
public class ForwardDataflow implements OptimizerPass {

  private Logger logger;
  
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

  @Override
  public void optimize(Logger logger, Program prog) throws UserException {
    this.logger = logger;
    for (Function f: prog.getFunctions()) {
      runPass(prog, f);
      liftWait(logger, prog, f);
    }
  }

  private void runPass(Program prog, Function f) {
    logger.trace("Optimizing function @" + f.getName());
    // First pass finds all congruence classes and expands some instructions
    Map<Block, Congruences> cong;
    cong = findCongruences(prog, f, ExecContext.CONTROL);
    
    // Second pass replaces values based on congruence classes
    replaceVals(f.mainBlock(), cong, InitState.enterFunction(f));
    
    // Third pass inlines continuations
    inlinePass(f.mainBlock(), cong);
    
  }

  private Congruences initFuncState(Logger logger,
        GlobalConstants constants, Function f) {
    Congruences congruent = new Congruences(logger, reorderingAllowed);
    for (Var v : constants.vars()) {
      // First, all constants can be treated as being set
      if (v.storage() == Alloc.GLOBAL_CONST) {
        Arg val = constants.lookupByVar(v);
        assert (val != null) : v.name();
        
        ValLoc assign = ComputedValue.assignComputedVal(v, val,
                                                IsAssign.TO_LOCATION);
        congruent.update(constants, f.getName(), assign, 0);
      }
    }
    
    for (WaitVar wv : f.blockingInputs()) {
      congruent.markClosed(wv.var, 0, false);
    }
    return congruent;
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
    List<WaitVar> blockingVariables;
    blockingVariables = findBlockingVariables(logger, program, f, main);

    if (blockingVariables != null) {
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
     */

    if (ProgressOpcodes.blockProgress(block, Category.NON_PROGRESS)) {
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
   */
  private Map<Block, Congruences> findCongruences(Program program,
                    Function f, ExecContext execCx) {
    Map<Block, Congruences> result = new HashMap<Block, Congruences>();
    findCongruencesRec(program, f, f.mainBlock(), execCx,
          initFuncState(logger, program.constants(), f), result);
    return result;
  }
  
  private void findCongruencesRec(Program program, Function f, Block block,
      ExecContext execCx, Congruences state, Map<Block, Congruences> result) {
    result.put(block, state);
    for (Var v : block.getVariables()) {
      if (v.mapping() != null && Types.isFile(v.type())) {
        // Track the mapping
        ValLoc filenameVal = ValLoc.makeFilename(v.mapping().asArg(), v);
        state.update(program.constants(), f.getName(), filenameVal, 0);
      }
    }
    
    ListIterator<Statement> stmts = block.statementIterator();
    while (stmts.hasNext()) {
      int stmtIndex = stmts.nextIndex();
      Statement stmt = stmts.next();
      
      if (stmt.type() == StatementType.INSTRUCTION) {
        /* First try to see if we can expand instruction sequence */

        Instruction inst = stmt.instruction();
        if (logger.isTraceEnabled()) {
          state.printTraceInfo(logger);
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
    
    for (Continuation cont: block.getContinuations()) {
      findCongruencesContRec(program, f, execCx, cont, stmts.previousIndex(),
                             state, result);
    }
    
    validateState(state);
  }

  private void findCongruencesInst(Program prog, Function f,
      ExecContext execCx, Block block, ListIterator<Statement> stmts,
      Instruction inst, int stmtIndex, Congruences state) {
    
    /*
     * See if value is already computed somewhere and see if we should replace
     * variables going forward
     * NOTE: we don't delete any instructions on this pass, but rather rely on
     * dead code elim to later clean up unneeded instructions instead.
     */
    updateCongruent(logger, prog.constants(), f, inst, stmtIndex, state);

    updateTransitiveDeps(prog, inst, state);

    for (Var out: inst.getClosedOutputs()) {
      if (logger.isTraceEnabled()) {
        logger.trace("Output " + out.name() + " is closed");
      }
      state.markClosed(out, stmtIndex, false);
    }
  }

  private UnifiedValues findCongruencesContRec(Program prog,
      Function fn, ExecContext execCx, Continuation cont,
      int stmtIndex, Congruences state, Map<Block, Congruences> result) {
    logger.trace("Recursing on continuation " + cont.getType());

    Congruences contState = state.enterContinuation(cont.inheritsParentVars(),
                                                    stmtIndex);
    // additional variables may be close once we're inside continuation
    List<BlockingVar> contClosedVars = cont.blockingVars(true);
    if (contClosedVars != null) {
      for (BlockingVar bv : contClosedVars) {
        int contStmtIndex = 0; // No statements in cont scope
        contState.markClosed(bv.var, contStmtIndex, bv.recursive);
      }
    }

    // For conditionals, find variables closed on all branches
    boolean unifyBranches = cont.isExhaustiveSyncConditional();
    List<Congruences> branchStates = unifyBranches ?
                      new ArrayList<Congruences>() : null;

    for (Block contBlock: cont.getBlocks()) {
      Congruences blockState = contState.enterBlock();
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
  
  private void replaceVals(Block block, Map<Block, Congruences> congruences,
                        InitState init) {
    Congruences state = congruences.get(block);

    if (logger.isTraceEnabled()) {
      logger.trace("=======================================");
      logger.trace("Replacing on block " + System.identityHashCode(block)
                   + ": " + block.getType());
      state.printTraceInfo(logger);
    }
    
    // TODO: ideally, use closed info when replacing to ensure correctness
    ListIterator<Statement> stmtIt = block.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      if (stmt.type() == StatementType.INSTRUCTION) {
        // Replace vars in instruction
        Instruction inst = stmt.instruction();
        replaceCongruent(inst, state, init);
        
        replaceInstruction(state, init, stmtIt, inst);
        
        // Update init state
        InitVariables.updateInitVars(logger, stmt, init, false);
      } else {
        assert(stmt.type() == StatementType.CONDITIONAL);
        /* Replace vars recursively in conditional.  Init state
         * is updated in this function */ 
        replaceCongruentNonRec(stmt.conditional(), state, init);
        replaceValsRec(stmt.conditional(), congruences, init);
      }
    }
    
    // Replace in cleanups
    replaceCleanupCongruent(block, state, init);
    
    for (Continuation cont: block.getContinuations()) {
      replaceCongruentNonRec(cont, state, init);
      replaceValsRec(cont, congruences, init);
    }
  }

  private void replaceValsRec(Continuation cont,
          Map<Block, Congruences> congruences, InitState init) {
    InitState contInit = init.enterContinuation(cont);
    List<InitState> branchInits = new ArrayList<InitState>();
    for (Block contBlock: cont.getBlocks()) {
      InitState branchInit = contInit.enterBlock(contBlock);
      replaceVals(contBlock, congruences, branchInit);
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
  private void inlinePass(Block block, Map<Block, Congruences> cong) {
    Congruences blockState = cong.get(block);
    assert(blockState != null);
    if (logger.isTraceEnabled()) {
      logger.trace("=======================================");
      logger.trace("Inlining on block " + System.identityHashCode(block)
                   + ": " + block.getType());
      blockState.printTraceInfo(logger);
    }
    
    // Use original statement count from when block was constructed
    int origStmtCount = block.getStatements().size();
    
    ListIterator<Statement> stmtIt = block.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      if (stmt.type() == StatementType.CONDITIONAL) {
        // First recurse
        tryInlineConditional(block, stmtIt, stmt.conditional(), cong);
      } else {
        assert(stmt.type() == StatementType.INSTRUCTION);
        // Leave instructions alone
      }
    }

    // Block state will reflect closed vars as of end of block
    Set<Var> closedVars = blockState.getClosed(origStmtCount);
    Set<Var> recClosedVars = blockState.getRecursivelyClosed(origStmtCount);
    
    ListIterator<Continuation> contIt = block.continuationIterator();
    while (contIt.hasNext()) {
      Continuation cont = contIt.next();
      // First recurse
      inlinePassRecurse(cont, cong);
      // Then try to inline
      if (cont.isNoop()) {
        contIt.remove();
      } else if (tryInlineContinuation(block, cont, contIt,
                                       closedVars, recClosedVars)) {
        // Success!  Will now iterate over rest
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

  private boolean tryInlineConditional(Block block,
      ListIterator<Statement> stmtIt, Conditional conditional,
      Map<Block, Congruences> cong) {
    inlinePassRecurse(conditional, cong);
    
    // Then see if we can inline
    Block predicted = conditional.branchPredict(
                                Collections.<Var,Arg>emptyMap());
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

  private void inlinePassRecurse(Continuation cont,
                                 Map<Block, Congruences> cong) {
    for (Block inner: cont.getBlocks()) {
      inlinePass(inner, cong);
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
  
  /**
   * Try to replace instruction with e.g. a store.  This propagates
   * constants in some cases where propagating the value doesn't work.
   * 
   * TODO: currently only does instructions with a single output.
   * We don't have any const foldable instructions with multiple outputs
   * yet
   * @param state
   * @param stmtIt
   * @param inst
   */
  private void replaceInstruction(Congruences state, InitState init,
      ListIterator<Statement> stmtIt, Instruction inst) {
    if (!inst.hasSideEffects() && inst.getOutputs().size() == 1) {
      Var output = inst.getOutput(0);
      if (!InitVariables.varMustBeInitialized(output, true) ||
          init.isInitialized(output, true)) {
        if (Types.isScalarFuture(output)) {
          // Replace a computation with future output with a store
          Arg val = state.findRetrieveResult(output);
          if (val != null) {
            stmtIt.set(ICInstructions.futureSet(output, val));
          }
        } else if (Types.isScalarValue(output)) {
          Arg val = state.findCanonical(output.asArg(), CongruenceType.VALUE);
          if (val != null && val.isConstant()) {
            stmtIt.set(ICInstructions.valueSet(output, val));
          }
        }
      }
    }
  }

  private static void replaceCongruentNonRec(Continuation cont,
                              Congruences congruent, InitState init) {
    for (RenameMode mode: RENAME_MODES) {
      cont.renameVars(congruent.replacements(mode, init), mode, false); 
    }
  }

  private static void replaceCongruent(Statement stmt, Congruences congruent,
                                                            InitState init) {
    for (RenameMode mode: RENAME_MODES) {
      stmt.renameVars(congruent.replacements(mode, init), mode);
    }
  }
  
  private static void replaceCleanupCongruent(Block block,
                            Congruences congruent, InitState init) {

    for (RenameMode mode: RENAME_MODES) {
      block.renameCleanupActions(congruent.replacements(mode, init), mode);
    }
  }

  private static void updateCongruent(Logger logger, GlobalConstants consts,
            Function function, Instruction inst, int stmtIndex,
            Congruences state) {
    List<ValLoc> irs = inst.getResults(state);
    
    if (irs != null) {
      if (logger.isTraceEnabled()) {
        logger.trace("irs: " + irs.toString());
      }
      for (ValLoc resVal : irs) {
        state.update(consts, function.getName(), resVal, stmtIndex);
      }
    } else {
      logger.trace("no icvs");
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
      ExecContext execCx, Block block, Congruences state,
      Instruction inst, ListIterator<Statement> stmts, int stmtIndex) {
    // First see if we can replace some futures with values
    MakeImmRequest req = inst.canMakeImmediate(state.getClosed(stmtIndex), false);

    if (req == null) {
      return false;
    }
    
    // Create replacement sequence
    Block insertContext;
    ListIterator<Statement> insertPoint;
    boolean noWaitRequired = req.mode == TaskMode.LOCAL
        || req.mode == TaskMode.SYNC
        || (req.mode == TaskMode.LOCAL_CONTROL && execCx == ExecContext.CONTROL);
    
    // First remove old instruction
    stmts.remove();
    
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
    List<Fetched<Arg>> inVals = fetchInputsForSwitch(state, req,
                                    insertContext, noWaitRequired, alt);
    
    // Need filenames for output file values
    Map<Var, Var> filenameVals = loadOutputFileNames(state,
                      req.out, insertContext, insertPoint, req.mapOutVars);
    
    List<Var> outFetched = OptUtil.createLocalOpOutputVars(insertContext,
                      insertPoint, req.out, filenameVals, req.mapOutVars);
    MakeImmChange change;
    change = inst.makeImmediate(Fetched.makeList(req.out, outFetched), inVals);
    OptUtil.fixupImmChange(block, insertContext, inst, change, alt,
                           outFetched, req.out, req.mapOutVars);

    if (logger.isTraceEnabled()) {
      logger.trace("Replacing instruction <" + inst + "> with sequence "
          + alt.toString());
    }

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

  private static List<Fetched<Arg>> fetchInputsForSwitch(
      Congruences state,
      MakeImmRequest req, Block insertContext, boolean noWaitRequired,
      List<Instruction> alt) {
    List<Fetched<Arg>> inVals = new ArrayList<Fetched<Arg>>(req.in.size());

    // same var might appear multiple times
    HashMap<Var, Arg> alreadyFetched = new HashMap<Var, Arg>();
    for (Var toFetch: req.in) {
      Arg maybeVal;
      boolean fetchedHere;
      if (alreadyFetched.containsKey(toFetch)) {
        maybeVal = alreadyFetched.get(toFetch);
        fetchedHere = true;
      } else {
        maybeVal = state.findRetrieveResult(toFetch);
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
        Var fetchedV = OptUtil.fetchForLocalOp(insertContext, alt, toFetch);
        Arg fetched = Arg.createVar(fetchedV);
        inVals.add(new Fetched<Arg>(toFetch, fetched));
        alreadyFetched.put(toFetch, fetched);
      }
    }
    return inVals;
  }

  private static Map<Var, Var> loadOutputFileNames(Congruences state,
      List<Var> outputs, Block insertContext,
      ListIterator<Statement> insertPoint,
      boolean mapOutVars) {
    if (outputs == null)
      outputs = Collections.emptyList();
    
    Map<Var, Var> filenameVals = new HashMap<Var, Var>();
    for (Var output: outputs) {
      if (Types.isFile(output) && mapOutVars) {
        Var filenameVal = insertContext.declareVariable(Types.V_STRING,
                        OptUtil.optFilenamePrefix(insertContext, output),
                        Alloc.LOCAL, DefType.LOCAL_COMPILER, null);

        if (output.isMapped() == Ternary.FALSE) {
          // Initialize unmapped var
          assert (output.type().fileKind().supportsTmpImmediate());
          WrapUtil.initTemporaryFileName(insertPoint, output, filenameVal);
        } else {
          // Load existing mapping
          // Should only get here if value of mapped var is available.
          assert(output.mapping() != null);
          int insertStmtIndex = insertPoint.nextIndex();
          assert(state.isClosed(output.mapping(), insertStmtIndex))
               : output.mapping() + " not closed @ " + insertStmtIndex;
          Var filenameAlias = insertContext.declareVariable(Types.F_STRING,
              OptUtil.optFilenamePrefix(insertContext, output),
              Alloc.ALIAS, DefType.LOCAL_COMPILER, null);
          insertPoint.add(TurbineOp.getFileName(filenameAlias, output));
          insertPoint.add(TurbineOp.retrieveString(filenameVal, filenameAlias));
        }
        filenameVals.put(output, filenameVal);
      }
    }
    return filenameVals;
  }

  /**
   * Do any validations of the state of things
   */
  private void validateState(Congruences state) {
    if (ICOptimizer.SUPER_DEBUG) {
      state.validate();
    }
  }
}
