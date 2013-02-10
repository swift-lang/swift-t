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

package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.util.Pair;
import exm.stc.tclbackend.tree.Command;
import exm.stc.tclbackend.tree.Dict;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.LiteralInt;
import exm.stc.tclbackend.tree.Sequence;
import exm.stc.tclbackend.tree.SetVariable;
import exm.stc.tclbackend.tree.Square;
import exm.stc.tclbackend.tree.TclList;
import exm.stc.tclbackend.tree.TclString;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;
import exm.stc.tclbackend.tree.Value;

/**
 * Automates creation of Turbine-specific Tcl constructs
 * @author wozniak
 *
 * This class is package-private: only TurbineGenerator uses it
 * */
class Turbine
{
  private static final String GET_FILE = "turbine::get_file";
  private static final Token GET_OUTPUT_FILE_PATH = new Token("turbine::get_output_file_path");
  private static final Token GET_FILE_PATH = new Token("turbine::get_file_path");
  private static final String EXEC_EXTERNAL = "turbine::exec_external";
  /* Names of types used by Turbine */
  public static final String STRING_TYPENAME = "string";
  public static final String INTEGER_TYPENAME = "integer";
  public static final String VOID_TYPENAME = "void";
  public static final String FLOAT_TYPENAME = "float";
  public static final String BLOB_TYPENAME = "blob";

  // Commonly used things:
  private static final Token ALLOCATE_CONTAINER_CUSTOM =
      new Token("turbine::allocate_container_custom");
  private static final Token ALLOCATE_FILE = 
          new Token("turbine::allocate_file2");
  private static final Token CONTAINER_INSERT =
      new Token("turbine::container_insert");
  private static final Token CONTAINER_F_INSERT =
      new Token("turbine::container_f_insert");
  private static final Token CONTAINER_F_REFERENCE =
      new Token("turbine::f_reference");
  private static final Token CONTAINER_REFERENCE =
      new Token("turbine::container_reference");
  private static final Token CONTAINER_IMMEDIATE_INSERT =
      new Token("turbine::container_immediate_insert");
  private static final Token CREF_F_LOOKUP =
      new Token("turbine::f_cref_lookup");
  private static final TclTree CREF_LOOKUP_LITERAL =
      new Token("turbine::f_cref_lookup_literal");
  private static final Token F_CONTAINER_CREATE_NESTED =
      new Token("turbine::f_container_create_nested");
  private static final Token F_CREF_CREATE_NESTED =
      new Token("turbine::f_cref_create_nested");
  private static final Token F_CONTAINER_CREATE_NESTED_STATIC =
      new Token("turbine::container_create_nested");
  private static final Token F_CREF_CREATE_NESTED_STATIC =
      new Token("turbine::cref_create_nested");
  private static final Token CONTAINER_SLOT_DROP =
      new Token("adlb::slot_drop");
  private static final Token CONTAINER_SLOT_CREATE =
      new Token("adlb::slot_create");
  private static final Token CONTAINER_ENUMERATE =
      new Token("adlb::enumerate");
  private static final Token RETRIEVE_UNTYPED =
      new Token("turbine::retrieve");
  private static final Token RETRIEVE_INTEGER =
      new Token("turbine::retrieve_integer");
  private static final Token RETRIEVE_FLOAT =
      new Token("turbine::retrieve_float");
  private static final Token RETRIEVE_STRING =
      new Token("turbine::retrieve_string");
  private static final Token RETRIEVE_BLOB = new Token("turbine::retrieve_blob");
  private static final Token STACK_LOOKUP =
      new Token("turbine::stack_lookup");
  static final String LOCAL_STACK_NAME = "stack";
  static final String PARENT_STACK_NAME = "stack";
  private static final Value STACK = new Value(LOCAL_STACK_NAME);
  private static final Value PARENT_STACK =
      new Value(PARENT_STACK_NAME);
  private static final Token PARENT_STACK_ENTRY =
      new Token("_parent");
  private static final Token C_RULE = new Token("turbine::c::rule");
  private static final Token RULE = new Token("turbine::rule");
  private static final Token DEEPRULE = new Token("turbine::deeprule");
  private static final Token NO_STACK = new Token("no_stack");
  private static final Token DEREFERENCE_INTEGER =
      new Token("turbine::f_dereference_integer");
  private static final Token DEREFERENCE_FLOAT =
      new Token("turbine::f_dereference_float");
  private static final Token DEREFERENCE_STRING =
      new Token("turbine::f_dereference_string");
  private static final Token DEREFERENCE_BLOB =
          new Token("turbine::f_dereference_blob");
  private static final Token DEREFERENCE_FILE =
          new Token("turbine::f_dereference_file");
  private static final Token TURBINE_LOG =
      new Token("turbine::c::log");
  private static final Token ALLOCATE_CUSTOM =
      new Token("turbine::allocate_custom");
  private static final Token DIVIDE_INTEGER =
    new Token("turbine::divide_integer_impl");
  private static final Token MOD_INTEGER =
      new Token("turbine::mod_integer_impl");
  
  static final Token CONTAINER_LOOKUP =
      new Token("turbine::container_lookup");
  static final Token CONTAINER_LOOKUP_CHECKED =
      new Token("turbine::container_lookup_checked");
  private static final Token STORE_INTEGER =
      new Token("turbine::store_integer");
  private static final Token STORE_VOID =
          new Token("turbine::store_void");
  private static final Token STORE_FLOAT =
      new Token("turbine::store_float");
  private static final Token STORE_STRING =
      new Token("turbine::store_string");
  private static final Token STORE_BLOB = new Token("turbine::store_blob");
  private static final Token INIT_UPD_FLOAT =
      new Token("turbine::init_updateable_float");
  private static final Token CONTAINER_DEREF_INSERT =
      new Token("turbine::container_deref_insert");
  private static final Token CONTAINER_F_DEREF_INSERT =
      new Token("turbine::container_f_deref_insert");
  
  private static final Token CALL_FUNCTION =
      new Token("turbine::call_composite");

  private static final Token UNCACHED_MODE = new Token("UNCACHED");
  private static final Token FREE_BLOB = new Token("turbine::free_blob");
  private static final Token FREE_LOCAL_BLOB = 
      new Token("turbine::free_local_blob");
  
  public static final LiteralInt VOID_DUMMY_VAL = new LiteralInt(12345);
  private static final Token REFCOUNT_INCR = new Token("turbine::read_refcount_incr");
  private static final Token REFCOUNT_DECR = new Token("turbine::read_refcount_decr");
  private static final Token FILE_REFCOUNT_INCR = new Token("turbine::file_read_refcount_incr");
  private static final Token FILE_REFCOUNT_DECR = new Token("turbine::file_read_refcount_decr");

  public enum StackFrameType {
    MAIN,
    FUNCTION,
    NESTED
  }

  /**
   * Used to specify what caching is allowed for retrieve
   * @author tga
   */
  public enum CacheMode {
    CACHED,
    UNCACHED
  }
  
  public Turbine()
  {}

  public static TclTree[] createStackFrame(StackFrameType type)
  {
    TclTree[] result;
    // Index into result
    int index = 0;

    if (type == StackFrameType.MAIN)
      result = new TclTree[1];
    else
      result = new TclTree[3];


    if (type == StackFrameType.NESTED || type == StackFrameType.FUNCTION) {
      // Make sure that there is a variable in scope called parent
      // (parent is passed in as an argument)
      result[index++] = new SetVariable("parent", STACK);
    }

    result[index++] = allocateContainer(LOCAL_STACK_NAME,
                        STRING_TYPENAME, LiteralInt.ONE, LiteralInt.ONE);

    if (type != StackFrameType.MAIN) {
      // main is the only procedure without a parent stack frame
      result[index++] = new Command(CONTAINER_INSERT, STACK,
                                    PARENT_STACK_ENTRY, PARENT_STACK);
    }
    return result;
  }

  public static TclTree createDummyStackFrame() {
    return new SetVariable(LOCAL_STACK_NAME, new LiteralInt(0));
  }


  public static Command storeInStack(String stackVarName, String tclVarName)
  {
    Token name = new Token(stackVarName);
    Value value = new Value(tclVarName);
    Command result =
      new Command(CONTAINER_INSERT, STACK, name, value);
    return result;
  }


  public static TclTree allocateFuture(String tclName, String typePrefix,
      Expression initReaders) {
    return new Command(ALLOCATE_CUSTOM, new Token(tclName),
        new Token(typePrefix), initReaders);
  }

  public static TclTree allocateUpdateable(String tclName, String typePrefix,
      Expression initReaders) {
    return new Command(ALLOCATE_CUSTOM, new Token(tclName),
            new Token(typePrefix), initReaders);
  }
  

  public static TclTree allocate(String tclName, String typePrefix) {
    return allocate(tclName, typePrefix, LiteralInt.ONE, LiteralInt.ONE,
                    false);
  }
  
  public static TclTree allocatePermanent(String tclName, 
                                                  String typePrefix) {
    return allocate(tclName, typePrefix, LiteralInt.ONE, LiteralInt.ONE,
                     true);
  }
  
  public static TclTree allocate(
      String tclName, String typePrefix,
      Expression initReadRefcount, Expression initWriteRefcount,
      boolean permanent) {
    return new Command(ALLOCATE_CUSTOM, new Token(tclName),
              new Token(typePrefix),
              initReadRefcount, initWriteRefcount,
              LiteralInt.boolValue(permanent));
  }

  public static TclTree allocateContainer(String name, String indexType,
      Expression initReaders, Expression initWriters) {
    return new Command(ALLOCATE_CONTAINER_CUSTOM,
                       new Token(name), new Token(indexType),
                       initReaders, initWriters);
  }
  
  public static TclTree allocateFile(Value mapVar, String tclName,
      Expression initReaders) {
    if (mapVar != null) {
      return new Command(ALLOCATE_FILE,
            new Token(tclName), mapVar, initReaders);
    } else {
      return new Command(ALLOCATE_FILE,
              new Token(tclName), TclString.EMPTY, initReaders);
    }
  }

  public static SetVariable stackLookup(String stackName,
      String tclVarName, String containerVarName) {
    Token v = new Token(containerVarName);
    Square square = new Square(STACK_LOOKUP,
            new Value(stackName), v);
    SetVariable sv = new SetVariable(tclVarName, square);
    return sv;
  }

  public static SetVariable lookupParentStack(String parentScope,
      String childScope) {
    Square square = new Square(STACK_LOOKUP,
            new Value(childScope),
            PARENT_STACK_ENTRY);
    SetVariable sv = new SetVariable(parentScope, square);
    return sv;
  }

  /**
     Do a data get operation to load the value from the TD
   */
  public static SetVariable integerGet(String target, Value variable) {
    return new SetVariable(target, new Square(RETRIEVE_INTEGER, variable));
  }

  public static SetVariable refGet(String target, Value variable) {
   return new SetVariable(target, new Square(RETRIEVE_UNTYPED, variable));
  }

  public static Command stringSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_STRING, turbineDstVar, src);
  }

  public static Command integerSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_INTEGER, turbineDstVar, src);
  }
  
  public static TclTree voidSet(Value voidVar) {
    return new Command(STORE_VOID, voidVar);
  }

  public static Command floatSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_FLOAT, turbineDstVar, src);
  }
  
  public static Command updateableFloatInit(Value turbineDstVar, Expression src) {
    return new Command(INIT_UPD_FLOAT, turbineDstVar, src);
  }

  public static SetVariable floatGet(String target, Value variable) {
    return floatGet(target, variable, CacheMode.CACHED);
  }
  public static SetVariable floatGet(String target, Value variable,
                                                    CacheMode caching) {
    if (caching == CacheMode.CACHED) {
      return new SetVariable(target,
            new Square(RETRIEVE_FLOAT, variable));
    } else {
      assert(caching == CacheMode.UNCACHED);
      return new SetVariable(target,
              new Square(RETRIEVE_FLOAT, variable, UNCACHED_MODE));
    }
  }

  /**
   * Do a data get operation to load the value from the TD
   */
  public static SetVariable stringGet(String target, Value variable) {
    return new SetVariable(target, new Square(RETRIEVE_STRING, variable));
  }

  public static SetVariable blobGet(String target, Value var) {
    return new SetVariable(target, new Square(RETRIEVE_BLOB, var));
  }

  public static Command freeBlob(Value var) {
    return new Command(FREE_BLOB, var);
  }
  
  public static Command freeLocalBlob(Value var) {
    return new Command(FREE_LOCAL_BLOB, var);
  }

  public static Command blobSet(Value target, Expression src) {
    // Calling convention requires separate pointer and length args
    return new Command(STORE_BLOB, target, src);
  }

  public static SetVariable integerDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_INTEGER, src, decr));
  }

  public static SetVariable floatDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_FLOAT, src, decr));
  }
  
  public static SetVariable stringDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_STRING, src, decr));
  }

  public static SetVariable blobDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_BLOB, src, decr));
  }
  
  public static SetVariable fileDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, Square.fnCall(GET_FILE, src, decr));
  }

  private static Value tclRuleType (TaskMode t) {
    switch (t) {
    case LOCAL:
    case LOCAL_CONTROL:
      return new Value("turbine::LOCAL");
    case CONTROL:
      return new Value("turbine::CONTROL");
    case LEAF:
      return new Value("turbine::WORK");
    default:
      throw new STCRuntimeError("Unexpected rule type: " + t);
    }
  }


  /**
   * Generate code for a rule
   * @param symbol
   * @param inputs
   * @param action the action, using a tcl list to ensure proper escaping
   * @param type
   * @return
   */
  private static Command ruleHelper(String symbol, 
      List<? extends Expression> inputs,
      TclList action, TaskMode type, Target target, boolean local) {
    Token s = new Token(symbol);
    TclList i = new TclList(inputs);
    
    return new Command(local ? C_RULE : RULE, s, i,
                       tclRuleType(type), target.toTcl(), action);
  }

  /**
   * @param symbol
   * @param blockOn
   * @param action
   * @param mode
   * @return
   */
  public static Command rule(String symbol,
      List<? extends Expression> blockOn, TclList action, TaskMode mode,
      Target target, 
      boolean local) {
    return ruleHelper(symbol, blockOn, action, mode, target, local);
  }

  public static Command deepRule(String symbol,
      List<? extends Expression> inputs, int[] depths, boolean[] isFile,
      TclList action, TaskMode mode, boolean local) {
    assert(inputs.size() == depths.length);
    assert(inputs.size() == isFile.length);
    
    List<Expression> depthExprs = new ArrayList<Expression>(depths.length);
    List<Expression> isFileExprs = new ArrayList<Expression>(isFile.length);
    
    for (int depth: depths) {
      depthExprs.add(new LiteralInt(depth));
    }
    
    for (boolean b: isFile) {
      isFileExprs.add(LiteralInt.boolValue(b));
    }
    
    return new Command(DEEPRULE, new Token(symbol),
          new TclList(inputs), new TclList(depthExprs), new TclList(isFileExprs),
          tclRuleType(mode), action);
  }
  
  public static Command loopRule(String symbol,
      List<Value> args, List<? extends Expression> blockOn) {
    List<Expression> actionElems = new ArrayList<Expression>();
    actionElems.add(new Token(symbol));
    for (Value arg: args) {
      actionElems.add(arg);
    }
    TclList action = new TclList(actionElems);
    return ruleHelper(symbol, blockOn, action, TaskMode.CONTROL, 
                      Target.rankAny(), true);
  }

  public static TclTree allocateStruct(String tclName) {
    Square createExpr = new Square(new Token("dict"), new Token("create"));
    return new SetVariable(tclName, createExpr);
  }
  
  /**
   * Insert src into struct at container.field
   * @param container
   * @param field
   * @param src
   */
  public static Sequence structInsert(String container, String field,
                    String src) {
    Sequence result = new Sequence();
    Command storeCmd = new Command(
        new Token("dict"), new Token("set"), 
        new Token(container), new TclString(field, true), new Value(src));
  
    result.add(storeCmd);
    return result;
  
  }

  public static Sequence structLookupFieldID(String structName, String structField,
      String resultVar) {
    Sequence result = new Sequence();

    Square containerGet = new Square(new Token("dict"), new Token("get"),
        new Value(structName), new TclString(structField, true));

    SetVariable loadCmd = new SetVariable(resultVar, containerGet);
    result.add(loadCmd);
    return result;
  }


  public static Command structRefLookupFieldID(String structName, String structField,
      String resultVar, String resultTypeName) { 
    Command lookup = new Command(new Token("turbine::struct_ref_lookup"),
            new Value(structName), new TclString(structField, true),
            new Value(resultVar), new TclString(resultTypeName, true));

    return lookup;
  }


  /**
   * Put reference to arrayVar[arrayIndex] into refVar once it is ready
   * @param refVar
   * @param arrayIndex
   * @param isArrayRef
   * @return
   */
  public static Command arrayLookupImmIx(String refVar, 
      boolean refIsString, String arrayVar,
      Expression arrayIndex, boolean isArrayRef) {
    Token refType = refIsString ?  new Token(STRING_TYPENAME) 
                                : new Token(INTEGER_TYPENAME); 
    
    // set up reference to point to array data
    if (isArrayRef) {
      return new Command(CREF_LOOKUP_LITERAL, NO_STACK,
          new TclList(),  new TclList(new Value(arrayVar),
          arrayIndex, new Value(refVar), refType));
    } else {
      return new Command(CONTAINER_REFERENCE, new Value(arrayVar),
        arrayIndex, new Value(refVar), refType);
    }
  }

  /**
   * Lookup arrayVar[arrayIndex] right away, regardless of whether
   * it is closed
   */
  public static SetVariable arrayLookupImm(String dst, String arrayVar,
      Expression arrayIndex) {
    return new SetVariable(dst,
        new Square(CONTAINER_LOOKUP_CHECKED, new Value(arrayVar), arrayIndex));
  }

  /**
   * Put a reference to arrayVar[indexVar] into refVar
   * @param refVar
   * @param arrayVar
   * @param indexVar
   * @param isArrayRef
   * @return
   */
  public static Command arrayLookupComputed(String refVar,
                          boolean refIsString,
                          String arrayVar,
                          String indexVar, boolean isArrayRef) {
    Token refType = refIsString ?  new Token(STRING_TYPENAME) 
                                : new Token(INTEGER_TYPENAME); 
    if (isArrayRef) {
      return new Command(CREF_F_LOOKUP, NO_STACK,
          new TclList(), new TclList(new Value(arrayVar), new Value(indexVar),
              new Value(refVar), refType));
    } else {
      return new Command(CONTAINER_F_REFERENCE, NO_STACK,
         new TclList(), new TclList(new Value(arrayVar), new Value(indexVar),
             new Value(refVar), refType));
    }
   }

   public static Command dereferenceInteger(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_INTEGER, NO_STACK, dstVar, refVar);
   }

   public static Command dereferenceFloat(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_FLOAT, NO_STACK, dstVar, refVar);
   }

   public static Command dereferenceString(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_STRING, NO_STACK,
         dstVar, refVar);
   }
   
   public static Command dereferenceBlob(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_BLOB, NO_STACK, dstVar, refVar);
   }
   
   public static Command dereferenceFile(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_FILE, NO_STACK, dstVar, refVar);
   }

  public static Command arrayStoreImmediate(String srcVar, String arrayVar,
                              Expression arrayIndex, Expression writersDecr) {
    return new Command(CONTAINER_IMMEDIATE_INSERT,
        new Value(arrayVar), arrayIndex, new Value(srcVar), writersDecr);
  }

  public static Command arrayDerefStore(String srcRefVar, String arrayVar,
      Expression arrayIndex, Expression writersDecr) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar),
                      arrayIndex, new Value(srcRefVar));
    return new Command(CONTAINER_DEREF_INSERT, NO_STACK, outputs, inputs,
                       writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayDerefStoreComputed(String srcRefVar, String arrayVar,
      String indexVar, Expression writersDecr) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
                     new Value(srcRefVar));
    return new Command(CONTAINER_F_DEREF_INSERT, NO_STACK, outputs, inputs,
                       writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayStoreComputed(String srcVar, String arrayVar,
                                                    String indexVar, Expression writersDecr) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
          new Value(srcVar));
    // Don't increment writers count, this is done in IC
    return new Command(CONTAINER_F_INSERT, NO_STACK, outputs, inputs,
                       writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayRefStoreImmediate(String srcVar, String arrayVar,
      Expression arrayIndex, String outerArray) {
    return new Command(new Token("turbine::cref_insert"),
                    NO_STACK, new TclList(), new TclList(
                    new Value(arrayVar), arrayIndex, new Value(srcVar),
                    new Value(outerArray)),
                    LiteralInt.FALSE);
  }


  public static Command arrayRefStoreComputed(String srcVar, String arrayVar,
      String indexVar, String outerArray) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
        new Value(srcVar), new Value(outerArray));
    return new Command(new Token("turbine::f_cref_insert"),
                        NO_STACK, outputs, inputs,
                        LiteralInt.FALSE);
  }

  public static Command arrayRefDerefStore(String srcRefVar, String arrayVar,
      Expression arrayIndex, String outerArrayVar) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar),
        arrayIndex, new Value(srcRefVar), new Value(outerArrayVar));
    return new Command(new Token("turbine::cref_deref_insert"),
                                  NO_STACK, outputs, inputs,
                                  LiteralInt.FALSE);
  }

  public static Command arrayRefDerefStoreComputed(String srcRefVar, String arrayVar,
      String indexVar, String outerArrayVar) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
        new Value(srcRefVar), new Value(outerArrayVar));
    return new Command(new Token("turbine::cref_f_deref_insert"),
                                    NO_STACK, outputs, inputs,
                                    LiteralInt.FALSE);
  }

  public static TclTree containerCreateNested(String resultVar,
        String containerVar, String indexVar) {
    return new Command(F_CONTAINER_CREATE_NESTED,
            new Token(resultVar), new Value(containerVar),
            new Value(indexVar), new Token(INTEGER_TYPENAME));
  }

  public static TclTree containerRefCreateNested(String resultVar,
      Value containerVar, Value indexVar, Value outerArr) {
    return new Command(F_CREF_CREATE_NESTED,
          new Token(resultVar), containerVar,
          indexVar, new Token(INTEGER_TYPENAME), outerArr, LiteralInt.FALSE);
  }

  public static TclTree containerRefCreateNestedImmIx(String resultVar,
      String containerVar, Expression arrIx, Value outerArr) {
    return new Command(F_CREF_CREATE_NESTED_STATIC,
        new Token(resultVar), new Value(containerVar),
        arrIx, new Token(INTEGER_TYPENAME), outerArr, LiteralInt.FALSE);
  }

  public static TclTree containerCreateNestedImmIx(String resultVar,
      String containerVar, Expression arrIx) {
    return new SetVariable(resultVar,
        new Square(F_CONTAINER_CREATE_NESTED_STATIC,
            new Value(containerVar), arrIx, new Token(INTEGER_TYPENAME)));
  }

  public static TclTree containerSlotCreate(Value arr) {
    return new Command(CONTAINER_SLOT_CREATE, arr);
  }
  
  public static TclTree containerSlotCreate(Value arr, Expression incr) {
    return new Command(CONTAINER_SLOT_CREATE, arr, incr);
  }

  public static TclTree decrArrayWriters(Value arr) {
    return new Command(CONTAINER_SLOT_DROP, arr);
  }
  
  public static TclTree containerSlotDrop(Value arr, Expression decr) {
    return new Command(CONTAINER_SLOT_DROP, arr, decr);
  }
  
  public static Command enableReferenceCounting() {
    return new Command("turbine::enable_read_refcount");
  }

  /**
   * Modify reference count by amount
   * @param var
   * @param change
   * @return
   */
  public static TclTree incrRef(Expression var, Expression change) {
    try {
      if (Settings.getBoolean(Settings.EXPERIMENTAL_REFCOUNTING)) {
        if (change == null) {
          return new Command(REFCOUNT_INCR, var);
        } else {
          return new Command(REFCOUNT_INCR, var, change);
        }
      } else {
        return new Token("");
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }

  /**
   * Modify reference count by amount
   * @param var
   * @param change
   * @return
   */
  public static TclTree decrRef(Expression var, Expression change) {
    try {
      if (Settings.getBoolean(Settings.EXPERIMENTAL_REFCOUNTING)) {
        if (change == null) {
          return new Command(REFCOUNT_DECR, var);
        } else {
          return new Command(REFCOUNT_DECR, var, change);
        }
      } else {
        return new Token("");
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }
  
  public static TclTree incrRef(Value var) {
    return incrRef(var, new LiteralInt(1));
  }
  
  public static TclTree decrRef(Value var) {
    return decrRef(var, new LiteralInt(1));
  }
  
  /**
   * Modify reference count by amount
   * @param var
   * @param change
   * @return
   */
  public static TclTree incrFileRef(Expression var, Expression change) {
    try {
      if (Settings.getBoolean(Settings.EXPERIMENTAL_REFCOUNTING)) {
        if (change == null) {
          return new Command(FILE_REFCOUNT_INCR, var);
        } else {
          return new Command(FILE_REFCOUNT_INCR, var, change);
        }
      } else {
        return new Token("");
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }

  public static TclTree decrFileRef(Expression var, Expression change) {
    try {
      if (Settings.getBoolean(Settings.EXPERIMENTAL_REFCOUNTING)) {
        if (change == null) {
          return new Command(FILE_REFCOUNT_DECR, var);
        } else {
          return new Command(FILE_REFCOUNT_DECR, var, change);
        }
      } else {
        return new Token("");
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }


  /**
   * Get entire contents of container
   * @param resultVar
   * @param arr
   * @param includeKeys
   * @return
   */
  public static SetVariable containerContents(String resultVar,
                        Value arr, boolean includeKeys) {
    Token mode = includeKeys ? new Token("dict") : new Token("members");
    return new SetVariable(resultVar, new Square(
            CONTAINER_ENUMERATE, arr, mode, new Token("all"),
                                          new LiteralInt(0)));
  }
  
  public static  Expression dictSize(Value tclDict) {
    Expression containerSize = Square.fnCall("dict",  new Token("size"), tclDict);
    return containerSize;
  }

  /**
   * Return the size of a container
   * @param resultVar
   * @param arr
   * @return
   */
  public static SetVariable containerSize(String resultVar,
        Value arr) {
    return new SetVariable(resultVar, new Square(
        CONTAINER_ENUMERATE, arr, new Token("count"), new Token("all"),
                                      new LiteralInt(0)));
  }

  /**
   * Retrieve partial contents of container from start to end inclusive
   * start to end are not the logical array indices, but rather physical indices
   * @param resultVar
   * @param arr
   * @param includeKeys
   * @param start
   * @param len
   * @return
   */
  public static SetVariable containerContents(String resultVar,
          Value arr, boolean includeKeys, Expression start, Expression len) {
    Token mode = includeKeys ? new Token("dict") : new Token("members");
    return new SetVariable(resultVar, new Square(
              CONTAINER_ENUMERATE, arr, mode, start, len));
  }

  public static Command turbineLog(String msg) {
    return new Command(TURBINE_LOG, new TclString(msg, true));
  }

  public static Command turbineLog(String... tokens) {
    return new Command(TURBINE_LOG, new TclList(tokens));
  }

  public static TclTree turbineLog(List<Expression> logMsg) {
    return new Command(TURBINE_LOG, new TclList(logMsg));
  }

  public static TclTree declareReference(String refVarName) {
    return allocate(refVarName, INTEGER_TYPENAME);
  }

  public static TclTree callFunction(String function, TclList oList,
                                      TclList iList, TclList blockOn) {
    return new Command(CALL_FUNCTION,
        new Value(Turbine.LOCAL_STACK_NAME), new Token(function),
                  oList, iList, blockOn);
  }


  public static TclTree callFunctionSync(String function,
      List<Expression> outVars, List<Expression> inVars) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(new Value(Turbine.LOCAL_STACK_NAME));
    args.addAll(outVars);
    args.addAll(inVars);
    return new Command(function, args);
  }

  public static Command makeTCLGlobal(String tclName) {
    return new Command(new Token("global"), new Token(tclName));
  }

  public static Square modInteger(Expression a, Expression b) {
    return new Square(new Expression[] {MOD_INTEGER, a, b});
  }

  public static Square divideInteger(Expression a, Expression b) {
    return new Square(new Expression[] {DIVIDE_INTEGER, a, b});
  }
  
  /**
   * @param cmd
   * @param stderrFilename 
   * @param stdoutFilename 
   * @param args
   * @return Tcl code to execute external executable 
   */
  public static Command exec(String cmd, Expression stdinFilename,
          Expression stdoutFilename, Expression stderrFilename,
                             List<Expression> args) {
    ArrayList<Expression> args2 = new ArrayList<Expression>(args.size() + 4);
    args2.add(new TclString(cmd, true));
    
    Dict kwOpts = execKeywordOpts(stdinFilename, stdoutFilename, stderrFilename);
    args2.add(kwOpts);
    args2.addAll(args);
    return new Command(EXEC_EXTERNAL, args2);
  }

  public static Dict execKeywordOpts(Expression stdinFilename,
      Expression stdoutFilename, Expression stderrFilename) {
    ArrayList<Pair<String, Expression>> keywordOpts =
                              new ArrayList<Pair<String,Expression>>();
    if (stdinFilename != null) {
      keywordOpts.add(Pair.create("stdin", stdinFilename));
    }
    if (stdoutFilename != null) {
      keywordOpts.add(Pair.create("stdout", stdoutFilename));
    }
    if (stderrFilename != null) {
      keywordOpts.add(Pair.create("stderr", stderrFilename));
    }
    Dict kwOpts = Dict.dictCreateSE(keywordOpts);
    return kwOpts;
  }
  

  /**
   * Expression that extracts the void status variable for
   * a file variable
   * @param fileVar
   * @return
   */
  public static Expression getFileStatus(Value fileVar) {
    return new Square(new Token("turbine::get_file_status"), fileVar);
  }
  
  /**
   * Expression that extracts the filename string future
   * a file variable
   * @param fileVar
   * @param initUnmapped 
   * @return
   */
  public static Expression getFileName(Value fileVar, boolean initUnmapped) {
    if (initUnmapped) {
      return new Square(GET_OUTPUT_FILE_PATH, fileVar);
    } else {
      return new Square(GET_FILE_PATH, fileVar);
    }
  }
  
  /**
   * Command to clsoe a file
   * @param fileVar
   * @return
   */
  public static Command closeFile(Value fileVar) {
    return new Command(new Token("turbine::close_file"), fileVar);
  }

  public static TclTree resetPriority() {
    return new Command("turbine::reset_priority");
  }

  public static TclTree setPriority(Expression priority) {
    return new Command("turbine::set_priority", Arrays.asList(priority));
  }

  public static Expression unpackArray(Expression array, int nestLevel,
                                       boolean isFile) {
    assert(nestLevel >= 0);
    return Square.fnCall("turbine::unpack_args", array,
          new LiteralInt(nestLevel), LiteralInt.boolValue(isFile));
  }

  public static Command fileSet(Value fileFuture, String localFileName) {
    return new Command("turbine::set_file", Arrays.asList(fileFuture, new Token(localFileName)));
  }

  public static TclTree fileGet(String prefixVar, Value varToExpr) {
    return new SetVariable(prefixVar, 
        Square.fnCall(GET_FILE, varToExpr));
  }
  
  public static Command decrLocalFileRef(String localFileName) {
    return new Command("turbine::decr_local_file_refcount", new Token(localFileName));
  }

  public static SetVariable createLocalFile(String varName, Expression fileName) {
    return new SetVariable(varName, Square.fnCall("turbine::create_local_file_ref",
                                                  fileName));
  }

  public static SetVariable mkTemp(String varName) {
    return new SetVariable(varName, Square.fnCall("turbine::mktemp"));
  }

  public static Command setFilenameVal(Value fileFuture,
                                       Expression filenameVal) {
    return new Command("turbine::set_filename_val",
                       Arrays.asList(fileFuture, filenameVal));
  }

  
  public static Command arrayBuild(Value array, List<Expression> arrMemExprs,
      boolean close) {
    return new Command("turbine::array_build", Arrays.asList(
          array, new TclList(arrMemExprs), LiteralInt.boolValue(close)));
  }
}
