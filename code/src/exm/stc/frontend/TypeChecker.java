package exm.stc.frontend;

import java.util.ArrayList;
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
import exm.stc.ast.descriptor.FunctionCall;
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
import exm.stc.common.util.MultiMap;
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
    case ExMParser.CALL_FUNCTION: {
      return callFunction(context, tree);
    }
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
  
  private static ExprType callFunction(Context context, SwiftAST tree)
      throws UndefinedFunctionException, UserException {
    FunctionCall f = FunctionCall.fromAST(context, tree);
    List<FunctionType> alts = concretiseFunctionCall(context, 
              f.function(), f.type(), f.args());
    if (alts.size() == 1) {
      // Function type determined entirely by input type
      return new ExprType(alts.get(0).getOutputs());
    } else {
      // Ambiguous type variable binding (based on inputs)
      assert(alts.size() > 2);
      int numOutputs = f.type().getOutputs().size(); 
      if (numOutputs == 0) {
        return new ExprType(Collections.<SwiftType>emptyList());
      } else {
        // Turn into a list of UnionTypes
        List<SwiftType> altOutputs = new ArrayList<SwiftType>();
        for (int out = 0; out < numOutputs; out++) {
          List<SwiftType> altOutput = new ArrayList<SwiftType>();
          for (FunctionType ft: alts) {
            altOutputs.add(ft.getOutputs().get(out));
          }
          altOutputs.add(UnionType.createUnionType(altOutput));
        }
        return new ExprType(altOutputs);
      }
    }
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
    // Shortcircuit common cases
    if (types.size() == 1 ||
        (types.size() == 2 && types.get(0).equals(types.get(1)))) {
      return UnionType.getAlternatives(types.get(0));
    }
    
    Set<SwiftType> intersection = null;
    for (SwiftType argType: types) {
      if (intersection == null) {
        intersection = new HashSet<SwiftType>();
        intersection.addAll(UnionType.getAlternatives(argType));
      } else {
        intersection.retainAll(UnionType.getAlternatives(argType));
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

  private static TypeMismatchException argumentTypeException(Context context,
      int argPos, SwiftType expType, SwiftType actType, String errContext) {
    return new TypeMismatchException(context, "Expected argument " +
        (argPos + 1) + " to have one of the following types: " 
        + expType.typeName() + ", but had type: " + actType.typeName() 
        + errContext);
  }

  public static Pair<SwiftType, SwiftType> checkFunArg(Context context,
      String function, int argNum, SwiftType argDeclaredType,
      SwiftType argExprType) throws TypeMismatchException {
    Pair<SwiftType, SwiftType> whichAlt = whichAlternativeType(argDeclaredType, argExprType);
    if (whichAlt == null && Types.isScalarUpdateable(argExprType)) {
      SwiftType futureExprType = new ScalarFutureType(argExprType.getPrimitiveType());
      whichAlt = whichAlternativeType(argDeclaredType, futureExprType);
    }
    if (whichAlt == null) {
      throw argumentTypeException(context, argNum, argDeclaredType, argExprType, 
                                             " in call to function " + function);
    }
    return whichAlt;
  }

  /**
   * 
   * @param formalArgType
   * @param argExprType
   * @return (selected type of argument, selected type of variable)
   */
  public static Pair<SwiftType, SwiftType> 
        whichAlternativeType(SwiftType formalArgType, SwiftType argExprType) {
    /*
     * Handles cases where both function formal argument type and expression type
     * are union types.  In case of multiple possibilities prefers picking first
     * alternative in expression type, followed by first formal argument alternative
     */
    for (SwiftType argExprAlt: UnionType.getAlternatives(argExprType)) {
      // Handle if argument type is union.
      for (SwiftType formalArgAlt: UnionType.getAlternatives(formalArgType)) {
        if (compatibleArgTypes(formalArgAlt, argExprAlt)) {
          return Pair.create(formalArgAlt, argExprAlt);
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
  
  public static FunctionType concretiseFunctionCall(Context context,
      String function, FunctionType abstractType, List<SwiftAST> args,
      List<Variable> outputs) throws UserException {
    List<SwiftType> outTs = new ArrayList<SwiftType>(outputs.size());
    for (Variable output: outputs) {
      outTs.add(output.getType());
    }
    List<FunctionType> alts = concretiseFunctionCall(context, function,
                                                    abstractType, args);
    assert(alts.size() > 0);
    for (FunctionType alt: alts) {
      assert(alt.getOutputs().size() == outputs.size());
      boolean match = true;
      for (int i = 0; i < outTs.size(); i++) {
        if (!alt.getOutputs().get(i).equals(outTs.get(i))) {
          match = false;
          break;
        }
      }
      // Choose first viable alternative
      if (match) {
        return alt;
      }
    }
    throw new TypeMismatchException(context, "Could not find consistent " +
    		" binding for type variables.  Viable function signatures based on " +
    		" input arguments were: " + alts + " but output types were " + outTs);
  }

  /**
   * @param context
   * @param abstractType function type with varargs, typevars, union types
   * @param args input argument expressions
   * @return list of possible concrete function types with varargs, typevars
   *          and union type args removed
   * @throws UserException 
   */
  public static List<FunctionType> concretiseFunctionCall(Context context,
          String function, FunctionType abstractType,
          List<SwiftAST> args) throws UserException {
    List<SwiftType> argTypes = new ArrayList<SwiftType>(args.size());
    for (SwiftAST arg: args) {
      argTypes.add(findSingleExprType(context, arg));
    }
    
    // Expand varargs
    List<SwiftType> expandedInputs = expandVarargs(context, abstractType,
                              function, args.size());
    
    // Narrow down possible bindings - choose union types
    // find possible typevar bindings
    List<SwiftType> specificInputs = new ArrayList<SwiftType>(args.size());
    MultiMap<String, SwiftType> possibleBindings =
                                new MultiMap<String, SwiftType>(); 
    for (int i = 0; i < args.size(); i++) {
      SwiftType expected = expandedInputs.get(i);
      SwiftType actual = argTypes.get(i);
      // TODO: change to method capable of matching up typevars to concrete
      // types
      Pair<SwiftType, SwiftType> t = 
            checkFunArg(context, function, i, expected, actual);
      specificInputs.add(t.val1);
    }
    
    MultiMap<String, SwiftType> binding = unifyTypeVarBindings(context,
              function, abstractType.getTypeVars(), possibleBindings);
    
    List<FunctionType> possibilities = findPossibleFunctionTypes(context,
        function, abstractType, specificInputs, binding);

    if (possibilities.size() == 0) {
      throw new TypeMismatchException(context, "Arguments for call to " +
          "function " + function + " were incompatible with function " +
          "type.  Function input types were: " + abstractType.getInputs() +
          ", argument types were " + argTypes);
    }
    
    return possibilities;
  }

  private static List<FunctionType> findPossibleFunctionTypes(Context context,
      String function, FunctionType abstractType,
      List<SwiftType> specificInputs, MultiMap<String, SwiftType> binding)
      throws TypeMismatchException {
    List<String> typeVars = abstractType.getTypeVars();
    
    int currChoices[] = new int[typeVars.size()]; // initialized to zero
    
    List<FunctionType> possibilities = new ArrayList<FunctionType>();
    while (true) {
      Map<String, SwiftType> currBinding = new HashMap<String, SwiftType>();
      for (int i = 0; i < currChoices.length; i++) {
        String tv = typeVars.get(i);
        currBinding.put(tv, binding.get(tv).get(currChoices[i]));
      }
      
      possibilities.add(constructFunctionType(abstractType, specificInputs,
                        currBinding));
      
      int pos = currChoices.length - 1;
      while (pos >= 0 &&
            currChoices[pos] >= binding.get(typeVars.get(pos)).size() - 1) {
        pos--;
      }
      if (pos < 0) {
        break;
      } else {
        currChoices[pos]++;
        for (int i = pos + 1; i < currChoices.length; i++) {
          currChoices[i] = 0;
        }
      }
    }
    return possibilities;
  }

  private static FunctionType constructFunctionType(FunctionType abstractType,
      List<SwiftType> inputs, Map<String, SwiftType> binding) {
    List<SwiftType> concreteInputs = bindTypeVariables(inputs, binding);
    List<SwiftType> concreteOutputs = bindTypeVariables(
                                    abstractType.getOutputs(), binding);
    return new FunctionType(concreteInputs, concreteOutputs, false);
  }

  private static List<SwiftType> expandVarargs(Context context, 
       FunctionType abstractType, String function, int numArgs)
           throws TypeMismatchException {
    List<SwiftType> abstractInputs = abstractType.getInputs();
    if (abstractType.hasVarargs()) {
      if (numArgs < abstractInputs.size() - 1) {
        throw new TypeMismatchException(context,  "Too few arguments in "
            + " call to function " + function + ": expected >= " 
            + (abstractInputs.size() - 1) + " but got " + numArgs);
      }
    } else if (abstractInputs.size() != numArgs) {
      throw new TypeMismatchException(context,  "Wrong number of arguments in "
          + " call to function " + function + ": expected " 
          + abstractInputs.size() + " but got " + numArgs);
    }
    
    if (abstractType.hasVarargs()) {
      List<SwiftType> expandedInputs;
      expandedInputs = new ArrayList<SwiftType>();
      expandedInputs.addAll(abstractInputs.subList(0,
                            abstractInputs.size() - 1));
      SwiftType varArgType = abstractInputs.get(abstractInputs.size() - 1);
      for (int i = abstractInputs.size() - 1; i < numArgs; i++) { 
        expandedInputs.add(varArgType);
      }
      return expandedInputs;
    } else {
      return abstractInputs;
    }
  }

  private static List<SwiftType> bindTypeVariables(List<SwiftType> types,
          Map<String, SwiftType> binding) {
    List<SwiftType> res = new ArrayList<SwiftType>(types.size());
    for (SwiftType type: types) {
      res.add(type.bindTypeVars(binding));
    }
    return res;
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
        + lValType.toString() + " but RVal has type " + rValType.toString());
  }

  /**
   * Create dictionary with null type var bindings
   * @param ftype
   * @return
   */
  public static MultiMap<String, SwiftType> typeVarBindings(FunctionType ftype) {
    return new MultiMap<String, SwiftType>();
  }
  
  /**
   * 
   * @param candidates MultiMap, with possible bindings for each type variable
   * @return
   * @throws TypeMismatchException
   */
  public static MultiMap<String, SwiftType> unifyTypeVarBindings(
      Context context, String function, List<String> typeVars,
      MultiMap<String, SwiftType> candidates)
      throws TypeMismatchException {
    MultiMap<String, SwiftType> narrowed = new MultiMap<String, SwiftType>();
    /* Check whether type variables were left unbound */
    for (String typeVar: typeVars) {
      List<SwiftType> cands = candidates.get(typeVar);
      if (cands == null || cands.size() == 0) {
        LogHelper.warn(context, "Type variable " + typeVar + " for call to " +
        		"function " + function + " was unbound");
      } else {
        List<SwiftType> intersection = typeIntersection(cands);
        if (intersection.size() == 0) {
          throw new TypeMismatchException(context, 
              "Type variable " + typeVar + " for call to function " +
              function + " could not be bound: no consistent type between " +
              " types: " + candidates);
        }
        for (SwiftType t: intersection) {
          narrowed.put(typeVar, t);
        }
      }
    }
    return narrowed;
  }
}
