
#pragma once

#include <mpi.h>

char* python_parallel_persist(MPI_Comm comm, char* expr, char* code);
