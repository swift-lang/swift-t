
#include <assert.h>
#include <stdio.h>

#include <mpi.h>

#include <turbine.h>

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;

  MPI_Init(&mpi_argc, &mpi_argv);

  // Create communicator for ADLB
  MPI_Comm comm;
  MPI_Comm_dup(MPI_COMM_WORLD, &comm);

  int rank;
  MPI_Comm_rank(comm, &rank);

  // Build up arguments
  int argc = 3;
  char const * argv[3] = { "howdy", "ok", "bye" };
  char output[128];

  // Run Turbine
  turbine_code rc = turbine_run(comm, "test-f.tic",
                                argc, &argv[0], output);
  assert(rc == TURBINE_SUCCESS);

  if (rank == 0)
    printf("controller: output: %s\n", output);

  MPI_Finalize();
  return 0;
}
