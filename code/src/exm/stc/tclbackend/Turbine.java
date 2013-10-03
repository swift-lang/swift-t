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
import exm.stc.common.lang.Location;
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
import exm.stc.tclbackend.tree.TclTarget;
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
  public static class TypeName extends Token {
    public TypeName(String token) {
      super(token);
    }
  }
  
  /* Names of types used by Turbine */
  public static final String INTEGER_TYPENAME = "integer";
  public static final TypeName ADLB_INT_TYPE = new TypeName(INTEGER_TYPENAME);
  public static final String FLOAT_TYPENAME = "float";
  public static final TypeName ADLB_FLOAT_TYPE = new TypeName(FLOAT_TYPENAME);
  public static final String STRING_TYPENAME = "string";
  public static final TypeName ADLB_STRING_TYPE = 
                                          new TypeName(STRING_TYPENAME);
  public static final String BLOB_TYPENAME = "blob";
  public static final TypeName ADLB_BLOB_TYPE = new TypeName(BLOB_TYPENAME);
  public static final String CONTAINER_TYPENAME = "container";
  public static final TypeName ADLB_CONTAINER_TYPE = 
                                          new TypeName(CONTAINER_TYPENAME);
  public static final String MULTISET_TYPENAME = "multiset";
  public static final TypeName ADLB_MULTISET_TYPE = 
                                          new TypeName(MULTISET_TYPENAME);
  public static final String REF_TYPENAME = "ref";
  public static final TypeName ADLB_REF_TYPE = new TypeName(REF_TYPENAME);
  public static final String FILE_REF_TYPENAME = "file_ref";
  public static final TypeName ADLB_FILE_REF_TYPE = 
                                          new TypeName(FILE_REF_TYPENAME);
  public static final String STRUCT_REF_TYPENAME = "struct";
  public static final TypeName ADLB_STRUCT_REF_TYPE = 
                                          new TypeName(STRUCT_REF_TYPENAME);


  private static final Token ALLOCATE_CONTAINER_CUSTOM =
      turbFn("allocate_container_custom");
  private static final Token ALLOCATE_FILE =  turbFn("allocate_file2");
  private static final Token ALLOCATE_CUSTOM = turbFn("allocate_custom");
  private static final Token MULTICREATE = adlbFn("multicreate");
  
  // Container insert
  private static final Token C_V_INSERT = turbFn("container_insert");
  private static final Token C_F_INSERT = turbFn("c_f_insert");
  private static final Token CR_V_INSERT = turbFn("cr_v_insert");
  private static final Token CR_F_INSERT = turbFn("cr_f_insert");
  private static final Token C_V_DEREF_INSERT = turbFn("c_v_insert_r");
  private static final Token C_F_DEREF_INSERT = turbFn("c_f_insert_r");
  private static final Token CR_V_DEREF_INSERT = turbFn("cr_v_insert_r");
  private static final Token CR_F_DEREF_INSERT = turbFn("cr_f_insert_r");
  private static final Token ARRAY_KV_BUILD = turbFn("array_kv_build");
  
  // Container nested creation
  private static final Token C_V_CREATE_NESTED = turbFn("create_nested");
  private static final Token C_F_CREATE_NESTED = turbFn("c_f_create");
  private static final Token CR_V_CREATE_NESTED = turbFn("cr_v_create");
  private static final Token CR_F_CREATE_NESTED = turbFn("cr_f_create");
  private static final Token C_V_CREATE_NESTED_BAG = turbFn("create_nested_bag");
  
  // Container lookup
  private static final Token C_LOOKUP_CHECKED = turbFn("container_lookup_checked");
  private static final Token C_REFERENCE = turbFn("container_reference");
  private static final Token C_F_LOOKUP = turbFn("c_f_lookup");
  private static final TclTree CR_V_LOOKUP = turbFn("cr_v_lookup");
  private static final Token CR_F_LOOKUP = turbFn("cr_f_lookup");
  private static final Token CONTAINER_ENUMERATE = adlbFn("enumerate");
  
  // Retrieve functions
  private static final Token RETRIEVE_INTEGER = turbFn("retrieve_integer");
  private static final Token RETRIEVE_FLOAT = turbFn("retrieve_float");
  private static final Token RETRIEVE_STRING = turbFn("retrieve_string");
  private static final Token RETRIEVE_BLOB = turbFn("retrieve_blob");
  private static final Token ACQUIRE_REF = turbFn("acquire_ref");
  private static final Token ACQUIRE_FILE_REF = turbFn("acquire_file_ref");
  private static final Token ACQUIRE_STRUCT_REF = turbFn("acquire_struct");
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
  private static final Token STORE_REF = turbFn("store_ref");
  private static final Token STORE_FILE_REF = turbFn("store_file_ref");
  private static final Token STORE_STRUCT_REF = turbFn("store_struct_ref");
  private static final Token INIT_UPD_FLOAT = turbFn("init_updateable_float");

  // Rule functions
  private static final Token SPAWN_RULE = turbFn("spawn_rule");
  private static final Token RULE = turbFn("rule");
  private static final Token DEEPRULE = turbFn("deeprule");
  private static final Token ADLB_PUT = adlbFn("put");
  private static final Token ADLB_SPAWN = adlbFn("spawn");
  private static final LiteralInt TURBINE_NULL_RULE = new LiteralInt(-1);
  
  // Keyword arg names for rule
  private static final Token RULE_KEYWORD_PAR = new Token("parallelism");
  private static final Token RULE_KEYWORD_TYPE = new Token("type");
  private static final Token RULE_KEYWORD_TARGET = new Token("target");
  
  // Dereference functions
  private static final Token DEREFERENCE_INTEGER = turbFn("dereference_integer");
  private static final Token DEREFERENCE_FLOAT = turbFn("dereference_float");
  private static final Token DEREFERENCE_STRING = turbFn("dereference_string");
  private static final Token DEREFERENCE_BLOB = turbFn("dereference_blob");
  private static final Token DEREFERENCE_FILE = turbFn("dereference_file");
  
  // Callstack functions
  private static final Token STACK_LOOKUP = turbFn("stack_lookup");
  static final String LOCAL_STACK_NAME = "stack";
  static final Value LOCAL_STACK_VAL = new Value(LOCAL_STACK_NAME, false, true);
  static final String PARENT_STACK_NAME = "stack";
  private static final Value STACK = new Value(LOCAL_STACK_NAME);
  private static final Value PARENT_STACK = new Value(PARENT_STACK_NAME);
  private static final Token PARENT_STACK_ENTRY = new Token("_parent");
   
  public enum StackFrameType {
    MAIN,
    FUNCTION,
    NESTED
  }

  private static final LiteralInt TURBINE_WORKER_WORK_ID = new LiteralInt(0);
  private static final LiteralInt TURBINE_CONTROL_WORK_ID = new LiteralInt(1);

  // Custom implementations of operators
  private static final Token DIVIDE_INTEGER = turbFn("divide_integer_impl");
  private static final Token MOD_INTEGER = turbFn("mod_integer_impl");
  
  // Refcounting
  private static final Token ENABLE_READ_REFCOUNT = turbFn("enable_read_refcount");
  private static final Token REFCOUNT_INCR = turbFn("read_refcount_incr");
  private static final Token REFCOUNT_DECR = turbFn("read_refcount_decr");
  private static final Token FILE_REFCOUNT_INCR = turbFn("file_read_refcount_incr");
  private static final Token FILE_REFCOUNT_DECR = turbFn("file_read_refcount_decr");
  private static final Token WRITE_REFCOUNT_INCR = adlbFn("write_refcount_incr");
  private static final Token WRITE_REFCOUNT_DECR = adlbFn("write_refcount_decr");
  private static final Token FREE_LOCAL_BLOB = turbFn("free_local_blob");
  
  // Files
  private static final Token GET_FILE = turbFn("get_file");
  private static final Token SET_FILE = turbFn("set_file");
  private static final Token GET_FILE_PATH = turbFn("get_file_path");
  private static final Token IS_MAPPED = turbFn("is_file_mapped");
  private static final Token GET_FILE_STATUS = turbFn("get_file_status");
  private static final Token LOCAL_FILE_PATH = turbFn("local_file_path");
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
  private static final Token GET_PRIORITY = turbFn("get_priority");
  private static final String TCLTMP_PRIO = "tcltmp:prio";
  private static final Value TCLTMP_PRIO_VAL = new Value(TCLTMP_PRIO, false, true);
  
  // Special values
  public static final LiteralInt VOID_DUMMY_VAL = new LiteralInt(12345);

  // Misc
  private static final Token TURBINE_LOG = turbFn("c::log");
  private static final Token ARGV_ADD_CONSTANT = turbFn("argv_add_constant");
  
  private static Token turbFn(String functionName) {
    return new Token("turbine::" + functionName);
  }
  
  private static Token adlbFn(String functionName) {
    return new Token("adlb::" + functionName);
  }  

  private static Value turbConst(String name) {
    return new Value("::turbine::" + name);
  }
  

  public static Command declareStructType(Expression typeId,
      Expression typeName, TclList fieldList) {
    return new Command("adlb::declare_struct_type",
                       Arrays.asList(typeId, typeName, fieldList));
  }
  
  public static TypeName structTypeName(int structType) {
    // Form full type name by concatenation
    return new TypeName(STRUCT_REF_TYPENAME + structType);
  }
  
  public static Command addConstantArg(Expression argName, Expression argVal) {
    return new Command(ARGV_ADD_CONSTANT, argName, argVal);
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
                        ADLB_STRING_TYPE, ADLB_REF_TYPE,
                        LiteralInt.ONE, LiteralInt.ONE);

    if (type != StackFrameType.MAIN) {
      // main is the only procedure without a parent stack frame
      result[index++] = new Command(C_V_INSERT, STACK,
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
      new Command(C_V_INSERT, STACK, name, value);
    return result;
  }

  public static TclTree allocate(String tclName, TypeName typePrefix) {
    return allocate(tclName, typePrefix, LiteralInt.ONE, LiteralInt.ONE,
                    false);
  }
  
  public static TclTree allocatePermanent(String tclName, 
                                                  TypeName typePrefix) {
    return allocate(tclName, typePrefix, LiteralInt.ONE, LiteralInt.ONE,
                     true);
  }
  
  public static TclTree allocate(
      String tclName, TypeName typePrefix,
      Expression initReadRefcount, Expression initWriteRefcount,
      boolean permanent) {
    return new Command(ALLOCATE_CUSTOM, new Token(tclName),
              typePrefix,
              initReadRefcount, initWriteRefcount,
              LiteralInt.boolValue(permanent));
  }

  public static TclTree allocateContainer(String name, TypeName indexType,
      TypeName valType, Expression initReaders, Expression initWriters) {
    return new Command(ALLOCATE_CONTAINER_CUSTOM,
                       new Token(name), indexType, valType,
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

  public static Command stringSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_STRING, turbineDstVar, src);
  }

  public static Command integerSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_INTEGER, turbineDstVar, src);
  }
  
  public static Command refSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_REF, turbineDstVar, src);
  }
  
  public static Command fileRefSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_FILE_REF, turbineDstVar, src);
  }
  
  public static Command structRefSet(Value dst, Expression src,
                            Expression structType) {
    return new Command(STORE_STRUCT_REF, dst, src, structType);
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

  public static SetVariable refGet(String target, Value variable) {
   return refDecrGet(target, variable, LiteralInt.ZERO);
  }
  
  public static SetVariable refDecrGet(String target, Value variable,
                                       Expression decr) {
    return new SetVariable(target, new Square(ACQUIRE_REF, variable,
                                               LiteralInt.ONE, decr));
   }
  
  public static SetVariable fileRefGet(String target, Value variable) {
    return fileRefDecrGet(target, variable, LiteralInt.ZERO);
   }
   
   public static SetVariable fileRefDecrGet(String target, Value variable,
                                        Expression decr) {
     return new SetVariable(target, new Square(ACQUIRE_FILE_REF, variable,
                                                LiteralInt.ONE, decr));
  }
   
  public static SetVariable structRefGet(String target, Value variable) {
    return fileRefDecrGet(target, variable, LiteralInt.ZERO);
  }
    
  public static SetVariable structRefDecrGet(String target, Value variable,
                                         Expression decr) {
    return new SetVariable(target, new Square(ACQUIRE_STRUCT_REF, variable,
                                                 LiteralInt.ONE, decr));
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
      List<Expression> action, TaskMode type,  ExecContext execCx,
      RuleProps props) {
    assert(action != null);
    for (Expression e: action) {
      assert(e != null): action;
    }
    
    assert(props.target != null);

    if (inputs.isEmpty()) {
      if (!type.isLocal()) {
        return spawnTask(action, type, execCx, props);
      }
    }
    
    Sequence res = new Sequence();    
    if (props.priority != null)
       res.add(setPriority(props.priority));
    // Use different command on worker
    Token ruleCmd = execCx == ExecContext.CONTROL ? RULE : SPAWN_RULE;
    
    List<Expression> args = new ArrayList<Expression>();
    
    args.add(new TclList(inputs)); // vars to block in
    args.add(TclUtil.tclStringAsList(action)); // Tcl string to execute
    ruleAddKeywordArgs(type, props.target, props.parallelism, args);
    
    res.add(new Command(ruleCmd, args));

    if (props.priority != null)
       res.add(resetPriority());
    return res;
  }

  public static List<Expression> ruleKeywordArgs(TclTarget target,
      Expression parallelism) {
    return ruleKeywordArgs(null, target, parallelism);
  }
  
  public static List<Expression> ruleKeywordArgs(
      TaskMode type, TclTarget target, Expression parallelism) {
    ArrayList<Expression> res = new ArrayList<Expression>();
    ruleAddKeywordArgs(type, target, parallelism, res);
    return res;
  }
  
  private static void ruleAddKeywordArgs(TaskMode type, TclTarget target,
      Expression parallelism, List<Expression> args) {
    if (!target.rankAny) {
      args.add(RULE_KEYWORD_TARGET);
      args.add(target.toTcl());
    }
    
    if (type != null && type != TaskMode.LOCAL && type != TaskMode.CONTROL) {
      args.add(RULE_KEYWORD_TYPE);
      args.add(tclRuleType(type));
    }
    
    if (parallelism != null && !LiteralInt.ONE.equals(parallelism)) {
      args.add(RULE_KEYWORD_PAR);
      args.add(parallelism);
    }
  }

  private static Sequence spawnTask(List<Expression> action, TaskMode type, 
        ExecContext execCx, RuleProps props) {
    Sequence res = new Sequence();
    
    // Store in var for readability

    Expression prio;
    if (props.priority != null) {
      prio = props.priority;
    } else {
      prio = currentPriority();
    }
    Value priorityVar = TCLTMP_PRIO_VAL;
    res.add(new SetVariable(TCLTMP_PRIO, prio));
    
    List<Expression> taskTokens = new ArrayList<Expression>();
    // Different task formats for work types
    if (type == TaskMode.WORKER) {
      taskTokens.add(TURBINE_NULL_RULE);
      taskTokens.addAll(action);
    } else {
      assert(type == TaskMode.CONTROL);
      taskTokens.add(new Token("command"));
      if (props.priority != null) {
        assert(priorityVar != null);
        taskTokens.add(new Token("priority:"));
        taskTokens.add(priorityVar);
      }
      taskTokens.addAll(action);
    }
    
    Expression task = TclUtil.tclStringAsList(taskTokens);

    Expression par = props.parallelism;
    if (props.target.rankAny && par == null) {
      // Use simple spawn
      res.append(spawnTask(type, priorityVar, task));
      return res;
    } else {
      if (par == null) {
        par = LiteralInt.ONE;
      }
      
      // Use put, which takes more arguments
      res.add(new Command(ADLB_PUT, props.target.toTcl(), adlbWorkTypeVal(type),
                            task, priorityVar, par));
      return res;
    }
  }

  private static Sequence spawnTask(TaskMode type, Value priority, Expression task) {
    Sequence res = new Sequence();
    if (priority != null)
      res.add(setPriority(priority));
    res.add(new Command(ADLB_SPAWN, adlbWorkTypeVal(type),
                          task));
    if (priority != null)
      res.add(resetPriority());
    return res;
  }

  public static Expression adlbWorkType(TaskMode type) {
    switch (type) {
      case CONTROL:
        return new Value("turbine::CONTROL_TASK");
      case WORKER:
        return new Value("turbine::WORK_TASK");
      default:
        throw new STCRuntimeError("Can't create task of type " + type);
    }
  }
  
  /**
   * Tcl is inefficient at looking up namespace vars.
   * Have option of hardcoding work ids
   */
  public static Expression adlbWorkTypeVal(TaskMode type) {
    switch (type) {
      case CONTROL:
        return TURBINE_CONTROL_WORK_ID;
      case WORKER:
        return TURBINE_WORKER_WORK_ID;
      default:
        throw new STCRuntimeError("Can't create task of type " + type);
    }
  }
  
  /**
   * Generate code to check compatibility of STC with Turbine, so
   * we don't mistakenly hardcode wrong constants
   */
  public static Command checkConstants() {
    List<Expression> args = new ArrayList<Expression>();
    // Check work types
    for (TaskMode taskMode: Arrays.asList(TaskMode.WORKER, TaskMode.CONTROL)) {
      args.add(new TclString(taskMode.toString(), true));
      args.add(adlbWorkType(taskMode));
      args.add(adlbWorkTypeVal(taskMode));
    }
    
    // Check ADLB_RANK_ANY value
    args.add(new TclString("ADLB_RANK_ANY", true));
    args.add(TclTarget.ADLB_RANK_ANY);
    args.add(new LiteralInt(Location.ANY_LOCATION_VAL));
    
    return new Command(turbFn("check_constants"), args);
  }

  public static Expression currentPriority() {
    // get the current turbine priority
    return new Square(GET_PRIORITY);
  }

  public static class RuleProps {
    public static final RuleProps DEFAULT =
          new RuleProps(TclTarget.RANK_ANY, null, null);
    
    public final TclTarget target;
    public final Expression parallelism; // can be null
    public final Expression priority; // can be null

    public RuleProps(TclTarget target, Expression parallelism, Expression priority) {
      assert(target != null);
      this.target = target;
      this.parallelism = parallelism;
      this.priority = priority;
    }
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
      ExecContext execCx, RuleProps props) {
    return ruleHelper(symbol, blockOn, action, mode, execCx, props);
  }

  public static Sequence deepRule(String symbol,
      List<? extends Expression> inputs, int[] depths, boolean[] isFile,
      List<Expression> action, TaskMode mode, ExecContext execCx,
      RuleProps props) {
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
    if (props.priority != null)
       res.add(setPriority(props.priority));
    
    List<Expression> args = new ArrayList<Expression>();
    args.add(new TclList(inputs));
    args.add(new TclList(depthExprs));
    args.add(new TclList(isFileExprs));
    args.add(TclUtil.tclStringAsList(action));
    ruleAddKeywordArgs(mode, props.target, props.parallelism, args);
    res.add(new Command(DEEPRULE, args));
    
    if (props.priority != null)
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
                      execCx, RuleProps.DEFAULT);
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
                    Value src) {
    Sequence result = new Sequence();
    Command storeCmd = new Command(
        new Token("dict"), new Token("set"), 
        new Token(container), new TclString(field, true), src);
  
    result.add(storeCmd);
    return result;
  
  }

  public static Sequence structLookupFieldID(Value struct, String structField,
      String resultVar) {
    Sequence result = new Sequence();

    Square containerGet = new Square(new Token("dict"), new Token("get"),
        struct, new TclString(structField, true));

    SetVariable loadCmd = new SetVariable(resultVar, containerGet);
    result.add(loadCmd);
    return result;
  }


  public static Command structRefLookupFieldID(Value struct, int structFieldIx,
      Value resultVar, TypeName refType) { 
    return new Command(turbFn("struct_ref_lookup"),
            struct, new LiteralInt(structFieldIx), resultVar, refType);
  }


  /**
   * Put reference to arrayVar[arrayIndex] into refVar once it is ready
   * @param refVar
   * @param arrayIndex
   * @param isArrayRef
   * @return
   */
  public static Command arrayLookupImmIx(Value refVar, TypeName refType,
      Value arrayVar, Expression arrayIndex, boolean isArrayRef) {    
    // set up reference to point to array data
    if (isArrayRef) {
      return new Command(CR_V_LOOKUP, arrayVar, arrayIndex, refVar, refType);
    } else {
      return new Command(C_REFERENCE, arrayVar, arrayIndex, refVar, refType);
    }
  }

  /**
   * Lookup arrayVar[arrayIndex] right away, regardless of whether
   * it is closed
   */
  public static SetVariable arrayLookupImm(String dst, Value arrayVar,
      Expression arrayIndex) {
    return new SetVariable(dst,
        new Square(C_LOOKUP_CHECKED, arrayVar, arrayIndex));
  }

  /**
   * Put a reference to arrayVar[indexVar] into refVar
   * @param refVar
   * @param arrayVar
   * @param indexVar
   * @param isArrayRef
   * @return
   */
  public static Command arrayLookupComputed(Value refVar,
                          TypeName refType,
                          Value arrayVar,
                          Value indexVar, boolean isArrayRef) { 
    if (isArrayRef) {
      return new Command(CR_F_LOOKUP, arrayVar, indexVar, refVar, refType);
    } else {
      return new Command(C_F_LOOKUP, arrayVar, indexVar,refVar, refType);
    }
   }

   public static Command dereferenceInteger(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_INTEGER, dstVar, refVar);
   }

   public static Command dereferenceFloat(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_FLOAT, dstVar, refVar);
   }

   public static Command dereferenceString(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_STRING, dstVar, refVar);
   }
   
   public static Command dereferenceBlob(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_BLOB, dstVar, refVar);
   }
   
   public static Command dereferenceFile(Value dstVar, Value refVar) {
     return new Command(DEREFERENCE_FILE, dstVar, refVar);
   }

  public static Command arrayStoreImmediate(Value srcVar, Value arrayVar,
                              Expression arrayIndex, Expression writersDecr,
                              TypeName valType) {
    return new Command(C_V_INSERT, arrayVar, arrayIndex, srcVar,
                                    valType, writersDecr);
  }

  public static Command arrayDerefStore(Value srcRefVar, Value arrayVar,
      Expression arrayIndex, Expression writersDecr,
      TypeName valType) {
    return new Command(C_V_DEREF_INSERT, arrayVar, arrayIndex, srcRefVar,
                       valType, writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayDerefStoreComputed(Value srcRefVar,
      Value arrayVar, Value indexVar, Expression writersDecr, TypeName valType) {
    return new Command(C_F_DEREF_INSERT, arrayVar, indexVar, srcRefVar,
        valType, writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayStoreComputed(Value srcVar, Value arrayVar,
      Value indexVar, Expression writersDecr,
                                  TypeName valType) {
    // Don't increment writers count, this is done in IC
    return new Command(C_F_INSERT, arrayVar, indexVar, srcVar, valType,
                                    writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayRefStoreImmediate(Value srcVar, Value arrayVar,
      Expression arrayIndex, Value outerArray,
      TypeName valType) {
    return new Command(CR_V_INSERT, arrayVar, arrayIndex, srcVar,
                    valType,  outerArray, LiteralInt.FALSE);
  }


  public static Command arrayRefStoreComputed(Value srcVar, Value arrayVar,
      Value indexVar, Value outerArray, TypeName valType) {
    return new Command(CR_F_INSERT, arrayVar, indexVar, srcVar,
        valType, outerArray, LiteralInt.FALSE);
  }

  public static Command arrayRefDerefStore(Value srcRefVar, Value arrayVar,
      Expression arrayIndex, Value outerArrayVar, TypeName valType) {
    return new Command(CR_V_DEREF_INSERT, arrayVar, arrayIndex, 
        srcRefVar, valType, outerArrayVar, LiteralInt.FALSE);
  }

  public static Command arrayRefDerefStoreComputed(Value srcRefVar,
      Value arrayVar, Value indexVar, Value outerArrayVar, TypeName valType) {
    return new Command(CR_F_DEREF_INSERT, arrayVar, indexVar, srcRefVar,
        valType, outerArrayVar, LiteralInt.FALSE);
  }

  public static TclTree containerCreateNested(Value resultVar,
      Value containerVar, Value indexVar, TypeName keyType, TypeName valType) {
    return new Command(C_F_CREATE_NESTED, resultVar,
            containerVar, indexVar, keyType, valType);
  }

  public static TclTree containerRefCreateNested(Value resultVar,
      Value containerVar, Value indexVar, Value outerArr, TypeName keyType,
      TypeName valType) {
    return new Command(CR_F_CREATE_NESTED,
          resultVar, containerVar,
          indexVar, keyType, valType, outerArr, LiteralInt.FALSE);
  }

  public static TclTree containerRefCreateNestedImmIx(Value resultVar,
      Value containerVar, Expression arrIx, Value outerArr,
      TypeName keyType, TypeName valType) {
    return new Command(CR_V_CREATE_NESTED, resultVar, containerVar,
        arrIx, keyType, valType, outerArr, LiteralInt.FALSE);
  }

  public static TclTree containerCreateNestedImmIx(String resultVar,
      Value containerVar, Expression arrIx, TypeName keyType,
      TypeName valType, Expression callerReadRefs, Expression callerWriteRefs) {
    return new SetVariable(resultVar,
        new Square(C_V_CREATE_NESTED,
            containerVar, arrIx, keyType, valType,
            callerReadRefs, callerWriteRefs));
  }
  
  public static TclTree containerCreateNestedBag(String resultVar,
          Value containerVar, Expression arrIx, TypeName valType,
          Expression callerReadRefs, Expression callerWriteRefs) {
        return new SetVariable(resultVar,
            new Square(C_V_CREATE_NESTED_BAG, containerVar, arrIx, valType,
                callerReadRefs, callerWriteRefs));
  }


  public static TclTree bagAppend(Value bag, TypeName elemType,
                                  Value elem, Expression decr) {
    return new Command(adlbFn("store"), bag, elemType, elem, decr);
  }
  
  public static TclTree incrWriters(Value arr, Expression incr) {
    return new Command(WRITE_REFCOUNT_INCR, arr, incr);
  }
  
  public static TclTree decrWriters(Value arr, Expression decr) {
    return new Command(WRITE_REFCOUNT_DECR, arr, decr);
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
    return allocate(refVarName, ADLB_INT_TYPE);
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
    return new Square(GET_FILE_STATUS, fileVar);
  }
  
  /**
   * Expression that extracts the filename string future
   * a file variable
   * @param fileVar
   * @return
   */
  public static Expression getFileName(Value fileVar) {
    return new Square(GET_FILE_PATH, fileVar);
  }
  
 public static SetVariable isMapped(String isMappedVar, Value fileVar) {
   return new SetVariable(isMappedVar, new Square(IS_MAPPED, fileVar));
 }
 
  
  public static Expression localFilePath(Value fileVar) {
    return new Square(LOCAL_FILE_PATH, fileVar);
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

  public static SetVariable createLocalFile(String varName, Expression fileName,
                                            Expression initRefcount) {
    return new SetVariable(varName, 
            new Square(CREATE_LOCAL_FILE_REF, fileName, initRefcount));
  }
  
  public static SetVariable mkTemp(String varName) {
    return new SetVariable(varName, new Square(MKTEMP));
  }

  public static Command setFilenameVal(Value fileFuture,
                                       Expression filenameVal) {
    return new Command(SET_FILENAME_VAL, fileFuture, filenameVal);
  }
  

  public static Command copyFileContents(Value dst, Value src) {
    return new Command(turbFn("copy_local_file_contents"), 
                          dst, src);
  }
  
  public static Command arrayBuild(Value array, 
      List<Expression> arrKeyExprs, List<Expression> arrValExprs,
      boolean close, TypeName valType) {
    return new Command(ARRAY_KV_BUILD, array, new TclList(arrKeyExprs),
         new TclList(arrValExprs), LiteralInt.boolValue(close), valType);
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
