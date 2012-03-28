
package exm.parser.util;

import exm.ast.Context;

public class UndefinedVariableException
extends UserException
{
  public UndefinedVariableException(Context context, String msg)
  {
    super(context, msg);
  }

  private static final long serialVersionUID = 1L;
}
