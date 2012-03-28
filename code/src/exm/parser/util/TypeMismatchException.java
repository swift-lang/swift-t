package exm.parser.util;

import exm.ast.Context;

public class TypeMismatchException
extends UserException
{
  public TypeMismatchException(Context context, String message)
  {
    super(context, message);
  }

  private static final long serialVersionUID = 1L;
}
