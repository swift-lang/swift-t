package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.descriptor.ArrayRange;
import exm.stc.ast.descriptor.ForLoopDescriptor;
import exm.stc.ast.descriptor.ForLoopDescriptor.LoopVar;
import exm.stc.ast.descriptor.ForeachLoop;
import exm.stc.ast.descriptor.FunctionDecl;
import exm.stc.ast.descriptor.If;
import exm.stc.ast.descriptor.InlineCode;
import exm.stc.ast.descriptor.IterateDescriptor;
import exm.stc.ast.descriptor.LValue;
import exm.stc.ast.descriptor.Literals;
import exm.stc.ast.descriptor.Switch;
import exm.stc.ast.descriptor.Update;
import exm.stc.ast.descriptor.VariableDeclaration;
import exm.stc.ast.descriptor.VariableDeclaration.VariableDescriptor;
import exm.stc.ast.descriptor.Wait;
import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.TclFunRef;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVariableException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.exceptions.VariableUsageException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.Builtins.TclOpTemplate;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayInfo;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.SubType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.frontend.Context.FnProp;
import exm.stc.frontend.VariableUsageInfo.VInfo;
/**
 * This class walks the Swift AST.
 * It performs typechecking and dataflow analysis as it goes
 *
 */
public class ASTWalker {

  private String inputFile;
  private VariableUsageAnalyzer varAnalyzer;
  private CompilerBackend backend;
  private LineMapping lineMapping;
  private VarCreator varCreator = null;
  private ExprWalker exprWalker = null;

  public ASTWalker(String inputFile,
      LineMapping lineMapping) {
    this.inputFile = inputFile;
    this.varAnalyzer = new VariableUsageAnalyzer(lineMapping);
    this.lineMapping = lineMapping;
  }


  /**
   * Walk the AST and make calls to backend to generate lower level code
   * @param backend
   * @param tree
   * @throws UserException
   */
  public void walk(CompilerBackend backend, SwiftAST tree) 
          throws UserException {
    this.backend = backend;
    this.varCreator = new VarCreator(backend);
    this.exprWalker = new ExprWalker(varCreator, backend, lineMapping);
    GlobalContext context = new GlobalContext(inputFile, Logging.getSTCLogger());

    // Dump ANTLR's view of the SwiftAST (unsightly):
    // if (logger.isDebugEnabled())
    // logger.debug("tree: \n" + tree.toStringTree());

    // Use our custom printTree
    if (LogHelper.isDebugEnabled())
      LogHelper.debug(context, tree.printTree());

    backend.header();
    walkProgram(context, tree);
    FunctionType fn = context.lookupFunction("main");
    if (fn == null || 
          !context.hasFunctionProp("main", FnProp.COMPOSITE)) {
      throw new UndefinedFunctionException(context,
          "No composite main function was defined in the script");
    }
    backend.turbineStartup();
  }

  private void walkProgram(Context context, SwiftAST programTree)
      throws UserException {
    /*
     * Do two passes over the program
     * First pass:
     *  - Register (but don't compile) all functions
     * Second pass:
     *  - Compile composite and app functions, now that all function names are known
     */
    int token = programTree.getType();
    

    context.syncFilePos(programTree, lineMapping);

    if (token == ExMParser.PROGRAM) {
      for (SwiftAST topLevelDefn: programTree.children()) {
        int type = topLevelDefn.getType();
        context.syncFilePos(topLevelDefn, lineMapping);
        switch (type) {

        case ExMParser.DEFINE_BUILTIN_FUNCTION:
          defineBuiltinFunction(context, topLevelDefn);
          break;

        case ExMParser.DEFINE_FUNCTION:
          defineFunction(context, topLevelDefn);
          break;

        case ExMParser.DEFINE_APP_FUNCTION:
          defineAppFunction(context, topLevelDefn);
          break;

        case ExMParser.DEFINE_NEW_STRUCT_TYPE:
          defineNewStructType(context, topLevelDefn);
          break;
          
        case ExMParser.DEFINE_NEW_TYPE:
        case ExMParser.TYPEDEF:
          defineNewType(context, topLevelDefn, type == ExMParser.TYPEDEF);
          break;
          
        case ExMParser.GLOBAL_CONST:
          globalConst(context, topLevelDefn);
          break;
        
        case ExMParser.EOF:
          endOfFile(context, topLevelDefn);
          break;

        default:
          String name = LogHelper.tokName(type);
          throw new STCRuntimeError("Unexpected token: " + name
              + " at program top level");
        }
      }
      
      context.syncFilePos(programTree, lineMapping);
      // Second pass to compile functions
      for (int i = 0; i < programTree.getChildCount(); i++) {
        SwiftAST topLevelDefn = programTree.child(i);
        context.syncFilePos(topLevelDefn, lineMapping);
        int type = topLevelDefn.getType();
        switch (type) {
        case ExMParser.DEFINE_FUNCTION:
          compileFunction(context, topLevelDefn);
          break;

        case ExMParser.DEFINE_APP_FUNCTION:
          compileAppFunction(context, topLevelDefn);
          break;
        }
      }
    } else {
      throw new STCRuntimeError("Unexpected token: " +
          LogHelper.tokName(token) + " instead of PROGRAM");
    }
  }

  /**
   * Walk a tree that is a procedure statement.
   *
   * @param context
   * @param tree
   * @param vu
   * @param blockVu
   * @throws UserException
   */
  private void walkStatement(Context context, SwiftAST tree,
                             VariableUsageInfo blockVu)
  throws UserException
  {
      int token = tree.getType();
      context.syncFilePos(tree, lineMapping);
      switch (token) {
        case ExMParser.BLOCK:
          // Create a local context (stack frame) for this nested block
          LocalContext nestedContext = new LocalContext(context);
          // Set up nested stack frame

          backend.startNestedBlock();
          backend.addComment("start of block@" + context.getFileLine());
          block(nestedContext, tree);
          backend.addComment("end of block@" + context.getFileLine());
          backend.endNestedBlock();
          break;

        case ExMParser.IF_STATEMENT:
          ifStatement(context, tree);
          break;

        case ExMParser.SWITCH_STATEMENT:
          switchStatement(context, tree);
          break;

        case ExMParser.DECLARATION:
          declareVariables(context, tree, blockVu);
          break;

        case ExMParser.ASSIGN_EXPRESSION:
          assignExpression(context, tree);
          break;

        case ExMParser.EXPR_STMT:
          exprStatement(context, tree);
          break;

        case ExMParser.FOREACH_LOOP:
          foreach(context, tree);
          break;
        
        case ExMParser.FOR_LOOP:
          forLoop(context, tree);
          break;
          
        case ExMParser.ITERATE:
          iterate(context, tree);
          break;
          
        case ExMParser.WAIT_STATEMENT:
          waitStmt(context, tree);
          break;
          
        case ExMParser.UPDATE:
          updateStmt(context, tree);
          break;
          
        default:
          throw new STCRuntimeError
          ("Unexpected token type for statement: " +
              LogHelper.tokName(token));
      }
  }

  private void waitStmt(Context context, SwiftAST tree) 
                                  throws UserException {
    Wait wait = Wait.fromAST(context, tree);
    ArrayList<Var> waitEvaled = new ArrayList<Var>();
    for (SwiftAST expr: wait.getWaitExprs()) {
      Var res = exprWalker.eval(context, expr, 
          TypeChecker.findSingleExprType(context, expr), false, null);
      waitEvaled.add(res);
    }
    
    ArrayList<Var> usedVars = new ArrayList<Var>();
    ArrayList<Var> keepOpenVars = new ArrayList<Var>();
    summariseBranchVariableUsage(context, 
                    Arrays.asList(wait.getBlock().getVariableUsage()), 
                                  usedVars, keepOpenVars);
    
    
    // Quick sanity check to see we're not directly blocking
    // on any arrays written inside
    HashSet<String> waitVarSet = 
        new HashSet<String>(Var.nameList(waitEvaled));
    waitVarSet.retainAll(Var.nameList(keepOpenVars));
    if (waitVarSet.size() > 0) {
      throw new UserException(context, 
          "Deadlock in wait statement. The following arrays are written "
        + "inside the body of the wait: " + waitVarSet.toString());
    }
    
    backend.startWaitStatement(
          context.getFunctionContext().constructName("explicitwait"),
                      waitEvaled, usedVars, keepOpenVars,
                      WaitMode.EXPLICIT, false, TaskMode.LOCAL);
    block(new LocalContext(context), wait.getBlock());
    backend.endWaitStatement(usedVars, keepOpenVars);
  }


  /**
   * block operates on a BLOCK node of the AST. This should be called for every
   * logical code block (e.g. function bodies, condition bodies, etc) in the
   * program
   *
   * @param context
   *          a new context for this block
   */
  private void block(Context context, SwiftAST tree) throws UserException {
    LogHelper.trace(context, "block start");

    if (tree.getType() != ExMParser.BLOCK) {
      throw new STCRuntimeError("Expected to find BLOCK token" + " at "
          + tree.getLine() + ":" + tree.getCharPositionInLine());
    }

    VariableUsageInfo blockVu = tree.checkedGetVariableUsage();

    for (SwiftAST stmt: tree.children()) {
      walkStatement(context, stmt, blockVu);
    }

    closeBlockVariables(context);

    LogHelper.trace(context, "block done");
  }

  /**
   * Make sure all arrays in block are closed upon exiting
   *
   * @param context
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void closeBlockVariables(Context context) 
          throws UndefinedTypeException, UserException {
    for (Var v : context.getArraysToClose()) {
      assert(v.defType() != DefType.INARG);
      assert(Types.isArray(v.type()));
      backend.decrWriters(v);
    }
    
    for (Var v: context.getScopeVariables()) {
      if (v.storage() == VarStorage.STACK || v.storage() == VarStorage.TEMP) {
        backend.decrRef(v);
      }
    }
  }

  private static class VarInfoPair {
    public final Var var;
    public final VInfo vinfo;
    public VarInfoPair(Var var, VInfo vinfo) {
      super();
      this.var = var;
      this.vinfo = vinfo;
    }
    
    @Override
    public String toString() {
      return this.var.toString() + " pair";
    }
  }
  private void findArraysInStruct(Context context,
      Var root, VInfo structVInfo, List<VarInfoPair> arrays)
          throws UndefinedTypeException, UserException {
    findArraysInStructToClose(context, root, root, structVInfo,
        new Stack<String>(), arrays);
  }

  private void findArraysInStructToClose(Context context,
      Var root, Var struct, VInfo structVInfo,
      Stack<String> fieldPath, List<VarInfoPair> arrays) throws UndefinedTypeException,
                                                                      UserException {
    StructType vtype = (StructType)struct.type();
    for (StructField f: vtype.getFields()) {
      fieldPath.push(f.getName());
      if (Types.isArray(f.getType())) {
        Var fieldVar = exprWalker.structLookup(context, struct, 
            f.getName(), false, root, fieldPath);
        arrays.add(new VarInfoPair(fieldVar, structVInfo.getFieldVInfo(f.getName())));
      } else if (Types.isStruct(f.getType())) {
        VInfo nestedVInfo = structVInfo.getFieldVInfo(f.getName());
        assert(nestedVInfo != null);
        Var field = exprWalker.structLookup(context, struct, f.getName(),
              false, root, fieldPath);

        findArraysInStructToClose(context, root, field, nestedVInfo, fieldPath,
            arrays);
      }
      fieldPath.pop();
    }
  }

  private void ifStatement(Context context, SwiftAST tree)
      throws UserException {    
    LogHelper.trace(context, "if...");
    If ifStmt = If.fromAST(context, tree); 
    
    
    // Condition must be boolean and stored to be retrieved later
    Var conditionVar = exprWalker.eval(context,
        ifStmt.getCondition(), ifStmt.getCondType(context),
        false, null);
    assert (conditionVar != null);

    // A list of variables that might be referenced in either branch
    List<Var> usedVariables = new ArrayList<Var>();

    // List of arrays that might be modified on one branch or the other
    // IF an array is modified on a branch, then we have to make sure that
    // it won't be prematurely closed
    List<Var> keepOpenVars = new ArrayList<Var>();

    VariableUsageInfo thenVU = ifStmt.getThenBlock().checkedGetVariableUsage();

    List<VariableUsageInfo> branchVUs;
    if (ifStmt.hasElse()) {
      VariableUsageInfo elseVU = ifStmt.getElseBlock()
                                    .checkedGetVariableUsage();
      branchVUs = Arrays.asList(thenVU, elseVU);
    } else {
      branchVUs = Arrays.asList(thenVU);
    }

    // Check that condition var isn't assigned inside block - would be deadlock
    checkConditionalDeadlock(context, conditionVar, branchVUs);

    summariseBranchVariableUsage(context, branchVUs, usedVariables,
        keepOpenVars);

    if (!usedVariables.contains(conditionVar))
      usedVariables.add(conditionVar);

    FunctionContext fc = context.getFunctionContext();
    backend.startWaitStatement( fc.constructName("if"), 
              Arrays.asList(conditionVar), usedVariables, keepOpenVars, 
                WaitMode.DATA_ONLY, false, TaskMode.LOCAL);

    Context waitContext = new LocalContext(context);
    Var condVal = varCreator.fetchValueOf(waitContext, conditionVar);
    backend.startIfStatement(Arg.createVar(condVal), ifStmt.hasElse());
    block(new LocalContext(waitContext), ifStmt.getThenBlock());

    if (ifStmt.hasElse()) {
      backend.startElseBlock();
      block(new LocalContext(waitContext), ifStmt.getElseBlock());
    }
    backend.endIfStatement();
    backend.endWaitStatement(usedVariables, keepOpenVars);
  }

  /**
   * Check for deadlocks of the form:
   * if (a) {
   *   a = 3;
   * } else {
   *   a = 2;
   * }
   * We should not allow any code to be compiled in which a variable is inside
   * a conditional statement for each is is the condition
   * TODO: this is a very limited form of deadlock detection.  In
   *      general we need to check the full variable dependency chain to make
   *      sure that the variable in the conditional statement isn't dependent
   *      at all on anything inside the condition
   * @param context
   * @param conditionVar
   * @param branchVU
   * @throws VariableUsageException
   */
  private void checkConditionalDeadlock(Context context, Var conditionVar,
      List<VariableUsageInfo> branchVUs) throws VariableUsageException {
    for (VariableUsageInfo branchVU: branchVUs) {
      assert(branchVU != null);
      VInfo vinfo = branchVU.lookupVariableInfo(conditionVar.name());
      if (vinfo != null && vinfo.isAssigned() != Ternary.FALSE) {
        throw new VariableUsageException(context, "Deadlock on " +
            conditionVar.name() + ", var is assigned inside conditional"
            + " branch for which it is the condition");
      }
    }
  }

  /**
   *
   * @param context
   * @param branchVUs
   *          The variable usage info for all branches
   * @param usedVariables
   *          All variables read or written in a branch added here
   * @param keepOpenVars
   *          All vars that might be written are added here
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void summariseBranchVariableUsage(Context context,
      List<VariableUsageInfo> branchVUs, List<Var> usedVariables,
      List<Var> keepOpenVars) throws UndefinedTypeException, UserException {
    for (Var v : context.getVisibleVariables()) {
      Ternary isUsed = Ternary.FALSE;
      for (VariableUsageInfo bvu : branchVUs) {
        VInfo vi = bvu.lookupVariableInfo(v.name());
        if (vi == null) {
          isUsed = Ternary.or(isUsed, Ternary.FALSE);
        } else {
          isUsed = Ternary.or(isUsed, vi.isUsed());
        }
      }
      if (isUsed != Ternary.FALSE) {
        usedVariables.add(v);
      }

      // Secondly, see if it is an array that might be modified
      if (Types.isArray(v.type())) {
        for (VariableUsageInfo bvu : branchVUs) {
          VInfo vi = bvu.lookupVariableInfo(v.name());
          if (vi != null && vi.isAssigned() != Ternary.FALSE) {
            keepOpenVars.add(v);
            break;
          }
        }
      } else if (Types.isStruct(v.type())) {
        // Need to find arrays inside structs
        ArrayList<VarInfoPair> arrs = new ArrayList<VarInfoPair>();
        // This procedure might add the same array multiple times,
        // so use a set to avoid duplicates
        HashSet<Var> alreadyFound = new HashSet<Var>();
        for (VariableUsageInfo bvu : branchVUs) {
          arrs.clear();
          findArraysInStruct(context, v, bvu.lookupVariableInfo(v.name()),
                                                                        arrs);
          for (VarInfoPair p: arrs) {
            if (p.vinfo.isAssigned() != Ternary.FALSE) {
              alreadyFound.add(p.var);
            }
          }
        }
        keepOpenVars.addAll(alreadyFound);
      }
    }

  }

  private void switchStatement(Context context, SwiftAST tree)
       throws UserException {
    LogHelper.trace(context, "switch...");    
    
    // Evaluate into a temporary variable. Only int supported now
    
    Switch sw = Switch.fromAST(context, tree);
    sw.typeCheck(context);
    
    Var switchVar = exprWalker.eval(context, sw.getSwitchExpr(), Types.F_INT, true, null);

    // A list of variables that might be referenced in either branch
    List<Var> usedVariables = new ArrayList<Var>();

    // List of vars that might be modified on one branch or the other and
    // need to be kept open
    // IF an array, e.g., is modified on a branch, then we have to make sure 
    // that it won't be prematurely closed
    List<Var> keepOpenVars = new ArrayList<Var>();

    List<VariableUsageInfo> branchVUs = new ArrayList<VariableUsageInfo>();
    for (SwiftAST b : sw.getCaseBodies()) {
      branchVUs.add(b.checkedGetVariableUsage());
    }

    checkConditionalDeadlock(context, switchVar, branchVUs);
    summariseBranchVariableUsage(context, branchVUs, usedVariables,
        keepOpenVars);
    if (!usedVariables.contains(switchVar))
        usedVariables.add(switchVar);

    // Generate all of the code
    FunctionContext fc = context.getFunctionContext();
    backend.startWaitStatement( fc.constructName("switch"),
                Arrays.asList(switchVar), usedVariables, keepOpenVars,
                WaitMode.DATA_ONLY, false, TaskMode.LOCAL);

    Context waitContext = new LocalContext(context);
    Var switchVal = varCreator.createValueOfVar(waitContext,
                                                     switchVar); 

    backend.retrieveInt(switchVal, switchVar);

    LogHelper.trace(context, "switch: " + 
            sw.getCaseBodies().size() + " cases");
    backend.startSwitch(Arg.createVar(switchVal), sw.getCaseLabels(),
                                                         sw.hasDefault());
    for (SwiftAST caseBody : sw.getCaseBodies()) {
      block(new LocalContext(waitContext), caseBody);
      backend.endCase();
    }
    backend.endSwitch();
    backend.endWaitStatement(usedVariables, keepOpenVars);
  }

  private void foreach(Context context, SwiftAST tree) throws UserException {
    ForeachLoop loop = ForeachLoop.fromAST(context, tree); 
    
    if (loop.iteratesOverRange() && loop.getCountVarName() == null) {
      foreachRange(context, loop);
    } else {
      foreachArray(context, loop);
    }
  }
  /**
   * Handle the special case of a foreach loop where we are looping over range
   * specified by two or three integer parameters
   * @param context
   * @param loop
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void foreachRange(Context context, ForeachLoop loop) 
                                          throws UserException {
    ArrayRange range = ArrayRange.fromAST(context, loop.getArrayVarTree());
    range.typeCheck(context);
    
    /* Just evaluate all of the expressions into futures and rely
     * on constant folding in IC to clean up where possible
     */ 
    Var start = exprWalker.eval(context, range.getStart(), Types.F_INT, false, null);
    Var end = exprWalker.eval(context, range.getEnd(), Types.F_INT, false, null);
    Var step;
    if (range.getStep() != null) {
      step = exprWalker.eval(context, range.getStep(), Types.F_INT, false, null);
    } else {
      // Inefficient but constant folding will clean up
      step = varCreator.createTmp(context, Types.F_INT);
      backend.assignInt(step, Arg.createIntLit(1));
    }
    
    ArrayList<Var> usedVariables = new ArrayList<Var>();
    ArrayList<Var> keepOpenVars = new ArrayList<Var>();

    // TODO: correct??
    VariableUsageInfo bodyVU = loop.getBody().checkedGetVariableUsage();
    summariseBranchVariableUsage(context, Arrays.asList(bodyVU),
        usedVariables, keepOpenVars);
    
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("foreach-range");
    
    // Need to pass in futures along with user vars
    ArrayList<Var> waitUsedVariables = 
        new ArrayList<Var>(usedVariables);
    waitUsedVariables.addAll(Arrays.asList(start, end, step));
    backend.startWaitStatement(fc.getFunctionName() + "-wait-range" + loopNum,
               Arrays.asList(start, end, step), waitUsedVariables,
               keepOpenVars, WaitMode.DATA_ONLY, false, TaskMode.LOCAL);
    Context waitContext = new LocalContext(context);
    Var startVal = varCreator.fetchValueOf(waitContext, start);
    Var endVal = varCreator.fetchValueOf(waitContext, end);
    Var stepVal = varCreator.fetchValueOf(waitContext, step);
    Context bodyContext = loop.setupLoopBodyContext(waitContext);
    
    // The per-iteration value of the range
    Var memberVal = varCreator.createValueOfVar(bodyContext,
                                            loop.getMemberVar(), false);
    Var counterVal = loop.getLoopCountVal();
    
    backend.startRangeLoop(fc.getFunctionName() + "-range" + loopNum,
            memberVal, counterVal,
            Arg.createVar(startVal), Arg.createVar(endVal), 
            Arg.createVar(stepVal), usedVariables, keepOpenVars,
            loop.getDesiredUnroll(), loop.getSplitDegree());
    // Need to spawn off task per iteration
    List<Var> waitUsedVars = null;
    if (!loop.isSyncLoop()) {
      waitUsedVars = new ArrayList<Var>(usedVariables);
      waitUsedVars.add(memberVal);
      backend.startWaitStatement(fc.getFunctionName() + "range-iter" + loopNum,
          Collections.<Var>emptyList(), waitUsedVars, keepOpenVars,
          WaitMode.TASK_DISPATCH, false, TaskMode.CONTROL);
    }
    
    // We have the current value, but need to put it in a future in case user
    //  code refers to it
    varCreator.initialiseVariable(bodyContext, loop.getMemberVar());
    backend.assignInt(loop.getMemberVar(), Arg.createVar(memberVal));
    if (loop.getCountVarName() != null) {
      Var loopCountVar = varCreator.createVariable(bodyContext,
          Types.F_INT, loop.getCountVarName(), VarStorage.STACK,
          DefType.LOCAL_USER, null);
      backend.assignInt(loopCountVar, Arg.createVar(counterVal));
    }
    block(bodyContext, loop.getBody());
    if (!loop.isSyncLoop()) {
      backend.endWaitStatement(waitUsedVars, keepOpenVars);
    }
    backend.endRangeLoop(usedVariables, keepOpenVars, loop.getSplitDegree());
    backend.endWaitStatement(usedVariables, keepOpenVars);
  }
  
  /**
   * Handle the general foreach loop where we are looping over array
   * @param context
   * @param loop
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void foreachArray(Context context, ForeachLoop loop)
      throws UserException, UndefinedTypeException {
    Var arrayVar = exprWalker.eval(context, loop.getArrayVarTree(), loop.findArrayType(context), true, null);

    ArrayList<Var> usedVariables = new ArrayList<Var>();
    ArrayList<Var> keepOpenVars = new ArrayList<Var>();

    VariableUsageInfo bodyVU = loop.getBody().checkedGetVariableUsage();
    summariseBranchVariableUsage(context, Arrays.asList(bodyVU),
        usedVariables, keepOpenVars);

    for (Var v: keepOpenVars) {
      if (v.name().equals(arrayVar.name())) {
        throw new STCRuntimeError("Array variable "
                  + v.name() + " is written in the foreach loop "
                  + " it is the loop array for - currently this " +
                  "causes a deadlock due to technical limitations");
      }
    }
    
    // Need to get handle to real array before running loop
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("foreach-array");
    
    Var realArray;
    Context outsideLoopContext;
    ArrayList<Var> arrRefWaitUsedVars = null;
    if (Types.isArrayRef(arrayVar.type())) {
      arrRefWaitUsedVars = new ArrayList<Var>(usedVariables);
      arrRefWaitUsedVars.add(arrayVar);
      // If its a reference, wrap a wait() around the loop call
      backend.startWaitStatement(
          fc.getFunctionName() + "-foreach-refwait" + loopNum,
          Arrays.asList(arrayVar), arrRefWaitUsedVars, keepOpenVars,
          WaitMode.DATA_ONLY, false, TaskMode.LOCAL);

      outsideLoopContext = new LocalContext(context);
      realArray = varCreator.createTmp(outsideLoopContext,
                              arrayVar.type().memberType(), false, true);
      backend.retrieveRef(realArray, arrayVar);
    } else {
      realArray = arrayVar;
      outsideLoopContext = context;
    }
    
    // Block on array
    ArrayList<Var> waitUsedVars = new ArrayList<Var>(usedVariables);
    waitUsedVars.add(realArray);
    backend.startWaitStatement(
        fc.getFunctionName() + "-foreach-wait" + loopNum,
        Arrays.asList(realArray), waitUsedVars, keepOpenVars,
        WaitMode.DATA_ONLY, false, TaskMode.LOCAL);
    
    loop.setupLoopBodyContext(outsideLoopContext);
    Context loopBodyContext = loop.getBodyContext();

    backend.startForeachLoop(fc.getFunctionName() + "-foreach" + loopNum,
        realArray, loop.getMemberVar(), loop.getLoopCountVal(),
        loop.getSplitDegree(), true, usedVariables, keepOpenVars);
    // May need to spawn off each iteration as task - use wait for this
    ArrayList<Var> iterUsedVars = null;
    if (!loop.isSyncLoop()) {
      iterUsedVars = new ArrayList<Var>(usedVariables);
      if (loop.getLoopCountVal() != null)
        iterUsedVars.add(loop.getLoopCountVal());
      backend.startWaitStatement(
          fc.getFunctionName() + "-foreach-spawn" + loopNum,
          Collections.<Var>emptyList(), iterUsedVars, keepOpenVars,
          WaitMode.TASK_DISPATCH, false, TaskMode.CONTROL);
    }
    // If the user's code expects a loop count var, need to create it here
    if (loop.getCountVarName() != null) {
      Var loopCountVar = varCreator.createVariable(loop.getBodyContext(),
          Types.F_INT, loop.getCountVarName(), VarStorage.STACK,
          DefType.LOCAL_USER, null);
      backend.assignInt(loopCountVar, Arg.createVar(loop.getLoopCountVal()));
    }
    block(loopBodyContext, loop.getBody());
    
    // Close spawn wait
    if (!loop.isSyncLoop()) {
      backend.endWaitStatement(iterUsedVars, keepOpenVars);
    }
    backend.endForeachLoop(loop.getSplitDegree(), true, usedVariables,
                           keepOpenVars);

    // Wait for array
    backend.endWaitStatement(waitUsedVars, keepOpenVars);
    if (Types.isArrayRef(arrayVar.type())) {
      // Wait for array ref
      backend.endWaitStatement(arrRefWaitUsedVars, keepOpenVars);
    }
  }

  private void forLoop(Context context, SwiftAST tree) throws UserException {
    ForLoopDescriptor forLoop = ForLoopDescriptor.fromAST(context, tree);
    
    // Evaluate initial values of loop vars
    List<Var> initVals = evalLoopVarExprs(context, forLoop, 
                                                  forLoop.getInitExprs());
    List<Var> usedVariables = new ArrayList<Var>();
    List<Var> keepOpenVars = new ArrayList<Var>();
    summariseBranchVariableUsage(context, 
            Arrays.asList(forLoop.getBody().getVariableUsage()), 
            usedVariables, keepOpenVars);
    
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("forloop");
    String loopName = fc.getFunctionName() + "-forloop-" + loopNum;
    
    HashMap<String, Var> parentLoopVarAliases = 
        new HashMap<String, Var>();
    for (LoopVar lv: forLoop.getLoopVars()) {
      if (lv.declaredOutsideLoop) {
        // Need to copy over value of loop variable on last iteration
        Var parentAlias = 
            varCreator.createVariable(context, lv.var.type(),
                  Var.OUTER_VAR_PREFIX + lv.var.name(),
                  VarStorage.ALIAS, DefType.LOCAL_COMPILER,
                  lv.var.mapping());
        // Copy turbine ID
        backend.makeAlias(parentAlias, lv.var);
        usedVariables.add(parentAlias);
        parentLoopVarAliases.put(lv.var.name(), parentAlias);
      }
    }
    
    LogHelper.debug(context, "usedVariables in forLoop: " + 
            usedVariables.toString() + " at " + forLoop.getBody().getLine() 
            + "." + forLoop.getBody().getCharPositionInLine());
    
    // Create context with loop variables
    Context loopBodyContext = forLoop.createBodyContext(context);
    forLoop.validateCond(loopBodyContext);
    Type condType = TypeChecker.findSingleExprType(loopBodyContext, 
                                              forLoop.getCondition());

    // Evaluate the conditional expression for the first iteration outside the
    // loop, directly using temp names for loop variables
    HashMap<String, String> initRenames = new HashMap<String, String>();
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      initRenames.put(forLoop.getLoopVars().get(i).var.name(), 
            initVals.get(i).name());
    }
    Var initCond = exprWalker.eval(context, forLoop.getCondition(), condType, true, initRenames);
    
    // Start the loop construct with some initial values
    Var condArg = 
        loopBodyContext.declareVariable(condType, Var.LOOP_COND_PREFIX + 
            loopNum, VarStorage.TEMP, DefType.INARG, null);



    /* Pack the variables into vectors with the first element the condition */
    ArrayList<Var> loopVars = new ArrayList<Var>(
                                              forLoop.loopVarCount() + 1);
    loopVars.add(condArg);
    loopVars.addAll(forLoop.getUnpackedLoopVars());
    
    List<Boolean> blockingVector = new ArrayList<Boolean>(loopVars.size());
    blockingVector.add(true); // block on condition
    blockingVector.addAll(forLoop.blockingLoopVarVector());
    
    initVals.add(0, initCond);
    
    backend.startLoop(loopName, loopVars, initVals, 
                      usedVariables, keepOpenVars,
                      blockingVector);
    
    // get value of condVar
    Var condVal = varCreator.fetchValueOf(loopBodyContext, condArg);
    
    // branch depending on if loop should start
    backend.startIfStatement(Arg.createVar(condVal), true);
    // If this iteration is good, run all of the stuff in the block
    block(loopBodyContext, forLoop.getBody());
    
    forLoop.validateUpdates(loopBodyContext);
    //evaluate update expressions
    List<Var> newLoopVars = evalLoopVarExprs(loopBodyContext, forLoop, 
                                                forLoop.getUpdateRules());
    
    HashMap<String, String> nextRenames = new HashMap<String, String>();
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      nextRenames.put(forLoop.getLoopVars().get(i).var.name(), 
            newLoopVars.get(i).name());
    }
    Var nextCond = exprWalker.eval(loopBodyContext, 
              forLoop.getCondition(), condType, true, nextRenames);
    newLoopVars.add(0, nextCond);
    backend.loopContinue(newLoopVars, usedVariables, keepOpenVars,
                                                            blockingVector);
    backend.startElseBlock();
    // Terminate loop, clean up open arrays and copy out final vals 
    // of loop vars
    for (LoopVar lv: forLoop.getLoopVars()) {
      if (lv.declaredOutsideLoop) {
        exprWalker.copyByValue(loopBodyContext, 
            lv.var, parentLoopVarAliases.get(lv.var.name()), 
            lv.var.type());
      }
    }
    
    backend.loopBreak(usedVariables, keepOpenVars);
    backend.endIfStatement();
    // finish loop construct
    backend.endLoop();
  }


  
  private void iterate(Context context, SwiftAST tree) throws UserException {
    IterateDescriptor loop = IterateDescriptor.fromAST(context, tree);
    
    
    
    //TODO: this is a little funny since the condition expr might be of type int,
    //    but this will work for time being
    Var falseV = varCreator.createTmp(context, Types.F_BOOL);
    backend.assignBool(falseV, Arg.createBoolLit(false));
    
    Var zero = varCreator.createTmp(context, Types.F_INT);
    backend.assignInt(zero, Arg.createIntLit(0));
    
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("iterate");
    String loopName = fc.getFunctionName() + "-iterate-" + loopNum;
    
    List<Var> usedVariables = new ArrayList<Var>();
    List<Var> keepOpenVars = new ArrayList<Var>();
    summariseBranchVariableUsage(context, 
            Arrays.asList(loop.getBody().getVariableUsage()), 
            usedVariables, keepOpenVars);

    Context bodyContext = loop.createBodyContext(context);
    
    
    // Start the loop construct with some initial values
    Var condArg = 
      bodyContext.declareVariable(Types.F_BOOL, Var.LOOP_COND_PREFIX + 
            loopNum, VarStorage.TEMP, DefType.INARG, null);
    
    List<Boolean> blockingVars = Arrays.asList(true, false);
    backend.startLoop(loopName, 
        Arrays.asList(condArg, loop.getLoopVar()), Arrays.asList(falseV, zero), 
        usedVariables, keepOpenVars, blockingVars);
    
    // get value of condVar
    Var condVal = varCreator.fetchValueOf(bodyContext, condArg); 
    
    backend.startIfStatement(Arg.createVar(condVal), true);
    backend.loopBreak(usedVariables, keepOpenVars);
    backend.startElseBlock();
    block(bodyContext, loop.getBody());
    
    // Check the condition type now that all loop body vars have been declared
    Type condType = TypeChecker.findSingleExprType(bodyContext,
        loop.getCond());
    if (!condType.assignableTo(Types.F_BOOL)) {
      throw new TypeMismatchException(bodyContext, 
          "iterate condition had invalid type: " + condType.typeName());
    }
    
    Var nextCond = exprWalker.eval(bodyContext, loop.getCond(),
                                          Types.F_BOOL, false, null);
    
    Var nextCounter = varCreator.createTmp(bodyContext,
                                      Types.F_INT);
    Var one = varCreator.createTmp(bodyContext, Types.F_INT);

    backend.assignInt(one, Arg.createIntLit(1));
    backend.asyncOp(BuiltinOpcode.PLUS_INT, nextCounter, 
        Arrays.asList(Arg.createVar(loop.getLoopVar()), Arg.createVar(one)),
        null);
    
    backend.loopContinue(Arrays.asList(nextCond, nextCounter), 
        usedVariables, keepOpenVars, blockingVars);

    backend.endIfStatement();
    backend.endLoop();
  }


  private ArrayList<Var> evalLoopVarExprs(Context context,
      ForLoopDescriptor forLoop, Map<String, SwiftAST> loopVarExprs)
      throws UserException {
    ArrayList<Var> results = new ArrayList<Var>(
                                                forLoop.loopVarCount() + 1);
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      Var v = forLoop.getLoopVars().get(i).var;
      Type argType = v.type();
      SwiftAST expr = loopVarExprs.get(v.name());
      Type exprType = TypeChecker.findSingleExprType(context, expr);
      exprType = TypeChecker.checkAssignment(context, exprType,
                                             argType,v.name());
      results.add(exprWalker.eval(context, expr, exprType, false, null));
    }
    return results;
  }



  
  private void declareVariables(Context context, SwiftAST tree,
      VariableUsageInfo blockVu) throws UserException {
    LogHelper.trace(context, "declareVariable...");
    assert(tree.getType() == ExMParser.DECLARATION);
    int count = tree.getChildCount();
    if (count < 2)
      throw new STCRuntimeError("declare_multi: child count < 2");
    VariableDeclaration vd =  VariableDeclaration.fromAST(context, 
                                                    tree);
    
    for (int i = 0; i < vd.count(); i++) {
      VariableDescriptor vDesc = vd.getVar(i);
      Var var = declareVariable(context, blockVu, vDesc);
      SwiftAST declTree = vd.getDeclTree(i);
      SwiftAST assignedExpr = vd.getVarExpr(i);
      if (Types.isScalarUpdateable(var.type())) {
        initUpdateableVar(context, var, assignedExpr);
      } else {
         if (assignedExpr != null) {
           assignMultiExpression(context, Arrays.asList(
               new LValue(declTree, var)), Arrays.asList(assignedExpr));
         }
      }
    }
  }


  private void initUpdateableVar(Context context, Var var,
                                                SwiftAST initExpr) {
    if (initExpr != null) {
      // TODO
      // Handle as special case because currently we need an initial
      // value for the updateable variable right away
      if (var.type().equals(Types.UP_FLOAT)) {
        Double initVal = Literals.extractFloatLit(context, initExpr);
        if (initVal == null) {
          String intLit = Literals.extractIntLit(context, initExpr);
          if (intLit != null) {
            initVal = Literals.interpretIntAsFloat(context, intLit);
          }
        } 
        if (initVal == null) {
          throw new STCRuntimeError("Don't yet support non-constant" +
                  " initialisers for updateable variables");
        }
        backend.initUpdateable(var, Arg.createFloatLit(initVal));
      } else {
        throw new STCRuntimeError("Non-float updateables not yet" +
                " implemented for type " + var.type());
      }
    } else {
      throw new STCRuntimeError("updateable variable " +
          var.name() + " must be given an initial value upon creation");
    }
  }

  private Var declareVariable(Context context, VariableUsageInfo blockVu,
      VariableDescriptor vDesc) throws UserException, UndefinedTypeException {
    VInfo vi = blockVu.lookupVariableInfo(vDesc.getName());
    Type definedType = vDesc.getType();
    // Sometimes we have to use a reference to an array instead of an array
    Type internalType;

    Var mappedVar = null;
    // First evaluate the mapping expr
    if (vDesc.getMappingExpr() != null) {
      if (Types.isMappable(vDesc.getType())) {
        Type mapType = TypeChecker.findSingleExprType(context, 
                                          vDesc.getMappingExpr());
        if (!Types.isString(mapType)) {
          throw new TypeMismatchException(context, "Tried to map using " +
                  "non-string expression with type " + mapType.typeName());
        }
        mappedVar = exprWalker.eval(context, vDesc.getMappingExpr(), Types.F_STRING, false, null);
      } else {
        throw new TypeMismatchException(context, "Variable " + vDesc.getName()
                + " of type " + vDesc.getType().typeName() + " cannot be " +
                    " mapped");
      }
    }
    
    boolean USE_ARRAY_REF_SWITCHEROO;
    try {
      USE_ARRAY_REF_SWITCHEROO = Settings.getBoolean(Settings.ARRAY_REF_SWITCHEROO);
    } catch(InvalidOptionException e) {
      throw new STCRuntimeError("Option should have been set: " +
                                                            e.getMessage());
    }
    
          
    /* temporary kludge, because implementing
     * an array variable as an array ref doesn't work properly yet
     * TODO: this is actually really bad because switching the variable types
     * means that type information is inconsistent between static analysis
     *      and the codegen stage
     */
    if (USE_ARRAY_REF_SWITCHEROO && Types.isArray(definedType)
        && vi.isAssigned() != Ternary.FALSE && vi.getArrayAssignDepth() == 0) {
      internalType = new RefType(definedType);
    } else {
      internalType = definedType;
    }
    Var var = varCreator.createVariable(context, internalType, 
        vDesc.getName(), VarStorage.STACK, DefType.LOCAL_USER, mappedVar);

    // Might need to close if array or struct containing array
    flagDeclaredVarForClosing(context, var, blockVu);
    return var;
  }

  private void assignExpression(Context context, SwiftAST tree)
      throws UserException {
    LogHelper.debug(context, "assignment: ");
    LogHelper.logChildren(context.getLevel(), tree);

    SwiftAST id_list = tree.child(0);
    ArrayList<SwiftAST> rValExprs = new ArrayList<SwiftAST>(
                            tree.getChildCount() - 1);
    for (int i = 1; i < tree.getChildCount(); i++) {
      SwiftAST rValExpr = tree.child(i);
      rValExprs.add(rValExpr);
    }

    List<LValue> identifiers = LValue.extractLVals(context, id_list);
    assignMultiExpression(context, identifiers, rValExprs);
  }

  private void assignMultiExpression(Context context, List<LValue> lVals,
      List<SwiftAST> rValExprs) throws UserException, TypeMismatchException,
      UndefinedTypeException, UndefinedVariableException {
    assert(!rValExprs.isEmpty());

    
  
    if (rValExprs.size() == 1) {
      // Single expression on RHS.  There still might be multiple lvalues
      // if the expression was a function with multiple return values
      assignSingleExpr(context, lVals, rValExprs.get(0));
    } else {
      // Match up RVals and LVals
      if (rValExprs.size() != lVals.size()) {
        throw new InvalidSyntaxException(context, "number of expressions on " +
                " right hand side of assignment (" + rValExprs.size() + ") does " +
                    "not match the number of targets on the left hand size (" +
                lVals.size());
      }
      for (int i = 0; i < lVals.size(); i++) {
        assignSingleExpr(context, lVals.subList(i, i+1), rValExprs.get(i));
      }
    }
  }

  private void assignSingleExpr(Context context, List<LValue> lVals,
      SwiftAST rValExpr) throws UserException, TypeMismatchException,
      UndefinedVariableException, UndefinedTypeException {
    List<Type> lValTypes = new ArrayList<Type>(lVals.size());
    for (LValue lval: lVals) {
      lValTypes.add(lval.getType(context));
    }
    
    ExprType rValTs = TypeChecker.findExprType(context, rValExpr);
    if (rValTs.elems() != lVals.size()) {
      throw new TypeMismatchException(context, "Needed " + rValTs.elems()
          + " " + "assignment targets on LHS of assignment, but "
          + lVals.size() + " were present");
    }

    List<Var> result = new ArrayList<Var>(lVals.size());
    Deque<Runnable> afterActions = new LinkedList<Runnable>();
    boolean skipEval = false;
    // TODO: need to handle ambiguous input types
    for (int i = 0; i < lVals.size(); i++) {
      LValue lval = lVals.get(i);
      Type lValType = lValTypes.get(i);
      Type rValType = rValTs.get(i);
      String targetName = lval.toString();
      Type rValConcrete = TypeChecker.checkAssignment(context, rValType,
                                                            lValType, targetName);
      backend.addComment("Swift l." + context.getLine() +
          ": assigning expression to " + targetName);

      // the variable we will evaluate expression into
      context.syncFilePos(lval.tree, lineMapping);
      Var var = evalLValue(context, rValExpr, rValConcrete, lval, 
                                                      afterActions);
      
      if (lVals.size() == 1 && rValExpr.getType() == ExMParser.VARIABLE) {
        /* Special case: 
         * A[i] = x;  
         * we just want x to be inserted into A without any temp variables being
         * created.  evalLvalue will do the insertion, and return the variable
         * represented by the LValue, but this happens to be x (because A[i] is 
         * now just an alias for x.  So we're done and can return!
         */
        String rValVar = rValExpr.child(0).getText();
        if (var.name().equals(rValVar)) {
          // LHS is just an alias for RHS.  This is ok if this is e.g.
          // A[i] = x; but not if it is x = x;
          if (lval.indices.size() == 0) {
            throw new UserException(context, "Assigning var " + rValVar
                + " to itself");
          }
          skipEval = true; 
          break;
        }
      }

      result.add(var);
    }

    if (! skipEval ) {
      exprWalker.evalToVars(context, rValExpr, result, null);
    }
    
    for (Runnable action: afterActions) {
      action.run();
    }
  }

  /**
   * Process an LValue for an assignment, resulting in a variable that
   * we can assign the RValue to
   * @param context
   * @param rValExpr
   * @param rValType type of the above expr
   * @param lval
   * @param afterActions sometimes the lvalue evaluation code wants to insert
   *                    code after the rvalue has been evaluated.  Any
   *                    runnables added to afterActions will be run after
   *                    the Rvalue is evaluated
   * @return the variable referred to by the LValue
   */
  private Var evalLValue(Context context, SwiftAST rValExpr,
      Type rValType, LValue lval, Deque<Runnable> afterActions)
      throws UndefinedVariableException, UserException, UndefinedTypeException,
      TypeMismatchException {
    LValue arrayBaseLval = null; // Keep track of the root of the array
    LogHelper.trace(context, ("Evaluating lval " + lval.toString() + " with type " +
    lval.getType(context)));
    // Iteratively reduce the target until we have a scalar we can
    // assign to
    while (lval.indices.size() > 0) {
      if (lval.indices.get(0).getType() == ExMParser.STRUCT_PATH) {
        lval = reduceStructLVal(context, lval);
      } else {
        assert(lval.indices.get(0).getType() == ExMParser.ARRAY_PATH);
        if (arrayBaseLval == null) { 
            arrayBaseLval = lval;
        }
        assert(Types.isArray(arrayBaseLval.var.type()));
        lval = reduceArrayLVal(context, arrayBaseLval, lval, rValExpr, rValType,
                                afterActions);
        LogHelper.trace(context, "Reduced to lval " + lval.toString() + 
                " with type " + lval.getType(context));
      }
    }

    String varName = lval.varName;
    Var lValVar = context.getDeclaredVariable(varName);
    if (lValVar == null) {
      throw new UndefinedVariableException(context, "variable " + varName
          + " is not defined");
    }

    // Now if there is some mismatch between reference/value, rectify it
    return fixupRefValMismatch(context, rValType, lValVar);
  }


  /**
   * If there is a mismatch between lvalue and rvalue type in assignment
   * where one is a reference and one isn't, fix that up
   * @param context
   * @param rValType
   * @param lValVar
   * @return a replacement lvalue var if needed, or the original if types match
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private Var fixupRefValMismatch(Context context, Type rValType,
      Var lValVar) throws UserException, UndefinedTypeException {
    if (rValType.assignableTo(lValVar.type())) {
      return lValVar;
    } else if (Types.isRef(lValVar.type())
            && rValType.assignableTo(lValVar.type())) {
      Var rValVar = varCreator.createTmp(context, rValType);
      backend.assignReference(lValVar, rValVar);
      return rValVar;
    } else if (Types.isRef(rValType) 
            && rValType.memberType().assignableTo(lValVar.type())) {
      Var rValVar = varCreator.createTmp(context, rValType);
      exprWalker.dereference(context, lValVar, rValVar);
      return rValVar;
    } else {
      throw new STCRuntimeError("Don't support assigning an "
          + "expression with type " + rValType.toString() + " to variable "
          + lValVar.toString() + " yet");
    }
  }


  /**
   * Processes part of an assignTarget path: the prefix
   * of struct lookups.  E.g. if we're trying to assign to
   * x.field.field.field[0], then this function handles the
   * 3x field lookups, making sure that a handle to
   * x.field.field.field is put into a temp variable, say called t0
   * This will then return a new assignment target for t0[0] for further
   * processing
   * @param context
   * @param lval
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws TypeMismatchException
   */
  private LValue reduceStructLVal(Context context,
      LValue lval) throws UserException, UndefinedTypeException,
      TypeMismatchException {
    // The variable at root of the current struct path
    Var rootVar = context.getDeclaredVariable(lval.varName);

    ArrayList<String> fieldPath = new ArrayList<String>();

    int structPathIndex = 0;
    while (structPathIndex < lval.indices.size() &&
        lval.indices.get(structPathIndex).getType() == ExMParser.STRUCT_PATH) {
      SwiftAST pathTree = lval.indices.get(structPathIndex);
      String fieldName = pathTree.child(0).getText();
      fieldPath.add(fieldName);
      structPathIndex++;
    }
    final int structPathLen = structPathIndex;

    Var curr = rootVar;
    for (int i = 0; i < structPathLen; i++) {
      List<String> currPath = fieldPath.subList(0, i+1);
      Var next = varCreator.createStructFieldTmp(context,
          rootVar, lval.getType(context, i+1), currPath, VarStorage.ALIAS);

      backend.structLookup(curr, fieldPath.get(i), next);
      LogHelper.trace(context, "Lookup " + curr.name() + "." +
                               fieldPath.get(i));
      curr = next;
    }
    LValue newTarget = new LValue(lval.tree, curr,
        lval.indices.subList(structPathLen, lval.indices.size()));
    LogHelper.trace(context, "Transform target " + lval.toString() +
        "<" + lval.getType(context).toString() + "> to " +
        newTarget.toString() + "<" +
        newTarget.getType(context).toString() + "> by looking up " +
        structPathLen + " fields");
    return newTarget;
  }

  /** Handle a prefix of array lookups for the assign target
   * @param afterActions 
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws TypeMismatchException */
  private LValue reduceArrayLVal(Context context, LValue origLval,
    LValue lval, SwiftAST rValExpr, Type rValType, Deque<Runnable> afterActions)
        throws TypeMismatchException, UndefinedTypeException, UserException {

    SwiftAST indexExpr = lval.indices.get(0);
    assert (indexExpr.getType() == ExMParser.ARRAY_PATH);
    assert (indexExpr.getChildCount() == 1);
    // Typecheck index expression
    Type indexType = TypeChecker.findSingleExprType(context, 
                                             indexExpr.child(0));
    if (!indexType.assignableTo(Types.F_INT)) {
      throw new TypeMismatchException(context, 
          "Indexing array using non-integer expression in lval.  Type " +
          "of expression was " + indexType.typeName());
    }
    
    if (lval.indices.size() == 1) {
      Var lookedup = assignTo1DArray(context, origLval, lval, rValExpr, 
                                                      rValType, afterActions);
      return new LValue(lval.tree, lookedup, new ArrayList<SwiftAST>());
    } else {
      //TODO: multi-dimensional array handling goes here: need to
      //    dynamically create subarray
      Var lvalArr = context.getDeclaredVariable(lval.varName);
      Type memberType = lval.getType(context, 1);
      Var mVar; // Variable for member we're looking up
      if (Types.isArray(memberType)) {

        String literal = Literals.extractIntLit(context, indexExpr.child(0));
        if (literal != null) {
          long arrIx = Long.parseLong(literal);
          // Add this variable to array
          if (Types.isArray(lvalArr.type())) {
            mVar = varCreator.createTmpAlias(context, memberType);
            backend.arrayCreateNestedImm(mVar, lvalArr, 
                        Arg.createIntLit(arrIx));
          } else {
            assert(Types.isArrayRef(lvalArr.type()));
            mVar = varCreator.createTmpAlias(context, 
                                  new RefType(memberType));
            backend.arrayRefCreateNestedImm(mVar, lvalArr, 
                Arg.createIntLit(arrIx));
          }

        } else {
          // Handle the general case where the index must be computed
          mVar = varCreator.createTmpAlias(context, 
                                        new RefType(memberType));
          Var indexVar = exprWalker.eval(context, indexExpr.child(0), Types.F_INT, false, null);
          
          if (Types.isArray(lvalArr.type())) {
            backend.arrayCreateNestedFuture(mVar, lvalArr, indexVar);
          } else {
            assert(Types.isArrayRef(lvalArr.type()));
            backend.arrayRefCreateNestedFuture(mVar, lvalArr, indexVar);
          }
        }
      } else {
        /* 
         * Retrieve non-array member
         * must use reference because we might have to wait for the result to 
         * be inserted
         */
        mVar = varCreator.createTmp(context, new RefType(memberType));
      }

      return new LValue(lval.tree, mVar,
          lval.indices.subList(1, lval.indices.size()));
    }
}

  private Var assignTo1DArray(Context context, final LValue origLval,
      LValue lval, SwiftAST rvalExpr, Type rvalType,
      Deque<Runnable> afterActions)
      throws TypeMismatchException, UserException, UndefinedTypeException {
    assert (rvalExpr.getType() != ExMParser.ARRAY_PATH);
    assert(lval.indices.size() == 1);
    assert(Types.isArray(origLval.var.type()));
    final Var lvalVar;
    // Check that it is a valid array
    final Var arr = lval.var;

    Type arrType = arr.type();

    if (!Types.isArray(arrType) && !Types.isArrayRef(arrType)) {
      throw new TypeMismatchException(context, "Variable " + arr.name()
          + "is not an array, cannot index\n.");
    }
    boolean isRef = Types.isArrayRef(arrType);

    LogHelper.debug(context, 
            "Token type: " + LogHelper.tokName(rvalExpr.getType()));
    // Find or create variable to store expression result

    if (!Types.isRef(rvalType)) {
      if (rvalExpr.getType() == ExMParser.VARIABLE) {
        // Get a handle to the variable, so we can just insert the variable
        //  directly into the array
        // This is a bit of a hack.  We return the rval as the lval and rely
        //  on the rest of the compiler frontend to treat the self-assignment
        //  as a no-op
        lvalVar = context.getDeclaredVariable(rvalExpr.child(0).getText());
      } else {
        // In other cases we need an intermediate variable
        Type arrMemberType;
        if (Types.isArray(arrType)) {
          arrMemberType = arrType.memberType();
        } else {
          assert(Types.isArrayRef(arrType));
          arrMemberType = arrType.memberType().memberType();
        }
        lvalVar = varCreator.createTmp(context, arrMemberType);
      }
    } else {
      //Rval is a ref, so create a new value of the dereferenced type and
      // rely on the compiler frontend later inserting instruction to
      // copy
      lvalVar = varCreator.createTmp(context, rvalType);
    }

    // We know what variable the result will go into now
    // Now need to work out the index and generate code to insert the
    // variable into the array

    SwiftAST indexTree = lval.indices.get(0);
    assert (indexTree.getType() == ExMParser.ARRAY_PATH);
    assert (indexTree.getChildCount() == 1);
    SwiftAST indexExpr = indexTree.child(0);

    String literal = Literals.extractIntLit(context, indexExpr);
    /*
     * use afterActions to insert the variable into array only 
     * after the RHS has been evaluated.  This means the resulting
     * code is in a more logical order and is easier for the
     * optimiser to work with
     */
    if (literal != null) {
      final long arrIx = Long.parseLong(literal);
      // Add this variable to array
      if (isRef) {
        // This should only be run when assigning to nested array
        afterActions.addFirst(new Runnable() {
          @Override
          public void run() {
            backend.arrayRefInsertImm(lvalVar,
                      arr, Arg.createIntLit(arrIx), origLval.var);
          }});
      } else {
        afterActions.addFirst(new Runnable() {
          @Override
          public void run() {
            backend.arrayInsertImm(lvalVar, arr, 
                Arg.createIntLit(arrIx));
          }});
      }
    } else {
      // Handle the general case where the index must be computed
      final Var indexVar = exprWalker.eval(context, indexExpr, Types.F_INT, false, null);

      if (isRef) {
        afterActions.addFirst(new Runnable() {
          @Override
          public void run() {
            backend.arrayRefInsertFuture(lvalVar, arr, 
                                              indexVar, origLval.var);
          }});
      } else {
        afterActions.addFirst(new Runnable() {
          @Override
          public void run() {
            backend.arrayInsertFuture(lvalVar, arr, indexVar);
          }});
      }
    }
    return lvalVar;
  }


  /**
   * Statement that evaluates an expression with no assignment E.g., trace()
   */
  private void exprStatement(Context context, SwiftAST tree) throws UserException {
    assert (tree.getChildCount() == 1);
    SwiftAST expr = tree.child(0);

    ExprType exprType = TypeChecker.findExprType(context, expr);

    backend.addComment("Swift l." + context.getLine() + " evaluating "
        + " expression and throwing away " + exprType.elems() +
        " results");

    // Need to create throwaway temporaries for return values
    List<Var> oList = new ArrayList<Var>();
    for (Type t : exprType.getTypes()) {
      oList.add(varCreator.createTmp(context, t));
    }

    exprWalker.evalToVars(context, expr, oList, null);
  }

  private void updateStmt(Context context, SwiftAST tree) 
        throws UserException {
    Update up = Update.fromAST(context, tree);
    Type exprType = up.typecheck(context);
    Var evaled = exprWalker.eval(context, up.getExpr(), exprType, false, null);
    backend.update(up.getTarget(), up.getMode(), evaled);
  }


  private void defineBuiltinFunction(Context context, SwiftAST tree)
  throws UserException
  {
    final int REQUIRED_CHILDREN = 5;
    assert(tree.getChildCount() >= REQUIRED_CHILDREN);
    String function  = tree.child(0).getText();
    SwiftAST typeParamsT = tree.child(1);
    SwiftAST outputs = tree.child(2);
    SwiftAST inputs  = tree.child(3);
    SwiftAST tclPackage = tree.child(4);
    assert(inputs.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    assert(outputs.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    assert(tclPackage.getType() == ExMParser.TCL_PACKAGE);
    assert(tclPackage.getChildCount() == 2);
    
    Set<String> typeParams = extractTypeParams(typeParamsT);

    FunctionDecl fdecl = FunctionDecl.fromAST(context, function, inputs,
                                              outputs, typeParams);
    
    FunctionType ft = fdecl.getFunctionType();
    LogHelper.debug(context, "builtin: " + function + " " + ft);
    
    String pkg = Literals.extractLiteralString(context, tclPackage.child(0)); 
    String version = Literals.extractLiteralString(context, tclPackage.child(1));
    backend.requirePackage(pkg, version);
    
    int pos = REQUIRED_CHILDREN;
    TclFunRef impl = null;
    if (pos < tree.getChildCount() && 
              tree.child(pos).getType() == ExMParser.TCL_FUN_REF) {
      SwiftAST tclImplRef = tree.child(pos);
      String symbol  = Literals.extractLiteralString(context, 
                                                     tclImplRef.child(0));
      impl = new TclFunRef(pkg, symbol, version);
      pos++;
    }
    
    TclOpTemplate inlineTcl = null;
    if (pos < tree.getChildCount() && 
          tree.child(pos).getType() == ExMParser.INLINE_TCL) {
      /* See if a template is provided for inline TCL code for function */
      SwiftAST inlineTclTree = tree.child(pos);
      inlineTcl = handleInlineTcl(context, function, fdecl, ft, inlineTclTree);
      pos++;
    }
    
    // Read annotations at end of child list
    for (; pos < tree.getChildCount(); pos++) {
      handleFunctionAnnotation(context, function, tree.child(pos),
                                inlineTcl != null);
    }
    
    context.defineFunction(function, ft);
    if (impl != null) {
      context.setFunctionProperty(function, FnProp.BUILTIN);
      backend.defineBuiltinFunction(function, ft, impl);
    } else {
      if (inlineTcl == null) {
        throw new UserException(context, "Must provide TCL implementation or " +
        		"inline TCL for function " + function);
      }
      // generate composite functino wrapping inline tcl
      context.setFunctionProperty(function, FnProp.COMPOSITE);
      generateWrapperFunction(context, function, ft, fdecl, inlineTcl);
    }
  }


  /**
   * Generate a function that wraps some inline tcl
   * @param function
   * @param ft 
   * @param fdecl
   * @param inlineTcl 
   * @throws UserException
   */
  private void generateWrapperFunction(Context global,
           String function, FunctionType ft,
           FunctionDecl fdecl, TclOpTemplate inlineTclinlineTcl)
          throws UserException {
    List<Var> outVars = fdecl.getOutVars();
    List<Var> inVars = fdecl.getInVars();
    
    Context context = new LocalContext(global, function);
    
    backend.startFunction(function, outVars, inVars);
    List<Arg> inVals = new ArrayList<Arg>(inVars.size());
    List<Var> outVals = new ArrayList<Var>(outVars.size());
    
    List<Var> waitVars = new ArrayList<Var>(inVars.size());
    for (Var in: inVars) {
      if (!Types.isScalarFuture(in.type()) ||
              Types.isFile(in.type())) {
        throw new STCRuntimeError("Can't handle type of " + in.type()
               + " for function " + function);
      } 
      waitVars.add(in);
    }
    

    WaitMode waitMode = WaitMode.DATA_ONLY;
    TaskMode taskMode = TaskMode.LOCAL;
    // Check to see how task should be dispatched
    if (Builtins.getTaskMode(function) != null) {
      taskMode = Builtins.getTaskMode(function);
      if (taskMode == TaskMode.LEAF || taskMode == TaskMode.CONTROL) {
        waitMode = WaitMode.TASK_DISPATCH;
      }
    }
    backend.startWaitStatement(
              context.getFunctionContext().constructName("wrap:" + function),
              waitVars, inVars, Arrays.<Var>asList(), waitMode,
              false, taskMode);
    
    for (Var in: inVars) {
      Var inVal = varCreator.fetchValueOf(context, in);
      inVals.add(Arg.createVar(inVal));
    }
    for (Var out: outVars) {
      if (!Types.isScalarFuture(out.type()) ||
              Types.isFile(out.type())) {
        throw new STCRuntimeError("Can't handle type of " + out.type()
               + " for function " + function);
      } 
      Var outVal = varCreator.createValueOfVar(context, out);
      outVals.add(outVal);
    }
    
    backend.builtinLocalFunctionCall(function, inVals, outVals);
    
    // Assign output values and cleanup
    for (int i = 0; i < outVars.size(); i++) {
      Var outFuture = outVars.get(i);
      Var outVal = outVals.get(i);
      exprWalker.assign(outFuture, Arg.createVar(outVal));
      
      if (Types.isBlob(outFuture.type())) {
        backend.freeBlob(outVal);
      }
    }
    
    // Cleanup input values
    for (int i = 0; i < inVars.size(); i++) {
      Var inFuture = inVars.get(i);
      Arg inVal = inVals.get(i);
      if (Types.isBlob(inFuture.type())) {
        backend.decrBlobRef(inFuture);
      }
    }
    backend.endWaitStatement(inVars, Arrays.<Var>asList());
    backend.endFunction();
  }


  private TclOpTemplate handleInlineTcl(Context context, String function,
          FunctionDecl fdecl, FunctionType ft, SwiftAST inlineTclTree)
          throws InvalidSyntaxException, UserException {
    assert(inlineTclTree.getType() == ExMParser.INLINE_TCL);
    TclOpTemplate inlineTcl;
    assert(inlineTclTree.getChildCount() == 1);
    String tclTemplateString = 
          Literals.extractLiteralString(context, inlineTclTree.child(0));
    inlineTcl = InlineCode.templateFromString(context, tclTemplateString);
    
    List<String> inNames = fdecl.getInNames();
    inlineTcl.addInNames(inNames);
    if (ft.hasVarargs()) {
      inlineTcl.setVarArgIn(inNames.get(inNames.size() - 1));
    }
    inlineTcl.addOutNames(fdecl.getOutNames());
    inlineTcl.verifyNames(context);
    Builtins.addInlineTemplate(function, inlineTcl);
    return inlineTcl;
  }


  private Set<String> extractTypeParams(SwiftAST typeParamsT) {
    assert(typeParamsT.getType() == ExMParser.TYPE_PARAMETERS);
    Set<String> typeParams = new HashSet<String>();
    for (SwiftAST typeParam: typeParamsT.children()) {
      assert(typeParam.getType() == ExMParser.ID);
      typeParams.add(typeParam.getText());
    }
    return typeParams;
  }


  private void handleFunctionAnnotation(Context context, String function,
      SwiftAST annotTree, boolean hasLocalVersion) throws UserException {
    assert(annotTree.getType() == ExMParser.ANNOTATION);
    
    assert(annotTree.getChildCount() > 0);
    String key = annotTree.child(0).getText();
    if (annotTree.getChildCount() == 1) { 
      registerFunctionAnnotation(context, function, key);
    } else {
      assert(annotTree.getChildCount() == 2);
      String val = annotTree.child(1).getText();
      if (key.equals(Annotations.FN_BUILTIN_OP)) {
        addlocalEquiv(context, function, val);
      } else if (key.equals(Annotations.FN_DISPATCH)) {
        try {
          TaskMode mode = TaskMode.valueOf(val);
          Builtins.addTaskMode(function, mode);
        } catch (IllegalArgumentException e) {
          throw new UserException(context, "Unknown dispatch mode " + val + ". "
              + " Valid options are: " + TaskMode.values());
        }
      } else {
        throw new UserException(context, "Invalid annotation" +
          " for TCL function: " + key + ":" + val);
      }
    }
  }


  private void addlocalEquiv(Context context, String function, String val)
      throws UserException {
    BuiltinOpcode opcode;
    try {
      opcode = BuiltinOpcode.valueOf(val);
    } catch (IllegalArgumentException e) {
      throw new UserException(context, "Unknown builtin op " + val);
    }
    assert(opcode != null);
    Builtins.addOpEquiv(function, opcode);
  }

  /**
   * Check that an annotation for the named function is valid, and
   * add it to the known semantic info
   * @param function
   * @param annotation
   * @throws UserException 
   */
  private void registerFunctionAnnotation(Context context, String function,
                  String annotation) throws UserException {
    if (annotation.equals(Annotations.FN_ASSERTION)) {
      Builtins.addAssertVariable(function);
    } else if (annotation.equals(Annotations.FN_PURE)) {
      Builtins.addPure(function);
    } else if (annotation.equals(Annotations.FN_COMMUTATIVE)) {
      Builtins.addCommutative(function);
    } else if (annotation.equals(Annotations.FN_COPY)) {
      Builtins.addCopy(function);
    } else if (annotation.equals(Annotations.FN_MINMAX)) {
      Builtins.addMinMax(function);
    } else {
      throw new UserException(context, "Undefined annotation for functions: "
          + annotation + " for function " + function);
    }
    
  }


  private void defineFunction(Context context, SwiftAST tree)
  throws UserException {
    context.syncFilePos(tree, lineMapping);
    String function = tree.child(0).getText();
    LogHelper.debug(context, "define function: " + context.getLocation() +
                              function);
    assert(tree.getChildCount() >= 4);
    SwiftAST outputs = tree.child(1);
    SwiftAST inputs = tree.child(2);
    
    List<String> annotations = extractFunctionAnnotations(context, tree, 4);
    
    FunctionDecl fdecl = FunctionDecl.fromAST(context, function, inputs,
                          outputs, Collections.<String>emptySet());
    FunctionType ft = fdecl.getFunctionType();
    
    if (ft.hasVarargs()) {
      throw new TypeMismatchException(context, "composite function cannot" +
              " have variable-length argument lists");
    }
    for (Type it: ft.getInputs()) {
      if (Types.isPolymorphic(it)) {
        throw new TypeMismatchException(context, "composite functions " +
                "cannot have polymorphic input argument types, such as: " + it);
      }
    }
    
    // Handle main as special case of regular function declaration
    if (function.equals("main") && (ft.getInputs().size() > 0 || 
                                    ft.getOutputs().size() > 0))
      throw new TypeMismatchException(context,
          "main() is not allowed to have input or output arguments");

    boolean async = true;
    for (String annotation: annotations) {
      if (annotation.equals(Annotations.FN_SYNC)) {
        async = false;
      } else {
        registerFunctionAnnotation(context, function, annotation);
      }
    }
    
    context.defineFunction(function, ft);
    context.setFunctionProperty(function, FnProp.COMPOSITE);
    if (!async) {
      context.setFunctionProperty(function, FnProp.SYNC);
    }
  }


  private List<String> extractFunctionAnnotations(Context context,
          SwiftAST tree, int firstChild) throws InvalidAnnotationException {
    List<String> annotations = new ArrayList<String>();
    for (SwiftAST subtree: tree.children(firstChild)) {
      context.syncFilePos(subtree, lineMapping);
      assert(subtree.getType() == ExMParser.ANNOTATION);
      assert(subtree.getChildCount() == 1 || subtree.getChildCount() == 2);
      if (subtree.getChildCount() == 2) {
        throw new InvalidAnnotationException(context,
                      "no key-value annotations for function defs");
      }
      String annotation = subtree.child(0).getText();
      annotations.add(annotation);
    }
    return annotations;
  }

  /** Compile the function, assuming it is already defined in context */
  private void compileFunction(Context context, SwiftAST tree)
                                            throws UserException {
    String function = tree.child(0).getText();
    LogHelper.debug(context, "compile function: starting: " + function );
    // defineFunction should already have been called
    assert(context.isFunction(function));
    assert(context.hasFunctionProp(function, FnProp.COMPOSITE));
    SwiftAST outputs = tree.child(1);
    SwiftAST inputs = tree.child(2);
    SwiftAST block = tree.child(3);

    FunctionDecl fdecl = FunctionDecl.fromAST(context, function, 
                  inputs, outputs, Collections.<String>emptySet());
    
    List<Var> iList = fdecl.getInVars();
    List<Var> oList = fdecl.getOutVars();
    
    // Analyse variable usage inside function and annotate AST
    context.syncFilePos(tree, lineMapping);
    varAnalyzer.analyzeVariableUsage(context, function, iList, oList, block);

    LocalContext functionContext = new LocalContext(context, function);
    functionContext.setNested(false);
    functionContext.addDeclaredVariables(iList);
    functionContext.addDeclaredVariables(oList);
    
    backend.startFunction(function, oList, iList);
    
    VariableUsageInfo vu = block.getVariableUsage();
    // Make sure output arrays get closed
    for (Var o: oList) {
      flagDeclaredVarForClosing(functionContext, o, vu);
    }

    block(functionContext, block);
    backend.endFunction();

    LogHelper.debug(context, "compile function: done: " + function);
  }


  private void flagDeclaredVarForClosing(Context context, Var var,
      VariableUsageInfo vu) throws UndefinedTypeException, UserException {
    List<VarInfoPair> foundArrs = null;

    // First find all of the arrays and the vinfo for them
    VInfo vi = vu.lookupVariableInfo(var.name());
    if (Types.isArray(var.type())) {
      foundArrs = Collections.singletonList(new VarInfoPair(var, vi));
    } else if (Types.isStruct(var.type())) {
      // Might have to dig into struct and load members to see if we 
      // should close it
      foundArrs = new ArrayList<VarInfoPair>();
      findArraysInStruct(context, var, vi, foundArrs);
    }
    
    if (foundArrs != null) {        
      for (VarInfoPair p: foundArrs) {
        VInfo arrVI = p.vinfo;
        Var arr = p.var;
        if (arrVI.isAssigned() == Ternary.FALSE ||
                          arrVI.getArrayAssignDepth() > 0) {
          // should def be closed if not touched, or if assigned by index
          context.flagArrayForClosing(arr);
        }
      }
    }
  }
  
  private void defineAppFunction(Context context, SwiftAST tree)
      throws UserException {
    LogHelper.info(context.getLevel(), "defineAppFunction");
    assert(tree.getChildCount() >= 4);
    SwiftAST functionT = tree.child(0);
    assert(functionT.getType() == ExMParser.ID);
    String function = functionT.getText();
    SwiftAST outArgsT = tree.child(1);
    SwiftAST inArgsT = tree.child(2);
    
    FunctionDecl decl = FunctionDecl.fromAST(context, function, inArgsT,
                        outArgsT,   Collections.<String>emptySet());
    context.defineFunction(function, decl.getFunctionType());
    context.setFunctionProperty(function, FnProp.APP);
  }

  private void compileAppFunction(Context context, SwiftAST tree)
      throws UserException {
    LogHelper.info(context.getLevel(), "compileAppFunction");
    assert(tree.getChildCount() >= 4);
    SwiftAST functionT = tree.child(0);
    assert(functionT.getType() == ExMParser.ID);
    String function = functionT.getText();
    SwiftAST outArgsT = tree.child(1);
    SwiftAST inArgsT = tree.child(2);
    SwiftAST appBodyT = tree.child(3);
    
    FunctionDecl decl = FunctionDecl.fromAST(context, function, inArgsT,
                        outArgsT,   Collections.<String>emptySet());
    List<Var> outArgs = decl.getOutVars();
    List<Var> inArgs = decl.getInVars();
    
    context.syncFilePos(tree, lineMapping);
    List<String> annotations = extractFunctionAnnotations(context, tree, 4);
    context.syncFilePos(tree, lineMapping);
    boolean hasSideEffects = true, deterministic = false;
    for (String annotation: annotations) {
      if (annotation.equals(Annotations.FN_PURE)) {
        hasSideEffects = false;
        deterministic = true;
      } else if (annotation.equals(Annotations.FN_SIDE_EFFECT_FREE)) {
        hasSideEffects = false;
      } else if (annotation.equals(Annotations.FN_DETERMINISTIC)) {
        deterministic = true;
      } else {
        throw new InvalidAnnotationException(context, "Unsupported annotation "
                + "@" + annotation + " on app function: " + function);
      }
    }
    
    LocalContext appContext = new LocalContext(context, function);
    appContext.setNested(false);
    appContext.addDeclaredVariables(outArgs);
    appContext.addDeclaredVariables(inArgs);
    
    
    backend.startFunction(function, outArgs, inArgs);
    genAppFunctionBody(appContext, appBodyT, outArgs,
                       hasSideEffects, deterministic);
    backend.endFunction();
  }


  /**
   * @param context local context for app function
   * @param cmd AST for app function command
   * @param outputs output arguments for app
   * @param hasSideEffects
   * @param deterministic
   * @throws UserException
   */
  private void genAppFunctionBody(Context context, SwiftAST appBody,
          List<Var> outputs,
          boolean hasSideEffects,
          boolean deterministic) throws UserException {
    //TODO: don't yet handle situation where user is naughty and
    //    uses output variable in expression context
    assert(appBody.getType() == ExMParser.APP_BODY);
    assert(appBody.getChildCount() >= 1);
    
    // Extract command from AST
    SwiftAST cmd = appBody.child(0);
    assert(cmd.getType() == ExMParser.COMMAND);
    assert(cmd.getChildCount() >= 1);
    SwiftAST appNameT = cmd.child(0);
    assert(appNameT.getType() == ExMParser.STRING);
    String appName = Literals.extractLiteralString(context, appNameT);
    
    // Evaluate any argument expressions
    List<Var> args = evalAppCmdArgs(context, cmd);
    
    // Process any redirections
    Redirects<Var> redirFutures = processAppRedirects(context,
                                                    appBody.children(1));
    
    checkAppOutputs(context, appName, outputs, args, redirFutures);
    
    // Work out what variables must be closed before command line executes
    Pair<Map<String, Var>, List<Var>> wait =
            selectAppWaitVars(context, args, redirFutures);
    Map<String, Var> fileNames = wait.val1; 
    List<Var> waitVars = wait.val2;
    
    List<Var> passIn = new ArrayList<Var>();
    passIn.addAll(fileNames.values());
    passIn.addAll(args);
    
    // use wait to wait for data then dispatch task to worker
    String waitName = context.getFunctionContext().constructName("app-leaf");
    // do deep wait for array args
    backend.startWaitStatement(waitName, waitVars, passIn,
        Collections.<Var>emptyList(), WaitMode.TASK_DISPATCH,
        true, TaskMode.LEAF);
    // On worker, just execute the required command directly
    Pair<List<Arg>, Redirects<Arg>> retrieved = retrieveAppArgs(context,
                                          args, redirFutures, fileNames);
    List<Arg> localArgs = retrieved.val1; 
    Redirects<Arg> localRedirects = retrieved.val2;
    backend.runExternal(appName, localArgs, outputs, localRedirects,
                        hasSideEffects, deterministic);
    backend.endWaitStatement(passIn, Arrays.<Var>asList());
  }


  private Redirects<Var> processAppRedirects(Context context,
                             List<SwiftAST> redirects) throws UserException {    
    Redirects<Var> redir = new Redirects<Var>();

    // Process redirections
    for (SwiftAST redirT: redirects) {
      context.syncFilePos(redirT, lineMapping);
      assert(redirT.getChildCount() == 2);
      SwiftAST redirType = redirT.child(0);
      SwiftAST redirExpr = redirT.child(1);
      String redirTypeName = redirT.getText();
      
      // Now typecheck
      Type type = TypeChecker.findSingleExprType(context, redirExpr);
      // TODO: maybe could have plain string for filename, e.g. /dev/null?
      if (!Types.isFile(type)) {
        throw new TypeMismatchException(context, "Invalid type for" +
            " app redirection, must be file: " + type.typeName());
      }
      
      Var result = exprWalker.eval(context, redirExpr, type, false, null);
      boolean mustBeOutArg = false;
      boolean doubleDefine = false;
      switch (redirType.getType()) {
        case ExMParser.STDIN:
          doubleDefine = redir.stdin != null;
          redir.stdin = result;
          break;
        case ExMParser.STDOUT:
          doubleDefine = redir.stdout != null;
          redir.stdout = result;
          break;
        case ExMParser.STDERR:
          doubleDefine = redir.stderr != null;
          redir.stderr = result;
          break;
        default:
          throw new STCRuntimeError("Unexpected token type: " +
                              LogHelper.tokName(redirType.getType())); 
      }
      if (result.defType() != DefType.OUTARG && mustBeOutArg) { 
        throw new UserException(context, redirTypeName + " parameter "
          + " must be output file");
      }

      if (doubleDefine) {
        throw new UserException(context, "Specified redirection " +
                redirTypeName + " more than once");
      }
    }

    return redir;
  }

  /**
   * Check that app output args are not omitted from command line
   * Omit warning
   * @param context
   * @param outputs
   * @param args
   * @param redir 
   * @throws UserException 
   */
  private void checkAppOutputs(Context context, String function,
      List<Var> outputs, List<Var> args, Redirects<Var> redirFutures)
                                                      throws UserException {
    boolean deferredError = false;
    HashMap<String, Var> outMap = new HashMap<String, Var>();
    for (Var output: outputs) {
      // Check output types
      if (!Types.isFile(output.type()) && !Types.isVoid(output.type())) {
        LogHelper.error(context, "Output argument " + output.name() + " has "
            + " invalid type for app output: " + output.type().typeName());
        deferredError = true;
      }
      outMap.put(output.name(), output);
    }
    if (redirFutures.stdout != null) {
      // Already typechecked
      Var output = redirFutures.stdout;
      outMap.put(output.name(), output);
    }
    
    for (Var arg: args) {
      if (arg.defType() == DefType.OUTARG) {
        outMap.remove(arg.name());
      }
    }
    for (Var redir: redirFutures.redirections(false, true)) {
      if (redir.defType() == DefType.OUTARG) {
        outMap.remove(redir.name());
      }
    }
    
    for (Var unreferenced: outMap.values()) {
      if (!Types.isVoid(unreferenced.type())) {
        LogHelper.warn(context, "Output argument " + unreferenced.name() 
          + " is not referenced in app command line");
      }
    }
    if (deferredError) {
      throw new UserException(context, "Compilation failed due to type "
          + "error in definition of function " + function);
    }
  }

  /**
   * Work out what the local args to the app function should be
   * @param context
   * @param args
   * @param fileNames
   * @return pair of the command line arguments, and local redirects
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws DoubleDefineException
   */
  private Pair<List<Arg>, Redirects<Arg>> retrieveAppArgs(Context context,
          List<Var> args, Redirects<Var> redirFutures,
          Map<String, Var> fileNames)
          throws UserException, UndefinedTypeException, DoubleDefineException {
    List<Arg> localInputs = new ArrayList<Arg>();
    for (Var in: args) {
      localInputs.add(Arg.createVar(retrieveAppArg(context, fileNames, in)));
    }
    Redirects<Arg> redirValues = new Redirects<Arg>();
    if (redirFutures.stdin != null) {
      redirValues.stdin = Arg.createVar(retrieveAppArg(context, fileNames,
                                                 redirFutures.stdin));
    }
    if (redirFutures.stdout != null) {
      redirValues.stdout = Arg.createVar(retrieveAppArg(context, fileNames,
                                                 redirFutures.stdout));
    }
    if (redirFutures.stderr != null) {
      redirValues.stderr = Arg.createVar(retrieveAppArg(context, fileNames,
                                                 redirFutures.stderr));
    }
    
    return Pair.create(localInputs, redirValues);
  }


  private Var
      retrieveAppArg(Context context, Map<String, Var> fileNames, Var in)
          throws UserException, UndefinedTypeException, DoubleDefineException {
    Var localInput;
    if (Types.isFile(in.type())) {
      Var filenameFuture = fileNames.get(in.name());
      assert(filenameFuture != null);
      localInput = varCreator.fetchValueOf(context, filenameFuture);
    } else if (Types.isArray(in.type())) {
      // Pass array reference directly
      localInput = in;
    } else {
      localInput = varCreator.fetchValueOf(context, in);
    }
    return localInput;
  }

  /**
   * Evaluates argument expressions for app command line
   * @param context
   * @param cmdArgs
   * @return
   * @throws TypeMismatchException
   * @throws UserException
   */
  private List<Var> 
      evalAppCmdArgs(Context context, SwiftAST cmdArgs) 
          throws TypeMismatchException, UserException {
    List<Var> args = new ArrayList<Var>();
    // Skip first arg: that is id
    for (SwiftAST cmdArg: cmdArgs.children(1)) {
      if (cmdArg.getType() == ExMParser.APP_FILENAME) {
        assert(cmdArg.getChildCount() == 1);
        String fileVarName = cmdArg.child(0).getText();
        Var file = context.getDeclaredVariable(fileVarName);
        if (!Types.isFile(file.type())) {
          throw new TypeMismatchException(context, "Variable " + file.name()
                  + " is not a file, cannot use @ prefix for app");
        }
        args.add(file);
      } else {
        Type exprType = TypeChecker.findSingleExprType(context, cmdArg);
        Type baseType; // Type after expanding arrays
        if (Types.isArray(exprType)) {
          ArrayInfo info = new ArrayInfo(exprType);
          baseType = info.baseType;
        } else if (Types.isRef(exprType)) {
          // TODO
          throw new STCRuntimeError("TODO: support reference types on " +
          		"app cmd line");
        } else {
          baseType = exprType;
        }
        if (Types.isString(baseType) || Types.isInt(baseType) ||
            Types.isFloat(baseType) || Types.isBool(baseType) ||
            Types.isFile(baseType)) {
            args.add(exprWalker.eval(context, cmdArg, exprType, false, null));
        } else {
          throw new TypeMismatchException(context, "Cannot convert type " +
                        baseType.typeName() + " to app command line arg");
        }
      }
    }
    return args;
  }

  /**
   * Choose which inputs/outputs to an app invocation should be blocked
   * upon.  This is somewhat complex since we sometimes need to block
   * on filenames/file statuses/etc  
   * @param context
   * @param redirFutures 
   * @param inputs
   * @param outputs
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private Pair<Map<String, Var>, List<Var>> selectAppWaitVars(
          Context context, List<Var> args, Redirects<Var> redirFutures)
                                                throws UserException,
          UndefinedTypeException {
    List<Var> allArgs = new ArrayList<Var>();
    allArgs.addAll(args);
    allArgs.addAll(redirFutures.redirections(true, true));
    
    // map from file var to filename
    Map<String, Var> fileNames = new HashMap<String, Var>(); 
    List<Var> waitVars = new ArrayList<Var>();
    for (Var arg: allArgs) {
      if (Types.isFile(arg.type())) {
        if (fileNames.containsKey(arg.name())) {
          continue;
        }
        // Need to wait for filename for files
        Var filenameFuture = varCreator.createFilenameAlias(context, arg);

        if (arg.defType() != DefType.OUTARG) {
          // If output is unmapped, need to assign file name
          backend.getFileName(filenameFuture, arg, true);
        } else {
          backend.getFileName(filenameFuture, arg, false);
        }
        waitVars.add(filenameFuture);
        fileNames.put(arg.name(), filenameFuture);
        if (arg.defType() != DefType.OUTARG) {
          // Don't wait for file to be closed for output arg
          waitVars.add(arg);
        }
      } else {
        waitVars.add(arg);
      }
    }
    
    return Pair.create(fileNames, waitVars);
  }


  private void defineNewType(Context context, SwiftAST defnTree,
                             boolean aliasOnly)
                                    throws DoubleDefineException {
    assert (defnTree.getType() == ExMParser.DEFINE_NEW_TYPE ||
            defnTree.getType() == ExMParser.TYPEDEF );
    int children = defnTree.getChildCount();
    assert(children >= 2);
    String typeName = defnTree.child(0).getText();
    String baseTypeName = defnTree.child(1).getText();
    int arrayMarkers = 0;
    for (SwiftAST arrayT: defnTree.children(2)) {
      assert(arrayT.getType() == ExMParser.ARRAY);
      arrayMarkers++;
    }
    
    Type baseType = context.lookupType(baseTypeName);
    for (int i = 0; i < arrayMarkers; i++) {
      baseType = new ArrayType(baseType);
    }
    
    Type newType;
    if (aliasOnly) {
      newType = baseType;
    } else {
      newType = new SubType(baseType, typeName);
    }
    
    context.defineType(typeName, newType);
  }


  private void defineNewStructType(Context context, SwiftAST defnTree)
      throws DoubleDefineException, UndefinedTypeException {
    assert (defnTree.getType() == ExMParser.DEFINE_NEW_STRUCT_TYPE);
    int children = defnTree.getChildCount();
    if (children < 1) {
      throw new STCRuntimeError("expected DEFINE_NEW_TYPE to have at "
          + "least one child");
    }
    String typeName = defnTree.child(0).getText();

    // Build the type from the fields
    ArrayList<StructField> fields = new ArrayList<StructField>(children - 1);

    HashSet<String> usedFieldNames = new HashSet<String>(children - 1);
    for (int i = 1; i < children; i++) {
      SwiftAST fieldTree = defnTree.child(i);
      assert (fieldTree.getType() == ExMParser.STRUCT_FIELD_DEF);
      assert(fieldTree.getChildCount() >= 2);
      assert(fieldTree.child(0).getType() == ExMParser.ID);
      assert(fieldTree.child(1).getType() == ExMParser.ID);
      String baseTypeName = fieldTree.child(0).getText();
      Type fieldType = context.lookupType(baseTypeName);
      if (fieldType == null) {
        throw new UndefinedTypeException(context, baseTypeName);
      }
      String name = fieldTree.child(1).getText();

      // Account for any [] 
      for (int j = 2; j < fieldTree.getChildCount(); j++) {
        assert(fieldTree.child(j).getType() == ExMParser.ARRAY);
        fieldType = new Types.ArrayType(fieldType);
      }
      if (usedFieldNames.contains(name)) {
        throw new DoubleDefineException(context, "Field " + name
            + " is defined twice in type" + typeName);
      }
      fields.add(new StructField(fieldType, name));
      usedFieldNames.add(name);
    }

    StructType newType = new StructType(typeName, fields);
    context.defineType(typeName, newType);
    LogHelper.debug(context, "Defined new type called " + typeName + ": "
        + newType.toString());
  }

  private String endOfFile(Context context, SwiftAST tree) {
    return "# EOF";
  }

  private void globalConst(Context context, SwiftAST tree) 
        throws UserException {
    assert(tree.getType() == ExMParser.GLOBAL_CONST);
    assert(tree.getChildCount() == 1);
    
    SwiftAST varTree = tree.child(0);
    assert(varTree.getType() == ExMParser.DECLARATION);
    
    VariableDeclaration vd = VariableDeclaration.fromAST(context,
                    varTree);
    assert(vd.count() == 1);
    VariableDescriptor vDesc = vd.getVar(0);
    if (vDesc.getMappingExpr() != null) {
      throw new UserException(context, "Can't have mapped global constant");
    }
    Var v = context.declareVariable(vDesc.getType(), vDesc.getName(),
                   VarStorage.GLOBAL_CONST, DefType.GLOBAL_CONST, null);
    
    
    SwiftAST val = vd.getVarExpr(0);
    assert(val != null);
    
    Type valType = TypeChecker.findSingleExprType(context, val);
    if (!valType.assignableTo(v.type())) {
      throw new TypeMismatchException(context, "trying to assign expression "
          + " of type " + valType.typeName() + " to global constant " 
          + v.name() + " which has type " + v.type());
    }
    
    String msg = "Don't support non-literal "
        + "expressions for global constants";
    switch (v.type().primType()) {
    case BOOL:
      String bval = Literals.extractBoolLit(context, val);
      if (bval == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.name(), Arg.createBoolLit(
                                  Boolean.parseBoolean(bval)));
      break;
    case INT:
      String ival = Literals.extractIntLit(context, val);
      if (ival == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.name(), Arg.createIntLit(
                                      Long.parseLong(ival)));
      break;
    case FLOAT:
      Double fval = Literals.extractFloatLit(context, val);
      if (fval == null) {
        String sfval = Literals.extractIntLit(context, val); 
        if (sfval == null) {
          throw new UserException(context, msg);
        } else {
          fval = Literals.interpretIntAsFloat(context, sfval);
        }
      }
      assert(fval != null);
      backend.addGlobal(v.name(), Arg.createFloatLit(fval));
      break;
    case STRING:
      String sval = Literals.extractStringLit(context, val);
      if (sval == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.name(), Arg.createStringLit(sval));
      break;
    default:
      throw new STCRuntimeError("Unexpect value tree type in "
          + " global constant: " + LogHelper.tokName(val.getType()));
    }
  }
}
