package exm.stc.frontend;

import java.util.*;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.Types.FunctionType;
import exm.stc.ast.Types.PrimType;
import exm.stc.ast.Types.ReferenceType;
import exm.stc.ast.Types.ScalarUpdateableType;
import exm.stc.ast.Types.StructType;
import exm.stc.ast.Types.SwiftType;
import exm.stc.ast.Types.FunctionType.InArgT;
import exm.stc.ast.Types.StructType.StructField;
import exm.stc.ast.Variable.DefType;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.ast.descriptor.ArrayElems;
import exm.stc.ast.descriptor.ArrayRange;
import exm.stc.ast.descriptor.ForLoopDescriptor;
import exm.stc.ast.descriptor.ForeachLoop;
import exm.stc.ast.descriptor.FunctionDecl;
import exm.stc.ast.descriptor.If;
import exm.stc.ast.descriptor.IterateDescriptor;
import exm.stc.ast.descriptor.LValue;
import exm.stc.ast.descriptor.StringLiteral;
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

  String inputFile;
  TypeChecker typecheck;
  VariableUsageAnalyzer varAnalyzer;
  CompilerBackend backend;
  private LineMapping lineMapping;

  static final Logger logger = Logger.getLogger("");

  public ASTWalker(String inputFile,
      LineMapping lineMapping) {
    this.inputFile = inputFile;
    this.typecheck = new TypeChecker(logger);
    this.varAnalyzer = new VariableUsageAnalyzer(logger, typecheck,
                                                        lineMapping);
    this.lineMapping = lineMapping;
  }


  public void walk(CompilerBackend backend, SwiftAST tree) 
          throws UserException {
    this.backend = backend;
    GlobalContext context = new GlobalContext(inputFile, logger);

    // Dump ANTLR's view of the SwiftAST (unsightly):
    // if (logger.isDebugEnabled())
    // logger.debug("tree: \n" + tree.toStringTree());

    // Use our custom printTree
    if (logger.isDebugEnabled())
      logger.debug(tree.printTree());

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

  


  /*
  private void linemarker(Context context, SwiftAST tree)
  {
    String arg0 = tree.getChild2(0).getText();
    String arg1 = tree.getChild2(1).getText();
    int cppLine = Integer.parseInt(arg0);
    String file = ParserUtils.unquote(arg1);

    // I don't know the point of these lines. -Justin
    if (file.startsWith("<"))
      return;

    logger.trace("linemarker: " + file + ":" + cppLine);
    int antlrLine = tree.getLine();

    context.setInputFile(file);
    context.setLineOffset(antlrLine, cppLine);
  }*/
  


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
      Variable res = evalExprToTmp(context, expr, 
          typecheck.findSingleExprType(context, expr), false, null);
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
    trace(context, "block start");

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

    trace(context, "block done");
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
          fieldVar = structLookup(context, struct, f.getName(),
                    false, root, fieldPath);
        }
        arrays.add(new VarInfoPair(fieldVar, structVInfo.getFieldVInfo(f.getName())));
      } else if (Types.isStruct(f.getType())) {
        VInfo nestedVInfo = structVInfo.getFieldVInfo(f.getName());
        assert(nestedVInfo != null);
        Variable field = context.getStructFieldTmp(root, fieldPath);
        if (field == null) {
          field = structLookup(context, struct, f.getName(),
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
    trace(context, "if...");
    If ifStmt = If.fromAST(context, tree); 
    
    
    // Condition must be boolean and stored to be retrieved later
    Variable conditionVar = evalExprToTmp(context, ifStmt.getCondition(),
        ifStmt.getCondType(context, typecheck), false, null);
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
    Variable condVal = retrieveScalarVal(waitContext, conditionVar);
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
    logger.trace("switch...");    // Evaluate into a temporary variable. Only int supported now
    
    Switch sw = Switch.fromAST(context, tree);
    sw.typeCheck(context, typecheck);
    
    Variable switchVar = evalExprToTmp(context, sw.getSwitchExpr(), 
                              Types.FUTURE_INTEGER, true, null);

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
    Variable switchVal = waitContext.createLocalValueVariable(
                        Types.VALUE_INTEGER, switchVar.getName());
    initialiseVariable(waitContext, switchVal);
    backend.retrieveInt(switchVal, switchVar);

    logger.trace("switch: " + sw.getCaseBodies().size() + " cases");
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
    ForeachLoop loop = ForeachLoop.fromAST(context, tree, typecheck); 
    
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
    range.typeCheck(context, typecheck);
    
    /* Just evaluate all of the expressions into futures and rely
     * on constant folding in IC to clean up where possible
     */ 
    Variable start = evalExprToTmp(context, range.getStart(), 
        Types.FUTURE_INTEGER, false, null);
    Variable end = evalExprToTmp(context, range.getEnd(), 
        Types.FUTURE_INTEGER, false, null);
    Variable step;
    if (range.getStep() != null) {
      step = evalExprToTmp(context, range.getStep(), 
                      Types.FUTURE_INTEGER, false, null);
    } else {
      // Inefficient but constant folding will clean up
      step = createTmp(context, Types.FUTURE_INTEGER, false, false);
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
    Variable startVal = retrieveScalarVal(waitContext, start);
    Variable endVal = retrieveScalarVal(waitContext, end);
    Variable stepVal = retrieveScalarVal(waitContext, step);
    Context bodyContext = loop.setupLoopBodyContext(waitContext, typecheck);
    
    // The per-iteration value of the range
    Variable memberVal = bodyContext.createLocalValueVariable(
        Types.VALUE_INTEGER, loop.getMemberVarName());
    backend.startRangeLoop("range" + loopNum, memberVal, 
            Oparg.createVar(startVal), Oparg.createVar(endVal), 
            Oparg.createVar(stepVal), loop.isSyncLoop(),
            usedVariables, containersToRegister, loop.getDesiredUnroll(),
            loop.getSplitDegree());
    
    // We have the current value, but need to put it in a future in case user
    //  code refers to it
    initialiseVariable(bodyContext, loop.getMemberVar());
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
    Variable arrayVar = evalExprToTmp(context, loop.getArrayVarTree(), 
                    loop.findArrayType(context, typecheck), true, null);

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
      realArray = createTmp(outsideLoopContext,
                              arrayVar.getType().getMemberType(), false, true);
      backend.retrieveRef(realArray, arrayVar);
    } else {
      realArray = arrayVar;
      outsideLoopContext = context;
    }
    loop.setupLoopBodyContext(outsideLoopContext, typecheck);
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
      Variable loopCountVar = createVariable(loop.getBodyContext(),
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
    ForLoopDescriptor forLoop = ForLoopDescriptor.fromAST(typecheck, context, tree);
    
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
              context.declareVariable(lv.var.getType(), 
                  Variable.OUTER_VAR_PREFIX + lv.var.getName(),
                  VariableStorage.ALIAS, DefType.LOCAL_COMPILER,
                  lv.var.getMapping());
        // Copy turbine ID
        backend.makeAlias(parentAlias, lv.var);
        backendDeclare(parentAlias);
        usedVariables.add(parentAlias);
        parentLoopVarAliases.put(lv.var.getName(), parentAlias);
      }
    }
    
    logger.debug("usedVariables in forLoop: " + usedVariables.toString()
        + " at " + forLoop.getBody().getLine() + "." + 
        forLoop.getBody().getCharPositionInLine());
    
    // Create context with loop variables
    Context loopBodyContext = forLoop.createBodyContext(context);
    forLoop.validateCond(loopBodyContext);
    SwiftType condType = typecheck.findSingleExprType(loopBodyContext, 
                                              forLoop.getCondition());

    // Evaluate the conditional expression for the first iteration outside the
    // loop, directly using temp names for loop variables
    HashMap<String, String> initRenames = new HashMap<String, String>();
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      initRenames.put(forLoop.getLoopVars().get(i).var.getName(), 
            initVals.get(i).getName());
    }
    Variable initCond = evalExprToTmp(context, forLoop.getCondition(),
        condType, true, initRenames);
    
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
    Variable condVal = retrieveScalarVal(loopBodyContext, condArg);
    
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
    Variable nextCond = evalExprToTmp(loopBodyContext, forLoop.getCondition(),
                                      condType, true, nextRenames);
    newLoopVars.add(0, nextCond);
    backend.loopContinue(newLoopVars, usedVariables, containersToRegister,
                                                            blockingVector);
    backend.startElseBlock();
    // Terminate loop, clean up open containers and copy out final vals 
    // of loop vars
    for (LoopVar lv: forLoop.getLoopVars()) {
      if (lv.declaredOutsideLoop) {
        copyByValue(loopBodyContext, 
            lv.var, parentLoopVarAliases.get(lv.var.getName()), 
            lv.var.getType());
      }
    }
    
    backend.loopBreak(containersToRegister);
    backend.endIfStatement();
    // finish loop construct
    backend.endLoop();
  }


  
  /**
   * Create a value variable and retrieve value of future into it
   * @param context
   * @param type
   * @param future
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   * @throws DoubleDefineException
   */
  private Variable retrieveScalarVal(Context context, Variable future) 
      throws UserException, UndefinedTypeException, DoubleDefineException {
    assert(Types.isScalarFuture(future.getType()));
    SwiftType futureType = future.getType();
    Variable val = context.createLocalValueVariable(
        Types.derefResultType(futureType), future.getName());
    initialiseVariable(context, val);
    switch (futureType.getPrimitiveType()) {
    case BOOLEAN:
      backend.retrieveBool(val, future);
      break;
    case INTEGER:
      backend.retrieveInt(val, future);
      break;
    case STRING:
      backend.retrieveString(val, future);
      break;
    case FLOAT:
      backend.retrieveFloat(val, future);
      break;
    default:
      throw new STCRuntimeError("Don't know how to retrieve value of "
          + " type " + futureType.typeName() + " for variable " 
          + future.getName());
    }
    
    return val;
  }
  
  private void iterate(Context context, SwiftAST tree) throws UserException {
    IterateDescriptor loop = IterateDescriptor.fromAST(typecheck, context, tree);
    
    
    
    //TODO: this is a little funny since the condition expr might be of type int,
    //    but this will work for time being
    Variable falseV = context.createLocalTmpVariable(Types.FUTURE_BOOLEAN);
    initialiseVariable(context, falseV);
    backend.assignBool(falseV, Oparg.createBoolLit(false));
    
    Variable zero = context.createLocalTmpVariable(Types.FUTURE_INTEGER);
    initialiseVariable(context, zero);
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
    Variable condVal = retrieveScalarVal(bodyContext, condArg); 
    
    backend.startIfStatement(Oparg.createVar(condVal), true);
    if (containersToRegister.size() > 0) {
      backend.loopBreak(containersToRegister);
    }
    backend.startElseBlock();
    block(bodyContext, loop.getBody());
    
    // Check the condition type now that all loop body vars have been declared
    SwiftType condType = typecheck.findSingleExprType(bodyContext,
        loop.getCond());
    if (condType.equals(Types.FUTURE_INTEGER)) {
      // TODO: for now, assume boolean
      throw new STCRuntimeError("don't support non-boolean conditions" +
      		" for iterate yet");
    } else if (!condType.equals(Types.FUTURE_BOOLEAN)) {
      throw new TypeMismatchException(bodyContext, 
          "iterate condition had invalid type: " + condType.typeName());
    }
    
    Variable nextCond = evalExprToTmp(bodyContext, loop.getCond(), 
        condType, false, null);
    Variable nextCounter = bodyContext.createIntermediateVariable(
                                            Types.FUTURE_INTEGER);
    Variable one = bodyContext.createIntermediateVariable(
        Types.FUTURE_INTEGER);
    initialiseVariable(bodyContext, nextCounter);
    initialiseVariable(bodyContext, one);
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
      SwiftType exprType = typecheck.findSingleExprType(context, expr);

      if (!exprType.equals(argType)) {
        throw new STCRuntimeError("haven't implemented conversion " +
            " from " + exprType.typeName() + " to " + argType.typeName() +
            " for loop expressions");
      }
      results.add(evalExprToTmp(context, expr, exprType, false, null));
    }
    return results;
  }



  
  private void declareVariables(Context context, SwiftAST tree,
      VariableUsageInfo blockVu) throws UserException {
    logger.trace("declareVariable...");
    assert(tree.getType() == ExMParser.DECLARATION);
    int count = tree.getChildCount();
    if (count < 2)
      throw new STCRuntimeError("declare_multi: child count < 2");
    VariableDeclaration vd =  VariableDeclaration.fromAST(context, typecheck, 
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
        Double initVal = extractFloatLit(context, initExpr);
        if (initVal == null) {
          String intLit = extractIntLit(context, initExpr);
          if (intLit != null) {
            initVal = interpretIntAsFloat(intLit);
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
        SwiftType mapType = typecheck.findSingleExprType(context, 
                                          vDesc.getMappingExpr());
        if (!Types.isString(mapType)) {
          throw new TypeMismatchException(context, "Tried to map using " +
          		"non-string expression with type " + mapType.typeName());
        }
        mappedVar = evalExprToTmp(context, vDesc.getMappingExpr(),
                Types.FUTURE_STRING, false, null);
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
    Variable var = createVariable(context, internalType, 
        vDesc.getName(), VariableStorage.STACK, DefType.LOCAL_USER, mappedVar);

    // Might need to close if array or struct containing array
    flagDeclaredVarForClosing(context, var, blockVu);
    return var;
  }

  /**
   * @param context
   * @param type
   * @param name
   * @param storage
   * @return
   * @throws UserException
   */
  private Variable createVariable(Context context, SwiftType type, String name,
      VariableStorage storage, DefType defType, Variable mapping)
                                                throws UserException {

    if (mapping != null && (!Types.isMappable(type))) {
      throw new UserException(context, "Variable " + name + " of type "
          + type.toString() + " cannot be mapped to " + mapping);
    }
    Variable v;

    try {
      v = context.declareVariable(type, name, storage, defType, mapping);
    } catch (DoubleDefineException e) {
      throw new DoubleDefineException(context, e.getMessage());
    }
    initialiseVariable(context, v);
    return v;
  }

  private void initialiseVariable(Context context, Variable v)
      throws UndefinedTypeException, DoubleDefineException {
    if (!Types.isStruct(v.getType())) {
      backendDeclare(v);
    } else {
      // Need to handle structs specially because they have lots of nested
      // variables created at declaration time
      initialiseStruct(context, v, v, new Stack<String>());
    }
  }

  private void initialiseStruct(Context context, Variable rootStruct,
              Variable structToInit, Stack<String> path)
      throws UndefinedTypeException, DoubleDefineException {
    assert(Types.isStruct(structToInit.getType()));
    
    backendDeclare(structToInit);
    
    if (structToInit.getStorage() == VariableStorage.ALIAS) {
      // Skip recursive initialisation if its just an alias
      return;
    } else {
      StructType type = (StructType)structToInit.getType();
  
      for (StructField f: type.getFields()) {
        path.push(f.getName());
  
        Variable tmp = context.createStructFieldTmp(
            rootStruct, f.getType(), path, VariableStorage.TEMPORARY);
  
        if (Types.isStruct(f.getType())) {
          // Continue recursive structure initialisation,
          // while keeping track of the full path
          initialiseStruct(context, rootStruct, tmp, path);
        } else {
          initialiseVariable(context, tmp);
        }
        backend.structInsert(structToInit, f.getName(), tmp);
        path.pop();
      }
      backend.structClose(structToInit);
    }
  }

  private void assignIntLit(Context context, SwiftAST tree,
                            Variable oVar, String value)
 throws UserException {
   trace(context, oVar.toString()+"="+value);
   if(Types.isInt(oVar.getType())) {
     backend.assignInt(oVar, Oparg.createIntLit(Long.parseLong(value)));
   } else if (Types.isFloat(oVar.getType())) {
     double floatval = interpretIntAsFloat(value);
     backend.assignFloat(oVar, Oparg.createFloatLit(floatval));
     
   } else {
     assert false : "assignIntLit to variable" + oVar;
   }
  }


  /**
   * Interpret an integer literal as a float literal, warning
   * if this would result in loss of precision
   * @param value an integer literal string
   * @return
   */
  private double interpretIntAsFloat(String value) {
    long longval = Long.parseLong(value);
     // Casts from long to double can lost precision
     double floatval = (double)longval;
     if (longval !=  (long)(floatval)) {
       logger.warn("Conversion of 64-bit integer constant " + longval
           + " to double precision floating point resulted in a loss of"
           + "  precision with result: " + (long)(floatval));
     }
    return floatval;
  }

  private void assignBoolLit(Context context, SwiftAST tree, Variable oVar,
      String value) throws UserException {
   assert(Types.isBool(oVar.getType()));
   backend.assignBool(oVar, Oparg.createBoolLit(Boolean.parseBoolean(value)));
  }

  private void assignFloatLit(Context context, SwiftAST tree, Variable oVar) 
  throws UserException {
   assert(Types.isFloat(oVar.getType()));
   double val = extractFloatLit(context, tree);
   backend.assignFloat(oVar, Oparg.createFloatLit(val));
  }

  private void assignStringLit(Context context, SwiftAST tree, Variable oVar,
      String value) throws UserException {
    assert(Types.isString(oVar.getType()));
    backend.assignString(oVar, Oparg.createStringLit(value));
  }

  private void assignVariable(Context context, Variable oVar,
      Variable src) throws UserException {
    if (Types.isScalarUpdateable(src.getType())) {
      // Create a future alias to the updateable type so that
      // types match
      Variable val = context.createLocalValueVariable(
          ScalarUpdateableType.asScalarValue(src.getType()));
      backendDeclare(val);
      backend.latestValue(val, src);
      /* Create a future with a snapshot of the value of the updateable
       * By making the retrieve and store explicit the optimizer should be
       * able to optimize out the future in many cases
       */
      Variable snapshot = context.createLocalTmpVariable(
          ScalarUpdateableType.asScalarFuture(src.getType()));
      backendDeclare(snapshot);
      if (!src.getType().equals(Types.UPDATEABLE_FLOAT)) {
        throw new STCRuntimeError(src.getType() + " not yet supported");
      }
      backend.assignFloat(snapshot, Oparg.createVar(val));
      src = snapshot;
    }
    
    SwiftType srctype = src.getType();
    SwiftType dsttype = oVar.getType();
    typecheck.checkCopy(context, srctype, dsttype);

    copyByValue(context, src, oVar, srctype);
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
  private void copyByValue(Context context, Variable src, Variable dst,
      SwiftType type) throws UserException {
    if (Types.isScalarFuture(type)) {
      if (type.equals(Types.FUTURE_INTEGER)) {
        backend.builtinFunctionCall(Builtins.COPY_INTEGER, Arrays.asList(src),
                                          Arrays.asList(dst), null);
      } else if (type.equals(Types.FUTURE_STRING)) {
        backend.builtinFunctionCall(Builtins.COPY_STRING, Arrays.asList(src),
            Arrays.asList(dst), null);
      } else if (type.equals(Types.FUTURE_FLOAT)) {
        backend.builtinFunctionCall(Builtins.COPY_FLOAT, Arrays.asList(src),
            Arrays.asList(dst), null);
      } else if (type.equals(Types.FUTURE_BOOLEAN)) {
        backend.builtinFunctionCall(Builtins.COPY_BOOLEAN, Arrays.asList(src),
            Arrays.asList(dst), null);
      } else if (type.equals(Types.FUTURE_BLOB)) {
        backend.builtinFunctionCall(Builtins.COPY_BLOB, Arrays.asList(src),
            Arrays.asList(dst), null);
      } else if (type.equals(Types.FUTURE_VOID)) {
        // Sort of silly, but might be needed
        backend.builtinFunctionCall(Builtins.COPY_VOID, Arrays.asList(src),
            Arrays.asList(dst), null);
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

  private void copyArrayByValue(Context context, Variable dst, Variable src) 
                                                throws UserException {
    assert(dst.getType().equals(src.getType()));
    assert(Types.isArray(src.getType()));
    LocalContext copyContext = new LocalContext(context);
    SwiftType t = src.getType();
    SwiftType memType = Types.getArrayMemberType(t);
    Variable member = copyContext.createAliasVariable(memType);
    Variable ix = copyContext.createLocalValueVariable(Types.VALUE_INTEGER);
    
    List<Variable> modifiedContainers = Arrays.asList(dst);
    backend.startForeachLoop(src, member, ix, true, -1, false, 
        Arrays.asList(src, dst), modifiedContainers);
    backend.arrayInsertImm(member, dst, Oparg.createVar(ix));
    backend.endForeachLoop(true, -1, false, modifiedContainers);
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

  private void assignExpression(Context context, SwiftAST tree)
      throws UserException {
    debug(context, "assignment: ");
    logChildren(context.getLevel(), tree);

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
    
    List<SwiftType> rValTypes = typecheck.findExprType(context, rValExpr,
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
      typecheck.checkAssignment(context, rValType, lValType, targetName);
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
      walkExpr(context, rValExpr, result, null);
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
    trace(context,
          "Evaluating lval " + lval.toString() + " with type " +
              lval.getType(context));
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
        logger.trace("Reduced to lval " + lval.toString() + " with type " +
                      lval.getType(context));
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
      Variable rValVar = createTmp(context, rValType, false, false);
      backend.assignReference(lValVar, rValVar);
      return rValVar;
    } else if (Types.isStructRef(rValType) &&  
                          Types.isStruct(lValVar.getType())) {
      Variable rValVar = createTmp(context, rValType, false, false);
      dereferenceStruct(context, lValVar, rValVar);
      return rValVar;
    } else {
      throw new STCRuntimeError("Don't support assigning an "
          + "expression with type " + rValType.toString() + " to variable "
          + lValVar.toString() + " yet");
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
    Variable rValDerefed = createTmp(context, src.getType().getMemberType(), 
        false, true);
    backend.retrieveRef(rValDerefed, src);
    copyByValue(context, rValDerefed, dst, dst.getType());
    backend.endWaitStatement(new ArrayList<Variable>());
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
          backendDeclare(next);
          backend.structLookup(curr, fieldPath.get(i), next);
      }
      curr = next;
      logger.trace("Lookup " + curr.getName() + "." + fieldPath.get(i));
    }
    LValue newTarget = new LValue(curr,
        lval.indices.subList(structPathLen, lval.indices.size()));
    logger.trace("Transform target " + lval.toString() +
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

        String literal = extractIntLit(context, indexExpr.child(0));
        if (literal != null) {
          long arrIx = Long.parseLong(literal);
          // Add this variable to container
          if (Types.isArray(lvalArr.getType())) {
            mVar = context.createAliasVariable(memberType);
            backendDeclare(mVar);
            backend.arrayCreateNestedImm(mVar, lvalArr, 
                        Oparg.createIntLit(arrIx));
          } else {
            assert(Types.isArrayRef(lvalArr.getType()));
            mVar = context.createAliasVariable(new ReferenceType(memberType));
            backendDeclare(mVar);
            backend.arrayRefCreateNestedImm(mVar, lvalArr, 
                Oparg.createIntLit(arrIx));
          }

        } else {
          // Handle the general case where the index must be computed
          mVar = context.createAliasVariable(new ReferenceType(memberType));
          backendDeclare(mVar);
          Variable indexVar = evalExprToTmp(context, indexExpr.child(0),
              Types.FUTURE_INTEGER, false, null);
          
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
        mVar = context.createLocalTmpVariable(new ReferenceType(memberType));
        backend.declare(mVar.getType(), mVar.getName(), mVar.getStorage(),
            mVar.getDefType(), null);
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

    logger.debug(" Token type: " + ExMParser.tokenNames[rvalExpr.getType()]);
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
        lvalVar = context.createLocalTmpVariable(arrMemberType);
        backendDeclare(lvalVar);
      }
    } else {
      //Rval is a ref, so create a new value of the dereferenced type and
      // rely on the compiler frontend later inserting instruction to
      // copy
      lvalVar = context.createLocalTmpVariable(rvalType);
      backendDeclare(lvalVar);
    }

    // We know what variable the result will go into now
    // Now need to work out the index and generate code to insert the
    // variable into the array

    SwiftAST indexTree = lval.indices.get(0);
    assert (indexTree.getType() == ExMParser.ARRAY_PATH);
    assert (indexTree.getChildCount() == 1);
    SwiftAST indexExpr = indexTree.child(0);

    String literal = extractIntLit(context, indexExpr);
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
      final Variable indexVar = evalExprToTmp(context, indexExpr, 
            Types.FUTURE_INTEGER, false, null);

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


  private void backendDeclare(Variable var) throws UndefinedTypeException {
    backend.declare(var.getType(), var.getName(), 
        var.getStorage(), var.getDefType(), var.getMapping());
  }

  /**
   * Statement that evaluates an expression with no assignment E.g., trace()
   */
  private void exprStatement(Context context, SwiftAST tree) throws UserException {
    assert (tree.getChildCount() == 1);
    SwiftAST expr = tree.child(0);

    List<SwiftType> exprType = typecheck.findExprType(context, expr);

    backend.addComment("Swift l." + context.getLine() + " evaluating "
        + " expression and throwing away " + exprType.size() +
        " results");

    // Need to create throwaway temporaries for return values
    List<Variable> oList = new ArrayList<Variable>();
    for (SwiftType t : exprType) {
      oList.add(createTmp(context, t, false, false));
    }

    walkExpr(context, expr, oList, null);
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
  private void walkExpr(Context context, SwiftAST tree, List<Variable> oList,
          Map<String, String> renames)
      throws UserException {
    String vars = "";
    for (Variable v : oList) {
      vars += " " + v.getName();
    }
    debug(context,
          "walkExpr " + tree.getText() +
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
        assignIntLit(context, tree, oVar, extractIntLit(context, tree));
        break;

      case ExMParser.FLOAT_LITERAL:
        assignFloatLit(context, tree, oVar);
        break;

      case ExMParser.STRING_LITERAL:
        assignStringLit(context, tree, oVar, extractStringLit(context, tree));
        break;

      case ExMParser.BOOL_LITERAL:
        assignBoolLit(context, tree, oVar, extractBoolLit(context, tree));
        break;

      case ExMParser.OPERATOR:
        // Handle unary negation as special case
        String intLit = extractIntLit(context, tree);
        Double floatLit = extractFloatLit(context, tree);
        if (intLit != null) {
          assignIntLit(context, tree, oVar, intLit);
        } else if (floatLit != null ) {
          assignFloatLit(context, tree, oVar);
        } else {
          callOperator(context, tree, oList, renames);
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
   *
   * @param context
   * @param tree
   * @return null if tree isn't a literal, the string otherwise e.g. "2312" or
   *         "-1"
   */
  private String extractIntLit(Context context, SwiftAST tree) {
    // Literals are either represented as a plain non-negative literal,
    // or the unary negation operator applied to a literal
    if (tree.getType() == ExMParser.INT_LITERAL) {
      return tree.child(0).getText();
    } else if (tree.getType() == ExMParser.OPERATOR
        && tree.getChildCount() == 2
        && tree.child(1).getType() == ExMParser.INT_LITERAL) {
      return "-" + tree.child(1).child(0).getText();
    } else {
      return null;
    }
  }

  private String extractBoolLit(Context context, SwiftAST tree) {
    assert(tree.getType() == ExMParser.BOOL_LITERAL);
    assert(tree.getChildCount() == 1);
    return tree.child(0).getText();
  }

  private Double extractFloatLit(Context context, SwiftAST tree) {
    // Literals are either represented as a plain non-negative literal,
    // or the unary negation operator applied to a literal
    SwiftAST litTree;
    boolean negate;
    if (tree.getType() == ExMParser.FLOAT_LITERAL) {
      litTree = tree.child(0);
      negate = false;
    } else if (tree.getType() == ExMParser.OPERATOR
        && tree.getChildCount() == 2
        && tree.child(0).getType() == ExMParser.NEGATE
        && tree.child(1).getType() == ExMParser.FLOAT_LITERAL) {
      litTree = tree.child(1).child(0);
      negate = true;
    } else {
      return null;
    }
    double num;
    if (litTree.getType() == ExMParser.NOTANUMBER) {
      num = Double.NaN;
    } else if (litTree.getType() == ExMParser.INFINITY) {
      num = Double.POSITIVE_INFINITY;
    } else {
      assert(litTree.getType() == ExMParser.DECIMAL);
      num = Double.parseDouble(litTree.getText());
    }
    return negate ? -1.0 * num : num;
  }

  private String extractStringLit(Context context, SwiftAST tree) throws InvalidSyntaxException {
    assert(tree.getType() == ExMParser.STRING_LITERAL);
    assert(tree.getChildCount() == 1);
    // E.g. "hello world\n" with plain escape codes and quotes
    String result = StringLiteral.extractLiteralString(context, tree.child(0));
    logger.trace("Unescaped string '" + tree.child(0).getText() + "', resulting in '"
        + result + "'");
    return result;
  }


  private void callOperator(Context context, SwiftAST tree, 
      List<Variable> oList, Map<String, String> renames) throws UserException {
    String op = tree.child(0).getText();
    int op_argcount = tree.getChildCount() - 1;


    // Use the AST token label to find the actual operator
    String builtin = typecheck.getBuiltInFromOpTree(context, tree);

    FunctionType ftype = Builtins.getBuiltinType(builtin);
    if (ftype == null) {
      throw new STCRuntimeError("unknown builtin function: " + builtin
          + " for operator " + op);
    }
    int argcount = ftype.getInputs().size();

    if (op_argcount != argcount && 
        !(ftype.hasVarargs() && op_argcount >= argcount - 1)) {
      throw new STCRuntimeError("Operator " + op + " has " + op_argcount
          + " arguments in AST, but expected" + argcount);
    }

    ArrayList<Variable> iList = new ArrayList<Variable>(argcount);
    for (int i = 0; i < op_argcount; i++) {
      InArgT argtype = ftype.getInputs().get(Math.min(i, argcount - 1));
      if (argtype.getAlternatives().length != 1) {
        throw new STCRuntimeError("Builtin operator "
            + builtin + " should not have polymorphic type for input " +
            		"argument: " + argtype.toString());
      }
      SwiftType type = argtype.getAlternatives()[0];

      // Store into temporary variables
      Variable arg = evalExprToTmp(context, tree.child(i + 1), type, false,
                                                                  renames);
      iList.add(arg);
    }

    backend.builtinFunctionCall(builtin, iList, oList, null);
  }
  
  private void updateStmt(Context context, SwiftAST tree) 
        throws UserException {
    Update up = Update.fromAST(context, tree);
    SwiftType exprType = up.typecheck(context, typecheck);
    Variable evaled = evalExprToTmp(context, up.getExpr(), exprType, 
                                                        false, null);
    backend.update(up.getTarget(), up.getMode(), evaled);
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
      SwiftType parentType = typecheck.findSingleExprType(context, parentTree);
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
  private Variable structLookup(Context context, Variable structVar,
      String fieldName, boolean storeInStack, Variable rootStruct,
      List<String> fieldPath) throws UserException,
      UndefinedTypeException {
    assert(rootStruct != null);
    assert(fieldPath != null);
    assert(fieldPath.size() > 0);
    SwiftType memType = typecheck.findStructFieldType(context, fieldName,
                                                    structVar.getType());
    Variable tmp;
    if (Types.isStructRef(structVar.getType())) {
      tmp = context.getStructFieldTmp(rootStruct, fieldPath);
      if (tmp == null) {
        tmp = context.createStructFieldTmp(rootStruct, 
            new ReferenceType(memType), 
            fieldPath, VariableStorage.TEMPORARY);

        backendDeclare(tmp);
        backend.structRefLookup(structVar, fieldName, tmp);
      }
    } else {
      assert(Types.isStruct(structVar.getType()));
      tmp = context.getStructFieldTmp(rootStruct, fieldPath);
      if (tmp == null) {
        tmp = context.createStructFieldTmp(rootStruct, 
            memType, fieldPath, VariableStorage.ALIAS);

        backendDeclare(tmp);
        backend.structLookup(structVar, fieldName, tmp);
      }
    }
    return tmp;
    
  }

  private void structLoad(Context context, SwiftAST tree, Variable oVar,
      Map<String, String> renames) throws UserException {
    logger.trace("structLoad l." + context.getLine());
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
    SwiftType arrExprType = typecheck.findSingleExprType(context, arrayTree);

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
    SwiftType indexType = typecheck.findSingleExprType(context, arrayIndexTree);
    if (!indexType.equals(Types.FUTURE_INTEGER)) {
      throw new TypeMismatchException(context,
          "array index expression does not" + "have integer type");
    }

    // The direct result of the array lookup
    Variable lookupIntoVar;
    boolean doDereference;
    if (memberType.equals(oVar.getType())) {
      // Need to dereference into temporary var
      lookupIntoVar = createTmp(context, new ReferenceType(memberType),
          false, false);
      doDereference = true;
    } else {
      assert(Types.isReference(oVar.getType()));
      assert(memberType.equals(oVar.getType().getMemberType()));
      lookupIntoVar = oVar;
      doDereference = false;
    }

    String arrayIndexStr = extractIntLit(context, arrayIndexTree);
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
          Oparg.createIntLit(arrayIndex), isRef);
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
  
  private void arrayRange(Context context, SwiftAST tree, Variable oVar,
      Map<String, String> renames) throws UserException {
    assert(Types.isArray(oVar.getType()));
    assert(Types.isInt(oVar.getType().getMemberType()));
    ArrayRange ar = ArrayRange.fromAST(context, tree);
    ar.typeCheck(context, typecheck);
    
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
      SwiftType arrType = typecheck.findSingleExprType(context, tree);
      assert(Types.isArray(arrType));
      assert(arrType.equals(oVar.getType()));
      
      SwiftType memType = arrType.getMemberType();
      
      /** Evaluate all the members and insert into list */
      for (int i = 0; i < ae.getMemberCount(); i++) {
        SwiftAST mem = ae.getMember(i);
        Variable computedMember = evalExprToTmp(context, mem, 
            memType, false, renames);
        backend.arrayInsertImm(computedMember, oVar, 
                                      Oparg.createIntLit(i));
      }
      // Will need to close this array
      context.flagArrayForClosing(oVar);
  }
  /**
   * Dereference src into dst
   * ie. dst = *src
   * @param oVar
   * @param lookupIntoVar
   * @throws UserException 
   * @throws UndefinedTypeException 
   */
  private void dereference(Context context, Variable dst, Variable src) 
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
      } else {
        throw new STCRuntimeError("Don't know how to dereference "
            + " type " + src.getType().toString());
      }
    } else if (Types.isArray(dst.getType())) {
      assert(dst.getStorage() == VariableStorage.ALIAS);
      backend.retrieveRef(dst, src);
    } else if (Types.isStruct(dst.getType())) {
      dereferenceStruct(context, dst, src);
    } else {
      throw new STCRuntimeError("Can't dereference type "
         + src.getType().toString());
    }
  }

  /**
   * Generate code for a call to a function, where the arguments might be
   * expressions
   *
   * @param context
   * @param tree
   * @param oList
   * @return
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
      if (Builtins.isAssertVariant(f) &&
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
    
    // evaluate argument expressions left to right, creating temporaries
    ArrayList<Variable> argVars = new ArrayList<Variable>(
            arglist.getChildCount());
    int argcount = arglist.getChildCount();
    for (int i = 0; i < argcount; i++) {
      SwiftAST argtree = arglist.child(i);
      InArgT expType = ftype.getInputs().get(Math.min(i, ftype.getInputs().size() - 1));
      
      
      SwiftType argtype;
      if (expType.getAltCount() == 1) {
        argtype = typecheck.findSingleExprType(context, argtree, expType.getAlt(0));
      } else {
        argtype = typecheck.findSingleExprType(context, argtree);
        SwiftType matching = typecheck.whichAlternativeType(expType, argtype);
        if (matching != null && Types.isUpdateableEquiv(argtype, matching)) {
          // Try to coerce
          argtree.clearTypeInfo();
          argtype = typecheck.findSingleExprType(context, argtree, matching);
        }
      }

      argVars.add(evalExprToTmp(context, argtree, argtype, false, renames));
    }
    
    // Process priority after arguments have been evaluated, so that
    // the argument evaluation is outside the wait statement
    Variable priorityVal = null;
    boolean openedWait = false;
    List<Variable> waitContainers = null;
    Context callContext = context;
    if (tree.getChildCount() == 3) {
      SwiftAST priorityT = tree.child(2);
      Variable priorityFuture = evalExprToTmp(context, priorityT,
                            Types.FUTURE_INTEGER, false, renames);
      waitContainers = new ArrayList<Variable>(0); // TODO: Do we need these?
      //TODO: used variables: any input or output args
      ArrayList<Variable> usedVariables = new ArrayList<Variable>();
      usedVariables.addAll(argVars);
      usedVariables.addAll(oList);
      
      backend.startWaitStatement(context.getFunctionContext().constructName("priority-wait"), 
                        Arrays.asList(priorityFuture), usedVariables, waitContainers, false);
      openedWait = true;
      callContext = new LocalContext(context);
      priorityVal = retrieveScalarVal(callContext, priorityFuture);
      
    }
    
    // callFunction will check that argument types match function
    callFunction(context, f, oList, argVars, priorityVal);
    if (openedWait) {
      backend.endWaitStatement(waitContainers);
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

  private Variable evalExprToTmp(Context context, SwiftAST tree, SwiftType type,
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
                typecheck.findSingleExprType(context, tree.child(0)))) {
      return lookupStructField(context, tree, type, storeInStack, null, 
                                                               renames);
    } else {
      Variable tmp = createTmp(context, type, storeInStack, false);
      ArrayList<Variable> childOList = new ArrayList<Variable>(1);
      childOList.add(tmp);
      walkExpr(context, tree, childOList, renames);
      return tmp;
    }
  }

  private Variable createTmp(Context context, SwiftType type,
      boolean storeInStack, boolean useAlias) throws UserException,
      UndefinedTypeException {
    Variable tmp;
    if ((!storeInStack) && useAlias) {
      tmp = context.createAliasVariable(type);
    } else if (storeInStack) {
      tmp = context.createIntermediateVariable(type);
    } else {
      tmp = context.createLocalTmpVariable(type);
    }

    initialiseVariable(context, tmp);
    return tmp;
  }

  private boolean immediateArrayLoadPossible(SwiftAST tree) {
    assert (tree.getType() == ExMParser.ARRAY_LOAD);
    // TODO later this would detect if we can immediately look up array value
    // at this point in the procedure
    // For the moment, disable this check
    return false;
  }

  private void callFunction(Context context, String function,
      List<Variable> oList, List<Variable> iList, Variable priorityVal)
      throws UndefinedTypeException, UserException {
    context.checkDefinedVariables(iList);

    List<SwiftType> expectedTypes = typecheck.checkFunctionCall(context,
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
        Variable copy = context.createLocalTmpVariable(
                  ScalarUpdateableType.asScalarFuture(inputType));
        initialiseVariable(waitContext, copy);
        assignVariable(context, copy, input);
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
        backendDeclare(derefVar);
        if (Types.isArrayRef(waitVars.get(i).getType())) {
          backend.retrieveRef(derefVar, waitVars.get(i));
        } else {
          throw new STCRuntimeError("Don't know how to " +
          		"deref non-array function arg " + derefVar);
        }
      }
    }


    Oparg priority = priorityVal != null ? Oparg.createVar(priorityVal) : null;
    if (context.isBuiltinFunction(function))
      backend.builtinFunctionCall(function, realIList, oList, priority);
    else if (context.isCompositeFunction(function))
      backend.compositeFunctionCall(function, realIList, oList, null, 
          !context.isSyncComposite(function), priority);
    else
      throw UndefinedFunctionException.unknownFunction(context, function);

    if (waitContext != null) {
      backend.endWaitStatement(new ArrayList<Variable>());
    }
  }




  
 

  private void defineBuiltinFunction(Context context, SwiftAST tree)
  throws UserException
  {
    String function  = tree.child(0).getText();
    SwiftAST outputs = tree.child(1);
    SwiftAST inputs  = tree.child(2);
    assert(inputs.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    assert(outputs.getType() == ExMParser.FORMAL_ARGUMENT_LIST);
    String pkg     = StringLiteral.extractLiteralString(context, tree.child(3));
    String version = StringLiteral.extractLiteralString(context,
                                                        tree.child(4));
    String symbol  = StringLiteral.extractLiteralString(context, tree.child(5));
    
    FunctionDecl fdecl = FunctionDecl.fromAST(context, 
                                        inputs, outputs);

    FunctionType ft = fdecl.getFunctionType();
    logger.debug("builtin: " + function + " " + ft);
    
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
    logger.debug("define function: " + context.getLocation() + function);
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
    logger.debug("compile function: starting: " + function );
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

    logger.debug("compile function: done: " + function);
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
    info(context.getLevel(), "defineAppFunction");
    // logChildren(tree);

    tree.printTree();

    String function = tree.child(0).getText();
    logger.trace("function: " + function);
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
    logger.debug("defineAppFunction done");
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
    logger.debug("Defined new type called " + typeName + ": "
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
    
    VariableDeclaration vd = VariableDeclaration.fromAST(context, typecheck,
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
    
    SwiftType valType = typecheck.findSingleExprType(context, val, v.getType());
    if (!valType.equals(v.getType())) {
      throw new TypeMismatchException(context, "trying to assign expression "
          + " of type " + valType.typeName() + " to global constant " 
          + v.getName() + " which has type " + v.getType());
    }
    
    String msg = "Don't support non-literal "
        + "expressions for global constants";
    switch (valType.getPrimitiveType()) {
    case BOOLEAN:
      String bval = extractBoolLit(context, val);
      if (bval == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.getName(), Oparg.createBoolLit(
                                  Boolean.parseBoolean(bval)));
      break;
    case INTEGER:
      String ival = extractIntLit(context, val);
      if (ival == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(v.getName(), Oparg.createIntLit(
                                      Long.parseLong(ival)));
      break;
    case FLOAT:
      Double fval = extractFloatLit(context, val);
      if (fval == null) {
        String sfval = extractIntLit(context, val); 
        if (sfval == null) {
          throw new UserException(context, msg);
        } else {
          fval = interpretIntAsFloat(sfval);
        }
      }
      assert(fval != null);
      backend.addGlobal(v.getName(), Oparg.createFloatLit(fval));
      break;
    case STRING:
      String sval = extractStringLit(context, val);
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


  void logChildren(int indent, SwiftAST tree) {
    for (int i = 0; i < tree.getChildCount(); i++) {
      trace(indent+2, tree.child(i).getText());
    }
  }

  void info(Context context, String msg) {
    log(context.getLevel(), Level.INFO, context.getLocation(), msg);
  }

  void debug(Context context, String msg) {
    log(context.getLevel(), Level.DEBUG, context.getLocation(), msg);
  }

  void trace(Context context, String msg) {
    log(context.getLevel(), Level.TRACE, context.getLocation(), msg);
  }

  /**
     INFO-level with indentation for nice output
   */
  void info(int indent, String msg) {
    log(indent, Level.INFO, msg);
  }

  /**
     DEBUG-level with indentation for nice output
   */
  void debug(int indent, String msg) {
    log(indent, Level.DEBUG, msg);
  }

  /**
     TRACE-level with indentation for nice output
   */
  void trace(int indent, String msg) {
    log(indent, Level.TRACE, msg);
  }

  void log(int indent, Level level, String location, String msg) {
    StringBuilder sb = new StringBuilder(256);
    sb.append(location);
    for (int i = 0; i < indent; i++)
      sb.append(' ');
    sb.append(msg);
    logger.log(level, sb.toString());
  }

  void log(int indent, Level level, String msg) {
    StringBuilder sb = new StringBuilder(256);
    for (int i = 0; i < indent; i++)
      sb.append(' ');
    sb.append(msg);
    logger.log(level, sb);
  }
}
