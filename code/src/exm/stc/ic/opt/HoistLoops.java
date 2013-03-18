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

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

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
      for (Var gv: prog.getGlobalVars()) {
        global.write(gv, false);
        global.declare(gv);
      }
      
      // Set up map for top block of function
      HoistTracking mainBlockState =
          global.makeChild(f.mainBlock(), 0, 0);
      
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
    /*
    StringBuilder sb = new StringBuilder();
    prog.prettyPrint(sb);
    System.err.println(sb.toString()); */
    // Might need to be updated
  }
  
  /**
   * @param logger
   * @param state 
   * @return true if change made
   */
  private boolean hoistRec(Logger logger, HoistTracking state) {
    // See if we can move any instructions from this block up
    boolean changed = hoistFromBlock(logger, state);
    
    // Recurse down to child blocks
    for (Continuation c: state.block.getContinuations()) {    
      for (Block childBlock: c.getBlocks()) {
        HoistTracking childState = state.makeChild(c, childBlock);
        if (hoistRec(logger, childState)) {
          changed = true;
        }
      }
    }
    return changed;
  }

  private static class HoistTracking {
    
    /**
     * Create root for whole program
     */
    public HoistTracking() {
      this(null, null, 0, 0,
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
      int childLoopHoist = c.isLoop() ? 0 : maxLoopHoist + 1;
      HoistTracking childState = makeChild(childBlock, childHoist,
                                            childLoopHoist);
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
        Block block, int maxHoist, int maxLoopHoist,
        HierarchicalMap<Var, Block> writeMap,
        HierarchicalMap<Var, Block> piecewiseWriteMap,
        HierarchicalMap<Var, Block> declareMap,
        HierarchicalMap<Var, Block> initializedMap) {
      super();
      this.parent = parent;
      this.block = block;
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
      HoistTracking curr = this;
      for (int i = 0; i < links; i++) {
        curr = this.parent;
      }
      return curr;
    }
    
    public HoistTracking makeChild(Block childBlock,
                       int maxHoist, int maxLoopHoist) {
      return new HoistTracking(this, childBlock,
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
      for (Var initAlias: inst.getInitializedAliases()) {
        initializeAlias(initAlias);
      }
    }
    
    public void initializeAlias(Var v) {
      assert(v.storage() == VarStorage.ALIAS);
      initializedMap.put(v, this.block);
    }
    
    public void write(Var v, boolean piecewise) {
      if (piecewise) {
        piecewiseWriteMap.put(v, this.block);
      } else {
        writeMap.put(v, this.block);
      }
    }

    public void declare(Var v) {
      Logging.getSTCLogger().trace("declared " + v);
      if (trackDeclares(v)) {
        declareMap.put(v, this.block);
      }
    }
  }

  private static boolean trackDeclares(Var v) {
    // Track declares for variables that can be waited on
    return (Types.isArray(v.type()) || Types.isScalarFuture(v.type()) ||
            Types.isRef(v.type()));
  }

  private static boolean canHoistThrough(Continuation c) {
    if (c.getType() == ContinuationType.FOREACH_LOOP ||
          c.getType() == ContinuationType.RANGE_LOOP ||
          c.getType() == ContinuationType.LOOP ||
          c.getType() == ContinuationType.NESTED_BLOCK) {
      return true;
    } else if (c.getType() == ContinuationType.WAIT_STATEMENT &&
            ((WaitStatement)c).getMode() == WaitMode.WAIT_ONLY) {
      return true;
    }
    return false;
  }

  private boolean hoistFromBlock(Logger logger, HoistTracking state) {
    boolean changed = false;
    Block curr = state.block;
    
    for (Var v: curr.getVariables()) {
      state.declare(v);
    }
    
    // See if we can lift any instructions out of block
    ListIterator<Instruction> it = curr.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      
      if (state.maxHoist > 0) {
        boolean hoisted = tryHoist(logger, inst, state);
        if (hoisted) {
          it.remove();
          changed = true;
        }
      }
      
      // Need to update state regardless of hoisting
      state.updateState(inst);
    }
    return changed;
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
        maxHoist = Math.min(maxHoist, maxInputHoist(logger, state, inVar));
      }
    }
    for (Var readOutput: inst.getReadOutputs()) {
      maxHoist = Math.min(maxHoist, maxInputHoist(logger, state, readOutput));
    }
    
    for (Var out: inst.getOutputs()) {
      if (trackDeclares(out)) {
        int declareDepth = state.declareMap.getDepth(out);
        if (logger.isTraceEnabled())
          logger.trace("DeclareDepth of " + out + " is " + declareDepth);
        assert(declareDepth >= 0);
        
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
      if (out.storage() == VarStorage.ALIAS) {
        if (!inst.getInitializedAliases().contains(out)) {
          int initDepth = state.initializedMap.getDepth(out);
          if (logger.isTraceEnabled())
            logger.trace("hoist limited to " + initDepth + " because of "
                + " initialization for alias var " + out);
          maxHoist = Math.min(maxHoist, initDepth);
          return false;
        }
      }
    }
    
    if (logger.isTraceEnabled())
      logger.trace("maxHoist was " + maxHoist);
    
    if (maxHoist > 0) {
      doHoist(logger, inst, maxHoist, state);
      return true;
    } else {
      return false;
    }
  }

  private int maxInputHoist(Logger logger, HoistTracking state,
                                   Var inVar) {
    int depth = state.writeMap.getDepth(inVar);
    if (Types.isPiecewiseAssigned(inVar.type()) && !aggressive) {
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
    
    
    Block target = state.getAncestor(hoistDepth).block;
    logger.trace("Hoisting instruction up " + hoistDepth + " blocks: "
                 + inst.toString());
    assert(target != null && target != state.block);
    target.addInstruction(inst);
    
    // Move variable declaration if needed to outer block.
    relocateVarDefs(state, inst, hoistDepth);
    
    // need to update write map to reflect moved instruction
    state.getAncestor(hoistDepth).updateState(inst);
  }

  private static void relocateVarDefs(HoistTracking state,
      Instruction inst, int hoistDepth) {
    HoistTracking ancestor = state;
    HoistTracking target = state.getAncestor(hoistDepth);
    assert(target != null && state != target);
    while (ancestor != target) {
      assert(ancestor != null);
      // Relocate all output variable definitions to target block 
      relocateVarDefs(ancestor, target, inst);
      ancestor = state.parent;
    }
  }

  /**
   * Relocate output variables to target block
   * @param source
   * @param target
   * @param inst
   */
  private static void relocateVarDefs(HoistTracking source, 
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
