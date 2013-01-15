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
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class Validate implements OptimizerPass {

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
