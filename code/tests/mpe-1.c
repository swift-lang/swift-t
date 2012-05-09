
#include <stdio.h>
#include <mpe.h>

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;

  MPI_Init(&mpi_argc, &mpi_argv);
  MPE_Init_log();
  printf("MPE OK\n");
  return 0;
}
