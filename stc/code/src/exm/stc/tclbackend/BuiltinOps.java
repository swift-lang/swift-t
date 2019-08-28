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
package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.PrimType;
import exm.stc.common.lang.Var;
import exm.stc.tclbackend.tree.Command;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.Expression.ExprContext;
import exm.stc.tclbackend.tree.SetVariable;
import exm.stc.tclbackend.tree.Square;
import exm.stc.tclbackend.tree.TclExpr;
import exm.stc.tclbackend.tree.TclString;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;

public class BuiltinOps {

  public static TclTree genLocalOpTcl(BuiltinOpcode op, Var out, List<Arg> in,
      ArrayList<Expression> argExpr) {

    if (op == BuiltinOpcode.ASSERT || op == BuiltinOpcode.ASSERT_EQ) {
      // Should have void output
      assert(out != null && Types.isVoidVal(out));
      Token tclFn;
      switch (op) {
      case ASSERT:
        tclFn = Turbine.TURBINE_ASSERT_IMPL;
        break;
      case ASSERT_EQ:
        tclFn = Turbine.TURBINE_ASSERT_EQUAL_IMPL;
        break;
      default:
        throw new STCRuntimeError("Can't handle local op: "
            + op.toString());
      }
      return new Command(tclFn, argExpr);
    } else {
      if (op == BuiltinOpcode.SPRINTF) {
        List<Expression> tokens = new ArrayList<>(argExpr.size()+1);
        tokens.add(new Token("turbine::sprintf_impl"));
        tokens.addAll(argExpr);
        Square sq = new Square(tokens);
        SetVariable sv = new SetVariable(TclNamer.prefixVar(out.name()), sq);
        return sv;
      } else {
        assert(out != null);
        assert(Types.isPrimValue(out));
        Expression rhs;
        // First handle special cases, then typical case
        if (op == BuiltinOpcode.STRCAT) {
          rhs = localStrCat(in, argExpr);
        } else if (op == BuiltinOpcode.DIRCAT) {
          rhs = localDirCat(in, argExpr);
        } else if (op == BuiltinOpcode.EQ_STRING
                  || op == BuiltinOpcode.NEQ_STRING) {
          assert(argExpr.size() == 2);
          Expression s1 = argExpr.get(0);
          Expression s2 = argExpr.get(1);
          rhs = Turbine.stringEqual(s1, s2);
          if (op == BuiltinOpcode.NEQ_STRING) {
            // Negate previous result
            rhs = new TclExpr(true, TclExpr.NOT, rhs);
          }
        } else if (op == BuiltinOpcode.COPY_BLOB ||
            op ==  BuiltinOpcode.COPY_BOOL ||
            op == BuiltinOpcode.COPY_INT ||
            op == BuiltinOpcode.COPY_VOID ||
            op == BuiltinOpcode.COPY_FLOAT ||
            op == BuiltinOpcode.COPY_STRING) {
          assert(argExpr.size() == 1);
          checkCopy(op, out, in.get(0));
          rhs = argExpr.get(0);
        } else if (op == BuiltinOpcode.MOD_INT) {
          // Special implementation to emulate old swift
          rhs = Turbine.modInteger(argExpr.get(0), argExpr.get(1));
        } else if (op == BuiltinOpcode.DIV_INT) {
          // special implementation to emulate old swift
          rhs = Turbine.divideInteger(argExpr.get(0), argExpr.get(1));
        } else if (op == BuiltinOpcode.POW_INT) {
          assert(argExpr.size() == 2);
          assert(in.get(0).isImmInt() && in.get(1).isImmInt());
          rhs = new Square(Turbine.POW_INTEGER_IMPL, argExpr.get(0), argExpr.get(1));
        } else if (op == BuiltinOpcode.SUBSTRING) {
          assert(argExpr.size() == 3);
          rhs = new Square(Turbine.SUBSTRING_IMPL,
              argExpr.get(0), argExpr.get(1), argExpr.get(2));
        } else if (op == BuiltinOpcode.INTTOSTR || op == BuiltinOpcode.FLOATTOSTR) {
          assert(argExpr.size() == 1);
          // TCL will convert automatically
          rhs = argExpr.get(0);
        } else if (op == BuiltinOpcode.PARSE_INT) {
          assert(argExpr.size() == 2);
          rhs = Square.fnCall(Turbine.PARSE_INT, argExpr.get(0), argExpr.get(1));
        } else if (op == BuiltinOpcode.PARSE_FLOAT) {
          assert(argExpr.size() == 1);
          rhs = Square.fnCall(Turbine.TOFLOAT_IMPL, argExpr.get(0));
        } else {
          // Case for operations that are implemented directly with
          // TCL's expr
          Expression exp[] = arithOpExpr(op, argExpr);
          rhs = new TclExpr(exp);
        }
        return new SetVariable(TclNamer.prefixVar(out.name()), rhs);
      }
    }
  }


  private static Expression[] arithOpExpr(BuiltinOpcode op,
      ArrayList<Expression> argExpr) {
    switch (op) {
    /* First handle binary ops that map nicely to TCL equivalent */
    case PLUS_INT:
    case PLUS_FLOAT:
    case MINUS_INT:
    case MINUS_FLOAT:
    case MULT_INT:
    case MULT_FLOAT:
    case DIV_FLOAT:
    case POW_FLOAT:
    case EQ_INT:
    case NEQ_INT:
    case LT_INT:
    case LTE_INT:
    case GT_INT:
    case GTE_INT:
    case LT_FLOAT:
    case LTE_FLOAT:
    case GT_FLOAT:
    case GTE_FLOAT:
    case EQ_FLOAT:
    case NEQ_FLOAT:
    case EQ_BOOL:
    case NEQ_BOOL:
    case EQ_STRING:
    case NEQ_STRING:
    case AND:
    case OR:
      assert (argExpr.size() == 2);
      return new Expression[] { argExpr.get(0), arithOpTok(op), argExpr.get(1) };
    case NOT:
    case NEGATE_INT:
    case NEGATE_FLOAT:

      /* next unary ops with tcl equivalent */
      assert (argExpr.size() == 1);
      return new Expression[] { arithOpTok(op), argExpr.get(0) };
    case IS_NAN:
      assert (argExpr.size() == 1);
      return new Expression[] { argExpr.get(0), TclExpr.NEQ, argExpr.get(0) };
    case LOG:
    case EXP:
    case SQRT:
    case ABS_FLOAT:
    case ABS_INT:
      // Single argument to expr function
      return new Expression[] { TclExpr.exprFn(arithOpFn(op), argExpr.get(0))};
    case INTTOFLOAT:
      assert (argExpr.size() == 1);
      // Need to explicitly convert to floating point number, other
      // TCL will do e.g. integer division
      return new Expression[] {
          TclExpr.exprFn(TclExpr.DOUBLE_CONV, argExpr.get(0))};
    case CEIL:
    case FLOOR:
    case ROUND: {
      String fname;
      assert (argExpr.size() == 1);
      switch (op) {
      case CEIL:
        fname = TclExpr.CEIL;
        break;
      case FLOOR:
        fname = TclExpr.FLOOR;
        break;
      case ROUND:
        fname = TclExpr.ROUND;
        break;
      default:
        throw new STCRuntimeError("impossible");
      }

      return new Expression[] {
              TclExpr.exprFn(fname, argExpr.get(0))};
    }
    case FLOATTOINT: {
      // Need to apply int( conversion, as the rounding function still return
      // a floating point (albeit one with no fractional part)
      return new Expression[] {
          TclExpr.exprFn(TclExpr.INT_CONV,
              TclExpr.exprFn(TclExpr.FLOOR, argExpr.get(0)))};
    }
    case MAX_FLOAT:
    case MAX_INT:
    case MIN_FLOAT:
    case MIN_INT:
      String fnName;
      if (op == BuiltinOpcode.MAX_FLOAT || op == BuiltinOpcode.MAX_INT) {
        fnName = TclExpr.MAX;
      } else {
        fnName = TclExpr.MIN;
      }
      return new Expression[] {
          TclExpr.exprFn(fnName, argExpr.get(0), argExpr.get(1))};
    default:
      throw new STCRuntimeError("Haven't implement code gen for "
          + "local arithmetic op " + op.toString());
    }
  }

  private static Token arithOpTok(BuiltinOpcode op) {
    switch (op) {
    case EQ_INT:
    case EQ_FLOAT:
    case EQ_BOOL:
      return TclExpr.EQ;
    case NEQ_INT:
    case NEQ_FLOAT:
    case NEQ_BOOL:
      return TclExpr.NEQ;
    case PLUS_INT:
    case PLUS_FLOAT:
      return TclExpr.PLUS;
    case MINUS_INT:
    case MINUS_FLOAT:
    case NEGATE_INT:
    case NEGATE_FLOAT:
      return TclExpr.MINUS;
    case MULT_FLOAT:
    case MULT_INT:
      return TclExpr.TIMES;
    case POW_FLOAT:
      return TclExpr.POW;
    case LT_INT:
    case LT_FLOAT:
      return TclExpr.LT;
    case LTE_INT:
    case LTE_FLOAT:
      return TclExpr.LTE;
    case GT_INT:
    case GT_FLOAT:
      return TclExpr.GT;
    case GTE_INT:
    case GTE_FLOAT:
      return TclExpr.GTE;
    case OR:
      return TclExpr.OR;
    case AND:
      return TclExpr.AND;
    case NOT:
      return TclExpr.NOT;
    case DIV_FLOAT:
      return TclExpr.DIV;
    default:
      throw new STCRuntimeError("need to add op " + op.toString());
    }
  }

  private static String arithOpFn(BuiltinOpcode op) {
    switch (op) {
    case EXP:
      return TclExpr.EXP;
    case LOG:
      return TclExpr.LOG;
    case SQRT:
      return TclExpr.SQRT;
    case ABS_FLOAT:
    case ABS_INT:
      return TclExpr.ABS;
    default:
      throw new STCRuntimeError("need to add op " + op.toString());
    }
  }


  private static void checkCopy(BuiltinOpcode op, Var out, Arg inArg) {
    PrimType expType = null;
    switch (op) {
    case COPY_BLOB:
      expType = PrimType.BLOB;
      break;
    case COPY_BOOL:
      expType = PrimType.BOOL;
      break;
    case COPY_FLOAT:
      expType = PrimType.FLOAT;
      break;
    case COPY_INT:
      expType = PrimType.INT;
      break;
    case COPY_STRING:
      expType = PrimType.STRING;
      break;
    case COPY_VOID:
      expType = PrimType.VOID;
      break;
    default:
      throw new STCRuntimeError("Unexpected op: " + op);
    }
    assert(Types.isVal(expType, inArg));
    assert(Types.isVal(expType, out));
  }

  private static Expression localStrCat(List<Arg> in, ArrayList<Expression> argExpr) {
    return new TclString(argExpr, ExprContext.VALUE_STRING);
  }

  private static Expression localDirCat(List<Arg> in, ArrayList<Expression> argExpr) {
    assert(argExpr.size() == 2);
    Expression e1 = argExpr.get(0);
    Expression e2 = argExpr.get(1);
    Expression op = new TclString("/");
    List<Expression> args = Arrays.asList(e1, op, e2);
    return new TclString(args, ExprContext.VALUE_STRING);
  }

  private static Map<BuiltinOpcode, TclFunRef> builtinOpImpls
    = new HashMap<BuiltinOpcode, TclFunRef>();

  static {
    populateBuiltinOpImpls();
  }

  /**
   * Get asynchronous implementation of builtin op
   * TODO: this is a temporary solution to get this working.
   * Will be better later on to have arith ops, etc in
   * different namespace entirely
   */
  public static TclFunRef getBuiltinOpImpl(BuiltinOpcode op) {
    return builtinOpImpls.get(op);
  }

  /** Package in which async implementations of TCL operators live */
  private static final String OP_TCL_PKG = "turbine";
  private static void populateBuiltinOpImpls() {
    builtinOpImpls.put(BuiltinOpcode.PLUS_INT, new TclFunRef(
        OP_TCL_PKG, "plus_integer"));
    builtinOpImpls.put(BuiltinOpcode.MINUS_INT, new TclFunRef(
        OP_TCL_PKG, "minus_integer"));
    builtinOpImpls.put(BuiltinOpcode.MULT_INT, new TclFunRef(
        OP_TCL_PKG, "multiply_integer"));
    builtinOpImpls.put(BuiltinOpcode.DIV_INT, new TclFunRef(
        OP_TCL_PKG, "divide_integer"));
    builtinOpImpls.put(BuiltinOpcode.MOD_INT, new TclFunRef(
        OP_TCL_PKG, "mod_integer"));
    builtinOpImpls.put(BuiltinOpcode.NEGATE_INT, new TclFunRef(
        OP_TCL_PKG, "negate_integer"));
    builtinOpImpls.put(BuiltinOpcode.EQ_INT, new TclFunRef(
        OP_TCL_PKG, "eq_integer"));
    builtinOpImpls.put(BuiltinOpcode.NEQ_INT, new TclFunRef(
        OP_TCL_PKG, "neq_integer"));
    builtinOpImpls.put(BuiltinOpcode.LT_INT, new TclFunRef(
        OP_TCL_PKG, "lt_integer"));
    builtinOpImpls.put(BuiltinOpcode.LTE_INT, new TclFunRef(
        OP_TCL_PKG, "lte_integer"));
    builtinOpImpls.put(BuiltinOpcode.GT_INT, new TclFunRef(
        OP_TCL_PKG, "gt_integer"));
    builtinOpImpls.put(BuiltinOpcode.GTE_INT, new TclFunRef(
        OP_TCL_PKG, "gte_integer"));
    builtinOpImpls.put(BuiltinOpcode.PLUS_FLOAT, new TclFunRef(
        OP_TCL_PKG, "plus_float"));
    builtinOpImpls.put(BuiltinOpcode.MINUS_FLOAT, new TclFunRef(
        OP_TCL_PKG, "minus_float"));
    builtinOpImpls.put(BuiltinOpcode.MULT_FLOAT, new TclFunRef(
        OP_TCL_PKG, "multiply_float"));
    builtinOpImpls.put(BuiltinOpcode.DIV_FLOAT, new TclFunRef(
        OP_TCL_PKG, "divide_float"));
    builtinOpImpls.put(BuiltinOpcode.NEGATE_FLOAT, new TclFunRef(
        OP_TCL_PKG, "negate_float"));
    builtinOpImpls.put(BuiltinOpcode.EQ_FLOAT, new TclFunRef(
        OP_TCL_PKG, "eq_float"));
    builtinOpImpls.put(BuiltinOpcode.NEQ_FLOAT, new TclFunRef(
        OP_TCL_PKG, "neq_float"));
    builtinOpImpls.put(BuiltinOpcode.LT_FLOAT, new TclFunRef(
        OP_TCL_PKG, "lt_float"));
    builtinOpImpls.put(BuiltinOpcode.LTE_FLOAT, new TclFunRef(
        OP_TCL_PKG, "lte_float"));
    builtinOpImpls.put(BuiltinOpcode.GT_FLOAT, new TclFunRef(
        OP_TCL_PKG, "gt_float"));
    builtinOpImpls.put(BuiltinOpcode.GTE_FLOAT, new TclFunRef(
        OP_TCL_PKG, "gte_float"));
    builtinOpImpls.put(BuiltinOpcode.EQ_STRING, new TclFunRef(
        OP_TCL_PKG, "eq_string"));
    builtinOpImpls.put(BuiltinOpcode.NEQ_STRING, new TclFunRef(
        OP_TCL_PKG, "neq_string"));
    builtinOpImpls.put(BuiltinOpcode.STRCAT, new TclFunRef(
        OP_TCL_PKG, "strcat"));
    builtinOpImpls.put(BuiltinOpcode.EQ_BOOL, new TclFunRef(
        OP_TCL_PKG, "eq_integer"));
    builtinOpImpls.put(BuiltinOpcode.NEQ_BOOL, new TclFunRef(
        OP_TCL_PKG, "neq_integer"));
    builtinOpImpls.put(BuiltinOpcode.AND, new TclFunRef(
        OP_TCL_PKG, "and"));
    builtinOpImpls.put(BuiltinOpcode.OR, new TclFunRef(
        OP_TCL_PKG, "or"));
    builtinOpImpls.put(BuiltinOpcode.NOT, new TclFunRef(
        OP_TCL_PKG, "not"));
    builtinOpImpls.put(BuiltinOpcode.COPY_INT, new TclFunRef(
        OP_TCL_PKG, "copy_integer"));
    builtinOpImpls.put(BuiltinOpcode.COPY_VOID, new TclFunRef(
        OP_TCL_PKG, "copy_void"));
    builtinOpImpls.put(BuiltinOpcode.COPY_FLOAT, new TclFunRef(
        OP_TCL_PKG, "copy_float"));
    builtinOpImpls.put(BuiltinOpcode.COPY_STRING, new TclFunRef(
        OP_TCL_PKG, "copy_string"));
    builtinOpImpls.put(BuiltinOpcode.COPY_BOOL, new TclFunRef(
        OP_TCL_PKG, "copy_integer"));
    builtinOpImpls.put(BuiltinOpcode.COPY_BLOB, new TclFunRef(
        OP_TCL_PKG, "copy_blob"));

  }

}
