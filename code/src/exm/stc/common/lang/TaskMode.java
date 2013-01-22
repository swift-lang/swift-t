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

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Where task should be executed: locally, on any engine, or on a 
 * worker
 */
public enum TaskMode {
  SYNC, // Execute synchronously
  LOCAL, // Execute asynchronously on any node locally w/o load balancing
  LOCAL_CONTROL, // Execute asynchronously on any control node w/o load bal.
  CONTROL, // Load balance as control task
  LEAF,// Load balance as leaf task on a worker
  ;
  
  public static final TaskMode DEFAULT_BUILTIN_MODE = LOCAL;
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
   */
  public void checkSpawn(ExecContext context) throws STCRuntimeError {
    if (!canSpawn(context)) {
      throw new STCRuntimeError("Cannot spawn task with mode " + this +
          " from context " + context);
    }
  } 
}