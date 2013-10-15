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
#include <turbine-defs.h>

typedef enum
{
  /** No action */
  TURBINE_ACTION_NULL = 0,
  /** Act locally */
  TURBINE_ACTION_LOCAL = 1,
  /** Act on a remote engine */
  TURBINE_ACTION_CONTROL = 2,
  /** Act on a worker */
  TURBINE_ACTION_WORK = 3
} turbine_action_type;

typedef int64_t turbine_transform_id;

typedef struct {
  char *key;
  size_t length;
} turbine_subscript;

static const turbine_subscript TURBINE_NO_SUB = { .key = NULL, .length = 0 };

typedef struct {
  turbine_datum_id td;
  turbine_subscript subscript;
} td_sub_pair;

/**
   If the user parallel task is being released, this
   will be set to the communicator to use.
   If task is not parallel, this is MPI_COMM_SELF
*/
extern MPI_Comm turbine_task_comm;

turbine_code turbine_init(int amserver, int rank, int size);

turbine_code turbine_engine_init(void);

extern bool turbine_engine_initialized;
static inline bool turbine_is_engine(void) {
  return turbine_engine_initialized;
}

void turbine_version(version* output);

/**
   @param id Output: the ID of the new rule
   @return TURBINE_SUCCESS/TURBINE_ERROR_*
           On error, id is undefined
 */
turbine_code turbine_rule(const char* name,
                          int input_tds,
                          const turbine_datum_id* input_td_list,
                          int input_td_subs,
                          const td_sub_pair* input_td_sub_list,
                          turbine_action_type action_type,
                          const char* action,
                          int priority,
                          int target,
                          int parallelism,
                          turbine_transform_id* id);

turbine_code turbine_rules_push(void);

/*
  Should be called when turbine engine is notified that an id is closed
 */
turbine_code turbine_close(turbine_datum_id id);

/*
  Should be called when turbine engine is notified that an id/subscript
  is closed
 */
turbine_code turbine_sub_close(turbine_datum_id id, const char *subscript);

/*
  action_type: this is TURBINE_ACTION_NULL if no ready actions.
               otherwise it will have the type of action and
               the output arguments will be filled in
  action: the string action.  Caller is responsible for freeing
 */
turbine_code turbine_pop(turbine_action_type* action_type,
                         turbine_transform_id *id,
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
