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
import java.util.Map.Entry;

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
   * into one
   * @param logger
   * @param prog
   * @throws InvalidOptionException
   */
  public void optimize(Logger logger, Program prog) 
                                      throws InvalidOptionException {
    for (Function f: prog.getFunctions()) {
      makeConstantsGlobal(logger, prog, f.getMainblock());
      // Fixup variable passing to avoid unnecessarily passing constants
      FixupVariables.fixupVariablePassing(logger, prog, f);
    }
  }

  /* Lift constants up to global scope to avoid reinitializing and 
   * duplication of constants
   */
  private static boolean makeConstantsGlobal(Logger logger, Program prog,
            Block block) throws InvalidOptionException {   
    // Find the remaining constant futures and delete assignments to them
    logger.debug("Making constant futures shared globals");
    HashMap<String, Var> localDeclsOfGlobalVars = 
          new HashMap<String, Var>();
    HashMap<String, Arg> knownConstants = new HashMap<String, Arg>();
    boolean changed = false;
    
    ConstantFold.findBlockConstants(logger, block, knownConstants, true, true);
    
    HashMap<String, Arg> globalReplacements = 
                            new HashMap<String, Arg>();
                  
    for (Entry<String, Arg> c: knownConstants.entrySet()) {
      String oldName = c.getKey();
      // Remove from this block's variable entries 
      
      Arg val = c.getValue();
      Var glob = null;
      String globName = prog.invLookupGlobalConst(val);
      if (globName == null) {
        // Add new global constant
        globName = prog.addGlobalConst(val);
      } else { 
        glob = localDeclsOfGlobalVars.get(globName);
      }
      if (glob == null) {
        glob = block.declareVariable(val.getType(), globName, 
                    VarStorage.GLOBAL_CONST, DefType.GLOBAL_CONST,
                    null);
        localDeclsOfGlobalVars.put(globName, glob);
        changed = true;
      }
      globalReplacements.put(oldName, Arg.createVar(glob));
    }
    block.renameVars(globalReplacements, false);
    
    // Do this recursively for child blocks
    for (Continuation c: block.getContinuations()) {
      for (Block childBlock: c.getBlocks()) {
        // We could pass in localDeclsOfGlobalVars, but
        // it doesn't matter if global vars are redeclared in inner scope
        boolean recChanged = makeConstantsGlobal(logger, prog, childBlock);
        changed = changed || recChanged;
      }
    }
    
    // Remove now redundant local constants
    if (Settings.getBoolean(Settings.OPT_DEAD_CODE_ELIM)) {
      if (changed) {
        DeadCodeEliminator.eliminate(logger, block);
      }
    }
    return changed;
  }

}
