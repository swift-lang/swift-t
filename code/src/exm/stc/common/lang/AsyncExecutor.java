package exm.stc.common.lang;

import java.util.Arrays;
import java.util.List;

import exm.stc.tclbackend.TclOpTemplate;

/**
 * Represent an asynchronous execution provider
 */
public class AsyncExecutor {
  private final String name;
  private final TclOpTemplate template;
  private final boolean appExecutor;

  /**
   * List of argument names used in executor templates.
   */
  public static final List<String> EXEC_ARG_NAMES = Arrays.asList(
      "cmd", "args", "stage_in", "stage_out", "props", "success", "failure");

  public AsyncExecutor(String name, TclOpTemplate template,
                       boolean appExecutor) {
    this.name = name;
    this.template = template;
    this.appExecutor = appExecutor;
  }

  public String name() {
    return name;
  }

  public TclOpTemplate template() {
    return template;
  }

  /**
   * @return true if this executes a command line app
   */
  public boolean isAppExecutor() {
    return appExecutor;
  }

  @Override
  public String toString() {
    return name;
  }
}
