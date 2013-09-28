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

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidWriteException;
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
import exm.stc.ic.opt.ProgressOpcodes.Category;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.opt.valuenumber.ComputedValue;
import exm.stc.ic.opt.valuenumber.CongruentVars;
import exm.stc.ic.opt.valuenumber.UnifiedValues;
import exm.stc.ic.opt.valuenumber.ValLoc;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsValCopy;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.Fetched;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.TurbineOp;

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

  /**
   * Do a kind of dataflow analysis where we try to identify which futures are
   * closed at different points in the program. This allows us to switch to
   * lower-overhead versions of many operations.
   * 
   * @param logger
   * @param program
   * @throws InvalidWriteException
   */
  public void optimize(Logger logger, Program program)
      throws InvalidWriteException {

    for (Function f : program.getFunctions()) {
      // Do repeated passes until converged
      boolean changes;
      int pass = 1;
      do {
        CongruentVars fnState = initFuncState(logger, program, f);
        
        logger.trace("closed variable analysis on function " + f.getName()
            + " pass " + pass);
        changes = forwardDataflow(logger, program, f, ExecContext.CONTROL,
            f.mainBlock(), fnState);
        liftWait(logger, program, f);
        pass++;
      } while (changes);
    }
  }

  private CongruentVars initFuncState(Logger logger,
        Program program, Function f) {
    CongruentVars congruent = new CongruentVars(logger, reorderingAllowed);
    for (Var v : program.getGlobalVars()) {
      // First, all constants can be treated as being set
      if (v.storage() == Alloc.GLOBAL_CONST) {
        Arg val = program.lookupGlobalConst(v.name());
        assert (val != null) : v.name();
        
        ValLoc assign = ComputedValue.assignComputedVal(v, val);
        ValLoc retrieve = new ValLoc(ComputedValue.retrieveCompVal(v),
                          val, Closed.YES_NOT_RECURSIVE, IsValCopy.NO);
        congruent.update(f.getName(), assign);
        congruent.update(f.getName(), retrieve);
      }
    }
    
    for (WaitVar wv : f.blockingInputs()) {
      congruent.markClosed(wv.var, false);
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
   * 
   * @param execCx
   * @param block
   * @param cv
   *          copy of cv from outer scope, or null if it should be initialized
   * @param replaceInputs
   *          : a set of variable replaces to do from this point in IC onwards
   * @return true if this should be called again
   * @throws InvalidWriteException
   */
  private boolean forwardDataflow(Logger logger, Program program, Function f,
      ExecContext execCx, Block block, CongruentVars state) throws InvalidWriteException {
    for (Var v : block.getVariables()) {
      if (v.mapping() != null && Types.isFile(v.type())) {
        // Track the mapping
        ValLoc filenameVal = ValLoc.makeFilename(v.mapping().asArg(), v);
        state.update(f.getName(), filenameVal);
      }
    }

    handleStatements(logger, program, f, execCx, block,
                     block.statementIterator(), state);

    replaceCleanupCongruent(block, state);

    boolean inlined = false;
    // might be able to eliminate wait statements or reduce the number
    // of vars they are blocking on
    for (int i = 0; i < block.getContinuations().size(); i++) {
      Continuation c = block.getContinuation(i);

      // Replace all variables in the continuation construct
      replaceCongruentNonRec(c, state);

      Block toInline = c.tryInline(state.getClosed(),
                                   state.getRecursivelyClosed(),
                                   reorderingAllowed);
      if (logger.isTraceEnabled()) {
        logger.trace("Inlining continuation " + c.getType());
      }
      if (toInline != null) {

        prepareForInline(toInline, state);
        c.inlineInto(block, toInline);
        i--; // compensate for removal of continuation
        inlined = true;
      }
    }
    

    if (inlined) {
      // Rebuild data structures for this block after inlining
      validateState(state);
      return true;
    }

    // Note: assume that continuations aren't added to rule engine until after
    // all code in block has run
    for (Continuation cont : block.getContinuations()) {
      recurseOnContinuation(logger, program, f, execCx, cont, state);
    }
    
    // Check that things are valid before leaving
    validateState(state);
    
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
   * @throws InvalidWriteException
   */
  private UnifiedValues recurseOnContinuation(Logger logger, Program program,
      Function fn, ExecContext execCx, Continuation cont,
      CongruentVars state) throws InvalidWriteException {
    logger.trace("Recursing on continuation " + cont.getType());

    CongruentVars contState = state.makeChild(cont.inheritsParentVars());
    // additional variables may be close once we're inside continuation
    List<BlockingVar> contClosedVars = cont.blockingVars(true);
    if (contClosedVars != null) {
      for (BlockingVar bv : contClosedVars) {
        contState.markClosed(bv.var, bv.recursive);
      }
    }

    // For conditionals, find variables closed on all branches
    boolean unifyBranches = cont.isExhaustiveSyncConditional();
    List<CongruentVars> branchStates = unifyBranches ?
                      new ArrayList<CongruentVars>() : null;

    List<Block> contBlocks = cont.getBlocks();
    for (int i = 0; i < contBlocks.size(); i++) {
      CongruentVars blockState;
      boolean again;
      int pass = 1;
      do {
        logger.debug("closed variable analysis on nested block pass " + pass);
        blockState = contState.makeChild(true);
        again = forwardDataflow(logger, program, fn, cont.childContext(execCx),
            contBlocks.get(i), blockState);

        // changes in nested scope don't require extra pass over this scope
        pass++;
      } while (again);

      if (unifyBranches) {
        branchStates.add(blockState);
      }
    }

    if (unifyBranches) {
      return UnifiedValues.unify(logger, fn, reorderingAllowed, state, cont,
                                branchStates, contBlocks);
    } else {
      return UnifiedValues.EMPTY;
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
                                       CongruentVars state) {
    // Redo replacements for newly inserted instructions/continuations
    for (Statement stmt: blockToInline.getStatements()) {
      replaceCongruent(stmt, state);
    }
    for (Continuation c : blockToInline.getContinuations()) {
      replaceCongruentNonRec(c, state);
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
   */
  private void handleStatements(Logger logger, Program program, Function f,
      ExecContext execCx, Block block, ListIterator<Statement> stmts,
      CongruentVars state) 
          throws InvalidWriteException {
    while (stmts.hasNext()) {
      Statement stmt = stmts.next();

      if (stmt.type() == StatementType.INSTRUCTION) {
        handleInstruction(logger, program, f, execCx, block, stmts,
            stmt.instruction(), state);
      } else {
        assert (stmt.type() == StatementType.CONDITIONAL);
        // handle situations like:
        // all branches assign future X a local values v1,v2,v3,etc.
        // in this case should try to create another local value outside of
        // conditional z which has the value from all branches stored
        UnifiedValues unified = recurseOnContinuation(logger, program, f,
            execCx, stmt.conditional(), state);
        state.addUnifiedValues(f.getName(), unified);
      }
    }
  }

  private void handleInstruction(Logger logger, Program prog,
      Function f, ExecContext execCx, Block block,
      ListIterator<Statement> stmts, Instruction inst,
      CongruentVars state) {
    if (logger.isTraceEnabled()) {
      state.printTraceInfo(logger);
      logger.trace("-----------------------------");
      logger.trace("At instruction: " + inst);
    }

    // Immediately replace congruent variables
    replaceCongruent(inst, state);
    
    if (logger.isTraceEnabled()) {
      logger.trace("Instruction after updates: " + inst);
    }
    // now try to see if we can change to the immediate version
    if (switchToImmediate(logger, f, execCx, block, state, inst, stmts)) {
      // Continue pass at the start of the newly inserted sequence
      // as if it was always there
      return;
    }
    
    /*
     * See if value is already computed somewhere and see if we should replace
     * variables going forward
     * NOTE: we don't delete any instructions on this pass, but rather rely on
     * dead code elim to later clean up unneeded instructions instead.
     */
    updateCongruent(logger, f, inst, state);

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

    for (Var out: inst.getClosedOutputs()) {
      if (logger.isTraceEnabled()) {
        logger.trace("Output " + out.name() + " is closed");
      }
      state.markClosed(out, false);
    }
  }

  private static final List<RenameMode> RENAME_MODES =
      Arrays.asList(RenameMode.VALUE, RenameMode.REFERENCE);
  
  private static void replaceCongruentNonRec(Continuation cont,
                                      CongruentVars congruent) {
    for (RenameMode mode: RENAME_MODES) {
      cont.renameVars(congruent.replacements(mode), mode, false); 
    }
  }

  private static void replaceCongruent(Statement stmt,
                                       CongruentVars congruent) {
    for (RenameMode mode: RENAME_MODES) {
      stmt.renameVars(congruent.replacements(mode), mode);
    }
  }
  
  private static void replaceCleanupCongruent(Block block,
                                        CongruentVars congruent) {

    for (RenameMode mode: RENAME_MODES) {
      block.renameCleanupActions(congruent.replacements(mode), mode);
    }
  }

  private static void updateCongruent(Logger logger, Function function,
            Instruction inst, CongruentVars state) {
    List<ValLoc> irs = inst.getResults(state);
    
    if (irs != null) {
      if (logger.isTraceEnabled()) {
        logger.trace("irs: " + irs.toString());
      }
      for (ValLoc resVal : irs) {
        boolean ok = state.update(function.getName(), resVal);
        if (!ok) {
          // Invalidate any computed values associated with instruction to
          // minimise chance of invalid optimization
          // TODO: remove later if less aggressive approach works?
          purgeValues(logger, state, irs);
          return;
        }
      }
    } else {
      logger.trace("no icvs");
    }
  }

  private static void purgeValues(Logger logger, CongruentVars congruent,
                                  List<ValLoc> rvs) {
    for (ValLoc rv: rvs) {
      Logging.getSTCLogger().debug("Invalidating " + rv);
      congruent.markContradiction(rv);
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
      ExecContext execCx, Block block, CongruentVars state,
      Instruction inst, ListIterator<Statement> stmts) {
    // First see if we can replace some futures with values
    MakeImmRequest req = inst.canMakeImmediate(state.getClosed(), false);

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
      CongruentVars state,
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

  private static Map<Var, Var> loadOutputFileNames(CongruentVars state,
      List<Var> outputs, Block insertContext,
      ListIterator<Statement> insertPoint, boolean mapOutVars) {
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
          assert(state.isClosed(output.mapping()));
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
  private void validateState(CongruentVars state) {
    if (ICOptimizer.SUPER_DEBUG) {
      state.validate();
    }
  }
}
