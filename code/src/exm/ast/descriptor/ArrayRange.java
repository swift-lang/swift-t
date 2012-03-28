package exm.ast.descriptor;

import java.util.Arrays;
import java.util.List;

import exm.ast.Context;
import exm.ast.SwiftAST;
import exm.ast.TypeChecker;
import exm.ast.Types;
import exm.ast.Types.SwiftType;
import exm.parser.antlr.ExMParser;
import exm.parser.util.InvalidSyntaxException;
import exm.parser.util.TypeMismatchException;
import exm.parser.util.UserException;

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

  public void typeCheck(Context context, TypeChecker typecheck) 
                                            throws UserException {
    SwiftType startT = typecheck.findSingleExprType(context, start);
    SwiftType endT = typecheck.findSingleExprType(context, end);
    if (step != null) {
      SwiftType stepT = typecheck.findSingleExprType(context, step);
      typeCheck(context, stepT, "step");
    }
    typeCheck(context, startT, "start");
    typeCheck(context, endT, "start");
  }
  
  public void typeCheck(Context context, SwiftType type, String name) 
  throws TypeMismatchException {
    if (!Types.isInt(type)) {
      throw new TypeMismatchException(context, "Expected " + name + " in " +
      		" range operators to be an integer, but type was " + type.typeName());
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
