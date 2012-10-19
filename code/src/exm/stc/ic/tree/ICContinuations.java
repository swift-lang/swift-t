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

import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.LoopBreak;
import exm.stc.ic.tree.ICInstructions.LoopContinue;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.GenInfo;

/**
 * This module contains definitions of all of the continuation varieties used
 * in the intermediate representation.  Each continuation is some sort of
 * control flow structure.
 * 
 * Each continuation object is responsible for being able to perform particular
 * transformations and report information about itself.  See the Continuation
 * base class to see what methods must be implemented. 
 *
 */
public class ICContinuations {
  public static final String indent = ICUtil.indent;

  public static abstract class Continuation {
    public abstract ContinuationType getType();

    public abstract void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException;

    public abstract void prettyPrint(StringBuilder sb, String currentIndent);


    /** Returns all nested blocks in this continuation */
    public abstract List<Block> getBlocks();


    /**
     * Replace usage of the values of the specified variables throughotu
     * where possible.  It is ok if the usedVariables get messed up a bit
     * here: we will fix them up later
     * @param renames
     */
    public abstract void replaceInputs(Map<String, Arg> renames);

    public abstract void replaceVars(Map<String, Arg> renames);
    protected void replaceVarsInBlocks(Map<String, Arg> renames,
        boolean inputsOnly) {
      for (Block b: this.getBlocks()) {
        HashMap<String, Arg> shadowed =
                new HashMap<String, Arg>();

        // See if any of the renamed vars are redeclared,
        //  and if so temporarily remove them from the
        //  rename map
        for (Var v: b.getVariables()) {
          String vName = v.name();
          if (renames.containsKey(vName)) {
            shadowed.put(vName, renames.get(vName));
            renames.remove(vName);
          }
        }
        b.renameVars(renames, inputsOnly);

        renames.putAll(shadowed);
      }
    }

    public abstract void removeVars(Set<String> removeVars);

    protected void removeVarsInBlocks(Set<String> removeVars) {
      for (Block b: this.getBlocks()) {
        b.removeVars(removeVars);
      }
    }

    /**
     * @return all variables whose values are needed to evaluate this construct
     * (e.g. branch condition).  empty list if none
     */
    public abstract Collection<Var> requiredVars();

    /**
     * See if we can predict branch and flatten this to a block
     * @param knownConstants
     * @return a block which is the branch that will run
     */
    public abstract Block branchPredict(Map<String, Arg> knownConstants);

    /**
     * replace variables with constants in loop construct
     * @param knownConstants
     * @return true if anything changed
     */
    public boolean constantReplace(Map<String, Arg> knownConstants) {
      // default: do nothing
      return false;
    }

    /** @return true if the continuation does nothing */
    public abstract boolean isNoop();

    public abstract boolean isAsync();

    /** Return list of variables that the continuations waits for
     * before executing
     * @return
     */
    public abstract List<Var> blockingVars();

    /**
     * Return list of variables that are defined by construct and
     * accessible inside
     * @return
     */
    public abstract List<Var> constructDefinedVars();

    /**
     * @return true if all variables in block containing continuation are
     *        automatically visible in inner blocks
     */
    public abstract boolean variablesPassedInAutomatically();

    public abstract Collection<Var> getPassedInVars();

    public abstract void addPassedInVar(Var variable);

    public abstract void removePassedInVar(Var variable);

    /** 
     * Remove all variables (e.g. arrays that are kept open)
     * This only makes sense to implement for async continuations, so
     * default implementation is for sync
     */
    public void clearKeepOpenVars() {
      // Implementation for synchronous continuations
      assert(!this.isAsync());
    }

    /**
     * 
     * @return List of variables kept open in this scope.
     *        null means none 
     */
    public Collection<Var> getKeepOpenVars() {
      // Implementation for synchronous continuations
      assert(!this.isAsync());
      return null;
    }
    
    /** 
     * Remove all variables (e.g. arrays that are kept open)
     * This only makes sense to implement for async continuations.
     */
    public void addKeepOpenVar(Var v) {
      //Implementation for synchronous continuations
      assert(!this.isAsync());
    }

    public void addKeepOpenVars(Collection<Var> keepOpenVars) {
      for (Var v: keepOpenVars) {
        addKeepOpenVar(v);
      }
    }

    /**
     * Remove this continuation from block, inlining one of
     * the nested blocks inside the continuation (e.g. the predicted branch
     *  of an if statement)
     * @param block the parent block of continuation
     * @param predictedBranch the branch in continuation that will be executed
     */
    public void inlineInto(Block block, Block predictedBranch) {
      // Default implementation
      block.insertInline(predictedBranch);
      block.removeContinuation(this);
    }

    /**
     * Returns true if a change was made.
     *
     * It is ok if the unrolling introduced duplicate variable names in
     * nested blocks (so long as they don't shadow each other) - a
     * subsequent pass will make those names unique
     * @param logger
     * @param outerBlock
     * @return
     */
    public boolean tryUnroll(Logger logger, Block outerBlock) {
      // default: do nothing
      return false;
    }

    /**
     * Try to inline a block, depending on which variables are closed
     * This is also a mechanism to let the continuation know what variables
     * are closed so it can make internal optimizations
     * @param closedVars
     * @return null if it cannot be inlined, a block that is equivalent to
     *          the continuation otherwise
     */
    public Block tryInline(Set<String> closedVars) {
      // Default: do nothing
      return null;
    }

    @Override
    public abstract Continuation clone();
  }

  public enum ContinuationType {
    NESTED_BLOCK,
    IF_STATEMENT,
    SWITCH_STATEMENT,
    FOREACH_LOOP,
    RANGE_LOOP,
    LOOP,
    WAIT_STATEMENT
  }

  public static abstract class AbstractLoop extends Continuation {
    protected Block loopBody;

    protected final List<Var> usedVariables;
    protected final List<Var> keepOpenVars;

    public AbstractLoop(Block block, List<Var> usedVariables,
        List<Var> keepOpenVars) {

      this.usedVariables = new ArrayList<Var>(usedVariables);
      this.keepOpenVars =
                      new ArrayList<Var>(keepOpenVars);
      this.loopBody = block;
    }

    public Block getLoopBody() {
      return loopBody;
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(loopBody);
    }

    @Override
    public Collection<Var> requiredVars() {
      ArrayList<Var> res = new ArrayList<Var>();
      for (Var c: keepOpenVars) {
        if (c.storage() == VarStorage.ALIAS) {
          // We might be holding it open so that a different var can
          // be written inside
        }
      }
      return res;
    }

    protected void checkNotRemoved(Var v, Set<String> removeVars) {
      if (removeVars.contains(v.name())) {
        throw new STCRuntimeError("bad optimization: tried to remove" +
        " required variable " + v.toString());
      }
    }
    protected void checkNotRemoved(Arg o, Set<String> removeVars) {
      if (o.kind == ArgKind.VAR) {
        checkNotRemoved(o.getVar(), removeVars);
      }
    }

    @Override
    public Collection<Var> getPassedInVars() {
      return Collections.unmodifiableList(this.usedVariables);
    }

    @Override
    public void addPassedInVar(Var variable) {
      assert(variable != null);
      this.usedVariables.add(variable);
    }

    @Override
    public void removePassedInVar(Var variable) {
      ICUtil.removeVarInList(usedVariables, variable.name());
    }

    @Override
    public void inlineInto(Block block, Block predictedBranch) {
      throw new STCRuntimeError("Can't inline loops yet");
    }

    protected void fuseIntoAbstract(AbstractLoop o, boolean insertAtTop) {
      this.loopBody.insertInline(o.loopBody, insertAtTop);
      this.keepOpenVars.addAll(o.keepOpenVars);
      ICUtil.removeDuplicates(this.keepOpenVars);
      this.usedVariables.addAll(o.usedVariables);
      ICUtil.removeDuplicates(this.usedVariables);
    }

    @Override
    public void clearKeepOpenVars() {
      this.keepOpenVars.clear();
    }

    @Override
    public void addKeepOpenVar(Var v) {
      this.keepOpenVars.add(v);
    }
  }

  public static class ForeachLoop extends AbstractLoop {
    private Var arrayVar;
    private boolean arrayClosed;
    private Var loopCounterVar;
    private Var loopVar;
    public Var getArrayVar() {
      return arrayVar;
    }

    private final int splitDegree;

    private ForeachLoop(Block block, Var arrayVar, Var loopVar,
        Var loopCounterVar, int splitDegree,
        boolean arrayClosed,
        List<Var> usedVariables, List<Var> keepOpenVars) {
      super(block, usedVariables, keepOpenVars);
      this.arrayVar = arrayVar;
      this.loopVar = loopVar;
      this.loopCounterVar = loopCounterVar;
      this.arrayClosed = arrayClosed;
      this.splitDegree = splitDegree;
    }

    public ForeachLoop(Var arrayVar, Var loopVar,
        Var loopCounterVar, int splitDegree,
        boolean arrayClosed, List<Var> usedVariables,
        List<Var> keepOpenVars) {
      this(new Block(BlockType.FOREACH_BODY), arrayVar, loopVar, loopCounterVar,
          splitDegree, arrayClosed, usedVariables, keepOpenVars);
    }

    @Override
    public ForeachLoop clone() {
      return new ForeachLoop(this.loopBody.clone(),
          arrayVar, loopVar, loopCounterVar, splitDegree, arrayClosed,
          new ArrayList<Var>(usedVariables),
          new ArrayList<Var>(keepOpenVars));
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
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startForeachLoop(arrayVar, loopVar, loopCounterVar,
                splitDegree, arrayClosed, usedVariables, keepOpenVars);
      this.loopBody.generate(logger, gen, info);
      gen.endForeachLoop(splitDegree, arrayClosed,
                                          keepOpenVars);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      if (arrayClosed) {
        sb.append(currentIndent + "@skiparrayblock\n");
      }
      sb.append(currentIndent + "foreach " + loopVar.name());
      if (loopCounterVar != null) {
        sb.append(", " + loopCounterVar.name());
      }
      sb.append(" in " + arrayVar.name() + " ");
      ICUtil.prettyPrintVarInfo(sb, usedVariables, keepOpenVars);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceVars(Map<String, Arg> renames) {
      this.replaceVarsInBlocks(renames, false);
      if (renames.containsKey(arrayVar.name())) {
        arrayVar = renames.get(arrayVar.name()).getVar();
      }
      if (renames.containsKey(loopVar.name())) {
        loopVar = renames.get(loopVar.name()).getVar();
      }
      if (this.loopCounterVar != null &&
          renames.containsKey(loopCounterVar.name())) {
        loopCounterVar = renames.get(loopCounterVar.name()).getVar();
      }
      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, keepOpenVars, true);
    }

    @Override
    public void replaceInputs(Map<String, Arg> renames) {
      // Replace only those we're reading
      this.replaceVarsInBlocks(renames, true);
      if (renames.containsKey(arrayVar.name())) {
        arrayVar = renames.get(arrayVar.name()).getVar();
      }
      ICUtil.replaceVarsInList(renames, usedVariables, true);
    }

    @Override
    public Collection<Var> requiredVars() {
      Collection<Var> res = super.requiredVars();
      res.add(arrayVar);
      return res;
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      checkNotRemoved(arrayVar, removeVars);
      checkNotRemoved(loopVar, removeVars);
      if (loopCounterVar != null) {
        checkNotRemoved(loopCounterVar, removeVars);
      }
      ICUtil.removeVarsInList(this.keepOpenVars, removeVars);
      ICUtil.removeVarsInList(this.usedVariables, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      return null;
    }

    @Override
    public boolean isNoop() {
      return this.loopBody.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }

    @Override
    public List<Var> constructDefinedVars() {
      return loopCounterVar == null ?
                Arrays.asList(loopVar)
              : Arrays.asList(loopCounterVar, loopVar);
    }

    @Override
    public List<Var> blockingVars() {
      return null;
    }

    @Override
    public Block tryInline(Set<String> closedVars) {
      if (closedVars.contains(arrayVar.name())) {
        this.arrayClosed = true;
      }
      return null;
    }

    public boolean fuseable(ForeachLoop o) {
      // annotation parameters should match to respect any
      // user settings
      return this.arrayVar.name().equals(o.arrayVar.name())
          && this.splitDegree == o.splitDegree;
    }

    public void fuseInto(ForeachLoop o, boolean insertAtTop) {
      Map<String, Arg> renames = new HashMap<String, Arg>();
      renames.put(o.loopVar.name(), Arg.createVar(this.loopVar));
      // Handle optional loop counter var
      if (o.loopCounterVar != null) {
        if (this.loopCounterVar != null) {
          renames.put(o.loopCounterVar.name(),
                      Arg.createVar(this.loopCounterVar));    
        } else {
          this.loopCounterVar = o.loopCounterVar;
        }
      }
      o.replaceVars(renames);
      
      fuseIntoAbstract(o, insertAtTop);
    }

  }

  public static class IfStatement extends Continuation {
    private final Block thenBlock;
    private final Block elseBlock;
    private Arg condition;

    public IfStatement(Arg condition) {
      this(condition, new Block(BlockType.THEN_BLOCK),
                          new Block(BlockType.ELSE_BLOCK));
    }

    private IfStatement(Arg condition, Block thenBlock, Block elseBlock) {
      assert(thenBlock != null);
      assert(elseBlock != null);
      this.condition = condition;
      this.thenBlock = thenBlock;
      // Always have an else block to make more uniform: empty block is then
      // equivalent to no else block
      this.elseBlock = elseBlock;
    }

    @Override
    public IfStatement clone() {
      return new IfStatement(condition, thenBlock.clone(), elseBlock.clone());
    }

    public Block getThenBlock() {
      return thenBlock;
    }

    public Block getElseBlock() {
      return elseBlock;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      boolean hasElse = !(elseBlock.isEmpty());
      gen.startIfStatement(condition, hasElse);
      this.thenBlock.generate(logger, gen, info);
      if (hasElse) {
        gen.startElseBlock();
        this.elseBlock.generate(logger, gen, info);
      }
      gen.endIfStatement();
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      String newIndent = currentIndent + indent;
      sb.append(currentIndent + "if (");
      sb.append(this.condition.toString());
      sb.append(") {\n");
      thenBlock.prettyPrint(sb, newIndent);
      if (!elseBlock.isEmpty()) {
        sb.append(currentIndent + "} else {\n");
        elseBlock.prettyPrint(sb, newIndent);
      }
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(thenBlock, elseBlock);
    }

    @Override
    public void replaceVars(Map<String, Arg> renames) {
      replaceShared(renames, false);
    }

    @Override
    public void replaceInputs(Map<String, Arg> renames) {
      replaceShared(renames, true);
    }

    private void replaceShared(Map<String, Arg> renames,
          boolean inputsOnly) {
      replaceVarsInBlocks(renames, inputsOnly);
      condition = ICUtil.replaceOparg(renames, condition, false);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.IF_STATEMENT;
    }

    @Override
    public boolean isAsync() {
      return false;
    }

    @Override
    public Collection<Var> requiredVars() {
      if (condition.isVar()) {
        return Arrays.asList(condition.getVar());
      } else {
        return Collections.emptyList();
      }
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      assert(!condition.isVar() ||
            (!removeVars.contains(condition.getVar().name())));
    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      Arg val;
      
      if (condition.isVar()) {
        val = knownConstants.get(condition.getVar().name());
        if (val == null) {
          return null;
        }
      } else {
       val = condition; 
      }
      
      assert(val.isIntVal()
            || val.isBoolVal());
      if (val.isIntVal()
          && val.getIntLit() != 0) {
        return thenBlock;
      } else if (val.isBoolVal() &&
          val.getBoolLit()) {
        return thenBlock;
      } else {
        return elseBlock;
      }
    }

    @Override
    public boolean isNoop() {
      return thenBlock.isEmpty() && elseBlock.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return true;
    }

    @Override
    public Collection<Var> getPassedInVars() {
      // doesn't apply
      return null;
    }

    @Override
    public void addPassedInVar(Var variable) {
      throw new STCRuntimeError("addPassedInVar not supported on if");
    }

    @Override
    public void removePassedInVar(Var variable) {
      throw new STCRuntimeError("removePassedInVar not supported on " +
      "if");
    }

    @Override
    public List<Var> constructDefinedVars() {
      return null;
    }

    @Override
    public List<Var> blockingVars() {
      return null;
    }

    /**
     * Can these be fused into one if statement
     * @param other
     * @return
     */
    public boolean fuseable(IfStatement other) {
      return this.condition.equals(other.condition);
              
    }

    /**
     * Fuse other if statement into this
     * @param other
     * @param insertAtTop if true, insert code from other about
     *    code from this in blcoks
     */
    public void fuse(IfStatement other, boolean insertAtTop) {
      thenBlock.insertInline(other.thenBlock, insertAtTop);
      elseBlock.insertInline(other.elseBlock, insertAtTop);
      
    }
  }

  public static class Loop extends AbstractLoop {
    private final String loopName;
    private final Var condVar;
    private final List<Var> loopVars;
    private final List<Var> initVals;

    /*
     * Have handles to the termination instructions
     */
    // private LoopBreak loopBreak;

    private LoopContinue loopContinue;
    private final ArrayList<Boolean> blockingVars;


    public Loop(String loopName, List<Var> loopVars,
            List<Var> initVals, List<Var> usedVariables,
            List<Var> keepOpenVars, List<Boolean> blockingVars) {
      this(loopName, new Block(BlockType.LOOP_BODY), loopVars, initVals,
          usedVariables, keepOpenVars, blockingVars);
    }

    private Loop(String loopName, Block loopBody,
        List<Var> loopVars,  List<Var> initVals,
        List<Var> usedVariables, List<Var> keepOpenVars,
        List<Boolean> blockingVars) {
      super(loopBody, usedVariables, keepOpenVars);
      this.loopName = loopName;
      this.condVar = loopVars.get(0);
      this.loopVars = new ArrayList<Var>(loopVars);
      this.initVals = new ArrayList<Var>(initVals);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
      assert(loopVars.size() == initVals.size());
      for (int i = 0; i < loopVars.size(); i++) {
        Var loopV = loopVars.get(i);
        Var initV = initVals.get(i);
        if (!loopV.type().equals(initV.type())) {
          throw new STCRuntimeError("loop variable " + loopV.toString()
              + " is given init value of wrong type: " + initV.toString());
        }
      }
    }

    @Override
    public Loop clone() {
      // Constructor creates copies of variable lists
      return new Loop(loopName, this.loopBody.clone(), loopVars, initVals,
          usedVariables, keepOpenVars, blockingVars);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.LOOP;
    }

    @Override
    public boolean isAsync() {
      return true;
    }

    public void setLoopBreak(LoopBreak loopBreak) {
      // this.loopBreak = loopBreak;
    }

    public void setLoopContinue(LoopContinue loopContinue) {
      this.loopContinue = loopContinue;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startLoop(loopName, loopVars, initVals,
                    usedVariables, keepOpenVars, blockingVars);
      this.loopBody.generate(logger, gen, info);
      gen.endLoop();
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      sb.append(currentIndent + "loop /*" + loopName + "*/\n");
      sb.append(currentIndent + indent + indent + "while (");
      sb.append(condVar.type().typeName() + " " + condVar.name());
      sb.append(")\n" + currentIndent + indent + indent + "loopvars (");
      boolean first = true;
      for (int i = 0; i < loopVars.size(); i++) {
        Var loopV = loopVars.get(i);
        Var initV = initVals.get(i);
        if (first) {
          first = false;
        } else {
          sb.append(", ");
        }
        sb.append(loopV.type().typeName() + " " + loopV.name() + "="
            + initV.name());
      }

      sb.append(")\n" + currentIndent + indent + indent);
      ICUtil.prettyPrintVarInfo(sb, usedVariables, keepOpenVars);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceVars(Map<String, Arg> renames) {
      this.replaceVarsInBlocks(renames, false);
      ICUtil.replaceVarsInList(renames, initVals, false);
      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, keepOpenVars, true);
    }

    @Override
    public void replaceInputs(Map<String, Arg> renames) {
      this.replaceVarsInBlocks(renames, true);
      ICUtil.replaceVarsInList(renames, initVals, false);
    }

    @Override
    public Collection<Var> requiredVars() {
      Collection<Var> res = super.requiredVars();
      res.addAll(initVals);
      return res;
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      // check it isn't removing initial values
      for (Var v: this.initVals) {
        checkNotRemoved(v, removeVars);
      }
      for (Var v: this.loopVars) {
        checkNotRemoved(v, removeVars);
      }
      ICUtil.removeVarsInList(this.keepOpenVars, removeVars);
      ICUtil.removeVarsInList(this.usedVariables, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      return null;
    }

    @Override
    public boolean isNoop() {
      // TODO: think about particular conditions that would render it a noop.
      //
      return false;
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }

    @Override
    public List<Var> constructDefinedVars() {
      ArrayList<Var> defVars = new ArrayList<Var>(
                                      loopVars.size() + 1);
      defVars.addAll(this.loopVars);
      defVars.add(this.condVar);
      return defVars;
    }

    @Override
    public List<Var> blockingVars() {
      ArrayList<Var> res = new ArrayList<Var>();
      res.add(condVar);
      for (int i = 0; i < loopVars.size(); i++) {
        if (blockingVars.get(i)) {
          res.add(loopVars.get(i));
        }
      }
      return res;
    }


    @Override
    public void addPassedInVar(Var variable) {
      // special implementation to also fix up the loopContinue instruction
      assert(variable != null);
      super.addPassedInVar(variable);
      this.loopContinue.addUsedVar(variable);
    }

    @Override
    public void removePassedInVar(Var variable) {
      // special implementation to also fix up the loopContinue instruction
      super.removePassedInVar(variable);
      this.loopContinue.removeUsedVar(variable);
    }

    public Var getInitCond() {
      return this.initVals.get(0);
    }
  }

  /**
   * For now, treat nested blocks as continuations.  If we implement a
   * proper variable renaming scheme for nested blocks we can just
   * flatten them into the main block
   */
  public static class NestedBlock extends Continuation {
    private final Block block;

    public NestedBlock() {
      this(new Block(BlockType.NESTED_BLOCK));
    }


    private NestedBlock(Block block) {
      this.block = block;
    }

    @Override
    public NestedBlock clone() {
      return new NestedBlock(this.block.clone());
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startNestedBlock();
      block.generate(logger, gen, info);
      gen.endNestedBlock();
    }

    public Block getBlock() {
      return this.block;
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      sb.append(currentIndent + "{\n");
      block.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(block);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.NESTED_BLOCK;
    }

    @Override
    public boolean isAsync() {
      return false;
    }


    @Override
    public void replaceVars(Map<String, Arg> renames) {
      replaceVarsInBlocks(renames, false);
    }

    @Override
    public void replaceInputs(Map<String, Arg> renames) {
      replaceVarsInBlocks(renames, true);
    }

    @Override
    public Collection<Var> requiredVars() {
      return new ArrayList<Var>(0);
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      return null;
    }

    @Override
    public boolean isNoop() {
      return block.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return true;
    }

    @Override
    public Collection<Var> getPassedInVars() {
      return null;
    }

    @Override
    public void addPassedInVar(Var variable) {
      throw new STCRuntimeError("addPassedInVar not supported on " +
      "nested");
    }

    @Override
    public void removePassedInVar(Var variable) {
      throw new STCRuntimeError("removePassedInVar not supported on " +
          "nested block");
    }

    @Override
    public List<Var> constructDefinedVars() {
      return null;
    }

    @Override
    public List<Var> blockingVars() {
      return null;
    }
  }

  public static class RangeLoop extends AbstractLoop {
    // arguments can be either value variable or integer literal
    private final String loopName;
    private Var loopVar;
    private Var countVar;
    private Arg start;
    private Arg end;
    private Arg increment;
    private int desiredUnroll;
    private final int splitDegree;

    public RangeLoop(String loopName, Var loopVar, Var countVar,
        Arg start, Arg end, Arg increment,
        List<Var> usedVariables, List<Var> keepOpenVars,
        int desiredUnroll, int splitDegree) {
      this(loopName, new Block(BlockType.RANGELOOP_BODY), loopVar, countVar,
          start, end, increment, usedVariables, keepOpenVars,
          desiredUnroll, splitDegree);
    }

    private RangeLoop(String loopName, Block block, Var loopVar, Var countVar,
        Arg start, Arg end, Arg increment,
        List<Var> usedVariables, List<Var> keepOpenVars,
        int desiredUnroll, int splitDegree) {
      super(block, usedVariables, keepOpenVars);
      assert(start.isImmediateInt());
      assert(end.isImmediateInt());
      assert(increment.isImmediateInt());
      assert(loopVar.type().equals(Types.V_INT));
      this.loopName = loopName;
      this.loopVar = loopVar;
      this.countVar = countVar;
      this.start = start;
      this.end = end;
      this.increment = increment;
      this.desiredUnroll = desiredUnroll;
      this.splitDegree = splitDegree;
    }

    @Override
    public RangeLoop clone() {
      return new RangeLoop(loopName, this.loopBody.clone(), loopVar, countVar,
          start.clone(), end.clone(), increment.clone(),
          new ArrayList<Var>(usedVariables),
          new ArrayList<Var>(keepOpenVars), desiredUnroll,
          splitDegree);
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
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startRangeLoop(loopName, loopVar, countVar, start, end, increment,
                         usedVariables, keepOpenVars,
                         desiredUnroll, splitDegree);
      this.loopBody.generate(logger, gen, info);
      gen.endRangeLoop(keepOpenVars, splitDegree);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      sb.append(currentIndent +   "for " + loopVar.name());
      if (countVar != null) {
        sb.append(", " + countVar.name());
      }

      sb.append(" = " + start.toString() + " to " + end.toString() + " ");

      if (!increment.isIntVal() || increment.getIntLit() != 1) {
          sb.append("incr " + increment.toString() + " ");
      }
      ICUtil.prettyPrintVarInfo(sb, usedVariables, keepOpenVars);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceVars(Map<String, Arg> renames) {
      this.replaceVarsInBlocks(renames, false);
      if (renames.containsKey(loopVar.name())) {
        loopVar = renames.get(loopVar.name()).getVar();
      }
      if (renames.containsKey(countVar.name())) {
        countVar = renames.get(countVar.name()).getVar();
      }
      start = renameRangeArg(start, renames);
      end = renameRangeArg(end, renames);
      increment = renameRangeArg(increment, renames);

      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, keepOpenVars, true);
    }
    @Override
    public void replaceInputs(Map<String, Arg> renames) {
      this.replaceVarsInBlocks(renames, true);
      start = renameRangeArg(start, renames);
      end = renameRangeArg(end, renames);
      increment = renameRangeArg(increment, renames);
    }

    private Arg renameRangeArg(Arg val, Map<String, Arg> renames) {
      if (val.kind == ArgKind.VAR) {
        String vName = val.getVar().name();
        if (renames.containsKey(vName)) {
          Arg o = renames.get(vName);
          assert(o != null);
          return o;
        }
      }
      return val;
    }

    @Override
    public Collection<Var> requiredVars() {
      Collection<Var> res = super.requiredVars();
      for (Arg o: Arrays.asList(start, end, increment)) {
        if (o.isVar()) {
          res.add(o.getVar());
        }
      }
      return res;
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      checkNotRemoved(start, removeVars);
      checkNotRemoved(end, removeVars);
      checkNotRemoved(increment, removeVars);
      removeVarsInBlocks(removeVars);
      ICUtil.removeVarsInList(this.keepOpenVars, removeVars);
      ICUtil.removeVarsInList(this.usedVariables, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      // Could inline loop if there is only one iteration...
      if (start.isIntVal() && end.isIntVal()) {
        long startV = start.getIntLit();
        long endV = end.getIntLit();
        boolean singleIter = false;
        if (endV < startV) {
          // Doesn't run - return empty block
          return new Block(BlockType.FOREACH_BODY);
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
      if (countVar != null) {
        this.loopBody.declareVariable(countVar);
        this.loopBody.addInstructionFront(Builtin.createLocal(
                     BuiltinOpcode.COPY_INT, countVar, Arg.createIntLit(0)));
      }
      block.insertInline(loopBody);
      block.removeContinuation(this);
    }

    @Override
    public boolean constantReplace(Map<String, Arg> knownConstants) {
      boolean anyChanged = false;
      Arg oldVals[] = new Arg[] {start, end, increment };
      Arg newVals[] = new Arg[3];
      for (int i = 0; i < oldVals.length; i++) {
        Arg old = oldVals[i];
        if (old.kind == ArgKind.VAR) {
          Arg replacement = knownConstants.get(old.getVar().name());
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

      assert(start != null); assert(end != null); assert(increment  != null);
      return anyChanged;
    }

    @Override
    public boolean isNoop() {
      return this.loopBody.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }

    @Override
    public List<Var> constructDefinedVars() {
      if (countVar != null) {
        return Arrays.asList(loopVar, countVar);
      } else {
        return Arrays.asList(loopVar);
      }
    }

    @Override
    public List<Var> blockingVars() {
      return null;
    }

    @Override
    public boolean tryUnroll(Logger logger, Block outerBlock) {
      logger.trace("DesiredUnroll for " + loopName + ": " + desiredUnroll);
      if (this.desiredUnroll > 1) {
        if (this.countVar != null) {
          logger.warn("Can't unroll range loop with counter variable yet," +
                      " ignoring unroll annotation");
          return false;
        }
        logger.debug("Unrolling range loop " + desiredUnroll + " times ");
        Arg oldStep = this.increment;

        long checkIter; // the time we need to check
        if(increment.isIntVal() &&
            start.isIntVal() &&
            end.isIntVal()) {
          long startV = start.getIntLit();
          long endV = end.getIntLit();
          long incV = increment.getIntLit();

          long diff = (endV - startV + 1);
          // Number of loop iterations
          long iters = ( (diff - 1) / incV ) + 1;

          // 0 if the number of iterations will go exactly into the
          // unroll factor
          long extra = iters % desiredUnroll;

          if (extra == 0) {
            checkIter = desiredUnroll;
          } else {
            checkIter = extra;
          }
        } else {
          checkIter = -1;
        }

        // Update step
        if (oldStep.isIntVal()) {
          this.increment = Arg.createIntLit(oldStep.getIntLit() * desiredUnroll);
        } else {
          Var old = oldStep.getVar();
          Var newIncrement = new Var(old.type(),
              old.name() + "@unroll" + desiredUnroll,
              VarStorage.LOCAL,
              DefType.LOCAL_COMPILER, null);
          outerBlock.declareVariable(newIncrement);
          outerBlock.addInstruction(Builtin.createLocal(BuiltinOpcode.MULT_INT,
              newIncrement, Arrays.asList(oldStep, Arg.createIntLit(desiredUnroll))));

          this.increment = Arg.createVar(newIncrement);
        }

        // Create a copy of the original loop body for reference
        Block orig = loopBody;
        this.loopBody = new Block(BlockType.LOOP_BODY);
        Block curr = loopBody;
        Var nextIter = loopVar; // Variable with current iter number

        for (int i = 0; i < desiredUnroll; i++) {
          // Put everything in nested block
          NestedBlock nb = new NestedBlock(orig.clone(BlockType.NESTED_BLOCK));
          curr.addContinuation(nb);
          if (i != 0) {
            // Replace references to the iteration counter
            nb.replaceVars(Collections.singletonMap(this.loopVar.name(),
                                            Arg.createVar(nextIter)));
          }

          if (i < desiredUnroll - 1) {
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
              IfStatement ifSt = new IfStatement(Arg.createVar(
                                                        nextIterCheck));
              curr.addContinuation(ifSt);

              curr = ifSt.getThenBlock();
            }
          } else {
            curr = null;
          }
        }
        this.desiredUnroll = 1;
        return true;
      }
      return false;
    }
    
    public boolean fuseable(RangeLoop o) {
      // Make sure loop bounds line up, but also annotations since we
      // want to respect any user annotations
      return this.start.equals(o.start)
          && this.increment.equals(o.increment)
          && this.end.equals(o.end)
          && this.desiredUnroll == o.desiredUnroll
          && this.splitDegree == o.splitDegree
          && (this.countVar == null) == (o.countVar == null);
    }
    
    /**
     * Fuse the other loop into this loop
     */
    public void fuseInto(RangeLoop o, boolean insertAtTop) {
      Map<String, Arg> renames = new HashMap<String, Arg>();
      // Update loop var in other loop
      renames.put(o.loopVar.name(), Arg.createVar(this.loopVar));
      if (countVar != null)
        renames.put(o.countVar.name(), Arg.createVar(this.countVar));
      o.replaceVars(renames);
     
      this.fuseIntoAbstract(o, insertAtTop);
    }
  }

  public static class SwitchStatement extends Continuation {
    private final ArrayList<Integer> caseLabels;
    private final ArrayList<Block> caseBlocks;
    private final Block defaultBlock;
    private Arg switchVar;

    public SwitchStatement(Arg switchVar, List<Integer> caseLabels) {
      this(switchVar, new ArrayList<Integer>(caseLabels),
          new ArrayList<Block>(), new Block(BlockType.CASE_BLOCK));

      // number of non-default cases
      int caseCount = caseLabels.size();
      for (int i = 0; i < caseCount; i++) {
        this.caseBlocks.add(new Block(BlockType.CASE_BLOCK));
      }
    }

    private SwitchStatement(Arg switchVar, ArrayList<Integer> caseLabels,
        ArrayList<Block> caseBlocks, Block defaultBlock) {
      super();
      this.switchVar = switchVar;
      this.caseLabels = caseLabels;
      this.caseBlocks = caseBlocks;
      this.defaultBlock = defaultBlock;
    }

    @Override
    public SwitchStatement clone() {
      return new SwitchStatement(switchVar,
          new ArrayList<Integer>(this.caseLabels),
          ICUtil.cloneBlocks(this.caseBlocks), this.defaultBlock.clone());

    }

    public List<Block> caseBlocks() {
      return Collections.unmodifiableList(caseBlocks);
    }

    public Block getDefaultBlock() {
      return this.defaultBlock;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      boolean hasDefault = !defaultBlock.isEmpty();
      gen.startSwitch(switchVar, caseLabels, hasDefault);

      for (Block b: this.caseBlocks) {
        b.generate(logger, gen, info);
        gen.endCase();
      }

      if (hasDefault) {
        defaultBlock.generate(logger, gen, info);
        gen.endCase();
      }

      gen.endSwitch();
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      assert(this.caseBlocks.size() == this.caseLabels.size());
      String caseIndent = currentIndent + indent;
      String caseBlockIndent = caseIndent + indent;
      sb.append(currentIndent + "switch (" + switchVar.toString() + ") {\n");
      for (int i = 0; i < caseLabels.size(); i++) {
        sb.append(caseIndent + "case " + caseLabels.get(i) + " {\n");
        caseBlocks.get(i).prettyPrint(sb, caseBlockIndent);
        sb.append(caseIndent + "}\n");
      }
      if (!defaultBlock.isEmpty()) {
        sb.append(caseIndent + "default {\n");
        defaultBlock.prettyPrint(sb, caseBlockIndent);
        sb.append(caseIndent + "}\n");
      }
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      List<Block> result = new ArrayList<Block>();
      result.addAll(this.caseBlocks);
      result.add(defaultBlock);
      return result;
    }

    @Override
    public void replaceVars(Map<String, Arg> renames) {
      replaceVarsInBlocks(renames, false);
      switchVar = ICUtil.replaceOparg(renames, switchVar, false);
    }

    @Override
    public void replaceInputs(Map<String, Arg> renames) {
      replaceVarsInBlocks(renames, true);
      switchVar = ICUtil.replaceOparg(renames, switchVar, false);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.SWITCH_STATEMENT;
    }

    @Override
    public boolean isAsync() {
      return false;
    }

    @Override
    public Collection<Var> requiredVars() {
      if (switchVar.isVar()) {
        return Arrays.asList(switchVar.getVar());
      } else {
        return Collections.emptyList();
      }
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      assert(!switchVar.isVar() 
          || !removeVars.contains(switchVar.getVar().name()));
      defaultBlock.removeVars(removeVars);
      for (Block caseBlock: this.caseBlocks) {
        caseBlock.removeVars(removeVars);
      }

    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      long val;
      if (switchVar.isVar()) {
        Arg switchVal = knownConstants.get(switchVar.getVar().name());
        if (switchVal == null) {
          return null;
        }
        assert(switchVal.isIntVal());
        val = switchVal.getIntLit();
      } else {
        val = switchVar.getIntLit();
      }
      // Check cases
      for (int i = 0; i < caseLabels.size(); i++) {
        if (val == caseLabels.get(i)) {
          return caseBlocks.get(i);
        }
      }
      // Otherwise return (maybe empty) default block
      return defaultBlock;
    }

    @Override
    public boolean isNoop() {
      for (Block b: caseBlocks) {
        if (!b.isEmpty()) {
          return false;
        }
      }
      return this.defaultBlock.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return true;
    }

    @Override
    public Collection<Var> getPassedInVars() {
      return null;
    }

    @Override
    public void addPassedInVar(Var variable) {
      throw new STCRuntimeError("addPassedInVar not supported on switch");
    }

    @Override
    public void removePassedInVar(Var variable) {
      throw new STCRuntimeError("removePassedInVar not supported on " +
          "switch");
    }

    @Override
    public List<Var> constructDefinedVars() {
      return null;
    }

    @Override
    public List<Var> blockingVars() {
      return null;
    }
  }

  /**
   * A new construct that blocks on a list of variables (blockVars),
   * and only runs the contents once all of those variables are closed
   *
   */
  public static class WaitStatement extends Continuation {
    /** Label name to use in final generated code */
    private final String procName;
    private final Block block;
    private final ArrayList<Var> waitVars;
    private final ArrayList<Var> usedVariables;
    private final ArrayList<Var> keepOpenVars;
    /* True if this wait was compiler-generated so can be removed if needed
     * We can only remove an explicit wait if we know that the variables are
     * already closed*/
    private final WaitMode mode;
    private final TaskMode target;

    public WaitStatement(String procName, List<Var> waitVars,
                    List<Var> usedVariables,
                    List<Var> keepOpenVars,
                    WaitMode mode, TaskMode target) {
      this(procName, new Block(BlockType.WAIT_BLOCK),
                        new ArrayList<Var>(waitVars),
                        new ArrayList<Var>(usedVariables),
                        new ArrayList<Var>(keepOpenVars),
                        mode, target);
    }

    private WaitStatement(String procName, Block block,
        ArrayList<Var> waitVars, ArrayList<Var> usedVariables,
        ArrayList<Var> keepOpenVars, WaitMode mode, TaskMode target) {
      super();
      assert(waitVars != null);
      assert(usedVariables != null);
      assert(keepOpenVars != null);
      this.procName = procName;
      this.block = block;
      this.waitVars = waitVars;
      ICUtil.removeDuplicates(waitVars);
      this.usedVariables = usedVariables;
      this.keepOpenVars = keepOpenVars;
      this.mode = mode;
      this.target = target;
    }

    @Override
    public WaitStatement clone() {
      return new WaitStatement(procName, this.block.clone(),
          new ArrayList<Var>(waitVars),
          new ArrayList<Var>(usedVariables),
          new ArrayList<Var>(keepOpenVars), mode, target);
    }

    public Block getBlock() {
      return block;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {

      gen.startWaitStatement(procName, waitVars, usedVariables,
          keepOpenVars, mode, target);
      this.block.generate(logger, gen, info);
      gen.endWaitStatement(keepOpenVars);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      String newIndent = currentIndent + indent;
      sb.append(currentIndent + "wait (");
      ICUtil.prettyPrintVarList(sb, waitVars);
      sb.append(") ");
      sb.append("/*" + procName + "*/ " );
      ICUtil.prettyPrintVarInfo(sb, usedVariables, keepOpenVars);
      sb.append(" <" + mode.toString() + ">");
      sb.append(" {\n");
      block.prettyPrint(sb, newIndent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(block);
    }

    @Override
    public void replaceVars(Map<String, Arg> renames) {
      replaceVarsInBlocks(renames, false);
      ICUtil.replaceVarsInList(renames, waitVars, true);
      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, keepOpenVars, true);
    }

    @Override
    public void replaceInputs(Map<String, Arg> renames) {
      replaceVarsInBlocks(renames, true);
      ICUtil.replaceVarsInList(renames, waitVars, true);
    }

    public WaitMode getMode() {
      return mode;
    }
    
    public TaskMode getTarget() {
      return target;
    }
    @Override
    public ContinuationType getType() {
      return ContinuationType.WAIT_STATEMENT;
    }

    @Override
    public boolean isAsync() {
      return true;
    }

    @Override
    public Collection<Var> requiredVars() {
      ArrayList<Var> res = new ArrayList<Var>();
      for (Var c: keepOpenVars) {
        if (c.storage() == VarStorage.ALIAS) {
          // Might be alias for actual array written inside
          res.add(c);
        }
      }
      if (mode == WaitMode.EXPLICIT) {
        for (Var v: waitVars) {
          res.add(v);
        }
      }
      return res; // can later eliminate waitVars, etc
    }

    public List<Var> getWaitVars() {
      return Collections.unmodifiableList(this.waitVars);
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      ICUtil.removeVarsInList(waitVars, removeVars);
      ICUtil.removeVarsInList(usedVariables, removeVars);
      ICUtil.removeVarsInList(keepOpenVars, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      // We can't really do branch prediction for a wait statement, but
      // it is a useful mechanism to piggy-back on to remove the wait
      return tryInline(knownConstants.keySet());
    }

    @Override
    public boolean isNoop() {
      return this.block.isEmpty();
    }

    public void removeWaitVars(Set<String> closedVars) {
      // In our current implementation, this is equivalent
      tryInline(closedVars);
    }
    
    @Override
    public Block tryInline(Set<String> closedVars) {
      boolean varsLeft = false;
      // iterate over wait vars, remove those in list
      ListIterator<Var> it = waitVars.listIterator();
      while(it.hasNext()) {
        Var wv = it.next();
        if (closedVars.contains(wv.name())) {
          it.remove();
        } else {
          varsLeft = true;
        }
      }
      // Can't eliminate if purpose of wait is to dispatch task
      if (varsLeft || mode == WaitMode.TASK_DISPATCH) {
        return null;
      } else {
        // if at end we have nothing left, return the inner block for inlining
        return block;
      }
    }

    @Override
    public List<Var> blockingVars() {
      return Collections.unmodifiableList(this.waitVars);
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }
    
    @Override
    public Collection<Var> getKeepOpenVars() {
      return Collections.unmodifiableList(this.keepOpenVars);
    }

    public Collection<Var> getUsedVariables() {
      return Collections.unmodifiableList(this.usedVariables);
    }


    public void addUsedVariables(Collection<Var> usedVars) {
      this.usedVariables.addAll(usedVars);
    }

    @Override
    public Collection<Var> getPassedInVars() {
      return Collections.unmodifiableList(this.usedVariables);
    }

    @Override
    public void addPassedInVar(Var variable) {
      this.usedVariables.add(variable);
    }

    @Override
    public void removePassedInVar(Var variable) {
      ICUtil.removeVarInList(this.usedVariables, variable.name());
    }

    @Override
    public List<Var> constructDefinedVars() {
      return null;
    }

    @Override
    public void clearKeepOpenVars() {
      this.keepOpenVars.clear();
    }

    @Override
    public void addKeepOpenVar(Var v) {
      this.keepOpenVars.add(v);
    }
  }

}
