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
package exm.stc.ast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTree;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.frontend.VariableUsageInfo;

/**
 * A custom tree class for the Swift AST with slots to store semantic info 
 * about Swift script
 *
 */
public class SwiftAST extends CommonTree {
  
  private ExprType exprType = null;
  private VariableUsageInfo variableUsage = null;
  
  public VariableUsageInfo getVariableUsage() {
    return variableUsage;
  }
  
  public VariableUsageInfo checkedGetVariableUsage() {
    if (variableUsage == null) {
      throw new STCRuntimeError("Lookup of saved variable usage info"
            + " for block at " + this.getLine() + ":"
            + this.getCharPositionInLine() + " failed");
    }
    return variableUsage;
  }

  public void setVariableUsage(VariableUsageInfo variableUsage) {
    this.variableUsage = variableUsage;
  }

  public ExprType getExprType() {
    return exprType;
  }

  public void setType(ExprType exprType) {
    this.exprType = exprType;
  }
  
  /**
   * alternative to getChild so we can avoid having the cast to 
   * SwiftAST everywhere
   */
  public SwiftAST child(int i) {
    return (SwiftAST)super.getChild(i);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public List<SwiftAST> children() {
    if (children == null) {
      return Collections.emptyList();
    }
    return (List<SwiftAST>)(List)(this.children);
  }
  
  public List<SwiftAST> children(int start) {
    return children().subList(start, children.size());
  }
  
  public List<SwiftAST> children(int start, int end) {
    return children().subList(start, end);
  }

  public SwiftAST(Token t) {
    super(t);
  }
  
  public String printTree() {
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    writer.println("printTree:");
    printTree(writer, 0);
    return sw.toString();
  }

  private void printTree(PrintWriter writer, int indent) {
    indent(writer, indent);
    writer.println(this.getText());
    for (int i = 0; i < this.getChildCount(); i++)
      this.child(i).printTree(writer, indent+2);
  }

  public static void indent(PrintWriter writer, int indent)
  {
    for (int i = 0; i < indent; i++)
      writer.print(' ');
  }
  
}
