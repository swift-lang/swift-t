package exm.stc.ic.refcount;

import java.util.ArrayList;
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

  /**
   * Add decrement operations to block, performing optimized placement
   * where possible
   * @param logger
   * @param fn
   * @param block
   * @param increments
   * @param type
   */
  public void placeDecrements(Logger logger, Function fn, Block block,
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
  public void placeIncrements(Block block, RCTracker increments,
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
      for (Entry<AliasKey, Long> e: increments.rcIter(rcType)) {
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
      for (Entry<AliasKey, Long> e: increments.rcIter(rcType)) {
        Var var = increments.getRefCountVar(block, e.getKey(), true);
        Long incr = e.getValue();
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
   * Try to piggyback reference decrements onto var declarations, for example if
   * a var is never read or written
   * 
   * @param block
   * @param increments
   *          updated to reflect changes
   * @param type
   */
  private void piggybackDecrementsOnDeclarations(Logger logger, Function fn,
      Block block, final RCTracker increments, final RefCountType rcType) {
    final Set<AliasKey> immDecrCandidates = Sets.createSet(
                        block.getVariables().size());
    for (Var blockVar : block.getVariables()) {
      if (blockVar.storage() != Alloc.ALIAS) {
        AliasKey countKey = increments.getCountKey(blockVar);
        long incr = increments.getCount(rcType, countKey);
        // -1 may correspond to the case when the value of the var is
        // thrown away, or where the var is never written. The exception is
        // if an instruction reads/writes the var without modifying the
        // refcount,
        // in which case we can't move the decrement to the front of the block
        // Shouldn't be less than this when var is declared in this
        // block.
        assert (incr >= -1) : blockVar + " " + incr;
        if (incr == -1) {
          immDecrCandidates.add(countKey);
        }
      }
    }

    // Check that the data isn't actually used in block or sync continuations
    TreeWalk.walkSyncChildren(logger, fn, block, true, new TreeWalker() {
      public void visit(Continuation cont) {
        removeDecrCandidatesNonRec(cont, rcType, immDecrCandidates, null,
                                                             increments);
      }

      public void visit(Instruction inst) {
        removeDecrCandidates(inst, rcType, immDecrCandidates, null,
                                                       increments);
      }
    });

    for (AliasKey key: immDecrCandidates) {
      Var immDecrVar = increments.getRefCountVar(block, key, true);
      assert(immDecrVar.storage() != Alloc.ALIAS) : immDecrVar;
      block.setInitRefcount(immDecrVar, rcType, 0);
      increments.incr(immDecrVar, rcType, 1);
    }
  }

  private void piggybackDecrementsOnInstructions(Logger logger, Function fn,
      Block block, final RCTracker tracker, final RefCountType rcType) {
    // Initially all increments are candidates for piggybacking
    final Counters<Var> candidates = tracker.getVarCandidates(block, rcType);

    // Remove any candidates from synchronous children that might
    // read/write the variables
    TreeWalker subblockWalker = new TreeWalker() {
      public void visit(Continuation cont) {
        removeDecrCandidatesNonRec(cont, rcType, null, candidates.keySet(),
                                   tracker);
      }

      public void visit(Instruction inst) {
        removeDecrCandidates(inst, rcType, null, candidates.keySet(), tracker);
      }
    };

    
    // Try to piggyback on continuations, starting at bottom up
    ListIterator<Continuation> cit = block.continuationIterator(block
        .getContinuations().size());
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
        tracker.resetAll(rcType, piggybacked);
      }

      // Walk continuation to remove anything
      TreeWalk.walkSyncChildren(logger, fn, cont, subblockWalker);
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
          removeDecrCandidates(inst, rcType, null, candidates.keySet(), tracker);
          break;
        }
        case CONDITIONAL:
          removeDecrCandidatesRec(logger, fn, stmt.conditional(), rcType,
                                  null, candidates.keySet(), tracker);
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
   * Remove from candidates any variables that can't have the refcount
   * decremented before this instruction executes
   * 
   * @param inst
   * @param type
   * @param candidates
   */
  private void removeDecrCandidates(Instruction inst, RefCountType type,
      Set<AliasKey> keys, Set<Var> vars, RCTracker tracker) {
    if (type == RefCountType.READERS) {
      for (Arg in : inst.getInputs()) {
        if (in.isVar())
          removeCandidate(in.getVar(), tracker, keys, vars);
      }
      for (Var read : inst.getReadOutputs(functionMap)) {
        removeCandidate(read, tracker, keys, vars);
      }
    } else {
      assert (type == RefCountType.WRITERS);
      for (Var modified : inst.getOutputs()) {
        removeCandidate(modified, tracker, keys, vars);
      }
    }
  }

  /**
   * Remove from candidates any variables that can't have the refcount
   * decremented before this continuation starts execution
   * 
   * @param inst
   * @param type
   * @param keys if non-null, remove from this set
   * @param vars if non-null, remove from this set
   */
  private  void removeDecrCandidatesNonRec(Continuation cont,
      RefCountType type, Set<AliasKey> keys, Set<Var> vars, RCTracker tracker) {
    // Continuation will need to read, can't decrement early
    if (type == RefCountType.READERS) {
      for (Var v : cont.requiredVars(false)) {
        removeCandidate(v, tracker, keys, vars);
      }
    }
    // Foreach loops have increments attached to them,
    // can't prematurely decrement
    if (RCUtil.isForeachLoop(cont)) {
      AbstractForeachLoop loop = (AbstractForeachLoop) cont;
      for (RefCount rc : loop.getStartIncrements()) {
        if (rc.type == type) {
          removeCandidate(rc.var, tracker, keys, vars);
        }
      }
    }
  }

  private void removeCandidate(Var var, RCTracker tracker,
      Set<AliasKey> keys, Set<Var> vars) {
    if (keys != null) {
      keys.remove(tracker.getCountKey(var));
    }
    if (vars != null) {
      vars.remove(var);
    }
  }
  
  private void removeDecrCandidatesRec(final Logger logger, final Function fn,
            Continuation cont, final RefCountType type,
            final Set<AliasKey> keys, final Set<Var> vars,
            final RCTracker tracker) {
    // Check that the data isn't actually used in block or sync continuations
    TreeWalk.walk(logger, fn, cont, true, new TreeWalker() {
      public void visit(Continuation cont) {
        removeDecrCandidatesNonRec(cont, type, keys, vars, tracker);
      }

      public void visit(Instruction inst) {
        removeDecrCandidates(inst, type, keys, vars, tracker);
      }
    });
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
    for (Entry<AliasKey, Long> e : increments.rcIter(type)) {
      Var var = increments.getRefCountVar(block, e.getKey(), true);
      long count = e.getValue();
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
    increments.merge(changes, type);
  }

  private void addDecrementsAsCleanups(Block block, RCTracker increments,
      RefCountType rcType) {
    Counters<Var> changes = new Counters<Var>();
    for (Entry<AliasKey, Long> e : increments.rcIter(rcType)) {
      Var var = increments.getRefCountVar(block, e.getKey(), true);
      long count = e.getValue();
      addDecrement(block, changes, rcType, var, count);
    }
    // Build and merge to avoid concurrent modification problems
    increments.merge(changes, rcType);
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
    Iterator<Entry<AliasKey, Long>> it = increments.rcIter(rcType).iterator();
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
    long incr = increments.getCount(rcType, out);
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
        long incr = increments.getCount(rcType, blockVar);
        if (incr > 0) {
          assert(RefCounting.hasRefCount(blockVar, rcType)) : blockVar;
          block.setInitRefcount(blockVar, rcType, incr + 1);
          increments.decr(blockVar, rcType, incr);
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
