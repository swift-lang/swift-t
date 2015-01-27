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

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.Conditionals.IfStatement;
import exm.stc.ic.tree.ForeachLoops.ForeachLoop;
import exm.stc.ic.tree.ForeachLoops.RangeLoop;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;

/**
 * Fuse together equivalent continuations e.g. if statements with
 *  same condition, loops with same bounds
 *  
 *  Currently we do:
 *  * if statements with same condition
 *  * range loops with same bounds and same loop settings
 *  * foreach loops over same array with same loop settings
 *  
 * Doing this for loops has the potential to reduce overhead, but the biggest
 * gains might be from the optimizations that can follow on after the fusion
 */
public class ContinuationFusion extends FunctionOptimizerPass {

  @Override
  public String getPassName() {
    return "Continuation fusion";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_CONTROLFLOW_FUSION;
  }

  @Override
  public void optimize(Logger logger, Function f) {
    fuseRecursive(logger, f, f.mainBlock());
  }

  private static void fuseRecursive(Logger logger, Function f, Block block) {
    if (block.getContinuations().size() > 1) {
      // no point trying to fuse anything if we don't have two continuations
      // to rub together
      fuseNonRecursive(f.name(), block);
    }
    
    // Recurse on child blocks
    for (Continuation c: block.allComplexStatements()) {
      for (Block child: c.getBlocks()) {
        fuseRecursive(logger, f, child);
      }
    }
  }

  private static void fuseNonRecursive(String function, Block block) {
    Iterator<Continuation> it = block.continuationIterator();
    
    /* We want to check all pairs of continuations.  
     * Use the simple n^2 algorithm rather than creating any index data
     * structure, shouldn't be a problem on any non-ridiculous programs.
     */
    
    // First create a copy of the list and keep all of the Continuations 
    // that fall after the first
    assert(block.getContinuations().size() > 1);
    LinkedList<Continuation> mergeCands = new LinkedList<Continuation>(
                                              block.getContinuations());
    mergeCands.removeFirst(); // Don't compare first with itself
    
    while(it.hasNext()) {
      // Iterate over all continuations except last 
      if (mergeCands.isEmpty()) {
        break;
      }
      Continuation c = it.next();
      
      // Check continuations [i..n) to see if they can be fused with this
      switch(c.getType()) {
        case IF_STATEMENT:
          // TODO: doesn't work now since we have inline conditionals
          fuseIfStatement(it, mergeCands, (IfStatement)c);
          break;
        case FOREACH_LOOP:
          fuseForeachLoop(function, it, mergeCands, (ForeachLoop)c);
          break;
        case RANGE_LOOP:
          fuseRangeLoop(function, it, mergeCands, (RangeLoop)c);
          break;
        default:
          // don't do anything, can't handle
          break;
      } 
      mergeCands.removeFirst();
    }
  }

  /**
   * Try and merge if1 into one of statements in mergeCands
   * If successful, call it.remove()
   * @param it
   * @param mergeCands
   * @param if1
   */
  private static void fuseIfStatement(Iterator<Continuation> it,
      LinkedList<Continuation> mergeCands, IfStatement if1) {
    for (Continuation c2: mergeCands) {
      if (c2.getType() == ContinuationType.IF_STATEMENT) {
        IfStatement if2 = (IfStatement)c2;
        if (if1.fuseable(if2)) {
          // Inline if1's blocks into if2's at top (to retain order
          //  of statements in hope that this gives a higher prob of
          //   further optimisations)
          if2.fuse(if1, true);
          it.remove();  // Remove first if statement
          return; // was removed
        }
      }
    }
  }
  /**
   * Try and merge if1 into one of statements in mergeCands
   * If successful, call it.remove()
   * @param it
   * @param mergeCands
   * @param loop1
   */
  private static void fuseForeachLoop(String function,
      Iterator<Continuation> it, LinkedList<Continuation> mergeCands,
      ForeachLoop loop1) {
    for (Continuation c2: mergeCands) {
      if (c2.getType() == ContinuationType.FOREACH_LOOP) {
        ForeachLoop loop2 = (ForeachLoop)c2;
        if (loop2.fuseable(loop1)) {
          loop2.fuseInto(function, loop1, true);
          it.remove();  // Remove first loop
          return; // was removed
        }
      }
    }
  }
  /**
   * Try and merge if1 into one of statements in mergeCands
   * If successful, call it.remove()
   * @param it
   * @param mergeCands
   * @param loop1
   */
  private static void fuseRangeLoop(String function, Iterator<Continuation> it,
      LinkedList<Continuation> mergeCands, RangeLoop loop1) {
    for (Continuation c2: mergeCands) {
      if (c2.getType() == ContinuationType.RANGE_LOOP) {
        RangeLoop loop2 = (RangeLoop)c2;
        if (loop2.fuseable(loop1)) {
          assert(loop2 != loop1);
          loop2.fuseInto(function, loop1, true);
          it.remove();  // Remove first loop
          return; // First loop was removed from block, can't fuse again
        }
      }
    }
  }
}
