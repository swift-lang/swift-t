package exm.stc.ic.opt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.Counters;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
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
                     new Counters<Var>(), new Counters<Var>(),
                     new HierarchicalSet<Var>());
  }

  private void elimRefCountsRec(Logger logger, Function f, Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements,
      HierarchicalSet<Var> parentAssignedAliasVars) {
    
    
    // Add own alias vars
    HierarchicalSet<Var> assignedAliasVars = parentAssignedAliasVars.makeChild();
    findAssignedAliasVars(block, assignedAliasVars);
    
    /* Traverse in bottom-up order so that we can access refcount info in
     * child blocks for pulling up */
    for (Continuation cont: block.getContinuations()) {
      elimRefCountsCont(logger, f, cont, assignedAliasVars);
    }
    
    fixBlockRefCounting(logger, block, readIncrements, writeIncrements,
                        parentAssignedAliasVars);
  }

  private void elimRefCountsCont(Logger logger, Function f,
           Continuation cont, HierarchicalSet<Var> parentAssignedAliasVars) {
    for (Block block: cont.getBlocks()) {
      // Build separate copy for each block
      Counters<Var> readIncrements = new Counters<Var>();
      Counters<Var> writeIncrements = new Counters<Var>();
      if (isSingleSpawnCont(cont)) {
        // TODO: handle foreach loops
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
      HierarchicalSet<Var> contAssignedAliasVars =
                    parentAssignedAliasVars.makeChild();
      for (Var v: cont.constructDefinedVars()) {
        if (v.storage() == VarStorage.ALIAS) {
          contAssignedAliasVars.add(v);
        }
      }
      
      elimRefCountsRec(logger, f, block, readIncrements, writeIncrements,
                      contAssignedAliasVars);
    }
  }

  /**
   * Is a continuation that spawns a single task initially
   * @param cont
   * @return
   */
  private boolean isSingleSpawnCont(Continuation cont) {
    return cont.isAsync() && 
            (cont.getType() == ContinuationType.WAIT_STATEMENT ||
             cont.getType() == ContinuationType.LOOP);
  }

  /**
   * 
   * @param logger
   * @param block
   * @param readIncrements pre-existing decrements
   * @param writeIncrements -existing decrements
   * @param parentAssignedAliasVars assign alias vars from parent blocks
   *                that we can immediately manipulate refcount of
   */
  private void fixBlockRefCounting(Logger logger, Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements,
      Set<Var> parentAssignedAliasVars) {
    
    // First collect up all required reference counting ops in block
    for (Instruction inst: block.getInstructions()) {
      updateInstructionRefCount(inst, readIncrements, writeIncrements);
    }
    for (Continuation cont: block.getContinuations()) {
      updateContinuationRefCount(cont, readIncrements, writeIncrements);
    }
    
    updateCleanupActionRefCount(block, readIncrements, writeIncrements);
    
    // Second put them back into IC
    updateBlockRefcounting(block, readIncrements, writeIncrements,
                           parentAssignedAliasVars);
  }
  
  private void updateBlockRefcounting(Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements,
      Set<Var> parentAssignedAliasVars) {
    // Move any increment instructions up to this block
    // if they can be combined with increments here
    pullUpRefIncrements(block, readIncrements, writeIncrements);
    
    // Add increments to end of block
    /*
     * TODO: move decrements to piggyback onto load instructions
     * - Scan backwards from end of block.
     * - If a load instruction  from X is found, and X has a net decrement,
     *    then piggy-back the entire decrement amount on the load 
     * - For writers counts, similar logic for container_insert
     */
    addDecrements(block, readIncrements, RefCountType.READERS);
    addDecrements(block, writeIncrements, RefCountType.WRITERS);
    
    // Add any remaining increments
    addRefIncrements(block, readIncrements, RefCountType.READERS,
                     parentAssignedAliasVars);
    addRefIncrements(block, writeIncrements, RefCountType.WRITERS,
                     parentAssignedAliasVars);    
  }

  private void updateCleanupActionRefCount(Block block,
          Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    ListIterator<CleanupAction> caIt = block.cleanupIterator(); 
    while (caIt.hasNext()) {
      CleanupAction ca = caIt.next();
      Instruction action = ca.action();
      switch (action.op) {
        case DECR_REF: 
        case DECR_WRITERS: {
          Var decrVar = action.getOutput(0);
          Arg amount = action.getInput(0);
          if (amount.isIntVal()) {
            // Remove instructions where counts is integer value
            Counters<Var> increments;
            if (action.op == Opcode.DECR_REF) {
              increments = readIncrements;
            } else {
              assert(action.op == Opcode.DECR_WRITERS);
              increments = writeIncrements;
            }
            increments.decrement(decrVar, amount.getIntLit());
            caIt.remove();
          }
          break;
        }
        default:
          // do nothing
          break;
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
   * Try to bring reference increments out from inner blocks.
   * The observation is that increments can safely be done earlier,
   * so if a child wait increments something, then we can just do it here.
   * @param rootBlock
   * @param readIncrements
   * @param writeIncrements
   */
  private void pullUpRefIncrements(Block rootBlock, Counters<Var> readIncrements,
          Counters<Var> writeIncrements) {
    Deque<Block> blockStack = new ArrayDeque<Block>();
    blockStack.push(rootBlock);
    
    while (!blockStack.isEmpty()) {
      Block block = blockStack.pop();
      
      accumulateIncrements(block, readIncrements, writeIncrements);
      
      for (Continuation cont: block.getContinuations()) {
        // Only enter wait statements since the block is executed exactly once
        if (cont.getType() == ContinuationType.WAIT_STATEMENT) {
          blockStack.push(((WaitStatement)cont).getBlock());
        }
      }
    }
  }

  /**
   * Remove positive constant increment instructions from block and
   * update counters
   * @param block
   * @param readIncrements
   * @param writeIncrements
   */
  private void accumulateIncrements(Block block, Counters<Var> readIncrements,
          Counters<Var> writeIncrements) {
    ListIterator<Instruction> it = block.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (inst.op == Opcode.INCR_REF ||
          inst.op == Opcode.INCR_WRITERS) {
        Var v = inst.getOutput(0);
        Arg amountArg = inst.getInput(0);
        if (amountArg.isIntVal()) {
          long amount = amountArg.getIntLit();
          Counters<Var> increments;
          if (inst.op == Opcode.INCR_REF) {
            increments = readIncrements;
          } else {
            assert(inst.op == Opcode.INCR_WRITERS);
            increments = writeIncrements;
          }
          if (increments.getCount(v) != 0) {
            // Check already being manipulated in this block
            increments.add(v, amount);
            it.remove();
          }
        }
      }
    }
  }

  /**
   * Add reference increments at head of block
   * @param block
   * @param increments
   * @param type
   * @param parentAssignedAliasVars assign alias vars from parent blocks
   *                that we can immediately manipulate refcount of
   */
  private void addRefIncrements(Block block, Counters<Var> increments,
                                RefCountType type, Set<Var> parentAssignedAliasVars) {
    Iterator<Entry<Var, Long>> it = increments.entries().iterator();
    while (it.hasNext()) {
      Entry<Var, Long> e = it.next();
      Var var = e.getKey();
      long count = e.getValue();
      if (var.storage() != VarStorage.ALIAS ||
              parentAssignedAliasVars.contains(var)) {
        // add increments that we can at top
        addRefIncrementAtTop(block, type, var, count);
        it.remove();
      }
    }
    // Now put increments for alias vars after point when var declared
    ListIterator<Instruction> instIt = block.instructionIterator();
    while (instIt.hasNext()) {
      Instruction inst = instIt.next();
      for (Var out: inst.getOutputs()) {
        if (out.storage() == VarStorage.ALIAS) {
          // Alias var must be set at this point, insert refcount instruction
          long incr = increments.getCount(out);
          assert(incr >= 0);
          if (incr > 0) {
            instIt.add(buildIncrInstruction(type, out, incr));
          }
          increments.reset(out);
        }
      }
    }
    
    // Check that all refcounts are zero
    for (Entry<Var, Long> e: increments.entries()) {
      assert(e.getValue() == 0) : "Refcount not 0 after pass " + e.toString() +
                                  " in block " + block;
    }
  }

  private void addRefIncrementAtTop(Block block, RefCountType type, Var var,
      long count) {
    assert(count >= 0) : var + ":" + count;
    if (count > 0) {
      // increment before anything spawned
      // TODO: add to var declaration?
      block.addInstructionFront(buildIncrInstruction(type, var, count));
    }
  }

  private Instruction buildIncrInstruction(RefCountType type, Var var,
          long count) {
    Instruction incrInst;
    if (type == RefCountType.READERS) {
      incrInst = TurbineOp.incrRef(var, Arg.createIntLit(count));
    } else {
      assert(type == RefCountType.WRITERS);
      incrInst = TurbineOp.incrWriters(var, Arg.createIntLit(count));
    }
    return incrInst;
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

  /**
   * Find aliasVars that were assigned in this block in order
   * to track where ref increment instructions can safely be put
   * @param block
   * @param assignedAliasVars 
   */
  private void findAssignedAliasVars(Block block, Set<Var> assignedAliasVars) {
    for (Instruction inst: block.getInstructions()) {
      for (Var out: inst.getOutputs()) {
        if (out.storage() == VarStorage.ALIAS) {
          assignedAliasVars.add(out);
        }
      }
    }
  }
}
