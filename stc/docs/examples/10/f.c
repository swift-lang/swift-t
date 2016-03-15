
#define _GNU_SOURCE // for vasprintf()

#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <mpi.h>

#include "f.h"

#include "shutdown.h"

double* appdata = NULL;
int task_rank, task_size;

void out(const char* fmt, ...)
{
  char* s;
  va_list ap;
  va_start(ap, fmt);
  vasprintf(&s, fmt, ap);
  va_end(ap);
  printf("rank: %i/%i: %s\n", task_rank, task_size, s);
  free(s);
}

MPI_Comm cached_comm;

static double f_work(MPI_Comm comm, int k);

void
f_init(MPI_Comm comm, int n)
{
  out("f_init()...");
  MPI_Comm_rank(comm, &task_rank);
  MPI_Comm_size(comm, &task_size);
  out("allocating app data");
  appdata = malloc(n*1024);
  if (task_rank == 0)
  {
    cached_comm = comm;
  }
  else
  {
    f_work(comm, 0);
  }
  out("f_init() done.");
}

double
f_task(int k)
{
  double result = f_work(cached_comm, k);
  return result;
}

static double
f_work(MPI_Comm comm, int k)
{
  out("f_work()...");
  double result;
  while (true)
  {
    out("loop");
    MPI_Barrier(comm);
    MPI_Bcast(&k, 1, MPI_INT, 0, comm);
    sleep(task_rank);
    out("got: %i", k);
    MPI_Barrier(comm);
    if (k == 10)
    {
      MPI_Comm_free(&comm);
      if (task_rank == 0)
        result = sin(k+task_size);
      else
        result = 0.0;
      break;
    }
    if (task_rank == 0)
    {
      result = sin(k+task_size);
      break;
    }
  }
  out("f_work() done.");
  return result;
}
