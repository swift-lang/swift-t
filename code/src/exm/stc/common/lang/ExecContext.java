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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import exm.stc.common.util.Pair;

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
    WORKER,
    CONTROL,
    WILDCARD, /* Wildcard type*/
    ;
  }

  /**
   * Represent a specific kind of worker.
   *
   * Compare based on identity
   * Include string to identify in debug messages, etc.
   */
  public static class WorkContext {
    private String name;

    public WorkContext(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    public String name() {
      return name;
    }

    private static WorkContext DEFAULT = new WorkContext("WORKER");
  }

  private ExecContext(Kind kind, WorkContext workContext) {
    if (kind == Kind.WORKER) {
      assert(workContext != null);
    } else {
      assert(kind == Kind.CONTROL ||
             kind == Kind.WILDCARD);
      assert(workContext == null);
    }
    this.kind = kind;
    this.workContext = workContext;
  }

  private final Kind kind;
  private final WorkContext workContext;

  private static final ExecContext WILDCARD_CONTEXT =
      new ExecContext(Kind.WILDCARD, null);
  private static final ExecContext CONTROL_CONTEXT =
                  new ExecContext(Kind.CONTROL, null);
  private static final ExecContext DEFAULT_WORKER_CONTEXT =
                  new ExecContext(Kind.WORKER, WorkContext.DEFAULT);

  public WorkContext workContext() {
    return workContext;
  }

  public static ExecContext wildcard() {
    return WILDCARD_CONTEXT;
  }

  public static ExecContext control() {
    return CONTROL_CONTEXT;
  }

  public static ExecContext worker(WorkContext workContext) {
    return new ExecContext(Kind.WORKER, workContext);
  }

  public static ExecContext defaultWorker() {
    return DEFAULT_WORKER_CONTEXT;
  }

  public List<ExecContext> asList() {
    return Collections.singletonList(this);
  }

  public boolean isWildcardContext() {
    return kind == Kind.WILDCARD;
  }

  public boolean isControlContext() {
    return kind == Kind.CONTROL;
  }

  public boolean isDefaultWorkContext() {
    return this.equals(DEFAULT_WORKER_CONTEXT);
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
    if (kind == Kind.CONTROL ||
        kind == Kind.WILDCARD) {
      return kind.name();
    } else {
      assert(kind == Kind.WORKER);
      return "WORKER[" + workContext + "]";
    }
  }

  /**
   * @param cx
   * @return true if this and other context are same at runtime
   */
  public boolean compatibleWith(ExecContext other) {
    if ((this.isControlContext() && other.isDefaultWorkContext()) ||
        this.isDefaultWorkContext() && other.isControlContext()) {
      // Merged control and worker contexts
      return true;
    }

    // Otherwise, contexts are different at runtime
    return this.equals(other);
  }


  /**
   * Return builtin context names.
   */
  public static List<Pair<String, ExecContext>> builtinExecContexts() {
    List<Pair<String, ExecContext>> targets = new
        ArrayList<Pair<String, ExecContext>>(Arrays.asList(
          Pair.create("CONTROL", CONTROL_CONTEXT),
          Pair.create("WORKER", DEFAULT_WORKER_CONTEXT),
          /* Deprecated synonym */
          Pair.create("LEAF", DEFAULT_WORKER_CONTEXT)));

    return targets;
  }
}