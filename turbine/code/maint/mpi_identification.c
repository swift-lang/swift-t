
/*
  MPI IDENTIFICATION C
  Identify the MPI implementation.  For use by configure.ac .
  Used only by the preprocessor, e.g., '$CC -E'
*/

#include "mpi.h"

// Defined by MPICH mpi.h:
#ifdef MPICH_API_PUBLIC
// If this text makes it through the preprocessor, this is MPICH:
FOUND_MPICH();
#endif

// Defined by OpenMPI mpi.h:
#ifdef OMPI_MPI_H
// If this text makes it through the preprocessor, this is OpenMPI:
FOUND_OpenMPI();
// The underscored symbols are defined by OpenMPI mpi.h:
MAJOR-VERSION OMPI_MAJOR_VERSION
MINOR-VERSION OMPI_MINOR_VERSION
#endif

#ifdef SMPI_H
// If this text makes it through the preprocessor, this is SMPI:
FOUND_SMPI();
#endif
