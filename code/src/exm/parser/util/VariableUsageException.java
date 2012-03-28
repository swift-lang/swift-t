package exm.parser.util;

import exm.ast.Context;

public class VariableUsageException extends UserException {

  private static final long serialVersionUID = 1L;

  public VariableUsageException(String file, int line, String message) {
    super(file, line, message);
  }

  public VariableUsageException(Context context, String message) {
    super(context, message);
  }

  public VariableUsageException(String message) {
    super(message);
  }

}
