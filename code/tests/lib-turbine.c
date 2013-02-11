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

#include <stdio.h>

#include <mpi.h>
#include <tcl.h>

#include <tools.h>

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;

  MPI_Init(&mpi_argc, &mpi_argv);

  Tcl_Interp* interp = Tcl_CreateInterp();
  Tcl_Init(interp);

  // Create communicator for ADLB
  MPI_Comm* adlb_comm;
  adlb_comm = malloc(sizeof(MPI_Comm));
  MPI_Comm_dup(MPI_COMM_WORLD, adlb_comm);

  int rank;
  MPI_Comm_rank(*adlb_comm, &rank);

  // Store communicator pointer in Tcl variable for turbine::init
  Tcl_Obj* TURBINE_ADLB_COMM =
      Tcl_NewStringObj("TURBINE_ADLB_COMM", -1);
  Tcl_Obj* adlb_comm_ptr = Tcl_NewLongObj((long) adlb_comm);
  Tcl_ObjSetVar2(interp, TURBINE_ADLB_COMM, NULL, adlb_comm_ptr, 0);

  // Set argc/argv to empty
  Tcl_Obj* argc  = Tcl_NewStringObj("argc", -1);
  Tcl_Obj* zero  = Tcl_NewIntObj(0);
  Tcl_ObjSetVar2(interp, argc, NULL, zero, 0);
  Tcl_Obj* argv  = Tcl_NewStringObj("argv", -1);
  Tcl_Obj* empty = Tcl_NewStringObj("", -1);
  Tcl_ObjSetVar2(interp, argv, NULL, empty, 0);

  // Slurp the user script
  char* script = slurp("tests/strings.tcl");
  //  if (rank == 0)
  //    printf("script: %s\n", script);

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
    printf("Tcl error: %s\n", msg_string);
  }

  // Clean up
  Tcl_DeleteInterp(interp);
  free(script);
  free(adlb_comm);
  MPI_Finalize();

  return 0;
}
