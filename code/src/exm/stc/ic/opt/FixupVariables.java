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
import java.util.ListIterator;
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
import exm.stc.ic.tree.ICTree.CleanupAction;
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
   * @param visible all
   *          variables logically visible in this block. will be modified in fn
   * @param referencedGlobals updated with names of any globals used
   * @return
   */
  private static HashSet<String> fixupVariablePassing(Logger logger,
      Function function, Block block, HierarchicalMap<String, Var> visible,
      Set<String> referencedGlobals) {
    HashSet<String> availVars = new HashSet<String>();

    ListIterator<Var> varIt = block.variableIterator();
    while (varIt.hasNext()) {
      Var v = varIt.next();
      if (v.defType() == DefType.GLOBAL_CONST) {
        // Remove global imports to be readded later if needed
        varIt.remove();
      } else {
        availVars.add(v.name());
        visible.put(v.name(), v);
      }
    }

    HashSet<String> neededVars = new HashSet<String>();
    /*
     * Work out which variables are needed which aren't locally declared
     */
    
    for (Var var: block.getVariables()) {
      if (var.isMapped()) {
        String n = var.mapping().name();
        if (!availVars.contains(n)) {
          neededVars.add(n);
        }
      }
    }
    
    for (Instruction inst : block.getInstructions()) {
      updateNeededVars(inst, availVars, neededVars);
    }
    for (CleanupAction ca: block.getCleanups()) {
      updateNeededVars(ca.action(), availVars, neededVars);
    }
    
    for (Continuation c : block.getContinuations()) {
      updateNeededVars(c, availVars, neededVars);

      // First see what variables the continuation defines inside itself
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
            function, innerBlock, childVisible, referencedGlobals);

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
            assert(visible.containsKey(passedIn)) : "passedIn var " + passedIn
                  + " not visible in " + function.getName() + ".";
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
                    + " should have been " + "visible but wasn't in "
                    + function.getName());

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
          // Add at top in case used as mapping var
          block.addVariable(v, true);
          globals.add(needed);
          referencedGlobals.add(needed);
        }
      }
    }
    neededVars.removeAll(globals);

    return neededVars;
  }

  private static void updateNeededVars(Continuation c,
      HashSet<String> availVars, HashSet<String> neededVars) {
    // Next see what variables the continuation needs from the outer scope
    // (or from its own defined vars)
    for (Var v: c.requiredVars()) {
      if (!availVars.contains(v.name())) {
        neededVars.add(v.name());
      }
    }
  }

  private static void updateNeededVars(Instruction inst,
      HashSet<String> availVars, HashSet<String> neededVars) {
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

  /**
   * Fix up any variables missing from the usedVariables passed through
   * continuations. This is useful because it is easier to write other
   * optimizations if they are allowed to mess up the usedVariables
   */
  public static void fixupVariablePassing(Logger logger, Program prog) {
    Set<String> referencedGlobals = new HashSet<String>();
    for (Function fn : prog.getFunctions()) {
      fixupVariablePassing(logger, prog, fn, referencedGlobals);
    }
    
    removeUnusedGlobals(prog, referencedGlobals);
  }

  private static void removeUnusedGlobals(Program prog, Set<String> referencedGlobals) {
    Set<String> globNames = new HashSet<String>(prog.getGlobalConsts().keySet());
    globNames.removeAll(referencedGlobals);
    for (String unused: globNames) {
      prog.removeGlobalConst(unused);
    }
  }

  public static void fixupVariablePassing(Logger logger, Program prog, Function fn) {
    fixupVariablePassing(logger, prog, fn, new HashSet<String>());
  }
  
  public static void fixupVariablePassing(Logger logger, Program prog,
          Function fn, Set<String> referencedGlobals) {
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
        fn, fn.getMainblock(), fnargs, referencedGlobals);
    // Check that all variables referred to are available as args
    neededVars.removeAll(Var.nameList(fn.getInputList()));
    neededVars.removeAll(Var.nameList(fn.getOutputList()));

    if (neededVars.size() > 0) {
      throw new STCRuntimeError("Reference in IC function "
          + fn.getName() + " to undefined variables " + neededVars.toString());
    }
  }
}
