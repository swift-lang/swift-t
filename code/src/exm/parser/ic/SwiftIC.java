package exm.parser.ic;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import java.util.TreeSet;

import org.apache.log4j.Logger;

import exm.ast.Types.FunctionType;
import exm.ast.Types.FunctionType.InArgT;
import exm.ast.Types.SwiftType;
import exm.ast.Variable;
import exm.ast.Variable.DefType;
import exm.ast.Variable.VariableStorage;
import exm.parser.CompilerBackend;
import exm.parser.ic.ICContinuations.Continuation;
import exm.parser.ic.ICInstructions.Instruction;
import exm.parser.ic.ICInstructions.Oparg;
import exm.parser.ic.ICInstructions.OpargType;
import exm.parser.ic.ICInstructions.Opcode;
import exm.parser.util.ParserRuntimeException;
import exm.parser.util.UndefinedTypeException;
import exm.parser.util.UserException;

/**
 * Program -> Comp Function
 *         -> App Function
 *         -> Comp Function
 *
 * Composite Function -> MainBlock
 *
 * Block -> Instruction
 *       -> Instruction
 *       -> Instruction
 *       -> Continuation       -> Block
 *                             -> Block
 */
public class SwiftIC {

  public static final String indent = ICUtil.indent;
  
  public static class Program {
    /**
     * Use treemap to keep them in alpha order
     */
    private final TreeMap<String, Oparg> globalConsts = 
                                            new TreeMap<String, Oparg>();
    private final HashMap<Oparg, String> globalConstsInv = 
                                            new HashMap<Oparg, String>();

    private final ArrayList<AppFunction> appFuns = new ArrayList<AppFunction>();
    private final ArrayList<CompFunction> compFuns = new ArrayList<CompFunction>();
    private final ArrayList<BuiltinFunction> builtinFuns = new ArrayList<BuiltinFunction>();
  
    public void generate(Logger logger, CompilerBackend gen)
        throws UserException {
      
      Map<String, List<Boolean>> blockVectors = new 
              HashMap<String, List<Boolean>> (compFuns.size());
      for (CompFunction f: compFuns) {
        blockVectors.put(f.getName(), f.getBlockingInputVector());
      }
      GenInfo info = new GenInfo(blockVectors);
      
      logger.debug("Starting to generate program from Swift IC");
      gen.header();
  
      // app functions can't refer to composites, so put these first
      logger.debug("Generating app functions");
      for (AppFunction f: appFuns) {
        f.generate(logger, gen, info);
      }
      logger.debug("Done generating app functions");
  
      logger.debug("Generating builtins");
      for (BuiltinFunction f: builtinFuns) {
        f.generate(logger, gen, info);
      }
      logger.debug("Done generating builtin functions");
  
      logger.debug("Generating composite functions");
      // output composite functions in original order
      for (CompFunction f: compFuns) {
        f.generate(logger, gen, info);
      }
      logger.debug("Done generating composite functions");
  
      gen.turbineStartup();
      
      // Global Constants
      logger.debug("Generating global constants");
      for (Entry<String, Oparg> c: globalConsts.entrySet()) {
        String name = c.getKey();
        Oparg val = c.getValue();
        gen.addGlobal(name, val);
      }
      logger.debug("Done generating global constants");
      
    }
  
    public void addBuiltin(BuiltinFunction fn) {
      this.builtinFuns.add(fn);
    }
  
    public void addComposite(CompFunction fn) {
      this.compFuns.add(fn);
    }
  
    public void addAppFun(AppFunction fn) {
      this.appFuns.add(fn);
    }
  
    public void addAppFuns(Collection<AppFunction> c) {
      appFuns.addAll(c);
    }
  
    public void addComposites(Collection<CompFunction> c) {
      compFuns.addAll(c);
    }
  
    public List<CompFunction> getComposites() {
      return Collections.unmodifiableList(this.compFuns);
    }
  
    public List<AppFunction> getAppFuns() {
      return Collections.unmodifiableList(this.appFuns);
    }
    
    public void addGlobalConst(String name, Oparg val) {
      if (globalConsts.put(name, val) != null) {
        throw new ParserRuntimeException("Overwriting global constant "
            + name);
      }
      globalConstsInv.put(val, name);
    }
    
    /** 
     * Add global const with generated name
     * @param val
     * @return
     */
    public String addGlobalConst(Oparg val) {
      
      String suffix;
      switch(val.type) {
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
        throw new ParserRuntimeException("Variable can't be a constant");
      default:
        throw new ParserRuntimeException("Unknown enum value " + 
                val.type.toString()); 
      }
      
      String origname = Variable.GLOBAL_CONST_VAR_PREFIX + suffix;
      String name = origname;
      int seq = 0;
      while (globalConsts.containsKey(name)) {
        seq++;
        name = origname + "-" + seq;
      }
      addGlobalConst(name, val);
      return name;
    }
    
    public String invLookupGlobalConst(Oparg val) {
      return this.globalConstsInv.get(val);
 
    }
  
    public Oparg lookupGlobalConst(String name) {
      return this.globalConsts.get(name);
 
    }

    public SortedMap<String, Oparg> getGlobalConsts() {
      return Collections.unmodifiableSortedMap(globalConsts);
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      prettyPrint(sb);
      return sb.toString();
    }
  
    public void prettyPrint(StringBuilder out) {
      for (Entry<String, Oparg> constE: globalConsts.entrySet()) {
        Oparg val = constE.getValue();
        out.append("const " +   constE.getKey() + " = ");
        out.append(val.toString());
        out.append(" as " + val.getSwiftType().typeName());
        out.append("\n");
      }
      
      out.append("\n");
      
      for (BuiltinFunction f: builtinFuns) {
        f.prettyPrint(out);
        out.append("\n");
      }
      
      for (AppFunction f: appFuns) {
        f.prettyPrint(out);
        out.append("\n");
      }
  
      for (CompFunction f: compFuns) {
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
        throw new ParserRuntimeException("Error while generating IC: " +
        		e.toString());
      }
    }
  }

  static class AppFunction {
    private final String name;
    private final List<Variable> iList;
    private final List<Variable> oList;
    private final String body;

    public AppFunction(String name, List<Variable> iList, List<Variable> oList,
          String body) {
      super();
      this.name = name;
      this.iList = iList;
      this.oList = oList;
      this.body = body;
    }

    public void prettyPrint(StringBuilder out) {
      out.append("app ");
      out.append("(");
      ICUtil.prettyPrintFormalArgs(out, this.oList);
      out.append(") @" + this.name + "(");
      ICUtil.prettyPrintFormalArgs(out, this.iList);
      out.append(") {");
      out.append(indent + body);
      out.append("\n}\n");
    }

    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      logger.debug("Generating app function " + name);
      gen.defineApp(name, iList, oList, body);
      logger.debug("Done generating app function " + name);
    }

    public String getName() {
      return name;
    }

  }

  static class BuiltinFunction {
    private final String name;
    private final String pkg;
    private final String version;
    private final String symbol;
    private final FunctionType fType;
    

    public BuiltinFunction(String name, String pkg, String version,
                           String symbol, FunctionType fType) {
      this.name    = name;
      this.pkg     = pkg;
      this.version = version;
      this.symbol  = symbol;
      this.fType = fType;
    }

    public void prettyPrint(StringBuilder out) {
      out.append("tcl ");
      out.append("(");
      //ICUtil.prettyPrintFormalArgs(out, this.oList);
      boolean first = true;
      for(SwiftType t: fType.getOutputs()) {
        if (first) {
          first = false;
        } else {
          out.append(", ");
        }
        out.append(t.typeName());
      }
      out.append(") @" + this.name + "(");
      first = true;
      for(InArgT t: fType.getInputs()) {
        if (first) {
          first = false;
        } else {
          out.append(", ");
        }
        out.append(t.typeName());
      }
      out.append(") { ");
      out.append(pkg + "::" + symbol + " ");
      out.append("pkgversion: " + version + " ");
      
      
      out.append(" }\n");
      
    }

    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
    throws UserException {
      logger.debug("generating: " + name);
      gen.defineBuiltinFunction(name, pkg, version, symbol, fType);
    }
  }

  public static class CompFunction {
    private final Block mainBlock;
    private final String name;
    public String getName() {
      return name;
    }


    public List<Boolean> getBlockingInputVector() {
      ArrayList<Boolean> res = new ArrayList<Boolean>(iList.size());
      Set<String> blocks = Variable.nameSet(this.blockingInputs);
      for (Variable input: this.iList) {
        boolean isBlocking = blocks.contains(input.getName());
        res.add(isBlocking);
      }
      return res;
    }


    private final List<Variable> iList;
    private final List<Variable> oList;
    
    /** Wait until the below inputs are available before running function */
    private final List<Variable> blockingInputs;

    private boolean async;

    public CompFunction(String name, List<Variable> iList,
        List<Variable> oList, boolean async) {
      this(name, iList, oList, new Block(BlockType.MAIN_BLOCK),
          async);
    }

    public CompFunction(String name, List<Variable> iList,
        List<Variable> oList, Block mainBlock, boolean async) {
      if (mainBlock.getType() != BlockType.MAIN_BLOCK) {
        throw new ParserRuntimeException("Expected main block " +
        		"for composite function to be tagged as such");
      }
      this.name = name;
      this.iList = iList;
      this.oList = oList;
      this.mainBlock = mainBlock;
      this.blockingInputs = new ArrayList<Variable>();
      this.async = async;
    }


    public List<Variable> getInputList() {
      return Collections.unmodifiableList(this.iList);
    }

    public List<Variable> getOutputList() {
      return Collections.unmodifiableList(this.oList);
    }

    public Block getMainblock() {
      return mainBlock;
    }

    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UserException {
      logger.debug("Generating composite function " + name);
      gen.startCompositeFunction(name, oList, iList, async);
      this.mainBlock.generate(logger, gen, info);
      gen.endCompositeFunction();
      logger.debug("Done generating composite function " + name);
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
    
    public List<Variable> getBlockingInputs() {
      return blockingInputs;
    }
    
    public void addBlockingInput(String vName) {
      Variable v = Variable.findByName(iList, vName);
      if (v == null) {
        throw new ParserRuntimeException(vName + " is not the name of " +
        		" an input argument to function " + name + ":\n" + this);
      }
      addBlockingInput(v);
    }
    
    public void addBlockingInput(Variable v) {
      assert(v.getDefType() == DefType.INARG);
      boolean oneOfArgs = false;
      for (Variable ia: iList) {
        if (ia.getName().equals(v.getName())
            && ia.getType().equals(v.getType())) {
          oneOfArgs = true;
          break;
        }
      }
      if (oneOfArgs) {
        for (Variable i: blockingInputs) {
          if (i.getName().equals(v.getName())) {
            // already there
            return;
          }
        }
        blockingInputs.add(v);
      } else {
        StringBuilder fn = new StringBuilder();
        prettyPrint(fn);
        throw new ParserRuntimeException("Tried to add blocking input" + v +
        		" which wasn't one of the input arguments of function: "
            + this.iList + "\n" + fn.toString());
      }
    }


    public boolean isAsync() {
      return async;
    }
  }


  // Keep track of block type, mainly for purpose of asserts
  enum BlockType {
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

  public static class Block {

    private final BlockType type;
    public Block(BlockType type) {
      this(type, new LinkedList<Instruction>(), new ArrayList<Variable>(),
          new ArrayList<Continuation>(), new ArrayList<Variable>());
    }
    
    /**
     * Used to create duplicate.  This will take ownership of all
     * data structures passed in
     * @param type
     * @param instructions
     */
    private Block(BlockType type, LinkedList<Instruction> instructions, 
        ArrayList<Variable> variables, ArrayList<Continuation> conds,
        ArrayList<Variable> arraysToClose) {
      this.type = type;
      this.instructions = instructions;
      this.variables = variables;
      this.conds = conds;
      this.arraysToClose = arraysToClose;
    }

    /**
     * Make a copy without any shared mutable state
     */
    public Block clone() {
      return this.clone(this.type);
    }
    
    public Block clone(BlockType newType) {
      return new Block(newType, ICUtil.cloneInstructions(this.instructions),
          new ArrayList<Variable>(this.variables),
          ICUtil.cloneContinuations(this.conds), 
          new ArrayList<Variable>(this.arraysToClose));
    }

    public BlockType getType() {
      return type;
    }

    private final LinkedList<Instruction> instructions;
    
    private final ArrayList<Variable> arraysToClose;

    private final ArrayList<Variable> variables;

    /** conditional statements for block */
    private final ArrayList<Continuation> conds;

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
      this.conds.add(c);
    }

    public List<Continuation> getContinuations() {
      return Collections.unmodifiableList(conds);
    }
    
    public Continuation getContinuation(int i) {
      return conds.get(i);
    }

    public List<Variable> getVariables() {
      return Collections.unmodifiableList(variables);
    }

    public Variable declareVariable(SwiftType t, String name,
        VariableStorage storage, DefType defType, String mapping) {
      Variable v = new Variable(t, name, storage, defType, mapping);
      this.variables.add(v);
      return v;
    }
    
    public Variable declareVariable(Variable v) {
      this.variables.add(v);
      return v;
    }

    public boolean isEmpty() {
      return instructions.isEmpty() && conds.isEmpty();
    }

    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {

      logger.trace("Generate code for block of type " + this.type.toString());
      // Can push forward variable declaration to top of block
      for (Variable v: variables) {
        logger.trace("generating variable decl for " + v.toString());
        gen.declare(v.getType(), v.getName(), v.getStorage(), v.getDefType(),
            v.getMapping());
      }
      for (Instruction i: instructions) {
        i.generate(logger, gen, info);
      }

      // Can put conditional statements at end of block, as ordering
      //    doesn't really matter for correctness
      for (Continuation c: conds) {
        logger.trace("generating code for continuation");
        c.generate(logger, gen, info);
      }

      for (Variable v: arraysToClose) {
        gen.closeArray(v);
      }
      logger.trace("Done with code for block of type " + this.type.toString());

    }

    public void prettyPrint(StringBuilder sb, String indent) {
      for (Variable v: variables) {
        sb.append(indent);
        sb.append("decl " + v.getName() + " as " + v.getType().typeName() +
                " <" + v.getStorage().toString().toLowerCase() + ">\n");
      }

      for (Instruction i: instructions) {
        sb.append(indent);
        sb.append(i.toString());
        sb.append("\n");
      }

      for (Continuation c: conds) {
        c.prettyPrint(sb, indent);
      }

      for (Variable v: arraysToClose) {
        sb.append(indent);
        sb.append(Opcode.ARRAY_CLOSE.toString().toLowerCase() + " " +
                                v.getName());
        sb.append("\n");
      }
    }

    public ListIterator<Continuation> continuationIterator() {
      return conds.listIterator();
    }

    public ListIterator<Variable> variableIterator() {
      return variables.listIterator();
    }
    public List<Instruction> getInstructions() {
      return Collections.unmodifiableList(instructions);
    }

    public ListIterator<Instruction> instructionIterator() {
      return instructions.listIterator();
    }
    
    public void addArrayToClose(Variable array) {
      this.arraysToClose.add(array);
    }
    
    public void addArraysToClose(Collection<Variable> arrays) {
      this.arraysToClose.addAll(arrays);
    }
    
    public List<Variable> getArraysToClose() {
      return Collections.unmodifiableList(arraysToClose);
    }

    /**
     * Rename variables in block (and nested blocks) according to map.
     * If the map doesn't have an entry, we don't rename anything
     *
     * @param inputsOnly: if true, only change references which are reading
     *      the var.  if false, completely remove the old variable and replace 
     *      with new
     * @param renames OldName -> NewName
     * @return
     */
    public void renameVars(Map<String, Oparg> renames, boolean inputsOnly) {
      if (!inputsOnly) {
        // Replace definition of var
        for (int i = 0; i < variables.size(); i++) {
          String varName = variables.get(i).getName();
          if (renames.containsKey(varName)) {
            variables.remove(i);
            Oparg replacement = renames.get(varName);
            if (replacement.getType() ==  OpargType.VAR) {
              variables.add(i, replacement.getVariable());
            } else {
              // value replaced with constant
              i--; // update
            }
          }
        }
      }
      
      for (Instruction i: instructions) {
        if (inputsOnly) {
          i.renameInputs(renames);
        } else {
          i.renameVars(renames);
        }
      }

      // Rename in nested blocks
      for (Continuation c: conds) {
        if (inputsOnly) {
          c.replaceInputs(renames);
        } else {
          c.replaceVars(renames);
        }
      }
      renameArraysToClose(renames);
    }


    public void renameArraysToClose(Map<String, Oparg> renames) {
      for (int i = 0; i < arraysToClose.size(); i++) {
        String varName = arraysToClose.get(i).getName();
        if (renames.containsKey(varName)) {
          arraysToClose.remove(i);
          arraysToClose.add(i, renames.get(varName).getVariable());
        }
      }
    }

    /**
     * Remove the variable from this block and all internal constructs,
     *    removing all instructions with this as output
     * preconditions: variable is not used as input for any instruction,
     *            variable is not used as output for any instruction with a sideeffect,
     *            variable is not required for any constructs
     * @param varName
     */
    public void removeVars(Set<String> removeVars) {
      int n = variables.size();
      for (int i = 0; i < n; i++) {
        String currVar = variables.get(i).getName();
        if (removeVars.contains(currVar)) {
          variables.remove(i);
          i--; // account for list being shorter
          n--;
        }
      }

      n = arraysToClose.size();
      for (int i = 0; i < n; i++) {
        String currVar = arraysToClose.get(i).getName();
        if (removeVars.contains(currVar)) {
          arraysToClose.remove(i);
          i--; // account for list being shorter
          n--;
        }
      }

      int icount = instructions.size();
      for (int i = 0; i < icount; i++) {
        Instruction inst = instructions.get(i);
        inst.removeVars(removeVars);
        // See if we can remove instruction
        if (!inst.hasSideEffects() && inst.op != Opcode.COMMENT) {
          boolean allRemoveable = true;
          for (Oparg oa: inst.getOutputs()) {
            // Doesn't make sense to assign to anything other than
            //  variable
            assert(oa.getType() == OpargType.VAR);
            if (! removeVars.contains(oa.getVariable().getName())) {
              allRemoveable = false; break;
            }
          }
          if (allRemoveable) {
            instructions.remove(i);
            // account for removed instruction
            i--;
            icount--;
          }
        }
      }
      for (Continuation c: conds) {
        c.removeVars(removeVars);
      }
    }


    public void addVariables(List<Variable> variables) {
      this.variables.addAll(variables);
    }

    public void addVariable(Variable variable) {
      this.variables.add(variable);
    }

    public void addContinuations(List<Continuation> continuations) {
      this.conds.addAll(continuations);
    }
    
    /*
    public void replaceInstruction(int i, Instruction inst) {
      this.instructions.set(i, inst);

    }*/

    /** replace one instruction with multiple instructions */
    /*public void replaceInstruction(int pos,
                                List<Instruction> replacements) {
      instructions.remove(pos);
      instructions.addAll(pos, replacements);
    }*/

    /*public void insertInstruction(int i, Instruction inst) {
      this.instructions.add(i, inst);
    } */

    public Set<String> unneededVars() {
      HashSet<String> toRemove = new HashSet<String>();
      Set<String> stillNeeded = findEssentialVars();
      for (Variable v: getVariables()) {
        if (!stillNeeded.contains(v.getName())) {
          toRemove.add(v.getName());
        }
      }
      return toRemove;
    }

    public Set<String> findEssentialVars() {
      HashSet<String> stillNeeded = new HashSet<String>();
      findEssentialVars(stillNeeded);
      return stillNeeded;

    }

    private void findEssentialVars(HashSet<String> stillNeeded) {
      // Need to hold on to mapped variables
      for (Variable v: this.getVariables()) {
        if (v.isMapped()) {
          stillNeeded.add(v.getName());
        }
      }
      for (Instruction i : this.getInstructions()) {
        // check which variables are still needed
        for (Oparg oa: i.getInputs()) {
          if (oa.getType() == OpargType.VAR) {
            stillNeeded.add(oa.getVariable().getName());
          }
        }
        // Can't eliminate instructions with side-effects
        if (i.hasSideEffects()) {
          for (Oparg oa: i.getOutputs()) {
            if (oa.getType() == OpargType.VAR) {
              stillNeeded.add(oa.getVariable().getName());
            }
          }
        }
      }

      for (Continuation c: this.getContinuations()) {
        for (Variable v: c.requiredVars()) {
          stillNeeded.add(v.getName());
        }
        for (Block b: c.getBlocks()) {
          b.findEssentialVars(stillNeeded);
        }
      }
      
      for (Variable v: this.arraysToClose) {
        if (v.getStorage() == VariableStorage.ALIAS) {
          stillNeeded.add(v.getName());
        }
      }
    }

    public void removeContinuation(Continuation c) {
      this.conds.remove(c);
    }

    /**
     * Insert the instructions, variables, etc from b inline
     * in the current block
     * @param b
     */
    public void insertInline(Block b) {
      Set<String> varNames = Variable.nameSet(this.variables);
      for (Variable newVar: b.getVariables()) {
        // Check for duplicates (may be duplicate globals)
        if (!varNames.contains(newVar.getName())) {
          variables.add(newVar);
        }
      }
      this.addContinuations(b.getContinuations());
      this.addInstructions(b.getInstructions());
      this.addArraysToClose(b.getArraysToClose());
    }

    public void removeVarDeclarations(Set<String> varNames) {
      int n = variables.size();
      for (int i = 0; i < n; i++) {
        if (varNames.contains(variables.get(i).getName())) {
          variables.remove(i);
          i--;
          n--;
        }
      }
    }

    public void removeVarDeclaration(String name) {
      TreeSet<String> tmp = new TreeSet<String>();
      tmp.add(name);
      removeVarDeclarations(tmp);
    }
  }
  
  /** State to pass around when doing code generation from SwiftIC */
  static class GenInfo {
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
}
