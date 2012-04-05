package exm.ast;

import java.util.*;

import org.apache.log4j.Logger;

import exm.ast.Types.ArrayType;
import exm.ast.Types.FunctionType;
import exm.ast.Types.FunctionType.InArgT;
import exm.ast.Types.PrimType;
import exm.ast.Types.ReferenceType;
import exm.ast.Types.ScalarFutureType;
import exm.ast.Types.ScalarUpdateableType;
import exm.ast.Types.StructType;
import exm.ast.Types.SwiftType;
import exm.ast.descriptor.ArrayElems;
import exm.ast.descriptor.ArrayRange;
import exm.parser.antlr.ExMParser;
import exm.parser.util.*;

/**
 * This module handles checking the internal consistency of expressions,
 * and inferring the types of expressions in the SwiftScript AST
 */
public class TypeChecker {

  private final Logger logger;

  public TypeChecker(Logger logger) {
    this.logger = logger;
  }


  public List<SwiftType> findExprType(Context context, SwiftAST tree) 
      throws UserException {
    return findExprType(context, tree, null);
  }
      
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
  public List<SwiftType> findExprType(Context context, SwiftAST tree, 
      List<SwiftType> expected)
      throws UserException {
    // Memoize this function to avoid recalculating type
    List<SwiftType> cached = tree.getSwiftType();
    if (cached != null) {
      logger.trace("Expr at l." + tree.getLine() + "." + tree.getCharPositionInLine()
          + " has cached type " + cached.toString());
      return cached;
    } else {
      List<SwiftType> calcedType = uncachedFindExprType(context, tree,
                                                                 expected);
      tree.setSwiftType(calcedType);
      logger.trace("Expr at l." + tree.getLine() + "." + tree.getCharPositionInLine()
          + " found type " + calcedType.toString());
      return calcedType;
    }
  }
  
  private List<SwiftType> uncachedFindExprType(Context context, SwiftAST tree,
      List<SwiftType> expected) throws UserException {
    int token = tree.getType();
    switch (token) {
    case ExMParser.CALL_FUNCTION:
      String function = tree.child(0).getText();
      FunctionType ftype = context.lookupFunction(function);
      if (ftype == null) {
        throw UndefinedFunctionException.unknownFunction(context, function);
      }
      return ftype.getOutputs();
    case ExMParser.VARIABLE: {
      Variable var = context.getDeclaredVariable(tree.child(0).getText());
      if (var == null) {
        throw new UndefinedVariableException(context, "Variable "
            + tree.child(0).getText() + " is not defined");
      }
      SwiftType exprType = var.getType();
      if (Types.isScalarUpdateable(exprType)) {
        if (expected != null &&
            expected.size() == 1 && Types.isScalarUpdateable(expected.get(0))) {
          // Keep type as updateable
        } else {
          // by default, coerce to future
          exprType = ScalarUpdateableType.asScalarFuture(exprType);
        }
      }
      return Arrays.asList(exprType);
    }
    case ExMParser.INT_LITERAL:
      if (expected != null && expected.size() == 1 && 
              expected.get(0).equals(Types.FUTURE_FLOAT)) {
        // interpret as float
        return Arrays.asList(Types.FUTURE_FLOAT);
      } else {
        return Arrays.asList(Types.FUTURE_INTEGER);
      }
    case ExMParser.FLOAT_LITERAL:
      return Arrays.asList(Types.FUTURE_FLOAT);
    case ExMParser.STRING_LITERAL:
      return Arrays.asList(Types.FUTURE_STRING);
    case ExMParser.BOOL_LITERAL:
      return Arrays.asList(Types.FUTURE_BOOLEAN);
    case ExMParser.OPERATOR:
      return findOperatorResultType(context, tree, expected);
    case ExMParser.STRUCT_LOAD:
      List<SwiftType> structTypeL = findExprType(context, tree.child(0));
      String fieldName = tree.child(1).getText();
      if (structTypeL.size() != 1) {
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
        return Arrays.asList(fieldType);
      } else { assert(Types.isStructRef(structType));
        return Arrays.asList(dereferenceResultType(fieldType));
      }

    case ExMParser.ARRAY_LOAD:
      List<SwiftType> arrTypeL = findExprType(context, tree.child(0));
      if (arrTypeL.size() != 1) {
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
      return Arrays.asList(resultType);
    case ExMParser.ARRAY_RANGE: {
      // Check the arguments for type validity
      ArrayRange ar = ArrayRange.fromAST(context, tree);
      for (SwiftAST child: ar.getArgs()) {
        SwiftType t = findSingleExprType(context, child);
        if (!Types.isInt(t)) {
          throw new TypeMismatchException(context, "Argument to " +
          		"array range operator must be an integer, but was " + 
              t.typeName()); 
        }
      }
      // Type is always the same: an array of integers
      return Arrays.asList((SwiftType)new ArrayType(Types.FUTURE_INTEGER));
    }
    case ExMParser.ARRAY_ELEMS: {
      // Check to see all arguments have same type
      ArrayElems ae = ArrayElems.fromAST(context, tree);
      List<SwiftAST> members = ae.getMembers();
      if (members.size() == 0) {
        throw new ParserRuntimeException("Empty array constructor, " +
        		"compiler doesn't yet know how to infer type");
      } else {
        SwiftAST first = members.get(0);
        SwiftType firstElemType = findSingleExprType(context, first);
        for (SwiftAST elem: members.subList(1, members.size())) {
          SwiftType elemType = findSingleExprType(context, elem);
          if (!elemType.equals(firstElemType)) {
            throw new TypeMismatchException(context, "Elements in array" +
            		" constructor have non-matching types: " + firstElemType
            		+ " and " + elemType);
          }
        }
        return Arrays.asList((SwiftType)new ArrayType(firstElemType));
      }
    }
    default:
      throw new ParserRuntimeException(
          "Unknown token type in expression context: " + token);
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
  public SwiftType findStructFieldType(Context context, String fieldName,
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


  
  public SwiftType findSingleExprType(Context context, SwiftAST tree)
                throws UserException {
    return findSingleExprType(context, tree, null);
  }
  /**
   * Same as findExprType, but check that we don't have a multiple
   * value type
   * @param context
   * @param tree
   * @return
   * @throws UserException
   */
  public SwiftType findSingleExprType(Context context, SwiftAST tree, 
      SwiftType expType) throws UserException {
    List<SwiftType> typeL = findExprType(context, tree,
                           expType == null ? null : Arrays.asList(expType));
    if (typeL.size() != 1) {
      throw new TypeMismatchException(context, "Expected expression to have "
          + " a single value, instead had " + typeL.size() + " values");
    }
    return typeL.get(0);
  }

  private List<SwiftType> findOperatorResultType(Context context, SwiftAST tree,
      List<SwiftType> expected) throws TypeMismatchException, UserException {
    int opType = tree.child(0).getType();
    String opName = ExMParser.tokenNames[opType].toLowerCase();

    ArrayList<SwiftType> argTypes = new ArrayList<SwiftType>();

    String fnName = getBuiltInFromOpTree(context, tree, argTypes, expected);
    assert(fnName != null);

    FunctionType opFnType = Builtins.getBuiltinType(fnName);

    if (opFnType == null) {
      throw new UserException(context, "built-in function " + fnName
          + " did not have a type that we could find.  You probably forgot" +
          " to include builtins.swift or are using an old turbine version" +
          " that doesn't have this function");
    }

    if (opFnType.getInputs().size() != argTypes.size()) {
      if (opFnType.hasVarargs() && 
            opFnType.getInputs().size() <= argTypes.size() - 1) {
        // this is ok
      } else {
        throw new TypeMismatchException(context, "Op " +
            opName + " expected "
            + opFnType.getInputs().size() + " arguments but " +
            + argTypes.size() + " were given");
      }
    }


    int nInTypes = opFnType.getInputs().size() - 1;
    for (int i = 0; i < argTypes.size(); i++) {
      InArgT exp = opFnType.getInputs().get(Math.min(i, nInTypes));
      if (exp.getAlternatives().length != 1) {
        throw new ParserRuntimeException("Builtin operator "
            + fnName + " should not have polymorphic type for input " +
                "argument: " + exp.toString());
      }
      SwiftType exp2 = exp.getAlternatives()[0];
      SwiftType act = argTypes.get(i);
      if (!exp2.equals(act)) {
        throw new TypeMismatchException(context, "Expected type " +
            exp2.toString() + " in argument " + i + " to operator " +
            opName + " but found type "
            + act.toString());

      }
    }

    return opFnType.getOutputs();
  }


  public String getBuiltInFromOpTree(Context context, SwiftAST tree)
        throws TypeMismatchException, UserException {
    return getBuiltInFromOpTree(context, tree, new ArrayList<SwiftType>(),
        null);
  }

  /**
   *
   * @param context
   * @param tree
   * @param argTypes Put the argument types of the operator in this list
   * @param expectedResult 
   * @return the builtin function name that implements the operator
   * @throws TypeMismatchException
   * @throws UserException
   */
  private String getBuiltInFromOpTree(Context context, SwiftAST tree,
      ArrayList<SwiftType> argTypes, List<SwiftType> expectedResult)
          throws TypeMismatchException,
      UserException {
    assert(tree.getType() == ExMParser.OPERATOR);
    assert(tree.getChildCount() >= 1);
    int opType = tree.child(0).getType();
    String opName = ExMParser.tokenNames[opType].toLowerCase();
 
    PrimType allArgType;
    if (opType == ExMParser.NEGATE && expectedResult != null && 
          expectedResult.size() == 1 
          && Types.isScalarFuture(expectedResult.get(0))) {
      // TODO: special case so that negative literals work as expected
      //      until we have more general handling of expected types
      allArgType = findOperatorArgTypes(context, tree, argTypes, opName,
              expectedResult.get(0).getPrimitiveType());
    } else {
      allArgType = findOperatorArgTypes(context, tree, argTypes, opName,
                                                                    null);
    }
                                                                        

    String fnName = Builtins.getArithBuiltin(allArgType, opType);
    if (fnName == null) {
      throw new UndefinedOperatorException(context,
          "Operator " + opName + " applied to arguments of type " +
              allArgType.toString().toLowerCase() + " is not supported");
    }
    return fnName;
  }


  /**
   * Find the argument type of the operators to a function
   * assuming all operators take all the same argument types
   * @param context
   * @param tree
   * @param argTypes
   * @param opName
   * @param coerceTo
   * @return
   * @throws TypeMismatchException
   * @throws UserException
   */
  private PrimType findOperatorArgTypes(Context context, SwiftAST tree,
      ArrayList<SwiftType> argTypes, String opName, PrimType coerceTo)
      throws TypeMismatchException, UserException {
    
    int argcount = tree.getChildCount() - 1;
    if (argcount == 0) {
      throw new TypeMismatchException(context,
          "provided no arguments to operator " +
          opName);
    }

    /* We assume that all arguments to operator should have same type.
     * The exception is if we can coerce one type to another: 
     * in this case, from int to float
     */
    PrimType allArgType = coerceTo;
    SwiftType desiredType = coerceTo == null ? 
                      null : new ScalarFutureType(coerceTo);
    for (int i = 0; i < argcount; i++) {
      if (coerceTo != null) {
        tree.child(i+1).clearTypeInfo();
      }
      SwiftType t = findSingleExprType(context, tree.child(i+1), desiredType);
      if(Types.isScalarUpdateable(t)) {
        //TODO: workaround to treat updateables as regular futures
        t = ScalarUpdateableType.asScalarFuture(t);
      } else if (!Types.isScalarFuture(t)) {
        throw new TypeMismatchException(context,
            "Non-scalar argument of type " + t.toString() + " " +
            		"was used as an argument to operator "
            + opName);
      }
      PrimType primT = t.getPrimitiveType();
      if (allArgType != null && allArgType != primT) {
        if (coerceTo == null && 
              (allArgType == PrimType.FLOAT && primT == PrimType.INTEGER)
            ||(allArgType == PrimType.INTEGER && primT == PrimType.FLOAT)) {
          // Try again with coercion.
          argTypes.clear();
          return findOperatorArgTypes(context, tree, argTypes, opName,
                                                        PrimType.FLOAT);
        } else {
          throw new TypeMismatchException(context,
            "Argument of different types " + primT.toString().toLowerCase() +
            " and " + allArgType.toString().toLowerCase() + " were used as "
            + " arguments to operator " + opName + " and could not be" +
            		" coerced to same type safely");
        }
      } else if (allArgType == null) {
        allArgType = primT;
        desiredType = new ScalarFutureType(primT);
      }
      argTypes.add(t);
    }
    return allArgType;
  }


  /**
   * The type of the internal variable that will be created from an array
   * dereference
   *
   * @param memberType
   *          the type of array memebrs for the array being dereferenced
   * @return
   */
  private SwiftType dereferenceResultType(SwiftType memberType) {
    SwiftType resultType;
    if (Types.isScalarFuture(memberType)) {
      resultType = memberType;
    } else if (Types.isArray(memberType) || Types.isStruct(memberType)) {
      resultType = new ReferenceType(memberType);
    } else {
      throw new ParserRuntimeException("Unexpected array member type"
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
  private void typeCheckIdentical(Context context, List<SwiftType> types,
      List<Variable> vars, String errContext) throws TypeMismatchException {
    if (types.size() != vars.size()) {
      throw new TypeMismatchException(context, "Number of variables "
          + vars.size() + " does not match expected number " + types.size()
          + errContext);
    }
    for (int i = 0; i < types.size(); i++) {
      if (!vars.get(i).getType().equals(types.get(i))) {
        throw argumentTypeException(context, i, 
            InArgT.fromSwiftT(types.get(i)), vars.get(i).getType(), 
                                                       errContext);
      }
    }

  }

  private TypeMismatchException argumentTypeException(Context context,
      int argPos, InArgT expType, SwiftType actType, String errContext) {
    return new TypeMismatchException(context, "Expected argument " +
        (argPos + 1) + " to have one of the follow types: " 
        + expType.typeName() + ", but had type: " + actType.typeName() 
        + errContext);
  }


  private void checkFunctionOutputs(Context context, List<SwiftType> types,
      List<Variable> outputs, String errContext) throws TypeMismatchException {
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
  private List<SwiftType> typeCheckFunargs(Context context, List<InArgT> funArgSpec,
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
      InArgT funArgType = funArgSpec.get(i);

      SwiftType whichAlt = whichAlternativeType(funArgType, varType);
      if (whichAlt == null) {
        throw argumentTypeException(context, i, funArgType, varType, 
                                              errContext);
      }
      concreteTypes.add(whichAlt);
    }
    return concreteTypes;
  }

  public SwiftType whichAlternativeType(InArgT funArgType, SwiftType varType) {
    for (SwiftType alt: funArgType.getAlternatives()) {
      if (varType.equals(alt)) {
        // Obviously ok if types are exactly the same
        return alt;
      } else if (Types.isReferenceTo(varType, alt)) {
        // We can block on reference, so we can transform type here
        return alt;
      } else if (Types.isUpdateableEquiv(varType, alt)) {
        return alt;
      }
    }
    return null; // if no alternatives
  }


  /**
   * Returns a list of the types the function arguments need
   * to be transformed into
   * @param context
   * @param ftype
   * @param inputs
   * @param errContext
   * @throws TypeMismatchException
   */
  private List<SwiftType> checkFunctionInputs(Context context, FunctionType ftype,
      List<Variable> inputs, String errContext) throws TypeMismatchException {
    List<InArgT> types = ftype.getInputs();
    if (!ftype.hasVarargs()) {
      List<SwiftType> concrete = typeCheckFunargs(context, types, inputs, errContext);
      return Collections.unmodifiableList(concrete);
    } else {
      List<Variable> nonVariadicInputs = new ArrayList<Variable>();
      List<InArgT> nonVariadicTypes = new ArrayList<InArgT>();
      for (int i = 0; i < types.size() - 1; i++) {
        nonVariadicTypes.add(types.get(i));
      }
      for (int i = 0; i < types.size() - 1 && i < inputs.size(); i++) {
        nonVariadicInputs.add(inputs.get(i));
      }

      InArgT variadicType = types.get(types.size() - 1);
      List<SwiftType> concreteNonvariadic =
            typeCheckFunargs(context, nonVariadicTypes, nonVariadicInputs, errContext);
      List<SwiftType> concreteTypeList =
                    new ArrayList<SwiftType>(concreteNonvariadic);
      for (int i = types.size() - 1; i < inputs.size(); i++) {
        SwiftType argT = inputs.get(i).getType();
        SwiftType whichAlt = whichAlternativeType(variadicType, argT);

        if (whichAlt == null) {
          throw argumentTypeException(context, i, variadicType, argT, errContext);
        }
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
  public List<SwiftType> checkFunctionCall(Context context, String function,
      List<Variable> oList, List<Variable> iList) 
          throws TypeMismatchException,UndefinedFunctionException {
    FunctionType ftype = context.lookupFunction(function);
    //TODO: auto-convert int lit args to float lit args (this requires
    //      changes in called)
    if (ftype == null) {
      throw UndefinedFunctionException.unknownFunction(context, function);
    }
    checkFunctionOutputs(context, ftype.getOutputs(), oList,
          " in returns for call to function " + function);
    return checkFunctionInputs(context, ftype, iList, " in arguments for "
          + "call to function " + function);
  }


  public void checkCopy(Context context, SwiftType srctype, SwiftType dsttype)
      throws TypeMismatchException {
    if (!srctype.equals(dsttype)) {
      throw new TypeMismatchException(context, "Type mismatch: copying from "
          + srctype.toString() + " to " + dsttype.toString());
    }
  }


  public void checkAssignment(Context context, SwiftType rValType,
      SwiftType lValType, String lValName) throws TypeMismatchException {
    if (!lValType.equals(rValType)
        && !(Types.isReference(rValType) && lValType.equals(rValType
            .getMemberType()))
        && !(Types.isReference(lValType) && lValType.getMemberType()
            .equals(rValType))) {
      throw new TypeMismatchException(context, "Cannot assign to "
          + lValName + ": LVal has type "
          + lValType.toString() + " but RVal type " + rValType.toString()
          + " was expected");
    }
  }

}
