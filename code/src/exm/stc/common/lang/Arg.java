package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types.Type;
import exm.stc.tclbackend.tree.TclString;

public class Arg implements Comparable<Arg> {
  public static enum ArgKind {
    INTVAL, FLOATVAL, STRINGVAL, BOOLVAL, VAR
  }

  public final ArgKind kind;

  /** Storage for arg, dependent on arg type */
  private String stringlit;
  private long intlit;
  private final double floatlit;
  private final boolean boollit;
  private Var var;

  /**
   * Private constructors so that it can only be build using static builder
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
  }

  public static List<Arg> cloneList(List<Arg> inputs) {
    ArrayList<Arg> res = new ArrayList<Arg>(inputs.size());
    for (Arg i : inputs) {
      res.add(i.clone());
    }
    return res;
  }

  public Arg clone() {
    return new Arg(kind, stringlit, var, intlit, floatlit, boollit);
  }

  public static Arg createIntLit(long v) {
    return new Arg(ArgKind.INTVAL, null, null, v, -1, false);
  }

  public static Arg createFloatLit(double v) {
    return new Arg(ArgKind.FLOATVAL, null, null, -1, v, false);
  }

  public static Arg createStringLit(String v) {
    assert (v != null);
    return new Arg(ArgKind.STRINGVAL, v, null, -1, -1, false);
  }

  public static Arg createBoolLit(boolean v) {
    return new Arg(ArgKind.BOOLVAL, null, null, -1, -1, v);
  }

  public static Arg createVar(Var var) {
    assert (var != null);
    return new Arg(ArgKind.VAR, null, var, -1, -1, false);
  }

  public ArgKind getKind() {
    return kind;
  }

  public String getStringLit() {
    if (kind == ArgKind.STRINGVAL) {
      return stringlit;
    } else {
      throw new STCRuntimeError("getStringVal for non-string type");
    }
  }

  public long getIntLit() {
    if (kind == ArgKind.INTVAL) {
      return intlit;
    } else {
      throw new STCRuntimeError("getIntVal for non-int type");
    }
  }

  public double getFloatLit() {
    if (kind == ArgKind.FLOATVAL) {
      return floatlit;
    } else {
      throw new STCRuntimeError("getFloatVal for non-float type");
    }
  }

  public boolean getBoolLit() {
    if (kind == ArgKind.BOOLVAL) {
      return boollit;
    } else {
      throw new STCRuntimeError("getBoolLit for non-bool type");
    }
  }

  public Var getVar() {
    if (kind == ArgKind.VAR) {
      return var;
    } else {
      throw new STCRuntimeError("getVariable for non-variable type");
    }
  }

  public void replaceVariable(Var var) {
    if (kind == ArgKind.VAR) {
      this.var = var;
    } else {
      throw new STCRuntimeError("replaceVariable for non-variable type");
    }
  }

  public Type getType() {
    switch (kind) {
    case INTVAL:
      return Types.F_INT;
    case STRINGVAL:
      // use same escaping as TCL
      return Types.F_STRING;
    case FLOATVAL:
      return Types.F_FLOAT;
    case BOOLVAL:
      return Types.F_BOOL;
    case VAR:
      return this.var.type();
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.kind.toString());
    }
  }
  
  public boolean isVar() {
    return kind == ArgKind.VAR;
  }
  
  public boolean isIntVal() {
    return kind == ArgKind.INTVAL;
  }
  
  public boolean isBoolVal() {
    return kind == ArgKind.BOOLVAL;
  }
  
  public boolean isFloatVal() {
    return kind == ArgKind.FLOATVAL;
  }
  
  public boolean isStringVal() {
    return kind == ArgKind.STRINGVAL;
  }

  /**
   * Is the oparg an int that can be immediately read (i.e. either a value or a
   * literal.
   * 
   * @return
   */
  public boolean isImmediateInt() {
    return kind == ArgKind.INTVAL
        || (kind == ArgKind.VAR && var.type().equals(Types.V_INT));
  }

  public boolean isImmediateFloat() {
    return kind == ArgKind.FLOATVAL
        || (kind == ArgKind.VAR && var.type().equals(Types.V_FLOAT));
  }

  public boolean isImmediateString() {
    return kind == ArgKind.STRINGVAL
        || (kind == ArgKind.VAR && var.type().equals(Types.V_STRING));
  }

  public boolean isImmediateBool() {
    return kind == ArgKind.BOOLVAL
        || (kind == ArgKind.VAR && var.type().equals(Types.V_BOOL));
  }
  
  public boolean isImmediateBlob() {
    return kind == ArgKind.VAR && var.type().equals(Types.V_BLOB);
  }

  @Override
  public String toString() {
    switch (kind) {
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
      return this.var.name();
    default:
      throw new STCRuntimeError("Unknown oparg type " + this.kind.toString());
    }
  }

  /**
   * Define hashCode and equals so this can be used as key in hash table
   */
  @Override
  public int hashCode() {
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

  /**
   * Put all variable names in a collection of opargs into addTo
   */
  public static void collectVarNames(Collection<String> addTo,
      Collection<Arg> args) {
    for (Arg o : args) {
      if (o.kind == ArgKind.VAR) {
        addTo.add(o.getVar().name());
      }
    }
  }

  public static List<String> varNameList(List<Arg> inputs) {
    ArrayList<String> result = new ArrayList<String>();
    collectVarNames(result, inputs);
    return result;
  }

  public static List<Arg> fromVarList(List<Var> vars) {
    ArrayList<Arg> result = new ArrayList<Arg>(vars.size());
    for (Var v : vars) {
      result.add(Arg.createVar(v));
    }
    return result;
  }

  public boolean isConstant() {
    return this.kind != ArgKind.VAR;
  }
}
