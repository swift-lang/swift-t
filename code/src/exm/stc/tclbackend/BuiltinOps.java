package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.tclbackend.tree.Command;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.SetVariable;
import exm.stc.tclbackend.tree.Square;
import exm.stc.tclbackend.tree.TclList;
import exm.stc.tclbackend.tree.TclString;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;

public class BuiltinOps {


  public static TclTree genLocalOpTcl(BuiltinOpcode op, Var out, List<Arg> in,
      ArrayList<Expression> argExpr) {

    if (op == BuiltinOpcode.ASSERT || op == BuiltinOpcode.ASSERT_EQ) {
      assert(out == null);
      String tclFn;
      switch (op) {
      case ASSERT:
        tclFn = "turbine::assert_impl";
        break;
      case ASSERT_EQ:
        tclFn = "turbine::assertEqual_impl";
        break;
      default:
        throw new STCRuntimeError("Cn't handle local op: "
            + op.toString());
      }
      return new Command(tclFn, argExpr);
    } else {
      if (op == BuiltinOpcode.SPRINTF) {
        Square fmtArgs = new TclList(argExpr);
        Square fmt = new Square(new Token("eval"), new Token("format"),
                                                                  fmtArgs);
        return new SetVariable(TclNamer.prefixVar(out.name()), fmt);
      } else {
        assert(out != null);
        assert(Types.isScalarValue(out.type()));
        Expression rhs;
        // First handle special cases, then typical case
        if (op == BuiltinOpcode.STRCAT) {
          rhs = localStrCat(in, argExpr);
        } else if (op == BuiltinOpcode.EQ_STRING
                  || op == BuiltinOpcode.NEQ_STRING) {
          assert(argExpr.size() == 2);
          rhs = new Square(new Token("string"), new Token("equal"),
              argExpr.get(0), argExpr.get(1));
          if (op == BuiltinOpcode.NEQ_STRING) {
            // Negate previous result
            rhs = Square.arithExpr(new Token("!"), rhs);
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
          assert(in.get(0).isImmediateInt() && in.get(1).isImmediateInt());
          rhs = new Square(new Token("turbine::pow_integer_impl"), argExpr.get(0),
                                                                  argExpr.get(1));
        } else if (op == BuiltinOpcode.SUBSTRING) {
          assert(argExpr.size() == 3);
          rhs = new Square(new Token("turbine::substring_impl"),
              argExpr.get(0), argExpr.get(1), argExpr.get(2));
        } else if (op == BuiltinOpcode.INTTOSTR || op == BuiltinOpcode.FLOATTOSTR) {
          assert(argExpr.size() == 1);
          // TCL will convert automatically
          rhs = argExpr.get(0);
        } else if (op == BuiltinOpcode.STRTOINT ||
            op == BuiltinOpcode.STRTOFLOAT ) {
          assert(argExpr.size() == 1);
          String tclCheck = (op == BuiltinOpcode.STRTOINT) ?
                    "turbine::check_str_int" : "turbine::check_str_float";
          rhs = Square.fnCall(tclCheck, argExpr.get(0));


        } else {
          // Case for operations that are implemented directly with
          // TCL's expr
          Expression exp[] = arithOpExpr(op, argExpr);
          rhs = Square.arithExpr(exp);
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
      return new Expression[] { argExpr.get(0), new Token("!="), argExpr.get(0) };
    case LOG:
    case EXP:
    case SQRT:
    case ABS_FLOAT:
    case ABS_INT:
      // Single argument to expr function
      return new Expression[] { arithOpTok(op), new Token("("), argExpr.get(0),
          new Token(")") };
    case INTTOFLOAT:
      assert (argExpr.size() == 1);
      // Need to explicitly convert to floating point number, other
      // TCL will do e.g. integer division
      return new Expression[] { new Token("double("), argExpr.get(0),
          new Token(")") };
    case CEIL:
    case FLOOR:
    case ROUND: {
      String fname;
      assert (argExpr.size() == 1);
      switch (op) {
      case CEIL:
        fname = "ceil";
        break;
      case FLOOR:
        fname = "floor";
        break;
      case ROUND:
        fname = "round";
        break;
      default:
        throw new STCRuntimeError("impossible");
      }
      // Need to apply int( conversion, as the rounding function still return
      // a floating point (albeit one with no fractional part)
      return new Expression[] { new Token("int(" + fname + "("),
          argExpr.get(0), new Token("))") };
    }
    case MAX_FLOAT:
    case MAX_INT:
    case MIN_FLOAT:
    case MIN_INT:
      String fnName;
      if (op == BuiltinOpcode.MAX_FLOAT || op == BuiltinOpcode.MAX_INT) {
        fnName = "max";
      } else {
        fnName = "min";
      }
      return new Expression[] { new Token(fnName), new Token("("),
          argExpr.get(0), new Token(","), argExpr.get(1), new Token(")") };
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
      return new Token("==");
    case NEQ_INT:
    case NEQ_FLOAT:
    case NEQ_BOOL:
      return new Token("!=");
    case PLUS_INT:
    case PLUS_FLOAT:
      return new Token("+");
    case MINUS_INT:
    case MINUS_FLOAT:
    case NEGATE_INT:
    case NEGATE_FLOAT:
      return new Token("-");
    case MULT_FLOAT:
    case MULT_INT:
      return new Token("*");
    case POW_FLOAT:
      return new Token("**");
    case LT_INT:
    case LT_FLOAT:
      return new Token("<");
    case LTE_INT:
    case LTE_FLOAT:
      return new Token("<=");
    case GT_INT:
    case GT_FLOAT:
      return new Token(">");
    case GTE_INT:
    case GTE_FLOAT:
      return new Token(">=");
    case OR:
      return new Token("||");
    case AND:
      return new Token("&&");
    case NOT:
      return new Token("!");
    case EXP:
      return new Token("exp");
    case LOG:
      return new Token("log");
    case SQRT:
      return new Token("sqrt");
    case ABS_FLOAT:
    case ABS_INT:
      return new Token("abs");
    case DIV_FLOAT:
      return new Token("/");
    default:
      throw new STCRuntimeError("need to add op " + op.toString());
    }
  }
  
  private static void checkCopy(BuiltinOpcode op, Var out, Arg inArg) {
    Type expType = null;
    switch (op) {
    case COPY_BLOB:
      expType = Types.V_BLOB;
      break;
    case COPY_BOOL:
      expType = Types.V_BOOL;
      break;
    case COPY_FLOAT:
      expType = Types.V_FLOAT;
      break;
    case COPY_INT:
      expType = Types.V_INT;
      break;
    case COPY_STRING:
      expType = Types.V_STRING;
      break;
    case COPY_VOID:
      expType = Types.V_VOID;
      break;
    default:
      throw new STCRuntimeError("Unexpected op: " + op);
    }
    if (inArg.isVar()) {
      assert(expType.equals(inArg.getType()));
    } else {
      // getType returns futures for constant vals
      assert(expType.primType() == inArg.getType().primType());
    }
    assert(expType.equals(out.type()));
  }

  private static Expression localStrCat(List<Arg> in, ArrayList<Expression> argExpr) {
    TclString rhs = new TclString("", false);
    for (Expression e: argExpr) {
      rhs.add(e);
    }
    return rhs;
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
    // first try hardcoded
    TclFunRef impl = builtinOpImpls.get(op);
    if (impl != null) {
      return impl;
    }
    return null;
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
    builtinOpImpls.put(BuiltinOpcode.COPY_FILE, new TclFunRef(
            OP_TCL_PKG, "copy_file"));

  }
  
}
