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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Tcl square bracket expression, e.g., [ expr ... ]
 * */
public class Square extends Expression
{
  private List<Expression> items = null;
  private Command command = null;

  /** if true, add braces as appropriate for context */
  private boolean treatAsList = false;

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

  public Square(List<Expression> tokens)
  {
    this.items = new ArrayList<Expression>(tokens);
  }

  public Square(Expression... tokens)
  {
    this(Arrays.asList(tokens));
  }

  public Square(String... strings)
  {
    items = new ArrayList<Expression>(strings.length);
    for (String s : strings)
      items.add(new Token(s));
  }

  public Square(Command command)
  {
    this.command = command;
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
    boolean brace = treatAsList && mode == ExprContext.LIST_STRING;

    if (brace)
        sb.append("{");

    sb.append("[ ");

    if (items != null)
    {
      Iterator<Expression> it = items.iterator();
      while (it.hasNext())
      {
        Expression tree = it.next();
        tree.appendTo(sb, mode);
        if (it.hasNext())
          sb.append(' ');
      }
    }
    else
    {
      command.appendTo(sb);
    }
    
    sb.append(" ]");

    if (brace)
      sb.append("}");
  }

  public static Square fnCall(String fnName, Expression... args) {
    return fnCall(new Token(fnName), args);
  }

  public static Square fnCall(Token fn, Expression... args) {
    return fnCall(fn, Arrays.asList(args));
  }

  public static Square fnCall(Token fn, List<? extends Expression> args) {
    Expression newE[] = new Expression[args.size() + 1];

    newE[0] = fn;
    int i = 1;
    for (Expression arg: args) {
      newE[i] = arg;
      i++;
    }
    return new Square(newE);
  }


  @Override
  public boolean supportsStringList() {
    return true;
  }

  public void setTreatAsList(boolean val) {
    this.treatAsList = val;
  }

}
