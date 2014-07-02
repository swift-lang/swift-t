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
  Represent state of a completed task
 */
typedef struct {
  bool success;
  turbine_task_callbacks callbacks;
} turbine_completed_task;

/*
  Error codes used for communicating error status from generic executor
  implementations to this module.  Should not be used for other
  interfaces,
 */
typedef enum {
  TURBINE_EXEC_SUCCESS,
  TURBINE_EXEC_SHUTDOWN,
  TURBINE_EXEC_OOM,
  TURBINE_EXEC_INVALID, // Invalid API usage or input
  TURBINE_EXEC_OTHER, // Unexpected or generic error, prob unrecoverable
  // TODO: more info - e.g. if bad arg, or invalid state
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
  Initialize: initialize executor before running tasks.
               Passed context pointer.
               Should initialize state pointer
 */
typedef turbine_exec_code (*turbine_exec_init)(void *context,
                                               void **state);

/*
  Shutdown: shut down initialized executor
 */
typedef turbine_exec_code (*turbine_exec_shutdown)(void *state);

/*
  Free: free memory for shut down executor
 */
typedef turbine_exec_code (*turbine_exec_free)(void *context);

/* 
  Waiting: called on an executor with active tasks.
          updates completed with completed task info
  state: executor state pointer
  completed: output array allocated by caller
  ncompleted: input/output, set to array size by caller,
              set to actual number complted by callee
 */
typedef turbine_exec_code (*turbine_exec_wait)(void *state,
          turbine_completed_task *completed, int *ncompleted);

/*
  Polling: periodically called on an executor with active tasks.
           updates completed with completed task info.
           Arguments same as wait
 */
typedef turbine_exec_code (*turbine_exec_poll)(void *state,
          turbine_completed_task *completed, int *ncompleted);

/*
  Slots: fill in counts of slots
 */
typedef turbine_exec_code (*turbine_exec_slots)(void *state,
                                  turbine_exec_slot_state *slots);

// Executor notification model
// TODO: only polling based currently used
typedef enum
{
  EXEC_POLLING, /* We have to periodically poll for status */
  //EXEC_BG_THREAD, /* Executor has background thread */
} async_exec_notif;

/*
  Structure with all information about registered executor.
  Typedef'd to turbine_executor in async_exec.h.
 */
struct turbine_executor
{
  const char *name;
  int adlb_work_type; // Type to request from adlb
  async_exec_notif notif_mode;
  void *context; // Context info
  void *state; // Internal state to pass to executor functions

  /*
    Function pointers for executors
   */
  turbine_exec_init initialize;
  turbine_exec_shutdown shutdown;
  turbine_exec_free free;
  turbine_exec_wait wait;
  turbine_exec_poll poll;
  turbine_exec_slots slots;
};

#endif //__EXEC_INTERFACE_H
