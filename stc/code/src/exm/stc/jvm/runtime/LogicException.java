package exm.stc.jvm.runtime;

/**
 * Exception caused by logic error in program
 * @author tim armstrong
 *
 */
@SuppressWarnings("serial")
public class LogicException extends Exception {
  public LogicException(String msg) {
    super(msg);
  }
}
