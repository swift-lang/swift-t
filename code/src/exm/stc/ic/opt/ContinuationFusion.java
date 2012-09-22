package exm.stc.ic.opt;

import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.ForeachLoop;
import exm.stc.ic.tree.ICContinuations.IfStatement;
import exm.stc.ic.tree.ICContinuations.RangeLoop;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CompFunction;
import exm.stc.ic.tree.ICTree.Program;

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
public class ContinuationFusion {

  public static void fuse(Logger logger, Program prog) {
    for (CompFunction f: prog.getComposites()) {
      fuseRecursive(logger, prog, f, f.getMainblock());
    }
  }

  private static void fuseRecursive(Logger logger, Program prog, 
            CompFunction f, Block block) {
    if (block.getContinuations().size() > 1) {
      // no point trying to fuse anything if we don't have two continuations
      // to rub together
      fuseNonRecursive(block);
    }
    
    // Recurse on child blocks
    for (Continuation c: block.getContinuations()) {
      for (Block child: c.getBlocks()) {
        fuseRecursive(logger, prog, f, child);
      }
    }
  }

  private static void fuseNonRecursive(Block block) {
    ListIterator<Continuation> it = block.continuationIterator();
    
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
          fuseIfStatement(it, mergeCands, (IfStatement)c);
          break;
        case FOREACH_LOOP:
          fuseForeachLoop(it, mergeCands, (ForeachLoop)c);
          break;
        case RANGE_LOOP:
          fuseRangeLoop(it, mergeCands, (RangeLoop)c);
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
  private static void fuseIfStatement(ListIterator<Continuation> it,
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
  private static void fuseForeachLoop(ListIterator<Continuation> it,
      LinkedList<Continuation> mergeCands, ForeachLoop loop1) {
    for (Continuation c2: mergeCands) {
      if (c2.getType() == ContinuationType.FOREACH_LOOP) {
        ForeachLoop loop2 = (ForeachLoop)c2;
        if (loop2.fuseable(loop1)) {
          loop2.fuseInto(loop1, true);
          it.remove();  // Remove first loop
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
  private static void fuseRangeLoop(ListIterator<Continuation> it,
      LinkedList<Continuation> mergeCands, RangeLoop loop1) {
    for (Continuation c2: mergeCands) {
      if (c2.getType() == ContinuationType.RANGE_LOOP) {
        RangeLoop loop2 = (RangeLoop)c2;
        if (loop2.fuseable(loop1)) {
          loop2.fuseInto(loop1, true);
          it.remove();  // Remove first loop
        }
      }
    }
  }
}
