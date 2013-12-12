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
#include <tcl.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/turbine/turbine.h"
#include "src/tcl/turbine/tcl-turbine.h"
#include "src/tclturbinesrc.h"

static void
register_packages(void);

int
main(int argc, char **argv)
{
  if (argc < 2)
  {
    fprintf(stderr, "Must provide script as first argument\n");
    return 1;
  }
  
  // Get script from first argument
  char *script = argv[1];

  int script_argc = argc - 1;
  char **script_argv = malloc(sizeof(char*) * (size_t)script_argc);
  if (script_argv == NULL)
  {
    fprintf(stderr, "Error allocating memory\n");
    return 1;
  }
  for (int i = 0; i < script_argc; i++)
  {
    if (i == 0)
    {
      script_argv[i] = argv[0];
    }
    else
    {
      // Skip script name
      script_argv[i] = argv[i + 1];
    }
  }

  // Create Tcl interpreter:
  Tcl_Interp* interp = Tcl_CreateInterp();
  Tcl_Init(interp);
  
  // Make packages available to all interpreters
  register_packages();

  turbine_code rc;
  rc = turbine_run_interp(MPI_COMM_NULL, script, script_argc, script_argv,
                          NULL, interp);
  free(script_argv);

  if (rc == TURBINE_SUCCESS)
  {
    return 0;
  }
  else
  {
    char code_name[64];
    turbine_code_tostring(code_name, rc);
    fprintf(stderr, "Error executing script %s: turbine error %s (%i)\n",
                script, code_name, rc);
    return 2;
  }
}

// Wrapper to initialize C module and tcl source files
int
Tclturbine_InitStatic(Tcl_Interp *interp)
{
  //fprintf(stderr, "Callback to init static package tclturbine\n");
  int rc = Tclturbine_Init(interp);
  //fprintf(stderr, "Inited static package tclturbine\n");
  if (rc != TCL_OK)
  {
    fprintf(stderr, "Error initializing Tcl Turbine C package\n");
    return rc;
  }

  // Initialize list of data
  file2array_data_init();
  
  for (int i = 0; i < file2array_data_len; i++)
  {
    // These are null terminated strings so we can use directly
    const char *tcl_src = file2array_data[i];
    // fprintf(stderr, "Eval %s\n", file2array_data_names[i]);
    int rc = Tcl_Eval(interp, tcl_src);
    if (rc != TCL_OK)
    {
      fprintf(stderr, "Error while loading Tcl source file (%s)\n",
                      file2array_data_names[i]);
      return rc;
    }
  }
  return rc;
}

/*
  Register but do not initialize statically linked packages
 */
static void
register_packages(void)
{
  Tcl_StaticPackage(NULL, "turbine", Tclturbine_InitStatic, Tclturbine_InitStatic);
}
