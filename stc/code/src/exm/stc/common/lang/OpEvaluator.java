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
      /* Check what arguments are */
      boolean allInt = true;
      boolean allFloat = true;
      boolean allString = true;
      boolean allBool = true;
      boolean allConst = true;
      // Check that there are no nulls
      for (Arg in : inputs) {
        if (in == null) {
          return null;
        }
        allInt = allInt && in.isImmInt();
        allFloat = allFloat && in.isImmFloat();
        allString = allString && in.isImmString();
        allBool = allBool && in.isImmBool();
        allConst = allConst && in.isConst();
      }

      if (allConst) {
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
      return null;
    }
  }

  /**
   * Constant folding for short-circuitable operations where we don't always
   * need to know both arguments to evaluate
   *
   * @param constArgs
   *          unknown args are null.  Currently assume 2 args
   * @return
   */
  private static Arg
      evalShortCircuit(BuiltinOpcode op, List<Arg> constArgs) {
    assert(constArgs.size() == 2) :
          "Assume 2 args for short circuited operations";
    List<Arg> constInputs = new ArrayList<Arg>(2);
    Arg nonConstInput = null;
    for (Arg in : constArgs) {
      if (in != null) {
        if (in.isBool()) {
          constInputs.add(in);
        } else {
          assert(in.isVar());
          nonConstInput = in;
        }
      }
    }
    if (constInputs.size() >= 1) {
      boolean arg1 = constInputs.get(0).getBool();
      if (constInputs.size() == 2) {
        // Can directly evaluate
        boolean arg2 = constInputs.get(1).getBool();
        switch (op) {
          case OR:
            return Arg.newBool(arg1 || arg2);
          case AND:
            return Arg.newBool(arg1 && arg2);
          default:
            // fall through
        }
      } else if (constInputs.size() == 1) {
        // see if we can short-circuit
        if (op == BuiltinOpcode.AND && !arg1) {
          return Arg.newBool(false);
        } else if (op == BuiltinOpcode.AND && arg1 && nonConstInput != null) {
          return nonConstInput;
        } else if (op == BuiltinOpcode.OR && arg1) {
          return Arg.newBool(true);
        } else if (op == BuiltinOpcode.OR && !arg1) {
          return nonConstInput;
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
          sb.append(oa.getString());
        }
        return Arg.newString(sb.toString());
    } else if (constInputs.size() == 1) {
      String arg1 = constInputs.get(0).getString();
      switch (op) {
        case COPY_STRING:
          return Arg.newString(arg1);
        case PARSE_FLOAT:
          try {
            // TODO: does this match Tcl implementation?
            double val = Double.valueOf(arg1);
            return Arg.newFloat(val);
          } catch (NumberFormatException ex) {
            // Handle at runtime
          }
          break;
        default:
          // Fall through
      }
    } else if (constInputs.size() == 2) {
      String arg1 = constInputs.get(0).getString();
      String arg2 = constInputs.get(1).getString();
      switch (op) {
        case EQ_STRING:
          return Arg.newBool(arg1.equals(arg2));
        case NEQ_STRING:
          return Arg.newBool(!arg1.equals(arg2));
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
      double arg1 = constInputs.get(0).getFloat();
      switch(op) {
        case COPY_FLOAT:
          return Arg.newFloat(arg1);
        case ABS_FLOAT:
          return Arg.newFloat(Math.abs(arg1));
        case EXP:
          return Arg.newFloat(Math.exp(arg1));
        case LOG:
          return Arg.newFloat(Math.log(arg1));
        case SQRT:
          return Arg.newFloat(Math.sqrt(arg1));
        case ROUND:
          return Arg.newFloat(Math.rint(arg1));
        case CEIL:
          return Arg.newFloat(Math.ceil(arg1));
        case FLOOR:
          return Arg.newFloat(Math.floor(arg1));
        case FLOATTOINT:
          return Arg.newInt((long)Math.floor(arg1));
        case FLOATTOSTR:
          // TODO: format might not be consistent with Tcl
          return Arg.newString(Double.toString(arg1));
        case IS_NAN:
          return Arg.newBool(Double.isNaN(arg1));
        default:
          return null;
      }
    } else if (constInputs.size() == 2) {
      double arg1 = constInputs.get(0).getFloat();
      double arg2 = constInputs.get(1).getFloat();
      switch(op) {
        case PLUS_FLOAT:
          return Arg.newFloat(arg1 + arg2);
        case MINUS_FLOAT:
          return Arg.newFloat(arg1 - arg2);
        case MULT_FLOAT:
          return Arg.newFloat(arg1 * arg2);
        case EQ_FLOAT:
          return Arg.newBool(arg1 == arg2);
        case NEQ_FLOAT:
          return Arg.newBool(arg1 != arg2);
        case GT_FLOAT:
          return Arg.newBool(arg1 > arg2);
        case GTE_FLOAT:
          return Arg.newBool(arg1 >= arg2);
        case LT_FLOAT:
          return Arg.newBool(arg1 < arg2);
        case LTE_FLOAT:
          return Arg.newBool(arg1 <= arg2);
        case MAX_FLOAT:
          return Arg.newFloat(Math.max(arg1, arg2));
        case MIN_FLOAT:
          return Arg.newFloat(Math.min(arg1, arg2));
        case POW_FLOAT:
          return Arg.newFloat(Math.pow(arg1, arg2));
        default:
          return null;
      }
    } else {
      return null;
    }
  }

  private static Arg evalIntOp(BuiltinOpcode op, List<Arg> constInputs) {
    if (constInputs.size() == 1) {
      long arg1 = constInputs.get(0).getInt();
      switch(op) {
        case COPY_INT:
          return Arg.newInt(arg1);
        case ABS_INT:
          return Arg.newInt(Math.abs(arg1));
        case NEGATE_INT:
          return Arg.newInt(0 - arg1);
        case INTTOFLOAT:
          return Arg.newFloat(arg1);
        case INTTOSTR:
          return Arg.newString(Long.toString(arg1));
        default:
          return null;
      }
    } else if (constInputs.size() == 2) {
      long arg1 = constInputs.get(0).getInt();
      long arg2 = constInputs.get(1).getInt();
      switch (op) {
        case PLUS_INT:
          return Arg.newInt(arg1 + arg2);
        case MINUS_INT:
          return Arg.newInt(arg1 - arg2);
        case MULT_INT:
          return Arg.newInt(arg1 * arg2);
        case DIV_INT:
          return Arg.newInt(arg1 / arg2);
        case MOD_INT:
          return Arg.newInt(arg1 % arg2);
        case EQ_INT:
          return Arg.newBool(arg1 == arg2);
        case NEQ_INT:
          return Arg.newBool(arg1 != arg2);
        case GT_INT:
          return Arg.newBool(arg1 > arg2);
        case GTE_INT:
          return Arg.newBool(arg1 >= arg2);
        case LT_INT:
          return Arg.newBool(arg1 < arg2);
        case LTE_INT:
          return Arg.newBool(arg1 <= arg2);
        case MAX_INT:
          return Arg.newInt(Math.max(arg1, arg2));
        case MIN_INT:
          return Arg.newInt(Math.min(arg1, arg2));
        case POW_INT:
          return Arg.newFloat(Math.pow(arg1, arg2));
        default:
          return null;
      }
    }
    return null;
  }

  private static Arg
      evalBoolOp(BuiltinOpcode op, List<Arg> constInputs) {
    if (constInputs.size() == 1) {
      boolean arg1 = constInputs.get(0).getBool();
      switch (op) {
        case NOT:
          return Arg.newBool(!arg1);
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
      case SUBSTRING: {
        String str = inputs.get(0).getString();
        long start = inputs.get(1).getInt();
        long len = inputs.get(2).getInt();
        long end = Math.min(start + len, str.length());
        return Arg.newString(str.substring((int) start, (int) (end)));
      }
      case PARSE_INT: {
        String str = inputs.get(0).getString();
        long baseL = inputs.get(1).getInt();
        if (baseL < 2 || baseL > Integer.MAX_VALUE) {
          // Cannot evaluate
          return null;
        }
        int base = (int)baseL;
        try {
          long val = Long.parseLong(str, base);
          return Arg.newInt(val);
        } catch (NumberFormatException ex) {
          return null;
        }
      }
      default:
        return null;
    }
  }

}
