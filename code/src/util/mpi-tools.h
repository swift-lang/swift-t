/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

#ifndef MPI_TOOLS_H
#define MPI_TOOLS_H

#include <stdio.h>
#include <stdlib.h>

#include <mpi.h>

// #define MPI_CHECK(rc) { if (rc != MPI_SUCCESS)

#define MPI_ASSERT(rc)				                \
  { if (rc != MPI_SUCCESS) {			                \
      printf("MPI_ASSERT FAILED: %s:%i\n", __FILE__, __LINE__);	\
      exit(1);					                \
    }}


#define MPI_ASSERT_MSG(rc,msg)		                          \
  { if (rc != MPI_SUCCESS) {		                          \
      printf("MPI_ASSERT: %s:%i: %s\n", __FILE__, __LINE__, msg); \
      exit(1);				                          \
    }}

#endif
