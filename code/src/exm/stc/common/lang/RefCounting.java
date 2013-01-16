package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.List;

import exm.stc.common.lang.Var.DefType;

public class RefCounting {
  
  /**
   * @param t
   * @return true if type has read refcount to be managed
   */
  public static boolean hasReadRefcount(Var v) {
    if (Types.isScalarValue(v.type())) {
      return false;
    } else if (v.defType() == DefType.GLOBAL_CONST) {
      return false;
    }
    return true;
  }
  
  /**
   * Return true if writer count is tracked for type
   * @param t
   * @return
   */
  public static boolean hasWriteRefcount(Var v) {
    return Types.isArray(v.type()) && v.defType() != DefType.GLOBAL_CONST;
  }
  
  /**
   * Filter vars to include only variables where writers count is tracked
   * @param outputs
   * @return
   */
  public static List<Var> filterWriteRefcount(List<Var> vars) {
    assert(vars != null);
    List<Var> res = new ArrayList<Var>();
    for (Var var: vars) {
      if (hasWriteRefcount(var)) {
        res.add(var);
      }
    }
    return res;
  }
}
