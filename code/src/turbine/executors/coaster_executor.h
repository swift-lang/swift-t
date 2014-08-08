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

#ifndef __COASTER_EXECUTOR_H
#define __COASTER_EXECUTOR_H

#include "src/turbine/async_exec.h"
#include "src/turbine/turbine-defs.h"

// Use C-based coaster API
#include <coaster.h>

// Registered name for coaster executor
#define COASTER_EXECUTOR_NAME "COASTER"

// Default staging mode to use
#define COASTER_DEFAULT_STAGING_MODE COASTER_STAGE_IF_PRESENT

/*
  Register a coaster executor with basic configuration settings.
 */
turbine_code
coaster_executor_register(void);

/*
  Execute a coaster job.  The job should be constructed with functions
  in the coaster C API.
 */
turbine_code
coaster_execute(Tcl_Interp *interp, const turbine_executor *exec,
                coaster_job *job, turbine_task_callbacks callbacks);

#endif //__COASTER_EXECUTOR_H
