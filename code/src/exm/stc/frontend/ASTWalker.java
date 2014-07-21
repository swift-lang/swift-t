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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Logging;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.InvalidConstructException;
import exm.stc.common.exceptions.ModuleLoadException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.exceptions.UndefinedPragmaException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.exceptions.VariableUsageException;
import exm.stc.common.lang.Annotations;
import exm.stc.common.lang.Annotations.Suppression;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.Checkpointing;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.ForeignFunctions.SpecialFunction;
import exm.stc.common.lang.Intrinsics.IntrinsicFunction;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayType;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.lang.Types.FileKind;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.NestedContainerInfo;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.SubType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.Out;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StringUtil;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.frontend.Context.FnProp;
import exm.stc.frontend.LValWalker.LRVals;
import exm.stc.frontend.LoadedModules.LocatedModule;
import exm.stc.frontend.VariableUsageInfo.VInfo;
import exm.stc.frontend.tree.ArrayRange;
import exm.stc.frontend.tree.Assignment;
import exm.stc.frontend.tree.Assignment.AssignOp;
import exm.stc.frontend.tree.ForLoopDescriptor;
import exm.stc.frontend.tree.ForLoopDescriptor.LoopVar;
import exm.stc.frontend.tree.ForeachLoop;
import exm.stc.frontend.tree.FunctionDecl;
import exm.stc.frontend.tree.If;
import exm.stc.frontend.tree.IterateDescriptor;
import exm.stc.frontend.tree.LValue;
import exm.stc.frontend.tree.Literals;
import exm.stc.frontend.tree.Switch;
import exm.stc.frontend.tree.TypeTree;
import exm.stc.frontend.tree.Update;
import exm.stc.frontend.tree.VariableDeclaration;
import exm.stc.frontend.tree.VariableDeclaration.VariableDescriptor;
import exm.stc.frontend.tree.Wait;
import exm.stc.ic.STCMiddleEnd;
import exm.stc.tclbackend.TclFunRef;
import exm.stc.tclbackend.TclOpTemplate;
import exm.stc.tclbackend.TclPackage;
/**
 * This class walks the Swift AST.
 * It performs typechecking and dataflow analysis as it goes
 *
 */
public class ASTWalker {

  private final STCMiddleEnd backend;
  private final VarCreator varCreator;
  private final LValWalker lValWalker;
  private final ExprWalker exprWalker;
  private final VariableUsageAnalyzer varAnalyzer;
  private final WrapperGen wrapper;

  /** Track which modules are loaded and compiled */
  private LoadedModules modules = null;

  private static enum FrontendPass {
    DEFINITIONS, // Process top level defs
    COMPILE,     // Compile functions
  }

  public ASTWalker(STCMiddleEnd backend) {
    this.backend = backend;
    this.modules = new LoadedModules();
    this.varCreator = new VarCreator(backend);
    this.wrapper = new WrapperGen(backend);
    this.exprWalker = new ExprWalker(wrapper, varCreator, backend, modules);
    this.lValWalker = new LValWalker(backend, varCreator, exprWalker, modules);
    this.varAnalyzer = new VariableUsageAnalyzer();
  }

  /**
   * Walk the AST and make calls to backend to generate lower level code.
   * This function is called to start the walk at the top level file
   * @param mainFilePath the main file path to process
   * @param originalMainFilePath original main file, in case a temporary file
   *                             is being directly parsed
   * @param preprocessed true if module was preprocessed
   * @throws UserException
   */
  public void walk(String mainFilePath, String originalMainFilePath,
                 boolean preprocessed) throws UserException {

    GlobalContext context = new GlobalContext(mainFilePath,
                                              Logging.getSTCLogger());

    // Assume root module for now
    String mainModuleName =  FilenameUtils.getBaseName(originalMainFilePath);
    LocatedModule mainModule = new LocatedModule(mainFilePath, mainModuleName,
                                                 preprocessed);
    LocatedModule builtins = LocatedModule.fromPath(context,
                          Arrays.asList("builtins"), false);

    // Two passes: first to find definitions, second to compile functions
    loadModule(context, FrontendPass.DEFINITIONS, builtins);
    loadModule(context, FrontendPass.DEFINITIONS, mainModule);
    for (LocatedModule loadedModule: modules.loadedModules()) {
      loadModule(context, FrontendPass.COMPILE, loadedModule);
    }

    FunctionType fn = context.lookupFunction(Constants.MAIN_FUNCTION);
    if (fn == null ||
        !context.hasFunctionProp(Constants.MAIN_FUNCTION, FnProp.COMPOSITE)) {
      throw new UndefinedFunctionException(context,
          "No composite main function was defined in the script");
    }
  }


  /**
   * Walk the statements in a file.
   * @param context
   * @param module the parsed file to compile
   * @param pass controls whether we just load top-level defs, or whether
   *          we attempt to compile the module
   * @throws UserException
   */
  private void walkFile(GlobalContext context, LocatedModule module,
      ParsedModule parsed, FrontendPass pass) throws UserException {
    LogHelper.debug(context, "Entered module " + module.canonicalName
               + " on pass " + pass);
    boolean compiling = (pass == FrontendPass.COMPILE);
    modules.enterModule(module, parsed, compiling);
    walkTopLevel(context, parsed.ast, pass);
    modules.exitModule();
    LogHelper.debug(context, "Finishing module" + module.canonicalName
               + " for pass " + pass);
  }

  /**
   * Get the line map for the current file
   */
  private LineMapping lineMap() {
    return modules.currLineMap();
  }

  /**
   * Synchronize file position to line mapping
   * @param context
   * @param tree
   */
  private void syncFilePos(Context context, SwiftAST tree) {
    context.syncFilePos(tree, modules.currentModule().moduleName(), lineMap());
  }

  private void walkTopLevel(GlobalContext context, SwiftAST fileTree,
      FrontendPass pass) throws UserException {
    if (pass == FrontendPass.DEFINITIONS) {
      walkTopLevelDefs(context, fileTree);
    } else {
      assert(pass == FrontendPass.COMPILE);
      walkTopLevelCompile(context, fileTree);
    }
  }

  /**
   * First pass:
   *  - Register (but don't compile) all functions and other definitions
   * @param context
   * @param fileTree
   * @throws UserException
   * @throws DoubleDefineException
   * @throws UndefinedTypeException
   */
  private void walkTopLevelDefs(GlobalContext context, SwiftAST fileTree)
      throws UserException, DoubleDefineException, UndefinedTypeException {
    assert(fileTree.getType() == ExMParser.PROGRAM);
    syncFilePos(context, fileTree);

    for (SwiftAST topLevelDefn: fileTree.children()) {
      int type = topLevelDefn.getType();
      syncFilePos(context, topLevelDefn);
      switch (type) {
      case ExMParser.IMPORT:
        importModule(context, topLevelDefn, FrontendPass.DEFINITIONS);
        break;

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

      case ExMParser.PRAGMA:
        pragmaTopLevel(context, topLevelDefn);
        break;

      case ExMParser.EOF:
        // Do nothing
        break;

      default:
        String name = LogHelper.tokName(type);
        if (isTopLevelStatement(type)) {
          throw new InvalidConstructException(context,
              "Statement type not yet supported at program top level: "
                  + name.toLowerCase());
        }
        throw new STCRuntimeError("Unexpected token: " + name
            + " at program top level");
      }
    }
  }


  /**
   * Second pass:
   *  - Compile composite and app functions, now that all function names are known
   * @param context
   * @param fileTree
   * @throws UserException
   */
  private void walkTopLevelCompile(GlobalContext context, SwiftAST fileTree)
      throws UserException {
    assert(fileTree.getType() == ExMParser.PROGRAM);
    syncFilePos(context, fileTree);
    // Second pass to compile functions
    for (int i = 0; i < fileTree.getChildCount(); i++) {
      SwiftAST topLevelDefn = fileTree.child(i);
      syncFilePos(context, topLevelDefn);
      int type = topLevelDefn.getType();
      switch (type) {
      case ExMParser.IMPORT:
        // Don't recurse: we invoke compilation of modules elsewhere
        break;

      case ExMParser.DEFINE_FUNCTION:
        compileFunction(context, topLevelDefn);
        break;

      case ExMParser.DEFINE_APP_FUNCTION:
        compileAppFunction(context, topLevelDefn);
        break;

      default:
        String name = LogHelper.tokName(type);
        if (isTopLevelStatement(type)) {
          throw new InvalidConstructException(context,
              "Statement type not yet supported at program top level: "
                  + name.toLowerCase());
        }
      }
    }
  }

  /**
   * @param token a AST token type
   * @return true if token is a statement token type that is syntactically
   *        valid at top level of program but isn't yet supported
   */
  private boolean isTopLevelStatement(int token) {
    switch (token) {
      case ExMParser.BLOCK:
      case ExMParser.IF_STATEMENT:
      case ExMParser.SWITCH_STATEMENT:
      case ExMParser.DECLARATION:
      case ExMParser.ASSIGN_EXPRESSION:
      case ExMParser.EXPR_STMT:
      case ExMParser.FOREACH_LOOP:
      case ExMParser.FOR_LOOP:
      case ExMParser.ITERATE:
      case ExMParser.WAIT_STATEMENT:
      case ExMParser.WAIT_DEEP_STATEMENT:
      case ExMParser.UPDATE:
      case ExMParser.STATEMENT_CHAIN:
        return true;
      default:
        return false;
    }
  }

  /**
   * Handle an import statement by loading definitions for, or compiling
   * module as needed.
   * @param context
   * @param tree
   * @param pass
   * @throws UserException
   */
  private void importModule(GlobalContext context, SwiftAST tree,
      FrontendPass pass) throws UserException {
    assert(tree.getType() == ExMParser.IMPORT);
    assert(tree.getChildCount() == 1);
    SwiftAST moduleID = tree.child(0);

    LocatedModule module = LocatedModule.fromModuleNameAST(context,
                                                  moduleID, false);
    loadModule(context, pass, module);
  }

  /**
   * Compile or load definitions for a module (depending on pass), if needed.
   * Avoids double-compiling or double loading a module.
   * @param context
   * @param pass
   * @param module
   * @throws ModuleLoadException
   * @throws UserException
   */
  private void loadModule(GlobalContext context, FrontendPass pass,
      LocatedModule module) throws ModuleLoadException, UserException {
    Pair<ParsedModule, Boolean> loaded = modules.loadIfNeeded(context, module);
    ParsedModule parsed = loaded.val1;
    boolean newlyLoaded = loaded.val2;

    // Now file is parsed, we decide how to handle import
    if (pass == FrontendPass.DEFINITIONS) {
      // Don't reload definitions
      if (newlyLoaded) {
        // Use our custom printTree
        if (LogHelper.isDebugEnabled()) {
          LogHelper.debug(context, "Loading new module " +
                                      module.canonicalName);
          LogHelper.debug(context, parsed.ast.printTree());
        }


        walkFile(context, module, parsed, pass);
      }
    } else {
      assert(pass == FrontendPass.COMPILE);
      // Should have been loaded at defs stage
      assert(!newlyLoaded);
      // Check if already being compiled
      if (!modules.wasCompiled(module)) {
        walkFile(context, module, parsed, pass);
      }
    }
  }

  private void pragmaTopLevel(GlobalContext context, SwiftAST pragmaT)
                                    throws UserException {
    assert(pragmaT.getType() == ExMParser.PRAGMA);
    assert(pragmaT.childCount() >= 1) : pragmaT.childCount();
    SwiftAST pragmaNameT = pragmaT.child(0);
    List<SwiftAST> pragmaArgs = pragmaT.children(1);

    assert(pragmaNameT.getType() == ExMParser.ID);
    String pragmaName = pragmaNameT.getText();

    if (pragmaName.equals("worktypedef")) {
      workTypeDef(context, pragmaArgs);
    } else {
      throw new UndefinedPragmaException(context, "Invalid pragma name: "
                                                      + pragmaName);
    }
  }

  /**
   * Define a new work type
   * @param context
   * @param args args for pragma
   * @throws UserException
   */
  private void workTypeDef(GlobalContext context, List<SwiftAST> args)
                          throws UserException {
    if (args.size() != 1) {
      throw new UserException(context, "Expected worktypedef pragma to "
                            + "have 1 argument, but got " + args.size());
    }
    SwiftAST workTypeT = args.get(0);
    if (workTypeT.getType() != ExMParser.VARIABLE) {
      throw new UserException(context, "Expected worktypedef pragma to "
                                  + "have identifier name as argument");
    }
    String workTypeName = workTypeT.child(0).getText();
    ExecContext.WorkContext workCx = context.declareWorkType(workTypeName);
    backend.declareWorkType(workCx);
  }

  /**
   * Walk a tree that is a procedure statement.
   *
   * @param context
   * @param tree
   * @param walkMode mode to evaluate statements in
   * @param blockVu
   * @return "results" of statement that are blocked on in event
   *         of chaining
   * @throws UserException
   */
  private List<Var> walkStatement(Context context, SwiftAST tree, WalkMode walkMode)
  throws UserException {
      int token = tree.getType();
      syncFilePos(context, tree);


      if (walkMode == WalkMode.ONLY_DECLARATIONS) {
        if (token == ExMParser.DECLARATION){
          return declareVariables(context, tree, walkMode);
        } else if (token == ExMParser.ASSIGN_EXPRESSION) {
          return assignExpression(context, tree, walkMode);
        } else {
          // Don't process non-variable-declaration statements
          return null;
        }
      }

      switch (token) {
        case ExMParser.BLOCK:
          // Create a local context (stack frame) for this nested block
          LocalContext nestedContext = new LocalContext(context);
          // Set up nested stack frame

          backend.startNestedBlock();
          block(nestedContext, tree);
          backend.endNestedBlock();
          break;

        case ExMParser.IF_STATEMENT:
          ifStatement(context, tree);
          break;

        case ExMParser.SWITCH_STATEMENT:
          switchStatement(context, tree);
          break;

        case ExMParser.DECLARATION:
          return declareVariables(context, tree, walkMode);

        case ExMParser.ASSIGN_EXPRESSION:
          return assignExpression(context, tree, walkMode);

        case ExMParser.EXPR_STMT:
          return exprStatement(context, tree);

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
        case ExMParser.WAIT_DEEP_STATEMENT:
          waitStmt(context, tree);
          break;

        case ExMParser.UPDATE:
          updateStmt(context, tree);
          break;

        case ExMParser.STATEMENT_CHAIN:
          stmtChain(context, tree);
          break;

        case ExMParser.IMPORT:
          throw new InvalidConstructException(context, "Import statements"
                  + " are only allowed at top level of program");

        case ExMParser.DEFINE_BUILTIN_FUNCTION:
        case ExMParser.DEFINE_FUNCTION:
        case ExMParser.DEFINE_APP_FUNCTION:
          throw new InvalidConstructException(context, "Function definitions"
              + " are only allowed at top level of program");

        case ExMParser.DEFINE_NEW_STRUCT_TYPE:
        case ExMParser.DEFINE_NEW_TYPE:
        case ExMParser.TYPEDEF:
          throw new InvalidConstructException(context, "Type definitions"
              + " are only allowed at top level of program");

        case ExMParser.GLOBAL_CONST:
          throw new InvalidConstructException(context, "Global constant"
              + " definitions are only allowed at top level of program");

        case ExMParser.PRAGMA:
          throw new InvalidConstructException(context, "No pragmas"
              + " are valid within functions");

        default:
          throw new STCRuntimeError
          ("Unexpected token type for statement: " +
              LogHelper.tokName(token));
      }
      // default is that statement has no output results
      return null;
  }

  private void stmtChain(Context context, SwiftAST tree) throws UserException {
    assert(tree.getType() == ExMParser.STATEMENT_CHAIN);

    // Evaluate multiple chainings iteratively

    // list of statements being waited on
    List<SwiftAST> stmts = new ArrayList<SwiftAST>();
    while (tree.getType() == ExMParser.STATEMENT_CHAIN) {
      assert(tree.getChildCount() == 2);
      stmts.add(tree.child(0));
      tree = tree.child(1);
    }

    // final statement in chain
    SwiftAST finalStmt = tree;
    // result futures of last statement
    List<Var> stmtResults = null;

    // Process declarations for outer block
    for (SwiftAST stmt: stmts) {
      walkStatement(context, stmt, WalkMode.ONLY_DECLARATIONS);
    }
    walkStatement(context, finalStmt, WalkMode.ONLY_DECLARATIONS);

    // Evaluate statements into nested waits
    for (SwiftAST stmt: stmts) {
      stmtResults = walkStatement(context, stmt, WalkMode.ONLY_EVALUATION);
      if (stmtResults == null || stmtResults.isEmpty()) {
        syncFilePos(context, stmt);
        throw new UserException(context, "Tried to wait for result"
            + " of statement of type " + LogHelper.tokName(stmt.getType())
            + " but statement doesn't have output future to wait on");
      }

      String waitName = context.getFunctionContext().constructName("chain");
      backend.startWaitStatement(waitName, VarRepr.backendVars(stmtResults),
             WaitMode.WAIT_ONLY, true, false, ExecTarget.nonDispatchedAny());
    }

    // Evaluate the final statement
    walkStatement(context, finalStmt, WalkMode.ONLY_EVALUATION);

    // Close all waits
    for (int i = 0; i < stmts.size(); i++) {
      backend.endWaitStatement();
    }
  }


  private void waitStmt(Context context, SwiftAST tree)
                                  throws UserException {
    Wait wait = Wait.fromAST(context, tree);
    ArrayList<Var> waitEvaled = new ArrayList<Var>();
    for (SwiftAST expr: wait.getWaitExprs()) {
      Type waitExprType = TypeChecker.findSingleExprType(context, expr);
      if (Types.isUnion(waitExprType)) {
        // Choose first alternative type
        for (Type alt: UnionType.getAlternatives(waitExprType)) {
          if (Types.canWaitForFinalize(alt)) {
            waitExprType = alt;
            break;
          }
        }
      }
      if (!Types.canWaitForFinalize(waitExprType)) {
        throw new TypeMismatchException(context, "Waiting for type " +
            waitExprType.typeName() + " is not supported");
      }
      Var res = exprWalker.eval(context, expr, waitExprType, false, null);
      waitEvaled.add(res);
    }

    ArrayList<Var> keepOpenVars = new ArrayList<Var>();
    summariseBranchVariableUsage(context,
          Arrays.asList(wait.getBlock().getVariableUsage()), keepOpenVars);


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
          context.getFunctionContext().constructName("explicit-wait"),
          VarRepr.backendVars(waitEvaled),
          WaitMode.WAIT_ONLY, true, wait.isDeepWait(), ExecTarget.nonDispatchedControl());
    block(new LocalContext(context), wait.getBlock());
    backend.endWaitStatement();
  }

  /**
   * block operates on a BLOCK node of the AST. This should be called for every
   * logical code block (e.g. function bodies, condition bodies, etc) in the
   * program
   *
   * @param context a new context for this block
   */
  private void block(Context context, SwiftAST tree) throws UserException {
    LogHelper.trace(context, "block start");

    if (tree.getType() != ExMParser.BLOCK) {
      throw new STCRuntimeError("Expected to find BLOCK token" + " at "
          + tree.getLine() + ":" + tree.getCharPositionInLine());
    }

    for (SwiftAST stmt: tree.children()) {
      walkStatement(context, stmt, WalkMode.NORMAL);
    }

    LogHelper.trace(context, "block done");
  }

  private void ifStatement(Context context, SwiftAST tree)
      throws UserException {
    LogHelper.trace(context, "if...");
    If ifStmt = If.fromAST(context, tree);


    // Condition must be boolean and stored to be fetched later
    Var conditionVar = exprWalker.eval(context,
        ifStmt.getCondition(), ifStmt.getCondType(context),
        false, null);
    assert (conditionVar != null);
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

    FunctionContext fc = context.getFunctionContext();
    backend.startWaitStatement( fc.constructName("if"),
                VarRepr.backendVar(conditionVar).asList(),
                WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedControl());

    Context waitContext = new LocalContext(context);
    Var condVal = exprWalker.retrieveToVar(waitContext, conditionVar);
    backend.startIfStatement(VarRepr.backendArg(condVal),
                             ifStmt.hasElse());
    block(new LocalContext(waitContext), ifStmt.getThenBlock());

    if (ifStmt.hasElse()) {
      backend.startElseBlock();
      block(new LocalContext(waitContext), ifStmt.getElseBlock());
    }
    backend.endIfStatement();
    backend.endWaitStatement();
  }

  /**
   * Check for deadlocks of the form:
   * if (a) {
   *   a = 3;
   * } else {
   *   a = 2;
   * }
   * We should not allow any code to be compiled in which a variable is inside
   * a conditional statement for each is is the condition.
   * This is a very limited form of deadlock detection.  In
   *  general we need to check the full variable dependency chain to make
   *  sure that the variable in the conditional statement isn't dependent
   *  at all on anything inside the condition
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
   * @param writtenVars
   *          All vars that might be written are added here
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private void summariseBranchVariableUsage(Context context,
      List<VariableUsageInfo> branchVUs, List<Var> writtenVars)
          throws UndefinedTypeException, UserException {
    for (Var v : context.getVisibleVariables()) {
      // see if it is an array that might be modified
      if (Types.isArray(v.type())) {
        for (VariableUsageInfo bvu : branchVUs) {
          VInfo vi = bvu.lookupVariableInfo(v.name());
          if (vi != null && vi.isAssigned() != Ternary.FALSE) {
            writtenVars.add(v);
            break;
          }
        }
      } else if (Types.isStruct(v.type())) {
        // Need to find arrays inside structs
        ArrayList<Pair<Var, VInfo>> arrs = new ArrayList<Pair<Var, VInfo>>();
        // This procedure might add the same array multiple times,
        // so use a set to avoid duplicates
        HashSet<Var> alreadyFound = new HashSet<Var>();
        for (VariableUsageInfo bvu : branchVUs) {
          arrs.clear();
          exprWalker.findArraysInStruct(context, v,
              bvu.lookupVariableInfo(v.name()), arrs);
          for (Pair<Var, VInfo> p: arrs) {
            if (p.val2.isAssigned() != Ternary.FALSE) {
              alreadyFound.add(p.val1);
            }
          }
        }
        writtenVars.addAll(alreadyFound);
      }
    }

  }

  private void switchStatement(Context context, SwiftAST tree)
       throws UserException {
    LogHelper.trace(context, "switch...");

    // Evaluate into a temporary variable. Only int supported now

    Switch sw = Switch.fromAST(context, tree);
    sw.typeCheck(context);

    Var switchVar = exprWalker.eval(context, sw.getSwitchExpr(), Types.F_INT,
                                    true, null);

    List<VariableUsageInfo> branchVUs = new ArrayList<VariableUsageInfo>();
    for (SwiftAST b : sw.getCaseBodies()) {
      branchVUs.add(b.checkedGetVariableUsage());
    }

    checkConditionalDeadlock(context, switchVar, branchVUs);

    // Generate all of the code
    FunctionContext fc = context.getFunctionContext();
    backend.startWaitStatement( fc.constructName("switch"),
                VarRepr.backendVar(switchVar).asList(),
                WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedControl());

    Context waitContext = new LocalContext(context);
    Var switchVal = varCreator.createValueOfVar(waitContext,
                                                     switchVar);

    exprWalker.retrieve(switchVal, switchVar);

    LogHelper.trace(context, "switch: " +
            sw.getCaseBodies().size() + " cases");
    backend.startSwitch(VarRepr.backendArg(switchVal), sw.getCaseLabels(),
                                                         sw.hasDefault());
    for (SwiftAST caseBody : sw.getCaseBodies()) {
      block(new LocalContext(waitContext), caseBody);
      backend.endCase();
    }
    backend.endSwitch();
    backend.endWaitStatement();
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
      step = exprWalker.assignToVar(context, Arg.ONE, false);
    }
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("foreach-range");

    // Need to pass in futures along with user vars
    List<Var> rangeBounds = Arrays.asList(start, end, step);
    backend.startWaitStatement(fc.getFunctionName() + "-wait-range" + loopNum,
             VarRepr.backendVars(rangeBounds), WaitMode.WAIT_ONLY, false,
             false, ExecTarget.nonDispatchedControl());
    Context waitContext = new LocalContext(context);
    Var startVal = exprWalker.retrieveToVar(waitContext, start);
    Var endVal = exprWalker.retrieveToVar(waitContext, end);
    Var stepVal = exprWalker.retrieveToVar(waitContext, step);
    Context bodyContext = loop.setupLoopBodyContext(waitContext, true, false);

    // The per-iteration value of the range
    Var memberVal = varCreator.createValueOfVar(bodyContext,
                                            loop.getMemberVar(), false);
    Var counterVal = loop.getLoopCountVal();

    backend.startRangeLoop(fc.getFunctionName() + "-range" + loopNum,
            VarRepr.backendVar(memberVal),
            (counterVal == null) ? null : VarRepr.backendVar(counterVal),
            VarRepr.backendArg(startVal), VarRepr.backendArg(endVal),
            VarRepr.backendArg(stepVal),
            loop.getDesiredUnroll(), loop.getSplitDegree(),
            loop.getLeafDegree());
    // Need to spawn off task per iteration
    if (!loop.isSyncLoop()) {
      backend.startWaitStatement(fc.getFunctionName() + "range-iter" + loopNum,
          Var.NONE, WaitMode.TASK_DISPATCH, false, false, ExecTarget.dispatchedControl());
    }

    // We have the current value, but need to put it in a future in case user
    //  code refers to it

    varCreator.initialiseVariable(bodyContext, loop.getMemberVar());
    exprWalker.assign(loop.getMemberVar(), memberVal.asArg());
    if (loop.getCountVarName() != null) {
      Var loopCountVar = varCreator.createVariable(bodyContext,
          Types.F_INT, loop.getCountVarName(), Alloc.STACK,
          DefType.LOCAL_USER, VarProvenance.userVar(context.getSourceLoc()),
          false);
      exprWalker.assign(loopCountVar, counterVal.asArg());
    }
    block(bodyContext, loop.getBody());
    if (!loop.isSyncLoop()) {
      backend.endWaitStatement();
    }
    backend.endRangeLoop();
    backend.endWaitStatement();
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
    Var arrayVar = exprWalker.eval(context, loop.getArrayVarTree(),
                          loop.findArrayType(context), false, null);

    VariableUsageInfo bodyVU = loop.getBody().checkedGetVariableUsage();
    List<Var> writtenVars = new ArrayList<Var>();
    summariseBranchVariableUsage(context, Arrays.asList(bodyVU), writtenVars);

    for (Var v: writtenVars) {
      if (v.equals(arrayVar)) {
        throw new STCRuntimeError("Array variable "
                  + v + " is written in the foreach loop "
                  + " it is the loop array for - currently this " +
                  "causes a deadlock due to technical limitations");
      }
    }

    // Need to get handle to real array before running loop
    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("foreach-array");

    Var realArray;
    Context outsideLoopContext;
    if (Types.isContainerRef(arrayVar)) {
      // If its a reference, wrap a wait() around the loop call
      backend.startWaitStatement(
          fc.getFunctionName() + "-foreach-refwait" + loopNum,
          VarRepr.backendVar(arrayVar).asList(),
          WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedControl());

      outsideLoopContext = new LocalContext(context);
      realArray = varCreator.createTmp(outsideLoopContext,
                              arrayVar.type().memberType(), false, true);
      exprWalker.retrieveRef(realArray, arrayVar, false);
    } else {
      assert(Types.isContainer(arrayVar));
      realArray = arrayVar;
      outsideLoopContext = context;
    }

    // Block on array
    backend.startWaitStatement(
        fc.getFunctionName() + "-foreach-wait" + loopNum,
        VarRepr.backendVar(realArray).asList(),
        WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedControl());

    loop.setupLoopBodyContext(outsideLoopContext, false, false);
    Context loopBodyContext = loop.getBodyContext();

    Var loopCountVal = loop.getLoopCountVal();

    boolean memberIsVal = (loop.getMemberVal() != null);
    Var backendIterVar = null;
    if (memberIsVal) {
      backendIterVar = VarRepr.backendVar(loop.getMemberVal());
    } else {
      backendIterVar = VarRepr.backendVar(loop.getMemberVar());
    }

    backend.startForeachLoop(fc.getFunctionName() + "-foreach" + loopNum,
            VarRepr.backendVar(realArray), backendIterVar,
            loopCountVal == null ? null : VarRepr.backendVar(loopCountVal),
            loop.getSplitDegree(), loop.getLeafDegree(), true);


    if (memberIsVal) {
      // Need to store to value that will be referenced by later generated code
      varCreator.initialiseVariable(loopBodyContext, loop.getMemberVar());
      exprWalker.assign(VarRepr.backendVar(loop.getMemberVar()),
                        backendIterVar.asArg());
    }

    // May need to spawn off each iteration as task - use wait for this
    if (!loop.isSyncLoop()) {
      backend.startWaitStatement(
          fc.getFunctionName() + "-foreach-spawn" + loopNum,
          Var.NONE, WaitMode.TASK_DISPATCH, false, false, ExecTarget.dispatchedControl());
    }
    // If the user's code expects a loop count var, need to create it here
    if (loop.getCountVarName() != null) {
      Var loopCountVar = varCreator.createVariable(loop.getBodyContext(),
                                     loop.createCountVar(context));
      exprWalker.assign(loopCountVar, loop.getLoopCountVal().asArg());
    }

    block(loopBodyContext, loop.getBody());

    // Close spawn wait
    if (!loop.isSyncLoop()) {
      backend.endWaitStatement();
    }
    backend.endForeachLoop();

    // Wait for array
    backend.endWaitStatement();
    if (Types.isContainerRef(arrayVar.type())) {
      // Wait for array ref
      backend.endWaitStatement();
    }
  }

  private void forLoop(Context context, SwiftAST tree) throws UserException {
    ForLoopDescriptor forLoop = ForLoopDescriptor.fromAST(context, tree);

    // Evaluate initial values of loop vars
    List<Arg> initVals = new ArrayList<Arg>();

    for (Var initVal: evalLoopVarExprs(context, forLoop,
                                       forLoop.getInitExprs())) {
      initVals.add(initVal.asArg());
    }

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
                  Alloc.ALIAS, DefType.LOCAL_COMPILER,
                  VarProvenance.userVar(context.getSourceLoc()),
                  lv.var.mappedDecl());
        // Copy turbine ID
        backend.makeAlias(VarRepr.backendVar(parentAlias),
                          VarRepr.backendVar(lv.var));
        parentLoopVarAliases.put(lv.var.name(), parentAlias);
      }
    }

    // Create context with loop variables
    Context loopIterContext = forLoop.createIterationContext(context);
    forLoop.validateCond(loopIterContext);
    Type condType = TypeChecker.findSingleExprType(loopIterContext,
                                              forLoop.getCondition());

    // Evaluate the conditional expression for the first iteration outside the
    // loop, directly using temp names for loop variables
    HashMap<String, String> initRenames = new HashMap<String, String>();
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      initRenames.put(forLoop.getLoopVars().get(i).var.name(),
            initVals.get(i).getVar().name());
    }
    Var initCond = exprWalker.eval(context, forLoop.getCondition(), condType, true, initRenames);

    // Start the loop construct with some initial values
    Var condArg =
        loopIterContext.declareVariable(condType, Var.LOOP_COND_PREFIX +
            loopNum, Alloc.TEMP, DefType.INARG,
            VarProvenance.exprTmp(context.getSourceLoc()), false);



    /* Pack the variables into vectors with the first element the condition */
    ArrayList<Var> loopVars = new ArrayList<Var>(forLoop.loopVarCount() + 1);
    loopVars.add(condArg);
    loopVars.addAll(forLoop.getUnpackedLoopVars());
    List<Boolean> definedHere = new ArrayList<Boolean>(forLoop.loopVarCount() + 1);
    definedHere.add(true); // Condition defined in construct
    for (LoopVar lv: forLoop.getLoopVars()) {
      definedHere.add(!lv.declaredOutsideLoop);
    }

    List<Boolean> blockingVector = new ArrayList<Boolean>(loopVars.size());
    blockingVector.add(true); // block on condition
    blockingVector.addAll(forLoop.blockingLoopVarVector());

    initVals.add(0, initCond.asArg());

    backend.startLoop(loopName, VarRepr.backendVars(loopVars), definedHere,
                      VarRepr.backendArgs(initVals), blockingVector);

    // get value of condVar
    Var condVal = exprWalker.retrieveToVar(loopIterContext, condArg);

    // branch depending on if loop should start
    backend.startIfStatement(VarRepr.backendArg(condVal), true);

    // Create new context for loop body to execute when condition passes
    Context loopBodyContext = new LocalContext(loopIterContext);

    // If this iteration is good, run all of the stuff in the block
    block(loopBodyContext, forLoop.getBody());

    forLoop.validateUpdates(loopBodyContext);
    //evaluate update expressions
    List<Arg> newLoopVars = new ArrayList<Arg>();
    for (Var newLoopVar: evalLoopVarExprs(loopBodyContext, forLoop,
                                          forLoop.getUpdateRules())) {
      newLoopVars.add(newLoopVar.asArg());
    }

    HashMap<String, String> nextRenames = new HashMap<String, String>();
    for (int i = 0; i < forLoop.loopVarCount(); i++) {
      nextRenames.put(forLoop.getLoopVars().get(i).var.name(),
                       newLoopVars.get(i).getVar().name());
    }
    Var nextCond = exprWalker.eval(loopBodyContext,
              forLoop.getCondition(), condType, true, nextRenames);
    newLoopVars.add(0, nextCond.asArg());
    backend.loopContinue(VarRepr.backendArgs(newLoopVars), blockingVector);
    backend.startElseBlock();
    // Terminate loop, clean up open arrays and copy out final vals
    // of loop vars
    Context loopFinalizeContext = new LocalContext(loopIterContext);
    for (LoopVar lv: forLoop.getLoopVars()) {
      if (lv.declaredOutsideLoop) {
        exprWalker.copyByValue(loopFinalizeContext,
            parentLoopVarAliases.get(lv.var.name()), lv.var);
      }
    }

    backend.loopBreak();
    backend.endIfStatement();

    // finish loop construct
    backend.endLoop();
  }

  private void iterate(Context context, SwiftAST tree) throws UserException {
    IterateDescriptor loop = IterateDescriptor.fromAST(context, tree);

    // Initial iteration should succeed
    Var falseV = exprWalker.assignToVar(context, Arg.FALSE, false);
    Var zero = exprWalker.assignToVar(context, Arg.ZERO, false);

    FunctionContext fc = context.getFunctionContext();
    int loopNum = fc.getCounterVal("iterate");
    String loopName = fc.getFunctionName() + "-iterate-" + loopNum;

    Context iterContext = loop.createIterContext(context);

    // Start the loop construct with some initial values
    Var condArg = iterContext.declareVariable(Types.F_BOOL,
            Var.LOOP_COND_PREFIX + loopNum, Alloc.TEMP, DefType.INARG,
            VarProvenance.exprTmp(context.getSourceLoc()), false);

    List<Boolean> blockingVars = Arrays.asList(true, false);
    backend.startLoop(loopName,
        VarRepr.backendVars(condArg, loop.getLoopVar()),
        Arrays.asList(true, true),
        VarRepr.backendArgs(falseV.asArg(), zero.asArg()), blockingVars);

    // get value of condVar
    Var condVal = exprWalker.retrieveToVar(iterContext, condArg);

    backend.startIfStatement(VarRepr.backendArg(condVal), true);
    backend.loopBreak();
    backend.startElseBlock();
    Context bodyContext = new LocalContext(iterContext);
    block(bodyContext, loop.getBody());

    // Check the condition type now that all loop body vars have been declared
    Type condType = TypeChecker.findSingleExprType(iterContext,
        loop.getCond());
    if (!condType.assignableTo(Types.F_BOOL)) {
      throw new TypeMismatchException(bodyContext,
          "iterate condition had invalid type: " + condType.typeName());
    }

    Var nextCond = exprWalker.eval(bodyContext, loop.getCond(),
                                          Types.F_BOOL, false, null);

    Var nextCounter = varCreator.createTmp(bodyContext,
                                      Types.F_INT);

    Var one = exprWalker.assignToVar(bodyContext, Arg.ONE, false);
    exprWalker.asyncOp(BuiltinOpcode.PLUS_INT, nextCounter, Arrays.asList(
                      loop.getLoopVar().asArg(), one.asArg()));

    backend.loopContinue(
        VarRepr.backendArgs(nextCond.asArg(), nextCounter.asArg()),
        blockingVars);

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




  private List<Var> declareVariables(Context context, SwiftAST tree, WalkMode walkMode)
          throws UserException {
    LogHelper.trace(context, "declareVariable...");
    assert(tree.getType() == ExMParser.DECLARATION);
    int count = tree.getChildCount();
    if (count < 2)
      throw new STCRuntimeError("declare_multi: child count < 2");
    VariableDeclaration vd =  VariableDeclaration.fromAST(context,
                                                    tree);
    List<Var> assignedVars = new ArrayList<Var>();

    for (int i = 0; i < vd.count(); i++) {
      VariableDescriptor vDesc = vd.getVar(i);
      SwiftAST declTree = vd.getDeclTree(i);
      SwiftAST assignedExpr = vd.getVarExpr(i);

      Var var;
      if (walkMode == WalkMode.ONLY_EVALUATION) {
        var = context.lookupVarInternal(vDesc.getName());
      } else {
        var = declareVariable(context, vDesc);
      }
      if (Types.isPrimUpdateable(var.type())) {
        if (walkMode == WalkMode.ONLY_DECLARATIONS) {
          throw new TypeMismatchException(context, var.name() +
                  " is an updateable and its declaration cannot be chained");
        }
        // Have to init at declare time
        initUpdateableVar(context, var, assignedExpr);
      } else if (walkMode != WalkMode.ONLY_DECLARATIONS) {
         if (assignedExpr != null) {
           Assignment assignment = new Assignment(AssignOp.ASSIGN,
                   Arrays.asList(new LValue(declTree, var)),
                   Arrays.asList(assignedExpr));
           assignedVars.addAll(assignMultiRVal(context, assignment, walkMode));
         }
      }
    }
    return assignedVars;
  }


  private void initUpdateableVar(Context context, Var var,
                                                SwiftAST initExpr) {
    if (initExpr != null) {
      // Handle as special case because currently we need an initial
      // value for the updateable variable right away
      if (var.type().equals(Types.UP_FLOAT)) {
        Double initVal = Literals.extractFloatLit(context, initExpr);
        if (initVal == null) {
          Long intLit = Literals.extractIntLit(context, initExpr);
          if (intLit != null) {
            initVal = Literals.interpretIntAsFloat(context, intLit);
          }
        }
        if (initVal == null) {
          throw new STCRuntimeError("Don't yet support non-constant" +
                  " initialisers for updateable variables");
        }
        backend.initScalarUpdateable(VarRepr.backendVar(var),
                               Arg.createFloatLit(initVal));
      } else {
        throw new STCRuntimeError("Non-float updateables not yet" +
                " implemented for type " + var.type());
      }
    } else {
      throw new STCRuntimeError("updateable variable " +
          var.name() + " must be given an initial value upon creation");
    }
  }

  private Var declareVariable(Context context,
      VariableDescriptor vDesc) throws UserException, UndefinedTypeException {
    Type definedType = vDesc.getType();

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

    Var var = varCreator.createMappedVariable(context, definedType,
        vDesc.getName(), Alloc.STACK, DefType.LOCAL_USER,
        VarProvenance.userVar(context.getSourceLoc()), mappedVar);
    return var;
  }

  private List<Var> assignExpression(Context context, SwiftAST tree,
        WalkMode walkMode) throws UserException {
    LogHelper.debug(context, "assignment: ");
    LogHelper.logChildren(context.getLevel(), tree);

    Assignment assign = Assignment.fromAST(context, tree);
    return assignMultiRVal(context, assign, walkMode);
  }

  private List<Var> assignMultiRVal(Context context, Assignment assign,
      WalkMode walkMode) throws UserException, TypeMismatchException,
      UndefinedTypeException, UndefinedVarError {
    List<Var> multiAssignTargets = new ArrayList<Var>();
    for (Pair<List<LValue>, SwiftAST> pair: assign.getMatchedAssignments(context)) {
      List<LValue> lVals = pair.val1;
      SwiftAST rVal = pair.val2;
      List<Var> assignTargets = assignSingleRVal(context, assign.op,
                                              lVals, rVal, walkMode);
      multiAssignTargets.addAll(assignTargets);
    }
    return multiAssignTargets;
  }

  /**
   * Handle an assignment from a single RValue expression to one
   * or more LValues
   * @param context
   * @param op
   * @param lVals
   * @param rValExpr
   * @param walkMode
   * @return
   * @throws UserException
   */
  private List<Var> assignSingleRVal(Context context, AssignOp op,
      List<LValue> lVals, SwiftAST rValExpr, WalkMode walkMode)
          throws UserException {
    // First do any preparation/reduction of lvals and obtain vars
    // to evaluate the R.H.S. expression(s) into
    LRVals target = lValWalker.prepareLVals(context, op, lVals, rValExpr,
                                            walkMode);

    // Evaluate the R.H.S. expressions(s)
    if (!target.skipREval && walkMode != WalkMode.ONLY_DECLARATIONS) {
      exprWalker.evalToVars(context, rValExpr, target.rValTargets, null);
    }

    // Do any final transformations/updates required
    return lValWalker.finalizeLVals(context, op, target);
  }
  /**
   * Statement that evaluates an expression with no assignment E.g., trace()
   */
  private List<Var> exprStatement(Context context, SwiftAST tree) throws UserException {
    assert (tree.getChildCount() == 1);
    SwiftAST expr = tree.child(0);

    ExprType exprType = TypeChecker.findExprType(context, expr);

    // Need to create throwaway temporaries for return values
    List<Var> oList = new ArrayList<Var>();
    for (Type t : exprType.getTypes()) {
      oList.add(varCreator.createTmp(context, t));
    }

    // TODO: some of oList will be references... should return
    //       dereferenced variable
    exprWalker.evalToVars(context, expr, oList, null);
    return oList;
  }

  private void updateStmt(Context context, SwiftAST tree)
        throws UserException {
    Update up = Update.fromAST(context, tree);
    Type exprType = up.typecheck(context);
    Var evaled = exprWalker.eval(context, up.getExpr(), exprType, false, null);
    backend.updateScalarFuture(VarRepr.backendVar(up.getTarget()), up.getMode(),
                   VarRepr.backendVar(evaled));
  }

  private void defineBuiltinFunction(Context context, SwiftAST tree)
  throws UserException {
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

    // TODO: other types of packages
    backend.requirePackage(new TclPackage(pkg, version));

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
      inlineTcl = wrapper.loadTclTemplate(context, function, fdecl, ft,
                                          inlineTclTree);
      pos++;
    }

    // Register as foreign function
    ForeignFunctions.addForeignFunction(function);

    // Read annotations at end of child list
    for (; pos < tree.getChildCount(); pos++) {
      handleBuiltinFunctionAnnotation(context, function, fdecl, tree.child(pos),
                                inlineTcl != null);
    }

    ExecTarget taskMode = ForeignFunctions.getTaskMode(function);

    // TODO: assume for now that all non-local builtins are targetable
    // This is still not quite right (See issue #230)
    boolean isTargetable = false;
    if (taskMode.isDispatched()) {
      isTargetable = true;
      context.setFunctionProperty(function, FnProp.TARGETABLE);
    }

    context.defineFunction(function, ft);
    FunctionType backendFT = VarRepr.backendFnType(ft);
    backend.defineBuiltinFunction(function, backendFT, inlineTcl, impl);

    if (impl != null) {
      context.setFunctionProperty(function, FnProp.BUILTIN);
    } else {
      if (inlineTcl == null) {
        throw new UserException(context, "Must provide TCL implementation or " +
        		"inline TCL for function " + function);
      }
      // generate composite function wrapping inline tcl
      context.setFunctionProperty(function, FnProp.WRAPPED_BUILTIN);
      context.setFunctionProperty(function, FnProp.SYNC);
      boolean isParallel = context.hasFunctionProp(function, FnProp.PARALLEL);
      if (isParallel &&
          (!taskMode.isAsync() ||
           !taskMode.targetContext().isAnyWorkContext()) ) {
        throw new UserException(context,
                        "Parallel tasks must execute on workers");
      }

      // Defer generation of wrapper until it is called
      wrapper.saveWrapper(function, backendFT, fdecl,
                          taskMode, isParallel, isTargetable);
    }
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


  private void handleBuiltinFunctionAnnotation(Context context, String function,
      FunctionDecl fdecl,
      SwiftAST annotTree, boolean hasLocalVersion) throws UserException {
    assert(annotTree.getType() == ExMParser.ANNOTATION);

    assert(annotTree.getChildCount() > 0);
    String key = annotTree.child(0).getText();
    if (annotTree.getChildCount() == 1) {
      registerFunctionAnnotation(context, function, fdecl, key);
    } else {
      assert(annotTree.getChildCount() == 2);
      String val = annotTree.child(1).getText();
      if (key.equals(Annotations.FN_BUILTIN_OP)) {
        addlocalEquiv(context, function, val);
      } else if (key.equals(Annotations.FN_STC_INTRINSIC)) {
        IntrinsicFunction intF;
        try {
          intF = IntrinsicFunction.valueOf(val.toUpperCase());
        } catch (IllegalArgumentException ex) {
          throw new InvalidAnnotationException(context, "Invalid intrinsic name: "
                + " " + val + ".  Expected one of: " + IntrinsicFunction.values());
        }
        context.addIntrinsic(function, intF);
      } else if (key.equals(Annotations.FN_IMPLEMENTS)) {
        SpecialFunction special = ForeignFunctions.findSpecialFunction(val);
        if (special == null) {
          throw new InvalidAnnotationException(context, "\"" + val +
              "\" is not the name of a specially handled function in STC. " +
              "Valid options are: " +
              StringUtil.concat(SpecialFunction.values()));
        }
        ForeignFunctions.addSpecialImpl(special, function);
      } else if (key.equals(Annotations.FN_DISPATCH)) {
        try {
          ExecContext cx = context.lookupExecContext(val);
          ForeignFunctions.addTaskMode(function, ExecTarget.dispatched(cx));
        } catch (IllegalArgumentException e) {
          List<String> dispatchNames = new ArrayList<String>(context.execTargetNames());
          Collections.sort(dispatchNames);

          throw new UserException(context, "Unknown dispatch mode " + val + ". "
              + " Valid options are: " + StringUtil.concat(dispatchNames));
        }
      } else {
        throw new InvalidAnnotationException(context, "Tcl function",
                                             key, true);
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
    ForeignFunctions.addOpEquiv(function, opcode);
  }

  /**
   * Check that an annotation for the named function is valid, and
   * add it to the known semantic info
   * @param function
   * @param annotation
   * @throws UserException
   */
  private void registerFunctionAnnotation(Context context, String function,
        FunctionDecl fdecl, String annotation) throws UserException {
    if (annotation.equals(Annotations.FN_ASSERTION)) {
      ForeignFunctions.addAssertVariable(function);
    } else if (annotation.equals(Annotations.FN_PURE)) {
      ForeignFunctions.addPure(function);
    } else if (annotation.equals(Annotations.FN_COMMUTATIVE)) {
      ForeignFunctions.addCommutative(function);
    } else if (annotation.equals(Annotations.FN_COPY)) {
      ForeignFunctions.addCopy(function);
    } else if (annotation.equals(Annotations.FN_MINMAX)) {
      ForeignFunctions.addMinMax(function);
    } else if (annotation.equals(Annotations.FN_PAR)) {
      context.setFunctionProperty(function, FnProp.PARALLEL);
    } else if (annotation.equals(Annotations.FN_DEPRECATED)) {
      context.setFunctionProperty(function, FnProp.DEPRECATED);
    } else if (annotation.equals(Annotations.FN_CHECKPOINT)) {
      Checkpointing.checkCanCheckpoint(context, function,
                                       fdecl.getFunctionType());

      context.setFunctionProperty(function, FnProp.CHECKPOINTED);
      backend.requireCheckpointing();
    } else {
      throw new InvalidAnnotationException(context, "function", annotation, false);
    }

  }


  private void defineFunction(Context context, SwiftAST tree)
  throws UserException {
    syncFilePos(context, tree);
    String function = tree.child(0).getText();
    LogHelper.debug(context, "define function: " + context.getLocation() +
                              function);
    assert(tree.getChildCount() >= 5);
    SwiftAST typeParams = tree.child(1);
    SwiftAST outputs = tree.child(2);
    SwiftAST inputs = tree.child(3);

    assert(typeParams.getType() == ExMParser.TYPE_PARAMETERS);
    if (typeParams.getChildCount() != 0) {
      throw new UserException(context, "Cannot provide type parameters for "
                                      + "Swift functions");
    }

    Set<Suppression> suppressions = new HashSet<Suppression>();
    List<String> annotations = extractFunctionAnnotations(context, tree, 5,
                                                          suppressions);

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
    if (function.equals(Constants.MAIN_FUNCTION) &&
        (ft.getInputs().size() > 0 || ft.getOutputs().size() > 0))
      throw new TypeMismatchException(context,
          "main() is not allowed to have input or output arguments");

    boolean async = true;
    for (String annotation: annotations) {
      if (annotation.equals(Annotations.FN_SYNC)) {
        async = false;
      } else {
        registerFunctionAnnotation(context, function, fdecl, annotation);
      }
    }

    context.defineFunction(function, ft);
    context.setFunctionProperty(function, FnProp.COMPOSITE);
    if (!async) {
      context.setFunctionProperty(function, FnProp.SYNC);
    }
  }

  /**
   * Extract function annotations for Swift function
   * @param context
   * @param tree
   * @param firstChild
   * @return
   * @throws InvalidAnnotationException
   */
  private List<String> extractFunctionAnnotations(Context context,
      SwiftAST tree, int firstChild, Set<Suppression> supps)
              throws InvalidAnnotationException {
    return extractFunctionAnnotations(context, tree, firstChild,
            false, new Out<AsyncExecutor>(), supps);
  }
  private List<String> extractAppFunctionAnnotations(Context context,
      SwiftAST tree, int firstChild,  Out<AsyncExecutor> exec,
      Set<Suppression> supps)
          throws InvalidAnnotationException {
    return extractFunctionAnnotations(context, tree, firstChild,
              true, exec, supps);
  }

  /**
   * Extract function annotations for Swift or app function
   * @param context
   * @param tree
   * @param firstChild
   * @return
   * @throws InvalidAnnotationException
   */
  private List<String> extractFunctionAnnotations(Context context,
          SwiftAST tree, int firstChild, boolean appFn,
          Out<AsyncExecutor> exec, Set<Suppression> suppressions)
              throws InvalidAnnotationException {
    exec.val = null;

    List<String> annotations = new ArrayList<String>();
    for (SwiftAST subtree: tree.children(firstChild)) {
      syncFilePos(context, subtree);
      assert(subtree.getType() == ExMParser.ANNOTATION);
      assert(subtree.getChildCount() == 1 || subtree.getChildCount() == 2);
      String annotation = subtree.child(0).getText();
      if (subtree.getChildCount() == 1) {
        annotations.add(annotation);
      } else {
        assert(subtree.getChildCount() == 2);
        String value = subtree.child(1).getText();
        if (appFn && Annotations.FN_DISPATCH.equals(annotation)) {
          try {
            if (exec.val != null) {
              throw new InvalidAnnotationException(context,
                              "Repeated annotation " + annotation);
            }
            exec.val = AsyncExecutor.fromUserString(value);
          } catch (IllegalArgumentException e) {
            throw new InvalidAnnotationException(context,
                "Unknown dispatch option: " + value);
          }
        } else if (annotation.equals(Annotations.FN_SUPPRESS)) {
          try {
            Suppression supp = Suppression.fromUserString(value);
            suppressions.add(supp);
          } catch (IllegalArgumentException e) {
            throw new InvalidAnnotationException(context,
                "Unknown suppression: " + value);
          }
        } else {
          throw new InvalidAnnotationException(context, "function definition",
              annotation, true);
        }
      }
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
    SwiftAST outputs = tree.child(2);
    SwiftAST inputs = tree.child(3);
    SwiftAST block = tree.child(4);

    FunctionDecl fdecl = FunctionDecl.fromAST(context, function,
                  inputs, outputs, Collections.<String>emptySet());

    List<Var> iList = fdecl.getInVars(context);
    List<Var> oList = fdecl.getOutVars(context);

    List<Var> backendIList = VarRepr.backendVars(iList);
    List<Var> backendOList = VarRepr.backendVars(oList);

    // Analyse variable usage inside function and annotate AST
    syncFilePos(context, tree);
    String moduleName = modules.currentModule().moduleName;
    varAnalyzer.analyzeVariableUsage(context, lineMap(), moduleName, function,
                                     iList, oList, block);

    LocalContext functionContext = new LocalContext(context, function);
    functionContext.addDeclaredVariables(iList);
    functionContext.addDeclaredVariables(oList);

    ExecTarget mode = context.hasFunctionProp(function, FnProp.SYNC) ?
                  ExecTarget.syncControl() : ExecTarget.dispatchedControl();
    backend.startFunction(function, backendOList, backendIList, mode);
    block(functionContext, block);
    backend.endFunction();

    LogHelper.debug(context, "compile function: done: " + function);
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
    context.setFunctionProperty(function, FnProp.SYNC);
    context.setFunctionProperty(function, FnProp.TARGETABLE);
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
    List<Var> outArgs = decl.getOutVars(context);
    List<Var> inArgs = decl.getInVars(context);

    List<Var> backendOutArgs = VarRepr.backendVars(outArgs);

    /* Pass in e.g. location */
    List<Var> backendInArgs = new ArrayList<Var>();
    for (Var inArg: inArgs) {
      backendInArgs.add(VarRepr.backendVar(inArg));
    }

    TaskProps props = new TaskProps();
    // Need to pass location arg into task dispatch wait statement
    // Priority is passed implicitly
    Var loc = new Var(Types.V_INT, Var.VALUEOF_VAR_PREFIX + "location",
        Alloc.LOCAL, DefType.INARG, VarProvenance.exprTmp(context.getSourceLoc()));
    backendInArgs.add(loc);
    props.put(TaskPropKey.LOCATION, loc.asArg());


    syncFilePos(context, tree);
    Out<AsyncExecutor> exec = new Out<AsyncExecutor>();
    Set<Suppression> suppressions = new HashSet<Suppression>();
    List<String> annotations = extractAppFunctionAnnotations(context,
                                        tree, 4, exec, suppressions);

    syncFilePos(context, tree);
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
        throw new InvalidAnnotationException(context, "app function",
                                             annotation, false);
      }
    }

    LocalContext appContext = new LocalContext(context, function);
    appContext.addDeclaredVariables(outArgs);
    appContext.addDeclaredVariables(inArgs);


    backend.startFunction(function, backendOutArgs, backendInArgs,
                          ExecTarget.syncControl());
    genAppFunctionBody(appContext, appBodyT, inArgs, outArgs,
                       hasSideEffects, deterministic, exec.val, props,
                       suppressions);
    backend.endFunction();
  }


  /**
   * @param context local context for app function
   * @param appBody AST for app function body
   * @param inArgs input arguments for app function
   * @param outArgs output arguments for app function
   * @param hasSideEffects
   * @param deterministic
   * @param val
   * @param props
   * @param suppressions
   * @throws UserException
   */
  private void genAppFunctionBody(Context context, SwiftAST appBody,
          List<Var> inArgs, List<Var> outArgs,
          boolean hasSideEffects,
          boolean deterministic, AsyncExecutor asyncExec,
          TaskProps props, Set<Suppression> suppressions) throws UserException {
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
    Pair<List<Var>, Boolean> evaledArgs = evalAppCmdArgs(context, cmd);

    List<Var> cmdArgs = evaledArgs.val1;
    boolean openedEvalWait = evaledArgs.val2;

    // Process any redirections
    Redirects<Var> redirFutures = processAppRedirects(context,
                                                    appBody.children(1));

    checkAppOutputs(context, appName, outArgs, cmdArgs, redirFutures,
                    suppressions);

    // Work out what variables must be closed before command line executes
    Pair<Map<String, Var>, List<WaitVar>> wait = selectAppWaitVars(context,
                                  cmdArgs, inArgs, outArgs, redirFutures);
    Map<String, Var> fileNames = wait.val1;
    List<WaitVar> waitVars = wait.val2;

    // Ensure it executes in correct context
    ExecContext targetCx = (asyncExec == null) ? ExecContext.defaultWorker() :
                                                 asyncExec.execContext();

    // use wait to wait for data then dispatch task to worker
    String waitName = context.getFunctionContext().constructName("app-leaf");
    // do deep wait for array args
    backend.startWaitStatement(waitName, VarRepr.backendWaitVars(waitVars),
        WaitMode.TASK_DISPATCH, true, ExecTarget.dispatched(targetCx), props);
    // On worker, just execute the required command directly
    Pair<List<Arg>, Redirects<Arg>> retrieved = retrieveAppArgs(context,
                                          cmdArgs, redirFutures, fileNames);
    List<Arg> localArgs = retrieved.val1;
    Redirects<Arg> localRedirects = retrieved.val2;

    // Create dummy dependencies for input files to avoid wait
    // being optimised out
    List<Arg> localInFiles = new ArrayList<Arg>();
    for (Var inArg: inArgs) {
      if (Types.isFile(inArg.type())) {
        Var localInputFile = exprWalker.retrieveToVar(context, inArg);
        localInFiles.add(Arg.createVar(localInputFile));
      }
    }

    // Declare local dummy output vars
    List<Var> localOutputs = new ArrayList<Var>(outArgs.size());
    for (Var output: outArgs) {
      Var localOutput = varCreator.createValueOfVar(context, output);
      localOutputs.add(localOutput);
      Arg localOutputFileName = null;
      if (Types.isFile(output.type())) {
        localOutputFileName = Arg.createVar(
            exprWalker.retrieveToVar(context, fileNames.get(output.name())));

        // Initialize the output with a filename
        backend.initLocalOutFile(VarRepr.backendVar(localOutput),
                                 VarRepr.backendArg(localOutputFileName),
                                 VarRepr.backendVar(output));
      }
    }

    List<Arg> beLocalArgs = VarRepr.backendArgs(localArgs);
    List<Var> beLocalOutputs = VarRepr.backendVars(localOutputs);
    List<Arg> beLocalInfiles = VarRepr.backendArgs(localInFiles);
    Redirects<Arg> beLocalRedirects = new Redirects<Arg>(
                VarRepr.backendArg(localRedirects.stdin, true),
                VarRepr.backendArg(localRedirects.stdout, true),
                VarRepr.backendArg(localRedirects.stderr, true));
    if (asyncExec == null) {
      backend.runExternal(appName, beLocalArgs, beLocalInfiles, beLocalOutputs,
                        beLocalRedirects, hasSideEffects, deterministic);
    } else {
      String aeName = context.constructName("async-exec");
      Map<String, Arg> taskProps = new HashMap<String, Arg>();
      beLocalRedirects.addProps(taskProps);

      backend.startAsyncExec(aeName, asyncExec, appName,
          beLocalOutputs, beLocalArgs,
          taskProps, !deterministic);
      // Rest of code executes in continuation after execution finishes
    }

    for (int i = 0; i < outArgs.size(); i++) {
      Var output = outArgs.get(i);
      Var localOutput = localOutputs.get(i);
      if (Types.isFile(output.type())) {
        Var outIsMapped = varCreator.createTmpLocalVal(context, Types.V_BOOL);
        Var setOutFilename = varCreator.createTmpLocalVal(context, Types.V_BOOL);
        backend.isMapped(VarRepr.backendVar(outIsMapped),
                         VarRepr.backendVar(output));
        exprWalker.localOp(BuiltinOpcode.NOT, setOutFilename,
                           outIsMapped.asArg().asList());
        exprWalker.assignFile(output, Arg.createVar(localOutput),
                              setOutFilename.asArg());
        if (output.isMapped() != Ternary.TRUE &&
            output.type().fileKind().supportsTmpImmediate()) {
          // Cleanup temporary local file if needed
          backend.decrLocalFileRef(VarRepr.backendVar(localOutput));
        }
      } else {
        assert(Types.isVoid(output.type()));
        exprWalker.assign(output, localOutput.asArg());
      }
    }

    if (asyncExec != null) {
      backend.endAsyncExec();
    }
    backend.endWaitStatement();
    if (openedEvalWait) {
      backend.endWaitStatement();
    }
  }

  private Redirects<Var> processAppRedirects(Context context,
                             List<SwiftAST> redirects) throws UserException {
    Redirects<Var> redir = new Redirects<Var>();

    // Process redirections
    for (SwiftAST redirT: redirects) {
      syncFilePos(context, redirT);
      assert(redirT.getChildCount() == 2);
      SwiftAST redirType = redirT.child(0);
      SwiftAST redirExpr = redirT.child(1);
      String redirTypeName = LogHelper.tokName(redirType.getType());

      // Now typecheck
      Type type = TypeChecker.findSingleExprType(context, redirExpr);
      // TODO: maybe could have plain string for filename, e.g. /dev/null?
      if (!Types.isFile(type)) {
        throw new TypeMismatchException(context, "Invalid type for" +
            " app redirection, must be file: " + type.typeName());
      } else if (type.fileKind() != FileKind.LOCAL_FS) {
        throw new TypeMismatchException(context, "Cannot redirect " +
              redirTypeName + " to/from variable type " + type.typeName() +
              ". Expected a regular file.");
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
   * @param outArgs
   * @param redir
   * @throws UserException
   */
  private void checkAppOutputs(Context context, String function,
      List<Var> outArgs, List<Var> args,
      Redirects<Var> redirFutures, Set<Suppression> suppressions)
          throws UserException {
    boolean deferredError = false;
    HashMap<String, Var> outMap = new HashMap<String, Var>();
    for (Var output: outArgs) {
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
      if (!Types.isVoid(unreferenced.type()) &&
          !suppressions.contains(Suppression.UNUSED_OUTPUT)) {
        LogHelper.warn(context, "Output argument " + unreferenced.name()
          + " is not referenced in app command line.  This usually " +
          "indicates an error.  However, if this is intended, for example " +
          "if the file location is implicit, you can suppress this warning " +
          "by annotating the function with @suppress=unused_output");
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
      localInput = exprWalker.retrieveToVar(context, filenameFuture);
    } else if (Types.isArray(in.type())) {
      // Unpack to flat representation
      NestedContainerInfo ci = new NestedContainerInfo(in.type());
      Type memberValType = Types.retrievedType(ci.baseType);
      Type localInType =  new ArrayType(true, Types.F_INT, memberValType);
      localInput = varCreator.createValueVar(context, localInType, in, true);
      backend.unpackArrayToFlat(VarRepr.backendVar(localInput),
                                VarRepr.backendArg(in));
    } else {
      localInput = exprWalker.retrieveToVar(context, in);
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
  private Pair<List<Var>, Boolean>
      evalAppCmdArgs(Context context, SwiftAST cmdArgs)
          throws TypeMismatchException, UserException {
    List<Var> refArgs = new ArrayList<Var>();
    List<Var> args = new ArrayList<Var>();
    // Skip first arg: that is id
    for (SwiftAST cmdArg: cmdArgs.children(1)) {
      if (cmdArg.getType() == ExMParser.APP_FILENAME) {
        assert(cmdArg.getChildCount() == 1);
        String fileVarName = cmdArg.child(0).getText();
        Var file = context.lookupVarUser(fileVarName);
        if (!Types.isFile(file.type())) {
          throw new TypeMismatchException(context, "Variable " + file.name()
                  + " is not a file, cannot use @ prefix for app");
        }
        args.add(file);
      } else {
        Type exprType = TypeChecker.findSingleExprType(context, cmdArg);
        Type baseType = exprType; // Type after expanding arrays
        while (true) {
          // Iteratively reduce until we get base type
          if (Types.isArray(baseType)) {
            NestedContainerInfo info = new NestedContainerInfo(baseType);
            baseType = info.baseType;
          } else if (Types.isRef(baseType)) {
            baseType = Types.retrievedType(baseType);
          } else {
            break;
          }
        }

        if (Types.isString(baseType) || Types.isInt(baseType) ||
            Types.isFloat(baseType) || Types.isBool(baseType) ||
            Types.isFile(baseType)) {
            Var arg = exprWalker.eval(context, cmdArg, exprType, false, null);
            args.add(arg);
            if (Types.isRef(arg)) {
              refArgs.add(arg);
            }
        } else {
          throw new TypeMismatchException(context, "Cannot convert type " +
                        baseType.typeName() + " to app command line arg");
        }
      }
    }

    if (refArgs.isEmpty()) {
      return Pair.create(args, false);
    } else {
      // Replace refs with dereferenced
      backend.startWaitStatement(
          context.getFunctionContext().constructName("ref-argwait"),
          VarRepr.backendVars(refArgs),
          WaitMode.WAIT_ONLY, false, false, ExecTarget.nonDispatchedAny());

      for (int i = 0; i < args.size(); i++) {
        Var oldArg = args.get(i);
        if (Types.isRef(oldArg)) {
          // Replace old arg with dereferenced version
          Var derefedArg = varCreator.createTmpAlias(context,
                              Types.retrievedType(oldArg));
          exprWalker.retrieveRef(derefedArg, oldArg, false);
          args.set(i, derefedArg);
        }
      }

      // Caller will close wait
      return Pair.create(args, true);
    }

  }

  /**
   * Choose which inputs/outputs to an app invocation should be blocked
   * upon.  This is somewhat complex since we sometimes need to block
   * on filenames/file statuses/etc
   * @param context
   * @param redirFutures
   * @param cmdArgs arguments for command line
   * @param inArgs input arguments for app function
   * @param outArgs output arguments for app function
   * @return
   * @throws UserException
   * @throws UndefinedTypeException
   */
  private Pair<Map<String, Var>, List<WaitVar>> selectAppWaitVars(
          Context context, List<Var> cmdArgs, List<Var> inArgs,
          List<Var> outArgs, Redirects<Var> redirFutures)
                                                throws UserException,
          UndefinedTypeException {
    // All command arguments including redirects
    List<Var> allCmdArgs = new ArrayList<Var>();
    allCmdArgs.addAll(cmdArgs);
    allCmdArgs.addAll(redirFutures.redirections(true, true));

    // map from file var to filename
    Map<String, Var> fileNames = new HashMap<String, Var>();
    List<WaitVar> waitVars = new ArrayList<WaitVar>();
    for (Var arg: allCmdArgs) {
      if (Types.isFile(arg)) {
        if (fileNames.containsKey(arg.name())) {
          continue;
        }
        loadAppFilename(context, fileNames, waitVars, arg);
      } else {
        waitVars.add(new WaitVar(arg, false));
      }
    }

    for (Var inArg: inArgs) {
      // Handle input files not referenced in command line
      if (!allCmdArgs.contains(inArg)) {
        // File doesn't need to be explicit since input files are
        // tracked explicitly in middle-end
        boolean explicit = !Types.isFile(inArg);
        waitVars.add(new WaitVar(inArg, explicit));
      }
    }

    // Fetch missing output arguments that weren't on command line
    for (Var outArg: outArgs) {
      if (Types.isFile(outArg.type()) && !fileNames.containsKey(outArg.name())) {
        loadAppFilename(context, fileNames, waitVars, outArg);
      }
    }

    return Pair.create(fileNames, waitVars);
  }


  private void loadAppFilename(Context context, Map<String, Var> fileNames,
                               List<WaitVar> waitVars, Var fileVar)
      throws UserException, UndefinedTypeException {
    // Need to wait for filename for files
    Var filenameFuture = varCreator.createFilenameAlias(context, fileVar);

    if (fileVar.defType() == DefType.OUTARG &&
        fileVar.type().fileKind().supportsTmpImmediate()) {
      // If output may be unmapped, need to assign file name
      backend.getFileNameAlias(VarRepr.backendVar(filenameFuture),
                          VarRepr.backendVar(fileVar), true);
    } else {
      backend.getFileNameAlias(VarRepr.backendVar(filenameFuture),
                          VarRepr.backendVar(fileVar), false);
    }
    waitVars.add(new WaitVar(filenameFuture, false));
    if (fileVar.defType() != DefType.OUTARG) {
      // Don't wait for file to be closed for output arg
      waitVars.add(new WaitVar(fileVar, true));
    }

    fileNames.put(fileVar.name(), filenameFuture);
  }


  private void defineNewType(Context context, SwiftAST defnTree,
                             boolean aliasOnly) throws UserException {
    assert (defnTree.getType() == ExMParser.DEFINE_NEW_TYPE ||
            defnTree.getType() == ExMParser.TYPEDEF );
    int children = defnTree.getChildCount();
    assert(children == 2);
    String typeName = defnTree.child(0).getText();
    SwiftAST baseTypeT = defnTree.child(1);

    Type baseType = TypeTree.extractStandaloneType(context, baseTypeT);

    Type newType;
    if (aliasOnly) {
      newType = baseType;
    } else {
      newType = new SubType(baseType, typeName);
    }

    context.defineType(typeName, newType);
  }


  private void defineNewStructType(Context context, SwiftAST defnTree)
      throws UserException {
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
      Type fieldType = context.lookupTypeUnsafe(baseTypeName);
      if (fieldType == null) {
        throw new UndefinedTypeException(context, baseTypeName);
      }
      String name = fieldTree.child(1).getText();

      // Account for any []'s
      List<SwiftAST> arrMarkers = fieldTree.children(2);
      fieldType = TypeTree.applyArrayMarkers(context, arrMarkers, fieldType);
      if (usedFieldNames.contains(name)) {
        throw new DoubleDefineException(context, "Field " + name
            + " is defined twice in type" + typeName);
      }
      fields.add(new StructField(fieldType, name));
      usedFieldNames.add(name);
    }

    StructType newType = StructType.sharedStruct(typeName, fields);
    context.defineType(typeName, newType);
    backend.defineStructType((StructType)VarRepr.backendType(newType, false));
    LogHelper.debug(context, "Defined new type called " + typeName + ": "
        + newType.toString());
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
                   Alloc.GLOBAL_CONST, DefType.GLOBAL_CONST,
                   VarProvenance.userVar(context.getSourceLoc()), false);


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

    Var backendVar = VarRepr.backendVar(v);
    switch (v.type().primType()) {
    case BOOL:
      String bval = Literals.extractBoolLit(context, val);
      if (bval == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(backendVar, Arg.createBoolLit(
                                        Boolean.parseBoolean(bval)));
      break;
    case INT:
      Long ival = Literals.extractIntLit(context, val);
      if (ival == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(backendVar, Arg.createIntLit(ival));
      break;
    case FLOAT:
      Double fval = Literals.extractFloatLit(context, val);
      if (fval == null) {
        Long sfval = Literals.extractIntLit(context, val);
        if (sfval == null) {
          throw new UserException(context, msg);
        } else {
          fval = Literals.interpretIntAsFloat(context, sfval);
        }
      }
      assert(fval != null);
      backend.addGlobal(backendVar, Arg.createFloatLit(fval));
      break;
    case STRING:
      String sval = Literals.extractStringLit(context, val);
      if (sval == null) {
        throw new UserException(context, msg);
      }
      backend.addGlobal(backendVar, Arg.createStringLit(sval));
      break;
    default:
      throw new STCRuntimeError("Unexpect value tree type in "
          + " global constant: " + LogHelper.tokName(val.getType()));
    }
  }
}
