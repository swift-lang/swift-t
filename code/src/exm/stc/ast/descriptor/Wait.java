package exm.stc.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.UserException;
import exm.stc.frontend.Context;

public class Wait {
  private final ArrayList<SwiftAST> waitExprs;
  private final SwiftAST block;
  public Wait(ArrayList<SwiftAST> waitExprs, SwiftAST block) {
    super();
    this.waitExprs = waitExprs;
    this.block = block;
  }
  public List<SwiftAST> getWaitExprs() {
    return Collections.unmodifiableList(waitExprs);
  }
  public SwiftAST getBlock() {
    return block;
  }
  
  public static Wait fromAST(Context context, SwiftAST tree) 
                                    throws UserException {
    assert(tree.getType() == ExMParser.WAIT_STATEMENT);
    assert(tree.getChildCount() == 2);
    SwiftAST exprs = tree.child(0);
    SwiftAST block = tree.child(1);
    assert(exprs.getType() == ExMParser.ARGUMENT_LIST);
    if (exprs.getChildCount() == 0) {
      throw new UserException(context, "Wait statement with 0 arguments");
    }
    
    ArrayList<SwiftAST> waitExprs = new ArrayList<SwiftAST>();
    for (int i = 0; i < exprs.getChildCount(); i++) {
      waitExprs.add(exprs.child(i));
    }
    
    
    return new Wait(waitExprs, block);
  }
  
}
