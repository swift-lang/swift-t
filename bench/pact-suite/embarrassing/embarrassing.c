
// For usleep
#define _BSD_SOURCE


#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <unistd.h>
#include <adlb.h>

// Work unit type
#define WORKT 0

#ifdef LOGNORM
static double lognorm_sample(double mu, double sigma);
#endif

int main(int argc, char *argv[])
{
  int rc,  done;

  int am_server;
  int num_servers;
  MPI_Comm app_comm;
  int my_world_rank, my_app_rank;
  int app_comm_size;

  int type_vect[2] = {WORKT};


  double start_time, end_time;

  printf("HELLO!\n");
  fflush(NULL);

  rc = MPI_Init( &argc, &argv );
  assert(rc == MPI_SUCCESS);

  MPI_Comm_rank( MPI_COMM_WORLD, &my_world_rank );

  num_servers = 1;
  if (getenv("ADLB_SERVERS") != NULL) {
    num_servers = atoi(getenv("ADLB_SERVERS"));
  }

  rc = ADLB_Init(num_servers, 1, type_vect, &am_server, MPI_COMM_WORLD, &app_comm);

  if ( !am_server ) /* application process */
  {
    printf("[%i] I AM NOT SERVER!\n", my_world_rank);
    
    MPI_Comm_rank( app_comm, &my_app_rank );
    MPI_Comm_size( app_comm, &app_comm_size );
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
#ifdef LOGNORM
    if (argc != 5) {
      printf("Got %i args\n", argc -1);
      printf("usage: %s <N> <M> <mu> <sigma> \n", argv[0]);
      ADLB_Fail(-1);
    }
#else
    if (argc != 4) {
      printf("Got %i args\n", argc -1);
      printf("usage: %s <N> <M> <sleeptime> \n", argv[0]);
      ADLB_Fail(-1);
    }
#endif

    int N = atoi(argv[1]);
    int M = atoi(argv[2]);
#ifdef LOGNORM
    double mu = atof(argv[3]);
    double sigma = atof(argv[4]);
#else
    double F = atof(argv[3]);
#endif

    int control_ratio = 8; // E.g. 1/8 workers start putting tasks
    if (getenv("CONTROL_RATIO") != NULL) {
      control_ratio = atoi(getenv("CONTROL_RATIO"));
    }
    int control_task_count = ((app_comm_size - 1) / control_ratio) + 1;
    if (my_app_rank < control_task_count ) {
      // Get a subset of procs to put in work
      // divide, rounding up to get control task count
      int my_control_rank = my_app_rank;
      int tasks_put = 0;
      // partition loop between ranks
      for (int i = my_control_rank; i < N; i+=control_task_count) {
        for (int j = 0; j < M; j++) {
          char buf[1024];
          int len = sprintf(buf, "%i %i\n", i, j);
          ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, WORKT, 1, 1);
        }
        tasks_put += M;
      }
      printf("[%i] put all tasks (%i)\n", my_app_rank,
              tasks_put);
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
      double sleep_time;
#ifdef LOGNORM
      sleep_time = lognorm_sample(mu, sigma);
#else
      sleep_time = F;
#endif

      double spin_start = MPI_Wtime();
      // Spin!
      while (MPI_Wtime() - spin_start < sleep_time);
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

#ifdef LOGNORM
// Float in (0, 1) range
static double rand_float(void)
{
  double v = (double)rand();

  // scale, non-inclusive  

  return (v + 1) / ((double)RAND_MAX + 2);
}
static double norm_sample(double mu, double sigma)
{
  while (true)
  {
    double r1 = rand_float() * 2 - 1;
    double r2 = rand_float() * 2 - 1;
    double s = r1*r1 + r2*r2;
    if (s < 1)
    {
      double unscaled = r1 * sqrt((-2 * log(s))/s);
      return mu + sigma * unscaled;
    }
  }
}

static double lognorm_sample(double mu, double sigma)
{
  return exp(norm_sample(mu, sigma));
}
#endif
