package exm.stc.ast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Stack;

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

  public ExprType getSwiftType() {
    return exprType;
  }

  public void setSwiftType(ExprType exprType) {
    this.exprType = exprType;
  }
  
  /**
   * alternative to getChild so we can avoid having the cast to 
   * SwiftAST everywhere
   */
  public SwiftAST child(int i) {
    return (SwiftAST)super.getChild(i);
  }

  public SwiftAST(Token t) {
    super(t);
  }
  
  /** Recursively clear all type annotations from AST */
  public void clearTypeInfo() {
    Stack<SwiftAST> nodes = new Stack<SwiftAST>();
    nodes.push(this);
    while(!nodes.empty()) {
      SwiftAST node = nodes.pop();
      node.setSwiftType(null);
      for (int i = 0; i < node.getChildCount(); i++) {
        nodes.push(node.child(i));
      }
    }
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
