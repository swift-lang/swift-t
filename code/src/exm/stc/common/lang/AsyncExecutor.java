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
  
  /**
   * @param s
   * @return null if not valid executor
   */
  static public AsyncExecutor fromUserString(String s)
      throws IllegalArgumentException {
    return AsyncExecutor.valueOf(s.toUpperCase());
  }
}
