package exm.stc.common.lang;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Where task should be executed.
 */
public enum TaskMode {
  SYNC, // Execute synchronously
  LOCAL, // Execute asynchronously on any node locally w/o load balancing
  LOCAL_CONTROL, // Execute asynchronously on any control node w/o load bal.
  CONTROL, // Load balance as control task
  LEAF,// Load balance as leaf task
  ;
  /**
   * Check if a task with this mode can be spawned in the given
   * execution context.
   * @param context
   * @return
   */
  public boolean canSpawn(ExecContext context) {
    switch (this) {
    case SYNC:
    case LOCAL:
    case CONTROL:
    case LEAF:
      return true;
    case LOCAL_CONTROL:
      return context == ExecContext.CONTROL;
    default:
      throw new STCRuntimeError("Unknown taskmode " + this);
    }
  }
  /**
   * Throw runtime error if spawn not valid
   * @param execContextStack
   */
  public void checkSpawn(ExecContext context) throws STCRuntimeError {
    if (!canSpawn(context)) {
      throw new STCRuntimeError("Cannot spawn task with mode " + this +
          " from context " + context);
    }
  } 
}