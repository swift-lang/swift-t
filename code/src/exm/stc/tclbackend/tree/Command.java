
package exm.stc.tclbackend.tree;

import java.util.*;

public class Command extends TclTree
{
  List<TclTree> tokens;

  public Command(TclTree... tokens)
  {
    this.tokens = Arrays.asList(tokens);
  }

  public Command(String cmd, List<? extends Expression> args) {
    tokens = new ArrayList<TclTree>(args.size() + 1);
    tokens.add(new Token(cmd));
    tokens.addAll(args);
  }
  
  public Command(String... strings)
  {
    tokens = new ArrayList<TclTree>(strings.length);
    for (String s : strings)
      tokens.add(new Token(s));
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    Iterator<TclTree> it = tokens.iterator();
    while (it.hasNext())
    {
      TclTree tree = it.next();
      tree.appendTo(sb);
      if (it.hasNext())
        sb.append(' ');
    }
    sb.append('\n');
  }
}
