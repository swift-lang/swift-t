package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.descriptor.ArrayElems;
import exm.stc.ast.descriptor.ArrayRange;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UndefinedOperatorException;
import exm.stc.common.exceptions.UndefinedVariableException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.OpType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.ReferenceType;
import exm.stc.common.lang.Types.ScalarFutureType;
import exm.stc.common.lang.Types.ScalarUpdateableType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Variable;
import exm.stc.common.util.Pair;

/**
 * This module handles checking the internal consistency of expressions,
 * and inferring the types of expressions in the SwiftScript AST
 */
public class TypeChecker {
  
  /**
   * Determine the expected type of an expression. If the expression is valid,
   * then this will return the type of the expression. If it is invalid, 
   * it will throw an exception, or may return a type
   * 
   * TODO: we don't fully validate the expression type here, some is still
   * done in SwiftScript (e.g. checking type of function arguments)
   *
   * @param context
   *          the context in which the expression resides
   * @param tree
   *          the expression tree
   * @param expected
   *          the type we expect this expression to have.  Can be null.
   *          If not null, in the case of an expression with ambiguous type,
   *          the typechecker will attempt to interpret it as that type.
   * @return
   * @throws UserException
   */
  public static ExprType findExprType(Context context, SwiftAST tree)
      throws UserException {
    // Memoize this function to avoid recalculating type
    ExprType cached = tree.getSwiftType();
    if (cached != null) {
      LogHelper.trace(context, "Expr has cached type " + cached.toString());
      return cached;
    } else {
      ExprType calcedType = uncachedFindExprType(context, tree);
      tree.setSwiftType(calcedType);
      LogHelper.trace(context, "Expr found type " + calcedType.toString());
      return calcedType;
    }
  }
  
  private static ExprType uncachedFindExprType(Context context, SwiftAST tree) 
      throws UserException {
    int token = tree.getType();
    switch (token) {
    case ExMParser.CALL_FUNCTION:
      String function = tree.child(0).getText();
      FunctionType ftype = context.lookupFunction(function);
      if (ftype == null) {
        throw UndefinedFunctionException.unknownFunction(context, function);
      }
      for (SwiftType out: ftype.getOutputs()) {
        if (Types.isTypeVar(out)) {
          //TODO
          throw new STCRuntimeError("Don't support type checking output " +
          		"typevar for function yet");
        }
      }
      return new ExprType(ftype.getOutputs());
    case ExMParser.VARIABLE: {
      Variable var = context.getDeclaredVariable(tree.child(0).getText());
      if (var == null) {
        throw new UndefinedVariableException(context, "Variable "
            + tree.child(0).getText() + " is not defined");
      }
      SwiftType exprType = var.getType();
      if (Types.isScalarUpdateable(exprType)) {
        // Can coerce to future
        return new ExprType(UnionType.createUnionType(exprType,
                            ScalarUpdateableType.asScalarFuture(exprType)));
      }
      return new ExprType(exprType);
    }
    case ExMParser.INT_LITERAL:
        // interpret as float
      return new ExprType(UnionType.createUnionType(Types.FUTURE_INTEGER,
                                                    Types.FUTURE_FLOAT));
    case ExMParser.FLOAT_LITERAL:
      return new ExprType(Types.FUTURE_FLOAT);
    case ExMParser.STRING_LITERAL:
      return new ExprType(Types.FUTURE_STRING);
    case ExMParser.BOOL_LITERAL:
      return new ExprType(Types.FUTURE_BOOLEAN);
    case ExMParser.OPERATOR:
      return findOperatorResultType(context, tree);
    case ExMParser.STRUCT_LOAD:
      ExprType structTypeL = findExprType(context, tree.child(0));
      String fieldName = tree.child(1).getText();
      if (structTypeL.elems() != 1) {
        throw new TypeMismatchException(context,
            "Trying to lookup field on return value of function with"
                + " zero or multiple return values");
      }
      SwiftType structType = structTypeL.get(0);
      SwiftType fieldType;
      fieldType = findStructFieldType(context, fieldName, structType);
      

      if (fieldType == null) {
        throw new TypeMismatchException(context, "No field called " + fieldName
            + " in structure type " + ((StructType) structType).getTypeName());
      }
      if (Types.isStruct(structType)) {
        return new ExprType(fieldType);
      } else { assert(Types.isStructRef(structType));
        return new ExprType(dereferenceResultType(fieldType));
      }

    case ExMParser.ARRAY_LOAD:
      ExprType arrTypeL = findExprType(context, tree.child(0));
      if (arrTypeL.elems() != 1) {
        throw new TypeMismatchException(context,
            "Indexing into return value of"
                + "function with zero or multiple return values");
      }

      SwiftType arrType = arrTypeL.get(0);
      SwiftType memberType;

      if (Types.isArray(arrType) || Types.isArrayRef(arrType)) {
        memberType = Types.getArrayMemberType(arrType);
      } else {
        throw new TypeMismatchException(context,
            "Trying to index into non-array " + "expression of type "
                + arrType.toString());
      }

      // Depending on the member type of the array, the result type might be
      // the actual member type, or a reference to the member type
      SwiftType resultType;

      resultType = dereferenceResultType(memberType);
      return new ExprType(resultType);
    case ExMParser.ARRAY_RANGE: {
      // Check the arguments for type validity
      ArrayRange ar = ArrayRange.fromAST(context, tree);
      ar.typeCheck(context);
      // Type is always the same: an array of integers
      return new ExprType(new ArrayType(Types.FUTURE_INTEGER));
    }
    case ExMParser.ARRAY_ELEMS: {
      return ArrayElems.fromAST(context, tree).getType(context);
    }
    default:
      throw new STCRuntimeError("Unexpected token type in expression context: "
          + LogHelper.tokName(token));
    }
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
  public static SwiftType findStructFieldType(Context context, String fieldName,
      SwiftType type) throws TypeMismatchException {
    StructType structType;
    if (Types.isStruct(type)) {
      structType = ((StructType) type);
    } else if (Types.isStructRef(type)) {
      structType = ((StructType) (type.getMemberType()));
    } else {
      throw new TypeMismatchException(context, "Trying to access named field "
          + fieldName + " on non-struct expression of type " + type.toString());
    }
    SwiftType fieldType = structType.getFieldTypeByName(fieldName);
    if (fieldType == null) {
      throw new TypeMismatchException(context, "Field named " + fieldName + 
          " does not exist in structure type " + structType.typeName());
    }
    return fieldType;
  }

  /**
   * Same as findExprType, but check that we don't have a multiple
   * value type
   * @param context
   * @param tree
   * @return
   * @throws UserException
   */
  public static SwiftType findSingleExprType(Context context, SwiftAST tree) 
        throws UserException {
    ExprType typeL = findExprType(context, tree);
    if (typeL.elems() != 1) {
      throw new TypeMismatchException(context, "Expected expression to have "
          + " a single value, instead had " + typeL.elems() + " values");
    }
    return typeL.get(0);
  }
  
  private static String extractOpName(SwiftAST opTree) {
    int tok = opTree.child(0).getType();
    try {
      return ExMParser.tokenNames[tok].toLowerCase();
    } catch (IndexOutOfBoundsException ex) {
      throw new STCRuntimeError("Out of range token number: "
          + tok);
    }
  }

  private static ExprType findOperatorResultType(Context context, SwiftAST tree)
      throws TypeMismatchException, UserException {
    String opName = extractOpName(tree);

    ArrayList<SwiftType> argTypes = new ArrayList<SwiftType>();

    List<BuiltinOpcode> opcodes = getBuiltInFromOpTree(context, tree, argTypes);
    assert(opcodes != null);
    assert(opcodes.size() > 0);
    
    // Handle possibility of multiple return types
    List<SwiftType> alternatives = new ArrayList<SwiftType>(opcodes.size());
    for (BuiltinOpcode opcode: opcodes) {
      OpType opType = Operators.getBuiltinOpType(opcode);
      assert(opType != null);
      
      checkOp(context, opName, opType, argTypes);
      alternatives.add(new ScalarFutureType(opType.out));
    }
    return new ExprType(UnionType.createUnionType(alternatives));
  }

  private static void checkOp(Context context, String opName, OpType opType,
      ArrayList<SwiftType> argTypes) throws TypeMismatchException {
    if (opType.in.length != argTypes.size()) {
      throw new STCRuntimeError("Op " +
            opName + " expected "
            + opType.in.length + " arguments but " +
            + argTypes.size() + " were in AST");
    }
 
    // TODO: maybe not needed?  Keep now to find internal errors?
    for (int i = 0; i < argTypes.size(); i++) {
      SwiftType exp = new ScalarFutureType(opType.in[i]);
      SwiftType act = argTypes.get(i);
      if (!act.assignableTo(exp)) {
        throw new TypeMismatchException(context, "Expected type " +
            exp.toString() + " in argument " + i + " to operator " +
            opName + " but found type "
            + act.toString());
 
      }
    }
  }

  public static BuiltinOpcode getBuiltInFromOpTree(Context context,
      SwiftAST tree, SwiftType outType) throws TypeMismatchException, UserException {
    List<BuiltinOpcode> ops = getBuiltInFromOpTree(context, tree, outType, null);
    assert(ops.size() != 0); // Should be caught earlier
    if (ops.size() > 1) {
      LogHelper.debug(context, "Ambiguous operator: " + ops);
    }
    return ops.get(0);
  }
  
  public static List<BuiltinOpcode> getBuiltInFromOpTree(Context context,
      SwiftAST tree, List<SwiftType> argTypes) throws TypeMismatchException, UserException {
    return getBuiltInFromOpTree(context, tree, null, argTypes);
  }

  /**
   *
   * @param context
   * @param tree
   * @param expectedResult
   * @param outType if not null, only return operators with this output type
   * @param argTypes Put the argument types of the operator in this list if not null
   * @return the possible builtin operator codes
   * @throws TypeMismatchException
   * @throws UserException
   */
  private static List<BuiltinOpcode> getBuiltInFromOpTree(Context context, SwiftAST tree,
          SwiftType outType, List<SwiftType> argTypes)
          throws TypeMismatchException, UserException {
    assert(tree.getType() == ExMParser.OPERATOR);
    assert(tree.getChildCount() >= 1);
    int opTok = tree.child(0).getType();
    String opName = extractOpName(tree);
 
    List<SwiftType> possibleAllArgTypes = findOperatorArgTypes(context,
                                        tree, opName, argTypes);
                                                                        
    ArrayList<BuiltinOpcode> ops = new ArrayList<BuiltinOpcode>();
    for (SwiftType allArgType: possibleAllArgTypes) {
      BuiltinOpcode op = Operators.getArithBuiltin(allArgType, opTok);
      if (op != null) {
        if (outType != null) {
          OpType opType = Operators.getBuiltinOpType(op);
          if (new ScalarFutureType(opType.out).equals(outType) ) {
            ops.add(op);
          }
        } else {
          ops.add(op);
        }
      }
    }
    if (ops.isEmpty()) {
      String msg = "Operator " + opName + " not supported for any of these possible " + 
          "input types:" + possibleAllArgTypes.toString();
      if (outType != null) {
        msg += " and output type " + outType;
      }
      throw new UndefinedOperatorException(context,
          msg);
    }
    return ops;
  }


  /**
   * Find the argument type of the operators to a function
   * assuming all operators take all the same argument types.
   * Preference order of types determined by first expression's 
   * union type order
   * @param context
   * @param tree
   * @param opName
   * @param argTypes used to return arg types of function, null for no return
   * @return
   * @throws TypeMismatchException
   * @throws UserException
   */
  private static List<SwiftType> findOperatorArgTypes(Context context, SwiftAST tree,
      String opName, List<SwiftType> argTypes)
          throws TypeMismatchException, UserException {
    
    int argcount = tree.getChildCount() - 1;
    if (argcount == 0) {
      throw new TypeMismatchException(context,
          "provided no arguments to operator " +
          opName);
    }
    if (argTypes == null) {
      argTypes = new ArrayList<SwiftType>(argcount);
    }
    for (SwiftAST argTree: tree.children(1)) {
      argTypes.add(findSingleExprType(context, argTree));
    }

    // We assume that all arguments to operator should have same type.
    return typeIntersection(argTypes);
  }

  /**
   * Find the intersection of a list of types where each element
   * is either a UnionType or a different non-union type
   * @param types
   * @return a list of types in intersection, in order of appearance in
   *         first type
   */
  public static List<SwiftType> typeIntersection(List<SwiftType> types) {
    assert(types.size() > 0);
    
    Set<SwiftType> intersection = null;
    for (SwiftType argType: types) {
      if (Types.isUnion(argType)) {
        List<SwiftType> alts = ((UnionType)argType).getAlternatives();
        if (intersection == null) {
          intersection = new HashSet<SwiftType>();
          intersection.addAll(alts);
        } else {
          intersection.retainAll(alts);
        }
      } else {
        // Shortcircuit: only one possible type
        intersection = Collections.singleton(argType);
      }
    }
    
    // Make sure alternatives in original order
    ArrayList<SwiftType> result = new ArrayList<SwiftType>();
    for (SwiftType alt: UnionType.getAlternatives(types.get(0))) {
      if (intersection.contains(alt)) {
        result.add(alt);
      }
    }
    return result;
  }


  /**
   * The type of the internal variable that will be created from an array
   * dereference
   *
   * @param memberType
   *          the type of array memebrs for the array being dereferenced
   * @return
   */
  private static SwiftType dereferenceResultType(SwiftType memberType) {
    SwiftType resultType;
    if (Types.isScalarFuture(memberType)) {
      resultType = memberType;
    } else if (Types.isArray(memberType) || Types.isStruct(memberType)) {
      resultType = new ReferenceType(memberType);
    } else {
      throw new STCRuntimeError("Unexpected array member type"
          + memberType.toString());
    }
    return resultType;
  }

  /**
   * Check that the variables in vars have the exact types of types. Throw an
   * exception otherwise
   *
   * @param types
   * @param vars
   * @throws TypeMismatchException
   */
  private static void typeCheckIdentical(Context context, List<SwiftType> types,
      List<Variable> vars, String errContext) throws TypeMismatchException {
    if (types.size() != vars.size()) {
      throw new TypeMismatchException(context, "Number of variables "
          + vars.size() + " does not match expected number " + types.size()
          + errContext);
    }
    for (int i = 0; i < types.size(); i++) {
      if (!vars.get(i).getType().equals(types.get(i))) {
        throw argumentTypeException(context, i, 
            types.get(i), vars.get(i).getType(), errContext);
      }
    }

  }

  private static TypeMismatchException argumentTypeException(Context context,
      int argPos, SwiftType expType, SwiftType actType, String errContext) {
    return new TypeMismatchException(context, "Expected argument " +
        (argPos + 1) + " to have one of the following types: " 
        + expType.typeName() + ", but had type: " + actType.typeName() 
        + errContext);
  }


  private static void checkFunctionOutputs(Context context, List<SwiftType> types,
      List<Variable> outputs, Map<String, SwiftType> typeVarBindings,
      String errContext) throws TypeMismatchException {
    // Type system is simple enough that we just check the types match exactly
    typeCheckIdentical(context, types, outputs, errContext);
  }


  /**
   * Check input types for the function.  In order to pass, we don't require
   * that the types exactly match, only that the compiler knows how to
   * transform them into the types required by funArgSpec
   * @param context
   * @param types
   * @param vars
   * @param errContext
   * @return list of which of the alternative will be passed into fn
   * @throws TypeMismatchException
   */
  private static List<SwiftType> typeCheckFunargs(Context context, List<SwiftType> funArgSpec,
      List<Variable> vars, String errContext) throws TypeMismatchException {
    if (funArgSpec.size() != vars.size()) {
      throw new TypeMismatchException(context, "Number of variables "
          + vars.size() + " does not match expected number " +
          funArgSpec.size() + errContext);
    }
    ArrayList<SwiftType> concreteTypes =
            new ArrayList<SwiftType>(funArgSpec.size());
    for (int i = 0; i < funArgSpec.size(); i++) {
      SwiftType varType= vars.get(i).getType();
      SwiftType funArgType = funArgSpec.get(i);
      concreteTypes.add(checkFunArg(context, errContext, i, funArgType,
                        varType).val1);
    }
    return concreteTypes;
  }

  public static Pair<SwiftType, SwiftType> checkFunArg(Context context, String errContext,
      int argNum, SwiftType argDeclaredType, SwiftType argExprType)
      throws TypeMismatchException {
    Pair<SwiftType, SwiftType> whichAlt = whichAlternativeType(argDeclaredType, argExprType);
    if (whichAlt == null && Types.isScalarUpdateable(argExprType)) {
      SwiftType futureExprType = new ScalarFutureType(argExprType.getPrimitiveType());
      whichAlt = whichAlternativeType(argDeclaredType, futureExprType);
    }
    if (whichAlt == null) {
      throw argumentTypeException(context, argNum, argDeclaredType, argExprType, 
                                                                     errContext);
    }
    return whichAlt;
  }

  /**
   * 
   * @param funArgType
   * @param varType
   * @return (selected type of argument, selected type of variable)
   */
  public static Pair<SwiftType, SwiftType> 
        whichAlternativeType(SwiftType funArgType, SwiftType varType) {
    /*
     * Handles cases where both function formal argument type and expression type
     * are union types.  In case of multiple possibilities prefers picking first
     * alternative in expression type, followed by first formal argument alternative
     */
    List<SwiftType> varTypes;
    if (Types.isUnion(varType)) {
      varTypes = ((UnionType)varType).getAlternatives();
    } else {
      varTypes = Collections.singletonList(varType);
    }
    
    for (SwiftType varTypeChoice: varTypes) {
      // Handle if argument type is union.
      Collection<SwiftType> alts;
      if (Types.isUnion(funArgType)) {
        alts = ((UnionType)funArgType).getAlternatives();
      } else {
        alts = Collections.singleton(funArgType);
      }
      for (SwiftType alt: alts) {
        if (compatibleArgTypes(alt, varTypeChoice)) {
          return Pair.create(alt, varTypeChoice);
        }
      }
    }
    return null; // if no alternatives
  }

  /**
   * Check if an expression type can be used for function argument
   * @param argType non-polymorphic function argument type
   * @param exprType type of argument expression
   * @return true if compatible
   */
  public static boolean compatibleArgTypes(SwiftType argType,
      SwiftType exprType) {
    if (exprType.equals(argType)) {
      // Obviously ok if types are exactly the same
      return true;
    } else if (Types.isReferenceTo(exprType, argType)) {
      // We can block on reference, so we can transform type here
      return true;
    } else {
      return false;
    }
  }


  /**
   * Returns a list of the types the function arguments need
   * to be transformed into
   * @param context
   * @param ftype
   * @param inputs
   * @param typeVarBindings 
   * @param errContext
   * @throws TypeMismatchException
   */
  private static List<SwiftType> checkFunctionInputs(Context context, FunctionType ftype,
      List<Variable> inputs, Map<String, SwiftType> typeVarBindings,
      String errContext) throws TypeMismatchException {
    List<SwiftType> types = ftype.getInputs();
    if (!ftype.hasVarargs()) {
      List<SwiftType> concrete = typeCheckFunargs(context, types, inputs, errContext);
      return Collections.unmodifiableList(concrete);
    } else {
      List<Variable> nonVariadicInputs = new ArrayList<Variable>();
      List<SwiftType> nonVariadicTypes = new ArrayList<SwiftType>();
      for (int i = 0; i < types.size() - 1; i++) {
        nonVariadicTypes.add(types.get(i));
      }
      for (int i = 0; i < types.size() - 1 && i < inputs.size(); i++) {
        nonVariadicInputs.add(inputs.get(i));
      }

      SwiftType variadicType = types.get(types.size() - 1);
      List<SwiftType> concreteNonvariadic =
            typeCheckFunargs(context, nonVariadicTypes, nonVariadicInputs, errContext);
      List<SwiftType> concreteTypeList =
                    new ArrayList<SwiftType>(concreteNonvariadic);
      for (int i = types.size() - 1; i < inputs.size(); i++) {
        SwiftType argT = inputs.get(i).getType();
        SwiftType whichAlt = checkFunArg(context, errContext, i, variadicType,
            argT).val2;
        concreteTypeList.add(whichAlt);
      }
      return Collections.unmodifiableList(concreteTypeList);
    }
  }

  /**
   * Check that a function call is valid
   * @param context the current context (to look up function type)
   * @param function the function name
   * @param oList list of variables function output is going into
   * @param iList list of variables used as input arguments
   * @return concrete list of the types expected to be passed into function
   * @throws TypeMismatchException
   * @throws UndefinedFunctionException 
   */
  public static List<SwiftType> checkFunctionCall(Context context, String function,
      List<Variable> oList, List<Variable> iList) 
          throws TypeMismatchException,UndefinedFunctionException {
    FunctionType ftype = context.lookupFunction(function);
    //TODO: auto-convert int lit args to float lit args (this requires
    //      changes in called)
    if (ftype == null) {
      throw UndefinedFunctionException.unknownFunction(context, function);
    }
    Map<String, SwiftType> typeVarBindings = typeVarBindings(ftype);
    checkFunctionOutputs(context, ftype.getOutputs(), oList, typeVarBindings,
          " in returns for call to function " + function);
    return checkFunctionInputs(context, ftype, iList, typeVarBindings,
          " in arguments for " + "call to function " + function);
  }


  public static void checkCopy(Context context, SwiftType srctype, SwiftType dsttype)
      throws TypeMismatchException {
    if (!srctype.equals(dsttype)) {
      throw new TypeMismatchException(context, "Type mismatch: copying from "
          + srctype.toString() + " to " + dsttype.toString());
    }
  }

  /**
   * Checks whether rValType can be assigned to lValType
   * @param context
   * @param rValType
   * @param lValType
   * @param lValName
   * @return returns rValType, or if rValType is a union, return chosen 
   *          member of union
   * @throws TypeMismatchException
   */
  public static SwiftType checkAssignment(Context context, SwiftType rValType,
      SwiftType lValType, String lValName) throws TypeMismatchException {
    for (SwiftType altRValType: UnionType.getAlternatives(rValType)) {
      if (lValType.equals(altRValType)
          || Types.isReferenceTo(altRValType, lValType)
          || Types.isReferenceTo(lValType, altRValType)) {
        return altRValType;
      }
    }
    throw new TypeMismatchException(context, "Cannot assign to "
        + lValName + ": LVal has type "
        + lValType.toString() + " but RVal type " + rValType.toString()
        + " was expected");
  }

  /**
   * Create dictionary with null type var bindings
   * @param ftype
   * @return
   */
  public static Map<String, SwiftType> typeVarBindings(FunctionType ftype) {
    Map<String, SwiftType> typeVarBindings = new HashMap<String, SwiftType>();
    for (String typeVar: ftype.getTypeVars()) {
      typeVarBindings.put(typeVar, null);
    }
    return typeVarBindings;
  }

}
