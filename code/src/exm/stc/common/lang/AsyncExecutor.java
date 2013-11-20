package exm.stc.common.lang;

/**
 * Represent an asynchronous execution provider
 */
public enum AsyncExecutor {
  COASTERS,
  ;
  
  public String toString() {
    return this.name().toLowerCase();
  }
}
