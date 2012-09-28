package exm.stc.ic.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.ExtArgType;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgType;
import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.FunctionSemantics;
import exm.stc.common.lang.OpEvaluator;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Variable;
import exm.stc.common.lang.Variable.VariableStorage;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.ComputedValue;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.tree.ICTree.GenInfo;
/**
 * This class contains instructions used in the intermediate representation.
 * Each instruction is responsible for making particular modifications to
 * itself, and for reporting particular information about itself. The
 * Instruction interface has a number of methods that each instruction
 * must implement for this purpose.
 *
 */
public class ICInstructions {
  public static abstract class Instruction {
    public final Opcode op;
  
    public Instruction(Opcode op) {
      super();
      this.op = op;
    }
  
    public void removeVars(Set<String> removeVars) {
      // default impl: do nothing
    }
  
    /**
     * Replace any reference to a key in the map with the value 
     * @param renames
     */
    public abstract void renameVars(Map<String, Arg> renames);
  
    /**
     * Replace any input variable with a replacement, which is another
     * variable in scope which should have the same value
     * Assume that the variable being replaced will be kept around
     * @param replacements
     */
    public abstract void renameInputs(Map<String, Arg> renames);

    @Override
    public abstract String toString();
  
    public abstract void generate(Logger logger, CompilerBackend gen,
            GenInfo info);
  
  
    /** List of variables the instruction reads */
    public abstract List<Arg> getInputs();
  
    /** List of variables the instruction writes */
    public abstract List<Variable> getOutputs();
    
    public Arg getInput(int i) {
      return getInputs().get(i);
    }
    
    public Variable getOutput(int i) {
      return getOutputs().get(i);
    }
  
    public abstract boolean hasSideEffects();
  
    public boolean writesAliasVar() {
      // Writes to alias variables can have non-local effects
      for (Variable out: this.getOutputs()) {
        if (out.getStorage() == VariableStorage.ALIAS) {
          return true;
        }
      }
      return false;
    }
    
    public boolean writesMappedVar() {
      // Writes to alias variables can have non-local effects
      for (Variable out: this.getOutputs()) {
        if (out.getMapping() != null) {
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
    public abstract Map<String, Arg> constantFold(
                    String fnName,
                    Map<String, Arg> knownConstants);
  
    /**
     * @param knownConstants
     * @return an instruction if this can be replaced by another instruction
     *      using a constant value, null if it cannot be replaced
     */
    public abstract Instruction constantReplace(
                                Map<String, Arg> knownConstants);
  
    
    public static class MakeImmRequest {
      public final List<Variable> out;
      public final List<Variable> in;
      public MakeImmRequest(List<Variable> out, List<Variable> in) {
        this.out = out;
        this.in = in;
      }
    }
    
    public static class MakeImmChange {
      /** Optional: if the output variable of op changed */
      public final Variable newOut;
      public final Variable oldOut;
      public final Instruction newInst;
      
      /**
       * If the output variable changed from reference to plain future
       * @param newOut
       * @param oldOut
       * @param newInst
       */
      public MakeImmChange(Variable newOut, Variable oldOut, Instruction newInst) {
        this.newOut = newOut;
        this.oldOut = oldOut;
        this.newInst = newInst;
      }
      
      /**
       * If we're just changing the instruction
       * @param newInst
       */
      public MakeImmChange(Instruction newInst) {
        this(null, null, newInst);
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
     * @param assumeAllInputsClosed if true, allowed to (must don't necessarily
     *        have to) assume that all input vars are closed
     * @return null if it cannot be made immediate, if true,
     *            a list of vars that are the variables whose values are needed
     *            and output vars that need to be have value vars created
     */
    public abstract MakeImmRequest canMakeImmediate(Set<String> closedVars, 
                                                  boolean assumeAllClosed);

    public abstract MakeImmChange makeImmediate(List<Variable> outVals,
                                                List<Arg> inValues);

    /**
     * @return the futures this instruction will block on
     *        it is ok if it forgets variables which aren't blocked on,
     *        but all variables returned must be blocked on
     */
    public abstract List<Variable> getBlockingInputs();
    
    /**
     * @param existing already known values (sometimes needed to 
     *              work out which vales are created by an instruction)
     * @return a list of all values computed by expression.  Each ComputedValue
     *        returned should have the out field set so we know where to find 
     *        it 
     */
    public abstract List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Arg> existing);
   
    public abstract Instruction clone();
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
    public void renameVars(Map<String, Arg> renames) {
      // Don't do anything
    }

    @Override
    public void renameInputs(Map<String, Arg> replacements) {
      // Nothing
    }
  
    @Override
    public List<Arg> getInputs() {
      return new ArrayList<Arg>(0);
    }
  
    @Override
    public List<Variable> getOutputs() {
      return new ArrayList<Variable>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return false;
    }
  
    @Override
    public Map<String, Arg> constantFold(String fnName,
                Map<String, Arg> knownConstants) {
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars, boolean assumeAllInputsClosed) {
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on comment!");
    }

    @Override
    public List<Variable> getBlockingInputs() {
      return null;
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Arg> existing) {
      return null;
    }

    @Override
    public Instruction clone() {
      return new Comment(this.text);
    }
  }
  
  /**
   * Class to represent builtin Turbine operations with fixed number
   * of arguments
   */
  public static class TurbineOp extends Instruction {
    /** Private constructor: use static methods to create */
    private TurbineOp(Opcode op, List<Arg> args) {
      super(op);
      this.args = args;
    }
  
    private final List<Arg> args;
  
    @Override
    public String toString() {
      String result = op.toString().toLowerCase();
      for (Arg v: args) {
        result += " " + v.toString();
      }
      return result;
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      // Recreate calls that were used to generate this instruction
      switch (op) {
      case STORE_INT:
        gen.assignInt(args.get(0).getVar(), args.get(1));
        break;
      case STORE_BOOL:
        gen.assignBool(args.get(0).getVar(), args.get(1));
        break;
      case STORE_FLOAT:
        gen.assignFloat(args.get(0).getVar(), args.get(1));
        break;
      case STORE_STRING:
        gen.assignString(args.get(0).getVar(), args.get(1));
        break;
      case ADDRESS_OF:
        gen.assignReference(args.get(0).getVar(), args.get(1).getVar());
        break;
      case ARRAY_LOOKUP_FUTURE:
        gen.arrayLookupFuture(args.get(0).getVar(),
              args.get(1).getVar(), args.get(2).getVar(), false);
        break;
      case ARRAYREF_LOOKUP_FUTURE:
        gen.arrayLookupFuture(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2).getVar(), true);
        break;
      case ARRAY_LOOKUP_REF_IMM:
        gen.arrayLookupRefImm(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2), false);
        break;
      case ARRAY_LOOKUP_IMM:
        gen.arrayLookupImm(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2));
        break;
      case ARRAYREF_LOOKUP_IMM:
        gen.arrayLookupRefImm(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2), true);
        break;
      case ARRAY_INSERT_FUTURE:
        gen.arrayInsertFuture(args.get(2).getVar(),
            args.get(0).getVar(), args.get(1).getVar());
        break;
      case ARRAY_INSERT_IMM:
        gen.arrayInsertImm(args.get(2).getVar(),
            args.get(0).getVar(), args.get(1));
        break;
      case ARRAYREF_INSERT_FUTURE:
        gen.arrayRefInsertFuture(args.get(2).getVar(),
            args.get(0).getVar(), args.get(1).getVar(),
            args.get(3).getVar());
        break;
      case ARRAYREF_INSERT_IMM:
        gen.arrayRefInsertImm(args.get(2).getVar(),
            args.get(0).getVar(), args.get(1), args.get(3).getVar());
        break;
      case STRUCT_LOOKUP:
        gen.structLookup(args.get(1).getVar(), args.get(2).getStringLit(),
                                                    args.get(0).getVar());
        break;
      case STRUCTREF_LOOKUP:
        gen.structRefLookup(args.get(1).getVar(), args.get(2).getStringLit(),
                                                    args.get(0).getVar());
        break;
      case STRUCT_INSERT:
        gen.structInsert(args.get(0).getVar(), args.get(1).getStringLit(),
            args.get(2).getVar());
        break;
      case STRUCT_CLOSE:
        gen.structClose(args.get(0).getVar());
        break;
      case DEREF_INT:
        gen.dereferenceInt(args.get(0).getVar(),
                                args.get(1).getVar());
        break;
      case DEREF_BOOL:
        gen.dereferenceBool(args.get(0).getVar(),
                                args.get(1).getVar());
        break;
      case DEREF_FLOAT:
        gen.dereferenceFloat(args.get(0).getVar(),
                                args.get(1).getVar());
        break;
      case DEREF_STRING:
        gen.dereferenceString(args.get(0).getVar(),
                                args.get(1).getVar());
        break;
      case DEREF_BLOB:
        gen.dereferenceBlob(args.get(0).getVar(),
                                args.get(1).getVar());
        break;
      case DEREF_FILE:
        gen.dereferenceFile(args.get(0).getVar(),
                args.get(1).getVar());
        break;
      case LOAD_REF:
        gen.retrieveRef(args.get(0).getVar(),
                                args.get(1).getVar());
        break;
      case COPY_REF:
        gen.makeAlias(args.get(0).getVar(), args.get(1).getVar());
        break;
      case ARRAY_CREATE_NESTED_FUTURE:
        gen.arrayCreateNestedFuture(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2).getVar());
        break;
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        gen.arrayRefCreateNestedFuture(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2).getVar());
        break;
      case ARRAY_REF_CREATE_NESTED_IMM:
        gen.arrayRefCreateNestedImm(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2));
        break;
      case ARRAY_CREATE_NESTED_IMM:
        gen.arrayCreateNestedImm(args.get(0).getVar(),
            args.get(1).getVar(), args.get(2));
        break;
      case LOAD_INT:
        gen.retrieveInt(args.get(0).getVar(),
                  args.get(1).getVar());
        break;
      case LOAD_STRING:
        gen.retrieveString(args.get(0).getVar(),
            args.get(1).getVar());
        break;
      case LOAD_BOOL:
        gen.retrieveBool(args.get(0).getVar(),
            args.get(1).getVar());
        break;
      case LOAD_FLOAT:
        gen.retrieveFloat(args.get(0).getVar(),
            args.get(1).getVar());
        break;  
      case INIT_UPDATEABLE_FLOAT:
        gen.initUpdateable(args.get(0).getVar(), args.get(1));
        break;
      case LATEST_VALUE:
        gen.latestValue(args.get(0).getVar(), args.get(1).getVar());
        break;
      case UPDATE_INCR:
        gen.update(args.get(0).getVar(), UpdateMode.INCR, 
            args.get(1).getVar());
        break;
      case UPDATE_MIN:
        gen.update(args.get(0).getVar(), UpdateMode.MIN, 
            args.get(1).getVar());
        break;
      case UPDATE_SCALE:
        gen.update(args.get(0).getVar(), UpdateMode.SCALE, 
            args.get(1).getVar());
        break;
      case UPDATE_INCR_IMM:
        gen.updateImm(args.get(0).getVar(), UpdateMode.INCR, 
            args.get(1));
        break;
      case UPDATE_MIN_IMM:
        gen.updateImm(args.get(0).getVar(), UpdateMode.MIN, 
            args.get(1));
        break;
      case UPDATE_SCALE_IMM:
        gen.updateImm(args.get(0).getVar(), UpdateMode.SCALE, 
            args.get(1));
        break;
      case GET_FILENAME:
        gen.getFileName(args.get(0).getVar(), args.get(1).getVar());
      default:
        throw new STCRuntimeError("didn't expect to see op " +
                  op.toString() + " here");
      }
  
    }
  
    public static TurbineOp arrayRefLookupFuture(Variable oVar, Variable arrayRefVar,
        Variable indexVar) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_FUTURE,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayRefVar),
                                    Arg.createVar(indexVar)));
    }
  
    public static TurbineOp arrayLookupFuture(Variable oVar, Variable arrayVar,
        Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_FUTURE,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                                Arg.createVar(indexVar)));
    }
  
    public static Instruction arrayInsertFuture(Variable iVar,
        Variable arrayVar, Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_INSERT_FUTURE,
          Arrays.asList(Arg.createVar(arrayVar), Arg.createVar(indexVar),
              Arg.createVar(iVar)));
    }
  
    public static Instruction arrayRefInsertFuture(Variable iVar,
        Variable arrayVar, Variable indexVar, Variable outerArrayVar) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_FUTURE,
          Arrays.asList(Arg.createVar(arrayVar), Arg.createVar(indexVar),
              Arg.createVar(iVar), Arg.createVar(outerArrayVar)));
    }
    
    public static Instruction arrayRefLookupImm(Variable oVar,
        Variable arrayVar, Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_IMM,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                               arrayIndex));
    }
  
    public static Instruction arrayLookupRefImm(Variable oVar, Variable arrayVar,
        Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_REF_IMM,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                               arrayIndex));
    }
    
    public static Instruction arrayLookupImm(Variable oVar, Variable arrayVar,
        Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_IMM,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                               arrayIndex));
    }
  
    public static Instruction arrayInsertImm(Variable iVar,
        Variable arrayVar, Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_INSERT_IMM,
          Arrays.asList(Arg.createVar(arrayVar), arrayIndex,
                        Arg.createVar(iVar)));
    }
    
    public static Instruction arrayRefInsertImm(Variable iVar,
        Variable arrayVar, Arg arrayIndex, Variable outerArray) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_IMM,
          Arrays.asList(Arg.createVar(arrayVar), arrayIndex,
                        Arg.createVar(iVar),
                        Arg.createVar(outerArray)));
    }
  
  
    public static Instruction structLookup(Variable oVar, Variable structVar,
                                                          String fieldName) {
      return new TurbineOp(Opcode.STRUCT_LOOKUP,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(structVar),
              Arg.createStringLit(fieldName)));
    }
    
    public static Instruction structRefLookup(Variable oVar, Variable structVar,
        String fieldName) {
      return new TurbineOp(Opcode.STRUCTREF_LOOKUP,
              Arrays.asList(Arg.createVar(oVar), Arg.createVar(structVar),
              Arg.createStringLit(fieldName)));
    }
  
    public static Instruction assignInt(Variable target, Arg src) {
      return new TurbineOp(Opcode.STORE_INT,
          Arrays.asList(Arg.createVar(target), src));
    }

    public static Instruction assignBool(Variable target, Arg src) {
      return new TurbineOp(Opcode.STORE_BOOL,
          Arrays.asList(Arg.createVar(target), src));
    }
  
    public static Instruction assignFloat(Variable target, Arg src) {
      return new TurbineOp(Opcode.STORE_FLOAT,
          Arrays.asList(Arg.createVar(target), src));
    }
  
    public static Instruction assignString(Variable target, Arg src) {
      return new TurbineOp(Opcode.STORE_STRING,
          Arrays.asList(Arg.createVar(target), src));
    }
  
    public static Instruction retrieveString(Variable target, Variable source) {
      return new TurbineOp(Opcode.LOAD_STRING,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
  
    public static Instruction retrieveInt(Variable target, Variable source) {
      return new TurbineOp(Opcode.LOAD_INT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
  
    public static Instruction retrieveBool(Variable target, Variable source) {
      return new TurbineOp(Opcode.LOAD_BOOL,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
    
    public static Instruction retrieveFloat(Variable target, Variable source) {
      return new TurbineOp(Opcode.LOAD_FLOAT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
  
    public static Instruction structClose(Variable struct) {
      return new TurbineOp(Opcode.STRUCT_CLOSE,
          Arrays.asList(Arg.createVar(struct)));
    }
  
    public static Instruction structInsert(Variable structVar,
        String fieldName, Variable fieldContents) {
      return new TurbineOp(Opcode.STRUCT_INSERT,
          Arrays.asList(Arg.createVar(structVar),
                      Arg.createStringLit(fieldName),
                      Arg.createVar(fieldContents)));
    }
  
    public static Instruction addressOf(Variable target, Variable src) {
      return new TurbineOp(Opcode.ADDRESS_OF,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceInt(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREF_INT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction dereferenceBool(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREF_BOOL,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceFloat(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREF_FLOAT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceString(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREF_STRING,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceBlob(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREF_BLOB,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction dereferenceFile(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREF_FILE,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction retrieveRef(Variable target, Variable src) {
      return new TurbineOp(Opcode.LOAD_REF,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction copyRef(Variable dst, Variable src) {
      return new TurbineOp(Opcode.COPY_REF,
          Arrays.asList(Arg.createVar(dst), Arg.createVar(src)));
          
    }
  
    public static Instruction arrayCreateNestedComputed(Variable arrayResult,
        Variable arrayVar, Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_FUTURE,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), Arg.createVar(indexVar)));
    }
  
    public static Instruction arrayCreateNestedImm(Variable arrayResult,
        Variable arrayVar, Arg arrIx) {
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_IMM,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), arrIx));
    }
  
    public static Instruction arrayRefCreateNestedComputed(Variable arrayResult,
        Variable arrayVar, Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_REF_CREATE_NESTED_FUTURE,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), Arg.createVar(indexVar)));
    }
  
  
    public static Instruction arrayRefCreateNestedImmIx(Variable arrayResult,
        Variable arrayVar, Arg arrIx) {
      return new TurbineOp(Opcode.ARRAY_REF_CREATE_NESTED_IMM,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), arrIx));
    }
  
  
    public static Instruction initUpdateableFloat(Variable updateable, 
                                                              Arg val) {
      return new TurbineOp(Opcode.INIT_UPDATEABLE_FLOAT, 
          Arrays.asList(Arg.createVar(updateable), val));
      
    }

    public static Instruction latestValue(Variable result, 
                              Variable updateable) {
      return new TurbineOp(Opcode.LATEST_VALUE, Arrays.asList(
          Arg.createVar(result), Arg.createVar(updateable)));
    }
    
    public static Instruction update(Variable updateable,
        UpdateMode updateMode, Variable val) {
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
        throw new STCRuntimeError("Unknown UpdateMode"
            + updateMode);
      }
      return new TurbineOp(op, Arrays.asList(Arg.createVar(updateable), 
                                             Arg.createVar(val)));
    }

    public static Instruction updateImm(Variable updateable,
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
      return new TurbineOp(op, Arrays.asList(Arg.createVar(updateable), 
                                                                   val));
    }
    
    public static Instruction getFileName(Variable filename, Variable file) {
      return new TurbineOp(Opcode.GET_FILENAME, 
              Arrays.asList(Arg.createVar(filename), Arg.createVar(file)));
    }
 

    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceOpargsInList(renames, args);
    }
  
    @Override
    public void renameInputs(Map<String, Arg> renames) {
      
      int firstInputArg;
      if (op == Opcode.ARRAY_CREATE_NESTED_FUTURE
       || op == Opcode.ARRAY_CREATE_NESTED_IMM
       || op == Opcode.ARRAY_REF_CREATE_NESTED_FUTURE
       || op == Opcode.ARRAY_REF_CREATE_NESTED_IMM
       || op == Opcode.ARRAY_INSERT_FUTURE
       || op == Opcode.ARRAY_INSERT_IMM
       || op == Opcode.ARRAYREF_INSERT_FUTURE
       || op == Opcode.ARRAYREF_INSERT_IMM) {
         // The arrays mutated by these instructions are also basically
         // inputs
         firstInputArg = 0;
       } else {
         firstInputArg = numOutputArgs();
       }
       ICUtil.replaceOpargsInList(renames, args.subList(firstInputArg, 
           args.size()));
    }

    private int numOutputArgs() {
      switch (op) {
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
      case UPDATE_INCR_IMM:
      case UPDATE_MIN_IMM:
      case UPDATE_SCALE_IMM:
      case INIT_UPDATEABLE_FLOAT:
      case LATEST_VALUE:
        return 1;
      
      case ARRAY_INSERT_FUTURE:
      case ARRAY_INSERT_IMM:
      case STRUCT_CLOSE:
      case STRUCT_INSERT:
        // We view array as output
        return 1;
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
        return 0;
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_IMM:
      case ARRAY_LOOKUP_REF_IMM:
      case ARRAY_LOOKUP_IMM:
      case ARRAY_LOOKUP_FUTURE:
      case STORE_INT:
      case STORE_BOOL:
      case STORE_FLOAT:
      case STORE_STRING:
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_REF_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_REF_CREATE_NESTED_IMM:
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
      case STRUCT_LOOKUP:
      case STRUCTREF_LOOKUP:
      case ADDRESS_OF:
      case LOAD_REF:
      case COPY_REF:
      case GET_FILENAME:
          return 1;
      default:
        throw new STCRuntimeError("Need to add opcode " + op.toString()
            + " to numOutputArgs");
      }
    }
  
    @Override
    public boolean hasSideEffects() {
      switch (op) {
      /* The direct container write functions only mutate their output 
       * argument */
      case ARRAY_INSERT_FUTURE:
      case ARRAY_INSERT_IMM:
      case STRUCT_CLOSE:
      case STRUCT_INSERT:
      case ARRAY_DECR_WRITERS:
        return this.writesAliasVar();
        
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
        return true;
  
      
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
      case DEREF_INT:
      case DEREF_BOOL:
      case DEREF_FLOAT:
      case DEREF_STRING:
      case DEREF_BLOB:
      case LOAD_INT:
      case LOAD_BOOL:
      case LOAD_FLOAT:
      case LOAD_STRING:
      case ARRAY_LOOKUP_REF_IMM:
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_IMM:
      case GET_FILENAME:
          return this.writesAliasVar();

      case DEREF_FILE:
        return this.writesAliasVar() ||
               this.args.get(0).getVar().isMapped();
          
      case STRUCT_LOOKUP:
      case LOAD_REF:
      case ADDRESS_OF:
      case COPY_REF:
      case STRUCTREF_LOOKUP:
      case ARRAY_LOOKUP_IMM:
      case LATEST_VALUE:
          // Always has alias as output because the instructions initialises
          // the aliases
          return false;
          
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_REF_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_REF_CREATE_NESTED_IMM:
          /* It might seem like these nested creation primitives have a 
           * side-effect, but for optimisation purposes they can be treated as 
           * side-effect free, as the side-effect is only relevant if the array 
           * created is subsequently used in a store operation
           */ 
        return false;
      default:
        throw new STCRuntimeError("Need to add opcode " + op.toString()
            + " to hasSideEffects");
      }
    }
  
    @Override
    public List<Variable> getOutputs() {
      List<Arg> l = args.subList(0, numOutputArgs());
      ArrayList<Variable> res = new ArrayList<Variable>(numOutputArgs());
      for (Arg a: l) {
        assert(a.getType() == ArgType.VAR);
        res.add(a.getVar());
      }
      return res;
    }
  
    @Override
    public List<Arg> getInputs() {
      return args.subList(numOutputArgs(), args.size());
    }
  
    @Override
    public Map<String, Arg> constantFold(String fnName,
                        Map<String, Arg> knownConstants) {
      switch (op) {
      case STORE_INT:
      case STORE_STRING:
      case STORE_BOOL:
      case STORE_FLOAT:
      case LOAD_BOOL:
      case LOAD_FLOAT:
      case LOAD_INT:
      case LOAD_STRING:
        // The input arg could be a var or a literal constant
        if (args.get(1).getType() == ArgType.VAR) {
          Arg val = knownConstants.get(args.get(1).getVar().getName());
          if (val != null) {
            HashMap<String, Arg> r = new HashMap<String, Arg>();
            r.put(args.get(0).getVar().getName(), val);
            return r;
          }
        }
        break;
      default:
        // do nothing
      }
  
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      switch (op) {
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
        Variable index = args.get(2).getVar();
        if (knownConstants.containsKey(index.getName())) {
          Arg cIndex = knownConstants.get(index.getName());
          if (op == Opcode.ARRAY_LOOKUP_FUTURE) {
            return arrayLookupRefImm(args.get(0).getVar(),
                args.get(1).getVar(), cIndex);
          } else {
            return arrayRefLookupImm(args.get(0).getVar(),
                args.get(1).getVar(), cIndex);
          }
        }
        break;
      case ARRAYREF_INSERT_FUTURE:
      case ARRAY_INSERT_FUTURE:
        Variable sIndex = args.get(1).getVar();
        if (knownConstants.containsKey(sIndex.getName())) {
          Arg cIndex = knownConstants.get(sIndex.getName());
          if (op == Opcode.ARRAY_INSERT_FUTURE) {
            return arrayInsertImm(args.get(2).getVar(),
                      args.get(0).getVar(), cIndex);
          } else {
            return arrayRefInsertImm(args.get(2).getVar(),
                args.get(0).getVar(), cIndex, args.get(3).getVar());
          }
        }
        break;
      default:
        // fall through
      }
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars,
                                                boolean assumeAllInputsClosed) {
      // Try to take advantage of closed variables 
      switch (op) {
      case ARRAY_LOOKUP_REF_IMM:
        // If array is closed or this index already inserted,
        // don't need to block on array.  
        // NOTE: could try to reduce other forms to this in one step,
        //      but its probably just easier to do it in multiple steps
        //      on subsequent passes
        Variable arr = args.get(1).getVar();
        if (closedVars.contains(arr.getName())) {
          // Don't need to retrieve any value, but just use this protocol
          return new MakeImmRequest(null, new ArrayList<Variable>());
        }
        break;
        
      case ARRAY_LOOKUP_FUTURE:
        Variable index = args.get(2).getVar();
        if (closedVars.contains(index.getName())) {
          return new MakeImmRequest(null, Arrays.asList(index));
        }
        break;
      case ARRAYREF_LOOKUP_FUTURE:
        // We will take either the index or the dereferenced array
        List<Variable> req = mkImmVarList(closedVars, 
                  args.get(1).getVar(), args.get(2).getVar());
        if (req.size() > 0) {
          return new MakeImmRequest(null, req);
        }
        break;
      case ARRAYREF_LOOKUP_IMM:
        // Could skip using reference
        Variable arrRef2 = args.get(1).getVar();
        if (closedVars.contains(arrRef2.getName())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef2));
        }
        break;
      case ARRAY_INSERT_FUTURE:
        Variable sIndex = args.get(1).getVar();
        if (closedVars.contains(sIndex.getName())) {
          return new MakeImmRequest(null, Arrays.asList(sIndex));
        }
        break;
      case ARRAYREF_INSERT_IMM:
        Variable arrRef3 = args.get(0).getVar();
        if (closedVars.contains(arrRef3.getName())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef3));
        }
        break;
      case ARRAYREF_INSERT_FUTURE:
        // We will take either the index or the dereferenced array
        List<Variable> req2 = mkImmVarList(closedVars,
                    args.get(0).getVar(), args.get(1).getVar());
        if (req2.size() > 0) {
          return new MakeImmRequest(null, req2);
        }
        break;
      case ARRAY_CREATE_NESTED_FUTURE:
        // Try to get immediate index
        Variable index2 = args.get(2).getVar();
        if (closedVars.contains(index2.getName())) {
          return new MakeImmRequest(null, Arrays.asList(index2));
        }
        break;
      case ARRAY_REF_CREATE_NESTED_IMM:
        Variable arrRef5 = args.get(1).getVar();
        if (closedVars.contains(arrRef5.getName())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef5));
        }
        break;
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        List<Variable> req5 = mkImmVarList(closedVars, 
            args.get(1).getVar(), args.get(2).getVar());
        if (req5.size() > 0) {
          return new MakeImmRequest(null, req5);
        }
        break;
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
        return new MakeImmRequest(null, Arrays.asList(
                  args.get(1).getVar()));
      default:
        // fall through
      }
      //TODO: add in create nested instructions
      return null;
    }
    
    private List<Variable> mkImmVarList(Set<String> closedVars, 
                                              Variable... args) {
      ArrayList<Variable> req = new ArrayList<Variable>(args.length);
      for (Variable v: args) {
        if (closedVars.contains(v.getName())) {
          req.add(v);
        }
      }
      return req;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> out, List<Arg> values) {
      switch (op) {
      case ARRAY_LOOKUP_REF_IMM:
        assert(values.size() == 0);
        // OUtput switched from ref to value
        Variable refOut = args.get(0).getVar();
        Variable valOut = Variable.createDerefTmp(refOut, 
                                      VariableStorage.ALIAS);
        Instruction newI = arrayLookupImm(valOut,
            args.get(1).getVar(), args.get(2));
        return new MakeImmChange(valOut, refOut, newI);
      case ARRAY_LOOKUP_FUTURE:
        assert(values.size() == 1);
        return new MakeImmChange(
                arrayLookupRefImm(args.get(0).getVar(), 
                args.get(1).getVar(), values.get(0)));
      case ARRAYREF_LOOKUP_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        // Could be either array ref, index, or both
        if (values.size() == 2) {
          return new MakeImmChange(arrayLookupRefImm(
              args.get(0).getVar(), values.get(0).getVar(), 
              values.get(1)));
        } else { 
          Arg v1 = values.get(0);
          if (v1.isImmediateInt()) {
            // replace index
            return new MakeImmChange(
                    arrayRefLookupImm(args.get(0).getVar(), 
                    args.get(1).getVar(), v1));
          } else {
            // replace the array ref
            return new MakeImmChange(
                    arrayLookupFuture(args.get(0).getVar(), 
                            v1.getVar(), args.get(2).getVar()));
          }
        }
      case ARRAYREF_LOOKUP_IMM:
        assert(values.size() == 1);
        // Switch from ref to plain array
        return new MakeImmChange(arrayLookupRefImm(
                args.get(0).getVar(), values.get(0).getVar(),
                                                         args.get(2)));
      case ARRAY_INSERT_FUTURE:
        assert(values.size() == 1);
        return new MakeImmChange(
                arrayInsertImm(args.get(2).getVar(), 
                args.get(0).getVar(), values.get(0)));
      case ARRAYREF_INSERT_IMM:
        assert(values.size() == 1);
        // Switch from ref to plain array
        return new MakeImmChange(arrayInsertImm(
            args.get(2).getVar(), values.get(0).getVar(),
                                                      args.get(1)));
      case ARRAYREF_INSERT_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        // Could be either array ref, index, or both
        if (values.size() == 2) {
          return new MakeImmChange(arrayInsertImm(
              args.get(2).getVar(),
              values.get(0).getVar(), values.get(1)));
        } else { 
          Arg v1 = values.get(0);
          if (v1.isImmediateInt()) {
            // replace index
            return new MakeImmChange(
                    arrayRefInsertImm(args.get(2).getVar(), 
                    args.get(0).getVar(), v1, args.get(3).getVar()));
          } else {
            // replace the array ref
            return new MakeImmChange(
                    arrayInsertFuture(args.get(2).getVar(), 
                            v1.getVar(), args.get(1).getVar()));
          }
        }
      case ARRAY_CREATE_NESTED_FUTURE:
        assert(values.size() == 1);
        // Output type of instruction changed from ref to direct
        // array handle
        Variable oldOut = args.get(0).getVar();
        assert(Types.isArrayRef(oldOut.getType()));
        Variable newOut = Variable.createDerefTmp(oldOut, 
                                                VariableStorage.ALIAS);
        return new MakeImmChange(newOut, oldOut,
            arrayCreateNestedImm(newOut,
                            args.get(1).getVar(), values.get(0)));
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        if (values.size() == 2) {
          Variable oldOut2 = args.get(0).getVar();
          assert(Types.isArrayRef(oldOut2.getType()));
          Variable newOut2 = Variable.createDerefTmp(oldOut2,
                                          VariableStorage.ALIAS);
          return new MakeImmChange(newOut2, oldOut2,
              arrayCreateNestedImm(newOut2, 
                  values.get(0).getVar(), values.get(1)));
        } else {
          // We weren't able to switch to the version returning a plain
          // array
          Arg newA = values.get(0);
          if (newA.isImmediateInt()) {
            return new MakeImmChange(
                arrayRefCreateNestedImmIx(args.get(0).getVar(),
                    args.get(1).getVar(), newA));
          } else {
            // Replacing array ref with array
            return new MakeImmChange(
                arrayRefCreateNestedImmIx(args.get(0).getVar(),
                    newA.getVar(), args.get(2)));
          }
        }
      case ARRAY_REF_CREATE_NESTED_IMM:
        assert(values.size() == 1);
        Variable oldOut3 = args.get(0).getVar();
        assert(Types.isArrayRef(oldOut3.getType()));
        Variable newOut3 = Variable.createDerefTmp(oldOut3,
                                                VariableStorage.ALIAS);
        return new MakeImmChange(newOut3, oldOut3,
            arrayCreateNestedImm(newOut3,
                            values.get(0).getVar(), args.get(2)));
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
            this.args.get(0).getVar(), mode, values.get(0)));
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
    public List<Variable> getBlockingInputs() {
      ArrayList<Variable> blocksOn = new ArrayList<Variable>();
      for (Arg oa: getInputs()) {
        if (oa.type == ArgType.VAR) {
          Variable v = oa.getVar();
          SwiftType t = v.getType();
          if (Types.isScalarFuture(t)
              || Types.isReference(t)) {
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
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Arg> existing) {
      Arg arr = null;
      Arg ix = null;
      Arg contents = null;
      ComputedValue cv = null;
      switch(op) {
        case LOAD_BOOL:
        case LOAD_FLOAT:
        case LOAD_INT:
        case LOAD_REF:
        case LOAD_STRING: {
          // retrieve* is invertible
          Arg src = args.get(1);
          Arg val = args.get(0);
          if (Types.isScalarUpdateable(src.getVar().getType())) {
            return null;
          }
          ComputedValue retrieve = vanillaComputedValue(true);
          Opcode cvop = assignOpcode(src.getSwiftType());
          if (cvop == null) {
            throw new STCRuntimeError("Need assign op for "
                + src.getVar());
          }
          ComputedValue assign = new ComputedValue(cvop,
                    "", Arrays.asList(val), src, true);
          
          Opcode derefOp = derefOpCode(src.getSwiftType());
          
          if (derefOp == null) {
            return Arrays.asList(retrieve, assign);
          } else {
            return Arrays.asList(retrieve, assign, 
                new ComputedValue(derefOp, "", Arrays.asList(src),
                    val, false));
          }
        }
        case ADDRESS_OF:
        case STORE_BOOL:
        case STORE_FLOAT:
        case STORE_INT:
        case STORE_STRING: {
          // add assign so we can avoid recreating future 
          // (true b/c this instruction closes val immediately)
          ComputedValue assign = vanillaComputedValue(true);
          // add retrieve so we can avoid retrieving later
          Arg dst = args.get(0);
          Arg src = args.get(1);
          ComputedValue retrieve = new ComputedValue(
                    retrieveOpcode(dst.getSwiftType()),
                    "", Arrays.asList(dst), src, false);
          if (op == Opcode.ADDRESS_OF) {
            Opcode derefOp = derefOpCode(dst.getSwiftType());
            if (derefOp != null) {
              ComputedValue deref = 
                   new ComputedValue(derefOp, "", Arrays.asList(dst),
                                                 src, false);
              return Arrays.asList(retrieve, assign, deref);
            }
          } 
          return Arrays.asList(retrieve, assign);
        }
        case GET_FILENAME:
          // TODO: standardise ComputedValue with related functions
        case DEREF_BLOB:
        case DEREF_BOOL:
        case DEREF_FLOAT:
        case DEREF_INT:
        case DEREF_STRING: {
          return Arrays.asList(vanillaComputedValue(false));
        }
        case DEREF_FILE: {
          if (args.get(0).getVar().isMapped()) {
            // Can't use interchangably
            return null;
          } else {
            return Arrays.asList(vanillaComputedValue(false));
          }
        }
        
        case STRUCT_INSERT: {
          // Lookup
          ComputedValue lookup = new ComputedValue(Opcode.STRUCT_LOOKUP,
              "", Arrays.asList(args.get(0), args.get(1)), args.get(2), false,
              EquivalenceType.REFERENCE);
          return Arrays.asList(lookup); 
        }
        case STRUCT_LOOKUP: {
          // don't know if its closed
          ComputedValue lookup = new ComputedValue(Opcode.STRUCT_LOOKUP,
              "", Arrays.asList(args.get(1), args.get(2)), args.get(0), false,
              EquivalenceType.REFERENCE);
          return Arrays.asList(lookup); 
        }
        case ARRAYREF_INSERT_FUTURE:
        case ARRAYREF_INSERT_IMM:
        case ARRAY_INSERT_IMM:
        case ARRAY_INSERT_FUTURE:{
          // STORE <out array> <in index> <in var>
          // OR
          // STORE <out array> <in index> <in var> <in outer array>
          arr = args.get(0); 
          ix = args.get(1);
          contents = args.get(2);
          return Arrays.asList(makeArrayComputedValue(arr, ix, contents));
        }
        case ARRAY_LOOKUP_IMM:
        case ARRAY_LOOKUP_REF_IMM:
        case ARRAY_LOOKUP_FUTURE:
        case ARRAYREF_LOOKUP_FUTURE:
        case ARRAYREF_LOOKUP_IMM: {
          // LOAD <out var> <in array> <in index>
          arr = args.get(1);
          ix = args.get(2);
          contents = args.get(0);
          Variable lookupRes = contents.getVar();
          
          cv = makeArrayComputedValue(arr, ix, contents);
  
          if (op == Opcode.ARRAY_LOOKUP_IMM) {
            assert(lookupRes.getType().equals(
                Types.getArrayMemberType(arr.getSwiftType())));
            // This just retrieves the item immediately
            return Arrays.asList(cv);
          } else {
            assert (Types.isReferenceTo(lookupRes.getType(), 
                Types.getArrayMemberType(arr.getSwiftType())));
            Arg prev = existing.get(new ComputedValue(Opcode.FAKE,
                ComputedValue.ARRAY_CONTENTS, Arrays.asList(arr, ix)));
            if (prev != null) {
              /* All these array loads give back a reference, but if a value
               * was previously inserted at this index, then we can 
               * short-circuit this as we know what is in the reference */
              ComputedValue retrieveCV = new ComputedValue(retrieveOpcode(
                  lookupRes.getType()), "", contents, prev, false);
              Opcode derefOp = derefOpCode(lookupRes.getType());
              if (derefOp == null) {
                return Arrays.asList(retrieveCV, cv);
              } else {
                ComputedValue derefCV = new ComputedValue(derefOp,
                    "", contents, prev, false);
                return Arrays.asList(retrieveCV, cv, derefCV);
              }
            } else {
              return Arrays.asList(cv);
            }
          }
        }
        case ARRAY_CREATE_NESTED_FUTURE:
        case ARRAY_CREATE_NESTED_IMM:
        case ARRAY_REF_CREATE_NESTED_FUTURE:
        case ARRAY_REF_CREATE_NESTED_IMM: {
          // CREATE_NESTED <out inner array> <in array> <in index>
          contents = args.get(0);
          Variable nestedArr = contents.getVar();
          arr = args.get(1);
          ix = args.get(2);
          cv = makeArrayComputedValue(arr, ix, contents);
          if (op == Opcode.ARRAY_CREATE_NESTED_IMM) {
            // No references involved, the instruction returns the nested
            // array directly
            return Arrays.asList(cv);
          } else {
            Arg prev = existing.get(new ComputedValue(Opcode.FAKE,
                ComputedValue.ARRAY_CONTENTS, Arrays.asList(arr, ix)));
            assert (Types.isReferenceTo(nestedArr.getType(), 
                        Types.getArrayMemberType(arr.getSwiftType())));
            if (prev != null) {
              // See if we know the value of this reference already
              ComputedValue derefCV = new ComputedValue(retrieveOpcode(
                  nestedArr.getType()), "", Arrays.asList(contents),
                                                        prev, false);
              return Arrays.asList(derefCV, cv);
            } else {
              return Arrays.asList(cv);
            }
          }
        }
        default:
          return null;
      }
    }

    /**
     * Create the "standard" computed value
     * assume 1 ouput arg
     * @return
     */
    private ComputedValue vanillaComputedValue(boolean closed) {
      assert(numOutputArgs() == 1);
      return new ComputedValue(op, "", 
          this.args.subList(1, this.args.size()),
          this.args.get(0), closed);
    }

    private ComputedValue makeArrayComputedValue(Arg arr, Arg ix, Arg contents) {
      ComputedValue cv;
      if (isMemberReference(contents.getVar(),
          arr.getVar())) {
        cv = new ComputedValue(Opcode.FAKE, ComputedValue.REF_TO_ARRAY_CONTENTS, 
            Arrays.asList(arr, ix), contents, false);
      } else {
        cv = new ComputedValue(Opcode.FAKE, ComputedValue.ARRAY_CONTENTS, 
            Arrays.asList(arr, ix), contents, false);
      }
      return cv;
    }

    /**
     * @param member
     * @param arr
     * @return true if member is a reference to the member type of arr,
     *          false if it is the same as member type of arr
     * @throws STCRuntimeError if member can't be a member or ref to 
     *                                      member of array
     */
    private boolean isMemberReference(Variable member, Variable arr) 
            throws STCRuntimeError{
      SwiftType memberType = Types.getArrayMemberType(arr.getType());
      if (memberType.equals(member.getType())) {
        return false;
      } else if (Types.isReferenceTo(member.getType(), memberType)) {
        return true;
      }
      throw new STCRuntimeError("Inconsistent types in IC instruction:"
          + this.toString() + " array of type " + arr.getType() 
          + " with member of type " + member.getType());
    }

    @Override
    public Instruction clone() {
      return new TurbineOp(op, Arg.cloneList(args));
    }
  }
  
  public static abstract class CommonFunctionCall extends Instruction {
    protected final String functionName;
    
    public CommonFunctionCall(Opcode op, String functionName) {
      super(op);
      this.functionName = functionName;
    }
    
    private boolean isCopyFunction() {
      if (FunctionSemantics.isCopyFunction(functionName)) {
        return true;
      } else if (FunctionSemantics.isMinMaxFunction(functionName)
              && getInput(0).equals(getInput(1))) {
        return true;
      }
      return false;
    }
    
    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Arg> existing) {
      // TODO: make order of args invariant where possible
      if (FunctionSemantics.isPure(functionName)) {
        if (!this.writesMappedVar() && isCopyFunction()) {
          // Handle copy as a special case
          return Collections.singletonList(
                ComputedValue.makeCopyCV(getOutput(0),
                                         getInput(0)));
        } else if (getOutputs().size() == 1) {
          // TODO: does it matter if this writes a mapped variable? 
          
          boolean outputClosed = false; // safe assumption
          String canonicalFunctionName = this.functionName;
          List<Arg> in = new ArrayList<Arg>(getInputs());
          if (FunctionSemantics.isCommutative(this.functionName)) {
            // put in canonical order
            Collections.sort(in);
          }
          
          List<ComputedValue> res = new ArrayList<ComputedValue>();
          res.add(new ComputedValue(this.op, 
              canonicalFunctionName, in, 
              Arg.createVar(getOutput(0)), outputClosed));
          if (op == Opcode.CALL_BUILTIN && 
                      this.functionName.equals(Builtins.INPUT_FILE)) {
            // Inferring filename is problematic
            res.add(new ComputedValue(Opcode.CALL_BUILTIN, Builtins.FILENAME,
                      Arrays.asList(Arg.createVar(getOutput(0))),
                      getInput(0), false));
          }
          return res;
        } else {
          // TODO: Not sure to do with multiple outputs
        }
      }
      return null;
    }
  }
  
  public static class FunctionCall extends CommonFunctionCall {
    private final List<Variable> outputs;
    private final List<Variable> inputs;
    private final List<Boolean> closedInputs; // which inputs are closed
    private Arg priority;
  
    private FunctionCall(Opcode op, String functionName,
        List<Variable> inputs, List<Variable> outputs, Arg priority) {
      super(op, functionName);
      if (op != Opcode.CALL_BUILTIN && op != Opcode.CALL_CONTROL &&
          op != Opcode.CALL_SYNC) {
        throw new STCRuntimeError("Tried to create function call"
            + " instruction with invalid opcode");
      }
      this.priority = priority;
      this.outputs = new ArrayList<Variable>();
      this.outputs.addAll(outputs);
      this.inputs = new ArrayList<Variable>();
      this.inputs.addAll(inputs);
      this.closedInputs = new ArrayList<Boolean>(inputs.size());
      for (int i = 0; i < inputs.size(); i++) {
        this.closedInputs.add(false);
      }
      
      for(Variable v: outputs) {
        assert(v != null);
      }
      
      for(Variable v: inputs) {
        assert(v != null);
      }
    }
  
    public static FunctionCall createFunctionCall(
        String functionName, List<Variable> inputs, List<Variable> outputs,
        TaskMode mode, Arg priority) {
      Opcode op;
      if (mode == TaskMode.SYNC) {
        op = Opcode.CALL_SYNC;
      } else if (mode == TaskMode.CONTROL) {
        op = Opcode.CALL_CONTROL;
      } else {
        throw new STCRuntimeError("Task mode " + mode + " not yet supported");
      }
      return new FunctionCall(op, functionName,
          inputs, outputs, priority);
    }
  
    public static FunctionCall createBuiltinCall(
        String functionName, List<Variable> inputs, List<Variable> outputs,
        Arg priority) {
      return new FunctionCall(Opcode.CALL_BUILTIN, functionName,
          inputs, outputs, priority);
    }
  
    @Override
    public String toString() {
      String result = op.toString().toLowerCase() + " " + functionName;
      result += " [";
      for (Variable v: outputs) {
        result += " " + v.getName();
      }
      result += " ] [";
      for (Variable v: inputs) {
        result += " " + v.getName();
      }
      result += " ]";
      if (priority != null) {
        result += " priority=" + priority.toString(); 
      }
      return result;
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      switch(this.op) {
      case CALL_BUILTIN:
        gen.builtinFunctionCall(functionName, inputs, outputs, priority);
        break;
      case CALL_SYNC:
      case CALL_CONTROL:
        TaskMode mode;
        if (op == Opcode.CALL_CONTROL) {
          mode = TaskMode.CONTROL;
        } else if (op == Opcode.CALL_SYNC) {
          mode = TaskMode.SYNC;
        } else {
          throw new STCRuntimeError("Unexpected op " + op);
        }
        List<Boolean> blocking = info.getBlockingInputVector(functionName);
        assert(blocking != null && blocking.size() == inputs.size());
        List<Boolean> needToBlock = new ArrayList<Boolean>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
          needToBlock.add(blocking.get(i) && (!this.closedInputs.get(i)));
        }
        gen.functionCall(functionName, inputs, outputs, needToBlock,
                                            mode, priority);
        break;
      default:
        throw new STCRuntimeError("Huh?");
      }
    }
  
    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, outputs, false);
      ICUtil.replaceVarsInList(renames, inputs, false);
      priority = ICUtil.replaceOparg(renames, priority, true);
    }
  
    public String getFunctionName() {
      return this.functionName;
    }
    @Override
    public void renameInputs(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, inputs, false);
      priority = ICUtil.replaceOparg(renames, priority, true);
    }
  
    @Override
    public List<Arg> getInputs() {
      List<Arg> inputVars = Arg.fromVarList(inputs);
      if (priority != null) {
        inputVars.add(priority);
      }
      return inputVars;
    }
  
    @Override
    public List<Variable> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    @Override
    public boolean hasSideEffects() {
      return (!FunctionSemantics.isPure(functionName)) ||
            this.writesAliasVar() || this.writesMappedVar();
    }
  
    @Override
    public Map<String, Arg> constantFold(String enclosingFnName,
                                  Map<String, Arg> knownConstants) {
      return null;
    }
    
    @Override
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      return null;
    }
    
    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars
                                      , boolean assumeAllInputsClosed) {
      // See which arguments are closed
      boolean allClosed = true;
      if (!assumeAllInputsClosed) {
        for (int i = 0; i < this.inputs.size(); i++) {
          Variable in = this.inputs.get(i);
          if (closedVars.contains(in.getName())) {
            this.closedInputs.set(i, true);
          } else {
            allClosed = false;
          }
        }
      }
      if (allClosed && (FunctionSemantics.hasOpEquiv(this.functionName)
                || FunctionSemantics.hasInlineVersion(this.functionName))) {
          // All args are closed!
          return new MakeImmRequest(
              Collections.unmodifiableList(this.outputs),
              Collections.unmodifiableList(this.inputs));

      }
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> outVars, 
                                        List<Arg> values) {
      if (FunctionSemantics.hasOpEquiv(functionName)) {
        BuiltinOpcode newOp = FunctionSemantics.getOpEquiv(functionName);
        assert(newOp != null);
        assert(values.size() == inputs.size());
        
        if (outputs.size() == 1) {
          assert(Types.derefResultType(outputs.get(0).getType()).equals(
              outVars.get(0).getType()));
          return new MakeImmChange(
              Builtin.createLocal(newOp, outVars.get(0), values));
        } else {
          assert(outputs.size() == 0);
          return new MakeImmChange(
              Builtin.createLocal(newOp, null, values));
        }
      } else {
        assert(FunctionSemantics.hasInlineVersion(functionName));
        for (int i = 0; i < outputs.size(); i++) {
          Variable out = outputs.get(i);
          assert(Types.derefResultType(out.getType()).equals(
                 outVars.get(i).getType()));
        }
        return new MakeImmChange(
                new LocalFunctionCall(functionName, values, outVars));
      }
    }

    @Override
    public List<Variable> getBlockingInputs() {
      List<Variable> blocksOn = new ArrayList<Variable>();
      if (op == Opcode.CALL_BUILTIN) {
        for (Variable v: inputs) {
          if (Types.isScalarFuture(v.getType())
              || Types.isReference(v.getType())) {
            // TODO: this is a conservative idea of which ones
            // are set
            blocksOn.add(v);
          }
        }
      } else if (op == Opcode.CALL_SYNC) {
        // Can't block because we need to enter the function immediately
        return null;
      } else if (op == Opcode.CALL_CONTROL ) {
        //TODO: should see which arguments are blocking
        return null;
      }
      return blocksOn;
    }

    @Override
    public Instruction clone() {
      // Variables are immutable so just need to clone lists
      return new FunctionCall(op, functionName, 
          new ArrayList<Variable>(inputs), new ArrayList<Variable>(outputs),
          priority);
    }
  }
  
  public static class LocalFunctionCall extends CommonFunctionCall {
    private final List<Variable> outputs;
    private final List<Arg> inputs;
  
    public LocalFunctionCall(String functionName,
        List<Arg> inputs, List<Variable> outputs) {
      super(Opcode.CALL_BUILTIN_LOCAL, functionName);
      this.outputs = new ArrayList<Variable>();
      this.outputs.addAll(outputs);
      this.inputs = new ArrayList<Arg>();
      this.inputs.addAll(inputs);
      for(Variable v: outputs) {
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
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, outputs, false);
      ICUtil.replaceOpargsInList(renames, inputs);
    }
  
    public String getFunctionName() {
      return this.functionName;
    }
    @Override
    public void renameInputs(Map<String, Arg> renames) {
      ICUtil.replaceOpargsInList(renames, inputs);
    }
  
    @Override
    public List<Arg> getInputs() {
      return Collections.unmodifiableList(inputs);
    }
  
    @Override
    public List<Variable> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    @Override
    public boolean hasSideEffects() {
      return (!FunctionSemantics.isPure(functionName)) ||
            this.writesAliasVar() || this.writesMappedVar();
    }
  
    @Override
    public Map<String, Arg> constantFold(String enclosingFnName,
                                  Map<String, Arg> knownConstants) {
      // Replace any variables for which constant values are known
      ICUtil.replaceOpargsInList(knownConstants, inputs);
      return null;
    }
    
    @Override
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      return null;
    }
    
    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars
                                      , boolean assumeAllInputsClosed) {
      return null; // already immediate
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> outVars, 
                                        List<Arg> values) {
      throw new STCRuntimeError("Invalid method call");
    }

    @Override
    public List<Variable> getBlockingInputs() {
      // doesn't take futures as args
      return null;
    }
    
    @Override
    public Instruction clone() {
      // Variables are immutable so just need to clone lists
      return new LocalFunctionCall(functionName, 
          new ArrayList<Arg>(inputs), new ArrayList<Variable>(outputs));
    }
  }
  
  public static class RunExternal extends Instruction {
    private final String cmd;
    private final ArrayList<Variable> outputs;
    private final ArrayList<Arg> inputs;
    private final List<ExtArgType> order;
    private final boolean hasSideEffects;
    private final boolean deterministic;
    
    public RunExternal(String cmd, List<Variable> outputs, List<Arg> inputs,
               List<ExtArgType> order, boolean hasSideEffects,
               boolean deterministic) {
      super(Opcode.RUN_EXTERNAL);
      this.cmd = cmd;
      this.outputs = new ArrayList<Variable>(outputs);
      this.inputs = new ArrayList<Arg>(inputs);
      this.order = new ArrayList<ExtArgType>(order);
      this.deterministic = deterministic;
      this.hasSideEffects = hasSideEffects;
    }

    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceOpargsInList(renames, inputs);
      ICUtil.replaceVarsInList(renames, outputs, false);
    }

    @Override
    public void renameInputs(Map<String, Arg> renames) {
      ICUtil.replaceOpargsInList(renames, inputs);
    }

    @Override
    public String toString() {
      return formatFunctionCall(op, cmd, outputs, inputs);
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.runExternal(cmd, inputs, outputs, order, hasSideEffects,
                      deterministic);
    }

    @Override
    public List<Arg> getInputs() {
      return Collections.unmodifiableList(inputs);
    }

    @Override
    public List<Variable> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    @Override
    public boolean hasSideEffects() {
      return hasSideEffects;
    }

    @Override
    public Map<String, Arg> constantFold(String fnName,
        Map<String, Arg> knownConstants) {
      // Replace variables for which values are known
      ICUtil.replaceOpargsInList(knownConstants, inputs);
      return null;
    }

    @Override
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars,
        boolean assumeAllClosed) {
      // Don't support reducing this
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> outVals,
        List<Arg> inValues) {
      return null;
    }

    @Override
    public List<Variable> getBlockingInputs() {
      // This instruction runs immediately: we won't block on any inputs
      return null;
    }

    @Override
    public List<ComputedValue> getComputedValues(
        Map<ComputedValue, Arg> existing) {
      if (deterministic) {
        ArrayList<ComputedValue> cvs = new ArrayList<ComputedValue>(
                                                        outputs.size());
        for (int i = 0; i < outputs.size(); i++) {
          // Unique key for cv includes number of output
          // Output file should be closed after external program executes
          ComputedValue cv = new ComputedValue(op, cmd + "!!" + i,
                     inputs, Arg.createVar(outputs.get(i)), true);
          cvs.add(cv);
        }
        return cvs;
      } else {
        return null;
      }
    }

    @Override
    public Instruction clone() {
      return new RunExternal(cmd, outputs, inputs, order,
                             hasSideEffects, deterministic);
    }
    
  }
  
  public static class LoopContinue extends Instruction {
    private final ArrayList<Variable> newLoopVars;
    private final ArrayList<Variable> usedVariables;
    private final ArrayList<Variable> keepOpenVars;
    private final ArrayList<Boolean> blockingVars;
  
    public LoopContinue(List<Variable> newLoopVars, 
                        List<Variable> usedVariables,
                        List<Variable> keepOpenVars,
                        List<Boolean> blockingVars) {
      super(Opcode.LOOP_CONTINUE);
      this.newLoopVars = new ArrayList<Variable>(newLoopVars);
      this.usedVariables = new ArrayList<Variable>(usedVariables);
      this.keepOpenVars = new ArrayList<Variable>(keepOpenVars);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
    }
  
    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, newLoopVars, false);
      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, keepOpenVars, true);
    }
    
    @Override
    public void renameInputs(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, newLoopVars, false);
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      assert(!removeVars.contains(newLoopVars.get(0).getName()));
      ICUtil.removeVarsInList(usedVariables, removeVars);
      ICUtil.removeVarsInList(keepOpenVars, removeVars);
      ICUtil.removeVarsInList(newLoopVars, removeVars);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.op.toString().toLowerCase());
      boolean first = true;
      sb.append(" [");
      for (Variable v: this.newLoopVars) {
        if (first) {
          first = false;
        } else {
          sb.append(' ');
        }
        sb.append(v.getName());
      }
      sb.append("] #passin[");
      ICUtil.prettyPrintVarList(sb, this.usedVariables);
      sb.append(']');
      return sb.toString();
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.loopContinue(this.newLoopVars, this.usedVariables,  
                                      this.keepOpenVars,
                                      this.blockingVars);
    }
  
    @Override
    public List<Arg> getInputs() {
      // need to make sure that these variables are avail in scope
      ArrayList<Arg> res = new ArrayList<Arg>(newLoopVars.size());
      for (Variable v: newLoopVars) {
        res.add(Arg.createVar(v));
      }
      return res;
    }
  
    @Override
    public List<Variable> getOutputs() {
      // No outputs
      return new ArrayList<Variable>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return true;
    }
  
    @Override
    public Map<String, Arg> constantFold(String fnName,
              Map<String, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars,
                                      boolean assumeAllInputsClosed) {
      // See if we need to block on all inputs
      HashSet<String> alreadyDone = new HashSet<String>();
      for (int i = 0; i < this.newLoopVars.size(); i++) {
        if (this.blockingVars.get(i)) {
          Variable v = this.newLoopVars.get(i);
          if (closedVars.contains(v.getName())) {
            // Don't need to block
            this.blockingVars.set(i, false);
          } else if (alreadyDone.contains(v.getName())) {
            // In case of repeated elements
            this.blockingVars.set(i, false);
          } else {
            alreadyDone.add(v.getName());
          }
        }
      }
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on loop continue!");
    }

    @Override
    public List<Variable> getBlockingInputs() {
      return null;
    }

    public void addUsedVar(Variable variable) {
      this.usedVariables.add(variable);
    }

    public void removeUsedVar(Variable variable) {
      ICUtil.removeVarInList(usedVariables, variable.getName());
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Arg> existing) {
      // Nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopContinue(new ArrayList<Variable>(newLoopVars), 
          new ArrayList<Variable>(usedVariables), 
          new ArrayList<Variable>(keepOpenVars), 
          new ArrayList<Boolean>(blockingVars));
    }
  }
  
  public static class LoopBreak extends Instruction {
    /**
     * Variables to be closed upon loop termination
     */
    private final List<Variable> varsToClose;
  
    public LoopBreak(List<Variable> varsToClose) {
      super(Opcode.LOOP_BREAK);
      this.varsToClose = varsToClose;
    }
  
    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, varsToClose, true);
    }
  
    @Override
    public void renameInputs(Map<String, Arg> replacements) {
      // do nothing
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.op.toString().toLowerCase());
      for (Variable v: this.varsToClose) {
        sb.append(' ');
        sb.append(v.getName());
      }
      return sb.toString();
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.loopBreak(varsToClose);
    }
  
    @Override
    public List<Arg> getInputs() {
      return new ArrayList<Arg>(0);
    }
  
    @Override
    public List<Variable> getOutputs() {
      return new ArrayList<Variable>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return true;
    }
  
    @Override
    public Map<String, Arg> constantFold(String fnName,
                Map<String, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      // don't think I can do this?
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars,
                                    boolean assumeAllInputsClosed) {
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on loop continue!");
    }

    @Override
    public List<Variable> getBlockingInputs() {
      return null;
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Arg> existing) {
      // nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopBreak(new ArrayList<Variable>(varsToClose));
    }
  }
  
  public static enum Opcode {
    FAKE, // Used for ComputedValue if there isn't a real opcode
    COMMENT,
    CALL_BUILTIN, CALL_BUILTIN_LOCAL, CALL_CONTROL, CALL_SYNC,
    DEREF_INT, DEREF_STRING, DEREF_FLOAT, DEREF_BOOL, DEREF_BLOB,
    DEREF_FILE,
    STORE_INT, STORE_STRING, STORE_FLOAT, STORE_BOOL, ADDRESS_OF, 
    LOAD_INT, LOAD_STRING, LOAD_FLOAT, LOAD_BOOL, LOAD_REF,
    ARRAY_DECR_WRITERS,
    
    ARRAYREF_LOOKUP_FUTURE, ARRAY_LOOKUP_FUTURE,
    ARRAYREF_LOOKUP_IMM, ARRAY_LOOKUP_REF_IMM, ARRAY_LOOKUP_IMM,
    ARRAY_INSERT_FUTURE, ARRAY_INSERT_IMM, ARRAYREF_INSERT_FUTURE,
    ARRAYREF_INSERT_IMM,
    STRUCT_LOOKUP, STRUCTREF_LOOKUP, STRUCT_CLOSE, STRUCT_INSERT,
    ARRAY_CREATE_NESTED_FUTURE, ARRAY_REF_CREATE_NESTED_FUTURE,
    ARRAY_CREATE_NESTED_IMM, ARRAY_REF_CREATE_NESTED_IMM,
    LOOP_BREAK, LOOP_CONTINUE, 
    COPY_REF,
    LOCAL_OP, ASYNC_OP,
    RUN_EXTERNAL,
    INIT_UPDATEABLE_FLOAT, UPDATE_MIN, UPDATE_INCR, UPDATE_SCALE, LATEST_VALUE,
    UPDATE_MIN_IMM, UPDATE_INCR_IMM, UPDATE_SCALE_IMM,
    GET_FILENAME,
  }

  
  
  /**
   * Builtin operation.  Depending on the opcode (LOCAL_OP or ASYNC_OP),
   * it applied to and returns local value variables or futures.
   * Constructors are private, use factory methods to create.
   */
  public static class Builtin extends Instruction {
    public final BuiltinOpcode subop;
    
    private Variable output; // null if no output
    private List<Arg> inputs;
    private Arg priority; // priority of op if async.  null for default prio

    private Builtin(Opcode op, BuiltinOpcode subop, Variable output, 
          List<Arg> inputs, Arg priority) {
      super(op);
      assert(op == Opcode.LOCAL_OP || op == Opcode.ASYNC_OP);
      if (op == Opcode.LOCAL_OP) {
        assert(priority == null);
      }
      this.subop = subop;
      this.output = output;
      this.inputs = new ArrayList<Arg>(inputs);
      this.priority = priority;
    }
    
    public static Builtin createLocal(BuiltinOpcode subop, Variable output, 
        Arg input) {
      return new Builtin(Opcode.LOCAL_OP, subop, output, Arrays.asList(input),
                          null);
    }
    
    public static Builtin createLocal(BuiltinOpcode subop, Variable output, 
        List<Arg> inputs) {
      return new Builtin(Opcode.LOCAL_OP, subop, output, inputs, null);
    }
    
    public static Builtin createAsync(BuiltinOpcode subop, Variable output, 
        Arg input, Arg priority) {
      return new Builtin(Opcode.ASYNC_OP, subop, output, Arrays.asList(input),
                  priority);
    }
    
    public static Builtin createAsync(BuiltinOpcode subop, Variable output, 
        List<Arg> inputs, Arg priority) {
      return new Builtin(Opcode.ASYNC_OP, subop, output, inputs, priority);
    }

    @Override
    public void renameVars(Map<String, Arg> renames) {
      if (output != null && renames.containsKey(this.output.getName())) {
        this.output = renames.get(this.output.getName()).getVar();
      }
      ICUtil.replaceOpargsInList(renames, inputs);
      priority = ICUtil.replaceOparg(renames, priority, true);
    }

    @Override
    public void renameInputs(Map<String, Arg> renames) {
      ICUtil.replaceOpargsInList(renames, inputs);
      priority = ICUtil.replaceOparg(renames, priority, true);
    }

    @Override
    public String toString() {
      String res = op.toString().toLowerCase() + " ";
      if (output != null) {
        res +=  output.getName() + " = ";
      }
      res += subop.toString().toLowerCase();
      for (Arg input: inputs) {
        res += " " + input.toString();
      }
      if (priority != null) {
        res += " priority=" + priority.toString();
      }
      return res;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      if (op == Opcode.LOCAL_OP) {
        gen.localOp(subop, output, inputs);
      } else {
        assert (op == Opcode.ASYNC_OP);
        gen.asyncOp(subop, output, inputs, priority);
      }
    }

    @Override
    public List<Arg> getInputs() {
      if (priority == null) {
        return Collections.unmodifiableList(inputs);
      } else {
        // Need to add priority so that e.g. it doesn't get optimised out
        ArrayList<Arg> res = new ArrayList<Arg>(inputs.size() + 1);
        res.addAll(inputs);
        res.add(priority);
        return res;
      }
    }

    @Override
    public List<Variable> getOutputs() {
      if (output != null) {
        return Arrays.asList(output);
      } else {
        return new ArrayList<Variable>(0);
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
    public Map<String, Arg> constantFold(String fnName,
                          Map<String, Arg> knownConstants) {
      if (this.output == null) {
        return null;
      }
      
      if (this.subop == BuiltinOpcode.ASSERT || 
          this.subop == BuiltinOpcode.ASSERT_EQ) {
        compileTimeAssertCheck(subop, this.inputs, knownConstants, fnName);
      }
      
      // List of constant values for inputs, null if input not const
      ArrayList<Arg> constInputs = new ArrayList<Arg>(inputs.size());
      /* First try to replace arguments with constants */
      for (int i = 0; i < inputs.size(); i++) {
        Arg in = inputs.get(i);
        if (in.getType() == ArgType.VAR) {
          Arg c = knownConstants.get(in.getVar().getName());
          constInputs.add(c);
          if (c != null && op == Opcode.LOCAL_OP) {
            // can replace local value arg with constant
            inputs.set(i, c);
          }
        } else {
          constInputs.add(in);
        }
      }
      return Builtin.constantFold(this.subop, this.output.getName(),
          constInputs);
    }

    private static void compileTimeAssertCheck(BuiltinOpcode subop2,
        List<Arg> inputs2, Map<String, Arg> knownConstants,
        String enclosingFnName) {
      if (subop2 == BuiltinOpcode.ASSERT) {
        Arg cond;
        if (inputs2.get(0).getType() == ArgType.VAR) {
          cond = knownConstants.get(inputs2.get(0).getVar().getName());
        } else {
          cond = inputs2.get(0);
        }
        if (cond != null) {
          assert(cond.getType() == ArgType.BOOLVAL);
          if(!cond.getBoolLit()) {
            compileTimeAssertWarn(enclosingFnName, 
                "constant condition evaluated to false",
                inputs2.get(1), knownConstants);
          }
        }
      } else {
        assert(subop2 == BuiltinOpcode.ASSERT_EQ);
        
        Arg a1;
        if (inputs2.get(0).getType() == ArgType.VAR) {
          a1 = knownConstants.get(inputs2.get(0).getVar().getName());
        } else {
          a1 = inputs2.get(0);
        }
        Arg a2;
        if (inputs2.get(1).getType() == ArgType.VAR) {
          a2 = knownConstants.get(inputs2.get(1).getVar().getName());
        } else {
          a2 = inputs2.get(0);
        } 
        assert(a1.isConstant());
        assert(a2.isConstant());
        if (a1 != null && a2 != null) {
          if(!a1.equals(a2)) {
            String reason = a1.toString() + " != " + a2.toString();
            Arg msg = inputs2.get(1);
            compileTimeAssertWarn(enclosingFnName, reason, msg, knownConstants);
          }
        }
      }
    }

    private static void compileTimeAssertWarn(String enclosingFnName,
        String reason, Arg assertMsg, Map<String, Arg> knownConstants) {
      String errMessage;
      if (assertMsg.isConstant()) {
        errMessage = assertMsg.getStringLit();
      } else if (knownConstants.containsKey(assertMsg.getVar().getName())) {
        errMessage = knownConstants.get(assertMsg.getVar().getName())
                                                  .getStringLit();
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
    public Instruction constantReplace(Map<String, Arg> knownConstants) {
      // can replace short-circuitable operations with direct assignment
      if (Operators.isShortCircuitable(subop)) {
        return tryShortCircuit(knownConstants);
      }
      return null;
    }

    private Builtin tryShortCircuit(Map<String, Arg> knownConstants) {
      List<Arg> constArgs = new ArrayList<Arg>(2);
      List<Variable> varArgs = new ArrayList<Variable>(2);
      for (Arg in: inputs) {
        if (in.isConstant()) {
          constArgs.add(in);
        } else {
          Arg constIn = knownConstants.get(in.getVar().getName());
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
                output, Arg.createVar(varArgs.get(0)),
                priority);
          } else {
            return Builtin.createLocal(BuiltinOpcode.COPY_BOOL, 
                output, Arg.createVar(varArgs.get(0)));
          }
        } 
      }
      return null;
    }
    
    public static Map<String, Arg> constantFold(BuiltinOpcode op, String outVarName,
        List<Arg> constInputs) {
      Arg out = OpEvaluator.eval(op, constInputs);
      return (out == null) ? null : Collections.singletonMap(outVarName, out);
    }
    
    private static boolean hasLocalVersion(BuiltinOpcode op) {
      if (op == BuiltinOpcode.COPY_FILE) {
        return false;
      } else {
        return true;
      }
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars,
                                    boolean assumeAllInputsClosed) {
      if (op == Opcode.LOCAL_OP) {
        // already is immediate
        return null; 
      } else { 
        assert(op == Opcode.ASYNC_OP);
        if (!hasLocalVersion(subop)) {
          return null;
        }
        
        // See which arguments are closed
        if (!assumeAllInputsClosed) {
          for (Arg inarg: this.inputs) {
            assert(inarg.getType() == ArgType.VAR);
            Variable in = inarg.getVar();
            if (!closedVars.contains(in.getName())) {
              // Non-closed arg
              return null;
            }
          }
        }
          // All args are closed!
        return new MakeImmRequest(
            (this.output == null) ? 
                  null : Collections.singletonList(this.output),
            ICUtil.extractVars(this.inputs));
      }
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> newOut, List<Arg> newIn) {
      if (op == Opcode.LOCAL_OP) {
        throw new STCRuntimeError("Already immediate!");
      } else {
        assert(newIn.size() == inputs.size());
        if (output != null) {
          assert(newOut.size() == 1);
          assert(Types.derefResultType(output.getType()).equals(
              newOut.get(0).getType()));
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
    public List<Variable> getBlockingInputs() {
      if (op == Opcode.LOCAL_OP) {
        // doesn't take futures as args
        return null;
      } else {
        assert(op == Opcode.ASYNC_OP);
        // blocks on all scalar inputs
        ArrayList<Variable> result = new ArrayList<Variable>();
        for (Arg inarg: inputs) {
          if (inarg.getType() == ArgType.VAR) {
            Variable invar = inarg.getVar();
            if (Types.isReference(invar.getType())
                || Types.isScalarFuture(invar.getType())) {
              result.add(invar);
            }
          }
        }
        return result;
      }
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Arg> existing) {
      if (this.hasSideEffects()) {
        // Two invocations of this aren't equivalent
        return null;
      } else if (Operators.isCopy(subop)) {
        if (this.output.isMapped()) {
          return null;
        }
        
        // It might be assigning a constant val
        return Collections.singletonList(ComputedValue.makeCopyCV(
              this.output, this.inputs.get(0)));
      } else if (Operators.isMinMaxOp(subop)) {
        assert(this.inputs.size() == 2);
        if (this.inputs.get(0).equals(this.inputs.get(1))) {
          return Collections.singletonList(ComputedValue.makeCopyCV(
                  this.output, this.inputs.get(0)));
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
        
        return Collections.singletonList(
            new ComputedValue(this.op, 
            cvOp.toString(), cvInputs, Arg.createVar(this.output),
            outClosed));
      }
      return null;
    }

    @Override
    public Instruction clone() {
      return new Builtin(op, subop, output, Arg.cloneList(inputs), priority);
    }
  }

  public static Instruction valueSet(Variable dst, Arg value) {
    if (Types.isScalarValue(dst.getType())) {
      switch (dst.getType().getPrimitiveType()) {
      case BOOLEAN:
        assert(value.isImmediateBool());
        return Builtin.createLocal(BuiltinOpcode.COPY_BOOL, dst, value);
      case INTEGER:
        assert(value.isImmediateInt());
        return Builtin.createLocal(BuiltinOpcode.COPY_INT, dst, value);
      case FLOAT:
        assert(value.isImmediateFloat());
        return Builtin.createLocal(BuiltinOpcode.COPY_FLOAT, dst, value);
      case STRING:
        assert(value.isImmediateString());
        return Builtin.createLocal(BuiltinOpcode.COPY_STRING, dst, value);
      default:
        // fall through
        break;
      }
    } else if (Types.isArray(dst.getType()) || Types.isStruct(dst.getType())) {
      assert(dst.getStorage() == VariableStorage.ALIAS);
      assert (value.getType() == ArgType.VAR);
      return TurbineOp.copyRef(dst, value.getVar());
    }

    throw new STCRuntimeError("Unhandled case in valueSet: "
        + " assign " + value.toString() + " to " + dst.toString());
  }

  public static Instruction retrieveValueOf(Variable dst, Variable src) {
    assert(Types.isScalarValue(dst.getType()));
    assert(Types.isScalarFuture(src.getType())
            || Types.isScalarUpdateable(src.getType()));
    switch (src.getType().getPrimitiveType()) {
    case BOOLEAN:
      return TurbineOp.retrieveBool(dst, src);
    case INTEGER:
      return TurbineOp.retrieveInt(dst, src);
    case FLOAT:
      return TurbineOp.retrieveFloat(dst, src);
    case STRING:
      return TurbineOp.retrieveString(dst, src);
    default:
      throw new STCRuntimeError("method to retrieve " +
      		src.getType().typeName() + " is not known yet");
    }
  }
  
  /**
   * Return the canonical ComputedValue representation for
   * retrieving the value of this type
   * @param src
   * @return null if cannot be retrieved
   */
  public static ComputedValue retrieveCompVal(Variable src) {
    SwiftType srcType = src.getType();
    Opcode op = retrieveOpcode(srcType);
    if (op == null) {
      return null;
    }
    return new ComputedValue(op, "", 
        Collections.singletonList(Arg.createVar(src)));
  }

  public static ComputedValue assignComputedVal(Variable dst, Arg val) {
    SwiftType dstType = dst.getType();
    if (Types.isScalarValue(dstType)) {
        BuiltinOpcode op;
        switch(dstType.getPrimitiveType()) {
        case BOOLEAN:
          op = BuiltinOpcode.COPY_BOOL;
          break;
        case INTEGER:
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
        case FILE:
          op = BuiltinOpcode.COPY_FILE;
          break;
        default:
          throw new STCRuntimeError("Unhandled type: "
              + dstType);
        }
        return new ComputedValue(Opcode.LOCAL_OP, 
            op.toString(), Arrays.asList(val), Arg.createVar(dst), false);
    } else {
      Opcode op = assignOpcode(dstType);
      if (op != null) {
        return new ComputedValue(op, "", Arrays.asList(val), Arg.createVar(dst)
                                                                          , true);
      }
    }
    throw new STCRuntimeError("DOn't know how to assign to " + dst);
  }

  private static Opcode assignOpcode(SwiftType dstType) {
    Opcode op = null;
    if (Types.isScalarFuture(dstType)) {
       switch(dstType.getPrimitiveType()) {
       case BOOLEAN:
         op = Opcode.STORE_BOOL;
         break;
       case INTEGER:
         op = Opcode.STORE_INT;
         break;
       case FLOAT:
         op = Opcode.STORE_FLOAT;
         break;
       case STRING:
         op = Opcode.STORE_STRING;
         break;
       default:
         throw new STCRuntimeError("don't know how to assign " + dstType);
       }
    } else if (Types.isReference(dstType)) {
      op = Opcode.ADDRESS_OF;
    }
    return op;
  }
  
  private static Opcode retrieveOpcode(SwiftType srcType) {
    Opcode op;
    if (Types.isScalarFuture(srcType)) {
      switch(srcType.getPrimitiveType()) {
      case BOOLEAN:
        op = Opcode.LOAD_BOOL;
        break;
      case INTEGER:
        op = Opcode.LOAD_INT;
        break;
      case FLOAT:
        op = Opcode.LOAD_FLOAT;
        break;
      case STRING:
        op = Opcode.LOAD_STRING;
        break;
      default:
        // Can't retrieve other types
        op = null;
      }

    } else if (Types.isReference(srcType)) {
      op = Opcode.LOAD_REF;
    } else {
      op = null;
    }
    return op;
  }
  

  private static Opcode derefOpCode(SwiftType type) {
    if (Types.isReference(type)) {
      SwiftType refedType = type.getMemberType();
      if (Types.isScalarFuture(refedType)) {
        switch (refedType.getPrimitiveType()) {
        case BLOB:
          return Opcode.DEREF_BLOB;
        case FILE:
          return Opcode.DEREF_FILE;
        case BOOLEAN:
          return Opcode.DEREF_BOOL;
        case FLOAT:
          return Opcode.DEREF_FLOAT;
        case INTEGER:
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


  public static Instruction futureSet(Variable dst, Arg src) {
    assert(Types.isScalarFuture(dst.getType()));
    switch (dst.getType().getPrimitiveType()) {
    case BOOLEAN:
      assert(src.isImmediateBool());
      return TurbineOp.assignBool(dst, src);
    case INTEGER:
      assert(src.isImmediateInt());
      return TurbineOp.assignInt(dst, src);
    case FLOAT:
      assert(src.isImmediateFloat());
      return TurbineOp.assignFloat(dst, src);
    case STRING:
      assert(src.isImmediateString());
      return TurbineOp.assignString(dst, src);
    default:
      throw new STCRuntimeError("method to set " +
          dst.getType().typeName() + " is not known yet");
    }
  }

  private static String formatFunctionCall(Opcode op, 
      String functionName, List<Variable> outputs, List<Arg> inputs) {
    String result = op.toString().toLowerCase() + " " + functionName;
    result += " [";
    for (Variable v: outputs) {
      result += " " + v.getName();
    }
    result += " ] [";
    for (Arg a: inputs) {
      result += " " + a.toString();
    }
    result += " ]";
    return result;
  }
}


