package exm.ast.descriptor;

import java.util.ArrayList;

import exm.ast.Context;
import exm.ast.SwiftAST;
import exm.ast.TypeChecker;
import exm.ast.Types.SwiftType;
import exm.ast.Variable.DefType;
import exm.ast.Variable;
import exm.parser.antlr.ExMParser;
import exm.parser.util.InvalidSyntaxException;
import exm.parser.util.UndefinedTypeException;

public class VariableDeclaration {

  private final ArrayList<Variable> vars;
  private final ArrayList<SwiftAST> exprs; // initial values, if provided
  
  public VariableDeclaration() {
    this.vars = new ArrayList<Variable>();
    this.exprs = new ArrayList<SwiftAST>();
  }
  
  public void addVar(Variable var) {
    addVar(var, null);
  }
  
  public void addVar(Variable var, SwiftAST expr) {
    this.vars.add(var);
    this.exprs.add(expr);
  }
  
  public int count() {
    return this.vars.size();
  }
  
  public Variable getVar(int i) {
    return vars.get(i);
  }
  
  public SwiftAST getVarExpr(int i) {
    return exprs.get(i);
  }
  
  public boolean isInitialised(int i) {
    return exprs.get(i) != null;
  }
  
  public static VariableDeclaration fromAST(Context context, 
                TypeChecker typecheck, SwiftAST tree, DefType defType) 
        throws UndefinedTypeException, InvalidSyntaxException  {
    VariableDeclaration res = new VariableDeclaration();
    assert(tree.getType() == ExMParser.DECLARATION);
    assert(tree.getChildCount() >= 2);
    assert(tree.child(0).getType() == ExMParser.ID);
    
    String typeName = tree.child(0).getText();
    SwiftType baseType = context.lookupType(typeName);
    if (baseType == null) {
      throw new UndefinedTypeException(context, typeName);
    }
    
    for (int i = 1; i < tree.getChildCount(); i++) {
      SwiftAST declTree = tree.child(i);
      SwiftAST expr;
      SwiftAST restTree;
      if (declTree.getType() == ExMParser.DECLARE_ASSIGN) {
        assert(declTree.getChildCount() == 2);
        restTree = declTree.child(0);
        expr = declTree.child(1);
      } else {
        restTree = declTree;
        expr = null;
      }
      assert(restTree.getType() == ExMParser.DECLARE_VARIABLE_REST);
      Variable var = Variable.fromDeclareVariableTree(context, baseType,
          restTree, defType);
      res.addVar(var, expr);
    }
    return res;
  }
}
