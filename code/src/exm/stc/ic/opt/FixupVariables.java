package exm.stc.ic.opt;

import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class FixupVariables implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Fixup variable passing";
  }

  @Override
  public String getConfigEnabledKey() {
    return null;
  }

  @Override
  public void optimize(Logger logger, Program program) {
    fixupVariablePassing(logger, program);
  }

  /**
   * 
   * @param logger
   * @param block
   * @param all
   *          variables logically visible in this block. will be modified in fn
   * @return
   */
  private static HashSet<String> fixupVariablePassing(Logger logger,
      Block block, HierarchicalMap<String, Var> visible) {
    HashSet<String> availVars = new HashSet<String>();
    for (Var v : block.getVariables()) {
      availVars.add(v.name());
      visible.put(v.name(), v);
    }

    HashSet<String> neededVars = new HashSet<String>();
    /*
     * Work out which variables are needed which aren't locally declared
     */
    for (Instruction inst : block.getInstructions()) {
      for (String n : Arg.varNameList(inst.getInputs())) {
        if (!availVars.contains(n)) {
          neededVars.add(n);
        }
      }
      for (Var v: inst.getOutputs()) {
        String n = v.name();
        if (!availVars.contains(n)) {
          neededVars.add(n);
        }
      }
    }
    for (Continuation c : block.getContinuations()) {
      // First see what variables the continuation needs from the outer scope
      for (Var v: c.requiredVars()) {
        if (!availVars.contains(v.name())) {
          neededVars.add(v.name());
        }
      }
      
      // Then see what variables the continuation defines inside itself
      List<Var> constructVars = c.constructDefinedVars();
      List<String> constructVarNames = null;
      if (constructVars != null) {
        constructVarNames = Var.nameList(constructVars);
      }

      for (Block innerBlock : c.getBlocks()) {
        HierarchicalMap<String, Var> childVisible = visible.makeChildMap();
        if (constructVars != null) {
          for (Var v : constructVars) {
            childVisible.put(v.name(), v);
          }
        }
        HashSet<String> innerNeededVars = fixupVariablePassing(logger,
            innerBlock, childVisible);

        // construct will provide some vars
        if (constructVars != null) {
          innerNeededVars.removeAll(constructVarNames);
        }

        boolean passedInAutomatically = c.inheritsParentVars();
        if (passedInAutomatically) {
          // Might be some variables not yet defined in this scope
          innerNeededVars.removeAll(availVars);
          neededVars.addAll(innerNeededVars);
        } else {
          Set<String> passedInVars = Var.nameSet(c.getPassedInVars());
          // Check all variables passed in are available
          // Check for redundant passing in, and any missing variables
          for (String passedIn : passedInVars) {
            if (!innerNeededVars.contains(passedIn)) {
              c.removePassedInVar(visible.get(passedIn));
            } else if (!availVars.contains(passedIn)) {
              neededVars.add(passedIn);
            }
          }
          for (String needed : innerNeededVars) {
            if (!availVars.contains(needed)) {
              neededVars.add(needed);
            }
            if (!passedInVars.contains(needed)) {
              Var v = visible.get(needed);
              if (v == null) {
                throw new STCRuntimeError("Variable " + needed
                    + " should have been " + "visible but wasn't");

              }
              c.addPassedInVar(v);
            }
          }
        }
      }
    }

    // if global constant missing, just add it
    Set<String> globals = new HashSet<String>();
    for (String needed : neededVars) {
      if (visible.containsKey(needed)) {
        Var v = visible.get(needed);
        if (v.storage() == VarStorage.GLOBAL_CONST) {
          block.addVariable(v);
          globals.add(needed);
        }
      }
    }
    neededVars.removeAll(globals);
    return neededVars;
  }

  /**
   * Fix up any variables missing from the usedVariables passed through
   * continuations. This is useful because it is easier to write other
   * optimizations if they are allowed to mess up the usedVariables
   */
  public static void fixupVariablePassing(Logger logger, Program prog) {
    for (Function fn : prog.getFunctions()) {
      fixupVariablePassing(logger, prog, fn);
    }
  }

  public static void fixupVariablePassing(Logger logger, Program prog,
          Function fn) {
    HierarchicalMap<String, Var> fnargs = new HierarchicalMap<String, Var>();
    for (Var v : fn.getInputList()) {
      fnargs.put(v.name(), v);
    }
    for (Var v : fn.getOutputList()) {
      fnargs.put(v.name(), v);
    }
    for (Entry<String, Arg> e : prog.getGlobalConsts().entrySet()) {
      Arg a = e.getValue();
      Var v = new Var(a.getType(), e.getKey(),
          VarStorage.GLOBAL_CONST, DefType.GLOBAL_CONST, null);
      fnargs.put(e.getKey(), v);
    }
    HashSet<String> neededVars = fixupVariablePassing(logger,
        fn.getMainblock(), fnargs);
    // Check that all variables referred to are available as args
    neededVars.removeAll(Var.nameList(fn.getInputList()));
    neededVars.removeAll(Var.nameList(fn.getOutputList()));

    if (neededVars.size() > 0) {
      throw new STCRuntimeError("Reference in IC function "
          + fn.getName() + " to undefined variables " + neededVars.toString());
    }
  }
}
