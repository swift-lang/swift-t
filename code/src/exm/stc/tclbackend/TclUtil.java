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
  
  public static List<Expression> argsToExpr(List<Arg> in) {
    List<Expression> res = new ArrayList<Expression>(in.size());
    for (Arg a: in) {
      res.add(argToExpr(a));
    }
    return res;
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
    for (Var v: inputs)
      result.add(varToExpr(v));
    return result;
  }
  
  public static TclList tclListOfArgs(List<Arg> inputs) {
    TclList result = new TclList();
    for (Arg a: inputs)
      result.add(argToExpr(a));
    return result;
  }
  
  /**
   * Try to pack list of expressions into a string that is a valid
   * tcl list
   * Fallback to list if we don't know how to do escaping correctly
   * @param ruleTokens
   * @return
   */
  public static Expression tclStringAsList(List<Expression> ruleTokens) {
    boolean canUseString = true;
    for (Expression tok: ruleTokens) {
      if (!tok.supportsStringList()) {
        canUseString = false;
        break;
      }
    }
    
    if (canUseString) {
      return new TclString(ruleTokens, true);
    } else {
      return new TclList(ruleTokens);
    }
  }
}
