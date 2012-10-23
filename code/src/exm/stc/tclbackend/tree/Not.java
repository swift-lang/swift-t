package exm.stc.tclbackend.tree;

public class Not extends Expression {

  private Expression expr;

  public Not(Expression expr) {
    this.expr = expr;
  }
  
  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    sb.append("! ");
    expr.appendTo(sb, mode);
  }

}
