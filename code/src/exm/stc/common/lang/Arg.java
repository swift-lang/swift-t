package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.tclbackend.tree.TclString;

public class Arg implements Comparable<Arg> {
  public static enum ArgType {
    INTVAL, FLOATVAL, STRINGVAL, BOOLVAL, VAR
  }

  public final ArgType type;

  /** Storage for arg, dependent on arg type */
  private String stringlit;
  private long intlit;
  private final double floatlit;
  private final boolean boollit;
  private Variable var;

  /**
   * Private constructors so that it can only be build using static builder
   * methods (below)
   * 
   * @param type
   * @param stringval
   */
  private Arg(ArgType type, String stringlit, Variable var, long intlit,
      double floatlit, boolean boollit) {
    super();
    this.type = type;
    this.stringlit = stringlit;
    this.intlit = intlit;
    this.floatlit = floatlit;
    this.boollit = boollit;
    this.var = var;
  }

  public static List<Arg> cloneList(List<Arg> inputs) {
    ArrayList<Arg> res = new ArrayList<Arg>(inputs.size());
    for (Arg i : inputs) {
      res.add(i.clone());
    }
    return res;
  }

  public Arg clone() {
    return new Arg(type, stringlit, var, intlit, floatlit, boollit);
  }

  public static Arg createIntLit(long v) {
    return new Arg(ArgType.INTVAL, null, null, v, -1, false);
  }

  public static Arg createFloatLit(double v) {
    return new Arg(ArgType.FLOATVAL, null, null, -1, v, false);
  }

  public static Arg createStringLit(String v) {
    assert (v != null);
    return new Arg(ArgType.STRINGVAL, v, null, -1, -1, false);
  }

  public static Arg createBoolLit(boolean v) {
    return new Arg(ArgType.BOOLVAL, null, null, -1, -1, v);
  }

  public static Arg createVar(Variable var) {
    assert (var != null);
    return new Arg(ArgType.VAR, null, var, -1, -1, false);
  }

  public ArgType getType() {
    return type;
  }

  public String getStringLit() {
    if (type == ArgType.STRINGVAL) {
      return stringlit;
    } else {
      throw new STCRuntimeError("getStringVal for non-string type");
    }
  }

  public long getIntLit() {
    if (type == ArgType.INTVAL) {
      return intlit;
    } else {
      throw new STCRuntimeError("getIntVal for non-int type");
    }
  }

  public double getFloatLit() {
    if (type == ArgType.FLOATVAL) {
      return floatlit;
    } else {
      throw new STCRuntimeError("getFloatVal for non-float type");
    }
  }

  public boolean getBoolLit() {
    if (type == ArgType.BOOLVAL) {
      return boollit;
    } else {
      throw new STCRuntimeError("getBoolLit for non-bool type");
    }
  }

  public Variable getVar() {
    if (type == ArgType.VAR) {
      return var;
    } else {
      throw new STCRuntimeError("getVariable for non-variable type");
    }
  }

  public void replaceVariable(Variable var) {
    if (type == ArgType.VAR) {
      this.var = var;
    } else {
      throw new STCRuntimeError("replaceVariable for non-variable type");
    }
  }

  public SwiftType getSwiftType() {
    switch (type) {
    case INTVAL:
      return Types.FUTURE_INTEGER;
    case STRINGVAL:
      // use same escaping as TCL
      return Types.FUTURE_STRING;
    case FLOATVAL:
      return Types.FUTURE_FLOAT;
    case BOOLVAL:
      return Types.FUTURE_BOOLEAN;
    case VAR:
      return this.var.getType();
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.type.toString());
    }
  }

  /**
   * Is the oparg an int that can be immediately read (i.e. either a value or a
   * literal.
   * 
   * @return
   */
  public boolean isImmediateInt() {
    return type == ArgType.INTVAL
        || (type == ArgType.VAR && var.getType().equals(Types.VALUE_INTEGER));
  }

  public boolean isImmediateFloat() {
    return type == ArgType.FLOATVAL
        || (type == ArgType.VAR && var.getType().equals(Types.VALUE_FLOAT));
  }

  public boolean isImmediateString() {
    return type == ArgType.STRINGVAL
        || (type == ArgType.VAR && var.getType().equals(Types.VALUE_STRING));
  }

  public boolean isImmediateBool() {
    return type == ArgType.BOOLVAL
        || (type == ArgType.VAR && var.getType().equals(Types.VALUE_BOOLEAN));
  }

  @Override
  public String toString() {
    switch (type) {
    case INTVAL:
      return Long.toString(this.intlit);
    case STRINGVAL:
      // use same escaping as TCL
      return "\"" + TclString.tclEscapeString(this.stringlit) + "\"";
    case FLOATVAL:
      return Double.toString(this.floatlit);
    case BOOLVAL:
      return Boolean.toString(this.boollit);
    case VAR:
      return this.var.getName();
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.type.toString());
    }
  }

  /**
   * Define hashCode and equals so this can be used as key in hash table
   */
  @Override
  public int hashCode() {
    int hash1;
    switch (type) {
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
      hash1 = this.var.getName().hashCode();
      break;
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.type.toString());
    }
    return this.type.hashCode() ^ hash1;
  }

  @Override
  public boolean equals(Object otherO) {
    if (!(otherO instanceof Arg)) {
      throw new STCRuntimeError("cannot compare oparg and "
          + otherO.getClass().getName());
    }
    Arg other = (Arg) otherO;
    if (this.type != other.type) {
      return false;
    }
    switch (this.type) {
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
      return this.var.getName().equals(other.var.getName());
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.type.toString());
    }
  }

  @Override
  public int compareTo(Arg o) {
    int typeComp = type.compareTo(o.type);
    if (typeComp == 0) {
      switch (type) {
      case BOOLVAL:
        return ((Boolean) boollit).compareTo(o.boollit);
      case INTVAL:
        return ((Long) intlit).compareTo(o.intlit);
      case FLOATVAL:
        return ((Double) floatlit).compareTo(o.floatlit);
      case STRINGVAL:
        return stringlit.compareTo(o.stringlit);
      case VAR:
        return var.getName().compareTo(o.getVar().getName());
      default:
        throw new STCRuntimeError("couldn't compare oparg type "
            + this.type.toString());
      }
    } else {
      return typeComp;
    }
  }

  /**
   * Put all variable names in a collection of opargs into addTo
   */
  public static void collectVarNames(Collection<String> addTo,
      Collection<Arg> args) {
    for (Arg o : args) {
      if (o.type == ArgType.VAR) {
        addTo.add(o.getVar().getName());
      }
    }
  }

  public static List<String> varNameList(List<Arg> inputs) {
    ArrayList<String> result = new ArrayList<String>();
    collectVarNames(result, inputs);
    return result;
  }

  public static List<Arg> fromVarList(List<Variable> vars) {
    ArrayList<Arg> result = new ArrayList<Arg>(vars.size());
    for (Variable v : vars) {
      result.add(Arg.createVar(v));
    }
    return result;
  }

  public boolean isConstant() {
    return this.type != ArgType.VAR;
  }

}
