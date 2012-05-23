package exm.stc.frontend;

import java.util.HashMap;

public class FunctionContext {

  private final String functionName;
  private final HashMap<String, Integer> counters;

  public FunctionContext(String functionName) {
    this.functionName = functionName;
    this.counters = new HashMap<String, Integer>();
  }

  public String getFunctionName() {
    return functionName;
  }

  /**
   * For any given string key, return integers
   * in a sequence starting from 0
   * @param counterName
   * @return
   */
  public int getCounterVal(String counterName) {
    Integer result = counters.get(counterName);
    if (result != null) {
      counters.put(counterName, result+1);
      return result;
    } else {
      counters.put(counterName, 1);
      return 0;
    }
  }

  /**
   * A way to automatically generate unique names.
   * returns something like:
   * <function name>-<construct type>-<unique number>
   * @param constructType
   * @return
   */
  public String constructName(String constructType) {
    return this.getFunctionName() + "-" + constructType + 
                  getCounterVal(constructType);
  }

}
