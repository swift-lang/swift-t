
// For usleep
#define _BSD_SOURCE


#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <adlb.h>

// Work unit type
#define WORKT 0

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
  int type_vect[2] = {WORKT};

  int quiet = 1;

  double start_time, end_time;

  printf("HELLO!\n");
  fflush(NULL);

  rc = MPI_Init( &argc, &argv );
  assert(rc == MPI_SUCCESS);

  MPI_Comm_rank( MPI_COMM_WORLD, &my_world_rank );

  aprintf_flag = 0;		/* no output from adlb itself */
  num_servers = 1;		/* one server should be enough */
  if (getenv("ADLB_SERVERS") != NULL) {
    num_servers = atoi(getenv("ADLB_SERVERS"));
  }

  use_debug_server = 0;		/* default: no debug server */
  rc = ADLB_Init(num_servers, 1, type_vect, &am_server, MPI_COMM_WORLD, &app_comm);
  if ( !am_server ) /* application process */
  {
    printf("[%i] I AM NOT SERVER!\n", my_world_rank);
    MPI_Comm_rank( app_comm, &my_app_rank );
  }

  //rc = MPI_Barrier( MPI_COMM_WORLD );
  start_time = MPI_Wtime();

  if ( am_server )
  {
    printf("[%i] I AM SERVER!\n", my_world_rank);
    ADLB_Server(3000000);
  }
  else
  {                                 
    if (argc != 4) {
      printf("Got %i args\n", argc -1);
      printf("usage: %s <N> <M> <sleeptime> \n", argv[0]);
      ADLB_Fail(-1);
    }

    int N = atoi(argv[1]);
    int M = atoi(argv[2]);
    double F = atof(argv[3]);

    int control_ratio = 24; // 1/24 put tasks
    if (getenv("CONTROL_RATIO") != NULL) {
      control_ratio = atoi(getenv("CONTROL_RATIO"));
    }
    if ( (my_app_rank % control_ratio) == 0 ) {
      // Get a subset of procs to put in work

      int control_task_count;// TODO: calc by dividing, round up
      int my_control_rank = my_app_rank / control_ratio;
      // partition loop between ranks
      for (int i = my_control_rank; i < N; i+=control_task_count) {
        for (int j = 0; j < M; j++) {
          char buf[1024];
          int len = sprintf(buf, "%i %i\n", i, j);
          ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, WORKT, 1, 1);
        }
      }
    }
  
    // Now all processes should try to complete tasks
    done = 0;
    int ndone = 0;
    while (!done)
    {
      //printf("Getting a command\n");
      MPI_Comm task_comm;
      char cmdbuffer[1024];
      int work_len, answer_rank, work_type;
      rc = ADLB_Get(WORKT,
                    cmdbuffer, &work_len, &answer_rank, &work_type,
                    &task_comm);
      if ( rc == ADLB_SHUTDOWN )
      {
	printf("trace: All jobs done\n");
	break;
      }
      int i, j;
      sscanf(cmdbuffer, "%i %i", &i, &j);
      usleep((int) F * 1000000);
      //printf("%i %i\n", i, j);
      ndone++;
      if (ndone % 50000 == 0) {
        printf("trace: rank %i finished %i\n", my_app_rank, ndone);
      }
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
