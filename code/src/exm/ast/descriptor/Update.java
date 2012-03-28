package exm.ast.descriptor;

import exm.ast.Builtins.UpdateMode;
import exm.ast.Context;
import exm.ast.SwiftAST;
import exm.ast.TypeChecker;
import exm.ast.Types;
import exm.ast.Types.ScalarUpdateableType;
import exm.ast.Types.SwiftType;
import exm.ast.Variable;
import exm.parser.antlr.ExMParser;
import exm.parser.util.TypeMismatchException;
import exm.parser.util.UndefinedVariableException;
import exm.parser.util.UserException;

public class Update {
  public Update(Variable target, SwiftAST expr, UpdateMode mode) {
    super();
    this.target = target;
    this.expr = expr;
    this.mode = mode;
  }

  private final Variable target;
  private final SwiftAST expr;
  private final UpdateMode mode;
  
  
  public Variable getTarget() {
    return target;
  }

  public SwiftAST getExpr() {
    return expr;
  }

  public UpdateMode getMode() {
    return mode;
  }

  public SwiftType typecheck(Context context, TypeChecker typecheck) 
                                                throws UserException {
    SwiftType expected = ScalarUpdateableType.asScalarFuture(
                            this.target.getType());
    
    SwiftType exprType = typecheck.findSingleExprType(context, expr, expected);
    if (expected.equals(exprType)) {
      return exprType;
    } else {
      throw new TypeMismatchException(context, "in update of variable "
          + target.getName() + " with type " + target.getType().typeName()
          + " expected expression of type " + expected.typeName() 
          + " but got expression of type " + exprType);
    }
  }
  
  public static Update fromAST(Context context, SwiftAST tree) 
          throws UserException {
    assert(tree.getType() == ExMParser.UPDATE);
    assert(tree.getChildCount() == 3);
    SwiftAST cmd = tree.child(0);
    SwiftAST var = tree.child(1);
    SwiftAST expr = tree.child(2);
    
    assert(cmd.getType() == ExMParser.ID);
    assert(var.getType() == ExMParser.ID);
    
    
    UpdateMode mode = UpdateMode.fromString(context, cmd.getText());
    assert(mode != null);
    
    Variable v = context.getDeclaredVariable(var.getText());
    if (v == null) {
      throw new UndefinedVariableException(context, "variable "
          + var.getType() + " is not defined");
    }
    
    if (!Types.isScalarUpdateable(v.getType())) {
      throw new TypeMismatchException(context, "can only update" +
          " updateable variables: variable " + v.getName() + " had " +
          " type " + v.getType().typeName());
    }
    
    return new Update(v, expr, mode);
  }
                                
}
