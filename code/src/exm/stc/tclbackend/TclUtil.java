package exm.stc.tclbackend;

import java.util.ArrayList;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.LiteralFloat;
import exm.stc.tclbackend.tree.LiteralInt;
import exm.stc.tclbackend.tree.TclList;
import exm.stc.tclbackend.tree.TclString;
import exm.stc.tclbackend.tree.Value;

public class TclUtil {

  public static Expression argToExpr(Arg in) {
    return argToExpr(in, false);
  } 
  public static Expression argToExpr(Arg in, boolean passThroughNull) {
    if (in == null) {
      if (passThroughNull) {
        return null;
      } else {
        throw new STCRuntimeError("Unexpected null variable in argToExpr");
      }
    }
    switch (in.getKind()) {
    case INTVAL:
      return new LiteralInt(in.getIntLit());
    case BOOLVAL:
      return new LiteralInt(in.getBoolLit() ? 1 : 0);
    case STRINGVAL:
      return new TclString(in.getStringLit(), true);
    case VAR:
      return new Value(TclNamer.prefixVar(in.getVar().name()));
    case FLOATVAL:
      return new LiteralFloat(in.getFloatLit());
    default:
      throw new STCRuntimeError("Unknown oparg type: "
          + in.getKind().toString());
    }
  }

  public static Value varToExpr(Var v) {
    return varToExpr(v, false);
  }
  
  public static Value varToExpr(Var v, boolean passThroughNull) {
    if (v == null) {
      if (passThroughNull) {
        return null;
      } else {
        throw new STCRuntimeError("Unexpected null variable in varToExpr");
      }
    }
    return new Value(TclNamer.prefixVar(v.name()));
  }

  public static List<Expression> varsToExpr(List<Var> inputs) {
    List<Expression> res = new ArrayList<Expression>(inputs.size());
    for (Var in: inputs) {
      res.add(varToExpr(in));
    }
    return res;
  }

  public static TclList tclListOfVariables(List<Var> inputs) {
    TclList result = new TclList();
    for (Var v : inputs)
      result.add(varToExpr(v));
    return result;
  }
}
