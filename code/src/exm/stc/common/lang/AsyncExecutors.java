package exm.stc.common.lang;

import java.util.HashMap;
import java.util.Map;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.ExecContext.WorkContext;
import exm.stc.tclbackend.TclOpTemplate;

/**
 * Represent an asynchronous execution provider
 */
public class AsyncExecutors {

  /**
   * Track executors by name
   */
  private final Map<String, AsyncExecutor> executors =
                              new HashMap<String, AsyncExecutor>();

  /**
   * @param s
   * @return null if not valid executor
   */
  public AsyncExecutor fromUserString(String name) {
    return executors.get(name);
  }

  public AsyncExecutor fromWorkContext(WorkContext workContext) {
    return contexts.inverse().get(ExecContext.worker(workContext));
  }

  public void addAppExecutor(String execName, WorkContext workContext,
                             TclOpTemplate template) {
    // TODO Auto-generated method stub
    throw new STCRuntimeError("TODO: Unimplemented");
  }

  public static class AsyncExecutor {

    private final String name;
    private final WorkContext workContext;
    private final TclOpTemplate template;
    private final boolean commandLineExecutor;

    public AsyncExecutor(String name, WorkContext workContext,
                         TclOpTemplate template, boolean commandLineExecutor) {
      this.name = name;
      this.workContext = workContext;
      this.template = template;
      this.commandLineExecutor = commandLineExecutor;
    }

    public String name() {
      return name;
    }

    public TclOpTemplate template() {
      return template;
    }

    public WorkContext workContext() {
      return workContext;
    }

    public ExecContext execContext() {
      return ExecContext.worker(workContext);
    }

    /**
     * @return true if this executes a command line app
     */
    public boolean isCommandLine() {
      return commandLineExecutor;
    }

    @Override
    public String toString() {
      return this.name();
    }

  }
}
