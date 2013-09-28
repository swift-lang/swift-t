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
package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.List;

import exm.stc.common.lang.Operators.BuiltinOpcode;

/**
 * Compile time evaluation of local arithmetic ops
 */
public class OpEvaluator {

  /**
   * Try to do compile-time evaluation of operator
   * 
   * @param op
   * @param inputs inputs to operator.  Null or a variable if not constant
   * @return output value of op if it could be evaluated at compile-time, null
   *         otherwise.  Also returns null if the operator is simply a copy,
   *         since no reduction can occur then 
   */
  public static Arg eval(BuiltinOpcode op, List<Arg> inputs) {
    if (Operators.isShortCircuitable(op)) {
      // TODO: could short-circuit e.g. x * 0 or x ** 0 or x - x
      return evalShortCircuit(op, inputs);
    } else if (Operators.isCopy(op)) {
      return null;
    } else {
      /* we need all arguments to constant fold */
      boolean allInt = true;
      boolean allFloat = true;
      boolean allString = true;
      boolean allBool = true;
      // Check that there are no nulls
      for (Arg in : inputs) {
        if (in == null) {
          return null;
        }
        allInt = allInt && in.isIntVal();
        allFloat = allFloat && in.isFloatVal();
        allString = allString && in.isStringVal();
        allBool = allBool && in.isBoolVal();
      }

      if (allInt) {
        return evalIntOp(op, inputs);
      } else if (allFloat) {
        return evalFloatOp(op, inputs);
      } else if (allString) {
        return evalStringOp(op, inputs);
      } else if (allBool) {
        return evalBoolOp(op, inputs);
      } else {
        return evalOtherOp(op, inputs);
      }
    }
  }

  /**
   * Constant folding for short-circuitable operations where we don't always
   * need to know both arguments to evaluate
   * 
   * @param constArgs
   *          unknown args are null
   * @return
   */
  private static Arg
      evalShortCircuit(BuiltinOpcode op, List<Arg> constArgs) {
    List<Arg> constInputs = new ArrayList<Arg>(2);
    for (Arg in : constArgs) {
      if (in != null) {
        assert (in.isBoolVal());
        constInputs.add(in);
      }
    }
    if (constInputs.size() >= 1) {
      boolean arg1 = constInputs.get(0).getBoolLit();
      if (constInputs.size() == 2) {
        // Can directly evaluate
        boolean arg2 = constInputs.get(1).getBoolLit();
        switch (op) { 
          case OR:
            return Arg.createBoolLit(arg1 || arg2);
          case AND:
            return Arg.createBoolLit(arg1 && arg2);
          default:
            // fall through
        }
      } else if (constInputs.size() == 1) {
        // see if we can short-circuit
        if (op == BuiltinOpcode.AND && arg1) {
          return Arg.createBoolLit(true);
        } else if (op == BuiltinOpcode.OR && !arg1) {
          return Arg.createBoolLit(false);
        }
      }
    }
    return null;
  }

  private static Arg evalStringOp(BuiltinOpcode op,
      List<Arg> constInputs) {
    if (op == BuiltinOpcode.STRCAT) {
        // Strcat can take multiple arguments
        StringBuilder sb = new StringBuilder();
        for (Arg oa : constInputs) {
          sb.append(oa.getStringLit());
        }
        return Arg.createStringLit(sb.toString());
    } else if (constInputs.size() == 1) {
      String arg1 = constInputs.get(0).getStringLit();
      switch (op) {
        case COPY_STRING:
          return Arg.createStringLit(arg1);
        case STRTOINT:
          try {
            long val = Long.parseLong(arg1);
            return Arg.createIntLit(val);
          } catch (NumberFormatException ex) {
            // Handle at runtime
          }
          break;
        case STRTOFLOAT:
          try {
            // TODO: does this match Tcl implementation?
            double val = Double.valueOf(arg1);
            return Arg.createFloatLit(val);
          } catch (NumberFormatException ex) {
            // Handle at runtime
          }
          break;
        default:
          // Fall through
      }
    } else if (constInputs.size() == 2) {
      String arg1 = constInputs.get(0).getStringLit();
      String arg2 = constInputs.get(1).getStringLit();
      switch (op) { 
        case EQ_STRING:
          return Arg.createBoolLit(arg1.equals(arg2));
        case NEQ_STRING:
          return Arg.createBoolLit(!arg1.equals(arg2));
        default:
          // fall through
          break;
      }
    }
    return null;
  }

  private static Arg
      evalFloatOp(BuiltinOpcode op, List<Arg> constInputs) {
    if (constInputs.size() == 1) {
      double arg1 = constInputs.get(0).getFloatLit();
      switch(op) {
        case COPY_FLOAT:
          return Arg.createFloatLit(arg1);
        case ABS_FLOAT:
          return Arg.createFloatLit(Math.abs(arg1));
        case EXP:
          return Arg.createFloatLit(Math.exp(arg1));
        case LOG:
          return Arg.createFloatLit(Math.log(arg1));
        case SQRT:
          return Arg.createFloatLit(Math.sqrt(arg1));
        case ROUND:
          return Arg.createIntLit(Math.round(arg1));
        case CEIL:
          return Arg.createIntLit((long) Math.ceil(arg1));
        case FLOOR:
          return Arg.createIntLit((long) Math.floor(arg1));
        case FLOATTOSTR:
          // TODO: format might not be consistent with TCL
          return Arg.createStringLit(Double.toString(arg1));
        case IS_NAN:
          return Arg.createBoolLit(Double.isNaN(arg1));
        default:
          return null;
      }
    } else if (constInputs.size() == 2) {
      double arg1 = constInputs.get(0).getFloatLit();
      double arg2 = constInputs.get(1).getFloatLit();
      switch(op) {
        case PLUS_FLOAT:
          return Arg.createFloatLit(arg1 + arg2);
        case MINUS_FLOAT:
          return Arg.createFloatLit(arg1 - arg2);
        case MULT_FLOAT:
          return Arg.createFloatLit(arg1 * arg2);
        case EQ_FLOAT:
          return Arg.createBoolLit(arg1 == arg2);
        case NEQ_FLOAT:
          return Arg.createBoolLit(arg1 != arg2);
        case GT_FLOAT:
          return Arg.createBoolLit(arg1 > arg2);
        case GTE_FLOAT:
          return Arg.createBoolLit(arg1 >= arg2);
        case LT_FLOAT:
          return Arg.createBoolLit(arg1 < arg2);
        case LTE_FLOAT:
          return Arg.createBoolLit(arg1 <= arg2);
        case MAX_FLOAT:
          return Arg.createFloatLit(Math.max(arg1, arg2));
        case MIN_FLOAT:
          return Arg.createFloatLit(Math.min(arg1, arg2));
        case POW_FLOAT:
          return Arg.createFloatLit(Math.pow(arg1, arg2));
        default:
          return null;
      }
    } else {
      return null;
    }
  }

  private static Arg evalIntOp(BuiltinOpcode op, List<Arg> constInputs) {
    if (constInputs.size() == 1) {
      long arg1 = constInputs.get(0).getIntLit();
      switch(op) {
        case COPY_INT:
          return Arg.createIntLit(arg1);
        case ABS_INT:
          return Arg.createIntLit(Math.abs(arg1));
        case NEGATE_INT:
          return Arg.createIntLit(0 - arg1);
        case INTTOFLOAT:
          return Arg.createFloatLit(arg1);
        case INTTOSTR:
          return Arg.createStringLit(Long.toString(arg1));
        default:
          return null;
      }
    } else if (constInputs.size() == 2) {
      long arg1 = constInputs.get(0).getIntLit();
      long arg2 = constInputs.get(1).getIntLit();
      switch (op) { 
        case PLUS_INT:
          return Arg.createIntLit(arg1 + arg2);
        case MINUS_INT:
          return Arg.createIntLit(arg1 - arg2);
        case MULT_INT:
          return Arg.createIntLit(arg1 * arg2);
        case DIV_INT:
          return Arg.createIntLit(arg1 / arg2);
        case MOD_INT:
          return Arg.createIntLit(arg1 % arg2);
        case EQ_INT:
          return Arg.createBoolLit(arg1 == arg2);
        case NEQ_INT:
          return Arg.createBoolLit(arg1 != arg2);
        case GT_INT:
          return Arg.createBoolLit(arg1 > arg2);
        case GTE_INT:
          return Arg.createBoolLit(arg1 >= arg2);
        case LT_INT:
          return Arg.createBoolLit(arg1 < arg2);
        case LTE_INT:
          return Arg.createBoolLit(arg1 <= arg2);
        case MAX_INT:
          return Arg.createIntLit(Math.max(arg1, arg2));
        case MIN_INT:
          return Arg.createIntLit(Math.min(arg1, arg2));
        case POW_INT:
          return Arg.createFloatLit(Math.pow((double) arg1, (double) arg2));
        default:
          return null;
      }
    }
    return null;
  }

  private static Arg
      evalBoolOp(BuiltinOpcode op, List<Arg> constInputs) {
    if (constInputs.size() == 1) {
      boolean arg1 = constInputs.get(0).getBoolLit();
      switch (op) { 
        case NOT:
          return Arg.createBoolLit(!arg1);
        default:
          // fall through
          break;
      }
    } else {
      // AND and OR are handled as shortcircuitable functions
      return null;
    }
    return null;
  }
  
  /**
   * Evaluate operator with mixed argument types
   * @param op
   * @param inputs
   * @return
   */
  private static Arg evalOtherOp(BuiltinOpcode op, List<Arg> inputs) {
    switch (op) { 
      case SUBSTRING:
        String str = inputs.get(0).getStringLit();
        long start = inputs.get(1).getIntLit();
        long len = inputs.get(2).getIntLit();
        long end = Math.min(start + len, str.length());
        return Arg.createStringLit(str.substring((int) start, (int) (end)));
      default:
        return null;
    }
  }

}
