
/*
 * io.c
 *
 *  Created on: Sep 10, 2014
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()
#include <assert.h>
#include <errno.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

// ExM c-utils
#include <log.h>
#include <tools.h>

#include "src/turbine/io.h"

#define EXM_MPIIO_FILE_CHUNK_SIZE 40*1024*1024

static bool
bcast_size(MPI_Comm comm, const char* filename, MPI_Offset* file_size)
{
  int rc;
  int mpi_rank;
  MPI_Comm_rank(comm, &mpi_rank);

  if (mpi_rank == 0)
  {
    struct stat s;
    rc = stat(filename, &s);
    if (rc != 0)
    {
      log_printf("Could not stat: %s\n", filename);
      return false;
    }
    *file_size = s.st_size;
  }

  rc = MPI_Bcast(file_size, sizeof(MPI_Offset), MPI_BYTE, 0, comm);
  assert(rc == MPI_SUCCESS);

  return true;
}

static FILE*
copy_destination(const char* name_in, const char* name_out)
{
  const char* target = NULL;
  char* new_name = NULL;
  struct stat s;
  int rc;
  rc = stat(name_out, &s);
  if (rc == 0)
  {
    // If this is a directory: copy file name
    if (S_ISDIR(s.st_mode))
    {
      char* tmp1 = strdup(name_in);
      char* name = basename(tmp1);
      rc = asprintf(&new_name, "%s/%s", name_out, name);
      assert(rc >= 0);
      free(tmp1);
      target = new_name;
    }
    else
    {
      // File exists: overwrite it
      target = name_out;
    }
  }
  else
  {
    if (errno != ENOENT)
    {
      log_printf("could not stat: %s\n", name_out);
      return NULL;
    }
    // Else: File does not exist: create it
    target = name_out;
  }

  FILE* fd_out = fopen(target, "w");
  if (fd_out == NULL)
  {
    log_printf("could not write to: %s", target);
    return NULL;
  }

  if (new_name != NULL)
    free(new_name);
  return fd_out;
}

bool
turbine_io_copy_to(MPI_Comm comm, const char* name_in,
                  const char* name_out)
{
  int rc;
  MPI_Offset file_size;

  bool result = bcast_size(comm, name_in, &file_size);
  if (!result) return result;

  // Allocate buffer
  static int chunk_max = EXM_MPIIO_FILE_CHUNK_SIZE;
  void* buffer = malloc(chunk_max);

  // Open files
  MPI_File fd_in;
  rc = MPI_File_open(comm, name_in, MPI_MODE_RDONLY, MPI_INFO_NULL,
                     &fd_in);
  if (rc != MPI_SUCCESS)
  {
    log_printf("Could not open: %s\n", name_in);
    return false;
  }

  FILE* fd_out = copy_destination(name_in, name_out);
  if (fd_out == NULL) return false;

  // Do the copy
  MPI_Status status;
  int64_t total = 0;
  while (total < file_size)
  {
    int chunk = min_integer(chunk_max, file_size-total);
    memset(buffer, '\0', chunk_max);
    rc = MPI_File_read_all(fd_in, buffer, chunk, MPI_BYTE, &status);
    assert(rc == MPI_SUCCESS);
    int r;
    MPI_Get_count(&status, MPI_BYTE, &r);
    assert(r == chunk);
    rc = fwrite(buffer, chunk, 1, fd_out);
    assert(rc != 0);
    total += r;
  }

  // Finish up
  free(buffer);
  MPI_File_close(&fd_in);
  fclose(fd_out);
  return true;
}
