package exm.stc.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedVariableException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;

public class LValue {
  public final Var var;
  public final String varName;
  public final List<SwiftAST> indices;
  public final SwiftAST tree;
  
  public LValue(SwiftAST tree, Var var) {
    this(tree, var, new ArrayList<SwiftAST>(0));
  }
  public Type getType(Context context) throws TypeMismatchException {
    return getType(context, indices.size());
  }

  public Type getType(Context context, int depth)
      throws TypeMismatchException {
    if (var == null) {
      throw new STCRuntimeError("invoked getType() on AssignTarget "
          + "without var field");
    }
    if (indices.size() < depth) {
      throw new STCRuntimeError("Trying to get type beyond length "
          + " of path");
    }

    Type t = var.type();
    for (int i = 0; i < depth; i++) {
      SwiftAST tree = indices.get(i);
      if (tree.getType() == ExMParser.ARRAY_PATH) {
        if (Types.isArray(t)) {
          t = t.memberType();
        } else if (Types.isArrayRef(t)) {
          t = t.memberType().memberType();
        } else {
          throw new TypeMismatchException(context, "Assignment target "
              + toString() + " does not have a valid type: can only "
              + " index " + i + " times");
        }

      } else if (tree.getType() == ExMParser.STRUCT_PATH) {
        if (!Types.isStruct(t)) {
          throw new TypeMismatchException(context, "Assignment target "
              + toString() + " does not have "
              + " a valid type: trying to assign to field of non-struct "
              + "type");
        }
        StructType st = (StructType) t;
        assert (tree.getChildCount() == 1);
        String fieldName = tree.child(0).getText();
        Type newType = st.getFieldTypeByName(fieldName);
        if (newType == null) {
          throw new TypeMismatchException(context, "Struct type "
              + st.getTypeName() + " does not have field called " + fieldName);
        }
        t = newType;
      } else {
        throw new STCRuntimeError("Unexpected token type"
            + LogHelper.tokName(tree.getType()) + " on right hand side"
            + " of assignment");
      }
    }
    return t;
  }

  @Override
  public String toString() {
    String res = varName;
    for (SwiftAST index: indices) {
      if (index.getType() == ExMParser.STRUCT_PATH) {
        res += "." + index.child(0).getText();
      } else {
        res += "[_]";
      }
    }
    return res;
  }

  /**
   * @return true if the path contains no array lookups
   */
  public boolean noArrayLookups() {
    for (SwiftAST i : indices) {
      if (i.getType() == ExMParser.ARRAY_PATH)
        return false;
    }
    return true;
  }

  /**
   * @return the first sequence of struct_path in the indicies
   */
  public List<String> structPath() {
    List<String> structPath = new ArrayList<String>();

    for (SwiftAST ixExpr : indices) {
      if (ixExpr.getType() != ExMParser.STRUCT_PATH) {
        break;
      }
      assert (ixExpr.getChildCount() == 1);

      structPath.add(ixExpr.child(0).getText());
    }
    return structPath;
  }

  public LValue(SwiftAST tree, Var var, List<SwiftAST> indicies) {
    this.tree = tree;
    this.var = var;
    this.varName = var.name();
    this.indices = Collections.unmodifiableList(indicies);
  }

  public LValue(SwiftAST tree, String varName, List<SwiftAST> indicies) {
    this.tree = tree;
    this.var = null;
    this.varName = varName;
    this.indices = Collections.unmodifiableList(indicies);
  }

  /**
  *
  * @param context
  *          If context is provided, set var field of targets, and check that
  *          variables are defined
  * @param tree
  * @return
  * @throws UndefinedVariableException
  */
 public static List<LValue> extractLVals(Context context,
     SwiftAST tree) throws UndefinedVariableException {
   if (tree.getType() != ExMParser.IDENTIFIER_LIST) {
     throw new STCRuntimeError("Expected token identifier_list "
         + " but got " + tree.getText());
   }
   
   ArrayList<LValue> lvals = new ArrayList<LValue>(tree.getChildCount());
   for (SwiftAST subtree: tree.children()) {
     LValue lval = extractAssignmentID(context, subtree);
     lvals.add(lval);
   }
   return lvals;
 }

 /**
    @param context 
  */
 private static LValue extractAssignmentID(Context context, SwiftAST subtree)
     throws UndefinedVariableException {
   if (subtree.getType() != ExMParser.ASSIGN_TARGET) {
     throw new STCRuntimeError("Expected ASSIGN_TARGET ast node");
   }
   if (subtree.getChildCount() < 1) {
     throw new STCRuntimeError("Expected ASSIGN_TARGET ast node "
         + "to have at least one child");
   }

   SwiftAST varTree = subtree.child(0);
   if (varTree.getType() != ExMParser.VARIABLE || varTree.getChildCount() != 1) {
     throw new STCRuntimeError("Expected VARIABLE with one child "
         + "as first child of ASSIGN_TARGET");
   }

   String varName = varTree.child(0).getText();

   List<SwiftAST> path = new ArrayList<SwiftAST>();

   for (int i = 1; i < subtree.getChildCount(); i++) {
     SwiftAST pathTree = subtree.child(i);
     if (pathTree.getType() == ExMParser.ARRAY_PATH
         || pathTree.getType() == ExMParser.STRUCT_PATH) {
       path.add(pathTree);
     } else {
       throw new STCRuntimeError("Unexpected token "
           + LogHelper.tokName(pathTree.getType()));
     }
   }

   Var var = context.getDeclaredVariable(varName);
   if (var == null) {
     throw new UndefinedVariableException(context, "Variable " + varName
         + " is not defined");
   }
   return new LValue(subtree, var, path);
 }
}

