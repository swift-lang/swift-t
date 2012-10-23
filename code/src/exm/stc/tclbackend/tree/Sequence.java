
package exm.stc.tclbackend.tree;

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
  
  /**
   * Append at end of current sequence
   * @param seq
   */
  public void append(Sequence seq)
  {
    members.addAll(seq.members);
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
