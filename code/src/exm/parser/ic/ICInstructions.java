package exm.parser.ic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.ast.Builtins;
import exm.ast.Builtins.UpdateMode;
import exm.ast.Types;
import exm.ast.Builtins.ArithOpcode;
import exm.ast.Types.SwiftType;
import exm.ast.Variable;
import exm.ast.Variable.VariableStorage;
import exm.parser.CompilerBackend;
import exm.parser.ic.SwiftIC.GenInfo;
import exm.parser.ic.opt.ComputedValue;
import exm.parser.ic.opt.ComputedValue.EquivalenceType;
import exm.parser.util.ParserRuntimeException;
import exm.tcl.TclString;

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
    public abstract void renameVars(Map<String, Oparg> renames);
  
    /**
     * Replace any input variable with a replacement, which is another
     * variable in scope which should have the same value
     * Assume that the variable being replaced will be kept around
     * @param replacements
     */
    public abstract void renameInputs(Map<String, Oparg> renames);

    @Override
    public abstract String toString();
  
    public abstract void generate(Logger logger, CompilerBackend gen,
            GenInfo info);
  
  
    /** List of variables the instruction reads */
    public abstract List<Oparg> getInputs();
  
    /** List of variables the instruction writes */
    public abstract List<Oparg> getOutputs();
    
    public Oparg getInput(int i) {
      return getInputs().get(i);
    }
    
    public Oparg getOutput(int i) {
      return getOutputs().get(i);
    }
  
    public abstract boolean hasSideEffects();
  
    public boolean writesAliasVar() {
      // Writes to alias variables can have non-local effects
      for (Oparg out: this.getOutputs()) {
        if (out.getType() == OpargType.VAR &&
            out.getVariable().getStorage() == VariableStorage.ALIAS) {
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
    public abstract Map<String, Oparg> constantFold(
                    String fnName,
                    Map<String, Oparg> knownConstants);
  
    /**
     * @param knownConstants
     * @return an instruction if this can be replaced by another instruction
     *      using a constant value, null if it cannot be replaced
     */
    public abstract Instruction constantReplace(
                                Map<String, Oparg> knownConstants);
  
    
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
     * @param closedVars
     * @return null if it cannot be made immediate, if true,
     *            a list of vars that are the variables whose values are needed
     *            and output vars that need to be have value vars created
     */
    public abstract MakeImmRequest canMakeImmediate(Set<String> closedVars);

    public abstract MakeImmChange makeImmediate(List<Variable> outVals,
                                                List<Oparg> inValues);

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
                        Map<ComputedValue, Oparg> existing);
   
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
    public void renameVars(Map<String, Oparg> renames) {
      // Don't do anything
    }

    @Override
    public void renameInputs(Map<String, Oparg> replacements) {
      // Nothing
    }
  
    @Override
    public List<Oparg> getInputs() {
      return new ArrayList<Oparg>(0);
    }
  
    @Override
    public List<Oparg> getOutputs() {
      return new ArrayList<Oparg>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return false;
    }
  
    @Override
    public Map<String, Oparg> constantFold(String fnName,
                Map<String, Oparg> knownConstants) {
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Oparg> knownConstants) {
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars) {
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> out, List<Oparg> values) {
      throw new ParserRuntimeException("Not valid on comment!");
    }

    @Override
    public List<Variable> getBlockingInputs() {
      return null;
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Oparg> existing) {
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
    private TurbineOp(Opcode op, List<Oparg> args) {
      super(op);
      this.args = args;
    }
  
    private final List<Oparg> args;
  
    @Override
    public String toString() {
      String result = op.toString().toLowerCase();
      for (Oparg v: args) {
        result += " " + v.toString();
      }
      return result;
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      // Recreate calls that were used to generate this instruction
      switch (op) {
      case ASSIGN_INT:
        gen.assignInt(args.get(0).getVariable(), args.get(1));
        break;
      case ASSIGN_BOOL:
        gen.assignBool(args.get(0).getVariable(), args.get(1));
        break;
      case ASSIGN_FLOAT:
        gen.assignFloat(args.get(0).getVariable(), args.get(1));
        break;
      case ASSIGN_STRING:
        gen.assignString(args.get(0).getVariable(), args.get(1));
        break;
      case ADDRESS_OF:
        gen.assignReference(args.get(0).getVariable(), args.get(1).getVariable());
        break;
      case ARRAY_LOOKUP_FUTURE:
        gen.arrayLookupFuture(args.get(0).getVariable(),
              args.get(1).getVariable(), args.get(2).getVariable(), false);
        break;
      case ARRAYREF_LOOKUP_FUTURE:
        gen.arrayLookupFuture(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2).getVariable(), true);
        break;
      case ARRAY_LOOKUP_REF_IMM:
        gen.arrayLookupRefImm(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2), false);
        break;
      case ARRAY_LOOKUP_IMM:
        gen.arrayLookupImm(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2));
        break;
      case ARRAYREF_LOOKUP_IMM:
        gen.arrayLookupRefImm(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2), true);
        break;
      case ARRAY_INSERT_FUTURE:
        gen.arrayInsertFuture(args.get(2).getVariable(),
            args.get(0).getVariable(), args.get(1).getVariable());
        break;
      case ARRAY_INSERT_IMM:
        gen.arrayInsertImm(args.get(2).getVariable(),
            args.get(0).getVariable(), args.get(1));
        break;
      case ARRAYREF_INSERT_FUTURE:
        gen.arrayRefInsertFuture(args.get(2).getVariable(),
            args.get(0).getVariable(), args.get(1).getVariable(),
            args.get(3).getVariable());
        break;
      case ARRAYREF_INSERT_IMM:
        gen.arrayRefInsertImm(args.get(2).getVariable(),
            args.get(0).getVariable(), args.get(1), args.get(3).getVariable());
        break;
      case STRUCT_LOOKUP:
        gen.structLookup(args.get(1).getVariable(), args.get(2).getStringLit(),
                                                    args.get(0).getVariable());
        break;
      case STRUCTREF_LOOKUP:
        gen.structRefLookup(args.get(1).getVariable(), args.get(2).getStringLit(),
                                                    args.get(0).getVariable());
        break;
      case STRUCT_INSERT:
        gen.structInsert(args.get(0).getVariable(), args.get(1).getStringLit(),
            args.get(2).getVariable());
        break;
      case STRUCT_CLOSE:
        gen.structClose(args.get(0).getVariable());
        break;
      case DEREFERENCE_INT:
        gen.dereferenceInt(args.get(0).getVariable(),
                                args.get(1).getVariable());
        break;
      case DEREFERENCE_BOOL:
        gen.dereferenceBool(args.get(0).getVariable(),
                                args.get(1).getVariable());
        break;
      case DEREFERENCE_FLOAT:
        gen.dereferenceFloat(args.get(0).getVariable(),
                                args.get(1).getVariable());
        break;
      case DEREFERENCE_STRING:
        gen.dereferenceString(args.get(0).getVariable(),
                                args.get(1).getVariable());
        break;
      case DEREFERENCE_BLOB:
        gen.dereferenceBlob(args.get(0).getVariable(),
                                args.get(1).getVariable());
        break;
      case RETRIEVE_REF:
        gen.retrieveRef(args.get(0).getVariable(),
                                args.get(1).getVariable());
        break;
      case COPY_REF:
        gen.makeAlias(args.get(0).getVariable(), args.get(1).getVariable());
        break;
      case ARRAY_CREATE_NESTED_FUTURE:
        gen.arrayCreateNestedFuture(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2).getVariable());
        break;
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        gen.arrayRefCreateNestedFuture(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2).getVariable());
        break;
      case ARRAY_REF_CREATE_NESTED_IMM:
        gen.arrayRefCreateNestedImm(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2));
        break;
      case ARRAY_CREATE_NESTED_IMM:
        gen.arrayCreateNestedImm(args.get(0).getVariable(),
            args.get(1).getVariable(), args.get(2));
        break;
      case RETRIEVE_INT:
        gen.retrieveInt(args.get(0).getVariable(),
                  args.get(1).getVariable());
        break;
      case RETRIEVE_STRING:
        gen.retrieveString(args.get(0).getVariable(),
            args.get(1).getVariable());
        break;
      case RETRIEVE_BOOL:
        gen.retrieveBool(args.get(0).getVariable(),
            args.get(1).getVariable());
        break;
      case RETRIEVE_FLOAT:
        gen.retrieveFloat(args.get(0).getVariable(),
            args.get(1).getVariable());
        break;  
      case INIT_UPDATEABLE_FLOAT:
        gen.initUpdateable(args.get(0).getVariable(), args.get(1));
        break;
      case LATEST_VALUE:
        gen.latestValue(args.get(0).getVariable(), args.get(1).getVariable());
        break;
      case UPDATE_INCR:
        gen.update(args.get(0).getVariable(), UpdateMode.INCR, 
            args.get(1).getVariable());
        break;
      case UPDATE_MIN:
        gen.update(args.get(0).getVariable(), UpdateMode.MIN, 
            args.get(1).getVariable());
        break;
      case UPDATE_SCALE:
        gen.update(args.get(0).getVariable(), UpdateMode.SCALE, 
            args.get(1).getVariable());
        break;
      case UPDATE_INCR_IMM:
        gen.updateImm(args.get(0).getVariable(), UpdateMode.INCR, 
            args.get(1));
        break;
      case UPDATE_MIN_IMM:
        gen.updateImm(args.get(0).getVariable(), UpdateMode.MIN, 
            args.get(1));
        break;
      case UPDATE_SCALE_IMM:
        gen.updateImm(args.get(0).getVariable(), UpdateMode.SCALE, 
            args.get(1));
        break;
      default:
        throw new ParserRuntimeException("didn't expect to see op " +
                  op.toString() + " here");
      }
  
    }
  
    public static TurbineOp arrayRefLookupFuture(Variable oVar, Variable arrayRefVar,
        Variable indexVar) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_FUTURE,
          Arrays.asList(Oparg.createVar(oVar), Oparg.createVar(arrayRefVar),
                                    Oparg.createVar(indexVar)));
    }
  
    public static TurbineOp arrayLookupFuture(Variable oVar, Variable arrayVar,
        Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_FUTURE,
          Arrays.asList(Oparg.createVar(oVar), Oparg.createVar(arrayVar),
                                                Oparg.createVar(indexVar)));
    }
  
    public static Instruction arrayInsertFuture(Variable iVar,
        Variable arrayVar, Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_INSERT_FUTURE,
          Arrays.asList(Oparg.createVar(arrayVar), Oparg.createVar(indexVar),
              Oparg.createVar(iVar)));
    }
  
    public static Instruction arrayRefInsertFuture(Variable iVar,
        Variable arrayVar, Variable indexVar, Variable outerArrayVar) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_FUTURE,
          Arrays.asList(Oparg.createVar(arrayVar), Oparg.createVar(indexVar),
              Oparg.createVar(iVar), Oparg.createVar(outerArrayVar)));
    }
    
    public static Instruction arrayRefLookupImm(Variable oVar,
        Variable arrayVar, Oparg arrayIndex) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_IMM,
          Arrays.asList(Oparg.createVar(oVar), Oparg.createVar(arrayVar),
                                               arrayIndex));
    }
  
    public static Instruction arrayLookupRefImm(Variable oVar, Variable arrayVar,
        Oparg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_REF_IMM,
          Arrays.asList(Oparg.createVar(oVar), Oparg.createVar(arrayVar),
                                               arrayIndex));
    }
    
    public static Instruction arrayLookupImm(Variable oVar, Variable arrayVar,
        Oparg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_IMM,
          Arrays.asList(Oparg.createVar(oVar), Oparg.createVar(arrayVar),
                                               arrayIndex));
    }
  
    public static Instruction arrayInsertImm(Variable iVar,
        Variable arrayVar, Oparg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_INSERT_IMM,
          Arrays.asList(Oparg.createVar(arrayVar), arrayIndex,
                        Oparg.createVar(iVar)));
    }
    
    public static Instruction arrayRefInsertImm(Variable iVar,
        Variable arrayVar, Oparg arrayIndex, Variable outerArray) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_IMM,
          Arrays.asList(Oparg.createVar(arrayVar), arrayIndex,
                        Oparg.createVar(iVar),
                        Oparg.createVar(outerArray)));
    }
  
  
    public static Instruction structLookup(Variable oVar, Variable structVar,
                                                          String fieldName) {
      return new TurbineOp(Opcode.STRUCT_LOOKUP,
          Arrays.asList(Oparg.createVar(oVar), Oparg.createVar(structVar),
              Oparg.createStringLit(fieldName)));
    }
    
    public static Instruction structRefLookup(Variable oVar, Variable structVar,
        String fieldName) {
      return new TurbineOp(Opcode.STRUCTREF_LOOKUP,
              Arrays.asList(Oparg.createVar(oVar), Oparg.createVar(structVar),
              Oparg.createStringLit(fieldName)));
    }
  
    public static Instruction assignInt(Variable target, Oparg src) {
      return new TurbineOp(Opcode.ASSIGN_INT,
          Arrays.asList(Oparg.createVar(target), src));
    }

    public static Instruction assignBool(Variable target, Oparg src) {
      return new TurbineOp(Opcode.ASSIGN_BOOL,
          Arrays.asList(Oparg.createVar(target), src));
    }
  
    public static Instruction assignFloat(Variable target, Oparg src) {
      return new TurbineOp(Opcode.ASSIGN_FLOAT,
          Arrays.asList(Oparg.createVar(target), src));
    }
  
    public static Instruction assignString(Variable target, Oparg src) {
      return new TurbineOp(Opcode.ASSIGN_STRING,
          Arrays.asList(Oparg.createVar(target), src));
    }
  
    public static Instruction retrieveString(Variable target, Variable source) {
      return new TurbineOp(Opcode.RETRIEVE_STRING,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(source)));
    }
  
    public static Instruction retrieveInt(Variable target, Variable source) {
      return new TurbineOp(Opcode.RETRIEVE_INT,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(source)));
    }
  
    public static Instruction retrieveBool(Variable target, Variable source) {
      return new TurbineOp(Opcode.RETRIEVE_BOOL,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(source)));
    }
    
    public static Instruction retrieveFloat(Variable target, Variable source) {
      return new TurbineOp(Opcode.RETRIEVE_FLOAT,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(source)));
    }
  
    public static Instruction structClose(Variable struct) {
      return new TurbineOp(Opcode.STRUCT_CLOSE,
          Arrays.asList(Oparg.createVar(struct)));
    }
  
    public static Instruction structInsert(Variable structVar,
        String fieldName, Variable fieldContents) {
      return new TurbineOp(Opcode.STRUCT_INSERT,
          Arrays.asList(Oparg.createVar(structVar),
                      Oparg.createStringLit(fieldName),
                      Oparg.createVar(fieldContents)));
    }
  
    public static Instruction addressOf(Variable target, Variable src) {
      return new TurbineOp(Opcode.ADDRESS_OF,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(src)));
    }
  
    public static Instruction dereferenceInt(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREFERENCE_INT,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(src)));
    }
    
    public static Instruction dereferenceBool(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREFERENCE_BOOL,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(src)));
    }
  
    public static Instruction dereferenceFloat(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREFERENCE_FLOAT,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(src)));
    }
  
    public static Instruction dereferenceString(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREFERENCE_STRING,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(src)));
    }
  
    public static Instruction dereferenceBlob(Variable target, Variable src) {
      return new TurbineOp(Opcode.DEREFERENCE_BLOB,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(src)));
    }
    
    public static Instruction retrieveRef(Variable target, Variable src) {
      return new TurbineOp(Opcode.RETRIEVE_REF,
          Arrays.asList(Oparg.createVar(target), Oparg.createVar(src)));
    }
    
    public static Instruction copyRef(Variable dst, Variable src) {
      return new TurbineOp(Opcode.COPY_REF,
          Arrays.asList(Oparg.createVar(dst), Oparg.createVar(src)));
          
    }
  
    public static Instruction arrayCreateNestedComputed(Variable arrayResult,
        Variable arrayVar, Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_FUTURE,
          Arrays.asList(Oparg.createVar(arrayResult),
              Oparg.createVar(arrayVar), Oparg.createVar(indexVar)));
    }
  
    public static Instruction arrayCreateNestedImm(Variable arrayResult,
        Variable arrayVar, Oparg arrIx) {
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_IMM,
          Arrays.asList(Oparg.createVar(arrayResult),
              Oparg.createVar(arrayVar), arrIx));
    }
  
    public static Instruction arrayRefCreateNestedComputed(Variable arrayResult,
        Variable arrayVar, Variable indexVar) {
      return new TurbineOp(Opcode.ARRAY_REF_CREATE_NESTED_FUTURE,
          Arrays.asList(Oparg.createVar(arrayResult),
              Oparg.createVar(arrayVar), Oparg.createVar(indexVar)));
    }
  
  
    public static Instruction arrayRefCreateNestedImmIx(Variable arrayResult,
        Variable arrayVar, Oparg arrIx) {
      return new TurbineOp(Opcode.ARRAY_REF_CREATE_NESTED_IMM,
          Arrays.asList(Oparg.createVar(arrayResult),
              Oparg.createVar(arrayVar), arrIx));
    }
  
  
    public static Instruction initUpdateableFloat(Variable updateable, 
                                                              Oparg val) {
      return new TurbineOp(Opcode.INIT_UPDATEABLE_FLOAT, 
          Arrays.asList(Oparg.createVar(updateable), val));
      
    }

    public static Instruction latestValue(Variable result, 
                              Variable updateable) {
      return new TurbineOp(Opcode.LATEST_VALUE, Arrays.asList(
          Oparg.createVar(result), Oparg.createVar(updateable)));
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
        throw new ParserRuntimeException("Unknown UpdateMode"
            + updateMode);
      }
      return new TurbineOp(op, Arrays.asList(Oparg.createVar(updateable), 
                                             Oparg.createVar(val)));
    }

    public static Instruction updateImm(Variable updateable,
        UpdateMode updateMode, Oparg val) {
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
        throw new ParserRuntimeException("Unknown UpdateMode"
            + updateMode);
      }
      return new TurbineOp(op, Arrays.asList(Oparg.createVar(updateable), 
                                                                   val));
    }

    @Override
    public void renameVars(Map<String, Oparg> renames) {
      ICUtil.replaceOpargsInList2(renames, args);
    }
  
    @Override
    public void renameInputs(Map<String, Oparg> renames) {
      
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
       ICUtil.replaceOpargsInList2(renames, args.subList(firstInputArg, 
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
      case ASSIGN_INT:
      case ASSIGN_BOOL:
      case ASSIGN_FLOAT:
      case ASSIGN_STRING:
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_REF_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_REF_CREATE_NESTED_IMM:
      case DEREFERENCE_INT:
      case DEREFERENCE_BOOL:
      case DEREFERENCE_FLOAT:
      case DEREFERENCE_STRING:
      case DEREFERENCE_BLOB:
      case RETRIEVE_INT:
      case RETRIEVE_BOOL:
      case RETRIEVE_FLOAT:
      case RETRIEVE_STRING:
      case STRUCT_LOOKUP:
      case STRUCTREF_LOOKUP:
      case ADDRESS_OF:
      case RETRIEVE_REF:
      case COPY_REF:
          return 1;
      default:
        throw new ParserRuntimeException("Need to add opcode " + op.toString()
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
      
      case ASSIGN_INT:
      case ASSIGN_BOOL:
      case ASSIGN_FLOAT:
      case ASSIGN_STRING:
      case DEREFERENCE_INT:
      case DEREFERENCE_BOOL:
      case DEREFERENCE_FLOAT:
      case DEREFERENCE_STRING:
      case DEREFERENCE_BLOB:
      case RETRIEVE_INT:
      case RETRIEVE_BOOL:
      case RETRIEVE_FLOAT:
      case RETRIEVE_STRING:
      case ARRAY_LOOKUP_REF_IMM:
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_IMM:
          return this.writesAliasVar();

      case STRUCT_LOOKUP:
      case RETRIEVE_REF:
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
        throw new ParserRuntimeException("Need to add opcode " + op.toString()
            + " to hasSideEffects");
      }
    }
  
    @Override
    public List<Oparg> getOutputs() {
      return args.subList(0, numOutputArgs());
    }
  
    @Override
    public List<Oparg> getInputs() {
      return args.subList(numOutputArgs(), args.size());
    }
  
    @Override
    public Map<String, Oparg> constantFold(String fnName,
                        Map<String, Oparg> knownConstants) {
      switch (op) {
      case ASSIGN_INT:
      case ASSIGN_STRING:
      case ASSIGN_BOOL:
      case ASSIGN_FLOAT:
      case RETRIEVE_BOOL:
      case RETRIEVE_FLOAT:
      case RETRIEVE_INT:
      case RETRIEVE_STRING:
        // The input arg could be a var or a literal constant
        if (args.get(1).getType() == OpargType.VAR) {
          Oparg val = knownConstants.get(args.get(1).getVariable().getName());
          if (val != null) {
            HashMap<String, Oparg> r = new HashMap<String, Oparg>();
            r.put(args.get(0).getVariable().getName(), val);
            return r;
          }
        }
        break;
      }
  
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Oparg> knownConstants) {
      switch (op) {
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
        Variable index = args.get(2).getVariable();
        if (knownConstants.containsKey(index.getName())) {
          Oparg cIndex = knownConstants.get(index.getName());
          if (op == Opcode.ARRAY_LOOKUP_FUTURE) {
            return arrayLookupRefImm(args.get(0).getVariable(),
                args.get(1).getVariable(), cIndex);
          } else {
            return arrayRefLookupImm(args.get(0).getVariable(),
                args.get(1).getVariable(), cIndex);
          }
        }
        break;
      case ARRAYREF_INSERT_FUTURE:
      case ARRAY_INSERT_FUTURE:
        Variable sIndex = args.get(1).getVariable();
        if (knownConstants.containsKey(sIndex.getName())) {
          Oparg cIndex = knownConstants.get(sIndex.getName());
          if (op == Opcode.ARRAY_INSERT_FUTURE) {
            return arrayInsertImm(args.get(2).getVariable(),
                      args.get(0).getVariable(), cIndex);
          } else {
            return arrayRefInsertImm(args.get(2).getVariable(),
                args.get(0).getVariable(), cIndex, args.get(3).getVariable());
          }
        }
        break;
      }
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars) {
      // Try to take advantage of closed variables 
      switch (op) {
      case ARRAY_LOOKUP_REF_IMM:
        // If array is closed or this index already inserted,
        // don't need to block on array.  
        // NOTE: could try to reduce other forms to this in one step,
        //      but its probably just easier to do it in multiple steps
        //      on subsequent passes
        Variable arr = args.get(1).getVariable();
        if (closedVars.contains(arr.getName())) {
          // Don't need to retrieve any value, but just use this protocol
          return new MakeImmRequest(null, new ArrayList<Variable>());
        }
        break;
        
      case ARRAY_LOOKUP_FUTURE:
        Variable index = args.get(2).getVariable();
        if (closedVars.contains(index.getName())) {
          return new MakeImmRequest(null, Arrays.asList(index));
        }
        break;
      case ARRAYREF_LOOKUP_FUTURE:
        // We will take either the index or the dereferenced array
        List<Variable> req = mkImmVarList(closedVars, 
                  args.get(1).getVariable(), args.get(2).getVariable());
        if (req.size() > 0) {
          return new MakeImmRequest(null, req);
        }
        break;
      case ARRAYREF_LOOKUP_IMM:
        // Could skip using reference
        Variable arrRef2 = args.get(1).getVariable();
        if (closedVars.contains(arrRef2.getName())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef2));
        }
        break;
      case ARRAY_INSERT_FUTURE:
        Variable sIndex = args.get(1).getVariable();
        if (closedVars.contains(sIndex.getName())) {
          return new MakeImmRequest(null, Arrays.asList(sIndex));
        }
        break;
      case ARRAYREF_INSERT_IMM:
        Variable arrRef3 = args.get(0).getVariable();
        if (closedVars.contains(arrRef3.getName())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef3));
        }
        break;
      case ARRAYREF_INSERT_FUTURE:
        // We will take either the index or the dereferenced array
        List<Variable> req2 = mkImmVarList(closedVars,
                    args.get(0).getVariable(), args.get(1).getVariable());
        if (req2.size() > 0) {
          return new MakeImmRequest(null, req2);
        }
        break;
      case ARRAY_CREATE_NESTED_FUTURE:
        // Try to get immediate index
        Variable index2 = args.get(2).getVariable();
        if (closedVars.contains(index2.getName())) {
          return new MakeImmRequest(null, Arrays.asList(index2));
        }
        break;
      case ARRAY_REF_CREATE_NESTED_IMM:
        Variable arrRef5 = args.get(1).getVariable();
        if (closedVars.contains(arrRef5.getName())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef5));
        }
        break;
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        List<Variable> req5 = mkImmVarList(closedVars, 
            args.get(1).getVariable(), args.get(2).getVariable());
        if (req5.size() > 0) {
          return new MakeImmRequest(null, req5);
        }
        break;
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
        return new MakeImmRequest(null, Arrays.asList(
                  args.get(1).getVariable())); 
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
    public MakeImmChange makeImmediate(List<Variable> out, List<Oparg> values) {
      switch (op) {
      case ARRAY_LOOKUP_REF_IMM:
        assert(values.size() == 0);
        // OUtput switched from ref to value
        Variable refOut = args.get(0).getVariable();
        Variable valOut = Variable.createDerefTmp(refOut, 
                                      VariableStorage.ALIAS);
        Instruction newI = arrayLookupImm(valOut,
            args.get(1).getVariable(), args.get(2));
        return new MakeImmChange(valOut, refOut, newI);
      case ARRAY_LOOKUP_FUTURE:
        assert(values.size() == 1);
        return new MakeImmChange(
                arrayLookupRefImm(args.get(0).getVariable(), 
                args.get(1).getVariable(), values.get(0)));
      case ARRAYREF_LOOKUP_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        // Could be either array ref, index, or both
        if (values.size() == 2) {
          return new MakeImmChange(arrayLookupRefImm(
              args.get(0).getVariable(), values.get(0).getVariable(), 
              values.get(1)));
        } else { 
          Oparg v1 = values.get(0);
          if (v1.isImmediateInt()) {
            // replace index
            return new MakeImmChange(
                    arrayRefLookupImm(args.get(0).getVariable(), 
                    args.get(1).getVariable(), v1));
          } else {
            // replace the array ref
            return new MakeImmChange(
                    arrayLookupFuture(args.get(0).getVariable(), 
                            v1.getVariable(), args.get(2).getVariable()));
          }
        }
      case ARRAYREF_LOOKUP_IMM:
        assert(values.size() == 1);
        // Switch from ref to plain array
        return new MakeImmChange(arrayLookupRefImm(
                args.get(0).getVariable(), values.get(0).getVariable(),
                                                         args.get(2)));
      case ARRAY_INSERT_FUTURE:
        assert(values.size() == 1);
        return new MakeImmChange(
                arrayInsertImm(args.get(2).getVariable(), 
                args.get(0).getVariable(), values.get(0)));
      case ARRAYREF_INSERT_IMM:
        assert(values.size() == 1);
        // Switch from ref to plain array
        return new MakeImmChange(arrayInsertImm(
            args.get(2).getVariable(), values.get(0).getVariable(),
                                                      args.get(1)));
      case ARRAYREF_INSERT_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        // Could be either array ref, index, or both
        if (values.size() == 2) {
          return new MakeImmChange(arrayInsertImm(
              args.get(2).getVariable(),
              values.get(0).getVariable(), values.get(1)));
        } else { 
          Oparg v1 = values.get(0);
          if (v1.isImmediateInt()) {
            // replace index
            return new MakeImmChange(
                    arrayRefInsertImm(args.get(2).getVariable(), 
                    args.get(0).getVariable(), v1, args.get(3).getVariable()));
          } else {
            // replace the array ref
            return new MakeImmChange(
                    arrayInsertFuture(args.get(2).getVariable(), 
                            v1.getVariable(), args.get(1).getVariable()));
          }
        }
      case ARRAY_CREATE_NESTED_FUTURE:
        assert(values.size() == 1);
        // Output type of instruction changed from ref to direct
        // array handle
        Variable oldOut = args.get(0).getVariable();
        assert(Types.isArrayRef(oldOut.getType()));
        Variable newOut = Variable.createDerefTmp(oldOut, 
                                                VariableStorage.ALIAS);
        return new MakeImmChange(newOut, oldOut,
            arrayCreateNestedImm(newOut,
                            args.get(1).getVariable(), values.get(0)));
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        if (values.size() == 2) {
          Variable oldOut2 = args.get(0).getVariable();
          assert(Types.isArrayRef(oldOut2.getType()));
          Variable newOut2 = Variable.createDerefTmp(oldOut2,
                                          VariableStorage.ALIAS);
          return new MakeImmChange(newOut2, oldOut2,
              arrayCreateNestedImm(newOut2, 
                  values.get(0).getVariable(), values.get(1)));
        } else {
          // We weren't able to switch to the version returning a plain
          // array
          Oparg newA = values.get(0);
          if (newA.isImmediateInt()) {
            return new MakeImmChange(
                arrayRefCreateNestedImmIx(args.get(0).getVariable(),
                    args.get(1).getVariable(), newA));
          } else {
            // Replacing array ref with array
            return new MakeImmChange(
                arrayRefCreateNestedImmIx(args.get(0).getVariable(),
                    newA.getVariable(), args.get(2)));
          }
        }
      case ARRAY_REF_CREATE_NESTED_IMM:
        assert(values.size() == 1);
        Variable oldOut3 = args.get(0).getVariable();
        assert(Types.isArrayRef(oldOut3.getType()));
        Variable newOut3 = Variable.createDerefTmp(oldOut3,
                                                VariableStorage.ALIAS);
        return new MakeImmChange(newOut3, oldOut3,
            arrayCreateNestedImm(newOut3,
                            values.get(0).getVariable(), args.get(2)));
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
          throw new ParserRuntimeException("op: " + op +
                                    " ... shouldn't be here");
        }
        return new MakeImmChange(null, null, TurbineOp.updateImm(
            this.args.get(0).getVariable(), mode, values.get(0)));
      }
      }
      throw new ParserRuntimeException("Couldn't make inst "
          + this.toString() + " immediate with vars: "
          + values.toString());
    }

    @Override
    public List<Variable> getBlockingInputs() {
      ArrayList<Variable> blocksOn = new ArrayList<Variable>();
      for (Oparg oa: getInputs()) {
        if (oa.type == OpargType.VAR) {
          Variable v = oa.getVariable();
          SwiftType t = v.getType();
          if (Types.isScalarFuture(t)
              || Types.isReference(t)) {
            blocksOn.add(v);
          } else if (Types.isScalarValue(t) ||
              Types.isStruct(t) || Types.isArray(t) ||
              Types.isScalarUpdateable(t)) {
            // No turbine ops block on these types
          } else {
            throw new ParserRuntimeException("Don't handle type "
                + t.toString() + " here");
          }
        }
      }
      return blocksOn;
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Oparg> existing) {
      Oparg arr = null;
      Oparg ix = null;
      Oparg contents = null;
      ComputedValue cv = null;
      switch(op) {
        case RETRIEVE_BOOL:
        case RETRIEVE_FLOAT:
        case RETRIEVE_INT:
        case RETRIEVE_REF:
        case RETRIEVE_STRING: {
          // address_of and retrieve* are invertible
          Oparg src = args.get(1);
          Oparg val = args.get(0);
          ComputedValue retrieve = vanillaComputedValue(true);
          Opcode cvop = assignOpcode(src.getSwiftType());
          if (cvop == null) {
            throw new ParserRuntimeException("Need assign op for "
                + src.getVariable());
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
        case ASSIGN_BOOL:
        case ASSIGN_FLOAT:
        case ASSIGN_INT:
        case ASSIGN_STRING: {
          // add assign so we can avoid recreating future 
          // (true b/c this instruction closes val immediately)
          ComputedValue assign = vanillaComputedValue(true);
          // add retrieve so we can avoid retrieving later
          Oparg dst = args.get(0);
          Oparg src = args.get(1);
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
        case DEREFERENCE_BLOB:
        case DEREFERENCE_BOOL:
        case DEREFERENCE_FLOAT:
        case DEREFERENCE_INT:
        case DEREFERENCE_STRING: {
          return Arrays.asList(vanillaComputedValue(false));
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
          Variable lookupRes = contents.getVariable();
          
          cv = makeArrayComputedValue(arr, ix, contents);
  
          if (op == Opcode.ARRAY_LOOKUP_IMM) {
            assert(lookupRes.getType().equals(
                Types.getArrayMemberType(arr.getSwiftType())));
            // This just retrieves the item immediately
            return Arrays.asList(cv);
          } else {
            assert (Types.isReferenceTo(lookupRes.getType(), 
                Types.getArrayMemberType(arr.getSwiftType())));
            Oparg prev = existing.get(new ComputedValue(Opcode.FAKE,
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
          Variable nestedArr = contents.getVariable();
          arr = args.get(1);
          ix = args.get(2);
          cv = makeArrayComputedValue(arr, ix, contents);
          if (op == Opcode.ARRAY_CREATE_NESTED_IMM) {
            // No references involved, the instruction returns the nested
            // array directly
            return Arrays.asList(cv);
          } else {
            Oparg prev = existing.get(new ComputedValue(Opcode.FAKE,
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

    private ComputedValue makeArrayComputedValue(Oparg arr, Oparg ix, Oparg contents) {
      ComputedValue cv;
      if (isMemberReference(contents.getVariable(),
          arr.getVariable())) {
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
     * @throws ParserRuntimeException if member can't be a member or ref to 
     *                                      member of array
     */
    private boolean isMemberReference(Variable member, Variable arr) 
            throws ParserRuntimeException{
      SwiftType memberType = Types.getArrayMemberType(arr.getType());
      if (memberType.equals(member.getType())) {
        return false;
      } else if (Types.isReferenceTo(member.getType(), memberType)) {
        return true;
      }
      throw new ParserRuntimeException("Inconsistent types in IC instruction:"
          + this.toString() + " array of type " + arr.getType() 
          + " with member of type " + member.getType());
    }

    @Override
    public Instruction clone() {
      return new TurbineOp(op, Oparg.cloneList(args));
    }
  
  }
  
  public static class FunctionCallInstruction extends Instruction {
    private final List<Variable> outputs;
    private final List<Variable> inputs;
    private final List<Boolean> closedInputs; // which inputs are closed
    private final String functionName;
    private final Oparg priority;
  
    private FunctionCallInstruction(Opcode op, String functionName,
        List<Variable> inputs, List<Variable> outputs, Oparg priority) {
      super(op);
      if (op != Opcode.CALL_BUILTIN && op != Opcode.CALL_COMPOSITE &&
          op != Opcode.CALL_APP && op != Opcode.CALL_COMPOSITE_SYNC) {
        throw new ParserRuntimeException("Tried to create function call"
            + " instruction with invalid opcode");
      }
      this.functionName = functionName;
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
  
    public static FunctionCallInstruction createAppCall(
        String functionName, List<Variable> inputs, List<Variable> outputs,
        Oparg priority) {
      return new FunctionCallInstruction(Opcode.CALL_APP, functionName,
          inputs, outputs, priority);
    }
  
    public static FunctionCallInstruction createCompositeCall(
        String functionName, List<Variable> inputs, List<Variable> outputs,
        boolean async, Oparg priority) {
      Opcode op;
      if (async) {
        op = Opcode.CALL_COMPOSITE;
      } else {
        op = Opcode.CALL_COMPOSITE_SYNC;
      }
      return new FunctionCallInstruction(op, functionName,
          inputs, outputs, priority);
    }
  
    public static FunctionCallInstruction createBuiltinCall(
        String functionName, List<Variable> inputs, List<Variable> outputs,
        Oparg priority) {
      return new FunctionCallInstruction(Opcode.CALL_BUILTIN, functionName,
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
      case CALL_APP:
        gen.appFunctionCall(functionName, inputs, outputs, priority);
        break;
      case CALL_BUILTIN:
        gen.builtinFunctionCall(functionName, inputs, outputs, priority);
        break;
      case CALL_COMPOSITE_SYNC:
      case CALL_COMPOSITE:
        boolean async = (op == Opcode.CALL_COMPOSITE);
        List<Boolean> blocking = info.getBlockingInputVector(functionName);
        assert(blocking != null && blocking.size() == inputs.size());
        List<Boolean> needToBlock = new ArrayList<Boolean>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
          needToBlock.add(blocking.get(i) && (!this.closedInputs.get(i)));
        }
        gen.compositeFunctionCall(functionName, inputs, outputs, needToBlock,
                                            async, priority);
        break;
      default:
        throw new ParserRuntimeException("Huh?");
      }
    }
  
    @Override
    public void renameVars(Map<String, Oparg> renames) {
      ICUtil.replaceVarsInList2(renames, outputs, false);
      ICUtil.replaceVarsInList2(renames, inputs, false);
    }
  
    public String getFunctionName() {
      return this.functionName;
    }
    @Override
    public void renameInputs(Map<String, Oparg> renames) {
      ICUtil.replaceVarsInList2(renames, inputs, false);
    }
  
    @Override
    public List<Oparg> getInputs() {
      List<Oparg> inputVars = Oparg.fromVarList(inputs);
      if (priority != null) {
        inputVars.add(priority);
      }
      return inputVars;
    }
  
    @Override
    public List<Oparg> getOutputs() {
      return Oparg.fromVarList(outputs);
    }
  
    /** Names of built-ins which don't have side effects */
    private static HashSet<String> sideEffectFree =
                                            new HashSet<String>();
    
    /** Names of built-ins which have a local equivalent operation */
    private static HashMap<String, ArithOpcode>
              localEquivalents = new HashMap<String, ArithOpcode>();
    
    
    /** Built-ins which are known to be deterministic */
    private static HashSet<String> knownDeterministic = 
                                        new HashSet<String>();

    /** Built-ins which are explicitly random */
    private static HashSet<String> randomFunctions = 
                          new HashSet<String>();
    
    /** Built-ins which are known to be deterministic */
    private static HashSet<String> commutative = 
                                        new HashSet<String>();
    
    /** Built-ins which are equivalent to another with
     * reversed arguments.  Reverse arguments and swap
     * to another function name to get canoical version
     */
    private static HashMap<String, String> flippedOps = 
                            new HashMap<String, String>();
    
    /**
     * Functions which just copy value of input to output
     */
    private static HashSet<String> copyFunctions =  
                            new HashSet<String>();
    
    {  
      for (String numType: Arrays.asList("integer", "float")) {
        sideEffectFree.add("plus_" + numType);
        sideEffectFree.add("minus_" + numType);
        sideEffectFree.add("multiply_" + numType);
        sideEffectFree.add("divide_" + numType);
        sideEffectFree.add("negate_" + numType);
        sideEffectFree.add("gt_" + numType);
        sideEffectFree.add("gte_" + numType);
        sideEffectFree.add("lt_" + numType);
        sideEffectFree.add("lte_" + numType);
        sideEffectFree.add("max_" + numType);
        sideEffectFree.add("min_" + numType);
        sideEffectFree.add("abs_" + numType);
        sideEffectFree.add("pow_" + numType);
        
        // Commutative operators
        commutative.add("plus_" + numType);
        commutative.add("multiply_" + numType);
        commutative.add("max_" + numType);
        commutative.add("min_" + numType);
        
        // e.g a > b is same as b < a
        flippedOps.put("gt_" + numType, "lt_" + numType);
        flippedOps.put("gte_" + numType, "lte_" + numType);
      }
    
      for (String numType: Arrays.asList("integer", "float", 
                                        "string", "boolean")) {
        sideEffectFree.add("eq_" + numType);
        sideEffectFree.add("copy_" + numType);
        sideEffectFree.add("neq_" + numType);
        commutative.add("eq_" + numType);
        commutative.add("neq_" + numType);
        copyFunctions.add("copy_" + numType);
      }
      
      sideEffectFree.add("copy_void");
      copyFunctions.add("copy_void");
      sideEffectFree.add("make_void");
    
      sideEffectFree.add("strcat");
      sideEffectFree.add("substring");
    
      sideEffectFree.add("and");
      sideEffectFree.add("or");
      sideEffectFree.add("xor");      
      sideEffectFree.add("not");
      commutative.add("and");
      commutative.add("or");
      commutative.add("xor");
    
      sideEffectFree.add("toint");
      sideEffectFree.add("fromint");
      sideEffectFree.add("tofloat");
      sideEffectFree.add("fromfloat");
      sideEffectFree.add("round");
      sideEffectFree.add("floor");
      sideEffectFree.add("ceil");
      sideEffectFree.add("itof");
      sideEffectFree.add("exp");
      sideEffectFree.add("log");
      sideEffectFree.add("sqrt");
      sideEffectFree.add("is_nan");
      
      sideEffectFree.add("argc");
      sideEffectFree.add("argv_contains");
      sideEffectFree.add("argv");
      localEquivalents.put("argc", ArithOpcode.ARGC_GET);
      localEquivalents.put("argv_contains", ArithOpcode.ARGV_CONTAINS);
      localEquivalents.put("argv", ArithOpcode.ARGV_GET);
      
    
      localEquivalents.put("plus_integer", ArithOpcode.PLUS_INT);
      localEquivalents.put("minus_integer", ArithOpcode.MINUS_INT);
      localEquivalents.put("multiply_integer", ArithOpcode.MULT_INT);
      localEquivalents.put("divide_integer", ArithOpcode.DIV_INT);
      localEquivalents.put("mod_integer", ArithOpcode.MOD_INT);
      localEquivalents.put("negate_integer", ArithOpcode.NEGATE_INT);
      localEquivalents.put("max_integer", ArithOpcode.MAX_INT);
      localEquivalents.put("min_integer", ArithOpcode.MIN_INT);
      localEquivalents.put("abs_integer", ArithOpcode.ABS_INT);
      localEquivalents.put("pow_integer", ArithOpcode.POW_INT);
      
      localEquivalents.put("eq_integer", ArithOpcode.EQ_INT);
      localEquivalents.put("neq_integer", ArithOpcode.NEQ_INT);
      localEquivalents.put("lt_integer", ArithOpcode.LT_INT);
      localEquivalents.put("lte_integer", ArithOpcode.LTE_INT);
      localEquivalents.put("gt_integer", ArithOpcode.GT_INT);
      localEquivalents.put("gte_integer", ArithOpcode.GTE_INT);
      
      localEquivalents.put("plus_float", ArithOpcode.PLUS_FLOAT);
      localEquivalents.put("minus_float", ArithOpcode.MINUS_FLOAT);
      localEquivalents.put("multiply_float", ArithOpcode.MULT_FLOAT);
      localEquivalents.put("divide_float", ArithOpcode.DIV_FLOAT);
      localEquivalents.put("negate_float", ArithOpcode.NEGATE_FLOAT);
      localEquivalents.put("max_float", ArithOpcode.MAX_FLOAT);
      localEquivalents.put("min_float", ArithOpcode.MIN_FLOAT);
      localEquivalents.put("abs_float", ArithOpcode.ABS_FLOAT);
      localEquivalents.put("pow_float", ArithOpcode.POW_FLOAT);
      localEquivalents.put("is_nan", ArithOpcode.IS_NAN);
      
      localEquivalents.put("ceil", ArithOpcode.CEIL);
      localEquivalents.put("floor", ArithOpcode.FLOOR);
      localEquivalents.put("round", ArithOpcode.ROUND);
      localEquivalents.put("itof", ArithOpcode.INTTOFLOAT);
      localEquivalents.put("toint", ArithOpcode.STRTOINT);
      localEquivalents.put("fromint", ArithOpcode.INTTOSTR);
      localEquivalents.put("tofloat", ArithOpcode.STRTOFLOAT);
      localEquivalents.put("fromfloat", ArithOpcode.FLOATTOSTR);
      localEquivalents.put("exp", ArithOpcode.EXP);
      localEquivalents.put("log", ArithOpcode.LOG);
      localEquivalents.put("sqrt", ArithOpcode.SQRT);
      
      localEquivalents.put("eq_float", ArithOpcode.EQ_FLOAT);
      localEquivalents.put("neq_float", ArithOpcode.NEQ_FLOAT);
      localEquivalents.put("lt_float", ArithOpcode.LT_FLOAT);
      localEquivalents.put("lte_float", ArithOpcode.LTE_FLOAT);
      localEquivalents.put("gt_float", ArithOpcode.GT_FLOAT);
      localEquivalents.put("gte_float", ArithOpcode.GTE_FLOAT);      
      
      localEquivalents.put("eq_string", ArithOpcode.EQ_STRING);
      localEquivalents.put("neq_string", ArithOpcode.NEQ_STRING);
      localEquivalents.put("strcat", ArithOpcode.STRCAT);
      localEquivalents.put("substrict", ArithOpcode.SUBSTRING);
      
      localEquivalents.put("eq_bool", ArithOpcode.EQ_FLOAT);
      localEquivalents.put("neq_bool", ArithOpcode.NEQ_FLOAT);
      localEquivalents.put("and", ArithOpcode.AND);
      localEquivalents.put("or", ArithOpcode.OR);
      localEquivalents.put("xor", ArithOpcode.XOR);
      localEquivalents.put("not", ArithOpcode.NOT);
      
      localEquivalents.put("copy_integer", ArithOpcode.COPY_INT);
      localEquivalents.put("copy_float", ArithOpcode.COPY_FLOAT);
      localEquivalents.put("copy_string", ArithOpcode.COPY_STRING);
      localEquivalents.put("copy_boolean", ArithOpcode.COPY_BOOL);
      localEquivalents.put("copy_blob", ArithOpcode.COPY_BLOB);

      localEquivalents.put("assert", ArithOpcode.ASSERT);
      localEquivalents.put("assertEqual", ArithOpcode.ASSERT_EQ);
      localEquivalents.put("trace", ArithOpcode.TRACE);
      localEquivalents.put("printf", ArithOpcode.PRINTF);
      localEquivalents.put("sprintf", ArithOpcode.SPRINTF);
      
      // Random functions
      randomFunctions.add("random");
      randomFunctions.add("randint");
      localEquivalents.put("random", ArithOpcode.RANDOM);
      localEquivalents.put("randint", ArithOpcode.RAND_INT);
      
      // All local arith ops are deterministic aside from random ones
      knownDeterministic.addAll(localEquivalents.keySet());
      knownDeterministic.removeAll(randomFunctions);
      knownDeterministic.remove("trace");
      knownDeterministic.remove("assert");
      knownDeterministic.remove("assertEqual");
      knownDeterministic.remove("printf");
    }

    @Override
    public boolean hasSideEffects() {
      return (!sideEffectFree.contains(functionName)) ||
            this.writesAliasVar();
    }
  
    @Override
    public Map<String, Oparg> constantFold(String enclosingFnName,
                                  Map<String, Oparg> knownConstants) {
      if (! hasSideEffects() ) {

        List<Oparg> constInputs = new ArrayList<Oparg>(inputs.size());
        for (Variable iVar: inputs) {
          Oparg constVal = knownConstants.get(iVar.getName());
          // Track all, including nulls
          constInputs.add(constVal);
        }
        if (op == Opcode.CALL_BUILTIN && 
                          localEquivalents.containsKey(this.functionName)) {
          assert(outputs.size() == 1); // assume for now
          LocalArithOp.constantFold(localEquivalents.get(this.functionName),
              outputs.get(0).getName(), constInputs);
        }
      } else {
        if (this.op == Opcode.CALL_BUILTIN && (functionName.equals("assert")
            || functionName.equals("assertEqual"))) {
          final boolean checkFailed;
          String reason = null;
          if (functionName.equals("assert")) {
            Oparg cond = knownConstants.get(inputs.get(0).getName());
            if (cond != null) {
              checkFailed = !cond.getBoolLit();
              reason = "constant condition evaluated to false";
            } else {
              checkFailed = false;
            }
          } else {
            Oparg a1 = knownConstants.get(inputs.get(0).getName());
            Oparg a2 = knownConstants.get(inputs.get(0).getName());
            if (a1 != null && a2 != null) {
              checkFailed = !a1.equals(a2);
              reason = a1.toString() + " != " + a2.toString();  
              
            } else {
              checkFailed = false;
            }
          }
          if (checkFailed) {
            String errMessage;
            if (knownConstants.containsKey(inputs.get(1).getName())) {
              errMessage = knownConstants.get(inputs.get(1).getName())
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
        }
      }
      return null;
    }
  
    private boolean shortCircuitableFunction() {
      return op == Opcode.CALL_BUILTIN && (functionName.equals("and") 
                                            || functionName.equals("or"));
    }

    
    @Override
    public Instruction constantReplace(Map<String, Oparg> knownConstants) {
      // can replace short-circuitable operations with direct assignment
      if (shortCircuitableFunction()) {
        List<Oparg> constArgs = new ArrayList<Oparg>(2);
        List<Variable> varArgs = new ArrayList<Variable>(2);
        for (Variable in: inputs) {
          Oparg c = knownConstants.get(in.getName());
          if (c == null) {
            varArgs.add(in);
          } else {
            constArgs.add(c);
          }
        }
        if (constArgs.size() == 1) {
          boolean arg1 = constArgs.get(0).getBoolLit();
          // Change it to a copy: should make it easier to further optimize
          if ((functionName.equals("or") && !arg1) ||
              (functionName.equals("and") && arg1)) {
            return createBuiltinCall(Builtins.COPY_BOOLEAN, varArgs, outputs,
                    null);
          } 
        }
      }
      return null;
    }
    
    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars) {
      // See which arguments are closed
      boolean allClosed = true;
      for (int i = 0; i < this.inputs.size(); i++) {
        Variable in = this.inputs.get(i);
        if (closedVars.contains(in.getName())) {
          this.closedInputs.set(i, true);
        } else {
          allClosed = false;
        }
      }
      if (allClosed && localEquivalents.containsKey(this.functionName)) {
          // All args are closed!
          return new MakeImmRequest(
              Collections.unmodifiableList(this.outputs),
              Collections.unmodifiableList(this.inputs));

      }
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> outVars, 
                                        List<Oparg> values) {
      ArithOpcode newOp = localEquivalents.get(this.functionName);
      assert(newOp != null);
      assert(values.size() == inputs.size());
      
      if (outputs.size() == 1) {
        assert(Types.derefResultType(outputs.get(0).getType()).equals(
            outVars.get(0).getType()));
        return new MakeImmChange(
            new LocalArithOp(newOp, outVars.get(0), values));
      } else {
        assert(outputs.size() == 0);
        return new MakeImmChange(
            new LocalArithOp(newOp, null, values));
      }
    }

    @Override
    public List<Variable> getBlockingInputs() {
      List<Variable> blocksOn = new ArrayList<Variable>();
      if (op == Opcode.CALL_APP) {
        for (Variable v: inputs) {
          if (!Types.isScalarValue(v.getType())) {
            blocksOn.add(v);
          }
        }
      } else if (op == Opcode.CALL_BUILTIN) {
        for (Variable v: inputs) {
          if (Types.isScalarFuture(v.getType())
              || Types.isReference(v.getType())) {
            // TODO: this is a conservative idea of which ones
            // are set
            blocksOn.add(v);
          }
        }
      } else if (op == Opcode.CALL_COMPOSITE_SYNC) {
        // Can't block because we need to enter the function immediately
        return null;
      } else if (op == Opcode.CALL_COMPOSITE ) {
        //TODO: should see which arguments are blocking
        return null;
      }
      return blocksOn;
    }

    public boolean isDefinitelyDeterministic() {
      if (this.op == Opcode.CALL_COMPOSITE || this.op == Opcode.CALL_COMPOSITE_SYNC
          || this.op == Opcode.CALL_APP) {
        return false;
      } else if (knownDeterministic.contains(functionName)) {
        return true;
      } else {
        // Safe default
        return false;
      }
    }
    
    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Oparg> existing) {
      // TODO: make order of args invariant where possible
      if (this.isDefinitelyDeterministic()) {
        if (this.op == Opcode.CALL_BUILTIN && 
            copyFunctions.contains(functionName)) {
          // Handle copy as a special case
          assert(inputs.size() == 1 && outputs.size() == 1);
          return Collections.singletonList(
                ComputedValue.makeCopyCV(this.outputs.get(0),
                                         Oparg.createVar(this.inputs.get(0))));
          
        } else if (this.outputs.size() == 1) {
          boolean outputClosed = false; // safe assumption
          String canonicalFunctionName = this.functionName;
          List<Oparg> in = Oparg.fromVarList(this.inputs);
          if (commutative.contains(this.functionName)) {
            // put in canonical order
            Collections.sort(in);
          } else if (flippedOps.containsKey(this.functionName)) {
            // E.g. flip a > b to a < b
            canonicalFunctionName = flippedOps.get(this.functionName);
            Collections.reverse(in);
          }
            
          return Collections.singletonList(
              new ComputedValue(this.op, 
              canonicalFunctionName, in, 
              Oparg.createVar(this.outputs.get(0)), outputClosed));
        } else {
          // Not sure to do with multiple outputs
        }
      }
      return null;
    }

    @Override
    public Instruction clone() {
      // Variables are immutable so just need to clone lists
      return new FunctionCallInstruction(op, functionName, 
          new ArrayList<Variable>(inputs), new ArrayList<Variable>(outputs),
          priority);
    }
  }
  
  public static class LoopContinue extends Instruction {
    private final ArrayList<Variable> newLoopVars;
    private final ArrayList<Variable> usedVariables;
    private final ArrayList<Variable> registeredContainers;
    private final ArrayList<Boolean> blockingVars;
  
    public LoopContinue(List<Variable> newLoopVars, 
                        List<Variable> usedVariables,
                        List<Variable> registeredContainers,
                        List<Boolean> blockingVars) {
      super(Opcode.LOOP_CONTINUE);
      this.newLoopVars = new ArrayList<Variable>(newLoopVars);
      this.usedVariables = new ArrayList<Variable>(usedVariables);
      this.registeredContainers = 
                      new ArrayList<Variable>(registeredContainers);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
    }
  
    @Override
    public void renameVars(Map<String, Oparg> renames) {
      ICUtil.replaceVarsInList2(renames, newLoopVars, false);
      ICUtil.replaceVarsInList2(renames, usedVariables, true);
      ICUtil.replaceVarsInList2(renames, registeredContainers, true);
    }
    
    @Override
    public void renameInputs(Map<String, Oparg> renames) {
      ICUtil.replaceVarsInList2(renames, newLoopVars, false);
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      assert(!removeVars.contains(newLoopVars.get(0).getName()));
      ICUtil.removeVarsInList(usedVariables, removeVars);
      ICUtil.removeVarsInList(registeredContainers, removeVars);
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
                                      this.registeredContainers,
                                      this.blockingVars);
    }
  
    @Override
    public List<Oparg> getInputs() {
      // need to make sure that these variables are avail in scope
      ArrayList<Oparg> res = new ArrayList<Oparg>(newLoopVars.size());
      for (Variable v: newLoopVars) {
        res.add(Oparg.createVar(v));
      }
      return res;
    }
  
    @Override
    public List<Oparg> getOutputs() {
      // No outputs
      return new ArrayList<Oparg>();
    }
  
    @Override
    public boolean hasSideEffects() {
      return true;
    }
  
    @Override
    public Map<String, Oparg> constantFold(String fnName,
              Map<String, Oparg> knownConstants) {
      // don't think I can do this?
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Oparg> knownConstants) {
      // don't think I can do this?
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars) {
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
    public MakeImmChange makeImmediate(List<Variable> out, List<Oparg> values) {
      throw new ParserRuntimeException("Not valid on loop continue!");
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
                        Map<ComputedValue, Oparg> existing) {
      // Nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopContinue(new ArrayList<Variable>(newLoopVars), 
          new ArrayList<Variable>(usedVariables), 
          new ArrayList<Variable>(registeredContainers), 
          new ArrayList<Boolean>(blockingVars));
    }
  }
  
  public static class LoopBreak extends Instruction {
    private final List<Variable> containersToClose;
  
    public LoopBreak(List<Variable> containersToClose) {
      super(Opcode.LOOP_BREAK);
      this.containersToClose = containersToClose;
    }
  
    @Override
    public void renameVars(Map<String, Oparg> renames) {
      ICUtil.replaceVarsInList2(renames, containersToClose, true);
    }
  
    @Override
    public void renameInputs(Map<String, Oparg> replacements) {
      // do nothing
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.op.toString().toLowerCase());
      for (Variable v: this.containersToClose) {
        sb.append(' ');
        sb.append(v.getName());
      }
      return sb.toString();
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.loopBreak(containersToClose);
    }
  
    @Override
    public List<Oparg> getInputs() {
      return new ArrayList<Oparg>(0);
    }
  
    @Override
    public List<Oparg> getOutputs() {
      return new ArrayList<Oparg>(0);
    }
  
    @Override
    public boolean hasSideEffects() {
      return true;
    }
  
    @Override
    public Map<String, Oparg> constantFold(String fnName,
                Map<String, Oparg> knownConstants) {
      // don't think I can do this?
      return null;
    }
  
    @Override
    public Instruction constantReplace(Map<String, Oparg> knownConstants) {
      // don't think I can do this?
      return null;
    }

    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars) {
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> out, List<Oparg> values) {
      throw new ParserRuntimeException("Not valid on loop continue!");
    }

    @Override
    public List<Variable> getBlockingInputs() {
      return null;
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Oparg> existing) {
      // nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopBreak(new ArrayList<Variable>(containersToClose));
    }
  }
  
  public static enum Opcode {
    FAKE, // Used for ComputedValue if there isn't a real opcode
    COMMENT,
    CALL_BUILTIN, CALL_COMPOSITE, CALL_APP, CALL_COMPOSITE_SYNC,
    DEREFERENCE_INT, DEREFERENCE_STRING, ADDRESS_OF, DEREFERENCE_FLOAT,
    DEREFERENCE_BOOL, DEREFERENCE_BLOB,
    ASSIGN_INT, ASSIGN_STRING, ASSIGN_FLOAT, ASSIGN_BOOL,
    RETRIEVE_INT, RETRIEVE_STRING, RETRIEVE_FLOAT, RETRIEVE_BOOL,
    RETRIEVE_REF,
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
    LOCAL_ARITH_OP,
    INIT_UPDATEABLE_FLOAT, UPDATE_MIN, UPDATE_INCR, UPDATE_SCALE, LATEST_VALUE,
    UPDATE_MIN_IMM, UPDATE_INCR_IMM, UPDATE_SCALE_IMM,
  }
  
  /** Keep track of which of the above functions are randomized or have
   * side effects, so we don't optimized these things out
   */
  private static HashSet<ArithOpcode> sideeffectLocalOps
            = new HashSet<ArithOpcode>();
  static {
    sideeffectLocalOps.add(ArithOpcode.RANDOM);
    sideeffectLocalOps.add(ArithOpcode.RAND_INT);
    sideeffectLocalOps.add(ArithOpcode.TRACE);
    sideeffectLocalOps.add(ArithOpcode.ASSERT);
    sideeffectLocalOps.add(ArithOpcode.ASSERT_EQ);
    sideeffectLocalOps.add(ArithOpcode.PRINTF);
    sideeffectLocalOps.add(ArithOpcode.SPRINTF);
  }
  public static class LocalArithOp extends Instruction {
    public final ArithOpcode localop;
    
    // First arg is always result, others are inputs
    private Variable output; // null if no output
    private List<Oparg> inputs;

    public LocalArithOp(ArithOpcode localop, Variable output, 
        Oparg input) {
      this(localop, output, Arrays.asList(input));
    }
    public LocalArithOp(ArithOpcode localop, Variable output, 
          List<Oparg> inputs) {
      super(Opcode.LOCAL_ARITH_OP);
      this.localop = localop;
      this.output = output;
      this.inputs = new ArrayList<Oparg>(inputs);
    }

    private static boolean shortCircuitable(ArithOpcode op) {
      return op == ArithOpcode.AND || op == ArithOpcode.OR;
    }
    
    public static Map<String, Oparg> 
        constantFold(ArithOpcode op, String outVarName,
            List<Oparg> constInputs) {
      if (shortCircuitable(op)) {
        // TODO: could short-circuit e.g. x * 0 or x ** 0 or x - x
        return constFoldShortCircuit(op, outVarName, constInputs);
      } else {
        /* we need all arguments to constant fold */
        boolean allInt = true;
        boolean allFloat = true;
        boolean allString = true;
        boolean allBool = true;
        // Check that there are no nulls
        for (Oparg in: constInputs) {
          if (in == null) {
            return null;
          }
          allInt = allInt && in.getType() == OpargType.INTVAL;
          allFloat = allFloat && in.getType() == OpargType.FLOATVAL;
          allString = allString && in.getType() == OpargType.STRINGVAL;
          allBool = allBool && in.getType() == OpargType.BOOLVAL;
        }

        if (allInt) {
          return constantFoldIntOp(op, outVarName, constInputs);
        } else if (allFloat) {
          return constantFoldFloatOp(op, outVarName, constInputs);
        } else if (allString) {
          return constantFoldStringOp(op, outVarName, constInputs);
        } else if (allBool) {
          return constantFoldBoolOp(op, outVarName, constInputs);
        } else if (op == ArithOpcode.SUBSTRING) {
          String str = constInputs.get(0).getStringLit();
          long start = constInputs.get(1).getIntLit();
          long len = constInputs.get(2).getIntLit();
          long end = Math.min(start + len, str.length());

          HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
          newConsts.put(outVarName, Oparg.createStringLit(
              str.substring((int)start, (int)(end))));
          return newConsts;
        }
      }
      return null;
    }

    /**
     * Constant folding for short-circuitable operations where we don't always
     * need to know both arguments to evaluate
     * @param constArgs unknown args are null
     * @return
     */
    private static Map<String, Oparg> constFoldShortCircuit(
        ArithOpcode op, String outVarName, List<Oparg> constArgs) {
      List<Oparg> constInputs = new ArrayList<Oparg>(2);
      for (Oparg in: constArgs) {
        if (in != null) {
          assert(in.getType() == OpargType.BOOLVAL);
          constInputs.add(in);
        }
      }
      if (constInputs.size() >= 1) { 
        boolean arg1 = constInputs.get(0).getBoolLit();
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        if (constInputs.size() == 2) {
          // Can directly evaluate
          boolean arg2 = constInputs.get(1).getBoolLit();
          if (op == ArithOpcode.OR) {
            newConsts.put(outVarName, Oparg.createBoolLit(arg1 || arg2 ));
            return newConsts;
          } else if (op == ArithOpcode.AND) {
            newConsts.put(outVarName, Oparg.createBoolLit(arg1 && arg2));
            return newConsts;
          }
        } else if (constInputs.size() == 1) {
          // see if we can short-circuit
          if (op == ArithOpcode.AND && arg1) {
            newConsts.put(outVarName, Oparg.createBoolLit(true));
            return newConsts;
          } else if (op == ArithOpcode.OR && !arg1) {
            newConsts.put(outVarName, Oparg.createBoolLit(false));
            return newConsts;
          }
        }
      }
      return null;
    }
  
    private static Map<String, Oparg> constantFoldStringOp(ArithOpcode op,
                      String outVar, List<Oparg> constInputs) {
      if (op == ArithOpcode.STRCAT) {
        // Strcat can take multiple arguments
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        StringBuilder sb = new StringBuilder();
        for (Oparg oa: constInputs) {
          sb.append(oa.getStringLit());
        }
        newConsts.put(outVar, Oparg.createStringLit(sb.toString()));
        return newConsts;
      } else 
      if (constInputs.size() == 1) {
        String arg1 = constInputs.get(0).getStringLit();
        if (op == ArithOpcode.COPY_STRING) {
          HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
          newConsts.put(outVar,
                Oparg.createStringLit(arg1));
          return newConsts;
        }
      } else if (constInputs.size() == 2) {
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        String arg1 = constInputs.get(0).getStringLit();
        String arg2 = constInputs.get(1).getStringLit();
        if (op == ArithOpcode.EQ_STRING) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1.equals(arg2)));
          return newConsts;
        } else if (op == ArithOpcode.NEQ_STRING) {
          newConsts.put(outVar, Oparg.createBoolLit(!arg1.equals(arg2)));
          return newConsts;
        }
      }
      return null;
    }
  
    private static Map<String, Oparg> constantFoldFloatOp(ArithOpcode op,
        String outVar, List<Oparg> constInputs) {
      if (constInputs.size() == 1) {
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        double arg1 = constInputs.get(0).getFloatLit();
        if (op == ArithOpcode.COPY_FLOAT) {
          newConsts.put(outVar, Oparg.createFloatLit(arg1));
        } else if (op == ArithOpcode.ABS_FLOAT) {
           newConsts.put(outVar, Oparg.createFloatLit(Math.abs(arg1)));
        } else if (op == ArithOpcode.EXP) {
          newConsts.put(outVar, Oparg.createFloatLit(Math.exp(arg1)));
        } else if (op == ArithOpcode.LOG) {
          newConsts.put(outVar, Oparg.createFloatLit(Math.log(arg1)));
        } else if (op == ArithOpcode.SQRT) {
          newConsts.put(outVar, Oparg.createFloatLit(Math.sqrt(arg1)));
        } else if (op == ArithOpcode.ROUND) {
          newConsts.put(outVar, Oparg.createIntLit(Math.round(arg1)));
        } else if (op == ArithOpcode.CEIL) {
          newConsts.put(outVar, Oparg.createIntLit((long)Math.ceil(arg1)));
        } else if (op == ArithOpcode.FLOOR) {
          newConsts.put(outVar, Oparg.createIntLit((long)Math.floor(arg1)));
        } else if (op == ArithOpcode.FLOATTOSTR) {
          //TODO: format might not be consistent with TCL
          newConsts.put(outVar, Oparg.createStringLit(Double.toString(arg1)));
        } else if (op == ArithOpcode.IS_NAN) {
          newConsts.put(outVar, Oparg.createBoolLit(Double.isNaN(arg1)));
        } else {
          return null;
        }
        return newConsts;
      } else if (constInputs.size() == 2) {
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        double arg1 = constInputs.get(0).getFloatLit();
        double arg2 = constInputs.get(1).getFloatLit();
        if (op == ArithOpcode.PLUS_FLOAT) {
          newConsts.put(outVar,  Oparg.createFloatLit(arg1 + arg2));
        } else if (op == ArithOpcode.MINUS_FLOAT) {
          newConsts.put(outVar,  Oparg.createFloatLit(arg1 - arg2));
        } else if (op == ArithOpcode.MULT_FLOAT) {
          newConsts.put(outVar, Oparg.createFloatLit(arg1 * arg2));
        } else if (op == ArithOpcode.EQ_FLOAT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 == arg2));
        } else if (op == ArithOpcode.NEQ_FLOAT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 != arg2));
        } else if (op == ArithOpcode.GT_FLOAT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 > arg2));
        } else if (op == ArithOpcode.GTE_FLOAT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 >= arg2));
        } else if (op == ArithOpcode.LT_FLOAT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 < arg2));
        } else if (op == ArithOpcode.LTE_FLOAT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 <= arg2));
        } else if (op == ArithOpcode.MAX_FLOAT) {
          newConsts.put(outVar, Oparg.createFloatLit(Math.max(arg1,arg2)));
        } else if (op == ArithOpcode.MIN_FLOAT) {
          newConsts.put(outVar, Oparg.createFloatLit(Math.min(arg1,arg2)));
        } else if (op == ArithOpcode.POW_FLOAT) {
          newConsts.put(outVar, Oparg.createFloatLit(Math.pow(arg1,arg2)));
        } else {
          return null;
        }
        return newConsts;
      } else {
        return null;
      }
    }
  
    private static Map<String, Oparg> constantFoldIntOp(ArithOpcode op,
        String outVar, List<Oparg> constInputs) {
      if (constInputs.size() == 1) {
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        long arg1 = constInputs.get(0).getIntLit();
        if (op == ArithOpcode.COPY_INT) {
          newConsts.put(outVar, Oparg.createIntLit(arg1));
        } else if (op == ArithOpcode.ABS_INT) {
          newConsts.put(outVar, Oparg.createIntLit(Math.abs(arg1)));
        } else if (op == ArithOpcode.NEGATE_INT) {
          newConsts.put(outVar, Oparg.createIntLit(0 - arg1));
        } else if (op == ArithOpcode.INTTOFLOAT) {
          newConsts.put(outVar, Oparg.createFloatLit(arg1));
        } else if (op == ArithOpcode.INTTOSTR) {
          newConsts.put(outVar, Oparg.createStringLit(Long.toString(arg1)));
        } else {
          return null;
        }
        return newConsts;
      } else if (constInputs.size() == 2) {
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        long arg1 = constInputs.get(0).getIntLit();
        long arg2 = constInputs.get(1).getIntLit();
        if (op == ArithOpcode.PLUS_INT) {
          newConsts.put(outVar, Oparg.createIntLit(arg1 + arg2));
        } else if (op == ArithOpcode.MINUS_INT) {
          newConsts.put(outVar, Oparg.createIntLit(arg1 - arg2));
        } else if (op == ArithOpcode.MULT_INT) {
          newConsts.put(outVar, Oparg.createIntLit(arg1 * arg2));
        } else if (op == ArithOpcode.DIV_INT) {
          newConsts.put(outVar, Oparg.createIntLit(arg1 / arg2));
        } else if (op == ArithOpcode.MOD_INT) {
          newConsts.put(outVar, Oparg.createIntLit(arg1 % arg2));
        } else if (op == ArithOpcode.EQ_INT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 == arg2));
        } else if (op == ArithOpcode.NEQ_INT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 != arg2));
        } else if (op == ArithOpcode.GT_INT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 > arg2));
        } else if (op == ArithOpcode.GTE_INT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 >= arg2));
        } else if (op == ArithOpcode.LT_INT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 < arg2));
        } else if (op == ArithOpcode.LTE_INT) {
          newConsts.put(outVar, Oparg.createBoolLit(arg1 <= arg2));
        } else if (op == ArithOpcode.MAX_INT) {
          newConsts.put(outVar, Oparg.createIntLit(Math.max(arg1,arg2)));
        } else if (op == ArithOpcode.MIN_INT) {
          newConsts.put(outVar, Oparg.createIntLit(Math.min(arg1,arg2)));
        } else if (op == ArithOpcode.POW_INT) {
          newConsts.put(outVar, Oparg.createFloatLit(Math.pow((double)arg1,
                                                          (double)arg2)));
        } else {
          return null;
        }
        return newConsts;
      } else {
        return null;
      }
    }
  
    private static Map<String, Oparg> constantFoldBoolOp(ArithOpcode op,
        String outVar, List<Oparg> constInputs) {
      if (constInputs.size() == 1) {
        HashMap<String, Oparg> newConsts = new HashMap<String, Oparg>(1);
        boolean arg1 = constInputs.get(0).getBoolLit();
        if (op == ArithOpcode.NOT) {
          newConsts.put(outVar, Oparg.createBoolLit(!arg1));
        } else {
          return null;
        }
        return newConsts;
      } else {
        // AND and OR are handled as shortcircuitable functions
        return null;
      }
    }

    
    @Override
    public void renameVars(Map<String, Oparg> renames) {
      if (output != null && renames.containsKey(this.output.getName())) {
        this.output = renames.get(this.output.getName()).getVariable();
      }
      ICUtil.replaceOpargsInList2(renames, inputs);
    }

    @Override
    public void renameInputs(Map<String, Oparg> renames) {
      ICUtil.replaceOpargsInList2(renames, inputs);
    }

    @Override
    public String toString() {
      String res = op.toString().toLowerCase() + " ";
      if (output != null) {
        res +=  output.getName() + " = ";
      }
      res += localop.toString().toLowerCase();
      for (Oparg input: inputs) {
        res += " " + input.toString();
      }
      return res;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.localArithOp(localop, output, inputs);
    }

    @Override
    public List<Oparg> getInputs() {
      return Collections.unmodifiableList(inputs);
    }

    @Override
    public List<Oparg> getOutputs() {
      if (output != null) {
        return Arrays.asList(Oparg.createVar(output));
      } else {
        return new ArrayList<Oparg>(0);
      }
    }

    @Override
    public boolean hasSideEffects() {
      return sideeffectLocalOps.contains(localop);
    }

    @Override
    public Map<String, Oparg> constantFold(String fnName,
                          Map<String, Oparg> knownConstants) {
      if (this.output == null) {
        return null;
      } else if (isValueCopy(this)) {
        assert(inputs.size() == 1);
        if (inputs.get(0).isConstant()) {
          // Already just assigning a constant, can't do any folding
          return null;
        }
      }
      // List of constant values for inputs, null if input not const
      ArrayList<Oparg> constInputs = new ArrayList<Oparg>(inputs.size());
      /* First try to replace arguments with constants */
      for (int i = 0; i < inputs.size(); i++) {
        Oparg in = inputs.get(i);
        if (in.getType() == OpargType.VAR) {
          Oparg c = knownConstants.get(in.getVariable().getName());
          constInputs.add(c);
          if (c != null) {
            // replace arg with constant
            inputs.set(i, c);
          }
        } else {
          constInputs.add(in);
        }
      }
      return LocalArithOp.constantFold(this.localop, this.output.getName(),
          constInputs);
    }

    @Override
    public Instruction constantReplace(Map<String, Oparg> knownConstants) {
      // Everything done in constant fold
      return null;
    }
    
    @Override
    public MakeImmRequest canMakeImmediate(Set<String> closedVars) {
      // already is immediate
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Variable> out, List<Oparg> values) {
      throw new ParserRuntimeException("Already immediate!");
    }

    @Override
    public List<Variable> getBlockingInputs() {
      // doesn't take futures as args
      return null;
    }

    @Override
    public List<ComputedValue> getComputedValues(
                        Map<ComputedValue, Oparg> existing) {
      if (this.hasSideEffects()) {
        // Two invocations of this aren't equivalent
        return null;
      } else {
        if (localop == ArithOpcode.COPY_INT || localop == ArithOpcode.COPY_BOOL
            || localop == ArithOpcode.COPY_FLOAT || localop == ArithOpcode.COPY_STRING
            || localop == ArithOpcode.COPY_BLOB) {
          // It might be assigning a constant val
          return Collections.singletonList(ComputedValue.makeCopyCV(
                this.output, this.inputs.get(0)));
        }
        

        if (output != null) {
          // TODO: make order of args invariant
          return Collections.singletonList(
              new ComputedValue(this.op, 
              this.localop.toString(),
              this.inputs, Oparg.createVar(this.output), true));
        } else {
          return null;
        }
      }
    }

    @Override
    public Instruction clone() {
      return new LocalArithOp(localop, output, 
          Oparg.cloneList(inputs));
    }
    
    public static boolean isValueCopy(Instruction inst) {
      if (inst instanceof LocalArithOp) {
        ArithOpcode aop = ((LocalArithOp) inst).localop;
        return aop == ArithOpcode.COPY_BLOB || aop == ArithOpcode.COPY_BOOL ||
            aop == ArithOpcode.COPY_FLOAT || aop == ArithOpcode.COPY_INT ||
            aop == ArithOpcode.COPY_STRING;
      }
      return false;
    }
  }
  
  
  
  public static enum OpargType {
    INTVAL, FLOATVAL,
    STRINGVAL, BOOLVAL,
    VAR
  }
  
  public static class Oparg implements Comparable<Oparg> {
    final OpargType type;
  
    /** Storage for arg, dependent on arg type */
    private String stringlit;
    private long intlit;
    private final double floatlit;
    private final boolean boollit;
    private Variable var;
  
    /** Private constructors so that it can only
     * be build using static builder methods (below)
     * @param type
     * @param stringval
     */
    private Oparg(OpargType type, String stringlit, Variable var, long intlit,
        double floatlit, boolean boollit) {
      super();
      this.type = type;
      this.stringlit = stringlit;
      this.intlit = intlit;
      this.floatlit = floatlit;
      this.boollit = boollit;
      this.var = var;
    }
    
    public static List<Oparg> cloneList(List<Oparg> inputs) {
      ArrayList<Oparg> res = new ArrayList<Oparg>(inputs.size());
      for (Oparg i: inputs) {
        res.add(i.clone());
      }
      return res;
    }

    public Oparg clone() {
      return new Oparg(type, stringlit, var, intlit, floatlit, boollit);
    }
  
    public static Oparg createIntLit(long v) {
      return new Oparg(OpargType.INTVAL, null, null, v, -1, false);
    }
  
    public static Oparg createFloatLit(double v) {
      return new Oparg(OpargType.FLOATVAL, null, null, -1, v, false);
    }
  
    public static Oparg createStringLit(String v) {
      assert(v != null);
      return new Oparg(OpargType.STRINGVAL, v, null, -1, -1, false);
    }
    
    public static Oparg createBoolLit(boolean v) {
      return new Oparg(OpargType.BOOLVAL, null, null, -1, -1, v);
    }
  
    public static Oparg createVar(Variable var) {
      assert(var != null);
      return new Oparg(OpargType.VAR, null, var, -1, -1, false);
    }
  
    public OpargType getType() {
      return type;
    }
  
    public String getStringLit() {
      if (type == OpargType.STRINGVAL) {
        return stringlit;
      } else {
        throw new ParserRuntimeException("getStringVal for non-string type");
      }
    }
  
    public long getIntLit() {
      if (type == OpargType.INTVAL) {
        return intlit;
      } else {
        throw new ParserRuntimeException("getIntVal for non-int type");
      }
    }
  
    public double getFloatLit() {
      if (type == OpargType.FLOATVAL) {
        return floatlit;
      } else {
        throw new ParserRuntimeException("getFloatVal for non-float type");
      }
    }
  
    public boolean getBoolLit() {
      if (type == OpargType.BOOLVAL) {
        return boollit;
      } else {
        throw new ParserRuntimeException("getBoolLit for non-bool type");
      }
    }
    
    public Variable getVariable() {
      if (type == OpargType.VAR) {
        return var;
      } else {
        throw new ParserRuntimeException("getVariable for non-variable type");
      }
    }
  
    public void replaceVariable(Variable var) {
      if (type == OpargType.VAR) {
        this.var = var;
      } else {
        throw new ParserRuntimeException(
              "replaceVariable for non-variable type");
      }
    }
  
    public SwiftType getSwiftType() {
      switch (type) {
      case INTVAL:
        return Types.FUTURE_INTEGER;
      case STRINGVAL:
        // use same escaping as TCL
        return Types.FUTURE_STRING;
      case FLOATVAL:
        return Types.FUTURE_FLOAT;
      case BOOLVAL:
        return Types.FUTURE_BOOLEAN;
      case VAR:
        return this.var.getType();
      default:
        throw new ParserRuntimeException("Unknown oparg type "
            + this.type.toString());
      }
    }
    
    /**
     * Is the oparg an int that can be immediately read (i.e. either
     * a value or a literal.
     * @return
     */
    public boolean isImmediateInt() {
      return type == OpargType.INTVAL 
          || (type == OpargType.VAR && 
              var.getType().equals(Types.VALUE_INTEGER));
    }
    
    public boolean isImmediateFloat() {
      return type == OpargType.FLOATVAL 
          || (type == OpargType.VAR && 
              var.getType().equals(Types.VALUE_FLOAT));
    }
    
    public boolean isImmediateString() {
      return type == OpargType.STRINGVAL 
          || (type == OpargType.VAR && 
              var.getType().equals(Types.VALUE_STRING));
    }
    
    public boolean isImmediateBool() {
      return type == OpargType.BOOLVAL 
          || (type == OpargType.VAR && 
              var.getType().equals(Types.VALUE_BOOLEAN));
    }
    
    @Override
    public String toString() {
      switch (type) {
      case INTVAL:
        return Long.toString(this.intlit);
      case STRINGVAL:
        // use same escaping as TCL
        return "\"" +  TclString.tclEscapeString(this.stringlit) + "\"";
      case FLOATVAL:
        return Double.toString(this.floatlit);
      case BOOLVAL:
        return Boolean.toString(this.boollit);
      case VAR:
        return this.var.getName();
      default:
        throw new ParserRuntimeException("Unknown oparg type "
            + this.type.toString());
      }
    }
    
    /**
     * Define hashCode and equals so this can be used as key in hash table
     */
    @Override
    public int hashCode() {
      int hash1;
      switch (type) {
      case INTVAL:
        hash1 = ((Long)this.intlit).hashCode();
        break;
      case STRINGVAL:
        hash1 = this.stringlit.hashCode();
        break;
      case FLOATVAL:
        hash1 =  ((Double)this.floatlit).hashCode();
        break;
      case BOOLVAL:
        hash1 = this.boollit ? 0 : 1;
        break;
      case VAR:
        hash1 = this.var.getName().hashCode();
        break;
      default:
        throw new ParserRuntimeException("Unknown oparg type "
            + this.type.toString());
      }
      return this.type.hashCode() ^ hash1;
    }
    
    @Override
    public boolean equals(Object otherO) {
      if (!(otherO instanceof Oparg)) {
        throw new ParserRuntimeException("cannot compare oparg and "
            + otherO.getClass().getName());
      }
      Oparg other = (Oparg)otherO;
      if (this.type != other.type) {
        return false;
      }
      switch(this.type) {
      case INTVAL:
        return this.intlit == other.intlit;
      case STRINGVAL:
        return this.stringlit.equals(other.stringlit);
      case FLOATVAL:
        return this.floatlit == other.floatlit;
      case BOOLVAL:
        return this.boollit == other.boollit;
      case VAR:
        // Compare only on name, assuming name is unique
        return this.var.getName().equals(other.var.getName());
      default:
        throw new ParserRuntimeException("Unknown oparg type "
            + this.type.toString());
      }
    }
    
    @Override
    public int compareTo(Oparg o) {
      int typeComp = type.compareTo(o.type);
      if (typeComp == 0) {
        switch (type) {
        case BOOLVAL:
          return ((Boolean)boollit).compareTo(o.boollit);
        case INTVAL:
          return ((Long)intlit).compareTo(o.intlit);
        case FLOATVAL:
          return ((Double)floatlit).compareTo(o.floatlit);
        case STRINGVAL:
          return stringlit.compareTo(o.stringlit);
        case VAR:
          return var.getName().compareTo(o.getVariable().getName());
        default:
          throw new ParserRuntimeException("couldn't compare oparg type "
              + this.type.toString());
        }
      } else { 
        return typeComp;
      }
    }
    

    /**
     * Put all variable names in a collection of opargs into
     * addTo
     */
    public static void collectVarNames(Collection<String> addTo,
                Collection<Oparg> args) {
      for (Oparg o: args) {
        if (o.type == OpargType.VAR) {
          addTo.add(o.getVariable().getName());
        }
      }
    }

    public static List<String> varNameList(List<Oparg> inputs) {
      ArrayList<String> result = new ArrayList<String>();
      collectVarNames(result, inputs);
      return result;
    }

    public static List<Oparg> fromVarList(List<Variable> vars) {
      ArrayList<Oparg> result = new ArrayList<Oparg>(vars.size());
      for (Variable v: vars) {
        result.add(Oparg.createVar(v));
      }
      return result;
    }
    
    public boolean isConstant() {
      return this.type != OpargType.VAR;
    }

  }

  public static Instruction valueSet(Variable dst, Oparg value) {
    if (Types.isScalarValue(dst.getType())) {
      switch (dst.getType().getPrimitiveType()) {
      case BOOLEAN:
        assert(value.isImmediateBool());
        return new LocalArithOp(ArithOpcode.COPY_BOOL, dst, value);
      case INTEGER:
        assert(value.isImmediateInt());
        return new LocalArithOp(ArithOpcode.COPY_INT, dst, value);
      case FLOAT:
        assert(value.isImmediateFloat());
        return new LocalArithOp(ArithOpcode.COPY_FLOAT, dst, value);
      case STRING:
        assert(value.isImmediateString());
        return new LocalArithOp(ArithOpcode.COPY_STRING, dst, value);
      }
    } else if (Types.isArray(dst.getType()) || Types.isStruct(dst.getType())) {
      assert(dst.getStorage() == VariableStorage.ALIAS);
      assert (value.getType() == OpargType.VAR);
      return TurbineOp.copyRef(dst, value.getVariable());
    }

    throw new ParserRuntimeException("Unhandled case in valueSet: "
        + " assign " + value.toString() + " to " + dst.toString());
  }

  public static Instruction retrieveValueOf(Variable dst, Variable src) {
    assert(Types.isScalarValue(dst.getType()));
    assert(Types.isScalarFuture(src.getType()));
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
      throw new ParserRuntimeException("method to retrieve " +
      		src.getType().typeName() + " is not known yet");
    }
  }
  
  /**
   * Return the canonical ComputedValue representation for
   * retrieving the value of this type
   * @param srcType
   * @return null if cannot be retrieved
   */
  public static ComputedValue retrieveCompVal(Variable src) {
    SwiftType srcType = src.getType();
    Opcode op = retrieveOpcode(srcType);
    if (op == null) {
      return null;
    }
    return new ComputedValue(op, "", 
        Collections.singletonList(Oparg.createVar(src)));
  }

  public static ComputedValue assignComputedVal(Variable dst, Oparg val) {
    SwiftType dstType = dst.getType();
    if (Types.isScalarValue(dstType)) {
        ArithOpcode op;
        switch(dstType.getPrimitiveType()) {
        case BOOLEAN:
          op = ArithOpcode.COPY_BOOL;
          break;
        case INTEGER:
          op = ArithOpcode.COPY_INT;
          break;
        case FLOAT:
          op = ArithOpcode.COPY_FLOAT;
          break;
        case STRING:
          op = ArithOpcode.COPY_STRING;
          break;
        case BLOB:
          op = ArithOpcode.COPY_BLOB;
          break;
        default:
          throw new ParserRuntimeException("Unhandled type: "
              + dstType);
        }
        return new ComputedValue(Opcode.LOCAL_ARITH_OP, 
            op.toString(), Arrays.asList(val), Oparg.createVar(dst), false);
    } else {
      Opcode op = assignOpcode(dstType);
      if (op != null) {
        return new ComputedValue(op, "", Arrays.asList(val), Oparg.createVar(dst)
                                                                          , true);
      }
    }
    throw new ParserRuntimeException("DOn't know how to assign to " + dst);
  }

  private static Opcode assignOpcode(SwiftType dstType) {
    Opcode op = null;
    if (Types.isScalarFuture(dstType)) {
       switch(dstType.getPrimitiveType()) {
       case BOOLEAN:
         op = Opcode.ASSIGN_BOOL;
         break;
       case INTEGER:
         op = Opcode.ASSIGN_INT;
         break;
       case FLOAT:
         op = Opcode.ASSIGN_FLOAT;
         break;
       case STRING:
         op = Opcode.ASSIGN_STRING;
         break;
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
        op = Opcode.RETRIEVE_BOOL;
        break;
      case INTEGER:
        op = Opcode.RETRIEVE_INT;
        break;
      case FLOAT:
        op = Opcode.RETRIEVE_FLOAT;
        break;
      case STRING:
        op = Opcode.RETRIEVE_STRING;
        break;
      default:
        // Can't retrieve other types
        op = null;
      }

    } else if (Types.isReference(srcType)) {
      op = Opcode.RETRIEVE_REF;
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
          return Opcode.DEREFERENCE_BLOB;
        case BOOLEAN:
          return Opcode.DEREFERENCE_BOOL;
        case FLOAT:
          return Opcode.DEREFERENCE_FLOAT;
        case INTEGER:
          return Opcode.DEREFERENCE_INT;
        case STRING:
          return Opcode.DEREFERENCE_STRING;
        }
      }
    }
    return null;
  }


  public static Instruction futureSet(Variable dst, Oparg src) {
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
      throw new ParserRuntimeException("method to set " +
          dst.getType().typeName() + " is not known yet");
    }
  }
}

