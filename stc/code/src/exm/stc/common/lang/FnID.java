package exm.stc.common.lang;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Unique identifier for a function.
 *
 * @author tim
 */
public class FnID {

  public static final FnID MAIN_FUNCTION =
      new FnID(Constants.MAIN_FUNCTION, Constants.MAIN_FUNCTION);
  public static final FnID ENTRY_FUNCTION =
      new FnID(Constants.ENTRY_FUNCTION, Constants.ENTRY_FUNCTION);

  /** Unique string */
  private final String uniqueName;

  /** Original name in code */
  private final String originalName;

  public FnID(String uniqueName, String originalName) {
    this.uniqueName = uniqueName;
    this.originalName = originalName;
  }

  public String uniqueName() {
    return uniqueName;
  }

  public String originalName() {
    return originalName;
  }

  @Override
  public String toString() {
    return uniqueName;
  }

  @Override
  public int hashCode() {
    return uniqueName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    // TODO Auto-generated method stub
    if (!(obj instanceof FnID)) {
      throw new STCRuntimeError("Invalid comparison");
    }
    return uniqueName.equals(((FnID)obj).uniqueName);
  }

}
