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
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "adlb.h"

// Work unit type
#define CMDLINE 0

char cmdbuffer[1024];
int quiet = 1;

static void
put_commands(int argc, char** argv)
{
  if (argc != 2)
  {
    printf("usage: %s <filename>\n", argv[0]);
    ADLB_Fail(-1);
  }

  printf("command file is %s\n", argv[1]);

  FILE* fp = fopen(argv[1], "r");
  if (fp == NULL)
  {
    printf("could not open command file\n");
    ADLB_Fail(-1);
  }

  while (fgets(cmdbuffer,1024,fp) != NULL)
  {
    cmdbuffer[strlen(cmdbuffer)] = '\0';
    if (!quiet) printf("command = %s\n", cmdbuffer);

    if (cmdbuffer[0] != '#')
    {
      /* put command into adlb here */
      int rc = ADLB_Put(cmdbuffer, strlen(cmdbuffer)+1, ADLB_RANK_ANY,
                    -1, CMDLINE, ADLB_DEFAULT_PUT_OPTS);
      printf("put cmd, rc = %d\n", rc);
    }
  }
  printf("\nall commands submitted\n");
}

static void
worker_loop(void)
{
  int work_type,work_len, answer_rank;
  while (true)
  {
    printf("Getting a command\n");
    MPI_Comm task_comm;
    int rc = ADLB_Get(CMDLINE,
                      cmdbuffer, &work_len, &answer_rank, &work_type,
                      &task_comm);

    if (rc == ADLB_SHUTDOWN)
    {
      printf("All jobs done\n");
      break;
    }
    /* printf("executing command line :%s:\n", cmdbuffer); */
    rc = system(cmdbuffer);
    if (rc != 0)
      printf("WARNING: COMMAND: (%s) EXIT CODE: %i\n",
             cmdbuffer, rc);
  }
}

int
main(int argc, char *argv[])
{
  int rc;

  int am_server;

  int num_types = 1;
  int type_vect[2] = { CMDLINE };


  printf("batcher...\n");

  rc = MPI_Init( &argc, &argv );
  assert(rc == MPI_SUCCESS);

  int my_world_rank, worker_rank;
  MPI_Comm_rank( MPI_COMM_WORLD, &my_world_rank );

  int num_servers = 1;

  MPI_Comm worker_comm;
  rc = ADLB_Init(num_servers, num_types, type_vect, &am_server,
                  MPI_COMM_WORLD, &worker_comm);
  if (! am_server)
    // worker rank
    MPI_Comm_rank(worker_comm, &worker_rank);

  MPI_Barrier(MPI_COMM_WORLD);
  double start_time = MPI_Wtime();

  if (am_server )
  {
    // server rank
    ADLB_Server(3000000);
  }
  else
  {
    // worker rank
    if (worker_rank == 0)
      // master worker: read and put commands
      put_commands(argc, argv);

    // All application processes, including the master worker,
    // execute this loop:
    worker_loop();

    if (worker_rank == 0)
    {
      double end_time = MPI_Wtime();
      printf("TOOK: %.3f\n", end_time-start_time);
    }
  }

  ADLB_Finalize();
  MPI_Finalize();
  return(0);
}
