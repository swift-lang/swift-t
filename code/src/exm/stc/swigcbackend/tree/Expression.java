
package exm.stc.swigcbackend.tree;

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
   * @param mode how to escape expression  
   */
  public abstract void appendTo(StringBuilder sb, ExprContext mode);
}
