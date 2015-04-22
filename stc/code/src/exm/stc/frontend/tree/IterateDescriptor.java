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

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.frontend.Context;
import exm.stc.frontend.LocalContext;

public class IterateDescriptor {

  private final SwiftAST body;
  private final SwiftAST cond;
  private final Var loopVar;

  public IterateDescriptor(SwiftAST body, SwiftAST cond,
      Var loopVar) {
    this.body = body;
    this.cond = cond;
    this.loopVar = loopVar;
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

    Var loopVar = new Var(Types.F_INT, loopVarName, Alloc.STACK,
        DefType.LOCAL_USER, VarProvenance.userVar(context.getSourceLoc()));
    IterateDescriptor iterateLoop = new IterateDescriptor(body, cond, loopVar);

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

  public Context createIterContext(Context context) throws UserException {
    Context bodyContext = LocalContext.fnSubcontext(context);
    bodyContext.declareVariable(loopVar);
    return bodyContext;
  }
}
