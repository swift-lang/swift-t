
package exm.stc.swigcbackend.tree;

public class Eval extends Sequence
{
  public Eval()
  {
    super();
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("::eval {\n");
    increaseIndent();
    super.appendTo(sb);
    decreaseIndent();
    indent(sb);
    sb.append("}\n");
  }
}
