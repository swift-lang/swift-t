package exm.ast.descriptor;

import exm.ast.Context;
import exm.ast.SwiftAST;
import exm.ast.TypeChecker;
import exm.ast.Types;
import exm.ast.Types.SwiftType;
import exm.parser.util.ParserRuntimeException;
import exm.parser.util.TypeMismatchException;
import exm.parser.util.UserException;

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
  
  public SwiftType getCondType(Context context, TypeChecker typecheck) 
                                                    throws UserException {
    SwiftType condType = typecheck.findSingleExprType(context, condition);
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
      throw new ParserRuntimeException("if: child count > 3 or < 2");
    SwiftAST condition = tree.child(0);
    SwiftAST thenBlock = tree.child(1);

    boolean hasElse = (count == 3);
    SwiftAST elseBlock = hasElse ? tree.child(2) : null;

    return new If(condition, thenBlock, elseBlock);
  }
}
