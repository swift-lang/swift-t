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
import java.util.Arrays;
import java.util.List;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.util.Pair;
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

  /**
   * Typecheck range arguments
   * @param context
   * @throws UserException
   * @return the type of elements of the range
   */
  public Type rangeType(Context context) throws UserException {
    List<Pair<String, Type>> types = new ArrayList<Pair<String, Type>>();

    Type startT = TypeChecker.findExprType(context, start);
    types.add(Pair.create("start", startT));

    Type endT = TypeChecker.findExprType(context, end);
    types.add(Pair.create("end", endT));

    if (step != null) {
      Type stepT = TypeChecker.findExprType(context, step);
      types.add(Pair.create("step", stepT));
    }

    boolean allInts = true;
    for (Pair<String, Type> p: types) {
      Type t = p.val2;
      if (!t.assignableTo(Types.F_INT)) {
        allInts = false;
        break;
      }
    }

    Type rangeT = allInts ? Types.F_INT : Types.F_FLOAT;

    for (Pair<String, Type> p: types) {
      String name = p.val1;
      Type t = p.val2;
      if (!t.assignableTo(rangeT)) {
        throw new TypeMismatchException(context, "Expected " + name + " in" +
                " range operators to have type " + rangeT.typeName() +
                " but type was " + startT.typeName());
      }
    }

    return rangeT;
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
