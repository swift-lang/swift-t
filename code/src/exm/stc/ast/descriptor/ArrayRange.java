package exm.stc.ast.descriptor;

import java.util.Arrays;
import java.util.List;

import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.frontend.Context;
import exm.stc.frontend.TypeChecker;

public class ArrayRange {
  private final SwiftAST start;
  private final SwiftAST end;
  private final SwiftAST step;
  
  
  public List<SwiftAST> getArgs() {
    if (step != null) {
      return Arrays.asList(start, end, step);
    } else {
      return Arrays.asList(start, end);
    }
  }
  public SwiftAST getStart() {
    return start;
  }

  public SwiftAST getEnd() {
    return end;
  }

  public SwiftAST getStep() {
    return step;
  }

  public ArrayRange(SwiftAST start, SwiftAST end, SwiftAST step) {
    super();
    this.start = start;
    this.end = end;
    this.step = step;
  }

  public void typeCheck(Context context) throws UserException {
    Type startT = TypeChecker.findSingleExprType(context, start);
    Type endT = TypeChecker.findSingleExprType(context, end);
    if (step != null) {
      Type stepT = TypeChecker.findSingleExprType(context, step);
      typeCheck(context, stepT, "step");
    }
    typeCheck(context, startT, "start");
    typeCheck(context, endT, "start");
  }
  
  public void typeCheck(Context context, Type exprType, String name) 
  throws TypeMismatchException {
    if (!exprType.assignableTo(Types.F_INT)) {
      throw new TypeMismatchException(context, "Expected " + name + " in" +
              " range operators to be int, but type was " + exprType.typeName());
    }
  }
  
  public static ArrayRange fromAST(Context context, SwiftAST tree) 
                                              throws InvalidSyntaxException {
    assert(tree.getType() == ExMParser.ARRAY_RANGE);
    
    int n = tree.getChildCount();
    if (n < 2 || n > 3) {
      throw new InvalidSyntaxException(context, "Array range operator" +
              " can take two or three parameters [start:end] or [start:end:step]"
        + " but this array range had " + n + " parameters");
    }
    
    SwiftAST startT = tree.child(0);
    SwiftAST endT = tree.child(1);
    SwiftAST stepT;
    if (n == 3) {
      stepT = tree.child(2);
    } else {
      stepT = null;
    }
    return new ArrayRange(startT, endT, stepT);
  }
}
