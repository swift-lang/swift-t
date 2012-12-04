package exm.stc.tclbackend.tree;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Tcl builtin {*} operator that expands a list into multiple arguments
 * for function call.
 */
public class Expand extends Expression {
  
  private Expression expr;
  
  public Expand(Expression expr) {
    this.expr = expr;
  }
  
  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    if (mode == ExprContext.VALUE_STRING) {
      throw new STCRuntimeError("Expand can't be used in string");
    }
    sb.append("{*}");
    expr.appendTo(sb, mode);
  }

}
