
/*
 * parallel-task.c
 *
 *  Created on: Jan 15, 2013
 *      Author: wozniak
 */

#include <stdio.h>
#include <mpi.h>
#include <adlb.h>
#include <tools.h>
#include "src/debug.h"

static void task(MPI_Comm comm);

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;
  MPI_Init(&mpi_argc, &mpi_argv);
  int types[2] = {0, 1};
  int am_server;
  MPI_Comm adlb_comm;
  ADLB_Init(1, 2, types, &am_server, &adlb_comm);

  int rank;
  MPI_Comm_rank(MPI_COMM_WORLD, &rank);

  if (am_server)
  {
    ADLB_Server(1);
  }
  else
  {
    char buffer[128];
    sprintf(buffer, "PARALLEL STRING");
    ADLB_Put(buffer, strlen(buffer)+1, ADLB_RANK_ANY, rank, 0, 0, 2);

    while (true)
    {
      int length;
      int answer;
      int type;
      MPI_Comm task_comm;
      adlb_code rc =
          ADLB_Get(0, buffer, &length, &answer, &type, &task_comm);
      if (rc == ADLB_SHUTDOWN)
        break;
      if (task_comm == MPI_COMM_SELF)
      {
        printf("SELF\n");
      }
      else
      {
        printf("PARALLEL TASK\n");
        task(task_comm);
      }
      printf("OK\n");
      printf("GOT: %s\n", buffer);
    }
  }

  ADLB_Finalize();
  MPI_Finalize();
  return 0;
}

static void
task(MPI_Comm comm)
{
  TRACE_START;
  int rank, size;
  MPI_Comm_rank(comm, &rank);
  MPI_Comm_size(comm, &size);
  printf("TASK: rank: %i\n", rank);
  printf("TASK: size: %i\n", size);
  MPI_Barrier(comm);
  MPI_Comm_free(&comm);
  TRACE_END;
}
