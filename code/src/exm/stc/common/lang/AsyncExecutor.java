package exm.stc.common.lang;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Represent an asynchronous execution provider
 */
public enum AsyncExecutor {
  COASTERS,
  ;
  
  public String toString() {
    return this.name().toLowerCase();
  }
  
  /**
   * @param s
   * @return null if not valid executor
   */
  static public AsyncExecutor fromUserString(String s)
      throws IllegalArgumentException {
    return AsyncExecutor.valueOf(s.toUpperCase());
  }
  
  /**
   * 
   * @return true if this executes a command line app
   */
  public boolean isCommandLine() {
    switch (this) {
      case COASTERS:
        return true;
      default:
        throw new STCRuntimeError("Unimplemented for " + this);
    }
  }
}
