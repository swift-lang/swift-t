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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.ic.tree.ICContinuations.ContVarDefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;

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
      List<Var> allGlobals = new ArrayList<Var>();
      allGlobals.addAll(in.constants().vars());
      allGlobals.addAll(in.globalVars().vars());

      makeVarNamesUnique(f, allGlobals);
    }
  }

  /**
   * Make all names in block unique
   *
   * @param block
   * @param existing existing variables
   */
  private static void makeVarNamesUnique(Function fn,
      Block block, Vars existing, HierarchicalMap<Var, Arg> renames) {
    for (Var v: block.getVariables()) {
      if (!v.defType().isGlobal()) {
        updateName(fn, block, existing, renames, v);
      }
    }

    if (!renames.isEmpty()) {
      // Rename variables in Block (and nested blocks) according to map
      block.renameVars(fn.getName(), renames, RenameMode.REPLACE_VAR, false);
    }

    // Recurse through nested blocks, making sure that all used variable
    // names are added to the usedNames
    for (Continuation c: block.allComplexStatements()) {
      makeVarNamesUnique(fn, existing, c, renames.makeChildMap());
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

  private static void makeVarNamesUnique(Function fn, Vars existing,
                 Continuation cont, HierarchicalMap<Var, Arg> renames) {
    // Update any continuation-defined vars
    List<Var> constructNewDefinedVars =
                      cont.constructDefinedVars(ContVarDefType.NEW_DEF);
    for (Var v: constructNewDefinedVars) {
      assert(cont.getBlocks().size() == 1) : "Assume continuation with " +
      		"construct defined vars has only one block";
      HashMap<Var, Arg> contVarRenames = new HashMap<Var, Arg>();
      updateName(fn, cont.getBlocks().get(0), existing, contVarRenames, v);

      if (contVarRenames.size() > 0) {
        // Update construct vars as required
        cont.renameVars(fn.getName(), contVarRenames, RenameMode.REPLACE_VAR,
                        false);
        renames.putAll(contVarRenames);
      }
    }
    fixupVarRedefines(fn, existing, cont, renames);

    for (Block b: cont.getBlocks()) {
      makeVarNamesUnique(fn, b, existing, renames.makeChildMap());
    }
  }

  /**
   * Handle the case where frontend generated intermediate representation
   * where a construct redefines the value of a variable present outside
   *
   * @param existing
   * @param cont
   * @param renames
   */
  private static void fixupVarRedefines(Function fn, Vars existing,
        Continuation cont, HierarchicalMap<Var, Arg> renames) {
    for (Var redef: cont.constructDefinedVars(ContVarDefType.REDEF)) {
      HashMap<Var, Arg> contVarRenames = new HashMap<Var, Arg>();
      updateName(fn, cont.getBlocks().get(0), existing, contVarRenames, redef);

      if (contVarRenames.size() > 0) {
        // Update construct vars as required
        assert(contVarRenames.size() == 1 &&
            contVarRenames.containsKey(redef));
        Var repl = contVarRenames.get(redef).getVar();
        Logging.getSTCLogger().debug("Replaced " + redef + " with " + repl);
        cont.removeRedef(redef, repl);
        renames.putAll(contVarRenames);
      }
    }
  }

  static void updateName(Function fn, Block block, Vars existing,
          Map<Var, Arg> renames, Var var) {
    if (existing.usedNames.contains(var.name())) {
      String newName = chooseNewName(existing.usedNames, var);
      Var newVar = var.makeRenamed(newName);
      fn.addUsedVarName(newVar);

      Arg oldVal = renames.put(var, Arg.newVar(newVar));
      assert(oldVal == null) : "Shadowed variable: " + var.name();
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

    makeVarNamesUnique(in, in.mainBlock(), declarations,
                       new HierarchicalMap<Var, Arg>());
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
