package exm.stc.common.exceptions;

import exm.stc.frontend.Context;

public class UndefinedTypeException
extends UserException
{

  public UndefinedTypeException(Context context, String typeName)
  {
    super(context, "The following type was not defined in the current " +
        "context: " + typeName);
  }

  private static final long serialVersionUID = 1L;
}
