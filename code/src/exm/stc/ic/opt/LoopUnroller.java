package exm.stc.ic.opt;

import org.apache.log4j.Logger;

import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class LoopUnroller {
  
  public static void unrollLoops(Logger logger, Program prog) {
    for (Function f: prog.getFunctions()) {
      if (unrollLoops(logger, prog, f, f.getMainblock())) {
        // Unrolling can introduce duplicate vars
        Flattener.makeVarNamesUnique(f, prog.getGlobalConsts().keySet());
        Flattener.flattenNestedBlocks(f.getMainblock());
      }
    }
  }

  private static boolean unrollLoops(Logger logger, Program prog, Function f,
      Block block) {
    logger.debug("looking to unroll loops in " + f.getName());
    boolean unrolled = false;
    for (int i = 0; i < block.getContinuations().size(); i++) {
      Continuation c = block.getContinuation(i);
      
      boolean cRes = c.tryUnroll(logger, block);
      unrolled = unrolled || cRes;

      for (Block b: c.getBlocks()) {
        boolean res = unrollLoops(logger, prog, f, b);
        unrolled = unrolled || res;
      }
    }
    return unrolled;
  }
}
