/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.frontend.tree;

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
  private final boolean deepWait;
  
  public Wait(ArrayList<SwiftAST> waitExprs, SwiftAST block,
              boolean deepWait) {
    this.waitExprs = waitExprs;
    this.block = block;
    this.deepWait = deepWait;
  }
  public List<SwiftAST> getWaitExprs() {
    return Collections.unmodifiableList(waitExprs);
  }
  
  public SwiftAST getBlock() {
    return block;
  }
  
  public boolean isDeepWait() {
    return deepWait;
  }
  
  public static Wait fromAST(Context context, SwiftAST tree) 
                                    throws UserException {
    boolean deepWait;
    if (tree.getType() == ExMParser.WAIT_STATEMENT) {
      deepWait = false;
    } else {
      assert(tree.getType() == ExMParser.WAIT_DEEP_STATEMENT);
      deepWait = true;
    }
    assert(tree.getChildCount() == 2);
    SwiftAST exprs = tree.child(0);
    SwiftAST block = tree.child(1);
    assert(exprs.getType() == ExMParser.ARGUMENT_LIST);
    if (exprs.getChildCount() == 0) {
      throw new UserException(context, "Wait statement with 0 arguments");
    }
    
    ArrayList<SwiftAST> waitExprs = new ArrayList<SwiftAST>();
    for (SwiftAST expr: exprs.children()) {
      waitExprs.add(expr);
    }
    
    
    return new Wait(waitExprs, block, deepWait);
  }
  
}
