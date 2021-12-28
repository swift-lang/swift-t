
/**
   PYTHON SWIG H
   Tcl calls to the Python module
   Just for python_parallel for now
 */

#pragma once

#include <mpi.h>

char* python_parallel_persist(MPI_Comm comm, char* expr, char* code);

/** Return 1 on error, else 0. */
int python_parallel_error_status(void);

/** Human-readable error message */
char* python_parallel_error_message(void);
