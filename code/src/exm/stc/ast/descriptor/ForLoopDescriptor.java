package exm.stc.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.Types.SwiftType;
import exm.stc.ast.Variable.DefType;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.ast.descriptor.VariableDeclaration.VariableDescriptor;
import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.InvalidAnnotationException;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UndefinedVariableException;
import exm.stc.common.exceptions.UserException;
import exm.stc.frontend.Context;
import exm.stc.frontend.LocalContext;
import exm.stc.frontend.TypeChecker;

/**
 * For loops are complex, so this class takes the AST of a for loop and 
 * makes it accessible in an easier to process form
 */
public class ForLoopDescriptor {
  private static final String WAITON_ANNOTATION = "waiton";
  private static final String VALID_ANN_TEXT = " waitonall and waiton=<loop var name>";
  private static final String WAITONALL_ANNOTATION = "waitonall";


  public static class LoopVar {
    public final Variable var;
    /** true if the variable was declared outside loop scope */
    public final boolean declaredOutsideLoop;
    public LoopVar(Variable var, boolean declaredOutsideLoop) {
      super();
      this.var = var;
      this.declaredOutsideLoop = declaredOutsideLoop;
    }
  }
  
  private final TypeChecker typecheck;
  private final SwiftAST body;
  private final SwiftAST condition;
  private final ArrayList<LoopVar> loopVars;
  private final HashMap<String, SwiftAST> initExprs;
  private final HashMap<String, SwiftAST> updateRules;
  
  /** Set of loop vars to block on */
  private final HashSet<String> blockingLoopVars;
  
  public SwiftAST getBody() {
    return body;
  }

  public SwiftAST getCondition() {
    return condition;
  }

  public List<LoopVar> getLoopVars() {
    return Collections.unmodifiableList(loopVars);
  }
  
  public List<Variable> getUnpackedLoopVars() {
    ArrayList<Variable> res = new ArrayList<Variable>(loopVars.size());
    for (LoopVar v: loopVars) {
      res.add(v.var);
    }
    return res;
  }

  public int loopVarCount() {
    return loopVars.size();
  }
  
  public Map<String, SwiftAST> getInitExprs() {
    return Collections.unmodifiableMap(initExprs);
  }

  public SwiftAST getInitExpr(String loopVarName) {
    return initExprs.get(loopVarName);
  }
  
  public Map<String, SwiftAST> getUpdateRules() {
    return Collections.unmodifiableMap(updateRules);
  }
  
  public SwiftAST getUpdateRule(String loopVarName) {
    return updateRules.get(loopVarName);
  }

  public ForLoopDescriptor(TypeChecker typecheck, SwiftAST body, 
                            SwiftAST condition, int loopVarCount) {
    this.body = body;
    this.typecheck = typecheck;
    loopVars = new ArrayList<LoopVar>(loopVarCount);
    initExprs = new HashMap<String, SwiftAST>();
    updateRules = new HashMap<String, SwiftAST>();
    blockingLoopVars = new HashSet<String>();
    this.condition = condition; 
  }
  
  public void addLoopVar(Variable loopVar, boolean declaredOutsideLoop,
                SwiftAST initExpr) {
    assert(loopVar != null);
    assert(initExpr != null);
    this.loopVars.add(new LoopVar(loopVar, declaredOutsideLoop));
    this.initExprs.put(loopVar.getName(), initExpr);
  }
  
  public void makeLoopVarBlocking(Context context, String loopVarName) 
                                        throws InvalidAnnotationException {
    assert(loopVarName != null);
    if (!initExprs.containsKey(loopVarName)) {
      throw new InvalidAnnotationException(context, loopVarName 
          + " specified to block on is not a loop variable, should be one of: "
          + Variable.nameList(this.getUnpackedLoopVars()).toString());
    }
    
    blockingLoopVars.add(loopVarName);
  }
  
  public boolean isLoopVarBlocking(Context context, String loopVarName) {
    return blockingLoopVars.contains(loopVarName);
  }
  
  /**
   * return a list, with one entry per loop variable, describing whether to 
   * block on loop variable before starting iteration
   */
  public List<Boolean> blockingLoopVarVector() {
    ArrayList<Boolean> res = new ArrayList<Boolean>(loopVars.size());
    for (LoopVar v: loopVars) {
      res.add(blockingLoopVars.contains(v.var.getName()));
    }
    return res;
  }
  
  public void addUpdateRule(Context errContext, String varName, 
                            SwiftAST expr) throws InvalidWriteException {
    if (this.updateRules.containsKey(varName)) {
      throw new InvalidWriteException(errContext, "Loop variable " + 
            varName + " is assigned twice in for " + "loop update");
            
    }
    this.updateRules.put(varName, expr);
  }
  
  /**
   * Check that for loop is sensible semantically
   */
  public void validateInit(Context context) throws UserException {
    //each loop var should have an update rule, with the correct type
    for (LoopVar lv: loopVars) {
      Variable v = lv.var;
      Variable outerV = context.getDeclaredVariable(v.getName());
      if (lv.declaredOutsideLoop) {
        if (outerV == null) {
          throw new UndefinedVariableException(context, v.toString() + " was not " +
          		"declared before for loop");
        }
      } else {
        if (outerV != null) {
          throw new DoubleDefineException(context, "Loop variable " + v.getName()
              + " is already defined in outer context: we don't allow shadowing");
        }
      }
      
      SwiftAST initExpr = initExprs.get(v.getName());
      if (initExpr == null) {
        throw new UserException(context, "loop variable " + v.getName()
            + " does not have an initial value");
      }
      SwiftType initExprType = typecheck.findSingleExprType(context, initExpr);
      typecheck.checkAssignment(context, initExprType, v.getType(), v.getName());
    }
  }
  
  public void validateCond(Context afterInitContext) throws UserException {
    //check condition type
    SwiftType condType = typecheck.findSingleExprType(afterInitContext, 
                                                                  condition);
    if ((!Types.isBool(condType)) && (!Types.isInt(condType))) {
      throw new TypeMismatchException(afterInitContext, "for loop condition "
          + "must be boolean or integer , but was " + condType.toString());
    }
    // TODO: check that condition refers to at least one of the loop vars
  }
  
  /**
   * Call after all loop body vars are declared
   * @param loopBodyContext
   * @throws UserException
   */
  public void validateUpdates(Context loopBodyContext) throws UserException {
    for (LoopVar lv: loopVars) {
      Variable v = lv.var;
      SwiftAST upExpr = updateRules.get(v.getName());
      if (upExpr == null) {
        throw new UserException(loopBodyContext, "loop variable " + v.getName()
            + " must be updated between iterations");
      }
      SwiftType upExprType = typecheck.findSingleExprType(loopBodyContext, 
                                                                  upExpr);
      typecheck.checkAssignment(loopBodyContext, upExprType, v.getType(), 
                                                              v.getName());
    }

    if (updateRules.size() > loopVars.size()) {
      throw new UserException(loopBodyContext, "An update rule is updating " +
          " a non-loop variable");
    }
  }
  
  public static ForLoopDescriptor fromAST(TypeChecker typecheck, Context context, 
                                                                SwiftAST tree)
      throws UndefinedTypeException, InvalidSyntaxException, UserException {
    assert(tree.getType() == ExMParser.FOR_LOOP);
    assert(tree.getChildCount() >= 4);
    SwiftAST init = tree.child(0); 
    SwiftAST cond = tree.child(1);
    SwiftAST update = tree.child(2);
    SwiftAST body = tree.child(3);
    ArrayList<SwiftAST> annotations = new ArrayList<SwiftAST>();
    for (int i = 4; i < tree.getChildCount(); i++) {
      SwiftAST ann = tree.child(i);
      assert(ann.getType() == ExMParser.ANNOTATION);
      annotations.add(ann);
    }
     
    assert(init.getType() == ExMParser.FOR_LOOP_INIT);
    assert(update.getType() == ExMParser.FOR_LOOP_UPDATE);
    
    int loopVarCount = init.getChildCount();
    if (loopVarCount == 0) {
      throw new STCRuntimeError("Must have at least one loop variable " +
          " in for loop for now");
    }
    
    ForLoopDescriptor forLoop = new ForLoopDescriptor(typecheck, body, cond,
                                                            loopVarCount);
    // Process the initializer
    for (int i = 0; i < loopVarCount; i++) {
      SwiftAST loopVarInit = init.child(i);
      int initType = loopVarInit.getType(); 
      if (initType == ExMParser.DECLARATION) {
        VariableDeclaration decl = VariableDeclaration.fromAST(context, 
                        typecheck, loopVarInit);
        assert(decl.count() == 1);
        VariableDescriptor loopVarDesc = decl.getVar(0);
        SwiftAST expr = decl.getVarExpr(0);
        assert(loopVarDesc.getMappingExpr() == null); 
        assert(expr != null);  // shouldn't be mapped, enforced by syntax
        Variable loopVar = new Variable(loopVarDesc.getType(),
                loopVarDesc.getName(), VariableStorage.STACK,
                DefType.LOCAL_USER, null);
        forLoop.addLoopVar(loopVar, false, expr);
      } else if (initType == ExMParser.FOR_LOOP_ASSIGN) {
        // Not declaring new variable, using variable from outside loop
        assert(loopVarInit.getChildCount() == 2);
        SwiftAST lvalTree = loopVarInit.child(0);
        SwiftAST expr = loopVarInit.child(1);
        assert(lvalTree.getType() == ExMParser.ID);
        String varName = lvalTree.getText();
        Variable var = context.getDeclaredVariable(varName);
        if (var == null) {
          throw new UndefinedVariableException(context, "Variable in " +
          		"for loop: " + varName + " has not been declared"); 
        }
        forLoop.addLoopVar(var, true, expr);
        
      } else {
        throw new STCRuntimeError("Don't support initializer type "
            + ExMParser.tokenNames[initType] + " yet ");
      }
    }
    
    // Process the updater
    int updateCount = update.getChildCount();
    for (int i = 0; i < updateCount; i++) {
      SwiftAST updateR = update.child(i);
      assert(updateR.getType() == ExMParser.FOR_LOOP_ASSIGN);
      assert(updateR.getChildCount() == 2);
      SwiftAST varNameTree = updateR.child(0);
      assert(varNameTree.getType() == ExMParser.ID);
      String varName = varNameTree.getText();
      SwiftAST updateExpr = updateR.child(1);
      forLoop.addUpdateRule(context, varName, updateExpr);
    }
    
    // Process annotations
    for (SwiftAST ann: annotations) {
      assert(ann.getChildCount() == 1 || ann.getChildCount() == 2);
      if (ann.getChildCount() == 1) {
        String annText = ann.child(0).getText();
        if (annText.equals(WAITONALL_ANNOTATION)) {
          for (LoopVar v: forLoop.getLoopVars()) {
            forLoop.makeLoopVarBlocking(context, v.var.getName());
          }
        } else {
          throw new InvalidAnnotationException(context, "Unknown annotation " +
          		annText + " on for loop, only " + VALID_ANN_TEXT + " supported ");
        }
      } else {
        String annName = ann.child(0).getText();
        String annVal = ann.child(1).getText();
        if (annName.equals(WAITON_ANNOTATION)) {
          if (ann.child(1).getType() == ExMParser.ID) {
            forLoop.makeLoopVarBlocking(context, annVal);
          } else {
            throw new InvalidAnnotationException(context, "Expected value" +
            		"for " + WAITON_ANNOTATION + " to be a variable name, but was"
            + annVal);
          }
        } else {
          throw new InvalidAnnotationException(context, "Unknown annotation " +
              annName + " on for loop, only " + VALID_ANN_TEXT + " supported ");
        }
      }
    }
    
    forLoop.validateInit(context);
    return forLoop;
  }


  public Context createBodyContext(Context context) throws UserException {
    Context bodyContext = new LocalContext(context);
    for (LoopVar lv: this.getLoopVars()) {
      if (!lv.declaredOutsideLoop) {
        Variable v = lv.var;
        bodyContext.declareVariable(v.getType(), v.getName(), v.getStorage(), 
                                              v.getDefType(), v.getMapping());
      }
    }
    return bodyContext;
  }
}