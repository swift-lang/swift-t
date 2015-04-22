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
  External generic API for async executors.
 */

#ifndef __ASYNC_EXEC_H
#define __ASYNC_EXEC_H
#include "src/turbine/turbine-defs.h"

#include <tcl.h>

#include <adlb-defs.h>

// Limit on number of registered async executors
#define TURBINE_ASYNC_EXEC_LIMIT 64

// Limit on number of concurrent get requests
#define TURBINE_ASYNC_EXEC_MAX_REQS 256

// Default number of buffers to allocate
#define TURBINE_ASYNC_EXEC_DEFAULT_BUFFER_COUNT 16

// Default size of buffers to allocate
#define TURBINE_ASYNC_EXEC_DEFAULT_BUFFER_SIZE ADLB_XFER_SIZE

// Forward declare turbine executor
typedef struct turbine_executor turbine_executor;

/*
  We implement callbacks by evaluating Tcl code on task completion.  This
  is not especially generic, but simplifies matters for the time being.

  Reference counts on objects will be incremented on task launch
  and decremented once task finishes.
 */

typedef struct {
  Tcl_Obj *code;
} turbine_task_callback;

/*
  Metadata to maintain with task.
 */
typedef struct {
  turbine_task_callback success;
  turbine_task_callback failure;
} turbine_task_callbacks;

turbine_code turbine_async_exec_initialize(void);

turbine_code turbine_async_exec_finalize(Tcl_Interp *interp);

/*
  Enumerate names of async executors.
  names: caller array to fill in with name pointers.  Pointers remain
        owned by this module, will be invalidated when finalized or
        an executor is added or removed.
  size: size of names array.  If >= TURBINE_ASYNC_EXEC_LIMIT, will return all
        names
 */
turbine_code
turbine_async_exec_names(const char **names, int size, int *count);

/*
  lookup registered executor.

  started: set to true if executor is started and not stopped.
           May be set to NULL to ignore
  returns executor, or null if not registered.
    pointer to executor remains valid until shut down
 */
turbine_executor *
turbine_get_async_exec(const char *name, bool *started);

turbine_code
turbine_configure_exec(Tcl_Interp *interp, turbine_executor *exec,
                       const char *config, size_t config_len);

/*
 * Can be called after configuration.
 * max: upper bound on slots, or -1 if unknown/unlimited
 */
turbine_code
turbine_async_exec_max_slots(Tcl_Interp *interp,
            const turbine_executor *exec, int *max);

/*
  Start an async worker loop.
  exec: executor to use.  Will be started by this function if not started.
        If this function had to start it, it will stop it.
  payload_buffer: array of initialized payload buffers to use.
        The number of concurrent work requests to ADLB will be limited
        by the number of buffers provided.
        At most TURBINE_ASYNC_EXEC_MAX_REQS will be used.
  nbuffers: size of payload_buffer array.
 */
turbine_code
turbine_async_worker_loop(Tcl_Interp *interp, turbine_executor *exec,
                int adlb_work_type, adlb_payload_buf *payload_buffers,
                int nbuffers);

#endif //__ASYNC_EXEC_H
