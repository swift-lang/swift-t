package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.ExecContext.WorkerContext;
import exm.stc.common.util.Pair;

/**
 * Represent an asynchronous execution provider
 */
public enum AsyncExecutor {
  COASTERS,
  ;
  
  /**
   * Map from executor to (unique) exec context
   */
  public static final Map<AsyncExecutor, ExecContext> contexts =
                                              initExecContexts();

  private static HashMap<AsyncExecutor, ExecContext> initExecContexts() {
    HashMap<AsyncExecutor, ExecContext> map = 
        new HashMap<AsyncExecutor, ExecContext>();
    
    for (AsyncExecutor exec: values()) {
      WorkerContext workContext = new WorkerContext(exec.name());
      map.put(exec, ExecContext.worker(workContext));
    }
    
    return map;
  }

  public String toString() {
    return this.name().toLowerCase();
  }
  
  /**
   * @param s
   * @return null if not valid executor
   */
  static public AsyncExecutor fromUserString(String s)
      throws IllegalArgumentException {
    return AsyncExecutor.valueOf(s.toUpperCase());
  }
  
  /**
   * Return appropriate execution target for executor.
   */
  public ExecContext execContext() {
    return contexts.get(this);
  }
  
  /**
   * 
   * @return true if this executes a command line app
   */
  public boolean isCommandLine() {
    switch (this) {
      case COASTERS:
        return true;
      default:
        throw new STCRuntimeError("Unimplemented for " + this);
    }
  }

  /**
   * @return list of named execution contexts for async executor.
   */
  public static List<Pair<String, ExecContext>> execContexts() {
    List<Pair<String, ExecContext>> result = 
        new ArrayList<Pair<String, ExecContext>>();
    for (Map.Entry<AsyncExecutor, ExecContext> e: contexts.entrySet()) {
      result.add(Pair.create(e.getKey().name(), e.getValue()));
    }
    return result;
  }
}
