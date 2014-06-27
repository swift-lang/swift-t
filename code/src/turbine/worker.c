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

static void task_error(Tcl_Interp* interp, int tcl_rc, char* command);

/*
  Main worker loop
  TODO: priority isn't inherited from parent tasks
 */
turbine_code
turbine_worker_loop(Tcl_Interp* interp, void* buffer, size_t buffer_size,
                    int work_type)
{
  int rc;

  turbine_code tc = turbine_service_init();
  turbine_check(tc);

  while (true)
  {
    MPI_Comm task_comm;
    int work_len, answer_rank, type_recved;
    adlb_code code = ADLB_Get(work_type, buffer, &work_len,
                              &answer_rank, &type_recved, &task_comm);
    if (code == ADLB_SHUTDOWN)
      break;
    turbine_task_comm = task_comm;
    if (code != ADLB_SUCCESS)
    {
      printf("Get failed with code %i\n", code);
      return TURBINE_ERROR_ADLB;
    }
    assert(work_len <= buffer_size);
    assert(type_recved == work_type);

    // Work unit is prepended with rule ID, followed by space.
    //char* rule_id_end = strchr(buffer, ' ');
    //assert(rule_id_end != NULL);
    // Set pointer to start of Tcl work unit string
    //char* command = rule_id_end + 1;

    // DEBUG_TURBINE("rule_id: %"PRId64"", atol(buffer));

    // Don't set rule_id
    char *command = buffer;
    DEBUG_TURBINE("eval: %s", command);

    // Work out length | null byte | prefix
    int cmd_len = work_len - 1 - (int)(command - (char*) buffer);
    rc = Tcl_EvalEx(interp, command, cmd_len, 0);
    if (rc != TCL_OK)
    {
      task_error(interp, rc, command);
      return TURBINE_ERROR_EXTERNAL;
    }
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
