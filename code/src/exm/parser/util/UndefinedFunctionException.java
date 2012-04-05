package exm.parser.util;

import exm.ast.Context;

public class UndefinedFunctionException
extends UserException
{
  public UndefinedFunctionException(Context context, String msg)
  {
    super(context, msg);
  }
  
  public static UndefinedFunctionException unknownFunction(Context context, 
                                                        String fnname) {
    return new UndefinedFunctionException(context, "undefined function: " + fnname);
  }

  private static final long serialVersionUID = 1L;
}
