
package exm.stc.swigcbackend.tree;

/**
 * Tcl single-line comment
 * */
public class Comment extends TclTree
{
  String text;

  public Comment(String text)
  {
    this.text = text;
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("# ");
    sb.append(text);
    sb.append('\n');
  }
}
