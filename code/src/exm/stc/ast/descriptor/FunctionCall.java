package exm.stc.ast.descriptor;

import java.util.List;

import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.UndefinedFunctionException;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.frontend.Context;

public class FunctionCall {
  private final String function;
  private final List<SwiftAST> args;
  private final FunctionType type;
  
  private FunctionCall(String function, List<SwiftAST> args,
                       FunctionType type) {
    super();
    this.function = function;
    this.args = args;
    this.type = type;
  }

  public String function() {
    return function;
  }

  public List<SwiftAST> args() {
    return args;
  }

  public FunctionType type() {
    return type;
  }

  public static FunctionCall fromAST(Context context, SwiftAST tree) 
        throws UndefinedFunctionException {
    assert(tree.getChildCount() >= 2 && tree.getChildCount() <= 3);
    String f = tree.child(0).getText();
    SwiftAST arglist = tree.child(1); 
    
    FunctionType ftype = context.lookupFunction(f);
    if (ftype == null) {
      throw UndefinedFunctionException.unknownFunction(context, f);
    }
    
    return new FunctionCall(f, arglist.children(), ftype);
  }

}
