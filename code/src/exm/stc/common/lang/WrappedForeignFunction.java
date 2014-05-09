package exm.stc.common.lang;

/**
 * Information about implementation of foreign function.
 * 
 * This type of foreign function uses the same calling convention as
 * Swift functions - i.e. futures, etc.
 * 
 * Different implementations will be supported by different backends.
 */
public abstract class WrappedForeignFunction {

  /**
   * Human-readable representation for intermediate representation.
   */
  @Override
  public abstract String toString();

}