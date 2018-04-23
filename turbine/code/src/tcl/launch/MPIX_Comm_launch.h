#ifndef MPIX_COMM_LAUNCH
#define MPIX_COMM_LAUNCH

#include <mpi.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MPI_DEFAULT_LAUNCHER "mpiexec"

int MPIX_Comm_launch(const char* cmd, char** argv, 
		MPI_Info info, int root, MPI_Comm comm,
		int* exit_code);

#ifdef __cplusplus
}
#endif

#endif
