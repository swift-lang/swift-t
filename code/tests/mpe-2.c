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
#include <mpe.h>

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;

  MPI_Init(&mpi_argc, &mpi_argv);
  MPE_Init_log();

  int n1, n2;
  MPE_Log_get_state_eventIDs(&n1, &n2);

  printf("pair: %i %i\n", n1, n2);

  char* state = "TEST_MPE-2_STATE";
  char* color = "MPE_CHOOSE_COLOR";
  MPE_Describe_state(n1, n2, state, color);

  MPE_Log_event(n1, 0, NULL);
  MPE_Log_event(n2, 0, "Howdy");

  MPE_Finish_log("test_mpe-2");
  MPI_Finalize();
  printf("MPE OK\n");
  return 0;
}
