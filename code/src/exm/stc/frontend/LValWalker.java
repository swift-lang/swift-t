package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WaitMode;
import exm.stc.frontend.tree.Assignment;
import exm.stc.frontend.tree.Assignment.AssignOp;
import exm.stc.frontend.tree.LValue;
import exm.stc.frontend.tree.Literals;
import exm.stc.ic.STCMiddleEnd;

/**
 * Module to walk LValue expressions and 
 */
public class LValWalker {
  
  public LValWalker(STCMiddleEnd backend, VarCreator varCreator,
                    ExprWalker exprWalker, LoadedModules modules) {
    this.backend = backend;
    this.varCreator = varCreator;
    this.exprWalker = exprWalker;
    this.modules = modules;
  }
  
  private final STCMiddleEnd backend;
  private final VarCreator varCreator;
  private final ExprWalker exprWalker;
  private final LoadedModules modules;
  
  /**
   * Setup LValues of expression for assignment
   * 
   * @param context
   * @param op
   * @param lVals
   * @param rValExpr
   * @param walkMode
   * @return LRVals object containing reduced version of lVal expressions (which
   *         are either a var or an array+index), a list of variables to
   *         evaluate the R.H.S. of the expression into, and a flag indicating
   *         that evaluating the R.H.S. can be skipped
   * @throws UserException
   */
  public LRVals prepareLVals(Context context, AssignOp op, List<LValue> lVals,
      SwiftAST rValExpr, WalkMode walkMode)
      throws UserException {
    ExprType rValTs = Assignment.checkAssign(context, lVals, rValExpr);

    List<LValue> reducedLVals = new ArrayList<LValue>(lVals.size());

    // Variables we should evaluate R.H.S. of expression into
    List<Var> rValTargets = new ArrayList<Var>(lVals.size());

    boolean skipEval = false;
    for (int i = 0; i < lVals.size(); i++) {
      LValue lVal = lVals.get(i);
      Type rValType = rValTs.get(i);

      lVal = declareLValueIfNeeded(context, walkMode, lVal, rValType);

      if (walkMode != WalkMode.ONLY_DECLARATIONS) {
        // the variable we will evaluate expression into
        context.syncFilePos(lVal.tree, modules.currentModule().moduleName,
                            modules.currLineMap());

        // First do typechecking
        Type lValType = lVal.getType(context);
        Type rValConcrete = TypeChecker.checkAssignment(context, op, rValType,
            lValType, lVal.toString());

        LValue reducedLVal = reduceLVal(context, lVal);

        if (rValExpr.getType() == ExMParser.VARIABLE) {
          // Should only be one lval if variable on rhs
          assert (lVals.size() == 1);
          Var rValVar = context.lookupVarUser(rValExpr.child(0).getText());

          if (lVal.var.equals(rValVar)) {
            throw new UserException(context, "Assigning var " + rValVar
                + " to itself");
          }

          if (isReducedArrLVal(reducedLVal) && op == AssignOp.ASSIGN) {
            /*
             * Special case: A[i] = x; we just want x to be inserted into A
             * without any temp variables being created. evalLvalue will do the
             * insertion, and return rValVar, which means we don't need to
             * evaluate anything further
             */

            rValTargets.add(rValVar);
            skipEval = true;
          }
        }

        if (!skipEval) {
          if (op == AssignOp.ASSIGN && isReducedVarLVal(reducedLVal)) {
            // If RVal and LVal types match exactly, then we just eval to LVal
            // var. Otherwise we may need to dereference something
            Var targetVar = reducedLVal.var;
            if (Types.isRefTo(rValConcrete, targetVar)) {
              // Evaluate into reference var, and deref later
              rValTargets.add(varCreator.createTmp(context, rValConcrete));
            } else {
              assert (rValConcrete.assignableTo(targetVar.type())) : rValConcrete
                  + " " + targetVar;
              rValTargets.add(targetVar);
            }
          } else if (op == AssignOp.ASSIGN && isReducedArrLVal(reducedLVal)) {
            // Create temporary variable to insert into array
            rValTargets.add(varCreator.createTmp(context, rValConcrete));
          } else {
            assert (op == AssignOp.APPEND);
            // Create temporary variable to append
            rValTargets.add(varCreator.createTmp(context, rValConcrete));
          }
        }
        reducedLVals.add(reducedLVal);
      }
    }

    return new LRVals(reducedLVals, rValTargets, skipEval);
  }

  /**
   * Perform any final actions for LVals like inserting into arrays
   * 
   * @param context
   * @param target
   * @return the final lvalues that can be waited on
   * @throws UserException
   */
  public List<Var> finalizeLVals(Context context, AssignOp op, LRVals target)
      throws UserException {
    List<Var> result = new ArrayList<Var>(target.rValTargets.size());
    for (int i = 0; i < target.reducedLVals.size(); i++) {
      LValue lVal = target.reducedLVals.get(i);
      Var rValVar = target.rValTargets.get(i);

      Var resultVar;
      if (op == AssignOp.ASSIGN && isReducedVarLVal(lVal)) {
        if (Types.isRefTo(rValVar, lVal.var)) {
          // dereference if needed
          exprWalker.dereference(context, lVal.var, rValVar);
        } else {
          // if not reference, types should match
          assert (rValVar.type().assignableTo(lVal.var.type()));
        }
        resultVar = lVal.var;
      } else if (op == AssignOp.ASSIGN && isReducedArrLVal(lVal)) {
        // Insert into array if needed
        handle1DArrayLVal(context, lVal, rValVar);

        // TODO: sometimes this will give back a reference to a var
        // rather than the actual thing that should be waited on
        resultVar = rValVar;
      } else if (op == AssignOp.APPEND && isReducedVarLVal(lVal)) {
        Var bag = lVal.var;
        resultVar = backendBagAppend(context, bag, rValVar);
      } else {
        assert (op == AssignOp.APPEND && isReducedArrLVal(lVal));
        appendToBagInArray(context, lVal.var, lVal.indices.get(0), rValVar);
        // TODO: sometimes this will give back a reference to a var
        // rather than the actual thing that should be waited on
        resultVar = rValVar;
      }

      result.add(resultVar);
    }
    return result;
  }

  /**
   * @param reducedLVal
   * @return true if this is a valid reduced array lval
   */
  private boolean isReducedArrLVal(LValue reducedLVal) {
    return reducedLVal.indices.size() == 1
        && reducedLVal.indices.get(0).getType() == ExMParser.ARRAY_PATH;
  }

  /**
   * @param reducedLVal
   * @return true if this is a valid reduced variable lval
   */
  private boolean isReducedVarLVal(LValue reducedLVal) {
    return reducedLVal.indices.isEmpty();
  }

  private LValue declareLValueIfNeeded(Context context, WalkMode walkMode,
      LValue lVal, Type rValType) throws UserException {
    // Declare and initialize lval if not previously declared
    if (lVal.var == null) {
      // Should already have declared if only evaluating
      assert (walkMode != WalkMode.ONLY_EVALUATION) : walkMode;
      LValue newLVal = lVal.varDeclarationNeeded(context, rValType);
      assert (newLVal != null && newLVal.var != null);
      varCreator.createVariable(context, newLVal.var);
      return newLVal;
    } else {
      return lVal;
    }
  }

  /**
   * Process an LValue for an assignment, resulting in a variable that we can
   * assign the RValue to
   * 
   * @param context
   * @param lval
   * @return an Lvalue which is either a simple value, or an array with a single
   *         level of indexing
   */
  private LValue reduceLVal(Context context, LValue lval)
      throws UndefinedVarError, UserException, UndefinedTypeException,
      TypeMismatchException {
    LogHelper.trace(context, ("Evaluating lval " + lval.toString()
        + " with type " + lval.getType(context)));
    if (lval.var == null) {
      lval = new LValue(lval.predecessor, lval.tree,
          context.lookupVarInternal(lval.varName), lval.indices);
    }

    boolean done = false;
    // Iteratively reduce the target until we have the thing we're assigning to
    while (!done && lval.indices.size() > 0) {
      int indexType = lval.indices.get(0).getType();
      switch (indexType) {
      case ExMParser.STRUCT_PATH:
        lval = reduceStructLVal(context, lval);
        break;
      case ExMParser.ARRAY_PATH: {
        if (lval.indices.size() == 1) {
          // Perform final insert after we evaluate RVal
          done = true;
        } else {
          lval = reduceMultiDArrayLVal(context, lval);
        }
        break;
      }
      default:
        throw new STCRuntimeError("Unexpected lval tree type: "
            + LogHelper.tokName(indexType));
      }

      LogHelper.trace(context, "Reduced to lval " + lval.toString()
          + " with type " + lval.getType(context));
    }
    return lval;
  }

  /**
   * Processes part of an assignTarget path: the prefix of struct lookups. E.g.
   * if we're trying to assign to x.field.field.field[0], then this function
   * handles the 3x field lookups, making sure that a handle to
   * x.field.field.field is put into a temp variable, say called t0 This will
   * then return a new assignment target for t0[0] for further processing
   * 
   * @param context
   * @param lval
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws TypeMismatchException
   */
  private LValue reduceStructLVal(Context context, LValue lval)
      throws UserException, UndefinedTypeException, TypeMismatchException {
    // The variable at root of the current struct path
    Var rootVar = context.lookupVarUser(lval.varName);

    ArrayList<String> fieldPath = new ArrayList<String>();

    int structPathIndex = 0;
    while (structPathIndex < lval.indices.size()
        && lval.indices.get(structPathIndex).getType() == ExMParser.STRUCT_PATH) {
      SwiftAST pathTree = lval.indices.get(structPathIndex);
      String fieldName = pathTree.child(0).getText();
      fieldPath.add(fieldName);
      structPathIndex++;
    }
    final int structPathLen = structPathIndex;


    Type fieldType = lval.getType(context, structPathLen);

    Var field = varCreator.createStructFieldAlias(context, rootVar,
                                          fieldType, fieldPath);

    if (VarRepr.storeRefInStruct(fieldType)) {
      // Lookup ref stored in struct
      backend.structRetrieveSub(VarRepr.backendVar(field),
                  VarRepr.backendVar(rootVar), fieldPath);
    } else {
      // Create an alias to data stored in struct
      backend.structCreateAlias(VarRepr.backendVar(field),
                  VarRepr.backendVar(rootVar), fieldPath);

    }
    
    List<SwiftAST> indicesLeft =
          lval.indices.subList(structPathLen, lval.indices.size());
      LValue newTarget = new LValue(lval, lval.tree, field, indicesLeft);
      
    LogHelper.trace(context, "Transform target " + lval.toString() + "<"
        + lval.getType(context).toString() + "> to " + newTarget.toString()
        + "<" + newTarget.getType(context).toString() + "> by looking up "
        + structPathLen + " fields");
    return newTarget;
  }

  /**
   * Perform the final step of evaluating an array lval
   * 
   * @param context
   * @param outerArrLVal
   *          the outermost array from the current lval
   * @param lval
   *          the current lval to reduce
   * @param rValVar
   *          if the rval is a variable, the variable, otherwise null
   * @param rValIsRef
   *          if the Rvalue should be dereference before insertion
   * @param afterActions
   * @throws TypeMismatchException
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void handle1DArrayLVal(Context context, LValue lval, Var rValVar)
      throws TypeMismatchException, UserException, UndefinedTypeException {
    assert (lval.indices.size() == 1);
    assert (Types.isArray(lval.var) || Types.isArrayRef(lval.var));

    // Check that it is a valid array
    final Var arr = lval.var;

    // Find or create variable to store expression result
    
    // Work out whether we store or copy in result.
    // E.g. if we store ints inline in container, storage type is int,
    //      and we can store directly if we have a $int, or copy if we have
    //      an int rval
    Type elemStorageType = VarRepr.containerElemRepr(
                    Types.containerElemType(arr), true);
    boolean rValDeref = !rValVar.type().assignableTo(elemStorageType);
    if (rValDeref) {
      assert(rValVar.type().assignableTo(
                             Types.retrievedType(elemStorageType)));
    }

    // We know what variable the result will go into now
    // Now need to work out the index and generate code to insert the
    // variable into the array

    SwiftAST indexTree = lval.indices.get(0);

    SwiftAST indexExpr = checkArrayKeyExpr(context, lval.var, indexTree);

    boolean isArrayRef = Types.isArrayRef(arr);
    Type keyType = Types.arrayKeyType(arr);
    Long literal = Literals.extractIntLit(context, indexExpr);

    if (Types.isInt(keyType) && literal != null) {
      long arrIx = literal;
      // Add this variable to array
      backendArrayInsert(arr, arrIx, rValVar, isArrayRef, rValDeref);
    } else {
      // Handle the general case where the index must be computed
      Var indexVar = exprWalker.eval(context, indexExpr, keyType, false, null);
      backendArrayInsert(arr, indexVar, rValVar, isArrayRef, rValDeref);
    }

  }

  /**
   * Create an empty bag inside an array/array ref, or return the bag at index
   * if not present
   * 
   * @param context
   * @param var
   * @param elem
   *          variable to insert into bag
   * @return
   * @throws UserException
   * @throws TypeMismatchException
   */
  private Var appendToBagInArray(Context context, Var arr, SwiftAST keyTree,
      Var elem) throws UserException {
    Var key = evalKey(context, arr, checkArrayKeyExpr(context, arr, keyTree));
    List<Var> waitVars;
    if (Types.isArrayRef(arr)) {
      // Wait for index and reference
      waitVars = Arrays.asList(key, arr);
    } else {
      assert (Types.isArray(arr));
      // Wait for index only
      waitVars = Arrays.asList(key);
    }

    backend.startWaitStatement(
        context.constructName("bag-create-wait"),
        VarRepr.backendVars(waitVars), WaitMode.WAIT_ONLY,
        false, false, TaskMode.LOCAL);

    Var derefArr; // Plain array (not reference);
    if (Types.isArrayRef(arr)) {
      derefArr = varCreator.createTmpAlias(context, Types.retrievedType(arr));
      exprWalker.retrieveRef(VarRepr.backendVar(derefArr),
                             VarRepr.backendVar(arr), true);
    } else {
      derefArr = arr;
    }

    Var keyVal = exprWalker.retrieveToVar(context, key);

    Type bagType = Types.containerElemType(derefArr);
    assert (Types.isBag(bagType));
    Var bag = varCreator.createTmpAlias(context, bagType);
    // create or get nested bag instruction
    backend.arrayCreateBag(VarRepr.backendVar(bag),
        VarRepr.backendVar(derefArr), VarRepr.backendArg(keyVal));
    backendBagAppend(context, bag, elem);

    backend.endWaitStatement();
    return bag;
  }

  /**
   * Handle a prefix of array lookups for the assign target
   * 
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws TypeMismatchException
   */
  private LValue reduceMultiDArrayLVal(Context context, LValue lval)
      throws UserException {

    SwiftAST indexTree = lval.indices.get(0);
    SwiftAST indexExpr = checkArrayKeyExpr(context, lval.var, indexTree);

    assert (lval.indices.size() > 1);
    // multi-dimensional array handling: need to dynamically create subarray
    Var lvalArr = context.lookupVarUser(lval.varName);
    Type memberType = lval.getType(context, 1);
    Var mVar; // Variable for member we're looking up
    if (Types.isArray(memberType)) {
      Long literal = Literals.extractIntLit(context, indexExpr);
      Var backendLValArr = VarRepr.backendVar(lvalArr);
      if (literal != null) {
        long arrIx = literal;
        // Add this variable to array
        if (Types.isArray(lvalArr.type())) {
          mVar = varCreator.createTmpAlias(context, memberType);
          backend.arrayCreateNestedImm(VarRepr.backendVar(mVar),
              backendLValArr, Arg.createIntLit(arrIx));
        } else {
          assert (Types.isArrayRef(lvalArr.type()));
          mVar = varCreator.createTmp(context, new RefType(memberType, true));
          backend.arrayRefCreateNestedImm(VarRepr.backendVar(mVar),
                          backendLValArr, Arg.createIntLit(arrIx));
        }

      } else {
        // Handle the general case where the index must be computed
        mVar = varCreator.createTmp(context, new RefType(memberType, true));
        Var indexVar = evalKey(context, lvalArr, indexExpr);

        Var backendIx = VarRepr.backendVar(indexVar);
        if (Types.isArray(lvalArr.type())) {
          backend.arrayCreateNestedFuture(VarRepr.backendVar(mVar),
                                        backendLValArr, backendIx);
        } else {
          assert (Types.isArrayRef(lvalArr.type()));
          backend.arrayRefCreateNestedFuture(VarRepr.backendVar(mVar),
                                            backendLValArr, backendIx);
        }
      }
    } else {
      /*
       * Retrieving a member that isn't a container type must use reference
       * because we might have to wait for the result to be inserted
       */
      mVar = varCreator.createTmp(context, new RefType(memberType, true));
    }

    return new LValue(lval, lval.tree, mVar, lval.indices.subList(1,
        lval.indices.size()));
  }
  
  private Var evalKey(Context context, Var arr, SwiftAST indexExpr)
      throws UserException {
    Type keyType = Types.arrayKeyType(arr);
    Var indexVar = exprWalker.eval(context, indexExpr, keyType,
                                   false, null);
    return indexVar;
  }
  

  /**
   * Type-check ARRAY_PATH tree and return index expression
   * @param context
   * @param array
   * @param indexExpr
   * @return
   * @throws UserException
   * @throws TypeMismatchException
   */
  private SwiftAST checkArrayKeyExpr(Context context, Var array,
      SwiftAST indexExpr) throws UserException, TypeMismatchException {
    assert (indexExpr.getType() == ExMParser.ARRAY_PATH);
    assert (indexExpr.getChildCount() == 1);
    // Typecheck index expression
    Type indexType = TypeChecker.findSingleExprType(context, 
                                             indexExpr.child(0));
    if (!Types.isArrayKeyFuture(array, indexType)) {
      throw new TypeMismatchException(context, 
          "Array key type mismatch in LVal.  " +
          "Expected: " + Types.arrayKeyType(array) + " " +
          "Actual: " + indexType.typeName());
    }
    return indexExpr.child(0);
  }

  private void backendArrayInsert(Var arr, long ix, Var member,
              boolean isArrayRef, boolean rValIsVal) {
    Var backendArr = VarRepr.backendVar(arr);
    Var backendMember = VarRepr.backendVar(member);
    Arg ixArg = Arg.createIntLit(ix);
    if (isArrayRef) {
      if (rValIsVal) {
        backend.arrayRefStoreImm(backendArr, ixArg,  backendMember.asArg());
      } else {
        // This should only be run when assigning to nested array
        backend.arrayRefCopyInImm(backendArr, ixArg, backendMember);
      }
    } else {
      assert(!isArrayRef);
      if (rValIsVal) {
        backend.arrayStore(backendArr, ixArg, backendMember.asArg());
      } else {
        backend.arrayCopyInImm(backendArr, ixArg, backendMember);
      }
    }
  }
  
  private void backendArrayInsert(Var arr, Var ix, Var member,
                                  boolean isArrayRef, boolean rValIsVal) {
    Var backendArr = VarRepr.backendVar(arr);
    Var backendIx = VarRepr.backendVar(ix);
    Var backendMember = VarRepr.backendVar(member);
    if (isArrayRef) {
      if (rValIsVal) {
        backend.arrayRefStoreFuture(backendArr, backendIx,
                                    backendMember.asArg());
      } else {
        backend.arrayRefCopyInFuture(backendArr, backendIx, backendMember);
      }
    } else {
      assert(!isArrayRef);
      if (rValIsVal) {
        backend.arrayStoreFuture(backendArr, backendIx, backendMember.asArg());
      } else {
        backend.arrayCopyInFuture(backendArr, backendIx, backendMember);
      }
    }
  }

  /**
   * Helper function to insert element into bag, selecting appropriate
   * backend implementation.
   * @param context
   * @param bag
   * @param elem
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private Var backendBagAppend(Context context, Var bag, Var elem)
                                            throws UserException {
    // Result variable for statement, which should be element being
    // inserted
    Var stmtResultVar = elem;

    Type elemType = Types.containerElemType(bag);
    
    boolean bagRef = Types.isBagRef(bag);
    boolean elemRef = Types.isRefTo(elem, elemType); 
    boolean openWait1 = bagRef || elemRef; 
    
    // May need to open wait in order to deal with dereference bag or element
    if (openWait1) {
      // Wait, then deref if needed
      String waitName = context.getFunctionContext().constructName(
                                                "bag-deref-append");
      
      List<Var> waitVars = new ArrayList<Var>(2);
      if (bagRef) {
        waitVars.add(bag);
      }
      if (elemRef) {
        waitVars.add(elem);
      }
          
      backend.startWaitStatement(waitName,VarRepr.backendVars(waitVars),
             WaitMode.WAIT_ONLY, false, false, TaskMode.LOCAL);
      
      if (bagRef) {
        // Dereference and use in place
        Var derefedBag = varCreator.createTmpAlias(context,
                               Types.retrievedType(bag));
        exprWalker.retrieveRef(VarRepr.backendVar(derefedBag),
                               VarRepr.backendVar(bag), true);
        bag = derefedBag;
      }
      if (elemRef) {
        // TODO: use real signal var rather than reference
        stmtResultVar = elem;
        
        // Dereference and use in place of old var
        Var derefedElem = varCreator.createTmpAlias(context, elemType);
        exprWalker.retrieveRef(VarRepr.backendVar(derefedElem),
                               VarRepr.backendVar(elem), true);
        elem = derefedElem;
      }
    }
    
    Var elemVal;
    // May need to add another wait to retrieve value
    boolean openWait2 = !VarRepr.storeRefInContainer(elem);
    if (openWait2) {
      String waitName = context.getFunctionContext().constructName(
                                                "bag-load-append");
      backend.startWaitStatement(waitName, VarRepr.backendVars(elem),
              WaitMode.WAIT_ONLY, false, false, TaskMode.LOCAL);
      elemVal = exprWalker.retrieveToVar(context, elem);
    } else {
      elemVal = elem;
    }
    
    // Do the actual insert
    backend.bagInsert(VarRepr.backendVar(bag), VarRepr.backendArg(elemVal));
    
    if (openWait2) {
      backend.endWaitStatement();
    }

    if (openWait1) {
      backend.endWaitStatement();
    }
    return stmtResultVar;
  }

  /**
   * Class to track current state of evaluating matching LVals and RVals
   */
  static class LRVals {
    private LRVals(List<LValue> reducedLVals, List<Var> rValTargets,
        boolean skipREval) {
      assert(reducedLVals.size() == rValTargets.size());
      this.reducedLVals = reducedLVals;
      this.rValTargets = rValTargets;
      this.skipREval = skipREval;
    }
    /**
     * LVals that have been stripped down
     */
    final List<LValue> reducedLVals;
    /**
     * Variables that we want to evaluate RVals into
     */
    final List<Var> rValTargets;
    /**
     * True if we can skip evaluation of RVal
     */
    final boolean skipREval;
  }
}
