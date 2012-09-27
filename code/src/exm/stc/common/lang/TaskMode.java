package exm.stc.common.lang;

/**
 * Where task should be executed.
 */
public enum TaskMode {
  SYNC, // Execute synchronously
  LOCAL, // Execute asynchronously on local node
  CONTROL, // Load balance as control task
  LEAF, // Load balance as leaf task
}