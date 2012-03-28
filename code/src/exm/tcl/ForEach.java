
package exm.tcl;

/**
 * Foreach construct
 * 
 * @author armstrong
 * */
public class ForEach extends Sequence
{
  Expression list;
  private Token loopVar;
  private TclTree loopBody;

  public ForEach(Token loopVar, Expression list, TclTree loopBody)
  {
    this.loopVar = loopVar;
    this.list = list;
    this.loopBody = loopBody;
    members.add(loopBody);
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("foreach ");
    loopVar.appendTo(sb);
    sb.append(" ");
    list.appendTo(sb);
    sb.append(" ");
    loopBody.setIndentation(this.indentation);
    loopBody.appendToAsBlock(sb);
    sb.append("\n");
  }
  

}
