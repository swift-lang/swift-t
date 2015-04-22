package exm.stc.jvm.runtime;

@SuppressWarnings("serial")
public class DoubleWriteException extends LogicException {
  public DoubleWriteException(String stringmsg) {
    super(stringmsg);
  }
}
