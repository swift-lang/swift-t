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
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.frontend.Context;
import exm.stc.frontend.TypeChecker;

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

  public Type getCondType(Context context) throws UserException {
    Type condType = TypeChecker.findExprType(context, condition);
    if (condType.assignableTo(Types.F_BOOL)) {
      return Types.F_BOOL;
    } else if (condType.assignableTo(Types.F_INT)) {
      return Types.F_INT;
    } else {
      throw new TypeMismatchException(context, "if statement condition must "
              + "be of type boolean or int, but was " + condType);
    }
  }

  public static If fromAST(Context context, SwiftAST tree) {
    int count = tree.getChildCount();
    if (count < 2 || count > 3)
      throw new STCRuntimeError("if: child count > 3 or < 2");
    SwiftAST condition = tree.child(0);
    SwiftAST thenBlock = tree.child(1);

    boolean hasElse = (count == 3);
    SwiftAST elseBlock = hasElse ? tree.child(2) : null;

    return new If(condition, thenBlock, elseBlock);
  }
}
