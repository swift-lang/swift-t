
package exm.stc.tclbackend.tree;

/**
 * Represents a simple Tcl token (e.g., word)
 * @author wozniak
 * */
public class Token extends Expression
{
  String token;

  public Token(String token)
  {
    this.token = token;
  }
  
  public Token(int target)
  {
    this.token = String.valueOf(target);
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode)
  {
    sb.append(token);
  }
}
