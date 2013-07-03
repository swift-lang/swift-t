
#include <stdio.h>

#include <mpi.h>

#include "g.h"

int
g(MPI_Comm comm, int k)
{
  printf("G!\n");

  printf("comm: %i\n", comm);

  int task_rank;
  MPI_Comm_rank(comm, &task_rank);
  MPI_Barrier(comm);
  sleep(task_rank);
  printf("task_rank: %i\n", task_rank);
  MPI_Barrier(comm);
}
