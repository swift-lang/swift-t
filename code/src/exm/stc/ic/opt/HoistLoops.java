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
import java.util.List;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * 
 * TODO: problem with current design: need to track where
 *    arrays are declared and then make sure if we're piece-wise assigning
 *    those arrays that we don't hoist out past the original variable
 *    declarations
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
        global.write(gv);
        global.declare(gv);
      }
      
      // Set up map for top block of function
      HoistTracking mainBlockState =
          global.makeChild(f.getMainblock(), 0, 0);
      
      // Inputs are written elsewhere
      for (Var in: f.getInputList()) {
        mainBlockState.write(in);
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
   * 
   * @param logger
   * @param curr current block
   * @param ancestors ancestors of current block
   * @param state 
   * @return true if change made
   */
  private static boolean hoistRec(Logger logger, HoistTracking state) {
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
        childState.write(v);
        childState.declare(v);
      }
      
      // If we are waiting for var, don't hoist out past that
      if (c.getType() == ContinuationType.WAIT_STATEMENT) {
        for (Var waitVar: ((WaitStatement)c).getWaitVars()) {
          childState.write(waitVar);            
        }
      }
      return childState;
    }

    private HoistTracking(HoistTracking parent,
        Block block, int maxHoist, int maxLoopHoist,
        HierarchicalMap<Var, Block> writeMap,
        HierarchicalMap<Var, Block> declareMap,
        HierarchicalMap<Var, Block> initializedMap) {
      super();
      this.parent = parent;
      this.block = block;
      this.maxHoist = maxHoist;
      this.maxLoopHoist = maxLoopHoist;
      this.writeMap = writeMap;
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
     * Track writes (TODO: non-piecewise only?) to variables
     */
    public final HierarchicalMap<Var, Block> writeMap;
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
                              declareMap.makeChildMap(),
                              initializedMap.makeChildMap());
    }
    
    public void updateState(Instruction inst) {
      for (Var out: inst.getModifiedOutputs()) {
        write(out);
      }
      for (Var initAlias: inst.getInitializedAliases()) {
        initializeAlias(initAlias);
      }
    }
    
    public void initializeAlias(Var v) {
      assert(v.storage() == VarStorage.ALIAS);
      initializedMap.put(v, this.block);
    }
    
    public void write(Var v) {
      if (trackWrites(v)) {
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
  
  /**
   * True if the variable is one we should track
   * @param in
   * @return
   */
  private static boolean trackWrites(Var in) {
    Type t = in.type();
    if (Types.isScalarFuture(t) || Types.isScalarValue(t)) {
      return true;
    } else if (Types.isRef(t)) {
      return true;
    } else if (Types.isArray(t)) {
      return true;
    }
    return false;
  }

  private static boolean trackDeclares(Var v) {
    return Types.isArray(v.type()) || Types.isArrayRef(v.type());
  }

  private static boolean canHoistThrough(Continuation c) {
    if (c.getType() == ContinuationType.FOREACH_LOOP ||
          c.getType() == ContinuationType.RANGE_LOOP ||
          c.getType() == ContinuationType.LOOP ||
          c.getType() == ContinuationType.NESTED_BLOCK) {
      return true;
    } else if (c.getType() == ContinuationType.WAIT_STATEMENT &&
            ((WaitStatement)c).getMode() == WaitMode.DATA_ONLY) {
      return true;
    }
    return false;
  }

  private static boolean hoistFromBlock(Logger logger, HoistTracking state) {
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
   * 
   * @param logger
   * @param curr
   * @param inst
   * @param ancestors
   * @param state
   * @return true if hoisted
   */
  private static boolean tryHoist(Logger logger,
      Instruction inst, HoistTracking state) {
    if (logger.isTraceEnabled()) {
      logger.trace("Try hoist " + inst + " maxHoist=" + state.maxHoist + 
                   " maxLoopHoist=" + state.maxLoopHoist);
    }
    if (inst.hasSideEffects()) {
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
    
    for (Var out: inst.getPiecewiseAssignedOutputs()) {
      // Don't hoist past declaration
      assert(trackDeclares(out));
      int declareDepth = state.declareMap.getDepth(out);
      assert(declareDepth >= 0);
      maxHoist = Math.min(maxHoist, declareDepth);

      // Don't hoist out of array - could do fewer assignments than intended
      maxHoist = Math.min(maxHoist, state.maxLoopHoist);
    }
    
    // Check that any output variables that are aliases are
    // initialized if needed
    for (Var out: inst.getOutputs()) {
      if (out.storage() == VarStorage.ALIAS) {
        if (!inst.getInitializedAliases().contains(out) &&
            !state.initializedMap.containsKey(out)) {
          logger.trace("can't hoist because of uninitialized alias");
          return false;
        }
      }
    }
    
    if (maxHoist > 0) {
      doHoist(logger, inst, maxHoist, state);
      return true;
    } else {
      return false;
    }
  }

  private static int maxInputHoist(Logger logger, HoistTracking state,
                                   Var inVar) {
    int depth = state.writeMap.getDepth(inVar);
    int maxHoist = Integer.MAX_VALUE;
    // If there was a write to a variable, don't hoist past that
    if (depth >= 0) {
      maxHoist = Math.min(maxHoist, depth);
      if (logger.isTraceEnabled())
        logger.trace("Depth reduced to " + maxHoist + " because of var "
                     + inVar);
    } else if (depth < 0 && trackDeclares(inVar)) {
      // Maybe there was a declaration of the variable that we
      // shouldn't hoist past
      int declareDepth = state.declareMap.getDepth(inVar);
      if (logger.isTraceEnabled())
        logger.trace("Hoist constrained by " + inVar + ": "
                                           + declareDepth);
      assert(declareDepth >= 0) : inVar;
      maxHoist = Math.min(maxHoist, declareDepth);
    } else if (depth < 0 && !trackWrites(inVar)) {
      // We weren't tracking anything, can't hoist
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
