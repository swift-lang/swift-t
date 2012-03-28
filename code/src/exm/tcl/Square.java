
package exm.tcl;

import java.util.ArrayList;

/**
 * Tcl square bracket expression, e.g., [ expr ... ]
 * */
public class Square extends Expression
{
  public Square()
  {
    super();
  }

  public Square(Expression expression)
  {
    super(expression);
  }

  public Square(String... tokens)
  {
    super(new Expression(tokens));
  }

  public Square(Expression... tokens)
  {
    super(new Expression(tokens));
  }
  

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode)
  {
    if (mode == ExprContext.VALUE_STRING) {
      sb.append("\\[ ");
    } else {
      sb.append("[ ");
    }
    super.appendTo(sb, mode);
    if (mode == ExprContext.VALUE_STRING) {
      sb.append(" \\]");
    } else {
      sb.append(" ]");
    }
  }
  
  public static Square arithExpr(Expression... contents) {
    ArrayList<Expression> newE = new ArrayList<Expression>(contents.length+1);

    newE.add(new Token("expr"));
    for (Expression expr: contents) {
      assert(expr != null);
      newE.add(expr);
    } 
    return new Square(newE.toArray(new Expression[0]));
  }
  
  public static Square fnCall(String fnName, Expression... args) {
    Expression newE[] = new Expression[args.length + 1];

    newE[0] = new Token(fnName);
    int i = 1;
    for (Expression arg: args) {
      newE[i] = arg;
      i++;
    } 
    return new Square(newE);
  }
}
