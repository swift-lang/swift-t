package exm.stc.ic.opt;

import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;

public class Semantics {
  /**
   * True if can pass to child task
   * @param t
   * @return
   */
  public static boolean canPassToChildTask(Type t) {
    if (t.assignableTo(Types.V_BLOB)) {
      return false;
    } else if (t.assignableTo(Types.V_FILE)) {
      return false;
    } else {
      return true;
    }        
  }
}
