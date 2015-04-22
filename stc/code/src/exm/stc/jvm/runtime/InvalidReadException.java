package exm.stc.jvm.runtime;

@SuppressWarnings("serial")
public class InvalidReadException extends LogicException {
  public InvalidReadException(String msg) {
    super(msg);
  }
}
