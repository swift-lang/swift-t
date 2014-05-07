package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

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
  
  // Merge two lists of passed variables
  public static List<PassedVar> mergeLists(List<PassedVar> l1,
      Collection<PassedVar> l2) {
    
    List<PassedVar> newList = new ArrayList<PassedVar>(l1.size() + l2.size());
    newList.addAll(l1);
    newList.addAll(l2);
    
    // Sort by variable name, putting writeOnly after readWrite
    Comparator<PassedVar> cmp = new Comparator<PassedVar>() {

      @Override
      public int compare(PassedVar o1, PassedVar o2) {
        int varCmp = o1.var.compareTo(o2.var);
        if (varCmp != 0) {
          return varCmp;
        }
        
        if (o2.writeOnly) {
          // writeOnly goes second
          return -1;
        } else if (o1.writeOnly) {
          // writeOnly goes second
          return 1;
        } else {
          return 0;
        }
      }
      
    };
    
    Collections.sort(newList, cmp);
   
    PassedVar prev = null;
    ListIterator<PassedVar> it = newList.listIterator();
    while (it.hasNext()) {
      PassedVar curr = it.next();
      if (prev != null && curr.var.equals(prev.var)) {
        // Retain readWrite if conflict
        
        // Should be true from sort order:
        assert(curr.writeOnly || !prev.writeOnly);
        it.remove();
      }
      prev = curr;
    }
    
    return newList;
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