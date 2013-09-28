package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.OpEvaluator;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.ic.opt.valuenumber.ComputedValue.RecCV;
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
  public static RecCV constantFold(Logger logger, CongruentSets sets,
                                   ComputedValue<RecCV> val) {
    if (val.op == Opcode.ASYNC_OP || val.op == Opcode.LOCAL_OP) {
      List<Arg> inputs;
      if (val.op == Opcode.LOCAL_OP) {
        inputs = convertToArgs(val);
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
    } else if (val.op == Opcode.IS_MAPPED) {
      // TODO: merge over other constantFold() implementations once we can
      //       replace constant folding pass with this analysis
      // ARGV, etc too
    }
    return null;
  }
  

  /**
   * Convert arg representing result of computation (maybe constant)
   * into a computed value
   * @param futureResult
   * @param constant
   * @return
   */
  private static RecCV valFromArg(boolean futureResult, Arg constant) {
    if (!futureResult) {
      // Can use directly
      return new RecCV(constant);
    } else {
      // Record stored future
      return new RecCV(Opcode.assignOpcode(constant.futureType()),
                                    new RecCV(constant).asList());
    }
  }

  private static List<Arg> convertToArgs(ComputedValue<RecCV> val) {
    for (RecCV arg: val.inputs) {
      if (!arg.isArg()) {
        return null;
      }
    }
    
    List<Arg> inputs = new ArrayList<Arg>(val.inputs.size());
    for (RecCV arg: val.inputs) {
      inputs.add(arg.arg());
    }
    return inputs;
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
                                            ComputedValue<RecCV> val) {
    List<Arg> inputs = new ArrayList<Arg>(val.inputs.size());
    for (RecCV arg: val.inputs) {
      if (!arg.isArg()) {
        return null;
      }
      Arg storedConst = findValueOf(sets, arg);
      if (storedConst != null && storedConst.isConstant()) {
        inputs.add(storedConst);
      } else {
        inputs.add(arg.arg());
      }
    }
    return inputs;
  }

  /**
   * Find if a future has a constant value stored in it
   * @param congruent
   * @param arg
   * @return a value stored to the var, or null
   */
  private static Arg findValueOf(CongruentSets sets, RecCV arg) {
    assert(arg.arg().isVar());
    // Try to find constant load
    Opcode retrieveOp = Opcode.retrieveOpcode(arg.arg().getVar());
    assert(retrieveOp != null);
    RecCV retrieveVal = new RecCV(retrieveOp, arg.asList());
    return sets.findCanonical(retrieveVal);
  }



}
