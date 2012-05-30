
package exm.stc.common.exceptions;

/**
 * This represents a parser internal error.
 * These always indicate a parser bug (or missing feature).
 * @author wozniak
 * */
public class STCRuntimeError extends RuntimeException
{
  public STCRuntimeError(String msg)
  {
    super(msg);
  }

  private static final long serialVersionUID = 1L;
}
