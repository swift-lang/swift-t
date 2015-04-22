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
package exm.stc.frontend.typecheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.ScalarUpdateableType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.TupleType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;
import exm.stc.frontend.VarRepr;
import exm.stc.frontend.tree.ArrayElems;
import exm.stc.frontend.tree.ArrayRange;
import exm.stc.frontend.tree.Assignment.AssignOp;
import exm.stc.frontend.tree.LValue;

/**
 * This module handles checking the internal consistency of expressions,
 * and inferring the types of expressions in the SwiftScript AST
 */
public class TypeChecker {

  /**
   * Determine the expected type of an expression. If the expression is valid,
   * then this will return the type of the expression. If it is invalid,
   * it will throw an exception.
   *
   * @param context
   *          the context in which the expression resides
   * @param tree
   *          the expression tree
   * @return expression type.  This can contain concrete types, union types,
   *                           type variables, and union types.
   * @throws UserException
   */
  public static Type findExprType(Context context, SwiftAST tree)
      throws UserException {
    // Memoize this function to avoid recalculating type
    Type cached = tree.getExprType();
    if (cached != null) {
      LogHelper.trace(context, "Expr has cached type " + cached.toString());
      return cached;
    } else {
      Type calcedType = uncachedFindExprType(context, tree);
      tree.setType(calcedType);
      LogHelper.trace(context, "Expr found type " + calcedType.toString());

      for (Type t: TupleType.getFields(calcedType)) {
        // Log information about non-concrete types
        if (!t.isConcrete()) {
          LogHelper.trace(context, "Non-concrete type to resolve later: " + t);
        }
      }
      return calcedType;
    }
  }

  /**
   * Helper method to find expression type that checks expression type
   * given lVals to evaluated into.
   * @param context
   * @param lVals
   * @param rValExpr
   * @return
   * @throws UserException
   */
  public static Type findExprType(Context context,
                 List<LValue> lVals, SwiftAST rValExpr) throws UserException {
    Type rValT = TypeChecker.findExprType(context, rValExpr);
    int numFields = TupleType.getFields(rValT).size();

    if (numFields != lVals.size()) {
      throw new TypeMismatchException(context, "Needed " + numFields
              + " " + "assignment targets on LHS of assignment, but "
              + lVals.size() + " were present");
    }
    return rValT;
  }

  private static Type uncachedFindExprType(Context context, SwiftAST tree)
      throws UserException {
    int token = tree.getType();
    switch (token) {
    case ExMParser.TUPLE: {
      return tuple(context, tree);
    }
    case ExMParser.CALL_FUNCTION: {
      return FunctionTypeChecker.findFuncCallExprType(context, tree);
    }
    case ExMParser.VARIABLE:
      return var(context, tree);
    case ExMParser.INT_LITERAL:
        // interpret as float
      return UnionType.createUnionType(Types.F_INT, Types.F_FLOAT);
    case ExMParser.FLOAT_LITERAL:
      return Types.F_FLOAT;
    case ExMParser.STRING_LITERAL:
      return Types.F_STRING;
    case ExMParser.BOOL_LITERAL:
      return Types.F_BOOL;
    case ExMParser.OPERATOR:
      return OpTypeChecker.findOperatorResultType(context, tree);
    case ExMParser.STRUCT_LOAD:
      return structLoad(context, tree);
    case ExMParser.ARRAY_LOAD:
      return arrayLoad(context, tree);
    case ExMParser.ARRAY_RANGE: {
      return arrayRange(context, tree);
    }
    case ExMParser.ARRAY_ELEMS:
    case ExMParser.ARRAY_KV_ELEMS: {
      return ArrayElems.fromAST(context, tree).getType(context);
    }
    default:
      throw new STCRuntimeError("Unexpected token type in expression context: "
          + LogHelper.tokName(token));
    }
  }

  public static Type findStructFieldType(Context context,
      List<String> fields, Type type) throws TypeMismatchException {
    for (String f: fields) {
      type = findStructFieldType(context, f, type);
    }
    return type;
  }

  /**
   * Find the type of a particular field
   * @param context
   * @param fieldName
   * @param type a struct or a reference to a struct
   * @return
   * @throws TypeMismatchException if the type isn't a struct or struct ref,
   *                or the field doesn't exist in the type
   */
  public static Type findStructFieldType(Context context, String fieldName,
      Type type) throws TypeMismatchException {
    StructType structType;
    if (Types.isStruct(type)) {
      structType = ((StructType) type);
    } else if (Types.isStructRef(type)) {
      structType = ((StructType) (type.memberType()));
    } else {
      throw new TypeMismatchException(context, "Trying to access named field "
          + fieldName + " on non-struct expression of type " + type.toString());
    }
    Type fieldType = structType.fieldTypeByName(fieldName);
    if (fieldType == null) {
      throw new TypeMismatchException(context, "Field named " + fieldName +
          " does not exist in structure type " + structType.typeName() + ". " +
          "Valid fields are: " + structType.fields());
    }

    return fieldType;
  }

  private static Type tuple(Context context, SwiftAST tree) throws UserException {
    assert(tree.getType() == ExMParser.TUPLE);

    int n = tree.childCount();
    List<Type> types = new ArrayList<Type>(n);

    for (int i = 0; i < n; i++) {
      // Single type - can't have nested multiple types
      Type type = findExprType(context, tree.child(i));
      types.add(type);
    }
    return TupleType.makeTuple(types);
  }

  private static Type var(Context context, SwiftAST tree)
      throws UndefinedVarError {
    Var var = context.lookupVarUser(tree.child(0).getText());

    Type exprType = var.type();
    if (Types.isScalarUpdateable(exprType)) {
      // Can coerce to future
      return TupleType.makeTuple(UnionType.createUnionType(exprType,
                          ScalarUpdateableType.asScalarFuture(exprType)));
    }
    return exprType;
  }

  private static Type arrayLoad(Context context, SwiftAST tree)
          throws UserException, TypeMismatchException {
    Type arrType = findExprType(context, tree.child(0));

    List<Type> resultAlts = new ArrayList<Type>();
    for (Type arrAlt: UnionType.getAlternatives(arrType)) {
      if (Types.isArray(arrAlt) || Types.isArrayRef(arrAlt)) {
        Type memberType = containerElemType(arrAlt, false);

        // Depending on the member type of the array, the result type might be
        // the actual member type, or a reference to the member type
        Type resultAlt = VarRepr.containerElemRepr(memberType, false);
        resultAlts.add(resultAlt);
      } else {
        throw new TypeMismatchException(context,
            "Trying to index into non-array expression of type "
                + arrType.toString());
      }
    }
    return UnionType.makeUnion(resultAlts);
  }

  private static Type structLoad(Context context, SwiftAST tree)
          throws UserException, TypeMismatchException {
    Type structType = findExprType(context, tree.child(0));
    String fieldName = tree.child(1).getText();
    Type fieldType;
    fieldType = findStructFieldType(context, fieldName, structType);

    if (fieldType == null) {
      throw new TypeMismatchException(context, "No field called " + fieldName
          + " in structure type " + ((StructType) structType).getStructTypeName());
    }

    return structLoadResultType(structType, fieldType);
  }

  public static Type structLoadResultType(Type structType, Type fieldType) {
    if (VarRepr.storeRefInStruct(fieldType)) {
      // Must copy reference once available
      return new RefType(fieldType, false);
    } else {
      // Can subscript immediately
      return fieldType;
    }
  }

  private static Type arrayRange(Context context, SwiftAST tree)
      throws InvalidSyntaxException, UserException {
    // Check the arguments for type validity
    ArrayRange ar = ArrayRange.fromAST(context, tree);
    Type rangeType = ar.rangeType(context);
    // Type is always the same: an array of integers
    return ArrayType.sharedArray(Types.F_INT, rangeType);
  }

  public static void checkCopy(Context context, Type srctype, Type dsttype)
      throws TypeMismatchException {
    if (!(srctype.assignableTo(dsttype) &&
          srctype.getImplType().equals(dsttype.getImplType()))) {
      throw new TypeMismatchException(context, "Type mismatch: copying from "
          + srctype.toString() + " to " + dsttype.toString());
    }
  }

  public static Type checkSingleAssignment(Context context,
      Type rValType, Type lValType, String lValName) throws TypeMismatchException {
    return checkAssignment(context, AssignOp.ASSIGN, rValType, lValType,
                          lValName, new TreeMap<String, Type>());
  }

  /**
   * Checks whether rValType can be assigned to lValType
   * @param context
   * @param op
   * @param rValType
   * @param lValType lVal type, should not be ref
   * @param lValName
   * @param rValTVBindings type var bindings
   * @return returns rValType, or if rValType is non-concrete, return chosen
   *          concrete type
   * @throws TypeMismatchException
   */
  public static Type checkAssignment(Context context, AssignOp op,
      Type rValType, Type lValType, String lValName,
      Map<String, Type> rValTVBindings) throws TypeMismatchException {
    assert(!Types.isRef(lValType)) : lValType;

    for (Type t: UnionType.getAlternatives(rValType)) {
      if (!t.isConcrete()) {
        LogHelper.trace(context, "Non-concrete type alt for RVal: " + t);
      }
    }

    Type targetLValT;
    if (op == AssignOp.ASSIGN) {
      targetLValT = lValType;
    } else {
      assert(op == AssignOp.APPEND);
      targetLValT = Types.containerElemType(lValType);
    }

    if (LogHelper.isTraceEnabled()) {
      LogHelper.trace(context, "checkAssignment: " + targetLValT +
                   " " + rValType + " (" + rValTVBindings + ")");
    }

    for (Type rValAltT: UnionType.getAlternatives(rValType)) {

      // Type variables may already be bound
      if (!rValTVBindings.isEmpty()) {
        rValAltT = rValAltT.bindTypeVars(rValTVBindings);
        LogHelper.trace(context, "After binding typevars: " + rValAltT);
      }

      // Types to match
      Type rMatchT = rValAltT;
      Type lMatchT = targetLValT;

      boolean rDerefed = false;
      if (Types.isRef(rMatchT)) {
        rMatchT = rMatchT.memberType();
        rDerefed = true;
      }

      Map<String, Type> newTVBindings = rMatchT.matchTypeVars(lMatchT);
      if (newTVBindings == null) {
        // Couldn't match with this alternative
        LogHelper.trace(context, "Could not match type vars L: " +
                        lMatchT + " R: " + rMatchT);
      } else {

        if (!newTVBindings.isEmpty()) {
          LogHelper.trace(context, "Bound type vars: " + newTVBindings);
          rValTVBindings.putAll(newTVBindings);
          rMatchT = rMatchT.bindTypeVars(newTVBindings);
        }

        if (rMatchT.assignableTo(lMatchT)) {
          Type rValResultT = rMatchT;
          if (rDerefed) {
            rValResultT = new RefType(rValResultT, ((RefType)rValAltT).mutable());
          }
          if (LogHelper.isTraceEnabled()) {
            LogHelper.trace(context, "Selected rVal type " + rValResultT +
                             " from " + rValType + " to match lVal type " +
                             targetLValT);
          }
          return rValResultT;
        }
      }
    }
    throw new TypeMismatchException(context, "Cannot "
        + op.toString().toLowerCase() + " to "
        + lValName + ": LVal has type "
        + lValType.toString() + " but RVal has type " + rValType.toString());
  }

  /**
   * Get container elem type, modifying const/mutable status as
   * appropriate
   * @param typed
   * @param mutable
   * @return
   */
  public static Type containerElemType(Typed typed, boolean mutable) {
    Type result = Types.containerElemType(typed);
    if (!mutable && Types.isMutableRef(result)) {
      // Should be read-only ref
      result = new RefType(result.memberType(), false);
    } else if (mutable && Types.isConstRef(result)) {
      throw new STCRuntimeError("Wanted mutable field, got " + result);
    }

    return result;
  }
}
