package exm.ast;

import java.util.Collections;
import java.util.List;
import java.util.Stack;

import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTree;

import exm.ast.Types.SwiftType;
import exm.parser.util.ParserRuntimeException;

/**
 * A custom tree class for the Swift AST with slots to store semantic info 
 * about Swift script
 *
 */
public class SwiftAST extends CommonTree {
  
  private List<SwiftType> swiftType = null;
  private VariableUsageInfo variableUsage = null;
  
  public VariableUsageInfo getVariableUsage() {
    return variableUsage;
  }
  
  public VariableUsageInfo checkedGetVariableUsage() {
    if (variableUsage == null) {
      throw new ParserRuntimeException("Lookup of saved variable usage info"
            + " for block at " + this.getLine() + ":"
            + this.getCharPositionInLine() + " failed");
    }
    return variableUsage;
  }

  public void setVariableUsage(VariableUsageInfo variableUsage) {
    this.variableUsage = variableUsage;
  }

  public List<SwiftType> getSwiftType() {
    return swiftType == null ? null : Collections.unmodifiableList(swiftType);
  }

  public void setSwiftType(List<SwiftType> swiftType) {
    this.swiftType = swiftType;
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
  
}
