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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
import exm.stc.common.TclFunRef;
import exm.stc.common.CompilerBackend.VarDecl;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.Pair;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;

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
    /**
     * Use treemap to keep them in alpha order
     */
    private final TreeMap<String, Arg> globalConsts = 
                                            new TreeMap<String, Arg>();
    private final HashMap<Arg, String> globalConstsInv = 
                                            new HashMap<Arg, String>();
    /**
     * Corresponding variable declarations
     */
    private final Set<Var> globalVars = new HashSet<Var>();

    private final ArrayList<Function> functions = new ArrayList<Function>();
    private final ArrayList<BuiltinFunction> builtinFuns = new ArrayList<BuiltinFunction>();
    private final Set<Pair<String, String>> required = new HashSet<Pair<String, String>>();
  
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
      gen.header();
      
      logger.debug("Generating required packages");
      for (Pair<String, String> req: required) {
        gen.requirePackage(req.val1, req.val2);
      }
      logger.debug("Done generating required packages");
  
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
  
      gen.turbineStartup();
      
      // Global Constants
      logger.debug("Generating global constants");
      for (Entry<String, Arg> c: globalConsts.entrySet()) {
        String name = c.getKey();
        Arg val = c.getValue();
        gen.addGlobal(name, val);
      }
      logger.debug("Done generating global constants");
      
    }
    
    public void addRequiredPackage(String pkg, String version) {
      required.add(Pair.create(pkg, version));
    }
  
    public void addBuiltin(BuiltinFunction fn) {
      this.builtinFuns.add(fn);
    }
  
    public void addFunction(Function fn) {
      this.functions.add(fn);
    }
  
    public void addFunctions(Collection<Function> c) {
      functions.addAll(c);
    }
  
    public List<Function> getFunctions() {
      return Collections.unmodifiableList(this.functions);
    }
    
    public ListIterator<Function> functionIterator() {
      return functions.listIterator();
    }
    
    public ListIterator<BuiltinFunction> builtinIterator() {
      return builtinFuns.listIterator();
    }

    public void addGlobalConst(String name, Arg val) {
      if (globalConsts.put(name, val) != null) {
        throw new STCRuntimeError("Overwriting global constant "
            + name);
      }
      globalConstsInv.put(val, name);
      Var globalVar;
      if (val.isVar()) {
        globalVar = val.getVar();
        assert(globalVar.defType() == DefType.GLOBAL_CONST);
      } else {
        globalVar = new Var(val.getType(), name, VarStorage.GLOBAL_CONST,
                            DefType.GLOBAL_CONST);
      }
      globalVars.add(globalVar);
    }
    
    /** 
     * Add global const with generated name
     * @param val
     * @return
     */
    public String addGlobalConst(Arg val) {
      
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
      while (globalConsts.containsKey(name)) {
        seq++;
        name = origname + "-" + seq;
      }
      addGlobalConst(name, val);
      return name;
    }
    
    public void removeGlobalConst(String unused) {
      Arg val = globalConsts.remove(unused);
      globalConstsInv.remove(val);
    }

    public String invLookupGlobalConst(Arg val) {
      return this.globalConstsInv.get(val);
 
    }
  
    public Arg lookupGlobalConst(String name) {
      return this.globalConsts.get(name);
 
    }

    public SortedMap<String, Arg> getGlobalConsts() {
      return Collections.unmodifiableSortedMap(globalConsts);
    }
    
    public Collection<Var> getGlobalVars() {
      return globalVars;
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb);
      return sb.toString();
    }
  
    public void prettyPrint(StringBuilder out) {
      for (Pair<String, String> req: required) {
        out.append("require " + req.val1 + "::" + req.val2 + "\n");
      }
      
      for (Entry<String, Arg> constE: globalConsts.entrySet()) {
        Arg val = constE.getValue();
        out.append("const " +   constE.getKey() + " = ");
        out.append(val.toString());
        out.append(" as " + val.getType().typeName());
        out.append("\n");
      }
      
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
  }

  public static class BuiltinFunction {
    private final String name;
    private final TclFunRef impl;
    private final FunctionType fType;
    

    public BuiltinFunction(String name, FunctionType fType,
                           TclFunRef impl) {
      this.name = name;
      this.impl = impl;
      this.fType = fType;
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
      out.append(") { ");
      out.append(impl.pkg + "::" + impl.symbol + " ");
      out.append("pkgversion: " + impl.version + " ");
      
      
      out.append(" }\n");
      
    }

    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
    throws UserException {
      logger.debug("generating: " + name);
      gen.defineBuiltinFunction(name, fType, impl);
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
        boolean isBlocking = blockingInputs.contains(input);
        res.add(isBlocking);
      }
      return res;
    }


    private final List<Var> iList;
    private final List<Var> oList;
    /** List of which outputs are write-only */
    private final List<Boolean> oListWriteOnly;
    
    /** Wait until the below inputs are available before running function.
     * Treated same as DATA_ONLY wait */
    private final List<Var> blockingInputs;

    private TaskMode mode;

    public Function(String name, List<Var> iList,
        List<Var> oList, TaskMode mode) {
      this(name, iList, oList, mode, new Block(BlockType.MAIN_BLOCK, null));
      this.mainBlock.setParent(this);
    }

    public Function(String name, List<Var> iList,
        List<Var> oList, TaskMode mode, Block mainBlock) {
      if (mainBlock.getType() != BlockType.MAIN_BLOCK) {
        throw new STCRuntimeError("Expected main block " +
        "for function to be tagged as such");
      }
      this.name = name;
      this.iList = iList;
      this.oList = oList;
      this.oListWriteOnly = new ArrayList<Boolean>(oList.size());
      for (int i = 0; i< oList.size(); i++) {
        // Assume read-write by default
        this.oListWriteOnly.add(false);
      }
      this.mode = mode;
      this.mainBlock = mainBlock;
      this.blockingInputs = new ArrayList<Var>();
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
      return oListWriteOnly.get(i);
    }
    
    public void makeOutputWriteOnly(int i) {
      oListWriteOnly.set(i, true);
    }
    
    /**
     * list outputs augmented with info about whether they are write-only
     * @return
     */
    public List<PassedVar> getPassedOutputList() {
      ArrayList<PassedVar> res = new ArrayList<PassedVar>();
      assert(oList.size() == oListWriteOnly.size());
      for (int i = 0; i < oList.size(); i++) {
        res.add(new PassedVar(oList.get(i), oListWriteOnly.get(i)));
      }
      // TODO Auto-generated method stub
      return res;
    }


    public Block getMainblock() {
      return mainBlock;
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
      sb.append("#waiton[");
      ICUtil.prettyPrintVarList(sb, this.blockingInputs);
      sb.append("] {\n");
      mainBlock.prettyPrint(sb, indent);
      sb.append("}\n");
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb);
      return sb.toString();
    }
    
    public List<Var> getBlockingInputs() {
      return blockingInputs;
    }
    
    public void addBlockingInput(Var var) {
      if (!iList.contains(var)) {
        throw new STCRuntimeError(var + " is not the name of " +
        " an input argument to function " + name + ":\n" + this);
      }
      for (Var i: blockingInputs) {
        if (i.equals(var)) {
          // already there
          return;
        }
      }
      blockingInputs.add(var);
    }
    
    public boolean isAsync() {
      return this.mode != TaskMode.SYNC;
    }
    
    public boolean varNameUsed(String name) {
      // TODO: keep set of used var names here?
      Deque<Block> blocks = new ArrayDeque<Block>();
      blocks.add(this.mainBlock);
      while (!blocks.isEmpty()) {
        Block curr = blocks.pop();
        for (Var v: curr.variables) {
          if (v.name().equals(name))
            return true;
        }
        for (Continuation c: curr.getContinuations()) {
          for (Var cv: c.constructDefinedVars())
            if (cv.name().equals(name))
              return true;
          for (Block inner: c.getBlocks()) {
            blocks.push(inner);
          }
        }
      }
      return false;
    }
    
    public boolean varNameUsed(String name) {
      // TODO: keep set of used var names here?
      Deque<Block> blocks = new ArrayDeque<Block>();
      blocks.add(this.mainBlock);
      while (!blocks.isEmpty()) {
        Block curr = blocks.pop();
        for (Var v: curr.variables) {
          if (v.name().equals(name))
            return true;
        }
        for (Continuation c: curr.getContinuations()) {
          List<Var> constructVars = c.constructDefinedVars();
          if (constructVars != null && constructVars.contains(name)) {
            return true;
          }
          for (Block inner: c.getBlocks()) {
            blocks.push(inner);
          }
        }
      }
      return false;
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
    RANGELOOP_BODY
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
    
    public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
      action.renameVars(renames, mode);

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
    
    public Block(BlockType type, Continuation parentCont, Function parentFunction) {
      this(type, parentCont, parentFunction, new LinkedList<Instruction>(),
<<<<<<< HEAD
          new ArrayList<Var>(), new HashMap<Var, Arg>(), new HashMap<Var, Arg>(),
          new ArrayList<Continuation>(), new ArrayList<CleanupAction>());
=======
          new ArrayList<Var>(), new ArrayList<Continuation>(), new ArrayList<CleanupAction>());
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
    }
    
    /**
     * Used to create duplicate.  This will take ownership of all
     * data structures passed in
     * @param type
     * @param instructions
     */
    private Block(BlockType type,
        Continuation parentCont, Function parentFunction,
        LinkedList<Instruction> instructions, 
<<<<<<< HEAD
        ArrayList<Var> variables, HashMap<Var, Arg> initReadRefcounts,
        HashMap<Var, Arg> initWriteRefcounts,
        ArrayList<Continuation> conds,
=======
        ArrayList<Var> variables, ArrayList<Continuation> conds,
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
        ArrayList<CleanupAction> cleanupActions) {
      setParent(type, parentCont, parentFunction);
      this.type = type;
      this.instructions = instructions;
      this.variables = variables;
      this.initReadRefcounts = initReadRefcounts;
      this.initWriteRefcounts = initWriteRefcounts;
      this.continuations = conds;
      this.cleanupActions = cleanupActions;
    }

    public void setParent(BlockType type, Continuation parentCont,
        Function parentFunction) {
      this.parentCont = parentCont;
      this.parentFunction = parentFunction;
    }

    public void setParent(Continuation parent) {
      setParent(type, parent, null);
    }
    
    public void setParent(Function parent) {
      setParent(BlockType.MAIN_BLOCK, null, parent);
    }

    /**
     * Make a copy without any shared mutable state
     */
    @Override
    public Block clone() {
      return this.clone(this.type, this.parentCont, this.parentFunction);
    }
    
    public Block clone(Function parentFunction) {
      return this.clone(BlockType.MAIN_BLOCK, null, parentFunction);
    }
    
    public Block clone(BlockType newType, Continuation parentCont) {
      return this.clone(BlockType.MAIN_BLOCK, parentCont, null);
    }
    
    public Block clone(BlockType newType, Continuation parentCont,
                       Function parentFunction) {
<<<<<<< HEAD
      Block cloned = new Block(newType, parentCont, parentFunction,
=======
      return new Block(newType, parentCont, parentFunction,
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
          ICUtil.cloneInstructions(this.instructions),
          new ArrayList<Var>(this.variables),
          new HashMap<Var, Arg>(this.initReadRefcounts),
          new HashMap<Var, Arg>(this.initWriteRefcounts),
          new ArrayList<Continuation>(), 
          ICUtil.cloneCleanups(this.cleanupActions));
      for (Continuation c: this.continuations) {
        // Add in way that ensures parent link updated
        cloned.addContinuation(c.clone());
      }
      return cloned;
    }

    public BlockType getType() {
      return type;
    }

    private final LinkedList<Instruction> instructions;
    
    private final ArrayList<CleanupAction> cleanupActions;

    private final ArrayList<Var> variables;
    
    /** Initial reference counts for vars defined in block */
    private final HashMap<Var, Arg> initReadRefcounts;
    private final HashMap<Var, Arg> initWriteRefcounts;

    /** conditional statements for block */
    private final ArrayList<Continuation> continuations;

    public void addInstruction(Instruction e) {
      instructions.add(e);
    }

    public void addInstructionFront(Instruction e) {
      instructions.addFirst(e);
    }
    
    public void addInstructions(List<Instruction> instructions) {
      this.instructions.addAll(instructions);
    }

    public void addContinuation(Continuation c) {
      c.setParent(this);
      this.continuations.add(c);
    }

    public List<Continuation> getContinuations() {
      return Collections.unmodifiableList(continuations);
    }
    
    public ListIterator<Continuation> getContinuationIterator() {
      return continuations.listIterator();
    }
    
    public void addContinuation(ListIterator<Continuation> it, Continuation c) {
      it.add(c);
      c.setParent(this);
    }
    
    public Continuation getContinuation(int i) {
      return continuations.get(i);
    }

    public List<Var> getVariables() {
      return Collections.unmodifiableList(variables);
    }

    public Var declareVariable(Type t, String name,
        VarStorage storage, DefType defType, Var mapping) {
      assert(mapping == null || Types.isString(mapping.type()));
      Var v = new Var(t, name, storage, defType, mapping);
      this.variables.add(v);
      return v;
    }
    
    public Var declareVariable(Var v) {
      this.variables.add(v);
      return v;
    }

    public boolean isEmpty() {
      return instructions.isEmpty() && continuations.isEmpty();
    }

    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {

      logger.trace("Generate code for block of type " + this.type.toString());
      // Pass variable declarations as batch
      List<VarDecl> declarations = new ArrayList<VarDecl>(variables.size());
      for (Var v: variables) {
        logger.trace("generating variable decl for " + v.toString());
        Arg initReaders = initReadRefcounts.get(v);
        Arg initWriters = initWriteRefcounts.get(v);
        
        // Initialize refcounts to default value if not
        //  explicitly overridden and check for bad refcounts
        if (v.storage() != VarStorage.ALIAS && 
            RefCounting.hasReadRefCount(v)) {
          if (initReaders == null)
            initReaders = Arg.ONE;
        } else {
          assert(initReaders == null);
        }
        if (v.storage() != VarStorage.ALIAS && 
            RefCounting.hasWriteRefCount(v)) {
          if (initWriters == null)
            initWriters = Arg.ONE;
        } else {
          assert(initWriters == null);
        }
        declarations.add(new VarDecl(v, initReaders, initWriters));
      }
      gen.declare(declarations);
      
      for (Instruction i: instructions) {
        i.generate(logger, gen, info);
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

    public void prettyPrint(StringBuilder sb, String indent) {
      for (Var v: variables) {
        sb.append(indent);
        sb.append("alloc " + v.type().typeName() + " " + v.name() + 
                " <" + v.storage().toString().toLowerCase() + ">");
        
        if (v.isMapped()) {
          sb.append(" @mapping=" + v.mapping().name());
        }
        if (initReadRefcounts.containsKey(v)) {
          sb.append(" <readers=" + initReadRefcounts.get(v) + ">");
        }
        if (initWriteRefcounts.containsKey(v)) {
          sb.append(" <writers=" + initWriteRefcounts.get(v) + ">");
        }
        sb.append("\n");
      }

      for (Instruction i: instructions) {
        sb.append(indent);
        sb.append(i.toString());
        sb.append("\n");
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

    public ListIterator<Continuation> continuationIterator() {
      return continuations.listIterator();
    }
    
    public ListIterator<Continuation> continuationIterator(int pos) {
      return continuations.listIterator(pos);
    }

    public ListIterator<Var> variableIterator() {
      return variables.listIterator();
    }
    public List<Instruction> getInstructions() {
      return Collections.unmodifiableList(instructions);
    }

    public ListIterator<Instruction> instructionIterator() {
      return instructions.listIterator();
    }
    
    public ListIterator<Instruction> instructionIterator(int i) {
      return instructions.listIterator(i);
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
     * @param renames OldName -> NewName
     * @param mode
     * @param recursive if it should be done on child blocks
     */
    public void renameVars(Map<Var, Arg> renames, RenameMode mode, boolean recursive) {
      if (renames.isEmpty())
        return;
      renameInDefs(renames, mode);
      renameInCode(renames, mode, recursive);
    }

    private void renameInDefs(Map<Var, Arg> renames, RenameMode mode) {
      // Track any changes to mapped vars, list of (original, new)
      List<Pair<Var, Var>> changedMappedVars = new ArrayList<Pair<Var, Var>>();
      
      ListIterator<Var> it = variables.listIterator();
      while (it.hasNext()) {
        // The original variable and the current one
        Var original, var;
        var = original = it.next();
        boolean removed = false;
        if (mode == RenameMode.REPLACE_VAR) {
          if (renames.containsKey(var)) {
            Arg replacement = renames.get(var);
            if (replacement.isVar()) {
              var = replacement.getVar();
              it.set(var);
            } else {
              // value replaced with constant
              it.remove();
              removed = true;
            }
          }
        }

        // Check to see if string variable for mapping is replaced
        if (!removed && var.isMapped() && renames.containsKey(var.mapping())) { 
          // Can't have double mapping
          assert(!var.mapping().isMapped());
          if (mode == RenameMode.VALUE) {
            // TODO: can't replace mapping since we rely on the rest of
            //      the rename pass to fix up references to the old instance
            //      of the variable
          } else {
            Arg newMapping = renames.get(var.mapping());
            if (newMapping.isVar() &&
                !newMapping.getVar().equals(var.mapping())) {
              // Need to maintain variable ordering so that mapped vars appear
              // after the variables containing the mapping string. Remove
              // var declaration here and put it at end of list
              it.remove();
              var = new Var(var.type(), var.name(), var.storage(),
                            var.defType(), newMapping.getVar());
              
              changedMappedVars.add(Pair.create(original, var));
            }
          }
        }
      }

      if (!changedMappedVars.isEmpty()) {
        //  Update mapped variable instances in child blocks
        for (Pair<Var, Var> change: changedMappedVars) {
          Var oldV = change.val1;
          Var newV = change.val2;
          this.variables.add(newV);
          renames.put(oldV, Arg.createVar(newV));
        }
      }
    }

    private void renameInCode(Map<Var, Arg> renames, RenameMode mode,
                              boolean recursive) {
      for (Instruction i: instructions) {
        i.renameVars(renames, mode);
      }

      // Rename in nested blocks
      for (Continuation c: continuations) {
        c.replaceVars(renames, mode, recursive);
      }
      renameCleanupActions(renames, mode);
    }
    
    public void renameCleanupActions(Map<Var, Arg> renames, RenameMode mode) {
      for (CleanupAction a: cleanupActions) {
        a.renameVars(renames, mode);
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

      ListIterator<Instruction> it = instructionIterator();
      while (it.hasNext()) {
        Instruction inst = it.next();
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
      }
      for (Continuation c: continuations) {
        c.removeVars(removeVars);
      }
    }


    public void addVariables(List<Var> variables) {
      this.variables.addAll(variables);
    }

    public void addVariable(Var variable) {
      addVariable(variable, false);
    }
<<<<<<< HEAD
    
    public void addVariable(Var variable, boolean atTop) {
      if (atTop) {
        this.variables.add(0, variable);
      } else {
        this.variables.add(variable);
=======

    public void addContinuations(List<? extends Continuation>
                                                continuations) {
      for (Continuation c: continuations) {
        addContinuation(c);
      }
    }

    public Set<String> unneededVars() {
      HashSet<String> toRemove = new HashSet<String>();
      Pair<Set<String>, List<List<Var>>> res = findEssentialVars();
      Set<String> stillNeeded = res.val1; 
      
      // Check to see if we have to retain additional
      // variables based on interdependencies
      for (List<Var> dependentSet: res.val2) { {
        boolean needed = false;
        for (Var v: dependentSet) {
          if (stillNeeded.contains(v.name())) {
            needed = true;
            break;
          }
        }
        if (needed) {
          for (Var v: dependentSet) {
            stillNeeded.add(v.name());
          }
        }
      }
        
      }
      for (Var v: getVariables()) {
        if (!stillNeeded.contains(v.name())) {
          toRemove.add(v.name());
        }
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
      }
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
      insertInline(b, insertAtTop ? instructionIterator() : null);
    }
    

    public void insertInline(Block b,
          ListIterator<Instruction> pos) {
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
          ListIterator<Instruction> pos) {
      Set<Var> varSet = new HashSet<Var>(this.variables);
      for (Var newVar: b.getVariables()) {
        // Check for duplicates (may be duplicate globals)
        if (!varSet.contains(newVar)) {
          variables.add(newVar);
        }
      }
      if (pos != null) {
        for (Instruction i: b.getInstructions()) {
          pos.add(i);
        }
      } else {
        this.instructions.addAll(b.getInstructions());
      }
      for (Continuation c: b.getContinuations()) {
        c.setParent(this);
        if (contPos != null) {
          contPos.add(c);
        } else {
<<<<<<< HEAD
          addContinuation(c);
=======
          this.continuations.add(c);
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
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
    
    public boolean replaceVarDeclaration(Var oldV, Var newV) {
      if (!this.variables.contains(oldV)) {
        return false;
      }

      Map<Var, Arg> replacement = 
          Collections.singletonMap(oldV, Arg.createVar(newV));
      // Must replace everywhere
      this.renameVars(replacement, RenameMode.REPLACE_VAR, true);
      return true;
    }

    /**
     * Remove instructions by object identity
     * @param insts
     */
    public void removeInstructions(Set<Instruction> insts) {
      ListIterator<Instruction> it = this.instructionIterator();
      while (it.hasNext()) {
        Instruction i = it.next();
        if (insts.contains(i)) {
          it.remove();
        }
      }
    }

    /**
     * replace old instructions with new
     * @param newInstructions
     */
    public void replaceInstructions(List<Instruction> newInstructions) {
      this.instructions.clear();
      this.instructions.addAll(newInstructions);
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
     * @param excluded
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
      return name;
    }
<<<<<<< HEAD

    /**
     * Number of instructions in block and descendents
     * @return
     */
    public int getInstructionCount() {
      Deque<Block> blocks = new ArrayDeque<Block>();
      blocks.push(this);
      int count = 0;
      while (!blocks.isEmpty()) {
        Block curr = blocks.pop();
        count += curr.instructions.size();
        count += curr.cleanupActions.size();
        for (Continuation c: curr.getContinuations()) {
          for (Block inner: c.getBlocks()) {
            blocks.push(inner);
          }
        }
      }
      return count;
    }

    /**
     * Set the initial reference count of a variable to something
     * @param blockVar
     * @param refcountType
     * @param val
     */
    public void setInitRefcount(Var blockVar, RefCountType refcountType,
                                   long val) {
      assert(val >= 0);
      HashMap<Var, Arg> refcountMap;
      if (refcountType == RefCountType.READERS) {
        refcountMap = this.initReadRefcounts;
      } else {
        assert(refcountType == RefCountType.WRITERS);
        refcountMap = this.initWriteRefcounts;
      }
      assert(!refcountMap.containsKey(blockVar)) :
        "Tried to reassign refcount for block var " + blockVar;
      
      refcountMap.put(blockVar, Arg.createIntLit(val));
    }
=======
>>>>>>> 0a77064... Add infrastructure to choose unique var names without appending sequential number.
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
