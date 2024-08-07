/*
 * Copyright 2014 University of Chicago and Argonne National Laboratory
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

/*
  Interfaces for interfacing async executor with Turbine

  Created: Tim Armstrong June 18 2014
 */
#ifndef __EXEC_INTERFACE_H
#define __EXEC_INTERFACE_H

#include "src/turbine/turbine-defs.h"

#include "src/turbine/async_exec.h"

/*
  Turbine context we're running in.
 */
typedef struct {
  Tcl_Interp *interp;
} turbine_context;

typedef struct turbine_task_callback_var turbine_task_callback_var;

/*
  Represent state of a completed task
 */
typedef struct {
  bool success;
  turbine_task_callbacks callbacks;

  /*
    Variables to set while callback is running, can be NULL/0.
   */
  turbine_task_callback_var *vars;
  int vars_len;
} turbine_completed_task;

struct turbine_task_callback_var {
  /*
    Variable name, can be freed after use
   */
  char *name;
  bool free_name;

  /*
    Variable value, reference count will be decremented after use.
   */
  Tcl_Obj *val;
};

/*
  Error codes used for communicating error status from generic executor
  implementations to this module.  Should not be used for other
  interfaces,
 */
typedef enum {
  TURBINE_EXEC_SUCCESS,
  TURBINE_EXEC_SHUTDOWN,
  TURBINE_EXEC_OOM,
  TURBINE_EXEC_TASK, // Error in task
  TURBINE_EXEC_INVALID, // Invalid API usage or input
  TURBINE_EXEC_OTHER, // Unexpected or generic error, prob unrecoverable
} turbine_exec_code;

/*
  Info about available/used slots in executor
 */
typedef struct {
  int used;
  int total;
} turbine_exec_slot_state;

/*
  Function pointer types.  All are passed void state pointer for
  any state needed.  A context pointer allows context to be
  passed in for initialization
 */

/*
  Configure: before starting executor, do any configuration.
  Input is a single string, which can be interpreted in an
  executor-specific way
 */
typedef turbine_exec_code (*turbine_exec_configure)(turbine_context tcx,
            void **context, const char *config, size_t config_len);

/*
  Start: start executor before running tasks.
               Passed context pointer.
               Should initialize state pointer
 */
typedef turbine_exec_code (*turbine_exec_start)(turbine_context tcx,
            void *context, void **state);

/*
  Stop: stop started executor
 */
typedef turbine_exec_code (*turbine_exec_stop)(turbine_context tcx,
            void *state);

/*
  Free: free memory for stopped executor.  Executor may or may not
        have been started or configured
 */
typedef turbine_exec_code (*turbine_exec_free)(turbine_context tcx,
            void *context);

/*
  Waiting: called on an executor with active tasks.
          updates completed with completed task info
  state: executor state pointer
  completed: output array allocated by caller
  ncompleted: input/output, set to array size by caller,
              set to actual number complted by callee
 */
typedef turbine_exec_code (*turbine_exec_wait)(turbine_context tcx,
          void *state, turbine_completed_task *completed, int *ncompleted);

/*
  Polling: periodically called on an executor with active tasks.
           updates completed with completed task info.
           Arguments same as wait
 */
typedef turbine_exec_code (*turbine_exec_poll)(turbine_context tcx,
          void *state, turbine_completed_task *completed, int *ncompleted);

/*
  Slots: fill in counts of slots.
  May be called any time after start.
  If maximum slots is unlimited or unknown, should be set to -1
 */
typedef turbine_exec_code (*turbine_exec_slots)(turbine_context tcx,
          void *state, turbine_exec_slot_state *slots);
/*
  Max_slots: determine maximum number of slots according to config
  May be called any time after configuration.
  If maximum slots is unlimited or unknown, should be set to -1
 */
typedef turbine_exec_code (*turbine_exec_max_slots)(turbine_context tcx,
          void *context, int *max);

/*
  Structure with all information about registered executor.
  Typedef'd to turbine_executor in async_exec.h.
 */
struct turbine_executor
{
  const char *name;

  /*
    Function pointers for executors
   */
  turbine_exec_configure configure;
  turbine_exec_start start;
  turbine_exec_stop stop;
  turbine_exec_free do_free;
  turbine_exec_wait wait;
  turbine_exec_poll poll;
  turbine_exec_slots slots;
  turbine_exec_max_slots max_slots;

  void *context; // Context info
  void *state; // Internal state to pass to executor functions
  bool started; // True if started
};

/*
  Register executor with async executors module.
  This can be called anytime, including before the module is
  initialized.  Ownership of all memory stays with the caller:
  this will copy any data such as the executor name as needed.

  All function pointers must be non-null.
 */
turbine_code
turbine_add_async_exec(turbine_executor executor);


#endif //__EXEC_INTERFACE_H
