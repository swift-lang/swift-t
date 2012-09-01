package exm.stc.swigcbackend.tree;

public class LiteralFloat extends Expression {
  public static final String TCL_INF = "inf";
  public static final String TCL_NEGINF = "-inf";
  public static final String TCL_NAN = "NaN";
  
  private double value;

  public LiteralFloat(double value) {
    this.value = value;
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    String tclLiteral; 
    if (Double.isInfinite(value)) {
      if (value > 0.0) {
        tclLiteral = TCL_INF;
      } else {
        tclLiteral = TCL_NEGINF;
      }
    } else if (Double.isNaN(value)) {
      tclLiteral = TCL_NAN;
    } else {
      tclLiteral = Double.toString(value);
    }
    sb.append(tclLiteral);
  }

}
