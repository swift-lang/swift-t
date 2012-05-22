
package exm.ast;

import java.util.*;

import org.apache.log4j.Logger;

import exm.ast.Types.FunctionType;
import exm.ast.Types.PrimType;
import exm.ast.Types.SwiftType;
import exm.parser.antlr.ExMParser;
import exm.parser.ui.Main;
import exm.parser.util.InvalidSyntaxException;

public class Builtins {

 public static final String COPY_FLOAT = "copy_float";
 public static final String COPY_STRING = "copy_string";
 public static final String COPY_INTEGER = "copy_integer";
 public static final String COPY_BOOLEAN = "copy_boolean";
 public static final String COPY_BLOB = "copy_blob";
 public static final String COPY_VOID = "copy_void";

  @SuppressWarnings("unused")
  private final static Logger logger = Main.getLogger();

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

  static void add(String name, FunctionType ft) {
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
}
