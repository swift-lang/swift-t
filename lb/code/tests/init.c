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

#include <adlb.h>

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;
  MPI_Init(&mpi_argc, &mpi_argv);

  int rank_world, size_world;
  MPI_Comm_rank(MPI_COMM_WORLD, &rank_world);
  MPI_Comm_size(MPI_COMM_WORLD, &size_world);
  printf("rank:   %i/%i\n", rank_world, size_world);

  int types = 0;
  int am_server;
  MPI_Comm adlb_comm;
  ADLB_Init(2, 1, &types, &am_server, MPI_COMM_WORLD, &adlb_comm);

  ADLB_Finalize();
  MPI_Finalize();

  if (rank_world == 0)
    printf("init.x: OK\n");
  return 0;
}
