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
 * BATCHER
 *
 * Simple demonstration of ADLB usage
 * Reads an input text file, distributes each line (via ADLB_Put())
 * for execution in the shell on a worker.
 *
 * Usage: mpiexec -n <PROCS> batcher.x <INPUT.TXT>
 * */

#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// From c-utils for chomp()
#include <exm-string.h>

#include "adlb.h"

// Work unit type
const int CMDLINE = 0;

#define GET_LENGTH 1024
char cmdbuffer[GET_LENGTH];
int quiet = 1;

static void
put_commands(int argc, char** argv)
{
  if (argc != 2)
  {
    printf("usage: %s <filename>\n", argv[0]);
    ADLB_Fail(-1);
  }

  printf("command file is '%s'\n", argv[1]);
  fflush(stdout);

  FILE* fp = fopen(argv[1], "r");
  if (fp == NULL)
  {
    printf("could not open command file\n");
    fflush(stdout);
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
      // printf("put cmd, rc = %d\n", rc);
    }
  }
  printf("\nall commands submitted\n");
  fflush(stdout);
  fclose(fp);
}

static void
worker_loop(void)
{
  int work_type = CMDLINE;
  int work_len  = GET_LENGTH;
  int answer_rank;
  MPI_Comm task_comm;
  int short_buffer_length = 2;
  void* buffer = malloc(short_buffer_length);
  while (true)
  {
    // void* payload = NULL; // &cmdbuffer[0];
    void* payload = buffer;
    work_len = short_buffer_length;

    // printf("Getting a command\n");
    int rc = ADLB_Get(work_type,
                      &payload, &work_len, GET_LENGTH,
                      &answer_rank, &work_type,
                      &task_comm);

    if (rc == ADLB_ERROR)
    {
      printf("ERROR!\n");
      exit(1);
    }

    if (rc == ADLB_SHUTDOWN)
    {
      break;
    }

    chomp(payload);
    printf("executing command line '%s'\n", (char*) payload);
    fflush(stdout);
    rc = system(payload);
    if (payload != buffer)
      free(payload);
    if (rc != 0)
      printf("WARNING: COMMAND: (%s) EXIT CODE: %i\n",
             cmdbuffer, rc);
  }
  free(buffer);
}

int
main(int argc, char *argv[])
{
  int rc;

  int am_server;

  int num_types = 1;
  int type_vect[2] = { CMDLINE };

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

  if (am_server)
  {
    // server rank
    printf("batcher server...\n");
    ADLB_Server(3000000);
    printf("batcher server done.\n");
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
      printf("worker time: %.3f\n", end_time-start_time);
    }
  }

  ADLB_Finalize();
  MPI_Finalize();
  return(0);
}
