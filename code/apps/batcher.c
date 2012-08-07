
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/adlb.h"

#define CMDLINE 1

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

  int work_prio, work_type, work_handle[ADLB_HANDLE_SIZE], work_len,
    answer_rank;
  int req_types[4];

  int quiet = 1;

  double start_time, end_time;

  printf("HELLO!\n");
  fflush(NULL);

  rc = MPI_Init( &argc, &argv );
  assert(rc == MPI_SUCCESS);
  printf("MPI_Init!\n");
    fflush(NULL);

  MPI_Comm_rank( MPI_COMM_WORLD, &my_world_rank );

  printf("COMM!\n");
      fflush(NULL);

  aprintf_flag = 0;		/* no output from adlb itself */
  num_servers = 1;		/* one server should be enough */
  use_debug_server = 0;		/* default: no debug server */
  rc = ADLB_Init(num_servers, use_debug_server, aprintf_flag, 1,
		 type_vect, &am_server, &am_debug_server, &app_comm);
  if ( !am_server && !am_debug_server ) /* application process */
    {
      MPI_Comm_rank( app_comm, &my_app_rank );
    }

  rc = MPI_Barrier( MPI_COMM_WORLD );
  start_time = MPI_Wtime();

  if ( am_server ) {
    ADLB_Server( 3000000, 0.0 );
  }
  else if ( am_debug_server ) {
    ADLB_Debug_server( 300.0 );
  }
  else {                                 /* application process */
    if ( my_app_rank == 0 ) {  /* if master app, read and put cmds */

      if (argc != 2) {
	printf("usage: %s <filename>\n", argv[0]);
	ADLB_Abort(-1);
      }
      else
	printf("command file is %s\n", argv[1]);

      fp = fopen(argv[1], "r");
      if (fp == NULL) {
	printf("could not open command file\n");
	ADLB_Abort(-1);
      }

      while (fgets(cmdbuffer,1024,fp) != NULL) {
	cmdbuffer[strlen(cmdbuffer)] = '\0';
        if (!quiet)
          printf("command = %s\n", cmdbuffer);

	if (cmdbuffer[0] != '#') {
	  /* put command into adlb here */

	  rc = ADLB_Put( cmdbuffer, strlen(cmdbuffer)+1, -1, -1,
			 CMDLINE, 1 );
	  aprintf( 1, "put cmd, rc = %d\n", rc );
	}
      }
      printf("\nall commands submitted\n");
    }
    /* all application processes, including the application master,
       execute this loop */

    done = 0;
    while ( !done ) {
      req_types[0] = -1;
      req_types[1] = req_types[2] = req_types[3] = -1;
      aprintf( 1, "Getting a command\n" );
      rc = ADLB_Reserve( req_types, &work_type, &work_prio,
			 work_handle, &work_len, &answer_rank);
      /* (work_handle is an array, so no & in above call) */
      aprintf( 1, "rc from getting command = %d\n", rc );
      if ( rc == ADLB_DONE_BY_EXHAUSTION ) {
	aprintf( 1, "All jobs done\n" );
	break;
      }
      if ( rc == ADLB_NO_MORE_WORK ) {
	aprintf( 1, "No more work on reserve\n" );
	break;
      }
      else if (rc < 0) {
	aprintf( 1, "Reserve failed, rc = %d\n", rc );
	ADLB_Abort(-1);
      }
      else if ( work_type != CMDLINE) {
	aprintf( 1, "unexpected work type %d\n", work_type );
	ADLB_Abort( 99 );
      }
      else {			/* reserved good work */
	rc = ADLB_Get_reserved( cmdbuffer, work_handle );
	if (rc == ADLB_NO_MORE_WORK) {
	  aprintf( 1, "No more work on get_reserved\n" );
	  break;
	}
	else { 			/* got good work */
	  /* print command to be executed */
	  /* printf("executing command line :%s:\n", cmdbuffer); */
	  rc = system( cmdbuffer );
          if (rc != 0)
            printf("WARNING: COMMAND: (%s) EXIT CODE: %i\n",
                   cmdbuffer, rc);
	}
      }
    }
  }
  end_time = MPI_Wtime();

  if (my_app_rank == 0)
    printf("TOOK: %.3f\n", end_time-start_time);

  ADLB_Finalize();
  MPI_Finalize();

  return(0);
}
