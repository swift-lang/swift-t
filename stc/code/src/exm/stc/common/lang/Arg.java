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
import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.tclbackend.tree.TclString;

public class Arg implements Comparable<Arg>, Typed {
  public static final Arg ZERO = Arg.newInt(0);
  public static final Arg ONE = Arg.newInt(1);
  public static final Arg TRUE = Arg.newBool(true);
  public static final Arg FALSE = Arg.newBool(false);

  public static final List<Arg> NONE = Collections.emptyList();


  public static enum ArgKind {
    INTVAL, FLOATVAL, STRINGVAL, BOOLVAL, VAR
  }

  public final ArgKind kind;

  /** Storage for arg, dependent on arg type */
  private final String stringlit;
  private long intlit;
  private final double floatlit;
  private final boolean boollit;
  private final Var var;

  private final int hashCode;

  /**
   * Private constructor: it can only be built using the static builder
   * methods (below)
   *
   * @param type
   * @param stringval
   */
  private Arg(ArgKind kind, String stringlit, Var var, long intlit,
      double floatlit, boolean boollit) {
    super();
    this.kind = kind;
    this.stringlit = stringlit;
    this.intlit = intlit;
    this.floatlit = floatlit;
    this.boollit = boollit;
    this.var = var;
    // precalculate hashcode since this is immutable and frequently stored
    // in maps
    this.hashCode = calcHashCode();
  }

  public static List<Arg> cloneList(List<Arg> inputs) {
    // Can do shallow copy since Arg is immutable
    return new ArrayList<Arg>(inputs);
  }

  public static Arg newInt(long v) {
    return new Arg(ArgKind.INTVAL, null, null, v, -1, false);
  }

  public static Arg newFloat(double v) {
    return new Arg(ArgKind.FLOATVAL, null, null, -1, v, false);
  }

  public static Arg newString(String v) {
    assert (v != null);
    return new Arg(ArgKind.STRINGVAL, v, null, -1, -1, false);
  }

  public static Arg newBool(boolean v) {
    return new Arg(ArgKind.BOOLVAL, null, null, -1, -1, v);
  }

  public static Arg newVar(Var var) {
    assert (var != null);
    return new Arg(ArgKind.VAR, null, var, -1, -1, false);
  }

  public ArgKind getKind() {
    return kind;
  }

  private void checkKind(ArgKind expected) {
    if (this.kind != expected) {
      throw new STCRuntimeError(this.kind + " where " + expected +
                                " was expected");
    }
  }

  public String getString() {
    checkKind(ArgKind.STRINGVAL);
    return stringlit;
  }

  public long getInt() {
    checkKind(ArgKind.INTVAL);
    return intlit;
  }

  public double getFloat() {
    checkKind(ArgKind.FLOATVAL);
    return floatlit;
  }

  public boolean getBool() {
    checkKind(ArgKind.BOOLVAL);
    return boollit;
  }

  public Var getVar() {
    checkKind(ArgKind.VAR);
    return var;
  }

  /**
   * Return the type if used as a future
   * @return
   */
  public Type futureType() {
    return typeInternal(true);
  }

  /**
   * Return the type if used as a value;
   * @return
   */
  @Override
  public Type type() {
    return typeInternal(false);
  }

  /**
   * Work out type of arg, dealing with fact that constants
   * can be interpreted as futures or values
   * @param futureContext
   * @return
   */
  public Type typeInternal(boolean futureContext) {
  switch (kind) {
    case INTVAL:
      if (futureContext) {
        return Types.F_INT;
      } else {
        return Types.V_INT;
      }
    case STRINGVAL:
      // use same escaping as TCL
      if (futureContext) {
        return Types.F_STRING;
      } else {
        return Types.V_STRING;
      }
    case FLOATVAL:
      if (futureContext) {
        return Types.F_FLOAT;
      } else {
        return Types.V_FLOAT;
      }
    case BOOLVAL:
      if (futureContext) {
        return Types.F_BOOL;
      } else {
        return Types.V_BOOL;
      }
    case VAR:
      return this.var.type();
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.kind.toString());
    }
  }

  public boolean isVar() {
    return kind == ArgKind.VAR;
  }

  public boolean isInt() {
    return kind == ArgKind.INTVAL;
  }

  public boolean isBool() {
    return kind == ArgKind.BOOLVAL;
  }

  public boolean isFloat() {
    return kind == ArgKind.FLOATVAL;
  }

  public boolean isString() {
    return kind == ArgKind.STRINGVAL;
  }

  /**
   * Is the oparg an int that can be immediately read (i.e. either a value or a
   * literal.
   *
   * @return
   */
  public boolean isImmInt() {
    return kind == ArgKind.INTVAL
        || (kind == ArgKind.VAR && Types.isIntVal(var));
  }

  public boolean isImmFloat() {
    return kind == ArgKind.FLOATVAL
        || (kind == ArgKind.VAR && Types.isFloatVal(var));
  }

  public boolean isImmString() {
    return kind == ArgKind.STRINGVAL
        || (kind == ArgKind.VAR && Types.isStringVal(var));
  }

  public boolean isImmBool() {
    return kind == ArgKind.BOOLVAL
        || (kind == ArgKind.VAR && Types.isBoolVal(var));
  }

  public boolean isImmBlob() {
    return kind == ArgKind.VAR && Types.isBlobVal(var);
  }

  @Override
  public String toString() {
    switch (kind) {
    case INTVAL:
      return Long.toString(this.intlit);
    case STRINGVAL:
      // use same escaping as Tcl
      return "\"" + TclString.tclEscapeString(this.stringlit) + "\"";
    case FLOATVAL:
      return Double.toString(this.floatlit);
    case BOOLVAL:
      return Boolean.toString(this.boollit);
    case VAR:
      // return this.var.name();
      return "Arg: Var: " + this.var.name();
    default:
      throw new STCRuntimeError("Unknown Arg type: " + this.kind.toString());
    }
  }

  /**
   * Translate to string with type info
   * @return
   */
  public String toStringTyped() {
    return toString() + ":" + this.type().typeName();
  }

  /**
   * Define hashCode and equals so this can be used as key in hash table
   */
  @Override
  public int hashCode() {
    return hashCode;
  }

  private int calcHashCode() {
    int hash1;
    switch (kind) {
    case INTVAL:
      hash1 = ((Long) this.intlit).hashCode();
      break;
    case STRINGVAL:
      hash1 = this.stringlit.hashCode();
      break;
    case FLOATVAL:
      hash1 = ((Double) this.floatlit).hashCode();
      break;
    case BOOLVAL:
      hash1 = this.boollit ? 0 : 1;
      break;
    case VAR:
      hash1 = this.var.name().hashCode();
      break;
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.kind.toString());
    }
    return this.kind.hashCode() ^ hash1;
  }

  @Override
  public boolean equals(Object otherO) {
    if (!(otherO instanceof Arg)) {
      throw new STCRuntimeError("cannot compare oparg and "
          + otherO.getClass().getName());
    }
    Arg other = (Arg) otherO;
    if (this.kind != other.kind) {
      return false;
    }
    switch (this.kind) {
    case INTVAL:
      return this.intlit == other.intlit;
    case STRINGVAL:
      return this.stringlit.equals(other.stringlit);
    case FLOATVAL:
      return this.floatlit == other.floatlit;
    case BOOLVAL:
      return this.boollit == other.boollit;
    case VAR:
      // Compare only on name, assuming name is unique
      return this.var.name().equals(other.var.name());
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.kind.toString());
    }
  }

  @Override
  public int compareTo(Arg o) {
    int typeComp = kind.compareTo(o.kind);
    if (typeComp == 0) {
      switch (kind) {
      case BOOLVAL:
        return ((Boolean) boollit).compareTo(o.boollit);
      case INTVAL:
        return ((Long) intlit).compareTo(o.intlit);
      case FLOATVAL:
        return ((Double) floatlit).compareTo(o.floatlit);
      case STRINGVAL:
        return stringlit.compareTo(o.stringlit);
      case VAR:
        return var.name().compareTo(o.getVar().name());
      default:
        throw new STCRuntimeError("couldn't compare oparg type "
            + this.kind.toString());
      }
    } else {
      return typeComp;
    }
  }

  public static List<Arg> fromVarList(List<Var> vars) {
    ArrayList<Arg> result = new ArrayList<Arg>(vars.size());
    for (Var v : vars) {
      result.add(Arg.newVar(v));
    }
    return result;
  }

  public boolean isConst() {
    return this.kind != ArgKind.VAR;
  }

  public Ternary isMapped() {
    switch (kind) {
      case VAR:
        return var.isMapped();
      case INTVAL:
      case FLOATVAL:
      case STRINGVAL:
      case BOOLVAL:
        return Ternary.FALSE;
      default:
        throw new STCRuntimeError("Unknown kind " + kind);
    }
  }

  public List<Arg> asList() {
    return Collections.singletonList(this);
  }

  /**
   * Extract string literals from list, assuming all members
   * are string literals
   * @param l a list of string args
   * @return
   */
  public static List<String> extractStrings(List<Arg> l) {
    ArrayList<String> result = new ArrayList<String>(l.size());
    for (Arg a: l) {
      result.add(a.getString());
    }
    return result;
  }
}
