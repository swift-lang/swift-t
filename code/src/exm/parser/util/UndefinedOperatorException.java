package exm.parser.util;

import exm.ast.Context;

public class UndefinedOperatorException
extends UserException
{
  public UndefinedOperatorException(Context context, String message)
  {
    super(context, message);
  }

  private static final long serialVersionUID = 1L;
}
