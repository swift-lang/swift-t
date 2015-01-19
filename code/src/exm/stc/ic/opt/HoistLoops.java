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

import java.util.List;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.Location;
import exm.stc.common.lang.Semantics;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.InitType;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;

/**
 * Hoist instructions out of continuations so that can be evaluated fewer times
 * (if hoisted out of loop), or evaluated earlier
 * CASES
 * -----
 * Regular assign to non-alias var -> hoist based on inputs
 * Initialize alias var (no other outputs) -> hoist based on inputs
 * Regular assign to alias var -> check inputs and alias init
 * Piecewise assign -> hoist based on inputs, don't hoist past declaration of
 *                                            output
 *
 */
public class HoistLoops implements OptimizerPass {

  /**
   * If true, hoist array reads in such a way that could prevent
   * future dataflow optimizations
   */
  private final boolean aggressive;

  public HoistLoops(boolean aggressive) {
    this.aggressive = true;
  }

  @Override
  public String getPassName() {
    return "Loop hoisting";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_HOIST;
  }

  @Override
  public void optimize(Logger logger, Program prog) {
    for (Function f: prog.getFunctions()) {
      HoistTracking global = new HoistTracking();
      // Global constants already written
      for (Var gv: prog.allGlobals()) {
        if (gv.storage().isConst()) {
          // Constants are pre-written
          global.write(gv, false);
        }
        global.declare(gv);
      }

      // Set up map for top block of function
      HoistTracking mainBlockState =
          global.makeChild(f.mainBlock(), true, ExecContext.control(), 0, 0);

      // Inputs are written elsewhere
      for (Var in: f.getInputList()) {
        mainBlockState.write(in, false);
        mainBlockState.declare(in);
      }
      for (Var out: f.getOutputList()) {
        mainBlockState.declare(out);
      }
      hoistRec(logger, mainBlockState);
    }
  }

  /**
   * @param logger
   * @param state
   * @return true if change made
   */
  private boolean hoistRec(Logger logger, HoistTracking state) {
    // See if we can move any instructions from this block up
    boolean changed = false;
    Block curr = state.block;

    for (Var v: curr.getVariables()) {
      state.declare(v);
    }

    // See if we can lift any instructions out of block
    ListIterator<Statement> it = curr.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction inst = stmt.instruction();

          if (state.maxHoist > 0) {
            boolean hoisted = tryHoist(logger, inst, state);
            if (hoisted) {
              it.remove();
              changed = true;
            }
          }

          // Need to update state regardless of hoisting
          state.updateState(inst);
          break;
        }
        case CONDITIONAL: {
          it.previous(); // Insert any hoisted instruction before conditional
          boolean changed2 = hoistRecCont(logger, state, it, stmt.conditional());
          changed = changed || changed2;
          // Return to position after conditional.  Note that this is guaranteed to
          // return to correct position after [0..n] calls to it.add(...)
          it.next();
          break;
        }
        default:
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }

    // Recurse down to child blocks
    for (Continuation c: state.block.getContinuations()) {
      boolean changed2 = hoistRecCont(logger, state, it, c);
      changed = changed || changed2;
    }
    return changed;
  }

  private boolean hoistRecCont(Logger logger, HoistTracking state,
                              ListIterator<Statement> insertPos, Continuation cont) {
    state.currPos = insertPos;
    for (Block childBlock: cont.getBlocks()) {
      HoistTracking childState = state.makeChild(cont, childBlock);
      if (hoistRec(logger, childState)) {
        return true;
      }
    }
    state.currPos = null; // Now invalid
    return false;
  }

  private static class HoistTracking {

    /**
     * Create root for whole program
     */
    public HoistTracking() {
      this(null, null, false, ExecContext.control(), 0, 0,
           new HierarchicalMap<Var, Block>(),
           new HierarchicalMap<Var, Block>(),
           new HierarchicalMap<Var, Block>(),
           new HierarchicalMap<Var, Block>());
    }

    /**
     * Initialize child state for block inside continuation
     * @param c
     * @param childBlock
     * @return
     */
    public HoistTracking makeChild(Continuation c, Block childBlock) {
      int childHoist = canHoistThrough(c) ? maxHoist + 1 : 0;

      Logging.getSTCLogger().trace("Child of " + c.getType() + " " + " childHoist: " + childHoist);
      int childLoopHoist = c.isLoop() ? 0 : maxLoopHoist + 1;
      HoistTracking childState = makeChild(childBlock, c.isAsync(),
          c.childContext(execCx), childHoist, childLoopHoist);
      // make sure loop iteration variables, etc are tracked
      for (Var v: c.constructDefinedVars()) {
        childState.write(v, false);
        childState.declare(v);
      }

      // If we are waiting for var, don't hoist out past that
      if (c.getType() == ContinuationType.WAIT_STATEMENT) {
        for (WaitVar waitVar: ((WaitStatement)c).getWaitVars()) {
          childState.write(waitVar.var, false);
        }
      }
      return childState;
    }

    private HoistTracking(HoistTracking parent,
        Block block, boolean async, ExecContext execCx,
        int maxHoist, int maxLoopHoist,
        HierarchicalMap<Var, Block> writeMap,
        HierarchicalMap<Var, Block> piecewiseWriteMap,
        HierarchicalMap<Var, Block> declareMap,
        HierarchicalMap<Var, Block> initializedMap) {
      super();
      this.parent = parent;
      this.block = block;
      this.async = async;
      this.execCx = execCx;
      this.maxHoist = maxHoist;
      this.maxLoopHoist = maxLoopHoist;
      this.writeMap = writeMap;
      this.piecewiseWriteMap = piecewiseWriteMap;
      this.declareMap = declareMap;
      this.initializedMap = initializedMap;
    }

    /** Tracking for parent block */
    public final HoistTracking parent;

    /** Current block */
    public final Block block;

    /** Whether it executes asynchronously from parent */
    public final boolean async;

    /** Execution context */
    public final ExecContext execCx;

    /** Track where instruction should be inserted by child */
    public ListIterator<Statement> currPos = null;

    /**
     * maximum number of blocks can lift out
     */
    public final int maxHoist;
    /**
     * max number of block can lift out without going through loop
     */
    public final int maxLoopHoist;

    /**
     * Track writes (non-piecewise only) to variables
     */
    public final HierarchicalMap<Var, Block> writeMap;

    /**
     * Track piecewise writes to variables
     */
    public final HierarchicalMap<Var, Block> piecewiseWriteMap;

    /**
     * Track variable declarations
     */
    public final HierarchicalMap<Var, Block> declareMap;
    /**
     * Track variables that need to be initialized
     */
    public final HierarchicalMap<Var, Block> initializedMap;

    public HoistTracking getAncestor(int links) {
      Logger logger = Logging.getSTCLogger();

      HoistTracking curr = this;
      for (int i = 0; i < links; i++) {
        if (logger.isTraceEnabled()) {
          logger.trace("Ancestor of " + this.block.getType() +
                           ": " + this.parent.block.getType());
        }
        curr = curr.parent;
      }
      return curr;
    }

    public HoistTracking makeChild(Block childBlock, boolean async,
                       ExecContext newExecCx,
                       int maxHoist, int maxLoopHoist) {
      return new HoistTracking(this, childBlock, async, execCx,
                              maxHoist, maxLoopHoist,
                              writeMap.makeChildMap(),
                              piecewiseWriteMap.makeChildMap(),
                              declareMap.makeChildMap(),
                              initializedMap.makeChildMap());
    }

    public void updateState(Instruction inst) {
      List<Var> piecewiseOutputs = inst.getPiecewiseAssignedOutputs();
      for (Var out: inst.getModifiedOutputs()) {
        write(out, piecewiseOutputs.contains(out));
      }
      for (Pair<Var, InitType> init: inst.getInitialized()) {
        initialize(init.val1, init.val2);
      }
    }

    public void initialize(Var v, InitType initType) {
      assert(Types.outputRequiresInitialization(v) ||
             Types.inputRequiresInitialization(v));
      // Track partial or full initialization
      initializedMap.put(v, this.block);
    }

    public void write(Var v, boolean piecewise) {
      if (piecewise) {
        this.piecewiseWriteMap.put(v, this.block);
      } else {
        this.writeMap.put(v, this.block);
      }

      /*
       * Also mark as written in parent continuation if synchronous -
       * e.g. branches of if.
       * This is because code in the parent continuation can reasonably
       * assume that this var was written, so hoisting it out would
       * be unreasonable.
       */
      HoistTracking taskRoot = this;
      while (!taskRoot.async && taskRoot.parent != null) {
        taskRoot = taskRoot.parent;
      }
      if (taskRoot != this) {
        taskRoot.write(v, piecewise);
      }
    }

    public void declare(Var v) {
      Logging.getSTCLogger().trace("declared " + v);
      if (trackDeclares(v)) {
        declareMap.put(v, this.block);
      }
    }

    /**
     * Add instruction at insert point
     * @param inst
     */
    public void addInstruction(Instruction inst) {
      inst.setParent(block);
      currPos.add(inst);
    }
  }

  private static boolean trackDeclares(Var v) {
    // Track declares for variables that can be waited on
    return (Types.isContainer(v) || Types.isPrimFuture(v) ||
            Types.isRef(v));
  }

  private static boolean canHoistThrough(Continuation c) {
    if (c.getType() == ContinuationType.FOREACH_LOOP ||
          c.getType() == ContinuationType.RANGE_LOOP ||
          c.getType() == ContinuationType.LOOP ||
          c.getType() == ContinuationType.NESTED_BLOCK) {
      return true;
    } else if (c.getType() == ContinuationType.WAIT_STATEMENT &&
            !((WaitStatement)c).hasExplicit() &&
            Location.isAnyLocation(((WaitStatement)c).targetLocation().location, true)) {
      // Don't hoist through wait statements that have explicit
      // ordering constraints or locations.
      // TODO: Could relax this assumption for target locations for basic operations
      //      like variable lookups
      return true;
    }
    return false;
  }

  /**
   * Try to hoist this instruction
   * @param logger
   * @param inst
   * @param state
   * @return true if hoisted
   */
  private boolean tryHoist(Logger logger,
      Instruction inst, HoistTracking state) {
    if (logger.isTraceEnabled()) {
      logger.trace("Try hoist " + inst + " maxHoist=" + state.maxHoist +
                   " maxLoopHoist=" + state.maxLoopHoist);
    }
    if (!inst.canChangeTiming()) {
      logger.trace("Can't hoist: side-effects");
      // Don't try to mess with things with side-effects
      return false;
    }

    // See where the input variables were written
    // Max number of blocks can be hoisted
    int maxHoist = state.maxHoist;

    for (Arg in: inst.getInputs()) {
      // Break out early in common case
      if (maxHoist <= 0)
        return false;

      if (in.isVar()) {
        Var inVar = in.getVar();
        // Check where input is assigned
        maxHoist = Math.min(maxHoist, maxInputHoist(logger, state, inVar));

        // Check if we can pass input between tasks, if not conservatively
        // don't hoist
        if (!Semantics.canPassToChildTask(inVar)) {
          return false;
        }
      }
    }
    for (Var readOutput: inst.getReadOutputs()) {
      int inputHoist = maxInputHoist(logger, state, readOutput);
      maxHoist = Math.min(maxHoist, inputHoist);
      logger.trace("Hoist limited to " + inputHoist + " by read output: "
                    + readOutput.name());
    }

    for (Var out: inst.getOutputs()) {
      /* Check if we can pass vars between tasks, if not conservatively
       * don't hoist, because hoisting may put producer and consumer of data
      // in different tasks */
      if (!Semantics.canPassToChildTask(out)) {
        return false;
      }
      if (trackDeclares(out)) {
        int declareDepth = state.declareMap.getDepth(out);
        if (logger.isTraceEnabled())
          logger.trace("DeclareDepth of " + out + " is " + declareDepth);
        assert(declareDepth >= 0) : declareDepth + " " + out;

        // Can't hoist out of loop if variable declared outside loop
        if (declareDepth > state.maxLoopHoist && !inst.isIdempotent()) {
          // Don't hoist out of loop - could do fewer assignments than intended
          maxHoist = Math.min(maxHoist, state.maxLoopHoist);
          if (logger.isTraceEnabled())
            logger.trace("Hoist constrained by declaration to: " +
                          state.maxLoopHoist);
        }
      }
    }

    // Don't hoist piecewise-assigned var declaration out of loop
    for (Var out: inst.getPiecewiseAssignedOutputs()) {
      if (!inst.isIdempotent()) {
        maxHoist = Math.min(maxHoist, state.maxLoopHoist);
        if (logger.isTraceEnabled())
          logger.trace("Can't hoist declaration of " + out + " out of loop, "
                  + "constrained to: " + state.maxLoopHoist);
      }
    }

    // Check that any output variables that are aliases are
    // initialized if needed
    for (Var out: inst.getOutputs()) {
      if (Types.outputRequiresInitialization(out)) {
        if (!inst.isInitialized(out)) {
          int initDepth = state.initializedMap.getDepth(out);
          if (logger.isTraceEnabled())
            logger.trace("hoist limited to " + initDepth + " because of "
                + " initialization for var " + out);
          maxHoist = Math.min(maxHoist, initDepth);
          return false;
        }
      }
    }

    if (logger.isTraceEnabled())
      logger.trace("maxHoist was " + maxHoist);

    if (maxHoist <= 0) {
      return false;
    }

    int maxCorrectContext = maxHoistContext(logger, state,
                                inst.execMode(), maxHoist);

    maxHoist = Math.max(maxHoist, maxCorrectContext);

    if (maxHoist <= 0) {
      return false;
    }

    doHoist(logger, inst, maxHoist, state);
    return true;
  }

  /**
   * Find maximum hoist with compatible execution context
   * @param logger
   * @param state
   * @param maxHoist
   * @return
   */
  private int maxHoistContext(Logger logger, HoistTracking state,
                        ExecTarget target, int maxHoist) {
    int maxCorrectContext = 0;
    HoistTracking curr = state;
    for (int hoist = 1; hoist <= maxHoist; hoist++) {
      curr = state.parent;
      if (target.canRunIn(curr.execCx)) {
        maxCorrectContext = hoist;
      }
    }

    if (logger.isTraceEnabled()) {
      logger.trace("Hoist limited to " + maxCorrectContext + " by execution " +
                   "target " + target);
    }
    return maxCorrectContext;
  }

  private int maxInputHoist(Logger logger, HoistTracking state,
                                   Var inVar) {
    int depth = state.writeMap.getDepth(inVar);
    if (logger.isTraceEnabled()) {
      logger.trace("Write depth of " + inVar.name() + ": " + depth);
    }

    if (Types.isPiecewiseAssigned(inVar) && !aggressive) {
      // We might want to avoid moving before a piecewise write since it could
      // hurt future optimization opportunities
      int pwDepth = state.piecewiseWriteMap.getDepth(inVar);
      if (pwDepth >= 0) {
        depth = Math.min(pwDepth, depth);
      }
      if (logger.isTraceEnabled())
        logger.trace("Hoist constrained by piecewise write " + inVar + ": "
                                           + pwDepth);
    }

    int maxHoist = Integer.MAX_VALUE;
    // If there was a write to a variable, don't hoist past that
    if (depth >= 0) {
      maxHoist = Math.min(maxHoist, depth);
      if (logger.isTraceEnabled())
        logger.trace("Depth reduced to " + maxHoist + " because of var "
                     + inVar);
    } else if (depth < 0 && trackDeclares(inVar)) {
      // Maybe there was a declaration of the variable that we
      // shouldn't hoist past.  This works for, e.g. an unwritten
      //  future that is is assigned by a concurrent task
      int declareDepth = state.declareMap.getDepth(inVar);
      if (logger.isTraceEnabled())
        logger.trace("Hoist constrained by " + inVar + ": "
                                           + declareDepth);
      assert(declareDepth >= 0) : inVar;
      maxHoist = Math.min(maxHoist, declareDepth);
    } else if (depth < 0) {
      // Don't have any info about how far we can hoist it, do nothing
      logger.trace("Can't hoist because of " + inVar);
      return 0;
    }
    return maxHoist;
  }

  private static void doHoist(Logger logger, Instruction inst,
          int hoistDepth, HoistTracking state) {
    assert(hoistDepth > 0);

    logger.trace("Hoisting instruction up " + hoistDepth + " blocks: "
                 + inst.toString());
    HoistTracking ancestor = state.getAncestor(hoistDepth);
    logger.trace("Ancestor block " + ancestor.block.getType());

    ancestor.addInstruction(inst);

    // Move variable declaration if needed to outer block.
    relocateVarDefs(state, inst, ancestor);

    // need to update write map to reflect moved instruction
    ancestor.updateState(inst);
  }

  /**
   * Relocate var defs from this block and any in-between ancestors
   * to target block
   * @param state
   * @param inst
   * @param targetAncestor
   */
  private static void relocateVarDefs(HoistTracking state,
      Instruction inst, HoistTracking targetAncestor) {
    HoistTracking ancestor = state;
    assert(targetAncestor != null && state != targetAncestor);
    while (ancestor != targetAncestor) {
      assert(ancestor != null);
      // Relocate all output variable definitions to target block
      relocateVarDefsFromBlock(ancestor, targetAncestor, inst);
      ancestor = ancestor.parent;
    }
  }

  /**
   * Relocate output variables from one block to target block
   * @param source
   * @param target
   * @param inst
   */
  private static void relocateVarDefsFromBlock(HoistTracking source,
      HoistTracking target, Instruction inst) {
    assert(source != target);
    ListIterator<Var> varIt = source.block.variableIterator();
    while (varIt.hasNext()) {
      Var def = varIt.next();
      for (Var out: inst.getOutputs()) {
        if (def.equals(out)) {
          varIt.remove();
          target.block.addVariable(def);
          moveVarCleanupAction(out, source.block, target.block);
          // Update map
          if (trackDeclares(out)) {
            source.declareMap.remove(out);
          }
          target.declare(out);
          break;
        }
      }
    }
  }

  /**
   * Relocate cleanup actions for variable to target block
   * @param var
   * @param source
   * @param target
   */
  private static void moveVarCleanupAction(Var var, Block source, Block target) {
    ListIterator<CleanupAction> actIt = source.cleanupIterator();
    while (actIt.hasNext()) {
      CleanupAction ca = actIt.next();
      if (ca.var().equals(var)) {
        actIt.remove();
        target.addCleanup(ca.var(), ca.action());
      }
    }
  }
}
