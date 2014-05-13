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
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Level;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.exceptions.VariableUsageException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.frontend.VariableUsageInfo.Violation;
import exm.stc.frontend.VariableUsageInfo.ViolationType;
import exm.stc.frontend.tree.ArrayElems;
import exm.stc.frontend.tree.ArrayRange;
import exm.stc.frontend.tree.Assignment;
import exm.stc.frontend.tree.Assignment.AssignOp;
import exm.stc.frontend.tree.ForLoopDescriptor;
import exm.stc.frontend.tree.ForLoopDescriptor.LoopVar;
import exm.stc.frontend.tree.ForeachLoop;
import exm.stc.frontend.tree.If;
import exm.stc.frontend.tree.IterateDescriptor;
import exm.stc.frontend.tree.LValue;
import exm.stc.frontend.tree.Switch;
import exm.stc.frontend.tree.Update;
import exm.stc.frontend.tree.VariableDeclaration;
import exm.stc.frontend.tree.VariableDeclaration.VariableDescriptor;
import exm.stc.frontend.tree.Wait;
/**
 * This module collects information about variable dataflow in the program, e.g.
 * if a variable is read or not, whether it is assigned twice, etc.
 *
 * It uses some type information, but doesn't attempt to perform full type checking
 */
class VariableUsageAnalyzer {

  /** Store line mapping as attribute to avoid passing everywhere */
  private LineMapping lineMapping;
  
  /** Store current module as attribute to avoid passing everywhere */
  private String currModuleName;
  
  /**
   * Analyse the variables that are present in the block and add
   * VariableUsageInfo object to the BLOCK AST nodes
   * @param function
   * @param oList
   * @param iList
   * @param block
   * @throws UserException
   */
  public void analyzeVariableUsage(Context context, 
          LineMapping lineMapping, String currModuleName,
          String function, List<Var> iList, List<Var> oList, SwiftAST block)
        throws UserException {
    LogHelper.debug(context, "analyzer: starting: " + function);
    this.lineMapping = lineMapping;
    this.currModuleName = currModuleName;
    
    VariableUsageInfo globVui = new VariableUsageInfo(); 
    // Add global constants
    for (Var global: context.getScopeVariables()) {
      globVui.declare(context,
          global.name(), global.type(), false);
      globVui.assign(context, global.name(), AssignOp.ASSIGN);
    }
    
    VariableUsageInfo argVui = globVui.createNested(); // create copy with globals
    Context fnContext = new LocalContext(context, function);
    
    // Add input and output variables to initial variable info
    for (Var i: iList) {
      argVui.declare(fnContext, i.name(), i.type(), false);
      argVui.assign(fnContext, i.name(), AssignOp.ASSIGN);
      fnContext.declareVariable(i.type(), i.name(), i.storage(), 
            i.defType(), VarProvenance.unknown(), i.mappedDecl());
    }
    for (Var o: oList) {
      argVui.declare(fnContext, o.name(), o.type(), false);
      // We should assume that return variables will be read
      argVui.read(fnContext, o.name());
      fnContext.declareVariable(o.type(), o.name(), o.storage(), 
          o.defType(), VarProvenance.unknown(), o.mappedDecl());
    }

    // obtain info about variable usage in function by walking tree
    walkBlock(fnContext, block, argVui);

    // Notify user of any warnings or errors, terminate compilation on errors
    boolean fatalError = false;
    for (Violation v: argVui.getViolations()) {
      String msg = v.toException().getMessage();
      if (v.getType() == ViolationType.ERROR) {
        fatalError = true;
        LogHelper.log(0, Level.ERROR, msg);
      } else {
        LogHelper.log(0, Level.WARN, msg);
      }
    }
    if (fatalError) {
      throw new VariableUsageException("Previous variable usage error in function " +
            function + " caused compilation termination");
    }
    LogHelper.debug(context, "analyzer: done: " + function);
  }

  private void syncFilePos(Context context, SwiftAST tree) {
    context.syncFilePos(tree, currModuleName, lineMapping);
  }
  
  private VariableUsageInfo walkBlock(Context context, SwiftAST block,
      VariableUsageInfo initialVu) throws UserException {
    return walkBlock(context, block, initialVu, true);
  }
  /**
   *
   * @param file
   * @param block
   * @param initialVu initial variable usage info for this block
   * @param vuiMap map to insert info for this block and all children
   * @param parent info about arguments, and about variables declared
   *                outside this block but visible
   * @return
   * @throws UserException
   */
  private VariableUsageInfo walkBlock(Context context, SwiftAST block,
                 VariableUsageInfo initialVu, boolean checkMisuse) 
                                                   throws UserException {
    assert(block.getType() == ExMParser.BLOCK);
    syncFilePos(context, block);

    VariableUsageInfo vu;
    if (initialVu != null) {
      vu = initialVu;
    } else {
      vu = new VariableUsageInfo();
    }

    for (SwiftAST t: block.children()) {
      syncFilePos(context, t);
      walk(context, t , vu);
    }
    
    // After having collected data for block, check for unread/unwritten vars
    if (checkMisuse) {
      syncFilePos(context, block);
      vu.detectVariableMisuse(context);
    }

    if (block.getVariableUsage() != null) {
      throw new STCRuntimeError("Error: overwriting variable usage "
          + " info " + " for block " + block.hashCode() + " at line "
          + context.getFileLine());
    }
    block.setVariableUsage(vu);
    return vu;
  }

  private void walk(Context context, SwiftAST tree, VariableUsageInfo vu)
                                                throws UserException {
    int token = tree.getType();

    syncFilePos(context, tree);
    LogHelper.trace(context, "walk " + context.getLocation() +
                    LogHelper.tokName(token));
    switch (token) {
      case ExMParser.BLOCK:
        VariableUsageInfo childVu = walkBlock(new LocalContext(context),
                                            tree, vu.createNested());
        vu.mergeNestedScopes(context, Arrays.asList(childVu), true);
        break;

      case ExMParser.IF_STATEMENT:
        ifStatement(context, vu, tree);
        break;

      case ExMParser.SWITCH_STATEMENT:
        switchStatement(context, vu, tree);
        break;

      case ExMParser.FOREACH_LOOP:
        foreach(context, vu, tree);
        break;
        
      case ExMParser.FOR_LOOP:
        forLoop(context, vu, tree);
        break;
        
      case ExMParser.ITERATE:
        iterate(context, vu, tree);
        break;
        
      case ExMParser.WAIT_STATEMENT:
      case ExMParser.WAIT_DEEP_STATEMENT:
        waitStmt(context, vu, tree);
        break;
        
      case ExMParser.DECLARATION:
        declareVariable(context, vu, tree);
        break;

      case ExMParser.ASSIGN_EXPRESSION:
        assignExpression(context, vu, tree);
        break;

      case ExMParser.EXPR_STMT:
        // Walk the expression to add in reads
        walkExpr(context, vu, tree.child(0));
        break;
        
      case ExMParser.UPDATE:
        updateStmt(context, vu, tree);
        break;
        
      case ExMParser.STATEMENT_CHAIN:
        walkStmtChain(context, tree, vu);
        break;
        
      default:
        throw new STCRuntimeError
        ("Unexpected token type inside procedure: " +
            LogHelper.tokName(token));
    }
  }

  private void walkStmtChain(Context context, SwiftAST tree,
      VariableUsageInfo vu) throws UserException {
    assert(tree.getType() == ExMParser.STATEMENT_CHAIN);
    
    // Do iteratively to avoid large stack
    while (tree.getType() == ExMParser.STATEMENT_CHAIN) {
      assert(tree.getChildCount() == 2);
      walk(context, tree.child(0), vu);
      tree = tree.child(1);
    }
    // Walk non-chained final statement
    walk(context, tree, vu);
  }

  private void updateStmt(Context context, VariableUsageInfo vu, SwiftAST tree)
                                      throws UserException {
    Update up = Update.fromAST(context, tree);
    walkExpr(context, vu, up.getExpr());
    
    // Treat the update as a read so that we know at least that the variable
    //  is used in a particular scope
    vu.read(context, up.getTarget().name());
  }

  private void ifStatement(Context context, VariableUsageInfo vu,
      SwiftAST tree) throws UserException {
    If ifStmt = If.fromAST(context, tree);
    
    // First walk the condition expression
    walkExpr(context, vu, ifStmt.getCondition());

    // Collect and merge up info from branches
    ArrayList<VariableUsageInfo> ifBranchVus = new ArrayList<VariableUsageInfo>();

    ifBranchVus.add(walkBlock(new LocalContext(context),
        ifStmt.getThenBlock(), vu.createNested()));
    if (ifStmt.hasElse()) {
      ifBranchVus.add(walkBlock(new LocalContext(context),
          ifStmt.getElseBlock(), vu.createNested()));
    }
    
    syncFilePos(context, tree);
    vu.mergeNestedScopes(context,  ifBranchVus, ifStmt.hasElse());
  }

  private void declareVariable(Context context,
      VariableUsageInfo vu, SwiftAST tree) throws UserException {
    VariableDeclaration vd = VariableDeclaration.fromAST(context, tree);
    for (int i = 0; i < vd.count(); i++) {
      VariableDescriptor var = vd.getVar(i);
      // walk mapping expresion
      if (var.getMappingExpr() != null) {
        walkExpr(context, vu, var.getMappingExpr());
      }
      
      syncFilePos(context, tree);
      // Don't retain mapping information in this pass since it might be
      // mapped to a temporary var
      vu.declare(context, var.getName(), var.getType(), var.getMappingExpr() != null);
      context.declareVariable(var.getType(), var.getName(), Alloc.STACK,
          DefType.LOCAL_USER, VarProvenance.unknown(), false);
      SwiftAST assignExpr = vd.getVarExpr(i);
      if (assignExpr != null) {
        LogHelper.debug(context, "Variable " + var.getName() + 
              " was declared and assigned"); 
        walkExpr(context, vu, assignExpr);
        vu.assign(context, var.getName(), AssignOp.ASSIGN);
      }
    }
  }

  private void assignExpression(Context context,
      VariableUsageInfo vu, SwiftAST tree) throws UserException {
    if (tree.getChildCount() < 2)
      throw new STCRuntimeError(
                  "assign_expression: child count < 2");
    // walk LHS to see what is assigned, and to walk index expressions
    Assignment assignments = Assignment.fromAST(context, tree);

    for (Pair<List<LValue>, SwiftAST> assign:
                    assignments.getMatchedAssignments(context)) {
      List<LValue> lVals = assign.val1;
      SwiftAST rVal = assign.val2;
      ExprType rValTs = Assignment.checkAssign(context, lVals, rVal);
      
      // Walk the rval expression to add in reads
      walkExpr(context, vu, rVal);
     
      for (int i = 0; i < lVals.size(); i++) {
        LValue lVal = lVals.get(i);
        syncFilePos(context, lVal.tree);
        if (lVal.var == null) {
          // Auto-declare variable
          lVal = lVal.varDeclarationNeeded(context, rValTs.get(i));
          assert(lVal != null);
          vu.declare(context, lVal.var.name(), lVal.var.type(), false);
          context.declareVariable(lVal.var.type(), lVal.var.name(), 
            Alloc.STACK, DefType.LOCAL_USER, VarProvenance.unknown(), false);
        }
        
        singleAssignment(context, vu, lVal, assignments.op);
      }
    }
  }

  private void singleAssignment(Context context, VariableUsageInfo vu,
          LValue lVal, AssignOp op) throws InvalidWriteException, InvalidSyntaxException {
    if (lVal.indices.size() == 0) {
      vu.assign(context, lVal.varName, op);
    } else {
      int arrayDepth = 0;
      // The path must have the structure
      // (.<struct_field>)*([<array_index>])*
      for (SwiftAST i: lVal.indices) {
        if (i.getType() == ExMParser.STRUCT_PATH) {
          if (arrayDepth > 0) {
            throw new InvalidWriteException(context,
                "Cannot assign directly to struct field inside array: "
                + lVal.toString() + ".  Must create struct and then "
                + "insert into array ");
          }
        } else if (i.getType() == ExMParser.ARRAY_PATH) {
          arrayDepth++;
        }
      }
      vu.complexAssign(context, lVal.varName, lVal.structPath(),
                                arrayDepth, op);
    }

    // Indicies can also be expressions with variables in them
    for (SwiftAST ixTree: lVal.indices) {
      if (ixTree.getType() == ExMParser.ARRAY_PATH) {
        walkExpr(context, vu, ixTree.child(0));
      }
    }
  }

  private void switchStatement(Context context,
      VariableUsageInfo vu, SwiftAST tree)
          throws UserException {
    Switch sw = Switch.fromAST(context, tree);

    //First walk the condition expression
    walkExpr(context, vu, sw.getSwitchExpr());

    ArrayList<VariableUsageInfo> caseVus = new ArrayList<VariableUsageInfo>();
    // Walk the different cases and merge info into this variable usage
    for (SwiftAST caseBody: sw.getCaseBodies()) {
      caseVus.add(walkBlock(new LocalContext(context), caseBody,
                                        vu.createNested()));
    }
    syncFilePos(context, tree);
    vu.mergeNestedScopes(context, caseVus, sw.hasDefault());
  }

  private void foreach(Context context, VariableUsageInfo vu, SwiftAST tree)
                                                        throws UserException {
    ForeachLoop loop = ForeachLoop.fromAST(context, tree);
    
    // Variables might appear in array var expression, so walk that first
    walkExpr(context, vu, loop.getArrayVarTree());

    // Then setup the variable usage info for the loop body,
    // taking into account the loop variables
    Context loopContext = loop.setupLoopBodyContext(context, false, true);

    VariableUsageInfo initial = vu.createNested();
    
    // Both loop variables are assigned before loop body runs
    initial.declare(context, loop.getMemberVarName(), 
        loop.getMemberVar().type(), loop.getMemberVar().mappedDecl());
    initial.assign(context, loop.getMemberVarName(), AssignOp.ASSIGN);
    if (loop.getCountVarName() != null) {
      initial.declare(context, loop.getCountVarName(), Types.F_INT, false);
      initial.assign(context, loop.getCountVarName(), AssignOp.ASSIGN);
    }
    
    // Workaround to get correct type info: have two nested variable usage infos.
    // In the outer one we assign the two loop counters, so that they're assigned
    // within the loop body
    // In the inner one, all of the actual action inside the loop body occurs.
    //  We merge the inner one with itself, which produces the same result as merging
    //  with itself n (n >= 2) times, but without causing false double-assign error
    // for loop counter
    VariableUsageInfo loopInfo = initial.createNested();
    VariableUsageInfo loopBodyInfo = walkBlock(loopContext,
        loop.getBody(), loopInfo);

    syncFilePos(context, tree);
    // Merge inner up into outer
    initial.mergeNestedScopes(context, 
            Arrays.asList(loopBodyInfo, loopBodyInfo), false);

    // Merge outer into the current scope
    vu.mergeNestedScopes(context, Arrays.asList(initial), true);
  }

  
  private void forLoop(Context context, VariableUsageInfo vu, SwiftAST tree)
      throws UserException {
    ForLoopDescriptor forLoop = ForLoopDescriptor.fromAST(context, tree);
    // process initial exprs in the current context
    for (SwiftAST initExpr: forLoop.getInitExprs().values()) {
      walkExpr(context, vu, initExpr);
    }
    
    VariableUsageInfo outerLoopInfo = vu.createNested();
    Context bodyContext = forLoop.createIterationContext(context);
    forLoop.validateCond(bodyContext);
    
    syncFilePos(context, tree);
    for (LoopVar lv: forLoop.getLoopVars()) {
      Var v = lv.var;
      LogHelper.debug(context, "declared loop var " + v.toString());
      
      
      if (!lv.declaredOutsideLoop) {
        outerLoopInfo.declare(context, v.name(), v.type(),
                              v.mappedDecl());
      }
      // we assume that each variable has an initializer and an update, so it
      // will be assigned before each loop iteration
      outerLoopInfo.assign(context, v.name(), AssignOp.ASSIGN);
    }
    
    // Create body context with loop vars
    VariableUsageInfo bodyInfo = outerLoopInfo.createNested();
    
    // condition can refer to in loop body context
    walkExpr(bodyContext, bodyInfo, forLoop.getCondition());
    
    walkBlock(bodyContext, forLoop.getBody(), bodyInfo, false);
    
    forLoop.validateCond(bodyContext);
    for (SwiftAST updateExpr: forLoop.getUpdateRules().values()) {
      walkExpr(bodyContext, bodyInfo, updateExpr);
    }
    
    syncFilePos(context, tree);
    // After walking the update expressions, check for warnings or errors
    bodyInfo.detectVariableMisuse(context);
    
    outerLoopInfo.mergeNestedScopes(context, 
                                    Arrays.asList(bodyInfo, bodyInfo), false);
    vu.mergeNestedScopes(context, 
                                          Arrays.asList(outerLoopInfo), true);
    //TODO: does this correctly add info about variables used in conditions etc
    //    to the context attached to the body?
  }
  
  private void iterate(Context context, VariableUsageInfo vu, SwiftAST tree)
  throws UserException {
    IterateDescriptor loop = IterateDescriptor.fromAST(context, 
        tree);
    
    VariableUsageInfo outerLoopInfo = vu.createNested();
    VariableUsageInfo bodyInfo = outerLoopInfo.createNested();
    Context bodyContext = loop.createIterContext(context);
    
    Var v = loop.getLoopVar();
    LogHelper.debug(context, "declared loop var " + v.toString());
    bodyInfo.declare(context, v.name(), v.type(), v.mappedDecl());
    // we assume that each variable has an initializer and an update, so it
    // will be assigned before each loop iteration
    bodyInfo.assign(context, v.name(), AssignOp.ASSIGN);
    
    walkBlock(bodyContext, loop.getBody(), bodyInfo);
    
    // condition can refer to any variable in loop body context, include those
    //  defined in the block
    walkExpr(bodyContext, bodyInfo, loop.getCond());
    
    syncFilePos(context, tree);
    outerLoopInfo.mergeNestedScopes(context, 
    Arrays.asList(bodyInfo, bodyInfo), false);
    vu.mergeNestedScopes(context, 
    Arrays.asList(outerLoopInfo), true);
  }
  
  private void waitStmt(Context context, VariableUsageInfo vu, SwiftAST tree)
        throws UserException {
    Wait wait = Wait.fromAST(context, tree);
    for (SwiftAST expr: wait.getWaitExprs()) {
      walkExpr(context, vu, expr);
    }
    LocalContext waitContext = new LocalContext(context);
    VariableUsageInfo waitVU = vu.createNested();
    walkBlock(waitContext, wait.getBlock(), waitVU);
    
    syncFilePos(context, tree);
    vu.mergeNestedScopes(context, Arrays.asList(waitVU), true);
  }
  
  /**
   * Walk an expression and add all variable reads to variable usage
   * @param file
   * @param vu the variable usage info to update
   * @param exprRoot the root of an expression tree
   * @throws InvalidSyntaxException 
   */
  private void walkExpr(Context context, VariableUsageInfo vu,
      SwiftAST exprRoot) throws InvalidSyntaxException {

    // Do a depth-first search of the expression tree to discover all
    // references to variables
    StackLite<SwiftAST> exprNodes = new StackLite<SwiftAST>();
    exprNodes.push(exprRoot);
    while (!exprNodes.isEmpty()) {
      SwiftAST node = exprNodes.pop();
      syncFilePos(context, node);
      int token = node.getType();
      /*context.getLogger().debug("walkExpr: token " +
              ExMParser.tokenNames[token] + " at " + context.getInputFile()
              + ":" + line);*/
      switch (token) {
        case ExMParser.CALL_FUNCTION:
          assert(node.getChildCount() >= 2);
          // Walk all the arguments
          SwiftAST argsTree = node.child(1);
          for (int i=0; i < argsTree.getChildCount(); i++) {
            SwiftAST argTree = argsTree.child(i);
            exprNodes.push(argTree);
          }
          for (SwiftAST annNode: node.children(2)) {
            assert(annNode.getType() == ExMParser.CALL_ANNOTATION);
            // Priority
            exprNodes.push(annNode.child(1));
          }
          break;

        case ExMParser.VARIABLE:
          assert(node.getChildCount() == 1);
          vu.read(context, node.child(0).getText());
          break;

        case ExMParser.INT_LITERAL:
        case ExMParser.STRING_LITERAL:
        case ExMParser.FLOAT_LITERAL:
        case ExMParser.BOOL_LITERAL:
          // nothing
          break;

        case ExMParser.OPERATOR:
          // Walk argument expressions
          // skip the first child, which is the operator name
          for (int i = 1; i < node.getChildCount(); i++) {
            exprNodes.push(node.child(i));
          }
          break;
        case ExMParser.ARRAY_RANGE:
          // Walk all the expressions
          ArrayRange ar = ArrayRange.fromAST(context, node);
          for (SwiftAST arg: ar.getArgs()) {
            exprNodes.push(arg);
          }
          break;
        case ExMParser.ARRAY_ELEMS:
        case ExMParser.ARRAY_KV_ELEMS:
          ArrayElems ae = ArrayElems.fromAST(context, node);
          if (ae.hasKeys()) {
            for (SwiftAST key: ae.getKeys()) {
              exprNodes.push(key);
            }
          }
          for (SwiftAST mem: ae.getVals()) {
            exprNodes.push(mem);
          }
          break;
        case ExMParser.ARRAY_LOAD:
        case ExMParser.STRUCT_LOAD:
          // Handle struct and array loads together: assume n>=0 struct lookups
          // and m>= array lookups
          SwiftAST currNode = node;

          // first count the number of array lookups
          int arrDepth = 0;
          while (currNode.getType() == ExMParser.ARRAY_LOAD) {
            assert(currNode.getChildCount() == 2);
            // Walk the array index expression to see which variables
            // it depends on
            exprNodes.push(currNode.child(1));
            currNode = currNode.child(0);
            arrDepth++;
          }

          LinkedList<String> fieldPath = new LinkedList<String>();
          while(currNode.getType() == ExMParser.STRUCT_LOAD) {
            assert(currNode.getChildCount() == 2);
            String field = currNode.child(1).getText();
            fieldPath.addFirst(field);
            currNode = currNode.child(0);
          }

          if (currNode.getType() == ExMParser.VARIABLE) {
            // Only need to add usage info if local variable
            String varName = currNode.child(0).getText();
            LogHelper.debug(context, "Complex read rooted at var: " + varName);
            vu.complexRead(context, varName, fieldPath, arrDepth);

          } else {
            // make sure we register usage of variables inside this expr
            exprNodes.push(currNode);
          }
          break;

        default:
          throw new STCRuntimeError
          ("Unexpected token type inside expression: " 
              + LogHelper.tokName(token)
              + " at l." + node.getLine() + ":" 
              + node.getCharPositionInLine());
      }
    }
  }
}