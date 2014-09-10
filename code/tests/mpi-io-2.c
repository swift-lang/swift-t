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

/*
  MPI-IO-2
  Make local copies of file.
*/

#include <assert.h>
#include <mpi.h>
#include <mpio.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
//#include <unistd.h>

#include <tools.h>
#include "../src/util/mpi-tools.h"

#define EXM_MPIIO_FILE_CHUNK_SIZE 40*1024*1024

bool
exm_mpiio_copy_to(MPI_Comm comm, const char* name_in,
                  const char* name_out)
{
  int mpi_size;
  MPI_Comm_size(comm, &mpi_size);
  int mpi_rank;
  MPI_Comm_rank(comm, &mpi_rank);

  int rc;
  MPI_Offset file_size;
  if (mpi_rank == 0)
  {
    struct stat s;
    rc = stat(name_in, &s);
    file_size = s.st_size;
  }

  printf("mpi: %i/%i\n", mpi_rank, mpi_size);

  MPI_Bcast(&file_size, sizeof(MPI_Offset), MPI_BYTE, 0, MPI_COMM_WORLD);
  int size_int = (int) file_size;
  printf("file size: %i\n", size_int);

  int chunk_max = EXM_MPIIO_FILE_CHUNK_SIZE;

  void* buffer = malloc(chunk_max);
  memset(buffer, '\0', chunk_max);

  MPI_File fd_in;
  rc = MPI_File_open(MPI_COMM_WORLD, name_in,
                     MPI_MODE_RDONLY,
                     MPI_INFO_NULL, &fd_in);
  MPI_ASSERT_MSG(rc, "could not open fd_in");

  FILE* fd_out = fopen(name_out, "w");
  if (fd_out == NULL)
  {
    printf("could not write to: %s", name_out);
    return false;
  }

  MPI_Status status;
  int64_t total = 0;
  while (total < file_size)
  {
    int chunk = min_integer(chunk_max, file_size-total);
    memset(buffer, '\0', chunk_max);
    rc = MPI_File_read_all(fd_in, buffer, chunk, MPI_BYTE, &status);
    MPI_ASSERT(rc);
    int r;
    MPI_Get_count(&status, MPI_BYTE, &r);
    assert(r == chunk);
    printf("r: %i\n", r);
    printf("data: %s\n", (char*) buffer);

    rc = fwrite(buffer, chunk, 1, fd_out);
    assert(rc != 0);

    total += r;
  }

  free(buffer);
  MPI_File_close(&fd_in);
  fclose(fd_out);
  return true;
}


int
main(int argc, char** argv)
{
  char* filename = "tests/mpi-io.data";

  MPI_Init(&argc, &argv);
  exm_mpiio_copy_to(MPI_COMM_WORLD, filename, "./t.txt");
  MPI_Finalize();

  return 0;
}
