
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

  public Token(boolean token)
  {
    if (token == true)
      this.token = "1";
    else 
      this.token = "0";
  }
  
  @Override
  public void appendTo(StringBuilder sb, ExprContext mode)
  {
    sb.append(token);
  }
}
