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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.OpEvaluator;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
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
  
    /**
     * @return a short name for the operation used for human-readable
     *        diagnostics 
     */
    public String shortOpName() {
      return op.toString().toLowerCase();
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
     * @param renames
     */
    public abstract void renameInputs(Map<String, Arg> renames);

    @Override
    public abstract String toString();
  
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
  
    public boolean writesAliasVar() {
      // Writes to alias variables can have non-local effects
      for (Var out: this.getOutputs()) {
        if (out.storage() == VarStorage.ALIAS) {
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
      public final List<Var> out;
      public final List<Var> in;
      /** Where immediate code should run.  Default is local: in the current context */
      public final TaskMode mode;
      
      
      public MakeImmRequest(List<Var> out, List<Var> in) {
        this(out, in, TaskMode.LOCAL);
      }
      
      public MakeImmRequest(List<Var> out, List<Var> in, TaskMode mode) {
        this.out = out;
        this.in = in;
        this.mode = mode;
      }
    }
    
    public static class MakeImmChange {
      /** Optional: if the output variable of op changed */
      public final Var newOut;
      public final Var oldOut;
      public final Instruction newInst;
      
      /**
       * If the output variable changed from reference to plain future
       * @param newOut
       * @param oldOut
       * @param newInst
       */
      public MakeImmChange(Var newOut, Var oldOut, Instruction newInst) {
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
     * @param assumeAllClosed if true, allowed to (must don't necessarily
     *        have to) assume that all input vars are closed
     * @return null if it cannot be made immediate, if true,
     *            a list of vars that are the variables whose values are needed
     *            and output vars that need to be have value vars created
     */
    public abstract MakeImmRequest canMakeImmediate(Set<String> closedVars, 
                                                  boolean assumeAllClosed);

    public abstract MakeImmChange makeImmediate(List<Var> outVals,
                                                List<Arg> inValues);

    /**
     * @return the futures this instruction will block on
     *        it is ok if it forgets variables which aren't blocked on,
     *        but all variables returned must be blocked on
     */
    public abstract List<Var> getBlockingInputs();
    
    /**
     * Some instructions will spawn off asynchronous tasks
     * @return SYNC if nothing spawned, otherwise the variety of task spawned
     */
    public abstract TaskMode getMode();

    public boolean closesOutputs() {
      return false; // Default
    }
    
    /**
     * @return priority of task spawned, if any.  null if no spawn or
     *      default priority
     */
    public Arg getPriority() {
      return null;
    }
    
    public static interface CVMap {
      public Arg getLocation(ComputedValue val);
    }
    
    /**
     * @param existing already known values (sometimes needed to 
     *              work out which vales are created by an instruction)
     * @return a list of all values computed by expression.  Each ComputedValue
     *        returned should have the out field set so we know where to find 
     *        it 
     */
    public abstract List<ComputedValue> getComputedValues(CVMap existing);
   
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
    public List<Var> getOutputs() {
      return new ArrayList<Var>(0);
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
    public MakeImmChange makeImmediate(List<Var> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on comment!");
    }

    @Override
    public List<Var> getBlockingInputs() {
      return null;
    }

    @Override
    public List<ComputedValue> getComputedValues(CVMap existing) {
      return null;
    }

    @Override
    public Instruction clone() {
      return new Comment(this.text);
    }

    @Override
    public TaskMode getMode() {
      // TODO: doesn't really make sense
      return TaskMode.SYNC;
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
      case STORE_VOID:
        gen.assignVoid(args.get(0).getVar(), args.get(1));
        break;
      case STORE_FLOAT:
        gen.assignFloat(args.get(0).getVar(), args.get(1));
        break;
      case STORE_STRING:
        gen.assignString(args.get(0).getVar(), args.get(1));
        break;
      case STORE_BLOB:
        gen.assignBlob(args.get(0).getVar(), args.get(1));
        break;
      case STORE_FILE:
        gen.assignFile(args.get(0).getVar(), args.get(1));
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
      case ARRAY_DECR_WRITERS:
        gen.decrWriters(args.get(0).getVar());
        break;
      case DECR_REF:
        gen.decrRef(args.get(0).getVar());
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
      case LOAD_VOID:
        gen.retrieveVoid(args.get(0).getVar(),
            args.get(1).getVar());
        break;
      case LOAD_FLOAT:
        gen.retrieveFloat(args.get(0).getVar(),
            args.get(1).getVar());
        break;  
      case LOAD_BLOB:
        gen.retrieveBlob(args.get(0).getVar(),
            args.get(1).getVar());
        break;
      case LOAD_FILE:
        gen.retrieveFile(args.get(0).getVar(),
            args.get(1).getVar());
        break;
      case DECR_BLOB_REF:
        gen.decrBlobRef(args.get(0).getVar());
        break;
      case FREE_BLOB:
        gen.freeBlob(args.get(0).getVar());
        break;
      case DECR_LOCAL_FILE_REF:
        gen.decrLocalFileRef(args.get(0).getVar());
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
        gen.getFileName(args.get(0).getVar(), args.get(1).getVar(), false);
        break;
      case GET_OUTPUT_FILENAME:
        gen.getFileName(args.get(0).getVar(), args.get(1).getVar(), true);
        break;
      default:
        throw new STCRuntimeError("didn't expect to see op " +
                  op.toString() + " here");
      }
  
    }
  
    public static TurbineOp arrayRefLookupFuture(Var oVar, Var arrayRefVar,
        Var indexVar) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_FUTURE,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayRefVar),
                                    Arg.createVar(indexVar)));
    }
  
    public static TurbineOp arrayLookupFuture(Var oVar, Var arrayVar,
        Var indexVar) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_FUTURE,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                                Arg.createVar(indexVar)));
    }
  
    public static Instruction arrayInsertFuture(Var iVar,
        Var arrayVar, Var indexVar) {
      return new TurbineOp(Opcode.ARRAY_INSERT_FUTURE,
          Arrays.asList(Arg.createVar(arrayVar), Arg.createVar(indexVar),
              Arg.createVar(iVar)));
    }
  
    public static Instruction arrayRefInsertFuture(Var iVar,
        Var arrayVar, Var indexVar, Var outerArrayVar) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_FUTURE,
          Arrays.asList(Arg.createVar(arrayVar), Arg.createVar(indexVar),
              Arg.createVar(iVar), Arg.createVar(outerArrayVar)));
    }
    
    public static Instruction arrayRefLookupImm(Var oVar,
        Var arrayVar, Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAYREF_LOOKUP_IMM,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                               arrayIndex));
    }
  
    public static Instruction arrayLookupRefImm(Var oVar, Var arrayVar,
        Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_REF_IMM,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                               arrayIndex));
    }
    
    public static Instruction arrayLookupImm(Var oVar, Var arrayVar,
        Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_LOOKUP_IMM,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(arrayVar),
                                               arrayIndex));
    }
  
    public static Instruction arrayInsertImm(Var iVar,
        Var arrayVar, Arg arrayIndex) {
      return new TurbineOp(Opcode.ARRAY_INSERT_IMM,
          Arrays.asList(Arg.createVar(arrayVar), arrayIndex,
                        Arg.createVar(iVar)));
    }
    
    public static Instruction arrayRefInsertImm(Var iVar,
        Var arrayVar, Arg arrayIndex, Var outerArray) {
      return new TurbineOp(Opcode.ARRAYREF_INSERT_IMM,
          Arrays.asList(Arg.createVar(arrayVar), arrayIndex,
                        Arg.createVar(iVar),
                        Arg.createVar(outerArray)));
    }
  
  
    public static Instruction structLookup(Var oVar, Var structVar,
                                                          String fieldName) {
      return new TurbineOp(Opcode.STRUCT_LOOKUP,
          Arrays.asList(Arg.createVar(oVar), Arg.createVar(structVar),
              Arg.createStringLit(fieldName)));
    }
    
    public static Instruction structRefLookup(Var oVar, Var structVar,
        String fieldName) {
      return new TurbineOp(Opcode.STRUCTREF_LOOKUP,
              Arrays.asList(Arg.createVar(oVar), Arg.createVar(structVar),
              Arg.createStringLit(fieldName)));
    }
  
    public static Instruction arrayDecrWriters(Var array) {
      return new TurbineOp(Opcode.ARRAY_DECR_WRITERS, 
              Arrays.asList(Arg.createVar(array)));
    }

    public static Instruction decrRef(Var var) {
      return new TurbineOp(Opcode.DECR_REF, 
              Arrays.asList(Arg.createVar(var)));
    }

    public static Instruction assignInt(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_INT,
          Arrays.asList(Arg.createVar(target), src));
    }

    public static Instruction assignBool(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_BOOL,
          Arrays.asList(Arg.createVar(target), src));
    }

    public static Instruction assignVoid(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_VOID,
              Arrays.asList(Arg.createVar(target), src));
    }
  
    public static Instruction assignFloat(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_FLOAT,
          Arrays.asList(Arg.createVar(target), src));
    }
  
    public static Instruction assignString(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_STRING,
          Arrays.asList(Arg.createVar(target), src));
    }
  
    public static Instruction assignBlob(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_BLOB,
          Arrays.asList(Arg.createVar(target), src));
    }
    
    public static Instruction assignFile(Var target, Arg src) {
      return new TurbineOp(Opcode.STORE_FILE,
          Arrays.asList(Arg.createVar(target), src));
    }

    public static Instruction retrieveString(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_STRING,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
  
    public static Instruction retrieveInt(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_INT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
  
    public static Instruction retrieveBool(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_BOOL,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }

    public static Instruction retrieveVoid(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_VOID,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
    
    public static Instruction retrieveFloat(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_FLOAT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
    
    public static Instruction retrieveBlob(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_BLOB,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
  
    public static Instruction retrieveFile(Var target, Var source) {
      return new TurbineOp(Opcode.LOAD_FILE,
          Arrays.asList(Arg.createVar(target), Arg.createVar(source)));
    }
    
    public static Instruction decrBlobRef(Var blob) {
      return new TurbineOp(Opcode.DECR_BLOB_REF,
                            Arrays.asList(Arg.createVar(blob)));
    }
    
    public static Instruction freeBlob(Var blobVal) {
      return new TurbineOp(Opcode.FREE_BLOB, 
              Arrays.asList(Arg.createVar(blobVal)));
    }

    public static Instruction decrLocalFileRef(Var fileVal) {
      return new TurbineOp(Opcode.DECR_LOCAL_FILE_REF,
              Arrays.asList(Arg.createVar(fileVal)));
    }

    public static Instruction structClose(Var struct) {
      return new TurbineOp(Opcode.STRUCT_CLOSE,
          Arrays.asList(Arg.createVar(struct)));
    }
  
    public static Instruction structInsert(Var structVar,
        String fieldName, Var fieldContents) {
      return new TurbineOp(Opcode.STRUCT_INSERT,
          Arrays.asList(Arg.createVar(structVar),
                      Arg.createStringLit(fieldName),
                      Arg.createVar(fieldContents)));
    }
  
    public static Instruction addressOf(Var target, Var src) {
      return new TurbineOp(Opcode.ADDRESS_OF,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceInt(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_INT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction dereferenceBool(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_BOOL,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceFloat(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_FLOAT,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceString(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_STRING,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
  
    public static Instruction dereferenceBlob(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_BLOB,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction dereferenceFile(Var target, Var src) {
      return new TurbineOp(Opcode.DEREF_FILE,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction retrieveRef(Var target, Var src) {
      return new TurbineOp(Opcode.LOAD_REF,
          Arrays.asList(Arg.createVar(target), Arg.createVar(src)));
    }
    
    public static Instruction copyRef(Var dst, Var src) {
      return new TurbineOp(Opcode.COPY_REF,
          Arrays.asList(Arg.createVar(dst), Arg.createVar(src)));
          
    }
  
    public static Instruction arrayCreateNestedComputed(Var arrayResult,
        Var arrayVar, Var indexVar) {
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_FUTURE,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), Arg.createVar(indexVar)));
    }
  
    public static Instruction arrayCreateNestedImm(Var arrayResult,
        Var arrayVar, Arg arrIx) {
      return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_IMM,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), arrIx));
    }
  
    public static Instruction arrayRefCreateNestedComputed(Var arrayResult,
        Var arrayVar, Var indexVar) {
      return new TurbineOp(Opcode.ARRAY_REF_CREATE_NESTED_FUTURE,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), Arg.createVar(indexVar)));
    }
  
  
    public static Instruction arrayRefCreateNestedImmIx(Var arrayResult,
        Var arrayVar, Arg arrIx) {
      return new TurbineOp(Opcode.ARRAY_REF_CREATE_NESTED_IMM,
          Arrays.asList(Arg.createVar(arrayResult),
              Arg.createVar(arrayVar), arrIx));
    }
  
  
    public static Instruction initUpdateableFloat(Var updateable, 
                                                              Arg val) {
      return new TurbineOp(Opcode.INIT_UPDATEABLE_FLOAT, 
          Arrays.asList(Arg.createVar(updateable), val));
      
    }

    public static Instruction latestValue(Var result, 
                              Var updateable) {
      return new TurbineOp(Opcode.LATEST_VALUE, Arrays.asList(
          Arg.createVar(result), Arg.createVar(updateable)));
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
        throw new STCRuntimeError("Unknown UpdateMode"
            + updateMode);
      }
      return new TurbineOp(op, Arrays.asList(Arg.createVar(updateable), 
                                             Arg.createVar(val)));
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
      return new TurbineOp(op, Arrays.asList(Arg.createVar(updateable), 
                                                                   val));
    }
    
    public static Instruction getFileName(Var filename, Var file,
                                          boolean initUnmapped) {
      if (initUnmapped) {
        return new TurbineOp(Opcode.GET_OUTPUT_FILENAME, 
                Arrays.asList(Arg.createVar(filename), Arg.createVar(file)));
      } else {
        return new TurbineOp(Opcode.GET_FILENAME, 
                Arrays.asList(Arg.createVar(filename), Arg.createVar(file)));
      }
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
      case ARRAY_DECR_WRITERS:
      case DECR_REF:
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
      case STORE_VOID:
      case STORE_FLOAT:
      case STORE_STRING:
      case STORE_BLOB:
      case STORE_FILE:
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
      case LOAD_VOID:
      case LOAD_FLOAT:
      case LOAD_STRING:
      case LOAD_BLOB:
      case LOAD_FILE:
      case STRUCT_LOOKUP:
      case STRUCTREF_LOOKUP:
      case ADDRESS_OF:
      case LOAD_REF:
      case COPY_REF:
      case GET_FILENAME:
      case GET_OUTPUT_FILENAME:
        return 1;
      case DECR_BLOB_REF:
      case FREE_BLOB:
      case DECR_LOCAL_FILE_REF:
        // View refcounted var as output
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
      case DECR_REF:
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
      case STORE_BLOB:
      case STORE_VOID:
      case DEREF_INT:
      case DEREF_BOOL:
      case DEREF_FLOAT:
      case DEREF_STRING:
      case DEREF_BLOB:
      case LOAD_INT:
      case LOAD_BOOL:
      case LOAD_FLOAT:
      case LOAD_STRING:
      case LOAD_BLOB:
      case LOAD_VOID:
      case LOAD_FILE:
      case ARRAY_LOOKUP_REF_IMM:
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_IMM:
          return this.writesAliasVar();

      case DEREF_FILE:
        return this.writesAliasVar() ||
               this.args.get(0).getVar().isMapped();

      case STORE_FILE:
        return this.writesAliasVar() ||
              this.args.get(0).getVar().isMapped();
        
      case GET_FILENAME:
        // Only effect is setting alias var
        return false;
      case GET_OUTPUT_FILENAME:
        // Might initialise mapping if not already mapped
        return !getInput(0).getVar().isMapped();
        
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
      case DECR_BLOB_REF:
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
  
    @Override
    public List<Var> getOutputs() {
      int nOutputs = numOutputArgs();
      ArrayList<Var> res = new ArrayList<Var>();
      for (int i = 0; i < nOutputs; i++) {
        Arg a = args.get(i);
        assert(a.isVar());
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
      case STORE_VOID:
      case STORE_FLOAT:
      case LOAD_BOOL:
      case LOAD_VOID:
      case LOAD_FLOAT:
      case LOAD_INT:
      case LOAD_STRING:
        // The input arg could be a var or a literal constant
        if (args.get(1).isVar()) {
          Arg val = knownConstants.get(args.get(1).getVar().name());
          if (val != null) {
            HashMap<String, Arg> r = new HashMap<String, Arg>();
            r.put(args.get(0).getVar().name(), val);
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
        Var index = args.get(2).getVar();
        if (knownConstants.containsKey(index.name())) {
          Arg cIndex = knownConstants.get(index.name());
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
        Var sIndex = args.get(1).getVar();
        if (knownConstants.containsKey(sIndex.name())) {
          Arg cIndex = knownConstants.get(sIndex.name());
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
        Var arr = args.get(1).getVar();
        if (closedVars.contains(arr.name())) {
          // Don't need to retrieve any value, but just use this protocol
          return new MakeImmRequest(null, new ArrayList<Var>());
        }
        break;
        
      case ARRAY_LOOKUP_FUTURE:
        Var index = args.get(2).getVar();
        if (closedVars.contains(index.name())) {
          return new MakeImmRequest(null, Arrays.asList(index));
        }
        break;
      case ARRAYREF_LOOKUP_FUTURE:
        // We will take either the index or the dereferenced array
        List<Var> req = mkImmVarList(closedVars, 
                  args.get(1).getVar(), args.get(2).getVar());
        if (req.size() > 0) {
          return new MakeImmRequest(null, req);
        }
        break;
      case ARRAYREF_LOOKUP_IMM:
        // Could skip using reference
        Var arrRef2 = args.get(1).getVar();
        if (closedVars.contains(arrRef2.name())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef2));
        }
        break;
      case ARRAY_INSERT_FUTURE:
        Var sIndex = args.get(1).getVar();
        if (closedVars.contains(sIndex.name())) {
          return new MakeImmRequest(null, Arrays.asList(sIndex));
        }
        break;
      case ARRAYREF_INSERT_IMM:
        Var arrRef3 = args.get(0).getVar();
        if (closedVars.contains(arrRef3.name())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef3));
        }
        break;
      case ARRAYREF_INSERT_FUTURE:
        // We will take either the index or the dereferenced array
        List<Var> req2 = mkImmVarList(closedVars,
                    args.get(0).getVar(), args.get(1).getVar());
        if (req2.size() > 0) {
          return new MakeImmRequest(null, req2);
        }
        break;
      case ARRAY_CREATE_NESTED_FUTURE:
        // Try to get immediate index
        Var index2 = args.get(2).getVar();
        if (closedVars.contains(index2.name())) {
          return new MakeImmRequest(null, Arrays.asList(index2));
        }
        break;
      case ARRAY_REF_CREATE_NESTED_IMM:
        Var arrRef5 = args.get(1).getVar();
        if (closedVars.contains(arrRef5.name())) {
          return new MakeImmRequest(null, Arrays.asList(arrRef5));
        }
        break;
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        List<Var> req5 = mkImmVarList(closedVars, 
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
    
    private List<Var> mkImmVarList(Set<String> closedVars, 
                                              Var... args) {
      ArrayList<Var> req = new ArrayList<Var>(args.length);
      for (Var v: args) {
        if (closedVars.contains(v.name())) {
          req.add(v);
        }
      }
      return req;
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> out, List<Arg> values) {
      switch (op) {
      case ARRAY_LOOKUP_REF_IMM:
        assert(values.size() == 0);
        // OUtput switched from ref to value
        Var refOut = args.get(0).getVar();
        Var valOut = Var.createDerefTmp(refOut, 
                                      VarStorage.ALIAS);
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
        Var oldOut = args.get(0).getVar();
        assert(Types.isArrayRef(oldOut.type()));
        Var newOut = Var.createDerefTmp(oldOut, 
                                                VarStorage.ALIAS);
        return new MakeImmChange(newOut, oldOut,
            arrayCreateNestedImm(newOut,
                            args.get(1).getVar(), values.get(0)));
      case ARRAY_REF_CREATE_NESTED_FUTURE:
        assert(values.size() == 1 || values.size() == 2);
        if (values.size() == 2) {
          Var oldOut2 = args.get(0).getVar();
          assert(Types.isArrayRef(oldOut2.type()));
          Var newOut2 = Var.createDerefTmp(oldOut2,
                                          VarStorage.ALIAS);
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
        Var oldOut3 = args.get(0).getVar();
        assert(Types.isArrayRef(oldOut3.type()));
        Var newOut3 = Var.createDerefTmp(oldOut3,
                                                VarStorage.ALIAS);
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
    public List<Var> getBlockingInputs() {
      if (getMode() == TaskMode.SYNC) {
        return null;
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
      case STRUCT_CLOSE:
      case STRUCT_INSERT:
      case STRUCT_LOOKUP:
      case ARRAY_DECR_WRITERS:
      case DECR_REF:
      case ARRAY_CREATE_NESTED_IMM:
      case ADDRESS_OF:
      case LOAD_REF:
      case DECR_BLOB_REF:
      case FREE_BLOB:
      case DECR_LOCAL_FILE_REF:
      case GET_FILENAME:
      case GET_OUTPUT_FILENAME:
      case ARRAY_LOOKUP_IMM:
      case COPY_REF:
        return TaskMode.SYNC;
      
      case ARRAY_INSERT_FUTURE:
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
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
      case ARRAY_REF_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_REF_CREATE_NESTED_IMM:
        return TaskMode.LOCAL;
      default:
        throw new STCRuntimeError("Need to add opcode " + op.toString()
            + " to getMode");
      }
    }
    
    @Override
    public List<ComputedValue> getComputedValues(CVMap existing) {
      Arg arr = null;
      Arg ix = null;
      Arg contents = null;
      ComputedValue cv = null;
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
          Arg src = args.get(1);
          Arg val = args.get(0);
          if (Types.isScalarUpdateable(src.getVar().type())) {
            return null;
          }
          ComputedValue retrieve = vanillaComputedValue(true);
          Opcode cvop = assignOpcode(src.getType());
          if (cvop == null) {
            throw new STCRuntimeError("Need assign op for "
                + src.getVar());
          }
          ComputedValue assign = new ComputedValue(cvop,
                    "", Arrays.asList(val), src, true);
          
          Opcode derefOp = derefOpCode(src.getType());
          
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
        case STORE_STRING: 
        case STORE_BLOB: 
        case STORE_VOID:
        case STORE_FILE: {
          // add assign so we can avoid recreating future 
          // (true b/c this instruction closes val immediately)
          ComputedValue assign = vanillaComputedValue(true);
          // add retrieve so we can avoid retrieving later
          Arg dst = args.get(0);
          Arg src = args.get(1);
          Opcode cvop = retrieveOpcode(dst.getType());
          assert(cvop != null);

          ComputedValue retrieve = new ComputedValue(cvop,
                    "", Arrays.asList(dst), src, false);
          if (op == Opcode.ADDRESS_OF) {
            Opcode derefOp = derefOpCode(dst.getType());
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
        case GET_OUTPUT_FILENAME: {
          Arg out = args.get(0);
          Arg in = args.get(1);
          return Arrays.asList(fileNameCV(out, in.getVar()));
        }
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
          Var lookupRes = contents.getVar();
          
          cv = makeArrayComputedValue(arr, ix, contents);
  
          if (op == Opcode.ARRAY_LOOKUP_IMM) {
            assert(lookupRes.type().equals(
                Types.getArrayMemberType(arr.getType())));
            // This just retrieves the item immediately
            return Arrays.asList(cv);
          } else {
            assert (Types.isRefTo(lookupRes.type(), 
                Types.getArrayMemberType(arr.getType())));
            Arg prev = existing.getLocation(new ComputedValue(Opcode.FAKE,
                ComputedValue.ARRAY_CONTENTS, Arrays.asList(arr, ix)));
            if (prev != null) {
              /* All these array loads give back a reference, but if a value
               * was previously inserted at this index, then we can 
               * short-circuit this as we know what is in the reference */
              ComputedValue retrieveCV = new ComputedValue(retrieveOpcode(
                  lookupRes.type()), "", contents, prev, false);
              Opcode derefOp = derefOpCode(lookupRes.type());
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
          Var nestedArr = contents.getVar();
          arr = args.get(1);
          ix = args.get(2);
          cv = makeArrayComputedValue(arr, ix, contents);
          if (op == Opcode.ARRAY_CREATE_NESTED_IMM) {
            // No references involved, the instruction returns the nested
            // array directly
            return Arrays.asList(cv);
          } else {
            Arg prev = existing.getLocation(new ComputedValue(Opcode.FAKE,
                ComputedValue.ARRAY_CONTENTS, Arrays.asList(arr, ix)));
            assert (Types.isRefTo(nestedArr.type(), 
                        Types.getArrayMemberType(arr.getType())));
            if (prev != null) {
              // See if we know the value of this reference already
              ComputedValue derefCV = new ComputedValue(retrieveOpcode(
                  nestedArr.type()), "", Arrays.asList(contents),
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
    private boolean isMemberReference(Var member, Var arr) 
            throws STCRuntimeError{
      Type memberType = Types.getArrayMemberType(arr.type());
      if (memberType.equals(member.type())) {
        return false;
      } else if (Types.isRefTo(member.type(), memberType)) {
        return true;
      }
      throw new STCRuntimeError("Inconsistent types in IC instruction:"
          + this.toString() + " array of type " + arr.type() 
          + " with member of type " + member.type());
    }

    @Override
    public boolean closesOutputs() {
      if (op == Opcode.GET_OUTPUT_FILENAME) {
        // Will be closed for unmapped vars
        return !getOutput(0).isMapped();
      }
      
      return super.closesOutputs();
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
    
    @Override
    public String shortOpName() {
      return op.toString().toLowerCase() + "-" + functionName;
    }
    
    private boolean isCopyFunction() {
      if (Builtins.isCopyFunction(functionName)) {
        return true;
      } else if (Builtins.isMinMaxFunction(functionName)
              && getInput(0).equals(getInput(1))) {
        return true;
      }
      return false;
    }
    
    @Override
    public List<ComputedValue> getComputedValues(CVMap existing) {
      // TODO: make order of args invariant where possible
      if (Builtins.isPure(functionName)) {
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
          if (Builtins.isCommutative(this.functionName)) {
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
            res.add(fileNameCV(getInput(0), getOutput(0)));
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
    private final List<Var> outputs;
    private final List<Var> inputs;
    private final List<Boolean> closedInputs; // which inputs are closed
    private Arg priority;
  
    private FunctionCall(Opcode op, String functionName,
        List<Var> inputs, List<Var> outputs, Arg priority) {
      super(op, functionName);
      if (op != Opcode.CALL_BUILTIN && op != Opcode.CALL_CONTROL &&
          op != Opcode.CALL_SYNC && op != Opcode.CALL_LOCAL &&
          op != Opcode.CALL_LOCAL_CONTROL) {
        throw new STCRuntimeError("Tried to create function call"
            + " instruction with invalid opcode");
      }
      this.priority = priority;
      this.outputs = new ArrayList<Var>();
      this.outputs.addAll(outputs);
      this.inputs = new ArrayList<Var>();
      this.inputs.addAll(inputs);
      this.closedInputs = new ArrayList<Boolean>(inputs.size());
      for (int i = 0; i < inputs.size(); i++) {
        this.closedInputs.add(false);
      }
      
      for(Var v: outputs) {
        assert(v != null);
      }
      
      for(Var v: inputs) {
        assert(v != null);
      }
    }
    
    public static FunctionCall createFunctionCall(
        String functionName, List<Var> inputs, List<Var> outputs,
        TaskMode mode, Arg priority) {
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
      return new FunctionCall(op, functionName,
          inputs, outputs, priority);
    }
  
    public static FunctionCall createBuiltinCall(
        String functionName, List<Var> inputs, List<Var> outputs,
        Arg priority) {
      return new FunctionCall(Opcode.CALL_BUILTIN, functionName,
          inputs, outputs, priority);
    }
  
    @Override
    public String toString() {
      String result = op.toString().toLowerCase() + " " + functionName;
      result += " [";
      for (Var v: outputs) {
        result += " " + v.name();
      }
      result += " ] [";
      for (Var v: inputs) {
        result += " " + v.name();
      }
      result += " ]";
      if (priority != null) {
        result += " priority=" + priority.toString(); 
      }
      result += " closed=" + closedInputs;
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
  
    /**
     * @return function input arguments
     */
    public List<Var> getFunctionInputs() {
      return Collections.unmodifiableList(inputs);
    }


    public Var getFunctionInput(int i) {
      return inputs.get(i);
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
    public Arg getPriority() {
      return priority;
    }

    @Override
    public List<Var> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    @Override
    public boolean hasSideEffects() {
      return (!Builtins.isPure(functionName)) ||
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
          Var in = this.inputs.get(i);
          if (closedVars.contains(in.name())) {
            this.closedInputs.set(i, true);
          } else {
            allClosed = false;
          }
        }
      }
      if (allClosed && (Builtins.hasOpEquiv(this.functionName)
                || Builtins.hasInlineVersion(this.functionName))) {
        TaskMode mode = Builtins.getTaskMode(this.functionName);
        if (mode == null) {
          mode = TaskMode.LOCAL;
        }
        // All args are closed!
        return new MakeImmRequest(
            Collections.unmodifiableList(this.outputs),
            Collections.unmodifiableList(this.inputs), mode);

      }
      return null;
    }

    @Override
    public MakeImmChange makeImmediate(List<Var> outVars, 
                                        List<Arg> values) {
      if (Builtins.hasOpEquiv(functionName)) {
        BuiltinOpcode newOp = Builtins.getOpEquiv(functionName);
        assert(newOp != null);
        assert(values.size() == inputs.size());
        
        if (outputs.size() == 1) {
          assert(Types.derefResultType(outputs.get(0).type()).equals(
              outVars.get(0).type()));
          return new MakeImmChange(
              Builtin.createLocal(newOp, outVars.get(0), values));
        } else {
          assert(outputs.size() == 0);
          return new MakeImmChange(
              Builtin.createLocal(newOp, null, values));
        }
      } else {
        assert(Builtins.hasInlineVersion(functionName));
        for (int i = 0; i < outputs.size(); i++) {
          Var out = outputs.get(i);
          assert(Types.derefResultType(out.type()).equals(
                 outVars.get(i).type()));
        }
        return new MakeImmChange(
                new LocalFunctionCall(functionName, values, outVars));
      }
    }

    @Override
    public List<Var> getBlockingInputs() {
      List<Var> blocksOn = new ArrayList<Var>();
      if (op == Opcode.CALL_BUILTIN) {
        for (Var v: inputs) {
          if (Types.isScalarFuture(v.type())
              || Types.isRef(v.type())) {
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
    public TaskMode getMode() {
      switch (op) {
        case CALL_SYNC:
          return TaskMode.SYNC;
        case CALL_LOCAL:
          return TaskMode.LOCAL;
        case CALL_LOCAL_CONTROL:
          return TaskMode.LOCAL_CONTROL;
        case CALL_BUILTIN:
          return Builtins.getTaskMode(functionName);
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
          new ArrayList<Var>(inputs), new ArrayList<Var>(outputs),
          priority);
    }
  }
  
  public static class LocalFunctionCall extends CommonFunctionCall {
    private final List<Var> outputs;
    private final List<Arg> inputs;
  
    public LocalFunctionCall(String functionName,
        List<Arg> inputs, List<Var> outputs) {
      super(Opcode.CALL_BUILTIN_LOCAL, functionName);
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
    public List<Var> getOutputs() {
      return Collections.unmodifiableList(outputs);
    }

    @Override
    public boolean hasSideEffects() {
      return (!Builtins.isPure(functionName)) ||
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
    public MakeImmChange makeImmediate(List<Var> outVars, 
                                        List<Arg> values) {
      throw new STCRuntimeError("Invalid method call");
    }

    @Override
    public List<Var> getBlockingInputs() {
      // doesn't take futures as args
      return null;
    }

    @Override
    public TaskMode getMode() {
      return TaskMode.SYNC;
    }
    
    @Override
    public Instruction clone() {
      // Variables are immutable so just need to clone lists
      return new LocalFunctionCall(functionName, 
          new ArrayList<Arg>(inputs), new ArrayList<Var>(outputs));
    }
  }
  
  public static class RunExternal extends Instruction {
    private final String cmd;
    private final ArrayList<Arg> inFiles;
    private final ArrayList<Var> outFiles;
    private final ArrayList<Arg> inputs;
    private final Redirects<Arg> redirects;
    private final boolean hasSideEffects;
    private final boolean deterministic;
    
    public RunExternal(String cmd, List<Arg> inFiles, List<Var> outFiles, List<Arg> inputs,
               Redirects<Arg> redirects,
               boolean hasSideEffects, boolean deterministic) {
      super(Opcode.RUN_EXTERNAL);
      this.cmd = cmd;
      this.inFiles = new ArrayList<Arg>(inFiles);
      this.outFiles = new ArrayList<Var>(outFiles);
      this.inputs = new ArrayList<Arg>(inputs);
      this.redirects = redirects.clone();
      this.deterministic = deterministic;
      this.hasSideEffects = hasSideEffects;
    }

    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceOpargsInList(renames, inputs);
      ICUtil.replaceOpargsInList(renames, inFiles);
      ICUtil.replaceVarsInList(renames, outFiles, false);
      redirects.stdin = ICUtil.replaceOparg(renames, redirects.stdin, true);
      redirects.stdout = ICUtil.replaceOparg(renames, redirects.stdout, true);
      redirects.stderr = ICUtil.replaceOparg(renames, redirects.stderr, true);
    }

    @Override
    public void renameInputs(Map<String, Arg> renames) {
      ICUtil.replaceOpargsInList(renames, inFiles);
      ICUtil.replaceOpargsInList(renames, inputs);
      redirects.stdin = ICUtil.replaceOparg(renames, redirects.stdin, true);
      redirects.stdout = ICUtil.replaceOparg(renames, redirects.stdout, true);
      redirects.stderr = ICUtil.replaceOparg(renames, redirects.stderr, true);
    }

    @Override
    public String toString() {
      StringBuilder res = new StringBuilder();
      res.append(formatFunctionCall(op, cmd, outFiles, inputs));
      String redirectString = redirects.toString();
      if (redirectString.length() > 0) {
        res.append(" " + redirectString);
      }
      res.append(" inFiles = [");
      ICUtil.prettyPrintArgList(res, inFiles);
      res.append("]");
      
      res.append(" outFiles = [");
      ICUtil.prettyPrintVarList(res, outFiles);
      res.append("]");
      return res.toString();
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.runExternal(cmd, inputs, inFiles, 
                  outFiles, redirects, hasSideEffects, deterministic);
    }

    @Override
    public List<Arg> getInputs() {
      ArrayList<Arg> res = new ArrayList<Arg>();
      res.addAll(inputs);
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
    public boolean closesOutputs() {
      return true;
    }

    @Override
    public List<ComputedValue> getComputedValues(CVMap existing) {
      if (deterministic) {
        ArrayList<ComputedValue> cvs = new ArrayList<ComputedValue>(
                                                        outFiles.size());
        for (int i = 0; i < outFiles.size(); i++) {
          // Unique key for cv includes number of output
          // Output file should be closed after external program executes
          ComputedValue cv = new ComputedValue(op, cmd + "!!" + i,
                     inputs, Arg.createVar(outFiles.get(i)), true);
          cvs.add(cv);
        }
        return cvs;
      } else {
        return null;
      }
    }

    @Override
    public Instruction clone() {
      return new RunExternal(cmd, inFiles, outFiles, inputs, redirects,
                             hasSideEffects, deterministic);
    }
    
  }
  
  public static class LoopContinue extends Instruction {
    private final ArrayList<Var> newLoopVars;
    private final ArrayList<Var> loopUsedVars;
    private final ArrayList<Var> keepOpenVars;
    private final ArrayList<Boolean> blockingVars;
  
    public LoopContinue(List<Var> newLoopVars, 
                        List<Var> loopUsedVars,
                        List<Var> keepOpenVars,
                        List<Boolean> blockingVars) {
      super(Opcode.LOOP_CONTINUE);
      this.newLoopVars = new ArrayList<Var>(newLoopVars);
      this.loopUsedVars = new ArrayList<Var>(loopUsedVars);
      this.keepOpenVars = new ArrayList<Var>(keepOpenVars);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
    }
  
    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, newLoopVars, false);
      ICUtil.replaceVarsInList(renames, loopUsedVars, true);
      ICUtil.replaceVarsInList(renames, keepOpenVars, true);
    }
    
    @Override
    public void renameInputs(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, newLoopVars, false);
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      assert(!removeVars.contains(newLoopVars.get(0).name()));
      ICUtil.removeVarsInList(loopUsedVars, removeVars);
      ICUtil.removeVarsInList(keepOpenVars, removeVars);
      ICUtil.removeVarsInList(newLoopVars, removeVars);
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
      sb.append(']');
      return sb.toString();
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.loopContinue(this.newLoopVars, this.loopUsedVars,
                                      this.keepOpenVars,
                                      this.blockingVars);
    }
  
    @Override
    public List<Arg> getInputs() {
      // need to make sure that these variables are avail in scope
      ArrayList<Arg> res = new ArrayList<Arg>(newLoopVars.size());
      for (Var v: newLoopVars) {
        res.add(Arg.createVar(v));
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
          Var v = this.newLoopVars.get(i);
          if (closedVars.contains(v.name())) {
            // Don't need to block
            this.blockingVars.set(i, false);
          } else if (alreadyDone.contains(v.name())) {
            // In case of repeated elements
            this.blockingVars.set(i, false);
          } else {
            alreadyDone.add(v.name());
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
    public List<Var> getBlockingInputs() {
      return null;
    }
    

    @Override
    public TaskMode getMode() {
      return TaskMode.CONTROL;
    }

    public void addUsedVar(Var variable) {
      this.loopUsedVars.add(variable);
      ICUtil.removeDuplicates(this.loopUsedVars);
    }
    
    public void addUsedVars(Collection<Var> variables) {
      for (Var v: variables) {
        addUsedVar(v);
      }
    }

    public void removeUsedVar(Var variable) {
      ICUtil.removeVarInList(loopUsedVars, variable.name());
    }

    public void removeUsedVars(Collection<Var> vars) {
      for (Var var: vars) {
        removeUsedVar(var);
      }
    }

    public void addKeepOpenVar(Var variable) {
      this.keepOpenVars.add(variable);
    }

    public void removeKeepOpenVar(Var variable) {
      ICUtil.removeVarInList(keepOpenVars, variable.name());
    }

    public void removeKeepOpenVars(Collection<Var> vars) {
      for (Var var: vars) {
        removeKeepOpenVar(var);
      }
    }

    @Override
    public List<ComputedValue> getComputedValues(CVMap existing) {
      // Nothing
      return null;
    }

    @Override
    public Instruction clone() {
      return new LoopContinue(new ArrayList<Var>(newLoopVars), 
          new ArrayList<Var>(loopUsedVars),
          new ArrayList<Var>(keepOpenVars), 
          new ArrayList<Boolean>(blockingVars));
    }
  }
  
  public static class LoopBreak extends Instruction {
    /**
     * Variables where refcount should be decremented upon loop termination
     */
    private final ArrayList<Var> loopUsedVars;

    /**
     * Variables to be closed upon loop termination
     */
    private final ArrayList<Var> keepOpenVars;
  
    public LoopBreak(List<Var> loopUsedVars, List<Var> varsToClose) {
      super(Opcode.LOOP_BREAK);
      this.loopUsedVars = new ArrayList<Var>(loopUsedVars);
      this.keepOpenVars = new ArrayList<Var>(varsToClose);
    }
  
    @Override
    public void renameVars(Map<String, Arg> renames) {
      ICUtil.replaceVarsInList(renames, loopUsedVars, true);
      ICUtil.replaceVarsInList(renames, keepOpenVars, true);
    }
  
    @Override
    public void renameInputs(Map<String, Arg> replacements) {
      // do nothing
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.op.toString().toLowerCase());

      sb.append(" #passin[");
      ICUtil.prettyPrintVarList(sb, this.loopUsedVars);

      sb.append("] #keepopen[");
      ICUtil.prettyPrintVarList(sb, this.keepOpenVars);
      sb.append(']');
      return sb.toString();
    }
  
    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      gen.loopBreak(loopUsedVars, keepOpenVars);
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
    public MakeImmChange makeImmediate(List<Var> out, List<Arg> values) {
      throw new STCRuntimeError("Not valid on loop continue!");
    }

    @Override
    public List<Var> getBlockingInputs() {
      return null;
    }

    @Override
    public TaskMode getMode() {
      return TaskMode.SYNC;
    }
    
    @Override
    public List<ComputedValue> getComputedValues(CVMap existing) {
      // nothing
      return null;
    }

    public void addUsedVar(Var variable) {
      this.loopUsedVars.add(variable);
      ICUtil.removeDuplicates(this.loopUsedVars);
    }
    
    public void addUsedVars(Collection<Var> variables) {
      for (Var v: variables) {
        addUsedVar(v);
      }
    }

    public void removeUsedVar(Var variable) {
      ICUtil.removeVarInList(loopUsedVars, variable.name());
    }

    public void removeUsedVars(Collection<Var> vars) {
      for (Var var: vars) {
        removeUsedVar(var);
      }
    }

    public void addKeepOpenVar(Var variable) {
      this.keepOpenVars.add(variable);
    }

    public void removeKeepOpenVar(Var variable) {
      ICUtil.removeVarInList(keepOpenVars, variable.name());
    }

    public void removeKeepOpenVars(Collection<Var> vars) {
      for (Var var: vars) {
        removeKeepOpenVar(var);
      }
    }

    @Override
    public Instruction clone() {
      return new LoopBreak(loopUsedVars, keepOpenVars);
    }

  }
  
  public static enum Opcode {
    FAKE, // Used for ComputedValue if there isn't a real opcode
    COMMENT,
    CALL_BUILTIN, CALL_BUILTIN_LOCAL, CALL_CONTROL, CALL_SYNC, CALL_LOCAL,
    CALL_LOCAL_CONTROL,
    DEREF_INT, DEREF_STRING, DEREF_FLOAT, DEREF_BOOL, DEREF_BLOB,
    DEREF_FILE,
    STORE_INT, STORE_STRING, STORE_FLOAT, STORE_BOOL, ADDRESS_OF, 
    LOAD_INT, LOAD_STRING, LOAD_FLOAT, LOAD_BOOL, LOAD_REF,
    STORE_BLOB, LOAD_BLOB, DECR_BLOB_REF, FREE_BLOB,
    STORE_VOID, LOAD_VOID, 
    STORE_FILE, DECR_LOCAL_FILE_REF,
    LOAD_FILE, // dummy instruction
    ARRAY_DECR_WRITERS, DECR_REF,
    
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
    GET_FILENAME, GET_OUTPUT_FILENAME
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
    private Arg priority; // priority of op if async.  null for default prio

    private Builtin(Opcode op, BuiltinOpcode subop, Var output, 
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
        Arg input, Arg priority) {
      return new Builtin(Opcode.ASYNC_OP, subop, output, Arrays.asList(input),
                  priority);
    }
    
    public static Builtin createAsync(BuiltinOpcode subop, Var output, 
        List<Arg> inputs, Arg priority) {
      return new Builtin(Opcode.ASYNC_OP, subop, output, inputs, priority);
    }

    @Override
    public void renameVars(Map<String, Arg> renames) {
      if (output != null && renames.containsKey(this.output.name())) {
        this.output = renames.get(this.output.name()).getVar();
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
        res +=  output.name() + " = ";
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
        if (in.isVar()) {
          Arg c = knownConstants.get(in.getVar().name());
          constInputs.add(c);
          if (c != null && op == Opcode.LOCAL_OP) {
            // can replace local value arg with constant
            inputs.set(i, c);
          }
        } else {
          constInputs.add(in);
        }
      }
      return Builtin.constantFold(this.subop, this.output.name(),
          constInputs);
    }

    private static void compileTimeAssertCheck(BuiltinOpcode subop2,
        List<Arg> inputs2, Map<String, Arg> knownConstants,
        String enclosingFnName) {
      if (subop2 == BuiltinOpcode.ASSERT) {
        Arg cond;
        if (inputs2.get(0).isVar()) {
          cond = knownConstants.get(inputs2.get(0).getVar().name());
        } else {
          cond = inputs2.get(0);
        }
        if (cond != null) {
          assert(cond.isBoolVal());
          if(!cond.getBoolLit()) {
            compileTimeAssertWarn(enclosingFnName, 
                "constant condition evaluated to false",
                inputs2.get(1), knownConstants);
          }
        }
      } else {
        assert(subop2 == BuiltinOpcode.ASSERT_EQ);
        
        Arg a1;
        if (inputs2.get(0).isVar()) {
          a1 = knownConstants.get(inputs2.get(0).getVar().name());
        } else {
          a1 = inputs2.get(0);
        }
        Arg a2;
        if (inputs2.get(1).isVar()) {
          a2 = knownConstants.get(inputs2.get(1).getVar().name());
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
      } else if (knownConstants.containsKey(assertMsg.getVar().name())) {
        errMessage = knownConstants.get(assertMsg.getVar().name())
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
      List<Var> varArgs = new ArrayList<Var>(2);
      for (Arg in: inputs) {
        if (in.isConstant()) {
          constArgs.add(in);
        } else {
          Arg constIn = knownConstants.get(in.getVar().name());
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
            assert(inarg.isVar());
            Var in = inarg.getVar();
            if (!closedVars.contains(in.name())) {
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
        return null;
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
    public List<ComputedValue> getComputedValues(CVMap existing) {
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
      default:
        // fall through
        break;
      }
    } else if (Types.isArray(dst.type()) || Types.isStruct(dst.type())) {
      assert(dst.storage() == VarStorage.ALIAS);
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
    return new ComputedValue(op, "", 
        Collections.singletonList(Arg.createVar(src)));
  }

  public static ComputedValue assignComputedVal(Var dst, Arg val) {
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
        case FILE:
          op = BuiltinOpcode.COPY_FILE;
          break;
        case VOID:
          op = BuiltinOpcode.COPY_VOID;
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
      op = Opcode.ADDRESS_OF;
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
      assert(src.isVar() && src.getVar().type().equals(Types.V_VOID));
      return TurbineOp.assignVoid(dst, src);
    default:
      throw new STCRuntimeError("method to set " +
          dst.type().typeName() + " is not known yet");
    }
  }

  public static ComputedValue fileNameCV(Arg outFilename, Var inFile) {
    assert(Types.isFile(inFile.type()));
    assert(outFilename.isVar());
    assert(Types.isString(outFilename.getVar().type()));
    return new ComputedValue(Opcode.GET_FILENAME,
        "", Arrays.asList(Arg.createVar(inFile)), outFilename, false);
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


