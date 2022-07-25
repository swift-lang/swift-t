#ifndef MPIX_COMM_LAUNCH
#define MPIX_COMM_LAUNCH

#include <mpi.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MPI_DEFAULT_LAUNCHER "mpiexec "

/** This is renamed to turbine_ ... because of the Cray implementation
    and other external implementations that may appear.
 */
int turbine_MPIX_Comm_launch(const char* cmd, char** argv,
		MPI_Info info, int root, MPI_Comm comm,
		int* exit_code);

#ifdef __cplusplus
}
#endif

#endif
