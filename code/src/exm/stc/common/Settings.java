
package exm.stc.common;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import exm.stc.common.exceptions.InvalidOptionException;

/**
 * General STC settings
 * @author wozniak
 * 
 * List of Java properties not processed here: 
 * stc.logfile: used to set up logging in Main 
 * */
public class Settings
{
  public static final String TURBINE_VERSION = "stc.turbine.version";
  public static final String DEBUG_LEVEL = "stc.debugging";

  public static final String RPATH = "stc.rpath";
  
  public static final String IC_OUTPUT_FILE = "stc.ic.output-file";
  public static final String OPT_CONSTANT_FOLD = "stc.opt.constant-fold";
  public static final String OPT_SHARED_CONSTANTS = "stc.opt.shared-constants";
  public static final String OPT_FLATTEN_NESTED = "stc.opt.flatten-nested";
  public static final String OPT_BRANCH_PREDICT = "stc.opt.branch-predict";
  public static final String OPT_DEAD_CODE_ELIM = "stc.opt.dead-code-elim";
  public static final String OPT_FORWARD_DATAFLOW =
                      "stc.opt.forward-dataflow-analysis";
  public static final String OPT_WAIT_COALESCE = "stc.opt.wait-coalesce";
  public static final String OPT_CONTROLFLOW_FUSION =
                                            "stc.opt.controlflow-fusion";
  public static final String OPT_DISABLE_ASSERTS = "stc.opt.disable-asserts";
  public static final String OPT_UNROLL_LOOPS = "stc.opt.unroll-loops";
  public static final String OPT_NUM_PASSES = "stc.opt.numpasses";
  public static final String TURBINE_NO_STACK = "stc.codegen.no-stack";
  public static final String TURBINE_NO_STACK_VARS = "stc.codegen.no-stack-vars";

  // TODO: get rid of this once no longer required
  public static final String ARRAY_REF_SWITCHEROO = "stc.array-ref-switcheroo";

  private static final Properties properties;

  static {
    Properties defaults = new Properties();
    // Set defaults here
    defaults.setProperty(TURBINE_VERSION, "0.0.4");
    defaults.setProperty(DEBUG_LEVEL, "COMMENTS");
    defaults.setProperty(IC_OUTPUT_FILE, "");
    defaults.setProperty(RPATH, "");
    
    // Code optimisation settings - defaults
    defaults.setProperty(OPT_FLATTEN_NESTED, "true");
    defaults.setProperty(OPT_CONSTANT_FOLD, "true");
    defaults.setProperty(OPT_SHARED_CONSTANTS, "true");
    defaults.setProperty(OPT_BRANCH_PREDICT, "true");
    defaults.setProperty(OPT_DEAD_CODE_ELIM, "true");
    defaults.setProperty(OPT_UNROLL_LOOPS, "true");
    defaults.setProperty(OPT_DISABLE_ASSERTS, "false");
    defaults.setProperty(OPT_FORWARD_DATAFLOW, "true");
    defaults.setProperty(OPT_WAIT_COALESCE, "false");
    defaults.setProperty(OPT_CONTROLFLOW_FUSION, "true");
    defaults.setProperty(OPT_NUM_PASSES, "5");
    defaults.setProperty(ARRAY_REF_SWITCHEROO, "false");

    // Turbine code generation
    // Turbine version
    defaults.setProperty(TURBINE_VERSION, "unknown");
    //If set to true, don't allocate any stacks at all
    defaults.setProperty(TURBINE_NO_STACK, "true");
    //If set to true, don't store any variables in stack
    defaults.setProperty(TURBINE_NO_STACK_VARS, "true");
    properties = new Properties(defaults);
  }

  /**
     Try to overwrite each default property in {@link properties}
     with value from System
   */
  public static void initSTCProperties() throws InvalidOptionException {
    // Pull in properties from wrapper script
    for (String key: properties.stringPropertyNames()) {
      String sysVal = System.getProperty(key);
      if (sysVal != null) {
        properties.setProperty(key, sysVal);
      }
    }
    validateProperties();
  }

  /**
     RPATH should be a Unix-style colon-separated list of directories
     @return Possibly empty String array
   */
  public static String[] getRpaths() {
    String rpaths = get(RPATH);
    if (rpaths == null)
      return new String[0];
    return rpaths.split(":");
  }
  
  /**
   * Do any checks for correctness of properties
   * @throws InvalidOptionException
   */
  private static void validateProperties() throws InvalidOptionException {
    // Check that boolean values are correct
    getBoolean(OPT_FLATTEN_NESTED);
    getBoolean(OPT_CONSTANT_FOLD);
    getBoolean(OPT_BRANCH_PREDICT);
    getBoolean(OPT_DEAD_CODE_ELIM);
    getBoolean(OPT_DISABLE_ASSERTS);
    getBoolean(OPT_FORWARD_DATAFLOW);
    getBoolean(OPT_WAIT_COALESCE);
    getBoolean(OPT_CONTROLFLOW_FUSION);
    getBoolean(OPT_UNROLL_LOOPS);
    getBoolean(TURBINE_NO_STACK);
    getBoolean(TURBINE_NO_STACK_VARS);
    getBoolean(ARRAY_REF_SWITCHEROO);

    getLong(OPT_NUM_PASSES);

    checkOneOf(DEBUG_LEVEL, Arrays.asList("off", "comments", "debugger"));

    if (getBoolean(TURBINE_NO_STACK)) {
      // no stack implies cannot put anything in stack
      properties.setProperty(TURBINE_NO_STACK_VARS, "true");
    }
  }

  public static String get(String key)
  {
    // System.out.println("Setting: " + key + " " + properties.getProperty(key));
    return properties.getProperty(key);
  }

  /**
   * Throw an exception if the property value for the specified key
   * is not in the set.  We are insensitive to the case
   * @param key
   * @param validVals
   * @throws InvalidOptionException
   */
  private static void checkOneOf(String key, List<String> validVals)
                                                  throws InvalidOptionException {

    boolean found = false;
    // Case insensitive
    String val = properties.getProperty(key);
    if (val == null) {
      throw new InvalidOptionException("Could not find property " + key);
    }
    String lcaseVal = val.toLowerCase();
    for (String vv: validVals) {
      if (lcaseVal.equals(vv.toLowerCase())) {
        found = true;
        break;
      }
    }

    if (!found) {
      StringBuilder sb = new StringBuilder();
      for (String vv: validVals) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append("'");
        sb.append(vv);
        sb.append("'");
      }
      throw new InvalidOptionException("Expected property " + key + " to be one of: "
          + sb.toString() + " but was '" + val + "'");
    }
  }

  public static long getLong(String key) throws InvalidOptionException {
    String strVal = properties.getProperty(key);
    if (strVal == null) {
      throw new InvalidOptionException("no value set for option " + key);
    }
    try {
      return Long.parseLong(strVal);
    } catch (NumberFormatException e) {
      throw new InvalidOptionException("Invalid integral value for option " +
      		key + ": " + strVal);
    }
  }

  public static boolean getBoolean(String key)
                  throws InvalidOptionException

  {
    String strVal = properties.getProperty(key);
    if (strVal == null) {
      throw new InvalidOptionException("no value set for option " + key);
    }

    String lStrVal = strVal.toLowerCase();
    if (lStrVal.equals("true")) {
      return true;
    } else if (lStrVal.equals("false")) {
      return false;
    } else {
      throw new InvalidOptionException(
          "option string for " + key + " must be true or false, but was '" +
              strVal + "'");
    }
  }
}
