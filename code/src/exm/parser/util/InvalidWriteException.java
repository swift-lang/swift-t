package exm.parser.util;

import exm.ast.Context;

/**
 * Used when an attempt is made to write to a constant variable,
 * or a double-write is detected
 */
public class InvalidWriteException extends UserException {

  private static final long serialVersionUID = 1L;

  public InvalidWriteException(String message) {
    super(message);
  }
  public InvalidWriteException(Context context, String message) {
    super(context, message);
  }

}
