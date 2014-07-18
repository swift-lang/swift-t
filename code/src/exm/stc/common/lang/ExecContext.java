/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.common.lang;

import java.util.Collections;
import java.util.List;

/**
 * Where a bit of code will execute.
 *
 * We treat control and work contexts differently even if they run on the same
 * resources: nothing that will block for an indeterminate or long amount of
 * time should run in a control context.
 */
public class ExecContext {
  /**
   * Represent a general kind of context
   */
  public static enum Kind {
    WORKER, CONTROL,;
  }

  /**
   * Represent a specific kind of worker.
   *
   * Compare based on identity
   * Include string to identify in debug messages, etc.
   */
  public static class WorkerContext {
    private String name;

    public WorkerContext(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    private static WorkerContext DEFAULT = new WorkerContext("WORKER");
  }

  private ExecContext(Kind kind, WorkerContext workContext) {
    if (kind == Kind.WORKER) {
      assert(workContext != null);
    } else {
      assert( kind == Kind.CONTROL);
      assert(workContext == null);
    }
    this.kind = kind;
    this.workContext = workContext;
  }

  private final Kind kind;
  private final WorkerContext workContext;

  private static final ExecContext CONTROL_CONTEXT =
                  new ExecContext(Kind.CONTROL, null);
  private static final ExecContext DEFAULT_WORKER_CONTEXT =
                  new ExecContext(Kind.WORKER, WorkerContext.DEFAULT);

  public WorkerContext workContext() {
    return workContext;
  }

  public static ExecContext control() {
    return CONTROL_CONTEXT;
  }

  public static ExecContext worker(WorkerContext workContext) {
    return new ExecContext(Kind.WORKER, workContext);
  }

  public static ExecContext defaultWorker() {
    return DEFAULT_WORKER_CONTEXT;
  }

  public List<ExecContext> asList() {
    return Collections.singletonList(this);
  }

  public boolean isControlContext() {
    return kind == Kind.CONTROL;
  }

  public boolean isAnyWorkContext() {
    return kind == Kind.WORKER;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((kind == null) ? 0 : kind.hashCode());
    result = prime * result
        + ((workContext == null) ? 0 : workContext.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    assert (obj != null) : "Compare " + this + " with null";
    assert(obj instanceof ExecContext) :
        "Compare ExecContext with " + obj.getClass().getCanonicalName();

    ExecContext other = (ExecContext) obj;
    if (kind != other.kind) {
      return false;
    }

    if (workContext == null) {
      if (other.workContext != null) {
        return false;
      }
    } else if (!workContext.equals(other.workContext))
      return false;
    return true;
  }

  @Override
  public String toString() {
    if (kind == Kind.CONTROL) {
      return "CONTROL";
    } else {
      assert(kind == Kind.WORKER);
      return "WORKER[" + workContext + "]";
    }
  }
}