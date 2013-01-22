/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package exm.stc.tclbackend.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Tcl square bracket expression, e.g., [ expr ... ]
 * */
public class Square extends Expression
{

  private final List<Expression> items;
  
  public Square()
  {
    super();
    this.items = new ArrayList<Expression>();
  }

  public Square(Expression expression)
  {
    this();
    this.add(expression);
  }

  public Square(Expression... tokens)
  {
    this.items = new ArrayList<Expression>(tokens.length);
    for (Expression e: tokens) {
      items.add(e);
    }
  }

  public Square(String... strings)
  {
    items = new ArrayList<Expression>(strings.length);
    for (String s : strings)
      items.add(new Token(s));
  }

  public void add(Expression item)
  {
    items.add(item);
  }
  
  public void addAll(Expression... exprs)
  {
    for (Expression e: exprs) {
      items.add(e); 
    }
  }
  
  public void addAll(Collection<? extends Expression> exprs)
  {
    items.addAll(exprs); 
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode)
  {
    if (mode == ExprContext.VALUE_STRING) {
      sb.append("\\[ ");
    } else {
      sb.append("[ ");
    }
    Iterator<Expression> it = items.iterator();
    while (it.hasNext())
    {
      Expression tree = it.next();
      tree.appendTo(sb, mode);
      if (it.hasNext())
        sb.append(' ');
    }
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
