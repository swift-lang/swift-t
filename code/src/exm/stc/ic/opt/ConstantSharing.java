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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;

public class ConstantSharing implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Constant sharing";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_SHARED_CONSTANTS;
  }

  /**
   * Consolidate all futures which are initialized with a constant
   * value into a global constants area, to avoid reinitializing them.
   * If the same constant appears in multiple locations, we consolidate them
   * into one.
   * 
   * This pass only affects cases where a variable is assigned in the same block
   * as it is defined, as handling other cases is more complex.
   * 
   * We rely on subsequent passes to add in the constant declarations
   * (the FixupVariables pass), and to remove unneeded variables 
   * (dead code elimination)
   * @param logger
   * @param prog
   * @throws InvalidOptionException
   */
  public void optimize(Logger logger, Program prog) 
                                      throws InvalidOptionException {
    for (Function f: prog.getFunctions()) {
      // First find replacements, then put them into effect.  Do these separately so
      // that we can detect potential inconsistent values.
      HashMap<Var, Arg> globalReplacements = 
          findNewGlobalConstants(logger, prog, f);

      // Do replacements and remove assignment statements
      doReplacement(logger, prog, f, globalReplacements);

    }
  }

  public  HashMap<Var, Arg> findNewGlobalConstants(Logger logger, Program prog, Function f)
      throws InvalidOptionException {
    // List of vars that should not be replaced
    Set<Var> blacklist = new HashSet<Var>();
    
    /**
     * Top-level map itself
     */
    HashMap<Var, Arg> globalReplacements = new HashMap<Var, Arg>();
    
    findConstants(logger, prog, f, f.mainBlock(),
        globalReplacements, blacklist);

    return globalReplacements;
  }

  private static void findConstants(Logger logger, Program prog,
            Function fn, Block block, HashMap<Var, Arg> globalReplacements,
            Set<Var> blacklist) throws InvalidOptionException {   
    // Find the remaining constant futures and delete assignments to them
    logger.debug("Making constant futures shared globals");
    
    // Find block constants.  Any conflicting values will be removed
    ConstantFold.findBlockConstants(logger, fn, block, blacklist,
                                    globalReplacements, true, true);

    
    // Do this recursively for child blocks
    for (Continuation c: block.allComplexStatements()) {
      for (Block childBlock: c.getBlocks()) {
        // We could pass in localDeclsOfGlobalVars, but
        // it doesn't matter if global vars are redeclared in inner scope
        findConstants(logger, prog, fn, childBlock,
                      globalReplacements, blacklist);
      }
    }
    
  }

  private void doReplacement(Logger logger, Program prog, Function f,
      Map<Var, Arg> constantVars) {
    HashMap<Var, Arg> replacements = new HashMap<Var, Arg>();
    
    for (Entry<Var, Arg> c: constantVars.entrySet()) {
      Var oldVar = c.getKey();
      Arg val = c.getValue();
      assert(val.isConstant()) : val + " not constant";
      logger.trace("Found constant: " + oldVar + " = " + val);
      
      String globName = prog.invLookupGlobalConst(val);
      if (globName == null) {
        // Add new global constant
        globName = prog.addGlobalConst(val);
      }

      Var glob = new Var(val.futureType(), globName, 
                  VarStorage.GLOBAL_CONST, DefType.GLOBAL_CONST, null);
      replacements.put(oldVar, glob.asArg());
    }

    /*
     * Now replace in block
     */
    f.mainBlock().renameVars(replacements, RenameMode.VALUE, true);
    
  }

}
