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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.UnionSet;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
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
  public static void eliminate(Logger logger, Function f, Block block) {
    int i = 1;
    boolean converged = false;
    // repeatedly remove code until no more can go.  running each of
    // the two steps here can lead to more unneeded code for the other step,
    // so it is easiest to just have a loop to make sure all code is eliminated
    while (!converged) {
      converged = eliminateIter(logger, f, block, i);
      i++;
    }
  }

  private static boolean eliminateIter(Logger logger, Function f,
                 Block block, int iteration) {

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
    
    // Vars needed in this block
    BlockWalker thisBlock = new BlockWalker();
    TreeWalk.walk(logger, block, f, thisBlock, false);
    

    // Track the union of this block and all subblocks
    UnionSet<Var> allNeeded = new UnionSet<Var>();
    List<List<Var>> allDependentSets = new ArrayList<List<Var>>();
    allNeeded.addSet(thisBlock.needed);
    allDependentSets.addAll(thisBlock.interdependencies);
    
    if (logger.isTraceEnabled()) {
      logger.trace("This block needed: " + thisBlock.needed
              + " outputs: " + thisBlock.written
              + " dependencies: " + thisBlock.interdependencies);
    }
    
    // Now see if we can push down any variable declarations
    /*var => null means candidate.  var => Block means that it already appeared
     *  in a single block */
    Map<Var, Block> pushdownCandidates = new HashMap<Var, Block>();
    for (Var v: block.getVariables()) {
      // Candidates are those not needed in this block
      if (!thisBlock.needed.contains(v) &&
          !thisBlock.written.contains(v))
        pushdownCandidates.put(v, null);
    }
    
    for (Continuation cont: block.getContinuations()) {
      for (Block subBlock: cont.getBlocks()) {
        BlockWalker subInfo = new BlockWalker();
        TreeWalk.walk(logger, subBlock, f, subInfo, true);
        if (logger.isTraceEnabled()) {
          logger.trace("Subblock " + subBlock.getType() + " needed: " 
              + subInfo.needed + " outputs: " + subInfo.written);
        }
        
        allDependentSets.addAll(subInfo.interdependencies);
        allNeeded.addSet(subInfo.needed);
        
        // All vars used in subblock
        Set<Var> subblockAll = subInfo.allNeeded();
        for (Var var: subblockAll) {
          if (pushdownCandidates.containsKey(var)) {
            if (pushdownCandidates.get(var) == null) {
              pushdownCandidates.put(var, subBlock);
            } else {
              // Appeared in two places
              pushdownCandidates.remove(var);
            }
          }
        }
      }
    }
    
    // Push down variable declarations
    pushdownDeclarations(block, pushdownCandidates);
    
    // Then see if we can remove individual instructions
    Set<Var> unneeded = unneededVars(block, allNeeded, allDependentSets);
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
        // Don't push declaration down into loop
        if (newHome != null && !newHome.getParentCont().isLoop()) {
          varIt.remove();
          newHome.addVariable(var);
          block.moveCleanups(var, newHome);
        }
      }
    }
  }

  public static void eliminate(Logger logger, Function f) {
    eliminateRec(logger, f, f.getMainblock());
  }
  
  public static void eliminateRec(Logger logger, Function f, Block block) {
    // Eliminate from bottom up so that references to vars in
    // subtrees are eliminated before checking vars in parent
    for (Continuation c: block.getContinuations()) {
      for (Block inner: c.getBlocks()) {
        eliminateRec(logger, f, inner);
      }
    }
    eliminate(logger, f, block);
  }
  
  private static class BlockWalker extends TreeWalker {
    /** list of essential vars that can't be eliminated */ 
    final Set<Var> needed = new HashSet<Var>();
   
    /** list of vars written in block */
    final Set<Var> written = new HashSet<Var>();
   
    /** list of vars which are interdependent: if one
     *              var in list is needed, none can be eliminated */
    final List<List<Var>> interdependencies = new ArrayList<List<Var>>();
   
    /**
     * Return read-only set of all needed vars
     * @return
     */
    public Set<Var> allNeeded() {
      return new UnionSet<Var>(Arrays.asList(needed, written));
    }

    @Override
    public void visit(Continuation cont) {
      for (Var v: cont.requiredVars()) {
        needed.add(v);
      }
    }

    @Override
    public void visitDeclaration(Var v) {
      if (v.isMapped()) {
        needed.add(v);
        needed.add(v.mapping());
      }
    }

    @Override
    public void visit(Instruction inst) {
      updateForInstruction(inst, inst.hasSideEffects());
    }

    @Override
    public void visit(CleanupAction cleanup) {
      boolean actionHasSideEffects = cleanup.hasSideEffect();
      updateForInstruction(cleanup.action(), actionHasSideEffects);
      if (actionHasSideEffects) {
        needed.add(cleanup.var());
      }
    }
      
    private void updateForInstruction(Instruction inst, boolean hasSideEffects) {
      for (Arg oa: inst.getInputs()) {
        if (oa.isVar()) {
          needed.add(oa.getVar());
        }
      }
      for (Var v: inst.getReadOutputs()) {
        needed.add(v);
      }
      
      for (Var v: inst.getOutputs()) {
        written.add(v);
      }
      
      // Can't eliminate instructions with side-effects
      if (hasSideEffects) {
        for (Var out: inst.getOutputs()) {
          needed.add(out);
        }
      } else {
        // Can only eliminate one var if can eliminate all
        List<Var> modOut = inst.getModifiedOutputs();
        if (modOut.size() > 1) {
          interdependencies.add(modOut);
        }
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
  private static Set<Var> unneededVars(Block block,
            Set<Var> stillNeeded, List<List<Var>> dependentSets) {
    HashSet<Var> removeCandidates = new HashSet<Var>();
    for (Var v: block.getVariables()) {
      if (!stillNeeded.contains(v)) {
        removeCandidates.add(v);
      }
    }
    
    Logger logger = Logging.getSTCLogger();
    if (logger.isTraceEnabled()) {
      logger.trace("start removeCandidates: " + removeCandidates + "\n" +
                   "dependentSets: " + dependentSets);
    }
    boolean converged = false;
    // Check to see if we have to retain additional variables based on
    // interdependencies.  We're really just computing the transitive
    // closure here in an iterative way.
    while (!converged && !dependentSets.isEmpty()) {
      Iterator<List<Var>> it = dependentSets.iterator();
      while (it.hasNext()) {
        List<Var> dependentSet = it.next();
        converged = true; // assume converged until something changes
        boolean hasRemoveCandidate = false;
        boolean allRemoveCandidates = true;
        for (Var v: dependentSet) {
          if (removeCandidates.contains(v)) {
            hasRemoveCandidate = true;
          } else {
            allRemoveCandidates = false;
          }
        }
        if (!hasRemoveCandidate) {
          // No longer relevant
          it.remove();
        } else if (!allRemoveCandidates) {
          // Have to keep at least one remove candidate
          for (Var v: dependentSet) {
            removeCandidates.remove(v);
          }
          converged = false;
        }
      }
    }
    
    if (logger.isTraceEnabled()) {
      logger.trace("final removeCandidates: " + removeCandidates + "\n" +
                   "dependentSets: " + dependentSets);
    }
    
    return removeCandidates;
  }

}
