package exm.stc.tclbackend.tree;

public class LiteralInt extends Expression {

  private long value;

  public LiteralInt(long value) {
    this.value = value;
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    sb.append(Long.toString(value));
  }

}
