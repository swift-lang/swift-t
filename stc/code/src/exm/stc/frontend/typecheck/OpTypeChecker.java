package exm.stc.frontend.typecheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UndefinedOperatorException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Operators;
import exm.stc.common.lang.Operators.Op;
import exm.stc.common.lang.Operators.OpInputType;
import exm.stc.common.lang.Operators.OpType;
import exm.stc.common.lang.Types.TupleType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.util.Pair;
import exm.stc.frontend.Context;

public class OpTypeChecker {

  public static Type findOperatorResultType(Context context, SwiftAST tree)
      throws TypeMismatchException, UserException {
    List<MatchedOp> ops = getOpsFromTree(context, tree, null);
    assert(ops != null);
    assert(ops.size() > 0);

    // Handle possibility of multiple return types
    List<Type> alternatives = new ArrayList<Type>(ops.size());
    for (MatchedOp op: ops) {
      alternatives.add(op.op.type.out());
    }
    return UnionType.makeUnion(alternatives);
  }

  /**
   * Get the final operator match using output argument info.
   *
   * @param context
   * @param tree
   * @param outType
   * @return
   * @throws UserException
   */
  public static MatchedOp getOpFromTree(Context context,
      SwiftAST tree, Type outType) throws UserException {
    List<MatchedOp> matches = getOpsFromTree(context, tree, outType, null,
                                             true);
    assert(matches.size() > 0);
    return matches.get(0);
  }

  private static String extractOpName(SwiftAST opTree) {
    int tok = opTree.child(0).getType();
    try {
      return ExMParser.tokenNames[tok].toLowerCase();
    } catch (IndexOutOfBoundsException ex) {
      throw new STCRuntimeError("Out of range token number: "
          + tok);
    }
  }

  /**
   * Get a list of possible matches
   * @param context
   * @param tree
   * @param argTypes
   * @return
   * @throws UserException
   */
  private static List<MatchedOp> getOpsFromTree(Context context,
      SwiftAST tree, List<Type> argTypes) throws UserException {
    return getOpsFromTree(context, tree, null, argTypes, false);
  }

  /**
   *
   * @param context
   * @param tree
   * @param expectedResult
   * @param outType if not null, only return operators with this output type
   * @param outArgTypes Put the argument types of the operator in this list if not null
   * @param expectUnambig at this stage, expect operator not to be ambiguous - log
   *              debug message if it is
   * @return at least one builtin operator
   * @throws TypeMismatchException if no matches possible
   */
  private static List<MatchedOp> getOpsFromTree(Context context,
          SwiftAST tree, Type outType, List<Type> outArgTypes,
          boolean expectUnambig)
          throws UserException {
    assert(tree.getType() == ExMParser.OPERATOR);
    assert(tree.getChildCount() >= 1);
    int opTok = tree.child(0).getType();
    String opName = extractOpName(tree);

    List<Type> argTypes = findOperatorArgTypes(context, tree, opName);

    if (outArgTypes != null) {
      // Store for caller
      outArgTypes.clear();
      outArgTypes.addAll(argTypes);
    }

    // Track operators that matched
    List<MatchedOp> matched = new ArrayList<MatchedOp>();

    for (Op candidate: Operators.getOps(opTok)) {
      MatchedOp match = opTypesMatch(outType, argTypes, candidate);
      if (match != null) {
        matched.add(match);
      }
    }

    if (matched.size() != 1) {
      // Hope to match exactly one operator
      List<String> typeNames = new ArrayList<String>();
      for (Type argType: argTypes) {
        typeNames.add(argType.typeName());
      }
      if (matched.size() == 0) {
        // Error - no matches
        String msg = "Operator " + opName + " not supported for these input "
                   + "types : " + typeNames.toString();
        if (outType != null) {
          msg += " and output type " + outType;
        }
        throw new UndefinedOperatorException(context, msg);
      } else if (expectUnambig) {
        // Ambiguous operator - might be of interest
        assert(matched.size() > 1);
        Logging.getSTCLogger().debug("Ambiguous operator " + opName +
                    " for arg types " + typeNames + ".  Matched: " +
                    matched.toString());
      }
    }

    return matched;
  }

  private static MatchedOp opTypesMatch(Type outType, List<Type> argTypes,
      Op candidate) {
    OpType opType = candidate.type;

    if (!opOutputMatches(outType, opType)) {
      return null;
    }

    List<Type> exprTypes = opInputsMatch(argTypes, opType);
    if (exprTypes == null) {
      return null;
    }

    return new MatchedOp(candidate, exprTypes);
  }

  /**
   * @param argTypes
   * @param opType
   * @return list of types that expressions should be evaluated to
   */
  private static List<Type> opInputsMatch(List<Type> argTypes,
                                              OpType opType) {
    if (argTypes.size() != opType.in().size()) {
      return null;
    }

    List<Type> result = new ArrayList<Type>(argTypes.size());

    for (int i = 0; i < argTypes.size(); i++) {
      OpInputType in = opType.in().get(i);
      Type argType = argTypes.get(i);
      List<Type> argTypeFields = TupleType.getFields(argType);

      if (!in.variadic && argTypeFields.size() != 1) {
        // Mismatch in arity
        return null;
      }

      List<Type> argResultTypes = new ArrayList<Type>(argTypeFields.size());

      for (int j = 0; j < argTypeFields.size(); j++) {
        Pair<Type, Type> altType = FunctionTypeChecker.selectArgType(
                                        in.type, argTypeFields.get(j), true);
        if (altType == null) {
          return null;
        }
        argResultTypes.add(altType.val2);
      }
      result.add(TupleType.makeTuple(argResultTypes));
    }
    return result;
  }

  private static boolean opOutputMatches(Type outType, OpType opType) {
    return outType == null ||
        (opType.out() != null &&
         opType.out().assignableTo(outType));
  }


  /**
   * Find the argument type of the operators to a function
   * assuming all operators take all the same argument types.
   * Preference order of types determined by first expression's
   * union type order
   * @param context
   * @param tree
   * @param opName
   * @param argTypes used to return arg types of function, null for no return
   * @return
   * @throws TypeMismatchException
   * @throws UserException
   */
  private static List<Type> findOperatorArgTypes(Context context, SwiftAST tree,
                                                     String opName)
          throws TypeMismatchException, UserException {
    assert(tree.getType() == ExMParser.OPERATOR);

    int argcount = tree.getChildCount() - 1;
    if (argcount == 0) {
      throw new TypeMismatchException(context,
          "provided no arguments to operator " +
          opName);
    }

    List<Type> argTypes = new ArrayList<Type>(argcount);
    for (SwiftAST argTree: tree.children(1)) {
      argTypes.add(TypeChecker.findExprType(context, argTree));
    }

    return argTypes;
  }

  public static class MatchedOp {
    public final Op op;
    public final List<Type> exprTypes;

    public MatchedOp(Op op, List<Type> exprTypes) {
      this.op = op;
      this.exprTypes = Collections.unmodifiableList(
                          new ArrayList<Type>(exprTypes));
    }
  }
}
