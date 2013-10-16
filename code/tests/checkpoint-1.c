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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include <mpi.h>

#include <adlb.h>
#include <adlb-xpt.h>

#define MAX_INDEX_SIZE 512

void dump_bin(const void *data, int length)
{
  for (int i = 0; i < length; i++)
  {
    fprintf(stderr, "%02x", (int)((unsigned char*)data)[i]);
  }
}

int
main()
{
  int mpi_argc = 0;
  char** mpi_argv = NULL;

  MPI_Init(&mpi_argc, &mpi_argv);

  // Create communicator for ADLB
  MPI_Comm comm;
  MPI_Comm_dup(MPI_COMM_WORLD, &comm);

  adlb_code ac;
  int rc;

  int types[2] = {0, 1};
  int am_server;
  MPI_Comm worker_comm;
  ac = ADLB_Init(1, 2, types, &am_server, comm, &worker_comm);
  assert(ac == ADLB_SUCCESS);

  ac = ADLB_Xpt_init("./checkpoint-1.xpt", ADLB_NO_FLUSH, MAX_INDEX_SIZE);
  assert(ac == ADLB_SUCCESS);

  if (am_server)
  {
    ADLB_Server(1);
  }
  else
  {
    int size = 128;
    char data[size];
    for (int i = 0; i < size; i++)
    {
      data[i] = (char)rand();
    }
    int key;
    rc = MPI_Comm_rank(comm, &key); // Use rank as unique key
    assert(rc == MPI_SUCCESS);

    ac = ADLB_Xpt_write(&key, (int)sizeof(key), data, size,
                        ADLB_PERSIST, true);
    assert(ac == ADLB_SUCCESS);


    adlb_binary_data data2;
    ac = ADLB_Xpt_lookup(&key, (int)sizeof(key), &data2);
    assert(ac == ADLB_SUCCESS);
    
    if (data2.length != size)
    {
      fprintf(stderr, "Retrieved checkpoint data size doesn't match: %i v %i\n",
                data2.length, size);
      exit(1);
    }
    
    if (memcmp(data2.data, data, size) != 0)
    {
      fprintf(stderr, "Retrieved checkpoint data doesn't match\n");
      fprintf(stderr, "Original: ");
      dump_bin(data, size);
      fprintf(stderr, "\n");
      fprintf(stderr, "Retrieved: ");
      dump_bin(data2.data, size);
      fprintf(stderr, "\n");
      exit(1);
    }
    
    ADLB_Free_binary_data(&data2);
  }
  

  ADLB_Finalize();
  MPI_Finalize();
  return 0;
}

