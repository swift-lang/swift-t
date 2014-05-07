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

package exm.stc.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.util.Pair;

/**
 * General STC settings
 * @author wozniak
 * 
 * List of Java properties not processed here: 
 * stc.logfile: used to set up logging in Main 
 * */
public class Settings
{
  
  // Whether we're using no engine
  // TODO: remove eventually
  public static boolean NO_TURBINE_ENGINE = false;
  public static final String NO_TURBINE_ENGINE_KEY = "stc.turbine.no-engine";
  
  public static final String TURBINE_VERSION = "stc.turbine.version";
  public static final String DEBUG_LEVEL = "stc.debugging";

  public static final String RPATH = "stc.rpath";
  
  public static final String IC_OUTPUT_FILE = "stc.ic.output-file";
  public static final String OPT_CONSTANT_FOLD = "stc.opt.constant-fold";
  public static final String OPT_SHARED_CONSTANTS = "stc.opt.shared-constants";
  public static final String OPT_FLATTEN_NESTED = "stc.opt.flatten-nested";
  public static final String OPT_DEAD_CODE_ELIM = "stc.opt.dead-code-elim";
  public static final String OPT_VALUE_NUMBER = "stc.opt.value-number";
  public static final String OPT_FINALIZED_VAR = "stc.opt.finalized-var";
  public static final String OPT_ALGEBRA = "stc.opt.algebra";
  public static final String OPT_DATAFLOW_OP_INLINE = "stc.opt.dataflow-op-inline";
  public static final String OPT_WAIT_COALESCE = "stc.opt.wait-coalesce";
  public static final String OPT_PIPELINE = "stc.opt.pipeline";
  public static final String OPT_CONTROLFLOW_FUSION =
                                            "stc.opt.controlflow-fusion";
  public static final String OPT_FUNCTION_INLINE = "stc.opt.function-inline";
  public static final String OPT_FUNCTION_INLINE_THRESHOLD =
                              "stc.opt.function-inline-threshold";
  public static final String OPT_FUNCTION_SIGNATURE = 
                              "stc.opt.function-signature";
  public static final String OPT_DISABLE_ASSERTS = "stc.opt.disable-asserts";
  /* Master switch for loop unrolling pass.  At minimum manually 
   * annotated loops are unrolled */
  public static final String OPT_UNROLL_LOOPS = "stc.opt.unroll-loops";
  /* Expand short loops with known bounds */
  public static final String OPT_EXPAND_LOOPS = "stc.opt.expand-loops";
  /* Unroll any loops */
  public static final String OPT_FULL_UNROLL = "stc.opt.full-unroll";
  // Threshold iters for fully unrolling loop with fixed bounds
  public static final String OPT_EXPAND_LOOP_THRESHOLD_ITERS =
                            "stc.opt.expand-loop-threshold-iters";
  // Max number of iters to unroll by default
  public static final String OPT_UNROLL_LOOP_THRESHOLD_ITERS =
                            "stc.opt.unroll-loop-threshold-iters";
  // Threshold extra instructions for fully unrolling loop with fixed bounds
  public static final String OPT_EXPAND_LOOP_THRESHOLD_INSTS =
                            "stc.opt.expand-loop-threshold-insts";
  // Threshold extra instructions for unrolling loop
  public static final String OPT_UNROLL_LOOP_THRESHOLD_INSTS =
                            "stc.opt.unroll-loop-threshold-insts";
  public static final String OPT_HOIST = "stc.opt.hoist";
  public static final String OPT_REORDER_INSTS = "stc.opt.reorder-insts";
  public static final String OPT_ARRAY_BUILD = "stc.opt.array-build";
  public static final String OPT_LOOP_SIMPLIFY = "stc.opt.loop-simplify";
  public static final String OPT_PROPAGATE_ALIASES = "stc.opt.propagate-aliases";
  
  public static final String OPT_MERGE_REFCOUNTS = "stc.opt.merge-refcounts";
  public static final String OPT_CANCEL_REFCOUNTS = "stc.opt.cancel-refcounts";
  public static final String OPT_PIGGYBACK_REFCOUNTS = "stc.opt.piggyback-refcounts";
  public static final String OPT_BATCH_REFCOUNTS = "stc.opt.batch-refcounts";
  public static final String OPT_HOIST_REFCOUNTS = "stc.opt.hoist-refcounts";
  
  public static final String OPT_MAX_ITERATIONS = "stc.opt.max-iterations";
  public static final String TURBINE_NO_STACK = "stc.codegen.no-stack";
  public static final String TURBINE_NO_STACK_VARS = "stc.codegen.no-stack-vars";

  public static final String ENABLE_REFCOUNTING = "stc.refcounting";
  public static final String ENABLE_CHECKPOINTING = "stc.checkpointing";
  
  public static final String AUTO_DECLARE = "stc.auto-declare";
  
  public static final String INPUT_FILENAME = "stc.input_filename";
  public static final String OUTPUT_FILENAME = "stc.output_filename";
  public static final String STC_HOME = "stc.stc_home";
  public static final String STC_VERSION = "stc.version";
  public static final String TURBINE_HOME = "stc.turbine_home";
  
  public static final String LOG_FILE = "stc.log.file";
  public static final String LOG_TRACE = "stc.log.trace";
  public static final String COMPILER_DEBUG = "stc.compiler-debug";
  
  /** Run compiler repeatedly so can be profiled */
  public static final String PROFILE_STC = "stc.profile";

  public static final String USE_C_PREPROCESSOR = "stc.c_preprocess";
  public static final String PREPROCESS_ONLY = "stc.preprocess_only";
  public static final String PREPROCESSOR_FORCE_GCC = "stc.preproc.force-gcc";
  public static final String PREPROCESSOR_FORCE_CPP = "stc.preproc.force-cpp";
  

  /** Record assumption that we need to pass waited-on vars into block */
  public static final String MUST_PASS_WAIT_VARS = "stc.must_pass_wait_vars";
  
  private static final Properties properties;
  
  private static final List<String> modulePath = new ArrayList<String>();
  
  /** Additional metadata */
  private static final List<Pair<String, String>> metadata = 
            new ArrayList<Pair<String, String>>();
  
  static {
    Properties defaults = new Properties();
    // Set defaults here
    defaults.setProperty(TURBINE_VERSION, "0.0.5");
    defaults.setProperty(DEBUG_LEVEL, "COMMENTS");
    defaults.setProperty(IC_OUTPUT_FILE, "");
    defaults.setProperty(NO_TURBINE_ENGINE_KEY, "true");
    defaults.setProperty(RPATH, "");
    defaults.setProperty(INPUT_FILENAME, "");
    defaults.setProperty(OUTPUT_FILENAME, "");
    defaults.setProperty(STC_HOME, "");
    defaults.setProperty(TURBINE_HOME, "");
    defaults.setProperty(COMPILER_DEBUG, "true");
    defaults.setProperty(USE_C_PREPROCESSOR, "false");
    defaults.setProperty(PREPROCESS_ONLY, "false");
    defaults.setProperty(PREPROCESSOR_FORCE_CPP, "false");
    defaults.setProperty(PREPROCESSOR_FORCE_GCC, "false");
    
    // Code optimisation settings - defaults
    defaults.setProperty(OPT_FLATTEN_NESTED, "true");
    defaults.setProperty(OPT_CONSTANT_FOLD, "true");
    defaults.setProperty(OPT_SHARED_CONSTANTS, "true");
    defaults.setProperty(OPT_DEAD_CODE_ELIM, "true");
    defaults.setProperty(OPT_UNROLL_LOOPS, "true");
    defaults.setProperty(OPT_EXPAND_LOOPS, "true");
    defaults.setProperty(OPT_FULL_UNROLL, "false");
    defaults.setProperty(OPT_EXPAND_LOOP_THRESHOLD_ITERS, "16");
    defaults.setProperty(OPT_UNROLL_LOOP_THRESHOLD_ITERS, "8");
    defaults.setProperty(OPT_EXPAND_LOOP_THRESHOLD_INSTS, "256");
    defaults.setProperty(OPT_UNROLL_LOOP_THRESHOLD_INSTS, "192");
    defaults.setProperty(OPT_DISABLE_ASSERTS, "false");
    defaults.setProperty(OPT_VALUE_NUMBER, "true");
    defaults.setProperty(OPT_FINALIZED_VAR, "true");
    defaults.setProperty(OPT_ALGEBRA, "true");
    defaults.setProperty(OPT_DATAFLOW_OP_INLINE, "true");
    defaults.setProperty(OPT_WAIT_COALESCE, "true");
    defaults.setProperty(OPT_PIPELINE, "false");
    defaults.setProperty(OPT_CONTROLFLOW_FUSION, "true");
    defaults.setProperty(OPT_FUNCTION_INLINE, "false");
    defaults.setProperty(OPT_FUNCTION_SIGNATURE, "true");
    defaults.setProperty(OPT_FUNCTION_INLINE_THRESHOLD, "500");
    defaults.setProperty(OPT_HOIST, "true");
    defaults.setProperty(OPT_REORDER_INSTS, "false");
    defaults.setProperty(OPT_ARRAY_BUILD, "true");
    defaults.setProperty(OPT_LOOP_SIMPLIFY, "true");
    defaults.setProperty(OPT_PROPAGATE_ALIASES, "true");
    defaults.setProperty(OPT_MERGE_REFCOUNTS, "true");
    defaults.setProperty(OPT_CANCEL_REFCOUNTS, "true");
    defaults.setProperty(OPT_PIGGYBACK_REFCOUNTS, "true");
    defaults.setProperty(OPT_BATCH_REFCOUNTS, "true");
    defaults.setProperty(OPT_HOIST_REFCOUNTS, "true");
    defaults.setProperty(OPT_MAX_ITERATIONS, "10");
    defaults.setProperty(ENABLE_REFCOUNTING, "true");
    defaults.setProperty(ENABLE_CHECKPOINTING, "true");
    defaults.setProperty(AUTO_DECLARE, "true");
    defaults.setProperty(PROFILE_STC, "false");
    defaults.setProperty(LOG_FILE, "");
    defaults.setProperty(LOG_TRACE, "false");

    

    // True for all current targets
    defaults.setProperty(MUST_PASS_WAIT_VARS, "true");
    
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
     Try to overwrite each default property in properties
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
    loadVersionNumber();
    initModulePath();
  }

  public static void set(String key, String value) {
    properties.setProperty(key, value);
  }

  private static void initModulePath() {
    // Search Turbine directory first
    modulePath.add(0, Settings.get(Settings.TURBINE_HOME) + "/export");
    // Search current directory last
    modulePath.add(".");
  }
  
  public static void addModulePath(String dir) {
    modulePath.add(dir);
  }

  /**
   * @return list of directory paths to search, from first to last
   */
  public static List<String> getModulePath() {
    return Collections.unmodifiableList(modulePath);
  }
  
  public static void addMetadata(String key, String val) {
    metadata.add(Pair.create(key, val));
  }

  /**
   * @return list of directory paths to search, from first to last
   */
  public static List<Pair<String, String>> getMetadata() {
    return Collections.unmodifiableList(metadata);
  }

  private static void loadVersionNumber() {
    String homeDir = get(STC_HOME);
    File versionFile = new File(homeDir + File.separator + 
                                "etc" + File.separator + 
                                "version.txt");
    try {
      BufferedReader r = new BufferedReader(new FileReader(versionFile));
      String version = r.readLine().trim();
      r.close();
      properties.setProperty(STC_VERSION, version);
    } catch (FileNotFoundException e) {
      throw new STCRuntimeError("Version file missing: " + versionFile);
    } catch (IOException e) {
      e.printStackTrace();
      throw new STCRuntimeError("IOException while reading version file: "
                              + versionFile);
    }
  }

  public static List<String> getKeys() {
    ArrayList<String> keys;
    keys = new ArrayList<String>(properties.stringPropertyNames());
    Collections.sort(keys);
    return keys;
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
    NO_TURBINE_ENGINE = getBoolean(NO_TURBINE_ENGINE_KEY);
    getBoolean(OPT_FLATTEN_NESTED);
    getBoolean(OPT_CONSTANT_FOLD);
    getBoolean(OPT_DEAD_CODE_ELIM);
    getBoolean(OPT_DISABLE_ASSERTS);
    getBoolean(OPT_VALUE_NUMBER);
    getBoolean(OPT_FINALIZED_VAR);
    getBoolean(OPT_ALGEBRA);
    getBoolean(OPT_DATAFLOW_OP_INLINE);
    getBoolean(OPT_WAIT_COALESCE);
    getBoolean(OPT_PIPELINE);
    getBoolean(OPT_CONTROLFLOW_FUSION);
    getBoolean(OPT_FUNCTION_INLINE);
    getBoolean(OPT_FUNCTION_SIGNATURE);
    getBoolean(OPT_HOIST);
    getBoolean(OPT_REORDER_INSTS);
    getLong(OPT_FUNCTION_INLINE_THRESHOLD);
    getBoolean(OPT_UNROLL_LOOPS);
    getBoolean(OPT_EXPAND_LOOPS);
    getBoolean(OPT_FULL_UNROLL);
    getBoolean(OPT_ARRAY_BUILD);
    getBoolean(OPT_LOOP_SIMPLIFY);
    getBoolean(OPT_PROPAGATE_ALIASES);
    getLong(OPT_EXPAND_LOOP_THRESHOLD_ITERS);
    getLong(OPT_UNROLL_LOOP_THRESHOLD_ITERS);
    getLong(OPT_EXPAND_LOOP_THRESHOLD_INSTS);
    getLong(OPT_UNROLL_LOOP_THRESHOLD_INSTS);
    getBoolean(OPT_MERGE_REFCOUNTS);
    getBoolean(OPT_CANCEL_REFCOUNTS);
    getBoolean(OPT_PIGGYBACK_REFCOUNTS);
    getBoolean(OPT_BATCH_REFCOUNTS);
    getBoolean(OPT_HOIST_REFCOUNTS);
    getBoolean(TURBINE_NO_STACK);
    getBoolean(TURBINE_NO_STACK_VARS);
    getBoolean(ENABLE_REFCOUNTING);
    getBoolean(ENABLE_CHECKPOINTING);
    getBoolean(AUTO_DECLARE);
    getBoolean(COMPILER_DEBUG);
    getBoolean(PROFILE_STC);
    getBoolean(USE_C_PREPROCESSOR);
    getBoolean(PREPROCESS_ONLY);
    getBoolean(PREPROCESSOR_FORCE_CPP);
    getBoolean(PREPROCESSOR_FORCE_GCC);
    getBoolean(MUST_PASS_WAIT_VARS);

    getLong(OPT_MAX_ITERATIONS);

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
  
  public static int getInt(String key) throws InvalidOptionException {
    String strVal = properties.getProperty(key);
    if (strVal == null) {
      throw new InvalidOptionException("no value set for option " + key);
    }
    try {
      return Integer.parseInt(strVal);
    } catch (NumberFormatException e) {
      throw new InvalidOptionException("Invalid integral value for option " +
      key + ": " + strVal);
    }
  }

  public static boolean getBoolean(String key)
                  throws InvalidOptionException {
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
