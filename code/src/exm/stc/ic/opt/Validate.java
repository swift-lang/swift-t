<<<<<<< HEAD
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
=======
package exm.stc.ic.opt;

import java.util.HashSet;
import java.util.List;
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
<<<<<<< HEAD
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICContinuations.ContVarDefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * Perform some sanity checks on intermediate code:
 * - Check variable names in block unique
 * - Check cleanups are in right place with block variables
 * - Check parent links are valid
 */
public class Validate implements OptimizerPass {
  private final boolean checkVarPassing;
  private final boolean checkCleanups;
  private final boolean noNestedBlocks;
  
  private Validate(boolean checkVarPassing,
                   boolean checkCleanups,
                   boolean noNestedBlocks) {
    super();
    this.checkVarPassing = checkVarPassing;
    this.checkCleanups = checkCleanups;
    this.noNestedBlocks = noNestedBlocks;
  }

  public static Validate standardValidator() {
    return new Validate(true, true, false);
  }
  
  /**
   * @returns validator for final form with additional refcounting
   *                    where we don't do cleanup location check or
   *                    variable passing check
   */
  public static Validate finalValidator() {
    return new Validate(false, false, false);
  }
  
=======
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class Validate implements OptimizerPass {

>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
  @Override
  public String getPassName() {
    return "Validate";
  }

  @Override
  public String getConfigEnabledKey() {
    return null;
  }

  @Override
  public void optimize(Logger logger, Program program) throws UserException {
<<<<<<< HEAD
    if (checkVarPassing) {
      // Check visibility of vars without modifying IC
      FixupVariables.fixupProgram(logger, program, false);
    }

    for (Function fn : program.getFunctions()) {
      checkParentLinks(logger, program, fn);
      checkUniqueVarNames(logger, program, fn);
      checkVarInit(logger, program, fn);
=======
    for (Function fn : program.getFunctions()) {
      checkParentLinks(logger, program, fn);
      checkUniqueVarNames(logger, program, fn);
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
    }
  }

  /**
<<<<<<< HEAD
   * Check that var names are unique within each function, and
   * that all references to variable have same attributes
=======
   * Check that var names are unique within each function
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
   * @param logger
   * @param program
   * @param fn
   */
  private void checkUniqueVarNames(Logger logger, Program program, Function fn) {
<<<<<<< HEAD
    Map<String, Var> declared = new HashMap<String, Var>();
    for (Var global: program.getGlobalVars()) {
      declared.put(global.name(), global);
    }
    
    for (Var in: fn.getInputList()) {
      declared.put(in.name(), in);
    }
    
    for (Var out: fn.getOutputList()) {
      declared.put(out.name(), out);
    }
    
    checkUniqueVarNames(logger, program, fn, fn.getMainblock(), declared);
  }

  private void checkUniqueVarNames(Logger logger, Program program, Function fn,
          Block block, Map<String, Var> declared) {
    for (Var v: block.getVariables()) {
      checkVarUnique(logger, fn, declared, v);
      if (v.isMapped()) {
        // Check that it refers to previously declared var
        assert(declared.containsKey(v.mapping().name()));
      }
    }
 
    checkVarReferences(logger, block, declared);
      
    if (checkCleanups)
      checkCleanups(fn, block);
    
    for (Continuation c: block.getContinuations()) {
      for (Var v: c.constructDefinedVars(ContVarDefType.NEW_DEF)) {
        checkVarUnique(logger, fn, declared, v);
      }
      for (Block inner: c.getBlocks()) { 
        checkUniqueVarNames(logger, program, fn, inner,
                            declared);
      }
    }
  }

  /**
   * Check that all variable objects refer to declared variables with
   * correct type, etc
   * @param logger
   * @param block
   * @param declared
   */
  private void checkVarReferences(Logger logger, Block block,
      Map<String, Var> declared) {
    for (Var v: block.getVariables()) {
      if (v.storage() == VarStorage.GLOBAL_CONST) {
        checkVarReference(declared, v, v);
      }
      if (v.isMapped()) {
        checkVarReference(declared, v.mapping(), v);
      }
    }
    for (Instruction inst: block.getInstructions()) {
      checkVarReferences(declared, inst);
    }
    
    for (Continuation c: block.getContinuations()) {
      for (Var v: c.requiredVars(false)) {
        checkVarReference(declared, v, c.getType());
      }
    }
    
    for (CleanupAction ca: block.getCleanups()) {
      checkVarReference(declared, ca.var(), ca);
      checkVarReferences(declared, ca.action());
    }
  }

  private void checkVarReferences(Map<String, Var> declared, Instruction inst) {
    for (Arg i: inst.getInputs()) {
      if (i.isVar()) {
        checkVarReference(declared, i.getVar(), inst);
      }
    }
    for (Var o: inst.getOutputs()) {
      checkVarReference(declared, o, inst);
    }
  }

  private void checkVarReference(Map<String, Var> declared, Var referencedVar,
                                 Object context) {
    assert(declared.containsKey(referencedVar.name())): referencedVar + " " + declared;
    Var declaredVar = declared.get(referencedVar.name());
    assert(referencedVar.identical(declaredVar)) : 
              context.toString() + " : " +
              declaredVar + " " + referencedVar + " | " +
              declaredVar.storage() + " " + referencedVar.storage() + " | " +
              declaredVar.defType() + " " + referencedVar.defType() + " | " +
              declaredVar.mapping() + " " + referencedVar.mapping();
  }

  private void checkCleanups(Function fn, Block block) {
    Set<Var> blockVars = new HashSet<Var>(block.getVariables());
    
    if (block.getType() == BlockType.MAIN_BLOCK) {
      // Cleanup actions for args valid in outer block of function
      blockVars.addAll(fn.getInputList());
      blockVars.addAll(fn.getOutputList());
    } else {
      // Clean actions can refer to construct defined vars
      List<Var> constructVars = block.getParentCont().constructDefinedVars();
      blockVars.addAll(constructVars);
    }
    
    
    for (CleanupAction ca: block.getCleanups()) {
      if (!blockVars.contains(ca.var())) {
        if (ca.action().op != Opcode.DECR_WRITERS) {
          // TODO: workaround to avoid eliminating functional code
          throw new STCRuntimeError("Cleanup action for var not defined in " +
              "block: " + ca.var() + " in function " + fn.getName() + ". " +
              " Valid variables are: " + blockVars); 
        }
=======
    Set<String> usedNames = new HashSet<String>();
    usedNames.addAll(program.getGlobalConsts().keySet());
    usedNames.addAll(Var.nameList(fn.getInputList()));
    usedNames.addAll(Var.nameList(fn.getOutputList()));
    checkUniqueVarNames(logger, program, fn, fn.getMainblock(), usedNames);
  }

  private void checkUniqueVarNames(Logger logger, Program program, Function fn,
          Block block, Set<String> usedNames) {
    for (Var v: block.getVariables()) {
      checkVarUnique(logger, program, fn, usedNames, v);
    }
    
    for (Continuation c: block.getContinuations()) {
      List<Var> constructDefined = c.constructDefinedVars();
      if (constructDefined != null) {
        for (Var v: constructDefined) {
          checkVarUnique(logger, program, fn, usedNames, v);
        }
      }
      for (Block inner: c.getBlocks()) { 
        checkUniqueVarNames(logger, program, fn, inner,
                            usedNames);
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
      }
    }
  }

  private void checkVarUnique(Logger logger, 
<<<<<<< HEAD
          Function fn, Map<String, Var> declared, Var var) {
    if (var.defType() == DefType.GLOBAL_CONST) {
      Var declaredGlobal = declared.get(var.name());
      if (declaredGlobal == null) { 
        throw new STCRuntimeError("Missing global constant: " + var.name());
      } else {
        // Check that definitions matcch
        assert(declaredGlobal.identical(var));
      }
    } else {
      if (declared.containsKey(var.name()))
        throw new STCRuntimeError("Duplicate variable name "
                + var.name() + " in function " + fn.getName());
    }
    declared.put(var.name(), var);
=======
          Program program, Function fn,
          Set<String> usedNames, Var var) {
    if (var.defType() == DefType.GLOBAL_CONST) {
      if (program.lookupGlobalConst(var.name()) == null) 
        throw new STCRuntimeError("Missing global constant: " + var.name());
    } else {
      if (usedNames.contains(var))
        throw new STCRuntimeError("Duplicate variable name "
                + var.name() + " in function " + fn.getName());
    }
    usedNames.add(var.name());
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
  }

  /**
   * Check that parent links are valid
   * @param logger
   * @param program
   * @param fn
   */
  private void checkParentLinks(Logger logger, Program program, Function fn) {
    Block mainBlock = fn.getMainblock();
    assert(mainBlock.getType() == BlockType.MAIN_BLOCK);
    checkParentLinks(logger, program, fn, mainBlock);
  }
  
<<<<<<< HEAD
  private void checkParentLinks(Logger logger, Program prog,
=======
  private static void checkParentLinks(Logger logger, Program prog,
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
          Function fn, Block block) {
    Function fn2 = block.getParentFunction();
    assert(fn2 == fn) : 
      "Parent function should be " + fn.getName() + " but was "
      + (fn2 == null ? null : fn2.getName());
    
    for (Continuation c: block.getContinuations()) {
<<<<<<< HEAD
      if (noNestedBlocks && c.getType() == ContinuationType.NESTED_BLOCK) {
        throw new STCRuntimeError("Nested block present");
      }
      
=======
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
      assert(c.parent() == block) : "Bad continuation parent for " + c 
        + "\n\n\nis " + c.parent()
        + "\n\n\nbut should be: " + block;
      for (Block innerBlock: c.getBlocks()) {
        assert(innerBlock.getType() != BlockType.MAIN_BLOCK);
        assert(innerBlock.getParentCont() == c) : 
          "Bad parent for block of type " + innerBlock.getType() 
                           + "\n" + innerBlock 
                           + "\n\n\nis " + innerBlock.getParentCont()
                           + "\n\n\nbut should be: " + c;
      }
    }
  }
<<<<<<< HEAD

  /**
   * Check alias and vars are initialized before being read. 
   * 
   * @param logger
   * @param program
   * @param fn
   * @param block
   */
  private void checkVarInit(Logger logger, Program program,
      Function fn) {
    HierarchicalSet<Var> initVars = new HierarchicalSet<Var>();
    for (Var v: fn.getInputList()) {
      if (varMustBeInitialized(v)) {
        initVars.add(v);
      }
    }
    checkAliasVarUsageRec(logger, program, fn, fn.getMainblock(), initVars);
  }
  
  private boolean varMustBeInitialized(Var v) {
    return v.storage() == VarStorage.ALIAS ||
           v.storage() == VarStorage.LOCAL ||
           Types.isScalarUpdateable(v.type()); 
  }
  
  private void checkAliasVarUsageRec(Logger logger, Program program,
      Function fn, Block block, HierarchicalSet<Var> initVars) {
    for (Var v: block.getVariables()) {
      if (v.isMapped() && varMustBeInitialized(v.mapping())) {
        assert (initVars.contains(v.mapping())):
            v + " mapped to uninitialized var " + v.mapping();
      }
    }
    
    for (Instruction inst: block.getInstructions()) {
      updateInitVars(inst, initVars);
    }
    
    for (Continuation c: block.getContinuations()) {
      for (Var v: c.requiredVars(false)) {
        checkInitialized(c.getType(), initVars, v);
      }
      if (c.isAsync()) {
        for (PassedVar pv: c.getPassedVars()) {
          checkInitialized(c.getType(), initVars, pv.var);
        }
      }
      
      HierarchicalSet<Var> contInit = initVars.makeChild();
      for (Var v: c.constructDefinedVars()) {
        if (varMustBeInitialized(v)) {
          contInit.add(v);
        }
      }
      for (Block inner: c.getBlocks()) {
        checkAliasVarUsageRec(logger, program, fn, inner,
            contInit.makeChild());
      }
    }
  }

  private void updateInitVars(Instruction inst, HierarchicalSet<Var> initVars) {
    for (Arg in: inst.getInputs()) {
      if (in.isVar() && varMustBeInitialized(in.getVar())
          && !initVars.contains(in.getVar())) {
        throw new STCRuntimeError("Var " + in + " was an uninitialized " +
            " var read in instruction " + inst);
      }
    }
    List<Var> regularOutputs = inst.getOutputs();
    List<Var> initializedAliases = inst.getInitializedAliases();
    List<Var> initializedUpdateables = inst.getInitializedUpdateables();
    
    if (initializedAliases.size() > 0 || initializedUpdateables.size() > 0) {
      regularOutputs = new ArrayList<Var>(regularOutputs);
      for (List<Var> initList: Arrays.asList(initializedAliases,
                                             initializedUpdateables)) {
        for (Var init: initList) {
          assert(init.storage() == VarStorage.ALIAS ||
                 Types.isScalarUpdateable(init.type())) : inst + " " + init;
          ICUtil.remove(regularOutputs, init);
          if (initVars.contains(init)) {
            throw new STCRuntimeError("double initialized variable " + init);
          }
          initVars.add(init);
        }
      }
    }
    
    for (Var out: regularOutputs) {
      if (out.storage() == VarStorage.LOCAL) {
        if (initVars.contains(out)) {
          throw new STCRuntimeError("double initialized variable " + out);
        }
        initVars.add(out);
      }
    }

    for (Var regularOut: regularOutputs) {
      checkInitialized(inst, initVars, regularOut);
    }
  }

  private void checkInitialized(Object context,
      HierarchicalSet<Var> initVars, Var var) {
    if (varMustBeInitialized(var) &&
        !initVars.contains(var)) {
      throw new STCRuntimeError("Uninitialized alias " +
                    var + " in " + context.toString());
    }
  }
=======
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
}
