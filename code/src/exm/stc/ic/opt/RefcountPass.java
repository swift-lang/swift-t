package exm.stc.ic.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.RefCount;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.Counters;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.ICContinuations.AbstractForeachLoop;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.LoopBreak;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * Eliminate, merge and otherwise reduce read/write reference counting
 * operations. Run as a post-processing step.
 * 
 * TODO: push down refcount decrement operations? Are there real situations
 * where this helps?
 */
public class RefcountPass implements OptimizerPass {

  /**
   * Map of names to functions, used inside pass. msut be initialized before
   * pass runs.
   */
  private Map<String, Function> functionMap = null;

  @Override
  public String getPassName() {
    return "Refcount adding";
  }

  @Override
  public String getConfigEnabledKey() {
    return null;
  }

  @Override
  public void optimize(Logger logger, Program program) throws UserException {
    functionMap = buildFunctionMap(program);

    for (Function f : program.getFunctions()) {
      addRefCountsRec(logger, f, f.getMainblock(), new Counters<Var>(),
          new Counters<Var>(), new HierarchicalSet<Var>());
    }
  }

  private static Map<String, Function> buildFunctionMap(Program program) {
    Map<String, Function> functionMap = new HashMap<String, Function>();
    for (Function f : program.getFunctions()) {
      functionMap.put(f.getName(), f);
    }
    return functionMap;
  }

  private void addRefCountsRec(Logger logger, Function f, Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements,
      HierarchicalSet<Var> parentAssignedAliasVars) {

    // Add own alias vars
    HierarchicalSet<Var> assignedAliasVars = parentAssignedAliasVars
        .makeChild();
    findAssignedAliasVars(block, assignedAliasVars);

    /*
     * Traverse in bottom-up order so that we can access refcount info in child
     * blocks for pulling up
     */
    for (Continuation cont : block.getContinuations()) {
      addRefCountsCont(logger, f, cont, assignedAliasVars);
    }

    fixBlockRefCounting(logger, f, block, readIncrements, writeIncrements,
        parentAssignedAliasVars);
  }

  private void addRefCountsCont(Logger logger, Function f, Continuation cont,
      HierarchicalSet<Var> parentAssignedAliasVars) {
    for (Block block : cont.getBlocks()) {
      // Build separate copy for each block
      Counters<Var> readIncrements = new Counters<Var>();
      Counters<Var> writeIncrements = new Counters<Var>();

      addDecrementsForCont(cont, readIncrements, writeIncrements);
      HierarchicalSet<Var> contAssignedAliasVars = parentAssignedAliasVars
          .makeChild();
      for (Var v : cont.constructDefinedVars()) {
        if (v.storage() == VarStorage.ALIAS) {
          contAssignedAliasVars.add(v);
        }
      }

      addRefCountsRec(logger, f, block, readIncrements, writeIncrements,
          contAssignedAliasVars);
    }
    // Do any additional work for continuation
    if (isForeachLoop(cont)) {
      addIncrementsToForeachLoop(cont);
    }
  }

  private boolean isAsyncForeachLoop(Continuation cont) {
    return cont.isAsync() && isForeachLoop(cont);
  }

  private boolean isForeachLoop(Continuation cont) {
    return (cont.getType() == ContinuationType.FOREACH_LOOP || cont.getType() == ContinuationType.RANGE_LOOP);
  }

  /**
   * Is a continuation that spawns a single task initially
   * 
   * @param cont
   * @return
   */
  private boolean isSingleSpawnCont(Continuation cont) {
    return cont.isAsync()
        && (cont.getType() == ContinuationType.WAIT_STATEMENT || cont.getType() == ContinuationType.LOOP);
  }

  /**
   * 
   * @param logger
   * @param fn
   * @param block
   * @param readIncrements
   *          pre-existing decrements
   * @param writeIncrements
   *          -existing decrements
   * @param parentAssignedAliasVars
   *          assign alias vars from parent blocks that we can immediately
   *          manipulate refcount of
   */
  private void fixBlockRefCounting(Logger logger, Function fn, Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements,
      Set<Var> parentAssignedAliasVars) {
    // First collect up all required reference counting ops in block
    for (Instruction inst : block.getInstructions()) {
      updateInstructionRefCount(inst, readIncrements, writeIncrements);
    }
    for (Continuation cont : block.getContinuations()) {
      addIncrementsForCont(cont, readIncrements, writeIncrements);
    }

    updateDecrementCounts(block, fn, readIncrements, writeIncrements);

    
    // Second put them back into IC
    updateBlockRefcounting(logger, fn, block, readIncrements, writeIncrements,
        parentAssignedAliasVars);
  }

  private void updateBlockRefcounting(Logger logger, Function fn, Block block,
      Counters<Var> readIncrements, Counters<Var> writeIncrements,
      Set<Var> parentAssignedAliasVars) {
    if (cancelEnabled()) {
      // Move any increment instructions up to this block
      // if they can be combined with increments here
      pullUpRefIncrements(block, readIncrements, writeIncrements);
    }

    // Add decrements to block
    addDecrements(logger, fn, block, readIncrements, RefCountType.READERS);
    addDecrements(logger, fn, block, writeIncrements, RefCountType.WRITERS);

    // Add any remaining increments
    addRefIncrements(block, readIncrements, RefCountType.READERS,
        parentAssignedAliasVars);
    addRefIncrements(block, writeIncrements, RefCountType.WRITERS,
        parentAssignedAliasVars);

    // Verify we didn't miss any
    checkIncrementsAdded(block, readIncrements);
    checkIncrementsAdded(block, writeIncrements);
  }

  private void checkIncrementsAdded(Block block, Counters<Var> increments) {
    // Check that all refcounts are zero
    for (Entry<Var, Long> e : increments.entries()) {
      String msg = "Refcount not 0 after pass " + e.toString() + " in block "
          + block;
      if (e.getKey().storage() == VarStorage.ALIAS) {
        // This is ok but indicates var declaration is in wrong place
        Logging.getSTCLogger().debug(msg);
      } else {
        assert (e.getValue() == 0) : msg;
      }
    }
  }

  /**
   * Search for decrements in block
   * 
   * @param block
   * @param fn
   * @param readIncrements
   * @param writeIncrements
   */
  private void updateDecrementCounts(Block block, Function fn,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    // If this is main block of function, add passed in
    if (block.getType() == BlockType.MAIN_BLOCK) {
      assert (block == fn.getMainblock());
      // System.err.println(fn.getName() + " async: " + fn.isAsync());
      if (fn.isAsync()) {
        // Need to do bookkeeping if this runs in separate task
        for (Var i : fn.getInputList()) {
          if (RefCounting.hasReadRefCount(i)) {
            readIncrements.decrement(i);
          }
        }
        for (PassedVar o : fn.getPassedOutputList()) {
          if (RefCounting.hasWriteRefCount(o.var)) {
            writeIncrements.decrement(o.var);
          }

          // Outputs might be read in function, need to maintain that
          // refcount
          if (!o.writeOnly && RefCounting.hasReadRefCount(o.var)) {
            readIncrements.decrement(o.var);
          }
        }
      }
    }

    // Refcount operation for declared variables
    for (Var var : block.getVariables()) {
      if (RefCounting.hasReadRefCount(var) && var.storage() != VarStorage.ALIAS) {
        readIncrements.decrement(var);
      }
      if (RefCounting.hasWriteRefCount(var)
          && var.storage() != VarStorage.ALIAS) {
        writeIncrements.decrement(var);
      }
    }

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
            throw new STCRuntimeError("Shouldn't be here" + action);
          } else {
            assert (action.op == Opcode.DECR_WRITERS);
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

  private void addDecrements(Logger logger, Function fn, Block block,
      Counters<Var> increments, RefCountType type) {
    if (piggybackEnabled()) {
      // First try to piggyback on variable declarations
      piggybackDecrementsOnDeclarations(logger, fn, block, increments, type);

      // Then see if we can do the decrement on top of another operation
      piggybackDecrementsOnInstructions(logger, fn, block, increments, type);
    }

    if (block.getType() != BlockType.MAIN_BLOCK
        && isForeachLoop(block.getParentCont())) {
      // Add remaining decrements to foreach loop where they can be batched
      addDecrementsToForeachLoop(block, increments, type);
    }

    // Add remaining decrements as cleanups at end of block
    addDecrementsAsCleanups(block, increments, type);
  }

  /**
   * Try to piggyback reference decrements onto var declarations, for example if
   * a var is never read or written
   * 
   * @param block
   * @param increments
   *          updated to reflect changes
   * @param type
   */
  private void piggybackDecrementsOnDeclarations(Logger logger, Function fn,
      Block block, Counters<Var> increments, final RefCountType type) {
    final Set<Var> immDecrCandidates = Sets.createSet(block.getVariables()
        .size());
    for (Var blockVar : block.getVariables()) {
      if (blockVar.storage() != VarStorage.ALIAS) {
        long incr = increments.getCount(blockVar);
        // -1 may correspond to the case when the value of the var is
        // thrown away, or where the var is never written. The exception is
        // if an instruction reads/writes the var without modifying the
        // refcount,
        // in which case we can't move the decrement to the front of the block
        // Shouldn't be less than this when var is declared in this
        // block.
        assert (incr >= -1) : blockVar + " " + incr;
        if (incr == -1) {
          immDecrCandidates.add(blockVar);
        }
      }
    }

    // Check that the data isn't actually used in block or sync continuations
    TreeWalk.walkSyncChildren(logger, fn, block, true, new TreeWalker() {
      public void visit(Continuation cont) {
        removeDecrCandidates(cont, type, immDecrCandidates);
      }

      public void visit(Instruction inst) {
        removeDecrCandidates(inst, type, immDecrCandidates);
      }
    });

    for (Var immDecrVar : immDecrCandidates) {
      block.setInitRefcount(immDecrVar, type, 0);
      increments.increment(immDecrVar);
    }
  }

  private void piggybackDecrementsOnInstructions(Logger logger, Function fn,
      Block block, Counters<Var> increments, final RefCountType type) {
    // Initially all increments are candidates for piggybacking
    final Counters<Var> candidates = increments.clone();
    
    // Remove any candidates from synchronous children that might 
    // read/write the variables
    TreeWalker subblockWalker = new TreeWalker() {
      public void visit(Continuation cont) {
        removeDecrCandidates(cont, type, candidates.keySet());
      }
      public void visit(Instruction inst) {
        removeDecrCandidates(inst, type, candidates.keySet());
      }
    };
    
    // Try to piggyback on continuations, starting at bottom up
    ListIterator<Continuation> cit = block.continuationIterator(
                                  block.getContinuations().size());
    while (cit.hasPrevious()) {
      Continuation cont = cit.previous();
      
      if (isAsyncForeachLoop(cont)) {
        AbstractForeachLoop loop = (AbstractForeachLoop)cont;
        loop.tryPiggyBack(increments, type, true);
      }
      
      // Walk continuation to remove anything 
      TreeWalk.walkSyncChildren(logger, fn, cont, subblockWalker);
    }
   
    
    // Vars where we were successful
    List<Var> successful = new ArrayList<Var>();
    
    // scan up from bottom of block instructions to see if we can piggyback
    ListIterator<Instruction> it = block.instructionIterator(
                                              block.getInstructions().size());
    while (it.hasPrevious()) {
      Instruction inst = it.previous();
      List<Var> piggybacked = inst.tryPiggyback(candidates, type);
      for (Var v: piggybacked) {
        candidates.reset(v);
        successful.add(v);
      }
      // Make sure we don't decrement before a use of the var by removing
      // from candidate set
      removeDecrCandidates(inst, type, candidates.keySet());
    }
    

    // Update main increments map
    for (Var v: successful) {
      increments.reset(v);
    }
  }

  /**
   * Remove from candidates any variables that can't have the refcount
   * decremented before this instruction executes
   * 
   * @param inst
   * @param type
   * @param candidates
   */
  private void removeDecrCandidates(Instruction inst, RefCountType type,
      Set<Var> candidates) {
    if (type == RefCountType.READERS) {
      for (Arg in : inst.getInputs()) {
        if (in.isVar())
          candidates.remove(in.getVar());
      }
      for (Var read : inst.getReadOutputs()) {
        candidates.remove(read);
      }
    } else {
      assert (type == RefCountType.WRITERS);
      for (Var modified : inst.getOutputs()) {
        candidates.remove(modified);
      }
    }
  }

  /**
   * Remove from candidates any variables that can't have the refcount
   * decremented before this continuation starts execution
   * 
   * @param inst
   * @param type
   * @param candidates
   */
  private void removeDecrCandidates(Continuation cont, RefCountType type,
      Set<Var> candidates) {
    // Continuation will need to read, can't decrement early
    if (type == RefCountType.READERS) {
      for (Var v : cont.requiredVars(false)) {
        candidates.remove(v);
      }
    }
    // Foreach loops have increments attached to them,
    // can't prematurely decrement
    if (isForeachLoop(cont)) {
      AbstractForeachLoop loop = (AbstractForeachLoop) cont;
      for (RefCount rc : loop.getStartIncrements()) {
        if (rc.type == type) {
          candidates.remove(rc.var);
        }
      }
    }
  }

  /**
   * Handle foreach loop as special case where we want to increment <# of
   * iterations> * <needed increments inside loop> before loop
   */
  private void addIncrementsToForeachLoop(Continuation c) {
    AbstractForeachLoop loop = (AbstractForeachLoop) c;

    Counters<Var> readIncrs = new Counters<Var>();
    Counters<Var> writeIncrs = new Counters<Var>();

    if (loop.isAsync()) {
      // If we're spawning off, increment once per iteration so that
      // each parallel task has a refcount to work with
      for (PassedVar v : loop.getPassedVars()) {
        if (!v.writeOnly && RefCounting.hasReadRefCount(v.var)) {
          readIncrs.increment(v.var);
        }
      }
      for (Var v : loop.getKeepOpenVars()) {
        if (RefCounting.hasWriteRefCount(v)) {
          writeIncrs.increment(v);
        }
      }
    }

    // Now see if we can pull some increments out of top of block
    Block loopBody = loop.getLoopBody();
    ListIterator<Instruction> it = loopBody.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (inst.op == Opcode.INCR_REF || inst.op == Opcode.INCR_WRITERS) {
        Var var = inst.getOutput(0);
        Arg amount = inst.getInput(0);
        if (amount.isIntVal() && definedOutsideCont(loop, loopBody, var)) {
          // Pull up constant increments
          if (inst.op == Opcode.INCR_REF) {
            readIncrs.add(var, amount.getIntLit());
          } else {
            assert (inst.op == Opcode.INCR_WRITERS);
            writeIncrs.add(var, amount.getIntLit());
          }
          it.remove();
        }
      } else {
        // stop processing once we get past the initial increments
        break;
      }
    }

    for (Entry<Var, Long> read : readIncrs.entries()) {
      loop.addStartIncrement(new RefCount(read.getKey(), RefCountType.READERS,
          Arg.createIntLit(read.getValue())));
    }

    for (Entry<Var, Long> write : writeIncrs.entries()) {
      loop.addStartIncrement(new RefCount(write.getKey(), RefCountType.WRITERS,
          Arg.createIntLit(write.getValue())));
    }
  }

  /**
   * Foreach loops have different method for handling decrements: we add them to
   * the parent continuation
   * 
   * @param block
   * @param increments
   * @param type
   */
  private void addDecrementsToForeachLoop(Block block,
      Counters<Var> increments, RefCountType type) {
    assert (block.getType() != BlockType.MAIN_BLOCK);
    Continuation parent = block.getParentCont();
    AbstractForeachLoop loop = (AbstractForeachLoop) parent;
    Counters<Var> changes = new Counters<Var>();
    for (Entry<Var, Long> e : increments.entries()) {
      Var var = e.getKey();
      long count = e.getValue();
      if (count < 0 && definedOutsideCont(loop, block, var)) {
        // Decrement vars defined outside block
        long amount = count * -1;
        loop.addEndDecrement(new RefCount(var, type, Arg.createIntLit(amount)));
        changes.add(var, amount);
      }

    }
    // Build and merge to avoid concurrent modification problems
    increments.merge(changes);
  }

  /**
   * 
   * @param cont
   * @param block
   * @param var a var that is in scope within block
   * @return true if var is accessible outside continuation
   */
  private boolean definedOutsideCont(Continuation cont, Block block,
      Var var) {
    assert(block.getParentCont() == cont);
    return !block.getVariables().contains(var) && 
          !cont.constructDefinedVars().contains(var);
  }

  private void addDecrementsAsCleanups(Block block, Counters<Var> increments,
      RefCountType type) {
    Counters<Var> changes = new Counters<Var>();
    for (Entry<Var, Long> e : increments.entries()) {
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
      assert (RefCounting.hasRefCount(var, type));
      Arg amount = Arg.createIntLit(count * -1);

      if (type == RefCountType.READERS) {
        block.addCleanup(var, TurbineOp.decrRef(var, amount));
      } else {
        assert (type == RefCountType.WRITERS);
        block.addCleanup(var, TurbineOp.decrWriters(var, amount));
      }
      increments.add(var, amount.getIntLit());
    }
  }

  /**
   * Try to bring reference increments out from inner blocks. The observation is
   * that increments can safely be done earlier, so if a child wait increments
   * something, then we can just do it here.
   * 
   * @param rootBlock
   * @param readIncrements
   * @param writeIncrements
   */
  private void pullUpRefIncrements(Block rootBlock,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    Deque<Block> blockStack = new ArrayDeque<Block>();
    blockStack.push(rootBlock);

    while (!blockStack.isEmpty()) {
      Block block = blockStack.pop();

      accumulateIncrements(block, readIncrements, writeIncrements);

      for (Continuation cont : block.getContinuations()) {
        // Only enter wait statements since the block is executed exactly once
        if (cont.getType() == ContinuationType.WAIT_STATEMENT) {
          blockStack.push(((WaitStatement) cont).getBlock());
        }
      }
    }
  }

  /**
   * Remove positive constant increment instructions from block and update
   * counters
   * 
   * @param block
   * @param readIncrements
   * @param writeIncrements
   */
  private void accumulateIncrements(Block block, Counters<Var> readIncrements,
      Counters<Var> writeIncrements) {
    ListIterator<Instruction> it = block.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (inst.op == Opcode.INCR_REF || inst.op == Opcode.INCR_WRITERS) {
        Var v = inst.getOutput(0);
        Arg amountArg = inst.getInput(0);
        if (amountArg.isIntVal()) {
          long amount = amountArg.getIntLit();
          Counters<Var> increments;
          if (inst.op == Opcode.INCR_REF) {
            increments = readIncrements;
          } else {
            assert (inst.op == Opcode.INCR_WRITERS);
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
   * 
   * @param block
   * @param increments
   * @param type
   * @param parentAssignedAliasVars
   *          assign alias vars from parent blocks that we can immediately
   *          manipulate refcount of
   */
  private void addRefIncrements(Block block, Counters<Var> increments,
      RefCountType type, Set<Var> parentAssignedAliasVars) {
    if (piggybackEnabled()) {
      // First try to piggy-back onto var declarations
      piggybackIncrementsOnDeclarations(block, increments, type);
    }

    // If we can't piggyback, put them at top of block before any tasks are
    // spawned
    addIncrementsAtTop(block, increments, type, parentAssignedAliasVars);
  }

  private void addIncrementsAtTop(Block block, Counters<Var> increments,
      RefCountType type, Set<Var> parentAssignedAliasVars) {
    // Next try to just put at top of block
    Iterator<Entry<Var, Long>> it = increments.entries().iterator();
    while (it.hasNext()) {
      Entry<Var, Long> e = it.next();
      Var var = e.getKey();
      long count = e.getValue();
      if (var.storage() != VarStorage.ALIAS
          || parentAssignedAliasVars.contains(var)) {
        // add increments that we can at top
        addRefIncrementAtTop(block, type, var, count);
        it.remove();
      }
    }
    // Now put increments for alias vars after point when var declared
    ListIterator<Instruction> instIt = block.instructionIterator();
    while (instIt.hasNext()) {
      Instruction inst = instIt.next();
      for (Var out : inst.getOutputs()) {
        if (out.storage() == VarStorage.ALIAS) {
          // Alias var must be set at this point, insert refcount instruction
          long incr = increments.getCount(out);
          assert (incr >= 0);
          if (incr > 0) {
            instIt.add(buildIncrInstruction(type, out, incr));
          }
          increments.reset(out);
        }
      }
    }
  }

  private void piggybackIncrementsOnDeclarations(Block block,
      Counters<Var> increments, RefCountType type) {
    for (Var blockVar : block.getVariables()) {
      if (blockVar.storage() != VarStorage.ALIAS) {
        long incr = increments.getCount(blockVar);
        if (incr > 0) {
          block.setInitRefcount(blockVar, type, incr + 1);
        }
        increments.decrement(blockVar, incr);
      }
    }
  }

  /**
   * Add an increment instruction at top of block
   * 
   * @param block
   * @param type
   * @param var
   * @param count
   */
  private void addRefIncrementAtTop(Block block, RefCountType type, Var var,
      long count) {
    assert (count >= 0) : var + ":" + count;
    if (count > 0) {
      // increment before anything spawned
      block.addInstructionFront(buildIncrInstruction(type, var, count));
    }
  }

  private Instruction buildIncrInstruction(RefCountType type, Var var,
      long count) {
    Instruction incrInst;
    if (type == RefCountType.READERS) {
      incrInst = TurbineOp.incrRef(var, Arg.createIntLit(count));
    } else {
      assert (type == RefCountType.WRITERS);
      incrInst = TurbineOp.incrWriters(var, Arg.createIntLit(count));
    }
    return incrInst;
  }

  private void updateInstructionRefCount(Instruction inst,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    Pair<List<Var>, List<Var>> refIncrs = inst.getIncrVars(functionMap);
    List<Var> readIncrVars = refIncrs.val1;
    List<Var> writeIncrVars = refIncrs.val2;
    for (Var v : readIncrVars) {
      if (RefCounting.hasReadRefCount(v)) {
        readIncrements.increment(v);
      }
    }
    for (Var v : writeIncrVars) {
      if (RefCounting.hasWriteRefCount(v)) {
        writeIncrements.increment(v);
      }
    }
    if (inst.op == Opcode.LOOP_BREAK) {
      // Special case decr
      LoopBreak loopBreak = (LoopBreak) inst;
      for (Var ko : loopBreak.getKeepOpenVars()) {
        assert (RefCounting.hasWriteRefCount(ko));
        writeIncrements.decrement(ko);
      }
      for (PassedVar pass : loopBreak.getLoopUsedVars()) {
        if (!pass.writeOnly && RefCounting.hasReadRefCount(pass.var)) {
          readIncrements.decrement(pass.var);
        }
      }
    }
  }

  private void addIncrementsForCont(Continuation cont,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    // TODO: handle other than wait
    if (cont.isAsync() && isSingleSpawnCont(cont)) {
      long incr = 1; // TODO: different for other continuations
      for (Var keepOpen : cont.getKeepOpenVars()) {
        if (RefCounting.hasWriteRefCount(keepOpen)) {
          writeIncrements.add(keepOpen, incr);
        }
      }

      // Avoid duplicate read increments
      Set<Var> readIncrTmp = new HashSet<Var>();

      for (PassedVar passedIn : cont.getPassedVars()) {
        if (!passedIn.writeOnly && RefCounting.hasReadRefCount(passedIn.var)) {
          readIncrTmp.add(passedIn.var);
        }
      }
      for (BlockingVar blockingVar : cont.blockingVars()) {
        if (RefCounting.hasReadRefCount(blockingVar.var)) {
          readIncrTmp.add(blockingVar.var);
        }
      }
      for (Var v : readIncrTmp) {
        readIncrements.add(v, incr);
      }
    }
  }

  /**
   * Add decrements that have to happen for continuation inside block
   * 
   * @param cont
   * @param readIncrements
   * @param writeIncrements
   */
  private void addDecrementsForCont(Continuation cont,
      Counters<Var> readIncrements, Counters<Var> writeIncrements) {
    // Get passed in variables decremented inside block
    // Loops don't need this as they decrement refs at loop_break instruction
    if ((isSingleSpawnCont(cont) || isAsyncForeachLoop(cont))
        && cont.getType() != ContinuationType.LOOP) {
      // TODO: handle foreach loops
      long amount = 1;

      for (Var keepOpen : cont.getKeepOpenVars()) {
        if (RefCounting.hasWriteRefCount(keepOpen)) {
          writeIncrements.decrement(keepOpen, amount);
        }
      }

      Set<Var> readIncrTmp = new HashSet<Var>();
      for (PassedVar passedIn : cont.getPassedVars()) {
        if (!passedIn.writeOnly && RefCounting.hasReadRefCount(passedIn.var)) {
          readIncrTmp.add(passedIn.var);
        }
      }

      // Hold read reference for wait var
      for (BlockingVar blockingVar : cont.blockingVars()) {
        if (RefCounting.hasReadRefCount(blockingVar.var)) {
          readIncrTmp.add(blockingVar.var);
        }
      }

      for (Var v : readIncrTmp) {
        readIncrements.decrement(v, amount);
      }
    }
  }

  /**
   * Find aliasVars that were assigned in this block in order to track where ref
   * increment instructions can safely be put
   * 
   * @param block
   * @param assignedAliasVars
   */
  private void findAssignedAliasVars(Block block, Set<Var> assignedAliasVars) {
    for (Instruction inst : block.getInstructions()) {
      for (Var out : inst.getOutputs()) {
        if (out.storage() == VarStorage.ALIAS) {
          assignedAliasVars.add(out);
        }
      }
    }
  }

  private boolean cancelEnabled() {
    try {
      return Settings.getBoolean(Settings.OPT_CANCEL_REFCOUNTS);
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }

  private boolean piggybackEnabled() {
    try {
      return Settings.getBoolean(Settings.OPT_PIGGYBACK_REFCOUNTS);
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }
}
