package exm.stc.common.exceptions;

import exm.stc.frontend.Context;

public class InvalidAnnotationException extends UserException {

  private static final long serialVersionUID = 1;

  public InvalidAnnotationException(Context context, String message) {
    super(context, message);
  }

}
