package exm.ast.descriptor;

import exm.ast.Context;
import exm.ast.LocalContext;
import exm.ast.SwiftAST;
import exm.ast.TypeChecker;
import exm.ast.Types;
import exm.ast.Variable;
import exm.ast.Variable.DefType;
import exm.ast.Variable.VariableStorage;
import exm.parser.antlr.ExMParser;
import exm.parser.util.UserException;

public class IterateDescriptor {
  
  private final TypeChecker typecheck;
  private final SwiftAST body;
  private final SwiftAST cond;
  private final Variable loopVar;

  public IterateDescriptor(TypeChecker typecheck, SwiftAST body, SwiftAST cond, 
      String loopVarName) {
    this.typecheck = typecheck;
    this.body = body;
    this.cond = cond;
    this.loopVar = new Variable(Types.FUTURE_INTEGER, loopVarName, 
        VariableStorage.STACK, DefType.INARG, null); 
  }
  
  public static IterateDescriptor fromAST(TypeChecker typecheck, Context context,
      SwiftAST tree) {
    // Extract info from tree
    assert(tree.getType() == ExMParser.ITERATE);
    assert(tree.getChildCount() == 3);
    assert(tree.child(0).getType() == ExMParser.ID);
    String loopVarName = tree.child(0).getText();
    SwiftAST body = tree.child(1);
    assert(body.getType() == ExMParser.BLOCK);
    SwiftAST cond = tree.child(2);
    
    IterateDescriptor iterateLoop = new IterateDescriptor(typecheck, body, 
                                                      cond, loopVarName);
        
    return iterateLoop;
  }

  public TypeChecker getTypecheck() {
    return typecheck;
  }

  public SwiftAST getBody() {
    return body;
  }

  public SwiftAST getCond() {
    return cond;
  }

  public Variable getLoopVar() {
    return loopVar;
  }
  
  public Context createBodyContext(Context context) throws UserException {
    Context bodyContext = new LocalContext(context);
    Variable v = loopVar;
    bodyContext.declareVariable(v.getType(), v.getName(), v.getStorage(), 
                                              v.getDefType(), v.getMapping());
    return bodyContext;
  }
}
