
package exm.stc.swigcbackend.tree;

import exm.stc.common.util.StringUtil;

/**
 * The TclTree class hierarchy represents all Tcl constructs
 * necessary for Turbine code generation
 *
 * TclTree is the most abstract Tcl expression
 *
 * @author wozniak
 * */
public abstract class TclTree
{
  int indentation = 0;
  static int indentWidth = 4;

  public abstract void appendTo(StringBuilder sb);

  /**
   * Append the sequence to the StringBuilder inside
   * curly braces.
   * @param sb
   */
  public void appendToAsBlock(StringBuilder sb) {
    sb.append("{\n");
    increaseIndent();
    appendTo(sb);
    decreaseIndent();
    indent(sb);
    sb.append("}");
  }

  public void indent(StringBuilder sb)
  {
    StringUtil.spaces(sb, indentation);
  }

  public void setIndentation(int i)
  {
    indentation = i;
  }

  public void increaseIndent()
  {
    indentation += indentWidth;
  }

  public void decreaseIndent()
  {
    indentation -= indentWidth;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder(2048);
    appendTo(sb);
    return sb.toString();
  }
}
