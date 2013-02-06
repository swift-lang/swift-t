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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.lang.Var;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;

public class DeadCodeEliminator {

  /**
   * Eliminates dead code in the current block and child blocks.  Since
   * this is a data flow language, the easiest way to do this is to 
   * find variables which aren't needed, eliminate those and the instructions
   * which write to them, and then do that repeatedly until we don't have anything
   * more to eliminate.
   * 
   * We avoid eliminating any instructions with side-effects, and anything that
   * contributes to the return value of a function.  We currently assume that
   * all non-builtin functions have side effects, as well as any builtins
   * operations that are not specifically marked as side-effect free.
   * @param logger
   * @param block
   */
  public static void eliminate(Logger logger, Block block) {
    int i = 1;
    boolean converged = false;
    // repeatedly remove code until no more can go.  running each of
    // the two steps here can lead to more unneeded code for the other step,
    // so it is easiest to just have a loop to make sure all code is eliminated
    while (!converged) {
      converged = eliminateIter(logger, block, i);
      i++;
    }
  }

  private static boolean eliminateIter(Logger logger, Block block,
                                                      int iteration) {

    if (logger.isTraceEnabled()) {
      logger.trace("Dead code elimination iteration " + iteration
      		+ " on block: " 
          + System.identityHashCode(block) + "<" + block.getType() + ">"
          + " with vars: " + block.getVariables());
    }
    
    boolean converged = true;

    // First see if we can get rid of any continuations
    ListIterator<Continuation> it = block.continuationIterator();
    while (it.hasNext()) {
      Continuation c = it.next();
      if (c.isNoop()) {
        it.remove();
        converged = false;
      }
    }
    
    // All dependent sets
    List<List<Var>> dependentSets = new ArrayList<List<Var>>();
    
    // Vars needed in this block
    Set<Var> thisBlockNeeded = new HashSet<Var>();
    Set<Var> thisBlockWritten = new HashSet<Var>();
    block.findThisBlockNeededVars(thisBlockNeeded, thisBlockWritten, dependentSets);

    if (logger.isTraceEnabled()) {
      logger.trace("This block needed: " + thisBlockNeeded
              + " outputs: " + thisBlockWritten);
    }
    
    // Now see if we can push down any variable declarations
    /*var => null means candidate.  var => Block means that it already appeared
     *  in a single block */
    Map<Var, Block> candidates = new HashMap<Var, Block>();
    for (Var v: block.getVariables()) {
      // Candidates are those not needed in this block
      if (!thisBlockNeeded.contains(v) &&
          !thisBlockWritten.contains(v))
        candidates.put(v, null);
    }
    
    // Vars needed by subblocks
    List<Set<Var>> subblockNeededVars = new ArrayList<Set<Var>>();
    for (Continuation cont: block.getContinuations()) {
      
      // All vars used within continuation blocks
      Set<Var> contAllUsed = new HashSet<Var>();
      
      for (Block subBlock: cont.getBlocks()) {
        Set<Var> subblockNeeded = new HashSet<Var>();
        Set<Var> subblockWritten = new HashSet<Var>();
        subBlock.findNeededVars(subblockNeeded, subblockWritten,
                                dependentSets);
        subblockNeededVars.add(subblockNeeded);
        if (logger.isTraceEnabled()) {
          logger.trace("Subblock " + subBlock.getType() + " needed: " 
              + subblockNeeded + " outputs: " + subblockWritten);
        }
        
        // All vars used in subblock
        Set<Var> subblockAll = new HashSet<Var>();
        subblockAll.addAll(subblockNeeded);
        subblockAll.addAll(subblockWritten);
        for (Var var: subblockAll) {
          if (candidates.containsKey(var)) {
            if (candidates.get(var) == null) {
              candidates.put(var, subBlock);
            } else {
              // Appeared in two places
              candidates.remove(var);
            }
          }
        }
        
        contAllUsed.addAll(subblockAll);
      }
      cont.removeUnused(contAllUsed);
    }
    
    // Push down variable declarations
    pushdownDeclarations(block, candidates);

    Set<Var> allNeeded = new HashSet<Var>();
    allNeeded.addAll(thisBlockNeeded);
    for (Set<Var> needed: subblockNeededVars) {
      allNeeded.addAll(needed);
    }
    
    // Then see if we can remove individual instructions
    Set<Var> unneeded = unneededVars(block, allNeeded, dependentSets);
    for (Var v: unneeded) {
      logger.debug("Eliminated variable " + v +  
                        " during dead code elimination");
      converged = false;
    }
    block.removeVars(unneeded);
    return converged;
  }

  private static void pushdownDeclarations(Block block,
          Map<Var, Block> candidates) {
    if (candidates.size() > 0) {
      ListIterator<Var> varIt = block.variableIterator();
      while (varIt.hasNext()) {
        Var var = varIt.next();
        Block newHome = candidates.get(var);
        if (newHome != null) {
          varIt.remove();
          newHome.addVariable(var);
          block.moveCleanups(var, newHome);
        }
      }
    }
  }

  public static void eliminate(Logger logger, Function f) {
    eliminateRec(logger, f.getMainblock());
  }
  
  public static void eliminateRec(Logger logger, Block block) {
    // Eliminate from bottom up so that references to vars in
    // subtrees are eliminated before checking vars in parent
    for (Continuation c: block.getContinuations()) {
      for (Block inner: c.getBlocks()) {
        eliminateRec(logger, inner);
      }
    }
    eliminate(logger, block);
  }
  
  /**
   * Find unneeded vars declared in local block
   * @param block
   * @param stillNeeded
   * @param dependentSets
   * @return
   */
  private static Set<Var> unneededVars(Block block,
            Set<Var> stillNeeded, List<List<Var>> dependentSets) {
    HashSet<Var> toRemove = new HashSet<Var>();
    
    // Check to see if we have to retain additional
    // variables based on interdependencies
    for (List<Var> dependentSet: dependentSets) { {
      boolean needed = false;
      for (Var v: dependentSet) {
        if (stillNeeded.contains(v)) {
          needed = true;
          break;
        }
      }
      if (needed) {
        for (Var v: dependentSet) {
          stillNeeded.add(v);
        }
      }
    }
      
    }
    for (Var v: block.getVariables()) {
      if (!stillNeeded.contains(v)) {
        toRemove.add(v);
      }
    }
    return toRemove;
  }

}
