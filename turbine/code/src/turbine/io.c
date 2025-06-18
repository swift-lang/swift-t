
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
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

// ExM c-utils
#include <log.h>
#include <tools.h>

#include "src/util/mpi-tools.h"
#include "src/turbine/io.h"

#define TURBINE_IO_FILE_CHUNK_SIZE 32*1024*1024

bool
turbine_io_bcast(MPI_Comm comm, char** s, int* length)
{
  int rc;
  int mpi_rank;
  MPI_Comm_rank(comm, &mpi_rank);

  int bytes;
  if (mpi_rank == 0)
  {
    size_t size = strlen(*s)+1;
    if (size > INT_MAX)
      return false;
    bytes = (int) size;
  }

  rc = MPI_Bcast(&bytes, 1, MPI_INT, 0, comm);
  MPI_ASSERT(rc);
  if (mpi_rank != 0)
    *s = malloc((size_t) bytes);

  rc = MPI_Bcast(*s, bytes, MPI_CHAR, 0, comm);
  MPI_ASSERT(rc);

  *length = bytes-1;
  return true;
}

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
  MPI_ASSERT(rc);

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

  // Set up file size
  MPI_Offset file_size;
  bool result = bcast_size(comm, name_in, &file_size);
  if (!result) return result;

  // Allocate buffer
  size_t chunk_max = TURBINE_IO_FILE_CHUNK_SIZE;
  void* buffer = malloc(chunk_max);

  double start = log_time();

  // Open files
  MPI_File fd_in;
  // Cast only needed for MPI-2
  rc = MPI_File_open(comm, (char*) name_in, MPI_MODE_RDONLY,
                     MPI_INFO_NULL, &fd_in);
  if (rc != MPI_SUCCESS)
  {
    log_printf("Could not open: %s\n", name_in);
    return false;
  }

  FILE* fd_out = copy_destination(name_in, name_out);
  if (fd_out == NULL) return false;

  log_printf("turbine_io_copy_to: size %"PRId64"\n", file_size);

  // Do the copy
  MPI_Status status;
  int64_t total = 0;
  while (total < file_size)
  {
    uint64_t remainder = (uint64_t) (file_size-total);
    int chunk = (int) min_uint64(chunk_max, remainder);
    rc = MPI_File_read_all(fd_in, buffer, chunk, MPI_BYTE, &status);
    assert(rc == MPI_SUCCESS);
    int r;
    MPI_Get_count(&status, MPI_BYTE, &r);
    assert(r == chunk);
    size_t count = fwrite(buffer, (size_t) chunk, 1, fd_out);
    assert(count == 1);
    total += r;
  }

  // Finish up
  free(buffer);
  MPI_File_close(&fd_in);
  fclose(fd_out);

  double stop = log_time();
  double duration = stop - start;
  log_printf("turbine_io_copy_to: "
             "wrote %"PRId64" bytes in %0.2f seconds.\n",
             total, duration);

  return true;
}
