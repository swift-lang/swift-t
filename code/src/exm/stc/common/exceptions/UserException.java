
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
    this(context.getInputFile(), context.getLine(), message);
  }
  
  public UserException(String file, int line, String message) {
    super(file + " l." + line + ": " + message);
  }
  
  public UserException(String message) {
    super(message);
  }


  private static final long serialVersionUID = 1L;
}
