package exm.stc.common.lang;

/**
 * This class should be inherited from for different backends.
 * 
 * Inheriting classes should implement equals and hashCode.
 */
public abstract class RequiredPackage {

  /**
   * Human-readable representation for intermediate representation.
   */
  @Override
  public abstract String toString();
  
  @Override  
  public abstract boolean equals(Object other);
  
  @Override
  public abstract int hashCode();
}