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
 * create-group.c
 *
 *  Created on: Jul 14, 2015
 *      Author: wozniak
 */

#include <stdio.h>
#include <unistd.h>

#include <mpi.h>
#include <adlb.h>

#include <tools.h>
#include "src/debug.h"

int rank_world;

int
main(int argc, char* argv[])
{
  if (argc != 2)
    crash("usage: <parallelism>\n");

  int mpi_argc = 0;
  char** mpi_argv = NULL;
  MPI_Init(&mpi_argc, &mpi_argv);
  MPI_Comm comm_task;
  MPI_Group group_world;
  MPI_Group group_task;
  int rank_task;

  int parallelism;
  int n = sscanf(argv[1], "%i", &parallelism);
  if (n != 1)
    crash("parallelism must be an integer!\n");

  MPI_Comm_rank(MPI_COMM_WORLD, &rank_world);
  MPI_Comm_group(MPI_COMM_WORLD, &group_world);

  // Recv ranks for output comm
  int ranks[parallelism];
  for (int i = 0; i < parallelism; i++)
    ranks[i] = i;
  int rc = MPI_Group_incl(group_world, parallelism, ranks, &group_task);
  assert(rc == MPI_SUCCESS);

  rc = MPI_Comm_create_group(MPI_COMM_WORLD, group_task, 0, &comm_task);
  assert(rc == MPI_SUCCESS);
  MPI_Group_free(&group_task);

  if (rank_world < parallelism)
  {
    MPI_Comm_rank(comm_task, &rank_task);
    printf("rank_task: %i\n", rank_task);
    MPI_Barrier(comm_task);
  }
  MPI_Barrier(MPI_COMM_WORLD);
  if (rank_world == 0) printf("TEST OK\n");

  MPI_Finalize();
  return 0;
}
