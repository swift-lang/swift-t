
#ifndef MPI_TOOLS_H
#define MPI_TOOLS_H

#include <stdio.h>
#include <stdlib.h>

#include <mpi.h>

// #define MPI_CHECK(rc) { if (rc != MPI_SUCCESS)

#define MPI_ASSERT(rc)				\
  { if (rc != MPI_SUCCESS) {			\
      printf("MPI_ASSERT FAILED\n");		\
      exit(1);					\
    }}


#define MPI_ASSERT_MSG(rc,msg)		 \
  { if (rc != MPI_SUCCESS) {		 \
      printf("MPI_ASSERT: %s\n", msg);   \
      exit(1);				 \
    }}

#endif
