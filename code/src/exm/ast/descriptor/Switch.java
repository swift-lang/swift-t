package exm.ast.descriptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import exm.ast.Context;
import exm.ast.SwiftAST;
import exm.ast.TypeChecker;
import exm.ast.Types;
import exm.parser.antlr.ExMParser;
import exm.parser.util.ParserRuntimeException;
import exm.parser.util.TypeMismatchException;
import exm.parser.util.UserException;

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

  public void typeCheck(Context context, TypeChecker typecheck) 
        throws UserException {
    if (!typecheck.findSingleExprType(context, switchExpr).equals(
        Types.FUTURE_INTEGER)) {
      throw new TypeMismatchException(context, "switch variable must "
           + "be of type int");
    }

  }
  
  public static Switch fromAST(Context context, SwiftAST tree) throws UserException {
    assert(tree.getType() == ExMParser.SWITCH_STATEMENT);

    int count = tree.getChildCount();
    if (count < 1) {
      throw new ParserRuntimeException("switch: no children found");
    }
    SwiftAST switchExpr = tree.child(0);

    int caseCount = count - 1;
    
    List<SwiftAST> caseBodies = new ArrayList<SwiftAST>(caseCount);
    // All of the integer labels: will be one shorter than casecount if there is
    // a default
    // case
    List<Integer> caseLabels = new ArrayList<Integer>(caseCount);
    // TODO: detect duplicate cases?
    Set<Integer> caseLabelSet = new HashSet<Integer>(caseCount);
    
    boolean hasDefault = false;
    
    for (int i = 1; i <= caseCount; i++) {
      SwiftAST thisCase = tree.child(i);
      if (thisCase.getType() == ExMParser.DEFAULT) {
        if (thisCase.getChildCount() != 1) {
          throw new ParserRuntimeException("default case: should only have "
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
          throw new ParserRuntimeException("case: should have two children,"
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
          throw new ParserRuntimeException(
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
