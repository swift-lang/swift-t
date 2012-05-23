package exm.stc.tclbackend.tree;

import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;

public class Switch extends Sequence {
  final TclTree condition;
  final List<Integer> caseLabels;
  final boolean hasDefault;
  final List<Sequence> cases;

  public Switch(TclTree condition, List<Integer> caseLabels, 
                      boolean hasDefault, List<Sequence> cases) {
    this.condition = condition;
    this.caseLabels = caseLabels;
    this.hasDefault = hasDefault;
    this.cases = cases;
    
    int labelcount = caseLabels.size();
    if (hasDefault) labelcount++;
    if (cases.size() != labelcount) {
      throw new STCRuntimeError("Number of case labels " + labelcount + 
                        " does not match number of case bodies " + cases.size());
    }
  }

  /**
   * Generate a TCL switch statement with integer labels 
   * and a default case if needed
   */
  @Override
  public void appendTo(StringBuilder sb) {
    
    indent(sb);
    // Open switch block
    sb.append("switch ");
    condition.appendTo(sb);
    sb.append(" {\n");
    increaseIndent();
    
    for (int i=0; i < cases.size(); i++) {
      Sequence caseBody = cases.get(i);
      indent(sb);
      if (hasDefault && i == (cases.size() - 1)) {
        sb.append("default ");
      } else {
        Integer caseLabel = caseLabels.get(i);
        sb.append(Integer.toString(caseLabel));
        sb.append(" ");
      }
      caseBody.setIndentation(indentation);
      caseBody.appendToAsBlock(sb);
      sb.append("\n");
    }
    
    // Close off switch block
    sb.append("\n");
    decreaseIndent();
    indent(sb);
    sb.append("}\n");
  }

}
