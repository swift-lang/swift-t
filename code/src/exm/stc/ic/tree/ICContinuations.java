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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.Location;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.LoopBreak;
import exm.stc.ic.tree.ICInstructions.LoopContinue;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

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
      Function oldParentFunction = this.parent != null ?
                                    this.parent.getParentFunction() : null;
      this.parent = parent;
      /*
       * Update parent function links if needed.  This appraoch used here
       * should only update info when needed (when a tree is
       * attached to a different function).
       */
      Function parentFunction = this.parent.getParentFunction();
      if (parentFunction != null && parentFunction != oldParentFunction) {
        parentFunction.addUsedVarNames(this.constructDefinedVars());
        for (Block block: getBlocks()) {
          // Fixup parent links in child blocks
          block.fixParentLinksRec(parentFunction);
        }
      }
    }

    public abstract void generate(Logger logger, CompilerBackend gen, GenInfo info);

    public abstract void prettyPrint(StringBuilder sb, String currentIndent);

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb, "");
      return sb.toString();
    }

    /** Returns all nested blocks in this continuation */
    public abstract List<Block> getBlocks();

    /**
     * @param renames
     * @param mode what sort of renaming
     * @param recursive recursively do replacement in inner blocks
     */
    public void renameVars(String function, Map<Var, Arg> renames,
                           RenameMode mode, boolean recursive) {
      if (renames.isEmpty())
        return;
      if (recursive) {
        this.replaceVarsInBlocks(function, renames, mode);
      }
      this.replaceConstructVars(renames, mode);
    }

    /**
     * Rename recursively
     * @param renames
     * @param mode
     */
    public void renameVars(String function, Map<Var, Arg> renames, RenameMode mode) {
      renameVars(function, renames, mode, true);
    }

    protected abstract void replaceConstructVars(Map<Var, Arg> renames,
                                                 RenameMode mode);

    /**
     * For the case where a consturct redefines a variable
     * name from outside, replace this variable.
     * @param oldV
     * @param newV
     */
    public void removeRedef(Var oldV, Var newV) {
       // Do nothing by default
    }

    protected void replaceVarsInBlocks(String function, Map<Var, Arg> renames,
                                       RenameMode mode) {
      for (Block b: this.getBlocks()) {
        b.renameVars(function, renames, mode, true);
      }
    }

    public abstract void removeVars(Set<Var> removeVars);

    protected void removeVarsInBlocks(Set<Var> removeVars) {
      for (Block b: this.getBlocks()) {
        b.removeVars(removeVars);
      }
    }

    /**
     * @param forDeadCodeElim if true, only return those variables
     * that this construct would prevent from eliminating
     * @return all variables whose values are needed to evaluate this construct
     * (e.g. branch condition).  empty list if none
     */
    public abstract Collection<Var> requiredVars(boolean forDeadCodeElim);

    /**
     * replace variables with constants in loop construct
     * @param knownConstants
     * @return true if anything changed
     */
    public boolean constantReplace(Map<Var, Arg> knownConstants) {
      // default: do nothing
      return false;
    }

    /** @return true if the continuation does nothing */
    public abstract boolean isNoop();

    public abstract boolean isAsync();

    /** @return true if continuation is async and a single task is spawned from
     *    current context */
    public boolean spawnsSingleTask() {
      assert(!isAsync());
      return false;
    }

    /** Return list of variables that the continuations waits for
     * before executing
     * @param includeConstructDefined if false, only include vars defined outside
     * @return
     */
    public List<BlockingVar> blockingVars(boolean includeConstructDefined) {
      // default implementation for sync continuations
      assert(!isAsync());
      return Collections.emptyList();
    }

    /**
     * Return list of variables closed inside continuation.  At a minimum this
     * is a superset of blockingVars.
     */
    public List<BlockingVar> closedVars(Set<Var> closed, Set<Var> recClosed) {
      // Default implementation is blocking vars
      return blockingVars(true);
    }

    /**
     * Return list of variables that are defined by construct and
     * accessible inside
     * @param type type of constructs to return
     * @return non-null list
     */
    public List<Var> constructDefinedVars(ContVarDefType type) {
      return Var.NONE;
    }

    public final List<Var> constructDefinedVars() {
      // In most cases don't care about redefs
      return constructDefinedVars(ContVarDefType.NEW_DEF);
    }

    /**
     * @return true if all variables in block containing continuation are
     *        automatically visible in inner blocks
     */
    public VarPassing variablePassing() {
      // Generally non-async continuations inherit scope
      return isAsync() ? VarPassing.MANUAL_NONLOCAL : VarPassing.AUTOMATIC;
    }

    /**
     * Only applies to async continuations
     * @return List of variables passed into scope.
     *        empty list means none
     */
    public Collection<PassedVar> getPassedVars() {
      throw new STCRuntimeError("not implemented");
    }

    /**
     * Only applies to async continuations
     * @return List of all variables that are used within continuation,
     *    including those that aren't directly referred to
     */
    public Collection<PassedVar> getAllPassedVars() {
      throw new STCRuntimeError("not implemented");
    }

    /**
     * Only applies to async continuations
     * @return List of variables kept open in this scope.
     *        empty list means none
     */
    public Collection<Var> getKeepOpenVars() {
      throw new STCRuntimeError("not implemented");
    }

    public void setPassedVars(Collection<PassedVar> passedIn) {
      throw new STCRuntimeError("not implemented");
    }

    /**
     * Set all keep open vars for continuation
     */
    public void setKeepOpenVars(Collection<Var> keepOpen) {
      throw new STCRuntimeError("not implemented");
    }

    /**
     * Remove this continuation from block, inlining one of
     * the nested blocks inside the continuation (e.g. the predicted branch
     *  of an if statement)
     * @param block the parent block of continuation
     * @param predictedBranch the branch in continuation that will be executed
     */
    public void inlineInto(Block block, Block predictedBranch, ListIterator<Statement> it) {
      // Default implementation
      block.insertInline(predictedBranch, it);
      if (parent != null)
        parent.removeContinuation(this);
    }

    public void inlineInto(Block block, Block predictedBranch) {
      inlineInto(block, predictedBranch, null);
    }

    /**
     *
     * It is ok if the unrolling introduced duplicate variable names in
     * nested blocks (so long as they don't shadow each other) - a
     * subsequent pass will make those names unique
     * @param logger
     * @param function
     * @param outerBlock
     * @return true if change made, also any additional continuations to be
     *        added by caller to outerBlock
     */
    public Pair<Boolean, List<Continuation>> tryUnroll(Logger logger,
                                   String function, Block outerBlock) {
      // default: do nothing
      return Pair.create(false, Collections.<Continuation>emptyList());
    }

    /**
     * Try to inline a block, depending on which variables are closed
     * This is also a mechanism to let the continuation know what variables
     * are closed so it can make internal optimizations
     * @param closedVars variables which are closed
     * @param recClosedVars variables which are recursively closed (may
     *    overlap with closed)
     * @param keepExplicitDependencies if true, don't remove variables
     *        that are explicitly waited on
     * @return null if it cannot be inlined, a block that is equivalent to
     *          the continuation otherwise
     */
    public Block tryInline(Set<Var> closedVars, Set<Var> recClosedVars,
        boolean keepExplicitDependencies) {
      // Default: do nothing
      return null;
    }

    /**
     * Return the execution context inside the continuation
     * @param outerContext the context outside
     * @return a context, or null if any context
     */
    public ExecContext childContext(ExecContext outerContext) {
      // Default implementation for sync continuations
      assert(!isAsync());
      return outerContext;
    }

    /**
     * Create a copy of the continuation.  Do not set any
     * parent links.
     */
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

    /**
     * If every block is executed exactly once
     * @return
     */
    public abstract boolean executesBlockOnce();

    public abstract boolean isLoop();

    /**
     * @return true if this is a conditional in which at most one of the
     *              subblocks is executed
     */
    public abstract boolean isConditional();

    /**
     * @return true if this is a conditional that executes synchronously, with
     *          the blocks representing the exhaustive set of possible paths
     */
    public boolean isExhaustiveSyncConditional() {
      // default
      return false;
    }

    /**
     * @return any variables that must be passed in even if they don't appear
     *        in any blocks within in the continuation
     */
    public List<PassedVar> getMustPassVars() {
      return PassedVar.NONE;
    }
  }

  public enum ContinuationType {
    NESTED_BLOCK,
    IF_STATEMENT,
    SWITCH_STATEMENT,
    FOREACH_LOOP,
    RANGE_LOOP,
    LOOP,
    WAIT_STATEMENT,
    ASYNC_EXEC,
    ;
  }

  /**
   * Categorization of vars defined inside continuations
   */
  public static enum ContVarDefType {
    ANY_DEF, // All variables defined or redefined inside construct
    NEW_DEF, // Doesn't shadow outer variables
    REDEF, // Redefines value of variable from outer scope
    INIT, // Initializes existing variable or defines new var
    ;

    public boolean includesRedefs() {
      return this == ANY_DEF || this == REDEF || this == INIT;
    }

    public boolean includesNewDefs() {
      return this == ANY_DEF || this == NEW_DEF || this == INIT;
    }

    public boolean includesInitOnly() {
      return this == INIT;
    }
  }

  /**
   * Style of variable passing.
   */
  public static enum VarPassing {
    AUTOMATIC /** Automatically inherit vars from parent */,
    MANUAL_LOCAL /** Must manually pass vars, but runs in same context */,
    MANUAL_NONLOCAL /** Cannot assume runs in same context */,
    ;

    public boolean isManual() {
      return this == MANUAL_LOCAL || this == MANUAL_NONLOCAL;
    }

    public boolean isAutomatic() {
      return this == AUTOMATIC;
    }

    public boolean isLocal() {
      return this == AUTOMATIC || this == MANUAL_LOCAL;
    }
  }

  /**
   * A variable that must be closed for a computation to proceed
   */
  public static class BlockingVar {
    public final Var var;
    /** Whether variable must be recursively closed */
    public final boolean recursive;
    public final boolean explicit;

    public BlockingVar(Var var, boolean recursive, boolean explicit) {
      super();
      this.var = var;
      this.recursive = recursive;
      this.explicit = explicit;
    }

    @Override
    public String toString() {
      String out = var.name();
      if (recursive)
        out += " RECURSIVE";
      if (explicit)
        out += " EXPLICIT";
      return out;
    }
  }

  public static abstract class AsyncContinuation extends Continuation {
    protected final List<PassedVar> passedVars;
    protected final List<Var> keepOpenVars;


    public AsyncContinuation(List<PassedVar> passedVars,
                            List<Var> keepOpenVars) {
      this.passedVars = new ArrayList<PassedVar>(passedVars);
      this.keepOpenVars = new ArrayList<Var>(keepOpenVars);
    }
    @Override
    public Collection<PassedVar> getPassedVars() {
      return Collections.unmodifiableList(this.passedVars);
    }

    @Override
    public Collection<PassedVar> getAllPassedVars() {
      // By default, no extras
      return getPassedVars();
    }

    @Override
    public void setPassedVars(Collection<PassedVar> passedVars) {
      this.passedVars.clear();
      this.passedVars.addAll(passedVars);
    }

    @Override
    public void setKeepOpenVars(Collection<Var> keepOpenVars) {
      this.keepOpenVars.clear();
      this.keepOpenVars.addAll(keepOpenVars);
    }

    @Override
    public Collection<Var> getKeepOpenVars() {
      return Collections.unmodifiableList(this.keepOpenVars);
    }

    /**
     * For overriding by child class
     * @param renames
     * @param mode
     */
    public abstract void replaceConstructVars_(Map<Var, Arg> renames,
                  RenameMode mode);

    @Override
    public final void replaceConstructVars(Map<Var, Arg> renames,
                                            RenameMode mode) {
      this.replaceConstructVars_(renames, mode);
    }

    /**
     * For overriding by child class
     */
    public abstract void removeVars_(Set<Var> removeVars);

    @Override
    public final void removeVars(Set<Var> removeVars) {
      removeVars_(removeVars);
      removeVarsInBlocks(removeVars);
    }

    @Override
    public abstract ExecContext childContext(ExecContext outerContext);

  }

  public static abstract class AbstractLoop extends AsyncContinuation {
    protected Block loopBody;

    public AbstractLoop(Block loopBody, List<PassedVar> passedVars,
        List<Var> keepOpenVars, boolean emptyLoop) {
      super(passedVars, keepOpenVars);
      this.loopBody = loopBody;
      this.loopBody.setParent(this, emptyLoop);
    }

    public Block getLoopBody() {
      return loopBody;
    }

    @Override
    public boolean isLoop() {
      return true;
    }

    @Override
    public boolean isConditional() {
      return false;
    }

    @Override
    public boolean executesBlockOnce() {
      return false;
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(loopBody);
    }

    @Override
    public Collection<Var> requiredVars(boolean de) {
      return Var.NONE;
    }

    protected void checkNotRemoved(Var v, Set<Var> removeVars) {
      if (removeVars.contains(v)) {
        throw new STCRuntimeError("bad optimization: tried to remove" +
        " required variable " + v.toString());
      }
    }
    protected void checkNotRemoved(Arg o, Set<Var> removeVars) {
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
    }
  }

  public static class Loop extends AbstractLoop {
    private final String loopName;
    private final Var condVar;
    private final List<Var> loopVars;
    // Whether loop var is defined here (instead of defined outside loop)
    private final List<Boolean> definedHere;
    private final List<Arg> initVals;

    /*
     * Have handles to the termination instructions
     */
    private LoopBreak loopBreak;

    private LoopContinue loopContinue;

    /** Which vars must be closed before executing loop body */
    private final List<Boolean> blockingVars;

    /** Which initial vals are closed */
    private final List<Boolean> closedInitVals;

    public Loop(String loopName, List<Var> loopVars,
            List<Boolean> definedHere, List<Arg> initVals,
            List<PassedVar> passedVars, List<Var> keepOpenVars,
            List<Boolean> blockingVars) {
      this(loopName, new Block(BlockType.LOOP_BODY, null), loopVars,
          definedHere, initVals, passedVars, keepOpenVars, blockingVars,
          newBoolList(initVals.size(), false), true);
    }

    private static List<Boolean> newBoolList(int size, boolean b) {
      ArrayList<Boolean> res = new ArrayList<Boolean>(size);
      for (int i = 0; i < size; i++) {
        res.add(b);
      }
      return res;

    }

    private Loop(String loopName, Block loopBody,
        List<Var> loopVars,  List<Boolean> definedHere,
        List<Arg> initVals,
        List<PassedVar> passedVars, List<Var> keepOpenVars,
        List<Boolean> blockingVars, List<Boolean> closedInitVals,
        boolean emptyLoop) {
      super(loopBody, passedVars, keepOpenVars, emptyLoop);
      this.loopName = loopName;
      this.condVar = loopVars.get(0);
      this.loopVars = new ArrayList<Var>(loopVars);
      this.definedHere = new ArrayList<Boolean>(definedHere);
      this.initVals = new ArrayList<Arg>(initVals);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
      this.closedInitVals = new ArrayList<Boolean>(closedInitVals);
      assert(loopVars.size() == definedHere.size());
      assert(loopVars.size() == initVals.size());
      assert(loopVars.size() == blockingVars.size());
      assert(loopVars.size() == closedInitVals.size());
      for (int i = 0; i < loopVars.size(); i++) {
        Var loopV = loopVars.get(i);
        Arg initV = initVals.get(i);
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
          passedVars, keepOpenVars, blockingVars,
          closedInitVals, false);

      // fix up the references to the loopContinue/loopBreak instructions
      LoopInstructions insts = cloned.findInstructions();
      cloned.setLoopBreak(insts.breakInst);
      cloned.setLoopContinue(insts.continueInst);
      return cloned;
    }

    public LoopInstructions findInstructions() {
      Block breakInstBlock = null, continueInstBlock = null;
      LoopBreak breakInst = null;
      LoopContinue continueInst = null;
      StackLite<Block> blocks = new StackLite<Block>();
      blocks.add(loopBody);
      while (!blocks.isEmpty()) {
        // Find instructions
        Block curr = blocks.pop();
        for (Statement stmt: curr.getStatements()) {
          if (stmt.type() == StatementType.INSTRUCTION) {
            Instruction inst = stmt.instruction();
            if (inst.op == Opcode.LOOP_BREAK) {
              assert(breakInst == null): "duplicate instructions: " + breakInst
                      + " and \n" + inst;
              breakInst = (LoopBreak)inst;
              breakInstBlock = curr;
            } else if (inst.op == Opcode.LOOP_CONTINUE) {
              assert(continueInst == null): "duplicate instructions: " + continueInst
                      + " and \n" + inst;
              continueInst = (LoopContinue)inst;
              continueInstBlock = curr;
            }
          }
        }

        for (Continuation cont: curr.allComplexStatements()) {
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
      assert(breakInstBlock != null);
      assert(continueInstBlock != null);
      return new LoopInstructions(breakInstBlock, breakInst,
                                  continueInstBlock, continueInst);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.LOOP;
    }

    public String loopName() {
      return loopName;
    }

    @Override
    public boolean isAsync() {
      return true;
    }

    @Override
    public boolean spawnsSingleTask() {
      // Only one task is spawned right away.  That tasks spawns further
      // for subsequent iterations
      return true;
    }

    public void setBlockingInput(Var var) {
      for (int i = 0; i < loopVars.size(); i++) {
        if (loopVars.get(i).equals(var)) {
          blockingVars.set(i, true);
          this.loopContinue.setBlocking(i, true);
          break;
        }
      }
      throw new STCRuntimeError("Loop var not found: " + var + " in " +
                    loopVars);
    }

    public void setLoopBreak(LoopBreak loopBreak) {
      this.loopBreak = loopBreak;
    }

    public void setLoopContinue(LoopContinue loopContinue) {
      this.loopContinue = loopContinue;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      List<BlockingVar> unclosedInit = unclosedBlockingInitVals();
      List<Var> initWait = new ArrayList<Var>(unclosedInit.size());
      for (BlockingVar init: unclosedInit) {
        // TODO: support recursive waits?
        initWait.add(init.var);
      }

      boolean simpleLoop = allBlockingClosed() && loopContinueSynchronous();

      gen.startLoop(loopName, loopVars, initVals,
                    PassedVar.extractVars(passedVars),
                    initWait, simpleLoop);
      this.loopBody.generate(logger, gen, info);
      gen.endLoop();
    }

    /**
     * Check if the loop continue construct executes synchronously with
     * the loop body.
     * @return
     */
    private boolean loopContinueSynchronous() {
      // Check to see if there is a wait between here and the loop continue
      LoopInstructions insts = findInstructions();
      // Scan back to see if continue block executes synchronously
      Block curr = insts.continueInstBlock;
      while (curr != this.loopBody) {
        assert(curr != null);


        Continuation cont = curr.getParentCont();
        assert(cont != null);
        if (cont.isAsync()) {
          return false;
        }
        curr = cont.parent();
      }
      return true;
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
        Arg initV = initVals.get(i);
        if (first) {
          first = false;
        } else {
          sb.append(", ");
        }
        sb.append(loopV.type().typeName() + " " + loopV.name() + "="
            + initV.toString());
      }

      sb.append(")\n" + currentIndent + indent + indent);
      ICUtil.prettyPrintVarInfo(sb, passedVars, keepOpenVars);
      if (blockingVars.contains(true)) {
        List<Var> blockon = new ArrayList<Var>();
        for (int i = 0; i < loopVars.size(); i++) {
          if (blockingVars.get(i)) {
            blockon.add(loopVars.get(i));
          }
        }
        sb.append(" #blockon[");
        ICUtil.prettyPrintVarList(sb, blockon);
        sb.append("]");
      }
      if (closedInitVals.contains(true)) {
        List<Arg> closedInit = new ArrayList<Arg>();
        for (int i = 0; i < loopVars.size(); i++) {
          if (closedInitVals.get(i)) {
            closedInit.add(initVals.get(i));
          }
        }
        sb.append(" #closedinit[");
        ICUtil.prettyPrintArgList(sb, closedInit);
        sb.append("]");
      }
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceConstructVars_(Map<Var, Arg> renames,
                                      RenameMode mode) {
      ICUtil.replaceArgsInList(renames, initVals, false);
      if (mode == RenameMode.REPLACE_VAR) {
        ICUtil.replaceVarsInList(renames, loopVars, false);
      }
    }

    @Override
    public void removeRedef(Var oldV, Var newV) {
      for (int i = 0; i < loopVars.size(); i++) {
        Var loopVar = loopVars.get(i);
        if (loopVar.equals(oldV)) {
          assert(!this.definedHere.get(i)) : loopVar;
          this.loopVars.set(i, newV);
          this.definedHere.set(i, true);
        }
      }
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      Collection<Var> res = new ArrayList<Var>(
          super.requiredVars(forDeadCodeElim));
      for (Arg initVal: initVals) {
        if (initVal.isVar()) {
          res.add(initVal.getVar());
        }
      }
      return res;
    }

    @Override
    public void removeVars_(Set<Var> removeVars) {
      // check it isn't removing initial values
      for (Arg initVal: this.initVals) {
        if (initVal.isVar()) {
          checkNotRemoved(initVal.getVar(), removeVars);
        }
      }
      for (Var v: this.loopVars) {
        checkNotRemoved(v, removeVars);
      }
    }

    @Override
    public boolean isNoop() {
      // Common case where it would be a noop are where we have nothing
      // but loop instructions:
      // retrieve_boolean v_cond cond
      // if () {
      //   loop_continue ...
      // } else {
      //   loop_break ...
      //  }

      if (!loopBody.getContinuations().isEmpty() ||
          loopBody.getStatements().size() != 2) {
        return false;
      }
      // Check first instruction is retrieve_boolean
      Statement first = loopBody.getStatements().get(0);
      if (first.type() != StatementType.INSTRUCTION ||
          first.instruction().op != Opcode.LOAD_SCALAR ||
          !Types.isBoolVal(first.instruction().getOutput(0))) {
        // First instruction doesn't match
        return false;
      }
      Statement second = loopBody.getStatements().get(1);
      if (second.type() != StatementType.CONDITIONAL) {
        // Second must be conditional
        return false;
      }

      // TODO: need more sophisticated analysis to check if
      //       operations have side-effect, or if they write
      //       variables outside of loop, since most loops
      //       will at a minimum have loop update code even if
      //       the loop as a whole has no effect
      Block thenB = second.conditional().getBlocks().get(0);
      Block elseB = second.conditional().getBlocks().get(1);

      if (thenB.getStatements().size() == 1 &&
          elseB.getStatements().size() == 1) {
        // Must have loop control instructions
        // TODO: must ignore comments here
        if(thenB.getStatements().get(0).instruction().op ==
               Opcode.LOOP_CONTINUE &&
           elseB.getStatements().get(0).instruction().op ==
               Opcode.LOOP_BREAK) {
          // TODO?
        }
      }
      return false;
    }

    @Override
    public List<Var> constructDefinedVars(ContVarDefType type) {
      ArrayList<Var> defVars = new ArrayList<Var>();
      for (int i = 0; i < this.loopVars.size(); i++) {
        Boolean defHere = this.definedHere.get(i);
        Var loopVar = this.loopVars.get(i);
        if (type.includesNewDefs() && defHere) {
          defVars.add(loopVar);
        } else if (type.includesRedefs() && !defHere) {
          defVars.add(loopVar);
        }
      }
      return defVars;
    }

    /**
     * Return all per-iteration vars defined within continuation
     * @return
     */
    public List<Var> getLoopVars() {
      return Collections.unmodifiableList(this.loopVars);
    }

    @Override
    public List<BlockingVar> blockingVars(boolean includeConstructDefined) {
      ArrayList<BlockingVar> res = new ArrayList<BlockingVar>();
      for (int i = 0; i < loopVars.size(); i++) {
        Arg initVal = initVals.get(i);
        if (blockingVars.get(i) && initVal.isVar()) {
          res.add(new BlockingVar(initVal.getVar(), false, false));
          if (includeConstructDefined) {
            res.add(new BlockingVar(loopVars.get(i), false, true));
          }
        }
      }
      return res;
    }

    /**
     * Loop stores information about which vars are closed
     * @return list of loop variables that are closed before launching
     *         loop iteration
     */
    public List<BlockingVar> closedLoopVars() {
      List<BlockingVar> res = new ArrayList<BlockingVar>();
      for (int i = 0; i < loopVars.size(); i++) {
        if (loopContinue.isLoopVarClosed(i) && closedInitVals.get(i)) {
          // Always closed when loop body starts running
          res.add(new BlockingVar(loopVars.get(i), false, false));
        }
      }
      return res;
    }

    @Override
    public List<BlockingVar> closedVars(Set<Var> closed, Set<Var> recClosed) {
      // Always includes blocking vars
      List<BlockingVar> res = blockingVars(true);
      for (int i = 0; i < loopVars.size(); i++) {
        Arg init = initVals.get(i);
        // Check for variables that are closed

        if (!closedInitVals.get(i) && init.isVar()
             && closed.contains(init.getVar())) {
          closedInitVals.set(i, true); // Record for later
        }
        if (!blockingVars.get(i)) {
          if (loopContinue.isLoopVarClosed(i) && closedInitVals.get(i)) {
            // Always closed when loop body starts running
            res.add(new BlockingVar(loopVars.get(i), false, false));
          }
        }
      }
      return res;
    }

    /**
     * @return vars that are blocking but where initial value isn't
     *        closed outside of wait, forcing us to wait for them
     */
    public List<BlockingVar> unclosedBlockingInitVals() {
      List<BlockingVar> res = new ArrayList<BlockingVar>();
      for (int i = 0; i < loopVars.size(); i++) {
        Arg initVal = initVals.get(i);
        if (blockingVars.get(i) && initVal.isVar() && !closedInitVals.get(i)) {
          res.add(new BlockingVar(initVal.getVar(), false, false));
        }
      }
      return res;
    }

    /**
     * Check that all blocking vars are closed
     */
    private boolean allBlockingClosed() {
      for (int i = 0; i < loopVars.size(); i++) {
        Var loopVar = loopVars.get(i);
        if (Types.canWaitForFinalize(loopVar) && blockingVars.get(i) &&
            (!closedInitVals.get(i) || !loopContinue.isLoopVarClosed(i))) {
          return false;
        }
      }
      return true;
    }

    public void setInitClosed(Arg initVal, boolean recursive) {
      // TODO: ignores recursive
      int index = initVals.indexOf(initVal);
      assert(index >= 0) : initVal;
      closedInitVals.set(index, true);
    }

    @Override
    public void setPassedVars(Collection<PassedVar> passedVars) {
      super.setPassedVars(passedVars);
      this.loopContinue.setLoopUsedVars(PassedVar.extractVars(passedVars));
      this.loopBreak.setLoopUsedVars(passedVars);
    }

    @Override
    public void setKeepOpenVars(Collection<Var> keepOpen) {
      super.setKeepOpenVars(keepOpen);
      this.loopBreak.setKeepOpenVars(keepOpen);
    }

    @Override
    public Collection<PassedVar> getAllPassedVars() {
      // Initial vals of loop vars are also passed in
      List<PassedVar> result = new ArrayList<PassedVar>();
      result.addAll(passedVars);
      for (Arg initVal: initVals) {
        if (initVal.isVar()) {
          result.add(new PassedVar(initVal.getVar(), false));
        }
      }
      return result;
    }


    public Arg getInitCond() {
      return this.initVals.get(0);
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      return outerContext;
    }

    /**
     * Initial value of loop variable
     * @param var
     * @return
     */
    public Arg getInitVal(Var var) {
      int index = loopVars.indexOf(var);
      assert(index >= 0) : var + " " + loopVars;
      return initVals.get(index);
    }

    public Arg getUpdateVal(Var var) {
      int index = loopVars.indexOf(var);
      assert(index >= 0) : var + " " + loopVars;
      return loopContinue.getNewLoopVar(index);
    }

    public boolean isBlocking(Var var) {
      int index = loopVars.indexOf(var);
      assert(index >= 0) : var + " " + loopVars;
      return blockingVars.get(index);
    }

    public void replaceLoopVar(Var oldLoopVar, Var newLoopVar, Arg initVal,
                   Arg updateVal, boolean blocking) {
      int index = loopVars.indexOf(oldLoopVar);
      assert(index >= 0) : oldLoopVar + " " + loopVars;
      assert(initVal.type().assignableTo(newLoopVar.type()));
      assert(updateVal.type().assignableTo(newLoopVar.type()));
      loopVars.set(index, newLoopVar);
      initVals.set(index, initVal);
      loopContinue.setNewLoopVar(index, updateVal);
      blockingVars.set(index, blocking);

      // Reset closed info to be safe
      closedInitVals.set(index, false);
      loopContinue.setLoopVarClosed(index, false);
    }
  }

  public static class LoopInstructions {
    private LoopInstructions(Block breakInstBlock, LoopBreak breakInst,
        Block continueInstBlock, LoopContinue continueInst) {
      super();
      this.breakInstBlock = breakInstBlock;
      this.breakInst = breakInst;
      this.continueInstBlock = continueInstBlock;
      this.continueInst = continueInst;
    }
    public final Block breakInstBlock;
    public final LoopBreak breakInst;
    public final Block continueInstBlock;
    public final LoopContinue continueInst;
  }


  public static class NestedBlock extends Continuation {
    private final Block block;

    public NestedBlock() {
      this(new Block(BlockType.NESTED_BLOCK, null));
    }


    public NestedBlock(Block block) {
      super();
      this.block = block;
      this.block.setParent(this, false);
    }

    @Override
    public NestedBlock clone() {
      return new NestedBlock(this.block.clone());
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
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
    public boolean isConditional() {
      return false;
    }

    @Override
    public boolean executesBlockOnce() {
      return true;
    }

    @Override
    protected void replaceConstructVars(Map<Var, Arg> renames,
                                        RenameMode mode) {
      // Do nothing
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      return Var.NONE;
    }

    @Override
    public void removeVars(Set<Var> removeVars) {
      removeVarsInBlocks(removeVars);
    }

    @Override
    public boolean isNoop() {
      return block.isEmpty();
    }
  }


  public static class TargetLocation {
    public static final TargetLocation ANY =
        new TargetLocation(Location.ANY_LOCATION,
            TaskProps.LOC_STRICTNESS_HARD_ARG,
            TaskProps.LOC_ACCURACY_RANK_ARG);

    public final Arg rank;
    public final Arg strictness; // Whether soft targeting
    public final Arg accuracy; // Node or rank level, etc

    public TargetLocation(Arg rank, Arg strictness, Arg accuracy) {
      assert(rank.type().assignableTo(Types.V_INT));
      assert(strictness.type().assignableTo(Types.V_LOC_STRICTNESS));
      assert(accuracy.type().assignableTo(Types.V_LOC_ACCURACY));
      this.rank = rank;
      this.strictness = strictness;
      this.accuracy = accuracy;
    }

    /**
     * @param location2
     * @return true if both have identical parameters
     */
    public boolean identicalTarget(TargetLocation location2) {
      return rank.equals(location2.rank) &&
            strictness.equals(location2.strictness) &&
            accuracy.equals(location2.accuracy);
    }

  }

  /**
   * A new construct that blocks on a list of variables (blockVars),
   * and only runs the contents once all of those variables are closed
   */
  public static class WaitStatement extends AsyncContinuation {
    /** Label name to use in final generated code */
    private final String procName;
    private final Block block;
    private final List<WaitVar> waitVars;

    /* True if this wait was compiler-generated so can be removed if needed
     * We can only remove an explicit wait if we know that the variables are
     * already closed*/
    private WaitMode mode;
    private boolean recursive;
    private ExecTarget target;

    private final TaskProps props;

    public WaitStatement(String procName, List<WaitVar> waitVars,
                    List<PassedVar> passedVars,
                    List<Var> keepOpenVars,
                    WaitMode mode, boolean recursive, ExecTarget target,
                    TaskProps props) {
      this(procName, new Block(BlockType.WAIT_BLOCK, null), true, waitVars,
           passedVars, keepOpenVars, mode, recursive, target, props);
      assert(this.block.getParentCont() != null);
    }

    private WaitStatement(String procName, Block block,
        boolean newBlock, List<WaitVar> waitVars, List<PassedVar> passedVars,
        List<Var> keepOpenVars,
        WaitMode mode, boolean recursive, ExecTarget target,
        TaskProps props) {
      super(passedVars, keepOpenVars);
      assert(waitVars != null);
      assert(passedVars != null);
      assert(keepOpenVars != null);
      assert(target != null);
      assert(mode != null);
      this.procName = procName;
      this.block = block;
      this.block.setParent(this, newBlock);
      this.waitVars = new ArrayList<WaitVar>(waitVars);
      WaitVar.removeDuplicates(this.waitVars);
      this.mode = mode;
      this.recursive = recursive;
      this.target = target;
      this.props = props.clone();
      updateRecursive();
    }

    @Override
    public WaitStatement clone() {
      return new WaitStatement(procName, this.block.clone(),
          false, waitVars, passedVars, keepOpenVars,
          mode, recursive, target, props.clone());
    }

    public Block getBlock() {
      return block;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.startWaitStatement(procName, WaitVar.asVarList(waitVars),
          PassedVar.extractVars(passedVars), recursive, target, props);
      this.block.generate(logger, gen, info);
      gen.endWaitStatement();
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      String newIndent = currentIndent + indent;
      sb.append(currentIndent + "wait (");
      ICUtil.prettyPrintList(sb, waitVars);
      sb.append(") ");
      sb.append("/*" + procName + "*/ " );
      ICUtil.prettyPrintVarInfo(sb, passedVars, keepOpenVars);
      sb.append(" <" + mode + ", " + target + ", " +
                (recursive ? "RECURSIVE" : "NONRECURSIVE") + ">");
      ICUtil.prettyPrintProps(sb, props);
      sb.append(" {\n");
      block.prettyPrint(sb, newIndent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(block);
    }

    @Override
    public void replaceConstructVars_(Map<Var, Arg> renames,
                                      RenameMode mode) {
      WaitVar.replaceVars(waitVars, renames);

      ICUtil.replaceArgValsInMap(renames, props);
    }



    public WaitMode getMode() {
      return mode;
    }

    public boolean isRecursive() {
      return recursive;
    }

    public ExecTarget getTarget() {
      return target;
    }
    @Override
    public ContinuationType getType() {
      return ContinuationType.WAIT_STATEMENT;
    }

    public void setTarget(ExecTarget target) {
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
    public boolean spawnsSingleTask() {
      return !isParallel();
    }

    public boolean isParallel() {
      Arg parallelism = parallelism();
      return parallelism != null && !(parallelism.isInt() &&
                                      parallelism.getInt() > 1);
    }

    public Arg parallelism() {
      return props.get(TaskPropKey.PARALLELISM);
    }

    /**
     * @return target location.  Non-null.
     */
    public TargetLocation targetLocation() {
      return new TargetLocation(props.getWithDefault(TaskPropKey.LOC_RANK),
                                props.getWithDefault(TaskPropKey.LOC_STRICTNESS),
                                props.getWithDefault(TaskPropKey.LOC_ACCURACY));
    }

    @Override
    public boolean isLoop() {
      // The parallel annotation means that the contents of block can execute
      // multiple times => basically a loop
      return isParallel();
    }

    @Override
    public boolean isConditional() {
      return false;
    }

    @Override
    public boolean executesBlockOnce() {
      return true;
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      ArrayList<Var> res = new ArrayList<Var>();
      for (WaitVar wv: waitVars) {
        // Explicit variables cannot be eliminated
        if (!forDeadCodeElim || wv.explicit) {
          res.add(wv.var);
        }
      }
      for (Arg arg: props.values()) {
        assert(arg != null);
        if (arg.isVar()) {
          res.add(arg.getVar());
        }
      }
      return res; // can later eliminate waitVars, etc
    }

    @Override
    public List<PassedVar> getMustPassVars() {
      try {
        if (Settings.getBoolean(Settings.MUST_PASS_WAIT_VARS)) {
          List<PassedVar> res = new ArrayList<PassedVar>();
          for (WaitVar wv: waitVars) {
            res.add(new PassedVar(wv.var, false));
          }
          return res;
        } else {
          return PassedVar.NONE;
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
    }

    public List<WaitVar> getWaitVars() {
      return Collections.unmodifiableList(this.waitVars);
    }

    public void addWaitVars(Collection<WaitVar> vars) {
      this.waitVars.addAll(vars);
      WaitVar.removeDuplicates(this.waitVars);
    }

    @Override
    public void removeVars_(Set<Var> removeVars) {
      ListIterator<WaitVar> it = waitVars.listIterator();
      while (it.hasNext()) {
        WaitVar wv = it.next();
        if (removeVars.contains(wv.var)) {
          it.remove();
        }
      }

      updateRecursive();
    }

    @Override
    public boolean isNoop() {
      return this.block.isEmpty();
    }

    @Override
    public Block tryInline(Set<Var> closedVars, Set<Var> recClosedVars,
        boolean keepExplicitDependencies) {
      boolean varsLeft = false;
      // iterate over wait vars, remove those in list
      ListIterator<WaitVar> it = waitVars.listIterator();
      while(it.hasNext()) {
        WaitVar wv = it.next();
        // See if we can skip waiting on var
        if (keepExplicitDependencies && wv.explicit) {
          varsLeft = true;
        } else if ((closedVars.contains(wv.var) && !recursionRequired(wv.var))
            || recClosedVars.contains(wv.var)) {
          it.remove();
        } else {
          varsLeft = true;
        }
      }
      // Can't eliminate if purpose of wait is to dispatch task
      if (varsLeft || mode == WaitMode.TASK_DISPATCH ||
          isParallel() || targetLocation() != null) {
        return null;
      } else {
        // if at end we have nothing left, return the inner block for inlining
        return block;
      }
    }

    public void removeWaitVars(List<WaitVar> toRemove,
        boolean allRecursive, boolean retainExplicit) {
      ListIterator<WaitVar> it = waitVars.listIterator();
      while (it.hasNext()) {
        WaitVar wv = it.next();
        for (WaitVar r: toRemove) {
          if (r.var.equals(wv.var)) {
            if (this.recursive && !allRecursive && recursionRequired(wv.var)) {
              // Do nothing
            } else if (!retainExplicit || r.explicit || !wv.explicit) {
              // Sufficiently explicit
              it.remove();
            }
          }
        }
      }

      updateRecursive();
    }

    /**
     * Check if we can convert a recursive wait to non-recursive
     */
    private void updateRecursive() {
      if (recursive) {
        for (WaitVar wv: waitVars) {
          if (recursionRequired(wv.var)) {
            return;
          }
        }
        // If we made it here, don't need to recurse
        recursive = false;
      }
    }

    public void inlineInto(Block dstBlock) {
      inlineInto(dstBlock, this.block);
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
      // Check if we actually need recursion
      return RefCounting.recursiveClosePossible(wv);
    }

    @Override
    public List<BlockingVar> blockingVars(boolean includeConstructDefined) {
      ArrayList<BlockingVar> res = new ArrayList<BlockingVar>(waitVars.size());
      for (WaitVar wv: waitVars) {
        res.add(new BlockingVar(wv.var, this.recursive, wv.explicit));
      }
      return res;
    }

    /**
     * True if any wait vars are explicit
     * @return
     */
    public boolean hasExplicit() {
      for (WaitVar wv: waitVars) {
        if (wv.explicit) {
          return true;
        }
      }

      return false;
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      return target.actualContext(outerContext);
    }
  }

  /**
   * A new construct that dispatches a task to an asynchronous executor,
   * which runs the provided block once execution is finished.
   */
  public static class AsyncExec extends AsyncContinuation {
    /** Label name to use in final generated code */
    private final String procName;
    private final Block block;

    /**
     * Executor that it will execute on
     */
    private final AsyncExecutor executor;

    /**
     * Name of command for executor
     */
    private Arg cmdName;

    /**
     * Output variables assigned by task, generally
     * local values
     */
    private final List<Var> taskOutputs;

    /**
     * Arguments that describe task
     */
    private final List<Arg> taskArgs;

    /**
     * Key-value properties to pass to executor
     */
    private final Map<String, Arg> taskProps;

    /**
     * If the task should be treated as having side effects
     */
    private final boolean hasSideEffects;

    public AsyncExec(String procName, AsyncExecutor executor,
          Arg cmdName, List<PassedVar> passedVars, List<Var> keepOpenVars,
          List<Var> taskOutputs, List<Arg> taskArgs,
          Map<String, Arg> taskProps, boolean hasSideEffects) {
      this(procName, new Block(BlockType.ASYNC_EXEC_CONTINUATION, null),
          true, executor, cmdName, passedVars, keepOpenVars, taskOutputs,
          taskArgs, taskProps, hasSideEffects);
      assert(this.block.getParentCont() != null);
    }

    private AsyncExec(String procName, Block block, boolean newBlock,
        AsyncExecutor executor,
        Arg cmdName, List<PassedVar> passedVars, List<Var> keepOpenVars,
        List<Var> taskOutputs,
        List<Arg> taskArgs, Map<String, Arg> taskProps,
        boolean hasSideEffects) {
      super(passedVars, keepOpenVars);
      assert(passedVars != null);
      assert(keepOpenVars != null);
      assert(executor != null);
      assert(taskArgs != null);
      assert(taskProps != null);
      this.procName = procName;
      this.block = block;
      this.block.setParent(this, newBlock);
      this.executor = executor;
      this.cmdName = cmdName;
      this.taskOutputs = new ArrayList<Var>(taskOutputs);
      this.taskArgs = new ArrayList<Arg>(taskArgs);
      this.taskProps = new HashMap<String, Arg>(taskProps);
      this.hasSideEffects = hasSideEffects;
    }

    @Override
    public AsyncExec clone() {
      return new AsyncExec(procName, this.block.clone(),
          false, executor, cmdName, passedVars, keepOpenVars,
          taskOutputs, taskArgs, taskProps, hasSideEffects);
    }

    public Block getBlock() {
      return block;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      boolean hasContinuation = !this.block.isEmpty();
      gen.startAsyncExec(procName, PassedVar.extractVars(passedVars),
          executor, cmdName, taskOutputs, taskArgs, taskProps,
          hasContinuation);
      this.block.generate(logger, gen, info);
      gen.endAsyncExec(hasContinuation);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      String newIndent = currentIndent + indent;
      sb.append(currentIndent + "async_exec ");
      sb.append(executor.toString());
      sb.append(" ");
      sb.append(cmdName.toString());
      sb.append(" (");
      ICUtil.prettyPrintVarList(sb, taskOutputs);
      sb.append(") (");
      ICUtil.prettyPrintArgList(sb, taskArgs);
      if (!taskProps.isEmpty()) {
        sb.append(",");
        for (Entry<String, Arg> e: taskProps.entrySet()) {
          sb.append("\"");
          sb.append(e.getKey());
          sb.append("\"=");
          sb.append(e.getValue().toString());
        }
      }
      sb.append(") ");
      sb.append("/*" + procName + "*/ " );
      ICUtil.prettyPrintVarInfo(sb, passedVars, keepOpenVars);
      sb.append(" {\n");
      block.prettyPrint(sb, newIndent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(block);
    }

    @Override
    public void replaceConstructVars_(Map<Var, Arg> renames,
                                      RenameMode mode) {
      cmdName = ICUtil.replaceArg(renames, cmdName, false);

      if (mode == RenameMode.REFERENCE ||
          mode == RenameMode.REPLACE_VAR)
      {
        ICUtil.replaceVarsInList(renames, taskOutputs, false);
      }
      ICUtil.replaceArgsInList(renames, taskArgs, false);
      ICUtil.replaceArgValsInMap(renames, taskProps);
    }
    @Override
    public ContinuationType getType() {
      return ContinuationType.ASYNC_EXEC;
    }

    @Override
    public boolean isAsync() {
      return true;
    }

    @Override
    public VarPassing variablePassing() {
      // Runs in same context
      return VarPassing.MANUAL_LOCAL;
    }

    @Override
    public boolean spawnsSingleTask() {
      return true;
    }

    @Override
    public boolean isLoop() {
      return false;
    }

    @Override
    public boolean isConditional() {
      return false;
    }

    @Override
    public boolean executesBlockOnce() {
      return true;
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      ArrayList<Var> res = new ArrayList<Var>();
      ICUtil.addIfVar(res, this.cmdName);
      ICUtil.addVars(res, this.taskArgs);
      ICUtil.addVars(res, this.taskProps.values());
      return res; // can later eliminate waitVars, etc
    }

    @Override
    public void removeVars_(Set<Var> removeVars) {
      // Nothing: arguments shouldn't be removed
    }

    @Override
    public boolean isNoop() {
      return !hasSideEffects && this.block.isEmpty();
    }

    @Override
    public Block tryInline(Set<Var> closedVars, Set<Var> recClosedVars,
        boolean keepExplicitDependencies) {
      // Can't inline since the task needs to execute
      return null;
    }

    @Override
    public List<BlockingVar> blockingVars(boolean includeConstructDefined) {
      return Collections.emptyList();
    }

    @Override
    public ExecContext childContext(ExecContext outerContext) {
      // Continuation should run in same context;
      return outerContext;
    }

    @Override
    public List<Var> constructDefinedVars(ContVarDefType type) {

      if (type.includesInitOnly()) {
        List<Var> assigned = new ArrayList<Var>();
        for (Var taskOutput: taskOutputs) {
          if (Types.assignBeforeRead(taskOutput)) {
            assigned.add(taskOutput);
          }
        }
        return assigned;
      } else {
        return Var.NONE;
      }
    }
  }
}
