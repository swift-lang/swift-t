package exm.stc.ic.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.Conditionals.IfStatement;
import exm.stc.ic.tree.ICContinuations.AbstractLoop;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
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
    public List<Var> constructDefinedVars() {
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
      return new RangeLoop(this.loopBody.clone(), loopName, loopVar,
          loopCounterVar,
          start.clone(), end.clone(), increment.clone(),
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
      assert(predictedBranch == this.loopBody);
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
      return this.loopBody.isEmpty();
    }

    @Override
    public List<Var> constructDefinedVars() {
      if (loopCounterVar != null) {
        return Arrays.asList(loopVar, loopCounterVar);
      } else {
        return Arrays.asList(loopVar);
      }
    }

    @Override
    public boolean tryUnroll(Logger logger, Block outerBlock) {
      logger.trace("DesiredUnroll for " + loopName + ": " + desiredUnroll);
      if (this.unrolled) {
        return false;
      } else if (this.desiredUnroll > 1) {
        // Unroll explicitly marked loops
        if (this.loopCounterVar != null) {
          logger.warn("Can't unroll range loop with counter variable yet," +
                      " ignoring unroll annotation");
          return false;
        }
        doUnroll(logger, outerBlock, desiredUnroll);
        return true;
      } else {
        long instCount = loopBody.getInstructionCount();
        if (start.isIntVal() && end.isIntVal() && increment.isIntVal()) {
          // See if the loop has a small number of iterations, could just expand
          long iters = calcIterations(start.getIntLit(), end.getIntLit(),
                                      increment.getIntLit());
          if (iters <= getUnrollMaxIters(true)) {
            long extraInstructions = instCount * (iters - 1);
            if (extraInstructions <= getUnrollMaxExtraInsts(true)) {
              doUnroll(logger, outerBlock, iters);
              return true;
            }
          }
        }
        // Finally, maybe unroll a few iterations
        long threshold = getUnrollMaxExtraInsts(false);
        long unrollFactor = Math.min(getUnrollMaxIters(false),
                                     (threshold / instCount) + 1);
        if (unrollFactor > 1) {
          doUnroll(logger, outerBlock, unrollFactor);
          return true;
        }
      }
      return false;
    }

    private long getUnrollMaxIters(boolean fullExpand) {
      try {
        if (fullExpand) {
          return Settings.getLong(Settings.OPT_EXPAND_LOOP_THRESHOLD_ITERS);
        } else {
          return Settings.getLong(Settings.OPT_UNROLL_LOOP_THRESHOLD_ITERS);
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
    }
    
    private long getUnrollMaxExtraInsts(boolean fullExpand) {
      try {
        if (fullExpand) {
          return Settings.getLong(Settings.OPT_EXPAND_LOOP_THRESHOLD_INSTS);
        } else {
          return Settings.getLong(Settings.OPT_UNROLL_LOOP_THRESHOLD_INSTS); 
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
    }

    private void doUnroll(Logger logger, Block outerBlock, long unrollFactor) {
      logger.debug("Unrolling range loop " + this.loopName 
                        + " " + desiredUnroll + " times ");
      Arg oldStep = this.increment;

      long checkIter; // the time we need to check
      if(increment.isIntVal() &&
          start.isIntVal() &&
          end.isIntVal()) {
        long startV = start.getIntLit();
        long endV = end.getIntLit();
        long incV = increment.getIntLit();

        long iters = calcIterations(startV, endV, incV);

        // 0 if the number of iterations will go exactly into the
        // unroll factor
        long extra = iters % unrollFactor;

        if (extra == 0) {
          checkIter = unrollFactor;
        } else {
          checkIter = extra;
        }
      } else {
        checkIter = -1;
      }

      // Update step
      if (oldStep.isIntVal()) {
        this.increment = Arg.createIntLit(oldStep.getIntLit() * unrollFactor);
      } else {
        Var old = oldStep.getVar();
        Var newIncrement = new Var(old.type(),
            old.name() + "@unroll" + unrollFactor,
            VarStorage.LOCAL,
            DefType.LOCAL_COMPILER, null);
        outerBlock.declareVariable(newIncrement);
        outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.MULT_INT,
            newIncrement, Arrays.asList(oldStep, Arg.createIntLit(unrollFactor))));

        this.increment = Arg.createVar(newIncrement);
      }

      // Create a copy of the original loop body for reference
      Block orig = loopBody;
      this.loopBody = new Block(BlockType.LOOP_BODY, this);
      Block curr = loopBody;
      Var nextIter = loopVar; // Variable with current iter number

      for (int i = 0; i < unrollFactor; i++) {
        // Put everything in nested block
        NestedBlock nb = new NestedBlock(orig.clone(BlockType.NESTED_BLOCK, null));
        curr.addContinuation(nb);
        if (i != 0) {
          // Replace references to the iteration counter
          nb.replaceVars(Collections.singletonMap(this.loopVar,
                       Arg.createVar(nextIter)), RenameMode.REPLACE_VAR, true);
        }

        if (i < unrollFactor - 1) {
          // Next iteration number and boolean check
          Var lastIter = nextIter;
          nextIter = new Var(Types.V_INT,
              this.loopVar.name() + "@" + (i + 1), VarStorage.LOCAL,
              DefType.LOCAL_COMPILER, null);

          curr.addVariable(nextIter);
          // Loop counter
          curr.addInstruction(Builtin.createLocal(BuiltinOpcode.PLUS_INT,
              nextIter, Arrays.asList(Arg.createVar(lastIter),
                                      oldStep)));

          boolean mustCheck = checkIter < 0 || i + 1 == checkIter;
          if (mustCheck) {
            Var nextIterCheck = new Var(Types.V_BOOL,
                this.loopVar.name() + "@" + (i + 1) + "_check",
                VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
            curr.addVariable(nextIterCheck);
            curr.addInstruction(Builtin.createLocal(BuiltinOpcode.LTE_INT,
                nextIterCheck, Arrays.asList(Arg.createVar(nextIter),
                                        this.end)));
            // check to see if we should run next iteration
            IfStatement ifSt = new IfStatement(Arg.createVar(nextIterCheck));
            curr.addContinuation(ifSt);

            curr = ifSt.getThenBlock();
          }
        } else {
          curr = null;
        }
      }
      this.unrolled = true;
      this.leafDegree /= unrollFactor;
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