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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.frontend.Context;
import exm.stc.frontend.typecheck.TypeChecker;

public class Switch {
  private final int caseCount;
  private final SwiftAST switchExpr;
  private final List<Integer> caseLabels;
  private final boolean hasDefault;
  private final List<SwiftAST> caseBodies;

  public int getCaseCount() {
    return caseCount;
  }

  public SwiftAST getSwitchExpr() {
    return switchExpr;
  }

  public List<Integer> getCaseLabels() {
    return caseLabels;
  }

  public boolean hasDefault() {
    return hasDefault;
  }

  public List<SwiftAST> getCaseBodies() {
    return caseBodies;
  }

  public Switch(SwiftAST switchExpr, int caseCount, List<Integer> caseLabels,
      boolean hasDefault, List<SwiftAST> caseBodies) {
    this.switchExpr = switchExpr;
    this.caseCount = caseCount;
    this.caseLabels = caseLabels;
    this.hasDefault = hasDefault;
    this.caseBodies = caseBodies;
  }

  public void typeCheck(Context context) throws UserException {
    if (!TypeChecker.findExprType(context, switchExpr).assignableTo(
        Types.F_INT)) {
      throw new TypeMismatchException(context, "switch variable must "
           + "be of type int");
    }

  }

  public static Switch fromAST(Context context, SwiftAST tree) throws UserException {
    assert(tree.getType() == ExMParser.SWITCH_STATEMENT);

    int count = tree.getChildCount();
    if (count < 1) {
      throw new STCRuntimeError("switch: no children found");
    }
    SwiftAST switchExpr = tree.child(0);

    int caseCount = count - 1;

    List<SwiftAST> caseBodies = new ArrayList<SwiftAST>(caseCount);
    // All of the integer labels: will be one shorter than casecount if there is
    // a default
    // case
    List<Integer> caseLabels = new ArrayList<Integer>(caseCount);
    Set<Integer> caseLabelSet = new HashSet<Integer>(caseCount);

    boolean hasDefault = false;

    for (int i = 1; i <= caseCount; i++) {
      SwiftAST thisCase = tree.child(i);
      if (thisCase.getType() == ExMParser.DEFAULT) {
        if (thisCase.getChildCount() != 1) {
          throw new STCRuntimeError("default case: should only have "
              + "one child, but had " + thisCase.getChildCount());
        }
        if (i != caseCount) {
          throw new UserException(context,
              "default label must come last in switch statement");
        }
        hasDefault = true;
        caseBodies.add(thisCase.child(0));
      } else {

        // Numeric case label
        if (thisCase.getChildCount() != 2) {
          throw new STCRuntimeError("case: should have two children,"
              + " but had " + thisCase.getChildCount());
        }

        String caseStr = thisCase.child(0).getText();
        caseBodies.add(thisCase.child(1));
        try {
          Integer caseNum = Integer.valueOf(caseStr);
          if (caseLabelSet.contains(caseNum)) {
            throw new UserException(context, "Duplicate case label "
                                                  + caseNum);
          }
          caseLabels.add(caseNum);
          caseLabelSet.add(caseNum);
        } catch (NumberFormatException e) {
          throw new STCRuntimeError(
              "switch: grammar should only permit "
                  + "integer literals in case statement, but got " + caseStr);
        }
      }
    }

    assert((hasDefault && caseLabels.size() + 1 == caseBodies.size())
        || (!hasDefault && caseLabels.size() == caseBodies.size()));


    return new Switch(switchExpr, caseCount, caseLabels, hasDefault,
                          caseBodies);
  }
}
