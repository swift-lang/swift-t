package exm.stc.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types.ExprType;
import exm.stc.common.util.Pair;
import exm.stc.frontend.Context;
import exm.stc.frontend.TypeChecker;

public class Assignment {
  public final List<SwiftAST> rValExprs;
  public final List<LValue> lVals;
  public Assignment(List<LValue> lVals, List<SwiftAST> rValExprs) {
    super();
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
  
  /**
   * Helper method to find expression type
   * @param context
   * @param lVals
   * @param rValExpr
   * @return
   * @throws UserException 
   */
  public static ExprType checkAssign(Context context,
                 List<LValue> lVals, SwiftAST rValExpr) throws UserException {
    ExprType rValTs = TypeChecker.findExprType(context, rValExpr);
    if (rValTs.elems() != lVals.size()) {
      throw new TypeMismatchException(context, "Needed " + rValTs.elems()
              + " " + "assignment targets on LHS of assignment, but "
              + lVals.size() + " were present");
    }
    return rValTs;
  }
  
  public static final Assignment fromAST(Context context, SwiftAST tree) {
    List<LValue> lVals = LValue.extractLVals(context, tree.child(0));
    List<SwiftAST> rValExprs = tree.children(1);
    return new Assignment(lVals, rValExprs);
  }
}
