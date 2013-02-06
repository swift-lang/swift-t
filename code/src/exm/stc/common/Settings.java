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
  public static final String OPT_DEAD_CODE_ELIM = "stc.opt.dead-code-elim";
  public static final String OPT_FORWARD_DATAFLOW = "stc.opt.forward-dataflow";
  public static final String OPT_EXPAND_DATAFLOW_OPS = "stc.opt.expand-dataflow-ops";
  public static final String OPT_WAIT_COALESCE = "stc.opt.wait-coalesce";
  public static final String OPT_PIPELINE = "stc.opt.pipeline";
  public static final String OPT_CONTROLFLOW_FUSION =
                                            "stc.opt.controlflow-fusion";
  public static final String OPT_FUNCTION_INLINE = "stc.opt.function-inline";
  public static final String OPT_FUNCTION_INLINE_THRESHOLD =
                              "stc.opt.function-inline-threshold";
  public static final String OPT_DISABLE_ASSERTS = "stc.opt.disable-asserts";
  public static final String OPT_UNROLL_LOOPS = "stc.opt.unroll-loops";
  public static final String OPT_HOIST = "stc.opt.hoist";
  public static final String OPT_ELIM_REFCOUNTS = "stc.opt.elim-refcounts";
  public static final String OPT_MAX_ITERATIONS = "stc.opt.max-iterations";
  public static final String TURBINE_NO_STACK = "stc.codegen.no-stack";
  public static final String TURBINE_NO_STACK_VARS = "stc.codegen.no-stack-vars";

  public static final String EXPERIMENTAL_REFCOUNTING = "stc.exp.refcounting";
  
  public static final String AUTO_DECLARE = "stc.auto-declare";
  
  public static final String INPUT_FILENAME = "stc.input_filename";
  public static final String OUTPUT_FILENAME = "stc.output_filename";
  public static final String STC_HOME = "stc.stc_home";
  public static final String STC_VERSION = "stc.version";
  public static final String TURBINE_HOME = "stc.turbine_home";
  
  public static final String LOG_FILE = "stc.log.file";
  public static final String LOG_TRACE = "stc.log.trace";

  private static final Properties properties;
  static {
    Properties defaults = new Properties();
    // Set defaults here
    defaults.setProperty(TURBINE_VERSION, "0.0.5");
    defaults.setProperty(DEBUG_LEVEL, "COMMENTS");
    defaults.setProperty(IC_OUTPUT_FILE, "");
    defaults.setProperty(RPATH, "");
    defaults.setProperty(INPUT_FILENAME, "");
    defaults.setProperty(OUTPUT_FILENAME, "");
    defaults.setProperty(STC_HOME, "");
    defaults.setProperty(TURBINE_HOME, "");
    
    // Code optimisation settings - defaults
    defaults.setProperty(OPT_FLATTEN_NESTED, "true");
    defaults.setProperty(OPT_CONSTANT_FOLD, "true");
    defaults.setProperty(OPT_SHARED_CONSTANTS, "true");
    defaults.setProperty(OPT_DEAD_CODE_ELIM, "true");
    defaults.setProperty(OPT_UNROLL_LOOPS, "true");
    defaults.setProperty(OPT_DISABLE_ASSERTS, "false");
    defaults.setProperty(OPT_FORWARD_DATAFLOW, "true");
    defaults.setProperty(OPT_EXPAND_DATAFLOW_OPS, "true");
    defaults.setProperty(OPT_WAIT_COALESCE, "true");
    defaults.setProperty(OPT_PIPELINE, "false");
    defaults.setProperty(OPT_CONTROLFLOW_FUSION, "true");
    defaults.setProperty(OPT_FUNCTION_INLINE, "false");
    defaults.setProperty(OPT_FUNCTION_INLINE_THRESHOLD, "500");
    defaults.setProperty(OPT_HOIST, "true");
    defaults.setProperty(OPT_ELIM_REFCOUNTS, "true");
    defaults.setProperty(OPT_MAX_ITERATIONS, "10");
    defaults.setProperty(EXPERIMENTAL_REFCOUNTING, "false");
    defaults.setProperty(AUTO_DECLARE, "false");
    defaults.setProperty(LOG_FILE, "");
    defaults.setProperty(LOG_TRACE, "false");

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
    getBoolean(OPT_FLATTEN_NESTED);
    getBoolean(OPT_CONSTANT_FOLD);
    getBoolean(OPT_DEAD_CODE_ELIM);
    getBoolean(OPT_DISABLE_ASSERTS);
    getBoolean(OPT_FORWARD_DATAFLOW);
    getBoolean(OPT_EXPAND_DATAFLOW_OPS);
    getBoolean(OPT_WAIT_COALESCE);
    getBoolean(OPT_PIPELINE);
    getBoolean(OPT_CONTROLFLOW_FUSION);
    getBoolean(OPT_FUNCTION_INLINE);
    getBoolean(OPT_ELIM_REFCOUNTS);
    getBoolean(OPT_HOIST);
    getLong(OPT_FUNCTION_INLINE_THRESHOLD);
    getBoolean(OPT_UNROLL_LOOPS);
    getBoolean(TURBINE_NO_STACK);
    getBoolean(TURBINE_NO_STACK_VARS);
    getBoolean(EXPERIMENTAL_REFCOUNTING);
    getBoolean(AUTO_DECLARE);

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
