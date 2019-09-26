#include "mpi.h"

#if MPI_VERSION < 3
#error "MPI_VERSION 3 or greater required!"
#endif

int main(int argc, char** argv) { return 0; }
