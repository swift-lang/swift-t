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

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.ForeignFunctions.SpecialFunction;
import exm.stc.common.lang.Intrinsics;
import exm.stc.common.lang.Intrinsics.IntrinsicFunction;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.Op;
import exm.stc.common.lang.Operators.OpInputType;
import exm.stc.common.lang.TaskProp;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.ScalarUpdateableType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.TupleType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.frontend.Context.FnProp;
import exm.stc.frontend.TypeChecker.MatchedOp;
import exm.stc.frontend.VariableUsageInfo.VInfo;
import exm.stc.frontend.tree.ArrayElems;
import exm.stc.frontend.tree.ArrayRange;
import exm.stc.frontend.tree.FunctionCall;
import exm.stc.frontend.tree.FunctionCall.FunctionCallKind;
import exm.stc.frontend.tree.Literals;
import exm.stc.ic.STCMiddleEnd;

/**
 * This module contains logic to walk individual expression in Swift and generate code to evaluate them
 */
public class ExprWalker {

  private final VarCreator varCreator;
  private final WrapperGen wrappers;
  private final STCMiddleEnd backend;
  private final LoadedModules modules;

  public ExprWalker(WrapperGen wrappers, VarCreator creator,
                    STCMiddleEnd backend, LoadedModules modules) {
    this.wrappers = wrappers;
    this.varCreator = creator;
    this.backend = backend;
    this.modules = modules;
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
    syncFilePos(context, tree);

    if (token == ExMParser.CALL_FUNCTION) {
      callFunctionExpression(context, tree, oList, renames);
      return;
    } else if (token == ExMParser.TUPLE) {
      tupleExpression(context, tree, oList, renames);
      return;
    }

    assert(oList.size() == 1): "Cannot assign expression type " +
            LogHelper.tokName(token) + " to multiple variables";

    Var oVar = oList.get(0);
    assert(oVar.type().isConcrete()) : oVar.type();

    switch (token) {
      case ExMParser.VARIABLE:
        String srcVarName = tree.child(0).getText();
        if (renames != null &&
            renames.containsKey(srcVarName)) {
          srcVarName = renames.get(srcVarName);
        }

        Var srcVar = context.lookupVarUser(srcVarName);

        if (oVar.name().equals(srcVar.name())) {
          throw new UserException(context, "Assigning variable " +
                oVar.name() + " to itself");

        }
        assignVariable(context, oVar, srcVar);
        break;

      case ExMParser.INT_LITERAL:
        assignIntLit(context, oVar, Literals.extractIntLit(context, tree));
        break;

      case ExMParser.FLOAT_LITERAL:
        assignFloatLit(context, tree, oVar);
        break;

      case ExMParser.STRING_LITERAL:
        assignStringLit(context, tree, oVar);
        break;

      case ExMParser.BOOL_LITERAL:
        assignBoolLit(context, tree, oVar);
        break;

      case ExMParser.OPERATOR:
        // Handle unary negation as special case
        Long intLit = Literals.extractIntLit(context, tree);
        Double floatLit = Literals.extractFloatLit(context, tree);
        if (intLit != null) {
          assignIntLit(context, oVar, intLit);
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
        structLoad(context, tree, oVar.type(), false, oVar, renames);
        break;

      case ExMParser.ARRAY_RANGE:
        arrayRange(context, tree, oVar, renames);
        break;
      case ExMParser.ARRAY_ELEMS:
      case ExMParser.ARRAY_KV_ELEMS:
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
    syncFilePos(context, tree);
    if (tree.getType() == ExMParser.VARIABLE) {
      // Base case: don't need to create new variable
      String varName = tree.child(0).getText();
      if (renames != null && renames.containsKey(varName)) {
        varName = renames.get(varName);
      }
      Var var = context.lookupVarUser(varName);
      // Check to see that the current variable's storage is adequate
      // Might need to convert type, can't do that here
      if ((var.storage() == Alloc.STACK || (!storeInStack))
              && var.type().equals(type)) {
        return var;
      }
    }

    if (tree.getType() == ExMParser.STRUCT_LOAD && Types.isStruct(
                TypeChecker.findExprType(context, tree.child(0)))) {
      return structLoad(context, tree, type, storeInStack, null, renames);
    } else {
      Var tmp = varCreator.createTmp(context, type, storeInStack, false);
      LogHelper.debug(context, "Create tmp " + tmp + " to eval expr " +
                      LogHelper.tokName(tree.getType()));
      evalToVars(context, tree, tmp.asList(), renames);
      return tmp;
    }
  }

  /**
   * Evaluate 1..n expressions to temporary variables.
   * In 1 cases, expression directly evaluated.
   * In 2+ cases, assumed to be tuple.
   * @param context
   * @param tree
   * @param type expression type, may be tuple type
   * @param storeInStack
   * @param renames
   * @return
   * @throws UserException
   */
  public List<Var> evalMulti(Context context, SwiftAST tree, List<Type> types,
      boolean storeInStack, Map<String, String> renames) throws UserException {
    int n = types.size();
    assert(n >= 1);

    if (n == 1) {
      return eval(context, tree, types.get(0), storeInStack, renames).asList();
    }

    List<Var> results = new ArrayList<Var>(n);
    assert(tree.getType() == ExMParser.TUPLE);
    assert(n == tree.childCount());
    for (int i = 0; i < n; i++) {
      results.add(eval(context, tree.child(i), types.get(i), storeInStack, renames));
    }

    return results;
  }

  /**
   * Do a by-value copy from src to dst
   *
   * @param context
   * @param dst
   * @param src
   * @param type
   * @throws UserException
   */
  public void copyByValue(Context context, Var dst, Var src)
                           throws UserException {
    assert(src.type().assignableTo(dst.type()));

    Var backendSrc = VarRepr.backendVar(src);
    Var backendDst = VarRepr.backendVar(dst);
    if (Types.simpleCopySupported(src)) {
       backendAsyncCopy(context, dst, src);
    } else if (Types.isFile(src)) {
      copyFileByValue(dst, src);
    } else {
      throw new STCRuntimeError(context.getFileLine() +
          " copying type " + src + " by value not yet "
          + " supported by compiler");
    }
  }

  /**
   *
   * @param context
   * @param structVar Variable of type struct or struct ref
   * @param fieldName Path from struct to lookup
   * @param storeInStack
   * @return the contents of the struct field if structVar is a non-ref,
   *        a reference to the contents of the struct field if structVar is
   *        a ref
   * @throws UserException
   * @throws UndefinedTypeException
   */
  public Var structLookup(Context context, Var struct,
      List<String> fieldPath, boolean storeInStack, Var outVar)
          throws UserException,
      UndefinedTypeException {
    assert(struct != null);
    assert(fieldPath != null);
    assert(fieldPath.size() > 0);
    Type fieldType = TypeChecker.findStructFieldType(context, fieldPath,
                                                   struct.type());
    boolean storedAsRef = VarRepr.storeRefInStruct(fieldType);

    Var result;
    Var backendStruct = VarRepr.backendVar(struct);

    Type resultType = TypeChecker.structLoadResultType(struct.type(), fieldType);

    if (Types.isStructRef(struct)) {
      if (outVar == null || !resultType.assignableTo(outVar.type())) {
        result = varCreator.createStructFieldTmp(context,
            struct, resultType, fieldPath, Alloc.TEMP);
      } else {
        result = outVar;
      }
      backend.structRefCopyOut(VarRepr.backendVar(result),
                               backendStruct, fieldPath);
    } else {
      assert(Types.isStruct(struct));
      if (storedAsRef)  {
        // Copy out reference once it's set (field maybe not initialized)
        result = varCreator.createStructFieldTmp(context, struct, resultType,
                                                 fieldPath, Alloc.TEMP);

        backend.structCopyOut(VarRepr.backendVar(result), backendStruct,
                              fieldPath);
      } else if (!storedAsRef && outVar == null) {
        // Just create alias to data in struct for later use
        result = structCreateAlias(context, struct, fieldPath);
      } else {
        assert(!storedAsRef && outVar != null);
        // Copy data in struct to existing output variable
        backend.structCopyOut(VarRepr.backendVar(outVar),
                              backendStruct, fieldPath);
        result = outVar;
      }
    }

    // If necessary, copy result to outVar
    if (result == outVar) {
      return outVar;
    } else {
      return derefOrCopyResult(context, result, outVar);
    }
  }

  public Var structLookup(Context context, Var struct,
      List<String> fieldPath, boolean storeInStack)
          throws UserException, UndefinedTypeException {
    return structLookup(context, struct, fieldPath, storeInStack, null);
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
    assert(Types.isRef(src));
    assert(Types.isAssignableRefTo(src, dst));

    Var backendDst = VarRepr.backendVar(dst);
    Var backendSrc = VarRepr.backendVar(src);

    Type dstType = dst.type();
    if (Types.isScalarFuture(dstType)) {
      backend.derefScalar(backendDst, backendSrc);
    } else if (Types.isFile(dstType)) {
      backend.derefFile(backendDst, backendSrc);
    } else if (Types.isContainer(dstType)) {
      derefThenCopyContainer(context, dst, src);
    } else if (Types.isStruct(dstType)) {
      dereferenceStruct(context, dst, src);
    } else {
      throw new STCRuntimeError("Can't dereference type " + src.type());
    }
  }

  public void assign(Var dst, Arg src) {
    assert(src.type().assignableTo(Types.retrievedType(dst))) :
                      dst + " = " + src;
    Var backendDst = VarRepr.backendVar(dst);
    Arg backendSrc = VarRepr.backendArg(src);
    if (Types.isScalarFuture(dst)) {
      backend.assignScalar(backendDst, backendSrc);
    } else if (Types.isArray(dst)) {
      backend.assignArray(backendDst, backendSrc);
    } else if (Types.isBag(dst)) {
      backend.assignBag(backendDst, backendSrc);
    } else if (Types.isStruct(dst)) {
      backend.assignStruct(backendDst, backendSrc);
    } else if (Types.isFile(dst)) {
      // TODO: is it right to always assign filename here?
      assignFile(dst, src, Arg.TRUE);
    } else {
      throw new STCRuntimeError("Can't assign: " + dst);
    }
  }

  public void assignFile(Var dst, Arg src, Arg setFilename) {
    Var backendDst = VarRepr.backendVar(dst);
    Arg backendSrc = VarRepr.backendArg(src);
    Arg backendSetFileName = VarRepr.backendArg(setFilename);
    backend.assignFile(backendDst, backendSrc, backendSetFileName);
  }

  public void retrieve(Var dst, Var src) {
    Var backendDst = VarRepr.backendVar(dst);
    Var backendSrc = VarRepr.backendVar(src);
    if (Types.isScalarFuture(src)) {
      backend.retrieveScalar(backendDst, backendSrc);
    } else if (Types.isFile(src)) {
      backend.retrieveFile(backendDst, backendSrc);
    } else if (Types.isArray(src)) {
      // TODO: recursively?
      backend.retrieveArray(backendDst, backendSrc);
    } else if (Types.isBag(src)) {
      // TODO: recursively?
      backend.retrieveBag(backendDst, backendSrc);
    } else if (Types.isStruct(src)) {
      // TODO: recursively?
      backend.retrieveStruct(backendDst, backendSrc);
    } else {
      throw new STCRuntimeError("Don't know how to fetch " + src);
    }
  }

  /**
   * Create a value variable and retrieve value of future into it
   * @param context
   * @param future
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws DoubleDefineException
   */
  public Var retrieveToVar(Context context, Var future)
      throws UserException, UndefinedTypeException, DoubleDefineException {
    Var val = varCreator.createValueOfVar(context, future);
    retrieve(val, future);
    return val;
  }

  public Var retrieveContainerValues(Context context, Var c)
          throws UserException {
    assert(Types.isContainer(c));
    Type unpackedT = Types.unpackedType(c);
    Var val = varCreator.createValueVar(context, unpackedT, c, true);
    backend.retrieveRecursive(VarRepr.backendVar(val), VarRepr.backendVar(c));
    // TODO: recursively free e.g. blobs in list
    return val;
  }

  public void retrieveRef(Var dst, Var src, boolean mutable) {
    backend.retrieveRef(VarRepr.backendVar(dst), VarRepr.backendVar(src),
                        mutable);
  }

  public void assignRef(Var dst, Var src, boolean mutable) {
    // Only assign single refcounts
    long readRefs = 1;
    long writeRefs = mutable ? 1 : 0;
    backend.assignRef(VarRepr.backendVar(dst), VarRepr.backendVar(src),
                      readRefs, writeRefs);
  }

  /**
   * Create a future of the appropriate type for the argument
   * @param bodyContext
   * @param value
   * @param mutableRef if storing to a ref, does it need to be mutable
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  public Var assignToVar(Context bodyContext, Arg value, boolean mutableRef)
      throws UserException, UndefinedTypeException {
    assert(value.isConst() || value.getVar().storage() == Alloc.LOCAL);
    Type resultType = Types.storeResultType(value, mutableRef);
    Var result = varCreator.createTmp(bodyContext, resultType);
    assign(result, value);
    return result;
  }

  /**
   * emit intermediate code for an async op
   * @param op
   * @param out
   * @param inputs
   */
  public void asyncOp(BuiltinOpcode op, Var out, List<Arg> inputs) {
    backend.asyncOp(op, VarRepr.backendVar(out), VarRepr.backendArgs(inputs));
  }

  public void localOp(BuiltinOpcode op, Var out, List<Arg> inputs) {
    backend.localOp(op, VarRepr.backendVar(out), VarRepr.backendArgs(inputs));
  }

  /**
   * Synchronize file position to line mapping
   * @param context
   * @param tree
   */
  private void syncFilePos(Context context, SwiftAST tree) {
    context.syncFilePos(tree, modules.currentModule().moduleName(),
                        modules.currLineMap());
  }

  private void callOperator(Context context, SwiftAST tree,
      Var out, Map<String, String> renames) throws UserException {
    String opName = tree.child(0).getText();
    int op_argcount = tree.getChildCount() - 1;

    // Use the AST token label to find the actual operator
    MatchedOp opMatch = TypeChecker.getOpFromTree(context, tree, out.type());
    Op op = opMatch.op;
    List<Type> exprTypes = opMatch.exprTypes;

    List<OpInputType> inArgs = op.type.in();
    int argcount = inArgs.size();

    if (op_argcount != argcount) {
      throw new STCRuntimeError("Operator " + opName + " has " + op_argcount
                             + " arguments in AST, but expected" + argcount);
    }

    ArrayList<Arg> iList = new ArrayList<Arg>(argcount);
    for (int i = 0; i < op_argcount; i++) {
      OpInputType inArg = inArgs.get(i);
      Type exprType = exprTypes.get(i);
      if (inArg.variadic) {
        // Flatten out variadic args list
        List<Var> args =  evalMulti(context, tree.child(i + 1),
                 TupleType.getFields(exprType), false, renames);
        for (Var arg: args) {
          iList.add(Arg.newVar(arg));
        }
      } else {
        // Store into temporary variables
        Var arg = eval(context, tree.child(i + 1), exprType, false, renames);
        iList.add(Arg.newVar(arg));
      }
    }
    asyncOp(op.code, out, iList);
  }

  /**
   * Generate code for a call to a function, where the arguments might be
   * expressions
   *
   * @param context
   * @param tree
   * @param oList
   * @throws UserException
   * @throws UndefinedVarError
   * @throws UndefinedFunctionException
   */
  private void callFunctionExpression(Context context, SwiftAST tree,
      List<Var> oList, Map<String, String> renames) throws UserException {
    assert(tree.getType() == ExMParser.CALL_FUNCTION);

    FunctionCall f = FunctionCall.fromAST(context, tree, true);

    // This will check the type of the function call
    FunctionType concrete = TypeChecker.concretiseFunctionCall(context,
                                f.function(), f.type(), f.args(), oList);

    // If this is an assert statement, disable it
    if (context.getForeignFunctions().isAssertVariant(f.function()) &&
            Settings.getBooleanUnchecked(Settings.OPT_DISABLE_ASSERTS)) {
      return;
    }

    // evaluate argument expressions left to right, creating temporaries
    ArrayList<Var> argVars = new ArrayList<Var>(f.args().size());


    for (int i = 0; i < f.args().size(); i++) {
      SwiftAST argtree = f.args().get(i);
      Type expType = concrete.getInputs().get(i);

      Type exprType = TypeChecker.findExprType(context, argtree);
      Type argType = TypeChecker.concretiseFnArg(context, f.function(), i,
                                      expType, exprType).val2;
      argVars.add(eval(context, argtree, argType, false, renames));
    }

    // Process priority after arguments have been evaluated, so that
    // the argument evaluation is outside the wait statement
    TaskProps propVals = new TaskProps();
    boolean openedWait = false;
    Context callContext = context;
    if (!f.annotations().isEmpty()) {
      List<Pair<TaskPropKey, Var>> propFutures =
            new ArrayList<Pair<TaskPropKey, Var>>();
      List<Var> waitVars = new ArrayList<Var>();
      for (TaskPropKey ann: f.annotations().keySet()) {
        checkCallAnnotation(context, f, ann);

        SwiftAST expr = f.annotations().get(ann);
        Type exprType = TypeChecker.findExprType(callContext, expr);
        Type concreteType = TaskProp.checkFrontendType(callContext, ann, exprType);
        Var future = eval(context, expr, concreteType, false, renames);
        waitVars.add(future);
        propFutures.add(Pair.create(ann, future));
      }

      backend.startWaitStatement(context.constructName("ann-wait"),
              VarRepr.backendVars(waitVars),
              WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedControl());
      openedWait = true;
      callContext = LocalContext.fnSubcontext(context);
      for (Pair<TaskPropKey,Var> x: propFutures) {
        Var value = retrieveToVar(callContext, x.val2);
        propVals.put(x.val1, value.asArg());
      }

      if (f.softLocation()) {
        propVals.put(TaskPropKey.SOFT_LOCATION, Arg.TRUE);
      }
    }

    callFunction(context, f.function(), f.kind(), concrete, oList, argVars,
                 propVals);
    if (openedWait) {
      backend.endWaitStatement();
    }

  }

  private void checkCallAnnotation(Context context, FunctionCall f,
      TaskPropKey ann) throws UserException {
    if (context.isIntrinsic(f.function())) {
      // Handle annotation specially
      IntrinsicFunction intF = context.lookupIntrinsic(f.function());
      List<TaskPropKey> validProps = Intrinsics.validProps(intF);
      if (!validProps.contains(ann)) {
          throw new InvalidAnnotationException(context, "Cannot specify " +
                "property " + ann + " for intrinsic function " + f.function());
      }
    } else if (ann == TaskPropKey.PARALLELISM) {
      if (!context.hasFunctionProp(f.function(), FnProp.PARALLEL)) {
        throw new UserException(context, "Tried to call non-parallel"
            + " function " + f.function() + " with parallelism.  "
            + " Maybe you meant to annotate the function definition with "
            + "@" + Annotations.FN_PAR);
      }
    } else if (ann == TaskPropKey.LOCATION) {
      if (!context.hasFunctionProp(f.function(), FnProp.TARGETABLE)) {
        throw new UserException(context, "Tried to call non-targetable"
            + " function " + f.function() + " with target");
      }
    }
  }

  /**
   * Evaluate a tuple expression.
   * @param context
   * @param tree
   * @param oList
   * @param renames
   * @throws UserException
   */
  private void tupleExpression(Context context, SwiftAST tree,
      List<Var> oList, Map<String, String> renames) throws UserException {
    assert(tree.getType() == ExMParser.TUPLE);
    int n = tree.childCount();
    assert(n == oList.size()) : "Assignment count mismatch not caught in type checking";

    for (int i = 0; i < n; i++) {
      evalToVars(context, tree.child(i), oList.get(i).asList(), renames);
    }
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
    Type arrExprType = TypeChecker.findExprType(context, arrayTree);
    Type arrType = chooseArrayTypeForLookup(oVar, arrExprType);

    // Evaluate the array
    Var arrayVar = eval(context, arrayTree, arrType, false, renames);

    // Any integer expression can index into array
    SwiftAST arrayIndexTree = tree.child(1);
    Type indexType = TypeChecker.findExprType(context, arrayIndexTree);
    if (!Types.isArrayKeyFuture(arrayVar, indexType)) {
      throw new TypeMismatchException(context,
            "array index expression does not have appropriate key type "
          + "for key of array type " + arrayVar.type() + ".  Type of index "
          + "expression was " + indexType.typeName());
    }

    Var backendArray = VarRepr.backendVar(arrayVar);
    Type backendElemType =
        TypeChecker.containerElemType(backendArray, false);

    Var backendOVar = VarRepr.backendVar(oVar);

    // The direct output of the array op
    Var copyDst;
    boolean mustDereference;
    if (backendElemType.assignableTo(backendOVar.type())) {
      // Want to copy out
      copyDst = oVar;
      mustDereference = false;
    } else {
      assert(Types.isRefTo(backendElemType, backendOVar)) :
            backendElemType + " => " + backendOVar;
      // Need to dereference into temporary var
      Type readOnlyElemType = TypeChecker.containerElemType(arrayVar, false);
      copyDst = varCreator.createTmp(context,
              new RefType(readOnlyElemType, false));
      mustDereference = true;
    }


    Var backendCopyDst = VarRepr.backendVar(copyDst);
    Long arrayIndex = Literals.extractIntLit(context, arrayIndexTree);
    if (arrayIndex != null) {
      // Handle the special case where the index is a constant.
      if (Types.isArrayRef(arrType)) {
        backend.arrayRefCopyOutImm(backendCopyDst,
            backendArray, Arg.newInt(arrayIndex));
      } else {
        backend.arrayCopyOutImm(backendCopyDst,
                backendArray, Arg.newInt(arrayIndex));
      }
    } else {
      // Handle the general case where the index must be computed
      Var indexVar = eval(context, arrayIndexTree,
                          Types.arrayKeyType(arrayVar), false, renames);
      if (Types.isArrayRef(arrType)) {
        backend.arrayRefCopyOutFuture(backendCopyDst, backendArray,
              VarRepr.backendVar(indexVar));
      } else {
        backend.arrayCopyOutFuture(backendCopyDst, backendArray,
                VarRepr.backendVar(indexVar));
      }
    }
    // Do the dereference down here so that it is generated in a more logical
    // order
    if (mustDereference) {
      dereference(context, oVar, copyDst);
    }
  }

  private Type chooseArrayTypeForLookup(Var oVar, Type arrExprType) {
    for (Type altType: UnionType.getAlternatives(arrExprType)) {
      assert(Types.isArray(altType) || Types.isArrayRef(altType));
      Type lookupRes = VarRepr.containerElemRepr(
                                Types.containerElemType(altType), false);
      if (lookupRes.equals(oVar.type())) {
        return altType;
      }
    }
    throw new STCRuntimeError("No viable array type for lookup up "
              + arrExprType + " into " + oVar);
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
  private Var structLoad(Context context, SwiftAST tree,
      Type type, boolean storeInStack, Var outVar,
      Map<String, String> renames) throws UndefinedTypeException,
      UserException {
    LogHelper.debug(context, "Eval struct lookup into " + outVar);

    if (storeInStack) {
      throw new STCRuntimeError("Dont know how to store results of "
          + " struct lookup in stack");
    }

    // Check if the field is cached
    assert (tree.getType() == ExMParser.STRUCT_LOAD);
    assert (tree.getChildCount() == 2);

    SwiftAST structTree = tree.child(0);
    LinkedList<String> pathFromRoot = new LinkedList<String>();
    pathFromRoot.addFirst(tree.child(1).getText());
    /*
     * Walk the tree to find out the full path if we are accessing a nested
     * struct.  rootStruct should be the name of the outermost nested struct
     */
    while (structTree.getType() == ExMParser.STRUCT_LOAD) {
      assert (structTree.getChildCount() == 2);
      pathFromRoot.addFirst(structTree.child(1).getText());
      structTree = structTree.child(0);
    }

    Var rootStruct;
    if (structTree.getType() == ExMParser.VARIABLE) {
      // The root is a local variable
      assert (structTree.getChildCount() == 1);
      String structVarName = structTree.child(0).getText();
      rootStruct = context.lookupVarUser(structVarName);
    } else {
      Type parentType = TypeChecker.findExprType(context, structTree);
      // Type error should have been caught earlier
      assert(Types.isStruct(parentType) || Types.isStructRef(parentType));
      rootStruct = eval(context, structTree, parentType, false, renames);
    }

    return structLookup(context, rootStruct, pathFromRoot, storeInStack, outVar);
  }


  private Var derefOrCopyResult(Context context, Var lookupResult,
      Var outVar) throws UndefinedTypeException, UserException {
    try {
      if (outVar == null) {
        return lookupResult;
      } else if (Types.isRefTo(lookupResult, outVar)) {
        dereference(context, outVar, lookupResult);
        return outVar;
      } else {
        copyByValue(context, outVar, lookupResult);
        return outVar;
      }
    } catch (RuntimeException e) {
      Logging.getSTCLogger().debug("Failure while trying to get " +
                                    lookupResult + " into " + outVar);
      throw e;
    }
  }


  private void arrayRange(Context context, SwiftAST tree, Var oVar,
      Map<String, String> renames) throws UserException {
    assert(Types.isArray(oVar));
    ArrayRange ar = ArrayRange.fromAST(context, tree);
    Type rangeType = ar.rangeType(context);

    assert(rangeType.assignableTo(oVar.type().memberType()));

    Var startV = eval(context, ar.getStart(), rangeType, false, null);
    Var endV = eval(context, ar.getEnd(), rangeType, false, null);
    List<Var> inArgs;
    SpecialFunction fn;
    if (ar.getStep() != null) {
      Var stepV = eval(context, ar.getStep(), rangeType,  false, null);

      inArgs = Arrays.asList(startV, endV, stepV);
      if (Types.isInt(rangeType)) {
        fn = SpecialFunction.RANGE_STEP;
      } else {
        assert(Types.isFloat(rangeType));
        fn = SpecialFunction.RANGE_FLOAT_STEP;
      }
    } else {
      inArgs = Arrays.asList(startV, endV);
      if (Types.isInt(rangeType)) {
        fn = SpecialFunction.RANGE;
      } else {
        assert(Types.isFloat(rangeType));
        fn = SpecialFunction.RANGE_FLOAT;
      }
    }
    String impl = context.getForeignFunctions().findSpecialImpl(fn);
    if (impl == null) {
      throw new STCRuntimeError("could not find implementation for " + fn);
    }
    backend.builtinFunctionCall(impl, impl, VarRepr.backendVars(inArgs),
                                VarRepr.backendVars(oVar));
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
    assert(Types.isArray(oVar));
    ArrayElems ae = ArrayElems.fromAST(context, tree);
    Type arrType = TypeChecker.findExprType(context, tree);
    assert(Types.isArray(arrType) || Types.isUnion(arrType));
    assert(arrType.assignableTo(oVar.type()));

    Type keyType = Types.arrayKeyType(oVar);
    Type valType = Types.containerElemType(oVar);

    // Evaluate all the values
    List<Var> vals = new ArrayList<Var>(ae.getElemCount());
    for (SwiftAST val: ae.getVals()) {
      vals.add(eval(context, val, valType, false, renames));
    }

    Var backendOVar = VarRepr.backendVar(oVar);
    boolean elemIsRef = VarRepr.storeRefInContainer(Types.containerElemType(oVar));
    /* We can only use arrayBuild operation if we have the keys and values in
     * the appropriate format for the internal container representation.
     * If user specified keys, they will be futures so we can't use them here.
     * If the container representation stores values inline, can't use
     * those either. */
    if (ae.hasKeys()) {
      List<Var> backendKeys = new ArrayList<Var>(ae.getElemCount());
      for (SwiftAST key: ae.getKeys()) {
        Var keyVar = eval(context, key, keyType, false, renames);
        backendKeys.add(VarRepr.backendVar(keyVar));
      }

      for (int i = 0; i < ae.getElemCount(); i++) {
        Var backendVal = VarRepr.backendVar(vals.get(i));
        if (elemIsRef) {
          // Store reference to future
          backend.arrayStoreFuture(backendOVar, backendKeys.get(i),
                                   backendVal.asArg());
        } else {
          // Must copy from future into container
          backend.arrayCopyInFuture(backendOVar, backendKeys.get(i),
                                    backendVal);
        }
      }
    } else {
      assert(!ae.hasKeys());
      assert(Types.isInt(keyType));
      List<Arg> backendKeys = arrayElemsDefaultKeys(ae);
      if (elemIsRef) {
        // We know keys ahead of time and elem storage format matches our
        // input variables, use arrayBuild operation
        backend.arrayBuild(backendOVar, backendKeys,
                           VarRepr.backendVars(vals));
      } else {
        for (int i = 0; i < ae.getElemCount(); i++) {
          Var backendVal = VarRepr.backendVar(vals.get(i));
          if (elemIsRef) {
            // Store reference to future
            backend.arrayStore(backendOVar, backendKeys.get(i),
                                     backendVal.asArg());
          } else {
            // Must copy from future into container
            backend.arrayCopyInImm(backendOVar, backendKeys.get(i),
                                   backendVal);
          }
        }
      }
    }

  }

  private List<Arg> arrayElemsDefaultKeys(ArrayElems ae) {
    List<Arg> backendKeys = new ArrayList<Arg>(ae.getElemCount());
    for (int i = 0; i < ae.getElemCount(); i++) {
      backendKeys.add(Arg.newInt(i));
    }
    return backendKeys;
  }

  private void callFunction(Context context, String function,
      FunctionCallKind kind, FunctionType concrete,
      List<Var> oList, List<Var> iList, TaskProps props)
      throws UndefinedTypeException, UserException {

    // The expected types might not be same as current input types, work out
    // what we need to do to make them the same
    ArrayList<Var> realIList = new ArrayList<Var>(iList.size());
    ArrayList<Var> derefVars = new ArrayList<Var>();
    ArrayList<Var> waitVars = new ArrayList<Var>();
    Context waitContext = null;

    assert(concrete.getInputs().size() == iList.size());
    for (int i = 0; i < iList.size(); i++) {
      Var input = iList.get(i);
      Type inputType = input.type();
      Type expType = concrete.getInputs().get(i);
      if (inputType.getImplType().assignableTo(expType.getImplType())) {
        realIList.add(input);
      } else if (Types.isAssignableRefTo(inputType, expType)) {
        if (waitContext == null) {
          waitContext = LocalContext.fnSubcontext(context);
        }
        Var derefed;
        derefed = waitContext.createTmpAliasVar(expType);
        waitVars.add(input);
        derefVars.add(derefed);
        realIList.add(derefed);
        Logging.getSTCLogger().trace("Deref function input " + input +
                                     " to " + derefed);
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

      // Only want to maintain priority for wait
      TaskProps waitProps = props.filter(TaskPropKey.PRIORITY);
      backend.startWaitStatement( fc.constructName("call-" + function),
           VarRepr.backendVars(waitVars), WaitMode.WAIT_ONLY,
           false, false, ExecTarget.nonDispatchedControl(),
           VarRepr.backendProps(waitProps));

      assert(waitVars.size() == derefVars.size());
      // Generate code to fetch actual array IDs  inside
      // wait statement
      for (int i = 0; i < waitVars.size(); i++) {
        Var derefVar = derefVars.get(i);
        varCreator.backendInit(derefVar);
        assert(Types.isRef(waitVars.get(i).type()));
        retrieveRef(derefVar, waitVars.get(i), false);
      }
    }

    boolean checkpointed =
        context.hasFunctionProp(function, FnProp.CHECKPOINTED);

    if (checkpointed) {

      Var lookupEnabled = VarRepr.backendVar(
                varCreator.createTmpLocalVal(context, Types.V_BOOL));
      backend.checkpointLookupEnabled(lookupEnabled);

      backend.startIfStatement(lookupEnabled.asArg(), true);
      checkpointedFunctionCall(context, function, kind, concrete, oList,
                               realIList, props, true);
      backend.startElseBlock();
      checkpointedFunctionCall(context, function, kind, concrete, oList,
                                realIList, props, false);
      backend.endIfStatement();
    } else {
      backendFunctionCall(context, function, kind, concrete, oList, realIList,
                          props);
    }
    if (waitContext != null) {
      backend.endWaitStatement();
    }
  }


  private void checkpointedFunctionCall(Context context, String function,
      FunctionCallKind kind, FunctionType concrete, List<Var> oList, List<Var> iList,
      TaskProps props, boolean lookupCheckpoint) throws UserException {

    /*
     * wait (checkpoint_key_futures) {
     *   checkpoint_key = lookup(checkpoint_key_futures)
     *   checkpoint_exists, vals = lookup_checkpoint(checkpoint_key)
     *   if (checkpoint_exists) {
     *     ... Set output variables
     *     ... Done
     *   } else {
     *     ... call function
     *     wait (output_futures) {
     *       output_vals = lookup(output_futures)
     *       write_checkpoint(checkpoint_key, output_vals)
     *     }
     *   }
     * }
     */

    List<Var> checkpointKeyFutures = iList; // TODO: right?

    if (lookupCheckpoint)
    {
      // Need to wait for lookup key before checking if checkpoint exists
      // Do recursive wait to get container contents
      backend.startWaitStatement(
          context.constructName(function + "-checkpoint-wait"),
          VarRepr.backendVars(checkpointKeyFutures), WaitMode.WAIT_ONLY,
          false, true, ExecTarget.nonDispatchedAny());
      Var keyBlob = packCheckpointKey(context, function,
                                       checkpointKeyFutures);

     // TODO: nicer names for vars?
      Var existingVal = varCreator.createTmpLocalVal(context, Types.V_BLOB);
      Var checkpointExists = varCreator.createTmpLocalVal(context,
                                                       Types.V_BOOL);

      backend.lookupCheckpoint(checkpointExists, existingVal, keyBlob.asArg());

      backend.startIfStatement(VarRepr.backendArg(checkpointExists), true);
      setVarsFromCheckpoint(context, oList, existingVal);
      backend.startElseBlock();
    }

    // Actually call function
    backendFunctionCall(context, function, kind, concrete, oList, iList, props);


    Var writeEnabled = varCreator.createTmpLocalVal(context, Types.V_BOOL);
    backend.checkpointWriteEnabled(writeEnabled);

    backend.startIfStatement(VarRepr.backendArg(writeEnabled), false);
    // checkpoint output values once set
    List<Var> checkpointVal = oList; // TODO: right?

    List<Var> waitVals;
    if (lookupCheckpoint) {
      // Already waited for inputs
      waitVals = checkpointVal;
    } else {
      // Didn't wait for inputs
      waitVals = new ArrayList<Var>();
      waitVals.addAll(checkpointKeyFutures);
      waitVals.addAll(checkpointVal);
    }
    backend.startWaitStatement(
        context.constructName(function + "-checkpoint-wait"),
        VarRepr.backendVars(waitVals), WaitMode.WAIT_ONLY,
        false, true, ExecTarget.nonDispatchedAny());

    // Lookup checkpoint key again since variable might not be able to be
    // passed through wait.  Rely on optimizer to clean up redundancy
    Var keyBlob2 = packCheckpointKey(context, function, checkpointKeyFutures);

    Var valBlob = packCheckpointVal(context, checkpointVal);

    backend.writeCheckpoint(keyBlob2.asArg(), valBlob.asArg());
    backend.endWaitStatement(); // Close wait for values
    backend.endIfStatement(); // Close if for write enabled
    if (lookupCheckpoint)
    {
      backend.endIfStatement(); // Close else block
      backend.endWaitStatement(); // Close wait for keys
    }
  }

  private Var packCheckpointKey(Context context,
      String functionName, List<Var> vars) throws UserException,
      UndefinedTypeException, DoubleDefineException {
    return packCheckpointData(context, functionName, vars);
  }

  private Var packCheckpointVal(Context context, List<Var> vars)
       throws UserException, UndefinedTypeException, DoubleDefineException {
    return packCheckpointData(context, null, vars);
  }

  /**
   * Take set of (recursively closed) variables and create a
   * unique key from their values.
   * @param context
   * @param vars
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws DoubleDefineException
   */
  private Var packCheckpointData(Context context,
      String functionName, List<Var> vars) throws UserException,
      UndefinedTypeException, DoubleDefineException {
    List<Arg> elems = new ArrayList<Arg>(vars.size());

    if (functionName != null) {
      // Prefix with function name
      elems.add(Arg.newString(functionName));
    }

    for (Var v: vars) {
      // Need to be values to form key
      if (v.storage() == Alloc.LOCAL) {
        elems.add(v.asArg());
      } else {
        Var fetched;
        if (Types.isContainer(v)) {
          // Recursively fetch to get nested lists/dicts
          fetched = retrieveContainerValues(context, v);
        } else {
          fetched = retrieveToVar(context, v);
        }
        elems.add(fetched.asArg());
      }
    }

    Var blob = varCreator.createTmpLocalVal(context, Types.V_BLOB);
    Var backendBlob = VarRepr.backendVar(blob);
    backend.packValues(backendBlob, VarRepr.backendArgs(elems));

    // Make sure it gets freed at end of block
    backend.freeBlob(backendBlob);
    return blob;
  }

  private void setVarsFromCheckpoint(Context context,
      List<Var> functionOutputs, Var checkpointVal) throws UserException {
    assert(Types.isBlobVal(checkpointVal));
    List<Var> values = new ArrayList<Var>();
    for (Var functionOutput: functionOutputs) {
      if (functionOutput.storage() == Alloc.LOCAL) {
        values.add(functionOutput);
      } else if (Types.isContainer(functionOutput)) {
        Type unpackedT = Types.unpackedType(functionOutput);
        values.add(varCreator.createValueVar(context, unpackedT,
                                             functionOutput, true));
      } else {
        values.add(varCreator.createValueOfVar(context, functionOutput));
      }
    }

    backend.unpackValues(VarRepr.backendVars(values),
                         VarRepr.backendVar(checkpointVal));

    assert(values.size() == functionOutputs.size());
    for (int i = 0; i < values.size(); i++) {
      Var value = values.get(i);
      Var functionOutput = functionOutputs.get(i);
      if (!value.equals(functionOutput)) {
        if (Types.isContainer(functionOutput)) {
          backend.storeRecursive(VarRepr.backendVar(functionOutput),
                                 VarRepr.backendArg(value));
        } else {
          assign(functionOutput, value.asArg());
        }
      }
    }

    backend.freeBlob(VarRepr.backendVar(checkpointVal));
  }

  /**
   * Generate backend instruction for function call
   * @param context
   * @param function name of function
   * @param oList list of output variables
   * @param iList list of input variables (with correct types)
   * @param priorityVal optional priority value (can be null)
   * @throws UserException
   */
  private void backendFunctionCall(Context context, String function,
      FunctionCallKind kind,
      FunctionType concrete, List<Var> oList, List<Var> iList,
      TaskProps props) throws UserException {
    props.assertInternalTypesValid();

    if (kind == FunctionCallKind.STRUCT_CONSTRUCTOR) {
      backendStructConstructor(context, function, concrete, oList, iList);
      return;
    }
    assert(kind == FunctionCallKind.REGULAR_FUNCTION);

    FunctionType def = context.lookupFunction(function);
    if (def == null) {
      throw new STCRuntimeError("Couldn't locate function definition for " +
          "previously defined function " + function);
    }

    if (context.hasFunctionProp(function, FnProp.DEPRECATED)) {
      LogHelper.warn(context, "Call to deprecated function: " + function);
    }

    List<Var> backendIList = VarRepr.backendVars(iList);
    List<Var> backendOList = VarRepr.backendVars(oList);

    TaskProps backendProps = VarRepr.backendProps(props);
    if (context.isIntrinsic(function)) {
      IntrinsicFunction intF = context.lookupIntrinsic(function);
      backend.intrinsicCall(intF, backendIList, backendOList,
                            backendProps);
    } else if (context.hasFunctionProp(function, FnProp.BUILTIN)) {
      ForeignFunctions foreignFuncs = context.getForeignFunctions();
      if (foreignFuncs.hasOpEquiv(function)) {
        assert(oList.size() <= 1);
        Var backendOut = (backendOList.size() == 0 ?
                   null : backendOList.get(0));

        backend.asyncOp(foreignFuncs.getOpEquiv(function), backendOut,
                        Arg.fromVarList(backendIList),
                        backendProps);
      } else {
        backend.builtinFunctionCall(function, function, backendIList, backendOList,
                                    backendProps);
      }
    } else if (context.hasFunctionProp(function, FnProp.COMPOSITE)) {
      ExecTarget mode;
      if (context.hasFunctionProp(function, FnProp.SYNC)) {
        mode = ExecTarget.syncControl();
      } else {
        mode = ExecTarget.dispatchedControl();
      }
      backend.functionCall(function, function, Var.asArgList(backendIList),
                            backendOList, mode, backendProps);
    } else {
      backendCallWrapped(context, function, concrete, backendOList,
                         backendIList, backendProps);
    }
  }

  /**
   * Call wrapper function for app or wrapped builtin
   * @param context
   * @param function
   * @param concrete
   * @param backendOList
   * @param backendIList
   * @param props
   * @throws UserException
   */
  private void backendCallWrapped(Context context, String function,
      FunctionType concrete,
      List<Var> backendOList, List<Var> backendIList, TaskProps props)
      throws UserException {
    String wrapperFnName; // The name of the wrapper to call
    if (context.hasFunctionProp(function, FnProp.WRAPPED_BUILTIN)) {
      // Wrapper may need to be generated
      wrapperFnName = wrappers.generateWrapper(context, function,
                                               concrete);
    } else {
      assert(context.hasFunctionProp(function, FnProp.APP));
      // Wrapper has same name for apps
      wrapperFnName = function;
    }
    List<Arg> realInputs = new ArrayList<Arg>();
    for (Var in: backendIList) {
      realInputs.add(in.asArg());
    }

    /* Wrapped builtins must have these properties passed
     * into function body so can be applied after arg wait
     * Target and parallelism are passed in as extra args */
    if (context.hasFunctionProp(function, FnProp.PARALLEL)) {
      // parallelism must be specified for parallel functions
      Arg par = props.get(TaskPropKey.PARALLELISM);
      if (par == null) {
        throw new UserException(context, "Parallelism not specified for " +
            "call to parallel function " + function);
      }
      realInputs.add(VarRepr.backendArg(par));
    }
    if (context.hasFunctionProp(function, FnProp.TARGETABLE)) {
      // Target is optional but we have to pass something in
      Arg location = props.getWithDefault(TaskPropKey.LOCATION);
      realInputs.add(VarRepr.backendArg(location));
      Arg softLocation = props.getWithDefault(TaskPropKey.SOFT_LOCATION);
      realInputs.add(VarRepr.backendArg(softLocation));
    }

    // Other code always creates sync wrapper
    assert(context.hasFunctionProp(function, FnProp.SYNC));
    ExecTarget mode = ExecTarget.syncControl();

    // Only priority property is used directly in sync instruction,
    // but other properties are useful to have here so that the optimizer
    // can replace instruction with local version and correct props
    backend.functionCall(wrapperFnName, function, realInputs, backendOList,
                         mode, VarRepr.backendProps(props));
  }

  private void backendStructConstructor(Context context, String function,
      FunctionType concrete, List<Var> outputs, List<Var> inputs)
          throws UserException {
    assert(outputs.size() == 1);
    Var struct = outputs.get(0);
    assert(Types.isStruct(struct));
    StructType st = (StructType)struct.type().getImplType();
    assert(st.fieldCount() == inputs.size());

    for (int i = 0; i < st.fieldCount(); i++) {
      StructField field = st.fields().get(i);
      Var input = inputs.get(i);
      assert(input.type().assignableTo(field.type()));
      List<String> fieldPath = Collections.singletonList(field.name());

      // Store or copy in the fields separately as appropriate
      if (VarRepr.storeRefInStruct(field)) {
        backend.structStoreSub(VarRepr.backendVar(struct), fieldPath,
                               VarRepr.backendVar(input).asArg());
      } else {
        structCopyIn(context, struct, fieldPath, input);
      }
    }
  }

  /**
   * Copy in.
   * @param struct
   * @param fieldPath
   * @param input
   * @throws TypeMismatchException
   * @throws DoubleDefineException
   * @throws UndefinedTypeException
   */
  private void structCopyIn(Context context, Var struct,
                      List<String> fieldPath, Var input)
                          throws UserException {
    if (Types.simpleCopySupported(input)) {
      backend.structCopyIn(VarRepr.backendVar(struct), fieldPath,
          VarRepr.backendVar(input));
    } else if (Types.isFile(input)) {
      Var fieldAlias = structCreateAlias(context, struct, fieldPath);
      copyFileByValue(fieldAlias, input);
    } else {
      throw new STCRuntimeError("Unexpected type: " + input);
    }
  }

  /**
   * Assign int literal value
   * @param context
   * @param dst
   * @param val value
   * @throws UserException
   */
  private void assignIntLit(Context context, Var dst, Long val)
                                  throws UserException {
   LogHelper.trace(context, dst.toString()+"="+val);
   if (Types.isInt(dst)) {
     assign(dst, Arg.newInt(val));
   } else {
     assert(Types.isFloat(dst)) : dst;
     double fVal = Literals.interpretIntAsFloat(context, val);
     assign(dst, Arg.newFloat(fVal));
   }
  }

  /**
   * Assign bool literal value from tree
   * @param context
   * @param dst
   * @param tree
   * @throws UserException
   */
  private void assignBoolLit(Context context, SwiftAST tree, Var dst)
            throws UserException {
   assert(Types.isBool(dst));
   String val = Literals.extractBoolLit(context, tree);
  assign(dst, Arg.newBool(Boolean.parseBoolean(val)));
  }

  /**
   * Assign float literal value from tree
   * @param context
   * @param tree
   * @param dst
   * @throws UserException
   */
  private void assignFloatLit(Context context, SwiftAST tree, Var dst)
  throws UserException {
   assert(Types.isFloat(dst));
   double val = Literals.extractFloatLit(context, tree);
   assign(dst, Arg.newFloat(val));
  }

  /**
   * Assign float literal value from tree
   * @param context
   * @param tree
   * @param dst
   * @throws UserException
   */
  private void assignStringLit(Context context, SwiftAST tree, Var dst)
          throws UserException {
    assert(Types.isString(dst));
    String val = Literals.extractStringLit(context, tree);
    assign(dst, Arg.newString(val));
  }

  /**
   * Handle variable assignment, assigning src to dst
   * @param context
   * @param dst
   * @param src
   * @throws UserException
   */
  private void assignVariable(Context context, Var dst, Var src)
                                  throws UserException {
    if (Types.isPrimUpdateable(src)) {
      Var snapshot = snapshotUpdateable(context, src);
      src = snapshot;
    }

    Type srcType = src.type();
    Type dstType = dst.type();
    TypeChecker.checkCopy(context, srcType, dstType);

    copyByValue(context, dst, src);
  }

  /**
   * Get a snapshot of an updateable and assign to future
   * @param context
   * @param src
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private Var snapshotUpdateable(Context context, Var src)
      throws UserException, UndefinedTypeException {
    assert(Types.isPrimUpdateable(src));
    // Create a future alias to the updateable type so that
    // types match
    Var val = varCreator.createTmpLocalVal(context,
                          ScalarUpdateableType.asScalarValue(src.type()));
    Var backendVal = VarRepr.backendVar(val);

    backend.latestValue(backendVal, src);

    if (!src.type().assignableTo(Types.UP_FLOAT)) {
      throw new STCRuntimeError(src.type() + " not yet supported");
    }

    /* Create a future with a snapshot of the value of the updateable
     * By making the retrieve and store explicit the optimizer should be
     * able to optimize out the future in many cases
     */
    Var snapshot = assignToVar(context, val.asArg(), false);
    return snapshot;
  }

  /**
   * Copy non-local data by value
   * @param context
   * @param dst
   * @param src
   * @throws UserException
   */
  private void backendAsyncCopy(Context context, Var dst, Var src)
                                                throws UserException {
    assert(src.type().assignableTo(dst.type()));
    assert(src.storage() != Alloc.LOCAL);
    backend.asyncCopy(VarRepr.backendVar(dst), VarRepr.backendVar(src));
  }

  private void copyFileByValue(Var dst, Var src)
      throws TypeMismatchException {
    Var backendSrc = VarRepr.backendVar(src);
    Var backendDst = VarRepr.backendVar(dst);
    if (dst.isMapped() == Ternary.FALSE ||
        dst.type().fileKind().supportsPhysicalCopy()) {
        backend.copyFile(backendDst, backendSrc);
    } else {
      throw new TypeMismatchException("Do not support physical copy " +
          "to (possibly) mapped variable " + dst.name() + " with " +
          "type " + dst.type().typeName());
    }
  }

  private void derefThenCopyContainer(Context context, Var dst, Var src)
      throws UserException, UndefinedTypeException {
    assert(Types.isContainerRef(src));
    assert(Types.isRefTo(src, dst));
    String wName = context.getFunctionContext().constructName("copy-wait");
    List<Var> waitVars = Arrays.asList(src);
    backend.startWaitStatement(wName, VarRepr.backendVars(waitVars),
            WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedAny());
    Var derefed = varCreator.createTmpAlias(context, dst.type());
    retrieveRef(derefed, src, false);
    backendAsyncCopy(context, dst, derefed);
    backend.endWaitStatement();
  }

  private Var structCreateAlias(Context context, Var struct,
      List<String> fieldPath) throws UserException {
    Type fieldType = TypeChecker.findStructFieldType(context, fieldPath,
                                                     struct.type());

    Var result = varCreator.createStructFieldAlias(context,
                        struct, fieldType, fieldPath);
    backend.structCreateAlias(VarRepr.backendVar(result),
                  VarRepr.backendVar(struct), fieldPath);
    return result;
  }

  /**
   * Copy a struct reference to a struct.
   * @param context
   * @param dst
   * @param src
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void dereferenceStruct(Context context, Var dst, Var src)
      throws UserException, UndefinedTypeException {
    List<Var> waitVars = Arrays.asList(src);
    backend.startWaitStatement( context.constructName("copystruct"),
                    VarRepr.backendVars(waitVars), WaitMode.WAIT_ONLY,
                    false, false, ExecTarget.nonDispatchedAny());
    Var rValDerefed = varCreator.createTmp(context,
            src.type().memberType(), false, true);
    retrieveRef(rValDerefed, src, false);
    backendAsyncCopy(context, dst, rValDerefed);
    backend.endWaitStatement();
  }

  void findArraysInStruct(Context context,
      Var root, VInfo structVInfo, List<Pair<Var, VInfo>> arrays)
          throws UndefinedTypeException, UserException {
    assert(Types.isStruct(root));
    findArraysInStruct(context, root, (StructType)root.type(),
                    structVInfo, new StackLite<String>(), arrays);
  }

  /**
   *
   * @param context
   * @param rootStruct root struct
   * @param currStructType current structure type (of root of sub-structure)
   * @param currStructVInfo
   * @param fieldPath
   * @param arrays
   * @throws UndefinedTypeException
   * @throws UserException
   */
  private void findArraysInStruct(Context context,
      Var rootStruct, StructType currStructType, VInfo currStructVInfo,
      StackLite<String> fieldPath, List<Pair<Var, VInfo>> arrays) throws UndefinedTypeException,
                                                                      UserException {
    assert(currStructVInfo != null);

    for (StructField f: currStructType.fields()) {
      fieldPath.push(f.name());
      if (Types.isArray(f.type())) {
        Var fieldVar = structLookup(context, rootStruct, fieldPath, false);
        VInfo fieldInfo = currStructVInfo != null ?
            currStructVInfo.getFieldVInfo(f.name()) : null;
        arrays.add(Pair.create(fieldVar, fieldInfo));
      } else if (Types.isStruct(f.type())) {
        VInfo nestedVInfo = currStructVInfo.getFieldVInfo(f.name());
        findArraysInStruct(context, rootStruct, (StructType)f.type(),
                                  nestedVInfo, fieldPath, arrays);
      }

      fieldPath.pop();
    }
  }
}
