package exm.stc.ic.opt;

import org.apache.log4j.Logger;

import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class TreeWalk {

  /**
   * Top-down tree walk
   * @param logger
   * @param prog
   * @param walker
   */
  public static void walk(Logger logger, Program prog, TreeWalker walker) {
    for (Function f: prog.getFunctions()) {
      walk(logger, f.getMainblock(), f.getName(), walker);
    }
  }
  
  public static void walk(Logger logger, Block block, String function,
                          TreeWalker walker) {
    for (Instruction i: block.getInstructions()) {
      walker.visit(logger, function, i);
    }
    for (Continuation c: block.getContinuations()) {
      for (Block b: c.getBlocks()) {
        walk(logger, b, function, walker);
      }
    }
  }

  public interface TreeWalker {
    public void visit(Logger logger, String functionContext, Instruction inst);
  }
}
