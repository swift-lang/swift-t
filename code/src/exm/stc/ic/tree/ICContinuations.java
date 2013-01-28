/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.ic.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.Pair;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.LoopBreak;
import exm.stc.ic.tree.ICInstructions.LoopContinue;
import exm.stc.ic.tree.ICInstructions.Opcode;
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
    private Block parent;
    
    protected Continuation() {
      this.parent = null;
    }
    
    public abstract ContinuationType getType();
    
    public Block parent() {
      return this.parent;
    }


    public void setParent(Block parent) {
      assert(parent != null);
      this.parent = parent;
    }
    
    public abstract void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException;

    public abstract void prettyPrint(StringBuilder sb, String currentIndent);

    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb, "");
      return sb.toString();
    }
    
    /** Returns all nested blocks in this continuation */
    public abstract List<Block> getBlocks();
    
    /** 
     * @param renames
     * @param inputsOnly only change inputs
     * @param recursive recursively do replacement in inner blocks
     */
    public void replaceVars(Map<String, Arg> renames, boolean inputsOnly,
                                     boolean recursive) {
      if (recursive) {
        this.replaceVarsInBlocks(renames, inputsOnly);
      }
      this.replaceConstructVars(renames, inputsOnly);
    }
    
    protected abstract void replaceConstructVars(Map<String, Arg> renames,
                                                 boolean inputsOnly);
    
    protected void replaceVarsInBlocks(Map<String, Arg> renames,
        boolean inputsOnly) {
      for (Block b: this.getBlocks()) {
        b.renameVars(renames, inputsOnly);
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
    public List<BlockingVar> blockingVars() {
      // default implementation for sync continuations
      assert(!isAsync());
      return null;
    }

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
    public boolean inheritsParentVars() {
      // Generally non-async continuations inherit scope
      return !isAsync();
    }

    public Collection<Var> getPassedInVars() {
      // Implementation for synchronous continuations
      assert(!this.isAsync());
      return null;
    }
    
    public void clearPassedInVars() {
      // Implementation for synchronous continuations
      assert(!this.isAsync());
    }
    
    public void addPassedInVar(Var variable) {
      // Implementation for synchronous continuations
      assert(!this.isAsync());
    }
    
    public void addPassedInVars(Collection<Var> variables) {
      for (Var v: variables) {
        addPassedInVar(v);
      }
    }
    public void removePassedInVar(Var variable) {
      // Implementation for synchronous continuations
      assert(!this.isAsync());
    }

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
      if (parent != null)
        parent.removeContinuation(this);
      else
        System.err.println("No parent for " + this.toString());
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
     * @param closedVars variables which are closed
     * @param recClosedVars variables which are recursively closed (may
     *    overlap with closed)
     * @return null if it cannot be inlined, a block that is equivalent to
     *          the continuation otherwise
     */
    public Block tryInline(Set<String> closedVars, Set<String> recClosedVars) {
      // Default: do nothing
      return null;
    }
    
    /**
     * update continuation given info about which vars are used in block
     * @param usedVars vars used within inner blocks
     */
    public void removeUnused(Set<String> usedVars) {
      // Default: do nothing
    }

    /**
     * Return the execution context inside the continuation
     * @param outerContext the context outside
     * @return
     */
    public ExecContext childContext(ExecContext outerContext) {
      // Default implementation for sync continuations
      assert(!isAsync());
      return outerContext;
    }
    
    @Override
    public abstract Continuation clone();
    
    /**
     * If the continuation should be put after all other continuations
     */
    private boolean runLast = false;
    public void setRunLast(boolean val) {
      runLast = val;
    }
    public boolean runLast() {
      return runLast;
    }

    public abstract boolean isLoop();

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
  
  /**
   * A variable that must be closed for a computation to proceed
   */
  public static class BlockingVar {
    public final Var var;
    /** Whether variable must be recursively closed */
    public final boolean recursive;
    
    public BlockingVar(Var var, boolean recursive) {
      super();
      this.var = var;
      this.recursive = recursive;
    }
    
  }
  
  public static abstract class AsyncContinuation extends Continuation {
    protected final List<Var> passedInVars;
    protected final List<Var> keepOpenVars;
    public AsyncContinuation(List<Var> usedVars,
                            List<Var> keepOpenVars) {
      this.passedInVars = new ArrayList<Var>(usedVars);
      this.keepOpenVars = new ArrayList<Var>(keepOpenVars);
    }
    @Override
    public Collection<Var> getPassedInVars() {
      return Collections.unmodifiableList(this.passedInVars);
    }
    @Override
    public void addPassedInVar(Var variable) {
      assert(variable != null);
      this.passedInVars.add(variable);
      ICUtil.removeDuplicates(this.passedInVars);
    }
    @Override
    public void addPassedInVars(Collection<Var> vars) {
      assert(vars != null);
      this.passedInVars.addAll(vars);
      ICUtil.removeDuplicates(this.passedInVars);
    }
    @Override
    public void removePassedInVar(Var variable) {
      assert(variable != null);
      ICUtil.removeVarInList(passedInVars, variable.name());
    }

    @Override
    public void clearPassedInVars() {
      this.passedInVars.clear();
    }
    
    @Override
    public void clearKeepOpenVars() {
      this.keepOpenVars.clear();
    }
    @Override
    public void addKeepOpenVar(Var v) {
      this.keepOpenVars.add(v);
    }
    
    @Override
    public void addKeepOpenVars(Collection<Var> v) {
      this.keepOpenVars.addAll(v);
    }

    @Override
    public Collection<Var> getKeepOpenVars() {
      return Collections.unmodifiableList(this.keepOpenVars);
    }
    /**
     * For overriding by child class
     * @param renames
     * @param inputsOnly
     */
    public abstract void replaceConstructVars_(Map<String, Arg> renames, 
            boolean inputsOnly);
    
    @Override
    public final void replaceConstructVars(Map<String, Arg> renames, 
        boolean inputsOnly) {
      this.replaceConstructVars_(renames, inputsOnly);
      if (!inputsOnly) {
        ICUtil.replaceVarsInList(renames, passedInVars, true);
        ICUtil.replaceVarsInList(renames, keepOpenVars, true);
      }
    }
    
    /**
     * For overriding by child class
     */
    public abstract void removeVars_(Set<String> removeVars);
    
    @Override
    public final void removeVars(Set<String> removeVars) {
      removeVars_(removeVars);
      removeVarsInBlocks(removeVars);
      ICUtil.removeVarsInList(passedInVars, removeVars);
      ICUtil.removeVarsInList(keepOpenVars, removeVars);
    }
    
    @Override
    public abstract ExecContext childContext(ExecContext outerContext);
    
  }

  public static abstract class AbstractLoop extends AsyncContinuation {
    protected Block loopBody;

    public AbstractLoop(Block loopBody, List<Var> usedVars,
        List<Var> keepOpenVars) {
      super(usedVars, keepOpenVars);
      this.loopBody = loopBody;
      this.loopBody.setParent(this);
    }

    public Block getLoopBody() {
      return loopBody;
    }
    
    @Override
    public boolean isLoop() {
      return true;
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
    public void inlineInto(Block block, Block predictedBranch) {
      throw new STCRuntimeError("Can't inline loops yet");
    }

    protected void fuseIntoAbstract(AbstractLoop o, boolean insertAtTop) {
      this.loopBody.insertInline(o.loopBody, insertAtTop);
      this.addKeepOpenVars(o.keepOpenVars);
      ICUtil.removeDuplicates(this.keepOpenVars);
      this.addPassedInVars(o.passedInVars);
    }
  }

  public static class ForeachLoop extends AbstractLoop {
    private String loopName;
    private Var arrayVar;
    private boolean arrayClosed;
    private Var loopCounterVar;
    private Var loopVar;
    public Var getArrayVar() {
      return arrayVar;
    }

    private final int splitDegree;

    private ForeachLoop(String loopName,
        Block block, Var arrayVar, Var loopVar,
        Var loopCounterVar, int splitDegree,
        boolean arrayClosed,
        List<Var> usedVariables, List<Var> keepOpenVars) {
      super(block, usedVariables, keepOpenVars);
      this.loopName = loopName;
      this.arrayVar = arrayVar;
      this.loopVar = loopVar;
      this.loopCounterVar = loopCounterVar;
      this.arrayClosed = arrayClosed;
      this.splitDegree = splitDegree;
    }

    public ForeachLoop(String loopName, Var arrayVar,
        Var loopVar, Var loopCounterVar, int splitDegree,
        boolean arrayClosed, List<Var> usedVariables,
        List<Var> keepOpenVars) {
      this(loopName, new Block(BlockType.FOREACH_BODY, null), arrayVar, loopVar, loopCounterVar,
          splitDegree, arrayClosed, usedVariables, keepOpenVars);
    }

    @Override
    public ForeachLoop clone() {
      return new ForeachLoop(loopName, this.loopBody.clone(),
          arrayVar, loopVar, loopCounterVar, splitDegree, arrayClosed,
          new ArrayList<Var>(passedInVars),
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

    /** Return list of variables that the continuations waits for
     * before executing
     * @return
     */
    @Override
    public List<BlockingVar> blockingVars() {
      return null;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startForeachLoop(loopName, arrayVar, loopVar, loopCounterVar,
                splitDegree, arrayClosed, passedInVars, keepOpenVars);
      this.loopBody.generate(logger, gen, info);
      gen.endForeachLoop(splitDegree, arrayClosed, passedInVars,
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
      ICUtil.prettyPrintVarInfo(sb, passedInVars, keepOpenVars);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceConstructVars_(Map<String, Arg> renames,
            boolean inputsOnly) {
      if (renames.containsKey(arrayVar.name())) {
        arrayVar = renames.get(arrayVar.name()).getVar();
      }
      
      if (!inputsOnly) {
        if (renames.containsKey(loopVar.name())) {
          loopVar = renames.get(loopVar.name()).getVar();
        }
        if (this.loopCounterVar != null &&
            renames.containsKey(loopCounterVar.name())) {
          loopCounterVar = renames.get(loopCounterVar.name()).getVar();
        }
      }
    }

    @Override
    public Collection<Var> requiredVars() {
      Collection<Var> res = super.requiredVars();
      res.add(arrayVar);
      return res;
    }

    @Override
    public void removeVars_(Set<String> removeVars) {
      checkNotRemoved(arrayVar, removeVars);
      checkNotRemoved(loopVar, removeVars);
      if (loopCounterVar != null) {
        checkNotRemoved(loopCounterVar, removeVars);
      }
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
    public List<Var> constructDefinedVars() {
      return loopCounterVar == null ?
                Arrays.asList(loopVar)
              : Arrays.asList(loopCounterVar, loopVar);
    }

    @Override
    public Block tryInline(Set<String> closedVars, Set<String> recClosedVars) {
      if (closedVars.contains(arrayVar.name()) ||
          recClosedVars.contains(arrayVar.name())) {
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
      o.replaceVars(renames, false, true);
      
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

  public static class IfStatement extends Continuation {
    private final Block thenBlock;
    private final Block elseBlock;
    private Arg condition;

    public IfStatement(Arg condition) {
      this(condition, new Block(BlockType.THEN_BLOCK, null),
                          new Block(BlockType.ELSE_BLOCK, null));
    }

    private IfStatement(Arg condition, Block thenBlock, Block elseBlock) {
      super();
      assert(thenBlock != null);
      assert(elseBlock != null);
      this.condition = condition;
      this.thenBlock = thenBlock;
      this.thenBlock.setParent(this);
      // Always have an else block to make more uniform: empty block is then
      // equivalent to no else block
      this.elseBlock = elseBlock;
      this.elseBlock.setParent(this);
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
    public boolean isLoop() {
      return false;
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
    protected void replaceConstructVars(Map<String, Arg> renames,
          boolean inputsOnly) {
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
    // Whether loop var is defined here (instead of defined outside loop)
    private final List<Boolean> definedHere;
    private final List<Var> initVals;

    /*
     * Have handles to the termination instructions
     */
    private LoopBreak loopBreak;

    private LoopContinue loopContinue;
    private final ArrayList<Boolean> blockingVars;


    public Loop(String loopName, List<Var> loopVars,
            List<Boolean> definedHere, List<Var> initVals, List<Var> usedVariables,
            List<Var> keepOpenVars, List<Boolean> blockingVars) {
      this(loopName, new Block(BlockType.LOOP_BODY, null), loopVars,
          definedHere, initVals, usedVariables, keepOpenVars, blockingVars);
    }

    private Loop(String loopName, Block loopBody,
        List<Var> loopVars,  List<Boolean> definedHere,
        List<Var> initVals,
        List<Var> usedVariables, List<Var> keepOpenVars,
        List<Boolean> blockingVars) {
      super(loopBody, usedVariables, keepOpenVars);
      this.loopName = loopName;
      this.condVar = loopVars.get(0);
      this.loopVars = new ArrayList<Var>(loopVars);
      this.definedHere = new ArrayList<Boolean>(definedHere);
      this.initVals = new ArrayList<Var>(initVals);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
      assert(loopVars.size() == definedHere.size());
      assert(loopVars.size() == initVals.size());
      assert(loopVars.size() == blockingVars.size());
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
      Loop cloned = new Loop(loopName, this.loopBody.clone(),
          loopVars, definedHere, initVals,
          passedInVars, keepOpenVars, blockingVars);

      // fix up the references to the loopContinue/loopBreak instructions
      Pair<LoopBreak, LoopContinue> insts = cloned.findInstructions();
      cloned.setLoopBreak(insts.val1);
      cloned.setLoopContinue(insts.val2);
      return cloned;
    }

    private Pair<LoopBreak, LoopContinue> findInstructions() {
      LoopBreak breakInst = null;
      LoopContinue continueInst = null;
      Deque<Block> blocks = new ArrayDeque<Block>();
      blocks.add(loopBody);
      while (!blocks.isEmpty()) {
        // Find instructions
        Block curr = blocks.pop();
        for (Instruction inst: curr.getInstructions()) {
          if (inst.op == Opcode.LOOP_BREAK) {
            assert(breakInst == null): "duplicate instructions: " + breakInst
                    + " and \n" + inst;
            breakInst = (LoopBreak)inst;
          } else if (inst.op == Opcode.LOOP_CONTINUE) {
            assert(continueInst == null): "duplicate instructions: " + continueInst
                    + " and \n" + inst;
            continueInst = (LoopContinue)inst;
          }
        }
        
        for (Continuation cont: curr.getContinuations()) {
          // Don't go into inner loops, as they will have their own
          // break/continue instructions
          if (cont.getType() != ContinuationType.LOOP) {
            for (Block inner: cont.getBlocks()) {
              blocks.push(inner);
            }
          }
        }
      }
      
      assert(breakInst != null) : "No loop break for loop\n" + this;
      assert(continueInst != null) : "No loop continue for loop\n" + this;
      
      Pair<LoopBreak, LoopContinue> insts = Pair.create(breakInst, continueInst);
      return insts;
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
      this.loopBreak = loopBreak;
    }

    public void setLoopContinue(LoopContinue loopContinue) {
      this.loopContinue = loopContinue;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startLoop(loopName, loopVars, definedHere, initVals,
                    passedInVars, keepOpenVars, blockingVars);
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
      ICUtil.prettyPrintVarInfo(sb, passedInVars, keepOpenVars);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }
    
    @Override
    public void replaceConstructVars_(Map<String, Arg> renames,
        boolean inputsOnly) {
      ICUtil.replaceVarsInList(renames, initVals, false);
      if (!inputsOnly) {
        ICUtil.replaceVarsInList(renames, loopVars, false);
      }
    }

    @Override
    public Collection<Var> requiredVars() {
      Collection<Var> res = super.requiredVars();
      res.addAll(initVals);
      return res;
    }

    @Override
    public void removeVars_(Set<String> removeVars) {
      // check it isn't removing initial values
      for (Var v: this.initVals) {
        checkNotRemoved(v, removeVars);
      }
      for (Var v: this.loopVars) {
        checkNotRemoved(v, removeVars);
      }
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
    public List<Var> constructDefinedVars() {
      ArrayList<Var> defVars = new ArrayList<Var>();
      for (int i = 0; i < this.loopVars.size(); i++) {
        if (this.definedHere.get(i)) {
          defVars.add(this.loopVars.get(i));
        }
      }
      return defVars;
    }

    @Override
    public List<BlockingVar> blockingVars() {
      ArrayList<BlockingVar> res = new ArrayList<BlockingVar>();
      res.add(new BlockingVar(condVar, false));
      for (int i = 0; i < loopVars.size(); i++) {
        if (blockingVars.get(i)) {
          res.add(new BlockingVar(loopVars.get(i), false));
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
      this.loopBreak.addUsedVar(variable);
    }

    @Override
    public void removePassedInVar(Var variable) {
      // special implementation to also fix up the loopContinue instruction
      super.removePassedInVar(variable);
      this.loopContinue.removeUsedVar(variable);
      this.loopBreak.removeUsedVar(variable);
    }

    @Override
    public void addPassedInVars(Collection<Var> vars) {
      // special implementation to also fix up the loopContinue instruction
      super.addPassedInVars(vars);
      this.loopContinue.addUsedVars(vars);
      this.loopBreak.addUsedVars(vars);
    }
    
    @Override
    public void clearPassedInVars() {
      super.clearPassedInVars();
      this.loopContinue.clearUsedVars();
      this.loopBreak.clearUsedVars();
    }
    
    @Override
    public void addKeepOpenVar(Var v) {
      // special implementation to also fix up the loopContinue instruction
      super.addKeepOpenVar(v);
      this.loopContinue.addKeepOpenVar(v);
      this.loopBreak.addKeepOpenVar(v);
    }

    @Override
    public void addKeepOpenVars(Collection<Var> v) {
      // special implementation to also fix up the loopContinue instruction
      super.addKeepOpenVars(v);
      this.loopContinue.removeKeepOpenVars(v);
      this.loopBreak.removeKeepOpenVars(v);
    }

    public Var getInitCond() {
      return this.initVals.get(0);
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      return outerContext;
    }
  }
  
  public static class NestedBlock extends Continuation {
    private final Block block;

    public NestedBlock() {
      this(new Block(BlockType.NESTED_BLOCK, null));
    }


    private NestedBlock(Block block) {
      super();
      this.block = block;
      this.block.setParent(this);
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
    public boolean isLoop() {
      return false;
    }

    @Override
    protected void replaceConstructVars(Map<String, Arg> renames, 
                  boolean inputsOnly) {
      // Do nothing
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
    public List<Var> constructDefinedVars() {
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
      this(loopName, new Block(BlockType.RANGELOOP_BODY, null),
          loopVar, countVar,
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
          new ArrayList<Var>(passedInVars),
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
    public List<BlockingVar> blockingVars() {
      return null;
    }
    
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startRangeLoop(loopName, loopVar, countVar, start, end, increment,
                         passedInVars, keepOpenVars,
                         desiredUnroll, splitDegree);
      this.loopBody.generate(logger, gen, info);
      gen.endRangeLoop(passedInVars, keepOpenVars, splitDegree);
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
      ICUtil.prettyPrintVarInfo(sb, passedInVars, keepOpenVars);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceConstructVars_(Map<String, Arg> renames, 
                                        boolean inputsOnly) {
      start = renameRangeArg(start, renames);
      end = renameRangeArg(end, renames);
      increment = renameRangeArg(increment, renames);
      
      if (renames.containsKey(loopVar.name())) {
        loopVar = renames.get(loopVar.name()).getVar();
      }
      if (countVar != null && renames.containsKey(countVar.name())) {
        countVar = renames.get(countVar.name()).getVar();
      }
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
    public void removeVars_(Set<String> removeVars) {
      checkNotRemoved(start, removeVars);
      checkNotRemoved(end, removeVars);
      checkNotRemoved(increment, removeVars);
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
    public List<Var> constructDefinedVars() {
      if (countVar != null) {
        return Arrays.asList(loopVar, countVar);
      } else {
        return Arrays.asList(loopVar);
      }
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
        this.loopBody = new Block(BlockType.LOOP_BODY, this);
        Block curr = loopBody;
        Var nextIter = loopVar; // Variable with current iter number

        for (int i = 0; i < desiredUnroll; i++) {
          // Put everything in nested block
          NestedBlock nb = new NestedBlock(orig.clone(BlockType.NESTED_BLOCK, null));
          curr.addContinuation(nb);
          if (i != 0) {
            // Replace references to the iteration counter
            nb.replaceVars(Collections.singletonMap(this.loopVar.name(),
                                 Arg.createVar(nextIter)), false, true);
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
              IfStatement ifSt = new IfStatement(Arg.createVar(nextIterCheck));
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
      o.replaceVars(renames, false, true);
     
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

  public static class SwitchStatement extends Continuation {
    private final ArrayList<Integer> caseLabels;
    private final ArrayList<Block> caseBlocks;
    private final Block defaultBlock;
    private Arg switchVar;

    public SwitchStatement(Arg switchVar, List<Integer> caseLabels) {
      this(switchVar, new ArrayList<Integer>(caseLabels),
          new ArrayList<Block>(), new Block(BlockType.CASE_BLOCK, null));

      // number of non-default cases
      int caseCount = caseLabels.size();
      for (int i = 0; i < caseCount; i++) {
        this.caseBlocks.add(new Block(BlockType.CASE_BLOCK, this));
      }
    }

    private SwitchStatement(Arg switchVar,
        ArrayList<Integer> caseLabels, ArrayList<Block> caseBlocks,
        Block defaultBlock) {
      super();
      this.switchVar = switchVar;
      this.caseLabels = caseLabels;
      this.caseBlocks = caseBlocks;
      this.defaultBlock = defaultBlock;
      this.defaultBlock.setParent(this);
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
    public void replaceConstructVars(Map<String, Arg> renames, 
            boolean inputsOnly) {
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
    public boolean isLoop() {
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
  }

  /**
   * A new construct that blocks on a list of variables (blockVars),
   * and only runs the contents once all of those variables are closed
   *
   */
  public static class WaitStatement extends AsyncContinuation {
    /** Label name to use in final generated code */
    private final String procName;
    private final Block block;
    private final ArrayList<Var> waitVars;
    private Arg priority;
    
    /* True if this wait was compiler-generated so can be removed if needed
     * We can only remove an explicit wait if we know that the variables are
     * already closed*/
    private WaitMode mode;
    private final boolean recursive;
    private TaskMode target;

    public WaitStatement(String procName, List<Var> waitVars,
                    List<Var> usedVars,
                    List<Var> keepOpenVars,
                    Arg priority,
                    WaitMode mode, boolean recursive, TaskMode target) {
      this(procName, new Block(BlockType.WAIT_BLOCK, null),
                        waitVars,
                        usedVars,
                        keepOpenVars,
                        priority, mode, recursive, target);
      assert(this.block.getParentCont() != null);
    }

    private WaitStatement(String procName, Block block,
        List<Var> waitVars, List<Var> usedVars,
        List<Var> keepOpenVars, Arg priority,
        WaitMode mode, boolean recursive, TaskMode target) {
      super(usedVars, keepOpenVars);
      assert(waitVars != null);
      assert(usedVars != null);
      assert(keepOpenVars != null);
      assert(target != null);
      assert(mode != null);
      this.procName = procName;
      this.block = block;
      this.block.setParent(this);
      this.waitVars = new ArrayList<Var>(waitVars);
      ICUtil.removeDuplicates(this.waitVars);
      this.priority = priority;
      this.mode = mode;
      this.recursive = recursive;
      this.target = target;
    }

    @Override
    public WaitStatement clone() {
      return new WaitStatement(procName, this.block.clone(),
          waitVars, passedInVars, keepOpenVars, priority,
          mode, recursive, target);
    }

    public Block getBlock() {
      return block;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {

      gen.startWaitStatement(procName, waitVars, passedInVars,
          keepOpenVars, priority, mode, recursive, target);
      this.block.generate(logger, gen, info);
      gen.endWaitStatement(waitVars, passedInVars, keepOpenVars);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      String newIndent = currentIndent + indent;
      sb.append(currentIndent + "wait (");
      ICUtil.prettyPrintVarList(sb, waitVars);
      sb.append(") ");
      sb.append("/*" + procName + "*/ " );
      ICUtil.prettyPrintVarInfo(sb, passedInVars, keepOpenVars);
      sb.append(" <" + mode + ", " + target + ", " +
                (recursive ? "RECURSIVE" : "NONRECURSIVE") + ">");
      if (priority != null) {
        sb.append(" @priority=" + priority);
      }
      sb.append(" {\n");
      block.prettyPrint(sb, newIndent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(block);
    }

    @Override
    public void replaceConstructVars_(Map<String, Arg> renames, 
        boolean inputsOnly) {
      ICUtil.replaceVarsInList(renames, waitVars, true);
      priority = ICUtil.replaceOparg(renames, priority, true);
    }
    
    public WaitMode getMode() {
      return mode;
    }
    
    public boolean isRecursive() {
      return recursive;
    }
    
    public TaskMode getTarget() {
      return target;
    }
    @Override
    public ContinuationType getType() {
      return ContinuationType.WAIT_STATEMENT;
    }

    public void setTarget(TaskMode target) {
      this.target = target;
    }

    public void setMode(WaitMode mode) {
      this.mode = mode;
    }

    @Override
    public boolean isAsync() {
      return true;
    }

    @Override
    public boolean isLoop() {
      return false;
    }

    @Override
    public Collection<Var> requiredVars() {
      ArrayList<Var> res = new ArrayList<Var>();
      if (mode == WaitMode.EXPLICIT || mode == WaitMode.TASK_DISPATCH) {
        for (Var v: waitVars) {
          res.add(v);
        }
      }
      if (priority != null && priority.isVar()) {
        res.add(priority.getVar());
      }
      return res; // can later eliminate waitVars, etc
    }

    public List<Var> getWaitVars() {
      return Collections.unmodifiableList(this.waitVars);
    }

    @Override
    public void removeVars_(Set<String> removeVars) {
      ICUtil.removeVarsInList(waitVars, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Arg> knownConstants) {
      // We can't really do branch prediction for a wait statement, but
      // it is a useful mechanism to piggy-back on to remove the wait
      return tryInline(Collections.<String>emptySet(), knownConstants.keySet());
    }

    @Override
    public boolean isNoop() {
      return this.block.isEmpty();
    }

    @Override
    public Block tryInline(Set<String> closedVars, Set<String> recClosedVars) {
      boolean varsLeft = false;
      // iterate over wait vars, remove those in list
      ListIterator<Var> it = waitVars.listIterator();
      while(it.hasNext()) {
        Var wv = it.next();
        // See if we can skip waiting on var
        if ((closedVars.contains(wv.name()) && !recursionRequired(wv))
            || recClosedVars.contains(wv.name())) {
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
    
    public void inlineInto(Block dstBlock) {
      inlineInto(dstBlock, this.block);
    }

    
    @Override
    public void removeUnused(Set<String> usedVars) {
      if (mode == WaitMode.EXPLICIT)
        return;
      
      ListIterator<Var> waitIt = waitVars.listIterator();
      while (waitIt.hasNext()) {
        Var waitVar = waitIt.next();
        // Remove variable not passed in
        if (!usedVars.contains(waitVar.name())) {
          waitIt.remove();
        }
      }
    }
    
    /**
     * @param wv
     * @return true if we need to recursively check closing for variable, i.e.
     *      if superficial closing isn't enough to consider it closed for this
     *      wait statement
     */
    private boolean recursionRequired(Var wv) {
      if (!recursive) {
        return false;
      }
      if (Types.isScalarFuture(wv.type())) {
        return false;
      }
      return true;
    }

    @Override
    public List<BlockingVar> blockingVars() {
      ArrayList<BlockingVar> res = new ArrayList<BlockingVar>(waitVars.size());
      for (Var wv: waitVars) {
        res.add(new BlockingVar(wv, this.recursive));
      }
      return res;
    }

    @Override
    public List<Var> constructDefinedVars() {
      return null;
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      switch (target) {
      case SYNC:
      case LOCAL:
        return outerContext;
      case LOCAL_CONTROL:
        // Check used validly
        assert(outerContext == ExecContext.CONTROL);
        return outerContext;
      case CONTROL:
        return ExecContext.CONTROL;
      case LEAF:
        return ExecContext.LEAF;
      default:
        throw new STCRuntimeError("Unknown wait target: " + target);
      }
    }
  }

}
