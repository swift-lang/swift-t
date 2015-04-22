package exm.stc.common.lang;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import exm.stc.common.Logging;

/**
 * Store bindings for argv that are specified at compile time
 */
public class CompileTimeArgs {
  /** Store in sorted order as nicety */
  private static final Map<String, String> compileTimeArgs =
                                    new TreeMap<String, String>();
  
  public static void addCompileTimeArg(String key, String value) {
    String prev = compileTimeArgs.put(key, value);
    if (prev != null) {
      Logging.getSTCLogger().warn("Overwriting old value of \"" + key + "\"."
          + " Replaced \"" + prev + "\" with \"" + value + "\"");
    }
  }
  
  public static String lookup(String key) {
    return compileTimeArgs.get(key);
  }
  
  public static Map<String, String> getCompileTimeArgs() {
    return Collections.unmodifiableMap(compileTimeArgs);
  }
}
