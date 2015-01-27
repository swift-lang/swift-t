package exm.stc.ic.refcount;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.RefCount;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.util.Counters;
import exm.stc.common.util.Pair;
import exm.stc.ic.aliases.AliasKey;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.opt.OptimizerPass;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ForeachLoops.AbstractForeachLoop;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalVars;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.TurbineOp.RefCountOp;
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;

/**
 * Eliminate, merge and otherwise reduce read/write reference counting
 * operations. Run as a post-processing step.
 *
 * Additional unimplemented ideas:
 * - Pushing down reference decrements to blocks where they can be merged
 */
public class RefcountPass implements OptimizerPass {

  private Logger logger = null;

  static final List<RefCountType> RC_TYPES = Arrays.asList(
      RefCountType.READERS, RefCountType.WRITERS);
  /**
   * Map of names to functions, used inside pass. Must be initialized before
   * pass runs.
   */
  private Map<String, Function> functionMap = null;

  private RCPlacer placer = null;

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
    this.logger = logger;

    functionMap = program.getFunctionMap();
    placer = new RCPlacer(functionMap);

    for (Function f: program.functions()) {
      logger.trace("Entering function " + f.name());
      recurseOnBlock(logger, program.globalVars(), f, f.mainBlock(),
                     new RCTracker(), new TopDownInfo());
    }

    this.functionMap = null;
    this.placer = null;
  }

  private void recurseOnBlock(Logger logger, GlobalVars globals, Function f,
      Block block, RCTracker increments, TopDownInfo parentInfo) {

    /*
     * Traverse in bottom-up order so that we can access refcount info in child
     * blocks for pulling up
     */

    // Make a child copy for first pass over this block
    TopDownInfo info = parentInfo.makeChild();
    for (Statement stmt: block.getStatements()) {
      switch (stmt.type()) {
        case INSTRUCTION: {
          // Add information that we'll want to know about in child blocks
          info.updateForInstruction(stmt.instruction());
          break;
        }
        case CONDITIONAL: {
          // Recurse on conditionals to add refcounts
          recurseOnCont(logger, globals, f, stmt.conditional(), info);
          break;
        }
        default:
          throw new STCRuntimeError("Unknown type " + stmt.type());
      }
    }

    // Recurse on remaining continuations to add refcounts
    for (Continuation cont: block.getContinuations()) {
      recurseOnCont(logger, globals, f, cont, info);
    }

    // Now add refcounts to this block
    processBlock(logger, globals, f, block, increments, parentInfo);
  }

  private void recurseOnCont(Logger logger, GlobalVars globals, Function f,
      Continuation cont, TopDownInfo info) {

    for (Block block: cont.getBlocks()) {
      // Build separate copy for each block
      TopDownInfo contInfo = info.makeChild(cont);

      RCTracker increments = new RCTracker(contInfo.aliases);
      addDecrementsBlocksInsideCont(cont, increments);

      recurseOnBlock(logger, globals, f, block, increments, contInfo);
    }


    // Try to batch increments for foreach loop
    if (RCUtil.isForeachLoop(cont)) {
      postprocessForeach((AbstractForeachLoop)cont);
    }
  }

  /**
   *
   * @param logger
   * @param fn
   * @param block
   * @param increments
   *          pre-existing decrements
   * @param parentAssignedAliasVars
   *          assign alias vars from parent blocks that we can immediately
   *          manipulate refcount of
   */
  private void processBlock(Logger logger, GlobalVars globals, Function fn,
      Block block, RCTracker increments, TopDownInfo parentInfo) {
    // First collect up all required reference counting ops in block
    countBlockIncrements(block, increments);

    countBlockDecrements(fn, block, increments);

    if (RCUtil.mergeEnabled()) {
      // Second put saved refcounts back into IC
      placeRefcounts(logger, globals, fn, block, increments,
                      parentInfo.initAliasVars);
    }

  }


  /**
   * Handle foreach loop as special case where we want to increment <# of
   * iterations> * <needed increments inside loop> before loop.
   *
   * If the batching optimization is enabled, this function will work out
   * if it can pull out increments from foreach loop body
   */
  private void postprocessForeach(AbstractForeachLoop loop) {
    Counters<Var> readIncrs = new Counters<Var>();
    Counters<Var> writeIncrs = new Counters<Var>();

    if (loop.isAsync()) {
      // If we're spawning off, increment once per iteration so that
      // each parallel task has a refcount to work with
      for (PassedVar v: loop.getAllPassedVars()) {
        if (!v.writeOnly && RefCounting.trackReadRefCount(v.var)) {
          readIncrs.increment(v.var);
        }
      }
      for (Var v: loop.getKeepOpenVars()) {
        if (RefCounting.trackWriteRefCount(v)) {
          writeIncrs.increment(v);
        }
      }
    }

    if (RCUtil.batchEnabled()) {
      // Optionally try to batch increments from body
      removeIncrementsForeachBody(loop, readIncrs, writeIncrs);
    }

    for (Entry<Var, Long> read: readIncrs.entries()) {
      loop.addStartIncrement(new RefCount(read.getKey(), RefCountType.READERS,
          Arg.newInt(read.getValue())));
    }

    for (Entry<Var, Long> write: writeIncrs.entries()) {
      loop.addStartIncrement(new RefCount(write.getKey(), RefCountType.WRITERS,
          Arg.newInt(write.getValue())));
    }
  }

  private void removeIncrementsForeachBody(AbstractForeachLoop loop,
      Counters<Var> readIncrs, Counters<Var> writeIncrs) {
    // Now see if we can pull some increments out of top of block
    Block loopBody = loop.getLoopBody();
    ListIterator<Statement> it = loopBody.statementIterator();

    while (it.hasNext()) {
      Statement stmt = it.next();
      // stop processing once we get past the initial increments
      if (stmt.type() != StatementType.INSTRUCTION ||
          !RefCountOp.isIncrement(stmt.instruction().op)) {
        break;
      }

      Instruction inst = stmt.instruction();
      Arg amount = RefCountOp.getRCAmount(inst);
      Var var = RefCountOp.getRCTarget(inst);
      RefCountType rcType = RefCountOp.getRCType(inst.op);
      if (amount.isInt() && RCUtil.definedOutsideCont(loop, loopBody, var)) {
        // Pull up constant increments
        if (rcType == RefCountType.READERS) {
          readIncrs.add(var, amount.getInt());
        } else {
          assert (rcType == RefCountType.WRITERS);
          writeIncrs.add(var, amount.getInt());
        }
        it.remove();
      }
    }
  }


  private void placeRefcounts(Logger logger, GlobalVars globals, Function fn,
      Block block, RCTracker increments, Set<Var> parentAssignedAliasVars) {

    // First canonicalize so we can merge refcounts
    increments.canonicalize();

    if (logger.isTraceEnabled()) {
      logger.trace("");
      logger.trace("Adding increments for block " + block.getType() + " of " +
          fn.name());
      logger.trace("==============================");
      logger.trace(increments);
    }

    reorderContinuations(logger, block);

    // Move any increment instructions up to this block
    // if they can be combined with increments here
    pullUpRefIncrements(block, increments);

    placer.placeAll(logger, globals, fn, block, increments,
                    parentAssignedAliasVars);
  }

  /**
   * Work out how much each variable needs to be incremented.
   *
   * In some cases this will place the increment instructions right away
   *
   * @param block
   * @param increments
   */
  private void countBlockIncrements(Block block, RCTracker increments) {
    ListIterator<Statement> stmtIt = block.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      switch (stmt.type()) {
        case INSTRUCTION:
          Instruction inst = stmt.instruction();
          updateCountsInst(inst, increments);
          increments.updateForInstruction(inst);
          break;
        case CONDITIONAL:
          updateIncrementsPassIntoCont(stmt.conditional(), increments);
          break;
        default:
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }

      if (!RCUtil.mergeEnabled()) {
        placer.dumpIncrements(stmt, block, stmtIt, increments);
      }
    }
    for (Continuation cont: block.getContinuations()) {
      updateIncrementsPassIntoCont(cont, increments);
      if (!RCUtil.mergeEnabled()) {
        placer.dumpIncrements(null, block, block.statementEndIterator(),
            increments);
      }
    }
  }

  /**
   * Work out how much each variable needs to be decrementent.
   *
   * In some cases this will place the decrement instructions right away
   *
   * @param block
   * @param increments
   */
  private void countBlockDecrements(Function fn, Block block,
      RCTracker increments) {

    // If this is main block of function, add passed in
    if (block.getType() == BlockType.MAIN_BLOCK) {
      assert (block == fn.mainBlock());
      if (fn.isAsync()) {
        // Need to do bookkeeping if this runs in separate task
        for (Var i: fn.getInputList()) {
          increments.readDecr(i);
          if (Types.isScalarUpdateable(i) && RefCounting.WRITABLE_UPDATEABLE_INARGS) {
            increments.writeDecr(i);
          }
        }
        for (PassedVar o: fn.getPassedOutputList()) {
          increments.writeDecr(o.var);

          // Outputs might be read in function, need to maintain that
          // refcount
          if (!o.writeOnly) {
            increments.readDecr(o.var);
          }
        }
      }
    }

    for (Var var: block.variables()) {
      // Any variables allocated in block will need to be freed when
      // they go out of scope, so do initial decrement of reference counts.
      // Alias variables aren't allocated here. Struct variables have
      // members separately allocated, so don't want to double-decrement
      // struct members.
      if (var.storage() != Alloc.ALIAS &&
          (var.storage() != Alloc.GLOBAL_VAR || OptUtil.isEntryBlock(fn, block))) {
        // Work out base refcounts that we'll have to start with for var
        long baseReadRC = RefCounting.baseReadRefCount(var, true, false);
        long baseWriteRC = RefCounting.baseWriteRefCount(var, true, false);
        increments.readDecr(var, baseReadRC);
        increments.writeDecr(var, baseWriteRC);
        if (logger.isTraceEnabled()) {
          logger.trace(var + " base r: " + baseReadRC + " w: " + baseWriteRC);
        }
      }
    }

    ListIterator<CleanupAction> caIt = block.cleanupIterator();
    while (caIt.hasNext()) {
      CleanupAction ca = caIt.next();
      Instruction action = ca.action();
      if (RefCountOp.isRefcountOp(action.op) &&
          RefCountOp.isDecrement(action.op)) {
        Var decrVar = RefCountOp.getRCTarget(action);
        Arg amount = RefCountOp.getRCAmount(action);
        if (amount.isInt()) {
          // Remove instructions where counts is integer value
          RefCountType rcType = RefCountOp.getRCType(action.op);
          increments.decr(decrVar, rcType, amount.getInt());
          caIt.remove();
        }
      }
    }

    if (!RCUtil.mergeEnabled()) {
      placer.dumpDecrements(block, increments);
    }
  }

  /**
   * Update reference count counters based on refcounts consumed and
   * returned by instructions
   * @param inst
   * @param increments
   */
  private void updateCountsInst(Instruction inst, RCTracker increments) {
    Pair<List<VarCount>, List<VarCount>> inRefs;
    inRefs = inst.inRefCounts(functionMap);

    List<VarCount> inReadRefs = inRefs.val1;
    List<VarCount> inWriteRefs = inRefs.val2;

    for (VarCount vc: inReadRefs) {
      increments.readIncr(vc.var, vc.count);
    }
    for (VarCount vc: inWriteRefs) {
      increments.writeIncr(vc.var, vc.count);
    }


    Pair<List<VarCount>, List<VarCount>> outRefs;
    outRefs = inst.outRefCounts(functionMap);

    List<VarCount> outReadRefs = outRefs.val1;
    List<VarCount> outWriteRefs = outRefs.val2;

    for (VarCount vc: outReadRefs) {
      increments.readDecr(vc.var, vc.count);
    }

    for (VarCount vc: outWriteRefs) {
      increments.writeDecr(vc.var, vc.count);
    }

    if (logger.isTraceEnabled()) {
      logger.trace("At " + inst +
          " inReadRefs: " + inReadRefs + " inWriteRefs: " + inWriteRefs +
          " outReadRefs: " + outReadRefs + " outWriteRefs: " + outWriteRefs);
    }
  }

  /**
   * Update increments for variables passed into continuation
   *
   * @param cont
   * @param increments
   */
  private void updateIncrementsPassIntoCont(Continuation cont,
      RCTracker increments) {
    if (cont.spawnsSingleTask()) {
      long incr = 1;  // Increment once per task

      // Track those already added
      Set<Var> alreadyAdded = new HashSet<Var>();

      for (Var keepOpen: cont.getKeepOpenVars()) {
        assert (RefCounting.trackWriteRefCount(keepOpen));
        alreadyAdded.add(keepOpen);
        increments.writeIncr(keepOpen, incr);
      }

      for (PassedVar passedIn: cont.getAllPassedVars()) {
        logger.trace(cont.getType() + ": passedIn: " + passedIn.var.name());
        if (!passedIn.writeOnly && RefCounting.trackReadRefCount(passedIn.var)) {
          increments.readIncr(passedIn.var, incr);
          alreadyAdded.add(passedIn.var);
        }
      }
      for (BlockingVar blockingVar: cont.blockingVars(false)) {
        if (RefCounting.trackReadRefCount(blockingVar.var) &&
             !alreadyAdded.contains(blockingVar.var)) {
          increments.readIncr(blockingVar.var, incr);
        }
      }
    } else if (RCUtil.isForeachLoop(cont)) {
      AbstractForeachLoop foreach = (AbstractForeachLoop) cont;
      updateIncrementsPassIntoForeach(increments, foreach);
    }
  }

  private void updateIncrementsPassIntoForeach(RCTracker increments,
      AbstractForeachLoop foreach) {
    long iterCount = foreach.constIterCount();
    if (iterCount >= 0) {
      // Constant iteration count: can hoist out increment
      ListIterator<RefCount> it = foreach.startIncrementIterator();
      while (it.hasNext()) {
        RefCount rc = it.next();
        if (rc.amount.isInt()) {
          increments.incr(rc.var, rc.type, iterCount * rc.amount.getInt());
          it.remove();
        }
      }
    }

    if (foreach.isAsync()) {
      /*
       * Hold a refcount until we get to this loop: do a switcheroo where
       * we pull an increment into the outer block, and balance by adding
       * a decrement to loop.
       */
      for (RefCount rc: foreach.getStartIncrements()) {
        increments.incr(rc.var, rc.type, 1);
        foreach.addConstantStartIncrement(rc.var, rc.type, Arg.newInt(-1));
      }
    }
  }

  /**
   * Try to bring reference increments out from inner blocks. The observation is
   * that increments can safely be done earlier, so if a child wait increments
   * something, then we can just do it here.
   *
   * We always pull up reference increments if possible in the hope we can
   * cancel them or find a better place put them.  This means that increments
   * will "float" up to the topmost block if possible. We can't really
   * do worse than the explicit increment.
   *
   * @param block
   * @param increments
   * @param rootBlock if this is the root block we're pulling increments into
   */
  private void pullUpRefIncrements(Block rootBlock, RCTracker increment) {
    if (!RCUtil.hoistEnabled()) {
      return;
    }

    for (Statement stmt: rootBlock.getStatements()) {
      if (stmt.type() == StatementType.CONDITIONAL) {
        pullUpRefIncrements(stmt.conditional(), increment);
      } else {
        assert(stmt.type() == StatementType.INSTRUCTION);
        // Do nothing
      }
    }

    for (Continuation cont: rootBlock.getContinuations()) {
      pullUpRefIncrements(cont, increment);
    }
   }

  private void pullUpRefIncrements(Continuation cont, RCTracker increment) {
    if (cont.executesBlockOnce()) {
      for (Block inner: cont.getBlocks()) {
        findPullupIncrements(inner, increment, true);
        if (!cont.isAsync() && !cont.runLast()) {
          findPullupDecrements(inner, increment, true);
        }
      }
    } else if (cont.isExhaustiveSyncConditional()) {
      // Find increments that occur on every branch
      pullUpBranches(cont.getBlocks(), !cont.runLast(), increment);
    }

  }

  /**
   * Remove positive constant increment instructions from block and update
   * counters.
   *
   * @param branches blocks that cover execution paths mutually exclusively and
   *                 exhaustively
   * @param runsBeforeCleanups if the continuation executes synchronously before
   *                          cleanups
   * @param increments
   *          counters for a block
   */
  private void pullUpBranches(List<Block> branches, boolean runsBeforeCleanups,
                                    RCTracker increments) {
    RCTracker allBranchIncrements = new RCTracker(increments.getAliases());

    // Find intersection of increments before removing anything
    findPullupIncrements(branches.get(0), allBranchIncrements, false);
    if (runsBeforeCleanups) {
      findPullupDecrements(branches.get(0), allBranchIncrements, false);
    }

    for (int i = 1; i < branches.size(); i++) {
      Block branch = branches.get(i);
      RCTracker tmpBranchIncrements = new RCTracker(increments.getAliases());
      findPullupIncrements(branch, tmpBranchIncrements, false);
      if (runsBeforeCleanups) {
        findPullupDecrements(branch, tmpBranchIncrements, false);
      }

      // Check for intersection
      for (RefCountType rcType: RefcountPass.RC_TYPES) {
        for (RCDir dir: RCDir.values()) {
          Iterator<Entry<AliasKey, Long>> it;
          it = allBranchIncrements.rcIter(rcType, dir).iterator();
          while (it.hasNext()) {
            Entry<AliasKey, Long> e = it.next();
            AliasKey key = e.getKey();
            long amount1 = e.getValue();
            long amount2 = tmpBranchIncrements.getCount(rcType, key, dir);
            // Update to minimum of the two
            if (amount1 == 0 || amount2 == 0) {
              it.remove();
            } else if (dir == RCDir.INCR){
              assert(amount1 > 0);
              assert(amount2 > 0);
              e.setValue(Math.min(amount1, amount2));
            } else {
              assert(dir == RCDir.DECR);
              assert(amount1 < 0);
              assert(amount2 < 0);
              e.setValue(Math.max(amount1, amount2));
            }
          }
        }
      }
    }

    // Remove increments from blocks
    for (Block branch: branches) {
      removePullupIncrements(branch, allBranchIncrements);
      if (runsBeforeCleanups) {
        removePullupDecrements(branch, allBranchIncrements);
      }
    }

    // Apply changes to parent increments
    for (RefCountType rcType: RefcountPass.RC_TYPES) {
      for (RCDir dir: RCDir.values()) {
        for (Entry<AliasKey, Long> e: allBranchIncrements.rcIter(rcType, dir)) {
          increments.incrDirect(e.getKey(), rcType, e.getValue());
        }
      }
    }
  }

  private void findPullupIncrements(Block block, RCTracker increments,
                              boolean removeInstructions) {
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction inst = stmt.instruction();
          if (RefCountOp.isIncrement(inst.op)) {
            Var v = RefCountOp.getRCTarget(inst);
            Arg amountArg = RefCountOp.getRCAmount(inst);
            RefCountType rcType = RefCountOp.getRCType(inst.op);
            if (amountArg.isInt()) {
              long amount = amountArg.getInt();
              if (!block.declaredHere(v)) {
                // Check already being manipulated in this block
                // Pull-up by default, if declared outside this block
                increments.incr(v, rcType, amount);
                if (removeInstructions) {
                  it.remove();
                }
              }
            }
          }
          break;
        }
        case CONDITIONAL:
          // Don't try to aggregate these increments here
          break;
        default:
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }
  }

  private void removePullupIncrements(Block block, RCTracker toRemove) {
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      switch (stmt.type()) {
      case INSTRUCTION: {
        Instruction inst = stmt.instruction();
        if (RefCountOp.isIncrement(inst.op)) {
          Var v = RefCountOp.getRCTarget(inst);
          Arg amountArg = RefCountOp.getRCAmount(inst);
          RefCountType rcType = RefCountOp.getRCType(inst.op);
          if (amountArg.isInt()) {
            long amount = amountArg.getInt();
            long toRemoveAmount = toRemove.getCount(rcType, v, RCDir.INCR);
            assert (toRemoveAmount <= amount && toRemoveAmount >= 0);
            if (toRemoveAmount > 0) {
              logger.trace("hoisted " + v.name() + ":" + rcType + " +"
                            + toRemoveAmount);
              it.remove();
              if (toRemoveAmount < amount) {
                // Need to replace with reduced refcount
                Arg newAmount = Arg.newInt(amount - toRemoveAmount);
                it.add(RefCountOp.incrRef(rcType, v, newAmount));
              }
            }
          }
        }
        break;
      }
      case CONDITIONAL:
        // Don't try to aggregate these increments here
        break;
      default:
        throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }
  }


  private void findPullupDecrements(Block block, RCTracker increments,
      boolean removeInstructions) {
    ListIterator<CleanupAction> it = block.cleanupIterator();
    while (it.hasNext()) {
      CleanupAction ca = it.next();
      Instruction inst = ca.action();
      if (RefCountOp.isDecrement(inst.op)) {
        Var v = RefCountOp.getRCTarget(inst);
        Arg amountArg = RefCountOp.getRCAmount(inst);
        RefCountType rcType = RefCountOp.getRCType(inst.op);
        if (amountArg.isInt()) {
          long amount = amountArg.getInt();
          assert(amount >= 0);
          if (!block.declaredHere(v)) {
            // Check already being manipulated in this block
            // Pull-up by default, if declared outside this block
            increments.incr(v, rcType, -amount);
            if (removeInstructions) {
              it.remove();
            }
          }
        }
      }
    }
  }

  private void removePullupDecrements(Block block, RCTracker toRemove) {
    ListIterator<CleanupAction> it = block.cleanupIterator();
    while (it.hasNext()) {
      CleanupAction ca = it.next();
      Instruction inst = ca.action();
      if (RefCountOp.isDecrement(inst.op)) {
        Var v = RefCountOp.getRCTarget(inst);
        Arg amountArg = RefCountOp.getRCAmount(inst);
        RefCountType rcType = RefCountOp.getRCType(inst.op);
        if (amountArg.isInt()) {
          long amount = amountArg.getInt();
          // Ensure both positive
          long toRemoveAmount = -1 * toRemove.getCount(rcType, v, RCDir.DECR);
          logger.trace("hoisted " + v.name() + ":" + rcType + " -"
                  + toRemoveAmount);
          assert (toRemoveAmount >= 0 && toRemoveAmount <= amount);
          if (toRemoveAmount > 0) {
            it.remove();
            if (toRemoveAmount < amount) {
              // Need to replace with reduced refcount
              Arg newAmount = Arg.newInt(amount - toRemoveAmount);
              Instruction newDecr = RefCountOp.decrRef(rcType, v, newAmount);
              it.add(new CleanupAction(ca.var(), newDecr));
            }
          }
        }
      }
    }
  }

  /**
   * Add decrements that have to happen for continuation inside block
   *
   * @param cont
   * @param increments
   */
  private void addDecrementsBlocksInsideCont(Continuation cont,
      RCTracker increments) {
    // Get passed in variables decremented inside block
    // Loops don't need this for passed in variables as they decrement refs
    // at loop_break instruction
    if ((cont.spawnsSingleTask() || RCUtil.isAsyncForeachLoop(cont)) &&
        cont.getType() != ContinuationType.LOOP) {
      addDecrementsAsyncCont(cont, increments);
    } else if (cont.getType() == ContinuationType.IF_STATEMENT) {
      Continuation parentCont = cont.parent().getParentCont();
      // This is a slightly hacky way to handle references in a loop.
      // The basic idea is that we want to push down references to either
      // branch of the conditional. In the standard of the loop a conditional
      // will be the last thing in the loop body. We handle this case
      // specifically, as we generally get better results pushing down the
      // decrements to both branches
      if (parentCont != null && parentCont.getType() == ContinuationType.LOOP &&
          getConditionalAtEnd((Loop) parentCont) == cont) {
        addDecrementsLoopStmt((Loop) parentCont, increments);
      }
    } else if (cont.getType() == ContinuationType.LOOP) {
      // Corner case: loop where we can't safely just increment on both branches
      // of
      // if statement. This should be valid in cases opposite to above:
      // where last thing in loop body isn't an if statement.
      Loop loop = (Loop) cont;
      if (getConditionalAtEnd(loop) == null) {
        addDecrementsLoopStmt(loop, increments);
      }
    }
  }

  private void addDecrementsAsyncCont(Continuation cont, RCTracker increments) {
    long amount = 1;

    Set<Var> alreadyAdded = new HashSet<Var>();
    for (Var keepOpen: cont.getKeepOpenVars()) {
      increments.writeDecr(keepOpen, amount);
      alreadyAdded.add(keepOpen);
    }

    for (PassedVar passedIn: cont.getAllPassedVars()) {
      if (!passedIn.writeOnly && RefCounting.trackReadRefCount(passedIn.var)) {
        increments.readDecr(passedIn.var, amount);
        alreadyAdded.add(passedIn.var);
      }
    }

    // Hold read reference for wait var if not already present
    for (BlockingVar blockingVar: cont.blockingVars(false)) {
      if (RefCounting.trackReadRefCount(blockingVar.var) &&
          !alreadyAdded.contains(blockingVar.var)) {
        increments.readDecr(blockingVar.var, amount);
      }
    }
  }

  /**
   * Add decrements for loop
   *
   * @param loop
   * @param increments
   */
  private void addDecrementsLoopStmt(Loop loop, RCTracker increments) {
    // Decrement read reference count of all loop iteration vars
    // (which are read-only)
    for (Var v: loop.getLoopVars()) {
      increments.readDecr(v);
    }
  }

  /**
   * Very specific utility function: returns a conditional statement if last
   * thing executed in loop body
   *
   * @param loop
   * @return
   */
  private Conditional getConditionalAtEnd(Loop loop) {
    Block loopBody = loop.getLoopBody();
    if (loopBody.getStatements().size() != 0) {
      Statement lastStmt = loopBody.statementEndIterator().previous();
      if (lastStmt.type() == StatementType.CONDITIONAL) {
        return lastStmt.conditional();
      }
    }
    return null;
  }

  /**
   * Place sync continuations (which may use var but not require refcount
   * increment) first to maximize chance of being able to piggyback things.
   */
  private void reorderContinuations(Logger logger, Block block) {
    ListIterator<Continuation> cIt = block.continuationIterator();
    List<Continuation> syncContinuations = new ArrayList<Continuation>();
    while (cIt.hasNext()) {
      Continuation cont = cIt.next();
      if (!cont.isAsync() && !cont.runLast()) {
        cIt.remove();
        syncContinuations.add(cont);
      }
    }

    cIt = block.continuationIterator();
    for (Continuation sync: syncContinuations) {
      cIt.add(sync);
    }
  }
}
