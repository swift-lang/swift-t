
#include <stdio.h>

#include <mpi-f.h>

void f(MPI_Comm comm, int k) {
  int task_rank;
  MPI_Comm_rank(comm, &task_rank);
  MPI_Barrier(comm);
  sleep(task_rank);
  printf("task_rank: %i\n", task_rank);
  MPI_Barrier(comm);
}
