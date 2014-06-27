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

turbine_code turbine_async_exec_finalize(void);

/*
  Register executor with async executors module
 */
turbine_code
turbine_add_async_exec(turbine_executor executor);

/*
  Lookup registered executor.
  Returns executor, or NULL if not registered.
  Pointer to executor remains valid until shut down
 */
const turbine_executor *
turbine_get_async_exec(const char *name);

turbine_code
turbine_async_worker_loop(Tcl_Interp *interp, const char *exec_name,
                          void *buffer, size_t buffer_size);

#endif //__ASYNC_EXEC_H
