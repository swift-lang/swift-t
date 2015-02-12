package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.common.lang.Types.FunctionType;

/**
 * Represent default arguments of function, if any
 */
public class DefaultVals<T> {
  /**
   * Index of first default arg, -1 if no default args
   */
  private final int firstDefault;
  private final List<T> defaultVals;

  private DefaultVals(int firstDefault, List<T> defaultVals) {
    this.firstDefault = firstDefault;
    this.defaultVals = new ArrayList<T>(defaultVals);
  }

  public static <T> DefaultVals<T> fromDefaultValVector(List<T> defaultVals) {
    int firstDefault = -1; // No defaults
    for (int i = 0; i < defaultVals.size(); i++) {
      boolean hasDefault = (defaultVals.get(i) != null);

      // Check we don't have gaps
      assert(firstDefault == -1 || hasDefault);
      if (hasDefault && firstDefault == -1) {
        firstDefault = i;
      }
    }

    return new DefaultVals<T>(firstDefault, defaultVals);
  }

  public static <T> DefaultVals<T> noDefaults(FunctionType type) {
    List<T> defaultVals = new ArrayList<T>();
    for (int i = 0; i < type.getInputs().size(); i++) {
      defaultVals.add(null);
    }
    return fromDefaultValVector(defaultVals);
  }

  public List<T> defaultVals() {
    return Collections.unmodifiableList(defaultVals);
  }

  public int firstDefault() {
    return firstDefault;
  }

  public boolean hasAnyDefaults() {
    return firstDefault != -1;
  }

  /**
   * @return only the existing defaults, starting at firstDefault
   */
  public List<T> trailingDefaults() {
    return trailingDefaults(firstDefault);
  }

  /**
   * @param min an argument position after firstDefault
   * @return
   */
  public List<T> trailingDefaults(int start) {
    assert(start >= firstDefault);
    if (!hasAnyDefaults()) {
      return Collections.emptyList();
    }

    return defaultVals.subList(start, defaultVals().size());
  }


  @Override
  public String toString() {
    return "DefaultVals: " + defaultVals + " " + firstDefault;
  }

}
