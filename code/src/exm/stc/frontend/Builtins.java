
package exm.stc.frontend;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.Types;
import exm.stc.ast.Types.FunctionType;
import exm.stc.ast.Types.PrimType;
import exm.stc.ast.Types.SwiftType;
import exm.stc.common.exceptions.InvalidSyntaxException;

public class Builtins {

 public static final String COPY_FLOAT = "copy_float";
 public static final String COPY_STRING = "copy_string";
 public static final String COPY_INTEGER = "copy_integer";
 public static final String COPY_BOOLEAN = "copy_boolean";
 public static final String COPY_BLOB = "copy_blob";
 public static final String COPY_VOID = "copy_void";

  private static final Map<String, FunctionType> builtins =
                        new HashMap<String, FunctionType>();


  /** Map of <number type> -> ( <token type> -> <function name> ) */
  private static final Map<Types.PrimType, Map<Integer, String>>
            arithOps = new HashMap<Types.PrimType, Map<Integer, String>>();

  /** Keep track of assert variants so they can be disabled as an optimization */
  private static final HashSet<String> assertVariants = new
                HashSet<String>();


  static {
    fillArithOps();

    assertVariants.add("assert");
    assertVariants.add("assertEqual");
    assertVariants.add("assertLT");
    assertVariants.add("assertLTE");
  }

  /**
     Is this the name of a builtin?
   */
  public static boolean exists(String name) {
    return builtins.containsKey(name);
  }

  public static void add(String name, FunctionType ft) {
    builtins.put(name, ft);
  }

  private static void fillArithOps() {
    for (PrimType numType: Arrays.asList(PrimType.FLOAT, PrimType.INTEGER,
        PrimType.STRING, PrimType.BOOLEAN)) {
      String turbineTypeName = numType.toString().toLowerCase();
      HashMap<Integer, String> opMapping = new HashMap<Integer, String>();

      // Want equality tests for all primitives
      opMapping.put(ExMParser.EQUALS, "eq_" + turbineTypeName);
      opMapping.put(ExMParser.NEQUALS, "neq_" + turbineTypeName);

      if (numType == PrimType.STRING) {
        opMapping.put(ExMParser.PLUS, "strcat");
      }

      if (numType == PrimType.INTEGER || numType == PrimType.FLOAT) {
        opMapping.put(ExMParser.PLUS, "plus_" + turbineTypeName);
        opMapping.put(ExMParser.MINUS, "minus_" + turbineTypeName);
        opMapping.put(ExMParser.MULT, "multiply_" + turbineTypeName);
        if (numType == PrimType.INTEGER) {
          opMapping.put(ExMParser.INTDIV, "divide_" + turbineTypeName);
          opMapping.put(ExMParser.MOD, "mod_" + turbineTypeName);
        } else {
          opMapping.put(ExMParser.DIV, "divide_" + turbineTypeName);
        }
        opMapping.put(ExMParser.NEGATE, "negate_" + turbineTypeName);
        opMapping.put(ExMParser.GT, "gt_" + turbineTypeName);
        opMapping.put(ExMParser.GTE, "gte_" + turbineTypeName);
        opMapping.put(ExMParser.LT, "lt_" + turbineTypeName);
        opMapping.put(ExMParser.LTE, "lte_" + turbineTypeName);
        opMapping.put(ExMParser.POW, "pow_" + turbineTypeName);
      }

      if (numType == PrimType.BOOLEAN) {
        opMapping.put(ExMParser.NOT, "not");
        opMapping.put(ExMParser.AND, "and");
        opMapping.put(ExMParser.OR, "or");
      }

      arithOps.put(numType, opMapping);
    }
  }

  public static Map<String, FunctionType> getBuiltins() {
    return Collections.unmodifiableMap(builtins);
  }

  /**
   * @param numType the numeric type to look up the operator
   * @param tokenType the antlr token number for the operator
   * @return the name of a builtin function for the operator, or null if
   *          the operator is unknown
   */
  public static String getArithBuiltin(Types.PrimType numType, int tokenType) {
    Map<Integer, String> mp = arithOps.get(numType);
    if (mp == null) {
      return null;
    }
    String fnName = mp.get(tokenType);
    return fnName;
  }

  /**
   *
   * @param builtin the name of a builtin function
   * @return the type of the builtin, or null if the built-in is unknown
   */
  public static FunctionType getBuiltinType(String builtin)
  {
    return builtins.get(builtin);
  }

  private static final Map<String, SwiftType> nativeTypes =
        new HashMap<String, SwiftType>();

  static {
    nativeTypes.put("int", Types.FUTURE_INTEGER);
    nativeTypes.put("string", Types.FUTURE_STRING);
    nativeTypes.put("float", Types.FUTURE_FLOAT);
    nativeTypes.put("boolean", Types.FUTURE_BOOLEAN);
    nativeTypes.put("void", Types.FUTURE_VOID);
    nativeTypes.put("blob", Types.FUTURE_BLOB);
    nativeTypes.put("file", Types.FUTURE_FILE);
    nativeTypes.put("updateable_float", Types.UPDATEABLE_FLOAT);
  }

  public static Map<String, SwiftType> getNativeTypes() {
    return Collections.unmodifiableMap(nativeTypes);
  }

  /**
   * @param fnName true if the named builtin is some kind of assert statemetn
   * @return
   */
  public static boolean isAssertVariant(String fnName) {
    return assertVariants.contains(fnName);
  }
  
  
  /**
   * Opcodes for operations operating on local variables
   */
  public static enum LocalOpcode {
    PLUS_INT, MINUS_INT, MULT_INT, DIV_INT, MOD_INT,
    PLUS_FLOAT, MINUS_FLOAT, MULT_FLOAT, DIV_FLOAT,
    NEGATE_INT, NEGATE_FLOAT, POW_INT, POW_FLOAT,
    MAX_INT, MAX_FLOAT, MIN_INT, MIN_FLOAT,
    ABS_INT, ABS_FLOAT,
    EQ_INT, NEQ_INT, GT_INT, LT_INT, GTE_INT, LTE_INT,
    EQ_FLOAT, NEQ_FLOAT, GT_FLOAT, LT_FLOAT, GTE_FLOAT, LTE_FLOAT,
    EQ_BOOL, NEQ_BOOL,
    EQ_STRING, NEQ_STRING,
    NOT, AND, OR, XOR,
    STRCAT, SUBSTRING, 
    COPY_INT, COPY_FLOAT, COPY_BOOL, COPY_STRING, COPY_BLOB,
    FLOOR, CEIL, ROUND, INTTOFLOAT,
    STRTOINT, INTTOSTR, STRTOFLOAT, FLOATTOSTR,
    LOG, EXP, SQRT, IS_NAN,
    RANDOM, RAND_INT,
    TRACE, ASSERT_EQ, ASSERT, PRINTF, SPRINTF,
    ARGC_GET, ARGV_GET, ARGV_CONTAINS, GETENV,
    N_WORKERS, N_ENGINES, N_ADLB_SERVERS,
    METADATA
  }
  
  public static enum UpdateMode {
    MIN, SCALE, INCR;

    @SuppressWarnings("serial")
    private static final Map<String, UpdateMode> nameMap = new
              HashMap<String, UpdateMode>() {{
                put("min", MIN);
                put("scale", SCALE);
                put("incr", INCR);
              }};
    public static UpdateMode fromString(Context errContext, String modeName)
                                            throws InvalidSyntaxException {
      UpdateMode result = nameMap.get(modeName);
      
      if (result == null) {
        throw new InvalidSyntaxException(errContext, "invalid update mode: "
            + modeName + " valid options are: " + nameMap.values());
      }
      return result;
    }
  }
  
  public static class SemanticInfo {
    
    /** Names of built-ins which don't have side effects */
    private static HashSet<String> sideEffectFree =
                                            new HashSet<String>();
    
    /** Names of built-ins which have a local equivalent operation */
    private static HashMap<String, LocalOpcode>
              localEquivalents = new HashMap<String, LocalOpcode>();
    
    
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
    private static HashSet<String> maxMinFunctions =  
            new HashSet<String>();
    
    
    
    public static boolean isSideEffectFree(String builtinFunction) {
      return sideEffectFree.contains(builtinFunction);
    }
    
    public static boolean hasLocalEquiv(String builtinFunction) {
      return localEquivalents.containsKey(builtinFunction);
    }
    
    public static LocalOpcode getLocalEquiv(String builtinFunction) {
      return localEquivalents.get(builtinFunction);
    }
    
    public static boolean isCommutative(String builtinFunction) {
      return commutative.contains(builtinFunction);
    }
    
    public static boolean isFlippable(String builtinFunction) {
      return flippedOps.containsKey(builtinFunction);
    }
    
    public static String getFlipped(String builtinFunction) {
      return flippedOps.get(builtinFunction);
    }
    
    public static boolean isCopyFunction(String builtinFunction) {
      return copyFunctions.contains(builtinFunction);
    }
    
    public static boolean isCopyOp(LocalOpcode localop) {
      return localop == LocalOpcode.COPY_INT 
          || localop == LocalOpcode.COPY_BOOL
          || localop == LocalOpcode.COPY_FLOAT 
          || localop == LocalOpcode.COPY_STRING
          || localop == LocalOpcode.COPY_BLOB;
    }
    
    public static boolean isMinMaxFunction(String builtinFunction) {
      return maxMinFunctions.contains(builtinFunction);
    }
    
    public static boolean isMinMaxOp(LocalOpcode localop) {
      return localop == LocalOpcode.MAX_FLOAT || localop == LocalOpcode.MAX_INT
        || localop == LocalOpcode.MIN_FLOAT || localop == LocalOpcode.MIN_INT;
    }
    
    /* Load all of the info (hardcoded for the time being) */
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
        maxMinFunctions.add("max_" + numType);
        sideEffectFree.add("min_" + numType);
        maxMinFunctions.add("min_" + numType);
        
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
      sideEffectFree.add("getenv");
      localEquivalents.put("argc", LocalOpcode.ARGC_GET);
      localEquivalents.put("argv_contains", LocalOpcode.ARGV_CONTAINS);
      localEquivalents.put("argv", LocalOpcode.ARGV_GET);
      localEquivalents.put("getenv", LocalOpcode.GETENV);
      
      sideEffectFree.add("turbine_workers");
      sideEffectFree.add("turbine_engines");
      sideEffectFree.add("adlb_servers");
      localEquivalents.put("turbine_workers", LocalOpcode.N_WORKERS);
      localEquivalents.put("turbine_engines", LocalOpcode.N_ENGINES);
      localEquivalents.put("adlb_servers", LocalOpcode.N_ADLB_SERVERS);
      
    
      localEquivalents.put("plus_integer", LocalOpcode.PLUS_INT);
      localEquivalents.put("minus_integer", LocalOpcode.MINUS_INT);
      localEquivalents.put("multiply_integer", LocalOpcode.MULT_INT);
      localEquivalents.put("divide_integer", LocalOpcode.DIV_INT);
      localEquivalents.put("mod_integer", LocalOpcode.MOD_INT);
      localEquivalents.put("negate_integer", LocalOpcode.NEGATE_INT);
      localEquivalents.put("max_integer", LocalOpcode.MAX_INT);
      localEquivalents.put("min_integer", LocalOpcode.MIN_INT);
      localEquivalents.put("abs_integer", LocalOpcode.ABS_INT);
      localEquivalents.put("pow_integer", LocalOpcode.POW_INT);
      
      localEquivalents.put("eq_integer", LocalOpcode.EQ_INT);
      localEquivalents.put("neq_integer", LocalOpcode.NEQ_INT);
      localEquivalents.put("lt_integer", LocalOpcode.LT_INT);
      localEquivalents.put("lte_integer", LocalOpcode.LTE_INT);
      localEquivalents.put("gt_integer", LocalOpcode.GT_INT);
      localEquivalents.put("gte_integer", LocalOpcode.GTE_INT);
      
      localEquivalents.put("plus_float", LocalOpcode.PLUS_FLOAT);
      localEquivalents.put("minus_float", LocalOpcode.MINUS_FLOAT);
      localEquivalents.put("multiply_float", LocalOpcode.MULT_FLOAT);
      localEquivalents.put("divide_float", LocalOpcode.DIV_FLOAT);
      localEquivalents.put("negate_float", LocalOpcode.NEGATE_FLOAT);
      localEquivalents.put("max_float", LocalOpcode.MAX_FLOAT);
      localEquivalents.put("min_float", LocalOpcode.MIN_FLOAT);
      localEquivalents.put("abs_float", LocalOpcode.ABS_FLOAT);
      localEquivalents.put("pow_float", LocalOpcode.POW_FLOAT);
      localEquivalents.put("is_nan", LocalOpcode.IS_NAN);
      
      localEquivalents.put("ceil", LocalOpcode.CEIL);
      localEquivalents.put("floor", LocalOpcode.FLOOR);
      localEquivalents.put("round", LocalOpcode.ROUND);
      localEquivalents.put("itof", LocalOpcode.INTTOFLOAT);
      localEquivalents.put("toint", LocalOpcode.STRTOINT);
      localEquivalents.put("fromint", LocalOpcode.INTTOSTR);
      localEquivalents.put("tofloat", LocalOpcode.STRTOFLOAT);
      localEquivalents.put("fromfloat", LocalOpcode.FLOATTOSTR);
      localEquivalents.put("exp", LocalOpcode.EXP);
      localEquivalents.put("log", LocalOpcode.LOG);
      localEquivalents.put("sqrt", LocalOpcode.SQRT);
      
      localEquivalents.put("eq_float", LocalOpcode.EQ_FLOAT);
      localEquivalents.put("neq_float", LocalOpcode.NEQ_FLOAT);
      localEquivalents.put("lt_float", LocalOpcode.LT_FLOAT);
      localEquivalents.put("lte_float", LocalOpcode.LTE_FLOAT);
      localEquivalents.put("gt_float", LocalOpcode.GT_FLOAT);
      localEquivalents.put("gte_float", LocalOpcode.GTE_FLOAT);      
      
      localEquivalents.put("eq_string", LocalOpcode.EQ_STRING);
      localEquivalents.put("neq_string", LocalOpcode.NEQ_STRING);
      localEquivalents.put("strcat", LocalOpcode.STRCAT);
      localEquivalents.put("substrict", LocalOpcode.SUBSTRING);
      
      localEquivalents.put("eq_bool", LocalOpcode.EQ_FLOAT);
      localEquivalents.put("neq_bool", LocalOpcode.NEQ_FLOAT);
      localEquivalents.put("and", LocalOpcode.AND);
      localEquivalents.put("or", LocalOpcode.OR);
      localEquivalents.put("xor", LocalOpcode.XOR);
      localEquivalents.put("not", LocalOpcode.NOT);
      
      localEquivalents.put("copy_integer", LocalOpcode.COPY_INT);
      localEquivalents.put("copy_float", LocalOpcode.COPY_FLOAT);
      localEquivalents.put("copy_string", LocalOpcode.COPY_STRING);
      localEquivalents.put("copy_boolean", LocalOpcode.COPY_BOOL);
      localEquivalents.put("copy_blob", LocalOpcode.COPY_BLOB);

      localEquivalents.put("assert", LocalOpcode.ASSERT);
      localEquivalents.put("assertEqual", LocalOpcode.ASSERT_EQ);
      localEquivalents.put("trace", LocalOpcode.TRACE);
      localEquivalents.put("metadata", LocalOpcode.METADATA);
      localEquivalents.put("printf", LocalOpcode.PRINTF);
      localEquivalents.put("sprintf", LocalOpcode.SPRINTF);
      
      // Random functions
      randomFunctions.add("random");
      randomFunctions.add("randint");
      localEquivalents.put("random", LocalOpcode.RANDOM);
      localEquivalents.put("randint", LocalOpcode.RAND_INT);
      
      // All local arith ops are deterministic aside from random ones
      knownDeterministic.addAll(localEquivalents.keySet());
      knownDeterministic.removeAll(randomFunctions);
      knownDeterministic.remove("trace");
      knownDeterministic.remove("assert");
      knownDeterministic.remove("assertEqual");
      knownDeterministic.remove("printf");
    }
  }
  
  
  /** Keep track of which of the above functions are randomized or have
   * side effects, so we don't optimized these things out
   */
  private static HashSet<LocalOpcode> sideeffectLocalOps
            = new HashSet<LocalOpcode>();
  static {
    sideeffectLocalOps.add(LocalOpcode.RANDOM);
    sideeffectLocalOps.add(LocalOpcode.RAND_INT);
    sideeffectLocalOps.add(LocalOpcode.TRACE);
    sideeffectLocalOps.add(LocalOpcode.ASSERT);
    sideeffectLocalOps.add(LocalOpcode.ASSERT_EQ);
    sideeffectLocalOps.add(LocalOpcode.PRINTF);
    sideeffectLocalOps.add(LocalOpcode.METADATA);
  }
  
  public static boolean hasSideEffect(LocalOpcode op) {
    return sideeffectLocalOps.contains(op);
  }
}
