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
 * worker.c
 *
 *  Created on: Aug 16, 2013
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()
#include <stdbool.h>

#include <adlb.h>
#include <tcl.h>

#include "src/util/debug.h"
#include "src/turbine/turbine.h"
#include "src/turbine/turbine-checks.h"
#include "src/turbine/services.h"

#include <log.h>

static void task_error(Tcl_Interp* interp, int tcl_rc, char* command);

/** Limit for biggest task ADLB_Get() can give us */
static const int MAX_TASK = 1*1000*1000*1000;

/*
  Main worker loop
  TODO: priority isn't inherited from parent tasks
 */
turbine_code
turbine_worker_loop(Tcl_Interp* interp,
                    void* buffer, int buffer_size,
                    int work_type)
{
  int rc;

  turbine_code tc = turbine_service_init();
  turbine_check(tc);

  while (true)
  {
    // These are overwritten by ADLB_Get():
    void* payload = buffer;
    int task_size = buffer_size;

    MPI_Comm task_comm;
    int answer_rank, type_recved;

    if (ADLB_Status() == ADLB_STATUS_SHUTDOWN)
      break;

    adlb_code code = ADLB_Get(work_type, &payload, &task_size, MAX_TASK,
                              &answer_rank, &type_recved, &task_comm);
    if (code == ADLB_SHUTDOWN)
      break;
    if (code != ADLB_SUCCESS)
    {
      printf("Get failed with code %i\n", code);
      return TURBINE_ERROR_ADLB;
    }
    assert(type_recved == work_type);

    // Set task communicator for parallel tasks
    turbine_task_comm = task_comm;

    bool print_command = false;
    if (task_comm != MPI_COMM_SELF)
    {
      int r;
      MPI_Comm_rank(task_comm, &r);
      if (r == 0)
        print_command = true;
    }
    char* command = payload;
    if (print_command)
      log_printf_force("eval: %s", command);

    rc = Tcl_EvalEx(interp, command, task_size-1, 0);
    if (rc != TCL_OK)
    {
      task_error(interp, rc, command);
      return TURBINE_ERROR_EXTERNAL;
    }
    if (payload != buffer)
      // Free the oversized buffer created by ADLB_Get()
      free(payload);
  }

  turbine_service_finalize();

  return TURBINE_SUCCESS;
}

static void
task_error(Tcl_Interp* interp, int tcl_rc, char* command)
{
  if (tcl_rc != TCL_ERROR)
    printf("WARNING: Unexpected return code from task: %d", tcl_rc);
  // Pass error to calling script
  const char* prefix = "Turbine worker task error in: ";
  char* msg;
  int rc = asprintf(&msg, "\n%s%s", prefix, command);
  assert(rc != -1);
  // printf("%s\n", msg);
  Tcl_AddErrorInfo(interp, msg);
  free(msg);
}
