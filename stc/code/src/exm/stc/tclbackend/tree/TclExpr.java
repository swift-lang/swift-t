package exm.stc.tclbackend.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TclExpr extends Square {

  public static class TclExprFn extends Expression {
    private final String fn;
    private final List<Expression> exprs;

    public TclExprFn(String fn, Expression... exprs) {
      this.fn = fn;
      this.exprs = new ArrayList<Expression>(Arrays.asList(exprs));
    }

    @Override
    public void appendTo(StringBuilder sb, ExprContext mode) {
      sb.append(fn);
      sb.append("(");
      boolean first = true;
      for (Expression e: exprs) {
        if (first) {
          first = false;
        } else {
          sb.append(",");
        }
        e.appendTo(sb, mode);
      }
      sb.append(")");
    }

    @Override
    public boolean supportsStringList() {
      return false;
    }
  }

  public static class TclExprParen extends Expression {
    private final boolean parenthesise;
    private final List<Expression> exprs;

    public TclExprParen(boolean parenthesise, Expression... exprs) {
      this.parenthesise = parenthesise;
      this.exprs = new ArrayList<Expression>(Arrays.asList(exprs));
    }

    @Override
    public void appendTo(StringBuilder sb, ExprContext mode) {
      if (parenthesise)
        sb.append("(");
      boolean first = true;
      for (Expression e: exprs) {
        if (first) {
          first = false;
        } else {
          sb.append(" ");
        }
        e.appendTo(sb, mode);
      }
      if (parenthesise)
        sb.append(")");
    }

    @Override
    public boolean supportsStringList() {
      return false;
    }
  }

  private static final Token EXPR = new Token("expr");

  public static final Token TIMES = new Token("*");
  public static final Token DIV = new Token("/");
  public static final Token MINUS = new Token("-");
  public static final Token PLUS = new Token("+");
  public static final Token POW = new Token("**");

  public static final Token NOT = new Token("!");
  public static final Token EQ = new Token("==");
  public static final Token NEQ = new Token("!=");
  public static final Token LT = new Token("<");
  public static final Token LTE = new Token("<=");
  public static final Token GT = new Token(">");
  public static final Token GTE = new Token(">=");

  public static final Token AND = new Token("&&");
  public static final Token OR = new Token("||");

  // Function names
  public static final String ABS = "abs";
  public static final String LOG = "log";
  public static final String SQRT = "sqrt";
  public static final String EXP = "exp";
  public static final String CEIL = "ceil";
  public static final String FLOOR = "floor";
  public static final String ROUND = "round";
  public static final String DOUBLE_CONV = "double";
  public static final String INT_CONV = "int";
  public static final String MIN = "min";
  public static final String MAX = "max";


  public TclExpr(Expression... contents) {
    this(true, contents);
  }

  public TclExpr(boolean braced, Expression... contents) {
    this(braced, Arrays.asList(contents));
  }

  public TclExpr(List<Expression> contents) {
    this(true, contents);
  }

  public TclExpr(boolean braced, List<Expression> contents) {
    super(squareContents(braced, contents));
  }

  private static List<Expression> squareContents(boolean braced,
                                          List<Expression> contents) {
    List<Expression> newE = new ArrayList<Expression>(contents.size()+3);
    // TODO: only certain expression types are valid inside braced expression
    newE.add(EXPR);
    if (braced)
      newE.add(new Token("{"));
    for (Expression expr: contents) {
      assert(expr != null);
      newE.add(expr);
    }
    if (braced)
      newE.add(new Token("}"));
    return newE;
  }

  public static TclExpr mult(Expression... es) {
    return opExpr(TIMES, es);
  }

  public static TclExpr sum(Expression... es) {
    return opExpr(PLUS, es);
  }

  public static TclExpr minus(Expression e1, Expression e2) {
    return opExpr(MINUS, e1, e2);
  }

  /**
   * Function call in expression context
   */
  public static Expression exprFn(String fn, Expression... es) {
    return new TclExprFn(fn, es);
  }

  public static Expression max(Expression... es) {
    return exprFn(MAX, es);
  }
  public static Expression min(Expression... es) {
    return exprFn(MIN, es);
  }

  public static Expression paren(Expression... es) {
    return new TclExprParen(true, es);
  }

  public static Expression group(Expression... es) {
    return new TclExprParen(false, es);
  }

  public static Expression ternary(Expression condition,
                            Expression e1, Expression e2) {
    return new TclExpr(Arrays.asList(paren(condition), new Token("?"),
                                paren(e1), new Token(":"), paren(e2)));
  }

  private static TclExpr opExpr(Token op, Expression... es) {
    assert(es.length >= 1);
    List<Expression> toks = new ArrayList<Expression>();
    toks.add(es[0]);
    for (int i = 1; i < es.length; i++) {
      toks.add(op);
      toks.add(es[i]);
    }
    return new TclExpr(toks);
  }

}
