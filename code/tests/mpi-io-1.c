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

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sys/stat.h>
#include <sys/types.h>

#include "src/util/mpi-tools.h"

int
main(int argc, char** argv)
{
  int rc;

  char* filename = "tests/mpi-io.data";
  MPI_File file;

  MPI_Init(&argc, &argv);

  int mpi_size;
  MPI_Comm_size(MPI_COMM_WORLD, &mpi_size);
  int mpi_rank;
  MPI_Comm_rank(MPI_COMM_WORLD, &mpi_rank);

  printf("mpi: %i/%i\n", mpi_rank, mpi_size);

  MPI_Offset size = 100;

  if (mpi_rank == 0)
  {
    struct stat s;
    rc = stat(filename, &s);
    size = s.st_size;
  }
  MPI_Bcast(&size, sizeof(MPI_Offset), MPI_BYTE, 0, MPI_COMM_WORLD);
  int size_int = (int) size;
  printf("file size: %i\n", size_int);

  int chunk_size = 4;
  MPI_Datatype chunk;
  rc = MPI_Type_contiguous(chunk_size, MPI_BYTE, &chunk);
  MPI_ASSERT(rc);
  MPI_Type_commit(&chunk);

  int chunks = (int)size/chunk_size + 1;
  MPI_Datatype strides;
  //  rc = MPI_Type_vector(chunks, chunk_size, mpi_size*chunk_size,
  //		       MPI_BYTE, &strides);
  rc = MPI_Type_vector(chunks, 1, 2, chunk, &strides);
  MPI_ASSERT(rc);
  MPI_Type_commit(&strides);

  rc = MPI_File_open(MPI_COMM_WORLD, filename,
		     MPI_MODE_RDONLY,
		     MPI_INFO_NULL, &file);
  MPI_ASSERT_MSG(rc, "could not open file");

  int disp = mpi_rank*chunk_size;
  rc = MPI_File_set_view(file, disp, MPI_BYTE, strides,
			 "native", MPI_INFO_NULL);
  MPI_ASSERT(rc);

  char buffer[10000]; // [chunk_size];
  memset(buffer, '\0', 10000);

  printf("read\n");

  MPI_Status status;

  rc = MPI_File_read_all(file, buffer, chunk_size*2, MPI_BYTE, &status);
  MPI_ASSERT(rc);
  int r;
  MPI_Get_count(&status, MPI_BYTE, &r);
  printf("r: %i\n", r);

  // printf("c: '%c'\n", buffer[0]);
  printf("c: '%s'\n", buffer);
  // MPI_File_seek(file, chunk_size, MPI_SEEK_CUR);

  // rc = MPI_File_set_view(

  MPI_Type_free(&strides);
  MPI_Type_free(&chunk);

  MPI_Finalize();

  return 0;
}
