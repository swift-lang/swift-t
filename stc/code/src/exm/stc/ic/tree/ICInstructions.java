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
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.CompileTimeArgs;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.ForeignFunctions.SpecialFunction;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Semantics;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.aliases.Alias;
import exm.stc.ic.componentaliases.Component;
import exm.stc.ic.componentaliases.ComponentAlias;
import exm.stc.ic.opt.valuenumber.ComputedValue;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ValLoc;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.refcount.RefCountsToPlace;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.Program;
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

    @Override
    public StatementType type() {
      return StatementType.INSTRUCTION;
    }
    @Override
    public Conditional conditional() {
      throw new STCRuntimeError("Not a conditional");
    }
    @Override
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
    @Override
    public abstract void renameVars(FnID function, Map<Var, Arg> renames,
                                    RenameMode mode);

    @Override
    public abstract String toString();

    @Override
    public void prettyPrint(StringBuilder sb, String indent) {
      sb.append(indent);
      sb.append(this.toString());
      sb.append("\n");
    }

    @Override
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

    /**
     * Class helping to specify how a variable should be handled when
     * making immediate
     */
    public static class MakeImmVar {
      public MakeImmVar(Var var, boolean fetch, boolean recursive,
          boolean initOutputMapping, boolean acquireWriteRefs) {
        super();
        this.var = var;
        this.fetch = fetch;
        this.recursive = recursive;
        this.preinitOutputMapping = initOutputMapping;
        this.acquireWriteRefs = acquireWriteRefs;
      }

      public static MakeImmVar in(Var var, boolean fetch, boolean recursive,
                                   boolean acquireWriteRefs) {
        return new MakeImmVar(var, fetch, recursive, false, acquireWriteRefs);
      }

      public static MakeImmVar out(Var var, boolean recursive, boolean initOutputMapping) {
        return new MakeImmVar(var, false, recursive, initOutputMapping, false);
      }

      public static final List<MakeImmVar> NONE = Collections.emptyList();;

      public final Var var;

      /** For inputs, if we should fetch (otherwise just wait) */
      public final boolean fetch;

      /** For inputs and outputs, if we should fetch recursively */
      public final boolean recursive;

      /** For outputs, whether output mapping should initialized
       * before calling this instruction */
      public final boolean preinitOutputMapping;

      /**
       * For inputs that are refs, if write refcounts should be acquired
       */
      public final boolean acquireWriteRefs;

      @Override
      public String toString() {
        return var.name() + " fetch: " + fetch + " recursive: " + recursive +
              " preinitOutputMapping: " + preinitOutputMapping +
              " acquireWriteRefs: " + acquireWriteRefs;
      }
    }

    public static class MakeImmRequest {
      /** Output variables to replace and store after changing instruction */
      public final List<MakeImmVar> out;

      /** Input variables to fetch before changing instruction */
      public final List<MakeImmVar> in;

      /** Where immediate code should run.  Default is local: in the current context */
      public final ExecTarget mode;

      private static final ExecTarget DEFAULT_MODE = ExecTarget.nonDispatchedAny();
      private static final boolean DEFAULT_RECURSIVE = false;
      private static final boolean DEFAULT_PREINIT_OUTPUT_MAPPING = false;
      private static final boolean DEFAULT_ACQUIRE_WRITE_REFS = false;

      public static MakeImmRequest fromVars(Var out, Var in) {
        return fromVars(out.asList(), in.asList());
      }

      public static MakeImmRequest fromVars(Var out, List<Var> in) {
        return fromVars(out.asList(), in);
      }


      public static MakeImmRequest fromVars(List<Var> out, Var in) {
        return fromVars(out, in.asList());
      }

      public static MakeImmRequest fromVars(List<Var> out, List<Var> in) {
        return MakeImmRequest.fromVars(out, in, null, DEFAULT_MODE,
            DEFAULT_RECURSIVE, DEFAULT_PREINIT_OUTPUT_MAPPING,
            DEFAULT_ACQUIRE_WRITE_REFS);
      }

      public static MakeImmRequest fromVars(List<Var> out,
          List<Var> in, ExecTarget mode) {
        return fromVars(out, in, null, mode,
            DEFAULT_RECURSIVE, DEFAULT_PREINIT_OUTPUT_MAPPING,
            DEFAULT_ACQUIRE_WRITE_REFS);
      }

      public static MakeImmRequest fromVars(List<Var> out, List<Var> in,
            List<Var> wait, ExecTarget mode, boolean recursive,
            boolean preinitOutputMapping, boolean acquireWriteRefs) {
        return new MakeImmRequest(buildOutList(out, recursive, preinitOutputMapping),
             buildInList(in, wait, recursive, acquireWriteRefs),
             mode);
      }

      public MakeImmRequest(List<MakeImmVar> out, List<MakeImmVar> in) {
        this(out, in, DEFAULT_MODE);
      }

      public MakeImmRequest(List<MakeImmVar> out, List<MakeImmVar> in,
          ExecTarget mode) {
        this.out = (out == null) ? MakeImmVar.NONE : out;
        this.in = (in == null) ? MakeImmVar.NONE : in ;
        this.mode = mode;
      }

      private static List<MakeImmVar> buildOutList(List<Var> out, boolean recursive,
          boolean initsOutputMapping) {
        List<MakeImmVar> l = new ArrayList<MakeImmVar>(out.size());
        if (out != null) {
          for (Var v: out) {
            l.add(MakeImmVar.out(v, recursive, initsOutputMapping));
          }
        }
        return l;
      }

      private static List<MakeImmVar> buildInList(List<Var> in, List<Var> wait,
          boolean recursive, boolean acquireWriteRefs) {
        List<MakeImmVar> l = new ArrayList<MakeImmVar>();
        if (in != null) {
          for (Var v: in) {
            l.add(MakeImmVar.in(v, true, recursive, acquireWriteRefs));
          }
        }
        if (wait != null) {
          for (Var v: wait) {
            l.add(MakeImmVar.in(v, false, recursive, acquireWriteRefs));
          }
        }
        return l;
      }

    }

    /**
     * Interface to let instruction logic create variables
     */
    public interface VarCreator {
      public Var createDerefTmp(Var toDeref);
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

    public static class Fetched<V> {
      public Fetched(Var original, V fetched) {
        super();
        this.original = original;
        this.fetched = fetched;
      }
      public final Var original;
      public final V fetched;

      /**
       *
       * @param original
       * @param fetched
       * @param includeAll if true, all were fetched.  If false, check
       *        fetched field of original
       * @return
       */
      public static <T> List<Fetched<T>> makeList(
          List<MakeImmVar> original, List<T> fetched,
          boolean includeAll) {
        // Handle nulls gracefully
        if (original == null) {
          original = Collections.emptyList();
        }
        if (fetched == null) {
          fetched = Collections.emptyList();
        }
        assert(original.size() >= fetched.size());

        int j = 0; // fetched index
        List<Fetched<T>> result = new ArrayList<Fetched<T>>(fetched.size());
        for (int i = 0; i < original.size(); i++) {
          MakeImmVar orig = original.get(i);
          if (includeAll || orig.fetch) {
            assert(j < fetched.size());
            result.add(new Fetched<T>(orig.var, fetched.get(j)));
            j++;
          }
        }

        // Check all were included
        assert(j == fetched.size()) : original + " " + fetched;
        return result;
      }

      public static <T> List<T> getFetched(List<Fetched<T>> fetched) {
        List<T> res = new ArrayList<T>(fetched.size());
        for (Fetched<T> f: fetched) {
          res.add(f.fetched);
        }
        return res;
      }

      public static <T> T findFetched(Collection<Fetched<T>> fetched, Var v) {
        for (Fetched<T> f: fetched) {
          if (v.equals(f.original)) {
            return f.fetched;
          }
        }
        return null;
      }

      /**
       * Find fetched and cast to var if returned
       * @param fetched
       * @param v
       * @return
       */
      public static Var findFetchedVar(Collection<Fetched<Arg>> fetched, Var v) {
        Arg res = findFetched(fetched, v);
        return (res == null) ? null : res.getVar();
      }

      @Override
      public String toString() {
        return "Fetched: " + original.toString() + " => " + fetched.toString();
      }
    }

    /**
     *
     * @param closedVars variables closed at point of current instruction
     * @param closedLocations abstract locations closed at point of current
     *          instruction
     * @param valueAvail variables where the retrieve resultis available at
     *                  current point (implies closed too)
     * @param waitForClose if true, allowed to (must don't necessarily
     *        have to) request that unclosed vars be waited for
     * @return null if it cannot be made immediate, if true,
     *            a list of vars that are the variables whose values are needed
     *            and output vars that need to be have value vars created
     */
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                      Set<ArgCV> closedLocations, Set<Var> valueAvail,
                      boolean waitForClose) {
      // Not implemented
      return null;
    }

    /**
     * Called to actually perform change requested
     * @param outVals any output values loaded
     * @param inValues any input values loaded
     * @return
     */
    public MakeImmChange makeImmediate(VarCreator creator,
                                       List<Fetched<Var>> outVals,
                                       List<Fetched<Arg>> inValues) {
      throw new STCRuntimeError("makeImmediate not valid  on " + type());
    }

    /**
     * @param prog the program.  Used to lookup function definitions
     * @return non-null the futures this instruction will block on
     *        it is ok if it forgets variables which aren't blocked on,
     *        but all variables returned must be blocked on
     */
    public abstract List<Var> getBlockingInputs(Program prog);

    /**
     * This must return information about the execution mode of the
     * instruction: whether it spawns asynchronous work and where it
     * expects to be run.
     * @return execution mode of instruction
     */
    public abstract ExecTarget execMode();
    /**
     * @return true if instruction is cheap: i.e. doesn't consume much CPU or IO
     *        time so doesn't need to be run in parallel
     */
    public abstract boolean isCheap();

    /**
     * @return true if instruction doesn't spawn or enable further work
     *           (e.g. by assigning future), so can be put off without problem
     */
    public abstract boolean isProgressEnabling();


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
      return Collections.emptyList();
    }

    /**
     * Return true if var is initialized by instruction
     * @param var
     * @return
     */
    public boolean isInitialized(Var var) {
      for (Var init: getInitialized()) {
        if (var.equals(init)) {
          return true;
        }
      }
      return false;
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
     * Specify more granular information about effect of instruction on
     * output variable.  If this returns non-null, it overrides info in
     * getModifiedOutputs().  Note that getModifiedOutputs() must be
     * specified for op regardless
     * @return list of components modified.
     */
    public List<Component> getModifiedComponents() {
      return null;
    }

    /**
     * @param fns map of functions (can optionally be null)
     * @return list of outputs for which previous value is read
     */
    public List<Var> getReadOutputs(Map<FnID, Function> fns) {
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
     * @return a list of all values computed by expression.  Each ComputedValue
     *        returned should have the out field set so we know where to find
     *        it
     */
    public abstract List<ValLoc> getResults();

    @Override
    public Statement cloneStatement() {
      return clone();
    }

    @Override
    public abstract Instruction clone();

    /**
     * @return vars with refcounts to be incremented: (read, write)
     *         NOTE: can include repeats
     */
    public Pair<List<VarCount>, List<VarCount>> inRefCounts(
        Map<FnID, Function> functions) {
      return Pair.create(VarCount.NONE, VarCount.NONE);
    }

    /**
     * @return vars with refcounts to be incremented: (read, write)
     *         NOTE: can include repeats
     */
    public Pair<List<VarCount>, List<VarCount>> outRefCounts(
                        Map<FnID, Function> functions) {
      return Pair.create(VarCount.NONE, VarCount.NONE);
    }

    /**
     * Try to piggyback increments or decrements to instruction.
     * Repeatedly called until it returns null;
     * @param increments count of increment or decrement operations per var
     * @param type
     * @return null if not successful, var for which piggyback occurred and change
     *        (-ive if decrement)
     *
     */
    public VarCount tryPiggyback(RefCountsToPlace increments, RefCountType type) {
      return null;
    }

    /**
     * Return list of all aliases created by function
     * @return
     */
    public List<Alias> getAliases() {
      // Default implementation: no aliases
      return Alias.NONE;
    }


    /**
     * If this instruction makes an output a part of another
     * variable such that modifying the output modifies something
     * else
     * @return empty list if nothing
     */
    public List<ComponentAlias> getComponentAliases() {
      // Default is nothing, few instructions do this
      return Collections.emptyList();
    }

    /**
     * @return true if side-effect or output modification is idempotent
     */
    public boolean isIdempotent() {
      return false;
    }
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
    public void renameVars(FnID function, Map<Var, Arg> renames,
                           RenameMode mode) {
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
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
        Set<ArgCV> closedLocations, Set<Var> valueAvail, boolean waitForClose) {
      return null;
    }

    @Override
    public List<Var> getBlockingInputs(Program prog) {
      return Var.NONE;
    }

    @Override
    public List<ValLoc> getResults() {
      return null;
    }

    @Override
    public Instruction clone() {
      return new Comment(this.text);
    }

    @Override
    public ExecTarget execMode() {
      return ExecTarget.syncAny();
    }

    @Override
    public boolean isCheap() {
      return true;
    }

    @Override
    public boolean isProgressEnabling() {
      return false;
    }

  }

  // Maximum number of array element CVs to insert
  public static final long MAX_ARRAY_ELEM_CVS = 128L;

  public static abstract class CommonFunctionCall extends Instruction {
    protected final FnID id;
    protected final List<Var> outputs;
    protected final List<Arg> inputs;
    protected final TaskProps props;
    protected final ForeignFunctions foreignFuncs; // Metadata about foreign funcs

    private final boolean hasUpdateableInputs;

    public CommonFunctionCall(Opcode op, FnID id,
        List<Var> outputs, List<Arg> inputs,
        TaskProps props, ForeignFunctions foreignFunctions) {
      super(op);
      assert(!id.equals(FnID.ENTRY_FUNCTION)) :
             "Can't call entry point";
      this.id = id;
      this.outputs = new ArrayList<Var>(outputs);
      this.inputs = new ArrayList<Arg>(inputs);
      this.props = props;
      this.foreignFuncs = foreignFunctions;

      boolean hasUpdateableInputs = false;
      for(Arg v: inputs) {
        assert(v != null);
        if (v.isVar() && Types.isScalarUpdateable(v.getVar())) {
          hasUpdateableInputs = true;
        }
      }
      this.hasUpdateableInputs = hasUpdateableInputs;
    }

    public FnID functionID() {
      return id;
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

    /**
    * @return function output arguments
    */
    public List<Var> getFunctionOutputs() {
      return Collections.unmodifiableList(outputs);
    }


    public Var getFunctionOutput(int i) {
      return outputs.get(i);
    }

    @Override
    public List<Arg> getInputs() {
      List<Arg> inputVars = new ArrayList<Arg>(inputs);
      if (treatUpdInputsAsOutputs()) {
        // Remove updateable inputs from list
        ListIterator<Arg> it = inputVars.listIterator();
        while (it.hasNext()) {
          Arg in = it.next();
          if (in.isVar() && Types.isScalarUpdateable(in.getVar())) {
            it.remove();
          }
        }
      }
      // Need to include any properties as inputs
      if (props != null) {
        inputVars.addAll(props.values());
      }
      return inputVars;
    }

    /**
     * Return subset of input list which are variables
     * @param noValues
     * @return
     */
    protected List<Var> varInputs(boolean noValues) {
      List<Var> varInputs = new ArrayList<Var>();
      for (Arg input: inputs) {
        if (input.isVar()) {
          if (!noValues || !Types.isPrimValue(input)) {
            varInputs.add(input.getVar());
          }
        }
      }
      return varInputs;
    }

    @Override
    public List<Var> getOutputs() {
      if (!treatUpdInputsAsOutputs()) {
        return Collections.unmodifiableList(outputs);
      } else {
        List<Var> realOutputs = new ArrayList<Var>();
        realOutputs.addAll(outputs);
        addAllUpdateableInputs(realOutputs);
        return realOutputs;
      }
    }

    private void addAllUpdateableInputs(List<Var> realOutputs) {
      for (Arg in: inputs) {
        if (in.isVar() && Types.isScalarUpdateable(in.getVar())) {
          realOutputs.add(in.getVar());
        }
      }
    }

    @Override
    public List<Var> getReadOutputs(Map<FnID, Function> fns) {
      switch (op) {
        case CALL_FOREIGN:
        case CALL_FOREIGN_LOCAL: {
          List<Var> result = new ArrayList<Var>();
          // Only some output types might be read
          for (Var o: outputs) {
            if (Types.hasReadableSideChannel(o)) {
              result.add(o);
            }
          }
          if (treatUpdInputsAsOutputs()) {
            addAllUpdateableInputs(result);
          }
          return result;
        }
        case CALL_LOCAL:
        case CALL_LOCAL_CONTROL:
        case CALL_SYNC:
        case CALL_CONTROL: {

          List<Var> result = new ArrayList<Var>();
          Function f = fns == null ? null : fns.get(this.id);
          if (f != null )
            // && f.getId().equals("f"))
            System.out.println(f.getId());
          for (int i = 0; i < outputs.size(); i++) {
            Var o = outputs.get(i);

            // Check to see if function might read the output
            if (Types.hasReadableSideChannel(o.type()) &&
                (f == null || !f.isOutputWriteOnly(i))) {
              result.add(o);
            }
          }

          if (treatUpdInputsAsOutputs()) {
            addAllUpdateableInputs(result);
          }
          return result;
        }
        default:
          throw new STCRuntimeError("unexpected op: " + op);
      }
    }


    @Override
    public String shortOpName() {
      return op.toString().toLowerCase() + "-" + id.uniqueName();
    }

    @Override
    public TaskProps getTaskProps() {
      // Return null if not found
      return props;
    }

    /**
     * @param fnCallOp
     * @return true if arguments should be local values
     */
    public static boolean acceptsLocalValArgs(Opcode fnCallOp) {
      switch (fnCallOp) {
        case CALL_CONTROL:
        case CALL_SYNC:
        case CALL_LOCAL:
        case CALL_LOCAL_CONTROL:
        case CALL_FOREIGN:
          return false;
        case CALL_FOREIGN_LOCAL:
          return true;
        default:
          throw new STCRuntimeError("Unexpected op: " + fnCallOp);
      }
    }

    private boolean isCopyFunction() {
      if (foreignFuncs.isCopyFunction(id)) {
        return true;
      } else if (foreignFuncs.isMinMaxFunction(id)
              && getInput(0).equals(getInput(1))) {
        return true;
      }
      return false;
    }

    @Override
    public boolean hasSideEffects() {
      return !foreignFuncs.isPure(id);
    }

    @Override
    public List<ValLoc> getResults() {
      if (foreignFuncs.isPure(id)) {
        if (isCopyFunction()) {
          // Handle copy as a special case
          return ValLoc.makeCopy(getOutput(0), getInput(0),
                                 IsAssign.TO_LOCATION).asList();
        } else {
          List<ValLoc> res = new ArrayList<ValLoc>();
          for (int output = 0; output < getOutputs().size(); output++) {
            Closed outputClosed = Closed.MAYBE_NOT;// safe assumption

            List<Arg> inputs = getInputs();
            List<Arg> cvArgs = new ArrayList<Arg>(inputs.size() + 1);
            cvArgs.addAll(inputs);
            if (foreignFuncs.isCommutative(this.id)) {
              // put in canonical order
              Collections.sort(cvArgs);
            }
            cvArgs.add(Arg.newInt(output)); // Disambiguate outputs

            res.add(ValLoc.buildResult(this.op, id, cvArgs,
                getOutput(output).asArg(), outputClosed, IsAssign.TO_LOCATION));
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
    private void addSpecialCVs(List<ValLoc> cvs) {
      if (isImpl(SpecialFunction.INPUT_FILE) ||
          isImpl(SpecialFunction.UNCACHED_INPUT_FILE) ||
          isImpl(SpecialFunction.INPUT_URL)) {
        // Track that the output variable has the filename of the input, if
        // the output was unmapped (otherwise it has the mapped name!)
        // This is compatible with UNCACHED_INPUT_FILE preventing caching,
        // as we still assume that the input_file function is impure
        Var file = getOutput(0);
        if (file.isMapped() == Ternary.FALSE) {
          if (op == Opcode.CALL_FOREIGN) {
            cvs.add(ValLoc.makeFilename(getInput(0), getOutput(0), IsAssign.TO_VALUE));
          } else if (op == Opcode.CALL_FOREIGN_LOCAL){
            // Don't mark as IsAssign since standard cv catches this
            cvs.add(ValLoc.makeFilenameLocal(getInput(0), getOutput(0),
                                             IsAssign.TO_VALUE));
          }
        }
      } else if (op == Opcode.CALL_FOREIGN_LOCAL &&
          (isImpl(SpecialFunction.RANGE) ||
           isImpl(SpecialFunction.RANGE_STEP))) {
        addRangeCVs(cvs);
      } else if (isImpl(SpecialFunction.SIZE)) {
        cvs.add(makeContainerSizeCV(IsAssign.NO));
      } else if (isImpl(SpecialFunction.CONTAINS)) {
        cvs.add(makeArrayContainsCV(IsAssign.NO));
      }
    }

    /**
     * @return true if this instruction calls any of the given special functions
     */
    public boolean isImpl(SpecialFunction ...specials) {
      return isImpl(foreignFuncs, this.id, specials);
    }

    public static boolean isImpl(ForeignFunctions foreignFuncs,
                 FnID id, SpecialFunction ...specials) {
      for (SpecialFunction special: specials) {
        if (foreignFuncs.isSpecialImpl(id, special)) {
          return true;
        }
      }
      return false;
    }

    private void addRangeCVs(List<ValLoc> cvs) {
      boolean allValues = true;
      long start = 0, end = 0, step = 1;

      if (getInput(0).isInt()) {
        start = getInput(0).getInt();
      } else {
        allValues = false;
      }
      if (getInput(1).isInt()) {
        end = getInput(1).getInt();
      } else {
        allValues = false;
      }
      if (isImpl(SpecialFunction.RANGE_STEP)) {
        if (getInput(2).isInt()) {
          step = getInput(2).getInt();
        } else {
          allValues = false;
        }
      }
      if (allValues) {
        // We can work out array contents
        long arrSize = Math.max(0, (end - start) / step + 1);
        Var arr = getOutput(0);
        cvs.add(ValLoc.makeContainerSizeCV(arr, Arg.newInt(arrSize),
                                false, IsAssign.NO));
        // add array elements up to some limit
        int max_elems = 64;
        for (long val = start, key = 0;
                  val <= end && key < max_elems;
                  val += step, key++) {
          cvs.add(ValLoc.makeArrayResult(arr, Arg.newInt(key),
                      Arg.newInt(val), true, IsAssign.TO_VALUE));
        }
      }
    }

    private ValLoc makeContainerSizeCV(IsAssign isAssign) {
      boolean isFuture;
      if (Types.isInt(getOutput(0))) {
        isFuture = true;
      } else {
        assert(Types.isIntVal(getOutput(0)));
        isFuture = false;
      }
      return ValLoc.makeContainerSizeCV(getInput(0).getVar(),
                      getOutput(0).asArg(), isFuture, isAssign);
    }


    private ValLoc makeArrayContainsCV(IsAssign isAssign) {
      boolean isFuture;
      if (Types.isBool(getOutput(0))) {
        isFuture = true;
      } else {
        assert(Types.isBoolVal(getOutput(0)));
        isFuture = false;
      }
      return ValLoc.makeArrayContainsCV(getInput(0).getVar(), getInput(1),
                      getOutput(0).asArg(), isFuture, isAssign);
    }

    /**
     * Check if we should try to constant fold. To enable constant
     * folding for a funciton it needs ot have an entry here and
     * in tryConstantFold()
     * @param cv
     * @return
     */
    public static boolean canConstantFold(ForeignFunctions foreignFuncs,
                                          ComputedValue<?> cv) {
      return isImpl(foreignFuncs, (FnID)cv.subop(), SpecialFunction.ARGV);
    }

    /**
     * Try to constant fold any special functions.
     * @param foreignFuncs
     * @param cv
     * @param inputs
     * @return a value arg if successful, null if not
     */
    public static Arg tryConstantFold(ForeignFunctions foreignFuncs,
        ComputedValue<?> cv, List<Arg> inputs) {
      FnID id = (FnID)cv.subop();
      if (isImpl(foreignFuncs, id, SpecialFunction.ARGV)) {
        Arg argName = inputs.get(0);
        if (argName.isString()) {
          String val = CompileTimeArgs.lookup(argName.getString());
          if (val != null) {
            // Success!
            return Arg.newString(val);
          }
        }
      }
      return null;
    }

    @Override
    public void renameVars(FnID function, Map<Var, Arg> renames,
                           RenameMode mode) {
      if (mode == RenameMode.REPLACE_VAR || mode == RenameMode.REFERENCE) {
        ICUtil.replaceVarsInList(renames, outputs, false);
      }
      ICUtil.replaceArgsInList(renames, inputs, false);
      if (props != null) {
        ICUtil.replaceArgValsInMap(renames, props);
      }
    }

    private boolean treatUpdInputsAsOutputs() {
      return hasUpdateableInputs && RefCounting.WRITABLE_UPDATEABLE_INARGS;
    }

    @Override
    public Pair<List<VarCount>, List<VarCount>> inRefCounts(
                            Map<FnID, Function> functions) {
      switch (op) {
        case CALL_FOREIGN:
        case CALL_FOREIGN_LOCAL:
        case CALL_CONTROL:
        case CALL_LOCAL:
        case CALL_LOCAL_CONTROL: {
          List<VarCount> readIncr = new ArrayList<VarCount>();
          List<VarCount> writeIncr = new ArrayList<VarCount>();
          for (Arg inArg: inputs) {
            if (inArg.isVar()) {
              Var inVar = inArg.getVar();
              if (RefCounting.trackReadRefCount(inVar)) {
                readIncr.add(VarCount.one(inVar));
              }
              if (Types.isScalarUpdateable(inVar) &&
                  treatUpdInputsAsOutputs()) {
                writeIncr.add(VarCount.one(inVar));
              }
            }
          }
          for (int i = 0; i < outputs.size(); i++) {
            Var outVar = outputs.get(i);
            if (RefCounting.trackWriteRefCount(outVar)) {
              writeIncr.add(VarCount.one(outVar));
            }
            boolean readRC = false;
            if (op != Opcode.CALL_FOREIGN &&
                op != Opcode.CALL_FOREIGN_LOCAL) {
              Function f = functions.get(this.id);
              boolean writeOnly = f.isOutputWriteOnly(i);

              // keep read references to output vars
              if (!writeOnly && RefCounting.trackReadRefCount(outVar)) {
                readRC = true;
              }
            }
            if (readRC && RefCounting.trackReadRefCount(outVar)) {
              readIncr.add(VarCount.one(outVar));
            }
          }
          return Pair.create(readIncr, writeIncr);
        }
        case CALL_SYNC:
          // Sync calls must acquire their own references
          return Pair.create(VarCount.NONE, VarCount.NONE);
        default:
          throw new STCRuntimeError("Unexpected function type: " + op);
      }
    }
  }

  public static class FunctionCall extends CommonFunctionCall {
    private final List<Boolean> closedInputs; // which inputs are closed

    private FunctionCall(Opcode op, FnID id,
        List<Var> outputs, List<Arg> inputs,
        TaskProps props, ForeignFunctions foreignFunctions) {
      super(op, id, outputs, inputs, props, foreignFunctions);
      if (op != Opcode.CALL_FOREIGN && op != Opcode.CALL_CONTROL &&
          op != Opcode.CALL_SYNC && op != Opcode.CALL_LOCAL &&
          op != Opcode.CALL_LOCAL_CONTROL) {
        throw new STCRuntimeError("Tried to create function call"
            + " instruction with invalid opcode");
      }
      this.closedInputs = new ArrayList<Boolean>(inputs.size());
      for (int i = 0; i < inputs.size(); i++) {
        this.closedInputs.add(false);
      }
      assert(props != null);

      for(Var v: outputs) {
        assert(v != null);
      }
    }

    public static FunctionCall createFunctionCall(
        FnID id, List<Var> outputs, List<Arg> inputs,
        ExecTarget mode, TaskProps props, ForeignFunctions foreignFuncs) {
      Opcode op;
      ExecContext targetCx = mode.targetContext();
      if (!mode.isAsync()) {
        assert(targetCx != null && targetCx.isControlContext()) : mode;
        op = Opcode.CALL_SYNC;
      } else if (mode.isDispatched()) {
        assert(targetCx != null && targetCx.isControlContext()) : mode;
        op = Opcode.CALL_CONTROL;
      } else {
        if (targetCx.isWildcardContext()) {
          op = Opcode.CALL_LOCAL;
        } else if (targetCx.isControlContext()) {
          op = Opcode.CALL_LOCAL_CONTROL;
        } else {
          throw new STCRuntimeError("Unexpected: " + mode);
        }
      }
      return new FunctionCall(op, id, outputs, inputs, props, foreignFuncs);
    }

    public static FunctionCall createBuiltinCall(
        FnID id,
        List<Var> outputs, List<Arg> inputs,
        TaskProps props, ForeignFunctions foreignFuncs) {
      return new FunctionCall(Opcode.CALL_FOREIGN, id,
                              outputs, inputs, props, foreignFuncs);
    }

    @Override
    public String toString() {
      String result = op.toString().toLowerCase() + " " + id.uniqueName();
      if (!id.uniqueName().equals(id.originalName())) {
        result += "(" + id.originalName() + ")";
      }
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
        gen.callForeignFunctionWrapped(id, outputs, inputs, props);
        break;
      case CALL_SYNC:
      case CALL_CONTROL:
      case CALL_LOCAL:
      case CALL_LOCAL_CONTROL:
          List<Boolean> blocking = info.getBlockingInputVector(id);
        assert(blocking != null && blocking.size() == inputs.size()) :
          this + "; blocking: " + blocking;
        List<Boolean> needToBlock = new ArrayList<Boolean>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
          needToBlock.add(blocking.get(i) && (!this.closedInputs.get(i)));
        }

        gen.functionCall(id, outputs, inputs, needToBlock,
                                            execMode(), props);
        break;
      default:
        throw new STCRuntimeError("Huh?");
      }
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
        Set<ArgCV> closedLocations, Set<Var> valueAvail, boolean waitForClose) {
      if (isImpl(SpecialFunction.SIZE)) {
        if (waitForClose || allInputsClosed(closedVars)) {
          // Input array closed, can lookup right away
          return MakeImmRequest.fromVars(getOutput(0), getInput(0).getVar());
        }
        return null;
      } else if (isImpl(SpecialFunction.CONTAINS)) {
        if (waitForClose || allInputsClosed(closedVars)) {
          // Need input array to be closed, and need value for index
          return MakeImmRequest.fromVars(getOutput(0), getInput(1).getVar());
        }
        return null;
      }

      // By default, need all arguments to be closed
      boolean allNeededClosed = waitForClose ||
          (allInputsClosed(closedVars) &&
              allOutputSideChannelsClosed(closedVars, closedLocations));

      if (allNeededClosed && (foreignFuncs.hasOpEquiv(this.id)
                || foreignFuncs.hasLocalImpl(this.id))) {
        ExecTarget mode = foreignFuncs.getTaskMode(this.id);

        // True unless the function alters mapping itself
        boolean preinitOutputMapping = true;

        // TODO: we're going to need to conditionally fetch this
        if (foreignFuncs.canInitOutputMapping(this.id)) {
          preinitOutputMapping = false;
        }

        // All args are closed!
        return MakeImmRequest.fromVars(
            Collections.unmodifiableList(this.outputs),
            Collections.unmodifiableList(this.varInputs(true)),
            Var.NONE,
            mode, recursiveInOut(foreignFuncs, this.op, this.id),
            preinitOutputMapping, false);

      }
      return null;
    }

    private boolean allInputsClosed(Set<Var> closedVars) {
      for (int i = 0; i < this.inputs.size(); i++) {
        if (!inputClosed(closedVars, i)) {
          return false;
        }
      }
      return true;
    }

    private boolean inputClosed(Set<Var> closedVars, int i) {
      Arg in = this.inputs.get(i);
      if (!in.isVar()) {
        return true;
      }
      if (closedVars.contains(in.getVar())) {
        this.closedInputs.set(i, true);
        return true;
      } else {
        return false;
      }
    }

    /**
     * Check if side channels from mapped variables are closed.
     * @param closedVars
     * @return
     */
    private boolean allOutputSideChannelsClosed(Set<Var> closedVars,
        Set<ArgCV> closedLocations) {
      for (Var out: this.outputs) {
        if (Types.isFile(out)) {
          // Need to wait for filename, unless unmapped
          if (!Semantics.outputMappingAvail(closedVars, closedLocations,
                                            out)) {
            return false;
          }
        }
      }
      return true;
    }

    @Override
    public MakeImmChange makeImmediate(VarCreator creator,
                                       List<Fetched<Var>> outVars,
                                       List<Fetched<Arg>> values) {
      if (isImpl(SpecialFunction.SIZE)) {
        // Don't use fetched array value
        Var newOut = outVars.get(0).fetched;
        Var arr = getInput(0).getVar();
        return new MakeImmChange(TurbineOp.containerSize(newOut, arr));
      } else if (isImpl(SpecialFunction.CONTAINS)) {
        // Don't use fetched array value
        Var newOut = outVars.get(0).fetched;
        Var arr = getInput(0).getVar();
        Var keyFuture = getInput(1).getVar();
        Arg key = Fetched.findFetched(values, keyFuture);
        return new MakeImmChange(TurbineOp.arrayContains(newOut, arr, key));
      }


      // Discard non-future inputs.  These are things like priorities or
      // targets which do not need to be retained for the local version
      List<Var> retainedInputs = varInputs(true);
      assert(values.size() == retainedInputs.size());

      Instruction inst;
      List<Arg> fetchedVals = Fetched.getFetched(values);
      if (foreignFuncs.hasOpEquiv(id)) {
        BuiltinOpcode newOp = foreignFuncs.getOpEquiv(id);
        assert(newOp != null);

        if (outputs.size() == 1) {
          checkSwappedOutput(outputs.get(0), outVars.get(0).fetched);
          inst = Builtin.createLocal(newOp, outVars.get(0).fetched,
                                                       fetchedVals);
        } else {
          assert(outputs.size() == 0);
          inst = Builtin.createLocal(newOp, null, fetchedVals);
        }
      } else {
        FnID localFunctionID = foreignFuncs.getLocalImpl(id);
        assert(localFunctionID != null);
        for (int i = 0; i < outputs.size(); i++) {
          assert(outputs.get(i).equals(outVars.get(i).original));
          checkSwappedOutput(outputs.get(i), outVars.get(i).fetched);
        }
        List<Var> fetchedOut = Fetched.getFetched(outVars);
        inst = new LocalFunctionCall(localFunctionID, fetchedVals, fetchedOut,
                                     foreignFuncs);
      }
      return new MakeImmChange(inst);
    }

    /**
     * Check that old output type was swapped correctly for
     * making immediate
     * @param oldOut
     * @param newOut
     */
    private void checkSwappedOutput(Var oldOut, Var newOut) {
      Type exp = Types.retrievedType(oldOut,
                  recursiveInOut(foreignFuncs, op, id));
      assert(exp.equals(newOut.type()));
    }

    @Override
    public List<Var> getBlockingInputs(Program prog) {
      List<Var> blocksOn = new ArrayList<Var>();
      if (op == Opcode.CALL_FOREIGN) {
        for (Arg in: inputs) {
          if (in.isVar()) {
            Var v = in.getVar();
            if (Types.isPrimFuture(v) || Types.isRef(v)) {
              // TODO: this is a conservative idea of which ones are set
              blocksOn.add(v);
            }
          }
        }
      } else if (op == Opcode.CALL_SYNC) {
        // Can't block because we need to enter the function immediately
        return Var.NONE;
      } else if (op == Opcode.CALL_CONTROL ) {
        Function f = prog.lookupFunction(id);

        List<Boolean> blocking = f.getBlockingInputVector();
        List<Var> blockingVars = new ArrayList<Var>();
        assert(blocking.size() == inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
          if (blocking.get(i)) {
            // Add the input. This input must be a variable as
            // otherwise we couldn't be blocking on it
            blockingVars.add(inputs.get(i).getVar());
          }
        }
        return blockingVars;
      }
      return blocksOn;
    }

    /**
     * Returns true if we recursively pack and unpack inputs and outputs for
     * this type of function call if converting to local version
     * @param op
     * @param frontendName frontendName, or null if unknown
     */
    private static boolean recursiveInOut(ForeignFunctions foreignFuncs,
                                        Opcode op, FnID id) {
      switch (op) {
        case CALL_SYNC:
        case CALL_LOCAL:
        case CALL_LOCAL_CONTROL:
        case CALL_CONTROL:
        case CALL_FOREIGN:
          // Foreign functions get unpack representations
          // Note: we may be calling wrapper of foreign function -
          // can't assume that it is not foreign based on opcode
          if (foreignFuncs.isForeignFunction(id)) {
            return foreignFuncs.recursivelyUnpackedInOut(id);
          } else {
            return false;
          }
        default:
          throw new STCRuntimeError("Unexpected function call opcode: " + op);
      }
    }

    @Override
    public ExecTarget execMode() {
      switch (op) {
        case CALL_SYNC:
          return ExecTarget.syncAny();
        case CALL_LOCAL:
          return ExecTarget.nonDispatchedAny();
        case CALL_LOCAL_CONTROL:
          return ExecTarget.nonDispatchedControl();
        case CALL_FOREIGN:
          return foreignFuncs.getTaskMode(id);
        case CALL_CONTROL:
          return ExecTarget.dispatchedControl();
        default:
          throw new STCRuntimeError("Unexpected function call opcode: " + op);
      }
    }

    @Override
    public boolean isCheap() {
      if (op == Opcode.CALL_SYNC) {
        // Not sure how much work it will involve without looking at
        // function def
        return false;
      } else {
        // Just spawns the task here
        return true;
      }
    }

    @Override
    public boolean isProgressEnabling() {
      return true; // Function may have side-effects, etc
    }

    @Override
    public Instruction clone() {
      // Variables are immutable so just need to clone lists
      return new FunctionCall(op, id,
          new ArrayList<Var>(outputs), new ArrayList<Arg>(inputs),
          props.clone(), foreignFuncs);
    }
  }

  public static class LocalFunctionCall extends CommonFunctionCall {

    public LocalFunctionCall(FnID id,
        List<Arg> inputs, List<Var> outputs, ForeignFunctions foreignFuncs) {
      super(Opcode.CALL_FOREIGN_LOCAL, id, outputs, inputs, null, foreignFuncs);
      for(Var v: outputs) {
        assert(v != null);
      }

      for(Arg a: inputs) {
        assert(a != null);
      }
    }

    @Override
    public String toString() {
      return formatFunctionCall(op, id.uniqueName(), outputs, inputs);
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.callForeignFunctionLocal(id, outputs, inputs);
    }

    @Override
    public List<Var> getInitialized() {
      return Collections.emptyList();
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
        Set<ArgCV> closedLocations, Set<Var> valueAvail, boolean waitForClose) {
      return null; // already immediate
    }

    @Override
    public List<Var> getBlockingInputs(Program prog) {
      // doesn't take futures as args
      return Var.NONE;
    }

    @Override
    public ExecTarget execMode() {
      ExecTarget fnMode = foreignFuncs.getTaskMode(id);

      // Executes synchronously in desired context
      return ExecTarget.sync(fnMode.targetContext());
    }

    @Override
    public boolean isCheap() {
      ExecTarget fnMode = foreignFuncs.getTaskMode(id);
      // The logic is that any functions which were designated to execute
      // non-locally must involve some amount of work.
      return !fnMode.isDispatched();
    }

    @Override
    public boolean isProgressEnabling() {
      // Only assigns value
      return false;
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
      return new LocalFunctionCall(id,
          new ArrayList<Arg>(inputs), new ArrayList<Var>(outputs),
          foreignFuncs);
    }
  }

  public static class ExecExternal extends Instruction {
    private Arg cmd;
    private final ArrayList<Arg> inFiles;
    private final ArrayList<Var> outFiles;
    private final ArrayList<Arg> args;
    private final Redirects<Arg> redirects;
    private final boolean hasSideEffects;
    private final boolean deterministic;

    public ExecExternal(Arg cmd, List<Arg> inFiles, List<Var> outFiles,
               List<Arg> args, Redirects<Arg> redirects,
               boolean hasSideEffects, boolean deterministic) {
      super(Opcode.EXEC);
      this.cmd = cmd;
      this.inFiles = new ArrayList<Arg>(inFiles);
      this.outFiles = new ArrayList<Var>(outFiles);
      this.args = new ArrayList<Arg>(args);
      this.redirects = redirects.clone();
      this.deterministic = deterministic;
      this.hasSideEffects = hasSideEffects;
    }

    @Override
    public void renameVars(FnID function, Map<Var, Arg> renames,
                           RenameMode mode) {
      cmd = ICUtil.replaceArg(renames, cmd, false);
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
      res.append(formatFunctionCall(op, cmd.toString(), outFiles, args));
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
      gen.execExternal(cmd, args, outFiles, inFiles,
                  redirects, hasSideEffects, deterministic);
    }

    @Override
    public List<Arg> getInputs() {
      ArrayList<Arg> res = new ArrayList<Arg>();
      res.add(cmd);
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
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
        Set<ArgCV> closedLocations, Set<Var> valueAvail, boolean waitForClose) {
      // Don't support reducing this
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(VarCreator creator,
                                       List<Fetched<Var>> outVals,
                                       List<Fetched<Arg>> inValues) {
      // Already immediate
      return null;
    }

    @Override
    public List<Var> getBlockingInputs(Program prog) {
      // This instruction runs immediately: we won't actually block on any inputs

      // However, the compiler should act as if we depend on input file vars
      return ICUtil.extractVars(inFiles);
    }

    @Override
    public ExecTarget execMode() {
      return ExecTarget.sync(ExecContext.defaultWorker());
    }

    @Override
    public boolean isCheap() {
      return false;
    }

    @Override
    public boolean isProgressEnabling() {
      return false;
    }

    @Override
    public List<Var> getClosedOutputs() {
      return getOutputs();
    }

    @Override
    public List<ValLoc> getResults() {
      if (deterministic) {
        List<ValLoc> cvs = new ArrayList<ValLoc>(outFiles.size());
        for (int i = 0; i < outFiles.size(); i++) {
          List<Arg> cvArgs = new ArrayList<Arg>(args.size() + 1);
          cvArgs.add(cmd);
          cvArgs.addAll(args);
          cvArgs.add(Arg.newInt(i)); // Disambiguate outputs
          // Unique key for cv includes number of output
          // Output file should be closed after external program executes
          ValLoc cv = ValLoc.buildResult(op, ComputedValue.NO_SUBOP,
                     cvArgs, outFiles.get(i).asArg(), Closed.YES_NOT_RECURSIVE,
                     IsAssign.TO_LOCATION);
          cvs.add(cv);
        }
        return cvs;
      } else {
        return null;
      }
    }

    @Override
    public Instruction clone() {
      return new ExecExternal(cmd, inFiles, outFiles,
              args, redirects, hasSideEffects, deterministic);
    }

  }

  public static class LoopContinue extends Instruction {
    private final ArrayList<Arg> newLoopVars;
    private final ArrayList<Var> loopUsedVars;
    private final ArrayList<Boolean> blockingVars;
    private final ArrayList<Boolean> closedVars;

    public LoopContinue(List<Arg> newLoopVars,
                        List<Var> loopUsedVars,
                        List<Boolean> blockingVars,
                        List<Boolean> closedVars) {
      super(Opcode.LOOP_CONTINUE);
      this.newLoopVars = new ArrayList<Arg>(newLoopVars);
      this.loopUsedVars = new ArrayList<Var>(loopUsedVars);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
      this.closedVars = new ArrayList<Boolean>(closedVars);
    }


    public LoopContinue(List<Arg> newLoopVars,
                        List<Var> loopUsedVars,
                        List<Boolean> blockingVars) {
      this(newLoopVars, loopUsedVars, blockingVars,
           initClosedVars(newLoopVars));
    }

    private static List<Boolean> initClosedVars(List<Arg> vars) {
      ArrayList<Boolean> res = new ArrayList<Boolean>(vars.size());
      for (int i = 0; i < vars.size(); i++) {
        res.add(false);
      }
      return res;
    }

    public void setNewLoopVar(int index, Arg newVal) {
      this.newLoopVars.set(index, newVal);
    }


    public void setBlocking(int i, boolean b) {
      this.blockingVars.set(i, b);
    }

    @Override
    public void renameVars(FnID function, Map<Var, Arg> renames,
                           RenameMode mode) {
      ICUtil.replaceArgsInList(renames, newLoopVars, false);
      if (mode == RenameMode.REFERENCE || mode == RenameMode.REPLACE_VAR) {
        ICUtil.replaceVarsInList(renames, loopUsedVars, true);
      }
    }

    @SuppressWarnings("unlikely-arg-type")
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
      sb.append(" [");
      ICUtil.prettyPrintArgList(sb, this.newLoopVars);
      sb.append("] #passin[");
      ICUtil.prettyPrintVarList(sb, this.loopUsedVars);
      sb.append("] #blocking[");
      ICUtil.prettyPrintList(sb, this.blockingVars);
      sb.append("] #closed[");
      ICUtil.prettyPrintList(sb, this.closedVars);
      sb.append("]");
      return sb.toString();
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      List<Boolean> waitFor = new ArrayList<Boolean>(this.blockingVars.size());
      // See if we need to block on all inputs
      Set<Var> alreadySeen = new HashSet<Var>();

      for (int i = 0; i < this.blockingVars.size(); i++) {
        // Add those that we need to wait for and that aren't closed
        Arg initVal = this.newLoopVars.get(i);
        boolean mustWait = initVal.isVar() &&
            Types.canWaitForFinalize(initVal.getVar()) &&
            this.blockingVars.get(i) && !this.closedVars.get(i);
        boolean newMustWait = mustWait && !alreadySeen.contains(initVal.getVar());
        waitFor.add(newMustWait);
        if (newMustWait) {
          alreadySeen.add(initVal.getVar());
        }
      }
      gen.loopContinue(this.newLoopVars, this.loopUsedVars, waitFor);
    }

    public Arg getNewLoopVar(int i) {
      return newLoopVars.get(i);
    }

    @Override
    public List<Arg> getInputs() {
      // need to make sure that these variables are avail in scope
      ArrayList<Arg> res = new ArrayList<Arg>();
      for (Arg v: newLoopVars) {
        res.add(v);
      }

      for (Var uv: loopUsedVars) {
        Arg uva = uv.asArg();
        if (!res.contains(uva)) {
          res.add(uva);
        }
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
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
        Set<ArgCV> closedLocations, Set<Var> valueAvail, boolean waitForClose) {
      // Variables we need to wait for to make immediate
      List<Var> waitForInputs = new ArrayList<Var>();

      for (int i = 0; i < this.newLoopVars.size(); i++) {
        Arg v = this.newLoopVars.get(i);
        if (v.isConst() || closedVars.contains(v.getVar())) {
          // Mark as closed
          this.closedVars.set(i, true);
        }

        if (this.blockingVars.get(i)) {
          if (this.closedVars.get(i)) {
            // TODO: if we were actually changing instruction,
            //      could request value here.  Since we're not changing,
            //      requesting value and doing nothing with it would result
            //      in infinite loop
          } else if (waitForClose && v.isVar()) {
              // Would be nice to have closed
              waitForInputs.add(v.getVar());
          }
        }
      }

      // TODO: not actually changing instruction - only change if
      //      there are additional things we want to wait for
      if (waitForInputs.isEmpty()) {
        return null;
      } else {
        return MakeImmRequest.fromVars(
                Var.NONE, waitForInputs,
                ExecTarget.nonDispatchedAny());
      }
    }

    @Override
    public MakeImmChange makeImmediate(VarCreator creator,
        List<Fetched<Var>> outVals,
        List<Fetched<Arg>> inValues) {
      // TODO: we waited for vars, for now don't actually change instruction
      return new MakeImmChange(this);
    }

    @Override
    public Pair<List<VarCount>, List<VarCount>> inRefCounts(
        Map<FnID, Function> functions) {
      List<VarCount> writeRefs = new ArrayList<VarCount>();
      for (Arg loopVar: newLoopVars) {
        if (loopVar.isVar()) {
          writeRefs.add(VarCount.one(loopVar.getVar()));
        }
      }
      // Increment variables passed to next iter
      return Pair.create(writeRefs, VarCount.NONE);
    }

    @Override
    public List<Var> getBlockingInputs(Program prog) {
      return Var.NONE;
    }


    public boolean isLoopVarClosed(int i) {
      return closedVars.get(i);
    }


    public void setLoopVarClosed(int i, boolean value) {
      closedVars.set(i, value);
    }


    @Override
    public ExecTarget execMode() {
      return ExecTarget.dispatchedControl();
    }

    @Override
    public boolean isCheap() {
      return true;
    }

    @Override
    public boolean isProgressEnabling() {
      return true;
    }

    public void setLoopUsedVars(Collection<Var> variables) {
      loopUsedVars.clear();
      loopUsedVars.addAll(variables);
    }

    @Override
    public List<ValLoc> getResults() {
      // Nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopContinue(newLoopVars, loopUsedVars,
                              blockingVars, closedVars);
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
    public void renameVars(FnID function, Map<Var, Arg> renames,
                           RenameMode mode) {
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
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
        Set<ArgCV> closedLocations, Set<Var> valueAvail, boolean waitForClose) {
      return null;
    }

    @Override
    public List<Var> getBlockingInputs(Program prog) {
      return Var.NONE;
    }

    @Override
    public ExecTarget execMode() {
      return ExecTarget.syncAny();
    }

    @Override
    public boolean isCheap() {
      return true;
    }

    @Override
    public boolean isProgressEnabling() {
      // ending loop might allow vars to be closed
      return !keepOpenVars.isEmpty();
    }

    @Override
    public List<ValLoc> getResults() {
      // nothing
      return null;
    }


    @Override
    public Pair<List<VarCount>, List<VarCount>> outRefCounts(
                   Map<FnID, Function> functions) {
      // Decrement all variables passed into loop from outside
      // NOTE: include multiple copies of vars
      List<VarCount> readOut = new ArrayList<VarCount>();
      List<VarCount> writeOut = new ArrayList<VarCount>();
      for (Var ko: this.getKeepOpenVars()) {
        assert (RefCounting.trackWriteRefCount(ko));
        writeOut.add(VarCount.one(ko));
      }
      for (PassedVar pass: this.getLoopUsedVars()) {
        if (!pass.writeOnly) {
          readOut.add(VarCount.one(pass.var));
        }
      }
      return Pair.create(readOut, writeOut);
    }

    @Override
    public Instruction clone() {
      return new LoopBreak(loopUsedVars, keepOpenVars);
    }
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
    public void renameVars(FnID function, Map<Var, Arg> renames,
                           RenameMode mode) {
      if (mode == RenameMode.REFERENCE || mode == RenameMode.REPLACE_VAR) {
        if (output != null && renames.containsKey(this.output)) {
          this.output = renames.get(this.output).getVar();
        }
      }
      ICUtil.replaceArgsInList(renames, inputs);
      if (props != null) {
        ICUtil.replaceArgValsInMap(renames, props);
      }

      // After we replace values, see if we can check assert
      if (op == Opcode.LOCAL_OP &&
          (this.subop == BuiltinOpcode.ASSERT ||
          this.subop == BuiltinOpcode.ASSERT_EQ)) {
        compileTimeAssertCheck(subop, this.inputs, function);
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
      return Operators.isImpure(subop);
    }

    private static void compileTimeAssertCheck(BuiltinOpcode subop2,
        List<Arg> inputs, FnID enclosingFn) {

      List<Arg> inputVals = new ArrayList<Arg>(inputs.size());
      // Check that all inputs are available
      for (Arg input: inputs) {
        if (input.isConst()) {
          inputVals.add(input);
        } else {
          // Can't check
          return;
        }
      }


      if (subop2 == BuiltinOpcode.ASSERT) {
        Arg cond = inputVals.get(0);

        assert(cond.isBool());
        if(!cond.getBool()) {
          compileTimeAssertWarn(enclosingFn,
              "constant condition evaluated to false", inputs.get(1));
        }
      } else {
        assert(subop2 == BuiltinOpcode.ASSERT_EQ);

        Arg a1 = inputVals.get(0);
        Arg a2 = inputVals.get(1);
        assert(a1.isConst()) : a1 + " " + a1.getKind();
        assert(a2.isConst()) : a2 + " " + a2.getKind();
        if (a1 != null && a2 != null) {
          if(!a1.equals(a2)) {
            String reason = a1.toString() + " != " + a2.toString();
            Arg msg = inputVals.get(2);
            compileTimeAssertWarn(enclosingFn, reason, msg);
          }
        }
      }
    }

    private static void compileTimeAssertWarn(FnID enclosingFn,
        String reason, Arg assertMsg) {
      String errMessage;
      if (assertMsg.isConst()) {
        errMessage = assertMsg.getString();
      } else {
        errMessage = "<RUNTIME ERROR MESSAGE>";
      }

      Logging.uniqueWarn("Assertion in function " + enclosingFn.originalName() +
          " with error message \"" + errMessage +
          "\" will fail at runtime because " + reason);
    }

    private static boolean hasLocalVersion(BuiltinOpcode op) {
      return true;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
        Set<ArgCV> closedLocations, Set<Var> valueAvail,
        boolean waitForClose) {
      if (op == Opcode.LOCAL_OP) {
        // already is immediate
        return null;
      } else {
        assert(op == Opcode.ASYNC_OP);
        if (!hasLocalVersion(subop)) {
          return null;
        }

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

        boolean preinitOutputMapping = true;
        if (Types.isFile(output) && preinitOutputMapping) {
          // Need to wait for filename, unless unmapped
          if (!(waitForClose ||
                Semantics.outputMappingAvail(closedVars, closedLocations,
                                             output))) {
            return null;
          }
        }

          // All args are closed!
        return MakeImmRequest.fromVars(
            (this.output == null) ?
                  null : Collections.singletonList(this.output),
            ICUtil.extractVars(this.inputs), Var.NONE,
            ExecTarget.nonDispatchedAny(), false, preinitOutputMapping, false);
      }
    }

    @Override
    public MakeImmChange makeImmediate(VarCreator creator,
                                       List<Fetched<Var>> newOut,
                                       List<Fetched<Arg>> newIn) {
      if (op == Opcode.LOCAL_OP) {
        throw new STCRuntimeError("Already immediate!");
      } else {
        assert(newIn.size() == inputs.size());
        List<Arg> newInArgs = Fetched.getFetched(newIn);
        if (output != null) {
          assert(newOut.size() == 1);
          assert(Types.retrievedType(output).equals(
                 newOut.get(0).fetched.type()));
          return new MakeImmChange(
              Builtin.createLocal(subop, newOut.get(0).fetched, newInArgs));
        } else {
          assert(newOut == null || newOut.size() == 0);
          return new MakeImmChange(
              Builtin.createLocal(subop, null, newInArgs));
        }
      }
    }

    @Override
    public List<Var> getBlockingInputs(Program prog) {
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
            if (Types.isRef(invar) || Types.isPrimFuture(invar)) {
              result.add(invar);
            }
          }
        }
        return result;
      }
    }
    @Override
    public ExecTarget execMode() {
      if (op == Opcode.ASYNC_OP) {
        return ExecTarget.dispatchedControl();
      } else {
        assert(op == Opcode.LOCAL_OP);
        return ExecTarget.syncAny();
      }
    }

    @Override
    public boolean isCheap() {
      // Ops are generally cheap
      return true;
    }

    @Override
    public boolean isProgressEnabling() {
      if (op == Opcode.ASYNC_OP) {
        return true; // Assigns future
      } else {
        assert(op == Opcode.LOCAL_OP);
        return false;
      }
    }

    @Override
    public List<ValLoc> getResults() {
      if (this.hasSideEffects()) {
        // Two invocations of this aren't equivalent
        return null;
      }

      ValLoc basic = makeBasicComputedValue();
      if (basic != null) {
        return basic.asList();
      } else {
        return null;
      }
    }

    /**
     * Create computed value that describes the output
     * @return
     */
    private ValLoc makeBasicComputedValue() {
      if (Operators.isCopy(this.subop)) {
        // It might be assigning a constant val
        return ValLoc.makeCopy(this.output, this.inputs.get(0),
                               IsAssign.TO_LOCATION);
      } else if (Operators.isMinMaxOp(subop)) {
        assert(this.inputs.size() == 2);
        if (this.inputs.get(0).equals(this.inputs.get(1))) {
          return ValLoc.makeCopy(this.output, this.inputs.get(0),
                                 IsAssign.TO_LOCATION);
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

        return ValLoc.buildResult(this.op, cvOp, cvInputs,
                                this.output.asArg(), outputClosed(op),
                                IsAssign.TO_LOCATION);
      }
      return null;
    }

    private static Closed outputClosed(Opcode op) {
      Closed locClosed = op == Opcode.LOCAL_OP ? Closed.YES_NOT_RECURSIVE : Closed.MAYBE_NOT;
      return locClosed;
    }


    @Override
    public Pair<List<VarCount>, List<VarCount>> inRefCounts(
                   Map<FnID, Function> functions) {
      if (op == Opcode.ASYNC_OP) {
        List<VarCount> readRCs = new ArrayList<VarCount>(inputs.size());
        for (Arg in: inputs) {
          if (RefCounting.trackReadRefCount(in.getVar())) {
            readRCs.add(VarCount.one(in.getVar()));
          }
        }
        return Pair.create(readRCs, VarCount.NONE);
      }
      return Pair.create(VarCount.NONE, VarCount.NONE);
    }


    @Override
    public Instruction clone() {
      TaskProps propsClone = props == null ? null : props.clone();
      return new Builtin(op, subop, output, Arg.cloneList(inputs), propsClone);
    }
  }

  public static Instruction valueSet(Var dst, Arg value) {
    if (Types.isPrimValue(dst)) {
      switch (dst.type().primType()) {
      case BOOL:
        assert(value.isImmBool());
        return Builtin.createLocal(BuiltinOpcode.COPY_BOOL, dst, value);
      case INT:
        assert(value.isImmInt());
        return Builtin.createLocal(BuiltinOpcode.COPY_INT, dst, value);
      case FLOAT:
        assert(value.isImmFloat());
        return Builtin.createLocal(BuiltinOpcode.COPY_FLOAT, dst, value);
      case STRING:
        assert(value.isImmString());
        return Builtin.createLocal(BuiltinOpcode.COPY_STRING, dst, value);
      case BLOB:
        assert(value.isImmBlob());
        return Builtin.createLocal(BuiltinOpcode.COPY_BLOB, dst, value);
      case VOID:
        assert(Types.isBoolVal(value));
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

  public static Instruction retrievePrim(Var dst, Var src) {
    assert(Types.isPrimValue(dst));
    assert(Types.isPrimFuture(src));
    if (Types.isScalarFuture(src)) {
      return TurbineOp.retrieveScalar(dst, src);
    } else if (Types.isFile(src)) {
      return TurbineOp.retrieveFile(dst, src);
    } else {
      throw new STCRuntimeError("method to retrieve " +
            src.type().typeName() + " is not known yet");
    }
  }

  private static String formatFunctionCall(Opcode op,
      String name, List<Var> outputs, List<Arg> inputs) {
    String result = op.toString().toLowerCase() + " " + name;
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
