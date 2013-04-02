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

import java.util.List;

public class TclString extends Expression
{
  public static final TclTree EMPTY = new TclString("", false);
  private final StringBuilder sb;

  public TclString(String string, boolean escape)
  {
    this.sb = new StringBuilder();
    if (escape) {
      this.sb.append(tclEscapeString(string));
    } else {
      this.sb.append(string);
    }
  }

  /**
   * Create a string from the expressions each expression
   * separated by a space
   * @param exprs
   */
  public TclString(List<? extends Expression> exprs) {
    this(exprs, true);
  }
  
  /**
   * This constructor lets you control if spaces are added between
   * items
   * @param exprs
   * @param insertSpaces
   */
  public TclString(List<? extends Expression> exprs, boolean insertSpaces)
  {
    this("", false);
    exprAppend(exprs, insertSpaces);
  }

  public void exprAppend(List<? extends Expression> exprs,
                                              boolean insertSpaces) {
    boolean first = true;
    for (Expression e: exprs) {
      if (first) {
        first = false;
      } else if (insertSpaces) {
        sb.append(' ');
      }
      add(e);
    }
  }

  @Override
  public void appendTo(StringBuilder outSb, ExprContext mode)
  {
    if (mode == ExprContext.TCL_CODE) {
      outSb.append('\"');
      outSb.append(this.sb);
      outSb.append('\"');
    } else {
      assert(mode == ExprContext.VALUE_STRING);
      outSb.append(this.sb);
    }
  }
  
  public void stringAppend(String s, boolean escape) {
    if (escape) {
      s = tclEscapeString(s);
    }
    this.sb.append(s);
  }
  
  public void add(Expression expr) {
    expr.appendTo(sb, ExprContext.VALUE_STRING);
  }
  
  public void add(Expression expr, ExprContext mode) {
    expr.appendTo(sb, mode);
  }
  
  @Override
  public boolean supportsStringList() {
    return false;
  }
  
  /**
   * See http://tmml.sourceforge.net/doc/tcl/Tcl.html
   * for information about tcl escape sequences
   * @param unescaped
   * @return
   */
  public static String tclEscapeString(String unescaped) {
    StringBuilder escaped = new StringBuilder();
    tclEscapeString(unescaped, escaped);
    return escaped.toString();
  }
  
  private static void tclEscapeString(String unescaped, StringBuilder escaped) {
    for (int i = 0; i < unescaped.length(); i++) {
      char c = unescaped.charAt(i);
      switch (c) {
      case '\007':
        escaped.append("\\007");
        break;
      case '\b':
        escaped.append("\\b");
        break;
      case '\f':
        escaped.append("\\f");
        break;
      case '\n':
        escaped.append("\\n");
        break;
      case '\r':
        escaped.append("\\r");
        break;
      case '\t':
        escaped.append("\\t");
        break;
      case '\013':
        escaped.append("\\v");
        break;
      case '$':
        escaped.append("\\$");
        break;
      case '[':
        escaped.append("\\[");
        break;
      case ']':
        escaped.append("\\]");
        break;
      case '\\':
        escaped.append("\\\\");
        break;
      case '"':
        escaped.append("\\\"");
        break;
      default:
        if (Character.isISOControl(c)) {
          escaped.append("\\" + Integer.toOctalString(c));
        } else {
          escaped.append(c);
        }
      }
    }
  }
}

