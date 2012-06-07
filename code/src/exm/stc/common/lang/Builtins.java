
package exm.stc.common.lang;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.frontend.Context;

/**
 * This class is used to store information about built-in functions defined for the
 * current program.
 *
 */
public class Builtins {

 public static final String COPY_FILE = "copy_file";
 public static final String FILENAME = "filename";
 public static final String INPUT_FILE = "input_file";

  /** Keep track of assert variants so they can be disabled as an optimization */
  private static final HashSet<String> assertVariants = new
                HashSet<String>();


  static {

    assertVariants.add("assert");
    assertVariants.add("assertEqual");
    assertVariants.add("assertLT");
    assertVariants.add("assertLTE");
  }

  /**
   * @param fnName true if the named builtin is some kind of assert statemetn
   * @return
   */
  public static boolean isAssertVariant(String fnName) {
    return assertVariants.contains(fnName);
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
    private static HashMap<String, BuiltinOpcode>
              localEquivalents = new HashMap<String, BuiltinOpcode>();
    
    
    /** Built-ins which are known to be deterministic and side-effect free */
    private static HashSet<String> knownPure = 
                                        new HashSet<String>();

    /** Built-ins which are known to be deterministic */
    private static HashSet<String> commutative = 
                                        new HashSet<String>();

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
    
    public static BuiltinOpcode getLocalEquiv(String builtinFunction) {
      return localEquivalents.get(builtinFunction);
    }
    
    public static boolean isCommutative(String builtinFunction) {
      return commutative.contains(builtinFunction);
    }
    
    public static boolean isCopyFunction(String builtinFunction) {
      return copyFunctions.contains(builtinFunction);
    }
    
    public static boolean isMinMaxFunction(String builtinFunction) {
      return maxMinFunctions.contains(builtinFunction);
    }
    
    /* Load all of the info (hardcoded for the time being) */
    static {  
      initSemanticInfo();
    }

    private static void initSemanticInfo() {
      for (String numType: Arrays.asList("integer", "float")) {
        sideEffectFree.add("max_" + numType);
        maxMinFunctions.add("max_" + numType);
        sideEffectFree.add("min_" + numType);
        maxMinFunctions.add("min_" + numType);
        
        sideEffectFree.add("abs_" + numType);
        sideEffectFree.add("pow_" + numType);
        
        // Commutative operators
        commutative.add("max_" + numType);
        commutative.add("min_" + numType);
        
      }

      sideEffectFree.add("make_void");
      
      sideEffectFree.add(COPY_FILE);
      copyFunctions.add(COPY_FILE);

      sideEffectFree.add(INPUT_FILE);
      knownPure.add(INPUT_FILE);
      sideEffectFree.add(FILENAME);
      knownPure.add(FILENAME);
    
      sideEffectFree.add("strcat");
      sideEffectFree.add("substring");

      sideEffectFree.add("xor");
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
      localEquivalents.put("argc", BuiltinOpcode.ARGC_GET);
      localEquivalents.put("argv_contains", BuiltinOpcode.ARGV_CONTAINS);
      localEquivalents.put("argv", BuiltinOpcode.ARGV_GET);
      localEquivalents.put("getenv", BuiltinOpcode.GETENV);
      
      sideEffectFree.add("turbine_workers");
      sideEffectFree.add("turbine_engines");
      sideEffectFree.add("adlb_servers");
      localEquivalents.put("turbine_workers", BuiltinOpcode.N_WORKERS);
      localEquivalents.put("turbine_engines", BuiltinOpcode.N_ENGINES);
      localEquivalents.put("adlb_servers", BuiltinOpcode.N_ADLB_SERVERS);
      
      localEquivalents.put("max_integer", BuiltinOpcode.MAX_INT);
      localEquivalents.put("min_integer", BuiltinOpcode.MIN_INT);
      localEquivalents.put("abs_integer", BuiltinOpcode.ABS_INT);
      localEquivalents.put("pow_integer", BuiltinOpcode.POW_INT);
      
      localEquivalents.put("max_float", BuiltinOpcode.MAX_FLOAT);
      localEquivalents.put("min_float", BuiltinOpcode.MIN_FLOAT);
      localEquivalents.put("abs_float", BuiltinOpcode.ABS_FLOAT);
      localEquivalents.put("pow_float", BuiltinOpcode.POW_FLOAT);
      localEquivalents.put("is_nan", BuiltinOpcode.IS_NAN);
      
      localEquivalents.put("ceil", BuiltinOpcode.CEIL);
      localEquivalents.put("floor", BuiltinOpcode.FLOOR);
      localEquivalents.put("round", BuiltinOpcode.ROUND);
      localEquivalents.put("itof", BuiltinOpcode.INTTOFLOAT);
      localEquivalents.put("toint", BuiltinOpcode.STRTOINT);
      localEquivalents.put("fromint", BuiltinOpcode.INTTOSTR);
      localEquivalents.put("tofloat", BuiltinOpcode.STRTOFLOAT);
      localEquivalents.put("fromfloat", BuiltinOpcode.FLOATTOSTR);
      localEquivalents.put("exp", BuiltinOpcode.EXP);
      localEquivalents.put("log", BuiltinOpcode.LOG);
      localEquivalents.put("sqrt", BuiltinOpcode.SQRT);
      
      localEquivalents.put("strcat", BuiltinOpcode.STRCAT);
      localEquivalents.put("substring", BuiltinOpcode.SUBSTRING);
      
      localEquivalents.put("xor", BuiltinOpcode.XOR);
      
      localEquivalents.put("assert", BuiltinOpcode.ASSERT);
      localEquivalents.put("assertEqual", BuiltinOpcode.ASSERT_EQ);
      localEquivalents.put("trace", BuiltinOpcode.TRACE);
      localEquivalents.put("metadata", BuiltinOpcode.METADATA);
      localEquivalents.put("printf", BuiltinOpcode.PRINTF);
      localEquivalents.put("sprintf", BuiltinOpcode.SPRINTF);
      
      // Random functions
      localEquivalents.put("random", BuiltinOpcode.RANDOM);
      localEquivalents.put("randint", BuiltinOpcode.RAND_INT);
      
      // Add information based on local op
      for (Entry<String, BuiltinOpcode> e: localEquivalents.entrySet()) {
        if (!Operators.isImpure(e.getValue())) {
          knownPure.add(e.getKey());
        }
      }
    }
  }
  
}
