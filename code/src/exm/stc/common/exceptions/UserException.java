
package exm.stc.common.exceptions;

import exm.stc.frontend.Context;

/**
 * Represents an error caused by user input
 * Thus, this should contain good error message information
 * */
public class UserException
extends Exception
{
  public UserException(Context context, String message)
  {
    this(context.getInputFile(), context.getLine(),
         context.getColumn(), message);
  }
  
  public UserException(String file, int line, int col, String message) {
    super(file + ":" + line + ":" + (col > 0 ? (col + 1) + ":" : "") + 
          " " + message);
  }
  
  public UserException(String message) {
    super(message);
  }


  private static final long serialVersionUID = 1L;
}
