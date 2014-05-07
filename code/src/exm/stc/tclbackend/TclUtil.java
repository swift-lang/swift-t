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
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.Expression.ExprContext;
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
      return varToExpr(in.getVar());
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
    
    Value val = new Value(TclNamer.prefixVar(v.name()));
    if (representationMaybeTclList(v)) {
      val.setTreatAsList(true);
    }
    val.setSupportsStringList(supportsStringList(v.type()));
    return val;
  }

  /**
   * Whether we can include value of this time in string list, e.g.
   * "${x} ${y}"
   * @param type
   * @return
   */
  public static boolean supportsStringList(Type type) {
    // Can't escape these types correctly
    List<Type> badTypes = Arrays.asList(Types.V_STRING, Types.V_BLOB);
    for (Type t: badTypes) {
      if (type.assignableTo(t)) {
        return false;
      }
    }
    return true;
  }

  public static boolean representationMaybeTclList(Var v) {
    return representationMaybeTclList(v.type(), v.isRuntimeAlias());
  }
  
  public static boolean representationMaybeTclList(Type type,
                                   Ternary isRuntimeAlias) {
    if (Types.isFile(type) || Types.isStructLocal(type) ||
        Types.isFileVal(type) || Types.isContainerLocal(type)) {
      // These always are lists
      return true;
    } else if (isHandle(type) && isRuntimeAlias != Ternary.FALSE) {
      // if it's an alias at runtime, might be a list of id + subscript,
      // so assume it needs to be handled specially
      return true;
    }
    return false;
  }

  /**
   * If it's a handle to shared data
   * @param type
   * @return
   */
  private static boolean isHandle(Type type) {
    return Types.isPrimFuture(type) || Types.isStruct(type) ||
               Types.isContainer(type) || Types.isPrimUpdateable(type) ||
               Types.isRef(type);
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
      return new TclString(ruleTokens, ExprContext.LIST_STRING);
    } else {
      return new TclList(ruleTokens);
    }
  }
}
