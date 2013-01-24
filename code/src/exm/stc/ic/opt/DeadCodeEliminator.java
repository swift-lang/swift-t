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
    boolean converged = false;
    // repeatedly remove code until no more can go.  running each of
    // the two steps here can lead to more unneeded code for the other step,
    // so it is easiest to just have a loop to make sure all code is eliminated
    while (!converged) {
      converged = eliminateIter(logger, block);
    }
  }

  private static boolean eliminateIter(Logger logger, Block block) {
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
    Set<String> thisBlockNeeded = new HashSet<String>();
    Set<String> thisBlockWritten = new HashSet<String>();
    block.findThisBlockNeededVars(thisBlockNeeded, thisBlockWritten, dependentSets);

    if (logger.isTraceEnabled()) {
      logger.trace("This block needed: " + thisBlockNeeded
              + " outputs: " + thisBlockWritten);
    }
    
    // Now see if we can push down any variable declarations
    /*var => null means candidate.  var => Block means that it already appeared
     *  in a single block */
    Map<String, Block> candidates = new HashMap<String, Block>();
    for (Var v: block.getVariables()) {
      // Candidates are those not needed in this block
      if (!thisBlockNeeded.contains(v.name()) &&
          !thisBlockWritten.contains(v.name()))
        candidates.put(v.name(), null);
    }
    
    // Vars needed by subblocks
    List<Set<String>> subblockNeededVars = new ArrayList<Set<String>>();
    for (Continuation c: block.getContinuations()) {
      for (Block subBlock: c.getBlocks()) {
        Set<String> subblockNeeded = new HashSet<String>();
        Set<String> subblockWritten = new HashSet<String>();
        subBlock.findNeededVars(subblockNeeded, subblockWritten,
                                dependentSets);
        subblockNeededVars.add(subblockNeeded);
        if (logger.isTraceEnabled()) {
          logger.trace("Subblock " + subBlock.getType() + " needed: " 
              + subblockNeeded + " outputs: " + subblockWritten);
        }
        
        // All vars used in subblock
        Set<String> subblockAll = new HashSet<String>();
        subblockAll.addAll(subblockNeeded);
        subblockAll.addAll(subblockWritten);
        for (String var: subblockAll) {
          if (candidates.containsKey(var)) {
            if (candidates.get(var) == null) {
              candidates.put(var, subBlock);
            } else {
              // Appeared in two places
              candidates.remove(var);
            }
          }
        }
      }
    }
    
    // Push down variable declarations
    pushdownDeclarations(block, candidates);

    Set<String> allNeeded = new HashSet<String>();
    allNeeded.addAll(thisBlockNeeded);
    for (Set<String> needed: subblockNeededVars) {
      allNeeded.addAll(needed);
    }
    
    // Then see if we can remove individual instructions
    Set<String> unneeded = unneededVars(block, allNeeded, dependentSets);
    for (String v: unneeded) {
      logger.debug("Eliminated variable " + v +  
                        " during dead code elimination");
      converged = false;
    }
    block.removeVars(unneeded);
    return converged;
  }

  private static void pushdownDeclarations(Block block,
          Map<String, Block> candidates) {
    if (candidates.size() > 0) {
      ListIterator<Var> varIt = block.variableIterator();
      while (varIt.hasNext()) {
        Var var = varIt.next();
        Block newHome = candidates.get(var.name());
        if (newHome != null) {
          //System.err.println("pushdown " + var + ": " + " only referenced in "
          //          + newHome);
          varIt.remove();
          newHome.addVariable(var);
          block.moveCleanups(var, newHome);
        }
      }
    }
  }

  public static void eliminate(Logger logger, Function f) {
    ArrayList<Block> stack = new ArrayList<Block>();
    stack.add(f.getMainblock());
    while (!stack.isEmpty()) {
      Block b = stack.remove(stack.size() - 1);
      eliminate(logger, b);
      for (Continuation c: b.getContinuations()) {
        stack.addAll(c.getBlocks());
      }
    }
  }
  
  /**
   * Find unneeded vars declared in local block
   * @param block
   * @param stillNeeded
   * @param dependentSets
   * @return
   */
  private static Set<String> unneededVars(Block block,
            Set<String> stillNeeded, List<List<Var>> dependentSets) {
    HashSet<String> toRemove = new HashSet<String>();
    
    // Check to see if we have to retain additional
    // variables based on interdependencies
    for (List<Var> dependentSet: dependentSets) { {
      boolean needed = false;
      for (Var v: dependentSet) {
        if (stillNeeded.contains(v.name())) {
          needed = true;
          break;
        }
      }
      if (needed) {
        for (Var v: dependentSet) {
          stillNeeded.add(v.name());
        }
      }
    }
      
    }
    for (Var v: block.getVariables()) {
      if (!stillNeeded.contains(v.name())) {
        toRemove.add(v.name());
      }
    }
    return toRemove;
  }

}
