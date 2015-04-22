package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.OpEvaluator;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgOrCV;
import exm.stc.ic.tree.ICInstructions.CommonFunctionCall;
import exm.stc.ic.tree.Opcode;

/**
 * Implement constant folding using information from congruent sets.
 */
public class ConstantFolder {

  /**
   * Do constant folding
   * @param val
   * @return the folded value if successful.  Note that this may be
   *         a constant, a variable, or a computed value representing
   *         one of these stored in a future.  Returns null if not
   *         successful.
   */
  public static ArgOrCV constantFold(Logger logger,
      ForeignFunctions foreignFuncs, CongruentSets sets,
      ComputedValue<Arg> val) {
    switch (val.op) {
      case ASYNC_OP:
      case LOCAL_OP:
        return foldBuiltinOp(logger, sets, val);
      case IS_MAPPED:
        return foldIsMapped(val);
      case CALL_CONTROL:
      case CALL_FOREIGN:
      case CALL_FOREIGN_LOCAL:
      case CALL_LOCAL:
      case CALL_LOCAL_CONTROL:
      case CALL_SYNC:
        return foldFunctionCall(logger, foreignFuncs, sets, val);
      case GET_FILENAME_ALIAS:
        return foldGetFilename(logger, sets, val);
      default:
        // Can't fold others
        return null;
    }
  }


  private static ArgOrCV foldBuiltinOp(Logger logger, CongruentSets sets,
                                     ComputedValue<Arg> val) {
    List<Arg> inputs;
    if (val.op == Opcode.LOCAL_OP) {
      inputs = val.inputs;
    } else {
      assert(val.op == Opcode.ASYNC_OP);
      inputs = findFutureValues(sets, val);
    }

    if (logger.isTraceEnabled()) {
      logger.trace("Try constant fold: " + val + " " + inputs);
    }
    if (inputs != null) {
      // constant fold
      Arg res = OpEvaluator.eval((BuiltinOpcode)val.subop, inputs);
      if (res != null) {
        if (logger.isDebugEnabled()) {
          logger.debug("Constant fold: " + val + " => " + res);
        }
        boolean futureResult = val.op != Opcode.LOCAL_OP;
        return valFromArg(futureResult, res);
      }
    }
    return null;
  }


  private static ArgOrCV foldIsMapped(ComputedValue<Arg> val) {
    Arg fileCV = val.getInput(0);
    assert(fileCV.isVar());
    Var file = fileCV.getVar();
    if (file.isMapped() != Ternary.MAYBE) {
      Arg isMapped = Arg.newBool(file.isMapped() == Ternary.TRUE);
      return new ArgOrCV(isMapped);
    }
    return null;
  }


  private static ArgOrCV foldFunctionCall(Logger logger,
      ForeignFunctions foreignFuncs, CongruentSets sets,
      ComputedValue<Arg> val) {
    List<Arg> inputs;
    if (!CommonFunctionCall.canConstantFold(foreignFuncs, val)) {
      return null;
    }
    boolean usesValues = CommonFunctionCall.acceptsLocalValArgs(val.op);
    if (usesValues) {
      inputs = val.inputs;
    } else {
      inputs = findFutureValues(sets, val);
    }
    if (inputs != null) {
      Arg result = CommonFunctionCall.tryConstantFold(foreignFuncs, val, inputs);
      if (result != null) {
        return valFromArg(!usesValues, result);
      }
    }
    return null;
  }


  /**
   * Convert arg representing result of computation (maybe constant)
   * into a computed value
   * @param futureResult
   * @param arg
   * @return
   */
  private static ArgOrCV valFromArg(boolean futureResult, Arg arg) {
    if (!futureResult) {
      // Can use directly
      return new ArgOrCV(arg);
    } else if (arg.isConst()){
      // Record stored future
      return new ArgOrCV(Opcode.assignOpcode(arg.futureType()),
                                             arg.asList());
    } else {
      // Should be future
      return new ArgOrCV(arg);
    }
  }

  /**
   * Try to find constant values of futures
   * @param val
   * @param congruent
   * @return a list with constants in places with constant values,
   *      or future values in places with future args.  Returns null
   *      if we couldn't resolve to args.
   */
  private static List<Arg> findFutureValues(CongruentSets sets,
                                            ComputedValue<Arg> val) {
    List<Arg> inputs = new ArrayList<Arg>(val.inputs.size());
    for (Arg arg: val.inputs) {
      if (arg.isConst()) {
        // For some calling conventions, constants are used
        inputs.add(arg);
      } else {
        Arg storedConst = sets.findRetrieveResult(arg, false);
        if (storedConst != null && storedConst.isConst()) {
          inputs.add(storedConst);
        } else {
          inputs.add(arg);
        }
      }
    }
    return inputs;
  }

  /**
   * Sometimes filename value is directly initialized.
   * @param logger
   * @param sets
   * @param val
   * @return
   */
  private static ArgOrCV foldGetFilename(Logger logger, CongruentSets sets,
      ComputedValue<Arg> val) {
    Var file = val.getInput(0).getVar();
    ArgCV filenameValCV = ComputedValue.filenameValCV(file);
    Arg filenameValCanon = sets.findCanonicalInternal(filenameValCV);
    if (filenameValCanon != null) {
      // If we know filename of file, then just use that
      ArgOrCV folded = new ArgOrCV(Opcode.assignOpcode(Types.F_STRING),
                               filenameValCanon);

      return folded;
    }
    return null;
  }


}
