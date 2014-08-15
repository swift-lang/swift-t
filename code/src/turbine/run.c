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

/*
 * run.c
 *
 *  Created on: Feb 11, 2013
 *      Author: wozniak
 *
 *  Source for turbine_run()
 *  Not a core Turbine feature, thus, not in turbine.c
 */

#include <tcl.h>

#include <mpi.h>

// From c-utils
#include <tools.h>

#include "src/tcl/util.h"

// From turbine
#include "src/turbine/turbine.h"

/*
  Run a Tcl script in a turbine instance
  comm: communicator to use, MPI_COMM_NULL if there is no enclosing MPI
        context, in which case we will initialize MPI
 */
turbine_code
turbine_run(MPI_Comm comm, char* script_file,
            int argc, char** argv, char* output)
{
  return turbine_run_interp(comm, script_file, argc, argv, output,
                            NULL);
}

turbine_code
turbine_run_interp(MPI_Comm comm, char* script_file,
                   int argc, char** argv, char* output,
                   Tcl_Interp* interp)
{
  // Read the user script
  char* script = slurp(script_file);
 
  if (script == NULL)
  {
    printf("turbine_run(): Could not load script: %s\n", script_file);
    return TURBINE_ERROR_INVALID;
  }

  int rc =  turbine_run_string(comm, script, argc, argv, output, interp);
  free(script);
  return rc;
}

turbine_code turbine_run_string(MPI_Comm comm, const char* script,
                                int argc, char** argv, char* output,
                                Tcl_Interp* interp)
{
  bool created_interp = false;
  if (interp == NULL)
  {
    // Create Tcl interpreter:
    interp = Tcl_CreateInterp();
    Tcl_Init(interp);
    created_interp = true;
  }
  
  if (comm != MPI_COMM_NULL)
  {
    // Store communicator pointer in Tcl variable for turbine::init
    MPI_Comm* comm_ptr = &comm;
    Tcl_Obj* TURBINE_ADLB_COMM =
        Tcl_NewStringObj("TURBINE_ADLB_COMM", -1);
    Tcl_Obj* adlb_comm_ptr = Tcl_NewLongObj((long) comm_ptr);
    Tcl_ObjSetVar2(interp, TURBINE_ADLB_COMM, NULL, adlb_comm_ptr, 0);
  }
  
  // Render argc/argv for Tcl
  turbine_tcl_set_integer(interp, "argc", argc);
  Tcl_Obj* argv_obj     = Tcl_NewStringObj("argv", -1);
  Tcl_Obj* argv_val_obj;
  if (argc > 0)
    argv_val_obj = turbine_tcl_list_new(argc, argv);
  else
    argv_val_obj = Tcl_NewStringObj("", 0);
  Tcl_ObjSetVar2(interp, argv_obj, NULL, argv_val_obj, 0);

  if (output != NULL)
    turbine_tcl_set_wideint(interp, "turbine_run_output",
                            (ptrdiff_t) output);

  // Run the user script
  int rc = Tcl_Eval(interp, script);

  // Check for errors
  if (rc != TCL_OK)
  {
    Tcl_Obj* error_dict = Tcl_GetReturnOptions(interp, rc);
    Tcl_Obj* error_info = Tcl_NewStringObj("-errorinfo", -1);
    Tcl_Obj* error_msg;
    Tcl_DictObjGet(interp, error_dict, error_info, &error_msg);
    char* msg_string = Tcl_GetString(error_msg);
    printf("turbine_run(): Tcl error: %s\n", msg_string);
    return TURBINE_ERROR_UNKNOWN;
  }
  
  if (created_interp)
  {
    // Clean up
    Tcl_DeleteInterp(interp);
  }

  return TURBINE_SUCCESS;
}
