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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class Validate implements OptimizerPass {

  private final boolean checkCleanups;
  
  private Validate(boolean checkCleanups) {
    super();
    this.checkCleanups = checkCleanups;
  }

  public static Validate standardValidator() {
    return new Validate(true);
  }
  
  /**
   * @returns validator for final form with additional refcounting
   */
  public static Validate finalValidator() {
    return new Validate(false);
  }
  
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
    for (Function fn : program.getFunctions()) {
      checkParentLinks(logger, program, fn);
      checkUniqueVarNames(logger, program, fn);
    }
  }

  /**
   * Check that var names are unique within each function
   * @param logger
   * @param program
   * @param fn
   */
  private void checkUniqueVarNames(Logger logger, Program program, Function fn) {
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
    if (checkCleanups)
      checkCleanups(fn, block);
    
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
      }
    }
  }

  private void checkCleanups(Function fn, Block block) {
    Set<String> blockVarNames = Var.nameSet(block.getVariables());
    
    if (block.getType() == BlockType.MAIN_BLOCK) {
      // Cleanup actions for args valid in outer block of function
      blockVarNames.addAll(Var.nameList(fn.getInputList()));
      blockVarNames.addAll(Var.nameList(fn.getOutputList()));
    } else {
      // Clean actions can refer to construct defined vars
      List<Var> constructVars = block.getParentCont().constructDefinedVars();
      if (constructVars != null) {
        blockVarNames.addAll(
            Var.nameList(constructVars));
      }
    }
    
    
    for (CleanupAction ca: block.getCleanups()) {
      if (!blockVarNames.contains(ca.var().name())) {
        if (ca.action().op != Opcode.DECR_WRITERS) {
          // TODO: workaround to avoid eliminating functional code
          throw new STCRuntimeError("Cleanup action for var not defined in " +
              "block: " + ca.var() + " in function " + fn.getName() + ". " +
              " Valid variables are: " + blockVarNames); 
        }
      }
    }
  }

  private void checkVarUnique(Logger logger, 
          Program program, Function fn,
          Set<String> usedNames, Var var) {
    if (var.defType() == DefType.GLOBAL_CONST) {
      if (program.lookupGlobalConst(var.name()) == null) 
        throw new STCRuntimeError("Missing global constant: " + var.name());
    } else {
      if (usedNames.contains(var.name()))
        throw new STCRuntimeError("Duplicate variable name "
                + var.name() + " in function " + fn.getName());
    }
    usedNames.add(var.name());
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
  
  private static void checkParentLinks(Logger logger, Program prog,
          Function fn, Block block) {
    Function fn2 = block.getParentFunction();
    assert(fn2 == fn) : 
      "Parent function should be " + fn.getName() + " but was "
      + (fn2 == null ? null : fn2.getName());
    
    for (Continuation c: block.getContinuations()) {
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
}
