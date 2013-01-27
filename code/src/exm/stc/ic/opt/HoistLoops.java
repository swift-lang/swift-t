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

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
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
      Block block = f.getMainblock();
      HierarchicalMap<String, Block> globalMap =
                      new HierarchicalMap<String, Block>();
      // Global constants already written
      for (String gc: prog.getGlobalConsts().keySet()) {
        Var gcV = new Var(prog.lookupGlobalConst(gc).getType(), gc,
                VarStorage.GLOBAL_CONST, DefType.GLOBAL_CONST);
        if (trackWrites(gcV)) { 
          globalMap.put(gc, null);
        }
      }
      
      // Set up map for top block of function
      HierarchicalMap<String, Block> writeMap = globalMap.makeChildMap();
      // Inputs are written
      for (Var in: f.getInputList()) {
        if (trackWrites(in)) {
          writeMap.put(in.name(), block);
        }
      }
      boolean changed = hoistRec(logger, block, new ArrayList<Block>(), 0, 0,
                                 writeMap);
      
      if (changed) {
        FixupVariables.fixupVariablePassing(logger, prog, f);
      }
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
   * @param maxHoist maximum number of blocks can lift out
   * @param maxLoopHoist max number of block can lift out without
   *                     going through loop
   * @param writeMap map for current block filled in with anything defined 
   *                by construct or outer blocks
   * @return true if change made
   */
  private static boolean hoistRec(Logger logger, Block curr, List<Block> ancestors,
            int maxHoist, int maxLoopHoist, HierarchicalMap<String, Block> writeMap) {
    boolean changed = false;
    // Update map with variables written in this block
    updateMapWithWrites(curr, writeMap);
    
    // See if we can move any instructions from this block up
    if (maxHoist > 0) {
      changed = tryHoist(logger, curr, ancestors, maxHoist, maxLoopHoist,
                         writeMap);
    }
    
    // Recurse down to child blocks
    ancestors.add(curr);
    for (Continuation c: curr.getContinuations()) {    
      int childHoist = canHoistThrough(c) ? maxHoist + 1 : 0;
      int childLoopHoist = c.isLoop() ? 0 : maxLoopHoist + 1;
      for (Block b: c.getBlocks()) {
        HierarchicalMap<String, Block> childWriteMap = writeMap.makeChildMap();
        
        // make sure loop iteration variables, etc are tracked
        List<Var> constructVars = c.constructDefinedVars();
        if (constructVars != null) {
          for (Var v: constructVars) {
            if (trackWrites(v)) {
              childWriteMap.put(v.name(), b);
            }
          }
        }
        
        // If we are waiting for var, don't hoist out past that
        if (c.getType() == ContinuationType.WAIT_STATEMENT) {
          for (Var waitVar: ((WaitStatement)c).getWaitVars()) {
            if (trackWrites(waitVar)) {
              childWriteMap.put(waitVar.name(), b);
            }
          }
        }
        
        if (hoistRec(logger, b, ancestors, childHoist, childLoopHoist,
                     childWriteMap)) {
          changed = true;
        }
      }
    }
    ancestors.remove(ancestors.size() - 1);
    return changed;
  }

  private static void updateMapWithWrites(Block curr,
          HierarchicalMap<String, Block> writeMap) {
    for (Instruction inst: curr.getInstructions()) {
      for (Var out: inst.getOutputs()) {
        if (trackWrites(out)) {
          Logging.getSTCLogger().trace("inst: " + inst + " tracking " + out);
          writeMap.put(out.name(), curr);
        } else {
          Logging.getSTCLogger().trace("inst: " + inst + " not tracking " + out);
        }
      }
    }
    
    // We can immediately do any array operations unless it is an alias,
    // e.g. for a nested array
    for (Var declared: curr.getVariables()) {
      if (Types.isArray(declared.type())) {
        writeMap.put(declared.name(), curr);
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

  private static boolean tryHoist(Logger logger, Block curr,
          List<Block> ancestors, int maxHoist, int maxLoopHoist,
          HierarchicalMap<String, Block> writeMap) {
    boolean changed = false;
    // See if we can lift any instructions out of block
    ListIterator<Instruction> it = curr.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (inst.hasSideEffects()) {
        // Don't try to mess with things with side-effects
        continue;
      }
      
      // See where the input variables were written
      // minDepth: how many blocks out can be hoisted
      int minDepth = -1;
      boolean canHoist = true;
      for (Arg in: inst.getInputs()) {
        if (in.isVar()) {
          int depth = writeMap.getDepth(in.getVar().name());
          if (depth < 0) {
            // Not written
            canHoist = false;
            break;
          } else if (minDepth < 0 || depth < minDepth) {
            minDepth = depth;
          }
        }
      }
      
      if (maybePiecewiseAssignment(inst)) {
        // Can't hoist array assignments, etc out through loop
        maxHoist = Math.min(maxHoist, maxLoopHoist);
      }
      
      if (canHoist) {
        // Max hoist for instruction determined by inputs and maxHoist
        int hoistDepth;
        if (minDepth < 0) {
          // Case where no variables
          hoistDepth = maxHoist;
        } else {
          hoistDepth = Math.min(maxHoist, minDepth);
        }

        if (hoistDepth > 0) {
          doHoist(logger, ancestors, curr, inst, it, hoistDepth, writeMap);
          changed = true;
        }
      }
    }
    return changed;
  }

  private static boolean maybePiecewiseAssignment(Instruction inst) {
    for (Var out: inst.getOutputs()) {
      if (Types.isArray(out.type())) {
        // TODO: only a problem if piecewise array assignment
        return true;
      }
    }
    return false;
  }

  private static void doHoist(Logger logger,
          List<Block> ancestors, Block curr,
          Instruction inst, ListIterator<Instruction> currInstIt,
          int hoistDepth, HierarchicalMap<String, Block> writeMap) {
    assert(hoistDepth > 0);
    assert(hoistDepth <= ancestors.size());
    Block target = ancestors.get(ancestors.size() - hoistDepth);
    logger.trace("Hoisting instruction up " + hoistDepth + " blocks: "
                 + inst.toString());
    assert(target != null);
    
    // Move the instruction
    currInstIt.remove();
    target.addInstruction(inst);
    
    // Move variable declaration if needed to outer block.
    relocateVarDefs(curr, target, ancestors, inst, hoistDepth);
    
    // need to update write map to reflect moved instruction
    for (Var out: inst.getOutputs()) {
      // Update map and parent maps
      writeMap.remove(out.name(), false);
      if (trackWrites(out)) {
        writeMap.put(out.name(), target, hoistDepth);
      }
    }
  }

  private static void relocateVarDefs(Block curr, Block target,
          List<Block> ancestors, Instruction inst, int hoistDepth) {
    // Rely on variable passing being fixed up later
    for (int i = 0; i < hoistDepth; i++) {
      Block ancestor;
      if (i == 0) {
        ancestor = curr;
      } else {
        int ancestorPos = ancestors.size() - i;
        ancestor = ancestors.get(ancestorPos);
      } 
      relocateVarDefs(ancestor, target, inst);
    }
  }

  /**
   * Relocate output variables to target block
   * @param source
   * @param target
   * @param inst
   */
  private static void relocateVarDefs(Block source, Block target,
      Instruction inst) {
    ListIterator<Var> varIt = source.variableIterator();
    while (varIt.hasNext()) {
      Var def = varIt.next();
      for (Var out: inst.getOutputs()) {
        if (def.name().equals(out.name())) {
          varIt.remove();
          target.addVariable(def);
          moveVarCleanupAction(out, source, target);
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
      if (ca.var().name().equals(var.name())) {
        actIt.remove();
        target.addCleanup(ca.var(), ca.action());
      }
    }
  }
}
