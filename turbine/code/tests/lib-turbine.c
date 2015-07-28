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

#include <assert.h>
#include <stdio.h>

#include <mpi.h>

#include "src/turbine/turbine.h"

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;

  printf("HI\n");

  MPI_Init(&mpi_argc, &mpi_argv);

  // Create communicator for ADLB
  MPI_Comm comm;
  MPI_Comm_dup(MPI_COMM_WORLD, &comm);

  // Build up arguments
  int argc = 3;
  const char* argv[argc];
  argv[0] = "howdy";
  argv[1] = "ok";
  argv[2] = "bye";

  // Run Turbine
  for (int i = 0; i < 10; i++)
  {
    turbine_code rc =
        turbine_run(comm, "tests/strings.tcl", argc, argv, NULL);
    assert(rc == TURBINE_SUCCESS);
  }

  MPI_Comm_free(&comm);
  MPI_Finalize();
  return 0;
}
