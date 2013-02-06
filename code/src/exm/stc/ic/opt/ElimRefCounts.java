package exm.stc.ic.opt;

import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.lang.Var;
import exm.stc.common.util.Counters;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;

/**
 * Eliminate, merge and otherwise reduce read/write reference
 * counting operations.  Run as a post-processing step.
 */
public class ElimRefCounts extends FunctionOptimizerPass {

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
    elimRefCountsRec(logger, f, f.getMainblock(),
                     new Counters<Var>(), new Counters<Var>());
  }

  /*
   * TODO: want to modify this optimization pass to add in reference decrements
   * at end of continuations.  Neeed to walk over tree and add refcount instructions
   * wherever there is keepOpen/passIn annotation
   */
  private void elimRefCountsRec(Logger logger, Function f, Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    
    
    fixBlockRefCounting(logger, block, readIncrements, writeIncrements);
    
    
    //cancelInstructionRefcounts(block, thisBlockArrays);
    
    
    //cancelContinuationRefcounts(block, thisBlockArrays);
    
    for (Continuation cont: block.getContinuations()) {
      elimRefCountsCont(logger, f, cont);
    }
  }

  private void elimRefCountsCont(Logger logger, Function f,
                                 Continuation cont) {
    for (Block block: cont.getBlocks()) {
      // Build separate copy for each block
      Counters<Var> readIncrements = new Counters<Var>();
      Counters<Var> writeIncrements = new Counters<Var>();
      if (cont.isAsync() && cont.getType() == ContinuationType.WAIT_STATEMENT) {
        // TODO: handle other than wait
        for (Var keepOpen: cont.getKeepOpenVars()) {
          if (RefCounting.hasWriteRefCount(keepOpen)) {
            writeIncrements.decrement(keepOpen);
          }
        }
        for (Var passedIn: cont.getPassedInVars()) {
          if (RefCounting.hasReadRefCount(passedIn)) {
            readIncrements.decrement(passedIn);
          }
        }
      }
      elimRefCountsRec(logger, f, block, readIncrements, writeIncrements);
    }
  }

  /**
   * 
   * @param logger
   * @param block
   * @param readIncrements pre-existing decrements
   * @param writeIncrements -existing decrements
   */
  private void fixBlockRefCounting(Logger logger, Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    for (Instruction inst: block.getInstructions()) {
      updateInstructionRefCount(inst, readIncrements, writeIncrements);
    }
    for (Continuation cont: block.getContinuations()) {
      updateContinuationRefCount(cont, readIncrements, writeIncrements);
    }
    
    updateBlockRefcounting(block, readIncrements, writeIncrements);
  }
  
  private void updateBlockRefcounting(Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    cancelDecrements(block, readIncrements, writeIncrements);
    
    // TODO: move decrements to piggyback onto read/write instructions
    addDecrements(block, readIncrements, RefCountType.READERS);
    addDecrements(block, writeIncrements, RefCountType.WRITERS);
    
    addRefIncrements(block, readIncrements, RefCountType.READERS);
    addRefIncrements(block, writeIncrements, RefCountType.WRITERS);
    
  }

  private void cancelDecrements(Block block, Counters<Var> readIncrements,
      Counters<Var> writeIncrements) {
    ListIterator<CleanupAction> caIt = block.cleanupIterator(); 
    while (caIt.hasNext()) {
      CleanupAction ca = caIt.next();
      Instruction action = ca.action();
      switch (action.op) {
        case DECR_REF: {
          Var decrVar = action.getOutput(0);
          Arg amount = action.getInput(0);
          cancelDecrement(caIt, ca, readIncrements, decrVar, amount);
          break;
        }
        case DECR_WRITERS: {
          Var decrVar = action.getOutput(0);
          Arg amount = action.getInput(0);
          cancelDecrement(caIt, ca, writeIncrements, decrVar, amount);
          break;
        }
        default:
          // do nothing
          break;
      }
    }
  }

  private void cancelDecrement(ListIterator<CleanupAction> caIt,
      CleanupAction ca,
      Counters<Var> increments, Var decrVar, Arg decrAmount) {
    if (decrAmount.isIntVal()) {
      long incr = increments.getCount(decrVar);
      long decr = decrAmount.getIntLit();
      
      long diff = incr - decr;
      if (diff >= 0) {
        // Enough to cancel out decrement
        caIt.remove();
        long curr = increments.decrement(decrVar, decr);
        assert(curr >= 0);
      } else {
        // Increment is cancelled out
        increments.reset(decrVar);
        ((TurbineOp)ca.action()).setInput(0,
                        Arg.createIntLit(diff * -1));
      }
    }
  }

  private void addDecrements(Block block, Counters<Var> increments,
      RefCountType type) {
    Counters<Var> changes = new Counters<Var>();
    for (Entry<Var, Long> e: increments.entries()) {
      Var var = e.getKey();
      long count = e.getValue();
      addDecrement(block, changes, type, var, count);
    }
    // Build and merge to avoid concurrent modification problems
    increments.merge(changes);
  }

  private void addDecrement(Block block, Counters<Var> increments,
                            RefCountType type, Var var, long count) {
    if (count < 0) {
      assert(RefCounting.hasRefCount(var, type));
      Arg amount = Arg.createIntLit(count * -1);
      
      if (type == RefCountType.READERS) {
        block.addCleanup(var, 
            TurbineOp.decrRef(var, amount));
      } else {
        assert(type == RefCountType.WRITERS);
        block.addCleanup(var, 
            TurbineOp.decrWriters(var, amount));
      }
      increments.add(var, amount.getIntLit());
    }
  }

  /**
   * Add reference increments at head of block
   * @param block
   * @param increments
   * @param type
   */
  private void addRefIncrements(Block block, Counters<Var> increments,
                                RefCountType type) {
    for (Entry<Var, Long> e: increments.entries()) {
      Var var = e.getKey();
      long count = e.getValue();
      // TODO: need to handle placing refcount incr at correct point
      // for alias vars
      addRefIncrement(block, type, var, count);
    }
  }

  private void addRefIncrement(Block block, RefCountType type, Var var,
      long count) {
    assert(count >= 0) : var + ":" + count;
    if (count > 0) {
      // increment before anything spawned
      // TODO: add to var declaration?
      Instruction incrInst;
      if (type == RefCountType.READERS) {
        incrInst = TurbineOp.incrRef(var, Arg.createIntLit(count));
      } else {
        assert(type == RefCountType.WRITERS);
        incrInst = TurbineOp.incrWriters(var, Arg.createIntLit(count));
      }
      block.addInstructionFront(incrInst);
    }
  }

  private void updateInstructionRefCount(Instruction inst,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    Pair<List<Var>, List<Var>> refIncrs = inst.getIncrVars();
    List<Var> readIncrVars = refIncrs.val1;
    List<Var> writeIncrVars = refIncrs.val2;
    for (Var v: readIncrVars) {
      if (RefCounting.hasReadRefCount(v)) {
        readIncrements.increment(v);
      }
    }
    for (Var v: writeIncrVars) {
      if (RefCounting.hasWriteRefCount(v)) {
        writeIncrements.increment(v);
      }
    }
  }

  private void updateContinuationRefCount(Continuation cont,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    // TODO: handle other than wait
    if (cont.isAsync() &&
        cont.getType() == ContinuationType.WAIT_STATEMENT) {
      long incr = 1; // TODO: different for other continuation
      for (Var passedIn: cont.getPassedInVars()) {
        if (RefCounting.hasReadRefCount(passedIn)) {
          readIncrements.add(passedIn, incr);
        } 
      }
      for (Var keepOpen: cont.getKeepOpenVars()) {
        if (RefCounting.hasWriteRefCount(keepOpen)) {
          writeIncrements.add(keepOpen, incr);
        }
      }
    }
  }
}
