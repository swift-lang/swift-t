package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Var;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgOrCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.tree.Opcode;

public class Algebra {


  /**
   * @param state
   * @param cv value with canonicalized arguments
   * @return
   */
  public static List<ArgCV> tryAlgebra(Congruences state,
                                       ArgCV cv) {
    if (cv.op == Opcode.ASYNC_OP || cv.op == Opcode.LOCAL_OP) {
      if (cv.subop == BuiltinOpcode.PLUS_INT ||
          cv.subop == BuiltinOpcode.MINUS_INT) {
        return tryAlgebra(state, cv.op, (BuiltinOpcode)cv.subop,
                          cv.getInput(0), cv.getInput(1));
      }
    }
    return Collections.emptyList();
  }

  /**
   *
   * Do basic algebra, useful for adjacent array indices
   * TODO: could be more sophisticated, e.g. build operator tree
   *     and reduce to canonical form
   * Don't handle constant folding here
   *
   * @param state
   * @param op
   * @param subop
   * @param in1 canonical input
   * @param in2 canonical input
   * @return
   */
  private static List<ArgCV> tryAlgebra(Congruences state,
          Opcode op, BuiltinOpcode subop, Arg in1, Arg in2) {
    if (!Settings.getBooleanUnchecked(Settings.OPT_ALGEBRA)) {
      return Collections.emptyList();
    }

    Logger logger = Logging.getSTCLogger();
    if (logger.isTraceEnabled()) {
      logger.trace("tryAlgebra " + op + " " + subop + " "
          + in1 + " " + in2);
    }

    Pair<Var, Long> args = convertToCanonicalAdd(subop, in1, in2);
    if (args == null) {
      return Collections.emptyList();
    }

    if (logger.isTraceEnabled()) {
      logger.trace("canonical: " + args);
    }

    // Now arg1 should be var, arg2 constant
    List<ArgOrCV> vals = state.findCongruent(args.val1.asArg(),
                                             CongruenceType.VALUE);
    if (logger.isTraceEnabled()) {
      logger.trace("found congruent: " + vals);
    }
    List<ArgCV> res = new ArrayList<ArgCV>();
    for (ArgOrCV val: vals) {
      if (val.isCV()) {
        ArgCV newCV = tryAlgebra(op, subop, args, val.cv());
        if (newCV != null) {
          if (logger.isTraceEnabled()) {
            logger.trace("tryAlgebra new CV: " + newCV);
          }
          res.add(newCV);
        }
      }
    }
    return res;
  }


  private static ArgCV tryAlgebra(Opcode op, BuiltinOpcode subop,
          Pair<Var, Long> canonArgs, ArgCV varVal) {
    if (varVal.op() != op) {
      return null;
    }
    BuiltinOpcode aop = (BuiltinOpcode)varVal.subop();
    if (aop == BuiltinOpcode.PLUS_INT ||
        aop == BuiltinOpcode.MINUS_INT) {
      // Don't handle recursive values (TODO?)
      Arg arg1 = varVal.getInput(0);
      Arg arg2 = varVal.getInput(1);
      Pair<Var, Long> add = convertToCanonicalAdd(aop, arg1, arg2);
      if (add != null) {
        // Note that if this instruction computes x = y + c1
        // and y = z + c2 was computed earlier, then
        // x = z + c1 + c2
        long c = canonArgs.val2 + add.val2;
        if (c == 0) {
          // Check if additions cancel
          return ComputedValue.makeCopy(add.val1.asArg());
        } else {
          // Otherwise add them together
          return new ArgCV(op, BuiltinOpcode.PLUS_INT,
                   Arrays.asList(add.val1.asArg(), Arg.newInt(c)));
        }
      }
    }
    return null;
  }


  private static Pair<Var, Long> convertToCanonicalAdd(BuiltinOpcode subop,
          Arg in1, Arg in2) {
    Var varArg;
    long constArg;
    if (!(in1.isVar() ^ in2.isVar())) {
      // Only handle one constant, one var
      return null;
    }
    if (in1.isVar()) {
      varArg = in1.getVar();
      constArg = in2.getInt();
      if (subop == BuiltinOpcode.MINUS_INT) {
        // Convert to addition
        constArg *= -1;
      }
    } else {
      if (subop == BuiltinOpcode.MINUS_INT) {
        // Don't handle negated variable
        return null;
      }
      constArg = in1.getInt();
      varArg = in2.getVar();
    }

    return Pair.create(varArg, constArg);
  }


}
