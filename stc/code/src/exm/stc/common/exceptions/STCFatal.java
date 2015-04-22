package exm.stc.common.exceptions;

/**
 * Used to signal that program should quit.
 */
public class STCFatal extends RuntimeException {
  private static final long serialVersionUID = 1L;
  public final int exitCode;

  public STCFatal(int exitCode) {
    super();
    this.exitCode = exitCode;
  }
  
}
