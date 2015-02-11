package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.common.lang.Types.FunctionType;

/**
 * Represent default arguments of function, if any
 */
public class DefaultVals {
  /**
   * Index of first default arg, -1 if no default args
   */
  private final int firstDefault;
  private final List<Arg> defaultVals;

  private DefaultVals(int firstDefault, List<Arg> defaultVals) {
    this.firstDefault = firstDefault;
    this.defaultVals = new ArrayList<Arg>(defaultVals);
  }

  public static DefaultVals fromDefaultValVector(List<Arg> defaultVals) {
    int firstDefault = -1; // No defaults
    for (int i = 0; i < defaultVals.size(); i++) {
      boolean hasDefault = (defaultVals.get(i) != null);

      // Check we don't have gaps
      assert(firstDefault == -1 || hasDefault);
      if (hasDefault && firstDefault == -1) {
        firstDefault = i;
      }
    }

    return new DefaultVals(firstDefault, defaultVals);
  }

  public static DefaultVals noDefaults(FunctionType type) {
    List<Arg> defaultVals = new ArrayList<Arg>();
    for (int i = 0; i < type.getInputs().size(); i++) {
      defaultVals.add(null);
    }
    return fromDefaultValVector(defaultVals);
  }

  public List<Arg> defaultVals() {
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
  public List<Arg> trailingDefaults() {
    if (!hasAnyDefaults()) {
      return Collections.emptyList();
    }

    return defaultVals.subList(firstDefault, defaultVals().size());
  }


  @Override
  public String toString() {
    return "DefaultVals: " + defaultVals + " " + firstDefault;
  }
}
