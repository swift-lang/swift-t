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

#pragma once

#include <mpi.h>
#include <tcl.h>

#include <version.h>
#include <turbine-defs.h>

/**
   If the user parallel task is being released, this
   will be set to the communicator to use.
   If task is not parallel, this is MPI_COMM_SELF
*/
extern MPI_Comm turbine_task_comm;

turbine_code turbine_init(int amserver, int rank, int size);

void turbine_version(version* output);

#define TURBINE_CODE_STRING_MAX 64

/**
  Convert code to string.
  output: buffer of at least TURBINE_CODE_STRING_MAX bytes
*/
int turbine_code_tostring(char* output, turbine_code code);

void turbine_finalize(Tcl_Interp *interp);

turbine_code turbine_run(MPI_Comm comm, const char* script_file,
                         int argc, char const *const * argv, char*  output);

/*
  Run script stored in C string.
  interp: if not null, use this interpreter.  If null, create a fresh one
 */
turbine_code turbine_run_string(MPI_Comm comm, const char* script,
                                int argc, char const *const * argv, char* output,
                                Tcl_Interp* interp);

turbine_code turbine_run_interp(MPI_Comm comm, const char* script_file,
                                int argc, char const *const * argv, char* output,
                                Tcl_Interp* interp);
