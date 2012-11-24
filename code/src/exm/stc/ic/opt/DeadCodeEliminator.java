package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Set;

import org.apache.log4j.Logger;

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
      converged = true;
      // First see if we can get rid of any large chunks:
      ListIterator<Continuation> it = block.continuationIterator();
      while (it.hasNext()) {
        Continuation c = it.next();
        if (c.isNoop()) {
          it.remove();
          converged = false;
        }
      }
      
      // Then see if we can remove individual instructions
      Set<String> unneeded = block.unneededVars();
      for (String v: unneeded) {
        logger.debug("Eliminated variable " + v +  
                          " during dead code elimination");
        converged = false;
      }
      block.removeVars(unneeded);
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

}
