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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.CompileTimeArgs;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.ForeignFunctions.SpecialFunction;
import exm.stc.common.lang.OpEvaluator;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.Counters;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.ComputedValue;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.opt.ResultVal;
import exm.stc.ic.opt.Semantics;
import exm.stc.ic.opt.ValueTracker;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
/**
 * This class contains instructions used in the intermediate representation.
 * Each instruction is responsible for making particular modifications to
 * itself, and for reporting particular information about itself. The
 * Instruction interface has a number of methods that each instruction
 * must implement for this purpose.
 *
 */
public class ICInstructions {
  public static abstract class Instruction implements Statement {
    public final Opcode op;
  
    public Instruction(Opcode op) {
      super();
      this.op = op;
    }
    

    public StatementType type() {
      return StatementType.INSTRUCTION;
    }
    public Conditional conditional() {
      throw new STCRuntimeError("Not a conditional");
    }
    public Instruction instruction() {
      return this;
    }
  
    /**
     * @return a short name for the operation used for human-readable
     *        diagnostics 
     */
    public String shortOpName() {
      return op.toString().toLowerCase();
    }
    
    @Override
    public void setParent(Block parent) {
      // Do nothing
    }
   
    public void removeVars(Set<Var> removeVars) {
      // default impl: do nothing
    }
  
    /**
     * Replace instruction variables according to mode
     * @param renames
     */
    public abstract void renameVars(Map<Var, Arg> renames, RenameMode mode);

    @Override
    public abstract String toString();
    
    public void prettyPrint(StringBuilder sb, String indent) {
      sb.append(indent);
      sb.append(this.toString());
      sb.append("\n");
    }
  
    public abstract void generate(Logger logger, CompilerBackend gen,
            GenInfo info);
  
  
    /** List of variables the instruction reads */
    public abstract List<Arg> getInputs();
  
    /** List of variables the instruction writes */
    public abstract List<Var> getOutputs();
    
    public Arg getInput(int i) {
      return getInputs().get(i);
    }
    
    public Var getOutput(int i) {
      return getOutputs().get(i);
    }
  
    public abstract boolean hasSideEffects();
  
    /**
     * @return true if it is safe to change timing relative
     * to other tasks (e.g. if it is necessary that it should
     * return an up to date version of something
     */
    public boolean canChangeTiming() {
      return !hasSideEffects();
    }

    public boolean writesAliasVar() {
      // Writes to alias variables can have non-local effects
      for (Var out: this.getOutputs()) {
        if (out.storage() == Alloc.ALIAS) {
          return true;
        }
      }
      return false;
    }
    
    public boolean writesMappedVar() {
      // Writes to alias variables can have non-local effects
      for (Var out: this.getOutputs()) {
        if (out.mapping() != null) {
          return true;
        }
      }
      return false;
    }
  
    /**
     *
     * @param fnName name of enclosing function for error message purposes
     * @param knownConstants
     * @return null if we cannot replace all outputs with constants
     */
    public abstract Map<Var, Arg> constantFold(
                    String fnName,
                    Map<Var, Arg> knownConstants);
  
    /**
     * @param knownConstants
     * @return an instruction if this can be replaced by another instruction
     *      using a constant value, null if it cannot be replaced
     */
    public abstract Instruction constantReplace(
                                Map<Var, Arg> knownConstants);
  
    
    public static class MakeImmRequest {
      public final List<Var> out;
      public final List<Var> in;
      /** Where immediate code should run.  Default is local: in the current context */
      public final TaskMode mode;
      /** If inputs should be recursively closed */
      public final boolean recursiveClose;
      /** If outputs should have mapping initialized */
      public final boolean mapOutVars;
      
      public MakeImmRequest(List<Var> out, List<Var> in) {
        this(out, in, TaskMode.LOCAL);
      }
      
      public MakeImmRequest(List<Var> out, List<Var> in, TaskMode mode) {
        this(out, in, mode, false);
      }
      public MakeImmRequest(List<Var> out, List<Var> in, TaskMode mode,
                            boolean recursiveClose) {
        this(out, in, mode, recursiveClose, true);
      } 
      public MakeImmRequest(List<Var> out, List<Var> in, TaskMode mode,
          boolean recursiveClose, boolean mapOutVars) {
        this.out = out;
        this.in = in;
        this.mode = mode;
        this.recursiveClose = recursiveClose;
        this.mapOutVars = mapOutVars;
      }
    }
    
    public static class MakeImmChange {
      /** Optional: if the output variable of op changed */
      public final Var newOut;
      public final Var oldOut;
      
      /** Whether caller should store output results */
      public final boolean storeOutputVals;
      public final Instruction newInsts[];
      
      /**
       * If the output variable changed from reference to plain future
       * @param newOut
       * @param oldOut
       * @param newInst
       */
      public MakeImmChange(Var newOut, Var oldOut, Instruction newInst) {
        this(newOut, oldOut, new Instruction[] {newInst});
      }
      
      /**
       * If the output variable changed from reference to plain future
       * @param newOut
       * @param oldOut
       * @param newInsts
       */
      public MakeImmChange(Var newOut, Var oldOut, Instruction newInsts[]) {
        this(newOut, oldOut, newInsts, true);
      }
      
      public MakeImmChange(Var newOut, Var oldOut, Instruction newInsts[],
          boolean storeOutputVals) {
        this.newOut = newOut;
        this.oldOut = oldOut;
        this.newInsts = newInsts;
        this.storeOutputVals = storeOutputVals;
      }
      
      /**
       * If we're just changing the instruction
       * @param newInst
       */
      public MakeImmChange(Instruction newInst) {
        this(null, null, newInst);
      }
      
      /**
       * If we're just changing the instructions
       * @param newInsts
       */
      public MakeImmChange(Instruction newInsts[]) {
        this(null, null, newInsts);
      }
      
      public MakeImmChange(Instruction[] newInsts, boolean storeOutputVals) {
        this(null, null, newInsts, storeOutputVals);
      }

      /**
       * Does the new instruction have a different output to the
       * old one
       * @return
       */
      public boolean isOutVarSame() {
        return newOut == null;
      }
    }
    
    /**
     * 
     * @param closedVars variables closed at point of current instruction
     * @param waitForClose if true, allowed to (must don't necessarily
     *        have to) request that unclosed vars be waited for
     * @return null if it cannot be made immediate, if true,
     *            a list of vars that are the variables whose values are needed
     *            and output vars that need to be have value vars created
     */
    public abstract MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                                  boolean waitForClose);

    public abstract MakeImmChange makeImmediate(List<Var> outVals,
                                                List<Arg> inValues);

    /**
     * @return non-null the futures this instruction will block on
     *        it is ok if it forgets variables which aren't blocked on,
     *        but all variables returned must be blocked on
     */
    public abstract List<Var> getBlockingInputs();
    
    /**
     * Some instructions will spawn off asynchronous tasks
     * @return SYNC if nothing spawned, otherwise the variety of task spawned
     */
    public abstract TaskMode getMode();
    
    /**
     * @return List of outputs closed immediately after instruction returns
     */
    public List<Var> getClosedOutputs() {
      return Var.NONE; // Default - assume nothing closed
    }

    /**
     * @return List of outputs that are piecewise assigned
     */
    public List<Var> getPiecewiseAssignedOutputs() {
      return Var.NONE;
    }
    
    /**
     * @return list of vars initialized by this instruction
     */
    public List<Var> getInitialized() {
      return Var.NONE;
    }

    /**
     * @return list of output variables that are actually modified
     *      typically this is all outputs, but in some special cases
     *      this is not true.  This is important to know for dead
     *      code elimination as sometimes we can safely eliminate an
     *      instruction even if all outputs can't be eliminated
     */
    public List<Var> getModifiedOutputs() {
      return this.getOutputs();
    }
    
    /**
     * @param fns map of functions (can optionally be null)
     * @return list of outputs for which previous value is read
     */
    public List<Var> getReadOutputs(Map<String, Function> fns) {
      return Var.NONE;
    }
    
    public final List<Var> getReadOutputs() {
      return getReadOutputs(null);
    }

    /**
     * @return priority of task spawned, if any.  null if no spawn or
     *      default priority
     */
    public TaskProps getTaskProps() {
      return null;
    }
    
    /**
     * @param existing already known values (sometimes needed to 
     *              work out which vales are created by an instruction)
     * @return a list of all values computed by expression.  Each ComputedValue
     *        returned should have the out field set so we know where to find 
     *        it 
     */
    public abstract List<ResultVal> getResults(CVMap existing);
    
    @Override
    public Statement cloneStatement() {
      return clone();
    }
    
    public abstract Instruction clone();

    
    public Pair<List<Var>, List<Var>> getIncrVars(Map<String, Function> functions) {
      return getIncrVars();
    }

    /**
     * @return (read vars to be incremented, write vars to be incremented)
     */
    protected Pair<List<Var>, List<Var>> getIncrVars() {
      return Pair.create(getReadIncrVars(), getWriteIncrVars());
    }

    /**
     * @return list of vars that need read refcount increment
     */
    public List<Var> getReadIncrVars() {
      return Var.NONE;
    }

    /**
     * @return list of vars that need write refcount increment
     */
    public List<Var> getWriteIncrVars() {
      return Var.NONE;
    }

    /**
     * Try to piggyback increments or decrements to instruction
     * @param increments count of increment or decrement operations per var
     * @param type
     * @return empty list if not successful, otherwise list of vars for which
     *      piggyback occurred
     *          
     */
    public List<Var> tryPiggyback(Counters<Var> increments, RefCountType type) {
      return Var.NONE;
    }

    /**
     * If this instruction makes an output a part of another
     * variable such that modifying the output modifies something
     * else
     * @return null if nothing
     */
    public Pair<Var, Var> getComponentAlias() {
      // Default is nothing, few instructions do this
      return null;
    }

    /**
     * @return true if side-effect or output modification is idempotent
     */
    public boolean isIdempotent() {
      return false;
    }
  }
  
  public static interface CVMap {
    
    public boolean isClosed(Var v);
    
    /**
     * @param val
     * @return the current location of a given computedValue
     *        (either a constant value, or a variable)
     */
    public Arg getLocation(ComputedValue val);
    /**
     * @param v
     * @return all computed values stored in var
     */
    public List<ComputedValue> getVarContents(Var v);
    
    /**
     * Get computed values in which this variable is in input
     * @param input
     * @return
     */
    public List<Pair<Arg, ComputedValue>> getReferencedCVs(Var input);
  }

  public static class Comment extends Instruction {
    private final String text;
    public Comment(String text) {
      super(Opcode.COMMENT);
      this.text = text;
    }
  
    @Override
    public String toString() {
      return "# " + text;
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.addComment(text);
    }
  
    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      // Don't do anything
    }
  
    @Override
    public List<Arg> getInputs() {
      return new ArrayList<Arg>(0);
    }
  
    @Override
    public List<Var> getOutputs() {
      return new ArrayList<Var>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return false;
    }
  
    @Override
    public Map<Var, Arg> constantFold(String fnName,
                Map<Var, Arg> knownConstants) {
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars, 
                                           boolean waitForClose) {
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on comment!");
    }

    @Override
    public List<Var> getBlockingInputs() {
      return Var.NONE;
    }

    @Override
    public List<ResultVal> getResults(CVMap existing) {
      return null;
    }

    @Override
    public Instruction clone() {
      return new Comment(this.text);
    }

    @Override
    public TaskMode getMode() {
      return TaskMode.SYNC;
    }
  }
  
  /**
   * Class to represent builtin Turbine operations with fixed number
   * of arguments
   */
  public static class TurbineOp extends Instruction {
    
    /** Private constructor: use static methods to create */
    private TurbineOp(Opcode op, List<Var> outputs, List<Arg> inputs) {
      this(op, outputs, inputs, Collections.<Arg>emptyList());
    }
    
    private TurbineOp(Opcode op, List<Var> outputs, List<Arg> inputs,
                      List<Arg> dummyInputs) {
      super(op);
      this.outputs = initArgList(outputs);
      this.inputs = initArgList(inputs);
      this.dummyInputs = initArgList(dummyInputs);
    }
    
    
    private static Class<? extends Object> SINGLETON_LIST =
                             Collections.singletonList(null).getClass();
    /**
     * Initialize args as list that support .set() operation.
     * @param args
     * @return
     */
    private static <T> List<T> initArgList(List<T> args) {
      if (args.isEmpty()) {
        // Nothing will be mutated in list, so use placeholder
        return Collections.emptyList();
      } else if (SINGLETON_LIST.isInstance(args)) {
        // Avoid known-bad list classes
        return new ArrayList<T>(args);
      } else {
        return args;
      }
    }

    private TurbineOp(Opcode op, Var output, Arg ...inputs) {
      this(op, Arrays.asList(output), Arrays.asList(inputs));
    }
    
    private TurbineOp(Opcode op, List<Var> outputs, Arg ...inputs) {
      this(op, outputs, Arrays.asList(inputs));
    }
  
    private List<Var> outputs; /** Variables that are modified by this instruction */
    private List<Arg> inputs; /** Variables that are read-only */
    
    // TODO: dummyInputs is unused?
    /** Variables that are not actually read, but somehow are related */
    private List<Arg> dummyInputs;
    
    @Override
    public String toString() {
      String result = op.toString().toLowerCase();
      for (Var o: outputs) {
        result += " " + o.name();
      }
      for (Arg i: inputs) {
        result += " " + i.toString();
      }
      for (Arg i: dummyInputs) {
        result += " " + i.toString();
      }
      return result;
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      // Recreate calls that were used to generate this instruction
      switch (op) {
      case STORE_INT:
        gen.assignInt(getOutput(0), getInput(0));
        break;
      case STORE_BOOL:
        gen.assignBool(getOutput(0), getInput(0));
        break;
      case STORE_VOID:
        gen.assignVoid(getOutput(0), getInput(0));
        break;
      case STORE_FLOAT:
        gen.assignFloat(getOutput(0), getInput(0));
        break;
      case STORE_STRING:
        gen.assignString(getOutput(0), getInput(0));
        break;
      case STORE_BLOB:
        gen.assignBlob(getOutput(0), getInput(0));
        break;
      case STORE_FILE:
        gen.assignFile(getOutput(0), getInput(0));
        break;
      case STORE_REF:
        gen.assignReference(getOutput(0), getInput(0).getVar());
        break;
      case ARRAY_LOOKUP_FUTURE:
        gen.arrayLookupFuture(getOutput(0), 
            getInput(0).getVar(), getInput(1).getVar(), false);
        break;
      case ARRAYREF_LOOKUP_FUTURE:
        gen.arrayLookupFuture(getOutput(0), 
            getInput(0).getVar(), getInput(1).getVar(), true);
        break;
      case ARRAY_LOOKUP_REF_IMM:
        gen.arrayLookupRefImm(getOutput(0), getInput(0).getVar(),
                              getInput(1), false);
        break;
      case ARRAY_LOOKUP_IMM:
        gen.arrayLookupImm(getOutput(0), getInput(0).getVar(),
                           getInput(1));
        break;
      case ARRAYREF_LOOKUP_IMM:
        gen.arrayLookupRefImm(getOutput(0), getInput(0).getVar(),
                              getInput(1), true);
        break;
      case ARRAY_INSERT_FUTURE:
        gen.arrayInsertFuture(getOutput(0), getInput(0).getVar(),
                              getInput(1).getVar(),
                              getInputs().size() == 3 ? getInput(2) : Arg.ONE);
        break;
      case ARRAY_DEREF_INSERT_FUTURE:
        gen.arrayDerefInsertFuture(getOutput(0), getInput(0).getVar(),
                              getInput(1).getVar(),
                              getInputs().size() == 3 ? getInput(2) : Arg.ONE);
        break;
      case ARRAY_INSERT_IMM:
        gen.arrayInsertImm(getOutput(0), getInput(0), getInput(1).getVar(),
            getInputs().size() == 3 ? getInput(2) : Arg.ZERO);
        break;
      case ARRAY_DEREF_INSERT_IMM:
        gen.arrayDerefInsertImm(getOutput(0), getInput(0), getInput(1).getVar(),
            getInputs().size() == 3 ? getInput(2) : Arg.ONE);
        break;
      case ARRAYREF_INSERT_FUTURE:
        gen.arrayRefInsertFuture(getOutput(0),
            getOutput(1), getInput(0).getVar(), getInput(1).getVar());
        break;
      case ARRAYREF_DEREF_INSERT_FUTURE:
        gen.arrayRefDerefInsertFuture(getOutput(0),
            getOutput(1), getInput(0).getVar(), getInput(1).getVar());
        break;
      case ARRAYREF_INSERT_IMM:
        gen.arrayRefInsertImm(getOutput(0),
            getOutput(1), getInput(0), getInput(1).getVar());
        break;
      case ARRAYREF_DEREF_INSERT_IMM:
        gen.arrayRefDerefInsertImm(getOutput(0),
            getOutput(1), getInput(0), getInput(1).getVar());
        break;
      case ARRAY_BUILD:
        gen.arrayBuild(getOutput(0), Arg.toVarList(getInputs()));
        break;
      case STRUCT_LOOKUP:
        gen.structLookup(getOutput(0), getInput(0).getVar(),
                         getInput(1).getStringLit());
        break;
      case STRUCTREF_LOOKUP:
        gen.structRefLookup(getOutput(0), getInput(0).getVar(),
                             getInput(1).getStringLit());
        break;
      case STRUCT_INSERT:
        gen.structInsert(getOutput(0), getInput(0).getStringLit(),
                         getInput(1).getVar());
        break;
      case DEREF_INT:
        gen.dereferenceInt(getOutput(0), getInput(0).getVar());
        break;
      case DEREF_BOOL:
        gen.dereferenceBool(getOutput(0), getInput(0).getVar());
        break;
      case DEREF_FLOAT:
        gen.dereferenceFloat(getOutput(0), getInput(0).getVar());
        break;
      case DEREF_STRING:
        gen.dereferenceString(getOutput(0), getInput(0).getVar());
        break;
      case DEREF_BLOB:
        gen.dereferenceBlob(getOutput(0), getInput(0).getVar());
        break;
      case DEREF_FILE:
        gen.dereferenceFile(getOutput(0), getInput(0).getVar());
        break;
      case LOAD_REF:
        gen.retrieveRef(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;
      case COPY_REF:
        gen.makeAlias(getOutput(0), getInput(0).getVar());
        break;
      case ARRAY_CREATE_NESTED_FUTURE:
        gen.arrayCreateNestedFuture(getOutput(0), getOutput(1), 
                                    getInput(0).getVar());
        break;
      case ARRAYREF_CREATE_NESTED_FUTURE:
        gen.arrayRefCreateNestedFuture(getOutput(0), getOutput(1), getOutput(2),
                                       getInput(0).getVar());
        break;
      case ARRAYREF_CREATE_NESTED_IMM:
        gen.arrayRefCreateNestedImm(getOutput(0), getOutput(1), getOutput(2),
                                    getInput(0));
        break;
      case ARRAY_CREATE_NESTED_IMM:
        gen.arrayCreateNestedImm(getOutput(0), getOutput(1), getInput(0),
                                 getInput(1), getInput(2));
        break;
      case LOAD_INT:
        gen.retrieveInt(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;
      case LOAD_STRING:
        gen.retrieveString(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;
      case LOAD_BOOL:
        gen.retrieveBool(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;
      case LOAD_VOID:
        gen.retrieveVoid(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;
      case LOAD_FLOAT:
        gen.retrieveFloat(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;  
      case LOAD_BLOB:
        gen.retrieveBlob(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;
      case LOAD_FILE:
        gen.retrieveFile(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
        break;
      case FREE_BLOB:
        gen.freeBlob(getOutput(0));
        break;
      case DECR_LOCAL_FILE_REF:
        gen.decrLocalFileRef(getInput(0).getVar());
        break;
      case INIT_UPDATEABLE_FLOAT:
        gen.initUpdateable(getOutput(0), getInput(0));
        break;
      case LATEST_VALUE:
        gen.latestValue(getOutput(0), getInput(0).getVar());
        break;
      case UPDATE_INCR:
        gen.update(getOutput(0), UpdateMode.INCR, getInput(0).getVar());
        break;
      case UPDATE_MIN:
        gen.update(getOutput(0), UpdateMode.MIN, getInput(0).getVar());
        break;
      case UPDATE_SCALE:
        gen.update(getOutput(0), UpdateMode.SCALE, getInput(0).getVar());
        break;
      case UPDATE_INCR_IMM:
        gen.updateImm(getOutput(0), UpdateMode.INCR, getInput(0));
        break;
      case UPDATE_MIN_IMM:
        gen.updateImm(getOutput(0), UpdateMode.MIN, getInput(0));
        break;
      case UPDATE_SCALE_IMM:
        gen.updateImm(getOutput(0), UpdateMode.SCALE, getInput(0));
        break;
      case GET_FILENAME:
        gen.getFileName(getOutput(0), getInput(0).getVar());
        break;
      case GET_LOCAL_FILENAME:
        gen.getLocalFileName(getOutput(0), getInput(0).getVar());
        break;
      case IS_MAPPED:
        gen.isMapped(getOutput(0), getInput(0).getVar());
        break;
      case SET_FILENAME_VAL:
        gen.setFilenameVal(getOutput(0), getInput(0));
        break;
      case CHOOSE_TMP_FILENAME:
        gen.chooseTmpFilename(getOutput(0));
        break;
      case INIT_LOCAL_OUTPUT_FILE:
        gen.initLocalOutputFile(getOutput(0), getInput(0), getInput(1));
        break;
      case COPY_FILE_CONTENTS:
        gen.copyFileContents(getOutput(0), getInput(0).getVar());
        break;
      default:
        throw new STCRuntimeError("didn't expect to see op " +
                  op.toString() + " here");
      }
  
    }
  
    public static TurbineOp arrayRefLookupFuture(Var oVar, Var arrayRefVar,
        Var indexVar) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_FUTURE, oVar,
                            arrayRefVar.asArg(), indexVar.asArg());
    }
  
    public static TurbineOp arrayLookupFuture(Var oVar, Var arrayVar,
        Var indexVar) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_FUTURE,
          oVar, arrayVar.asArg(), indexVar.asArg());
    }
  
    public static Instruction arrayInsertFuture(Var array,
        Var ix, Var member) {
      return new TurbineOp(Opcode.ARRAY_INSERT_FUTURE,
              array, ix.asArg(),
              member.asArg());
    }
    
    public static Instruction arrayDerefInsertFuture(Var array,
        Var ix, Var member) {
      return new TurbineOp(Opcode.ARRAY_DEREF_INSERT_FUTURE,
              array, ix.asArg(),
              member.asArg());
    }
  
    public static Instruction arrayRefInsertFuture(Var outerArray,
        Var array, Var ix, Var member) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_FUTURE,
          Arrays.asList(outerArray, array), ix.asArg(), member.asArg());
    }
    
    public static Instruction arrayRefDerefInsertFuture(Var outerArray,
        Var array, Var ix, Var member) {
      return new TurbineOp(Opcode.ARRAYREF_DEREF_INSERT_FUTURE,
          Arrays.asList(outerArray, array),
          ix.asArg(), member.asArg());
    }
    
    public static Instruction arrayInsertImm(Var array,
        Arg ix, Var member) {
      return new TurbineOp(Opcode.ARRAY_INSERT_IMM,
                            array, ix, member.asArg());
    }
    
    public static Instruction arrayDerefInsertImm(Var array,
        Arg ix, Var member) {
      return new TurbineOp(Opcode.ARRAY_DEREF_INSERT_IMM,
                           array, ix, member.asArg());
    }

    public static Instruction arrayRefInsertImm(Var outerArray,
        Var array, Arg ix, Var member) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_IMM,
          Arrays.asList(outerArray, array),
          ix, member.asArg());
    }
    
    public static Instruction arrayRefDerefInsertImm(Var outerArray,
        Var array, Arg ix, Var member) {
      return new TurbineOp(Opcode.ARRAYREF_DEREF_INSERT_IMM,
          Arrays.asList(outerArray, array),
          ix, member.asArg());
    }

    public static Instruction arrayRefLookupImm(Var oVar,
        Var arrayVar, Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_IMM,
          oVar, arrayVar.asArg(), arrayIndex);
    }
  
    public static Instruction arrayLookupRefImm(Var oVar, Var arrayVar,
        Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_REF_IMM,
          oVar, arrayVar.asArg(), arrayIndex);
    }
    
    public static Instruction arrayLookupImm(Var oVar, Var arrayVar,
        Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_IMM,
          oVar, arrayVar.asArg(), arrayIndex);
    }
  
    public static Instruction arrayBuild(Var array, List<Var> members) {
      ArrayList<Arg> inputs = new ArrayList<Arg>(members.size());
      for (Var mem: members) {
        inputs.add(mem.asArg());
      }
      return new TurbineOp(Opcode.ARRAY_BUILD, array.asList(), inputs);
    }

    public static Instruction structInsert(Var structVar,
        String fieldName, Var fieldContents) {
      return new TurbineOp(Opcode.STRUCT_INSERT,
                      structVar,
                      Arg.createStringLit(fieldName),
                      fieldContents.asArg());
    }

    public static Instruction structLookup(Var oVar, Var structVar,
                                                          String fieldName) {
      assert(oVar.storage() == Alloc.ALIAS) : oVar;
      return new TurbineOp(Opcode.STRUCT_LOOKUP,
          oVar, structVar.asArg(),
              Arg.createStringLit(fieldName));
    }
    
    public static Instruction structRefLookup(Var oVar, Var structVar,
        String fieldName) {
      return new TurbineOp(Opcode.STRUCTREF_LOOKUP,
              oVar, structVar.asArg(),
              Arg.createStringLit(fieldName));
    }

    public static Instruction assignInt(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_INT, target, src);
    }

    public static Instruction assignBool(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_BOOL, target, src);
    }

    public static Instruction assignVoid(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_VOID, target, src);
    }
  
    public static Instruction assignFloat(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_FLOAT, target, src);
    }
  
    public static Instruction assignString(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_STRING, target, src);
    }
  
    public static Instruction assignBlob(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_BLOB, target, src);
    }
    
    public static Instruction assignFile(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_FILE, target, src);
    }

    public static Instruction retrieveString(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_STRING, target, source.asArg());
    }
  
    public static Instruction retrieveInt(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_INT, target, source.asArg());
    }
  
    public static Instruction retrieveBool(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_BOOL, target, source.asArg());
    }

    public static Instruction retrieveVoid(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_VOID, target, source.asArg());
    }
    
    public static Instruction retrieveFloat(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_FLOAT, target, source.asArg());
    }
    
    public static Instruction retrieveBlob(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_BLOB, target, source.asArg());
    }
  
    public static Instruction retrieveFile(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_FILE, target, source.asArg());
    }
    
    public static Instruction freeBlob(Var blobVal) {
      // View refcounted var as output
      return new TurbineOp(Opcode.FREE_BLOB, blobVal);
    }

    public static Instruction decrLocalFileRef(Var fileVal) {
      // View all as inputs: only used in cleanupaction context
      return new TurbineOp(Opcode.DECR_LOCAL_FILE_REF, Collections.<Var>emptyList(),
                                                       fileVal.asArg());
    }
  
    public static Instruction addressOf(Var target, Var src) {
      return new TurbineOp(Opcode.STORE_REF,
          target, src.asArg());
    }
  
    public static Instruction dereferenceInt(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_INT,
          target, src.asArg());
    }
    
    public static Instruction dereferenceBool(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_BOOL,
          target, src.asArg());
    }
  
    public static Instruction dereferenceFloat(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_FLOAT,
          target, src.asArg());
    }
  
    public static Instruction dereferenceString(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_STRING,
          target, src.asArg());
    }
  
    public static Instruction dereferenceBlob(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_BLOB,
          target, src.asArg());
    }
    
    public static Instruction dereferenceFile(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_FILE,
          target, src.asArg());
    }
    
    public static Instruction retrieveRef(Var target, Var src) {
      return new TurbineOp(Opcode.LOAD_REF,
          target, src.asArg());
    }
    
    public static Instruction copyRef(Var dst, Var src) {
      return new TurbineOp(Opcode.COPY_REF,
          dst, src.asArg());
          
    }
  
    public static Instruction arrayCreateNestedComputed(Var arrayResult,
        Var array, Var ix) {
      assert(Types.isArrayRef(arrayResult.type()));
      assert(arrayResult.storage() != Alloc.ALIAS);
      assert(Types.isArray(array.type()));
      assert(Types.isArrayKeyFuture(array, ix));
      // Both arrays are modified, so outputs
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_FUTURE,
          Arrays.asList(arrayResult, array), ix.asArg());
    }
  
    public static Instruction arrayCreateNestedImm(Var arrayResult,
        Var arrayVar, Arg arrIx) {
      assert(Types.isArrayKeyVal(arrayVar, arrIx));
      assert(arrayResult.storage() == Alloc.ALIAS);
      // Both arrays are modified, so outputs
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_IMM,
          Arrays.asList(arrayResult, arrayVar),
          arrIx, Arg.ZERO, Arg.ZERO);
    }
  
    public static Instruction arrayRefCreateNestedComputed(Var arrayResult,
        Var outerArr, Var array, Var ix) {
      assert(Types.isArrayRef(arrayResult.type())): arrayResult;
      assert(arrayResult.storage() != Alloc.ALIAS);
      assert(Types.isArrayRef(array.type())): array;
      assert(Types.isArray(outerArr.type())): outerArr;
      assert(Types.isArrayKeyFuture(array, ix));
      // Returns nested array, modifies outer array and
      // reference counts outmost array
      return new TurbineOp(Opcode.ARRAYREF_CREATE_NESTED_FUTURE,
          Arrays.asList(arrayResult, outerArr, array),
          ix.asArg());
    }
  
    public static Instruction arrayRefCreateNestedImmIx(Var arrayResult,
        Var outerArray, Var array, Arg ix) {
      assert(Types.isArrayRef(arrayResult.type())): arrayResult;
      assert(arrayResult.storage() != Alloc.ALIAS);
      assert(Types.isArrayRef(array.type())): array;
      assert(Types.isArray(outerArray.type())): outerArray;
      assert(Types.isArrayKeyVal(array, ix));
      return new TurbineOp(Opcode.ARRAYREF_CREATE_NESTED_IMM,
          // Returns nested array, modifies outer array and
          // reference counts outmost array
          Arrays.asList(arrayResult, outerArray, array),
          ix);
    }
  
    public static Instruction initUpdateableFloat(Var updateable, Arg val) {
      return new TurbineOp(Opcode.INIT_UPDATEABLE_FLOAT, updateable, val);
      
    }

    public static Instruction latestValue(Var result, Var updateable) {
      return new TurbineOp(Opcode.LATEST_VALUE, result, updateable.asArg());
    }
    
    public static Instruction update(Var updateable,
        UpdateMode updateMode, Var val) {
      Opcode op;
      switch (updateMode) {
      case MIN:
        op = Opcode.UPDATE_MIN;
        break;
      case INCR:
        op = Opcode.UPDATE_INCR;
        break;
      case SCALE:
        op = Opcode.UPDATE_SCALE;
        break;
      default:
        throw new STCRuntimeError("Unknown UpdateMode" + updateMode);
      }
      return new TurbineOp(op, updateable, val.asArg());
    }

    public static Instruction updateImm(Var updateable,
        UpdateMode updateMode, Arg val) {
      Opcode op;
      switch (updateMode) {
      case MIN:
        op = Opcode.UPDATE_MIN_IMM;
        break;
      case INCR:
        op = Opcode.UPDATE_INCR_IMM;
        break;
      case SCALE:
        op = Opcode.UPDATE_SCALE_IMM;
        break;
      default:
        throw new STCRuntimeError("Unknown UpdateMode"
            + updateMode);
      }
      return new TurbineOp(op, updateable, val);
    }
    
    public static Instruction getFileName(Var filename, Var file) {
      return new TurbineOp(Opcode.GET_FILENAME, filename, file.asArg());
    }
    
    public static Instruction getLocalFileName(Var filename, Var file) {
      assert(file.type().assignableTo(Types.V_FILE));
      assert(filename.type().assignableTo(Types.V_STRING));
      return new TurbineOp(Opcode.GET_LOCAL_FILENAME, filename, file.asArg());
    }
 
    public static Instruction setFilenameVal(Var file, Arg filenameVal) {
      return new TurbineOp(Opcode.SET_FILENAME_VAL, file, filenameVal);
    }

    public static Instruction copyFileContents(Var target, Var src) {
      return new TurbineOp(Opcode.COPY_FILE_CONTENTS, target, src.asArg());
    }
    
    public static Instruction isMapped(Var isMapped, Var filename) {
      assert(Types.isBoolVal(isMapped));
      assert(Types.isFile(filename));
      return new TurbineOp(Opcode.IS_MAPPED, isMapped, filename.asArg());
    }

    public static Instruction chooseTmpFilename(Var filenameVal) {
      return new TurbineOp(Opcode.CHOOSE_TMP_FILENAME, filenameVal);
    }
    
    public static Instruction initLocalOutFile(Var localOutFile,
                                  Arg outFilename, Arg isMapped) {
      assert(Types.isFileVal(localOutFile));
      assert(Types.isStringVal(outFilename.type()));
      assert(Types.isBoolVal(isMapped.type()));
      return new TurbineOp(Opcode.INIT_LOCAL_OUTPUT_FILE, localOutFile.asList(),
                           outFilename, isMapped);
    }

    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      if (mode == RenameMode.VALUE) {
        // Fall through
      } else if (mode == RenameMode.REPLACE_VAR) {
        // Straightforward replacement
        ICUtil.replaceVarsInList(renames, outputs, false);
      } else {
        assert(mode == RenameMode.REFERENCE);
        // Don't replace initialized aliases
        List<Var> initAliasOut = getInitialized();
        for (int i = 0; i < outputs.size(); i++) {
          Var output = outputs.get(i);
          if (!initAliasOut.contains(output) &&
                renames.containsKey(output)) {
            Arg repl = renames.get(output);
            if (repl.isVar()) {
              outputs.set(i, repl.getVar());
            }
          }
        }
      }
      renameInputs(renames);
    }
  
    public void renameInputs(Map<Var, Arg> renames) {     
       ICUtil.replaceArgsInList(renames, inputs);
       ICUtil.replaceArgsInList(renames, dummyInputs);
    }
  
    @Override
    public boolean hasSideEffects() {
      switch (op) {
      /* The direct container write functions only mutate their output 
       * argument */
      case STRUCT_INSERT:
        return this.writesAliasVar();
        
      case ARRAY_BUILD:
      case ARRAY_INSERT_FUTURE:
      case ARRAY_DEREF_INSERT_FUTURE:
      case ARRAY_INSERT_IMM:
      case ARRAY_DEREF_INSERT_IMM:
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_DEREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
      case ARRAYREF_DEREF_INSERT_IMM:
        // Effect can be tracked back to original array
        return false;
  
      
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
      case UPDATE_INCR_IMM:
      case UPDATE_MIN_IMM:
      case UPDATE_SCALE_IMM:
      case INIT_UPDATEABLE_FLOAT:
        return true;
      
      case STORE_INT:
      case STORE_BOOL:
      case STORE_FLOAT:
      case STORE_STRING:
      case STORE_BLOB:
      case STORE_VOID:
      case STORE_FILE:
      case DEREF_INT:
      case DEREF_BOOL:
      case DEREF_FLOAT:
      case DEREF_STRING:
      case DEREF_BLOB:
      case DEREF_FILE:
      case LOAD_INT:
      case LOAD_BOOL:
      case LOAD_FLOAT:
      case LOAD_STRING:
      case LOAD_BLOB:
      case LOAD_VOID:
      case LOAD_FILE:
        return this.writesAliasVar();
        
      case ARRAY_LOOKUP_REF_IMM:
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_IMM:
        return false;

      case GET_FILENAME:
        // Only effect is setting alias var
        return false;
      case GET_LOCAL_FILENAME:
        return false;
      case IS_MAPPED:
        // will always returns same result for same var
        return false;
      case CHOOSE_TMP_FILENAME:
        // Non-deterministic
        return true;
      case SET_FILENAME_VAL:
        // Only effect is in file output var
        return false;
      case COPY_FILE_CONTENTS:
        // Only effect is to modify file represented by output var
        return false;

      case INIT_LOCAL_OUTPUT_FILE:
        // If the output is mapped, we want to retain the file,
        // so we treat this as having side-effects
        if (getInput(1).isBoolVal() && getInput(1).getBoolLit() == false) {
          // Definitely unmapped
          return false;
        } else {
          // Maybe mapped
          return true;
        }
        
      case STRUCT_LOOKUP:
      case LOAD_REF:
      case STORE_REF:
      case COPY_REF:
      case STRUCTREF_LOOKUP:
      case ARRAY_LOOKUP_IMM:
      case LATEST_VALUE:
          // Always has alias as output because the instructions initialises
          // the aliases
          return false;
          
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAYREF_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAYREF_CREATE_NESTED_IMM:
          /* It might seem like these nested creation primitives have a 
           * side-effect, but for optimisation purposes they can be treated as 
           * side-effect free, as the side-effect is only relevant if the array 
           * created is subsequently used in a store operation
           */ 
        return false;
      case FREE_BLOB:
      case DECR_LOCAL_FILE_REF:
        /*
         * Reference counting ops can have sideeffect
         */
        return true;
      default:
        throw new STCRuntimeError("Need to add opcode " + op.toString()
            + " to hasSideEffects");
      }
    }
    

    public boolean canChangeTiming() {
      return !hasSideEffects() && op != Opcode.LATEST_VALUE;
    }
  
    @Override
    public List<Var> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }
    
    @Override
    public Arg getInput(int i) {
      return inputs.get(i);
    }
    
    @Override
    public Var getOutput(int i) {
      return outputs.get(i);
    }
  
    @Override
    public List<Arg> getInputs() {
      return Collections.unmodifiableList(inputs);
    }
  
    public void setInput(int i, Arg arg) {
      this.inputs.set(i, arg);
    }

    @Override
    public Map<Var, Arg> constantFold(String fnName,
                        Map<Var, Arg> knownConstants) {
      switch (op) {
      case STORE_INT:
      case STORE_STRING:
      case STORE_BOOL:
      case STORE_VOID:
      case STORE_FLOAT:
      case LOAD_BOOL:
      case LOAD_VOID:
      case LOAD_FLOAT:
      case LOAD_INT:
      case LOAD_STRING:
        // The input arg could be a var or a literal constant
        if (getInput(0).isVar()) {
          Arg val = knownConstants.get(getInput(0).getVar());
          if (val != null) {
            return Collections.singletonMap(getOutput(0), val);
          }
        }
        break;
      case IS_MAPPED: {
        Var file = getInput(0).getVar();
        if (file.isMapped() != Ternary.MAYBE) {
          Arg val = Arg.createBoolLit(file.isMapped() == Ternary.TRUE);
          return Collections.singletonMap(getOutput(0), val);
        }
        break;
      }
      default:
        // do nothing
      }
  
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      switch (op) {
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
        Var index = getInput(1).getVar();
        if (knownConstants.containsKey(index)) {
          Arg cIndex = knownConstants.get(index);
          if (op == Opcode.ARRAY_LOOKUP_FUTURE) {
            return arrayLookupRefImm(getOutput(0),
                getInput(0).getVar(), cIndex);
          } else {
            return arrayRefLookupImm(getOutput(0),
                getInput(0).getVar(), cIndex);
          }
        }
        break;
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_DEREF_INSERT_FUTURE:
      case ARRAY_INSERT_FUTURE:
      case ARRAY_DEREF_INSERT_FUTURE:
        Var sIndex = getInput(0).getVar();
        if (knownConstants.containsKey(sIndex)) {
          Arg cIndex = knownConstants.get(sIndex);
          if (op == Opcode.ARRAYREF_INSERT_FUTURE) {
            return arrayRefInsertImm(getOutput(0),
                getOutput(1), cIndex, getInput(1).getVar());
          } else if (op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE) {
            return arrayRefDerefInsertImm(getOutput(0),
                getOutput(1), cIndex, getInput(1).getVar());            
          } else if (op == Opcode.ARRAY_INSERT_FUTURE) {
            return arrayInsertImm(getOutput(0),
                      cIndex, getInput(1).getVar());
          } else {
            assert(op == Opcode.ARRAY_DEREF_INSERT_FUTURE);
            return arrayDerefInsertImm(getOutput(0),
                cIndex, getInput(1).getVar());
          }
        }
        break;
      default:
        // fall through
      }
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                           boolean waitForClose) {
      boolean insertRefWaitForClose = waitForClose;
      // Try to take advantage of closed variables 
      switch (op) {
      case ARRAY_LOOKUP_REF_IMM: {
        // If array is closed or this index already inserted,
        // don't need to block on array.  
        // NOTE: could try to reduce other forms to this in one step,
        //      but its probably just easier to do it in multiple steps
        //      on subsequent passes
        Var arr = getInput(0).getVar();
        if (closedVars.contains(arr)) {
          // Don't request to wait for close - whole array doesn't need to be
          // closed
          return new MakeImmRequest(null, Arrays.<Var>asList(arr));
        }
        break;
      }
      case ARRAY_LOOKUP_FUTURE: {
        Var index = getInput(1).getVar();
        if (waitForClose || closedVars.contains(index)) {
          return new MakeImmRequest(null, Arrays.asList(index));
        }
        break;
      }
      case ARRAYREF_LOOKUP_FUTURE: {
        Var arr = getInput(0).getVar();
        Var ix = getInput(1).getVar();
        // We will take either the index or the dereferenced array
        List<Var> req = mkImmVarList(waitForClose, closedVars, arr, ix);
        if (req.size() > 0) {
          return new MakeImmRequest(null, req);
        }
        break;
      }
      case ARRAYREF_LOOKUP_IMM: {
        // Could skip using reference
        Var arrRef = getInput(0).getVar();
        if (waitForClose || closedVars.contains(arrRef)) {
          return new MakeImmRequest(null, Arrays.asList(arrRef));
        }
        break;
      }
      // TODO: can we do something with DEREF_INSERT versions here?
      case ARRAY_INSERT_FUTURE:
      case ARRAY_DEREF_INSERT_FUTURE: {
        Var ix = getInput(0).getVar();
        if (waitForClose || closedVars.contains(ix)) {
          return new MakeImmRequest(null, Arrays.asList(ix));
        }
        break;
      }
      case ARRAYREF_INSERT_IMM: 
      case ARRAYREF_DEREF_INSERT_IMM: {
        Var innerArrRef = getOutput(1);
        if (insertRefWaitForClose || closedVars.contains(innerArrRef)) {
          return new MakeImmRequest(null, Arrays.asList(innerArrRef));
        }
        break;
      }
      case ARRAYREF_INSERT_FUTURE: 
      case ARRAYREF_DEREF_INSERT_FUTURE: {
        Var innerArrRef = getOutput(1);
        Var ix = getInput(0).getVar();
        // We will take either the index or the dereferenced array
        List<Var> req2 = mkImmVarList(insertRefWaitForClose, closedVars,
                                      innerArrRef, ix);
        if (req2.size() > 0) {
          return new MakeImmRequest(null, req2);
        }
        break;
      }
      case ARRAY_CREATE_NESTED_FUTURE: {
        // Try to get immediate index
        Var ix = getInput(0).getVar();
        if (waitForClose || closedVars.contains(ix)) {
          return new MakeImmRequest(null, Arrays.asList(ix));
        }
        break;
      }
      case ARRAYREF_CREATE_NESTED_IMM: {
        Var arrRef = getOutput(2);
        if (waitForClose || closedVars.contains(arrRef)) {
          return new MakeImmRequest(null, Arrays.asList(arrRef));
        }
        break;
      }
      case ARRAYREF_CREATE_NESTED_FUTURE: {
        Var arrRef = getOutput(2);
        Var ix = getInput(0).getVar();
        List<Var> req5 = mkImmVarList(waitForClose, closedVars, arrRef, ix);
        if (req5.size() > 0) {
          return new MakeImmRequest(null, req5);
        }
        break;
      }
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
        return new MakeImmRequest(null, Arrays.asList(
                  getInput(0).getVar()));
      default:
        // fall through
      }
      return null;
    }
    
    private List<Var> mkImmVarList(boolean waitForClose,
                                   Set<Var> closedVars, Var... args) {
      ArrayList<Var> req = new ArrayList<Var>(args.length);
      for (Var v: args) {
        if (waitForClose || closedVars.contains(v)) {
          req.add(v);
        }
      }
      return req;
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> out, List<Arg> values) {
      switch (op) {
      case ARRAY_LOOKUP_REF_IMM:
        assert(values.size() == 1);
        // Input should be unchanged
        assert(values.get(0).getVar().equals(getInput(0).getVar()));
        // OUtput switched from ref to value
        Var refOut = getOutput(0);
        Var valOut = Var.createDerefTmp(refOut, 
                                      Alloc.ALIAS);
        Instruction newI = arrayLookupImm(valOut,
            getInput(0).getVar(), getInput(1));
        return new MakeImmChange(valOut, refOut, newI);
      case ARRAY_LOOKUP_FUTURE:
        assert(values.size() == 1);
        return new MakeImmChange(
                arrayLookupRefImm(getOutput(0), 
                          getInput(0).getVar(), values.get(0)));
      case ARRAYREF_LOOKUP_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        // Could be either array ref, index, or both
        if (values.size() == 2) {
          return new MakeImmChange(arrayLookupRefImm(
              getOutput(0), values.get(0).getVar(), 
              values.get(1)));
        } else { 
          Arg v1 = values.get(0);
          Var arrayVar = getInput(0).getVar();
          if (Types.isArrayKeyVal(arrayVar, v1)) {
            // replace index
            return new MakeImmChange(arrayRefLookupImm(getOutput(0), 
                                                      arrayVar, v1));
          } else {
            // replace the array ref
            return new MakeImmChange(
                    arrayLookupFuture(getOutput(0), 
                            v1.getVar(), getInput(1).getVar()));
          }
        }
      case ARRAYREF_LOOKUP_IMM:
        assert(values.size() == 1);
        // Switch from ref to plain array
        return new MakeImmChange(arrayLookupRefImm(
                getOutput(0), values.get(0).getVar(), getInput(1)));
      case ARRAY_INSERT_FUTURE:
        assert(values.size() == 1);
        return new MakeImmChange(
            arrayInsertImm(getOutput(0), values.get(0), getInput(1).getVar()));
      case ARRAY_DEREF_INSERT_FUTURE:
        assert(values.size() == 1);
        return new MakeImmChange(
            arrayDerefInsertImm(getOutput(0), values.get(0),
                                getInput(1).getVar()));
      case ARRAYREF_INSERT_IMM: {
        assert(values.size() == 1);
        Var newOut = values.get(0).getVar();
        // Switch from ref to plain array
        return new MakeImmChange(arrayInsertImm(
            newOut, getInput(0), getInput(1).getVar()));
      }
      case ARRAYREF_DEREF_INSERT_IMM: {
        assert(values.size() == 1);
        Var newOut = values.get(0).getVar();
        // Switch from ref to plain array
        return new MakeImmChange(arrayDerefInsertImm(
            newOut, getInput(0), getInput(1).getVar()));
      }
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_DEREF_INSERT_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        // Could be either array ref, index, or both
        if (values.size() == 2) {
          return new MakeImmChange(arrayInsertImm(
              values.get(0).getVar(),
              values.get(1), getInput(1).getVar()));
        } else {
          Var array = getOutput(1);
          Arg v1 = values.get(0);
          if (Types.isArrayKeyVal(array, v1)) {
            // replace index
            if (op == Opcode.ARRAYREF_INSERT_FUTURE) {
              return new MakeImmChange(arrayRefInsertImm(getOutput(0), 
                              array, v1, getInput(1).getVar()));
            } else {
              assert(op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE);
              return new MakeImmChange(arrayRefDerefInsertImm(getOutput(0), 
                  array, v1, getInput(1).getVar()));
            }
          } else {
            // replace the array ref
            if (op == Opcode.ARRAYREF_INSERT_FUTURE) {
              return new MakeImmChange(arrayInsertFuture(v1.getVar(),
                              getInput(0).getVar(), getInput(1).getVar()));
            } else {
              assert(op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE);
              return new MakeImmChange(arrayDerefInsertFuture(v1.getVar(),
                  getInput(0).getVar(), getInput(1).getVar()));
            }
          }
        }
      case ARRAY_CREATE_NESTED_FUTURE: {
        assert(values.size() == 1);
        Arg ix = values.get(0);
        Var oldResult = getOutput(0);
        Var oldArray = getOutput(1);
        assert(Types.isArrayKeyVal(oldArray, ix)) : oldArray + " " + ix.type();
        // Output type of instruction changed from ref to direct
        // array handle
        assert(Types.isArrayRef(oldResult.type()));
        Var newOut = Var.createDerefTmp(oldResult, Alloc.ALIAS);
        return new MakeImmChange(newOut, oldResult,
            arrayCreateNestedImm(newOut, oldArray, ix));
      }
      case ARRAYREF_CREATE_NESTED_FUTURE: {
        assert(values.size() == 1 || values.size() == 2);
        if (values.size() == 2) {
          Var oldOut = getOutput(0);
          assert(Types.isArrayRef(oldOut.type()));
          Var newOut = Var.createDerefTmp(oldOut, Alloc.ALIAS);
          return new MakeImmChange(newOut, oldOut,
              arrayCreateNestedImm(newOut, values.get(0).getVar(),
                                   values.get(1)));
        } else {
          // We weren't able to switch to the version returning a plain
          // array
          Arg newA = values.get(0);
          Var arr = getOutput(2); // The outer array
          if (Types.isArrayKeyVal(arr, newA)) {
            return new MakeImmChange(
                arrayRefCreateNestedImmIx(getOutput(0), getOutput(1), arr, newA));
          } else {
            assert(Types.isArray(newA.type()));
            // Replacing array ref with array
            assert(Types.isArray(newA.type()));
            return new MakeImmChange(
                arrayCreateNestedComputed(getOutput(0), newA.getVar(),
                                          getInput(0).getVar()));
          }
        }
      }
      case ARRAYREF_CREATE_NESTED_IMM: {
        assert(values.size() == 1);
        Var newArr = values.get(0).getVar();
        Arg ix = getInput(0);
        Var arrResult = getOutput(0);
        assert(Types.isArray(newArr));
        assert(Types.isArrayRef(arrResult.type()));
        Var newOut3 = Var.createDerefTmp(arrResult, Alloc.ALIAS);
        assert(Types.isArrayKeyVal(newArr, ix));
        return new MakeImmChange(newOut3, arrResult,
            arrayCreateNestedImm(newOut3, newArr, getInput(0)));
      }
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE: {
        assert(values.size() == 1);
        UpdateMode mode;
        switch (op) {
        case UPDATE_INCR:
          mode = UpdateMode.INCR;
          break;
        case UPDATE_MIN:
          mode = UpdateMode.MIN;
          break;
        case UPDATE_SCALE:
          mode = UpdateMode.SCALE;
          break;
        default:
          throw new STCRuntimeError("op: " + op +
                                    " ... shouldn't be here");
        }
        return new MakeImmChange(null, null, TurbineOp.updateImm(
            getOutput(0), mode, values.get(0)));
      }
      default:
        // fall through
        break;
      }
      throw new STCRuntimeError("Couldn't make inst "
          + this.toString() + " immediate with vars: "
          + values.toString());
    }

    @Override
    public List<Var> getInitialized() {
      switch (op) {
        case LOAD_REF:
        case COPY_REF:
        case ARRAY_LOOKUP_IMM:
        case ARRAY_CREATE_NESTED_IMM:
        case GET_FILENAME:
        case STRUCT_LOOKUP:
          // Initialises alias
          return getOutput(0).asList();
          

        case INIT_UPDATEABLE_FLOAT:
          // Initializes updateable
          return getOutput(0).asList();
        
        case INIT_LOCAL_OUTPUT_FILE:
        case LOAD_FILE:
          // Initializes output file value
          return getOutput(0).asList();
        default:
          return Var.NONE;
      }
    }

    /**
     * @return list of outputs for which previous value is read
     */
    public List<Var> getReadOutputs(Map<String, Function> fns) {
      switch (op) {
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_CREATE_NESTED_FUTURE:
        // In create_nested instructions the 
        // second array being inserted into is needed
        return Arrays.asList(getOutput(1));
      case ARRAYREF_CREATE_NESTED_IMM:
      case ARRAYREF_CREATE_NESTED_FUTURE:
        // In ref_create_nested instructions the 
        // second array being inserted into is needed
        return Arrays.asList(getOutput(2));
        default:
          return Var.NONE;
      }
    }
    
    public List<Var> getModifiedOutputs() {
      switch (op) {
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAYREF_CREATE_NESTED_IMM:
      case ARRAYREF_CREATE_NESTED_FUTURE:
        // In create_nested instructions only the 
        // first output (the created array) is needed
        return Collections.singletonList(getOutput(0));

      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
        // In the arrayref_insert instructions, the first output
        // is a reference to an outer array that is kept open but not
        // modified
        return Collections.singletonList(getOutput(1));
      default:
          return this.getOutputs();
      }
    }

    /**
     * @return List of outputs that are piecewise assigned
     */
    public List<Var> getPiecewiseAssignedOutputs() {
      switch (op) {
        case ARRAY_INSERT_FUTURE:
        case ARRAY_DEREF_INSERT_FUTURE:
        case ARRAY_INSERT_IMM:
        case ARRAY_DEREF_INSERT_IMM:
        case ARRAYREF_INSERT_FUTURE:
        case ARRAYREF_DEREF_INSERT_FUTURE:
        case ARRAYREF_INSERT_IMM:
        case ARRAYREF_DEREF_INSERT_IMM:
          // All outputs are piecewise assigned
          return getOutputs();
        case ARRAY_CREATE_NESTED_FUTURE:
        case ARRAY_CREATE_NESTED_IMM:
        case ARRAYREF_CREATE_NESTED_FUTURE:
        case ARRAYREF_CREATE_NESTED_IMM: {
          // All arrays except the newly created array; 
          List<Var> outputs = getOutputs();
          return outputs.subList(1, outputs.size());
        }
        case STRUCT_INSERT:
          return getOutputs();
        case SET_FILENAME_VAL:
          // File's filename might be modified
          return Collections.singletonList(getOutput(0));
        default:
          return Var.NONE;
      }
    }

    @Override
    public List<Var> getBlockingInputs() {
      if (getMode() == TaskMode.SYNC) {
        return Var.NONE;
      }
      
      // If async, assume that all scalar input vars are blocked on
      ArrayList<Var> blocksOn = new ArrayList<Var>();
      for (Arg oa: getInputs()) {
        if (oa.kind == ArgKind.VAR) {
          Var v = oa.getVar();
          Type t = v.type();
          if (Types.isScalarFuture(t)
              || Types.isRef(t)) {
            blocksOn.add(v);
          } else if (Types.isScalarValue(t) ||
              Types.isStruct(t) || Types.isArray(t) ||
              Types.isScalarUpdateable(t)) {
            // No turbine ops block on these types
          } else {
            throw new STCRuntimeError("Don't handle type "
                + t.toString() + " here");
          }
        }
      }
      return blocksOn;
    }


    @Override
    public TaskMode getMode() {
      switch (op) {
      case STORE_INT:
      case STORE_BOOL:
      case STORE_VOID:
      case STORE_FLOAT:
      case STORE_STRING:
      case STORE_BLOB:
      case STORE_FILE:
      case LOAD_INT:
      case LOAD_BOOL:
      case LOAD_VOID:
      case LOAD_FLOAT:
      case LOAD_STRING:
      case LOAD_BLOB:
      case LOAD_FILE:
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
      case UPDATE_INCR_IMM:
      case UPDATE_MIN_IMM:
      case UPDATE_SCALE_IMM:
      case INIT_UPDATEABLE_FLOAT:
      case LATEST_VALUE:
      case ARRAY_INSERT_IMM:
      case STRUCT_INSERT:
      case STRUCT_LOOKUP:
      case ARRAY_CREATE_NESTED_IMM:
      case STORE_REF:
      case LOAD_REF:
      case FREE_BLOB:
      case DECR_LOCAL_FILE_REF:
      case GET_FILENAME:
      case GET_LOCAL_FILENAME:
      case IS_MAPPED:
      case COPY_FILE_CONTENTS:
      case ARRAY_LOOKUP_IMM:
      case COPY_REF:
      case CHOOSE_TMP_FILENAME:
      case SET_FILENAME_VAL:
      case INIT_LOCAL_OUTPUT_FILE:
      case ARRAY_BUILD:
        return TaskMode.SYNC;
      
      case ARRAY_DEREF_INSERT_IMM:
      case ARRAY_INSERT_FUTURE:
      case ARRAY_DEREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_DEREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
      case ARRAYREF_DEREF_INSERT_IMM:
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_IMM:
      case ARRAY_LOOKUP_REF_IMM:
      case DEREF_INT:
      case DEREF_BOOL:
      case DEREF_FLOAT:
      case DEREF_STRING:
      case DEREF_BLOB:
      case DEREF_FILE:
      case STRUCTREF_LOOKUP:
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAYREF_CREATE_NESTED_IMM:
        return TaskMode.LOCAL;
      default:
        throw new STCRuntimeError("Need to add opcode " + op.toString()
            + " to getMode");
      }
    }

    @Override
    public List<ResultVal> getResults(CVMap existing) {
      switch(op) {
        case LOAD_BOOL:
        case LOAD_FLOAT:
        case LOAD_INT:
        case LOAD_REF:
        case LOAD_STRING: 
        case LOAD_BLOB: 
        case LOAD_VOID: 
        case LOAD_FILE: {
          // retrieve* is invertible
          Arg src = getInput(0);
          Var dst = getOutput(0);
          if (Types.isScalarUpdateable(src.getVar().type())) {
            return null;
          }

          boolean outIsClosed;
          EquivalenceType equiv;
          if (op == Opcode.LOAD_REF) {
            outIsClosed = false;
            equiv = EquivalenceType.REFERENCE;
          } else {
            outIsClosed = true;
            equiv = EquivalenceType.VALUE;
          }
          
          List<ResultVal> result = new ArrayList<ResultVal>();
          
          ResultVal retrieve = vanillaResult(outIsClosed, equiv);
          result.add(retrieve);
          
          Opcode cvop = assignOpcode(src.futureType());
          if (cvop == null) {
            throw new STCRuntimeError("Need assign op for "
                + src.getVar());
          }
          ResultVal assign = ResultVal.buildResult(cvop,
                    Arrays.asList(dst.asArg()), src, outIsClosed);
          result.add(assign);
          
          
          Opcode derefOp = derefOpCode(src.futureType());
          if (derefOp != null) {
            ResultVal deref = ResultVal.buildResult(derefOp, 
                                      Arrays.asList(src), dst.asArg(), false);
            result.add(deref);
            // Add any new cvs that result from dereferencing the variable
            for (ComputedValue refCV: existing.getVarContents(src.getVar())) {
              result.addAll(ResultVal.createDereferencedCVs(refCV, dst));
            }
          }
          return result;
        }
        case STORE_REF:
        case STORE_BOOL:
        case STORE_FLOAT:
        case STORE_INT:
        case STORE_STRING: 
        case STORE_BLOB: 
        case STORE_VOID:
        case STORE_FILE: {

          // add assign so we can avoid recreating future 
          // (true b/c this instruction closes val immediately)
          ResultVal assign = vanillaResult(true);
          // add retrieve so we can avoid retrieving later
          Arg dst = getOutput(0).asArg();
          Arg src = getInput(0);
          Opcode cvop = retrieveOpcode(dst.futureType());
          assert(cvop != null);

          
          EquivalenceType equiv;
          if (op == Opcode.STORE_REF) {
            equiv = EquivalenceType.REFERENCE;
          } else {
            equiv = EquivalenceType.VALUE;
          }
          ResultVal retrieve = ResultVal.buildResult(cvop,
              Arrays.asList(dst), src, false, equiv);
          
          if (op == Opcode.STORE_REF) {
            Opcode derefOp = derefOpCode(dst.futureType());
            if (derefOp != null) {
              ResultVal deref = 
                   ResultVal.buildResult(derefOp, Arrays.asList(dst),
                               src, false, EquivalenceType.REFERENCE);
              return Arrays.asList(retrieve, assign, deref);
            }
          }
          return Arrays.asList(retrieve, assign);
        }
        case IS_MAPPED: {
          ResultVal vanilla = vanillaResult(true);
          assert(vanilla != null);
          Var fileVar = getInput(0).getVar();
          if (fileVar.isMapped() == Ternary.MAYBE) {
            return vanilla.asList();
          } else {
            // We know the value already, so check it's a constant
            Arg result = Arg.createBoolLit(fileVar.isMapped() == Ternary.TRUE);
            return Arrays.asList(vanilla,
                  ResultVal.makeCopy(getOutput(0), result));
          }
        }
        case GET_FILENAME: {
          List<ResultVal> res = new ArrayList<ResultVal>();
          Arg filename = getOutput(0).asArg();
          Arg file = getInput(0);
          res.add(filenameCV(filename, file.getVar()));
          
          // Check to see if value of filename is in local value
          Arg filenameVal = existing.getLocation(filenameValCV(file, null).value());
          if (filenameVal != null) {
            // We know that if we fetch from the output future of this instruction,
            // we'll get the previously stored filename
            res.add(ResultVal.buildResult(Opcode.LOAD_STRING,
                                      filename, filenameVal, true));
          }
          return res;
        }
        case GET_LOCAL_FILENAME: {
          return filenameLocalCV(getOutput(0).asArg(),
                                 getInput(0).getVar()).asList();
        }
        case SET_FILENAME_VAL: {
          Arg file = getOutput(0).asArg();
          Arg val = getInput(0);
          return filenameValCV(file, val).asList();
        }
        case DEREF_BLOB:
        case DEREF_BOOL:
        case DEREF_FLOAT:
        case DEREF_INT:
        case DEREF_STRING: {
          return vanillaResult(false).asList();
        }
        case DEREF_FILE: {
          if (getOutput(0).isMapped() == Ternary.FALSE) {
            return vanillaResult(false).asList();
          } else {
            // Can't use potentially mapped files interchangeably
            return null;
          }
        }
        
        case STRUCT_INSERT: {
          // Lookup
          ResultVal lookup = ResultVal.buildResult(Opcode.STRUCT_LOOKUP,
              Arrays.asList(getOutput(0).asArg(), getInput(0)),
              getInput(1), false, EquivalenceType.REFERENCE);
          return lookup.asList(); 
        }
        case STRUCT_LOOKUP: {
          // don't know if its closed
          ResultVal lookup = ResultVal.buildResult(Opcode.STRUCT_LOOKUP,
              Arrays.asList(getInput(0), getInput(1)), getOutput(0).asArg(),
              false, EquivalenceType.REFERENCE);
          return lookup.asList(); 
        }
        case ARRAY_INSERT_IMM:
        case ARRAY_DEREF_INSERT_IMM:
        case ARRAY_INSERT_FUTURE:
        case ARRAY_DEREF_INSERT_FUTURE:
        case ARRAYREF_INSERT_IMM:
        case ARRAYREF_DEREF_INSERT_IMM: 
        case ARRAYREF_INSERT_FUTURE:
        case ARRAYREF_DEREF_INSERT_FUTURE: {
          // STORE <out array> <in index> <in var>
          // STORE  <in outer array> <out array> <in index> <in var>
          Var arr;
          if (op == Opcode.ARRAYREF_INSERT_FUTURE ||
              op == Opcode.ARRAYREF_INSERT_IMM ||
              op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE ||
              op == Opcode.ARRAYREF_DEREF_INSERT_IMM) {
            arr = getOutput(1);
          } else {
            arr = getOutput(0);
          }
          Arg ix = getInput(0);
          Var member = getInput(1).getVar();
          boolean insertingRef = (op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE ||
                                  op == Opcode.ARRAYREF_DEREF_INSERT_IMM ||
                                  op == Opcode.ARRAY_DEREF_INSERT_FUTURE ||
                                  op == Opcode.ARRAY_DEREF_INSERT_IMM);
          return Arrays.asList(makeArrayCV(arr, ix, member, insertingRef));
        }
        case ARRAY_BUILD: {
          Var arr = getOutput(0);
          List<ResultVal> res = new ArrayList<ResultVal>();
          // Computed value for whole array
          res.add(ResultVal.buildResult(op, getInputs(), arr.asArg(), true));
          // For individual array elements
          int arrSize = getInputs().size();
          for (int i = 0; i < arrSize; i++) {
            res.add(makeArrayCV(arr, Arg.createIntLit(i),
                                                getInput(i).getVar(), false));
          }
          
          res.add(CommonFunctionCall.makeArraySizeCV(arr,
                      Arg.createIntLit(arrSize), false));
          return res;
        }
        case ARRAY_LOOKUP_IMM:
        case ARRAY_LOOKUP_REF_IMM:
        case ARRAY_LOOKUP_FUTURE:
        case ARRAYREF_LOOKUP_FUTURE:
        case ARRAYREF_LOOKUP_IMM: {
          // LOAD <out var> <in array> <in index>
          Var arr = getInput(0).getVar();
          Arg ix = getInput(1);
          Var contents = getOutput(0);
          
  
          if (op == Opcode.ARRAY_LOOKUP_IMM) {
            // This just retrieves the item immediately
            return Arrays.asList(makeArrayCV(arr, ix, contents, false));
          } else {
            assert (Types.isAssignableRefTo(contents.type(), 
                Types.arrayMemberType(arr.type())));
            ResultVal refCV = makeArrayCV(arr, ix, contents, true);
            Arg prev = existing.getLocation(ComputedValue.arrayCV(arr, ix));
            if (prev != null) {
              /* All these array loads give back a reference, but if a value
               * was previously inserted at this index, then we can 
               * short-circuit this as we know what is in the reference */
              ResultVal retrieveCV = ResultVal.buildResult(retrieveOpcode(
                  contents.type()), contents.asArg(), prev, false);
              Opcode derefOp = derefOpCode(contents.type());
              if (derefOp == null) {
                return Arrays.asList(retrieveCV, refCV);
              } else {
                ResultVal derefCV = ResultVal.buildResult(derefOp,
                                                contents.asArg(), prev, false);
                return Arrays.asList(retrieveCV, refCV, derefCV);
              }
            } else {
              return Arrays.asList(refCV);
            }
          }
        }
        case ARRAY_CREATE_NESTED_FUTURE:
        case ARRAY_CREATE_NESTED_IMM:
        case ARRAYREF_CREATE_NESTED_FUTURE:
        case ARRAYREF_CREATE_NESTED_IMM: {
          // CREATE_NESTED <out inner array> <in array> <in index>
          // OR
          // CREATE_NESTED <out inner array> <outer arr> <in array> <in index>
          Var nestedArr = getOutput(0);
          Var arr;
          if (op == Opcode.ARRAYREF_CREATE_NESTED_FUTURE ||
              op == Opcode.ARRAYREF_CREATE_NESTED_IMM) {
            arr = getOutput(2);
          } else {
            arr = getOutput(1);
          }
          Arg ix = getInput(0);
          List<ResultVal> res = new ArrayList<ResultVal>();
          
          boolean returnsRef =  op != Opcode.ARRAY_CREATE_NESTED_IMM;
          // Mark as not substitutable since this op may have
          // side-effect of creating array
          res.add(ResultVal.makeArrayResult(arr, ix, nestedArr,
                                                returnsRef));
          res.add(ResultVal.makeCreateNestedResult(arr, ix, nestedArr,
              returnsRef));
          
          if (op == Opcode.ARRAY_CREATE_NESTED_IMM) {
            // No references involved, the instruction returns the nested
            // array directly
          } else {
            Arg prev = existing.getLocation(ComputedValue.arrayCV(arr, ix));
            assert (Types.isRefTo(nestedArr.type(), 
                        Types.arrayMemberType(arr.type())));
            if (prev != null) {
              // See if we know the value of this reference already
              ResultVal derefCV = ResultVal.buildResult(
                  retrieveOpcode(nestedArr.type()),
                  Arrays.asList(nestedArr.asArg()), prev, false);
              res.add(derefCV);
            }
          }
          return res;
        }
        case COPY_REF: {
          List<ResultVal> res = new ArrayList<ResultVal>();
          res.add(ResultVal.makeAlias(getOutput(0), getInput(0)));
          res.addAll(ValueTracker.makeCopiedRVs(existing, getOutput(0), getInput(0)));
          return res;
        }
        default:
          return null;
      }
    }
    
    private static ResultVal makeArrayCV(Var arr, Arg ix, Var member,
        boolean insertingRef) {
      return ResultVal.makeArrayResult(arr, ix, member, insertingRef, true);
    }

    private ResultVal vanillaResult(boolean closed) {
      return vanillaResult(closed, EquivalenceType.VALUE);
    }

    /**
     * Create the "standard" computed value
     * assume 1 ouput arg
     * @return
     */
    private ResultVal vanillaResult(boolean closed, EquivalenceType equiv) {
      assert(outputs.size() == 1);
      return ResultVal.buildResult(op, inputs, outputs.get(0).asArg(),
          closed, equiv);
    }

    @Override
    public List<Var> getClosedOutputs() {
      if (op == Opcode.ARRAY_BUILD) {
        // Output array should be closed
        return Collections.singletonList(getOutput(0));
      } else if (op == Opcode.STORE_REF) {
        return Collections.singletonList(getOutput(0));
      }
      return super.getClosedOutputs();
    }

    @Override
    public Instruction clone() {
      return new TurbineOp(op, new ArrayList<Var>(outputs),
                               new ArrayList<Arg>(inputs));
    }

    @Override
    public Pair<List<Var>, List<Var>> getIncrVars() {
      switch (op) {
        case STORE_REF:
          return Pair.create(getInput(0).getVar().asList(), Var.NONE);
        case ARRAY_BUILD: {
          List<Var> readIncr = new ArrayList<Var>(getInputs().size());
          for (Arg elem: getInputs()) {
            // Container gets reference
            if (RefCounting.hasReadRefCount(elem.getVar())) {
              readIncr.add(elem.getVar());
            }
          }
          Var arr = getOutput(0);
          return Pair.create(readIncr, Arrays.asList(arr));
        }
        case DEREF_BLOB:
        case DEREF_BOOL:
        case DEREF_FILE:
        case DEREF_FLOAT:
        case DEREF_INT:
        case DEREF_STRING: {
          // Increment refcount of ref var
          return Pair.create(Arrays.asList(getInput(0).getVar()),
                             Var.NONE);
        }
        case ARRAYREF_LOOKUP_FUTURE:
        case ARRAY_LOOKUP_FUTURE: {
          // Array and index
          return Pair.create(
                  Arrays.asList(getInput(0).getVar(), getInput(1).getVar()),
                  Var.NONE);
        }
        case ARRAYREF_LOOKUP_IMM:
        case ARRAY_LOOKUP_REF_IMM: {
          // Array only
          return Pair.create(
                    Arrays.asList(getInput(0).getVar()),
                    Var.NONE);
        }
        case ARRAY_INSERT_IMM: {
          Var mem = getInput(1).getVar();
          // Increment reference to member
          return Pair.create(Arrays.asList(mem), Var.NONE);
        }
        case ARRAY_DEREF_INSERT_IMM: {
          // Increment reference to member ref
          // Increment writers count on array
          Var mem = getInput(1).getVar();
          return Pair.create(Arrays.asList(mem),
                             Arrays.asList(getOutput(0)));
        }
        case ARRAY_INSERT_FUTURE: 
        case ARRAY_DEREF_INSERT_FUTURE: {
          // Increment reference to member/member ref and index future
          // Increment writers count on array
          return Pair.create(Arrays.asList(
                  getInput(0).getVar(), getInput(1).getVar()),
                  Arrays.asList(getOutput(0)));
        }
        case ARRAYREF_INSERT_IMM:
        case ARRAYREF_DEREF_INSERT_IMM:
        case ARRAYREF_INSERT_FUTURE: 
        case ARRAYREF_DEREF_INSERT_FUTURE: {
          Arg ix = getInput(0);
          Var mem = getInput(1).getVar();
          Var outerArr = getOutput(0);
          Var arrayRef = getOutput(1);
          List<Var> readers = new ArrayList<Var>(3);
          readers.add(mem);
          readers.add(arrayRef);
          if (op == Opcode.ARRAYREF_INSERT_FUTURE ||
              op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE) {
            readers.add(ix.getVar());
          } else {
            assert(op == Opcode.ARRAYREF_INSERT_IMM ||
                   op == Opcode.ARRAYREF_DEREF_INSERT_IMM);
          }
          // Maintain slots on outer array
          return Pair.create(readers,
                  Arrays.asList(outerArr));
        }
        case ARRAY_CREATE_NESTED_FUTURE: {
          Var srcArray = getOutput(1);
          Var ix = getInput(0).getVar();
          return Pair.create(ix.asList(), srcArray.asList());
        }
        case STRUCTREF_LOOKUP: {
          return Pair.create(Arrays.asList(getInput(0).getVar()),
                             Var.NONE);
        }
        case ARRAYREF_CREATE_NESTED_IMM:
        case ARRAYREF_CREATE_NESTED_FUTURE: {
          Var outerArr = getOutput(1);
          assert(Types.isArray(outerArr.type())): outerArr + " " + this;
          assert(Types.isArray(outerArr.type().memberType()));
          Var arr = getOutput(2);
          Arg ixArg = getInput(0);
          List<Var> readVars;
          if (op == Opcode.ARRAYREF_CREATE_NESTED_IMM) {
            readVars = Arrays.asList(arr);
          } else {
            assert(op == Opcode.ARRAYREF_CREATE_NESTED_FUTURE);
            readVars = Arrays.asList(arr, ixArg.getVar());
          }
          return Pair.create(readVars,
                  Arrays.asList(outerArr));
        }
        case STRUCT_INSERT:
          // Do nothing: reference count tracker can track variables
          // across struct boundaries
          return super.getIncrVars();
        case COPY_REF: {
          return Pair.create(getInput(0).getVar().asList(),
                             getInput(0).getVar().asList());
        }
        case UPDATE_INCR:
        case UPDATE_MIN:
        case UPDATE_SCALE:
          // Consumes a read refcount for the input argument and
          // write refcount for updated variable
          return Pair.create(getInput(0).getVar().asList(),
                             getOutput(0).asList());
        default:
          // Return default
          return super.getIncrVars();
      }
    }
    
    @Override
    public List<Var> tryPiggyback(Counters<Var> increments, RefCountType type) {
      switch (op) {
        case LOAD_BLOB:
        case LOAD_BOOL:
        case LOAD_FILE:
        case LOAD_FLOAT:
        case LOAD_INT:
        case LOAD_REF:
        case LOAD_STRING:
        case LOAD_VOID: {
          Var inVar = getInput(0).getVar();
          if (type == RefCountType.READERS) {
            long amt = increments.getCount(inVar);
            if (amt < 0) {
              assert(getInputs().size() == 1);
              // Add extra arg
              this.inputs = Arrays.asList(getInput(0),
                                        Arg.createIntLit(amt * -1));
              return inVar.asList();
            }
          }
          break;
        }
        case ARRAY_INSERT_IMM:
        case ARRAY_DEREF_INSERT_IMM: 
        case ARRAY_INSERT_FUTURE: 
        case ARRAY_DEREF_INSERT_FUTURE: {
          Var arr = getOutput(0);
          if (type == RefCountType.WRITERS) {
            long amt = increments.getCount(arr);
            if (amt < 0) {
              assert(getInputs().size() == 2);
              // All except the fully immediate version decrement by 1 by default
              int defaultDecr = op == Opcode.ARRAY_INSERT_IMM ? 0 : 1;
              Arg decrArg = Arg.createIntLit(amt * -1 + defaultDecr);
              this.inputs = Arrays.asList(getInput(0), getInput(1), decrArg);
              return arr.asList();
            }
          }
          break;
        }
        case ARRAY_CREATE_NESTED_IMM: {
          // Instruction can give additional refcounts back
          Var nestedArr = getOutput(0);
          long amt = increments.getCount(nestedArr);
          if (amt > 0) {
            assert(getInputs().size() == 3);
            // Which argument is increment
            int inputPos = (type == RefCountType.READERS ? 1 : 2);

            Arg oldAmt = getInput(inputPos);
            if (oldAmt.isIntVal()) {
              setInput(inputPos, Arg.createIntLit(oldAmt.getIntLit() + amt));
              return nestedArr.asList();
            }
          }
          break;
        }
        default:
          // Do nothing
      }

      // Fall through to here if can do nothing
      return Var.NONE;
    }
    
    public Pair<Var, Var> getComponentAlias() {
      switch (op) {
        case ARRAY_CREATE_NESTED_IMM:
        case ARRAY_CREATE_NESTED_FUTURE:
          // From inner array to immediately enclosing
          return Pair.create(getOutput(0), getOutput(1));
        case ARRAYREF_CREATE_NESTED_IMM:
        case ARRAYREF_CREATE_NESTED_FUTURE:
          // From inner array to immediately enclosing
          return Pair.create(getOutput(0), getOutput(2));
        case LOAD_REF:
          // If reference was a part of something, modifying the
          // dereferenced object will modify the whole
          return Pair.create(getOutput(0), getInput(0).getVar());
        case COPY_REF:
          return Pair.create(getOutput(0), getInput(0).getVar());
        case STORE_REF:
          // Sometimes a reference is filled in
          return Pair.create(getOutput(0), getInput(0).getVar());
        case STRUCT_LOOKUP:
        case STRUCTREF_LOOKUP:
          // Output is alias for part of struct
          return Pair.create(getOutput(0), getInput(0).getVar());
        default:
          return null;
      }
    }

    public boolean isIdempotent() {
      switch (op) {
        case ARRAY_CREATE_NESTED_FUTURE:
        case ARRAY_CREATE_NESTED_IMM:
        case ARRAYREF_CREATE_NESTED_FUTURE:
        case ARRAYREF_CREATE_NESTED_IMM:
          return true;
        default:
          return false;
      }
    }
  }
  
  /**
   * Instruction class specifically for reference counting operations with
   * defaults derived from TurbineOp
   */
  public static class RefCountOp extends TurbineOp {
    
    private RefCountOp(Var target, boolean increment, RefCountType type, Arg amount) {
      super(getRefCountOp(increment, type), Var.NONE,
            Arrays.asList(target.asArg(), amount));
    }
    
    public static RefCountOp decrWriters(Var target, Arg amount) {
      return new RefCountOp(target, false, RefCountType.WRITERS, amount);
    }
    
    public static RefCountOp incrWriters(Var target, Arg amount) {
      return new RefCountOp(target, true, RefCountType.WRITERS, amount);
    }
    
    public static RefCountOp decrRef(Var target, Arg amount) {
      return new RefCountOp(target, false, RefCountType.READERS, amount);
    }
    
    public static RefCountOp incrRef(Var target, Arg amount) {
      return new RefCountOp(target, false, RefCountType.READERS, amount);
    }
    
    public static RefCountOp decrRef(RefCountType rcType, Var v, Arg amount) {
      return new RefCountOp(v, false, rcType, amount);
    }
    
    public static RefCountOp incrRef(RefCountType rcType, Var v, Arg amount) {
      return new RefCountOp(v, true, rcType, amount);
    }
    
    public static RefCountType getRCType(Opcode op) {
      assert(isRefcountOp(op));
      if (op == Opcode.INCR_REF || op == Opcode.DECR_REF) {
        return RefCountType.READERS;
      } else {
        assert(op == Opcode.INCR_WRITERS || op == Opcode.DECR_WRITERS);
        return RefCountType.WRITERS;
      }
    }

    public static Var getRCTarget(Instruction refcountOp) {
      assert(isRefcountOp(refcountOp.op));
      return refcountOp.getInput(0).getVar();
    }
    
    public static Arg getRCAmount(Instruction refcountOp) {
      assert(isRefcountOp(refcountOp.op));
      return refcountOp.getInput(1);
    }
    
    
    
    private static Opcode getRefCountOp(boolean increment, RefCountType type) {
      if (type == RefCountType.READERS) {
        if (increment) {
          return Opcode.INCR_REF;
        } else {
          return Opcode.DECR_REF;
        }
      } else {
        assert(type == RefCountType.WRITERS);
        if (increment) {
          return Opcode.INCR_WRITERS;
        } else {
          return Opcode.DECR_WRITERS;
        }
      }
    }

    public static boolean isIncrement(Opcode op) {
      return (op == Opcode.INCR_REF || op == Opcode.INCR_WRITERS);
    }
    
    public static boolean isDecrement(Opcode op) {
      return (op == Opcode.DECR_REF || op == Opcode.DECR_WRITERS);
    }

    public static boolean isRefcountOp(Opcode op) {
      return isIncrement(op) || isDecrement(op);
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      switch (op) {
        case DECR_WRITERS:
          gen.decrWriters(getRCTarget(this), getRCAmount(this));
          break;
        case INCR_WRITERS:
          gen.incrWriters(getRCTarget(this), getRCAmount(this));
          break;
        case DECR_REF:
          gen.decrRef(getRCTarget(this), getRCAmount(this));
          break;
        case INCR_REF:
          gen.incrRef(getRCTarget(this), getRCAmount(this));
          break;
        default:
          throw new STCRuntimeError("Unknown op type: " + op);
      }
    }

    @Override
    public TaskMode getMode() {
      // Executes right away
      return TaskMode.SYNC;
    }
    
    @Override
    public boolean hasSideEffects() {
      // Model refcount change as side-effect
      return true;
    }

    @Override
    public Instruction clone() {
      return new RefCountOp(getRCTarget(this), isIncrement(this.op),
                            getRCType(this.op), getRCAmount(this));
    }
  }

  // Maximum number of array element CVs to insert
  public static final long MAX_ARRAY_ELEM_CVS = 128L;
  
  public static abstract class CommonFunctionCall extends Instruction {
    protected final String functionName;
    
    public CommonFunctionCall(Opcode op, String functionName) {
      super(op);
      this.functionName = functionName;
    }

    public String functionName() {
      return functionName;
    }
    
    @Override
    public String shortOpName() {
      return op.toString().toLowerCase() + "-" + functionName;
    }
    
    private boolean isCopyFunction() {
      if (ForeignFunctions.isCopyFunction(functionName)) {
        return true;
      } else if (ForeignFunctions.isMinMaxFunction(functionName)
              && getInput(0).equals(getInput(1))) {
        return true;
      }
      return false;
    }
    
    @Override
    public List<ResultVal> getResults(CVMap existing) {
      if (ForeignFunctions.isPure(functionName)) {
        if (!this.writesMappedVar() && isCopyFunction()) {
          // Handle copy as a special case
          return ResultVal.makeCopy(getOutput(0),
                                         getInput(0)).asList();
        } else {
          List<ResultVal> res = new ArrayList<ResultVal>();
          for (int output = 0; output < getOutputs().size(); output++) {
            boolean outputClosed = false;// safe assumption
            String canonicalFunctionName = this.functionName;
            List<Arg> in = new ArrayList<Arg>(getInputs());
            if (ForeignFunctions.isCommutative(this.functionName)) {
              // put in canonical order
              Collections.sort(in);
            }
            
            res.add(ResultVal.buildResult(this.op, 
                canonicalFunctionName, output, in, 
                getOutput(output).asArg(), outputClosed));
          }
          addSpecialCVs(res);
          return res;
        }
      }
      return null;
    }

    /**
     * Add specific CVs for special operations
     * @param res
     */
    private void addSpecialCVs(List<ResultVal> cvs) {
      if (isImpl(SpecialFunction.INPUT_FILE) ||
          isImpl(SpecialFunction.UNCACHED_INPUT_FILE)) {
        if (op == Opcode.CALL_FOREIGN) {
          cvs.add(filenameCV(getInput(0), getOutput(0)));
        } else if (op == Opcode.CALL_FOREIGN_LOCAL){
          cvs.add(filenameLocalCV(getInput(0), getOutput(0)));
        }
      } else if (op == Opcode.CALL_FOREIGN_LOCAL &&
          (isImpl(SpecialFunction.RANGE) ||
           isImpl(SpecialFunction.RANGE_STEP))) {
        addRangeCVs(cvs);
      } else if (isImpl(SpecialFunction.SIZE)) {
          cvs.add(makeArraySizeCV());
      }
    }

    /**
     * @param inputFile
     * @return true if this instruction calls a given special function
     */
    public boolean isImpl(SpecialFunction special) {
      return ForeignFunctions.isSpecialImpl(special, this.functionName);
    }

    private void addRangeCVs(List<ResultVal> cvs) {
      boolean allValues = true;
      long start = 0, end = 0, step = 1; 
      
      if (getInput(0).isIntVal()) {
        start = getInput(0).getIntLit();
      } else {
        allValues = false;
      }
      if (getInput(1).isIntVal()) {
        end = getInput(1).getIntLit();
      } else {
        allValues = false;
      }
      if (isImpl(SpecialFunction.RANGE_STEP)) {
        if (getInput(2).isIntVal()) {
          step = getInput(2).getIntLit();
        } else {
          allValues = false;
        }
      }
      if (allValues) {
        // We can work out array contents 
        long arrSize = Math.max(0, (end - start) / step + 1);
        Var arr = getOutput(0);
        cvs.add(makeArraySizeCV(arr, Arg.createIntLit(arrSize),
                                           false));
        // TODO: somehow add array elements?
      }
    }

    private ResultVal makeArraySizeCV() {
      boolean isFuture;
      if (Types.isString(getOutput(0))) {
        isFuture = true;
      } else {
        assert(getOutput(0).type().assignableTo(Types.V_INT));
        isFuture = false;
      }
      return makeArraySizeCV(getInput(0).getVar(), getOutput(0).asArg(),
                             isFuture);
    }

    static ResultVal makeArraySizeCV(Var arr, Arg size, boolean future) {
      assert(Types.isArray(arr.type()));
      assert(size.isImmediateInt());
      String subop = future ? ComputedValue.ARRAY_SIZE_FUTURE :
                              ComputedValue.ARRAY_SIZE_VAL;
      return ResultVal.buildResult(Opcode.FAKE, subop,
                                   arr.asArg(), size, true);
    }
    
  }
  
  public static class FunctionCall extends CommonFunctionCall {
    private final List<Var> outputs;
    private final List<Arg> inputs;
    private final List<Boolean> closedInputs; // which inputs are closed
    private final TaskProps props;
  
    private FunctionCall(Opcode op, String functionName,
        List<Arg> inputs, List<Var> outputs, TaskProps props) {
      super(op, functionName);
      if (op != Opcode.CALL_FOREIGN && op != Opcode.CALL_CONTROL &&
          op != Opcode.CALL_SYNC && op != Opcode.CALL_LOCAL &&
          op != Opcode.CALL_LOCAL_CONTROL) {
        throw new STCRuntimeError("Tried to create function call"
            + " instruction with invalid opcode");
      }
      this.props = props;
      this.outputs = new ArrayList<Var>(outputs);
      this.inputs = new ArrayList<Arg>(inputs);
      this.closedInputs = new ArrayList<Boolean>(inputs.size());
      for (int i = 0; i < inputs.size(); i++) {
        this.closedInputs.add(false);
      }
      
      for(Var v: outputs) {
        assert(v != null);
      }
      
      for(Arg v: inputs) {
        assert(v != null);
      }
    }
    
    public static FunctionCall createFunctionCall(
        String functionName, List<Arg> inputs, List<Var> outputs,
        TaskMode mode, TaskProps props) {
      Opcode op;
      if (mode == TaskMode.SYNC) {
        op = Opcode.CALL_SYNC;
      } else if (mode == TaskMode.CONTROL) {
        op = Opcode.CALL_CONTROL;
      } else if (mode == TaskMode.LOCAL) {
        op = Opcode.CALL_LOCAL;
      } else if (mode == TaskMode.LOCAL_CONTROL) {
        op = Opcode.CALL_LOCAL_CONTROL;
      } else {
        throw new STCRuntimeError("Task mode " + mode + " not yet supported");
      }
      return new FunctionCall(op, functionName, inputs, outputs, props);
    }
  
    public static FunctionCall createBuiltinCall(
        String functionName, List<Arg> inputs, List<Var> outputs,
        TaskProps props) {
      return new FunctionCall(Opcode.CALL_FOREIGN, functionName,
          inputs, outputs, props);
    }
  
    @Override
    public String toString() {
      String result = op.toString().toLowerCase() + " " + functionName;
      result += " [";
      for (Var v: outputs) {
        result += " " + v.name();
      }
      result += " ] [";
      for (Arg v: inputs) {
        result += " " + v.toString();
      }
      result += " ]";
      
      result += ICUtil.prettyPrintProps(props);
      
      result += " closed=" + closedInputs;
      return result;
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      switch(this.op) {
      case CALL_FOREIGN:
        gen.builtinFunctionCall(functionName, inputs, outputs, props);
        break;
      case CALL_SYNC:
      case CALL_CONTROL:
      case CALL_LOCAL:
      case CALL_LOCAL_CONTROL:
        TaskMode mode;
        if (op == Opcode.CALL_CONTROL) {
          mode = TaskMode.CONTROL;
        } else if (op == Opcode.CALL_SYNC) {
          mode = TaskMode.SYNC;
        } else if (op == Opcode.CALL_LOCAL) {
          mode = TaskMode.LOCAL;
        } else if (op == Opcode.CALL_LOCAL_CONTROL) {
          mode = TaskMode.LOCAL;
        } else {
          throw new STCRuntimeError("Unexpected op " + op);
        }
        List<Boolean> blocking = info.getBlockingInputVector(functionName);
        assert(blocking != null && blocking.size() == inputs.size()) :
          this + "; blocking: " + blocking;
        List<Boolean> needToBlock = new ArrayList<Boolean>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
          needToBlock.add(blocking.get(i) && (!this.closedInputs.get(i)));
        }
                           
        gen.functionCall(functionName, inputs, outputs, needToBlock,
                                            mode, props);
        break;
      default:
        throw new STCRuntimeError("Huh?");
      }
    }
  
    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      if (mode == RenameMode.REPLACE_VAR || mode == RenameMode.REFERENCE) {
        ICUtil.replaceVarsInList(renames, outputs, false);
      }
      ICUtil.replaceArgsInList(renames, inputs, false);
      ICUtil.replaceArgValsInMap(renames, props);
    }
  
    public String getFunctionName() {
      return this.functionName;
    }
  
    /**
     * @return function input arguments
     */
    public List<Arg> getFunctionInputs() {
      return Collections.unmodifiableList(inputs);
    }


    public Arg getFunctionInput(int i) {
      return inputs.get(i);
    }
    
    @Override
    public List<Arg> getInputs() {
      List<Arg> inputVars = new ArrayList<Arg>(inputs);
      inputVars.addAll(props.values());
      return inputVars;
    }
  
    private List<Var> varInputs(boolean noValues) {
      List<Var> varInputs = new ArrayList<Var>();
      for (Arg input: inputs) {
        if (input.isVar()) {
          if (!noValues || !Types.isScalarValue(input.type())) {
            varInputs.add(input.getVar());
          }
        }
      }
      return varInputs;
    }

    @Override
    public TaskProps getTaskProps() {
      // Return null if not found
      return props;
    }

    @Override
    public List<Var> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    @Override
    public List<Var> getReadOutputs(Map<String, Function> fns) {
      switch (op) {
        case CALL_FOREIGN: {
          List<Var> res = new ArrayList<Var>();
          // Only some output types might be read
          for (Var o: outputs) {
            if (Types.hasReadableSideChannel(o.type())) {
              res.add(o);
            }
          }
          return res;
        }
        case CALL_LOCAL:
        case CALL_LOCAL_CONTROL:
        case CALL_SYNC:
        case CALL_CONTROL: {
          List<Var> res = new ArrayList<Var>();
          Function f = fns == null ? null : fns.get(this.functionName);
          for (int i = 0; i < outputs.size(); i++) {
            Var o = outputs.get(i);

            // Check to see if function might read the output
            if (Types.hasReadableSideChannel(o.type()) &&
                (f == null || !f.isOutputWriteOnly(i))) {
              res.add(o);
            }
          }
          return res;
        }
        default:
          throw new STCRuntimeError("unexpected op: " + op);
      }
    }

    @Override
    public boolean hasSideEffects() {
      return (!ForeignFunctions.isPure(functionName)) ||
            this.writesAliasVar() || this.writesMappedVar();
    }
  
    @Override
    public Map<Var, Arg> constantFold(String enclosingFnName,
                                  Map<Var, Arg> knownConstants) {
      
      if (isImpl(SpecialFunction.ARGV)) {
        // See if argument name is constant
        Arg argName = knownConstants.get(inputs.get(0).getVar());
        if (argName != null) {
          String val = CompileTimeArgs.lookup(argName.getStringLit());
          if (val != null) {
            return Collections.singletonMap(outputs.get(0),
                                             Arg.createStringLit(val));
          }
        }
      }
      return null;
    }
    
    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      return null;
    }
    
    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                           boolean waitForClose) {
      // See which arguments are closed
      boolean allClosed = true;
      if (!waitForClose) {
        for (int i = 0; i < this.inputs.size(); i++) {
          Arg in = this.inputs.get(i);
          if (in.isVar()) {
            if (closedVars.contains(in.getVar())) {
              this.closedInputs.set(i, true);
            } else {
              allClosed = false;
            }
          }
        }
      }
      
      // Deal with mapped variables, which are effectively side-channels
      for (int i = 0; i < this.outputs.size(); i++) {
        Var out = this.outputs.get(i);
        if (Types.isFile(out)) {
          // Need to wait for filename, unless unmapped
          if (!(waitForClose || Semantics.outputMappingAvail(closedVars, out))) {
            allClosed = false;
          }
        }
      }
      
      if (allClosed && (ForeignFunctions.hasOpEquiv(this.functionName)
                || ForeignFunctions.hasInlineVersion(this.functionName))) {
        TaskMode mode = ForeignFunctions.getTaskMode(this.functionName);
        if (mode == null) {
          mode = TaskMode.LOCAL;
        }
        
        // True unless the function alters mapping itself
        boolean mapOutVars = true;
        if (isImpl(SpecialFunction.INPUT_FILE)) {
          mapOutVars = false;
        }
        
        // All args are closed!
        return new MakeImmRequest(
            Collections.unmodifiableList(this.outputs),
            Collections.unmodifiableList(this.varInputs(true)),
            mode, false, mapOutVars);

      }
      return null;
    }
    
    @Override
    public MakeImmChange makeImmediate(List<Var> outVars, 
                                        List<Arg> values) {
      // Discard non-future inputs.  These are things like priorities or
      // targets which do not need to be retained for the local version
      List<Var> retainedInputs = varInputs(true);
      assert(values.size() == retainedInputs.size());
      
      if (ForeignFunctions.hasOpEquiv(functionName)) {
        BuiltinOpcode newOp = ForeignFunctions.getOpEquiv(functionName);
        assert(newOp != null);
        
        if (outputs.size() == 1) {
          checkSwappedOutput(outputs.get(0), outVars.get(0));
          return new MakeImmChange(
              Builtin.createLocal(newOp, outVars.get(0), values));
        } else {
          assert(outputs.size() == 0);
          return new MakeImmChange(
              Builtin.createLocal(newOp, null, values));
        }
      } else {
        assert(ForeignFunctions.hasInlineVersion(functionName));
        for (int i = 0; i < outputs.size(); i++) {
          checkSwappedOutput(outputs.get(i), outVars.get(i));
        }
        return new MakeImmChange(
                new LocalFunctionCall(functionName, values, outVars));
      }
    }

    /**
     * Check that old output type was swapped correctly for
     * making immediate
     * @param oldOut
     * @param newOut
     */
    private void checkSwappedOutput(Var oldOut, Var newOut) {
      if (Types.isArray(oldOut.type())) {
        assert(Types.isArray(newOut.type()));
      } else {
        assert(Types.derefResultType(oldOut.type()).equals(
                newOut.type()));
      }
    }

    @Override
    public List<Var> getBlockingInputs() {
      List<Var> blocksOn = new ArrayList<Var>();
      if (op == Opcode.CALL_FOREIGN) {
        for (Arg in: inputs) {
          if (in.isVar()) {
            Var v = in.getVar();
            if (Types.isScalarFuture(v.type())
                || Types.isRef(v.type())) {
              // TODO: this is a conservative idea of which ones
              // are set
              blocksOn.add(v);
            }
          }
        }
      } else if (op == Opcode.CALL_SYNC) {
        // Can't block because we need to enter the function immediately
        return Var.NONE;
      } else if (op == Opcode.CALL_CONTROL ) {
        //TODO: should see which arguments are blocking
        return Var.NONE;
      }
      return blocksOn;
    }
    
    @Override
    public Pair<List<Var>, List<Var>> getIncrVars(Map<String, Function> functions) {
      switch (op) { 
        case CALL_FOREIGN:
        case CALL_CONTROL:
        case CALL_LOCAL:
        case CALL_LOCAL_CONTROL: {
          List<Var> readIncr = new ArrayList<Var>();
          List<Var> writeIncr = new ArrayList<Var>();
          for (Arg inArg: inputs) {
            if (inArg.isVar()) {
              if (RefCounting.hasReadRefCount(inArg.getVar())) {
                readIncr.add(inArg.getVar());
              }
            }
          }
          for (int i = 0; i < outputs.size(); i++) {
            Var outVar = outputs.get(i);
            if (RefCounting.hasWriteRefCount(outVar)) {
              writeIncr.add(outVar);
            }
            if (op != Opcode.CALL_FOREIGN) {              
              Function f = functions.get(this.functionName);
              boolean writeOnly = f.isOutputWriteOnly(i);
              
              // keep read references to output vars
              if (!writeOnly && RefCounting.hasReadRefCount(outVar)) {
                readIncr.add(outVar);
              }
            }
          }
          return Pair.create(readIncr, writeIncr);
        }
        default:
          // Return default
          return super.getIncrVars();
      }
    }
      
    @Override
    public TaskMode getMode() {
      switch (op) {
        case CALL_SYNC:
          return TaskMode.SYNC;
        case CALL_LOCAL:
          return TaskMode.LOCAL;
        case CALL_LOCAL_CONTROL:
          return TaskMode.LOCAL_CONTROL;
        case CALL_FOREIGN:
          return ForeignFunctions.getTaskMode(functionName);
        case CALL_CONTROL:
          return TaskMode.CONTROL;
        default:
          throw new STCRuntimeError("Unexpected function call opcode: " + op);
      }
    }

    @Override
    public Instruction clone() {
      // Variables are immutable so just need to clone lists
      return new FunctionCall(op, functionName, 
          new ArrayList<Arg>(inputs), new ArrayList<Var>(outputs),
          props.clone());
    }
  }
  
  public static class LocalFunctionCall extends CommonFunctionCall {
    private final List<Var> outputs;
    private final List<Arg> inputs;
  
    public LocalFunctionCall(String functionName,
        List<Arg> inputs, List<Var> outputs) {
      super(Opcode.CALL_FOREIGN_LOCAL, functionName);
      this.outputs = new ArrayList<Var>();
      this.outputs.addAll(outputs);
      this.inputs = new ArrayList<Arg>();
      this.inputs.addAll(inputs);
      for(Var v: outputs) {
        assert(v != null);
      }
      
      for(Arg a: inputs) {
        assert(a != null);
      }
    }
  
    @Override
    public String toString() {
      return formatFunctionCall(op, functionName, outputs, inputs);
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.builtinLocalFunctionCall(functionName, inputs, outputs);
    }
  
    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      if (mode == RenameMode.REFERENCE || mode == RenameMode.REPLACE_VAR) {
        ICUtil.replaceVarsInList(renames, outputs, false);
      }
      ICUtil.replaceArgsInList(renames, inputs);
    }
  
    public String getFunctionName() {
      return this.functionName;
    }
  
    @Override
    public List<Arg> getInputs() {
      return Collections.unmodifiableList(inputs);
    }
  
    @Override
    public List<Var> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    
    @Override
    public List<Var> getInitialized() {
      if (isImpl(SpecialFunction.INPUT_FILE)) {
        // The local version of input_file initializes the output for writing
        return getOutput(0).asList();
      }
      return Var.NONE;
    }
    
    @Override
    public boolean hasSideEffects() {
      return (!ForeignFunctions.isPure(functionName)) ||
            this.writesAliasVar() || this.writesMappedVar();
    }
  
    @Override
    public Map<Var, Arg> constantFold(String enclosingFnName,
                                  Map<Var, Arg> knownConstants) {
      // Replace any variables for which constant values are known
      ICUtil.replaceArgsInList(knownConstants, inputs);
      
      if (isImpl(SpecialFunction.ARGV)) {
        Arg argName = this.inputs.get(0);
        if (argName.isStringVal()) {
          String val = CompileTimeArgs.lookup(argName.getStringLit());
          if (val != null) {
            return Collections.singletonMap(outputs.get(0),
                                             Arg.createStringLit(val));
          }
        }
      }
      return null;
    }
    
    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      return null;
    }
    
    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                           boolean waitForClose) {
      return null; // already immediate
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> outVars, 
                                        List<Arg> values) {
      throw new STCRuntimeError("Invalid method call");
    }

    @Override
    public List<Var> getBlockingInputs() {
      // doesn't take futures as args
      return Var.NONE;
    }

    @Override
    public TaskMode getMode() {
      return TaskMode.SYNC;
    }
    
    @Override
    public List<Var> getClosedOutputs() {
      if (isImpl(SpecialFunction.RANGE) ||
          isImpl(SpecialFunction.RANGE_STEP)) {
        // Range closes outputs at end
        return Arrays.asList(outputs.get(0));
      }
      return super.getClosedOutputs();
    }

    @Override
    public Instruction clone() {
      // Variables are immutable so just need to clone lists
      return new LocalFunctionCall(functionName, 
          new ArrayList<Arg>(inputs), new ArrayList<Var>(outputs));
    }

    @Override
    public List<Var> getWriteIncrVars() {
      // Range is special case where output array modified
      if (isImpl(SpecialFunction.RANGE) ||
          isImpl(SpecialFunction.RANGE_STEP)) {
        // Array output must be incremented
        return Arrays.asList(getOutput(0));
      }
      return Var.NONE;
    }
  }
  
  public static class RunExternal extends Instruction {
    private final String cmd;
    private final ArrayList<Arg> inFiles;
    private final ArrayList<Var> outFiles;
    private final ArrayList<Arg> args;
    private final Redirects<Arg> redirects;
    private final boolean hasSideEffects;
    private final boolean deterministic;
    
    public RunExternal(String cmd, List<Arg> inFiles, List<Var> outFiles, 
               List<Arg> args, Redirects<Arg> redirects,
               boolean hasSideEffects, boolean deterministic) {
      super(Opcode.RUN_EXTERNAL);
      this.cmd = cmd;
      this.inFiles = new ArrayList<Arg>(inFiles);
      this.outFiles = new ArrayList<Var>(outFiles);
      this.args = new ArrayList<Arg>(args);
      this.redirects = redirects.clone();
      this.deterministic = deterministic;
      this.hasSideEffects = hasSideEffects;
    }

    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      ICUtil.replaceArgsInList(renames, args);
      ICUtil.replaceArgsInList(renames, inFiles);
      redirects.stdin = ICUtil.replaceArg(renames, redirects.stdin, true);
      redirects.stdout = ICUtil.replaceArg(renames, redirects.stdout, true);
      redirects.stderr = ICUtil.replaceArg(renames, redirects.stderr, true);
      if (mode == RenameMode.REFERENCE || mode == RenameMode.REPLACE_VAR) {
        ICUtil.replaceVarsInList(renames, outFiles, false);
      }
    }

    @Override
    public String toString() {
      StringBuilder res = new StringBuilder();
      res.append(formatFunctionCall(op, cmd, outFiles, args));
      String redirectString = redirects.toString();
      if (redirectString.length() > 0) {
        res.append(" " + redirectString);
      }
      res.append(" infiles=[");
      ICUtil.prettyPrintArgList(res, inFiles);
      res.append("]");
      
      res.append(" outfiles=[");
      ICUtil.prettyPrintVarList(res, outFiles);
      res.append("]");
      return res.toString();
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.runExternal(cmd, args, inFiles, outFiles, 
                  redirects, hasSideEffects, deterministic);
    }

    @Override
    public List<Arg> getInputs() {
      ArrayList<Arg> res = new ArrayList<Arg>();
      res.addAll(args);
      res.addAll(inFiles);
      for (Arg redirFilename: redirects.redirections(true, true)) {
        if (redirFilename != null) {
          res.add(redirFilename);
        }
      }
      return res;
    }

    @Override
    public List<Var> getOutputs() {
      return Collections.unmodifiableList(outFiles);
    }

    @Override
    public boolean hasSideEffects() {
      return hasSideEffects;
    }

    @Override
    public Map<Var, Arg> constantFold(String fnName,
        Map<Var, Arg> knownConstants) {
      // Replace variables for which values are known
      ICUtil.replaceArgsInList(knownConstants, args);
      return null;
    }

    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                           boolean waitForClose) {
      // Don't support reducing this
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> outVals,
        List<Arg> inValues) {
      return null;
    }

    @Override
    public List<Var> getBlockingInputs() {
      // This instruction runs immediately: we won't actually block on any inputs
      
      // However, the compiler should act as if we depend on input file vars
      return ICUtil.extractVars(inFiles);
    }

    @Override
    public TaskMode getMode() {
      return TaskMode.SYNC;
    }
    
    @Override
    public List<Var> getClosedOutputs() {
      return getOutputs();
    }

    @Override
    public List<ResultVal> getResults(CVMap existing) {
      if (deterministic) {
        List<ResultVal> cvs = new ArrayList<ResultVal>(
                                                        outFiles.size());
        for (int i = 0; i < outFiles.size(); i++) {
          // Unique key for cv includes number of output
          // Output file should be closed after external program executes
          ResultVal cv = ResultVal.buildResult(op, cmd, i,
                     args, outFiles.get(i).asArg(), true);
          cvs.add(cv);
        }
        return cvs;
      } else {
        return null;
      }
    }

    @Override
    public Instruction clone() {
      return new RunExternal(cmd, inFiles, outFiles,
              args, redirects, hasSideEffects, deterministic);
    }
    
  }
  
  public static class LoopContinue extends Instruction {
    private final ArrayList<Var> newLoopVars;
    private final ArrayList<Var> loopUsedVars;
    private final ArrayList<Boolean> blockingVars;

    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      ICUtil.replaceVarsInList(renames, newLoopVars, false);
      if (mode == RenameMode.REFERENCE || mode == RenameMode.REPLACE_VAR) {
        ICUtil.replaceVarsInList(renames, loopUsedVars, true);
      }
    }

    @Override
    public void removeVars(Set<Var> removeVars) {
      assert(!removeVars.contains(newLoopVars.get(0)));
      loopUsedVars.removeAll(removeVars);
      newLoopVars.removeAll(removeVars);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.op.toString().toLowerCase());
      boolean first = true;
      sb.append(" [");
      for (Var v: this.newLoopVars) {
        if (first) {
          first = false;
        } else {
          sb.append(' ');
        }
        sb.append(v.name());
      }
      sb.append("] #passin[");
      ICUtil.prettyPrintVarList(sb, this.loopUsedVars);
      sb.append("] #blocking[");
      ICUtil.prettyPrintList(sb, this.blockingVars);
      sb.append("]");
      return sb.toString();
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.loopContinue(this.newLoopVars, this.loopUsedVars, this.blockingVars);
    }
  
    @Override
    public List<Arg> getInputs() {
      // need to make sure that these variables are avail in scope
      ArrayList<Arg> res = new ArrayList<Arg>(newLoopVars.size());
      for (Var v: newLoopVars) {
        res.add(v.asArg());
      }
      return res;
    }
  
    @Override
    public List<Var> getOutputs() {
      // No outputs
      return new ArrayList<Var>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return true;
    }
  
    @Override
    public Map<Var, Arg> constantFold(String fnName,
              Map<Var, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                           boolean waitForClose) {
      // See if we need to block on all inputs
      Set<Var> alreadyDone = new HashSet<Var>();
      for (int i = 0; i < this.newLoopVars.size(); i++) {
        if (this.blockingVars.get(i)) {
          Var v = this.newLoopVars.get(i);
          if (closedVars.contains(v)) {
            // Don't need to block
            this.blockingVars.set(i, false);
          } else if (alreadyDone.contains(v)) {
            // In case of repeated elements
            this.blockingVars.set(i, false);
          } else {
            alreadyDone.add(v);
          }
        }
      }
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on loop continue!");
    }
    
    @Override
    public List<Var> getReadIncrVars() {
      // Increment variables passed to next iter
      return Collections.unmodifiableList(newLoopVars);
    }

    @Override
    public List<Var> getBlockingInputs() {
      return Var.NONE;
    }
    

    @Override
    public TaskMode getMode() {
      return TaskMode.CONTROL;
    }
    
    public void setLoopUsedVars(Collection<Var> variables) {
      loopUsedVars.clear();
      loopUsedVars.addAll(variables);
    }
    
    public LoopContinue(List<Var> newLoopVars, 
                        List<Var> loopUsedVars,
                        List<Boolean> blockingVars) {
      super(Opcode.LOOP_CONTINUE);
      this.newLoopVars = new ArrayList<Var>(newLoopVars);
      this.loopUsedVars = new ArrayList<Var>(loopUsedVars);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
    }

    @Override
    public List<ResultVal> getResults(CVMap existing) {
      // Nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopContinue(new ArrayList<Var>(newLoopVars), 
          new ArrayList<Var>(loopUsedVars),
          new ArrayList<Boolean>(blockingVars));
    }
  }
  
  public static class LoopBreak extends Instruction {
    /**
     * Variables where refcount should be decremented upon loop termination
     */
    private final ArrayList<PassedVar> loopUsedVars;

    /**
     * Variables to be closed upon loop termination
     */
    private final ArrayList<Var> keepOpenVars;
  
    public LoopBreak(List<PassedVar> loopUsedVars, List<Var> keepOpenVars) {
      super(Opcode.LOOP_BREAK);
      this.loopUsedVars = new ArrayList<PassedVar>(loopUsedVars);
      this.keepOpenVars = new ArrayList<Var>(keepOpenVars);
    }
  
    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      // do nothing
    }

    public List<PassedVar> getLoopUsedVars() {
      return Collections.unmodifiableList(loopUsedVars);
    }
    
    public List<Var> getKeepOpenVars() {
      return Collections.unmodifiableList(keepOpenVars);
    }
    
    public void setLoopUsedVars(Collection<PassedVar> passedVars) {
      this.loopUsedVars.clear();
      this.loopUsedVars.addAll(passedVars);
    }

    public void setKeepOpenVars(Collection<Var> keepOpen) {
      this.keepOpenVars.clear();
      this.keepOpenVars.addAll(keepOpen);
    }

    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.op.toString().toLowerCase());

      sb.append(" #passin[");
      ICUtil.prettyPrintList(sb, this.loopUsedVars);

      sb.append("] #keepopen[");
      ICUtil.prettyPrintVarList(sb, this.keepOpenVars);
      sb.append(']');
      return sb.toString();
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.loopBreak(PassedVar.extractVars(loopUsedVars), keepOpenVars);
    }
  
    @Override
    public List<Arg> getInputs() {
      return new ArrayList<Arg>(0);
    }
  
    @Override
    public List<Var> getOutputs() {
      return new ArrayList<Var>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return true;
    }
  
    @Override
    public Map<Var, Arg> constantFold(String fnName,
                Map<Var, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                           boolean waitForClose) {
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on loop continue!");
    }
   
    @Override
    public List<Var> getBlockingInputs() {
      return Var.NONE;
    }

    @Override
    public TaskMode getMode() {
      return TaskMode.SYNC;
    }
   
    @Override
    public List<ResultVal> getResults(CVMap existing) {
      // nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopBreak(loopUsedVars, keepOpenVars);
    }
  }
  
  public static enum Opcode {
    FAKE, // Used for ComputedValue if there isn't a real opcode
    COMMENT,
    CALL_FOREIGN, CALL_FOREIGN_LOCAL, CALL_CONTROL, CALL_SYNC, CALL_LOCAL,
    CALL_LOCAL_CONTROL,
    DEREF_INT, DEREF_STRING, DEREF_FLOAT, DEREF_BOOL, DEREF_BLOB,
    DEREF_FILE,
    STORE_INT, STORE_STRING, STORE_FLOAT, STORE_BOOL, STORE_REF, 
    LOAD_INT, LOAD_STRING, LOAD_FLOAT, LOAD_BOOL, LOAD_REF,
    STORE_BLOB, LOAD_BLOB, FREE_BLOB,
    STORE_VOID, LOAD_VOID, 
    STORE_FILE, DECR_LOCAL_FILE_REF,
    LOAD_FILE, // dummy instruction
    DECR_WRITERS, DECR_REF, INCR_WRITERS, INCR_REF,
    
    ARRAYREF_LOOKUP_FUTURE, ARRAY_LOOKUP_FUTURE,
    ARRAYREF_LOOKUP_IMM, ARRAY_LOOKUP_REF_IMM, ARRAY_LOOKUP_IMM,
    ARRAY_INSERT_FUTURE, ARRAY_DEREF_INSERT_FUTURE, 
    ARRAY_INSERT_IMM, ARRAY_DEREF_INSERT_IMM, 
    ARRAYREF_INSERT_FUTURE, ARRAYREF_DEREF_INSERT_FUTURE,
    ARRAYREF_INSERT_IMM, ARRAYREF_DEREF_INSERT_IMM, 
    ARRAY_BUILD,
    STRUCT_LOOKUP, STRUCTREF_LOOKUP, STRUCT_INSERT,
    ARRAY_CREATE_NESTED_FUTURE, ARRAYREF_CREATE_NESTED_FUTURE,
    ARRAY_CREATE_NESTED_IMM, ARRAYREF_CREATE_NESTED_IMM,
    LOOP_BREAK, LOOP_CONTINUE, 
    COPY_REF,
    LOCAL_OP, ASYNC_OP,
    RUN_EXTERNAL,
    INIT_UPDATEABLE_FLOAT, UPDATE_MIN, UPDATE_INCR, UPDATE_SCALE, LATEST_VALUE,
    UPDATE_MIN_IMM, UPDATE_INCR_IMM, UPDATE_SCALE_IMM,
    INIT_LOCAL_OUTPUT_FILE,
    GET_FILENAME, CHOOSE_TMP_FILENAME, IS_MAPPED,
    SET_FILENAME_VAL, GET_FILENAME_VAL, GET_LOCAL_FILENAME,
    COPY_FILE_CONTENTS
  }

  
  
  /**
   * Builtin operation.  Depending on the opcode (LOCAL_OP or ASYNC_OP),
   * it applied to and returns local value variables or futures.
   * Constructors are private, use factory methods to create.
   */
  public static class Builtin extends Instruction {
    public final BuiltinOpcode subop;
    
    private Var output; // null if no output
    private List<Arg> inputs;
    private final TaskProps props; // only defined for async

    private Builtin(Opcode op, BuiltinOpcode subop, Var output, 
          List<Arg> inputs, TaskProps props) {
      super(op);
      if (op == Opcode.LOCAL_OP) {
        assert(props == null);
      } else {
        assert(op == Opcode.ASYNC_OP);
        assert(props != null);
      }
      this.subop = subop;
      this.output = output;
      this.inputs = new ArrayList<Arg>(inputs);
      this.props = props;
    }
    

    @Override
    public String shortOpName() {
      return op.toString().toLowerCase() + "-" + subop.toString().toLowerCase();
    }
    
    public static Builtin createLocal(BuiltinOpcode subop, Var output, 
        Arg input) {
      return new Builtin(Opcode.LOCAL_OP, subop, output, Arrays.asList(input),
                          null);
    }
    
    public static Builtin createLocal(BuiltinOpcode subop, Var output, 
        List<Arg> inputs) {
      return new Builtin(Opcode.LOCAL_OP, subop, output, inputs, null);
    }
    
    public static Builtin createAsync(BuiltinOpcode subop, Var output, 
        List<Arg> inputs, TaskProps props) {
      return new Builtin(Opcode.ASYNC_OP, subop, output, inputs, props);
    }

    public static Builtin createAsync(BuiltinOpcode subop, Var output, 
        List<Arg> inputs) {
      return createAsync(subop, output, inputs, new TaskProps());
    }


    @Override
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      if (mode == RenameMode.REFERENCE || mode == RenameMode.REPLACE_VAR) {
        if (output != null && renames.containsKey(this.output)) {
          this.output = renames.get(this.output).getVar();
        }
      }
      ICUtil.replaceArgsInList(renames, inputs);
      if (props != null) {
        ICUtil.replaceArgValsInMap(renames, props);
      }
    }

    @Override
    public String toString() {
      String res = op.toString().toLowerCase() + " ";
      if (output != null) {
        res +=  output.name() + " = ";
      }
      res += subop.toString().toLowerCase();
      for (Arg input: inputs) {
        res += " " + input.toString();
      }
      if (props != null) {
        res += ICUtil.prettyPrintProps(props);
      }
      return res;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      if (op == Opcode.LOCAL_OP) {
        assert(props == null);
        gen.localOp(subop, output, inputs);
      } else {
        assert (op == Opcode.ASYNC_OP);
        gen.asyncOp(subop, output, inputs, props);
      }
    }

    @Override
    public List<Arg> getInputs() {
      if (props == null) {
        return Collections.unmodifiableList(inputs);
      } else {
        // Need to add priority so that e.g. it doesn't get optimised out
        ArrayList<Arg> res = new ArrayList<Arg>(inputs.size() + 1);
        res.addAll(inputs);
        res.addAll(props.values());
        return res;
      }
    }

    @Override
    public List<Var> getOutputs() {
      if (output != null) {
        return Arrays.asList(output);
      } else {
        return new ArrayList<Var>(0);
      }
    }
    
    @Override
    public boolean hasSideEffects() {
      if (op == Opcode.LOCAL_OP) {
        return Operators.isImpure(subop);
      } else {
        return Operators.isImpure(subop) || this.writesAliasVar()
            || this.writesMappedVar();
      }
    }

    @Override
    public Map<Var, Arg> constantFold(String fnName,
                          Map<Var, Arg> knownConstants) {
      if (this.subop == BuiltinOpcode.ASSERT || 
          this.subop == BuiltinOpcode.ASSERT_EQ) {
        compileTimeAssertCheck(subop, this.inputs, knownConstants, fnName);
      }
      
      if (this.output == null) {
        return null;
      }
      
      // List of constant values for inputs, null if input not const
      ArrayList<Arg> constInputs = new ArrayList<Arg>(inputs.size());
      /* First try to replace arguments with constants */
      for (int i = 0; i < inputs.size(); i++) {
        Arg in = inputs.get(i);
        if (in.isVar()) {
          Arg c = knownConstants.get(in.getVar());
          constInputs.add(c);
          if (c != null && op == Opcode.LOCAL_OP) {
            // can replace local value arg with constant
            inputs.set(i, c);
          }
        } else {
          constInputs.add(in);
        }
      }
      return Builtin.constantFold(this.subop, this.output, constInputs);
    }

    private static void compileTimeAssertCheck(BuiltinOpcode subop2,
        List<Arg> inputs2, Map<Var, Arg> knownConstants,
        String enclosingFnName) {
      
      List<Arg> inputVals = new ArrayList<Arg>(inputs2.size());
      // Check that all inputs are available
      for (Arg input: inputs2) {
        if (input.isConstant()) {
          inputVals.add(input);
        } else if (knownConstants.containsKey(input.getVar())) {
          inputVals.add(knownConstants.get(input.getVar()));
        } else {
          // Can't check
          return;
        }
      }
      
      
      if (subop2 == BuiltinOpcode.ASSERT) {
        Arg cond = inputVals.get(0);
        
        assert(cond.isBoolVal());
        if(!cond.getBoolLit()) {
          compileTimeAssertWarn(enclosingFnName, 
              "constant condition evaluated to false",
              inputs2.get(1), knownConstants);
        }
      } else {
        assert(subop2 == BuiltinOpcode.ASSERT_EQ);
        
        Arg a1 = inputVals.get(0);
        Arg a2 = inputVals.get(1);
        assert(a1.isConstant()) : a1 + " " + a1.getKind();
        assert(a2.isConstant()) : a2 + " " + a2.getKind();
        if (a1 != null && a2 != null) {
          if(!a1.equals(a2)) {
            String reason = a1.toString() + " != " + a2.toString();
            Arg msg = inputVals.get(2);
            compileTimeAssertWarn(enclosingFnName, reason, msg, knownConstants);
          }
        }
      }
    }

    private static void compileTimeAssertWarn(String enclosingFnName,
        String reason, Arg assertMsg, Map<Var, Arg> knownConstants) {
      String errMessage;
      if (assertMsg.isConstant()) {
        errMessage = assertMsg.getStringLit();
      } else if (knownConstants.containsKey(assertMsg.getVar())) {
        errMessage = knownConstants.get(assertMsg.getVar()).getStringLit();
      } else {
        errMessage = "<RUNTIME ERROR MESSAGE>";
      }
        
      System.err.println("Warning: assertion in " + enclosingFnName +
          " with error message: \"" + errMessage + 
          "\" will fail at runtime because " + reason + "\n"
          + "This may be a compiler internal error: check your code" +
              " and report if this warning is faulty");
    }

    @Override
    public Instruction constantReplace(Map<Var, Arg> knownConstants) {
      // can replace short-circuitable operations with direct assignment
      if (Operators.isShortCircuitable(subop)) {
        return tryShortCircuit(knownConstants);
      }
      return null;
    }

    private Builtin tryShortCircuit(Map<Var, Arg> knownConstants) {
      List<Arg> constArgs = new ArrayList<Arg>(2);
      List<Var> varArgs = new ArrayList<Var>(2);
      for (Arg in: inputs) {
        if (in.isConstant()) {
          constArgs.add(in);
        } else {
          Arg constIn = knownConstants.get(in.getVar());
          if (constIn == null) {
            varArgs.add(in.getVar());
          } else {
            constArgs.add(constIn);
          }
        }
      }
      if (constArgs.size() == 1) {
        boolean arg1 = constArgs.get(0).getBoolLit();
        // Change it to a copy: should make it easier to further optimize
        if ((subop == BuiltinOpcode.OR && !arg1) ||
            (subop == BuiltinOpcode.AND && arg1)) {
          if (op == Opcode.ASYNC_OP) { 
            return Builtin.createAsync(BuiltinOpcode.COPY_BOOL, 
                output, varArgs.get(0).asArg().asList());
          } else {
            return Builtin.createLocal(BuiltinOpcode.COPY_BOOL, 
                output, varArgs.get(0).asArg());
          }
        } 
      }
      return null;
    }
    
    public static Map<Var, Arg> constantFold(BuiltinOpcode op, Var outVar,
        List<Arg> constInputs) {
      Arg out = OpEvaluator.eval(op, constInputs);
      return (out == null) ? null : Collections.singletonMap(outVar, out);
    }
    
    private static boolean hasLocalVersion(BuiltinOpcode op) {
      return true;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                           boolean waitForClose) {
      if (op == Opcode.LOCAL_OP) {
        // already is immediate
        return null; 
      } else { 
        assert(op == Opcode.ASYNC_OP);
        if (!hasLocalVersion(subop)) {
          return null;
        }
        
        // COPY_FILE wants to initialize its own output file
        boolean mapOutputVars = true;
        
        // See which arguments are closed
        if (!waitForClose) {
          for (Arg inarg: this.inputs) {
            assert(inarg.isVar());
            Var in = inarg.getVar();
            if (!closedVars.contains(in)) {
              // Non-closed arg
              return null;
            }
          }
        }
        
        if (Types.isFile(output) && !mapOutputVars) {
          // Need to wait for filename, unless unmapped
          if (!(waitForClose ||
                Semantics.outputMappingAvail(closedVars, output))) {
            return null;
          }
        }
      
          // All args are closed!
        return new MakeImmRequest(
            (this.output == null) ? 
                  null : Collections.singletonList(this.output),
            ICUtil.extractVars(this.inputs),
            TaskMode.LOCAL, false, mapOutputVars);
      }
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> newOut, List<Arg> newIn) {
      if (op == Opcode.LOCAL_OP) {
        throw new STCRuntimeError("Already immediate!");
      } else {
        assert(newIn.size() == inputs.size());
        if (output != null) {
          assert(newOut.size() == 1);
          assert(Types.derefResultType(output.type()).equals(
              newOut.get(0).type()));
          return new MakeImmChange(
              Builtin.createLocal(subop, newOut.get(0), newIn));
        } else {
          assert(newOut == null || newOut.size() == 0);
          return new MakeImmChange(
              Builtin.createLocal(subop, null, newIn));
        }
      }
    }

    @Override
    public List<Var> getBlockingInputs() {
      if (op == Opcode.LOCAL_OP) {
        // doesn't take futures as args
        return Var.NONE;
      } else {
        assert(op == Opcode.ASYNC_OP);
        // blocks on all scalar inputs
        ArrayList<Var> result = new ArrayList<Var>();
        for (Arg inarg: inputs) {
          if (inarg.isVar()) {
            Var invar = inarg.getVar();
            if (Types.isRef(invar.type())
                || Types.isScalarFuture(invar.type())) {
              result.add(invar);
            }
          }
        }
        return result;
      }
    }
    @Override
    public TaskMode getMode() {
      if (op == Opcode.ASYNC_OP) {
        return TaskMode.CONTROL;
      } else {
        return TaskMode.SYNC;
      }
    }
    
    @Override
    public List<ResultVal> getResults(CVMap existing) {
      if (this.hasSideEffects()) {
        // Two invocations of this aren't equivalent
        return null;
      }
      
      ResultVal basic = makeBasicComputedValue();
      
      if (subop == BuiltinOpcode.COPY_INT || subop == BuiltinOpcode.COPY_BOOL
      || subop == BuiltinOpcode.COPY_FLOAT || subop == BuiltinOpcode.COPY_STRING
      || subop == BuiltinOpcode.COPY_BLOB || subop == BuiltinOpcode.COPY_VOID) {
        // Add transitively valid computed values if a copy
        List<ResultVal> res = new ArrayList<ResultVal>();
        res.add(basic);
        res.addAll(ValueTracker.makeCopiedRVs(existing, getOutput(0), getInput(0)));
        return res;
      }
      
      List<ResultVal> inferred = makeInferredComputedValues(existing);
      if (inferred.isEmpty()) {
        if (basic != null) {
          return Collections.singletonList(basic);
        } else {
          return Collections.emptyList();
        }
      } else {
        if (basic == null) {  
          return inferred;
        } else {
          List<ResultVal> res = new ArrayList<ResultVal>(
                                                    1 + inferred.size());
          res.add(basic);
          res.addAll(inferred);
          return res;
        }
      }
    }


    /**
     * Create computed value that describes the output
     * @return
     */
    private ResultVal makeBasicComputedValue() {
      if (subop == BuiltinOpcode.COPY_INT || subop == BuiltinOpcode.COPY_BOOL
      || subop == BuiltinOpcode.COPY_FLOAT || subop == BuiltinOpcode.COPY_STRING
      || subop == BuiltinOpcode.COPY_BLOB || subop == BuiltinOpcode.COPY_VOID) {
        // It might be assigning a constant val
        return ResultVal.makeCopy(this.output, this.inputs.get(0));
      } else if (Operators.isMinMaxOp(subop)) {
        assert(this.inputs.size() == 2);
        if (this.inputs.get(0).equals(this.inputs.get(1))) {
          return ResultVal.makeCopy(this.output, this.inputs.get(0));
        }
      } else if (output != null) {
        // put arguments into canonical order
        List<Arg> cvInputs;
        BuiltinOpcode cvOp;
        if (Operators.isCommutative(subop)) {
          cvInputs = new ArrayList<Arg>(this.inputs);
          Collections.sort(cvInputs);
          cvOp = subop;
        } else if (Operators.isFlippable(subop)) {
          cvInputs = new ArrayList<Arg>(this.inputs);
          Collections.reverse(cvInputs);
          cvOp = Operators.flippedOp(subop);
        } else {
          cvInputs = this.inputs;
          cvOp = subop;
        }
        
        boolean outClosed = (this.op == Opcode.LOCAL_OP);
        return ResultVal.buildResult(this.op, cvOp.name(), cvInputs,
                                this.output.asArg(), outClosed);
      }
      return null;
    }

    private List<ResultVal> makeInferredComputedValues(CVMap cvs) {
      try {
        if (!Settings.getBoolean(Settings.OPT_ALGEBRA)) {
          return Collections.emptyList();
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
      switch (subop) {
        case PLUS_INT:
        case MINUS_INT:
        List<ResultVal> inferred = tryAlgebra(cvs);
        printInferredValues(inferred);
        return inferred;
        default:
          // do nothing
          return Collections.emptyList();
      }
    }


    private void printInferredValues(List<ResultVal> inferred) {
      Logger logger = Logging.getSTCLogger();
      if (logger.isTraceEnabled()) {
        StringBuilder sb = new StringBuilder();
        if (!inferred.isEmpty()){
          sb.append(this + " => ");
          for (ResultVal t: inferred) {
            sb.append(t.location() + " == " + t + ", ");
          }
        }
        logger.trace(sb.toString());
      }
    }


    private List<ResultVal> tryAlgebra(CVMap cvs) {
      // do basic algebra, useful for adjacent array indices
      // TODO: could be more sophisticated, e.g. build operator tree
      //    and reduce to canonical form
      Arg in1 = getInput(0);
      Arg in2 = getInput(1);
      // Don't handle constant folding here

      Pair<Var, Long> args = convertToCanonicalAdd(subop, in1, in2); 
      if (args == null) {
        return Collections.emptyList();
      }
      List<ComputedValue> varVals = cvs.getVarContents(args.val1);
      List<ResultVal> res = new ArrayList<ResultVal>(); 
      for (ComputedValue varVal: varVals) {
        if (varVal.op() == this.op) {
          BuiltinOpcode aop = BuiltinOpcode.valueOf(varVal.subop());
          if (aop == BuiltinOpcode.PLUS_INT ||
              aop == BuiltinOpcode.MINUS_INT) { 
            Pair<Var, Long> add = convertToCanonicalAdd(aop,
                                  varVal.getInput(0), varVal.getInput(1));
            if (add != null) {
              // Note that if this instruction computes x = y + c1
              // and y = z + c2 was computed earlier, then
              // x = z + c1 + c2
              long c = args.val2 + add.val2;
              if (c == 0) {
                res.add(ResultVal.makeCopy(this.output,
                                                 add.val1.asArg()));
              } else {
                res.add(plusCV(op, add.val1.asArg(),
                             Arg.createIntLit(c),
                             this.output));
              }
            }
          }
        }
      }
      return res;
    }

    
    private static Pair<Var, Long> convertToCanonicalAdd(BuiltinOpcode aop,
                                                    Arg in1, Arg in2) {
      Var varArg;
      long constArg;
      if (!(in1.isVar() ^ in2.isVar())) {
        // Only handle one constant, one var
        return null;
      }
      if (in1.isVar()) {
        varArg = in1.getVar();
        constArg = in2.getIntLit();
        if (aop == BuiltinOpcode.MINUS_INT) {
          // Convert to addition
          constArg *= -1;
        }
      } else {
        if (aop == BuiltinOpcode.MINUS_INT) {
          // Don't handle negated variable
          return null;
        }
        constArg = in1.getIntLit();
        varArg = in2.getVar();
      }
      
      return Pair.create(varArg, constArg);
    }


    /**
     * Create computed value for addition
     * @param op
     * @param asArg
     * @param createIntLit
     * @param output2
     * @return
     */
    private static ResultVal plusCV(Opcode op, Arg arg1, Arg arg2,
        Var output) {
      return ResultVal.buildResult(op, BuiltinOpcode.PLUS_INT.name(),
          Arrays.asList(arg1, arg2), output.asArg(), op == Opcode.LOCAL_OP);
    }


    @Override
    public List<Var> getReadIncrVars() {
      if (op == Opcode.ASYNC_OP) {
        List<Var> res = new ArrayList<Var>(inputs.size());
        for (Arg in: inputs) {
          if (RefCounting.hasReadRefCount(in.getVar())) {
            res.add(in.getVar());
          }
        }
        return res;
      }
      return Var.NONE;
    }


    @Override
    public Instruction clone() {
      TaskProps propsClone = props == null ? null : props.clone();
      return new Builtin(op, subop, output, Arg.cloneList(inputs), propsClone);
    }
  }

  public static Instruction valueSet(Var dst, Arg value) {
    if (Types.isScalarValue(dst.type())) {
      switch (dst.type().primType()) {
      case BOOL:
        assert(value.isImmediateBool());
        return Builtin.createLocal(BuiltinOpcode.COPY_BOOL, dst, value);
      case INT:
        assert(value.isImmediateInt());
        return Builtin.createLocal(BuiltinOpcode.COPY_INT, dst, value);
      case FLOAT:
        assert(value.isImmediateFloat());
        return Builtin.createLocal(BuiltinOpcode.COPY_FLOAT, dst, value);
      case STRING:
        assert(value.isImmediateString());
        return Builtin.createLocal(BuiltinOpcode.COPY_STRING, dst, value);
      case BLOB:
        assert(value.isImmediateBlob());
        return Builtin.createLocal(BuiltinOpcode.COPY_BLOB, dst, value);
      case VOID:
        assert(Types.isBoolVal(value.type()));
        return Builtin.createLocal(BuiltinOpcode.COPY_VOID, dst, value);
      default:
        // fall through
        break;
      }
    } else if (Types.isArray(dst.type()) || Types.isStruct(dst.type())) {
      assert(dst.storage() == Alloc.ALIAS);
      assert (value.isVar());
      return TurbineOp.copyRef(dst, value.getVar());
    }

    throw new STCRuntimeError("Unhandled case in valueSet: "
        + " assign " + value.toString() + " to " + dst.toString());
  }

 
  public static Instruction retrieveValueOf(Var dst, Var src) {
    assert(Types.isScalarValue(dst.type()));
    assert(Types.isScalarFuture(src.type())
            || Types.isScalarUpdateable(src.type()));
    switch (src.type().primType()) {
    case BOOL:
      return TurbineOp.retrieveBool(dst, src);
    case INT:
      return TurbineOp.retrieveInt(dst, src);
    case FLOAT:
      return TurbineOp.retrieveFloat(dst, src);
    case STRING:
      return TurbineOp.retrieveString(dst, src);
    case BLOB:
      return TurbineOp.retrieveBlob(dst, src);
    case VOID:
      return TurbineOp.retrieveVoid(dst, src);
    case FILE:
      return TurbineOp.retrieveFile(dst, src);
    default:
      throw new STCRuntimeError("method to retrieve " +
            src.type().typeName() + " is not known yet");
    }
  }
  
  /**
   * Return the canonical ComputedValue representation for
   * retrieving the value of this type
   * @param src
   * @return null if cannot be retrieved
   */
  public static ComputedValue retrieveCompVal(Var src) {
    Type srcType = src.type();
    Opcode op = retrieveOpcode(srcType);
    if (op == null) {
      return null;
    }
    return new ComputedValue(op, Arrays.asList(src.asArg()));
  }

  public static ResultVal assignComputedVal(Var dst, Arg val) {
    Type dstType = dst.type();
    if (Types.isScalarValue(dstType)) {
        BuiltinOpcode op;
        switch(dstType.primType()) {
        case BOOL:
          op = BuiltinOpcode.COPY_BOOL;
          break;
        case INT:
          op = BuiltinOpcode.COPY_INT;
          break;
        case FLOAT:
          op = BuiltinOpcode.COPY_FLOAT;
          break;
        case STRING:
          op = BuiltinOpcode.COPY_STRING;
          break;
        case BLOB:
          op = BuiltinOpcode.COPY_BLOB;
          break;
        case VOID:
          op = BuiltinOpcode.COPY_VOID;
          break;
        default:
          throw new STCRuntimeError("Unhandled type: " + dstType);
        }
        return ResultVal.buildResult(Opcode.LOCAL_OP, 
            op.toString(), Arrays.asList(val), dst.asArg(), false);
    } else {
      Opcode op = assignOpcode(dstType);
      if (op != null) {
        return ResultVal.buildResult(op, Arrays.asList(val), dst.asArg()
                                                                          , true);
      }
    }
    throw new STCRuntimeError("DOn't know how to assign to " + dst);
  }

  private static Opcode assignOpcode(Type dstType) {
    Opcode op = null;
    if (Types.isScalarFuture(dstType)) {
       switch(dstType.primType()) {
       case BOOL:
         op = Opcode.STORE_BOOL;
         break;
       case INT:
         op = Opcode.STORE_INT;
         break;
       case FLOAT:
         op = Opcode.STORE_FLOAT;
         break;
       case STRING:
         op = Opcode.STORE_STRING;
         break;
       case BLOB:
         op = Opcode.STORE_BLOB;
         break;
       case VOID:
         op = Opcode.STORE_VOID;
         break;
       case FILE:
         op = Opcode.STORE_FILE;
         break;
       default:
         throw new STCRuntimeError("don't know how to assign " + dstType);
       }
    } else if (Types.isRef(dstType)) {
      op = Opcode.STORE_REF;
    }
    return op;
  }
  
  private static Opcode retrieveOpcode(Type srcType) {
    Opcode op;
    if (Types.isScalarFuture(srcType)) {
      switch(srcType.primType()) {
      case BOOL:
        op = Opcode.LOAD_BOOL;
        break;
      case INT:
        op = Opcode.LOAD_INT;
        break;
      case FLOAT:
        op = Opcode.LOAD_FLOAT;
        break;
      case STRING:
        op = Opcode.LOAD_STRING;
        break;
      case BLOB:
        op = Opcode.LOAD_BLOB;
        break;
      case VOID:
        op = Opcode.LOAD_VOID;
        break;
      case FILE:
        op = Opcode.LOAD_FILE;
        break;
      default:
        // Can't retrieve other types
        op = null;
      }

    } else if (Types.isRef(srcType)) {
      op = Opcode.LOAD_REF;
    } else {
      op = null;
    }
    return op;
  }
  

  private static Opcode derefOpCode(Type type) {
    if (Types.isRef(type)) {
      Type refedType = type.memberType();
      if (Types.isScalarFuture(refedType)) {
        switch (refedType.primType()) {
        case BLOB:
          return Opcode.DEREF_BLOB;
        case FILE:
          return Opcode.DEREF_FILE;
        case BOOL:
          return Opcode.DEREF_BOOL;
        case FLOAT:
          return Opcode.DEREF_FLOAT;
        case INT:
          return Opcode.DEREF_INT;
        case STRING:
          return Opcode.DEREF_STRING;
        case VOID:
          throw new STCRuntimeError("Tried to dereference void");
        }
      }
    }
    return null;
  }


  public static Instruction futureSet(Var dst, Arg src) {
    assert(Types.isScalarFuture(dst.type()));
    switch (dst.type().primType()) {
    case BOOL:
      assert(src.isImmediateBool());
      return TurbineOp.assignBool(dst, src);
    case INT:
      assert(src.isImmediateInt());
      return TurbineOp.assignInt(dst, src);
    case FLOAT:
      assert(src.isImmediateFloat());
      return TurbineOp.assignFloat(dst, src);
    case STRING:
      assert(src.isImmediateString());
      return TurbineOp.assignString(dst, src);
    case BLOB:
      assert(src.isImmediateBlob());
      return TurbineOp.assignBlob(dst, src);
    case VOID:
      assert(src.isVar() && Types.isVoidVal(src.getVar()));
      return TurbineOp.assignVoid(dst, src);
    case FILE:
      assert(src.isVar() && Types.isFileVal(src.getVar()));
      return TurbineOp.assignFile(dst, src);
    default:
      throw new STCRuntimeError("method to set " +
          dst.type().typeName() + " is not known yet");
    }
  }

  public static ResultVal filenameCV(Arg outFilename, Var inFile) {
    assert(Types.isFile(inFile.type()));
    assert(outFilename.isVar());
    assert(Types.isString(outFilename.getVar().type()));
    return ResultVal.buildResult(Opcode.GET_FILENAME,
        Arrays.asList(inFile.asArg()), outFilename, false);
  }
  
  public static ResultVal filenameValCV(Arg file, Arg filenameVal) {
    assert(Types.isFile(file.type()));
    assert(filenameVal == null || filenameVal.isImmediateString());
    return ResultVal.buildResult(Opcode.GET_FILENAME_VAL,
                                            file, filenameVal, true);
  }

  public static ResultVal filenameLocalCV(Arg outFilename, Var inFile) {
    assert(inFile.type().assignableTo(Types.V_FILE));
    assert(outFilename.type().assignableTo(Types.V_STRING));
    return ResultVal.buildResult(Opcode.GET_LOCAL_FILENAME,
                    inFile.asArg().asList(), outFilename, true);
  }
  
  private static String formatFunctionCall(Opcode op, 
      String functionName, List<Var> outputs, List<Arg> inputs) {
    String result = op.toString().toLowerCase() + " " + functionName;
    result += " [";
    for (Var v: outputs) {
      result += " " + v.name();
    }
    result += " ] [";
    for (Arg a: inputs) {
      result += " " + a.toString();
    }
    result += " ]";
    return result;
  }
  
}


