
package exm.stc.tclbackend.tree;

import java.util.List;
import java.util.Set;

import exm.stc.common.util.StringUtil;

public class Proc extends TclTree
{
  String name;
  List<String> args;
  Sequence sequence;

  /**
   * 
   * @param name
   * @param usedFunctionNames used to ensure we're not generating duplicate 
   *                          functions
   * @param args
   * @param sequence
   */
  public Proc(String name, Set<String> usedFunctionNames,
                          List<String> args, Sequence sequence)
  {
    assert(!usedFunctionNames.contains(name));
    usedFunctionNames.add(name);
    this.name = name;
    this.args = args;
    this.sequence = sequence;
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("\nproc ");
    sb.append(name);
    sb.append(" { ");
    sb.append(StringUtil.concat(args));
    sb.append(" } {\n");
    sequence.setIndentation(indentation+indentWidth);
    sequence.appendTo(sb);
    sb.append("}\n\n");
  }
}
