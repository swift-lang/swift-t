
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "adlb.h"

// Work unit type
#define CMDLINE 0

int main(int argc, char *argv[])
{
  FILE *fp;
  int rc, i, done;
  char c, cmdbuffer[1024];

  int am_server, am_debug_server;
  int num_servers, use_debug_server, aprintf_flag;
  MPI_Comm app_comm;
  int my_world_rank, my_app_rank;

  int num_types = 1;
  int type_vect[2] = {CMDLINE};

  int work_type,  work_len,
    answer_rank;

  int quiet = 1;

  double start_time, end_time;

  printf("HELLO!\n");
  fflush(NULL);

  rc = MPI_Init( &argc, &argv );
  assert(rc == MPI_SUCCESS);

  MPI_Comm_rank( MPI_COMM_WORLD, &my_world_rank );

  aprintf_flag = 0;		/* no output from adlb itself */
  num_servers = 1;		/* one server should be enough */
  use_debug_server = 0;		/* default: no debug server */
  rc = ADLB_Init(num_servers, 1, type_vect, &am_server, &app_comm);
  if ( !am_server ) /* application process */
  {
    MPI_Comm_rank( app_comm, &my_app_rank );
  }

  rc = MPI_Barrier( MPI_COMM_WORLD );
  start_time = MPI_Wtime();

  if ( am_server )
  {
    ADLB_Server(3000000);
  }
  else
  {                                 /* application process */
    if ( my_app_rank == 0 ) {  /* if master app, read and put cmds */

      if (argc != 2) {
	printf("usage: %s <filename>\n", argv[0]);
	ADLB_Fail(-1);
      }
      else
	printf("command file is %s\n", argv[1]);

      fp = fopen(argv[1], "r");
      if (fp == NULL) {
	printf("could not open command file\n");
	ADLB_Fail(-1);
      }

      while (fgets(cmdbuffer,1024,fp) != NULL) {
	cmdbuffer[strlen(cmdbuffer)] = '\0';
        if (!quiet)
          printf("command = %s\n", cmdbuffer);

	if (cmdbuffer[0] != '#') {
	  /* put command into adlb here */
	  rc = ADLB_Put(cmdbuffer, strlen(cmdbuffer)+1, ADLB_RANK_ANY,
	                -1, CMDLINE, 1, 1);
	  printf("put cmd, rc = %d\n", rc);
	}
      }
      printf("\nall commands submitted\n");
    }
    /* all application processes, including the application master,
       execute this loop */

    done = 0;
    while (!done)
    {
      printf("Getting a command\n");
      MPI_Comm task_comm;
      rc = ADLB_Get(CMDLINE,
                    cmdbuffer, &work_len, &answer_rank, &work_type,
                    &task_comm);

      if ( rc == ADLB_SHUTDOWN )
      {
	printf("All jobs done\n");
	break;
      }
      /* printf("executing command line :%s:\n", cmdbuffer); */
      rc = system( cmdbuffer );
      if (rc != 0)
        printf("WARNING: COMMAND: (%s) EXIT CODE: %i\n",
               cmdbuffer, rc);
    }

    if (my_app_rank == 0)
    {
      end_time = MPI_Wtime();
      printf("TOOK: %.3f\n", end_time-start_time);
    }
  }

  ADLB_Finalize();
  MPI_Finalize();

  return(0);
}
