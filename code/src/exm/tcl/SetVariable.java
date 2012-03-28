
package exm.tcl;

public class SetVariable extends TclTree
{
  String variable;
  Expression expression;

  public SetVariable(String variable, Expression expression)
  {
    this.variable = variable;
    this.expression = expression;
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("set ");
    sb.append(variable);
    sb.append(' ');
    expression.appendTo(sb);
    sb.append('\n');
  }
}
