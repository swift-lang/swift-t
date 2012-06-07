package exm.stc.ast.descriptor;

import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.frontend.Context;
import exm.stc.frontend.TypeChecker;

public class If {

  private final SwiftAST condition;
  private final SwiftAST thenBlock;
  public SwiftAST getCondition() {
    return condition;
  }

  public SwiftAST getThenBlock() {
    return thenBlock;
  }

  public SwiftAST getElseBlock() {
    return elseBlock;
  }

  public If(SwiftAST condition, SwiftAST thenBlock, SwiftAST elseBlock) {
    super();
    this.condition = condition;
    this.thenBlock = thenBlock;
    this.elseBlock = elseBlock;
  }

  private final SwiftAST elseBlock;
  
  public boolean hasElse() {
    return elseBlock != null;
  }
  
  public SwiftType getCondType(Context context) throws UserException {
    SwiftType condType = TypeChecker.findSingleExprType(context, condition);
    if (!(condType.equals(Types.FUTURE_BOOLEAN) ||
        condType.equals(Types.FUTURE_INTEGER))) {
      throw new TypeMismatchException(context, "if statement condition must "
              + "be of type boolean or integer");
    }
    return condType;
  }
  
  public static If fromAST(Context context, SwiftAST tree) {
    int count = tree.getChildCount();
    if (count < 2 || count > 3)
      throw new STCRuntimeError("if: child count > 3 or < 2");
    SwiftAST condition = tree.child(0);
    SwiftAST thenBlock = tree.child(1);

    boolean hasElse = (count == 3);
    SwiftAST elseBlock = hasElse ? tree.child(2) : null;

    return new If(condition, thenBlock, elseBlock);
  }
}
