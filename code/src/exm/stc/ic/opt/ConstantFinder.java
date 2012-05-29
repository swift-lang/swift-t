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

import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.Variable.DefType;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.ic.ICInstructions;
import exm.stc.ic.ICUtil;
import exm.stc.ic.ICContinuations.Continuation;
import exm.stc.ic.ICInstructions.Instruction;
import exm.stc.ic.ICInstructions.LocalBuiltin;
import exm.stc.ic.ICInstructions.Oparg;
import exm.stc.ic.ICInstructions.Opcode;
import exm.stc.ic.SwiftIC.Block;
import exm.stc.ic.SwiftIC.CompFunction;
import exm.stc.ic.SwiftIC.Program;

/**
 * This module contains a set of optimisations that either seek out or
 * consolidate constant values in a Swift-IC function 
 *
 */
public class ConstantFinder {

  /**
   * Perform constant folding and propagations on the program
   * NOTE: we assume that all variable names in a composite function are unique,
   * so the makeVarNamesUnique pass should be performed first
   * @param logger
   * @param in
   * @return
   * @throws InvalidOptionException
   */
  public static Program constantFold(Logger logger, Program in) throws InvalidOptionException {
    HierarchicalMap<String, Oparg> globalConsts = 
              new HierarchicalMap<String, Oparg>();
    // Populate global constants
    globalConsts.putAll(in.getGlobalConsts());
    
    for (CompFunction f: in.getComposites()) {
      HashMap<String, Variable> funVars = new HashMap<String, Variable>();
      for (Variable v: f.getInputList()) {
        funVars.put(v.getName(), v);  
      }
      for (Variable v: f.getOutputList()) {
        funVars.put(v.getName(), v);
      }
      constantFold(logger, in, f, f.getMainblock(), funVars, 
                          globalConsts.makeChildMap());
    }
    return in;
  }

  /**
   * Perform constant folding and propagation on a block and all of its children
   * @param logger
   * @param block
   * @param varMap variables known from outer scope
   * @throws InvalidOptionException 
   */
  private static void constantFold(Logger logger, Program prog, 
      CompFunction fn, Block block, 
      HashMap<String, Variable> varMap,
      HierarchicalMap<String, Oparg> knownConstants) throws InvalidOptionException {
    for (Variable v: block.getVariables()) {
      varMap.put(v.getName(), v);
    }
    
    // Find all constants in block
    findBlockConstants(logger, block, knownConstants, false, false);
    
    boolean converged = false;
    while (!converged) {
      converged = true; // assume no changes
      // iterate over instructions in this block, find instructions which 
      //      take only constants as inputs 
      ListIterator<Instruction> it = block.instructionIterator();
      while (it.hasNext()) {
        Instruction inst = it.next();
        logger.debug("Candidate instruction for constant folding: " 
                                              + inst.toString());
        Map<String, Oparg> newConsts = inst.constantFold(fn.getName(),
                                            knownConstants);
        if (newConsts == null) {
          logger.debug("Couldn't constant fold");
          Instruction newInst = inst.constantReplace(knownConstants);
          if (newInst != null) {
            it.set(newInst);
          }
        } else {
          logger.debug("Can replace instruction " + inst.toString() + 
                                                  " with constant");
          converged = false;
          knownConstants.putAll(newConsts);
          // replace with multiple set instructions
          ArrayList<Instruction> replacements = 
                    new ArrayList<Instruction>(newConsts.size());
          
          for (Entry<String, Oparg> newConst: newConsts.entrySet()) {              
            String name = newConst.getKey();
            Variable var = varMap.get(name);
            Oparg newVal = newConst.getValue();
            logger.debug("New constant: " + name);
            if (Types.isScalarFuture(var.getType())) {
              replacements.add(ICInstructions.futureSet(var, newVal));
            } else {
              assert(Types.isScalarValue(var.getType()));
              replacements.add(ICInstructions.valueSet(var, newVal));
            }
          }
          ICUtil.replaceInsts(it, replacements);
        }
      }
      for (Continuation c: block.getContinuations()) {
        boolean updated = c.constantReplace(knownConstants);
        converged = converged && !updated;
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
        constantFold(logger, prog, fn, b, varMap, 
                    knownConstants.makeChildMap());
      }
    }
  
    if (Settings.getBoolean(Settings.OPT_BRANCH_PREDICT)) {
      branchPredict(block, knownConstants);
    }
  
    // Eliminate variables (only those which were declared in this block) 
    // where possible (this will catch unneeded variables created by 
    // constant folding but also some previously unneeded ones)
    if (Settings.getBoolean(Settings.OPT_DEAD_CODE_ELIM)) {
      DeadCodeEliminator.eliminate(logger, block);
    }
  }

  /**
   * Find all the directly assigned constants in the block
   * @param logger
   * @param block
   * @param knownConstants
   * @param removeDefs if true, remove the set instructions as we go
   */
  private static void findBlockConstants(Logger logger, Block block,
      Map<String, Oparg> knownConstants, boolean removeLocalConsts,
      boolean ignoreLocalValConstants) {
    Set<String> removalCandidates = null;
    if (removeLocalConsts) {
      removalCandidates = new HashSet<String>();
      // Only remove variables defined in this scope: don't know how they
      // are used in other scopes
      for (Variable v: block.getVariables()) {
        // Avoid removing alias variables as writes to them have side-effects
        if (v.getStorage() != VariableStorage.ALIAS) {
          removalCandidates.add(v.getName());
        }
      }
    }
      
    ListIterator<Instruction> it = block.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (inst.getInputs().size() == 1) {
        Oparg input = inst.getInput(0);
        if (input.isConstant() &&
            (inst.op == Opcode.STORE_INT || inst.op == Opcode.STORE_BOOL            
            || inst.op == Opcode.STORE_FLOAT || inst.op == Opcode.STORE_STRING
            || (!ignoreLocalValConstants && inst.op == Opcode.LOCAL_OP && 
                  LocalBuiltin.isValueCopy(inst)))) {
          Oparg output = inst.getOutput(0); 
          String varName = output.getVar().getName();
          if ((!removeLocalConsts) || removalCandidates.contains(varName)) {
            logger.debug("Found constant " + varName);
            knownConstants.put(varName, input);
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
  public static void branchPredict(Block block,
      HierarchicalMap<String, Oparg> knownConstants) {
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

  /**
   * Consolidate all futures which are initialized with a constant
   * value into a global constants area, to avoid reinitializing them.
   * If the same constant appears in multiple locations, we consolidate them
   * into one
   * @param logger
   * @param prog
   * @throws InvalidOptionException
   */
  public static void makeConstantsGlobal(Logger logger, Program prog) 
                                      throws InvalidOptionException {
    for (CompFunction f: prog.getComposites()) {
      makeConstantsGlobal(logger, prog, f.getMainblock());
          
    }
  }

  /* Lift constants up to global scope to avoid reinitializing and 
   * duplication of constants
   */
  private static void makeConstantsGlobal(Logger logger, Program prog,
            Block block) throws InvalidOptionException {   
    // Find the remaining constant futures and delete assignments to them
    logger.debug("Making constant futures shared globals");
    HashMap<String, Variable> localDeclsOfGlobalVars = 
          new HashMap<String, Variable>();
    HashMap<String, Oparg> knownConstants = new HashMap<String, Oparg>();
    
    findBlockConstants(logger, block, knownConstants, true, true);
    
    HashMap<String, Oparg> globalReplacements = 
                            new HashMap<String, Oparg>();
                  
    for (Entry<String, Oparg> c: knownConstants.entrySet()) {
      String oldName = c.getKey();
      // Remove from this block's variable entries 
      
      Oparg val = c.getValue();
      Variable glob = null;
      String globName = prog.invLookupGlobalConst(val);
      if (globName == null) {
        // Add new global constant
        globName = prog.addGlobalConst(val);
      } else { 
        glob = localDeclsOfGlobalVars.get(globName);
      }
      if (glob == null) {
        glob = block.declareVariable(val.getSwiftType(), globName, 
                    VariableStorage.GLOBAL_CONST, DefType.GLOBAL_CONST,
                    null);
        localDeclsOfGlobalVars.put(globName, glob);
      }
      globalReplacements.put(oldName, Oparg.createVar(glob));
    }
    block.renameVars(globalReplacements, false);
    
    // Do this recursively for child blocks
    for (Continuation c: block.getContinuations()) {
      for (Block childBlock: c.getBlocks()) {
        // We could pass in localDeclsOfGlobalVars, but
        // it doesn't matter if global vars are redeclared in inner scope
        makeConstantsGlobal(logger, prog, childBlock);
      }
    }
    
    // Remove now redundant local constants
    if (Settings.getBoolean(Settings.OPT_DEAD_CODE_ELIM)) {
      DeadCodeEliminator.eliminate(logger, block);
    }
  }

}
