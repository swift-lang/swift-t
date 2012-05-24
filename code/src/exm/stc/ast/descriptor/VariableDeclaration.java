package exm.stc.ast.descriptor;

import java.util.ArrayList;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.Types;
import exm.stc.ast.Types.SwiftType;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.frontend.Context;
import exm.stc.frontend.TypeChecker;

public class VariableDeclaration {

  private final ArrayList<VariableDescriptor> vars;
  private final ArrayList<SwiftAST> exprs; // initial values, if provided
  
  public VariableDeclaration() {
    this.vars = new ArrayList<VariableDescriptor>();
    this.exprs = new ArrayList<SwiftAST>();
  }
  
  public void addVar(VariableDescriptor var) {
    addVar(var, null);
  }
  
  public void addVar(VariableDescriptor var, SwiftAST expr) {
    this.vars.add(var);
    this.exprs.add(expr);
  }
  
  public int count() {
    return this.vars.size();
  }
  
  public VariableDescriptor getVar(int i) {
    return vars.get(i);
  }
  
  public SwiftAST getVarExpr(int i) {
    return exprs.get(i);
  }
  
  public boolean isInitialised(int i) {
    return exprs.get(i) != null;
  }
  
  public static VariableDeclaration fromAST(Context context, 
                TypeChecker typecheck, SwiftAST tree) 
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
      VariableDescriptor var =
              fromDeclareVariableRest(context, baseType, restTree);
      res.addVar(var, expr);
    }
    return res;
  }
  
  public static VariableDescriptor fromDeclareVariableRest(
          Context context, SwiftType baseType, SwiftAST tree)
      throws UndefinedTypeException, InvalidSyntaxException {
    assert(tree.getType() == ExMParser.DECLARE_VARIABLE_REST);
    assert(tree.getChildCount() >= 1);
    SwiftAST nameTree = tree.child(0);
    assert(nameTree.getType() == ExMParser.ID);
    String varName = nameTree.getText();
    SwiftAST mappingExpr = null;
    
    SwiftType varType = baseType;
    for (int i = 1; i < tree.getChildCount(); i++) {
      SwiftAST subtree = tree.child(i);
      if (subtree.getType() == ExMParser.ARRAY) {
        varType = new Types.ArrayType(varType);
      } else if (subtree.getType() == ExMParser.MAPPING) {
        assert(mappingExpr == null);
        assert(subtree.getChildCount() == 1);
        mappingExpr = subtree.child(0);
      } else {
        throw new STCRuntimeError("Unexpected token in variable " +
            "declaration: " + ExMParser.tokenNames[subtree.getType()]);
      }
    }
    return new VariableDescriptor(varType, varName, mappingExpr);
  }
  
  public static class VariableDescriptor {
    private final SwiftType type;
    private final String name;
    private final SwiftAST mappingExpr;
    public VariableDescriptor(SwiftType type, String name, SwiftAST mappingExpr) {
      super();
      this.type = type;
      this.name = name;
      this.mappingExpr = mappingExpr;
    }
    
    public SwiftType getType() {
      return type;
    }
    public String getName() {
      return name;
    }
    public SwiftAST getMappingExpr() {
      return mappingExpr;
    }
  }
  
}
