
package exm.stc.tclbackend.tree;

/**
 * Foreach construct
 * 
 * @author armstrong
 * */
public class DictFor extends Sequence
{
  Expression list;
  private Token loopValVar;
  private Token loopKeyVar;
  private TclTree loopBody;

  public DictFor(Token loopKeyVar, Token loopValVar, 
            Expression list, TclTree loopBody) {
    this.loopKeyVar = loopKeyVar;
    this.loopValVar = loopValVar;
    this.list = list;
    this.loopBody = loopBody;
    members.add(loopBody);
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("dict for {");
    loopKeyVar.appendTo(sb);
    sb.append(" ");
    loopValVar.appendTo(sb);
    sb.append("} ");
    list.appendTo(sb);
    sb.append(" ");
    loopBody.setIndentation(this.indentation);
    loopBody.appendToAsBlock(sb);
    sb.append("\n");
  }
  

}
