
package exm.tcl;

/**
 * Arbitrary text that contains Tcl code
 * */
public class Text extends TclTree
{
  String text;

  public Text(String text)
  {
    this.text = text;
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append(text);
    sb.append('\n');
  }
}
