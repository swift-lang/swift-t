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
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.util.Counters;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.ic.aliases.AliasKey;
import exm.stc.ic.opt.TreeWalk;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.refcount.RCTracker.RefCountCandidates;
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
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;

/**
 * Functions to insert reference count operations in IC tree.  There are a number
 * of different strategies for actually inserting the reference counts, which
 * are all implemented in this module.  For example, we can insert explicit
 * reference count instructions, or we can do tricky things like piggybacking
 * them on other operations, or canceling them out.
 * 
 * 
 * TODO: Merge together refcounts to place - e.g. read/write same var,
 *       or two vars if they happen to alias same refcounted var
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
      preprocessIncrements(increments, rcType);
      if (logger.isTraceEnabled()) {
        logger.trace("After preprocessing: \n" + increments);
      }
      
      // Cancel out increments and decrements
      cancelIncrements(logger, fn, block, increments, rcType);
      
      // Add decrements to block
      placeDecrements(logger, fn, block, increments, rcType);

      // Add any remaining increments
      placeIncrements(fn, block, increments, rcType, parentAssignedAliasVars);

      // Verify we didn't miss any
      RCUtil.checkRCZero(block, increments, rcType, true, true);
    }
  }

  /**
   * Do preprocessing of increments to get ready for placement.
   * This entails taking all increments/decrements for keys that aren't
   * the datum roots and moving the refcount to the datum root var key.
   * This simplifies the remainder of the pass.
   * @param increments
   * @param rcType
   */
  private void preprocessIncrements(RCTracker increments, RefCountType rcType) {
    for (RCDir dir: RCDir.values()) {
      Iterator<Entry<AliasKey, Long>> it = 
                          increments.rcIter(rcType, dir).iterator();
      List<Pair<Var, Long>> newRCs = new ArrayList<Pair<Var, Long>>();
      // Remove all refcounts
      while (it.hasNext()) {
        Entry<AliasKey, Long> e = it.next();  
        Var rcVar = increments.getRefCountVar(e.getKey());
        if (logger.isTraceEnabled()) {
          logger.trace(rcVar + " is refcount var for key " + e.getKey());
        }
        newRCs.add(Pair.create(rcVar, e.getValue()));
        it.remove();
      }
      // Add them back in with updated key
      for (Pair<Var, Long> newRC: newRCs) {
        increments.incr(newRC.val1, rcType, newRC.val2);
      }
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
    // First try to piggyback on variable declarations
    piggybackDecrementsOnDeclarations(logger, fn, block, increments, type);

    // Then see if we can do the decrement on top of another operation
    piggybackOnStatements(logger, fn, block, increments, RCDir.DECR, type);
  
    if (block.getType() != BlockType.MAIN_BLOCK
        && RCUtil.isForeachLoop(block.getParentCont())) {
      // Add remaining decrements to foreach loop where they can be batched
      batchDecrementsForeach(block, increments, type);
    }
  
    // Add remaining decrements as cleanups at end of block
    addDecrementsAsCleanups(block, increments, type);
  }

  /**
   * Add reference increments at head of block
   * 
   * @param fn
   * @param block
   * @param increments
   * @param rcType
   * @param parentAssignedAliasVars
   *          assign alias vars from parent blocks that we can immediately
   *          manipulate refcount of
   */
  private void placeIncrements(Function fn, Block block, RCTracker increments,
      RefCountType rcType, Set<Var> parentAssignedAliasVars) {
    // First try to piggy-back onto var declarations
    piggybackIncrementsOnDeclarations(block, increments, rcType);
    
    // Then see if we can do the increment on top of another operation
    piggybackOnStatements(logger, fn, block, increments, RCDir.INCR, rcType);

    // If we can't piggyback, put them at top of block before any tasks are
    // spawned
    addIncrementsAtTop(block, increments, rcType, parentAssignedAliasVars);
  }

  public void dumpDecrements(Block block, RCTracker increments) {
    // TODO: can we guarantee that the refcount var is available in the
    //       current scope?
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      for (Entry<AliasKey, Long> e: increments.rcIter(rcType, RCDir.DECR)) {
        assert (e.getValue() <= 0);
        Var var = increments.getRefCountVar(e.getKey());
        Arg amount = Arg.createIntLit(e.getValue() * -1);
        block.addCleanup(var, RefCountOp.decrRef(rcType, var, amount));
      }
    }
    
    // Clear out all decrements
    increments.resetAll(RCDir.DECR);
  }

  /**
   * Insert all reference increments and decrements in place
   * 
   * @param stmt the statement to insert before or after
   *              null indicates end of the block
   * @param stmtIt
   * @param increments
   */
  public void dumpIncrements(Statement stmt, Block block,
      ListIterator<Statement> stmtIt, RCTracker increments) {
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      for (Entry<AliasKey, Long> e: increments.rcIter(rcType, RCDir.INCR)) {
        // TODO: can we guarantee that the refcount var is available in the
        //       current scope?
        Var var = increments.getRefCountVar(e.getKey());
        assert(var != null);
        Long incr = e.getValue();
        assert(incr >= 0);
        if (incr > 0) {
          boolean varInit = stmt != null &&
                   stmt.type() == StatementType.INSTRUCTION && 
                   stmt.instruction().isInitialized(var);
          // TODO: what if not initialized in a conditional? Should insert before
          if (stmt != null &&
                  (stmt.type() == StatementType.INSTRUCTION &&
                  !(var.storage() == Alloc.ALIAS && varInit))) {
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
    increments.resetAll(RCDir.INCR);
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
    if (!RCUtil.cancelEnabled()) {
      return;
    }

    // Set of keys that we might be able to cancel
    // Use var instead of key since we can now deal with concrete vars
    Set<Var> cancelCandidates = new HashSet<Var>();
    
    // TODO: inelegant
    // Need to track which vars are associated with each refcount var
    MultiMap<Var, AliasKey> rcVarToKey = new MultiMap<Var, AliasKey>();
    
    for (Entry<AliasKey, Long> e: tracker.rcIter(rcType, RCDir.DECR)) {
      long decr = e.getValue();
      AliasKey key = e.getKey();
      long incr = tracker.getCount(rcType, key, RCDir.INCR);
      assert(decr <= 0);
      assert(incr >= 0);
      if (incr != 0 && decr != 0) {
        Var rcVar = tracker.getRefCountVar(key);
        cancelCandidates.add(rcVar);
        rcVarToKey.put(rcVar, key);
      }
    }
    
    if (logger.isTraceEnabled()) {
      logger.trace("Cancel candidates " + rcType + ": " + cancelCandidates);
    }
    
    /*
     * Scan backwards up block to find out if we need to hold onto refcount
     * past point where it is consumed.
     */
    
    // Set of variables where refcount was consumed by statement/continuation
    // after current position
    Set<Var> consumedAfter = new HashSet<Var>();
    
    // Check that the data isn't actually used in block or sync continuations
    UseFinder useFinder = new UseFinder(tracker, rcType, cancelCandidates);
    
    ListIterator<Continuation> cit = block.continuationEndIterator();
    while (cit.hasPrevious()) {
      Continuation cont = cit.previous();
  
      useFinder.reset();
      TreeWalk.walkSyncChildren(logger, fn, cont, useFinder);
      updateCancelCont(tracker, cont, useFinder.getUsedVars(), rcType,
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
        updateCancelCont(tracker, cond, useFinder.getUsedVars(), rcType,
                     cancelCandidates, consumedAfter);
      }
    }
    
    for (Var toCancel: cancelCandidates) {
      // Simple approach to cancelling: if we are totally free to
      // cancel, cancel as much as possible
      long incr = 0;
      long decr = 0;
      
      // Account for fact we may have multiple vars for key
      List<AliasKey> matchingKeys = rcVarToKey.get(toCancel);
      assert(!matchingKeys.isEmpty());
      for (AliasKey key: matchingKeys) {
        incr += tracker.getCount(rcType, key, RCDir.INCR);
        decr += tracker.getCount(rcType, key, RCDir.DECR);
      }
      long cancelAmount = Math.min(incr, Math.abs(decr));
      if (logger.isTraceEnabled()) {
        logger.trace("Cancel " + toCancel.toString() + " " + cancelAmount);
      }
  
      // cancel out increment and decrement
      tracker.cancel(toCancel, rcType, cancelAmount);
      tracker.cancel(toCancel, rcType, -cancelAmount);
    }
  }


  /**
   * Maintain list of cancel candidates based on what instruction does
   * @param tracker
   * @param inst
   * @param rcType
   * @param cancelCandidates
   * @param consumedAfter
   */
  private void updateCancel(RCTracker tracker, Instruction inst,
      RefCountType rcType,
      Set<Var> cancelCandidates, Set<Var> consumedAfter) {
    List<VarCount> consumedVars;
    if (rcType == RefCountType.READERS) {
      consumedVars = inst.inRefCounts(functionMap).val1;
    } else {
      assert (rcType == RefCountType.WRITERS);
      consumedVars = inst.inRefCounts(functionMap).val2;
    }
    Set<Var> consumed = new HashSet<Var>();
    for (VarCount v: consumedVars) {
      if (v.count != 0) {
        consumed.add(tracker.getRefCountVar(v.var));
      }
    }
    
    if (logger.isTraceEnabled()) {
      logger.trace(inst + " | " + rcType + " consumed " + consumed);
    }
    
    if (rcType == RefCountType.READERS) {
      for (Arg in: inst.getInputs()) {
        if (in.isVar()) {
          Var inRCVar = tracker.getRefCountVar(in.getVar());
          updateCancel(inRCVar, cancelCandidates, consumedAfter,
                       true, consumed.contains(inRCVar));
        }
      }
      for (Var read: inst.getReadOutputs(functionMap)) {
        Var readRCVar = tracker.getRefCountVar(read);
        updateCancel(readRCVar, cancelCandidates, consumedAfter,
                     true, consumed.contains(readRCVar));
      }
    } else {
      assert (rcType == RefCountType.WRITERS);
      for (Var modified: inst.getOutputs()) {
        Var modifiedRCVar = tracker.getRefCountVar(modified);
        updateCancel(modifiedRCVar, cancelCandidates, consumedAfter,
                     true, consumed.contains(modifiedRCVar));
      }
    }
    
    // Update with any remaining consumptions.  Note that it doesn't
    // matter if we update consumption after use, but the other way
    // doesn't work
    for (Var consumedVar: consumed) {
      if (cancelCandidates.contains(consumedVar)) {
        Var consumedRCVar = tracker.getRefCountVar(consumedVar);
        updateCancel(consumedRCVar, cancelCandidates, consumedAfter,
                      false, true);
      }
    }
  }


  private void updateCancelCont(RCTracker tracker, Continuation cont,
      List<Var> usedRCVars, RefCountType rcType,
      Set<Var> cancelCandidates, Set<Var> consumedAfter) {
    List<Var> consumedRCVars;
    if (!cont.isAsync()) {
      consumedRCVars = Collections.emptyList();
    } else {
      if (rcType == RefCountType.READERS) {
        Collection<PassedVar> passed = cont.getPassedVars();
        if (passed.isEmpty()) {
          consumedRCVars = Collections.emptyList();
        } else {
          consumedRCVars = new ArrayList<Var>(passed.size());
          for (PassedVar pv: passed) {
            consumedRCVars.add(tracker.getRefCountVar(pv.var));
          }
        }
      } else {
        assert(rcType == RefCountType.WRITERS);
        Collection<Var> keepOpen = cont.getKeepOpenVars();
        if (keepOpen.isEmpty()) {
          consumedRCVars = Collections.emptyList();
        } else {
          consumedRCVars = new ArrayList<Var>(keepOpen.size());
          for (Var v: keepOpen) {
            consumedRCVars.add(tracker.getRefCountVar(v));
          }
        }
      }
    }
    
    for (Var usedRCVar: usedRCVars) {
      updateCancel(usedRCVar, cancelCandidates, consumedAfter, true,
                   consumedRCVars.contains(usedRCVar));
    }
    
    for (Var consumedRCVar: consumedRCVars) {
      updateCancel(consumedRCVar, cancelCandidates, consumedAfter,
                   false, true);
    }
    
  }


  private void updateCancel(Var var,
      Set<Var> cancelCandidates, Set<Var> consumedAfter,
      boolean usedHere, boolean consumedHere) {
    if (logger.isTraceEnabled()) {
      logger.trace("updateCancel " + var + " usedHere: " + usedHere 
          + " consumedHere: " + consumedHere);
    }
    
    if (consumedHere) {
      // For instructions that just consume a refcount, don't need to do
      // anything, just mark that it was consumed
      consumedAfter.add(var);
      return;
    }
    if (usedHere) {
      if (consumedAfter.contains(var)) {
        // We hold onto a refcount until later in block - ok
        return;
      } else {
        // The refcount is consumed by a prior instruction: can't safely
        // cancel
        cancelCandidates.remove(var);
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
    if (!RCUtil.piggybackEnabled()) {
      return;
    }
    
    final Set<Var> immDecrCandidates = Sets.createSet(
                        block.getVariables().size());
    for (Var blockVar : block.getVariables()) {
      if (blockVar.storage() != Alloc.ALIAS) {
        // NOTE: this block var will be it's own root since
        // it's allocated here
        assert(tracker.getRefCountVar(blockVar).equals(blockVar)) : blockVar;
        long incr = tracker.getCount(rcType, blockVar, RCDir.DECR);
        assert(incr <= 0);

        long baseRC = RefCounting.baseRefCount(blockVar, rcType, true, false);
        // -baseCount may correspond to the case when the value of the var is
        // thrown away, or where the var is never written. The exception is
        // if an instruction reads/writes the var without modifying the
        // refcount,
        // in which case we can't move the decrement to the front of the block
        // Shouldn't be less than this when var is declared in this
        // block.
        assert (incr >= -baseRC) : blockVar + " " + rcType + ": " +
                                    incr + " < base " + baseRC;
        if (incr < 0) {
          immDecrCandidates.add(blockVar);
        }
      }
    }

    // Check that the data isn't actually used in block or sync continuations
    // In the case of async continuations, there should be an increment to
    // pass the var
    UseFinder useFinder = new UseFinder(tracker, rcType, immDecrCandidates);
    useFinder.reset();
    TreeWalk.walkSyncChildren(logger, fn, block, true, useFinder);
    immDecrCandidates.removeAll(useFinder.getUsedVars());
   
    for (Var immDecrVar: immDecrCandidates) {
      assert(immDecrVar.storage() != Alloc.ALIAS) : immDecrVar;
      long incr = tracker.getCount(rcType, immDecrVar, RCDir.DECR);
      block.modifyInitRefcount(immDecrVar, rcType, incr);
      tracker.cancel(immDecrVar, rcType, -incr);
    }
  }

  /**
   * Try to piggyback decrement operations on instructions or continuations
   * in block
   * 
   * @param logger
   * @param fn
   * @param block
   * @param tracker
   * @param rcType
   */
  private void piggybackOnStatements(Logger logger, Function fn,
      Block block, RCTracker tracker, RCDir dir, RefCountType rcType) {
    if (!RCUtil.piggybackEnabled()) {
      return;
    }
    
    // Initially all decrements are candidates for piggybacking
    RefCountCandidates candidates =
        tracker.getVarCandidates(block, rcType, dir);
    if (logger.isTraceEnabled()) {
      logger.trace("Piggyback candidates: " + candidates);
    }

    UseFinder subblockWalker = new UseFinder(tracker, rcType,
                                             candidates.varKeySet());
    
    // Depending on whether it's a decrement or an increment, we need
    // to traverse statements in a different direciton so that refcounts
    // can be disqualified in the right order
    boolean reverse = (dir == RCDir.DECR);

    if (reverse) {
      piggybackOnContinuations(logger, fn, block, tracker, dir, rcType,
                               candidates, subblockWalker, reverse);
    }

    piggybackOnStatements(logger, fn, block, tracker, dir, rcType, candidates,
                          subblockWalker, reverse);
    
    if (!reverse) {
      piggybackOnContinuations(logger, fn, block, tracker, dir, rcType,
                               candidates, subblockWalker, reverse);
    }
  }


  private void piggybackOnStatements(Logger logger, Function fn, Block block,
      RCTracker tracker, RCDir dir, RefCountType rcType,
      RefCountCandidates candidates, UseFinder subblockWalker, boolean reverse) {
    // Vars where we were successful
    List<VarCount> successful = new ArrayList<VarCount>();

    // scan up from bottom of block instructions to see if we can piggyback
    ListIterator<Statement> it = reverse ? block.statementEndIterator()
                                          : block.statementIterator();
    while ((reverse && it.hasPrevious()) || (!reverse && it.hasNext())) {
      Statement stmt;
      if (reverse) {
        stmt = it.previous();
      } else {
        stmt = it.next();
      }
      
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction inst = stmt.instruction();

          if (logger.isTraceEnabled()) {
            logger.trace("Try piggyback " + dir + " on " + inst);
          }
          
          VarCount piggybacked;
          do {
            /* Process one at a time so that candidates is correctly updated
             * for each call based on previous changes */
            piggybacked = inst.tryPiggyback(candidates, rcType);
          
            if (piggybacked != null && piggybacked.count != 0) {
              if (logger.isTraceEnabled()) {
                logger.trace("Piggybacked decr " + piggybacked + " on " + inst);
              }

              candidates.add(piggybacked.var, -piggybacked.count);
              successful.add(piggybacked);
            }
          } while (piggybacked != null && piggybacked.count != 0);
            
          // Make sure we don't modify before a use of the var by removing
          // from candidate set
          List<Var> used = findUses(inst, tracker, rcType,
                                    candidates.varKeySet());
          removeCandidates(used, tracker, candidates);
          break;
        }
        case CONDITIONAL:
          // Walk continuation to find usages
          subblockWalker.reset();
          TreeWalk.walkSyncChildren(logger, fn, stmt.conditional(), subblockWalker);
          removeCandidates(subblockWalker.getUsedVars(), tracker, candidates);
          break;
        default:
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }

    if (logger.isTraceEnabled()) {
      logger.trace(successful);
    }
    // Update main increments map
    for (VarCount vc: successful) {
      assert(vc != null);
      tracker.cancel(tracker.getRefCountVar(vc.var), rcType, -vc.count);
    }
  }


  private void piggybackOnContinuations(Logger logger, Function fn,
      Block block, RCTracker tracker, RCDir dir, RefCountType rcType,
      RefCountCandidates candidates, UseFinder subblockWalker, boolean reverse) {
    // Try to piggyback on continuations, starting at bottom up
    ListIterator<Continuation> cit = reverse ? block.continuationEndIterator() 
                                              : block.continuationIterator();
    while ((reverse && cit.hasPrevious()) || (!reverse && cit.hasNext())) {
      Continuation cont;
      if (reverse) {
        cont = cit.previous();
      } else {
        cont = cit.next();
      }

      if (RCUtil.isAsyncForeachLoop(cont)) {
        AbstractForeachLoop loop = (AbstractForeachLoop) cont;
        
        VarCount piggybacked;
        do {
          /* Process one at a time so that candidates is correctly updated
           * for each call based on previous changes */
          piggybacked = loop.tryPiggyBack(candidates, rcType, dir);

          if (piggybacked != null) {
            if (logger.isTraceEnabled()) {
              logger.trace("Piggybacked on foreach: " + piggybacked + " " +
                     rcType + " " + piggybacked.count);
            }
            candidates.add(piggybacked.var, -piggybacked.count);
            tracker.cancel(tracker.getRefCountVar(piggybacked.var), rcType,
                           -piggybacked.count);
          }
        } while (piggybacked != null);
      }

      // Walk continuation to find usages
      subblockWalker.reset();
      TreeWalk.walkSyncChildren(logger, fn, cont, subblockWalker);
      removeCandidates(subblockWalker.getUsedVars(), tracker, candidates);
    }
  }

  /**
   * Find uses of refcounted variables in continuations.
   * Note that we only count root variables i.e. returned by getRefcountVar()
   * in RCTracker
   */
  private final class UseFinder extends TreeWalker {
    private final RCTracker tracker;
    private final RefCountType rcType;
    private final Set<Var> varCandidates; 
    
    /**
     * List into which usages are accumulated.  Must
     * be reset by caller
     */
    private final ArrayList<Var> varAccum;
    
    private UseFinder(RCTracker tracker, RefCountType rcType,
            Set<Var> varCandidates) {
      this.tracker = tracker;
      this.rcType = rcType;
      this.varCandidates = varCandidates;
      this.varAccum = new ArrayList<Var>();
    }
  
    public void reset() {
      this.varAccum.clear();
    }
    
    public void visit(Continuation cont) {
      findUsesNonRec(cont, tracker, rcType, varCandidates, varAccum);
    }
  
    public void visit(Instruction inst) {
      findUses(inst, tracker, rcType, varCandidates, varAccum);
    }
    
    public List<Var> getUsedVars() {
      return varAccum;
    }
  }


  private List<Var> findUses(Instruction inst, RCTracker tracker,
              RefCountType rcType, Set<Var> candidates) {
    ArrayList<Var> res = new ArrayList<Var>();
    findUses(inst, tracker, rcType, candidates, res);
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
      RefCountType rcType, Set<Var> varCandidates,  List<Var> varAccum) {
    if (rcType == RefCountType.READERS) {
      for (Arg in : inst.getInputs()) {
        if (in.isVar()) {
          updateUses(in.getVar(), tracker, varCandidates, varAccum);
        }
      }
      for (Var read : inst.getReadOutputs(functionMap)) {
        updateUses(read, tracker, varCandidates, varAccum);
      }
    } else {
      assert (rcType == RefCountType.WRITERS);
      for (Var modified : inst.getOutputs()) {
        updateUses(modified, tracker, varCandidates, varAccum);
      }
    }    
  }
  
  private void findUsesNonRec(Continuation cont, RCTracker tracker,
      RefCountType rcType, Set<Var> varCandidates, ArrayList<Var> varAccum) {
    if (rcType == RefCountType.READERS) {
      for (Var v: cont.requiredVars(false)) {
        updateUses(v, tracker, varCandidates, varAccum);
      }
      if (cont.isAsync() && rcType == RefCountType.READERS) {
        for (PassedVar pv: cont.getPassedVars()) {
          if (!pv.writeOnly) {
            updateUses(pv.var, tracker, varCandidates, varAccum);
          }
        }
      } else if (cont.isAsync() && rcType == RefCountType.WRITERS) {
        for (Var v: cont.getKeepOpenVars()) {
          updateUses(v, tracker, varCandidates, varAccum);
        }
      }
    }
    // Foreach loops have increments attached to them,
    // can't prematurely decrement
    if (RCUtil.isForeachLoop(cont)) {
      AbstractForeachLoop loop = (AbstractForeachLoop) cont;
      for (RefCount rc: loop.getStartIncrements()) {
        if (rc.type == rcType) {
          updateUses(rc.var, tracker, varCandidates, varAccum);
        }
      }
    }
  }


  private void updateUses(Var v, RCTracker tracker, Set<Var> varCandidates,
                          List<Var> varAccum) {
    if (varCandidates != null && varCandidates.contains(v)) {
      varAccum.add(tracker.getRefCountVar(v));
    }
  }
  
  private void removeCandidates(Collection<Var> vars, RCTracker tracker,
                                RefCountCandidates candidates) {
    for (Var key: vars) {
      if (logger.isTraceEnabled()) {
        logger.trace("Remove candidate " + key);
      }
      candidates.reset(key);
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
  private void batchDecrementsForeach(Block block,
      RCTracker increments, RefCountType type) {
    if (!RCUtil.batchEnabled()) {
      return;
    }
    
    assert (block.getType() != BlockType.MAIN_BLOCK);
    Continuation parent = block.getParentCont();
    AbstractForeachLoop loop = (AbstractForeachLoop) parent;
    Counters<Var> changes = new Counters<Var>();
    for (Entry<AliasKey, Long> e : increments.rcIter(type, RCDir.DECR)) {
      Var var = increments.getRefCountVar(e.getKey());
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
      Var var = increments.getRefCountVar(e.getKey());
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
      assert (RefCounting.trackRefCount(var, type));
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
    if (logger.isTraceEnabled()) {
      logger.trace("Leftover increments to add at top:");
      logger.trace("==============================");
      logger.trace(increments);
    }
    
    // Next try to just put at top of block
    Iterator<Entry<AliasKey, Long>> it =
        increments.rcIter(rcType, RCDir.INCR).iterator();
    while (it.hasNext()) {
      Entry<AliasKey, Long> e = it.next();
      Var var = increments.getRefCountVar(e.getKey());
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
  private Set<Var> findConditionalInitAliases(Conditional cond) {
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

  private void addIncrForVar(Block block, RCTracker increments,
      RefCountType rcType, ListIterator<Statement> stmtIt, Var out) {
    // Alias var must be set at this point, insert refcount instruction
    long incr = increments.getCount(rcType, out, RCDir.INCR);
    assert (incr >= 0);
    if (incr > 0) {
      insertIncrAfter(block, stmtIt, out, incr, rcType);
    }
    increments.reset(rcType, out, RCDir.INCR);
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
    if (!RCUtil.piggybackEnabled()) {
      return;
    }
    
    for (Var blockVar : block.getVariables()) {
      if (blockVar.storage() != Alloc.ALIAS) {
        long incr = increments.getCount(rcType, blockVar, RCDir.INCR);
        assert(incr >= 0);
        if (incr > 0) {
          assert(RefCounting.trackRefCount(blockVar, rcType)) : blockVar;
          block.modifyInitRefcount(blockVar, rcType, incr);
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
