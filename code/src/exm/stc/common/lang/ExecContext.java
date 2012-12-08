package exm.stc.common.lang;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Where a bit of code will execute
 */
public enum ExecContext {
  LEAF, CONTROL,;

  public TaskMode toTaskMode() {
    switch (this) {
    case LEAF:
      return TaskMode.LEAF;
    case CONTROL:
      return TaskMode.CONTROL;
    default:
      throw new STCRuntimeError("Unknown ExecContext " + this);
    }
  }
}