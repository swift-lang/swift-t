package exm.stc.common.lang;

import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.TaskProp.TaskPropKey;

public class Intrinsics {
  
  /**
   * Intrinsic functions with special handling that don't fit in above
   * framework: e.g. have different calling conventions.
   */
  public static enum IntrinsicFunction {
    FILENAME,
  }
  
  
  /**
   * Which properties are accepted for an intrinsic function
   * @param intF
   * @return
   */
  public static List<TaskPropKey> validProps(IntrinsicFunction intF) {
    switch (intF) {
      case FILENAME:
        return Collections.emptyList();
      default:
        throw new STCRuntimeError("Unimplemented for " + intF);
    }
  }
}
