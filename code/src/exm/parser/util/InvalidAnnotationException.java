package exm.parser.util;

import exm.ast.Context;

public class InvalidAnnotationException extends UserException {

  private static final long serialVersionUID = 1;

  public InvalidAnnotationException(Context context, String message) {
    super(context, message);
  }

}
