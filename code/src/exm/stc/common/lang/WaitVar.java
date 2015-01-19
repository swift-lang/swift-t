package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Represent a variable being waited for.
 * TODO: add recursive?
 */
public class WaitVar implements Comparable<WaitVar> {
  public final Var var;
  public final boolean explicit;
  public WaitVar(Var var, boolean explicit) {
    this.var = var;
    this.explicit = explicit;
  }

  /**
   * Remove duplicate entries.  If one is explicit, retained one
   * must be explicit
   * @param list
   */
  public static final void removeDuplicates(List<WaitVar> list) {
    Collections.sort(list);
    ListIterator<WaitVar> it = list.listIterator();
    if (!it.hasNext())
      return;

    WaitVar last = it.next();
    while (it.hasNext()) {
      WaitVar curr = it.next();
      if (curr.var.equals(last.var)) {
        // Remove any duplicates.  Due to ordering, explicit version will be retained
        it.remove();
      } else {
        last = curr;
      }
    }
  }

  /**
   * Make comparable so can sort to find duplicates
   */
  @Override
  public int compareTo(WaitVar o) {
    int res = var.name().compareTo(o.var.name());
    if (res != 0) {
      return res;
    }
    if (explicit == o.explicit)
      return 0;
    // explicit should be before not explicit
    return explicit ? -1 : 1;
  }

  public static List<Var> asVarList(List<WaitVar> waitVars) {
    ArrayList<Var> res = new ArrayList<Var>();
    for (WaitVar wv: waitVars) {
      res.add(wv.var);
    }
    return res;
  }

  public static List<WaitVar> asWaitVarList(List<Var> vars, boolean explicit) {
    ArrayList<WaitVar> res = new ArrayList<WaitVar>(vars.size());
    for (Var v: vars) {
      res.add(new WaitVar(v, explicit));
    }
    return res;
  }

  public static List<WaitVar> makeList(Collection<Var> waitVars, boolean explicit) {
    List<WaitVar> waitVars2 = new ArrayList<WaitVar>(waitVars.size());
    for (Var v: waitVars) {
      waitVars2.add(new WaitVar(v, explicit));
    }
    return waitVars2;
  }

  public static WaitVar find(List<WaitVar> waitVars,
      Var var) {
    for (WaitVar wv: waitVars) {
      if (var.equals(wv.var))
        return wv;
    }
    return null;
  }

  public static void replaceVars(List<WaitVar> waitVars, Map<Var, Arg> renames) {
    boolean replaced = false;
    ListIterator<WaitVar> it = waitVars.listIterator();
    while (it.hasNext()) {
      WaitVar wv = it.next();
      Arg replacement = renames.get(wv.var);
      if (replacement != null && replacement.isVar()) {
        it.set(new WaitVar(replacement.getVar(), wv.explicit));
        replaced = true;
      }
    }
    if (replaced) {
      removeDuplicates(waitVars);
    }
  }

  /**
   * For pretty printing
   */
  @Override
  public String toString() {
    return var.name() + (explicit ? ":EXPLICIT" : "");
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof WaitVar))
        throw new STCRuntimeError("Comparing WaitVar with: " + other +
                                  " of class " + other.getClass());
    WaitVar owv = (WaitVar)other;
    return owv.explicit == explicit &&
           owv.var.equals(this.var);
  }

  @Override
  public int hashCode() {
    return Integer.rotateLeft(var.hashCode(), 1) & (explicit ? 0 : 1);
  }

  public static final List<WaitVar> NONE = Collections.emptyList();
}