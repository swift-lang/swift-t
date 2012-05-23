
package exm.stc.tclbackend.tree;

/**
 * Simply deference variable as $variable
 * @author wozniak
 * */
public class Value extends Expression
{
  String variable;

  public Value(String variable)
  {
    this.variable = variable;
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode)
  {
    // enclose in {} to allow a wider range of characters to be used in 
    // var names, such as :
    sb.append("${");
    sb.append(variable);
    sb.append("}");
  }
}
