
package exm.stc.swigcbackend.tree;

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

    add(new Token("list"));
  }

  public TclList(Expression... listItems) {
    this();
    addAll(listItems);
  }
  
  public TclList(List<? extends Expression> listItems)
  {
    this();
    addAll(listItems);
  }

  public TclList(String... strings)
  {
    this();
    for (String s : strings)
      add(new Token(s));
  }
}
