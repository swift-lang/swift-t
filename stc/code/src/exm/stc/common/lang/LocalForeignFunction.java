package exm.stc.common.lang;

/**
 * Information about implementation of foreign function.
 * Different implementations will be supported by different backends.
 */
public abstract class LocalForeignFunction {

  /**
   * Human-readable representation for intermediate representation.
   */
  @Override
  public abstract String toString();

}
