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
  Alternative launcher program that can be used in place of tclsh
  to launch a Tcl script.  Avoids need to dynamically load libraries.

  Tim Armstrong - Dec 11 2013
 */


#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/turbine/turbine.h"
#include "src/tcl/turbine/tcl-turbine.h"
#include "src/tcl/static-pkg/static-pkg.h"

int
main(int argc, char **argv)
{
  if (argc < 2)
  {
    fprintf(stderr, "Must provide script as first argument\n");
    return 1;
  }
  
  // Get script from first argument
  char *script_file = argv[1];

  // Pass remaining arguments minus script to turbine_run
  for (int i = 1; i < argc - 1; i++)
  {
    argv[i] = argv[i+1];
  }
  argc--;

  // Make packages available to all interpreters
  register_tcl_turbine_static_pkg();

  turbine_code rc;
  rc = turbine_run(MPI_COMM_NULL, script_file, argc, argv, NULL);

  if (rc == TURBINE_SUCCESS)
  {
    return 0;
  }
  else
  {
    char code_name[64];
    turbine_code_tostring(code_name, rc);
    fprintf(stderr, "Error executing script file %s: turbine error "
                    "%s (%i)\n", script_file, code_name, rc);
    return 2;
  }
}
