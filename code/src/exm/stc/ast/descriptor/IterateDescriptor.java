package exm.stc.ast.descriptor;

import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.frontend.Context;
import exm.stc.frontend.LocalContext;

public class IterateDescriptor {
  
  private final SwiftAST body;
  private final SwiftAST cond;
  private final Var loopVar;

  public IterateDescriptor(SwiftAST body, SwiftAST cond, 
      String loopVarName) {
    this.body = body;
    this.cond = cond;
    this.loopVar = new Var(Types.F_INT, loopVarName, 
        VarStorage.STACK, DefType.INARG, null); 
  }
  
  public static IterateDescriptor fromAST(Context context, SwiftAST tree) {
    // Extract info from tree
    assert(tree.getType() == ExMParser.ITERATE);
    assert(tree.getChildCount() == 3);
    assert(tree.child(0).getType() == ExMParser.ID);
    String loopVarName = tree.child(0).getText();
    SwiftAST body = tree.child(1);
    assert(body.getType() == ExMParser.BLOCK);
    SwiftAST cond = tree.child(2);
    
    IterateDescriptor iterateLoop = new IterateDescriptor(body, 
                                                      cond, loopVarName);
        
    return iterateLoop;
  }

  public SwiftAST getBody() {
    return body;
  }

  public SwiftAST getCond() {
    return cond;
  }

  public Var getLoopVar() {
    return loopVar;
  }
  
  public Context createBodyContext(Context context) throws UserException {
    Context bodyContext = new LocalContext(context);
    Var v = loopVar;
    bodyContext.declareVariable(v.type(), v.name(), v.storage(), 
                                              v.defType(), v.mapping());
    return bodyContext;
  }
}
