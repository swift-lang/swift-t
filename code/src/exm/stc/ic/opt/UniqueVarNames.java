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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class UniqueVarNames implements OptimizerPass {
  
  @Override
  public String getPassName() {
    return "Uniquify variable names";
  }
  
  @Override
  public String getConfigEnabledKey() {
    return null;
  }
  
  /**
   * Make all of variable names in functions completely
   * unique within the function
   * @param in
   */
  @Override
  public void optimize(Logger logger, Program in) {
    for (Function f: in.getFunctions()) {
      makeVarNamesUnique(f, in.getGlobalVars());
    }
  }

  /**
   * Make all names in block unique
   *
   * @param block
   * @param existing existing variables
   */
  private static void makeVarNamesUnique(Block block, Vars existing) {
    HashMap<Var, Arg> renames = new HashMap<Var, Arg>();
    for (Var v: block.getVariables()) {
      if (v.defType() == DefType.GLOBAL_CONST) {
        continue;
      }
      updateName(block, existing, renames, v);
    }
  
    // Rename variables in Block (and nested blocks) according to map
    block.renameVars(renames, false);
  
    // Recurse through nested blocks, making sure that all used variable
    // names are added to the usedNames
    for (Continuation c: block.getContinuations()) {
      makeVarNamesUnique(existing, c);
    }
  }

  private static String chooseNewName(Set<String> usedNames, Var v) {
    int counter = 1;
    String newName;
    // try x:1 x:2 x:3, etc until we find something
    do {
      newName = v.name() + ":" + counter;
      counter++;
    } while(usedNames.contains(newName));
    return newName;
  }

  private static void makeVarNamesUnique(Vars existing,
                                         Continuation cont) {
    // Update any continuation-defined vars
    List<Var> constructVars = cont.constructDefinedVars();
    if (!constructVars.isEmpty()) {
      HashMap<Var, Arg> renames = new HashMap<Var, Arg>();
      for (Var v: cont.constructDefinedVars()) {
        assert(cont.getBlocks().size() == 1) : "Assume continuation with " +
        		"construct defined vars has only one block";
        updateName(cont.getBlocks().get(0), existing, renames, v);
      }
      cont.replaceVars(renames, false, true);
    }
    
    for (Block b: cont.getBlocks()) {
      makeVarNamesUnique(b, existing);
    }
  }

  static void updateName(Block block, Vars existing,
          HashMap<Var, Arg> renames, Var var) {
    if (existing.usedNames.contains(var.name())) {
      String newName = chooseNewName(existing.usedNames, var);
      Var newVar = new Var(var.type(), newName,
                      var.storage(), var.defType(), var.mapping());
      renames.put(var, Arg.createVar(newVar));
      replaceCleanup(block, var, newVar);
      existing.addDeclaration(newVar);
    } else {
      existing.addDeclaration(var);
    }
  }

  static void replaceCleanup(Block block, Var var, Var newVar) {
    ListIterator<CleanupAction> it = block.cleanupIterator();
    while (it.hasNext()) {
      CleanupAction ca = it.next();
      if (ca.var().equals(var)) {
        it.set(new CleanupAction(newVar, ca.action()));
      }
    }
  }

  public static void makeVarNamesUnique(Function in,
            Collection<Var> globals) {
    Vars declarations = new Vars();
    for (Var global: globals) {
      declarations.addDeclaration(global);
    }
    for (Var v: in.getInputList()) {
      declarations.addDeclaration(v);
    }
    for (Var v: in.getOutputList()) {
      declarations.addDeclaration(v);
    }
  
    makeVarNamesUnique(in.getMainblock(), declarations);
  }
  
  private static class Vars {
    public final Set<String> usedNames = new HashSet<String>();
    public final Map<String, Var> vars = new HashMap<String, Var>();
    
    public void addDeclaration(Var var) {
      usedNames.add(var.name());
      vars.put(var.name(), var);
    }
  }
}
