
package exm.stc.tclbackend.tree;

public abstract class Expression extends TclTree
{
  @Override
  public final void appendTo(StringBuilder sb) {
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
  public abstract void appendTo(StringBuilder sb, ExprContext mode);
}
