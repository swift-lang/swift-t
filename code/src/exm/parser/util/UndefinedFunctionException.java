package exm.parser.util;

import exm.ast.Context;

public class UndefinedFunctionException
extends UserException
{
  public UndefinedFunctionException(Context context, String message)
  {
    super(context, message);
  }

  private static final long serialVersionUID = 1L;
}
