package exm.stc.ic.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.RefCount;
import exm.stc.common.Settings;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.util.Pair;
import exm.stc.ic.ICUtil;
import exm.stc.ic.refcount.RefCountsToPlace;
import exm.stc.ic.tree.ICContinuations.AbstractLoop;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.ContVarDefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.NestedBlock;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;

/**
 * Module to encapsulate the multiple styles of foreach loop in the IC
 */
public class ForeachLoops {
  public static final String indent = ICUtil.indent;

  public abstract static class AbstractForeachLoop extends AbstractLoop {
    protected final String loopName;
    protected Var loopVar;
    protected Var loopCounterVar;
    protected final int desiredUnroll;
    protected boolean unrolled;
    protected int splitDegree;
    protected int leafDegree;

    /** Increments that should happen before loop spawn.  Each
     * increment is multiplied by the number of loop iterations */
    protected final List<RefCount> startIncrements;

    /**
     * Increments that should happen before loop spawn.
     * Each increment is a constant amount independent of iterations.
     * This is useful because we can piggyback ops on the normal start increments
     */
    protected final ListMultimap<Var, RefCount> constStartIncrements;


    /** Decrements that should happen at end of loop body (once per iteration) */
    protected final List<RefCount> endDecrements;

    public AbstractForeachLoop(Block loopBody, String loopName, Var loopVar,
        Var loopCounterVar, int splitDegree, int leafDegree, int desiredUnroll,
        boolean unrolled,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        List<RefCount> startIncrements,
        ListMultimap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements, boolean emptyLoop) {
      super(loopBody, passedVars, keepOpenVars, emptyLoop);
      this.loopName = loopName;
      this.loopVar = loopVar;
      this.loopCounterVar = loopCounterVar;
      this.splitDegree = splitDegree;
      this.leafDegree = leafDegree;
      this.desiredUnroll = desiredUnroll;
      this.unrolled = unrolled;
      this.startIncrements = new ArrayList<RefCount>(startIncrements);
      this.constStartIncrements = ArrayListMultimap.create(constStartIncrements);
      this.endDecrements = new ArrayList<RefCount>(endDecrements);
    }

    public List<RefCount> getStartIncrements() {
      return Collections.unmodifiableList(startIncrements);
    }

    public ListIterator<RefCount> startIncrementIterator() {
      return startIncrements.listIterator();
    }

    public void addStartIncrement(RefCount incr) {
      startIncrements.add(incr);
    }

    public void addConstantStartIncrement(Var v, RefCountType t, Arg amount) {
      // Check to see if already present
      List<RefCount> prev = constStartIncrements.get(v);
      ListIterator<RefCount> it = prev.listIterator();
      while (it.hasNext()) {
        RefCount rc = it.next();
        if (rc.var.equals(v) && rc.type == t && rc.amount.isInt()
                                             && amount.isInt()) {
          Arg newAmount = Arg.newInt(rc.amount.getInt() + amount.getInt());
          it.set(new RefCount(v, t, newAmount));
          return;
        }
      }

      // If we didn't have it already
      constStartIncrements.put(v, new RefCount(v, t, amount));
    }

    public List<RefCount> getEndDecrements() {
      return Collections.unmodifiableList(endDecrements);
    }

    public void addEndDecrement(RefCount decr) {
      endDecrements.add(decr);
    }

    public void prettyPrintIncrs(StringBuilder sb) {
      if (!startIncrements.isEmpty()) {
        sb.append(" #beforeperiter[");
        ICUtil.prettyPrintList(sb, startIncrements);
        sb.append("]");
      }

      if (!constStartIncrements.isEmpty()) {
        sb.append(" #beforeconst[");
        ICUtil.prettyPrintLists(sb, constStartIncrements.asMap().values());
        sb.append("]");
      }
      if (!endDecrements.isEmpty()) {
        sb.append(" #after[");
        ICUtil.prettyPrintList(sb, endDecrements);
        sb.append("]");
      }
    }

    /**
     * Try to piggyback constant incrs/decrs from outside continuation.
     *
     * Called repeatedly until it returns null.
     *
     * @param increments
     * @param type
     * @param dir whether to piggyback decrements or increments
     * @return if piggybacked, the var for which increments were piggybacked
     *          and amount (e.g. -2 if 2 decrements were piggybacked),
     *          otherwise null
     */
    public VarCount tryPiggyBack(RefCountsToPlace increments,
            RefCountType type, RCDir dir) {
      for (RefCount startIncr: startIncrements) {
        // Only consider piggybacking where we already are modifying
        // that particular count
        // TODO: could change read and write counts in single operation
        if (startIncr.type == type) {
          long incr = increments.getCount(startIncr.var);
          if ((dir == RCDir.DECR && incr < 0) ||
              (dir == RCDir.INCR && incr > 0)) {
            addConstantStartIncrement(startIncr.var, type, Arg.newInt(incr));
            return new VarCount(startIncr.var, incr);
          }
        }
      }
      return null;
    }

    /**
     * Constant iteration count
     * @return -1 if iter count not known, otherwise iteration count
     */
    public long constIterCount() {
      return -1;
    }


    protected Collection<Var> abstractForeachRequiredVars(boolean forDeadCodeElim) {
      Collection<Var> res = new ArrayList<Var>();
      if (!forDeadCodeElim) {
        // Need reference count vars
        addRefCountVars(res, startIncrements);
        addRefCountVars(res, constStartIncrements.values());
        addRefCountVars(res, endDecrements);
      }
      return res;
    }


    private void addRefCountVars(Collection<Var> res,
                      Collection<RefCount> refcounts) {
      for (RefCount rc: refcounts) {
        res.add(rc.var);
      }
    }

  }

  public static class ForeachLoop extends AbstractForeachLoop {
    private Var container;
    private boolean containerClosed;
    public Var getArrayVar() {
      return container;
    }

    private ForeachLoop(Block block,
        String loopName, Var container, Var loopVar,
        Var loopCounterVar, int splitDegree, int leafDegree,
        boolean arrayClosed,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        List<RefCount> startIncrements,
        ListMultimap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements, boolean emptyBody) {
      super(block, loopName, loopVar, loopCounterVar, splitDegree, leafDegree,
          -1, false, passedVars, keepOpenVars, startIncrements, constStartIncrements,
          endDecrements, emptyBody);
      this.container = container;
      this.containerClosed = arrayClosed;
    }

    public ForeachLoop(String loopName, Var container,
        Var loopVar, Var loopCounterVar, int splitDegree, int leafDegree,
        boolean containerClosed, List<PassedVar> passedVars,
        List<Var> keepOpenVars, List<RefCount> startIncrements,
        ListMultimap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements) {
      this(new Block(BlockType.FOREACH_BODY, null), loopName,
          container, loopVar, loopCounterVar,
          splitDegree, leafDegree, containerClosed,
          passedVars, keepOpenVars, startIncrements,
          constStartIncrements, endDecrements, true);
    }

    @Override
    public ForeachLoop clone() {
      return new ForeachLoop(this.loopBody.clone(), loopName,
        container, loopVar, loopCounterVar, splitDegree, leafDegree,
        containerClosed, passedVars, keepOpenVars, startIncrements,
        constStartIncrements, endDecrements, false);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.FOREACH_LOOP;
    }

    @Override
    public ExecTarget target() {
      boolean async = !containerClosed || splitDegree > 0;
      if (async) {
        return ExecTarget.dispatchedControl();
      } else {
        return ExecTarget.syncAny();
      }
    }

    @Override
    public boolean spawnsSingleTask() {
      return false;
    }

    @Override
    public Collection<PassedVar> getAllPassedVars() {
      // Need to mark container as passed in
      boolean found = false;

      Collection<PassedVar> regularPassed = this.getPassedVars();
      for (PassedVar passed: regularPassed) {
        if (passed.var.equals(container)) {
          found = true;
          break;
        }
      }
      if (found) {
        return regularPassed;
      } else {
        // Need to pass in container too
        List<PassedVar> res =
            new ArrayList<PassedVar>(regularPassed.size() + 1);
        res.add(new PassedVar(container, false));
        return res;
      }
    }

    /** Return list of variables that the continuations waits for
     * before executing
     * @return
     */
    @Override
    public List<BlockingVar> blockingVars(boolean includeConstructDefined) {
      return Collections.emptyList();
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.startForeachLoop(loopName, container, loopVar, loopCounterVar,
                splitDegree, leafDegree, containerClosed,
                passedVars, startIncrements, constStartIncrements,
                endDecrements);
      this.loopBody.generate(logger, gen, info);
      gen.endForeachLoop(splitDegree, containerClosed, endDecrements);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      if (!containerClosed) {
        sb.append(currentIndent + "@arrayblock\n");
      }
      if (splitDegree < 0) {
        sb.append(currentIndent + "@nospawn\n");
      }
      sb.append(currentIndent + "foreach " + loopVar.name());
      if (loopCounterVar != null) {
        sb.append(", " + loopCounterVar.name());
      }
      sb.append(" in " + container.name() + " ");
      ICUtil.prettyPrintVarInfo(sb, passedVars, keepOpenVars);
      prettyPrintIncrs(sb);
      sb.append(" /* ");
      sb.append(this.loopName);
      sb.append("*/");
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceConstructVars_(Map<Var, Arg> renames,
                                      RenameMode mode) {
      if (renames.containsKey(container)) {
        container = renames.get(container).getVar();
      }

      if (mode == RenameMode.REPLACE_VAR) {
        if (renames.containsKey(loopVar)) {
          loopVar = renames.get(loopVar).getVar();
        }
        if (this.loopCounterVar != null &&
            renames.containsKey(loopCounterVar)) {
          loopCounterVar = renames.get(loopCounterVar).getVar();
        }
      }
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      Collection<Var> res = new ArrayList<Var>();
      res.add(container);
      res.addAll(abstractForeachRequiredVars(forDeadCodeElim));
      return res;
    }

    @Override
    public void removeVars_(Set<Var> removeVars) {
      checkNotRemoved(container, removeVars);
      checkNotRemoved(loopVar, removeVars);
      if (loopCounterVar != null) {
        checkNotRemoved(loopCounterVar, removeVars);
      }
    }

    @Override
    public boolean isNoop() {
      return this.loopBody.isEmpty();
    }

    @Override
    public List<Var> constructDefinedVars(ContVarDefType type) {
      if (type.includesNewDefs()) {
        return loopCounterVar == null ?
                Arrays.asList(loopVar)
              : Arrays.asList(loopCounterVar, loopVar);
      } else {
        return Var.NONE;
      }
    }

    @Override
    public Block tryInline(Set<Var> closedVars, Set<Var> recClosedVars,
        boolean keepExplicitDependencies) {
      if (closedVars.contains(container) ||
          recClosedVars.contains(container)) {
        this.containerClosed = true;
      }
      return null;
    }

    public boolean fuseable(ForeachLoop o) {
      // annotation parameters should match to respect any
      // user settings
      return this.container.equals(o.container)
          && this.splitDegree == o.splitDegree;
    }

    public void fuseInto(FnID function, ForeachLoop o, boolean insertAtTop) {
      Map<Var, Arg> renames = new HashMap<Var, Arg>();
      renames.put(o.loopVar, Arg.newVar(this.loopVar));
      // Handle optional loop counter var
      if (o.loopCounterVar != null) {
        if (this.loopCounterVar != null) {
          renames.put(o.loopCounterVar, Arg.newVar(this.loopCounterVar));
        } else {
          this.loopCounterVar = o.loopCounterVar;
        }
      }
      o.renameVars(function, renames, RenameMode.REPLACE_VAR, true);

      fuseIntoAbstract(o, insertAtTop);
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      if (splitDegree > 0) {
        return ExecContext.control();
      } else {
        return outerContext;
      }
    }

    /**
     * Switch to version iterating over local container
     * @param var
     */
    public void switchToLocalForeach(Var localContainer) {
      assert(localContainer.type().assignableTo(
          Types.retrievedType(this.container, false)));
      this.container = localContainer;
      this.containerClosed = true;
      this.splitDegree = -1; // Execute locally
    }
  }

  public static class RangeLoop extends AbstractForeachLoop {
    // arguments can be either value variable or integer literal
    private Arg start;
    private Arg end;
    private Arg increment;

    public RangeLoop(String loopName, Var loopVar, Var countVar,
        Arg start, Arg end, Arg increment,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        int desiredUnroll, boolean unrolled, int splitDegree, int leafDegree,
        List<RefCount> startIncrements,
        ListMultimap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements) {
      this(new Block(BlockType.RANGELOOP_BODY, null), loopName,
          loopVar, countVar,
          start, end, increment, passedVars, keepOpenVars,
          desiredUnroll, unrolled, splitDegree, leafDegree,
          startIncrements, constStartIncrements, endDecrements,
          true);
    }

    private RangeLoop(Block block, String loopName,
        Var loopVar, Var loopCounterVar,
        Arg start, Arg end, Arg increment,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        int desiredUnroll, boolean unrolled, int splitDegree, int leafDegree,
        List<RefCount> startIncrements, ListMultimap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements, boolean emptyBody) {
      super(block, loopName, loopVar, loopCounterVar, splitDegree, leafDegree,
          desiredUnroll, unrolled,
          passedVars, keepOpenVars, startIncrements,
          constStartIncrements, endDecrements, emptyBody);

      if (Types.isIntVal(loopVar)) {
        assert(start.isImmInt());
        assert(end.isImmInt());
        assert(increment.isImmInt());
      } else {
        assert(Types.isFloatVal(loopVar));
        assert(start.isImmFloat());
        assert(end.isImmFloat());
        assert(increment.isImmFloat());
      }
      this.start = start;
      this.end = end;
      this.increment = increment;
    }

    @Override
    public RangeLoop clone() {
      return clone(true);
    }

    public RangeLoop clone(boolean cloneLoopBody) {
      Block newLoopBody;
      if (cloneLoopBody) {
        newLoopBody = this.loopBody.clone();
      } else {
        newLoopBody = new Block(BlockType.RANGELOOP_BODY, null);
      }
      return new RangeLoop(newLoopBody, loopName, loopVar, loopCounterVar,
          start, end, increment,
          passedVars, keepOpenVars, desiredUnroll, unrolled,
          splitDegree, leafDegree, startIncrements, constStartIncrements,
          endDecrements, !cloneLoopBody);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.RANGE_LOOP;
    }

    @Override
    public ExecTarget target() {
      if (splitDegree > 0) {
        return ExecTarget.dispatchedControl();
      } else {
        return ExecTarget.syncAny();
      }
    }

    @Override
    public boolean spawnsSingleTask() {
      return false;
    }

    @Override
    public List<BlockingVar> blockingVars(boolean includeConstructDefined) {
      return Collections.emptyList();
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.startRangeLoop(loopName, loopVar, loopCounterVar, start, end, increment,
                         splitDegree, leafDegree, passedVars, startIncrements,
                         constStartIncrements, endDecrements);
      this.loopBody.generate(logger, gen, info);
      gen.endRangeLoop(splitDegree, endDecrements);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      if (splitDegree < 0) {
        sb.append(currentIndent + "@nospawn\n");
      }
      sb.append(currentIndent +   "for " + loopVar.name());
      if (loopCounterVar != null) {
        sb.append(", " + loopCounterVar.name());
      }

      sb.append(" = " + start.toString() + " to " + end.toString() + " ");

      sb.append("incr " + increment.toString() + " ");

      ICUtil.prettyPrintVarInfo(sb, passedVars, keepOpenVars);
      prettyPrintIncrs(sb);
      sb.append(" /* ");
      sb.append(this.loopName);
      sb.append("*/");
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceConstructVars_(Map<Var, Arg> renames,
                                      RenameMode mode) {
      start = renameRangeArg(start, renames);
      end = renameRangeArg(end, renames);
      increment = renameRangeArg(increment, renames);

      if (mode == RenameMode.REPLACE_VAR) {
        if (renames.containsKey(loopVar)) {
          loopVar = renames.get(loopVar).getVar();
        }
        if (loopCounterVar != null && renames.containsKey(loopCounterVar)) {
          loopCounterVar = renames.get(loopCounterVar).getVar();
        }
      }
    }

    private Arg renameRangeArg(Arg val, Map<Var, Arg> renames) {
      if (val.kind == ArgKind.VAR) {
        Var var = val.getVar();
        if (renames.containsKey(var)) {
          Arg o = renames.get(var);
          assert(o != null);
          return o;
        }
      }
      return val;
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      Collection<Var> res = new ArrayList<Var>();
      for (Arg o: Arrays.asList(start, end, increment)) {
        if (o.isVar()) {
          res.add(o.getVar());
        }
      }

      res.addAll(abstractForeachRequiredVars(forDeadCodeElim));
      return res;
    }

    @Override
    public void removeVars_(Set<Var> removeVars) {
      checkNotRemoved(start, removeVars);
      checkNotRemoved(end, removeVars);
      checkNotRemoved(increment, removeVars);
    }

    @Override
    public Block tryInline(Set<Var> closedVars, Set<Var> recClosedVars,
                           boolean keepExplicitDependencies) {
      // Could inline loop if one or zero iterations...
      long iterCount = constIterCount();

      if (iterCount == 0) {
        return new Block(BlockType.FOREACH_BODY, this);
      } else if (iterCount == 1) {
        this.loopBody.addVariable(loopVar);
        this.loopBody.addInstructionFront(ICInstructions.valueSet(loopVar, start));
        return this.loopBody;
      }
      return null;
    }

    /**
     * @return iteration count if known
     */
    @Override
    public long constIterCount() {
      long iterCount = -1; // Negative if unknown

      // Need to know bounds at least
      if (start.isInt() && end.isInt()) {
        long startV = start.getInt();
        long endV = end.getInt();
        if (increment.isInt()) {
          long incrV = increment.getInt();
          iterCount = Math.max(0, (endV - startV + incrV) / incrV);
        } else {
          // Don't know increment, but might be able to bound
          iterCount = iterCountUnknownIncr(startV, endV);
        }

      } else if (start.isFloat() && end.isFloat()) {
        double startV = start.getFloat();
        double endV = end.getFloat();
        if (increment.isFloat()) {
          double incrV = increment.getFloat();
          iterCount = Math.max(0,
                              (long)Math.floor((endV - startV + incrV) / incrV));
        } else {
          iterCount = iterCountUnknownIncr(startV, endV);
        }
      }

      return iterCount;
    }

    private <T extends Comparable<T>> int iterCountUnknownIncr(T start, T end) {
      int comparison = start.compareTo(end);
      if (comparison > 0) {
        // Doesn't run
        return 0;
      } else if (comparison == 0) {
        return 1;
      }
      return -1; // Unknown
    }

    @Override
    public void inlineInto(Block block, Block predictedBranch) {
      // Shift loop variable to body and inline loop body
      this.loopBody.addVariable(loopVar);
      this.loopBody.addInstructionFront(
            ICInstructions.valueSet(this.loopVar, start));
      if (loopCounterVar != null) {
        this.loopBody.addVariable(loopCounterVar);
        this.loopBody.addInstructionFront(
            ICInstructions.valueSet(loopCounterVar, Arg.newInt(0)));
      }
      block.insertInline(loopBody);
      block.removeContinuation(this);
    }

    @Override
    public boolean constantReplace(Map<Var, Arg> knownConstants) {
      boolean anyChanged = false;
      Arg oldVals[] = new Arg[] {start, end, increment };
      Arg newVals[] = new Arg[3];
      for (int i = 0; i < oldVals.length; i++) {
        Arg old = oldVals[i];
        if (old.kind == ArgKind.VAR) {
          Arg replacement = knownConstants.get(old.getVar());
          if (replacement != null) {
            assert(replacement.type().assignableTo(oldVals[i].type()));
            anyChanged = true;
            newVals[i] = replacement;
          } else {
            newVals[i] = old;
          }
        } else {
          newVals[i] = old;
        }
      }
      start = newVals[0];
      end = newVals[1];
      increment = newVals[2];

      long iterCount = constIterCount();

      if (iterCount >= 0) {
        if (iterCount <= leafDegree) {
          // Don't need to split
          splitDegree = -1;
        }
      }
      assert(start != null); assert(end != null); assert(increment  != null);
      return anyChanged;
    }

    @Override
    public boolean isNoop() {
      if (this.loopBody.isEmpty()) {
        return true;
      } else if (constIterCount() == 0) {
        return true;
      } else {
        return false;
      }
    }

    @Override
    public List<Var> constructDefinedVars(ContVarDefType type) {
      if (type.includesNewDefs()) {
        if (loopCounterVar != null) {
          return Arrays.asList(loopVar, loopCounterVar);
        } else {
          return Arrays.asList(loopVar);
        }
      } else {
        return Var.NONE;
      }
    }

    // Return value indicating no unrolling
    private static final Pair<Boolean, List<Continuation>> NO_UNROLL =
                                  Pair.create(false, Collections.<Continuation>emptyList());
    @Override
    public Pair<Boolean, List<Continuation>> tryUnroll(Logger logger,
        FnID function, Block outerBlock) {
      logger.trace("DesiredUnroll for " + loopName + ": " + desiredUnroll);
      boolean expandLoops = isExpandLoopsEnabled();
      boolean fullUnroll = isFullUnrollEnabled();

      if (!Types.isIntVal(start)) {
        /*
         * TODO: only unroll integer ranges now - don't want to deal with
         * floating point rounding issues
         */
        return NO_UNROLL;
      }

      if (!this.unrolled && this.desiredUnroll > 1) {
        // Unroll explicitly marked loops
        if (this.loopCounterVar != null) {
          logger.warn("Can't unroll range loop with counter variable yet," +
                      " ignoring unroll annotation");
          return NO_UNROLL;
        }
        return Pair.create(true, doUnroll(logger, function, outerBlock,
                                          desiredUnroll));
      } else if (expandLoops || fullUnroll) {
        long instCount = loopBody.getInstructionCount();
        long iterCount = constIterCount();

        if (instCount == 0) {
          return NO_UNROLL;
        }

        if (expandLoops && iterCount >= 0) {
          // See if the loop has a small number of iterations, could just expand;
          if (iterCount <= getUnrollMaxIters(true)) {
            long extraInstructions = instCount * (iterCount - 1);
            if (extraInstructions <= getUnrollMaxExtraInsts(true)) {
              return Pair.create(true, doUnroll(logger, function, outerBlock,
                                 (int)iterCount));
            }
          }
        }
        if (!fullUnroll) {
          logger.trace("Full unrolled not enabled");
          return NO_UNROLL;
        }

        if (this.unrolled) {
          // Don't do extra unrolling unless we're just expanding a small loop
          return NO_UNROLL;
        }
        // Finally, maybe unroll a few iterations
        long threshold = getUnrollMaxExtraInsts(false);
        long unrollFactor = Math.min(getUnrollMaxIters(false),
                                     (threshold / instCount) + 1);
        if (unrollFactor > 1) {
          return Pair.create(true, doUnroll(logger, function, outerBlock,
                                            (int)unrollFactor));
        }
      }
      return NO_UNROLL;
    }

    private boolean isExpandLoopsEnabled() {
      return Settings.getBooleanUnchecked(Settings.OPT_EXPAND_LOOPS);
    }

    private boolean isFullUnrollEnabled() {
      return Settings.getBooleanUnchecked(Settings.OPT_FULL_UNROLL);
    }


    private int getUnrollMaxIters(boolean fullExpand) {
      if (fullExpand) {
        return Settings.getIntUnchecked(Settings.OPT_EXPAND_LOOP_THRESHOLD_ITERS);
      } else {
        return Settings.getIntUnchecked(Settings.OPT_UNROLL_LOOP_THRESHOLD_ITERS);
      }
    }

    private int getUnrollMaxExtraInsts(boolean fullExpand) {
      if (fullExpand) {
        return Settings.getIntUnchecked(Settings.OPT_EXPAND_LOOP_THRESHOLD_INSTS);
      } else {
        return Settings.getIntUnchecked(Settings.OPT_UNROLL_LOOP_THRESHOLD_INSTS);
      }
    }

    /**
     * Unroll a loop by splitting into two loops, one short one
     * with original stride, and another with a long stride
     *
     * We transform:
     * range_loop [start:end:step]
     *
     *  =======>
     *
     *  range_loop [start : unroll_end : big_step]
     *  range_loop [remainder_start  : end : step]
     *
     */
    private List<Continuation> doUnroll(Logger logger, FnID function,
                                        Block outerBlock, int unrollFactor) {
      logger.debug("Unrolling range loop " + this.loopName
                        + " " + desiredUnroll + " times ");

      String vPrefix = Var.VALUEOF_VAR_PREFIX + loopName;
      String bigStepName = outerBlock.uniqueVarName(vPrefix + ":unrollincr");
      VarProvenance prov = VarProvenance.optimizerTmp();
      Var bigIncr = new Var(Types.V_INT, bigStepName, Alloc.LOCAL,
                  DefType.LOCAL_COMPILER, prov);
      Var diff = new Var(Types.V_INT, outerBlock.uniqueVarName(vPrefix + ":diff"),
                        Alloc.LOCAL, DefType.LOCAL_COMPILER, prov);
      Var diff2 = new Var(Types.V_INT, outerBlock.uniqueVarName(vPrefix + ":diff2"),
                        Alloc.LOCAL, DefType.LOCAL_COMPILER, prov);
      Var extra = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":extra"),
          Alloc.LOCAL, DefType.LOCAL_COMPILER, prov);
      Var remainder = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":rem"),
          Alloc.LOCAL, DefType.LOCAL_COMPILER, prov);
      Var remainderStart = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":remstart"),
          Alloc.LOCAL, DefType.LOCAL_COMPILER, prov);
      Var unrollEnd = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":unrollEnd"),
          Alloc.LOCAL, DefType.LOCAL_COMPILER, prov);

      outerBlock.addVariable(bigIncr);
      outerBlock.addVariable(diff);
      outerBlock.addVariable(diff2);
      outerBlock.addVariable(extra);
      outerBlock.addVariable(remainder);
      outerBlock.addVariable(remainderStart);
      outerBlock.addVariable(unrollEnd);

      // Generate the code for calculations here.  Constant folding will
      // clean up later in special cases where values known
      // unroll_end = end - (end - start + 1 % big_step)
      // remainder_start = unroll_end + 1
      outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.MULT_INT,
          bigIncr, Arrays.asList(increment, Arg.newInt(unrollFactor))));
      outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.MINUS_INT,
          diff, Arrays.asList(end, start)));
      outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.PLUS_INT,
          diff2, Arrays.asList(diff.asArg(), Arg.ONE)));
      outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.MOD_INT,
          remainder, Arrays.asList(diff2.asArg(), bigIncr.asArg())));
      outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.MINUS_INT,
          unrollEnd, Arrays.asList(end, remainder.asArg())));
      outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.PLUS_INT,
          remainderStart, Arrays.asList(unrollEnd.asArg(), Arg.ONE)));

      // Create new unrolled range loop.
      // Modify the start of this loop
      RangeLoop unrolled = this.clone(false);
      this.start = remainderStart.asArg();
      this.unrolled = true;

      unrolled.end = unrollEnd.asArg();
      unrolled.increment = bigIncr.asArg();
      unrolled.unrolled = true;
      unrolled.leafDegree = Math.max(1, unrolled.leafDegree / unrollFactor);

      // clone body of unrolled multiple times
      Block orig = this.loopBody;
      Arg oldIncr = this.increment;
      Var lastIterLoopVar = null;

      for (int i = 0; i < unrollFactor; i++) {
        // Put everything in nested block to avoid var shadowing (We uniquify
        // the varnames later, but need to avoid shadowing for that to work)
        NestedBlock nb = new NestedBlock(orig.clone(BlockType.NESTED_BLOCK, null));
        Block unrolledBody = unrolled.getLoopBody();
        unrolledBody.addContinuation(nb);
        Var currIterLoopVar; // Variable with current iter number
        if (i == 0) {
          currIterLoopVar = loopVar;
        } else {
          // E.g. if loop counter is i and unrolling 4x, allocate
          //    i, i@2, i@3, i@4, where i@k = i@(k-1) + step
          String newLoopVarName = outerBlock.uniqueVarName(
              unrolled.loopVar.name() + "@" + (i + 1));
          currIterLoopVar = new Var(Types.V_INT, newLoopVarName,
              Alloc.LOCAL, DefType.LOCAL_COMPILER,
              VarProvenance.renamed(unrolled.loopVar));
          unrolledBody.addVariable(currIterLoopVar);
          unrolledBody.addInstruction(Builtin.createLocal(BuiltinOpcode.PLUS_INT,
              currIterLoopVar, Arrays.asList(Arg.newVar(lastIterLoopVar), oldIncr)));
          // Replace references to the iteration counter in nested block
          nb.renameVars(function, Collections.singletonMap(unrolled.loopVar,
                       Arg.newVar(currIterLoopVar)), RenameMode.REPLACE_VAR, true);
        }
        lastIterLoopVar = currIterLoopVar;
      }
      return Collections.<Continuation>singletonList(unrolled);
    }

    public boolean fuseable(RangeLoop o) {
      // Make sure loop bounds line up, but also annotations since we
      // want to respect any user annotations
      return this.start.equals(o.start)
          && this.increment.equals(o.increment)
          && this.end.equals(o.end)
          && this.desiredUnroll == o.desiredUnroll
          && this.splitDegree == o.splitDegree
          && (this.loopCounterVar == null) == (o.loopCounterVar == null);
    }

    /**
     * Fuse the other loop into this loop
     */
    public void fuseInto(FnID function, RangeLoop o, boolean insertAtTop) {
      Map<Var, Arg> renames = new HashMap<Var, Arg>();
      // Update loop var in other loop
      renames.put(o.loopVar, Arg.newVar(this.loopVar));
      if (loopCounterVar != null)
        renames.put(o.loopCounterVar, Arg.newVar(this.loopCounterVar));
      o.renameVars(function, renames, RenameMode.REPLACE_VAR, true);

      this.fuseIntoAbstract(o, insertAtTop);
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      if (splitDegree > 0) {
        return ExecContext.control();
      } else {
        return outerContext;
      }
    }
  }

}
