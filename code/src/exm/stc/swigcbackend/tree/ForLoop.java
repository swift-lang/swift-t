package exm.stc.swigcbackend.tree;

/**
 * Loop over a range
 *
 */
public class ForLoop extends Sequence {
  private String loopVar;
  private TclTree loopBody;
  private Expression start;
  private Expression end; 
  private Expression incr; 

  /**
   * 
   * @param loopVar
   * @param start an integer Value or integer literal - start of range
   * @param end an integer Value or integer literal - end of range inclusive
   * @param incr an integer Value or integer literal - 
   *                amount to increment each iteration
   * @param loopBody
   */
  public ForLoop(String loopVar, Expression start, Expression end, 
      Expression incr, TclTree loopBody)
  {
    this.loopVar = loopVar;
    this.start = start;
    this.end = end;
    this.incr = incr;
    this.loopBody = loopBody;
    members.add(loopBody);
  }

  @Override
  public void appendTo(StringBuilder sb)
  { 
    Value loopVarVal = new Value(loopVar);
    indent(sb);
    
    // E.g. for { set i 0 } { $i <= $n } { incr i $k } 
    sb.append("for ");
    // initializer
    sb.append("{ set " + loopVar + " " + start.toString() + " } ");
    // condition
    sb.append("{ " + loopVarVal.toString() + " <= " + end.toString() + " } "); 
    // next
    sb.append("{ incr " + loopVar + " " + incr.toString() + " } "); 
    
    loopBody.setIndentation(this.indentation);
    loopBody.appendToAsBlock(sb);
    sb.append("\n");
  }
}
