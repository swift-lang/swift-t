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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.CodeGenOptions;
import exm.stc.common.CompilerBackend.VarDecl;
import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext.WorkContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.LocalForeignFunction;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.RequiredPackage;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.lang.WrappedForeignFunction;
import exm.stc.common.util.StackLite;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;

/**
 * This has the definitions for the top-level constructs in the intermediate
 * representation, including functions and blocks
 *
 * The IC tree looks like:
 *
 * Program -> Comp Function
 *         -> App Function
 *         -> Comp Function
 *
 * Function -> Block
 *
 * Block -> Variable
 *       -> Variable
 *       -> Instruction
 *       -> Instruction
 *       -> Instruction
 *       -> Continuation       -> Block
 *                             -> Block
 */
public class ICTree {

  public static final String indent = ICUtil.indent;

  public static class Program {

    private final GlobalConstants constants = new GlobalConstants();

    private final ForeignFunctions foreignFunctions;

    private final ArrayList<Function> functions = new ArrayList<Function>();
    private final Map<String, Function> functionsByName =
                                            new HashMap<String, Function>();

    private final ArrayList<BuiltinFunction> builtinFuns =
                                            new ArrayList<BuiltinFunction>();

    private final Set<RequiredPackage> required = new HashSet<RequiredPackage>();

    private final List<StructType> structTypes = new ArrayList<StructType>();

    private final List<WorkContext> workTypes = new ArrayList<WorkContext>();

    /**
     * If checkpointing required
     */
    private boolean checkpointRequired = false;

    public Program(ForeignFunctions foreignFunctions) {
      this.foreignFunctions = foreignFunctions;
    }

    public void generate(Logger logger, CompilerBackend gen)
        throws UserException {
      Map<String, List<Boolean>> blockVectors = new
              HashMap<String, List<Boolean>> (functions.size());
      for (Function f: functions) {
        blockVectors.put(f.getName(), f.getBlockingInputVector());
      }
      if (logger.isTraceEnabled())
        logger.trace("blocking inputs: " + blockVectors);

      GenInfo info = new GenInfo(blockVectors);

      logger.debug("Starting to generate program from Swift IC");
      gen.initialize(new CodeGenOptions(checkpointRequired), foreignFunctions);

      logger.debug("Generating required packages");
      for (RequiredPackage pkg: required) {
        gen.requirePackage(pkg);
      }
      logger.debug("Done generating required packages");

      logger.debug("generating struct types");
      for (StructType st: structTypes) {
        gen.declareStructType(st);
      }
      logger.debug("Done generating struct types");

      logger.debug("generating work types");
      for (WorkContext wt: workTypes) {
        gen.declareWorkType(wt);
      }
      logger.debug("Done generating work types");

      logger.debug("Generating builtins");
      for (BuiltinFunction f: builtinFuns) {
        f.generate(logger, gen, info);
      }
      logger.debug("Done generating builtin functions");

      logger.debug("Generating functions");
      // output functions in original order
      for (Function f: functions) {
        f.generate(logger, gen, info);
      }
      logger.debug("Done generating functions");

      constants.generate(logger, gen);

      gen.finalize();
    }

    public void addRequiredPackage(RequiredPackage pkg) {
      required.add(pkg);
    }

    public void addStructType(StructType newType) {
      structTypes.add(newType);
    }

    public void addWorkType(WorkContext workType) {
      workTypes.add(workType);
    }

    public void addBuiltin(BuiltinFunction fn) {
      this.builtinFuns.add(fn);
    }

    public void addFunction(Function fn) {
      this.functions.add(fn);
      this.functionsByName.put(fn.getName(), fn);
    }

    public void addFunctions(Collection<Function> c) {
      for (Function f: c) {
        addFunction(f);
      }
    }

    public List<Function> getFunctions() {
      return Collections.unmodifiableList(this.functions);
    }

    public Set<String> getFunctionNames() {
      Set<String> res = new HashSet<String>();
      for (Function f: functions) {
        res.add(f.getName());
      }
      return res;
    }

    public Map<String, Function> getFunctionMap() {
      return Collections.unmodifiableMap(functionsByName);
    }

    public Function lookupFunction(String functionName) {
      return functionsByName.get(functionName);
    }

    public ListIterator<Function> functionIterator() {
      // Use custom iterator to intercept operations
      return new ListIterator<Function>() {
        private final ListIterator<Function> internal =
                              functions.listIterator();

        Function lastReturned = null;

        @Override
        public void set(Function e) {
          internal.set(e);
          functionsByName.remove(lastReturned.getName());
          functionsByName.put(e.getName(), e);
        }

        @Override
        public void remove() {
          internal.remove();
          functionsByName.remove(lastReturned);
        }

        @Override
        public int previousIndex() {
          return internal.previousIndex();
        }

        @Override
        public Function previous() {
          Function f = internal.previous();
          lastReturned = f;
          return f;
        }

        @Override
        public int nextIndex() {
          return internal.nextIndex();
        }

        @Override
        public Function next() {
          Function f = internal.next();
          lastReturned = f;
          return f;
        }

        @Override
        public boolean hasPrevious() {
          return internal.hasPrevious();
        }

        @Override
        public boolean hasNext() {
          return internal.hasNext();
        }

        @Override
        public void add(Function e) {
          internal.add(e);
          functionsByName.put(e.getName(), e);
        }
      };
    }

    public ListIterator<BuiltinFunction> builtinIterator() {
      return builtinFuns.listIterator();
    }

    public GlobalConstants constants() {
      return constants;
    }

    public ForeignFunctions getForeignFunctions() {
      return foreignFunctions;
    }

    /**
     * Should be called if a function uses checkpointing
     */
    public void requireCheckpointing() {
      this.checkpointRequired = true;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb);
      return sb.toString();
    }

    public void prettyPrint(StringBuilder out) {
      for (RequiredPackage rp: required) {
        out.append("require " + rp.toString() + "\n");
      }

      for (StructType st: structTypes) {
        out.append(st.toString() + "\n");
      }

      for (WorkContext wc: workTypes) {
        out.append(wc.toString() + "\n");
      }

      constants.prettyPrint(out);
      out.append("\n");

      for (BuiltinFunction f: builtinFuns) {
        f.prettyPrint(out);
        out.append("\n");
      }

      for (Function f: functions) {
        f.prettyPrint(out);
        out.append("\n");
      }
    }


    public void log(PrintStream icOutput, String codeTitle) {
      StringBuilder ic = new StringBuilder();
      try {
        icOutput.append("\n\n" + codeTitle + ": \n" +
            "============================================\n");
        prettyPrint(ic) ;
        icOutput.append(ic.toString());
        icOutput.flush();
      } catch (Exception e) {
        icOutput.append("ERROR while generating code. Got: "
            + ic.toString());
        icOutput.flush();
        e.printStackTrace();
        throw new STCRuntimeError("Error while generating IC: " +
        e.toString());
      }
    }

    public void cleanup() {
      for (Function f: functions) {
        f.rebuildUsedVarNames();
      }
    }

  }

  public static class GlobalConstants {
    /**
     * Use treemap to keep them in alpha order
     */
    private final TreeMap<Var, Arg> globalConsts = new TreeMap<Var, Arg>();
    private final HashMap<Arg, Var> globalConstsInv =  new HashMap<Arg, Var>();
    private final HashSet<String> usedNames = new HashSet<String>();

    public void add(Var var, Arg val) {
      assert(var.storage() == Alloc.GLOBAL_CONST);
      assert(var.defType() == DefType.GLOBAL_CONST);
      assert(var.type().getImplType().equals(val.futureType().getImplType()));

      Arg prevVal = globalConsts.put(var, val);
      assert(prevVal == null) :
          new STCRuntimeError("Overwriting global constant " + var.name());

      Var prev = globalConstsInv.put(val, var);
      // It's ok to have duplicate constants
      Logging.getSTCLogger().debug("Duplicate global const for value " + val
                                   + " " + prev);

      usedNames.add(var.name());
    }

    /**
     * Return a global constant with matching value, otherwise create it
     * @param val
     * @return global constant for given value
     */
    public Var getOrCreateByVal(Arg val) {
      Var existing = lookupByValue(val);
      if (existing != null) {
        return existing;
      } else {
        return autoCreate(val);
      }
    }

    private Var autoCreate(Arg val) {
      String suffix;
      switch(val.kind) {
      case BOOLVAL:
        suffix = "b_" + Boolean.toString(val.getBoolLit());
        break;
      case INTVAL:
        suffix = "i_" + Long.toString(val.getIntLit());
        break;
      case FLOATVAL:
        String stringRep = Double.toString(val.getFloatLit());
        // Truncate float val
        suffix = "f_" +
              stringRep.substring(0, Math.min(5, stringRep.length()));
        break;
      case STRINGVAL:
        // Try to have var name something to do with string contents
        String onlyalphanum = val.getStringLit()
              .replaceAll("[ \n\r\t.,]", "_")
              .replaceAll("[^a-zA-Z0-9_]", "");

        suffix = "s_" + onlyalphanum.substring(0,
            Math.min(10, onlyalphanum.length()));
        break;
      case VAR:
        throw new STCRuntimeError("Variable can't be a constant");
      default:
        throw new STCRuntimeError("Unknown enum value " +
                val.kind.toString());
      }

      String origname = Var.GLOBAL_CONST_VAR_PREFIX + suffix;
      String name = origname;
      int seq = 0;
      while (usedNames.contains(name)) {
        seq++;
        name = origname + "-" + seq;
      }
      Var var = new Var(val.futureType(), name, Alloc.GLOBAL_CONST,
                        DefType.GLOBAL_CONST, VarProvenance.optimizerTmp());
      add(var, val);
      return var;
    }

    public void remove(Var unused) {
      Arg val = globalConsts.remove(unused);
      globalConstsInv.remove(val);
    }

    public Var lookupByValue(Arg val) {
      return this.globalConstsInv.get(val);

    }

    public Arg lookupByVar(Var var) {
      return this.globalConsts.get(var);

    }

    public SortedMap<Var, Arg> map() {
      return Collections.unmodifiableSortedMap(globalConsts);
    }

    public Collection<Var> vars() {
      return globalConsts.keySet();
    }

    public void generate(Logger logger, CompilerBackend gen) {
      // Global Constants
      logger.debug("Generating global constants");
      for (Entry<Var, Arg> c: globalConsts.entrySet()) {
        Var var = c.getKey();
        Arg val = c.getValue();
        gen.addGlobalConst(var, val);
      }
      logger.debug("Done generating global constants");
    }

    public void prettyPrint(StringBuilder out) {
      for (Entry<Var, Arg> constE: globalConsts.entrySet()) {
        Arg val = constE.getValue();
        out.append("const " +   constE.getKey().name() + " = ");
        out.append(val.toString());
        out.append(" as " + val.futureType().typeName());
        out.append("\n");
      }

    }

  }

  public static class BuiltinFunction {
    private final String name;
    private final LocalForeignFunction localImpl;
    private final WrappedForeignFunction wrappedImpl;
    private final FunctionType fType;


    public BuiltinFunction(String name, FunctionType fType,
        LocalForeignFunction localImpl, WrappedForeignFunction wrappedImpl) {
      this.name = name;
      this.fType = fType;
      this.localImpl = localImpl;
      this.wrappedImpl = wrappedImpl;
    }

    public String getName() {
      return name;
    }

    public void prettyPrint(StringBuilder out) {
      out.append("tcl ");
      out.append("(");
      //ICUtil.prettyPrintFormalArgs(out, this.oList);
      boolean first = true;
      for(Type t: fType.getOutputs()) {
        if (first) {
          first = false;
        } else {
          out.append(", ");
        }
        out.append(t.typeName());
      }
      out.append(") @" + this.name + "(");
      first = true;
      for(Type t: fType.getInputs()) {
        if (first) {
          first = false;
        } else {
          out.append(", ");
        }
        out.append(t.typeName());
      }
      out.append(")");

      if (localImpl != null) {
        out.append(" local { ");
        out.append(localImpl.toString());
        out.append(" }");
      }

      if (wrappedImpl != null) {
        out.append(" wrapped { ");
        out.append(wrappedImpl.toString());
        out.append(" }\n");
      }

    }

  public void generate(Logger logger, CompilerBackend gen, GenInfo info)
    throws UserException {
      logger.debug("generating: " + name);
      gen.defineForeignFunction(name, fType, localImpl, wrappedImpl);
    }
  }

  public static class Function {
    private Block mainBlock;
    private final String name;
    public String getName() {
      return name;
    }


    public List<Boolean> getBlockingInputVector() {
      ArrayList<Boolean> res = new ArrayList<Boolean>(iList.size());
      for (Var input: this.iList) {

        boolean isBlocking = WaitVar.find(blockingInputs, input) != null;
        res.add(isBlocking);
      }
      return res;
    }


    private final List<Var> iList;
    private final List<Var> oList;
    /** List of which outputs are write-only */
    private final List<Var> oListWriteOnly;

    /** Wait until the below inputs are available before running function. */
    private final List<WaitVar> blockingInputs;

    private ExecTarget mode;

    private final HashSet<String> usedVarNames;

    public Function(String name, List<Var> iList,
        List<Var> oList, ExecTarget mode) {
      this(name, iList, Collections.<WaitVar>emptyList(), oList,
           mode, new Block(BlockType.MAIN_BLOCK, null), true);
    }

    public Function(String name, List<Var> iList,
            List<WaitVar> blockingInputs,
            List<Var> oList, ExecTarget mode, Block mainBlock) {
      this(name, iList, blockingInputs, oList, mode, mainBlock, false);
    }

    private Function(String name, List<Var> iList,
        List<WaitVar> blockingInputs,
        List<Var> oList, ExecTarget mode, Block mainBlock,
        boolean emptyBlock) {
      if (mainBlock.getType() != BlockType.MAIN_BLOCK) {
        throw new STCRuntimeError("Expected main block " +
        "for function to be tagged as such");
      }
      this.name = name;
      this.iList = new ArrayList<Var>(iList);
      this.oList = new ArrayList<Var>(oList);
      this.oListWriteOnly = new ArrayList<Var>();
      this.mode = mode;
      this.mainBlock = mainBlock;
      this.blockingInputs = new ArrayList<WaitVar>(blockingInputs);
      this.usedVarNames = new HashSet<String>();

      this.mainBlock.setParent(this, emptyBlock); // Rebuild vars later
      rebuildUsedVarNames();
    }


    public List<Var> getInputList() {
      return Collections.unmodifiableList(this.iList);
    }

    public List<Var> getOutputList() {
      return Collections.unmodifiableList(this.oList);
    }

    public Var getOutput(int i) {
      return oList.get(i);
    }

    public boolean isOutputWriteOnly(int i) {
      return oListWriteOnly.contains(oList.get(i));
    }

    public void makeOutputWriteOnly(int i) {
      assert(i >= 0 && i < oList.size());
      // Files are complicated and can't be simply treated as write-only
      assert(!Types.isFile(oList.get(i).type()));
      Var output = oList.get(i);
      if (!oListWriteOnly.contains(output)) {
        oListWriteOnly.add(output);
      }
    }

    /**
     * list outputs augmented with info about whether they are write-only
     * @return
     */
    public List<PassedVar> getPassedOutputList() {
      ArrayList<PassedVar> res = new ArrayList<PassedVar>();
      for (int i = 0; i < oList.size(); i++) {
        Var out = oList.get(i);
        res.add(new PassedVar(out, oListWriteOnly.contains(out)));
      }
      return res;
    }


    public Block mainBlock() {
      return mainBlock;
    }

    public Block swapBlock(Block newBlock) {
      Block old = this.mainBlock;
      this.mainBlock = newBlock;
      this.mainBlock.setParent(this, false);
      return old;
    }


    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UserException {
      logger.debug("Generating function " + name);
      gen.startFunction(name, oList, iList, mode);
      this.mainBlock.generate(logger, gen, info);
      gen.endFunction();
      logger.debug("Done generating function " + name);
    }

    public void prettyPrint(StringBuilder sb) {
      ICUtil.prettyPrintFormalArgs(sb, this.oList);
      sb.append(" @" + name + " ");
      ICUtil.prettyPrintFormalArgs(sb, this.iList);

      if (!this.blockingInputs.isEmpty()) {
        sb.append(" #waiton[");
        ICUtil.prettyPrintList(sb, this.blockingInputs);
        sb.append("]");
      }

      if (!this.oListWriteOnly.isEmpty()) {
        sb.append(" #writeonly[");
        ICUtil.prettyPrintVarList(sb, this.oListWriteOnly);
        sb.append("]");
      }
      sb.append(" {\n");
      mainBlock.prettyPrint(sb, indent);
      sb.append("}\n");
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb);
      return sb.toString();
    }

    public List<WaitVar> blockingInputs() {
      return blockingInputs;
    }

    public void addBlockingInput(WaitVar newWaitVar) {
      if (!iList.contains(newWaitVar.var)) {
        throw new STCRuntimeError(newWaitVar.var + " is not the name of " +
        " an input argument to function " + name + ":\n" + this);
      }
      // Check to see if already present
      ListIterator<WaitVar> it = blockingInputs.listIterator();
      while (it.hasNext()) {
        WaitVar i = it.next();
        if (i.var.equals(newWaitVar.var)) {
          // already there
          if (newWaitVar.explicit && !i.explicit) {
            it.set(newWaitVar);
          }
          return;
        }
      }
      blockingInputs.add(newWaitVar);
    }

    public ExecTarget mode() {
      return this.mode;
    }

    public boolean isAsync() {
      return this.mode.isAsync();
    }


    /**
     * Rebuild usedVarNames from scratch based on current IR.
     */
    private void rebuildUsedVarNames() {
      this.usedVarNames.clear();
      addUsedVarNames(this.iList);
      addUsedVarNames(this.oList);
      addUsedVarsRec(mainBlock);
    }


    private void addUsedVarsRec(Block rootBlock) {
      StackLite<Block> work = new StackLite<Block>();
      work.push(rootBlock);

      while (!work.isEmpty()) {
        Block b = work.pop();
        addUsedVarNames(b.getVariables());
        for (Statement s: b.getStatements()) {
          if (s.type() == StatementType.CONDITIONAL) {
            Conditional c = s.conditional();
            addUsedVarNames(c.constructDefinedVars());
            work.addAll(c.getBlocks());
          }
        }
        for (Continuation c: b.getContinuations()) {
          addUsedVarNames(c.constructDefinedVars());
          work.addAll(c.getBlocks());
        }
      }
    }


    public void addUsedVarName(Var var) {
      this.usedVarNames.add(var.name());
    }


    public void addUsedVarName(String name) {
      this.usedVarNames.add(name);
    }


    public void addUsedVarNames(List<Var> vars) {
      for (Var v: vars) {
        this.usedVarNames.add(v.name());
      }
    }


    public boolean varNameUsed(String name) {
      return usedVarNames.contains(name);
    }


    public Set<String> usedVarNames() {
      return Collections.unmodifiableSet(usedVarNames);
    }
  }


  // Keep track of block type, mainly for purpose of asserts
  public enum BlockType {
    MAIN_BLOCK,
    NESTED_BLOCK,
    CASE_BLOCK,
    ELSE_BLOCK,
    THEN_BLOCK,
    FOREACH_BODY,
    WAIT_BLOCK,
    LOOP_BODY,
    RANGELOOP_BODY,
    ASYNC_EXEC_CONTINUATION,
  }

  public static class CleanupAction {
    public CleanupAction(Var var, Instruction action) {
      super();
      this.var = var;
      this.action = action;
    }
    private Var var;
    private final Instruction action;

    public Var var() {
      return var;
    }
    public Instruction action() {
      return action;
    }

    /**
     * @return false if only effect of instruction is to cleanup current variable
     */
    public boolean hasSideEffect() {
      if (action.op == Opcode.DECR_WRITERS &&
          action.writesAliasVar()) {
        return true;
      }
      return false;
    }

    public void renameVars(String function, Map<Var, Arg> renames,
                           RenameMode mode) {
      action.renameVars(function, renames, mode);

      if (mode != RenameMode.VALUE &&
          canMoveToAlias() && renames.containsKey(var)) {
        Arg replacement = renames.get(var);
        assert(replacement.isVar()) : replacement;
        this.var = replacement.getVar();
      }
    }

    /**
     * Can move cleanup action to alias of current var
     * @return
     */
    private boolean canMoveToAlias() {
      return action.op == Opcode.DECR_WRITERS;
    }
    @Override
    public CleanupAction clone() {
      return new CleanupAction(var, action.clone());
    }

    @Override
    public String toString() {
      return action.toString() + " # cleanup " + var.name();
    }
  }

  public static class Block {

    private final BlockType type;
    private Continuation parentCont;
    private Function parentFunction;

    public Block(BlockType type, Continuation parentCont) {
      this(type, parentCont, null);
    }

    public Block(Function parentFunction) {
      this(BlockType.MAIN_BLOCK, null, parentFunction);
    }

    private Block(BlockType type, Continuation parentCont, Function parentFunction) {
      this(type, parentCont, parentFunction, true, new LinkedList<Statement>(),
          new ArrayList<Var>(), new HashMap<Var, Arg>(), new HashMap<Var, Arg>(),
          new ArrayList<Continuation>(), new ArrayList<CleanupAction>());
    }

    /**
     * Used to create duplicate.  This will take ownership of all
     * data structures passed in
     * @param type
     * @param instructions
     */
    private Block(BlockType type,
        Continuation parentCont, Function parentFunction,
        boolean emptyBlock,
        LinkedList<Statement> instructions,
        ArrayList<Var> variables, HashMap<Var, Arg> initReadRefcounts,
        HashMap<Var, Arg> initWriteRefcounts,
        ArrayList<Continuation> conds,
        ArrayList<CleanupAction> cleanupActions) {
      this.type = type;
      this.statements = instructions;
      this.variables = variables;
      this.initReadRefcounts = initReadRefcounts;
      this.initWriteRefcounts = initWriteRefcounts;
      this.continuations = conds;
      this.cleanupActions = cleanupActions;

      // Set parent at end so it will be updated correctly with vars
      setParent(type, parentCont, parentFunction, emptyBlock);
    }

    private void setParent(BlockType type, Continuation parentCont,
        Function parentFunction, boolean emptyBlock) {
      this.parentCont = parentCont;
      if (parentCont == null && parentFunction == null) {
        // Not yet linked up
        this.parentFunction = null;
        this.parentCont = null;
        return;
      }
      assert(parentCont == null || parentFunction == null);
      if (parentCont != null && parentCont.parent() != null) {
        // Fill in, otherwise depend on it being filled in later
        parentFunction = parentCont.parent().getParentFunction();
      }
      // Check to see if we need to link children up to function
      if (parentFunction != null) {
        if (emptyBlock) {
          setParentFunction(parentFunction);
        } else {
          // Not empty, so do updates necessary for blocks under this one
          fixParentLinksRec(parentFunction);
        }
      }
    }

    private void setParentFunction(Function parentFunction) {
      this.parentFunction = parentFunction;
      parentFunction.addUsedVarNames(this.variables);
    }

    /**
     * Update parent function links for all blocks under this one
     * @param parentFunction
     */
    void fixParentLinksRec(Function parentFunction) {
      StackLite<Block> work = new StackLite<Block>();
      work.push(this);
      while (!work.isEmpty()) {
        Block curr = work.pop();
        curr.setParentFunction(parentFunction);
        for (Statement stmt: curr.getStatements()) {
          if (stmt.type() == StatementType.CONDITIONAL) {
            Conditional cond = stmt.conditional();
            parentFunction.addUsedVarNames(cond.constructDefinedVars());
            work.addAll(cond.getBlocks());
          }
        }
        for (Continuation c: curr.getContinuations()) {
          parentFunction.addUsedVarNames(c.constructDefinedVars());
          work.addAll(c.getBlocks());
        }
      }
    }

    public void setParent(Continuation parent, boolean newBlock) {
      setParent(type, parent, null, newBlock);
    }

    public void setParent(Function parent, boolean newBlock) {
      setParent(BlockType.MAIN_BLOCK, null, parent, newBlock);
    }

    /**
     * Make a copy without any shared mutable state
     */
    @Override
    public Block clone() {
      return this.clone(this.type, null, null);
    }

    public Block clone(Function parentFunction) {
      return this.clone(BlockType.MAIN_BLOCK, null, parentFunction);
    }

    public Block clone(BlockType newType, Continuation parentCont) {
      return this.clone(BlockType.MAIN_BLOCK, parentCont, null);
    }

    public Block clone(BlockType newType, Continuation parentCont,
                       Function parentFunction) {
      Block cloned = new Block(newType, parentCont, parentFunction,
          false, ICUtil.cloneStatements(this.statements),
          new ArrayList<Var>(this.variables),
          new HashMap<Var, Arg>(this.initReadRefcounts),
          new HashMap<Var, Arg>(this.initWriteRefcounts),
          new ArrayList<Continuation>(),
          ICUtil.cloneCleanups(this.cleanupActions));
      for (Continuation c: this.continuations) {
        // Add in way that ensures parent link updated
        cloned.addContinuation(c.clone());
      }
      for (Statement s: cloned.statements) {
        s.setParent(cloned);
      }
      return cloned;
    }

    public BlockType getType() {
      return type;
    }

    private final LinkedList<Statement> statements;

    private final ArrayList<CleanupAction> cleanupActions;

    private final ArrayList<Var> variables;

    /** Initial reference counts for vars defined in block */
    private final HashMap<Var, Arg> initReadRefcounts;
    private final HashMap<Var, Arg> initWriteRefcounts;

    /** conditional statements for block */
    private final ArrayList<Continuation> continuations;

    public void addStatement(Statement st) {
      st.setParent(this);
      statements.add(st);
    }

    public void addInstruction(Instruction e) {
      addStatement(e);
    }

    public void addInstructionFront(Instruction e) {
      statements.addFirst(e);
    }

    public void addInstructions(List<Instruction> instructions) {
      addStatements(instructions);
    }

    public void addStatements(List<? extends Statement> stmts) {
      for (Statement stmt: stmts) {
        stmt.setParent(this);
      }
      this.statements.addAll(stmts);
    }

    public void addContinuation(Continuation c) {
      c.setParent(this);
      this.continuations.add(c);
    }

    public List<Continuation> getContinuations() {
      return Collections.unmodifiableList(continuations);
    }

    public Continuation getContinuation(int i) {
      return continuations.get(i);
    }

    public void removeContinuation(int i) {
      continuations.remove(i);
    }

    public List<Var> getVariables() {
      return Collections.unmodifiableList(variables);
    }

    public boolean declaredHere(Var var) {
      return variables.contains(var);
    }

    public boolean isEmpty() {
      if (!continuations.isEmpty())
        return false;
      for (Statement stmt: statements) {
        if (stmt.type() != StatementType.INSTRUCTION ||
            stmt.instruction().op != Opcode.COMMENT) {
          return false;
        }
      }
      return true;
    }

    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {

      logger.trace("Generate code for block of type " + this.type.toString());
      // Pass variable declarations as batch
      generateBlockVariables(logger, gen);

      for (Statement stmt: statements) {
        stmt.generate(logger, gen, info);
      }

      // Can put conditional statements at end of block, making sure
      // Ones which are marked as runLast occur after those not
      for (boolean runLast: new boolean[] {false, true}) {
        for (Continuation c: continuations) {
          if (c.runLast() == runLast) {
            logger.trace("generating code for continuation");
            c.generate(logger, gen, info);
          }
        }
      }

      for (CleanupAction cleanup: cleanupActions) {
        cleanup.action().generate(logger, gen, info);
      }
      logger.trace("Done with code for block of type " + this.type.toString());

    }

    private void generateBlockVariables(Logger logger, CompilerBackend gen) {
      List<VarDecl> declarations = new ArrayList<VarDecl>(variables.size());
      for (Var v: variables) {
        logger.trace("generating variable decl for " + v.toString());
        Arg initReaders = initReadRefcounts.get(v);
        Arg initWriters = initWriteRefcounts.get(v);

        if (initReaders == null) {
          logger.trace("Init readers: " + v.name() + " null");
        } else {
          logger.trace("Init readers: " + v.name() + " " + initReaders);
        }

        if (initWriters == null) {
          logger.trace("Init writers: " + v.name() + " null");
        } else {
          logger.trace("Init writers: " + v.name() + " " + initWriters);
        }

        // Initialize refcounts to default value if not
        //  explicitly overridden and check for bad refcounts
        if (v.storage() == Alloc.ALIAS ||
            !RefCounting.trackReadRefCount(v)) {
          // Check we don't have refcount for untracked var
          assert(initReaders == null) : v + " " +   initReaders;
        }

        if (v.storage() == Alloc.ALIAS ||
            !RefCounting.trackWriteRefCount(v)) {
          // Check we don't have refcount for untracked var
          assert(initWriters == null);
        }

        if (v.storage() != Alloc.ALIAS) {
          // If not an alias, need to select refcount
          if (initReaders == null) {
            // Init to default refcount
            long baseReaders = RefCounting.baseReadRefCount(v, true, true);
            initReaders = Arg.createIntLit(baseReaders);
          }

          if (initWriters == null) {
            // Init to default refcount
            long baseWriters = RefCounting.baseWriteRefCount(v, true, true);
            initWriters = Arg.createIntLit(baseWriters);
          }
        }

        declarations.add(new VarDecl(v, initReaders, initWriters));
      }
      gen.declare(declarations);
    }

    public void prettyPrint(StringBuilder sb, String indent) {
      for (Var v: variables) {
        sb.append(indent);
        sb.append("alloc " + v.type().typeName() + " " + v.name() +
                " <" + v.storage().toString().toLowerCase() + ">");

        if (v.mappedDecl()) {
          sb.append(" @mapped");
        }
        if (initReadRefcounts.containsKey(v)) {
          sb.append(" <readers=" + initReadRefcounts.get(v) + ">");
        }
        if (initWriteRefcounts.containsKey(v)) {
          sb.append(" <writers=" + initWriteRefcounts.get(v) + ">");
        }
        sb.append("\n");
      }

      for (Statement stmt: statements) {
        stmt.prettyPrint(sb, indent);
      }

      for (boolean runLast: new boolean[] {false, true}) {
        for (Continuation c: continuations) {
          if (c.runLast() == runLast) {
            c.prettyPrint(sb, indent);
          }
        }
      }

      for (CleanupAction a: cleanupActions) {
        sb.append(indent);
        sb.append(a.toString());
        sb.append("\n");
      }
    }

    /**
     * @return an iterator over continuations.  Modifications through iterator
     * will correctly update parent links etc
     */
    public ListIterator<Continuation> continuationIterator() {
      return new ContIt(this.continuations.listIterator());
    }

    public ListIterator<Continuation> continuationIterator(int pos) {
      return new ContIt(this.continuations.listIterator(pos));
    }

    public ListIterator<Continuation> continuationEndIterator() {
      return continuationIterator(continuations.size());
    }

    /**
     * Wrapper around ListIterator to intercept calls and make sure IR is
     * consistent, e.g. with parent links.
     */
    private final class ContIt implements ListIterator<Continuation> {
      private ContIt(ListIterator<Continuation> it) {
        this.it = it;
      }

      final ListIterator<Continuation> it;
      @Override
      public void set(Continuation e) {
        e.setParent(Block.this);
        it.set(e);
      }

      @Override
      public void remove() {
        it.remove();
      }

      @Override
      public int previousIndex() {
        return it.previousIndex();
      }

      @Override
      public Continuation previous() {
        return it.previous();
      }

      @Override
      public int nextIndex() {
        return it.nextIndex();
      }

      @Override
      public Continuation next() {
        return it.next();
      }

      @Override
      public boolean hasPrevious() {
        return it.hasPrevious();
      }

      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public void add(Continuation e) {
        it.add(e);
        e.setParent(Block.this);
      }
    }

    public class AllContIt implements Iterator<Continuation>,
                              Iterable<Continuation>{
      Continuation next = null;
      /**
       * null if exhausted
       */
      Iterator<Statement> stmtIt = statementIterator();
      Iterator<Continuation> contIt = null;

      @Override
      public Iterator<Continuation> iterator() {
        return this;
      }

      private boolean checkMoreStatements() {
        if (next != null)
          return true;

        if (stmtIt != null) {
          // Check for continuation
          while (stmtIt.hasNext()) {
            Statement stmt = stmtIt.next();
            if (stmt.type() == StatementType.CONDITIONAL) {
              next = stmt.conditional();
              return true;
            }
          }
        }
        if (contIt == null) {
          stmtIt = null;
          contIt = continuationIterator();
        }
        return false;
      }

      @Override
      public boolean hasNext() {
        if (next != null) {
          return true;
        }
        if (stmtIt != null && checkMoreStatements()) {
          return true;
        }
        return contIt.hasNext();
      }

      @Override
      public Continuation next() {
        if (checkMoreStatements()) {
          Continuation res = next;
          next = null;
          return res;
        } else {
          return contIt.next();
        }
      }

      @Override
      public void remove() {
        if (stmtIt != null) {
          assert(contIt == null);
          stmtIt.remove();
        } else {
          assert(contIt != null);
          contIt.remove();
        }
      }
    }

    /**
     * @return iterable for all continuations, including statements
     */
    public Iterable<Continuation> allComplexStatements() {
      return new AllContIt();
    }

    public ListIterator<Var> variableIterator() {
      return variables.listIterator();
    }
    public List<Statement> getStatements() {
      return Collections.unmodifiableList(statements);
    }

    public ListIterator<Statement> statementIterator() {
      return statements.listIterator();
    }

    public ListIterator<Statement> statementIterator(int i) {
      return statements.listIterator(i);
    }

    public ListIterator<Statement> statementEndIterator() {
      return statements.listIterator(statements.size());
    }

    public ListIterator<CleanupAction> cleanupIterator() {
      return cleanupActions.listIterator();
    }

    public List<CleanupAction> getCleanups() {
      return Collections.unmodifiableList(cleanupActions);
    }

    public void addCleanup(Var var, Instruction action) {
      this.cleanupActions.add(new CleanupAction(var, action));
    }

    // Remove cleanup actions for variable
    public void removeCleanups(Var var) {
      moveCleanups(var, null);
    }

    public void moveCleanups(Var var, Block target) {
      ListIterator<CleanupAction> it = cleanupActions.listIterator();
      while (it.hasNext()) {
        CleanupAction ca = it.next();
        if (ca.var().equals(var)) {
          it.remove();
          if (target != null) {
            target.addCleanup(ca.var, ca.action);
          }
        }
      }
    }

    /**
     * Rename variables in block (and nested blocks) according to map.
     * If the map doesn't have an entry, we don't rename anything
     * @param function
     * @param renames OldName -> NewName
     * @param mode
     * @param recursive if it should be done on child blocks
     */
    public void renameVars(String function, Map<Var, Arg> renames,
                            RenameMode mode, boolean recursive) {
      if (renames.isEmpty())
        return;
      renameInDefs(renames, mode);
      renameInCode(function, renames, mode, recursive);
    }

    private void renameInDefs(Map<Var, Arg> renames, RenameMode mode) {
      ListIterator<Var> it = variables.listIterator();
      while (it.hasNext()) {
        // The original variable and the current one
        Var var;
        var = it.next();
        if (mode == RenameMode.REPLACE_VAR) {
          if (renames.containsKey(var)) {
            Arg replacement = renames.get(var);
            if (replacement.isVar()) {
              var = replacement.getVar();
              it.set(var);
            } else {
              // value replaced with constant
              it.remove();
            }
          }
        }
      }
    }

    private void renameInCode(String function, Map<Var, Arg> renames,
          RenameMode mode, boolean recursive) {
      for (Statement stmt: statements) {
        if (stmt.type() == StatementType.INSTRUCTION) {
          stmt.instruction().renameVars(function, renames, mode);
        } else {
          assert(stmt.type() == StatementType.CONDITIONAL);
          stmt.conditional().renameVars(function, renames, mode, recursive);
        }
      }

      // Rename in nested blocks
      for (Continuation c: continuations) {
        c.renameVars(function, renames, mode, recursive);
      }
      renameCleanupActions(function, renames, mode);
    }

    public void renameCleanupActions(String function, Map<Var, Arg> renames,
                                     RenameMode mode) {
      for (CleanupAction a: cleanupActions) {
        a.renameVars(function, renames, mode);
      }
    }

    /**
     * Remove the variable from this block and all internal constructs,
     *    removing all instructions with this as output
     * preconditions: variable is not used as input for any instruction,
     *            variable is not used as output for any instruction with a sideeffect,
     *            variable is not required for any constructs
     */
    public void removeVars(Set<Var> removeVars) {
      if (removeVars.isEmpty()) {
        return;
      }

      removeVarDeclarations(removeVars);

      ListIterator<Statement> it = statementIterator();
      while (it.hasNext()) {
        Statement stmt = it.next();
        if (stmt.type() == StatementType.INSTRUCTION) {
          Instruction inst = stmt.instruction();
          inst.removeVars(removeVars);
          // See if we can remove instruction
          int removeable = 0;
          int notRemoveable = 0;
          for (Var out: inst.getModifiedOutputs()) {
            // Doesn't make sense to assign to anything other than
            //  variable
            if (removeVars.contains(out)) {
              removeable++;
            } else {
              notRemoveable++;
            }
          }
          if (removeable > 0 && notRemoveable == 0) {
            // One of the remove vars is output
            it.remove();
          } else if (removeable > 0 && notRemoveable > 0) {
            // Can't remove
            throw new STCRuntimeError("Can't remove instruction " + inst +
                " because not all outputs in remove vars set " + removeVars);
          }
        } else {
          assert(stmt.type() == StatementType.CONDITIONAL);
          stmt.conditional().removeVars(removeVars);
        }
      }
      for (Continuation c: continuations) {
        c.removeVars(removeVars);
      }
    }


    public void addVariables(List<Var> variables) {
      this.variables.addAll(variables);
      if (this.parentFunction != null) {
        this.parentFunction.addUsedVarNames(variables);
      }
    }

    public void addVariable(Var variable) {
      addVariable(variable, false);
    }

    public void addVariable(Var variable, boolean atTop) {
      if (atTop) {
        this.variables.add(0, variable);
      } else {
        this.variables.add(variable);
      }
      if (this.parentFunction != null) {
        parentFunction.addUsedVarName(variable);
      }
    }

    public Var declareUnmapped(Type t, String name, Alloc storage,
        DefType defType, VarProvenance provenance) {
      return declare(t, name, storage, defType, provenance, false);
    }

    public Var declareMapped(Type t, String name, Alloc storage,
          DefType defType, VarProvenance provenance) {
      return declare(t, name, storage, defType, provenance, true);
    }

    public Var declare(Type t, String name, Alloc storage,
        DefType defType, VarProvenance provenance, boolean mapped) {
      Var v = new Var(t, name, storage, defType, provenance, mapped);
      addVariable(v);
      return v;
    }

    public void addContinuations(List<? extends Continuation>
                                                continuations) {
      for (Continuation c: continuations) {
        addContinuation(c);
      }
    }

    public void removeContinuation(Continuation c) {
      this.continuations.remove(c);
    }

    public void removeContinuations(
                    Collection<? extends Continuation> c) {
      this.continuations.removeAll(c);
    }

    /**
     * Insert the instructions, variables, etc from b inline
     * in the current block
     * @param b
     * @param insertAtTop whether to insert at top of block or not
     */
    public void insertInline(Block b, boolean insertAtTop) {
      insertInline(b, insertAtTop ? statementIterator() : null);
    }


    public void insertInline(Block b,
          ListIterator<Statement> pos) {
      insertInline(b, null, pos);
    }

    /**
     * Insert the instructions, variables, etc from b inline
     * in the current block
     * @param b
     * @param pos
     */
    public void insertInline(Block b,
          ListIterator<Continuation> contPos,
          ListIterator<Statement> pos) {
      Set<Var> varSet = new HashSet<Var>(this.variables);
      for (Var newVar: b.getVariables()) {
        // Check for duplicates (may be duplicate globals)
        if (!varSet.contains(newVar)) {
          addVariable(newVar);
        }
      }

      if (pos != null) {
        for (Statement stmt: b.getStatements()) {
          stmt.setParent(this);
          pos.add(stmt);
        }
      } else {
        addStatements(b.getStatements());
      }
      for (Continuation c: b.getContinuations()) {
        c.setParent(this);
        if (contPos != null) {
          contPos.add(c);
        } else {
          addContinuation(c);
        }
      }
      this.cleanupActions.addAll(b.cleanupActions);
    }

    public void insertInline(Block b) {
      insertInline(b, false);
    }

    public void removeVarDeclarations(Set<Var> vars) {
      variables.removeAll(vars);
      ListIterator<CleanupAction> it = cleanupActions.listIterator();
      while (it.hasNext()) {
        CleanupAction a = it.next();
        if (vars.contains(a.var)) {
          it.remove();
        }
      }
    }

    public boolean replaceVarDeclaration(String function,
                                         Var oldV, Var newV) {
      if (!this.variables.contains(oldV)) {
        return false;
      }

      Map<Var, Arg> replacement =
          Collections.singletonMap(oldV, Arg.createVar(newV));
      // Must replace everywhere
      this.renameVars(function, replacement, RenameMode.REPLACE_VAR, true);
      return true;
    }

    /**
     * Remove statements by object identity
     * @param stmts
     */
    public void removeStatements(Set<? extends Statement> stmts) {
      ListIterator<Statement> it = this.statementIterator();
      while (it.hasNext()) {
        Statement stmt = it.next();
        if (stmts.contains(stmt)) {
          it.remove();
        }
      }
    }

    /**
     * replace old instructions with new
     * @param newStatements
     */
    public void replaceStatements(List<Statement> newStatements) {
      this.statements.clear();
      this.statements.addAll(newStatements);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb, "");
      return sb.toString();
    }

    public Continuation getParentCont() {
      return parentCont;
    }

    public Function getParentFunction() {
      return parentFunction;
    }

    public Function getFunction() {
      Block curr = this;
      while (curr.getType() != BlockType.MAIN_BLOCK) {
        Block next = curr.getParentCont().parent();
        assert(next != null) : "Block of type " + curr.getType()
            + " had no parent:\n\n" + curr;
        curr = next;
      }
      return curr.getParentFunction();
    }

    public String uniqueVarName(String prefix) {
      return uniqueVarName(prefix, Collections.<String>emptySet());
    }

    /**
     * choose variable name unique within the current function.
     * NOTE: does not avoid clashes with global constants.  Avoid clashes
     * with global constants by providing a prefix or adding global constant
     * names to excluded
     * @param prefix
     * @return
     */
    public String uniqueVarName(String prefix, Set<String> excluded) {
      String name = prefix;
      int attempt = 1;
      Function func = getFunction();
      while (excluded.contains(name) || func.varNameUsed(name)) {
        name = prefix + ":" + attempt;
        attempt++;
      }
      func.addUsedVarName(name);
      return name;
    }

    /**
     * Number of instructions in block and descendents
     * @return
     */
    public int getInstructionCount() {
      StackLite<Block> blocks = new StackLite<Block>();
      blocks.push(this);
      int count = 0;
      while (!blocks.isEmpty()) {
        Block curr = blocks.pop();
        count += curr.statements.size();
        count += curr.cleanupActions.size();
        for (Continuation c: curr.allComplexStatements()) {
          for (Block inner: c.getBlocks()) {
            blocks.push(inner);
          }
        }
      }
      return count;
    }

    /**
     * Set the initial refcount to the base refcount, plus amount provided.
     * Should be called at most once per var
     * @param blockVar
     * @param refcountType
     * @param incr
     */
    public void modifyInitRefcount(Var blockVar, RefCountType rcType,
                                   long incr) {
      long baseRC = RefCounting.baseRefCount(blockVar, rcType, true, true);
      setInitRefcount(blockVar, rcType, baseRC + incr);
    }

    /**
     * Set the initial reference count of a variable to something
     * @param blockVar
     * @param refcountType
     * @param val
     */
    public void setInitRefcount(Var blockVar, RefCountType rcType,
                                   long val) {
      assert(val >= 0);
      HashMap<Var, Arg> refcountMap;
      if (rcType == RefCountType.READERS) {
        refcountMap = this.initReadRefcounts;
      } else {
        assert(rcType == RefCountType.WRITERS);
        refcountMap = this.initWriteRefcounts;
      }
      assert(!refcountMap.containsKey(blockVar)) :
        "Tried to reassign refcount for block var " + blockVar;

      refcountMap.put(blockVar, Arg.createIntLit(val));
    }
  }

  public static enum StatementType {
    INSTRUCTION,
    CONDITIONAL,
  }

  public static interface Statement {
    public StatementType type();
    public Conditional conditional();
    public Instruction instruction();
    public void setParent(Block block);
    public void generate(Logger logger, CompilerBackend gen, GenInfo info);
    public void prettyPrint(StringBuilder sb, String indent);
    /**
     * Note: don't call clone() because of Java 6 limitations in dealing with
     * supertypes when class inheriting from interface and class
     * @return
     */
    public Statement cloneStatement();
    /**
     * Rename vars throughout statement
     * @param function TODO
     * @param replaceInputs
     * @param value
     */
    public void renameVars(String function,
                           Map<Var, Arg> replaceInputs, RenameMode value);
  }

  /** State to pass around when doing code generation from SwiftIC */
  public static class GenInfo {
    /** Function name -> vector of which inputs are blocking */
    private final Map<String, List<Boolean>> compBlockingInputs;

    public GenInfo(Map<String, List<Boolean>> compBlockingInputs) {
      super();
      this.compBlockingInputs = compBlockingInputs;
    }

    public List<Boolean> getBlockingInputVector(String fnName) {
      return compBlockingInputs.get(fnName);
    }
  }

  public static enum RenameMode {
    VALUE, // Replace where same value
    REFERENCE, // Replace where reference to same thing is appropriate
    REPLACE_VAR, // Replace var everywhere (e.g. if renaming)
  }
}
