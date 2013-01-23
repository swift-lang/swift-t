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
package exm.stc.frontend.tree;

import java.util.ArrayList;

import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;

public class VariableDeclaration {

  private final ArrayList<VariableDescriptor> vars;
  private final ArrayList<SwiftAST> declTrees;
  private final ArrayList<SwiftAST> exprs; // initial values, if provided
  
  public VariableDeclaration() {
    this.vars = new ArrayList<VariableDescriptor>();
    this.exprs = new ArrayList<SwiftAST>();
    this.declTrees = new ArrayList<SwiftAST>();
  }
  
  public void addVar(VariableDescriptor var, SwiftAST declTree) {
    addVar(var, declTree, null);
  }
  
  public void addVar(VariableDescriptor var, SwiftAST declTree, SwiftAST expr) {
    this.vars.add(var);
    this.declTrees.add(declTree);
    this.exprs.add(expr);
  }
  
  public int count() {
    return this.vars.size();
  }
  
  public VariableDescriptor getVar(int i) {
    return vars.get(i);
  }
  
  /**
   * Return the AST for the variable declaration
   * @param i
   * @return
   */
  public SwiftAST getDeclTree(int i) {
    return declTrees.get(i);
  }
  
  public SwiftAST getVarExpr(int i) {
    return exprs.get(i);
  }
  
  public boolean isInitialised(int i) {
    return exprs.get(i) != null;
  }
  
  public static VariableDeclaration fromAST(Context context,
                                              SwiftAST tree) 
        throws UndefinedTypeException, InvalidSyntaxException  {
    VariableDeclaration res = new VariableDeclaration();
    assert(tree.getType() == ExMParser.DECLARATION);
    assert(tree.getChildCount() >= 2);
    assert(tree.child(0).getType() == ExMParser.ID);
    
    String typeName = tree.child(0).getText();
    Type baseType = context.lookupType(typeName);
    if (baseType == null) {
      throw new UndefinedTypeException(context, typeName);
    }
    
    for (SwiftAST declTree: tree.children(1)) {
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
      res.addVar(var, declTree, expr);
    }
    return res;
  }
  
  public static VariableDescriptor fromDeclareVariableRest(
          Context context, Type baseType, SwiftAST tree)
      throws UndefinedTypeException, InvalidSyntaxException {
    assert(tree.getType() == ExMParser.DECLARE_VARIABLE_REST);
    assert(tree.getChildCount() >= 1);
    SwiftAST nameTree = tree.child(0);
    assert(nameTree.getType() == ExMParser.ID);
    String varName = nameTree.getText();
    SwiftAST mappingExpr = null;
    
    Type varType = baseType;
    for (SwiftAST subtree: tree.children(1)) {
      if (subtree.getType() == ExMParser.ARRAY) {
        varType = new Types.ArrayType(varType);
      } else if (subtree.getType() == ExMParser.MAPPING) {
        assert(mappingExpr == null);
        assert(subtree.getChildCount() == 1);
        mappingExpr = subtree.child(0);
      } else {
        throw new STCRuntimeError("Unexpected token in variable " +
            "declaration: " + LogHelper.tokName(subtree.getType()));
      }
    }
    return new VariableDescriptor(varType, varName, mappingExpr);
  }
  
  public static class VariableDescriptor {
    private final Type type;
    private final String name;
    private final SwiftAST mappingExpr;
    public VariableDescriptor(Type type, String name, SwiftAST mappingExpr) {
      super();
      this.type = type;
      this.name = name;
      this.mappingExpr = mappingExpr;
    }
    
    public Type getType() {
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
