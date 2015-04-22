package exm.stc.common.lang;

/**
 * Different kinds of wait statements that can be optimized in
 * different ways
 */
public enum WaitMode {
  WAIT_ONLY, /* Used to defer execution of block until data closed */
  TASK_DISPATCH; /* Used to dispatch async task to 
  load balancer/other node */
}