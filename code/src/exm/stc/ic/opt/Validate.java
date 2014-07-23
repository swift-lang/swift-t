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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.ic.tree.ICContinuations.ContVarDefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalConstants;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.TurbineOp.RefCountOp;

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
  private final boolean checkExecContext;


  private Validate(boolean checkVarPassing,
                   boolean checkCleanups,
                   boolean noNestedBlocks,
                   boolean checkExecContext) {
    super();
    this.checkVarPassing = checkVarPassing;
    this.checkCleanups = checkCleanups;
    this.noNestedBlocks = noNestedBlocks;
    this.checkExecContext = checkExecContext;
  }

  public static Validate standardValidator() {
    return new Validate(true, true, false, true);
  }

  /**
   * @returns validator for final form with additional refcounting
   *                    where we don't do cleanup location check or
   *                    variable passing check
   */
  public static Validate finalValidator() {
    return new Validate(false, false, false, true);
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
    if (checkVarPassing) {
      // Check visibility of vars without modifying IC
      FixupVariables.fixupProgram(logger, program, false);
    }

    for (Function fn : program.getFunctions()) {
      checkParentLinks(logger, program, fn);
      checkUniqueVarNames(logger, program.constants(), fn);
      InitVariables.checkVarInit(logger, fn);
      if (checkExecContext) {
        checkExecCx(logger, program, fn);
      }
    }
  }
  /**
   * Check that var names are unique within each function, and
   * that all references to variable have same attributes
   * @param logger
   * @param program
   * @param fn
   */
  private void checkUniqueVarNames(Logger logger, GlobalConstants constants,
        Function fn) {
    Map<String, Var> declared = new HashMap<String, Var>();
    for (Var consts: constants.vars()) {
      declared.put(consts.name(), consts);
    }

    for (Var in: fn.getInputList()) {
      declared.put(in.name(), in);
    }

    for (Var out: fn.getOutputList()) {
      declared.put(out.name(), out);
    }

    checkUniqueVarNames(logger, fn, fn.mainBlock(), declared);
  }

  private void checkUniqueVarNames(Logger logger,
      Function fn, Block block, Map<String, Var> declared) {
    for (Var v: block.getVariables()) {
      checkVarUnique(logger, fn, declared, v);
    }

    checkVarReferences(logger, fn, block, declared);

    if (checkCleanups)
      checkCleanups(logger, fn, block);

    for (Continuation c: block.getContinuations()) {
      for (Var v: c.constructDefinedVars(ContVarDefType.NEW_DEF)) {
        checkVarUnique(logger, fn, declared, v);
      }
      for (Block inner: c.getBlocks()) {
        checkUniqueVarNames(logger, fn, inner, declared);
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
  private void checkVarReferences(Logger logger, Function f,
      Block block, Map<String, Var> declared) {
    for (Var v: block.getVariables()) {
      if (v.storage() == Alloc.GLOBAL_CONST) {
        checkVarReference(f, declared, v, v);
      }
    }
    for (Statement stmt: block.getStatements()) {
      checkVarReferences(f, declared, stmt);
    }

    for (Continuation c: block.getContinuations()) {
      checkVarReferencesCont(f, declared, c);
    }

    for (CleanupAction ca: block.getCleanups()) {
      checkVarReference(f, declared, ca.var(), ca);
      checkVarReferencesInstruction(f, declared, ca.action());
    }
  }

  private void checkVarReferences(Function f, Map<String, Var> declared,
                                  Statement stmt) {
    switch (stmt.type()) {
      case INSTRUCTION:
        checkVarReferencesInstruction(f, declared, stmt.instruction());
        break;
      case CONDITIONAL:
        checkVarReferencesCont(f, declared, stmt.conditional());
        break;
      default:
        throw new STCRuntimeError("Unknown statement type " + stmt.type());
    }
  }

  private void checkVarReferencesCont(Function f, Map<String, Var> declared,
                                      Continuation c) {
    for (Var v: c.requiredVars(false)) {
      checkVarReference(f, declared, v, c.getType());
    }
  }

  private void checkVarReferencesInstruction(Function f,
          Map<String, Var> declared, Instruction inst) {
    for (Arg i: inst.getInputs()) {
      if (i.isVar()) {
        checkVarReference(f, declared, i.getVar(), inst);
      }
    }
    for (Var o: inst.getOutputs()) {
      checkVarReference(f, declared, o, inst);
    }
  }

  private void checkVarReference(Function f, Map<String, Var> declared,
                                 Var referencedVar, Object context) {
    assert(declared.containsKey(referencedVar.name())): referencedVar +
                              " not among declared vars in scope: " + declared;
    Var declaredVar = declared.get(referencedVar.name());
    assert(referencedVar.identical(declaredVar)) :
              context.toString() + " : " +
              declaredVar + " " + referencedVar + " | " +
              declaredVar.storage() + " " + referencedVar.storage() + " | " +
              declaredVar.defType() + " " + referencedVar.defType() + " | " +
              declaredVar.mappedDecl() + " " + referencedVar.mappedDecl();
    checkUsed(f, referencedVar);
  }

  private void checkCleanups(Logger logger, Function fn, Block block) {
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
      // Don't expect these operations to appear yet
      if (RefCountOp.isRefcountOp(ca.action().op)) {
        throw new STCRuntimeError("Didn't expect to see reference " +
        		                        "counting operations yet: " + ca);
      }
      // TODO: might indicate error sometimes?
      if (!blockVars.contains(ca.var())) {
        logger.debug("Cleanup action for var not defined in " +
            "block: " + ca.var() + " in function " + fn.getName() + ". " +
            " Valid variables are: " + blockVars);
      }
    }
  }

  private void checkVarUnique(Logger logger,
          Function fn, Map<String, Var> declared, Var var) {
    checkUsed(fn, var);
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
  }

  private void checkUsed(Function fn, Var var) {
    assert(var.storage() == Alloc.GLOBAL_CONST || fn.varNameUsed(var.name())) :
          "Variable name not marked as used " + var + ".\n" +
          fn.usedVarNames();
  }

  /**
   * Check that parent links are valid
   * @param logger
   * @param program
   * @param fn
   */
  private void checkParentLinks(Logger logger, Program program, Function fn) {
    Block mainBlock = fn.mainBlock();
    assert(mainBlock.getType() == BlockType.MAIN_BLOCK);
    checkParentLinksRec(logger, program, fn, mainBlock);
  }

  private void checkParentLinksRec(Logger logger, Program prog,
          Function fn, Block block) {
    Function fn2 = block.getParentFunction();
    assert(fn2 == fn) :
      "Parent function should be " + fn.getName() + " but was "
      + (fn2 == null ? null : fn2.getName());

    for (Continuation c: block.allComplexStatements()) {
      if (noNestedBlocks && c.getType() == ContinuationType.NESTED_BLOCK) {
        throw new STCRuntimeError("Nested block present");
      }

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
        checkParentLinksRec(logger, prog, fn, innerBlock);
      }
    }
  }



  private void checkExecCx(Logger logger, Program program, Function fn) {
    checkExecCx(logger, program, fn.mainBlock(), ExecContext.control());
  }

  private void checkExecCx(Logger logger, Program program,
      Block block, ExecContext execCx) {

    for (Statement stmt: block.getStatements()) {
      switch (stmt.type()) {
        case INSTRUCTION:
          ExecTarget mode = stmt.instruction().execMode();
          assert(mode.canRunIn(execCx)) : stmt + " has execution "
                + "mode: " + mode + " but is in context " + execCx;
          break;
        case CONDITIONAL:
          checkExecCxRecurse(logger, program, execCx, stmt.conditional());
          break;
        default:
          throw new STCRuntimeError("Unexpected: " + stmt.type());
      }
    }

    for (Continuation c: block.getContinuations()) {
      checkExecCxRecurse(logger, program, execCx, c);
    }

  }

  private void checkExecCxRecurse(Logger logger, Program program,
      ExecContext execCx, Continuation cont) {
    for (Block branch: cont.getBlocks()) {
      checkExecCx(logger, program, branch, cont.childContext(execCx));
    }
  }

}
