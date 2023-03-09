package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.DefaultVals;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Intrinsics;
import exm.stc.common.lang.Intrinsics.IntrinsicFunction;
import exm.stc.common.lang.TaskProp;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.util.Pair;
import exm.stc.frontend.Context.FnOverload;
import exm.stc.frontend.Context.FnProp;
import exm.stc.frontend.tree.FunctionCall;
import exm.stc.frontend.tree.FunctionCall.FunctionCallKind;
import exm.stc.frontend.typecheck.FunctionTypeChecker;
import exm.stc.frontend.typecheck.FunctionTypeChecker.ConcreteMatch;
import exm.stc.frontend.typecheck.TypeChecker;
import exm.stc.ic.STCMiddleEnd;

public class FunctionCallEvaluator {

  private final VarCreator varCreator;
  private final WrapperGen wrappers;
  private final ExprWalker exprWalker;
  private final STCMiddleEnd backend;

  public FunctionCallEvaluator(VarCreator varCreator, WrapperGen wrappers,
      ExprWalker exprWalker, STCMiddleEnd backend) {
    this.varCreator = varCreator;
    this.wrappers = wrappers;
    this.exprWalker = exprWalker;
    this.backend = backend;
  }

  /**
   * Evaluate a function call expression.
   *
   * @param context
   * @param tree
   * @param outVars
   * @throws UserException
   * @throws UndefinedVarError
   * @throws UndefinedFunctionException
   */
  public void evalFunctionCall(Context context, SwiftAST tree,
      List<Var> outVars, Map<String, String> renames) throws UserException {
    assert(tree.getType() == ExMParser.CALL_FUNCTION);

    FunctionCall f = FunctionCall.fromAST(context, tree, true);

    // This will check the type of the function call
    ConcreteMatch concrete =
        FunctionTypeChecker.concretiseFunctionCall(context, f, outVars);

    FnID concreteID = concrete.overload.id;
    FunctionType concreteType = concrete.type;

    // Some functions, e.g. asserts, can be omitted after typechecking
    if (omitFunctionCall(context, concreteID)) {
      return;
    }

    // First evaluate inputs before opening any waits
    List<Var> inVars = evalFunctionInputs(context, renames, concrete.overload,
                concreteType, f.posArgs(), f.kwArgs());

    assert(inVars.size() == concrete.overload.inArgNames.size() ||
          (concrete.overload.type.hasVarargs() &&
              inVars.size() >= concrete.overload.inArgNames.size() - 1));

    // Process priority after arguments have been evaluated, so that
    // the argument evaluation is outside the wait statement
    boolean openedWait = false;

    TaskProps propVals = new TaskProps();
    openedWait = evalCallProperties(context, concreteID, f, propVals, renames);
    evalFunctionCallInner(context, concreteID, f.kind(), concreteType, outVars,
                          inVars, concrete.overload.defaultVals, propVals);

    if (openedWait) {
      backend.endWaitStatement();
    }
  }

  /**
   * Generate code for function call, once task properties have been evaluated.
   *
   * @param context
   * @param function
   * @param kind
   * @param concrete
   * @param oList
   * @param iList
   * @param props
   * @throws UndefinedTypeException
   * @throws UserException
   */
  private void evalFunctionCallInner(Context context, FnID id,
      FunctionCallKind kind, FunctionType concrete,
      List<Var> oList, List<Var> iList, DefaultVals<Var> defaultVals,
      TaskProps props)
      throws UserException {
    Pair<Context, List<Var>> fixupResult;
    fixupResult = fixupFunctionInputs(context, id, concrete, iList,
                                      defaultVals, props);
    Context waitContext = fixupResult.val1;
    List<Var> fixedIList = fixupResult.val2;

    Context callContext = waitContext != null ? waitContext : context;

    boolean checkpointed =
        context.hasFunctionProp(id, FnProp.CHECKPOINTED);

    if (checkpointed) {
      checkpointedFunctionCall(callContext, id, kind, concrete, oList,
                                     props, fixedIList);
    } else {
      backendFunctionCall(callContext, id, kind, concrete, oList, fixedIList,
                          props);
    }

    if (waitContext != null) {
      backend.endWaitStatement();
    }
  }

  /**
   * Evaluate function inputs
   * @param context
   * @param renames
   * @param overload
   * @param concrete
   * @param posArgs
   * @param kwArgs
   * @return list of inputs, with nulls where defaults need to be filled in
   * @throws UserException
   */
  private List<Var> evalFunctionInputs(Context context,
      Map<String, String> renames, FnOverload overload, FunctionType concrete,
      List<SwiftAST> posArgs, Map<String, SwiftAST> kwArgs)
          throws UserException {
    // evaluate argument expressions left to right, creating temporaries
    Var argVars[] = new Var[concrete.getInputs().size()];

    for (int i = 0; i < concrete.getInputs().size(); i++) {
      int formalArgIx = Math.min(i, overload.inArgNames.size() - 1);
      String inArgName = overload.inArgNames.get(formalArgIx);
      SwiftAST expr;
      if (i < posArgs.size()) {
        expr = posArgs.get(i);
      } else {
        expr = kwArgs.get(inArgName);
        if (expr == null) {
          continue;
        }
      }

      Type expType = concrete.getInputs().get(i);
      Type exprType = TypeChecker.findExprType(context, expr);
      Type argType = FunctionTypeChecker.concretiseFnArg(context,
            overload.id.originalName(), inArgName, expType, exprType).val2;
      argVars[i] = exprWalker.eval(context, expr, argType, false, renames);
    }

    return Arrays.asList(argVars);
  }

  /**
   * Fixup any mismatch between function formal inputs and input expressions by,
   * e.g., dereferencing variables.
   *
   * @param context
   * @param function
   * @param concrete
   * @param iList
   * @param props
   * @return (wait context, fixed up inputs).  Wait context is null if no wait opened.
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private Pair<Context, List<Var>> fixupFunctionInputs(Context context,
      FnID id, FunctionType concrete, List<Var> iList,
      DefaultVals<Var> defaultVals, TaskProps props)
      throws UserException {
    // The expected types might not be same as current input types, work out
    // what we need to do to make them the same
    List<Var> fixedIList = new ArrayList<Var>(iList.size());
    List<Var> derefVars = new ArrayList<Var>();
    List<Var> waitVars = new ArrayList<Var>();
    Context waitContext = null;

    assert(concrete.getInputs().size() == iList.size());
    for (int i = 0; i < iList.size(); i++) {
      Var input = iList.get(i);
      if (input == null) {
        input = defaultVals.defaultVals().get(i);
        assert(input != null);
      }

      Type inputType = input.type();
      Type expType = concrete.getInputs().get(i);
      if (inputType.getImplType().assignableTo(expType.getImplType())) {
        fixedIList.add(input);
      } else if (Types.isAssignableRefTo(inputType, expType)) {
        if (waitContext == null) {
          waitContext = LocalContext.fnSubcontext(context);
        }
        Var derefed;
        derefed = waitContext.createTmpAliasVar(expType);
        waitVars.add(input);
        derefVars.add(derefed);
        fixedIList.add(derefed);
        Logging.getSTCLogger().trace("Deref function input " + input +
                                     " to " + derefed);
      } else if (Types.isUpdateableEquiv(inputType, expType)) {
        fixedIList.add(exprWalker.snapshotUpdateable(context, input));
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
      backend.startWaitStatement( fc.constructName("call-" + id.uniqueName()),
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
        exprWalker.retrieveRef(derefVar, waitVars.get(i), false);
      }
    }

    return Pair.create(waitContext, fixedIList);
  }

  /**
   * Evaluate any task properties.  Put values into propVals.
   *
   * @param context
   * @param fc
   * @param propVals: results go in here.
   * @param renames
   * @throws UserException
   * @returns true if a wait statement was opened
   */
  private boolean evalCallProperties(Context context, FnID id, FunctionCall fc,
		  TaskProps propVals, Map<String, String> renames) throws UserException {
    List<Pair<TaskPropKey, Var>> propFutures =
          new ArrayList<Pair<TaskPropKey, Var>>();
    List<Var> waitVars = new ArrayList<Var>();
    Var locationVar = null;

    for (TaskPropKey ann: fc.annotations().keySet()) {
      checkCallAnnotation(context, id, fc, ann);
      SwiftAST expr = fc.annotations().get(ann);
      Type exprType = TypeChecker.findExprType(context, expr);
      Type concreteType = TaskProp.checkFrontendType(context, ann, exprType);
      Var future = exprWalker.eval(context, expr, concreteType, false, renames);
      waitVars.add(future);
      Pair<TaskPropKey,Var> pair = Pair.create(ann, future);
      propFutures.add(pair);
    }

    if (fc.location() != null) {
      checkCanTarget(context, id);

      locationVar = exprWalker.eval(context, fc.location(), Types.F_LOCATION, false,
                                    renames);
      waitVars.add(locationVar);
    }

    if (waitVars.isEmpty()) {
      return false;
    }

    backend.startWaitStatement(context.constructName("ann-wait"),
            VarRepr.backendVars(waitVars),
            WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedControl());

    for (Pair<TaskPropKey,Var> x: propFutures) {
      Var value = exprWalker.retrieveToVar(context, x.val2);
      propVals.put(x.val1, value.asArg());
    }

    if (locationVar != null) {
      populateFromLocationStruct(context, locationVar, propVals);
    }

    if (fc.softLocationOverride()) {
      // Override strictness if @soft_location was used
      propVals.put(TaskPropKey.LOC_STRICTNESS,
                   TaskProps.LOC_STRICTNESS_SOFT_ARG);
    }

    return true;
  }

  private boolean omitFunctionCall(Context context, FnID id) {
    return context.getForeignFunctions().isAssertVariant(id) &&
            Settings.getBooleanUnchecked(Settings.OPT_DISABLE_ASSERTS);
  }

  /**
   * Generate code for a function call where we may be able to short-circuit by
   * looking up checkpoint.
   *
   * @param context
   * @param function
   * @param kind
   * @param concrete
   * @param oList
   * @param props
   * @param realIList
   * @throws UserException
   */
  private void checkpointedFunctionCall(Context context, FnID id,
      FunctionCallKind kind, FunctionType concrete, List<Var> oList,
      TaskProps props, List<Var> realIList) throws UserException {
    Var lookupEnabled = VarRepr.backendVar(
              varCreator.createTmpLocalVal(context, Types.V_BOOL));
    backend.checkpointLookupEnabled(lookupEnabled);

    backend.startIfStatement(lookupEnabled.asArg(), true);
    checkpointSaveFunctionCall(context, id, kind, concrete, oList,
                             realIList, props, true);
    backend.startElseBlock();
    checkpointSaveFunctionCall(context, id, kind, concrete, oList,
                              realIList, props, false);
    backend.endIfStatement();
  }

  /**
   * Generate code for function call that may save its results in a checkpoint.
   * @param context
   * @param function
   * @param kind
   * @param concrete
   * @param oList
   * @param iList
   * @param props
   * @param lookupCheckpoint
   * @throws UserException
   */
  private void checkpointSaveFunctionCall(Context context, FnID id,
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
          context.constructName(id.uniqueName() + "-checkpoint-wait"),
          VarRepr.backendVars(checkpointKeyFutures), WaitMode.WAIT_ONLY,
          false, true, ExecTarget.nonDispatchedAny());
      Var keyBlob = packCheckpointKey(context, id, checkpointKeyFutures);

      Var existingVal = varCreator.createTmpLocalVal(context, Types.V_BLOB);
      Var checkpointExists = varCreator.createTmpLocalVal(context,
                                                       Types.V_BOOL);

      backend.lookupCheckpoint(checkpointExists, existingVal, keyBlob.asArg());

      backend.startIfStatement(VarRepr.backendArg(checkpointExists), true);
      setVarsFromCheckpoint(context, oList, existingVal);
      backend.startElseBlock();
    }

    // Actually call function
    backendFunctionCall(context, id, kind, concrete, oList, iList, props);


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
        context.constructName(id.uniqueName() + "-checkpoint-wait"),
        VarRepr.backendVars(waitVals), WaitMode.WAIT_ONLY,
        false, true, ExecTarget.nonDispatchedAny());

    // Lookup checkpoint key again since variable might not be able to be
    // passed through wait.  Rely on optimizer to clean up redundancy
    Var keyBlob2 = packCheckpointKey(context, id, checkpointKeyFutures);

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


  /**
   * Generate backend instruction for function call
   * @param context
   * @param function name of function
   * @param oList list of output variables
   * @param iList list of input variables (with correct types)
   * @param priorityVal optional priority value (can be null)
   * @throws UserException
   */
  private void backendFunctionCall(Context context, FnID id,
      FunctionCallKind kind,
      FunctionType concrete, List<Var> oList, List<Var> iList,
      TaskProps props) throws UserException {
    props.assertInternalTypesValid();

    if (kind == FunctionCallKind.STRUCT_CONSTRUCTOR) {
      backendStructConstructor(context, id, concrete, oList, iList);
      return;
    } else if (kind == FunctionCallKind.SUBTYPE_CONSTRUCTOR) {
      backendSubtypeConstructor(context, id, concrete, oList, iList);
      return;
    }

    assert(kind == FunctionCallKind.REGULAR_FUNCTION);

    if (context.hasFunctionProp(id, FnProp.DEPRECATED)) {
      LogHelper.warn(context, "Call to deprecated function: " + id.originalName());
    }

    List<Var> backendIList = VarRepr.backendVars(iList);
    List<Var> backendOList = VarRepr.backendVars(oList);

    TaskProps backendProps = VarRepr.backendProps(props);
    if (context.isIntrinsic(id)) {
      IntrinsicFunction intF = context.lookupIntrinsic(id);
      backend.intrinsicCall(intF, backendIList, backendOList,
                            backendProps);
    } else if (context.hasFunctionProp(id, FnProp.BUILTIN)) {
      ForeignFunctions foreignFuncs = context.getForeignFunctions();
      if (foreignFuncs.hasOpEquiv(id)) {
        assert(oList.size() <= 1);
        Var backendOut = (backendOList.size() == 0 ?
                   null : backendOList.get(0));

        backend.asyncOp(foreignFuncs.getOpEquiv(id), backendOut,
                        Arg.fromVarList(backendIList),
                        backendProps);
      } else {
        backend.builtinFunctionCall(id, backendIList, backendOList,
                                    backendProps);
      }
    } else if (context.hasFunctionProp(id, FnProp.COMPOSITE)) {
      ExecTarget mode;
      if (context.hasFunctionProp(id, FnProp.SYNC)) {
        mode = ExecTarget.syncControl();
      } else {
        mode = ExecTarget.dispatchedControl();
      }
      backend.functionCall(id, Var.asArgList(backendIList),
                            backendOList, mode, backendProps);
    } else {
      backendCallWrapped(context, id, concrete, backendOList,
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
  private void backendCallWrapped(Context context, FnID id,
      FunctionType concrete,
      List<Var> backendOList, List<Var> backendIList, TaskProps props)
      throws UserException {
    FnID wrapperID; // The name of the wrapper to call
    if (context.hasFunctionProp(id, FnProp.WRAPPED_BUILTIN)) {
      // Wrapper may need to be generated
      wrapperID = wrappers.generateWrapper(context, id, concrete);
    } else {
      assert(context.hasFunctionProp(id, FnProp.APP));
      // Wrapper has same id for apps
      wrapperID = id;
    }
    List<Arg> realInputs = new ArrayList<Arg>();
    for (Var in: backendIList) {
      realInputs.add(in.asArg());
    }

    /* Wrapped builtins must have these properties passed
     * into function body so can be applied after arg wait
     * Target and parallelism are passed in as extra args */
    if (context.hasFunctionProp(id, FnProp.PARALLEL)) {
      // parallelism must be specified for parallel functions
      Arg par = props.get(TaskPropKey.PARALLELISM);
      if (par == null) {
        throw new UserException(context, "Parallelism not specified for " +
            "call to parallel function " + id.originalName());
      }
      realInputs.add(VarRepr.backendArg(par));
    }

    if (context.hasFunctionProp(id, FnProp.TARGETABLE)) {
      // Target is optional but we have to pass something in
      Arg location = props.getWithDefault(TaskPropKey.LOC_RANK);
      realInputs.add(VarRepr.backendArg(location));

      Arg locStrictness = props.getWithDefault(TaskPropKey.LOC_STRICTNESS);
      realInputs.add(VarRepr.backendArg(locStrictness));

      Arg locAccuracy = props.getWithDefault(TaskPropKey.LOC_ACCURACY);
      realInputs.add(VarRepr.backendArg(locAccuracy));
    }

    // Other code always creates sync wrapper
    assert(context.hasFunctionProp(id, FnProp.SYNC));
    ExecTarget mode = ExecTarget.syncControl();

    // Only priority property is used directly in sync instruction,
    // but other properties are useful to have here so that the optimizer
    // can replace instruction with local version and correct props
    backend.functionCall(wrapperID, realInputs, backendOList,
                         mode, VarRepr.backendProps(props));
  }

  private void checkCallAnnotation(Context context, FnID id,
      FunctionCall f, TaskPropKey ann) throws UserException {
    if (context.isIntrinsic(id)) {
      // Handle annotation specially
      IntrinsicFunction intF = context.lookupIntrinsic(id);
      List<TaskPropKey> validProps = Intrinsics.validProps(intF);
      if (!validProps.contains(ann)) {
          throw new InvalidAnnotationException(context, "Cannot specify " +
                "property " + ann + " for intrinsic function " + f.originalName());
      }
    } else if (ann == TaskPropKey.PARALLELISM) {
      if (!context.hasFunctionProp(id, FnProp.PARALLEL)) {
        throw new UserException(context, "Tried to call non-parallel"
            + " function " + f.originalName() + " with parallelism.  "
            + " Maybe you meant to annotate the function definition with "
            + "@" + Annotations.FN_PAR);
      }
    }
  }

  private void checkCanTarget(Context context, FnID id)
      throws UserException {
    if (!context.hasFunctionProp(id, FnProp.TARGETABLE)) {
      throw new UserException(context, "Tried to call non-targetable"
          + " function " + id.originalName() + " with target");
    }
  }

  /**
   * Fill extract members from struct and add to dictionary
   * @param context
   * @param loc
   * @param propVals
   * @throws UserException
   */
  private void populateFromLocationStruct(Context context, Var loc,
          TaskProps propVals) throws UserException {
    assert(loc.type().assignableTo(Types.F_LOCATION));

    Var locRank = varCreator.createStructFieldTmpVal(context, loc,
                  Types.V_INT, Collections.singletonList("rank"), Alloc.LOCAL);

    Var locStrictness = varCreator.createStructFieldTmpVal(context, loc,
        Types.V_LOC_STRICTNESS, Collections.singletonList("strictness"), Alloc.LOCAL);

    Var locAccuracy = varCreator.createStructFieldTmpVal(context, loc,
        Types.V_LOC_ACCURACY, Collections.singletonList("accuracy"), Alloc.LOCAL);

    /*
     * Retrieve seperately from the struct.  In principle this could be
     * inefficient but in practice we hope the optimiser should be able
     * to resolve these.
     */
    backend.structRetrieveSub(VarRepr.backendVar(locRank),
              VarRepr.backendVar(loc), Arrays.asList("rank"));
    backend.structRetrieveSub(VarRepr.backendVar(locStrictness),
              VarRepr.backendVar(loc), Arrays.asList("strictness"));
    backend.structRetrieveSub(VarRepr.backendVar(locAccuracy),
              VarRepr.backendVar(loc), Arrays.asList("accuracy"));

    propVals.put(TaskPropKey.LOC_RANK, locRank.asArg());
    propVals.put(TaskPropKey.LOC_STRICTNESS, locStrictness.asArg());
    propVals.put(TaskPropKey.LOC_ACCURACY, locAccuracy.asArg());
  }

  private Var packCheckpointKey(Context context,
      FnID id, List<Var> vars) throws UserException,
      UndefinedTypeException, DoubleDefineException {
    return packCheckpointData(context, id, vars);
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
      FnID id, List<Var> vars) throws UserException,
      UndefinedTypeException, DoubleDefineException {
    List<Arg> elems = new ArrayList<Arg>(vars.size());

    if (id != null) {
      assert(id.uniqueName().equals(id.originalName())) :
        "Cannot checkpoint overloaded function";
      // Prefix with function name
      elems.add(Arg.newString(id.originalName()));
    }

    for (Var v: vars) {
      // Need to be values to form key
      if (v.storage() == Alloc.LOCAL) {
        elems.add(v.asArg());
      } else {
        Var fetched;
        if (Types.isContainer(v)) {
          // Recursively fetch to get nested lists/dicts
          fetched = exprWalker.retrieveContainerValues(context, v);
        } else {
          fetched = exprWalker.retrieveToVar(context, v);
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
          exprWalker.assign(functionOutput, value.asArg());
        }
      }
    }

    backend.freeBlob(VarRepr.backendVar(checkpointVal));
  }

  private void backendStructConstructor(Context context, FnID id,
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
        exprWalker.structCopyIn(context, struct, fieldPath, input);
      }
    }
  }

  private void backendSubtypeConstructor(Context context, FnID function,
        FunctionType concrete, List<Var> outputs, List<Var> inputs)
            throws UserException {
      assert(outputs.size() == 1);
      assert(inputs.size() == 1);
      Var dst = outputs.get(0);
      Var src = inputs.get(0);
      assert(Types.isSubType(dst));
      exprWalker.copyByValue(context, dst, src, false);
    }


}
