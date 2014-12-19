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
import java.util.Collections;
import java.util.List;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;
import exm.stc.frontend.TypeChecker;

public class LValue {
  /** A predecessor before reduction */
  public final LValue predecessor;
  public final Var var;
  public final String varName;
  public final List<SwiftAST> indices;
  public final SwiftAST tree;

  public LValue(SwiftAST tree, Var var) {
    this(tree, var, new ArrayList<SwiftAST>(0));
  }

  public LValue(SwiftAST tree, Var var, List<SwiftAST> indices) {
    this(null, tree, var, indices);
  }

  public LValue(LValue predecessor,
                SwiftAST tree, Var var, List<SwiftAST> indices) {
    this.predecessor = predecessor;
    this.tree = tree;
    this.var = var;
    this.varName = var.name();
    this.indices = Collections.unmodifiableList(indices);
  }

  public LValue(SwiftAST tree, String varName, List<SwiftAST> indices) {
    this.predecessor = null;
    this.tree = tree;
    this.var = null;
    this.varName = varName;
    this.indices = Collections.unmodifiableList(indices);
  }

  public LValue getPredecessor() {
    return predecessor;
  }

  public Var getOuterArray() {
    assert(Types.isArray(this.var) || Types.isArrayRef(this.var));
    LValue prev = this;
    LValue curr = this.predecessor;
    while (curr != null &&
           (Types.isArray(curr.var) || Types.isArrayRef(curr.var))) {
      prev = curr;
      curr = curr.predecessor;
    }
    return prev.var;
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
              + st.getStructTypeName() + " does not have field called " + fieldName);
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

  /**
  *
  * @param context
  *          If context is provided, set var field of targets, and check that
  *          variables are defined
  * @param tree
  * @return
  */
  public static List<LValue> extractLVals(Context context,
     SwiftAST tree) {
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
  private static LValue extractAssignmentID(Context context, SwiftAST subtree) {
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

    // It is ok if here variable isn't undeclared, since we might want
    // to automatically declare it
    Var var = context.lookupVarUnsafe(varName);
    if (var != null) {
      return new LValue(subtree, var, path);
    } else {
      // Return only var names
      Logging.getSTCLogger().debug("Undeclared var " + varName);
      return new LValue(subtree, varName, path);
    }
  }

  /**
   * If lval var not declared in current context, return var that
   * needs to be declared
   * @param rValType
   * @return
   * @throws UserException
   */
  public LValue varDeclarationNeeded(Context context,
          Type rValType) throws UserException {
    if (this.var != null) {
      // lval var already declared
      return null;
    }

    if (!Settings.getBoolean(Settings.AUTO_DECLARE)) {
      throw UndefinedVarError.fromName(context, this.varName);
    }

    for (SwiftAST t: this.indices) {
      if (t.getType() != ExMParser.ARRAY_PATH) {
        throw new UndefinedTypeException(context, "Referencing structure field" +
                " for variable of unknown structure type: " + this);
      }
    }
    // All must be array indices
    int arrayDepth = this.indices.size();

    // Work out what type lhs var must be
    Type declType = rValType;
    if (Types.isUnion(declType)) {
      declType = UnionType.getAlternatives(declType).get(0);
    }

    if (Types.isRef(declType)) {
      declType = declType.memberType();
    }

    declType = Types.concretiseArbitrarily(declType);

    for (int i = 0; i < arrayDepth; i++) {
      SwiftAST keyExpr = indices.get(i).child(0);
      Type keyType = TypeChecker.findExprType(context, keyExpr);
      keyType = Types.concretiseArbitrarily(keyType);
      declType = ArrayType.sharedArray(keyType, declType);
    }

    Var newVar = new Var(declType, this.varName, Alloc.STACK, DefType.LOCAL_USER,
                         VarProvenance.userVar(context.getSourceLoc()));
    return new LValue(this.tree, newVar, this.indices);
  }
}

