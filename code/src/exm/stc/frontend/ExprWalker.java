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
package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVariableException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.OpType;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.ScalarFutureType;
import exm.stc.common.lang.Types.ScalarUpdateableType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.Pair;
import exm.stc.frontend.Context.FnProp;
import exm.stc.frontend.VariableUsageInfo.VInfo;
import exm.stc.frontend.tree.ArrayElems;
import exm.stc.frontend.tree.ArrayRange;
import exm.stc.frontend.tree.FunctionCall;
import exm.stc.frontend.tree.Literals;
import exm.stc.ic.STCMiddleEnd;

/**
 * This module contains logic to walk individual expression in Swift and generate code to evaluate them
 */
public class ExprWalker {

  private VarCreator varCreator;
  private STCMiddleEnd backend;
  private LineMapping lineMapping;
  
  public ExprWalker(VarCreator creator, 
                    STCMiddleEnd backend, 
                    LineMapping lineMapping) {
    super();
    this.varCreator = creator;
    this.backend = backend;
    this.lineMapping = lineMapping;
  }

  /**
   * Evaluate expression into provided output variables
   *
   * @param oList
   *          : the list of variables that the result of the expression should
   *          be assigned to. Multiple variables are only valid if the
   *          expression is a function call
   * @param renames
   *          if not null, replace references to variables in map
   */
  public void evalToVars(Context context, SwiftAST tree, List<Var> oList,
          Map<String, String> renames) throws UserException {
    LogHelper.debug(context, "walkExpr " + tree.getText() +
          " assigning to vars: " + oList);
    int token = tree.getType();
    context.syncFilePos(tree, lineMapping);

    if (token == ExMParser.CALL_FUNCTION) {
      callFunctionExpression(context, tree, oList, renames);
      return;
    }

    if (oList.size() != 1)
      throw new UserException
      (context, "Cannot assign expression to multiple variables");

    Var oVar = oList.get(0);
    switch (token) {
      case ExMParser.VARIABLE:
        String srcVarName = tree.child(0).getText();
        if (renames != null && 
            renames.containsKey(srcVarName)) {
          srcVarName = renames.get(srcVarName);
        }

        Var srcVar = context.getDeclaredVariable(srcVarName);
        
        if (oVar.name().equals(srcVar.name())) {
          throw new UserException(context, "Assigning variable " + 
                oVar.name() + " to itself");
          
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
        ("Unexpected token type in expression context: "
            + LogHelper.tokName(token));
    }
  }

  /**
   * Evaluates expression, creating temporary output variable if needed
   *
   * @param type expected result type of expression
   * @return return the name of a newly created tmp variable
   * @throws UserException
   */
  
  public Var eval(Context context, SwiftAST tree, Type type,
      boolean storeInStack, Map<String, String> renames) throws UserException {
    assert(type != null);
    context.syncFilePos(tree, lineMapping);
    if (tree.getType() == ExMParser.VARIABLE) {
      // Base case: don't need to create new variable
      String varName = tree.child(0).getText();
      if (renames != null && renames.containsKey(varName)) {
        varName = renames.get(varName);
      }
      Var var = context.getDeclaredVariable(varName);
  
      if (var == null) {
        throw new UndefinedVariableException(context, "Variable " + varName
            + " is not defined");
      }
      
      // Check to see that the current variable's storage is adequate
      // Might need to convert type, can't do that here
      if ((var.storage() == VarStorage.STACK || (!storeInStack))
              && var.type().equals(type)) {
        return var;
      }
    }
  
    if (tree.getType() == ExMParser.STRUCT_LOAD
          && Types.isStruct(
                TypeChecker.findSingleExprType(context, tree.child(0)))) {
      return lookupStructField(context, tree, type, storeInStack, null, 
                                                               renames);
    } else {
      Var tmp = varCreator.createTmp(context, type, storeInStack, false);
      flagDeclaredVarForClosing(context, tmp);
      
      ArrayList<Var> childOList = new ArrayList<Var>(1);
      childOList.add(tmp);
      evalToVars(context, tree, childOList, renames);
      return tmp;
    }
  }


  /**
   * Do a by-value copy from src to dst
   *
   * @param context
   * @param src
   * @param dst
   * @param type
   * @throws UserException
   */
  public void copyByValue(Context context, Var src, Var dst,
      Type type) throws UserException {
    if (Types.isScalarFuture(type)) {
      if (Types.isInt(type)) {
        backend.asyncOp(BuiltinOpcode.COPY_INT, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (Types.isString(type)) {
        backend.asyncOp(BuiltinOpcode.COPY_STRING, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (Types.isFloat(type)) {
        backend.asyncOp(BuiltinOpcode.COPY_FLOAT, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (Types.isBool(type)) {
        backend.asyncOp(BuiltinOpcode.COPY_BOOL, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (Types.isBlob(type)) {
        backend.asyncOp(BuiltinOpcode.COPY_BLOB, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (Types.isVoid(type)) {
        // Sort of silly, but might be needed
        backend.asyncOp(BuiltinOpcode.COPY_VOID, dst, 
            Arrays.asList(Arg.createVar(src)), null);
      } else if (Types.isFile(type)) {
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
   * @param rootStruct top level variable for structure
   * @param fieldPath path from top level
   * @return the contents of the struct field if structVar is a non-ref, 
   *        a reference to the contents of the struct field if structVar is 
   *        a ref
   * @throws UserException
   * @throws UndefinedTypeException
   */
  public Var structLookup(Context context, Var structVar,
      String fieldName, boolean storeInStack, Var rootStruct,
      List<String> fieldPath) throws UserException,
      UndefinedTypeException {
    assert(rootStruct != null);
    assert(fieldPath != null);
    assert(fieldPath.size() > 0);
    Type memType = TypeChecker.findStructFieldType(context, fieldName,
                                                    structVar.type());
    Var tmp;
    if (Types.isStructRef(structVar.type())) {
      tmp = varCreator.createStructFieldTmp(context, 
          rootStruct, new RefType(memType),
          fieldPath, VarStorage.TEMP);
      backend.structRefLookup(structVar, fieldName, tmp);
    } else {
      assert(Types.isStruct(structVar.type()));
      tmp = varCreator.createStructFieldTmp(context, 
          rootStruct, memType, fieldPath, VarStorage.ALIAS);
      backend.structLookup(structVar, fieldName, tmp);
    }
    return tmp;
    
  }

  /**
   * Dereference src into dst
   * ie. dst = *src
   * @param dst
   * @param src
   * @throws UserException 
   * @throws UndefinedTypeException 
   */
  public void dereference(Context context, Var dst, Var src) 
      throws UndefinedTypeException, UserException {
    assert(Types.isRef(src.type()));
    assert(Types.isRefTo(src.type(), dst.type()));
  
    if (Types.isScalarFuture(dst.type())) {
      Type dstType = dst.type();
      if (dstType.equals(Types.F_INT)) {
        backend.dereferenceInt(dst, src);
      } else if (dstType.equals(Types.F_STRING)) {
        backend.dereferenceString(dst, src);
      } else if (dstType.equals(Types.F_FLOAT)) {
        backend.dereferenceFloat(dst, src);
      } else if (dstType.equals(Types.F_BOOL)) {
        backend.dereferenceBool(dst, src);
      } else if (dstType.equals(Types.F_FILE)) {
        backend.dereferenceFile(dst, src);
      } else {
        throw new STCRuntimeError("Don't know how to dereference "
            + " type " + src.type().toString());
      }
    } else if (Types.isArray(dst.type())) {
      String wName = context.getFunctionContext().constructName("copy-wait");
      List<Var> keepOpenVars = Arrays.asList(dst);
      List<Var> usedVars = Arrays.asList(src, dst);
      List<Var> waitVars = Arrays.asList(src);
      backend.startWaitStatement(wName, waitVars,
              usedVars, keepOpenVars, null,
              WaitMode.DATA_ONLY, false, TaskMode.LOCAL);
      Var derefed = varCreator.createTmpAlias(context, dst.type());
      backend.retrieveRef(derefed, src);
      copyArrayByValue(context, dst, derefed);
      backend.endWaitStatement();
    } else if (Types.isStruct(dst.type())) {
      dereferenceStruct(context, dst, src);
    } else {
      throw new STCRuntimeError("Can't dereference type " + src.type());
    }
  }

  public void assign(Var dst, Arg src) {
    assert(Types.isScalarValue(src.getType()));
    assert(Types.isScalarFuture(dst.type()));
    switch (src.getType().primType()) {
      case INT:
        backend.assignInt(dst, src);
        break;
      case BOOL:
        backend.assignBool(dst, src);
        break;
      case FLOAT:
        backend.assignFloat(dst, src);
        break;
      case STRING:
        backend.assignInt(dst, src);
        break;
      case BLOB:
        backend.assignBlob(dst, src);
        break;
      case VOID:
        backend.assignVoid(dst, src);
        break;
      default:
        throw new STCRuntimeError("assigning from type " + src.getType()
                + " not supported internally");
    }
  }

  private void callOperator(Context context, SwiftAST tree, 
      Var out, Map<String, String> renames) throws UserException {
    String op = tree.child(0).getText();
    int op_argcount = tree.getChildCount() - 1;

    // Use the AST token label to find the actual operator
    BuiltinOpcode opcode = TypeChecker.getBuiltInFromOpTree(context, tree,
                                                            out.type());
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
      Type type = new ScalarFutureType(optype.in[i]);

      // Store into temporary variables
      Var arg = eval(context, tree.child(i + 1), type, false, renames);
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
      List<Var> oList, Map<String, String> renames) throws UserException {
    FunctionCall f = FunctionCall.fromAST(context, tree, true);
    FunctionType concrete = TypeChecker.concretiseFunctionCall(context,
                                f.function(), f.type(), f.args(), oList, false);
    try {
      // If this is an assert statement, disable it
      if (Builtins.isAssertVariant(f.function()) &&
              Settings.getBoolean(Settings.OPT_DISABLE_ASSERTS)) {
        return;
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError("Expected option to be present: " +
                                                          e.toString());
    }
    
    // evaluate argument expressions left to right, creating temporaries
    ArrayList<Var> argVars = new ArrayList<Var>(
            f.args().size());
    
    for (int i = 0; i < f.args().size(); i++) {
      SwiftAST argtree = f.args().get(i);
      Type expType = concrete.getInputs().get(i);

      Type exprType = TypeChecker.findSingleExprType(context, argtree);
      Type argtype = TypeChecker.checkFunArg(context, f.function(), i,
                                                    expType, exprType).val2;
      argVars.add(eval(context, argtree, argtype, false, renames));
    }
    
    // Process priority after arguments have been evaluated, so that
    // the argument evaluation is outside the wait statement
    Var priorityVal = null;
    boolean openedWait = false;
    List<Var> keepOpen = RefCounting.filterWriteRefcount(oList);
    Context callContext = context;
    if (tree.getChildCount() == 3) {
      SwiftAST priorityT = tree.child(2);
      Var priorityFuture = eval(context, priorityT,
                            Types.F_INT, false, renames);
      // used variables: any input or output args
      List<Var> usedVariables = new ArrayList<Var>();
      usedVariables.addAll(argVars);
      usedVariables.addAll(oList);
      
      List<Var> waitVars = Arrays.asList(priorityFuture);
      backend.startWaitStatement(context.getFunctionContext().constructName("priority-wait"), 
                        waitVars, usedVariables, keepOpen,
                        null,
                        WaitMode.DATA_ONLY, false, TaskMode.LOCAL_CONTROL);
      openedWait = true;
      callContext = new LocalContext(context);
      priorityVal = varCreator.fetchValueOf(callContext, priorityFuture);
    }
    
    // callFunction will check that argument types match function
    callFunction(context, f.function(), concrete, oList, argVars, priorityVal);
    if (openedWait) {
      backend.endWaitStatement();
    }
  
  }
  
  private void structLoad(Context context, SwiftAST tree, Var oVar,
      Map<String, String> renames) throws UserException {
    LogHelper.trace(context, "structLoad");
    lookupStructField(context, tree, oVar.type(), false, oVar, renames);
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
  private void arrayLoad(Context context, SwiftAST tree, Var oVar, 
        Map<String, String> renames)
      throws UserException {
    if (tree.getChildCount() != 2) {
      throw new STCRuntimeError("array_load subtree should have "
          + " only two children, but has " + tree.getChildCount());
    }

    // Work out the type of the array so we know the type of the temp var
    SwiftAST arrayTree = tree.child(0);
    Type arrExprType = TypeChecker.findSingleExprType(context, arrayTree);
    Type arrType = null;
    
    for (Type altType: UnionType.getAlternatives(arrExprType)) {
      assert(Types.isArray(altType) || Types.isArrayRef(altType));
      Type lookupRes = TypeChecker.dereferenceResultType(
                                Types.getArrayMemberType(altType));
      if (lookupRes.equals(oVar.type())) {
        arrType = altType;
        break;
      }
    }
    if (arrType == null) {
      throw new STCRuntimeError("No viable array type for lookup up "
              + arrExprType + " into " + oVar);
    }

    // Evaluate the array
    Var arrayVar = eval(context, arrayTree, arrType,
                                      false, renames);

    Type memberType = Types.getArrayMemberType(arrType);

    // Any integer expression can index into array
    SwiftAST arrayIndexTree = tree.child(1);
    Type indexType = TypeChecker.findSingleExprType(context, arrayIndexTree);
    if (!indexType.assignableTo(Types.F_INT)) {
      throw new TypeMismatchException(context,
          "array index expression does not have integer type.  Type of " +
          "index expression was " + indexType.typeName());
    }

    // The direct result of the array lookup
    Var lookupIntoVar;
    boolean doDereference;
    if (memberType.equals(oVar.type())) {
      // Need to dereference into temporary var
      lookupIntoVar = varCreator.createTmp(context, 
              new RefType(memberType));
      doDereference = true;
    } else {
      assert(Types.isRefTo(oVar.type(), memberType));
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
          Arg.createIntLit(arrayIndex), Types.isArrayRef(arrType));
    } else {
      // Handle the general case where the index must be computed
      Var indexVar = eval(context, arrayIndexTree,
          Types.F_INT, false, renames);
      backend.arrayLookupFuture(lookupIntoVar, arrayVar, indexVar,
                                Types.isArrayRef(arrType));
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
  private Var lookupStructField(Context context, SwiftAST tree,
      Type type, boolean storeInStack, Var outVar, 
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

    
    Var parent;
    SwiftAST parentTree = tree.child(0);
    String fieldName = tree.child(1).getText();

    if (parentTree.getType() == ExMParser.VARIABLE) {
      parent = context.getDeclaredVariable(parentTree.child(0).getText());
    } else {
      Type parentType = TypeChecker.findSingleExprType(context, parentTree);
      // Type error should have been caught earlier
      assert(Types.isStruct(parentType) || Types.isStructRef(parentType));
      parent = eval(context, parentTree, parentType, false, renames);
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
    Var rootStruct = null;
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
    Var tmp = structLookup(context, parent, fieldName,
        storeInStack, rootStruct, pathFromRoot);
    return derefOrCopyResult(context, tmp, outVar);
  }

  
  private Var derefOrCopyResult(Context context, Var lookupResult,
      Var outVar) throws UndefinedTypeException, UserException {
    if (outVar == null) {
      return lookupResult;
    } else if (Types.isRefTo(lookupResult.type(), outVar.type())) {
      dereference(context, outVar, lookupResult);
      return outVar;
    } else {
      copyByValue(context, lookupResult, outVar, outVar.type());
      return outVar;
    }
  }

  
  private void arrayRange(Context context, SwiftAST tree, Var oVar,
      Map<String, String> renames) throws UserException {
    assert(Types.isArray(oVar.type()));
    assert(Types.isInt(oVar.type().memberType()));
    ArrayRange ar = ArrayRange.fromAST(context, tree);
    ar.typeCheck(context);
    
    Var startV = eval(context, ar.getStart(), Types.F_INT, 
                      false, null);
    Var endV = eval(context, ar.getEnd(), Types.F_INT, 
        false, null);

   if (ar.getStep() != null) {
      Var stepV = eval(context, ar.getStep(), Types.F_INT, 
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
  private void arrayElems(Context context, SwiftAST tree, Var oVar,
    Map<String, String> renames) throws UserException {
    assert(Types.isArray(oVar.type()));
    ArrayElems ae = ArrayElems.fromAST(context, tree);
    Type arrType = TypeChecker.findSingleExprType(context, tree);
    assert(Types.isArray(arrType) || Types.isUnion(arrType));
    assert(arrType.assignableTo(oVar.type()));
    
    Type memType = oVar.type().memberType();
    /** Evaluate all the members */
    List<Var> computedMembers = new ArrayList<Var>();
    for (int i = 0; i < ae.getMemberCount(); i++) {
      SwiftAST mem = ae.getMember(i);
      Var computedMember = eval(context, mem, 
          memType, false, renames);
      computedMembers.add(computedMember);
    }
    backend.arrayBuild(oVar, computedMembers, false);
  }

  private void callFunction(Context context, String function,
      FunctionType concrete,
      List<Var> oList, List<Var> iList, Var priorityVal)
      throws UndefinedTypeException, UserException {

    // The expected types might not be same as current input types, work out
    // what we need to do to make theme the same
    ArrayList<Var> realIList = new ArrayList<Var>(iList.size());
    ArrayList<Var> derefVars = new ArrayList<Var>();
    ArrayList<Var> waitVars = new ArrayList<Var>();
    Context waitContext = null;

    assert(concrete.getInputs().size() == iList.size());
    for (int i = 0; i < iList.size(); i++) {
      Var input = iList.get(i);
      Type inputType = input.type();
      Type expType = concrete.getInputs().get(i);
      if (inputType.getImplType().equals(expType.getImplType())) {
        realIList.add(input);
      } else if (Types.isRefTo(inputType, expType)) {
        if (waitContext == null) {
          waitContext = new LocalContext(context);
        }
        Var derefed;
        derefed = waitContext.createAliasVariable(expType);
        waitVars.add(input);
        derefVars.add(derefed);
        realIList.add(derefed);
      } else if (Types.isUpdateableEquiv(inputType, expType)) {
        realIList.add(snapshotUpdateable(context, input));
      } else {
        throw new STCRuntimeError(context.getFileLine() + 
                " Shouldn't be here, don't know how to"
            + " convert " + inputType.toString() + " to " + expType.toString());
      }
    }

    if (waitContext != null) {
      FunctionContext fc = context.getFunctionContext();
      List<Var> usedVars = new ArrayList<Var>();
      usedVars.addAll(iList); usedVars.addAll(oList);
      List<Var> keepOpen = RefCounting.filterWriteRefcount(oList);
      backend.startWaitStatement(
           fc.constructName("call-" + function),
           waitVars, usedVars, keepOpen, 
           priorityVal == null ? null : Arg.createVar(priorityVal),
           WaitMode.DATA_ONLY, false, TaskMode.LOCAL_CONTROL);

      assert(waitVars.size() == derefVars.size());
      // Generate code to fetch actual array IDs  inside
      // wait statement
      for (int i = 0; i < waitVars.size(); i++) {
        Var derefVar = derefVars.get(i);
        varCreator.declare(derefVar);
        if (Types.isArrayRef(waitVars.get(i).type())) {
          backend.retrieveRef(derefVar, waitVars.get(i));
        } else {
          throw new STCRuntimeError("Don't know how to " +
              "deref non-array function arg " + derefVar);
        }
      }
    }

    backendFunctionCall(context, function, oList, realIList, priorityVal);

    if (waitContext != null) {
      backend.endWaitStatement();
    }
  }

  /**
   * Generate backend instruction for function call
   * @param context
   * @param function name of function
   * @param oList list of output variables
   * @param iList list of input variables (with correct types)
   * @param priorityVal optional priority value (can be null)
   */
  private void backendFunctionCall(Context context, String function,
      List<Var> oList, ArrayList<Var> iList, Var priorityVal) {
    assert(priorityVal == null ||
           priorityVal.type().equals(Types.V_INT)); 
    Arg priority = priorityVal != null ? Arg.createVar(priorityVal) : null;
    FunctionType def = context.lookupFunction(function);
    if (def == null) {
      throw new STCRuntimeError("Couldn't locate function definition for " +
          "previously defined function " + function);
    }
    if (context.hasFunctionProp(function, FnProp.BUILTIN)) {
      if (Builtins.hasOpEquiv(function)) {
        assert(oList.size() <= 1);
        Var out = oList.size() == 0 ? null : oList.get(0);
        backend.asyncOp(Builtins.getOpEquiv(function), out, 
                        Arg.fromVarList(iList), priority);
      } else {
        backend.builtinFunctionCall(function, iList, oList, priority);
      }
    } else if (context.hasFunctionProp(function, FnProp.COMPOSITE)) {
      TaskMode mode;
      if (context.hasFunctionProp(function, FnProp.SYNC)) {
        mode = TaskMode.SYNC;
      } else {
        mode = TaskMode.CONTROL;
      }
      backend.functionCall(function, iList, oList, null, 
          mode, priority);
    } else {
      assert(context.hasFunctionProp(function, FnProp.APP));
      // Execute app function wrapper locally (real work will
      //   be dispatched to worker by wrapper)
      backend.functionCall(function, iList, oList, null,
              TaskMode.LOCAL, priority);
    }
  }


  private void assignIntLit(Context context, SwiftAST tree,
                            Var oVar, String value)
 throws UserException {
   LogHelper.trace(context, oVar.toString()+"="+value);
   if(Types.isInt(oVar.type())) {
     backend.assignInt(oVar, Arg.createIntLit(Long.parseLong(value)));
   } else if (Types.isFloat(oVar.type())) {
     double floatval = Literals.interpretIntAsFloat(context, value);
     backend.assignFloat(oVar, Arg.createFloatLit(floatval));
     
   } else {
     assert false : "assignIntLit to variable" + oVar;
   }
  }

  private void assignBoolLit(Context context, SwiftAST tree, Var oVar,
      String value) throws UserException {
   assert(Types.isBool(oVar.type()));
   backend.assignBool(oVar, Arg.createBoolLit(Boolean.parseBoolean(value)));
  }

  private void assignFloatLit(Context context, SwiftAST tree, Var oVar) 
  throws UserException {
   assert(Types.isFloat(oVar.type()));
   double val = Literals.extractFloatLit(context, tree);
   backend.assignFloat(oVar, Arg.createFloatLit(val));
  }

  private void assignStringLit(Context context, SwiftAST tree, Var oVar,
      String value) throws UserException {
    assert(Types.isString(oVar.type()));
    backend.assignString(oVar, Arg.createStringLit(value));
  }

  private void assignVariable(Context context, Var oVar,
      Var src) throws UserException {
    if (Types.isScalarUpdateable(src.type())) {
      Var snapshot = snapshotUpdateable(context, src);
      src = snapshot;
    }
    
    Type srctype = src.type();
    Type dsttype = oVar.type();
    TypeChecker.checkCopy(context, srctype, dsttype);

    copyByValue(context, src, oVar, srctype);
  }

  private Var snapshotUpdateable(Context context, Var src)
      throws UserException, UndefinedTypeException {
    assert(Types.isScalarUpdateable(src.type()));
    // Create a future alias to the updateable type so that
    // types match
    Var val = varCreator.createTmpLocalVal(context,
        ScalarUpdateableType.asScalarValue(src.type()));

    backend.latestValue(val, src);
    /* Create a future with a snapshot of the value of the updateable
     * By making the retrieve and store explicit the optimizer should be
     * able to optimize out the future in many cases
     */
    Var snapshot = varCreator.createTmp(context,
        ScalarUpdateableType.asScalarFuture(src.type()));

    if (!src.type().equals(Types.UP_FLOAT)) {
      throw new STCRuntimeError(src.type() + " not yet supported");
    }
    backend.assignFloat(snapshot, Arg.createVar(val));
    return snapshot;
  }

  private void copyArrayByValue(Context context, Var dst, Var src) 
                                                throws UserException {
    assert(dst.type().equals(src.type()));
    assert(Types.isArray(src.type()));
    LocalContext copyContext = new LocalContext(context);
    Type t = src.type();
    Type memType = Types.getArrayMemberType(t);
    Var member = copyContext.createAliasVariable(memType);
    Var ix = copyContext.createLocalValueVariable(Types.V_INT);
    
    List<Var> keepOpen = Arrays.asList(dst);
    List<Var> usedVars = Arrays.asList(src, dst);
    List<Var> waitVars = Arrays.asList(src);
    backend.startWaitStatement(
        context.getFunctionContext().constructName("arrcopy-wait"),
        waitVars, usedVars, keepOpen, null,
        WaitMode.DATA_ONLY, false, TaskMode.LOCAL);
    backend.startForeachLoop(
            context.getFunctionContext().constructName("arrcopy"),
            src, member, ix, -1, true, usedVars, keepOpen);
    backend.arrayInsertImm(member, dst, Arg.createVar(ix));
    backend.endForeachLoop();
    backend.endWaitStatement();
  }

  private void copyStructByValue(Context context,
      Var srcRoot, Var dstRoot,
      Stack<String> srcPath, Stack<String> dstPath,
      Var src, Var dst, Type type)
          throws UserException, UndefinedTypeException {
    assert(src.type().equals(dst.type()));
    assert(Types.isStruct(src.type()));

    // recursively copy struct members
    StructType st = (StructType) type;
    for (StructField f : st.getFields()) {
      // get handles to both src and dst field
      Type fieldType = f.getType();
      srcPath.push(f.getName());
      dstPath.push(f.getName());
      Var fieldSrc = structLookup(context, src, f.getName(),
          false, srcRoot, srcPath);

      Var fieldDst = structLookup(context, dst, f.getName(),
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
  private void dereferenceStruct(Context context, Var dst, Var src)
      throws UserException, UndefinedTypeException {
    List<Var> usedVars = Arrays.asList(src, dst);
    List<Var> waitVars = Arrays.asList(src);
    backend.startWaitStatement( 
                    context.getFunctionContext().constructName("copystruct"), 
                    waitVars, usedVars, 
                    new ArrayList<Var>(), null, WaitMode.DATA_ONLY,
                    false, TaskMode.LOCAL);
    Var rValDerefed = varCreator.createTmp(context, 
            src.type().memberType(), false, true);
    backend.retrieveRef(rValDerefed, src);
    copyByValue(context, rValDerefed, dst, dst.type());
    backend.endWaitStatement();
  }
 



  void flagDeclaredVarForClosing(Context context, Var var)
                            throws UndefinedTypeException, UserException {
    List<Pair<Var, VInfo>> foundArrs = null;

    if (Types.isArray(var.type())) {
      foundArrs = Collections.singletonList(Pair.create(var, (VInfo)null));
    } else if (Types.isStruct(var.type())) {
      // Might have to dig into struct and load members to see if we 
      // should close it
      foundArrs = new ArrayList<Pair<Var, VInfo>>();
      findArraysInStruct(context, var, null, foundArrs);
    }
    
    if (foundArrs != null) {        
      for (Pair<Var, VInfo> p: foundArrs) {
        Var arr = p.val1;
        context.flagArrayForClosing(arr);
      }
    }
  }
  
  
  void findArraysInStruct(Context context,
      Var root, VInfo structVInfo, List<Pair<Var, VInfo>> arrays)
          throws UndefinedTypeException, UserException {
    findArraysInStructToClose(context, root, root, structVInfo,
        new Stack<String>(), arrays);
  }

  private void findArraysInStructToClose(Context context,
      Var root, Var struct, VInfo structVInfo,
      Stack<String> fieldPath, List<Pair<Var, VInfo>> arrays) throws UndefinedTypeException,
                                                                      UserException {
    StructType vtype = (StructType)struct.type();
    for (StructField f: vtype.getFields()) {
      fieldPath.push(f.getName());
      if (Types.isArray(f.getType())) {
        Var fieldVar = structLookup(context, struct, 
            f.getName(), false, root, fieldPath);
        VInfo fieldInfo = structVInfo != null ?
            structVInfo.getFieldVInfo(f.getName()) : null;
        arrays.add(Pair.create(fieldVar, fieldInfo));
      } else if (Types.isStruct(f.getType())) {
        VInfo nestedVInfo = structVInfo != null ?
            structVInfo.getFieldVInfo(f.getName()) : null;
        assert(nestedVInfo != null || structVInfo == null);
        Var field = structLookup(context, struct, f.getName(),
                                  false, root, fieldPath);

        findArraysInStructToClose(context, root, field, nestedVInfo, fieldPath,
            arrays);
      }
      fieldPath.pop();
    }
  }
}
