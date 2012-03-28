
package exm.tcl;

import java.util.*;

public class Expression extends TclTree
{
  List<Expression> items = null;

  public Expression()
  {
    this.items = new ArrayList<Expression>();
  }

  public Expression(Expression... tokens)
  {
    this.items = Arrays.asList(tokens);
  }

  public Expression(String... strings)
  {
    items = new ArrayList<Expression>(strings.length);
    for (String s : strings)
      items.add(new Token(s));
  }

  public void add(Expression item)
  {
    items.add(item);
  }

  @Override
  public void appendTo(StringBuilder sb) {
    appendTo(sb, ExprContext.TCL_CODE);
  }
  
  public static enum ExprContext {
    TCL_CODE,
    VALUE_STRING
  }
  
  /**
   * 
   * @param sb
   * @param escapeForString escape expression for insertion into double
   *              quoted TCL string.  
   */
  public void appendTo(StringBuilder sb, ExprContext mode)
  {
    Iterator<Expression> it = items.iterator();
    while (it.hasNext())
    {
      Expression tree = it.next();
      tree.appendTo(sb, mode);
      if (it.hasNext())
        sb.append(' ');
    }
  }
}
