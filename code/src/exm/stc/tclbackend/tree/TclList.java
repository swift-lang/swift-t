
package exm.stc.tclbackend.tree;

import java.util.List;

/**
 * Tcl list, e.g., [ list ... ]
 * @author wozniak
 * */
public class TclList extends Square
{
  public TclList()
  {
    super();

    items.add(new Token("list"));
  }

  public TclList(Expression... listItems) {
    this();
    for (Expression e: listItems) {
      items.add(e);
    }
  }
  
  public TclList(List<? extends Expression> listItems)
  {
    this();
    for (Expression tok: listItems) {
      items.add(tok);
    }
  }

  public TclList(String... strings)
  {
    this();
    for (String s : strings)
      items.add(new Token(s));
  }

  @Override
  public void add(Expression token)
  {
    items.add(token);
  }
}
