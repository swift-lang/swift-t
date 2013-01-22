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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * This module contains a set of optimisations that either seek out or
 * consolidate constant values in a Swift-IC function 
 *
 */
public class ConstantFold implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Constant folding";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_CONSTANT_FOLD;
  }

  /**
   * Perform constant folding and propagations on the program
   * NOTE: we assume that all variable names in a function are unique,
   * so the makeVarNamesUnique pass should be performed first
   * @param logger
   * @param in
   * @throws InvalidOptionException
   */
  @Override
  public void optimize(Logger logger, Program in) throws InvalidOptionException {
    HierarchicalMap<String, Arg> globalConsts = 
              new HierarchicalMap<String, Arg>();
    // Populate global constants
    globalConsts.putAll(in.getGlobalConsts());
    
    for (Function f: in.getFunctions()) {
      HashMap<String, Var> funVars = new HashMap<String, Var>();
      for (Var v: f.getInputList()) {
        funVars.put(v.name(), v);  
      }
      for (Var v: f.getOutputList()) {
        funVars.put(v.name(), v);
      }
      constantFold(logger, in, f, f.getMainblock(), funVars, 
                          globalConsts.makeChildMap());
    }
  }

  /**
   * Perform constant folding and propagation on a block and all of its children
   * @param logger
   * @param block
   * @param varMap variables known from outer scope
   * @return true if change made
   * @throws InvalidOptionException 
   */
  private static boolean constantFold(Logger logger, Program prog, 
      Function fn, Block block, 
      HashMap<String, Var> varMap,
      HierarchicalMap<String, Arg> knownConstants) throws InvalidOptionException {
    for (Var v: block.getVariables()) {
      varMap.put(v.name(), v);
    }
    
    // Find all constants in block
    findBlockConstants(logger, block, knownConstants, false, false);
    
    boolean changed = false; 
    boolean converged = false;
    while (!converged) {
      converged = true; // assume no changes
      // iterate over instructions in this block, find instructions which 
      //      take only constants as inputs 
      ListIterator<Instruction> it = block.instructionIterator();
      while (it.hasNext()) {
        Instruction inst = it.next();
        if (logger.isDebugEnabled()) {
          logger.debug("Candidate instruction for constant folding: " 
                                              + inst.toString());
        }
        Map<String, Arg> newConsts = inst.constantFold(fn.getName(),
                                            knownConstants);
        if (newConsts == null) {
          logger.debug("Couldn't constant fold");
          
          Instruction newInst = inst.constantReplace(knownConstants);
          if (newInst != null) {
            it.set(newInst);
          }
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Can replace instruction " + inst.toString() + 
                                                  " with constant");
          }
          converged = false;
          changed = true;
          knownConstants.putAll(newConsts);
          // replace with multiple set instructions
          ArrayList<Instruction> replacements = 
                    new ArrayList<Instruction>(newConsts.size());
          
          for (Entry<String, Arg> newConst: newConsts.entrySet()) {              
            String name = newConst.getKey();
            Var var = varMap.get(name);
            Arg newVal = newConst.getValue();
            logger.debug("New constant: " + name);
            if (Types.isScalarFuture(var.type())) {
              replacements.add(ICInstructions.futureSet(var, newVal));
            } else {
              assert(Types.isScalarValue(var.type()));
              replacements.add(ICInstructions.valueSet(var, newVal));
            }
          }
          ICUtil.replaceInsts(it, replacements);
        }
      }
      for (Continuation c: block.getContinuations()) {
        boolean updated = c.constantReplace(knownConstants);
        converged = converged && !updated;
        changed = changed || updated;
      }
      if (!converged) {
        logger.debug("Didn't converge, doing another constant folding pass");
      }
    }
    
    
    // Do it recursively on all child blocks.  We do this after doing the outer
    // block because more constants will have been propagated into inner block, 
    // enabled more folding
    for (Continuation c: block.getContinuations()) {
      for (Block b: c.getBlocks()) {
        // Make copy of constant map so that binds don't get mixed up
        boolean changedRec = constantFold(logger, prog, fn, b, varMap, 
                            knownConstants.makeChildMap());
        changed = changed || changedRec;
      }
    }
  
    if (Settings.getBoolean(Settings.OPT_BRANCH_PREDICT)) {
      branchPredict(block, knownConstants);
    }
  
    // Eliminate variables (only those which were declared in this block) 
    // where possible (this will catch unneeded variables created by 
    // constant folding but also some previously unneeded ones)
    if (Settings.getBoolean(Settings.OPT_DEAD_CODE_ELIM)) {
      if (changed) {
        DeadCodeEliminator.eliminate(logger, block);
      }
    }
    return changed;
  }

  /**
   * Find all the directly assigned constants in the block
   * @param logger
   * @param block
   * @param knownConstants
   * @param removeDefs if true, remove the set instructions as we go
   */
  static void findBlockConstants(Logger logger, Block block,
      Map<String, Arg> knownConstants, boolean removeLocalConsts,
      boolean ignoreLocalValConstants) {
    Set<String> removalCandidates = null;
    if (removeLocalConsts) {
      removalCandidates = new HashSet<String>();
      // Only remove variables defined in this scope: don't know how they
      // are used in other scopes
      for (Var v: block.getVariables()) {
        // Avoid removing alias variables as writes to them have side-effects
        if (v.storage() != VarStorage.ALIAS) {
          removalCandidates.add(v.name());
        }
      }
    }
      
    ListIterator<Instruction> it = block.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (inst.getInputs().size() == 1) {
        if (isValueStoreInst(inst, ignoreLocalValConstants)) {
          String varName = inst.getOutput(0).name();
          if ((!removeLocalConsts) || removalCandidates.contains(varName)) {
            logger.debug("Found constant " + varName);
            knownConstants.put(varName, inst.getInput(0));
            if (removeLocalConsts && !inst.hasSideEffects()) {
              logger.trace("Removing instruction " + inst.toString());
              it.remove();
            }
          }
        }
      }
    }
    if (removeLocalConsts) {
      block.removeVarDeclarations(knownConstants.keySet());
    }
  }

  /**
   * Return true if this instruction assigns a constant value to
   * a variable
   * @param inst
   * @param ignoreLocalValConstants
   * @return
   */
  private static boolean isValueStoreInst(Instruction inst,
      boolean ignoreLocalValConstants) {
    Arg input = inst.getInput(0);
    if (input.isConstant()) {
      if (inst.op == Opcode.STORE_INT || inst.op == Opcode.STORE_BOOL            
          || inst.op == Opcode.STORE_FLOAT || inst.op == Opcode.STORE_STRING
          || inst.op == Opcode.STORE_BLOB || inst.op == Opcode.STORE_VOID) {
        return true;
      } else if (!ignoreLocalValConstants && inst.op == Opcode.LOCAL_OP) {
        BuiltinOpcode op = ((Builtin)inst).subop;
        return (Operators.isCopy(op));
      }
    }
    return false;
  }

  static private class Predicted {
    final Continuation cont;
    final Block block;
    private Predicted(Continuation cont, Block block) {
      super();
      this.cont = cont;
      this.block = block;
    }
  }
  
  /**
   * Predict which way a branch will go based on known values of variables
   * in the program
   * @param block
   * @param knownConstants
   */
  private static void branchPredict(Block block,
      HierarchicalMap<String, Arg> knownConstants) {
    // Use list to preserve order
    List<Predicted> predictedBranches = new ArrayList<Predicted>();
    for (Continuation c: block.getContinuations()) {
      // With constants, we might be able to predict branches
      Block branch = c.branchPredict(knownConstants);
      if (branch != null) {
        predictedBranches.add(new Predicted(c, branch));
      }
    }
    for (Predicted p: predictedBranches) {
      p.cont.inlineInto(block, p.block);
    }
  }
}
