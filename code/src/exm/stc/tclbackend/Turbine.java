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
import exm.stc.common.lang.ExecContext;
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
class Turbine {
  /* Names of types used by Turbine */
  public static final String STRING_TYPENAME = "string";
  public static final String INTEGER_TYPENAME = "integer";
  public static final String VOID_TYPENAME = "void";
  public static final String FLOAT_TYPENAME = "float";
  public static final String BLOB_TYPENAME = "blob";

  private static final Token ALLOCATE_CONTAINER_CUSTOM =
      turbFn("allocate_container_custom");
  private static final Token ALLOCATE_FILE =  turbFn("allocate_file2");
  private static final Token ALLOCATE_CUSTOM = turbFn("allocate_custom");
  private static final Token MULTICREATE = adlbFn("multicreate");
  
  // Container insert
  private static final Token C_INSERT = turbFn("c_v_insert");
  private static final Token C_F_INSERT = turbFn("c_f_insert");
  private static final Token C_IMM_INSERT = turbFn("container_insert");
  private static final Token C_DEREF_INSERT = turbFn("c_v_deref_insert");
  private static final Token C_F_DEREF_INSERT = turbFn("c_f_deref_insert");
  private static final Token ARRAY_BUILD = turbFn("array_build");
  
  // Container nested creation
  private static final Token C_CREATE_NESTED = turbFn("c_v_create");
  private static final Token C_F_CREATE_NESTED = turbFn("c_f_create");
  private static final Token CR_CREATE_NESTED = turbFn("cr_v_create");
  private static final Token CR_F_CREATE_NESTED = turbFn("cr_f_create");
  
  // Container lookup
  private static final Token C_LOOKUP_CHECKED = turbFn("container_lookup_checked");
  private static final Token C_REFERENCE = turbFn("container_reference");
  private static final Token C_F_REFERENCE = turbFn("c_f_reference");
  private static final TclTree CR_LOOKUP = turbFn("cr_v_lookup");
  private static final Token CR_F_LOOKUP = turbFn("cr_f_lookup");
  private static final Token CONTAINER_ENUMERATE = adlbFn("enumerate");

  // Container reference counting
  private static final Token C_SLOT_CREATE = adlbFn("slot_create");
  private static final Token C_SLOT_DROP = adlbFn("slot_drop");
  
  // Retrieve functions
  private static final Token RETRIEVE_UNTYPED = turbFn("retrieve");
  private static final Token RETRIEVE_INTEGER = turbFn("retrieve_integer");
  private static final Token RETRIEVE_FLOAT = turbFn("retrieve_float");
  private static final Token RETRIEVE_STRING = turbFn("retrieve_string");
  private static final Token RETRIEVE_BLOB = turbFn("retrieve_blob");
  private static final Token CACHED = new Token("CACHED");
  private static final Token UNCACHED_MODE = new Token("UNCACHED");
  
  /**
   * Used to specify what caching is allowed for retrieve
   * @author tga
   */
  public enum CacheMode {
    CACHED,
    UNCACHED
  }
  
  // Store functions
  private static final Token STORE_INTEGER = turbFn("store_integer");
  private static final Token STORE_VOID = turbFn("store_void");
  private static final Token STORE_FLOAT = turbFn("store_float");
  private static final Token STORE_STRING = turbFn("store_string");
  private static final Token STORE_BLOB = turbFn("store_blob");
  private static final Token INIT_UPD_FLOAT = turbFn("init_updateable_float");

  // Rule functions
  private static final Token SPAWN_RULE = turbFn("spawn_rule");
  private static final Token RULE = turbFn("rule");
  private static final Token DEEPRULE = turbFn("deeprule");
  private static final Token ADLB_SPAWN = adlbFn("spawn");
  private static final LiteralInt TURBINE_NULL_RULE = new LiteralInt(-1);
  
  // Dereference functions
  private static final Token DEREFERENCE_INTEGER = turbFn("f_dereference_integer");
  private static final Token DEREFERENCE_FLOAT = turbFn("f_dereference_float");
  private static final Token DEREFERENCE_STRING = turbFn("f_dereference_string");
  private static final Token DEREFERENCE_BLOB = turbFn("f_dereference_blob");
  private static final Token DEREFERENCE_FILE = turbFn("f_dereference_file");
  
  // Callstack functions
  private static final Token STACK_LOOKUP = turbFn("stack_lookup");
  static final String LOCAL_STACK_NAME = "stack";
  static final Value LOCAL_STACK_VAL = new Value(LOCAL_STACK_NAME, false, true);
  static final String PARENT_STACK_NAME = "stack";
  private static final Value STACK = new Value(LOCAL_STACK_NAME);
  private static final Value PARENT_STACK = new Value(PARENT_STACK_NAME);
  private static final Token PARENT_STACK_ENTRY = new Token("_parent");
  private static final Token NO_STACK = new Token("no_stack");
   
  public enum StackFrameType {
    MAIN,
    FUNCTION,
    NESTED
  }

  // Types
  public static final Value ADLB_NULL_TYPE = adlbConst("NULL_TYPE");
  public static final Value ADLB_FLOAT_TYPE = adlbConst("FLOAT");
  public static final Value ADLB_INT_TYPE = adlbConst("INTEGER");
  public static final Value ADLB_STRING_TYPE = adlbConst("STRING");
  public static final Value ADLB_BLOB_TYPE = adlbConst("BLOB");
  public static final Value ADLB_CONTAINER_TYPE = adlbConst("CONTAINER");

  // Custom implementations of operators
  private static final Token DIVIDE_INTEGER = turbFn("divide_integer_impl");
  private static final Token MOD_INTEGER = turbFn("mod_integer_impl");
  
  // Refcounting
  private static final Token ENABLE_READ_REFCOUNT = turbFn("enable_read_refcount");
  private static final Token REFCOUNT_INCR = turbFn("read_refcount_incr");
  private static final Token REFCOUNT_DECR = turbFn("read_refcount_decr");
  private static final Token FILE_REFCOUNT_INCR = turbFn("file_read_refcount_incr");
  private static final Token FILE_REFCOUNT_DECR = turbFn("file_read_refcount_decr");
  private static final Token FREE_LOCAL_BLOB = turbFn("free_local_blob");
  
  // Files
  private static final Token GET_FILE = turbFn("get_file");
  private static final Token SET_FILE = turbFn("set_file");
  private static final Token GET_OUTPUT_FILE_PATH = turbFn("get_output_file_path");
  private static final Token GET_FILE_PATH = turbFn("get_file_path");
  private static final Token CREATE_LOCAL_FILE_REF = turbFn("create_local_file_ref");
  private static final Token DECR_LOCAL_FILE_REFCOUNT = turbFn("decr_local_file_refcount");
  private static final Token MKTEMP = turbFn("mktemp");
  private static final Token SET_FILENAME_VAL = turbFn("set_filename_val");

  // External apps
  private static final Token UNPACK_ARGS = turbFn("unpack_args");
  private static final Token EXEC_EXTERNAL = turbFn("exec_external");

  // Task priorities
  private static final Token SET_PRIORITY = turbFn("set_priority");
  private static final Token RESET_PRIORITY = turbFn("reset_priority");
  private static final String TCLTMP_PRIO = "tcltmp:prio";
  private static final Value TCLTMP_PRIO_VAL = new Value(TCLTMP_PRIO, false, true);
  
  // Special values
  public static final LiteralInt VOID_DUMMY_VAL = new LiteralInt(12345);
  public static final Value ADLB_NULL_ID = adlbConst("NULL_ID");

  // Misc
  private static final Token TURBINE_LOG = turbFn("c::log");
  
  private static Token turbFn(String functionName) {
    return new Token("turbine::" + functionName);
  }
  
  private static Token adlbFn(String functionName) {
    return new Token("adlb::" + functionName);
  }  

  private static Value turbConst(String name) {
    return new Value("turbine::" + name);
  }
  
  private static Value adlbConst(String name) {
    return new Value("adlb::" + name);
  }
  
  
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
      result[index++] = new Command(C_INSERT, STACK,
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
      new Command(C_INSERT, STACK, name, value);
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
  
  public static Command freeLocalBlob(Value var) {
    return new Command(FREE_LOCAL_BLOB, var);
  }

  public static Command blobSet(Value target, Expression src) {
    // Calling convention requires separate pointer and length args
    return new Command(STORE_BLOB, target, src);
  }

  public static SetVariable integerDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_INTEGER, src, CACHED, decr));
  }

  public static SetVariable floatDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_FLOAT, src, CACHED, decr));
  }
  
  public static SetVariable stringDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_STRING, src, CACHED, decr));
  }

  public static SetVariable blobDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_BLOB, src, decr));
  }
  
  public static SetVariable fileDecrGet(String target, Value src,
      Expression decr) {
    return new SetVariable(target, new Square(GET_FILE, src, decr));
  }

  private static Value tclRuleType (TaskMode t) {
    switch (t) {
    case LOCAL:
    case LOCAL_CONTROL:
      return turbConst("LOCAL");
    case CONTROL:
      return turbConst("CONTROL");
    case WORKER:
      return turbConst("WORK");
    default:
      throw new STCRuntimeError("Unexpected rule type: " + t);
    }
  }


  /**
   * Generate code for a rule
   * @param symbol
   * @param inputs
   * @param action tokens making up the action
   * @param type
   * @return
   */
  private static Sequence ruleHelper(String symbol, 
      List<? extends Expression> inputs,
      List<Expression> action, TaskMode type, Target target,
      Expression parallelism,
      Expression priority, ExecContext execCx) {

    if (inputs.isEmpty()) {
      if (type != TaskMode.LOCAL && type != TaskMode.LOCAL_CONTROL) {
        return spawnTask(action, type, target, priority, execCx);
      }
    }
    
    Sequence res = new Sequence();    
    if (priority != null)
       res.add(setPriority(priority));
    // Use different command on worker
    Token ruleCmd = execCx == ExecContext.CONTROL ? RULE : SPAWN_RULE;
    
    List<Expression> kwArgs = new ArrayList<Expression>();
    if (!target.rankAny) {
      kwArgs.add(new Token("target"));
      kwArgs.add(target.toTcl());
    }
    
    if (type != TaskMode.LOCAL && type != TaskMode.CONTROL) {
      kwArgs.add(new Token("type"));
      kwArgs.add(tclRuleType(type));
    }
    
    if (parallelism != null && !LiteralInt.ONE.equals(parallelism)) {
      kwArgs.add(new Token("parallelism"));
      kwArgs.add(parallelism);
    }
    
    res.add(new Command(ruleCmd,  new TclList(inputs),
                        TclUtil.tclStringAsList(action)));

    if (priority != null)
       res.add(resetPriority());
    return res;
  }

  private static Sequence spawnTask(List<Expression> action, TaskMode type, Target target,
      Expression priority, ExecContext execCx) {
    Sequence res = new Sequence();
    
    // Store in var for readability
    Value priorityVar = null;
    if (priority != null) {
      priorityVar = TCLTMP_PRIO_VAL;
      res.add(new SetVariable(TCLTMP_PRIO, priority));
    }
    
    List<Expression> taskTokens = new ArrayList<Expression>();
    // Different task formats for work types
    if (type == TaskMode.WORKER) {
      taskTokens.add(TURBINE_NULL_RULE);
      taskTokens.addAll(action);
    } else {
      assert(type == TaskMode.CONTROL);
      taskTokens.add(new Token("command"));
      if (priority != null) {
        taskTokens.add(new Token("priority:"));
        taskTokens.add(priorityVar);
      }
      taskTokens.addAll(action);
    }
    // add to shared work queue
    if (priority != null)
      res.add(setPriority(priorityVar));
    res.add(new Command(ADLB_SPAWN, adlbWorkType(type),
                          TclUtil.tclStringAsList(taskTokens)));
    if (priority != null)
      res.add(resetPriority());
    return res;
  }

  private static TclTree adlbWorkType(TaskMode type) {
    switch (type) {
      case CONTROL:
        return new Value("turbine::CONTROL_TASK");
      case WORKER:
        return new Value("turbine::WORK_TASK");
      default:
        throw new STCRuntimeError("Can't create task of type " + type);
    }
  }
  

  public static Expression currentPriority() {
    // get the current turbine priority
    return new Square("turbine::get_priority");
  }


  /**
   * @param symbol
   * @param blockOn
   * @param action
   * @param mode
   * @return
   */
  public static Sequence rule(String symbol,
      List<? extends Expression> blockOn, List<Expression> action, TaskMode mode,
      Target target, Expression priority, ExecContext execCx) {
    return ruleHelper(symbol, blockOn, action, mode, target, null,
                      priority, execCx);
  }

  public static Sequence deepRule(String symbol,
      List<? extends Expression> inputs, int[] depths, boolean[] isFile,
      List<Expression> action, TaskMode mode, Expression priority, ExecContext execCx) {
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
    
    Sequence res = new Sequence();
    if (priority != null)
       res.add(setPriority(priority));
    res.add(new Command(DEEPRULE, new Token(symbol),
          new TclList(inputs), new TclList(depthExprs), new TclList(isFileExprs),
          tclRuleType(mode), TclUtil.tclStringAsList(action)));
    if (priority != null)
      res.add(resetPriority());
    return res;
  }
  
  public static Sequence loopRule(String symbol,
      List<Value> args, List<? extends Expression> blockOn,
      ExecContext execCx) {
    // Assume executes on control for now
    assert (execCx == ExecContext.CONTROL);
    
    List<Expression> action = new ArrayList<Expression>();
    action.add(new Token(symbol));
    for (Value arg: args) {
      action.add(arg);
    }
    return ruleHelper(symbol, blockOn, action, TaskMode.CONTROL, 
                      Target.RANK_ANY, null, null, execCx);
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
    Command lookup = new Command(turbFn("struct_ref_lookup"),
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
      return new Command(CR_LOOKUP, NO_STACK,
          new TclList(),  new TclList(new Value(arrayVar),
          arrayIndex, new Value(refVar), refType));
    } else {
      return new Command(C_REFERENCE, new Value(arrayVar),
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
        new Square(C_LOOKUP_CHECKED, new Value(arrayVar), arrayIndex));
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
      return new Command(CR_F_LOOKUP, NO_STACK,
          new TclList(), new TclList(new Value(arrayVar), new Value(indexVar),
              new Value(refVar), refType));
    } else {
      return new Command(C_F_REFERENCE, NO_STACK,
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
    return new Command(C_IMM_INSERT,
        new Value(arrayVar), arrayIndex, new Value(srcVar), writersDecr);
  }

  public static Command arrayDerefStore(String srcRefVar, String arrayVar,
      Expression arrayIndex, Expression writersDecr) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar),
                      arrayIndex, new Value(srcRefVar));
    return new Command(C_DEREF_INSERT, NO_STACK, outputs, inputs,
                       writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayDerefStoreComputed(String srcRefVar, String arrayVar,
      String indexVar, Expression writersDecr) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
                     new Value(srcRefVar));
    return new Command(C_F_DEREF_INSERT, NO_STACK, outputs, inputs,
                       writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayStoreComputed(String srcVar, String arrayVar,
                                                    String indexVar, Expression writersDecr) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
          new Value(srcVar));
    // Don't increment writers count, this is done in IC
    return new Command(C_F_INSERT, NO_STACK, outputs, inputs,
                       writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayRefStoreImmediate(String srcVar, String arrayVar,
      Expression arrayIndex, String outerArray) {
    return new Command(turbFn("cref_insert"),
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
    return new Command(turbFn("f_cref_insert"),
                        NO_STACK, outputs, inputs,
                        LiteralInt.FALSE);
  }

  public static Command arrayRefDerefStore(String srcRefVar, String arrayVar,
      Expression arrayIndex, String outerArrayVar) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar),
        arrayIndex, new Value(srcRefVar), new Value(outerArrayVar));
    return new Command(turbFn("cref_deref_insert"),
                                  NO_STACK, outputs, inputs,
                                  LiteralInt.FALSE);
  }

  public static Command arrayRefDerefStoreComputed(String srcRefVar, String arrayVar,
      String indexVar, String outerArrayVar) {
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
        new Value(srcRefVar), new Value(outerArrayVar));
    return new Command(turbFn("cref_f_deref_insert"),
                                    NO_STACK, outputs, inputs,
                                    LiteralInt.FALSE);
  }

  public static TclTree containerCreateNested(String resultVar,
        String containerVar, String indexVar) {
    return new Command(C_F_CREATE_NESTED,
            new Token(resultVar), new Value(containerVar),
            new Value(indexVar), new Token(INTEGER_TYPENAME));
  }

  public static TclTree containerRefCreateNested(String resultVar,
      Value containerVar, Value indexVar, Value outerArr) {
    return new Command(CR_F_CREATE_NESTED,
          new Token(resultVar), containerVar,
          indexVar, new Token(INTEGER_TYPENAME), outerArr, LiteralInt.FALSE);
  }

  public static TclTree containerRefCreateNestedImmIx(String resultVar,
      String containerVar, Expression arrIx, Value outerArr) {
    return new Command(CR_CREATE_NESTED,
        new Token(resultVar), new Value(containerVar),
        arrIx, new Token(INTEGER_TYPENAME), outerArr, LiteralInt.FALSE);
  }

  public static TclTree containerCreateNestedImmIx(String resultVar,
      String containerVar, Expression arrIx) {
    return new SetVariable(resultVar,
        new Square(C_CREATE_NESTED,
            new Value(containerVar), arrIx, new Token(INTEGER_TYPENAME)));
  }

  public static TclTree containerSlotCreate(Value arr) {
    return new Command(C_SLOT_CREATE, arr);
  }
  
  public static TclTree containerSlotCreate(Value arr, Expression incr) {
    return new Command(C_SLOT_CREATE, arr, incr);
  }

  public static TclTree decrArrayWriters(Value arr) {
    return new Command(C_SLOT_DROP, arr);
  }
  
  public static TclTree containerSlotDrop(Value arr, Expression decr) {
    return new Command(C_SLOT_DROP, arr, decr);
  }
  
  public static Command enableReferenceCounting() {
    return new Command(ENABLE_READ_REFCOUNT);
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

  public static TclTree callFunctionSync(String function,
      List<Expression> outVars, List<Expression> inVars) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(LOCAL_STACK_VAL);
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
    return new Square(turbFn("get_file_status"), fileVar);
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

  public static TclTree resetPriority() {
    return new Command(RESET_PRIORITY);
  }

  public static TclTree setPriority(Expression priority) {
    return new Command(SET_PRIORITY, Arrays.asList(priority));
  }

  public static Expression unpackArray(Expression array, int nestLevel,
                                       boolean isFile) {
    assert(nestLevel >= 0);
    return new Square(UNPACK_ARGS, array,
          new LiteralInt(nestLevel), LiteralInt.boolValue(isFile));
  }

  public static Command fileSet(Value fileFuture, String localFileName) {
    return new Command(SET_FILE, fileFuture, new Token(localFileName));
  }

  public static TclTree fileGet(String prefixVar, Value varToExpr) {
    return new SetVariable(prefixVar,  new Square(GET_FILE, varToExpr));
  }
  
  public static Command decrLocalFileRef(String localFileName) {
    return new Command(DECR_LOCAL_FILE_REFCOUNT, new Token(localFileName));
  }

  public static SetVariable createLocalFile(String varName, Expression fileName) {
    return new SetVariable(varName, 
            new Square(CREATE_LOCAL_FILE_REF, fileName));
  }
  public static SetVariable mkTemp(String varName) {
    return new SetVariable(varName, new Square(MKTEMP));
  }

  public static Command setFilenameVal(Value fileFuture,
                                       Expression filenameVal) {
    return new Command(SET_FILENAME_VAL, fileFuture, filenameVal);
  }
  
  public static Command arrayBuild(Value array, List<Expression> arrMemExprs,
      boolean close) {
    return new Command(ARRAY_BUILD, array, new TclList(arrMemExprs),
                       LiteralInt.boolValue(close));
  }
  
  public static Command batchDeclare(List<String> batchedVarNames,
      List<TclList> batched) {
    ArrayList<Expression> exprs = new ArrayList<Expression>();
    
    
    List<Expression> multiCreateCall = new ArrayList<Expression>();
    multiCreateCall.add(MULTICREATE);
    multiCreateCall.addAll(batched);
    exprs.add(new Square(multiCreateCall));
    
    for (String varName: batchedVarNames) {
      exprs.add(new Token(varName));
    }
    return new Command("lassign",  exprs);
  }
  
  public static Command log(TclString logMsg) {
    return new Command(TURBINE_LOG, logMsg);
  }
}
