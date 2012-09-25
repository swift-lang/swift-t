package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.descriptor.ArrayElems;
import exm.stc.ast.descriptor.ArrayRange;
import exm.stc.ast.descriptor.Literals;
import exm.stc.common.CompilerBackend;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVariableException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.FunctionSemantics;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.OpType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.FunctionType.InArgT;
import exm.stc.common.lang.Types.ReferenceType;
import exm.stc.common.lang.Types.ScalarFutureType;
import exm.stc.common.lang.Types.ScalarUpdateableType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Variable;
import exm.stc.common.lang.Variable.VariableStorage;

/**
 * This module contains logic to walk individual expression in Swift and generate code to evaluate them
 */
public class ExprWalker {

  private VarCreator varCreator;
  private CompilerBackend backend;
  private LineMapping lineMapping;
  
  public ExprWalker(VarCreator creator, 
                    CompilerBackend backend, 
                    LineMapping lineMapping) {
    super();
    this.varCreator = creator;
    this.backend = backend;
    this.lineMapping = lineMapping;
  }

  /**
   * Generate the code for an expression evaluation
   *
   * @param oList
   *          : the list of variables that the result of the expression should
   *          be assigned to. Multiple variables are only valid if the
   *          expression is a function call
   * @param renames
   *          if not null, replace references to variables in map
   */
  public void walkExpr(Context context, SwiftAST tree, List<Variable> oList,
          Map<String, String> renames)
      throws UserException {
    String vars = "";
    for (Variable v : oList) {
      vars += " " + v.getName();
    }
    LogHelper.debug(context, "walkExpr " + tree.getText() +
          " assigning to vars:" + vars);
    int token = tree.getType();
    context.syncFileLine(tree.getLine(), lineMapping);

    if (token == ExMParser.CALL_FUNCTION) {
      callFunctionExpression(context, tree, oList, renames);
      return;
    }

    if (oList.size() != 1)
      throw new UserException
      (context, "Cannot assign expression to multiple variables");

    Variable oVar = oList.get(0);
    switch (token) {
      case ExMParser.VARIABLE:
        String srcVarName = tree.child(0).getText();
        if (renames != null && 
            renames.containsKey(srcVarName)) {
          srcVarName = renames.get(srcVarName);
        }

        Variable srcVar = context.getDeclaredVariable(srcVarName);
        
        if (oVar.getName().equals(srcVar.getName())) {
          throw new UserException(context, "Assigning variable " + 
                oVar.getName() + " to itself");
          
        }
        assignVariable(context, oVar, srcVar);
        break;

      case ExMParser.INT_LITERAL:
        assignIntLit(context, tree, oVar, 
                Literals.extractIntLit(context, tree));
        break;

      case ExMParser.FLOAT_LITERAL:
        assignFloatLit(context, tree, oVar);
        break;

      case ExMParser.STRING_LITERAL:
        assignStringLit(context, tree, oVar, 
                  Literals.extractStringLit(context, tree));
        break;

      case ExMParser.BOOL_LITERAL:
        assignBoolLit(context, tree, oVar, 
                  Literals.extractBoolLit(context, tree));
        break;

      case ExMParser.OPERATOR:
        // Handle unary negation as special case
        String intLit = Literals.extractIntLit(context, tree);
        Double floatLit = Literals.extractFloatLit(context, tree);
        if (intLit != null) {
          assignIntLit(context, tree, oVar, intLit);
        } else if (floatLit != null ) {
          assignFloatLit(context, tree, oVar);
        } else {
          if (oList.size() != 1) {
            throw new STCRuntimeError("Operator had " +
            		oList.size() + " outputs, doesn't make sense");
          }
          callOperator(context, tree, oList.get(0), renames);
        }
        break;

      case ExMParser.ARRAY_LOAD:
        arrayLoad(context, tree, oVar, renames);
        break;

      case ExMParser.STRUCT_LOAD:
        structLoad(context, tree, oVar, renames);
        break;
        
      case ExMParser.ARRAY_RANGE:
        arrayRange(context, tree, oVar, renames);
        break;
      case ExMParser.ARRAY_ELEMS:
        arrayElems(context, tree, oVar, renames);
        break;
      default:
        throw new STCRuntimeError
        ("Unknown token type in expression context: "
            + ExMParser.tokenNames[token]);
    }
  }

  /**
   * Creates a temporary variable and evaluates expression into it
   *
   * @param codebuf
   *          buffer to append code to
   * @param type
   *          type of tmp variable
   * @return return the name of a newly created tmp variable
   * @throws UserException
   */
  
  public Variable evalExprToTmp(Context context, SwiftAST tree, SwiftType type,
      boolean storeInStack, Map<String, String> renames) throws UserException {
    if (tree.getType() == ExMParser.VARIABLE) {
      // Base case: don't need to create new variable
      String varName = tree.child(0).getText();
      if (renames != null && renames.containsKey(varName)) {
        varName = renames.get(varName);
      }
      Variable var = context.getDeclaredVariable(varName);
  
      if (var == null) {
        throw new UndefinedVariableException(context, "Variable " + varName
            + " is not defined");
      }
  
      // Check to see that the current variable's storage is adequate
      // Might need to convert type, can't do that here
      if (var.getStorage() == VariableStorage.STACK || (!storeInStack)
              && var.getType().equals(type)) {
        return var;
      }
    }
  
    if (tree.getType() == ExMParser.ARRAY_LOAD
        && immediateArrayLoadPossible(tree)) {
      // Variable tmp = createTmp(context, type, storeInStack, true);
      throw new STCRuntimeError("Immediate array load not supported");
    } else if (tree.getType() == ExMParser.STRUCT_LOAD
          && Types.isStruct(
                TypeChecker.findSingleExprType(context, tree.child(0)))) {
      return lookupStructField(context, tree, type, storeInStack, null, 
                                                               renames);
    } else {
      Variable tmp = varCreator.createTmp(context, type, storeInStack, false);
      ArrayList<Variable> childOList = new ArrayList<Variable>(1);
      childOList.add(tmp);
      walkExpr(context, tree, childOList, renames);
      return tmp;
    }
  }


  /**
   * Do a by-value copy from src to dst
   *
   * @param context
   * @param src
   * @param dst
   * @param srctype
   * @throws UserException
   */
  public void copyByValue(Context context, Variable src, Variable dst,
      SwiftType type) throws UserException {
    if (Types.isScalarFuture(type)) {
      if (type.equals(Types.FUTURE_INTEGER)) {
        backend.asyncOp(BuiltinOpcode.COPY_INT, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (type.equals(Types.FUTURE_STRING)) {
        backend.asyncOp(BuiltinOpcode.COPY_STRING, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (type.equals(Types.FUTURE_FLOAT)) {
        backend.asyncOp(BuiltinOpcode.COPY_FLOAT, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (type.equals(Types.FUTURE_BOOLEAN)) {
        backend.asyncOp(BuiltinOpcode.COPY_BOOL, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (type.equals(Types.FUTURE_BLOB)) {
        backend.asyncOp(BuiltinOpcode.COPY_BLOB, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (type.equals(Types.FUTURE_VOID)) {
        // Sort of silly, but might be needed
        backend.asyncOp(BuiltinOpcode.COPY_VOID, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (type.equals(Types.FUTURE_FILE)) {
        backend.asyncOp(BuiltinOpcode.COPY_FILE, dst, 
                Arrays.asList(Arg.createVar(src)), null);
      } else {
        throw new STCRuntimeError(context.getFileLine() +
            "Haven't implemented copy for scalar type " +
                                        type.toString());
      }
    } else if (Types.isStruct(type)) {
      copyStructByValue(context, src, dst, new Stack<String>(), new Stack<String>(),
                src, dst, type);
    } else if (Types.isArray(type)) {
      copyArrayByValue(context, dst, src);
    } else {
      throw new STCRuntimeError(context.getFileLine() +
          " copying type " + type + " by value not yet "
          + " supported by compiler");
    }
  }


  /**
   * 
   * @param context
   * @param structVar Variable of type struct or struct ref
   * @param fieldName
   * @param storeInStack
   * @param structToCache
   * @param fieldPathToCache
   * @return the contents of the struct field if structVar is a non-ref, 
   *        a reference to the contents of the struct field if structVar is 
   *        a ref
   * @throws UserException
   * @throws UndefinedTypeException
   */
  public Variable structLookup(Context context, Variable structVar,
      String fieldName, boolean storeInStack, Variable rootStruct,
      List<String> fieldPath) throws UserException,
      UndefinedTypeException {
    assert(rootStruct != null);
    assert(fieldPath != null);
    assert(fieldPath.size() > 0);
    SwiftType memType = TypeChecker.findStructFieldType(context, fieldName,
                                                    structVar.getType());
    Variable tmp;
    if (Types.isStructRef(structVar.getType())) {
      tmp = varCreator.createStructFieldTmp(context, 
          rootStruct, new ReferenceType(memType),
          fieldPath, VariableStorage.TEMPORARY);
      backend.structRefLookup(structVar, fieldName, tmp);
    } else {
      assert(Types.isStruct(structVar.getType()));
      tmp = varCreator.createStructFieldTmp(context, 
          rootStruct, memType, fieldPath, VariableStorage.ALIAS);
      backend.structLookup(structVar, fieldName, tmp);
    }
    return tmp;
    
  }

  /**
   * Dereference src into dst
   * ie. dst = *src
   * @param oVar
   * @param lookupIntoVar
   * @throws UserException 
   * @throws UndefinedTypeException 
   */
  public void dereference(Context context, Variable dst, Variable src) 
      throws UndefinedTypeException, UserException {
    assert(Types.isReference(src.getType()));
    assert(Types.isReferenceTo(src.getType(), dst.getType()));
  
    if (Types.isScalarFuture(dst.getType())) {
      SwiftType primType = dst.getType();
      if (primType.equals(Types.FUTURE_INTEGER)) {
        backend.dereferenceInt(dst, src);
      } else if (primType.equals(Types.FUTURE_STRING)) {
        backend.dereferenceString(dst, src);
      } else if (primType.equals(Types.FUTURE_FLOAT)) {
        backend.dereferenceFloat(dst, src);
      } else if (primType.equals(Types.FUTURE_BOOLEAN)) {
        backend.dereferenceBool(dst, src);
      } else if (primType.equals(Types.FUTURE_FILE)) {
        backend.dereferenceFile(dst, src);
      } else {
        throw new STCRuntimeError("Don't know how to dereference "
            + " type " + src.getType().toString());
      }
    } else if (Types.isArray(dst.getType())) {
      String wName = context.getFunctionContext().constructName("copy-wait");
      List<Variable> keepOpenVars = Arrays.asList(dst);
      backend.startWaitStatement(wName, Arrays.asList(src),
              Arrays.asList(src, dst), keepOpenVars, false);
      Variable derefed = varCreator.createTmpAlias(context, dst.getType());
      backend.retrieveRef(derefed, src);
      copyArrayByValue(context, dst, derefed);
      backend.endWaitStatement(keepOpenVars);
    } else if (Types.isStruct(dst.getType())) {
      dereferenceStruct(context, dst, src);
    } else {
      throw new STCRuntimeError("Can't dereference type "
         + src.getType().toString());
    }
  }

  private void callOperator(Context context, SwiftAST tree, 
      Variable out, Map<String, String> renames) throws UserException {
    String op = tree.child(0).getText();
    int op_argcount = tree.getChildCount() - 1;

    // Use the AST token label to find the actual operator
    BuiltinOpcode opcode = TypeChecker.getBuiltInFromOpTree(context, tree);
    assert(opcode != null);
    
    OpType optype = Operators.getBuiltinOpType(opcode);
    assert(optype != null);
    
    int argcount = optype.in.length;

    if (op_argcount != argcount) {
      throw new STCRuntimeError("Operator " + op + " has " + op_argcount
          + " arguments in AST, but expected" + argcount);
    }

    ArrayList<Arg> iList = new ArrayList<Arg>(argcount);
    for (int i = 0; i < op_argcount; i++) {
      SwiftType type = new ScalarFutureType(optype.in[i]);

      // Store into temporary variables
      Variable arg = evalExprToTmp(context, tree.child(i + 1), type, false,
                                                                  renames);
      iList.add(Arg.createVar(arg));
    }
    backend.asyncOp(opcode, out, iList, null);
  }
  

  /**
   * Generate code for a call to a function, where the arguments might be
   * expressions
   *
   * @param context
   * @param tree
   * @param oList
   * @throws UserException
   * @throws UndefinedVariableException
   * @throws UndefinedFunctionException
   */
  private void callFunctionExpression(Context context, SwiftAST tree,
      List<Variable> oList, Map<String, String> renames) throws UserException {
    assert(tree.getChildCount() >= 2 && tree.getChildCount() <= 3);
    String f = tree.child(0).getText();
    try {
      // If this is an assert statement, disable it
      if (FunctionSemantics.isAssertVariant(f) &&
              Settings.getBoolean(Settings.OPT_DISABLE_ASSERTS)) {
        return;
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError("Expected option to be present: " +
                                                          e.toString());
    }

    SwiftAST arglist = tree.child(1);
    
    FunctionType ftype = context.lookupFunction(f);
    if (ftype == null) {
      throw UndefinedFunctionException.unknownFunction(context, f);
    }
    
    if (arglist.getChildCount() > 0 && ftype.getInputs().size() == 0) {
      throw new TypeMismatchException(context, "Argument provided to " +
      		"zero-argument function: " + f);
    }
    
    // evaluate argument expressions left to right, creating temporaries
    ArrayList<Variable> argVars = new ArrayList<Variable>(
            arglist.getChildCount());
    int argcount = arglist.getChildCount();
    for (int i = 0; i < argcount; i++) {
      SwiftAST argtree = arglist.child(i);
      InArgT expType = ftype.getInputs().get(Math.min(i, ftype.getInputs().size() - 1));
      
      
      SwiftType argtype;
      if (expType.getAltCount() == 1) {
        argtype = TypeChecker.findSingleExprType(context, argtree, expType.getAlt(0));
      } else {
        argtype = TypeChecker.findSingleExprType(context, argtree);
        SwiftType matching = TypeChecker.whichAlternativeType(expType, argtype);
        if (matching != null && Types.isUpdateableEquiv(argtype, matching)) {
          // Try to coerce
          argtree.clearTypeInfo();
          argtype = TypeChecker.findSingleExprType(context, argtree, matching);
        }
      }

      argVars.add(evalExprToTmp(context, argtree, argtype, false, renames));
    }
    
    // Process priority after arguments have been evaluated, so that
    // the argument evaluation is outside the wait statement
    Variable priorityVal = null;
    boolean openedWait = false;
    List<Variable> keepOpen = null;
    Context callContext = context;
    if (tree.getChildCount() == 3) {
      SwiftAST priorityT = tree.child(2);
      Variable priorityFuture = evalExprToTmp(context, priorityT,
                            Types.FUTURE_INTEGER, false, renames);
      keepOpen = new ArrayList<Variable>(0); // TODO: Do we need these?
      // used variables: any input or output args
      ArrayList<Variable> usedVariables = new ArrayList<Variable>();
      usedVariables.addAll(argVars);
      usedVariables.addAll(oList);
      
      backend.startWaitStatement(context.getFunctionContext().constructName("priority-wait"), 
                        Arrays.asList(priorityFuture), usedVariables, keepOpen, false);
      openedWait = true;
      callContext = new LocalContext(context);
      priorityVal = varCreator.fetchValueOf(callContext, priorityFuture);
      
    }
    
    // callFunction will check that argument types match function
    callFunction(context, f, oList, argVars, priorityVal);
    if (openedWait) {
      backend.endWaitStatement(keepOpen);
    }
  
  }
  

  private boolean immediateArrayLoadPossible(SwiftAST tree) {
    assert (tree.getType() == ExMParser.ARRAY_LOAD);
    // TODO later this would detect if we can immediately look up array value
    // at this point in the procedure
    // For the moment, disable this check
    return false;
  }
  
  private void structLoad(Context context, SwiftAST tree, Variable oVar,
      Map<String, String> renames) throws UserException {
    LogHelper.trace(context, "structLoad");
    lookupStructField(context, tree, oVar.getType(), false, oVar, renames);
  }
  

  /**
   * Handle an expression which is an array access. Copies a member of an array,
   * specified by index, into another variable. If the other variable is an
   * alias variable, we can avoid the copy.
   *
   * @param context
   * @param tree
   * @param oVar
   *          the variable to copy into
   * @throws UserException
   */
  private void arrayLoad(Context context, SwiftAST tree, Variable oVar, 
        Map<String, String> renames)
      throws UserException {
    if (tree.getChildCount() != 2) {
      throw new STCRuntimeError("array_load subtree should have "
          + " only two children, but has " + tree.getChildCount());
    }

    // Work out the type of the array so we know the type of the temp var
    SwiftAST arrayTree = tree.child(0);
    SwiftType arrExprType = TypeChecker.findSingleExprType(context, arrayTree);

    // Evaluate the array
    Variable arrayVar = evalExprToTmp(context, arrayTree, arrExprType, false,
                                                                    renames);

    // At this point arrayVar could either be an array, or a reference to an
    // array

    // Check that the types make sense for array indexing
    boolean isRef;
    SwiftType arrayType = arrayVar.getType();
    SwiftType memberType;
    if (Types.isArray(arrayType)) {
      isRef = false;
      memberType = arrayType.getMemberType();
    } else if (Types.isArrayRef(arrayType)) {
      isRef = true;
      memberType = arrayType.getMemberType().getMemberType();
    } else {
      throw new TypeMismatchException(context,
          "Cannot index variable that is not array or reference to" + " array");
    }

    // Check the match between member type and output var type
    if (Types.isArray(memberType)) {
      // Output should be *reference* to the member type
      if (!(Types.isArrayRef(oVar.getType()) && oVar.getType().getMemberType()
          .equals(memberType))) {
        SwiftType expType = new Types.ReferenceType(memberType);
        throw new TypeMismatchException(context, "Need variable of type "
            + expType.toString() + " for output of array lookup for array "
            + arrayVar.getName() + " of type " + arrayVar.getType().toString()
            + " but output variable had type " + oVar.getType().toString());
      }
    } else if (Types.isStruct(memberType)) {
      if (!(Types.isStructRef(oVar.getType()) && oVar.getType().getMemberType()
          .equals(memberType))) { 
        SwiftType expType = new Types.ReferenceType(memberType);
        throw new TypeMismatchException(context, "Need variable of type "
                + expType.toString() + " for output of array lookup for array "
                + arrayVar.getName() + " of type " + arrayVar.getType().toString()
                + " but output variable had type " + oVar.getType().toString());
      }
    } else if (Types.isScalarFuture(memberType)) {
      // Types should match
      if (!memberType.equals(oVar.getType())) {
        throw new TypeMismatchException(context,
            "Cannot assign from element of " + arrayVar.toString() + " to "
                + oVar.toString());
      }
    } else {
      throw new STCRuntimeError("Don't know how to deal with arrays"
          + " with member type" + memberType.toString());
    }

    // Any integer expression can index into array
    SwiftAST arrayIndexTree = tree.child(1);
    SwiftType indexType = TypeChecker.findSingleExprType(context, arrayIndexTree);
    if (!indexType.equals(Types.FUTURE_INTEGER)) {
      throw new TypeMismatchException(context,
          "array index expression does not have integer type.  Type of " +
          "index expression was " + indexType.typeName());
    }

    // The direct result of the array lookup
    Variable lookupIntoVar;
    boolean doDereference;
    if (memberType.equals(oVar.getType())) {
      // Need to dereference into temporary var
      lookupIntoVar = varCreator.createTmp(context, 
              new ReferenceType(memberType));
      doDereference = true;
    } else {
      assert(Types.isReference(oVar.getType()));
      assert(memberType.equals(oVar.getType().getMemberType()));
      lookupIntoVar = oVar;
      doDereference = false;
    }

    String arrayIndexStr = Literals.extractIntLit(context, 
                                          arrayIndexTree);
    if (arrayIndexStr != null) {
      // Handle the special case where the index is a constant.
      long arrayIndex;
      try {
        arrayIndex = Long.parseLong(arrayIndexStr);
      } catch (NumberFormatException e) {
        throw new STCRuntimeError(
            "Invalid non-numeric array index token " + arrayIndexStr);
      }
      backend.arrayLookupRefImm(lookupIntoVar, arrayVar, 
          Arg.createIntLit(arrayIndex), isRef);
    } else {
      // TODO: there may be special cases where we know the index
      // at composite runtime, so could do an arrayLoadImmediate

      // Handle the general case where the index must be computed
      Variable indexVar = evalExprToTmp(context, arrayIndexTree,
          Types.FUTURE_INTEGER, false, renames);
      backend.arrayLookupFuture(lookupIntoVar, arrayVar, indexVar, isRef);
    }
    // Do the dereference down here so that it is generated in a more logical
    // order
    if (doDereference) {
      dereference(context, oVar, lookupIntoVar);
    }
  }
  

  
  /**
   * Lookup the turbine ID of a struct member
   *
   * @param context
   * @param tree
   *          STRUCT_LOOKUP expression
   * @param type
   *          type of expression
   * @param storeInStack
   * @param outVar (optional) variable to copy output into
   * @return a new variable which is an alias for the struct member
   * @throws UndefinedTypeException
   * @throws UserException
   */
  private Variable lookupStructField(Context context, SwiftAST tree,
      SwiftType type, boolean storeInStack, Variable outVar, 
      Map<String, String> renames) throws UndefinedTypeException,
      UserException {

    if (storeInStack) {
      throw new STCRuntimeError("Dont know how to store results of "
          + " struct lookup in stack");
    }

    // Check if the field is cached
    assert (tree.getType() == ExMParser.STRUCT_LOAD);
    assert (tree.getChildCount() == 2);
    
    LinkedList<String> path = new LinkedList<String>();
    path.add(tree.child(1).getText());
    SwiftAST structTree = tree.child(0);

    
    Variable parent;
    SwiftAST parentTree = tree.child(0);
    String fieldName = tree.child(1).getText();

    if (parentTree.getType() == ExMParser.VARIABLE) {
      parent = context.getDeclaredVariable(parentTree.child(0).getText());
    } else {
      SwiftType parentType = TypeChecker.findSingleExprType(context, parentTree);
      // Type error should have been caught earlier
      assert(Types.isStruct(parentType) || Types.isStructRef(parentType));
      parent = evalExprToTmp(context, parentTree, parentType, false, renames);
    }
    

    /* 
     * Walk the tree to find out the full path if we are accessing a nested 
     * struct.  rootStruct should be the name of the outermost nested struct 
     */
    while (structTree.getType() == ExMParser.STRUCT_LOAD) {
      assert (structTree.getChildCount() == 2);
      path.addFirst(structTree.child(1).getText());
      structTree = structTree.child(0);
    }
    Variable rootStruct = null;
    List<String> pathFromRoot = null;
    if (structTree.getType() == ExMParser.VARIABLE) {
      // The root is a local variable
      assert (structTree.getChildCount() == 1);
      String structVarName = structTree.child(0).getText();
      rootStruct = context.getDeclaredVariable(structVarName);
      pathFromRoot = path;
    } else {
      rootStruct = parent;
      pathFromRoot = Arrays.asList(fieldName);
    }
    Variable tmp = structLookup(context, parent, fieldName,
        storeInStack, rootStruct, pathFromRoot);
    return derefOrCopyResult(context, tmp, outVar);
  }

  
  private Variable derefOrCopyResult(Context context, Variable lookupResult,
      Variable outVar) throws UndefinedTypeException, UserException {
    if (outVar == null) {
      return lookupResult;
    } else if (Types.isReferenceTo(lookupResult.getType(), outVar.getType())) {
      dereference(context, outVar, lookupResult);
      return outVar;
    } else {
      copyByValue(context, lookupResult, outVar, outVar.getType());
      return outVar;
    }
  }

  
  private void arrayRange(Context context, SwiftAST tree, Variable oVar,
      Map<String, String> renames) throws UserException {
    assert(Types.isArray(oVar.getType()));
    assert(Types.isInt(oVar.getType().getMemberType()));
    ArrayRange ar = ArrayRange.fromAST(context, tree);
    ar.typeCheck(context);
    
    Variable startV = evalExprToTmp(context, ar.getStart(), Types.FUTURE_INTEGER, 
                      false, null);
    Variable endV = evalExprToTmp(context, ar.getEnd(), Types.FUTURE_INTEGER, 
        false, null);

   if (ar.getStep() != null) {
      Variable stepV = evalExprToTmp(context, ar.getStep(), Types.FUTURE_INTEGER, 
          false, null);
      backend.builtinFunctionCall("rangestep", Arrays.asList(startV, endV, stepV), 
          Arrays.asList(oVar), null);
    } else {
      backend.builtinFunctionCall("range", Arrays.asList(startV, endV), 
          Arrays.asList(oVar), null);
    }
  }

  /**
   * Construct an array with elements
   * [e1, e2, e3, e4].  We start numbering from 0
   * @param context
   * @param tree
   * @param oVar
   * @param renames
   * @throws UserException
   */
  private void arrayElems(Context context, SwiftAST tree, Variable oVar,
      Map<String, String> renames) throws UserException {
      assert(Types.isArray(oVar.getType()));
      ArrayElems ae = ArrayElems.fromAST(context, tree);
      SwiftType arrType = TypeChecker.findSingleExprType(context, tree);
      assert(Types.isArray(arrType));
      assert(arrType.equals(oVar.getType()));
      
      SwiftType memType = arrType.getMemberType();
      
      /** Evaluate all the members and insert into list */
      for (int i = 0; i < ae.getMemberCount(); i++) {
        SwiftAST mem = ae.getMember(i);
        Variable computedMember = evalExprToTmp(context, mem, 
            memType, false, renames);
        backend.arrayInsertImm(computedMember, oVar, 
                                      Arg.createIntLit(i));
      }
      // Will need to close this array
      context.flagArrayForClosing(oVar);
  }


  private void callFunction(Context context, String function,
      List<Variable> oList, List<Variable> iList, Variable priorityVal)
      throws UndefinedTypeException, UserException {

    List<SwiftType> expectedTypes = TypeChecker.checkFunctionCall(context,
        function, oList, iList);


    // The expected types might not be same as current input types, work out
    // what we need to do to make theme the same
    ArrayList<Variable> realIList = new ArrayList<Variable>(iList.size());
    ArrayList<Variable> derefVars = new ArrayList<Variable>();
    ArrayList<Variable> waitVars = new ArrayList<Variable>();
    Context waitContext = null;

    assert(expectedTypes.size() == iList.size());
    for (int i = 0; i < iList.size(); i++) {
      Variable input = iList.get(i);
      SwiftType inputType = input.getType();
      SwiftType expType = expectedTypes.get(i);
      if (inputType.equals(expType)) {
        realIList.add(input);
      } else if (Types.isReferenceTo(inputType, expType)) {
        if (waitContext == null) {
          waitContext = new LocalContext(context);
        }
        Variable derefed;
        derefed = waitContext.createAliasVariable(expType);
        waitVars.add(input);
        derefVars.add(derefed);
        realIList.add(derefed);
      } else if (Types.isUpdateableEquiv(inputType, expType)) {
        if (waitContext == null) {
          waitContext = new LocalContext(context);
        }
        Variable copy = varCreator.createTmp(waitContext,
                  ScalarUpdateableType.asScalarFuture(inputType));
        assignVariable(waitContext, copy, input);
        realIList.add(copy);
      } else {
        throw new STCRuntimeError(context.getFileLine() + 
                " Shouldn't be here, don't know how to "
            + " convert " + inputType.toString() + " to " + expType.toString());
      }
    }

    if (waitContext != null) {
      FunctionContext fc = context.getFunctionContext();
      ArrayList<Variable> usedVars = new ArrayList<Variable>();
      usedVars.addAll(iList); usedVars.addAll(oList);
      backend.startWaitStatement(
           fc.constructName("call-" + function),
           waitVars, usedVars, new ArrayList<Variable>(),
           false);

      assert(waitVars.size() == derefVars.size());
      // Generate code to fetch actual array IDs  inside
      // wait statement
      for (int i = 0; i < waitVars.size(); i++) {
        Variable derefVar = derefVars.get(i);
        varCreator.declare(derefVar);
        if (Types.isArrayRef(waitVars.get(i).getType())) {
          backend.retrieveRef(derefVar, waitVars.get(i));
        } else {
          throw new STCRuntimeError("Don't know how to " +
              "deref non-array function arg " + derefVar);
        }
      }
    }


    Arg priority = priorityVal != null ? Arg.createVar(priorityVal) : null;
    if (context.isBuiltinFunction(function)) {
      if (FunctionSemantics.hasOpEquiv(function)) {
        assert(oList.size() <= 1);
        Variable out = oList.size() == 0 ? null : oList.get(0);
        //TODO: priority?
        backend.asyncOp(FunctionSemantics.getOpEquiv(function), out, 
                        Arg.fromVarList(realIList), priority);
      } else {
        backend.builtinFunctionCall(function, realIList, oList, priority);
      }
    } else if (context.isCompositeFunction(function))
      backend.compositeFunctionCall(function, realIList, oList, null, 
          !context.isSyncComposite(function), priority);
    else
      throw UndefinedFunctionException.unknownFunction(context, function);

    if (waitContext != null) {
      backend.endWaitStatement(new ArrayList<Variable>());
    }
  }


  private void assignIntLit(Context context, SwiftAST tree,
                            Variable oVar, String value)
 throws UserException {
   LogHelper.trace(context, oVar.toString()+"="+value);
   if(Types.isInt(oVar.getType())) {
     backend.assignInt(oVar, Arg.createIntLit(Long.parseLong(value)));
   } else if (Types.isFloat(oVar.getType())) {
     double floatval = Literals.interpretIntAsFloat(context, value);
     backend.assignFloat(oVar, Arg.createFloatLit(floatval));
     
   } else {
     assert false : "assignIntLit to variable" + oVar;
   }
  }

  private void assignBoolLit(Context context, SwiftAST tree, Variable oVar,
      String value) throws UserException {
   assert(Types.isBool(oVar.getType()));
   backend.assignBool(oVar, Arg.createBoolLit(Boolean.parseBoolean(value)));
  }

  private void assignFloatLit(Context context, SwiftAST tree, Variable oVar) 
  throws UserException {
   assert(Types.isFloat(oVar.getType()));
   double val = Literals.extractFloatLit(context, tree);
   backend.assignFloat(oVar, Arg.createFloatLit(val));
  }

  private void assignStringLit(Context context, SwiftAST tree, Variable oVar,
      String value) throws UserException {
    assert(Types.isString(oVar.getType()));
    backend.assignString(oVar, Arg.createStringLit(value));
  }

  private void assignVariable(Context context, Variable oVar,
      Variable src) throws UserException {
    if (Types.isScalarUpdateable(src.getType())) {
      // Create a future alias to the updateable type so that
      // types match
      Variable val = varCreator.createTmpLocalVal(context,
          ScalarUpdateableType.asScalarValue(src.getType()));

      backend.latestValue(val, src);
      /* Create a future with a snapshot of the value of the updateable
       * By making the retrieve and store explicit the optimizer should be
       * able to optimize out the future in many cases
       */
      Variable snapshot = varCreator.createTmp(context,
          ScalarUpdateableType.asScalarFuture(src.getType()));

      if (!src.getType().equals(Types.UPDATEABLE_FLOAT)) {
        throw new STCRuntimeError(src.getType() + " not yet supported");
      }
      backend.assignFloat(snapshot, Arg.createVar(val));
      src = snapshot;
    }
    
    SwiftType srctype = src.getType();
    SwiftType dsttype = oVar.getType();
    TypeChecker.checkCopy(context, srctype, dsttype);

    copyByValue(context, src, oVar, srctype);
  }

  private void copyArrayByValue(Context context, Variable dst, Variable src) 
                                                throws UserException {
    assert(dst.getType().equals(src.getType()));
    assert(Types.isArray(src.getType()));
    LocalContext copyContext = new LocalContext(context);
    SwiftType t = src.getType();
    SwiftType memType = Types.getArrayMemberType(t);
    Variable member = copyContext.createAliasVariable(memType);
    Variable ix = copyContext.createLocalValueVariable(Types.VALUE_INTEGER);
    
    List<Variable> keepOpen = Arrays.asList(dst);
    backend.startForeachLoop(src, member, ix, true, -1, false, 
        Arrays.asList(src, dst), keepOpen);
    backend.arrayInsertImm(member, dst, Arg.createVar(ix));
    backend.endForeachLoop(true, -1, false, keepOpen);
    backend.closeArray(dst);
  }


  private void copyStructByValue(Context context,
      Variable srcRoot, Variable dstRoot,
      Stack<String> srcPath, Stack<String> dstPath,
      Variable src, Variable dst, SwiftType type)
          throws UserException, UndefinedTypeException {
    assert(src.getType().equals(dst.getType()));
    assert(Types.isStruct(src.getType()));

    // recursively copy struct members
    StructType st = (StructType) type;
    for (StructField f : st.getFields()) {
      // get handles to both src and dst field
      SwiftType fieldType = f.getType();
      srcPath.push(f.getName());
      dstPath.push(f.getName());
      Variable fieldSrc = structLookup(context, src, f.getName(),
          false, srcRoot, srcPath);

      Variable fieldDst = structLookup(context, dst, f.getName(),
          false, dstRoot, dstPath);

      if (Types.isStruct(fieldType)) {
        copyStructByValue(context, srcRoot, dstRoot,
          srcPath, dstPath, fieldSrc, fieldDst, fieldType);
      } else {
        copyByValue(context, fieldSrc, fieldDst, fieldType);
      }
      srcPath.pop(); dstPath.pop();
    }
  }

  /**
   * Copy a struct reference to a struct.  We need to do this in the
   * compiler front-end because we want to generate specialized code
   * to walk the structure and copy all struct members
   * @param context
   * @param dst
   * @param src
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void dereferenceStruct(Context context, Variable dst, Variable src)
      throws UserException, UndefinedTypeException {
    backend.startWaitStatement( 
                    context.getFunctionContext().constructName("copystruct"), 
                    Arrays.asList(src), Arrays.asList(src, dst), 
                    new ArrayList<Variable>(), false);
    Variable rValDerefed = varCreator.createTmp(context, 
            src.getType().getMemberType(), false, true);
    backend.retrieveRef(rValDerefed, src);
    copyByValue(context, rValDerefed, dst, dst.getType());
    backend.endWaitStatement(new ArrayList<Variable>());
  }
  
}
