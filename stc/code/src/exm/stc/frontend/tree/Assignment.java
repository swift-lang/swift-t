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

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.util.Pair;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;

public class Assignment {

  public static enum AssignOp {
    ASSIGN, // Regular = assignment
    APPEND, // Append += assignment
    ;
  }

  public final AssignOp op;
  public final List<SwiftAST> rValExprs;
  public final List<LValue> lVals;
  public Assignment(AssignOp op, List<LValue> lVals,
                    List<SwiftAST> rValExprs) {
    this.op = op;
    this.lVals = Collections.unmodifiableList(new ArrayList<LValue>(lVals));
    this.rValExprs = Collections.unmodifiableList(
                                         new ArrayList<SwiftAST>(rValExprs));
  }

  /**
   * Try to match up lVals and corresponding rVals
   * @return List of paired lvals and rvals
   * @throws UserException
   */
  public List<Pair<List<LValue>, SwiftAST>> getMatchedAssignments(Context context)
                  throws UserException {
    assert(!rValExprs.isEmpty());
    if (rValExprs.size() == 1) {
      // Single expression on RHS.  There still might be multiple lvalues
      // if the expression was a function with multiple return values
      return Collections.singletonList(Pair.create(lVals, rValExprs.get(0)));
    } else {
      // Match up RVals and LVals
      if (rValExprs.size() != lVals.size()) {
        throw new UserException(context, "number of expressions on " +
                " right hand side of assignment (" + rValExprs.size() + ") does " +
                    "not match the number of targets on the left hand size (" +
                lVals.size());
      }
      List<Pair<List<LValue>, SwiftAST>> paired =
                new ArrayList<Pair<List<LValue>,SwiftAST>>();
      for (int i = 0; i < lVals.size(); i++) {
        paired.add(Pair.create(lVals.subList(i, i+1), rValExprs.get(i)));
      }
      return paired;
    }
  }


  public static final Assignment fromAST(Context context, SwiftAST tree) {
    assert(tree.getType() == ExMParser.ASSIGN_EXPRESSION);
    assert(tree.childCount() >= 3);
    AssignOp assignOp;
    int assignOpTok = tree.child(0).getType();
    switch (assignOpTok) {
      case ExMParser.ASSIGN:
        assignOp = AssignOp.ASSIGN;
        break;
      case ExMParser.APPEND:
        assignOp = AssignOp.APPEND;
        break;
      default:
        throw new STCRuntimeError("Unknown assign op: " +
                                  LogHelper.tokName(assignOpTok));
    }

    SwiftAST lValsT = tree.child(1);
    List<LValue> lVals = LValue.extractLVals(context, lValsT);

    List<SwiftAST> rValExprs = tree.children(2);
    return new Assignment(assignOp, lVals, rValExprs);
  }
}
