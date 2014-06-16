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

#ifndef __ASYNC_EXEC_H
#define __ASYNC_EXEC_H

#include "src/turbine/turbine-defs.h"

/*
  Represent state of a completed task
 */
typedef struct {
  bool success;
  // TODO: callbacks
} turbine_completed_task;

typedef enum {
  TURBINE_EXEC_SUCCESS,
  TURBINE_EXEC_ERROR,
  TURBINE_EXEC_SHUTDOWN,
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
typedef turbine_exec_code (*turbine_exec_init)(const void *context,
                                               void **state);

/*
  Shutdown: shut down executor
 */
typedef turbine_exec_code (*turbine_exec_shutdown)(void *state);

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

typedef struct {
  const char *name;
  int adlb_work_type; // Type to request from adlb
  async_exec_notif notif_mode;
  const void *context; // Context info that is not modified
  void *state; // Internal state to pass to executor functions

  /*
    Function pointers for executors
   */
  turbine_exec_init initialize;
  turbine_exec_shutdown shutdown;
  turbine_exec_wait wait;
  turbine_exec_poll poll;
  turbine_exec_slots slots;
} turbine_executor;

turbine_code
turbine_add_async_exec(turbine_executor executor);

turbine_code
turbine_async_worker_loop(const char *exec_name, void *buffer, int buffer_size);

turbine_code
turbine_async_exec_finalize(void);

// To be replaced with correct check
#define TMP_ADLB_CHECK(code) assert((code) == ADLB_SUCCESS)
#define TMP_WARN(fmt, args...) fprintf(stderr, fmt "\n", ##args)

#define TMP_EXEC_CHECK(code) assert((code) == TURBINE_EXEC_SUCCESS)
#define TMP_MALLOC_CHECK(p) assert(p != NULL)

#endif //__ASYNC_EXEC_H
