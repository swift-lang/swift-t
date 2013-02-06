package exm.stc.ic.opt;

import java.util.HashSet;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;

/**
 * Eliminate, merge and otherwise reduce read/write reference
 * counting operations.  Run as a post-processing step.
 */
public class ElimRefcounts extends FunctionOptimizerPass {

  @Override
  public String getPassName() {
    return "Eliminate reference counting";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_ELIM_REFCOUNTS;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    elimRefcountsRec(logger, f, f.getMainblock());
  }

  private void elimRefcountsRec(Logger logger, Function f, Block block) {
    HashSet<String> thisBlockArrays = new HashSet<String>();
    for (Var v: block.getVariables()) {
      if (Types.isArray(v.type())) {
        thisBlockArrays.add(v.name());
      }
    }
    
    for (Instruction i: block.getInstructions()) {
      if (i.op == Opcode.ARRAY_BUILD) {
        Var arr = i.getOutput(0);
        boolean close = i.getInput(0).getBoolLit();
        if (!close && thisBlockArrays.contains(arr.name())) {
          boolean success = removeArrayDecr(block, arr);
          assert(success): "should have array decr here";
          // Remove refcount, decrement it here instead
          ((TurbineOp)i).setInput(0, Arg.createBoolLit(true));
        }
      }
    }

  }

  private boolean removeArrayDecr(Block block, Var arr) {
    ListIterator<CleanupAction> caIt = block.cleanupIterator();
    while (caIt.hasNext()) {
      CleanupAction ca = caIt.next();
      if (arr.name().equals(ca.var().name()) &&
        ca.action().op == Opcode.ARRAY_DECR_WRITERS) {
        caIt.remove();
        return true;
      }
    }
    return false;
  }

}
