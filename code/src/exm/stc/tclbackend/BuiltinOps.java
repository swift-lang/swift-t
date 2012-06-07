package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Variable;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.tclbackend.tree.Command;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.SetVariable;
import exm.stc.tclbackend.tree.Square;
import exm.stc.tclbackend.tree.TclList;
import exm.stc.tclbackend.tree.TclString;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Token;

public class BuiltinOps {


  public static TclTree genLocalOpTcl(BuiltinOpcode op, Variable out, List<Arg> in,
      ArrayList<Expression> argExpr) {

    if (op == BuiltinOpcode.ASSERT || op == BuiltinOpcode.ASSERT_EQ ||
        op == BuiltinOpcode.TRACE || op == BuiltinOpcode.METADATA) {
      assert(out == null);
      String tclFn;
      switch (op) {
      case ASSERT:
        tclFn = "turbine::assert_impl";
        break;
      case ASSERT_EQ:
        tclFn = "turbine::assertEqual_impl";
        break;
      case TRACE:
        tclFn = "turbine::trace_impl";
        break;
      case METADATA:
        tclFn = "turbine::metadata_impl";
        break;
      default:
        throw new STCRuntimeError("Cn't handle local op: "
            + op.toString());
      }
      return new Command(tclFn, argExpr);
    } else {
      if (op == BuiltinOpcode.ARGC_GET || op == BuiltinOpcode.ARGV_CONTAINS
              || op == BuiltinOpcode.ARGV_GET || op == BuiltinOpcode.N_ADLB_SERVERS
              || op == BuiltinOpcode.N_ENGINES ||op == BuiltinOpcode.N_WORKERS
              || op == BuiltinOpcode.GETENV) {
        assert(out != null);
        String tclFn;
        switch (op) {
        case ARGC_GET:
          tclFn = "turbine::argc_get_impl";
          break;
        case ARGV_CONTAINS:
          tclFn = "turbine::argv_contains_impl";
          break;
        case ARGV_GET:
          tclFn = "turbine::argv_get_impl";
          break;
        case N_ADLB_SERVERS:
          tclFn = "turbine::adlb_servers";
          break;
        case N_ENGINES:
          tclFn = "turbine::turbine_engines";
          break;
        case N_WORKERS:
          tclFn = "turbine::turbine_workers";
          break;
        case GETENV:
          tclFn = "turbine::getenv_impl";
          break;
        default:
          throw new STCRuntimeError("Can't handle local op: "
              + op.toString());
        }
        return new SetVariable(TclNamer.prefixVar(out.getName()),
                          Square.fnCall(tclFn, argExpr.toArray(
                              new Expression[argExpr.size()])));
      } else if (op == BuiltinOpcode.PRINTF || op == BuiltinOpcode.SPRINTF) {
        Square fmtArgs = new TclList(argExpr);
        Square fmt = new Square(new Token("eval"), new Token("format"),
                                                                  fmtArgs);
        if (op ==  BuiltinOpcode.PRINTF) {
          return new Command(new Token("puts"), fmt);
        } else {
          assert(op == BuiltinOpcode.SPRINTF);
          return new SetVariable(TclNamer.prefixVar(out.getName()), fmt);
        }
      } else {
        assert(out != null);
        assert(Types.isScalarValue(out.getType()));
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
        } else if (op == BuiltinOpcode.RAND_INT) {
          rhs = new Square(new Token("turbine::randint_impl"), argExpr.get(0),
                                                      argExpr.get(1));
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
        return new SetVariable(TclNamer.prefixVar(out.getName()), rhs); 
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
    case RANDOM:
      // No arguments to expr function
      return new Expression[] { arithOpTok(op), new Token("()") };
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
    case RANDOM:
      return new Token("rand");
    case DIV_FLOAT:
      return new Token("/");
    default:
      throw new STCRuntimeError("need to add op " + op.toString());
    }
  }
  
  private static void checkCopy(BuiltinOpcode op, Variable out, Arg inArg) {
    SwiftType expType = null;
    switch (op) {
    case COPY_BLOB:
      expType = Types.VALUE_BLOB;
      break;
    case COPY_BOOL:
      expType = Types.VALUE_BOOLEAN;
      break;
    case COPY_FLOAT:
      expType = Types.VALUE_FLOAT;
      break;
    case COPY_INT:
      expType = Types.VALUE_INTEGER;
      break;
    case COPY_STRING:
      expType = Types.VALUE_STRING;
      break;
    case COPY_VOID:
      expType = Types.VALUE_VOID;
      break;
    }
    if (inArg.getType() == ArgType.VAR) {
      assert(expType.equals(inArg.getSwiftType()));
    } else {
      // getSwiftType returns futures for constant vals
      assert(expType.getPrimitiveType()
          == inArg.getSwiftType().getPrimitiveType());
    }
    assert(expType.equals(out.getType()));
  }

  private static Expression localStrCat(List<Arg> in, ArrayList<Expression> argExpr) {
    TclString rhs = new TclString("", false);
    for (Expression e: argExpr) {
      rhs.add(e);
    }
    return rhs;
  }

  private static Map<BuiltinOpcode, String> builtinOpImpls
    = new HashMap<BuiltinOpcode, String>();
  
  static {
    populateBuiltinOpImpls();
  }

  /**
   * Get asynchronous implementation of builtin op
   * TODO: this is a temporary solution to get this working.
   * Will be better later on to have arith ops, etc in 
   * different namespace entirely
   */
  public static String getBuiltinOpImpl(BuiltinOpcode op) {
    return builtinOpImpls.get(op);
  }
  
  private static void populateBuiltinOpImpls() {
    builtinOpImpls.put(BuiltinOpcode.ARGC_GET, "argc");
    builtinOpImpls.put(BuiltinOpcode.ARGV_CONTAINS, "argv_contains");
    builtinOpImpls.put(BuiltinOpcode.ARGV_GET, "argv");
    builtinOpImpls.put(BuiltinOpcode.GETENV, "getenv");
    builtinOpImpls.put(BuiltinOpcode.N_WORKERS, "turbine_workers");
    builtinOpImpls.put(BuiltinOpcode.N_ENGINES, "turbine_engines");
    builtinOpImpls.put(BuiltinOpcode.N_ADLB_SERVERS, "adlb_servers");
    builtinOpImpls.put(BuiltinOpcode.PLUS_INT, "plus_integer");
    builtinOpImpls.put(BuiltinOpcode.MINUS_INT, "minus_integer");
    builtinOpImpls.put(BuiltinOpcode.MULT_INT, "multiply_integer");
    builtinOpImpls.put(BuiltinOpcode.DIV_INT, "divide_integer");
    builtinOpImpls.put(BuiltinOpcode.MOD_INT, "mod_integer");
    builtinOpImpls.put(BuiltinOpcode.NEGATE_INT, "negate_integer");
    builtinOpImpls.put(BuiltinOpcode.MAX_INT, "max_integer");
    builtinOpImpls.put(BuiltinOpcode.MIN_INT, "min_integer");
    builtinOpImpls.put(BuiltinOpcode.ABS_INT, "abs_integer");
    builtinOpImpls.put(BuiltinOpcode.POW_INT, "pow_integer");
    builtinOpImpls.put(BuiltinOpcode.EQ_INT, "eq_integer");
    builtinOpImpls.put(BuiltinOpcode.NEQ_INT, "neq_integer");
    builtinOpImpls.put(BuiltinOpcode.LT_INT, "lt_integer");
    builtinOpImpls.put(BuiltinOpcode.LTE_INT, "lte_integer");
    builtinOpImpls.put(BuiltinOpcode.GT_INT, "gt_integer");
    builtinOpImpls.put(BuiltinOpcode.GTE_INT, "gte_integer");
    builtinOpImpls.put(BuiltinOpcode.PLUS_FLOAT, "plus_float");
    builtinOpImpls.put(BuiltinOpcode.MINUS_FLOAT, "minus_float");
    builtinOpImpls.put(BuiltinOpcode.MULT_FLOAT, "multiply_float");
    builtinOpImpls.put(BuiltinOpcode.DIV_FLOAT, "divide_float");
    builtinOpImpls.put(BuiltinOpcode.NEGATE_FLOAT, "negate_float");
    builtinOpImpls.put(BuiltinOpcode.MAX_FLOAT, "max_float");
    builtinOpImpls.put(BuiltinOpcode.MIN_FLOAT, "min_float");
    builtinOpImpls.put(BuiltinOpcode.ABS_FLOAT, "abs_float");
    builtinOpImpls.put(BuiltinOpcode.POW_FLOAT, "pow_float");
    builtinOpImpls.put(BuiltinOpcode.IS_NAN, "is_nan");
    builtinOpImpls.put(BuiltinOpcode.CEIL, "ceil");
    builtinOpImpls.put(BuiltinOpcode.FLOOR, "floor");
    builtinOpImpls.put(BuiltinOpcode.ROUND, "round");
    builtinOpImpls.put(BuiltinOpcode.INTTOFLOAT, "itof");
    builtinOpImpls.put(BuiltinOpcode.STRTOINT, "toint");
    builtinOpImpls.put(BuiltinOpcode.INTTOSTR, "fromint");
    builtinOpImpls.put(BuiltinOpcode.STRTOFLOAT, "tofloat");
    builtinOpImpls.put(BuiltinOpcode.FLOATTOSTR, "fromfloat");
    builtinOpImpls.put(BuiltinOpcode.EXP, "exp");
    builtinOpImpls.put(BuiltinOpcode.LOG, "log");
    builtinOpImpls.put(BuiltinOpcode.SQRT, "sqrt");
    builtinOpImpls.put(BuiltinOpcode.EQ_FLOAT, "eq_float");
    builtinOpImpls.put(BuiltinOpcode.NEQ_FLOAT, "neq_float");
    builtinOpImpls.put(BuiltinOpcode.LT_FLOAT, "lt_float");
    builtinOpImpls.put(BuiltinOpcode.LTE_FLOAT, "lte_float");
    builtinOpImpls.put(BuiltinOpcode.GT_FLOAT, "gt_float");
    builtinOpImpls.put(BuiltinOpcode.GTE_FLOAT, "gte_float");      
    builtinOpImpls.put(BuiltinOpcode.EQ_STRING, "eq_string");
    builtinOpImpls.put(BuiltinOpcode.NEQ_STRING, "neq_string");
    builtinOpImpls.put(BuiltinOpcode.STRCAT, "strcat");
    builtinOpImpls.put(BuiltinOpcode.SUBSTRING, "substrict");
    builtinOpImpls.put(BuiltinOpcode.EQ_BOOL, "eq_boolean");
    builtinOpImpls.put(BuiltinOpcode.NEQ_BOOL, "neq_boolean");
    builtinOpImpls.put(BuiltinOpcode.AND, "and");
    builtinOpImpls.put(BuiltinOpcode.OR, "or");
    builtinOpImpls.put(BuiltinOpcode.XOR, "xor");
    builtinOpImpls.put(BuiltinOpcode.NOT, "not");
    builtinOpImpls.put(BuiltinOpcode.COPY_INT, "copy_integer");
    builtinOpImpls.put(BuiltinOpcode.COPY_VOID, "copy_void");
    builtinOpImpls.put(BuiltinOpcode.COPY_FLOAT, "copy_float");
    builtinOpImpls.put(BuiltinOpcode.COPY_STRING, "copy_string");
    builtinOpImpls.put(BuiltinOpcode.COPY_BOOL, "copy_boolean");
    builtinOpImpls.put(BuiltinOpcode.COPY_BLOB, "copy_blob");
    builtinOpImpls.put(BuiltinOpcode.ASSERT, "assert");
    builtinOpImpls.put(BuiltinOpcode.ASSERT_EQ, "assertEqual");
    builtinOpImpls.put(BuiltinOpcode.TRACE, "trace");
    builtinOpImpls.put(BuiltinOpcode.METADATA, "metadata");
    builtinOpImpls.put(BuiltinOpcode.PRINTF, "printf");
    builtinOpImpls.put(BuiltinOpcode.SPRINTF, "sprintf");
    builtinOpImpls.put(BuiltinOpcode.RANDOM, "random");
    builtinOpImpls.put(BuiltinOpcode.RAND_INT, "randint");
  }
  
}
