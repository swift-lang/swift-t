
package exm.tcl;

import java.util.ArrayList;
import java.util.List;

/**
 * Line after line sequence of Tcl code
 * @author wozniak
 * */
public class Sequence extends TclTree
{
  List<TclTree> members = new ArrayList<TclTree>();

  public void add(TclTree tree)
  {
    members.add(tree);
  }

  public void add(TclTree[] trees)
  {
    for (TclTree tree : trees)
      add(tree);
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    for (TclTree member : members)
    {
      member.setIndentation(indentation);
      member.appendTo(sb);
    }
  }
}
