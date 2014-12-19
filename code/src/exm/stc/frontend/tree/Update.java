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
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ScalarUpdateableType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.frontend.Context;
import exm.stc.frontend.TypeChecker;

public class Update {
  public Update(Var target, SwiftAST expr, Operators.UpdateMode mode) {
    super();
    this.target = target;
    this.expr = expr;
    this.mode = mode;
  }

  private final Var target;
  private final SwiftAST expr;
  private final Operators.UpdateMode mode;


  public Var getTarget() {
    return target;
  }

  public SwiftAST getExpr() {
    return expr;
  }

  public Operators.UpdateMode getMode() {
    return mode;
  }

  /**
   * @param context
   * @return Concrete type of Rval expression
   * @throws UserException
   */
  public Type typecheck(Context context) throws UserException {
    Type expected = ScalarUpdateableType.asScalarFuture(
                            this.target.type());

    Type exprType = TypeChecker.findExprType(context, expr);
    if (exprType.assignableTo(expected)) {
      return expected;
    } else {
      throw new TypeMismatchException(context, "in update of variable "
          + target.name() + " with type " + target.type().typeName()
          + " expected expression of type " + expected.typeName()
          + " but got expression of type " + exprType);
    }
  }

  public static Update fromAST(Context context, SwiftAST tree)
          throws UserException {
    assert(tree.getType() == ExMParser.UPDATE);
    assert(tree.getChildCount() == 3);
    SwiftAST cmd = tree.child(0);
    SwiftAST var = tree.child(1);
    SwiftAST expr = tree.child(2);

    assert(cmd.getType() == ExMParser.ID);
    assert(var.getType() == ExMParser.ID);


    Operators.UpdateMode mode = Operators.UpdateMode.fromString(context, cmd.getText());
    assert(mode != null);

    Var v = context.lookupVarUser(var.getText());

    if (!Types.isPrimUpdateable(v.type())) {
      throw new TypeMismatchException(context, "can only update" +
          " updateable variables: variable " + v.name() + " had " +
          " type " + v.type().typeName());
    }

    return new Update(v, expr, mode);
  }

}
