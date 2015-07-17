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
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecContext.WorkContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.Location;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.util.Pair;
import exm.stc.tclbackend.TclTemplateProcessor.TemplateArg;
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
 *
 * @author wozniak
 * @author armstron
 *
 *         This class is package-private: only TurbineGenerator uses it
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
  public static final TypeName ADLB_STRING_TYPE = new TypeName(STRING_TYPENAME);
  public static final String BLOB_TYPENAME = "blob";
  public static final TypeName ADLB_BLOB_TYPE = new TypeName(BLOB_TYPENAME);
  public static final String CONTAINER_TYPENAME = "container";
  public static final TypeName ADLB_CONTAINER_TYPE = new TypeName(
          CONTAINER_TYPENAME);
  public static final String MULTISET_TYPENAME = "multiset";
  public static final TypeName ADLB_MULTISET_TYPE = new TypeName(
          MULTISET_TYPENAME);
  public static final String REF_TYPENAME = "ref";
  public static final TypeName ADLB_REF_TYPE = new TypeName(REF_TYPENAME);
  public static final String FILE_TYPENAME = "file";
  public static final TypeName ADLB_FILE_TYPE = new TypeName(
          FILE_TYPENAME);
  public static final String FILE_REF_TYPENAME = "file_ref";
  public static final TypeName ADLB_FILE_REF_TYPE = new TypeName(
          FILE_REF_TYPENAME);
  public static final String STRUCT_TYPENAME = "struct";
  public static final TypeName ADLB_STRUCT_TYPE = new TypeName(STRUCT_TYPENAME);

  private static final Token ALLOCATE_CUSTOM = turbFn("allocate_custom");
  private static final Token MULTICREATE = adlbFn("multicreate");
  private static final Token DECLARE_GLOBALS = turbFn("declare_globals");
  private static final Token MAKE_FILE_TDS = turbFn("make_file_tds");

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
  private static final Token MULTISET_BUILD = turbFn("multiset_build");
  private static final Token BUILD_REC = turbFn("build_rec");

  // Container nested creation
  private static final Token CREATE_NESTED = turbFn("create_nested");
  private static final Token C_F_CREATE_NESTED = turbFn("c_f_create");
  private static final Token CR_V_CREATE_NESTED = turbFn("cr_v_create");
  private static final Token CR_F_CREATE_NESTED = turbFn("cr_f_create");

  // Container lookup
  private static final Token C_LOOKUP = turbFn("container_lookup");
  private static final Token C_REFERENCE = turbFn("container_reference");
  private static final Token C_F_LOOKUP = turbFn("c_f_lookup");
  private static final TclTree CR_V_LOOKUP = turbFn("cr_v_lookup");
  private static final Token CR_F_LOOKUP = turbFn("cr_f_lookup");
  private static final Token ENUMERATE = adlbFn("enumerate");
  private static final Token ENUMERATE_REC = turbFn("enumerate_rec");
  private static final Token EXISTS_SUB = adlbFn("exists_sub");

  // Struct nested creation
  private static final Token STRUCT_CREATE_NESTED = turbFn("struct_create_nested");

  // Retrieve functions
  private static final Token RETRIEVE_INTEGER = turbFn("retrieve_integer");
  private static final Token RETRIEVE_FLOAT = turbFn("retrieve_float");
  private static final Token RETRIEVE_STRING = turbFn("retrieve_string");
  private static final Token RETRIEVE_BLOB = turbFn("retrieve_blob");
  private static final Token ACQUIRE_REF = adlbFn("acquire_ref");
  private static final Token ACQUIRE_WRITE_REF = adlbFn("acquire_write_ref");
  private static final Token ACQUIRE_STRUCT_REF = turbFn("acquire_struct");
  private static final Token ADLB_ACQUIRE_REF = adlbFn("acquire_ref");
  private static final Token ADLB_STORE = adlbFn("store");
  private static final Token CACHED = new Token("CACHED");
  private static final Token UNCACHED_MODE = new Token("UNCACHED");

  /**
   * Used to specify what caching is allowed for retrieve
   *
   * @author tga
   */
  public enum CacheMode {
    CACHED, UNCACHED
  }

  // Store functions
  private static final Token STORE_INTEGER = turbFn("store_integer");
  private static final Token STORE_VOID = turbFn("store_void");
  private static final Token STORE_FLOAT = turbFn("store_float");
  private static final Token STORE_STRING = turbFn("store_string");
  private static final Token STORE_BLOB = turbFn("store_blob");
  private static final Token STORE_REF = turbFn("store_ref");
  private static final Token STORE_FILE_REF = turbFn("store_file_ref");
  private static final Token STORE_STRUCT = turbFn("store_struct");
  private static final Token INIT_UPD_FLOAT = turbFn("init_updateable_float");

  // Struct functions
  private static final Token STRUCT_REFERENCE = adlbFn("struct_reference");
  private static final Token STRUCTREF_REFERENCE = turbFn("structref_reference");
  private static final Token STRUCT_LOOKUP = adlbFn("struct_lookup");
  private static final Token STRUCT_INSERT = adlbFn("struct_insert");
  private static final Token STRUCT_INSERT_R = turbFn("struct_insert_r");
  private static final Token STRUCTREF_INSERT_R = turbFn("structref_insert_r");
  private static final Token STRUCTREF_INSERT_V = turbFn("structref_insert_v");

  // Rule functions
  private static final Token RULE = turbFn("rule");
  private static final Token DEEPRULE = turbFn("deeprule");
  private static final Token ADLB_PUT = adlbFn("put");
  private static final Token ADLB_SPAWN = adlbFn("spawn");

  // Keyword arg names for rule
  private static final Token RULE_KEYWORD_PAR = new Token("parallelism");
  private static final Token RULE_KEYWORD_TYPE = new Token("type");
  private static final Token RULE_KEYWORD_TARGET = new Token("target");
  private static final Token RULE_KEYWORD_STRICTNESS = new Token("strictness");
  private static final Token RULE_KEYWORD_ACCURACY = new Token("accuracy");

  // Dereference functions
  private static final Token DEREFERENCE_INTEGER =
          turbFn("dereference_integer");
  private static final Token DEREFERENCE_VOID = turbFn("dereference_void");
  private static final Token DEREFERENCE_FLOAT = turbFn("dereference_float");
  private static final Token DEREFERENCE_STRING = turbFn("dereference_string");
  private static final Token DEREFERENCE_BLOB = turbFn("dereference_blob");
  private static final Token DEREFERENCE_FILE = turbFn("dereference_file");

  private static final LiteralInt TURBINE_WORKER_WORK_ID = new LiteralInt(0);

  // Custom implementations of operators
  private static final Token DIVIDE_INTEGER = turbFn("divide_integer_impl");
  private static final Token MOD_INTEGER = turbFn("mod_integer_impl");

  // Refcounting
  private static final Token ENABLE_READ_REFCOUNT =
          turbFn("enable_read_refcount");
  private static final Token REFCOUNT_INCR = turbFn("read_refcount_incr");
  private static final Token REFCOUNT_DECR = turbFn("read_refcount_decr");
  private static final Token FILE_REFCOUNT_INCR =
          turbFn("file_read_refcount_incr");
  private static final Token FILE_REFCOUNT_DECR =
          turbFn("file_read_refcount_decr");
  private static final Token WRITE_REFCOUNT_INCR =
          adlbFn("write_refcount_incr");
  private static final Token WRITE_REFCOUNT_DECR =
          adlbFn("write_refcount_decr");
  private static final Token FREE_LOCAL_BLOB = turbFn("free_local_blob");

  // Files
  private static final Token RETRIEVE_FILE = turbFn("retrieve_file");
  private static final Token STORE_FILE = turbFn("store_file");
  private static final Token GET_FILE_PATH = turbFn("get_file_path");
  private static final Token COPY_IN_FILENAME = turbFn("copy_in_filename");
  private static final Token IS_MAPPED = turbFn("is_file_mapped");
  private static final Token GET_FILE_ID = turbFn("get_file_td");
  private static final Token LOCAL_FILE_PATH = turbFn("local_file_path");
  private static final Token CREATE_LOCAL_FILE_REF =
          turbFn("create_local_file_ref");
  private static final Token DECR_LOCAL_FILE_REFCOUNT =
          turbFn("decr_local_file_refcount");
  private static final Token MKTEMP = turbFn("mktemp");
  private static final Token GET_FILENAME_VAL = turbFn("get_filename_val");
  private static final Token SET_FILENAME_VAL = turbFn("set_filename_val");

  // External apps
  private static final Token UNPACK_ARGS = turbFn("unpack_args");
  private static final Token EXEC_EXTERNAL = turbFn("exec_external");

  // Task priorities
  private static final Token SET_PRIORITY = turbFn("set_priority");
  private static final Token RESET_PRIORITY = turbFn("reset_priority");
  private static final Token GET_PRIORITY = turbFn("get_priority");
  private static final String TCLTMP_PRIO = "tcltmp:prio";
  private static final Value TCLTMP_PRIO_VAL = new Value(TCLTMP_PRIO, false,
          true);

  // Special values
  public static final LiteralInt VOID_DUMMY_VAL = new LiteralInt(12345);

  // Library function implementations
  public static final Token TOFLOAT_IMPL = turbFn("tofloat_impl");
  public static final Token PARSE_INT = turbFn("parse_int_impl");
  public static final Token SUBSTRING_IMPL = turbFn("substring_impl");
  public static final Token POW_INTEGER_IMPL = turbFn("pow_integer_impl");
  public static final Token TURBINE_ASSERT_EQUAL_IMPL
              = turbFn("assertEqual_impl");
  public static final Token TURBINE_ASSERT_IMPL
              = turbFn("assert_impl");

  // Checkpointing
  public static Token XPT_INIT = turbFn("xpt_init");
  public static Token XPT_FINALIZE = turbFn("xpt_finalize");
  public static Token XPT_WRITE = turbFn("xpt_write");
  public static Token XPT_WRITE_ENABLED = turbFn("xpt_write_enabled");
  public static Token XPT_LOOKUP = turbFn("xpt_lookup");
  public static Token XPT_LOOKUP_ENABLED = turbFn("xpt_lookup_enabled");
  public static Token XPT_PACK = adlbFn("xpt_pack");
  public static Token XPT_UNPACK = adlbFn("xpt_unpack");

  // Debug symbols
  public static Token ADD_DEBUG_SYMBOL = adlbFn("add_debug_symbol");

  // Subscript/handle manipulation
  public static Token MAKE_CONTAINER_SUBSCRIPT = adlbFn("subscript_container");
  public static Token MAKE_STRUCT_SUBSCRIPT = adlbFn("subscript_struct");

  // Checkpointing options
  public static enum XptFlushPolicy {
    NO_FLUSH, PERIODIC_FLUSH, ALWAYS_FLUSH;

    public Token toToken() {
      switch (this) {
      case NO_FLUSH:
        return XPT_NO_FLUSH;
      case PERIODIC_FLUSH:
        return XPT_PERIODIC_FLUSH;
      case ALWAYS_FLUSH:
        return XPT_ALWAYS_FLUSH;
      default:
        throw new STCRuntimeError("Unknown flush policy " + this);
      }
    }
  }

  public static Token XPT_NO_FLUSH = new Token("no_flush");
  public static Token XPT_PERIODIC_FLUSH = new Token("periodic_flush");
  public static Token XPT_ALWAYS_FLUSH = new Token("always_flush");

  public static enum XptPersist {
    NO_PERSIST, PERSIST, PERSIST_FLUSH;

    public Token toToken() {
      switch (this) {
      case NO_PERSIST:
        return XPT_NO_PERSIST;
      case PERSIST:
        return XPT_PERSIST;
      case PERSIST_FLUSH:
        return XPT_PERSIST_FLUSH;
      default:
        throw new STCRuntimeError("Unknown XptPersist: " + this);
      }
    }
  }

  public static Token XPT_NO_PERSIST = new Token("no_persist");
  public static Token XPT_PERSIST = new Token("persist");
  public static Token XPT_PERSIST_FLUSH = new Token("persist_flush");

  // Misc
  private static final Token TURBINE_LOG = turbFn("c::log");
  private static final Token ARGV_ADD_CONSTANT = turbFn("argv_add_constant");
  private static final Token ADLB_WORK_TYPE = turbFn("adlb_work_type");
  private static final Token DECLARE_CUSTOM_WORK_TYPES =
                                  turbFn("declare_custom_work_types");
  private static final Token LASSIGN = new Token("lassign");

  private static Token turbFn(String functionName) {
    return new Token("turbine::" + functionName);
  }

  private static Token adlbFn(String functionName) {
    return new Token("adlb::" + functionName);
  }

  public static Command declareCustomWorkTypes(List<Expression> args) {
    return new Command(DECLARE_CUSTOM_WORK_TYPES, args);
  }

  public static Command declareStructType(Expression typeId,
          Expression typeName, TclList fieldList) {
    return new Command("adlb::declare_struct_type", Arrays.asList(typeId,
            typeName, fieldList));
  }

  public static Command addConstantArg(Expression argName, Expression argVal) {
    return new Command(ARGV_ADD_CONSTANT, argName, argVal);
  }

  public static TclTree allocatePermanent(String tclName, TypeName typePrefix,
                                          int debugSymbol) {
    return allocate(tclName, typePrefix, LiteralInt.ONE, LiteralInt.ONE,
                    debugSymbol, true);
  }

  public static TclTree allocate(String tclName, TypeName typePrefix,
          Expression initReadRefcount, Expression initWriteRefcount,
          int debugSymbol, boolean permanent) {
    return new Command(ALLOCATE_CUSTOM, new Token(tclName), typePrefix,
            initReadRefcount, initWriteRefcount, new LiteralInt(debugSymbol),
            LiteralInt.boolValue(permanent));
  }

  /**
   * Do a data get operation to load the value from the TD
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

  public static Command refSet(Value turbineDstVar, Expression src,
          Expression storeReaders, Expression storeWriters) {
    return new Command(STORE_REF, turbineDstVar, src,
                       storeReaders, storeWriters);
  }

  public static Command fileRefSet(Value turbineDstVar, Expression src,
          Expression storeReaders, Expression storeWriters) {
    return new Command(STORE_FILE_REF, turbineDstVar, src,
                        storeReaders, storeWriters);
  }

  public static Command structSet(Value dst, Expression src,
                  TypeName structType, Expression writeDecr) {
    return new Command(STORE_STRUCT, dst, src, structType, writeDecr);
  }

  public static TclTree voidSet(Value voidVar) {
    return new Command(STORE_VOID, voidVar);
  }

  public static Command floatSet(Value turbineDstVar, Expression src) {
    return new Command(STORE_FLOAT, turbineDstVar, src);
  }

  public static Command
          updateableFloatInit(Value turbineDstVar, Expression src) {
    return new Command(INIT_UPD_FLOAT, turbineDstVar, src);
  }

  public static SetVariable floatGet(String target, Value variable) {
    return floatGet(target, variable, CacheMode.CACHED);
  }

  public static SetVariable floatGet(String target, Value variable,
          CacheMode caching) {
    if (caching == CacheMode.CACHED) {
      return new SetVariable(target, new Square(RETRIEVE_FLOAT, variable));
    } else {
      assert (caching == CacheMode.UNCACHED);
      return new SetVariable(target, new Square(RETRIEVE_FLOAT, variable,
              UNCACHED_MODE));
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

  public static SetVariable readRefGet(String target, Value variable,
      TypeName refType, Expression acquireReadExpr, Expression decrRead) {
    return new SetVariable(target, new Square(ACQUIRE_REF, variable,
                           refType, acquireReadExpr, decrRead));
  }

  public static SetVariable readWriteRefGet(String target, Value variable,
      TypeName refType,
          Expression acquireReadExpr, Expression acquireWriteExpr,
          Expression decrRead) {
    return new SetVariable(target, new Square(ACQUIRE_WRITE_REF, variable,
            refType, acquireReadExpr, acquireWriteExpr, decrRead));
  }

  public static SetVariable structDecrGet(String target, Value variable,
          Expression decr) {
    return new SetVariable(target, new Square(ACQUIRE_STRUCT_REF, variable,
            LiteralInt.ONE, decr));
  }

  /**
   * General-purpose acquire primitive
   *
   * @param dst
   * @param src
   * @param srcType
   *          type of src
   * @param incrReferand
   * @param decr
   * @return
   */
  public static SetVariable retrieveAcquire(String dst, Value src,
          TypeName srcType, Expression incrReferand, Expression decr) {
    return new SetVariable(dst, new Square(ADLB_ACQUIRE_REF, src, srcType,
            incrReferand, decr));
  }

  public static Command adlbStore(Value dst, Expression src,
      List<? extends Expression> dstTypeInfo, Expression decrWriters,
      Expression decrReaders) {
    return adlbStore(dst, src, dstTypeInfo, decrWriters, decrReaders,
                     null, null);
  }

  public static Command adlbStore(Value dst, Expression src,
          List<? extends Expression> dstTypeInfo, Expression decrWriters,
          Expression decrReaders, Expression storeReaders,
          Expression storeWriters) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(dst);
    args.addAll(dstTypeInfo);
    args.add(src);
    if (decrWriters != null) {
      args.add(decrWriters);
    }
    if (decrReaders != null) {
      assert (decrWriters != null);
      args.add(decrReaders);
    }
    if (storeReaders != null) {
      assert (decrReaders != null);
      args.add(storeReaders);
    }
    if (storeWriters != null) {
      assert (storeReaders != null);
      args.add(storeWriters);
    }
    return new Command(ADLB_STORE, args);
  }

  public static SetVariable integerDecrGet(String target, Value src,
          Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_INTEGER, src, CACHED,
            decr));
  }

  public static SetVariable floatDecrGet(String target, Value src,
          Expression decr) {
    return new SetVariable(target,
            new Square(RETRIEVE_FLOAT, src, CACHED, decr));
  }

  public static SetVariable stringDecrGet(String target, Value src,
          Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_STRING, src, CACHED,
            decr));
  }

  public static SetVariable blobDecrGet(String target, Value src,
          Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_BLOB, src, decr));
  }

  public static SetVariable fileDecrGet(String target, Value src,
          Expression decr) {
    return new SetVariable(target, new Square(RETRIEVE_FILE, src, CACHED, decr));
  }

  /**
   * Recursively enumerate container/bag contents typeList: list of types from
   * outer container to inner vale
   */
  public static SetVariable enumerateRec(String target,
          List<Expression> typeList, Value src, Expression decr) {
    Square fnCall =
            Square.fnCall(ENUMERATE_REC, src, new TclList(typeList),
                    LiteralInt.ZERO, decr);

    return new SetVariable(target, fnCall);
  }

  private static Expression tclRuleType(ExecTarget t) {
    assert(t.isAsync());

    if (t.isDispatched()) {
      // Same as ADLB work types
      return adlbWorkTypeVal(t.targetContext());
    } else {
      // Just implement as control
      return adlbWorkTypeVal(ExecContext.control());
    }
  }

  /**
   * Generate code for a rule
   *
   * @param symbol
   * @param inputs
   * @param action
   *          tokens making up the action
   * @param type
   * @return
   */
  private static Sequence ruleHelper(String symbol,
          List<? extends Expression> inputs, List<Expression> action,
          ExecTarget type, ExecContext execCx, RuleProps props) {
    assert (action != null);
    for (Expression e : action) {
      assert (e != null) : action;
    }

    assert (props.targetRank != null);

    if (inputs.isEmpty()) {
      if (type.isDispatched()) {
        return spawnTask(action, type, execCx, props);
      }
    }

    Sequence res = new Sequence();
    if (props.priority != null)
      res.add(setPriority(props.priority));
    // Use different command on worker
    Token ruleCmd;
    // No worker/control distinction
    ruleCmd = RULE;

    List<Expression> args = new ArrayList<Expression>();

    args.add(new TclList(inputs)); // vars to block in
    args.add(TclUtil.tclStringAsList(action)); // Tcl string to execute
    ruleAddKeywordArgs(type, props, args);

    res.add(new Command(ruleCmd, args));

    if (props.priority != null)
      res.add(resetPriority());
    return res;
  }

  public static List<Expression> ruleKeywordArgs(TclTarget targetRank,
          Expression targetStrictness, Expression targetAccuracy,
          Expression parallelism) {
    return ruleKeywordArgs(null, targetRank, targetStrictness, targetAccuracy,
                           parallelism);
  }

  public static List<Expression> ruleKeywordArgs(ExecTarget type,
      TclTarget targetRank, Expression targetStrictness,
      Expression targetAccuracy, Expression parallelism) {
    ArrayList<Expression> res = new ArrayList<Expression>();
    ruleAddKeywordArgs(type, targetRank, targetStrictness, targetAccuracy,
                       parallelism, res);
    return res;
  }

  private static void ruleAddKeywordArgs(ExecTarget type, RuleProps props,
      List<Expression> args) {
    ruleAddKeywordArgs(type, props.targetRank, props.targetStrictness,
                props.targetAccuracy, props.parallelism, args);
  }

  private static void ruleAddKeywordArgs(ExecTarget type, TclTarget targetRank,
      Expression targetStrictness, Expression targetAccuracy,
      Expression parallelism, List<Expression> args) {
    if (!targetRank.rankAny) {
      args.add(RULE_KEYWORD_TARGET);
      args.add(targetRank.toTcl());

      // Avoid generating unnecessary arguments
      if (!isDefaultStrictness(targetStrictness)) {
        args.add(RULE_KEYWORD_STRICTNESS);
        args.add(targetStrictness);
      }

      if (!isDefaultAccuracy(targetAccuracy)) {
        args.add(RULE_KEYWORD_ACCURACY);
        args.add(targetAccuracy);
      }
    }

    // Only a single rule type with no turbine engines
    if (!isDefaultExecTarget(type)) {
      args.add(RULE_KEYWORD_TYPE);
      args.add(tclRuleType(type));
    }

    if (parallelism != null && !LiteralInt.ONE.equals(parallelism)) {
      args.add(RULE_KEYWORD_PAR);
      args.add(parallelism);
    }
  }

  private static boolean isDefaultExecTarget(ExecTarget type) {
    if (type == null) {
      return true;
    }

    // Should be async if using with rule
    assert(type.isAsync()) : type;

    ExecContext targetContext = type.targetContext();

    if (targetContext.isWildcardContext()) {
      // Don't care about target
      return true;
    } else if (targetContext.isControlContext() ||
            targetContext.isDefaultWorkContext()) {
      // Target matches (control and worker are the same)
      return true;
    } else {
      return false;
    }
  }

  private static boolean isDefaultStrictness(Expression targetStrictness) {
    String strictnessVal = tryExtractValue(targetStrictness);

    return strictnessVal != null &&
        strictnessVal.equals(TaskProps.LOC_STRICTNESS_DEFAULT);
  }

  private static boolean isDefaultAccuracy(Expression targetAccuracy) {
    String accuracyVal = tryExtractValue(targetAccuracy);
    return accuracyVal != null &&
        accuracyVal.equals(TaskProps.LOC_ACCURACY_DEFAULT);
  }

  /**
   * Try to get string value of expression
   * @param expr
   * @return null if unsuccessful
   */
  private static String tryExtractValue(Expression expr) {
    if (expr instanceof TclString) {
      return ((TclString)expr).value();
    } else if (expr instanceof Token) {
      return ((Token)expr).value();
    }
    return null;
  }

  private static Sequence spawnTask(List<Expression> action, ExecTarget type,
          ExecContext execCx, RuleProps props) {
    assert(type.isAsync());

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
    ExecContext targetContext = type.targetContext();
    // Different task formats for work types
    if (targetContext.isAnyWorkContext()) {
      // TODO: handle priorities?
      taskTokens.addAll(action);
    } else {
      assert (targetContext.isControlContext() ||
              targetContext.isWildcardContext());
      // Treat as work task - no prefix
      // TODO: handle priorities?
      taskTokens.addAll(action);
    }

    Expression task = TclUtil.tclStringAsList(taskTokens);

    Expression par = props.parallelism;
    if (props.targetRank.rankAny && par == null) {
      // Use simple spawn
      res.append(spawnTask(targetContext, priorityVar, task));
      return res;
    } else {
      if (par == null) {
        par = LiteralInt.ONE;
      }

      List<Expression> putArgs = new ArrayList<Expression>();
      putArgs.add(props.targetRank.toTcl());
      putArgs.add(adlbWorkTypeVal(targetContext));
      putArgs.add(task);
      putArgs.add(priorityVar);
      putArgs.add(par);

      boolean addStrictness = !isDefaultStrictness(props.targetStrictness);
      boolean addAccuracy = !isDefaultAccuracy(props.targetAccuracy);
      if (addStrictness || addAccuracy) {
        putArgs.add(props.targetStrictness);
      }

      if (addAccuracy) {
        putArgs.add(props.targetAccuracy);
      }

      // Use put, which takes more arguments
      res.add(new Command(ADLB_PUT, putArgs));
      return res;
    }
  }

  private static Sequence spawnTask(ExecContext type, Value priority,
          Expression task) {
    Sequence res = new Sequence();
    if (priority != null)
      res.add(setPriority(priority));
    res.add(new Command(ADLB_SPAWN, adlbWorkTypeVal(type), task));
    if (priority != null)
      res.add(resetPriority());
    return res;
  }

  /**
   * Return NULL if not a valid async worker name.
   * @param worker
   * @return
   */
  public static Expression asyncWorkerName(WorkContext worker) {
    AsyncExecutor exec = worker.asyncExecutor();
    if (exec == null) {
      return null;
    }
    // Directly use enum name
    return new Token(worker.name());
  }

  public static Expression definedWorkerName(WorkContext worker) {
    return new Token(worker.name());
  }

  public static Expression adlbWorkType(ExecContext target) {
    if (target.isDefaultWorkContext() ||
           target.isControlContext()) {
      return new Value("turbine::WORK_TASK");
    } else {
      return nonDefaultWorkType(target.workContext());
    }
  }

  public static Expression nonDefaultWorkTypeName(WorkContext workContext) {
    Expression workTypeName = asyncWorkerName(workContext);
    if (workTypeName != null) {
      return workTypeName;
    }
    workTypeName = definedWorkerName(workContext);
    if (workTypeName != null) {
      return workTypeName;
    }

    throw new STCRuntimeError("Unknown WorkContext: " + workContext);
  }

  public static Expression nonDefaultWorkType(WorkContext workContext) {
    return Square.fnCall(ADLB_WORK_TYPE, nonDefaultWorkTypeName(workContext));
  }

  /**
   * Tcl is inefficient at looking up namespace vars. Have option of hardcoding
   * work ids
   */
  public static Expression adlbWorkTypeVal(ExecContext target) {
    if (target.isControlContext() ||
        target.isDefaultWorkContext()) {
      return TURBINE_WORKER_WORK_ID;
    } else {
      return nonDefaultWorkType(target.workContext());
    }
  }

  /**
   * Generate code to check compatibility of STC with Turbine, so we don't
   * mistakenly hardcode wrong constants
   */
  public static Command checkConstants() {
    List<Expression> args = new ArrayList<Expression>();
    // Check work types
    // TODO: multiple work types
    for (ExecContext target : Arrays.asList(ExecContext.defaultWorker(),
                                            ExecContext.control())) {
      args.add(new TclString(target.toString(), true));
      args.add(adlbWorkType(target));
      args.add(adlbWorkTypeVal(target));
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
    public static final RuleProps DEFAULT = new RuleProps(TclTarget.RANK_ANY,
            new Token(TaskProps.LOC_STRICTNESS_DEFAULT),
            new Token(TaskProps.LOC_ACCURACY_DEFAULT), null, null);

    public final TclTarget targetRank;
    public final Expression targetStrictness; // cannot be null;
    public final Expression targetAccuracy; // cannot be null;
    public final Expression parallelism; // can be null
    public final Expression priority; // can be null

    public RuleProps(TclTarget targetRank, Expression targetStrictness,
          Expression targetAccuracy, Expression parallelism,
          Expression priority) {
      assert (targetRank != null);
      assert (targetStrictness != null);
      assert (targetAccuracy != null);
      this.targetRank = targetRank;
      this.targetStrictness = targetStrictness;
      this.targetAccuracy = targetAccuracy;
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
          List<? extends Expression> blockOn, List<Expression> action,
          ExecTarget mode, ExecContext execCx, RuleProps props) {
    return ruleHelper(symbol, blockOn, action, mode, execCx, props);
  }

  public static Sequence deepRule(String symbol,
          List<? extends Expression> inputs, int[] depths,
          Expression[] baseTypes, List<Expression> action, ExecTarget mode,
          ExecContext execCx, RuleProps props) {
    assert (inputs.size() == depths.length);
    assert (inputs.size() == baseTypes.length);

    List<Expression> depthExprs = new ArrayList<Expression>(depths.length);
    List<Expression> baseTypeExprs = new ArrayList<Expression>(baseTypes.length);

    for (int depth : depths) {
      depthExprs.add(new LiteralInt(depth));
    }

    for (Expression t : baseTypes) {
      baseTypeExprs.add(t);
    }

    Sequence res = new Sequence();
    if (props.priority != null)
      res.add(setPriority(props.priority));

    List<Expression> args = new ArrayList<Expression>();
    args.add(new TclList(inputs));
    args.add(new TclList(depthExprs));
    args.add(new TclList(baseTypeExprs));
    args.add(TclUtil.tclStringAsList(action));
    ruleAddKeywordArgs(mode, props, args);
    res.add(new Command(DEEPRULE, args));

    if (props.priority != null)
      res.add(resetPriority());
    return res;
  }

  public static Sequence loopRule(String symbol,
          List<? extends Expression> args, List<? extends Expression> blockOn,
          ExecContext execCx) {
    List<Expression> action = new ArrayList<Expression>();
    action.add(new Token(symbol));
    for (Expression arg : args) {
      action.add(arg);
    }
    return ruleHelper(symbol, blockOn, action, ExecTarget.dispatchedControl(),
                      execCx, RuleProps.DEFAULT);
  }

  /**
   * Put reference to arrayVar[arrayIndex] into refVar once it is ready
   *
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
   * Lookup arrayVar[arrayIndex] right away, regardless of whether it is closed
   */
  public static SetVariable arrayLookupImm(String dst, Value arrayVar,
          Expression arrayIndex, Expression readDecr, Expression readAcquire) {
    return new SetVariable(dst,
          new Square(C_LOOKUP, arrayVar, arrayIndex, readDecr, readAcquire));
  }

  /**
   * Convert list of field indices into internal subscript representation
   *
   * @param fieldIndices
   * @return
   */
  public static Expression structSubscript(int fieldIndices[]) {
    assert(fieldIndices.length >= 1);
    for (int i = 0; i < fieldIndices.length; i++)
    {
      assert(fieldIndices[i] >= 0) : Arrays.toString(fieldIndices);
    }

    if (fieldIndices.length == 1) {
      int fieldIndex = fieldIndices[0];

      return new LiteralInt(fieldIndex);
    } else {
      // String separated by "."s
      List<Integer> indexList = new ArrayList<Integer>(fieldIndices.length);
      for (int index: fieldIndices) {
        indexList.add(index);
      }
      String subscript = StringUtils.join(indexList, '.');
      return new TclString(subscript, true);
    }
  }

  /**
   * Construct a new handle to a subscript of the array
   *
   * @param var
   *          may be a plain ID, or a pre-existing alias
   * @param sub array subscript (only one supported for onw)
   * @return
   */
  public static Expression arrayAlias(Value var, Expression sub) {
    return Square.fnCall(MAKE_CONTAINER_SUBSCRIPT, var, sub);
  }

  public static Expression arrayFileAlias(Value var, Expression sub) {
    // TODO
    throw new STCRuntimeError("Unimplemented");
  }

  /**
   * Construct a new handle to a subscript of the struct
   *
   * @param var
   *          may be a plain ID, or a pre-existing alias
   * @param fields
   * @return
   */
  public static Expression structAlias(Value var, int fieldIndices[]) {
    List<Expression> args = new ArrayList<Expression>(
                                      fieldIndices.length + 1);
    args.add(var);
    for (int field : fieldIndices) {
      assert(field >= 0) : var + " " + Arrays.asList(fieldIndices);
      args.add(new LiteralInt(field));
    }
    return Square.fnCall(MAKE_STRUCT_SUBSCRIPT, args);
  }


  public static Expression structFileAlias(Value var, int fieldIndices[]) {
    // TODO
    throw new STCRuntimeError("Unimplemented");
  }

  /**
   * Lookup subscript in a variable
   *
   * @param var
   * @param subscript
   * @return
   */
  public static Expression lookupStruct(Value var,
            Expression subscript, Expression decrRead, Expression incrRead,
            Expression decrWrite, Expression incrWrite) {
    List<Expression> inputs = new ArrayList<Expression>();
    inputs.add(var);
    inputs.add(subscript);
    if (decrRead != null) {
      inputs.add(decrRead);
    }
    if (incrRead != null) {
      assert(decrRead != null);
      inputs.add(incrRead);
    }
    if (decrWrite != null) {
      assert(incrRead != null);
      inputs.add(decrWrite);
    }
    if (incrWrite != null) {
      assert(decrWrite != null);
      inputs.add(incrWrite);
    }
    return Square.fnCall(STRUCT_LOOKUP, inputs);
  }

  public static Command structInsert(Value struct,
      Expression subscript, Expression member, List<TypeName> type,
      Expression decrWrite) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(struct);
    args.add(subscript);
    args.add(member);
    args.addAll(type);
    args.add(decrWrite);
    return new Command(STRUCT_INSERT, args);
  }

  public static Command structCopyIn(Value struct,
      Expression subscript, Expression member, List<TypeName> type,
      Expression decrWrite, Expression incrWrite) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(struct);
    args.add(subscript);
    args.add(member);
    if (type.size() > 1) {
      args.add(new TclList(type));
    } else {
      args.add(type.get(0));
    }
    args.add(decrWrite);
    args.add(incrWrite);
    return new Command(STRUCT_INSERT_R, args);
  }

  public static Command structRefInsert(Value structRef,
      Expression subscript, Expression member, List<TypeName> type,
      Expression decrWrite) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(structRef);
    args.add(subscript);
    args.add(member);
    if (type.size() > 1) {
      args.add(new TclList(type));
    } else {
      args.add(type.get(0));
    }
    args.add(decrWrite);
    return new Command(STRUCTREF_INSERT_V, args);
  }

  public static Command structRefCopyIn(Value structRef,
      Expression subscript, Expression member, List<TypeName> type,
      Expression decrWrite) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(structRef);
    args.add(subscript);
    args.add(member);
    if (type.size() > 1) {
      args.add(new TclList(type));
    } else {
      args.add(type.get(0));
    }
    args.add(decrWrite);
    return new Command(STRUCTREF_INSERT_R, args);
  }

  /**
   * Copy subscript of a variable to another variable
   *
   * @param arrayVar
   * @param arrayIndex
   * @return
   */
  public static Command copyStructSubscript(Value out, Value var,
          Expression subscript, TypeName fieldType, long outWriteDecr) {
    return new Command(STRUCT_REFERENCE, var, subscript, out, fieldType,
                        new LiteralInt(outWriteDecr));
  }

  public static Command copyStructRefSubscript(Value out, Value var,
          Expression subscript, TypeName fieldType, long outWriteDecr) {

    return new Command(STRUCTREF_REFERENCE, var, subscript, out, fieldType,
                       new LiteralInt(outWriteDecr));
  }

  /**
   * Put a reference to arrayVar[indexVar] into refVar
   *
   * @param refVar
   * @param arrayVar
   * @param indexVar
   * @param isArrayRef
   * @return
   */
  public static Command arrayLookupComputed(Value refVar, TypeName refType,
          Value arrayVar, Value indexVar, boolean isArrayRef) {
    if (isArrayRef) {
      return new Command(CR_F_LOOKUP, arrayVar, indexVar, refVar, refType);
    } else {
      return new Command(C_F_LOOKUP, arrayVar, indexVar, refVar, refType);
    }
  }

  public static Expression arrayContains(Value arr, Expression key) {
    return new Square(EXISTS_SUB, arr, key);
  }

  public static Command dereferenceInteger(Value dstVar, Value refVar) {
    return new Command(DEREFERENCE_INTEGER, dstVar, refVar);
  }

  public static Command dereferenceVoid(Value dstVar, Value refVar) {
    return new Command(DEREFERENCE_VOID, dstVar, refVar);
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

  public static Command arrayStoreImmediate(Expression srcVar, Value arrayVar,
          Expression arrayIndex, Expression writersDecr, TypeName valType) {
    return new Command(C_V_INSERT, arrayVar, arrayIndex, srcVar, valType,
            writersDecr);
  }

  public static Command arrayDerefStore(Value srcRefVar, Value arrayVar,
          Expression arrayIndex, Expression writersDecr, TypeName valType) {
    return new Command(C_V_DEREF_INSERT, arrayVar, arrayIndex, srcRefVar,
            valType, writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayDerefStoreComputed(Value srcRefVar,
          Value arrayVar, Value indexVar, Expression writersDecr,
          TypeName valType) {
    return new Command(C_F_DEREF_INSERT, arrayVar, indexVar, srcRefVar,
            valType, writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayStoreComputed(Expression srcVar, Value arrayVar,
          Value indexVar, Expression writersDecr, TypeName valType) {
    // Don't increment writers count, this is done in IC
    return new Command(C_F_INSERT, arrayVar, indexVar, srcVar, valType,
            writersDecr, LiteralInt.FALSE);
  }

  public static Command arrayRefStoreImmediate(Expression srcVar,
          Value arrayVar, Expression arrayIndex, TypeName valType) {
    return new Command(CR_V_INSERT, arrayVar, arrayIndex, srcVar, valType);
  }

  public static Command arrayRefStoreComputed(Expression srcVar,
          Value arrayVar, Value indexVar, TypeName valType) {
    return new Command(CR_F_INSERT, arrayVar, indexVar, srcVar, valType);
  }

  public static Command arrayRefDerefStore(Value srcRefVar, Value arrayVar,
          Expression arrayIndex, TypeName valType) {
    return new Command(CR_V_DEREF_INSERT, arrayVar, arrayIndex, srcRefVar, valType);
  }

  public static Command
          arrayRefDerefStoreComputed(Value srcRefVar, Value arrayVar,
                  Value indexVar, TypeName valType) {
    return new Command(CR_F_DEREF_INSERT, arrayVar, indexVar, srcRefVar, valType);
  }

  public static TclTree
          arrayCreateNested(Value resultVar, Value containerVar,
                  Value indexVar, List<TypeName> fullType) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(resultVar);
    args.add(containerVar);
    args.add(indexVar);
    args.add(new TclList(fullType));

    return new Command(C_F_CREATE_NESTED, args);
  }

  public static TclTree arrayRefCreateNested(Value resultVar,
          Value containerVar, Value indexVar, List<TypeName> fullType) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(resultVar);
    args.add(containerVar);
    args.add(indexVar);
    args.add(new TclList(fullType));

    return new Command(CR_F_CREATE_NESTED, args);
  }

  public static TclTree arrayRefCreateNestedImmIx(Value resultVar,
          Value containerVar, Expression arrIx, List<TypeName> fullType) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(resultVar);
    args.add(containerVar);
    args.add(arrIx);
    args.add(new TclList(fullType));

    return new Command(CR_V_CREATE_NESTED, args);
  }

  public static TclTree arrayCreateNested(String resultVar,
          Value containerVar, Expression arrIx, List<TypeName>  fullType,
          Expression callerReadRefs, Expression callerWriteRefs,
          Expression decrRead, Expression decrWrite) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(containerVar);
    args.add(arrIx);
    args.addAll(fullType);
    args.add(callerReadRefs);
    args.add(callerWriteRefs);
    args.add(decrWrite);
    args.add(decrRead);

    return new SetVariable(resultVar, Square.fnCall(CREATE_NESTED, args));
  }

  public static TclTree bagAppend(Value bag, TypeName elemType,
          Expression elem, Expression decr) {
    // Append to arbitrary subscript
    return new Command(adlbFn("insert"), bag, new TclString(""), elem,
            elemType, decr);
  }

  public static TclTree structCreateNested(String resultVar,
          Value structVar, Expression subscript, List<TypeName> fullType,
          Expression callerReadRefs, Expression callerWriteRefs,
          Expression decrRead, Expression decrWrite) {
    List<Expression> args = new ArrayList<Expression>();
    args.add(structVar);
    args.add(subscript);
    args.addAll(fullType);
    args.add(callerReadRefs);
    args.add(callerWriteRefs);
    args.add(decrWrite);
    args.add(decrRead);

    return new SetVariable(resultVar, Square.fnCall(STRUCT_CREATE_NESTED, args));
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
   *
   * @param var
   * @param change
   * @return
   */
  public static TclTree incrRef(Expression var, Expression change) {
    assert(change != null);
    if (Settings.getBooleanUnchecked(Settings.ENABLE_REFCOUNTING)) {
      return new Command(REFCOUNT_INCR, var, change);
    } else {
      return new Token("");
    }
  }

  /**
   * Modify reference count by amount
   *
   * @param var
   * @param change
   * @return
   */
  public static TclTree decrRef(Expression var, Expression change) {
    assert(change != null);
    if (Settings.getBooleanUnchecked(Settings.ENABLE_REFCOUNTING)) {
      return new Command(REFCOUNT_DECR, var, change);
    } else {
      return new Token("");
    }
  }

  /**
   * Modify reference count by amount
   *
   * @param var
   * @param change
   * @return
   */
  public static TclTree incrFileRef(Expression var, Expression change) {
    assert(change != null);
    if (Settings.getBooleanUnchecked(Settings.ENABLE_REFCOUNTING)) {
      return new Command(FILE_REFCOUNT_INCR, var, change);
    } else {
      return new Token("");
    }
  }

  public static TclTree decrFileRef(Expression var, Expression change) {
    assert(change != null);
    if (Settings.getBooleanUnchecked(Settings.ENABLE_REFCOUNTING)) {
      return new Command(FILE_REFCOUNT_DECR, var, change);
    } else {
      return new Token("");
    }
  }

  public static Expression dictSize(Value tclDict) {
    Expression containerSize =
            Square.fnCall("dict", new Token("size"), tclDict);
    return containerSize;
  }

  public static Expression listLength(Value tclList) {
    Expression containerSize = Square.fnCall("llength", tclList);
    return containerSize;
  }

  public static Expression dictExists(Value tclDict, Expression key) {
    return Square.fnCall("dict", new Token("exists"), tclDict, key);
  }

  /**
   * Return the size of a container
   *
   * @param resultVar
   * @param arr
   * @return
   */
  public static SetVariable containerSize(String resultVar, Value arr) {
    return new SetVariable(resultVar, new Square(ENUMERATE, arr, new Token(
            "count"), new Token("all"), new LiteralInt(0)));
  }

  /**
   * Get entire contents of container
   *
   * @param resultVar
   * @param arr
   * @param includeKeys
   * @return
   */
  public static SetVariable enumerateAll(String resultVar, Value arr,
          boolean includeKeys, Expression readDecr) {
    return enumerate(resultVar, arr, includeKeys, new LiteralInt(0), new Token(
            "all"), readDecr);
  }

  public static SetVariable enumerateAll(String resultVar, Value arr,
          boolean includeKeys) {
    return enumerateAll(resultVar, arr, includeKeys, null);
  }

  /**
   * Retrieve partial contents of container from start to end inclusive start to
   * end are not the logical array indices, but rather physical indices
   *
   * @param resultVar
   * @param arr
   * @param includeKeys
   * @param start
   * @param len
   * @return
   */
  public static SetVariable enumerate(String resultVar, Value arr,
          boolean includeKeys, Expression start, Expression len,
          Expression readDecr) {
    Token mode = includeKeys ? new Token("dict") : new Token("members");
    Expression enumE;
    if (readDecr != null) {
      enumE = new Square(ENUMERATE, arr, mode, len, start, readDecr);
    } else {
      enumE = new Square(ENUMERATE, arr, mode, len, start);
    }
    return new SetVariable(resultVar, enumE);
  }

  public static SetVariable enumerate(String resultVar, Value arr,
          boolean includeKeys, Expression start, Expression len) {
    return enumerate(resultVar, arr, includeKeys, start, len, null);
  }

  public static Command turbineLog(String msg) {
    return new Command(TURBINE_LOG, new TclString(msg, true));
  }

  public static TclTree turbineLog(List<Expression> logMsg) {
    return new Command(TURBINE_LOG, new TclList(logMsg));
  }

  public static TclTree callFunctionSync(String function,
          List<Expression> outVars, List<Expression> inVars) {
    List<Expression> args = new ArrayList<Expression>();
    args.addAll(outVars);
    args.addAll(inVars);
    return new Command(function, args);
  }

  public static Command makeTCLGlobal(String tclName) {
    return new Command(new Token("global"), new Token(tclName));
  }

  public static Square modInteger(Expression a, Expression b) {
    return new Square(new Expression[] { MOD_INTEGER, a, b });
  }

  public static Square divideInteger(Expression a, Expression b) {
    return new Square(new Expression[] { DIVIDE_INTEGER, a, b });
  }

  /**
   * @param cmd
   * @param stderrFilename
   * @param stdoutFilename
   * @param args
   * @return Tcl code to execute external executable
   */
  public static Command exec(Expression cmd, Expression stdinFilename,
          Expression stdoutFilename, Expression stderrFilename,
          List<Expression> args) {
    ArrayList<Expression> args2 = new ArrayList<Expression>(args.size() + 4);
    args2.add(cmd);

    Dict kwOpts =
            execKeywordOpts(stdinFilename, stdoutFilename, stderrFilename);
    args2.add(kwOpts);
    args2.addAll(args);
    return new Command(EXEC_EXTERNAL, args2);
  }

  public static Dict execKeywordOpts(Expression stdinFilename,
          Expression stdoutFilename, Expression stderrFilename) {
    ArrayList<Pair<String, Expression>> keywordOpts =
            new ArrayList<Pair<String, Expression>>();
    if (stdinFilename != null) {
      keywordOpts.add(Pair.create("stdin", stdinFilename));
    }
    if (stdoutFilename != null) {
      keywordOpts.add(Pair.create("stdout", stdoutFilename));
    }
    if (stderrFilename != null) {
      keywordOpts.add(Pair.create("stderr", stderrFilename));
    }
    Dict kwOpts = Dict.dictCreateSE(false, keywordOpts);
    return kwOpts;
  }

  /**
   * Expression that extracts the ID to wait on for a file variable
   *
   * @param fileVar
   * @return
   */
  public static Expression getFileID(Value fileVar) {
    return new Square(GET_FILE_ID, fileVar);
  }

  /**
   * Expression that extracts the filename string future a file variable
   *
   * @param fileVar
   * @return
   */
  public static Expression getFileName(Value fileVar) {
    return new Square(GET_FILE_PATH, fileVar);
  }

  public static Command copyInFilename(Value file, Value filename) {
    return new Command(COPY_IN_FILENAME, file, filename);
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
                                       Expression baseType) {
    assert (nestLevel >= 0);
    return new Square(UNPACK_ARGS, array, new LiteralInt(nestLevel), baseType);
  }

  public static Command fileSet(Value fileFuture, String localFile,
                                  Expression setFilename) {
    return new Command(STORE_FILE, fileFuture, new Token(localFile),
                        setFilename);
  }

  public static TclTree fileGet(String prefixVar, Value varToExpr) {
    return new SetVariable(prefixVar, new Square(RETRIEVE_FILE, varToExpr));
  }

  public static Command decrLocalFileRef(String localFileName) {
    return new Command(DECR_LOCAL_FILE_REFCOUNT, new Token(localFileName));
  }

  public static SetVariable createLocalFile(String varName,
          Expression fileName, Expression initRefcount, Expression mapped) {
    return new SetVariable(varName, new Square(CREATE_LOCAL_FILE_REF, fileName,
            initRefcount, mapped));
  }

  public static SetVariable mkTemp(String varName) {
    return new SetVariable(varName, new Square(MKTEMP));
  }

  public static Expression getFilenameVal(Value fileFuture) {
    return Square.fnCall(GET_FILENAME_VAL, fileFuture);
  }

  public static Command
          setFilenameVal(Value fileFuture, Expression filenameVal) {
    return new Command(SET_FILENAME_VAL, fileFuture, filenameVal);
  }

  public static Command copyFileContents(Value dst, Value src) {
    return new Command(turbFn("copy_local_file_contents"), dst, src);
  }

  public static Command arrayBuild(Value array, Expression kvDict,
          Expression writeDecr, TypeName keyType, List<TypeName> valType) {
    List<Expression> argList = new ArrayList<Expression>();
    argList.addAll(Arrays.asList(array, kvDict, writeDecr, keyType));
    argList.addAll(valType);
    return new Command(ARRAY_KV_BUILD, argList);
  }

  public static Command multisetBuild(Value multiset, Expression list,
          Expression writeDecr, List<TypeName> valType) {
    List<Expression> argList = new ArrayList<Expression>();
    argList.addAll(Arrays.asList(multiset, list, writeDecr));
    argList.addAll(valType);
    return new Command(MULTISET_BUILD, argList);
  }

  public static Command buildRec(List<Expression> typeList, Value target,
          Expression src, long writeDecr) {
    Expression typeListStartIx = LiteralInt.ZERO;
    return new Command(BUILD_REC, Arrays.asList(target, src, new TclList(
                  typeList), typeListStartIx, new LiteralInt(writeDecr)));
  }

  public static TclTree batchDeclare(List<String> batchedVarNames,
          List<TclList> batched) {
    assert(batchedVarNames.size() == batched.size());

    if (batchedVarNames.size() == 0) {
      return new Sequence();
    }

    return lassign(multicreate(batched), batchedVarNames);
  }

  private static Square multicreate(List<TclList> batched) {
    return Square.fnCall(MULTICREATE, batched);
  }

  public static TclTree batchDeclareGlobals(List<String> varNames,
      List<TclList> parameters) {
    assert(varNames.size() == parameters.size());
    if (varNames.size() == 0) {
      return new Sequence();
    }

    return lassign(createGlobals(varNames, parameters), varNames);
  }

  private static Square createGlobals(List<String> varNames,
                                      List<TclList> parameters) {
    ;
    return Square.fnCall(DECLARE_GLOBALS,
        new TclList(TclString.makeList(varNames, true)),
        new TclList(parameters));
  }

  public static TclTree batchDeclareFiles(List<String> fileVarNames,
      List<TclList> parameters, List<Boolean> isMappeds) {
    assert(parameters.size() == fileVarNames.size());
    assert(isMappeds.size() == fileVarNames.size());
    if (fileVarNames.size() == 0) {
      return new Sequence();
    }

    TclList isMappedList = new TclList();
    for (Boolean isMapped: isMappeds) {
      isMappedList.add(LiteralInt.boolValue(isMapped));
    }

    return lassign(
        Square.fnCall(MAKE_FILE_TDS, multicreate(parameters), isMappedList),
        fileVarNames);
  }

  public static TclTree batchDeclareGlobalFiles(List<String> fileVarNames,
                List<TclList> parameters, List<Boolean> isMappeds) {
    assert(parameters.size() == fileVarNames.size());
    assert(isMappeds.size() == fileVarNames.size());

    if (fileVarNames.size() == 0) {
      return new Sequence();
    }

    TclList isMappedList = new TclList();
    for (Boolean isMapped: isMappeds) {
      isMappedList.add(LiteralInt.boolValue(isMapped));
    }


    return lassign(Square.fnCall(MAKE_FILE_TDS,
        createGlobals(fileVarNames, parameters), isMappedList), fileVarNames);
  }

  private static TclTree lassign(Expression expr, List<String> varNames) {
    assert(varNames.size() >= 1) : "need at least one variable for lassign";
    List<Expression> exprs = new ArrayList<Expression>();
    exprs.add(expr);

    for (String varName: varNames) {
      exprs.add(new Token(varName));
    }
    return new Command(LASSIGN, exprs);
  }

  public static Command log(TclString logMsg) {
    return new Command(TURBINE_LOG, logMsg);
  }

  public static Expression stringEqual(Expression s1, Expression s2) {
    return new Square(new Token("string"), new Token("equal"), s1, s2);
  }

  /**
   *
   * @param executor
   * @param cmdName
   * @param outVarNames
   * @param taskArgExprs
   * @param taskPropExprs
   * @param successContinuation
   *          may be null if no continuation to call
   * @param failureContinuation
   *          may be null if no continuation to call
   * @return
   */
  public static Command asyncExec(AsyncExecutor executor, Expression cmdName,
          List<Token> outVarNames, List<Expression> taskArgExprs,
          List<Pair<String, Expression>> taskPropExprs,
          List<Expression> stageIns, List<Expression> stageOuts,
          List<Expression> successContinuation, List<Expression> failureContinuation) {
    List<Expression> execArgs = new ArrayList<Expression>();
    execArgs.add(cmdName);

    execArgs.add(new TclList(taskArgExprs));
    execArgs.add(new TclList(stageIns));
    execArgs.add(new TclList(stageOuts));

    execArgs.add(Dict.dictCreateSE(true, taskPropExprs));
    if (successContinuation != null) {
      execArgs.add(TclUtil.tclStringAsList(successContinuation));
    }
    if (failureContinuation != null) {
      assert(successContinuation != null);
      execArgs.add(TclUtil.tclStringAsList(failureContinuation));
    }

    List<TemplateArg> execTemplateArgs = new ArrayList<TemplateArg>();
    for (Expression execArg: execArgs) {
      execTemplateArgs.add(TemplateArg.createInput(null, execArg));
    }

    List<TclTree> cmdToks = TclTemplateProcessor.processTemplate(executor.name(),
                          executor.template(), execTemplateArgs,
                          Collections.<TemplateArg>emptyList());
    return new Command(cmdToks);
  }

  /**
   * @param unpacked
   *          List of alternating types/values
   */
  public static Expression xptPack(List<Expression> unpacked) {
    return Square.fnCall(XPT_PACK, unpacked);
  }

  /**
   * @param unpacked
   *          List of variable names
   * @param packed
   * @param types
   *          List of types
   */
  public static Command xptUnpack(List<String> unpacked, Expression packed,
          List<TypeName> types) {
    List<Expression> unpackArgs = new ArrayList<Expression>();
    for (String unpackedVar : unpacked) {
      unpackArgs.add(new Token(unpackedVar));
    }
    unpackArgs.add(packed);
    unpackArgs.addAll(types);
    return new Command(XPT_UNPACK, unpackArgs);
  }

  public static Expression xptWriteEnabled() {
    return Square.fnCall(XPT_WRITE_ENABLED);
  }

  public static Expression xptLookupEnabled() {
    return Square.fnCall(XPT_LOOKUP_ENABLED);
  }

  /**
   * Both lists are alternating types and values, allowing them to be serialized
   * with the correct type
   *
   * @param packedKey
   * @param packedVal
   * @param persist
   *          how to persist
   * @param indexAdd
   *          whether to add to index
   * @return
   */
  public static Command xptWrite(Expression packedKey, Expression packedVal,
          XptPersist persist, Expression indexAdd) {
    return new Command(XPT_WRITE, Arrays.asList(packedKey, packedVal,
            persist.toToken(), indexAdd));
  }

  /**
   * @param resultVarName
   * @param packedKey
   * @return
   */
  public static Expression xptLookupExpr(String resultVarName,
          Expression packedKey) {
    return Square.fnCall(XPT_LOOKUP, packedKey, new Token(resultVarName));
  }

  /**
   * @param existsVarName
   * @param resultVarName
   * @param packedKey
   * @return
   */
  public static SetVariable xptLookupStmt(String existsVarName,
          String resultVarName, Expression packedKey) {
    return new SetVariable(existsVarName, xptLookupExpr(resultVarName,
            packedKey));
  }

  public static Command addDebugSymbol(int symbol, String name, String context) {
    return new Command(ADD_DEBUG_SYMBOL, new LiteralInt(symbol),
                       new TclString(name, true), new TclString(context, true));
  }

}
