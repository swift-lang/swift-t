
#include <adlb.h>

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;
  MPI_Init(&mpi_argc, &mpi_argv);
  int types = 1;
  int am_server;
  MPI_Comm adlb_comm;
  ADLB_Init(2, 1, &types, &am_server, &adlb_comm);

  ADLB_Finalize();
  MPI_Finalize();
  return 0;
}
