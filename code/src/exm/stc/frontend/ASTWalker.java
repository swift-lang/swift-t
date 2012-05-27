package exm.stc.frontend;

import java.util.*;

import org.apache.log4j.Logger;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.Types.FunctionType;
import exm.stc.ast.Types.PrimType;
import exm.stc.ast.Types.ReferenceType;
import exm.stc.ast.Types.StructType;
import exm.stc.ast.Types.SwiftType;
import exm.stc.ast.Types.FunctionType.InArgT;
import exm.stc.ast.Types.StructType.StructField;
import exm.stc.ast.Variable.DefType;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.ast.descriptor.ArrayRange;
import exm.stc.ast.descriptor.ForLoopDescriptor;
import exm.stc.ast.descriptor.ForeachLoop;
import exm.stc.ast.descriptor.FunctionDecl;
import exm.stc.ast.descriptor.If;
import exm.stc.ast.descriptor.IterateDescriptor;
import exm.stc.ast.descriptor.LValue;
import exm.stc.ast.descriptor.Literals;
import exm.stc.ast.descriptor.Switch;
import exm.stc.ast.descriptor.Update;
import exm.stc.ast.descriptor.VariableDeclaration;
import exm.stc.ast.descriptor.Wait;
import exm.stc.ast.descriptor.ForLoopDescriptor.LoopVar;
import exm.stc.ast.descriptor.VariableDeclaration.VariableDescriptor;
import exm.stc.common.CompilerBackend;
import exm.stc.common.Settings;
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
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.frontend.Context.VisibleVariable;
import exm.stc.frontend.VariableUsageInfo.VInfo;
import exm.stc.ic.ICInstructions.Oparg;

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


  public void walk(CompilerBackend backend, SwiftAST tree) 
          throws UserException {
    this.backend = backend;
    this.varCreator = new VarCreator(backend);
    this.exprWalker = new ExprWalker(varCreator, backend, lineMapping);
    GlobalContext context = new GlobalContext(inputFile, Logger.getLogger(""));

    // Dump ANTLR's view of the SwiftAST (unsightly):
    // if (logger.isDebugEnabled())
    // logger.debug("tree: \n" + tree.toStringTree());

    // Use our custom printTree
    if (LogHelper.isDebugEnabled())
      LogHelper.debug(context, tree.printTree());

    backend.header();
    walkProgram(context, tree);
    if (!context.isCompositeFunction("main")) {
      throw new UndefinedFunctionException(context,
          "No main function was defined in the script");
    }
    backend.turbineStartup();
  }

  private void walkProgram(Context context, SwiftAST programTree)
      throws UserException {
    /*
     * Do two passes over the program
     * First pass:
     *  - Register (but don't compile) composites
     *  - Fully process app functions and built-in functions
     * Second pass:
     *  - Compile composites, now that all function names are known
     */
    int token = programTree.getType();
    

    context.syncFileLine(programTree.getLine(), lineMapping);

    if (token == ExMParser.PROGRAM) {
      for (int i = 0; i < programTree.getChildCount(); i++) {
        SwiftAST topLevelDefn = programTree.child(i);
        int type = topLevelDefn.getType();
        context.syncFileLine(topLevelDefn.getLine(), lineMapping);
        switch (type) {

        /*
         * Not used anymore: replaced with LineMapping
        case ExMParser.CPP_LINEMARKER:
          linemarker(context, topLevelDefn);
          break;
          */

        case ExMParser.DEFINE_BUILTIN_FUNCTION:
          defineBuiltinFunction(context, topLevelDefn);
          break;

        case ExMParser.DEFINE_FUNCTION:
          defineFunction(context, topLevelDefn);
          break;

        case ExMParser.DEFINE_APP_FUNCTION:
          defineAppFunction(context, topLevelDefn);
          break;

        case ExMParser.DEFINE_NEW_TYPE:
          defineNewType(context, topLevelDefn);
          break;
          
        case ExMParser.GLOBAL_CONST:
          globalConst(context, topLevelDefn);
          break;
        
        case ExMParser.EOF:
          endOfFile(context, topLevelDefn);
          break;

        default:
          String name = ExMParser.tokenNames[type];
          throw new STCRuntimeError("Unexpected token: " + name
              + " at program top level");
        }
      }
      
      context.syncFileLine(programTree.getLine(), lineMapping);
      // Second pass to compile functions
      for (int i = 0; i < programTree.getChildCount(); i++) {
        SwiftAST topLevelDefn = programTree.child(i);
        context.syncFileLine(topLevelDefn.getLine(), lineMapping);
        int type = topLevelDefn.getType();
        switch (type) {
        case ExMParser.DEFINE_FUNCTION:
          compileFunction(context, topLevelDefn);
          break;
        }
      }
    } else {
      throw new STCRuntimeError("Unexpected token: " +
          ExMParser.tokenNames[token] + " instead of PROGRAM");
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
      context.syncFileLine(tree.getLine(), lineMapping);
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
           ExMParser.tokenNames[token]);
      }
  }

  private void waitStmt(Context context, SwiftAST tree) 
                                  throws UserException {
    Wait wait = Wait.fromAST(context, tree);
    ArrayList<Variable> waitEvaled = new ArrayList<Variable>();
    for (SwiftAST expr: wait.getWaitExprs()) {
      Variable res = exprWalker.evalExprToTmp(context, expr, 
          TypeChecker.findSingleExprType(context, expr), false, null);
      waitEvaled.add(res);
    }
    
    ArrayList<Variable> usedVars = new ArrayList<Variable>();
    ArrayList<Variable> writtenContainers = new ArrayList<Variable>();
    summariseBranchVariableUsage(context, 
                    Arrays.asList(wait.getBlock().getVariableUsage()), 
                                  usedVars, writtenContainers);
    
    
    // Quick sanity check to see we're not directly blocking
    // on any containers written inside
    HashSet<String> waitVarSet = 
        new HashSet<String>(Variable.nameList(waitEvaled));
    waitVarSet.retainAll(Variable.nameList(writtenContainers));
    if (waitVarSet.size() > 0) {
      throw new UserException(context, 
          "Deadlock in wait statement. The following containers are written "
        + "inside the body of the wait: " + waitVarSet.toString());
    }
    
    backend.startWaitStatement(
          context.getFunctionContext().constructName("explicitwait"),
                      waitEvaled, usedVars, writtenContainers, true);
    block(new LocalContext(context), wait.getBlock());
    backend.endWaitStatement(writtenContainers);
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

    for (int i = 0; i < tree.getChildCount(); i++) {
      SwiftAST child = tree.child(i);
      walkStatement(context, child, blockVu);
    }

    closeBlockVariables(context);

    LogHelper.trace(context, "block done");
  }

  /**
   * Make sure all arrays in block are closed upon exiting
   *
   * TODO: this is currently broken, because we want to run this code after all
   * branches have finished executing, but we don't currently have a way to know
   * that all branches are actually done
   *
   * @param context
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void closeBlockVariables(Context context) 
          throws UndefinedTypeException, UserException {
    for (Variable v : context.getArraysToClose()) {
      assert(v.getDefType() != DefType.INARG);
      assert(Types.isArray(v.getType()));
      backend.closeArray(v);
    }
  }

  private static class VarInfoPair {
    public final Variable var;
    public final VInfo vinfo;
    public VarInfoPair(Variable var, VInfo vinfo) {
      super();
      this.var = var;
      this.vinfo = vinfo;
    }
    
    public String toString() {
      return this.var.toString() + " pair";
    }
  }
  private void findArraysInStruct(Context context,
      Variable root, VInfo structVInfo, List<VarInfoPair> arrays)
          throws UndefinedTypeException, UserException {
    findArraysInStructToClose(context, root, root, structVInfo,
        new Stack<String>(), arrays);
  }

  private void findArraysInStructToClose(Context context,
      Variable root, Variable struct, VInfo structVInfo,
      Stack<String> fieldPath, List<VarInfoPair> arrays) throws UndefinedTypeException,
                                                                      UserException {
    StructType vtype = (StructType)struct.getType();
    for (StructField f: vtype.getFields()) {
      fieldPath.push(f.getName());
      if (Types.isArray(f.getType())) {
        Variable fieldVar = context.getStructFieldTmp(root, fieldPath);
        if (fieldVar == null) { 
          fieldVar = exprWalker.structLookup(context, struct, f.getName(),
                    false, root, fieldPath);
        }
        arrays.add(new VarInfoPair(fieldVar, structVInfo.getFieldVInfo(f.getName())));
      } else if (Types.isStruct(f.getType())) {
        VInfo nestedVInfo = structVInfo.getFieldVInfo(f.getName());
        assert(nestedVInfo != null);
        Variable field = context.getStructFieldTmp(root, fieldPath);
        if (field == null) {
          field = exprWalker.structLookup(context, struct, f.getName(),
              false, root, fieldPath);
        }
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
    Variable conditionVar = exprWalker.evalExprToTmp(context,
        ifStmt.getCondition(), ifStmt.getCondType(context),
        false, null);
    assert (conditionVar != null);

    // A list of variables that might be referenced in either branch
    List<Variable> usedVariables = new ArrayList<Variable>();

    // List of containers that might be modified on one branch or the other
    // IF a container is modified on a branch, then we have to make sure that
    // it won't be prematurely closed
    List<Variable> containersToRegister = new ArrayList<Variable>();

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
        containersToRegister);

    if (!usedVariables.contains(conditionVar))
      usedVariables.add(conditionVar);

    FunctionContext fc = context.getFunctionContext();
    backend.startWaitStatement( fc.constructName("if"), 
              Arrays.asList(conditionVar),
                usedVariables, containersToRegister, false);

    Context waitContext = new LocalContext(context);
    Variable condVal = varCreator.fetchValueOf(waitContext, conditionVar);
    backend.startIfStatement(Oparg.createVar(condVal), ifStmt.hasElse());
    block(new LocalContext(waitContext), ifStmt.getThenBlock());

    if (ifStmt.hasElse()) {
      backend.startElseBlock();
      block(new LocalContext(waitContext), ifStmt.getElseBlock());
    }
    backend.endIfStatement();
    backend.endWaitStatement(containersToRegister);
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
  private void checkConditionalDeadlock(Context context, Variable conditionVar,
      List<VariableUsageInfo> branchVUs) throws VariableUsageException {
    for (VariableUsageInfo branchVU: branchVUs) {
      assert(branchVU != null);
      VInfo vinfo = branchVU.lookupVariableInfo(conditionVar.getName());
      if (vinfo != null && vinfo.isAssigned() != Ternary.FALSE) {
        throw new VariableUsageException(context, "Deadlock on " +
            conditionVar.getName() + ", var is assigned inside conditional"
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
   * @param containersToRegister
   *          All containers that might be written are added here
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void summariseBranchVariableUsage(Context context,
      List<VariableUsageInfo> branchVUs, List<Variable> usedVariables,
      List<Variable> containersToRegister) throws UndefinedTypeException, UserException {
    for (VisibleVariable vv : context.getVisibleVariables().values()) {
      Variable v = vv.getVariable();
      Ternary isUsed = Ternary.FALSE;
      for (VariableUsageInfo bvu : branchVUs) {
        VInfo vi = bvu.lookupVariableInfo(v.getName());
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
      if (Types.isArray(v.getType())) {
        for (VariableUsageInfo bvu : branchVUs) {
          VInfo vi = bvu.lookupVariableInfo(v.getName());
          if (vi != null && vi.isAssigned() != Ternary.FALSE) {
            containersToRegister.add(v);
            break;
          }
        }
      } else if (Types.isStruct(v.getType())) {
        // Need to find arrays inside structs
        ArrayList<VarInfoPair> arrs = new ArrayList<VarInfoPair>();
        // This procedure might add the same array multiple times,
        // so use a set to avoid duplicates
        HashSet<Variable> alreadyFound = new HashSet<Variable>();
        for (VariableUsageInfo bvu : branchVUs) {
          arrs.clear();
          findArraysInStruct(context, v, bvu.lookupVariableInfo(v.getName()),
                                                                        arrs);
          for (VarInfoPair p: arrs) {
            if (p.vinfo.isAssigned() != Ternary.FALSE) {
              alreadyFound.add(p.var);
            }
          }
        }
        containersToRegister.addAll(alreadyFound);
      }
    }
    
    /* Conservatively assume that all cached structure fields are used
     * This always produces correct results, but results in unnecessary
     * variable passing.  IC optimisations can clean this up later
     */
    usedVariables.addAll(context.getCachedStructFields());
  }

  private void switchStatement(Context context, SwiftAST tree)
       throws UserException {
    LogHelper.trace(context, "switch...");    
    
    // Evaluate into a temporary variable. Only int supported now
    
    Switch sw = Switch.fromAST(context, tree);
    sw.typeCheck(context);
    
    Variable switchVar = exprWalker.evalExprToTmp(context, sw.getSwitchExpr(), Types.FUTURE_INTEGER, true, null);

    // A list of variables that might be referenced in either branch
    List<Variable> usedVariables = new ArrayList<Variable>();

    // List of containers that might be modified on one branch or the other
    // IF a container is modified on a branch, then we have to make sure that
    // it won't be prematurely closed
    List<Variable> containersToRegister = new ArrayList<Variable>();

    List<VariableUsageInfo> branchVUs = new ArrayList<VariableUsageInfo>();
    for (SwiftAST b : sw.getCaseBodies()) {
      branchVUs.add(b.checkedGetVariableUsage());
    }

    checkConditionalDeadlock(context, switchVar, branchVUs);
    summariseBranchVariableUsage(context, branchVUs, usedVariables,
        containersToRegister);
    if (!usedVariables.contains(switchVar))
        usedVariables.add(switchVar);

    // Generate all of the code
    FunctionContext fc = context.getFunctionContext();
    backend.startWaitStatement( fc.constructName("switch"),
                Arrays.asList(switchVar),
                usedVariables, containersToRegister, false);

    Context waitContext = new LocalContext(context);
    Variable switchVal = varCreator.createValueOfVar(waitContext,
                                                     switchVar); 

    backend.retrieveInt(switchVal, switchVar);

    LogHelper.trace(context, "switch: " + 
            sw.getCaseBodies().size() + " cases");
    backend.startSwitch(Oparg.createVar(switchVal), sw.getCaseLabels(),
                                                         sw.hasDefault());
    for (SwiftAST caseBody : sw.getCaseBodies()) {
      block(new LocalContext(waitContext), caseBody);
      backend.endCase();
    }
    backend.endSwitch();
    backend.endWaitStatement(containersToRegister);
  }

  private void foreach(Context context, SwiftAST tree) throws UserException {
    ForeachLoop loop = ForeachLoop.fromAST(context, tree); 
    
    if (loop.iteratesOverRange() && loop.getCountVarName() == null) {
      //TODO: don't bother about optimizing cases with loop counter yet
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
    Variable start = exprWalker.evalExprToTmp(context, range.getStart(), Types.FUTURE_INTEGER, false, null);
    Variable end = exprWalker.evalExprToTmp(context, range.getEnd(), Types.FUTURE_INTEGER, false, null);
    Variable step;
    if (range.getStep() != null) {
      step = exprWalker.evalExprToTmp(context, range.getStep(), Types.FUTURE_INTEGER, false, null);
    } else {
      // Inefficient but constant folding will clean up
      step = varCreator.createTmp(context, Types.FUTURE_INTEGER);
      backend.assignInt(step, Oparg.createIntLit(1));
    }
    
    ArrayList<Variable> usedVariables = new ArrayList<Variable>();
    ArrayList<Variable> containersToRegister = new ArrayList<Variable>();

    // TODO: correct??
    VariableUsageInfo bodyVU = loop.getBody().checkedGetVariableUsage();
    summariseBranchVariableUsage(context, Arrays.asList(bodyVU),
        usedVariables, containersToRegister);
    
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("foreach-range");
    
    // Need to pass in futures along with user vars
    ArrayList<Variable> waitUsedVariables = 
        new ArrayList<Variable>(usedVariables);
    waitUsedVariables.addAll(Arrays.asList(start, end, step));
    backend.startWaitStatement("wait-range" + loopNum, 
                                Arrays.asList(start, end, step), 
                                waitUsedVariables, containersToRegister, false);
    Context waitContext = new LocalContext(context);
    Variable startVal = varCreator.fetchValueOf(waitContext, start);
    Variable endVal = varCreator.fetchValueOf(waitContext, end);
    Variable stepVal = varCreator.fetchValueOf(waitContext, step);
    Context bodyContext = loop.setupLoopBodyContext(waitContext);
    
    // The per-iteration value of the range
    Variable memberVal = varCreator.createValueOfVar(bodyContext,
                                            loop.getMemberVar(), false);
    backend.startRangeLoop("range" + loopNum, memberVal, 
            Oparg.createVar(startVal), Oparg.createVar(endVal), 
            Oparg.createVar(stepVal), loop.isSyncLoop(),
            usedVariables, containersToRegister, loop.getDesiredUnroll(),
            loop.getSplitDegree());
    
    // We have the current value, but need to put it in a future in case user
    //  code refers to it
    varCreator.initialiseVariable(bodyContext, loop.getMemberVar());
    backend.assignInt(loop.getMemberVar(), Oparg.createVar(memberVal));
    block(bodyContext, loop.getBody());
    backend.endRangeLoop(loop.isSyncLoop(), containersToRegister,
                                                  loop.getSplitDegree());
    backend.endWaitStatement(containersToRegister);
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
    Variable arrayVar = exprWalker.evalExprToTmp(context, loop.getArrayVarTree(), loop.findArrayType(context), true, null);

    ArrayList<Variable> usedVariables = new ArrayList<Variable>();
    ArrayList<Variable> containersToRegister = new ArrayList<Variable>();

    VariableUsageInfo bodyVU = loop.getBody().checkedGetVariableUsage();
    summariseBranchVariableUsage(context, Arrays.asList(bodyVU),
        usedVariables, containersToRegister);

    for (Variable v: containersToRegister) {
      if (v.getName().equals(arrayVar.getName())) {
        throw new STCRuntimeError("Array variable "
                  + v.getName() + " is written in the foreach loop "
                  + " it is the loop array for - currently this " +
                  "causes a deadlock due to technical limitations");
      }
    }
    
    // Need to get handle to real array before running loop
    Variable realArray;
    Context outsideLoopContext;
    if (Types.isArrayRef(arrayVar.getType())) {
      // If its a reference, wrap a wait() around the loop call
      FunctionContext fc = context.getFunctionContext();
      ArrayList<Variable> waitUsedVars =
            new ArrayList<Variable>(usedVariables);
      waitUsedVars.add(arrayVar);

      backend.startWaitStatement(fc.constructName("foreach_wait"),
          Arrays.asList(arrayVar), waitUsedVars, containersToRegister, false);

      outsideLoopContext = new LocalContext(context);
      realArray = varCreator.createTmp(outsideLoopContext,
                              arrayVar.getType().getMemberType(), false, true);
      backend.retrieveRef(realArray, arrayVar);
    } else {
      realArray = arrayVar;
      outsideLoopContext = context;
    }
    loop.setupLoopBodyContext(outsideLoopContext);
    Context loopBodyContext = loop.getBodyContext();

    if (loop.getDesiredUnroll() != 1) {
      throw new STCRuntimeError("Loop unrolling not"
          + " yet supported for loops over containers");
    }
    backend.startForeachLoop(realArray, loop.getMemberVar(), loop.getLoopCountVal(),
        loop.isSyncLoop(), loop.getSplitDegree(), false, 
        usedVariables, containersToRegister);
    // If the user's code expects a loop count var, need to create it here
    if (loop.getCountVarName() != null) {
      Variable loopCountVar = varCreator.createVariable(loop.getBodyContext(),
          Types.FUTURE_INTEGER, loop.getCountVarName(), VariableStorage.STACK,
          DefType.LOCAL_USER, null);
      backend.assignInt(loopCountVar, Oparg.createVar(loop.getLoopCountVal()));
    }
    block(loopBodyContext, loop.getBody());
    backend.endForeachLoop(loop.isSyncLoop(), loop.getSplitDegree(), false, 
                                                    containersToRegister);

    if (Types.isArrayRef(arrayVar.getType())) {
      backend.endWaitStatement(containersToRegister);
    }
  }

  private void forLoop(Context context, SwiftAST tree) throws UserException {
    ForLoopDescriptor forLoop = ForLoopDescriptor.fromAST(context, tree);
    
    // Evaluate initial values of loop vars
    List<Variable> initVals = evalLoopVarExprs(context, forLoop, 
                                                  forLoop.getInitExprs());
    List<Variable> usedVariables = new ArrayList<Variable>();
    List<Variable> containersToRegister = new ArrayList<Variable>();
    summariseBranchVariableUsage(context, 
            Arrays.asList(forLoop.getBody().getVariableUsage()), 
            usedVariables, containersToRegister);
    
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("forloop");
    String loopName = fc.getFunctionName() + "-forloop-" + loopNum;
    
    HashMap<String, Variable> parentLoopVarAliases = 
        new HashMap<String, Variable>();
    for (LoopVar lv: forLoop.getLoopVars()) {
      if (lv.declaredOutsideLoop) {
        // Need to copy over value of loop variable on last iteration
        Variable parentAlias = 
            varCreator.createVariable(context, lv.var.getType(), 
                  Variable.OUTER_VAR_PREFIX + lv.var.getName(),
                  VariableStorage.ALIAS, DefType.LOCAL_COMPILER,
                  lv.var.getMapping());
        // Copy turbine ID
        backend.makeAlias(parentAlias, lv.var);
        usedVariables.add(parentAlias);
        parentLoopVarAliases.put(lv.var.getName(), parentAlias);
      }
    }
    
    LogHelper.debug(context, "usedVariables in forLoop: " + 
            usedVariables.toString() + " at " + forLoop.getBody().getLine() 
            + "." + forLoop.getBody().getCharPositionInLine());
    
    // Create context with loop variables
    Context loopBodyContext = forLoop.createBodyContext(context);
    forLoop.validateCond(loopBodyContext);
    SwiftType condType = TypeChecker.findSingleExprType(loopBodyContext, 
                                              forLoop.getCondition());

    // Evaluate the conditional expression for the first iteration outside the
    // loop, directly using temp names for loop variables
    HashMap<String, String> initRenames = new HashMap<String, String>();
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      initRenames.put(forLoop.getLoopVars().get(i).var.getName(), 
            initVals.get(i).getName());
    }
    Variable initCond = exprWalker.evalExprToTmp(context, forLoop.getCondition(), condType, true, initRenames);
    
    // Start the loop construct with some initial values
    Variable condArg = 
        loopBodyContext.declareVariable(condType, Variable.LOOP_COND_PREFIX + 
            loopNum, VariableStorage.TEMPORARY, DefType.INARG, null);



    /* Pack the variables into vectors with the first element the condition */
    ArrayList<Variable> loopVars = new ArrayList<Variable>(
                                              forLoop.loopVarCount() + 1);
    loopVars.add(condArg);
    loopVars.addAll(forLoop.getUnpackedLoopVars());
    
    List<Boolean> blockingVector = new ArrayList<Boolean>(loopVars.size());
    blockingVector.add(true); // block on condition
    blockingVector.addAll(forLoop.blockingLoopVarVector());
    
    initVals.add(0, initCond);
    
    backend.startLoop(loopName, loopVars, initVals, 
                      usedVariables, containersToRegister,
                      blockingVector);
    
    // get value of condVar
    Variable condVal = varCreator.fetchValueOf(loopBodyContext, condArg);
    
    // branch depending on if loop should start
    backend.startIfStatement(Oparg.createVar(condVal), true);
    // If this iteration is good, run all of the stuff in the block
    block(loopBodyContext, forLoop.getBody());
    
    forLoop.validateUpdates(loopBodyContext);
    //evaluate update expressions
    List<Variable> newLoopVars = evalLoopVarExprs(loopBodyContext, forLoop, 
                                                forLoop.getUpdateRules());
    
    HashMap<String, String> nextRenames = new HashMap<String, String>();
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      nextRenames.put(forLoop.getLoopVars().get(i).var.getName(), 
            newLoopVars.get(i).getName());
    }
    Variable nextCond = exprWalker.evalExprToTmp(loopBodyContext, 
              forLoop.getCondition(), condType, true, nextRenames);
    newLoopVars.add(0, nextCond);
    backend.loopContinue(newLoopVars, usedVariables, containersToRegister,
                                                            blockingVector);
    backend.startElseBlock();
    // Terminate loop, clean up open containers and copy out final vals 
    // of loop vars
    for (LoopVar lv: forLoop.getLoopVars()) {
      if (lv.declaredOutsideLoop) {
        exprWalker.copyByValue(loopBodyContext, 
            lv.var, parentLoopVarAliases.get(lv.var.getName()), 
            lv.var.getType());
      }
    }
    
    backend.loopBreak(containersToRegister);
    backend.endIfStatement();
    // finish loop construct
    backend.endLoop();
  }


  
  private void iterate(Context context, SwiftAST tree) throws UserException {
    IterateDescriptor loop = IterateDescriptor.fromAST(context, tree);
    
    
    
    //TODO: this is a little funny since the condition expr might be of type int,
    //    but this will work for time being
    Variable falseV = varCreator.createTmp(context, Types.FUTURE_BOOLEAN);
    backend.assignBool(falseV, Oparg.createBoolLit(false));
    
    Variable zero = varCreator.createTmp(context, Types.FUTURE_INTEGER);
    backend.assignInt(zero, Oparg.createIntLit(0));
    
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("iterate");
    String loopName = fc.getFunctionName() + "-iterate-" + loopNum;
    
    List<Variable> usedVariables = new ArrayList<Variable>();
    List<Variable> containersToRegister = new ArrayList<Variable>();
    summariseBranchVariableUsage(context, 
            Arrays.asList(loop.getBody().getVariableUsage()), 
            usedVariables, containersToRegister);

    Context bodyContext = loop.createBodyContext(context);
    
    
    // Start the loop construct with some initial values
    Variable condArg = 
      bodyContext.declareVariable(Types.FUTURE_BOOLEAN, Variable.LOOP_COND_PREFIX + 
            loopNum, VariableStorage.TEMPORARY, DefType.INARG, null);
    
    List<Boolean> blockingVars = Arrays.asList(true, false);
    backend.startLoop(loopName, 
        Arrays.asList(condArg, loop.getLoopVar()), Arrays.asList(falseV, zero), 
        usedVariables, containersToRegister, blockingVars);
    
    // get value of condVar
    Variable condVal = varCreator.fetchValueOf(bodyContext, condArg); 
    
    backend.startIfStatement(Oparg.createVar(condVal), true);
    if (containersToRegister.size() > 0) {
      backend.loopBreak(containersToRegister);
    }
    backend.startElseBlock();
    block(bodyContext, loop.getBody());
    
    // Check the condition type now that all loop body vars have been declared
    SwiftType condType = TypeChecker.findSingleExprType(bodyContext,
        loop.getCond());
    if (condType.equals(Types.FUTURE_INTEGER)) {
      // TODO: for now, assume boolean
      throw new STCRuntimeError("don't support non-boolean conditions" +
      		" for iterate yet");
    } else if (!condType.equals(Types.FUTURE_BOOLEAN)) {
      throw new TypeMismatchException(bodyContext, 
          "iterate condition had invalid type: " + condType.typeName());
    }
    
    Variable nextCond = exprWalker.evalExprToTmp(bodyContext, loop.getCond(), condType, false, null);
    
    Variable nextCounter = varCreator.createTmp(bodyContext,
                                      Types.FUTURE_INTEGER);
    Variable one = varCreator.createTmp(bodyContext, Types.FUTURE_INTEGER);

    backend.assignInt(one, Oparg.createIntLit(1));
    backend.builtinFunctionCall(
        Builtins.getArithBuiltin(PrimType.INTEGER, ExMParser.PLUS), 
        Arrays.asList(loop.getLoopVar(), one), Arrays.asList(nextCounter),
        null);
    backend.loopContinue(Arrays.asList(nextCond, nextCounter), 
        usedVariables, containersToRegister, blockingVars);

    backend.endIfStatement();
    backend.endLoop();
  }


  private ArrayList<Variable> evalLoopVarExprs(Context context,
      ForLoopDescriptor forLoop, Map<String, SwiftAST> loopVarExprs)
      throws UserException {
    ArrayList<Variable> results = new ArrayList<Variable>(
                                                forLoop.loopVarCount() + 1);
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      Variable v = forLoop.getLoopVars().get(i).var;
      SwiftType argType = v.getType();
      SwiftAST expr = loopVarExprs.get(v.getName());
      SwiftType exprType = TypeChecker.findSingleExprType(context, expr);

      if (!exprType.equals(argType)) {
        throw new STCRuntimeError("haven't implemented conversion " +
            " from " + exprType.typeName() + " to " + argType.typeName() +
            " for loop expressions");
      }
      results.add(exprWalker.evalExprToTmp(context, expr, exprType, false, null));
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
      Variable var = declareVariable(context, blockVu, vDesc);
      SwiftAST assignedExpr = vd.getVarExpr(i);
      if (Types.isScalarUpdateable(var.getType())) {
        initUpdateableVar(context, var, assignedExpr);
      } else {
         if (assignedExpr != null) {
           assignMultiExpression(context, Arrays.asList(new LValue(var)),
                                  Arrays.asList(assignedExpr));
         }
      }
    }
  }


  private void initUpdateableVar(Context context, Variable var,
                                                SwiftAST initExpr) {
    if (initExpr != null) {
      // TODO
      // Handle as special case because currently we need an initial
      // value for the updateable variable right away
      if (var.getType().equals(Types.UPDATEABLE_FLOAT)) {
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
        backend.initUpdateable(var, Oparg.createFloatLit(initVal));
      } else {
        throw new STCRuntimeError("Non-float updateables not yet" +
        		" implemented");
      }
    } else {
      throw new STCRuntimeError("updateable variable " +
          var.getName() + " must be given an initial value upon creation");
    }
  }

  private Variable declareVariable(Context context, VariableUsageInfo blockVu,
      VariableDescriptor vDesc) throws UserException, UndefinedTypeException {
    VInfo vi = blockVu.lookupVariableInfo(vDesc.getName());
    SwiftType definedType = vDesc.getType();
    // Sometimes we have to use a reference to an array instead of an array
    SwiftType internalType;

    Variable mappedVar = null;
    // First evaluate the mapping expr
    if (vDesc.getMappingExpr() != null) {
      if (Types.isMappable(vDesc.getType())) {
        SwiftType mapType = TypeChecker.findSingleExprType(context, 
                                          vDesc.getMappingExpr());
        if (!Types.isString(mapType)) {
          throw new TypeMismatchException(context, "Tried to map using " +
          		"non-string expression with type " + mapType.typeName());
        }
        mappedVar = exprWalker.evalExprToTmp(context, vDesc.getMappingExpr(), Types.FUTURE_STRING, false, null);
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
      internalType = new ReferenceType(definedType);
    } else {
      internalType = definedType;
    }
    Variable var = varCreator.createVariable(context, internalType, 
        vDesc.getName(), VariableStorage.STACK, DefType.LOCAL_USER, mappedVar);

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
    List<SwiftType> lValTypes = new ArrayList<SwiftType>(lVals.size());
    for (LValue lval: lVals) {
      lValTypes.add(lval.getType(context));
    }
    
    List<SwiftType> rValTypes = TypeChecker.findExprType(context, rValExpr,
                                                                lValTypes);
    if (rValTypes.size() != lVals.size()) {
      throw new TypeMismatchException(context, "Needed " + rValTypes.size()
          + " " + "assignment targets on LHS of assignment, but "
          + lVals.size() + " were present");
    }

    List<Variable> result = new ArrayList<Variable>(lVals.size());
    Deque<Runnable> afterActions = new LinkedList<Runnable>();
    boolean skipEval = false;
    
    for (int i = 0; i < lVals.size(); i++) {
      LValue lval = lVals.get(i);
      SwiftType lValType = lValTypes.get(i);
      SwiftType rValType = rValTypes.get(i);
      String targetName = lval.toString();
      TypeChecker.checkAssignment(context, rValType, lValType, targetName);
      backend.addComment("Swift l." + context.getLine() +
          ": assigning expression to " + targetName);

      // the variable we will evaluate expression into
      Variable var = evalLValue(context, rValExpr, rValType, lval, 
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
        if (var.getName().equals(rValVar)) {
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
      exprWalker.walkExpr(context, rValExpr, result, null);
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
  private Variable evalLValue(Context context, SwiftAST rValExpr,
      SwiftType rValType, LValue lval, Deque<Runnable> afterActions)
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
        assert(Types.isArray(arrayBaseLval.var.getType()));
        lval = reduceArrayLVal(context, arrayBaseLval, lval, rValExpr, rValType,
                                afterActions);
        LogHelper.trace(context, "Reduced to lval " + lval.toString() + 
                " with type " + lval.getType(context));
      }
    }

    String varName = lval.varName;
    Variable lValVar = context.getDeclaredVariable(varName);
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
  private Variable fixupRefValMismatch(Context context, SwiftType rValType,
      Variable lValVar) throws UserException, UndefinedTypeException {
    if (lValVar.getType().equals(rValType)) {
      return lValVar;
    } else if (Types.isReferenceTo(lValVar.getType(), rValType)) {
      Variable rValVar = varCreator.createTmp(context, rValType);
      backend.assignReference(lValVar, rValVar);
      return rValVar;
    } else if (Types.isReferenceTo(lValVar.getType(), rValType)) {
      Variable rValVar = varCreator.createTmp(context, rValType);
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
    Variable rootVar = context.getDeclaredVariable(lval.varName);

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

    Variable curr = rootVar;
    for (int i = 0; i < structPathLen; i++) {
      List<String> currPath = fieldPath.subList(0, i+1);
      Variable next = context.getStructFieldTmp(rootVar, currPath);
      if (next == null) {
          next = context.createStructFieldTmp(rootVar, 
              lval.getType(context, i+1), currPath, VariableStorage.ALIAS);
          varCreator.declare(next);
          backend.structLookup(curr, fieldPath.get(i), next);
      }
      curr = next;
      LogHelper.trace(context, "Lookup " + curr.getName() + "." +
                               fieldPath.get(i));
    }
    LValue newTarget = new LValue(curr,
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
    LValue lval, SwiftAST rValExpr, SwiftType rValType, Deque<Runnable> afterActions)
        throws TypeMismatchException, UndefinedTypeException, UserException {

    SwiftAST indexExpr = lval.indices.get(0);
    assert (indexExpr.getType() == ExMParser.ARRAY_PATH);
    assert (indexExpr.getChildCount() == 1);

    if (lval.indices.size() == 1) {
      Variable lookedup = assignTo1DArray(context, origLval, lval, rValExpr, 
                                                      rValType, afterActions);
      return new LValue(lookedup, new ArrayList<SwiftAST>());
    } else {
      //TODO: multi-dimensional array handling goes here: need to
      //    dynamically create subarray
      Variable lvalArr = context.getDeclaredVariable(lval.varName);
      SwiftType memberType = lval.getType(context, 1);
      Variable mVar; // Variable for member we're looking up
      if (Types.isArray(memberType)) {

        String literal = Literals.extractIntLit(context, indexExpr.child(0));
        if (literal != null) {
          long arrIx = Long.parseLong(literal);
          // Add this variable to container
          if (Types.isArray(lvalArr.getType())) {
            mVar = context.createAliasVariable(memberType);
            varCreator.declare(mVar);
            backend.arrayCreateNestedImm(mVar, lvalArr, 
                        Oparg.createIntLit(arrIx));
          } else {
            assert(Types.isArrayRef(lvalArr.getType()));
            mVar = context.createAliasVariable(new ReferenceType(memberType));
            varCreator.declare(mVar);
            backend.arrayRefCreateNestedImm(mVar, lvalArr, 
                Oparg.createIntLit(arrIx));
          }

        } else {
          // Handle the general case where the index must be computed
          mVar = context.createAliasVariable(new ReferenceType(memberType));
          varCreator.declare(mVar);
          Variable indexVar = exprWalker.evalExprToTmp(context, indexExpr.child(0), Types.FUTURE_INTEGER, false, null);
          
          if (Types.isArray(lvalArr.getType())) {
            backend.arrayCreateNestedFuture(mVar, lvalArr, indexVar);
          } else {
            assert(Types.isArrayRef(lvalArr.getType()));
            backend.arrayRefCreateNestedFuture(mVar, lvalArr, indexVar);
          }
        }
      } else {
        /* 
         * Retrieve non-array member
         * must use reference because we might have to wait for the result to 
         * be inserted
         */
        mVar = varCreator.createTmp(context, new ReferenceType(memberType));
      }

      return new LValue(mVar,
          lval.indices.subList(1, lval.indices.size()));
    }
}

  private Variable assignTo1DArray(Context context, final LValue origLval,
      LValue lval, SwiftAST rvalExpr, SwiftType rvalType,
      Deque<Runnable> afterActions)
      throws TypeMismatchException, UserException, UndefinedTypeException {
    assert (rvalExpr.getType() != ExMParser.ARRAY_PATH);
    assert(lval.indices.size() == 1);
    assert(Types.isArray(origLval.var.getType()));
    final Variable lvalVar;
    // Check that it is a valid array
    final Variable arr = lval.var;

    SwiftType arrType = arr.getType();

    if (!Types.isArray(arrType) && !Types.isArrayRef(arrType)) {
      throw new TypeMismatchException(context, "Variable " + arr.getName()
          + "is not an array, cannot index\n.");
    }
    boolean isRef = Types.isArrayRef(arrType);

    LogHelper.debug(context, 
            "Token type: " + ExMParser.tokenNames[rvalExpr.getType()]);
    // Find or create variable to store expression result

    if (!Types.isReference(rvalType)) {
      if (rvalExpr.getType() == ExMParser.VARIABLE) {
        // Get a handle to the variable, so we can just insert the variable
        //  directly into the array
        // This is a bit of a hack.  We return the rval as the lval and rely
        //  on the rest of the compiler frontend to treat the self-assignment
        //  as a no-op
        lvalVar = context.getDeclaredVariable(rvalExpr.child(0).getText());
      } else {
        // In other cases we need an intermediate variable
        SwiftType arrMemberType;
        if (Types.isArray(arrType)) {
          arrMemberType = arrType.getMemberType();
        } else {
          assert(Types.isArrayRef(arrType));
          arrMemberType = arrType.getMemberType().getMemberType();
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
      // Add this variable to container
      if (isRef) {
        // This should only be run when assigning to nested array
        afterActions.addFirst(new Runnable() {
          public void run() {
            backend.arrayRefInsertImm(lvalVar,
                      arr, Oparg.createIntLit(arrIx), origLval.var);
          }});
      } else {
        afterActions.addFirst(new Runnable() {
          public void run() {
            backend.arrayInsertImm(lvalVar, arr, 
                Oparg.createIntLit(arrIx));
          }});
      }
    } else {
      // Handle the general case where the index must be computed
      final Variable indexVar = exprWalker.evalExprToTmp(context, indexExpr, Types.FUTURE_INTEGER, false, null);

      if (isRef) {
        afterActions.addFirst(new Runnable() {
          public void run() {
            backend.arrayRefInsertFuture(lvalVar, arr, 
                                              indexVar, origLval.var);
          }});
      } else {
        afterActions.addFirst(new Runnable() {
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

    List<SwiftType> exprType = TypeChecker.findExprType(context, expr);

    backend.addComment("Swift l." + context.getLine() + " evaluating "
        + " expression and throwing away " + exprType.size() +
        " results");

    // Need to create throwaway temporaries for return values
    List<Variable> oList = new ArrayList<Variable>();
    for (SwiftType t : exprType) {
      oList.add(varCreator.createTmp(context, t));
    }

    exprWalker.walkExpr(context, expr, oList, null);
  }

  private void updateStmt(Context context, SwiftAST tree) 
        throws UserException {
    Update up = Update.fromAST(context, tree);
    SwiftType exprType = up.typecheck(context);
    Variable evaled = exprWalker.evalExprToTmp(context, up.getExpr(), exprType, false, null);
    backend.update(up.getTarget(), up.getMode(), evaled);
  }


  private void defineBuiltinFunction(Context context, SwiftAST tree)
  throws UserException
  {
    String function  = tree.child(0).getText();
    SwiftAST outputs = tree.child(1);
    SwiftAST inputs  = tree.child(2);
    assert(inputs.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    assert(outputs.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    String pkg     = Literals.extractLiteralString(context, tree.child(3));
    String version = Literals.extractLiteralString(context,
                                                        tree.child(4));
    String symbol  = Literals.extractLiteralString(context, tree.child(5));
    
    FunctionDecl fdecl = FunctionDecl.fromAST(context, 
                                        inputs, outputs);

    FunctionType ft = fdecl.getFunctionType();
    LogHelper.debug(context, "builtin: " + function + " " + ft);
    
    if (context.isFunction(function)) {
      throw new DoubleDefineException(context, "function called " + function 
          + " is already defined");
    }
    
    Builtins.add(function, ft);
    backend.defineBuiltinFunction(function, pkg, version, symbol, ft);
  }

  private void defineFunction(Context context, SwiftAST tree)
  throws UserException {
    context.syncFileLine(tree.getLine(), lineMapping);
    String function = tree.child(0).getText();
    LogHelper.debug(context, "define function: " + context.getLocation() +
                              function);
    assert(tree.getChildCount() >= 4);
    SwiftAST outputs = tree.child(1);
    SwiftAST inputs = tree.child(2);
    
    ArrayList<String> annotations = new ArrayList<String>();
    for (int i = 4; i < tree.getChildCount(); i++) {
      SwiftAST subtree =tree.child(i);
      assert(subtree.getType() == ExMParser.ANNOTATION);
      assert(subtree.getChildCount() == 1 || subtree.getChildCount() == 2);
      if (subtree.getChildCount() == 2) {
        throw new InvalidAnnotationException(context,
                      "no key-value annotations for function defs");
      }
      String annotation = subtree.child(0).getText();
      annotations.add(annotation);
    }

    
    FunctionDecl fdecl = FunctionDecl.fromAST(context, inputs, outputs);
    FunctionType ft = fdecl.getFunctionType();
    
    if (ft.hasVarargs()) {
      throw new TypeMismatchException(context, "composite function cannot" +
      		" have variable-length argument lists");
    }
    for (InArgT it: ft.getInputs()) {
      if (it.getAlternatives().length != 1) {
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
    if (annotations.size() > 1) {
      throw new InvalidAnnotationException(context, "declaration of composite " +
      		"function " + function + " has multiple annotations: " + 
          annotations.toString() + " but composite functions only take the " +
          		"@sync annotation currently");
          
    } else if (annotations.size() == 1) {
      if (annotations.get(0).equals("sync")) {
        async = false;
      } else {
        throw new InvalidAnnotationException(context, "unknown annotation" +
        		" for composite function: @" + annotations.get(0));
      }
    }
    
    context.defineCompositeFunction(function, ft, async);
  }

  /** Compile the function, assuming it is already defined in context */
  private void compileFunction(Context context, SwiftAST tree)
                                            throws UserException {
    String function = tree.child(0).getText();
    LogHelper.debug(context, "compile function: starting: " + function );
    // defineFunction should already have been called
    assert(context.isCompositeFunction(function));
    SwiftAST outputs = tree.child(1);
    SwiftAST inputs = tree.child(2);
    SwiftAST block = tree.child(3);

    FunctionDecl fdecl = FunctionDecl.fromAST(context, inputs, outputs);
    
    List<Variable> iList = fdecl.getInVars();
    List<Variable> oList = fdecl.getOutVars();
    
    // Analyse variable usage inside function and annotate AST
    context.syncFileLine(tree.getLine(), lineMapping);
    varAnalyzer.analyzeVariableUsage(context, function, iList, oList, block);

    LocalContext functionContext = new LocalContext(context, function);
    functionContext.setNested(false);
    functionContext.addDeclaredVariables(iList);
    functionContext.addDeclaredVariables(oList);

    backend.startCompositeFunction(function, oList, iList,
        !context.isSyncComposite(function));
    
    VariableUsageInfo vu = block.getVariableUsage();
    // Make sure output arrays get closed
    for (Variable o: oList) {
      flagDeclaredVarForClosing(functionContext, o, vu);
    }

    block(functionContext, block);
    backend.endCompositeFunction();

    LogHelper.debug(context, "compile function: done: " + function);
  }


  private void flagDeclaredVarForClosing(Context context, Variable var,
      VariableUsageInfo vu) throws UndefinedTypeException, UserException {
    List<VarInfoPair> foundArrs = null;

    // First find all of the arrays and the vinfo for them
    VInfo vi = vu.lookupVariableInfo(var.getName());
    if (Types.isArray(var.getType())) {
      foundArrs = Collections.singletonList(new VarInfoPair(var, vi));
    } else if (Types.isStruct(var.getType())) {
      // Might have to dig into struct and load members to see if we 
      // should close it
      foundArrs = new ArrayList<VarInfoPair>();
      findArraysInStruct(context, var, vi, foundArrs);
    }
    
    if (foundArrs != null) {        
      for (VarInfoPair p: foundArrs) {
        VInfo arrVI = p.vinfo;
        Variable arr = p.var;
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
    // logChildren(tree);

    tree.printTree();

    String function = tree.child(0).getText();
    LogHelper.trace(context, "function: " + function);
    // SwiftAST inputs = tree.getChild2(1);
    // SwiftAST outputs = tree.getChild2(2);
    SwiftAST command = tree.child(3);

    // List<Declaration> iList = Declaration.fromTree(inputs);
    // List<Declaration> oList = Declaration.fromTree(outputs);

    // TODO: fix once we have variables defined
    context.defineAppFunction(function, 
        new FunctionType(new ArrayList<InArgT>(), new ArrayList<SwiftType>()));
    // String code = generator.defineApp(function, iList, oList);
    appFunctionBody(new LocalContext(context), command);
    LogHelper.debug(context, "defineAppFunction done");
  }

  private void appFunctionBody(LocalContext localContext, SwiftAST command) {
    // TODO Auto-generated method stub

  }

  private void defineNewType(Context context, SwiftAST defnTree)
      throws DoubleDefineException, UndefinedTypeException {
    assert (defnTree.getType() == ExMParser.DEFINE_NEW_TYPE);
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
      SwiftType fieldType = context.lookupType(baseTypeName);
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

    if (context.lookupType(typeName) != null) {
      // don't allow type names to be redefined
      throw new DoubleDefineException(context, "Type called " + typeName
          + " is already defined");
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
    Variable v = context.declareVariable(vDesc.getType(), vDesc.getName(),
                   VariableStorage.GLOBAL_CONST, DefType.GLOBAL_CONST, null);
    
    
    SwiftAST val = vd.getVarExpr(0);
    assert(val != null);
    
    SwiftType valType = TypeChecker.findSingleExprType(context, val, v.getType());
    if (!valType.equals(v.getType())) {
      throw new TypeMismatchException(context, "trying to assign expression "
          + " of type " + valType.typeName() + " to global constant " 
          + v.getName() + " which has type " + v.getType());
    }
    
    String msg = "Don't support non-literal "
        + "expressions for global constants";
    switch (valType.getPrimitiveType()) {
    case BOOLEAN:
      String bval = Literals.extractBoolLit(context, val);
      if (bval == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.getName(), Oparg.createBoolLit(
                                  Boolean.parseBoolean(bval)));
      break;
    case INTEGER:
      String ival = Literals.extractIntLit(context, val);
      if (ival == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.getName(), Oparg.createIntLit(
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
      backend.addGlobal(v.getName(), Oparg.createFloatLit(fval));
      break;
    case STRING:
      String sval = Literals.extractStringLit(context, val);
      if (sval == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.getName(), Oparg.createStringLit(sval));
      break;
    default:
      throw new STCRuntimeError("Unexpect value tree type in "
          + " global constant: " + ExMParser.tokenNames[val.getType()]);
    }
  }
}
