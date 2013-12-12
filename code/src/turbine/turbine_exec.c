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

#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/turbine/turbine.h"

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

  turbine_code rc;
  rc = turbine_run(MPI_COMM_WORLD, script, script_argc, script_argv, NULL);
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
