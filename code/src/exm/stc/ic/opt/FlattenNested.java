package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;

public class FlattenNested extends FunctionOptimizerPass {
  @Override
  public String getPassName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getConfigEnabledKey() {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Remove all nested blocks from program
   * Precondition: all variable names in functions should be unique
   * @param in
   * @return
   */
  @Override
  public void optimize(Logger logger, Function f) {
    flattenNestedBlocks(f.getMainblock());
  }
  
  public static void flattenNestedBlocks(Block block) {
    List<Continuation> originalContinuations =
          new ArrayList<Continuation>(block.getContinuations());
    // Stick any nested blocks instructions into the main thing
    for (Continuation c: originalContinuations) {
      switch (c.getType()) {
      case NESTED_BLOCK:
        assert(c.getBlocks().size() == 1);
        if (!c.runLast()) {
          Block inner = c.getBlocks().get(0);
          flattenNestedBlocks(inner);
          c.inlineInto(block, inner);
        }
        break;
      default:
        // Recursively flatten any blocks inside the continuation
        for (Block b: c.getBlocks()) {
          flattenNestedBlocks(b);
        }
      }

    }
  }
}
