
package exm.parser.util;

import exm.ast.Context;

public class DoubleDefineException
extends UserException
{
  public DoubleDefineException(Context context, String msg)
  {
    super(context, msg);
  }

  private static final long serialVersionUID = 1L;
}
