package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents a variable passed between tasks
 *
 */
public class PassedVar {
  public final Var var;
  /** True if var isn't read in inner scope */
  public final boolean writeOnly;
  
  public PassedVar(Var var, boolean writeOnly) {
    super();
    this.var = var;
    this.writeOnly = writeOnly;
  }
  
  public static List<Var> extractVars(Collection<PassedVar> vars) {
    ArrayList<Var> res = new ArrayList<Var>(vars.size());
    for (PassedVar pv: vars) {
      res.add(pv.var);
    }
    return res;
  }
  
  public static final List<PassedVar> NONE = Collections.emptyList();

  public static List<Var> filterRead(List<PassedVar> vars) {
    ArrayList<Var> res = new ArrayList<Var>();
    for (PassedVar v: vars) {
      if (!v.writeOnly) {
        res.add(v.var);
      }
    }
    return res;
  }
  
  public static boolean contains(Collection<PassedVar> vars, Var v) {
    for (PassedVar pv: vars) {
      if (pv.var.equals(v)) {
        return true;
      }
    }
    return false;
  }
  
  @Override
  public String toString() {
    String res = var.name() + "<";
    if (writeOnly) {
      res += "WRITEONLY";
    } else {
      res += "READWRITE";
    }
    res += ">";
    return res;
  }
}