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
    fixupProgram(logger, program);
  }

  /**
   * Fix up any variables missing from the usedVariables passed through
   * continuations. This is useful because it is easier to write other
   * optimizations if they are allowed to mess up the usedVariables
   */
  public static void fixupProgram(Logger logger, Program prog) {
    Set<String> referencedGlobals = new HashSet<String>();
    for (Function fn : prog.getFunctions()) {
      fixupFunction(logger, prog, fn, referencedGlobals);
    }
    
    removeUnusedGlobals(prog, referencedGlobals);
  }

  public static void fixupFunction(Logger logger, Program prog,
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
    HashSet<String> neededVars = fixupBlockRec(logger,
        fn, fn.getMainblock(), fnargs, referencedGlobals);
    // Check that all variables referred to are available as args
    neededVars.removeAll(Var.nameList(fn.getInputList()));
    neededVars.removeAll(Var.nameList(fn.getOutputList()));
  
    if (neededVars.size() > 0) {
      throw new STCRuntimeError("Reference in IC function "
          + fn.getName() + " to undefined variables " + neededVars.toString());
    }
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
  private static HashSet<String> fixupBlockRec(Logger logger,
      Function function, Block block, HierarchicalMap<String, Var> visible,
      Set<String> referencedGlobals) {

    // Remove global imports to be readded later if needed
    removeGlobalImports(block);
    

    // blockVars: variables defined in this block
    Set<String> blockVars = new HashSet<String>();
    
    // update block variables and visible variables
    for (Var v: block.getVariables()) {
      blockVars.add(v.name());
      visible.put(v.name(), v);
    }

    // Work out which variables are needed which aren't locally declared
    HashSet<String> neededVars = new HashSet<String>();
    block.findThisBlockUsedVars(neededVars);

    
    for (Continuation c : block.getContinuations()) {
      fixupContinuationRec(logger, function, c, visible, referencedGlobals,
              blockVars, neededVars);
    }

    neededVars.removeAll(blockVars);
    
    Set<String> globals = addGlobalImports(block, visible, neededVars);

    referencedGlobals.addAll(globals);
    neededVars.removeAll(globals);
    return neededVars;
  }

  /**
   * Update variable passing for nested continuation
   * @param logger
   * @param function
   * @param continuation
   * @param visible
   * @param referencedGlobals
   * @param outerBlockVars
   * @param neededVars
   */
  private static void fixupContinuationRec(Logger logger, Function function,
          Continuation continuation, HierarchicalMap<String, Var> visible,
          Set<String> referencedGlobals, Set<String> outerBlockVars,
          Set<String> neededVars) {
    // First see what variables the continuation defines inside itself
    List<Var> constructVars = continuation.constructDefinedVars();
    List<String> constructVarNames = null;
    if (constructVars != null) {
      constructVarNames = Var.nameList(constructVars);
    }
    
    for (Block innerBlock : continuation.getBlocks()) {
      HierarchicalMap<String, Var> childVisible = visible.makeChildMap();
      if (constructVars != null) {
        for (Var v : constructVars) {
          childVisible.put(v.name(), v);
        }
      }
      
      HashSet<String> innerNeededVars = fixupBlockRec(logger,
          function, innerBlock, childVisible, referencedGlobals);

      // construct will provide some vars
      if (constructVarNames != null) {
        innerNeededVars.removeAll(constructVarNames);
      }

      if (continuation.inheritsParentVars()) {
        // Might be some variables not yet defined in this scope
        innerNeededVars.removeAll(outerBlockVars);
        neededVars.addAll(innerNeededVars);
      } else {
        rebuildContinuationPassedVars(function, continuation, visible,
                outerBlockVars, neededVars, innerNeededVars);
      }
    }
  }

  private static void rebuildContinuationPassedVars(Function function,
          Continuation continuation, HierarchicalMap<String, Var> visibleVars,
          Set<String> outerBlockVars, Set<String> outerNeededVars,
          Set<String> innerNeededVars) {
    // Rebuild passed in vars
    continuation.clearPassedInVars();
    
    for (String needed: innerNeededVars) {
      if (!outerBlockVars.contains(needed)) {
        outerNeededVars.add(needed);
      }
      Var v = visibleVars.get(needed);
      if (v == null) {
        throw new STCRuntimeError("Variable " + needed
            + " should have been " + "visible but wasn't in "
            + function.getName());
      }
      continuation.addPassedInVar(v);
    }
  }

  private static void removeGlobalImports(Block block) {
    ListIterator<Var> varIt = block.variableIterator();
    while (varIt.hasNext()) {
      Var v = varIt.next();
      if (v.defType() == DefType.GLOBAL_CONST) {
        varIt.remove();
      }
    }
  }

  /**
   * 
   * @param block
   * @param visible
   * @param neededVars
   * @return set of global var names
   */
  private static Set<String> addGlobalImports(Block block,
          HierarchicalMap<String, Var> visible, Set<String> neededVars) {
    // if global constant missing, just add it
    Set<String> globals = new HashSet<String>();
    for (String needed: neededVars) {
      if (visible.containsKey(needed)) {
        Var v = visible.get(needed);
        if (v.storage() == VarStorage.GLOBAL_CONST) {
          // Add at top in case used as mapping var
          block.addVariable(v, true);
          globals.add(needed);
        }
      }
    }
    return globals;
  }

  private static void removeUnusedGlobals(Program prog, Set<String> referencedGlobals) {
    Set<String> globNames = new HashSet<String>(prog.getGlobalConsts().keySet());
    globNames.removeAll(referencedGlobals);
    for (String unused: globNames) {
      prog.removeGlobalConst(unused);
    }
  }

  public static void fixupVariablePassing(Logger logger, Program prog, Function fn) {
    fixupFunction(logger, prog, fn, new HashSet<String>());
  }
}
