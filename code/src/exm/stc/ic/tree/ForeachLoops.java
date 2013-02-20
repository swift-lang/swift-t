package exm.stc.ic.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.RefCount;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.Counters;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICContinuations.AbstractLoop;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.NestedBlock;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.RenameMode;

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
    protected final MultiMap<Var, RefCount> constStartIncrements;
    
    
    /** Decrements that should happen at end of loop body (once per iteration) */
    protected final List<RefCount> endDecrements;
    
    public AbstractForeachLoop(Block loopBody, String loopName, Var loopVar,
        Var loopCounterVar, int splitDegree, int leafDegree, int desiredUnroll,
        boolean unrolled,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        List<RefCount> startIncrements,
        MultiMap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements) {
      super(loopBody, passedVars, keepOpenVars);      
      assert(loopCounterVar == null || loopCounterVar.type().equals(Types.V_INT));
      this.loopName = loopName;
      this.loopVar = loopVar;
      this.loopCounterVar = loopCounterVar;
      this.splitDegree = splitDegree;
      this.leafDegree = leafDegree;
      this.desiredUnroll = desiredUnroll;
      this.unrolled = unrolled;
      this.startIncrements = new ArrayList<RefCount>(startIncrements);
      this.constStartIncrements = constStartIncrements.clone();
      this.endDecrements = new ArrayList<RefCount>(endDecrements);
    }
    
    public List<RefCount> getStartIncrements() {
      return Collections.unmodifiableList(startIncrements);
    }
    
    public void addStartIncrement(RefCount incr) {
      startIncrements.add(incr);
    }
    
    public void addConstantStartIncrement(Var v, RefCountType t, Arg amount) {
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
        ICUtil.prettyPrintLists(sb, constStartIncrements.values());
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
     * Reset counters in increments for any that are changed
     * @param increments
     * @param type
     * @param if true, try to piggyback decrements, if false, increments
     */
    public void tryPiggyBack(Counters<Var> increments, RefCountType type,
        boolean decrement) {
      for (RefCount startIncr: startIncrements) {
        // Only consider piggybacking where we already are modifying
        // that particular count
        // TODO: could change read and write counts in single operation
        if (startIncr.type == type) {
          long incr = increments.getCount(startIncr.var);
          if ((decrement && incr < 0) ||
              (!decrement && incr > 0)) {
            addConstantStartIncrement(startIncr.var, type,
                                      Arg.createIntLit(incr));
            increments.add(startIncr.var, -1 * incr);
          }
        }
      }
    }
  }

  public static class ForeachLoop extends AbstractForeachLoop {
    private Var arrayVar;
    private boolean arrayClosed;
    public Var getArrayVar() {
      return arrayVar;
    }

    private ForeachLoop(Block block,
        String loopName, Var arrayVar, Var loopVar,
        Var loopCounterVar, int splitDegree, int leafDegree,
        boolean arrayClosed,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        List<RefCount> startIncrements, 
        MultiMap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements) {
      super(block, loopName, loopVar, loopCounterVar, splitDegree, leafDegree,
          -1, false, passedVars, keepOpenVars, startIncrements, constStartIncrements,
          endDecrements);
      this.arrayVar = arrayVar;
      this.arrayClosed = arrayClosed;
    }

    public ForeachLoop(String loopName, Var arrayVar,
        Var loopVar, Var loopCounterVar, int splitDegree, int leafDegree,
        boolean arrayClosed, List<PassedVar> passedVars,
        List<Var> keepOpenVars, List<RefCount> startIncrements,
        MultiMap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements) {
      this(new Block(BlockType.FOREACH_BODY, null), loopName,
          arrayVar, loopVar, loopCounterVar,
          splitDegree, leafDegree, arrayClosed, 
          passedVars, keepOpenVars, startIncrements, 
          constStartIncrements, endDecrements);
    }

    @Override
    public ForeachLoop clone() {
      return new ForeachLoop(this.loopBody.clone(), loopName,
        arrayVar, loopVar, loopCounterVar, splitDegree, leafDegree,
        arrayClosed, passedVars, keepOpenVars, startIncrements, 
        constStartIncrements, endDecrements);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.FOREACH_LOOP;
    }

    @Override
    public boolean isAsync() { 
      return !arrayClosed || splitDegree > 0;
    }
    

    @Override
    public void setPassedVars(Collection<PassedVar> passedVars) {
      if (this.isAsync()) {
        boolean found = false;
        for (PassedVar passed: passedVars) {
          if (passed.var.equals(arrayVar)) {
            found = true;
            break;
          }
        }
        if (found) {
          super.setPassedVars(passedVars);
        } else {
          // TODO: a little hacky but does job for now
          // Need to pass in array
          ArrayList<PassedVar> passedPlus = 
              new ArrayList<PassedVar>(passedVars.size() + 1);
          passedPlus.addAll(passedVars);
          passedPlus.add(new PassedVar(arrayVar, false));
          super.setPassedVars(passedPlus);
          return;
        }
      } else {
        super.setPassedVars(passedVars);
      }
    }

    /** Return list of variables that the continuations waits for
     * before executing
     * @return
     */
    @Override
    public List<BlockingVar> blockingVars() {
      return Collections.emptyList();
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startForeachLoop(loopName, arrayVar, loopVar, loopCounterVar,
                splitDegree, leafDegree, arrayClosed, 
                passedVars, startIncrements, constStartIncrements);
      this.loopBody.generate(logger, gen, info);
      gen.endForeachLoop(splitDegree, arrayClosed, endDecrements);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      if (!arrayClosed) {
        sb.append(currentIndent + "@arrayblock\n");
      }
      if (splitDegree < 0) {
        sb.append(currentIndent + "@nospawn\n");
      }
      sb.append(currentIndent + "foreach " + loopVar.name());
      if (loopCounterVar != null) {
        sb.append(", " + loopCounterVar.name());
      }
      sb.append(" in " + arrayVar.name() + " ");
      ICUtil.prettyPrintVarInfo(sb, passedVars, keepOpenVars);
      prettyPrintIncrs(sb);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceConstructVars_(Map<Var, Arg> renames,
                                      RenameMode mode) {
      if (renames.containsKey(arrayVar)) {
        arrayVar = renames.get(arrayVar).getVar();
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
      Collection<Var> res = new ArrayList<Var>(
          super.requiredVars(forDeadCodeElim));
      res.add(arrayVar);
      return res;
    }

    @Override
    public void removeVars_(Set<Var> removeVars) {
      checkNotRemoved(arrayVar, removeVars);
      checkNotRemoved(loopVar, removeVars);
      if (loopCounterVar != null) {
        checkNotRemoved(loopCounterVar, removeVars);
      }
    }

    @Override
    public Block branchPredict(Map<Var, Arg> knownConstants) {
      return null;
    }

    @Override
    public boolean isNoop() {
      return this.loopBody.isEmpty();
    }

    @Override
    public List<Var> constructDefinedVars(boolean includeRedefs) {
      return loopCounterVar == null ?
                Arrays.asList(loopVar)
              : Arrays.asList(loopCounterVar, loopVar);
    }

    @Override
    public Block tryInline(Set<Var> closedVars, Set<Var> recClosedVars,
        boolean keepExplicitDependencies) {
      if (closedVars.contains(arrayVar) ||
          recClosedVars.contains(arrayVar)) {
        this.arrayClosed = true;
      }
      return null;
    }

    public boolean fuseable(ForeachLoop o) {
      // annotation parameters should match to respect any
      // user settings
      return this.arrayVar.equals(o.arrayVar)
          && this.splitDegree == o.splitDegree;
    }

    public void fuseInto(ForeachLoop o, boolean insertAtTop) {
      Map<Var, Arg> renames = new HashMap<Var, Arg>();
      renames.put(o.loopVar, Arg.createVar(this.loopVar));
      // Handle optional loop counter var
      if (o.loopCounterVar != null) {
        if (this.loopCounterVar != null) {
          renames.put(o.loopCounterVar, Arg.createVar(this.loopCounterVar));    
        } else {
          this.loopCounterVar = o.loopCounterVar;
        }
      }
      o.replaceVars(renames, RenameMode.REPLACE_VAR, true);
      
      fuseIntoAbstract(o, insertAtTop);
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      if (splitDegree > 0) {
        return ExecContext.CONTROL;
      } else {
        return outerContext;
      }
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
        MultiMap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements) {
      this(new Block(BlockType.RANGELOOP_BODY, null), loopName,
          loopVar, countVar,
          start, end, increment, passedVars, keepOpenVars,
          desiredUnroll, unrolled, splitDegree, leafDegree,
          startIncrements, constStartIncrements, endDecrements);
    }

    private RangeLoop(Block block, String loopName,
        Var loopVar, Var loopCounterVar,
        Arg start, Arg end, Arg increment,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        int desiredUnroll, boolean unrolled, int splitDegree, int leafDegree,
        List<RefCount> startIncrements, MultiMap<Var, RefCount> constStartIncrements,
        List<RefCount> endDecrements) {
      super(block, loopName, loopVar, loopCounterVar, splitDegree, leafDegree,
          desiredUnroll, unrolled,
          passedVars, keepOpenVars, startIncrements,
          constStartIncrements, endDecrements);
      assert(loopVar.type().equals(Types.V_INT));
      assert(start.isImmediateInt());
      assert(end.isImmediateInt());
      assert(increment.isImmediateInt());
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
          endDecrements);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.RANGE_LOOP;
    }

    @Override
    public boolean isAsync() {
      return splitDegree > 0;
    }

    @Override
    public List<BlockingVar> blockingVars() {
      return Collections.emptyList();
    }
    
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startRangeLoop(loopName, loopVar, loopCounterVar, start, end, increment,
                         splitDegree, leafDegree, passedVars, startIncrements,
                         constStartIncrements);
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

      if (!increment.isIntVal() || increment.getIntLit() != 1) {
          sb.append("incr " + increment.toString() + " ");
      }
      ICUtil.prettyPrintVarInfo(sb, passedVars, keepOpenVars);
      prettyPrintIncrs(sb);
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
      Collection<Var> res = new ArrayList<Var>(
          super.requiredVars(forDeadCodeElim));
      for (Arg o: Arrays.asList(start, end, increment)) {
        if (o.isVar()) {
          res.add(o.getVar());
        }
      }
      return res;
    }

    @Override
    public void removeVars_(Set<Var> removeVars) {
      checkNotRemoved(start, removeVars);
      checkNotRemoved(end, removeVars);
      checkNotRemoved(increment, removeVars);
    }

    @Override
    public Block branchPredict(Map<Var, Arg> knownConstants) {
      // Could inline loop if there is only one iteration...
      if (start.isIntVal() && end.isIntVal()) {
        long startV = start.getIntLit();
        long endV = end.getIntLit();
        boolean singleIter = false;
        if (endV < startV) {
          // Doesn't run - return empty block
          return new Block(BlockType.FOREACH_BODY, this);
        } else if (endV == startV) {
          singleIter = true;
        } else if (increment.isIntVal()) {
          long incV = increment.getIntLit();
          if (startV + incV > endV) {
            singleIter = true;
          }
        }

        if (singleIter) {
          return this.loopBody;
        }
      }
      return null;
    }

    @Override
    public void inlineInto(Block block, Block predictedBranch) {
      // Shift loop variable to body and inline loop body
      this.loopBody.declareVariable(loopVar);
      this.loopBody.addInstructionFront(
          Builtin.createLocal(BuiltinOpcode.COPY_INT, this.loopVar, start));
      if (loopCounterVar != null) {
        this.loopBody.declareVariable(loopCounterVar);
        this.loopBody.addInstructionFront(Builtin.createLocal(
                     BuiltinOpcode.COPY_INT, loopCounterVar, Arg.createIntLit(0)));
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
            assert(replacement.isIntVal());
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
      
      if (start.isIntVal() && end.isIntVal() && increment.isIntVal()) {
        long iters = (end.getIntLit() - start.getIntLit()) /
                      increment.getIntLit() + 1;
        if (iters <= leafDegree) {
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
      } else if (this.start.isIntVal() && this.end.isIntVal() &&
          this.end.getIntLit() < this.start.getIntLit()) {
        return true;
      } else { 
        return false;
      }
    }

    @Override
    public List<Var> constructDefinedVars(boolean includeRedefs) {
      if (loopCounterVar != null) {
        return Arrays.asList(loopVar, loopCounterVar);
      } else {
        return Arrays.asList(loopVar);
      }
    }

    // Return value indicating no unrolling
    private static final Pair<Boolean, List<Continuation>> NO_UNROLL = 
                                  Pair.create(false, Collections.<Continuation>emptyList());
    @Override
    public Pair<Boolean, List<Continuation>> tryUnroll(Logger logger,
                                                       Block outerBlock) {
      logger.trace("DesiredUnroll for " + loopName + ": " + desiredUnroll);
      boolean expandLoops = isExpandLoopsEnabled();
      boolean fullUnroll = isFullUnrollEnabled();
      
      if (!this.unrolled && this.desiredUnroll > 1) {
        // Unroll explicitly marked loops
        if (this.loopCounterVar != null) {
          logger.warn("Can't unroll range loop with counter variable yet," +
                      " ignoring unroll annotation");
          return NO_UNROLL;
        }
        return Pair.create(true, doUnroll(logger, outerBlock, desiredUnroll));
      } else if (expandLoops || fullUnroll) {
        long instCount = loopBody.getInstructionCount();
        if (expandLoops && start.isIntVal() && end.isIntVal() && increment.isIntVal()) {
          // See if the loop has a small number of iterations, could just expand
          long iters = calcIterations(start.getIntLit(), end.getIntLit(),
                                      increment.getIntLit());
          if (iters <= getUnrollMaxIters(true)) {
            long extraInstructions = instCount * (iters - 1);
            if (extraInstructions <= getUnrollMaxExtraInsts(true)) {
              return Pair.create(true, doUnroll(logger, outerBlock, (int)iters));
            }
          }
        } 
        if (!fullUnroll) {
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
          return Pair.create(true, doUnroll(logger, outerBlock, (int)unrollFactor));
        }
      }
      return NO_UNROLL;
    }

    private boolean isExpandLoopsEnabled() {
      try {
        return Settings.getBoolean(Settings.OPT_EXPAND_LOOPS);
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
    }
    
    private boolean isFullUnrollEnabled() {
      try {
        return Settings.getBoolean(Settings.OPT_FULL_UNROLL);
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
    }
    
    
    private int getUnrollMaxIters(boolean fullExpand) {
      try {
        if (fullExpand) {
          return Settings.getInt(Settings.OPT_EXPAND_LOOP_THRESHOLD_ITERS);
        } else {
          return Settings.getInt(Settings.OPT_UNROLL_LOOP_THRESHOLD_ITERS);
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
    }
    
    private int getUnrollMaxExtraInsts(boolean fullExpand) {
      try {
        if (fullExpand) {
          return Settings.getInt(Settings.OPT_EXPAND_LOOP_THRESHOLD_INSTS);
        } else {
          return Settings.getInt(Settings.OPT_UNROLL_LOOP_THRESHOLD_INSTS); 
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
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
    private List<Continuation> doUnroll(Logger logger, Block outerBlock, int unrollFactor) {
      logger.debug("Unrolling range loop " + this.loopName 
                        + " " + desiredUnroll + " times ");
      
      String vPrefix = Var.OPT_VALUE_VAR_PREFIX + loopName;
      String bigStepName = outerBlock.uniqueVarName(vPrefix + ":unrollincr"); 
      Var bigIncr = new Var(Types.V_INT, bigStepName, VarStorage.LOCAL,
                            DefType.LOCAL_COMPILER, null);
      Var diff = new Var(Types.V_INT, outerBlock.uniqueVarName(vPrefix + ":diff"),
                        VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      Var diff2 = new Var(Types.V_INT, outerBlock.uniqueVarName(vPrefix + ":diff2"),
                        VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      Var extra = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":extra"),
          VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      Var remainder = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":rem"),
          VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      Var remainderStart = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":remstart"),
          VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      Var unrollEnd = new Var(Types.V_INT,
          outerBlock.uniqueVarName(vPrefix + ":unrollEnd"),
          VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);

      outerBlock.declareVariable(bigIncr);
      outerBlock.declareVariable(diff);
      outerBlock.declareVariable(diff2);
      outerBlock.declareVariable(extra);
      outerBlock.declareVariable(remainder);
      outerBlock.declareVariable(remainderStart);
      outerBlock.declareVariable(unrollEnd);
      
      // Generate the code for calculations here.  Constant folding will
      // clean up later in special cases where values known
      // unroll_end = end - (end - start + 1 % big_step)
      // remainder_start = unroll_end + 1
      outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.MULT_INT,
          bigIncr, Arrays.asList(increment, Arg.createIntLit(unrollFactor))));
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
      Set<String> createdVarNames = new HashSet<String>();
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
              unrolled.loopVar.name() + "@" + (i + 1), createdVarNames);
          currIterLoopVar = new Var(Types.V_INT, newLoopVarName,
              VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
          createdVarNames.add(newLoopVarName);
          unrolledBody.addVariable(currIterLoopVar);
          unrolledBody.addInstruction(Builtin.createLocal(BuiltinOpcode.PLUS_INT,
              currIterLoopVar, Arrays.asList(Arg.createVar(lastIterLoopVar), oldIncr)));
          // Replace references to the iteration counter in nested block
          nb.replaceVars(Collections.singletonMap(unrolled.loopVar,
                       Arg.createVar(currIterLoopVar)), RenameMode.REPLACE_VAR, true);
        }
        lastIterLoopVar = currIterLoopVar;
      }
      //System.err.println("FIRST: " + this + "\nSECOND: " + unrolled);
      return Collections.<Continuation>singletonList(unrolled);
    }

    private long calcIterations(long startV, long endV, long incV) {
      long diff = (endV - startV + 1);
      // Number of loop iterations
      long iters = ( (diff - 1) / incV ) + 1;
      return iters;
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
    public void fuseInto(RangeLoop o, boolean insertAtTop) {
      Map<Var, Arg> renames = new HashMap<Var, Arg>();
      // Update loop var in other loop
      renames.put(o.loopVar, Arg.createVar(this.loopVar));
      if (loopCounterVar != null)
        renames.put(o.loopCounterVar, Arg.createVar(this.loopCounterVar));
      o.replaceVars(renames, RenameMode.REPLACE_VAR, true);
     
      this.fuseIntoAbstract(o, insertAtTop);
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      if (splitDegree > 0) {
        return ExecContext.CONTROL;
      } else {
        return outerContext;
      }
    }
  }

}