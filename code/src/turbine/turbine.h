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

/**
 *  TURBINE
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 * */

#ifndef TURBINE_H
#define TURBINE_H

#include <mpi.h>
#include <tcl.h>

#include <version.h>

#include "src/turbine/turbine-defs.h"

typedef enum
{
  /** Act locally */
  TURBINE_ACTION_LOCAL = 1,
  /** Act on a remote engine */
  TURBINE_ACTION_CONTROL = 2,
  /** Act on a worker */
  TURBINE_ACTION_WORK = 3
} turbine_action_type;

typedef long turbine_transform_id;

/**
   If the user parallel task is being released, this
   will be set to the communicator to use
*/
extern MPI_Comm turbine_task_comm;

turbine_code turbine_init(int amserver, int rank, int size);

turbine_code turbine_engine_init(void);

void turbine_version(version* output);

turbine_code turbine_rule(const char* name,
                          int inputs,
                          const turbine_datum_id* input_list,
                          turbine_action_type action_type,
                          const char* action,
                          int priority,
                          int target,
                          int parallelism,
                          turbine_transform_id* id);

turbine_code turbine_rules_push(void);

/**
   Obtain the list of TRs ready to run
   @param count: maximum number to return
   @param output: location to store TR IDs
   @param result: number of TR IDs returned
 */
turbine_code turbine_ready(int count, turbine_transform_id* output,
                           int *result);

turbine_code turbine_close(turbine_datum_id id);

/*
  action: the string action.  Caller is responsible for freeing
 */
turbine_code turbine_pop(turbine_transform_id id,
                         turbine_action_type* action_type,
                         char** action, int* priority, int* target,
                         int* parallelism);

int turbine_code_tostring(char* output, turbine_code code);

void turbine_finalize(void);

turbine_code turbine_run(MPI_Comm comm, char* script_file,
                         int argc, char** argv, char* output);

turbine_code turbine_run_interp(MPI_Comm comm, char* script_file,
                                int argc, char** argv, char* output,
                                Tcl_Interp* interp);

#endif
