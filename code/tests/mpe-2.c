
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
