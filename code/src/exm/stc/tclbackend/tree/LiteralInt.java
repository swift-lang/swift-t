package exm.stc.tclbackend.tree;

public class LiteralInt extends Expression {

  public static final Expression TRUE = new LiteralInt(1);
  public static final Expression FALSE = new LiteralInt(0);
  
  private long value;

  public LiteralInt(long value) {
    this.value = value;
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    sb.append(Long.toString(value));
  }

}
