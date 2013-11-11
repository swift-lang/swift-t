package exm.stc.ic.refcount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.Counters;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.ic.opt.AliasTracker.AliasKey;
import exm.stc.ic.opt.TreeWalk;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.refcount.RCTracker.RCDir;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ForeachLoops.AbstractForeachLoop;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.InitType;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.TurbineOp.RefCountOp;

/**
 * Functions to insert reference count operations in IC tree.  There are a number
 * of different strategies for actually inserting the reference counts, which
 * are all implemented in this module.  For example, we can insert explicit
 * reference count instructions, or we can do tricky things like piggybacking
 * them on other operations, or canceling them out.
 */
public class RCPlacer {

  private final Logger logger = Logging.getSTCLogger();
  private final Map<String, Function> functionMap;

  public RCPlacer(Map<String, Function> functionMap) {
    super();
    this.functionMap = functionMap;
  }


  public void placeAll(Logger logger, Function fn, Block block,
             RCTracker increments, Set<Var> parentAssignedAliasVars) {
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      // Cancel out increments and decrements
      cancelIncrements(logger, fn, block, increments, rcType);
      
      // Add decrements to block
      placeDecrements(logger, fn, block, increments, rcType);

      // Add any remaining increments
      placeIncrements(block, increments, rcType, parentAssignedAliasVars);

      // Verify we didn't miss any
      RCUtil.checkRCZero(block, increments, rcType, true, true);
    }
  }

  
  /**
   * Add decrement operations to block, performing optimized placement
   * where possible
   * @param logger
   * @param fn
   * @param block
   * @param increments
   * @param type
   */
  private void placeDecrements(Logger logger, Function fn, Block block,
      RCTracker increments, RefCountType type) {
    if (RCUtil.piggybackEnabled()) {
      // First try to piggyback on variable declarations
      piggybackDecrementsOnDeclarations(logger, fn, block, increments, type);
  
      // Then see if we can do the decrement on top of another operation
      piggybackDecrementsOnInstructions(logger, fn, block, increments, type);
    }
  
    if (block.getType() != BlockType.MAIN_BLOCK
        && RCUtil.isForeachLoop(block.getParentCont())) {
      // Add remaining decrements to foreach loop where they can be batched
      addDecrementsToForeachLoop(block, increments, type);
    }
  
    // Add remaining decrements as cleanups at end of block
    addDecrementsAsCleanups(block, increments, type);
  }

  /**
   * Add reference increments at head of block
   * 
   * @param block
   * @param increments
   * @param rcType
   * @param parentAssignedAliasVars
   *          assign alias vars from parent blocks that we can immediately
   *          manipulate refcount of
   */
  private void placeIncrements(Block block, RCTracker increments,
      RefCountType rcType, Set<Var> parentAssignedAliasVars) {
    if (RCUtil.piggybackEnabled()) {
      // First try to piggy-back onto var declarations
      piggybackIncrementsOnDeclarations(block, increments, rcType);
    }
    // If we can't piggyback, put them at top of block before any tasks are
    // spawned
    addIncrementsAtTop(block, increments, rcType, parentAssignedAliasVars);
  }

  public void dumpDecrements(Block block, RCTracker increments) {
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      for (Entry<AliasKey, Long> e: increments.rcIter(rcType, RCDir.DECR)) {
        assert (e.getValue() <= 0);
        Var var = increments.getRefCountVar(block, e.getKey(), true);
        Arg amount = Arg.createIntLit(e.getValue() * -1);
        block.addCleanup(var, RefCountOp.decrRef(rcType, var, amount));
        e.setValue(0L); // Clear entry
      }
    }
  }

  /**
   * Insert all reference increments and decrements in place
   * 
   * @param stmtIt
   * @param increments
   */
  public void dumpIncrements(Instruction inst, Block block,
      ListIterator<Statement> stmtIt, RCTracker increments) {
    
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      for (Entry<AliasKey, Long> e: increments.rcIter(rcType, RCDir.INCR)) {
        Var var = increments.getRefCountVar(block, e.getKey(), true);
        Long incr = e.getValue();
        assert(incr >= 0);
        if (incr > 0) {
          boolean varInit = inst.isInitialized(var);
          if (inst != null && !(var.storage() == Alloc.ALIAS && varInit)) {
            insertIncrBefore(block, stmtIt, var, incr, rcType);
          } else {
            insertIncrAfter(block, stmtIt, var, incr, rcType);
          }
        } else if (incr < 0) {
          insertDecrAfter(block, stmtIt, var, incr * -1, rcType);
        }
      }
    }
  
    // Clear out all increments
    increments.resetAll();
  }

  /**
   * Cancel out increments and decrements.  We need to validate some
   * conditions to be sure that it's valid.  In particular, we need
   * to make sure that there isn't a "trailing" read/write of a variable
   * that occurs after a reference count has been consumed by an instruction. 
   * @param logger
   * @param fn
   * @param block
   * @param tracker
   * @param rcType
   */
  private void cancelIncrements(Logger logger, Function fn, Block block,
      RCTracker tracker, RefCountType rcType) {
    // Set of keys that we might be able to cancel
    Set<AliasKey> cancelCandidates = new HashSet<AliasKey>();
    
    for (Entry<AliasKey, Long> e: tracker.rcIter(rcType, RCDir.DECR)) {
      long decr = e.getValue();
      long incr = tracker.getCount(rcType, e.getKey(), RCDir.INCR);
      assert(decr <= 0);
      assert(incr >= 0);
      if (incr != 0 && decr != 0) {
        cancelCandidates.add(e.getKey());
      }
    }
    
    /*
     * Scan backwards up block to find out if we need to hold onto refcount
     * past point where it is consumed.
     */
    
    // Set of variables where refcount was consumed by statement/continuation
    // after current position
    Set<AliasKey> consumedAfter = new HashSet<AliasKey>();
    
    // Check that the data isn't actually used in block or sync continuations
    UseFinder useFinder = new UseFinder(tracker, rcType,
                                             null, cancelCandidates);
    
    ListIterator<Continuation> cit = block.continuationEndIterator();
    while (cit.hasPrevious()) {
      Continuation cont = cit.previous();
  
      useFinder.reset();
      TreeWalk.walkSyncChildren(logger, fn, cont, useFinder);
      updateCancelCont(tracker, cont, useFinder.getUsedKeys(), rcType,
                   cancelCandidates, consumedAfter);
    }
    
    ListIterator<Statement> it = block.statementEndIterator();
    while (it.hasPrevious()) {
      Statement stmt = it.previous();
      if (stmt.type() == StatementType.INSTRUCTION) {
        Instruction inst = stmt.instruction();
        updateCancel(tracker, inst, rcType, cancelCandidates, consumedAfter);
      } else {
        assert(stmt.type() == StatementType.CONDITIONAL);
        Conditional cond = stmt.conditional();
        useFinder.reset();
        TreeWalk.walkSyncChildren(logger, fn, cond, useFinder);
        updateCancelCont(tracker, cond, useFinder.getUsedKeys(), rcType,
                     cancelCandidates, consumedAfter);
      }
    }
    
    for (AliasKey toCancel: cancelCandidates) {
      // Simple approach to cancelling: if we are totally free to
      // cancel, cancel as much as possible
      long incr = tracker.getCount(rcType, toCancel, RCDir.INCR);
      long decr = tracker.getCount(rcType, toCancel, RCDir.DECR);
      long cancelAmount = Math.min(incr, Math.abs(decr));
      if (logger.isTraceEnabled()) {
        logger.trace("Cancel " + toCancel.toString() + " " + cancelAmount);
      }
  
      // cancel out increment and decrement
      tracker.cancel(toCancel, rcType, cancelAmount);
      tracker.cancel(toCancel, rcType, -cancelAmount);
    }
  }


  private void updateCancel(RCTracker tracker, Instruction inst,
      RefCountType rcType,
      Set<AliasKey> cancelCandidates, Set<AliasKey> consumedAfter) {
    List<Var> consumedVars;
    if (rcType == RefCountType.READERS) {
      consumedVars = inst.getIncrVars(functionMap).val1;
    } else {
      assert (rcType == RefCountType.WRITERS);
      consumedVars = inst.getIncrVars(functionMap).val2;
    }
    List<AliasKey> consumed = new ArrayList<AliasKey>(consumedVars.size());
    for (Var v: consumedVars) {
      consumed.add(tracker.getCountKey(v));
    }
    
    if (logger.isTraceEnabled()) {
      logger.trace(inst + " | " + rcType + " consumed " + consumed);
    }
    
    if (rcType == RefCountType.READERS) {
      for (Arg in: inst.getInputs()) {
        if (in.isVar()) {
          AliasKey key = tracker.getCountKey(in.getVar());
          updateCancel(key, cancelCandidates, consumedAfter,
                       true, consumed.contains(key));
        }
      }
      for (Var read: inst.getReadOutputs(functionMap)) {
        AliasKey key = tracker.getCountKey(read);
        updateCancel(key, cancelCandidates, consumedAfter,
            true, consumed.contains(key));
      }
    } else {
      assert (rcType == RefCountType.WRITERS);
      for (Var modified: inst.getOutputs()) {
        AliasKey key = tracker.getCountKey(modified);
        updateCancel(key, cancelCandidates, consumedAfter,
            true, consumed.contains(key));
      }
    }
    
    // Update with any remaining consumptions.  Note that it doesn't
    // matter if we update consumption after use, but the other way
    // doesn't work
    for (AliasKey con: consumed) {
      if (cancelCandidates.contains(con)) {
        updateCancel(con, cancelCandidates, consumedAfter, false,
                     true);
      }
    }
  }


  private void updateCancelCont(RCTracker tracker, Continuation cont,
      List<AliasKey> usedKeys, RefCountType rcType,
      Set<AliasKey> cancelCandidates, Set<AliasKey> consumedAfter) {
    List<AliasKey> consumed;
    if (!cont.isAsync()) {
      consumed = Collections.emptyList();
    } else {
      if (rcType == RefCountType.READERS) {
        Collection<PassedVar> passed = cont.getPassedVars();
        if (passed.isEmpty()) {
          consumed = Collections.emptyList();
        } else {
          consumed = new ArrayList<AliasKey>(passed.size());
          for (PassedVar pv: passed) {
            consumed.add(tracker.getCountKey(pv.var));
          }
        }
      } else {
        assert(rcType == RefCountType.WRITERS);
        Collection<Var> keepOpen = cont.getKeepOpenVars();
        if (keepOpen.isEmpty()) {
          consumed = Collections.emptyList();
        } else {
          consumed = new ArrayList<AliasKey>(keepOpen.size());
          for (Var v: keepOpen) {
            consumed.add(tracker.getCountKey(v));
          }
        }
      }
    }
    
    for (AliasKey usedKey: usedKeys) {
      updateCancel(usedKey, cancelCandidates, consumedAfter, true,
                   consumed.contains(usedKey));
    }
    
    for (AliasKey ck: consumed) {
      updateCancel(ck, cancelCandidates, consumedAfter, false, true);
    }
    
  }


  private void updateCancel(AliasKey key,
      Set<AliasKey> cancelCandidates, Set<AliasKey> consumedAfter,
      boolean usedHere, boolean consumedHere) {
    if (logger.isTraceEnabled()) {
      logger.trace("updateCancel " + key + " usedHere: " + usedHere 
          + " consumedHere: " + consumedHere);
    }
    
    if (consumedHere) {
      // For instructions that just consume a refcount, don't need to do
      // anything, just mark that it was consumed
      consumedAfter.add(key);
      return;
    }
    if (usedHere) {
      if (consumedAfter.contains(key)) {
        // We hold onto a refcount until later in block - ok
        return;
      } else {
        // The refcount is consumed by a prior instruction: can't safely
        // cancel
        cancelCandidates.remove(key);
      }
    }
  }


  /**
   * Try to piggyback reference decrements onto var declarations, for example if
   * a var is never read or written
   * 
   * @param block
   * @param tracker
   *          updated to reflect changes
   * @param type
   */
  private void piggybackDecrementsOnDeclarations(Logger logger, Function fn,
      Block block, final RCTracker tracker, final RefCountType rcType) {
    final Set<AliasKey> immDecrCandidates = Sets.createSet(
                        block.getVariables().size());
    for (Var blockVar : block.getVariables()) {
      if (blockVar.storage() != Alloc.ALIAS) {
        AliasKey key = tracker.getCountKey(blockVar);
        long incr = tracker.getCount(rcType, key, RCDir.DECR);
        assert(incr <= 0);
        // -1 may correspond to the case when the value of the var is
        // thrown away, or where the var is never written. The exception is
        // if an instruction reads/writes the var without modifying the
        // refcount,
        // in which case we can't move the decrement to the front of the block
        // Shouldn't be less than this when var is declared in this
        // block.
        assert (incr >= -1) : blockVar + " " + incr;
        if (incr == -1) {
          immDecrCandidates.add(key);
        }
      }
    }

    // Check that the data isn't actually used in block or sync continuations
    // In the case of async continuations, there should be an increment to
    // pass the var
    UseFinder useFinder = new UseFinder(tracker, rcType, null, immDecrCandidates);
    useFinder.reset();
    TreeWalk.walkSyncChildren(logger, fn, block, true, useFinder);
    immDecrCandidates.removeAll(useFinder.getUsedKeys());
   
    for (AliasKey key: immDecrCandidates) {
      Var immDecrVar = tracker.getRefCountVar(block, key, true);
      assert(immDecrVar.storage() != Alloc.ALIAS) : immDecrVar;
      block.setInitRefcount(immDecrVar, rcType, 0);
      tracker.cancel(immDecrVar, rcType, 1);
    }
  }

  private void piggybackDecrementsOnInstructions(Logger logger, Function fn,
      Block block, final RCTracker tracker, final RefCountType rcType) {
    // Initially all increments are candidates for piggybacking
    final Counters<Var> candidates =
        tracker.getVarCandidates(block, rcType, RCDir.DECR);

    UseFinder subblockWalker = new UseFinder(tracker, rcType,  
                                             candidates.keySet(), null);
    
    // Try to piggyback on continuations, starting at bottom up
    ListIterator<Continuation> cit = block.continuationEndIterator();
    while (cit.hasPrevious()) {
      Continuation cont = cit.previous();

      if (RCUtil.isAsyncForeachLoop(cont)) {
        AbstractForeachLoop loop = (AbstractForeachLoop) cont;
        List<Var> piggybacked = loop.tryPiggyBack(candidates, rcType, true);
        
        for (Var pv: piggybacked) {
          logger.trace("Piggybacked on foreach: " + pv + " " + rcType + " " +
                       candidates.getCount(pv));
        }
        candidates.resetAll(piggybacked);
        tracker.resetAll(rcType, piggybacked, RCDir.DECR);
      }

      // Walk continuation to find usages
      subblockWalker.reset();
      TreeWalk.walkSyncChildren(logger, fn, cont, subblockWalker);
      removeCandidates(subblockWalker.getUsedVars(), tracker,
                       candidates.keySet());
    }

    // Vars where we were successful
    List<Var> successful = new ArrayList<Var>();

    // scan up from bottom of block instructions to see if we can piggyback
    ListIterator<Statement> it = block.statementEndIterator();
    while (it.hasPrevious()) {
      Statement stmt = it.previous();
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction inst = stmt.instruction();
          List<Var> piggybacked = inst.tryPiggyback(candidates, rcType);
          
          candidates.resetAll(piggybacked);
          successful.addAll(piggybacked);
          
          // Make sure we don't decrement before a use of the var by removing
          // from candidate set
          List<Var> used = findUses(inst, rcType, candidates.keySet());
          removeCandidates(used, tracker, candidates.keySet());
          break;
        }
        case CONDITIONAL:
          // Walk continuation to find usages
          subblockWalker.reset();
          TreeWalk.walkSyncChildren(logger, fn, stmt.conditional(),
                                     subblockWalker);
          removeCandidates(subblockWalker.getUsedVars(), tracker,
                           candidates.keySet());
          break;
        default:
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }

    // Update main increments map
    for (Var v : successful) {
      assert(v != null);
      tracker.reset(rcType, v);
    }
  }

  /**
   * Find uses of variables in continuations
   *
   */
  private final class UseFinder extends TreeWalker {
    private final RCTracker tracker;
    private final RefCountType rcType;
    private final Set<Var> varCandidates;
    private final Set<AliasKey> keyCandidates;
    
    /**
     * List into which usages are accumulated.  Must
     * be reset by caller
     */
    private final ArrayList<Var> varAccum;
    private final ArrayList<AliasKey> keyAccum;
  
    private UseFinder(RCTracker tracker, RefCountType rcType,
            Set<Var> varCandidates, Set<AliasKey> keyCandidates) {
      this.tracker = tracker;
      this.rcType = rcType;
      this.varCandidates = varCandidates;
      this.keyCandidates = keyCandidates;
      this.varAccum = new ArrayList<Var>();
      this.keyAccum = new ArrayList<AliasKey>();
    }
  
    public void reset() {
      this.varAccum.clear();
      this.keyAccum.clear();
    }
    
    public void visit(Continuation cont) {
      findUsesNonRec(cont, tracker, rcType, varCandidates, keyCandidates,
                     varAccum, keyAccum);
    }
  
    public void visit(Instruction inst) {
      findUses(inst, tracker, rcType, varCandidates, keyCandidates,
                             varAccum, keyAccum);
    }
    
    public List<Var> getUsedVars() {
      return varAccum;
    }
    
    public List<AliasKey> getUsedKeys() {
      return keyAccum;
    }
  }


  private List<Var> findUses(Instruction inst,
      RefCountType rcType, Set<Var> candidates) {
    ArrayList<Var> res = new ArrayList<Var>();
    findUses(inst, null, rcType, candidates, null, res, null);
    return res;
  }
  
  /**
   * 
   * @param inst
   * @param tracker required if keyCandidates != null
   * @param rcType
   * @param varCandidates
   * @param keyCandidates
   * @param varAccum required if varCandidates != null
   * @param keyAccum required if keyCandidates != null
   */
  private void findUses(Instruction inst, RCTracker tracker,
      RefCountType rcType, Set<Var> varCandidates, Set<AliasKey> keyCandidates,
      List<Var> varAccum, List<AliasKey> keyAccum) {
    if (rcType == RefCountType.READERS) {
      for (Arg in : inst.getInputs()) {
        if (in.isVar()) {
          updateUses(in.getVar(), tracker, varCandidates, keyCandidates,
                     varAccum, keyAccum);
        }
      }
      for (Var read : inst.getReadOutputs(functionMap)) {
        updateUses(read, tracker, varCandidates, keyCandidates,
            varAccum, keyAccum);
      }
    } else {
      assert (rcType == RefCountType.WRITERS);
      for (Var modified : inst.getOutputs()) {
        updateUses(modified, tracker, varCandidates, keyCandidates,
            varAccum, keyAccum);
      }
    }    
  }
  
  private void findUsesNonRec(Continuation cont, RCTracker tracker,
      RefCountType rcType,
      Set<Var> varCandidates, Set<AliasKey> keyCandidates,
      ArrayList<Var> varAccum, ArrayList<AliasKey> keyAccum) {
    if (rcType == RefCountType.READERS) {
      for (Var v: cont.requiredVars(false)) {
        updateUses(v, tracker, varCandidates, keyCandidates,
                   varAccum, keyAccum);
      }
      if (cont.isAsync() && rcType == RefCountType.READERS) {
        for (PassedVar pv: cont.getPassedVars()) {
          if (!pv.writeOnly) {
            updateUses(pv.var, tracker, varCandidates, keyCandidates,
                       varAccum, keyAccum);
          }
        }
      } else if (cont.isAsync() && rcType == RefCountType.WRITERS) {
        for (Var v: cont.getKeepOpenVars()) {
          updateUses(v, tracker, varCandidates, keyCandidates,
              varAccum, keyAccum);
        }
      }
    }
    // Foreach loops have increments attached to them,
    // can't prematurely decrement
    if (RCUtil.isForeachLoop(cont)) {
      AbstractForeachLoop loop = (AbstractForeachLoop) cont;
      for (RefCount rc: loop.getStartIncrements()) {
        if (rc.type == rcType) {
          updateUses(rc.var, tracker, varCandidates, keyCandidates,
              varAccum, keyAccum);
        }
      }
    }
  }


  private void updateUses(Var v, RCTracker tracker, Set<Var> varCandidates,
      Set<AliasKey> keyCandidates, List<Var> varAccum,
      List<AliasKey> keyAccum) {
    if (varCandidates != null && varCandidates.contains(v)) {
      varAccum.add(v);
    }
    if (keyCandidates != null) {
      AliasKey key = tracker.getCountKey(v);
      if (keyCandidates.contains(key)) {
        keyAccum.add(key);
      }
    }
  }

  private void removeCandidates(Collection<Var> vars, RCTracker tracker,
                                Set<Var> varSet) {
    for (Var var: vars) {
      removeCandidate(var, tracker, null, varSet);
    }
  }
  
  private void removeCandidate(Var var, RCTracker tracker,
      Set<AliasKey> keySet, Set<Var> varSet) {
    if (keySet != null) {
      keySet.remove(tracker.getCountKey(var));
    }
    if (varSet != null) {
      varSet.remove(var);
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
      RCTracker increments, RefCountType type) {
    assert (block.getType() != BlockType.MAIN_BLOCK);
    Continuation parent = block.getParentCont();
    AbstractForeachLoop loop = (AbstractForeachLoop) parent;
    Counters<Var> changes = new Counters<Var>();
    for (Entry<AliasKey, Long> e : increments.rcIter(type, RCDir.DECR)) {
      Var var = increments.getRefCountVar(block, e.getKey(), true);
      long count = e.getValue();
      assert(count <= 0);
      if (count < 0 && RCUtil.definedOutsideCont(loop, block, var)) {
        // Decrement vars defined outside block
        long amount = count * -1;
        Arg amountArg = Arg.createIntLit(amount);
        loop.addEndDecrement(new RefCount(var, type, amountArg));
        changes.add(var, amount);
        Logging.getSTCLogger().trace("Piggyback " + var + " " + type + " " +
                                     amount + " on foreach"); 
      }
    }
    // Build and merge to avoid concurrent modification problems
    increments.merge(changes, type, RCDir.DECR);
  }

  private void addDecrementsAsCleanups(Block block, RCTracker increments,
      RefCountType rcType) {
    Counters<Var> changes = new Counters<Var>();
    for (Entry<AliasKey, Long> e : increments.rcIter(rcType, RCDir.DECR)) {
      Var var = increments.getRefCountVar(block, e.getKey(), true);
      long count = e.getValue();
      assert(count <= 0);
      addDecrement(block, changes, rcType, var, count);
    }
    // Build and merge to avoid concurrent modification problems
    increments.merge(changes, rcType, RCDir.DECR);
  }

  private void addDecrement(Block block, Counters<Var> increments,
      RefCountType type, Var var, long count) {
    if (count < 0) {
      assert (RefCounting.hasRefCount(var, type));
      Arg amount = Arg.createIntLit(count * -1);

      block.addCleanup(var, RefCountOp.decrRef(type, var, amount));

      increments.add(var, amount.getIntLit());
      
      
      if (logger.isTraceEnabled()) {
        logger.trace("Add " + var.name() + " " + type + " " + count +
                     " as cleanup");
      }
    }
  }

  private void insertDecrAfter(Block block, ListIterator<Statement> stmtIt,
      Var var, Long val, RefCountType type) {
    Arg amount = Arg.createIntLit(val);
    block.addCleanup(var, RefCountOp.decrRef(type, var, amount));
  }

  private void insertIncr(Block block,
      ListIterator<Statement> stmtIt, Var var, Long val,
      RefCountType type, boolean before) {
    assert(val >= 0);
    if (val == 0)
      return;
    
    if (before)
      stmtIt.previous();
    Instruction inst;
    Arg amount = Arg.createIntLit(val);
    inst = RefCountOp.incrRef(type, var, amount);
    inst.setParent(block);
    stmtIt.add(inst);
    if (before)
      stmtIt.next();
  }

  private void insertIncrBefore(Block block, ListIterator<Statement> stmtIt,
      Var var, Long val, RefCountType type) {
    insertIncr(block, stmtIt, var, val, type, true);
  }

  private void insertIncrAfter(Block block, ListIterator<Statement> stmtIt,
      Var var, Long val, RefCountType type) {
    insertIncr(block, stmtIt, var, val, type, false);
  }

  private void addIncrementsAtTop(Block block, RCTracker increments,
      RefCountType rcType, Set<Var> parentAssignedAliasVars) {
    // Next try to just put at top of block
    Iterator<Entry<AliasKey, Long>> it =
        increments.rcIter(rcType, RCDir.INCR).iterator();
    while (it.hasNext()) {
      Entry<AliasKey, Long> e = it.next();
      Var var = increments.getRefCountVar(block, e.getKey(), true);
      long count = e.getValue();
      if (var.storage() != Alloc.ALIAS
          || parentAssignedAliasVars.contains(var)) {
        // add increments that we can at top
        addRefIncrementAtTop(block, rcType, var, count);
        it.remove();
      }
    }
    // Now put increments for alias vars after point when var declared
    ListIterator<Statement> stmtIt = block.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      if (logger.isTraceEnabled()) {
        for (Entry<AliasKey, Long> e: increments.rcIter(rcType, RCDir.INCR)) {
          logger.trace("Try to place: " + e.getKey() + " at " + stmt);
        }
      }
      switch (stmt.type()) {
        case INSTRUCTION: {
          for (Pair<Var, InitType> init: stmt.instruction().getInitialized()) {
            if (init.val1.storage() == Alloc.ALIAS) {
              assert(init.val2 == InitType.FULL);
              addIncrForVar(block, increments, rcType, stmtIt, init.val1);
            }
          }
          break;
        }
        case CONDITIONAL: {
          for (Var initAlias: findConditionalInitAliases(stmt.conditional())) {
            addIncrForVar(block, increments, rcType, stmtIt, initAlias);
          }
          break;
        }
        default: 
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }
  }

  /**
   * Find any alias variables that are initialized on
   * all branches of the continuation
   * @param cond
   * @return
   */
  public Set<Var> findConditionalInitAliases(Conditional cond) {
    Set<Var> initAllBranches; 
    if (cond.isExhaustiveSyncConditional()) {
      List<Set<Var>> branchesInit = new ArrayList<Set<Var>>();
      for (Block b: cond.getBlocks()) {
        branchesInit.add(findBlockInitAliases(b));
      }
      initAllBranches = Sets.intersection(branchesInit);
    } else {
      initAllBranches = Collections.emptySet();
    }
    return initAllBranches;
  }

  private Set<Var> findBlockInitAliases(Block block) {
    HashSet<Var> result = new HashSet<Var>();
    for (Statement stmt: block.getStatements()) {
      if (stmt.type() == StatementType.INSTRUCTION) {
        for (Pair<Var, InitType> init: stmt.instruction().getInitialized()) {
          if (init.val1.storage() == Alloc.ALIAS) {
            assert(init.val2 == InitType.FULL);
            result.add(init.val1);
          }
        }
      } else {
        assert(stmt.type() == StatementType.CONDITIONAL);
        result.addAll(findConditionalInitAliases(stmt.conditional()));
      }
    }
    return result;
  }

  public void addIncrForVar(Block block, RCTracker increments,
      RefCountType rcType, ListIterator<Statement> stmtIt, Var out) {
    // Alias var must be set at this point, insert refcount instruction
    long incr = increments.getCount(rcType, out, RCDir.INCR);
    assert (incr >= 0);
    if (incr > 0) {
      insertIncrAfter(block, stmtIt, out, incr, rcType);
    }
    increments.reset(rcType, out);
  }

  /**
   * Try to implement refcount by adding to initial refcount of var declared
   * in this block.  This is always possible for non-alias vars and we don't
   * run into any timing issues since the count is incremented as soon as
   * the variable is in existence
   * @param block
   * @param increments
   * @param rcType
   */
  private void piggybackIncrementsOnDeclarations(Block block,
                RCTracker increments, RefCountType rcType) {
    for (Var blockVar : block.getVariables()) {
      if (blockVar.storage() != Alloc.ALIAS) {
        long incr = increments.getCount(rcType, blockVar, RCDir.INCR);
        assert(incr >= 0);
        if (incr > 0) {
          assert(RefCounting.hasRefCount(blockVar, rcType)) : blockVar;
          block.setInitRefcount(blockVar, rcType, incr + 1);
          increments.cancel(blockVar, rcType, -incr);
        }
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
  private static void addRefIncrementAtTop(Block block, RefCountType type, Var var,
      long count) {
    assert (count >= 0) : var + ":" + count;
    if (count > 0) {
      // increment before anything spawned
      block.addInstructionFront(buildIncrInstruction(type, var, count));
    }
  }


  private static Instruction buildIncrInstruction(RefCountType type, Var var,
      long count) {
    return RefCountOp.incrRef(type, var, Arg.createIntLit(count));
  }

}
