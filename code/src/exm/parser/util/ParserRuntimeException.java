
package exm.parser.util;

/**
 * This represents a parser internal error.
 * These always indicate a parser bug.
 * @author wozniak
 * */
public class ParserRuntimeException extends RuntimeException
{
  public ParserRuntimeException(String msg)
  {
    super(msg);
  }

  private static final long serialVersionUID = 1L;
}
