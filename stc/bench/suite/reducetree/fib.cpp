
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define _Bool bool

extern "C" {
#include <adlb.h>
}

#include <sys/time.h>
#include <unistd.h>

extern "C" {
#include "bench_util.h"
}

#include <utility>
using namespace std;

// Work unit type
#define WORKT 0
#define CONTROL 1

#ifdef FIB_DEBUG
#define FIB_DEBUG(fmt, args...) printf(fmt, ## args)
#else
#define FIB_DEBUG(fmt, args...)
#endif


void mystore(adlb_datum_id id, long val) {
    ADLB_Store(id, ADLB_NO_SUB, ADLB_DATA_TYPE_INTEGER,
               &val, sizeof(long), ADLB_WRITE_REFC, ADLB_NO_REFC);
}

adlb_datum_id spawnfib(int N) {
      char buf[1024];
      adlb_datum_id id;
      ADLB_Create_integer(ADLB_DATA_ID_NULL, DEFAULT_CREATE_PROPS,
                          &id);
      int len = sprintf(buf, "fib %i %li\n", N, id);
      ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, CONTROL,
               ADLB_DEFAULT_PUT_OPTS);
      return id;
}

void spawnfib2(adlb_datum_id *f1, adlb_datum_id *f2, int N1, int N2) {
      int rc;
      char buf[1024];
      ADLB_create_spec specs[2];
      for (int i = 0; i < 2; i++) {
        specs[i].id = ADLB_DATA_ID_NULL;
        specs[i].type = ADLB_DATA_TYPE_INTEGER;
        specs[i].props = DEFAULT_CREATE_PROPS;
      }
      rc = ADLB_Multicreate(specs, 2);
      assert(rc == ADLB_SUCCESS);
      int len = sprintf(buf, "fib %i %li\n", N1, specs[0].id);
      rc = ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, CONTROL,
                    ADLB_DEFAULT_PUT_OPTS);
      assert(rc == ADLB_SUCCESS);
      len = sprintf(buf, "fib %i %li\n", N2, specs[1].id);
      rc = ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, CONTROL,
                    ADLB_DEFAULT_PUT_OPTS);
      assert(rc == ADLB_SUCCESS);
      *f1 = specs[0].id;
      *f2 = specs[1].id;
}

void spawnadd(adlb_datum_id result, adlb_datum_id f1, adlb_datum_id f2)
{
  char buf[1024];
  adlb_datum_id wait_ids[] = {f1, f2};
  int len = sprintf(buf, "add %li %li %li\n", result, f1, f2);
  int rc = ADLB_Dput(buf, len+1, ADLB_RANK_ANY, -1, CONTROL,
                         ADLB_DEFAULT_PUT_OPTS,
                         "", wait_ids, 2, NULL, 0);
  assert(rc == ADLB_SUCCESS);
}

void spawnfin(adlb_datum_id result)
{
  char buf[1024];
  int len = sprintf(buf, "fin %li\n", result);
  int rc = ADLB_Dput(buf, len+1, ADLB_RANK_ANY, -1, CONTROL,
                         ADLB_DEFAULT_PUT_OPTS,
                         "", &result, 1, NULL, 0);
  assert(rc == ADLB_SUCCESS);
}


long getnum(adlb_datum_id id) {
          long result_val;
          adlb_data_type t;
          size_t l;
          adlb_code code = ADLB_Retrieve(id, ADLB_NO_SUB, ADLB_RETRIEVE_READ_REFC,
                                         &t, &result_val, &l);
          assert(code == ADLB_SUCCESS);
          //printf("Got <%ld> = %ld\n", id, result_val);
          return result_val;
}

int main(int argc, char *argv[])
{
  int rc, done;

  int am_server;
  int num_servers;
  MPI_Comm app_comm;
  int my_world_rank, my_app_rank;

  int num_types = 2;
  int type_vect[2] = {WORKT, CONTROL};

  double start_time, end_time;

  //printf("HELLO!\n");
  fflush(NULL);

  rc = MPI_Init( &argc, &argv );
  assert(rc == MPI_SUCCESS);

  MPI_Comm_rank( MPI_COMM_WORLD, &my_world_rank );

  num_servers = 1;		/* one server should be enough */
  if (getenv("ADLB_SERVERS") != NULL) {
    num_servers = atoi(getenv("ADLB_SERVERS"));
  }
  rc = ADLB_Init(num_servers, num_types, type_vect, &am_server, MPI_COMM_WORLD, &app_comm);
  if ( !am_server ) /* application process */
  {
    FIB_DEBUG("[%i] I AM SERVER!\n", my_world_rank);
    MPI_Comm_rank( app_comm, &my_app_rank );
  }

  rc = MPI_Barrier( MPI_COMM_WORLD );
  start_time = MPI_Wtime();

  if ( am_server )
  {
    ADLB_Server(3000000);
  }
  else
  {
    if (argc != 2 && argc != 3) {
      printf("usage: %s <n> <sleep>", argv[0]);
      ADLB_Fail(-1);
    }

    int N = atoi(argv[1]);
    double sleep = 0.0;
    if (argc == 3) {
        sleep=atof(argv[2]);
        printf("Sleep for %lf\n", sleep);
    }

    if ( my_app_rank == 0 ) {
      spawnfin(spawnfib(N));
    }
  
    done = 0;
    int ndone = 0;
    while (!done)
    {
      //FIB_DEBUG("Getting a command\n");
      MPI_Comm task_comm;
      char cmdbuffer[1024];
      int work_len, answer_rank, work_type;
      rc = ADLB_Get(CONTROL,
                    cmdbuffer, &work_len, &answer_rank, &work_type,
                    &task_comm);
      if ( rc == ADLB_SHUTDOWN )
      {
	FIB_DEBUG("trace: All jobs done\n");
	break;
      }

      if (strncmp(cmdbuffer, "fib ", 4) == 0) {
        int i;
        adlb_datum_id id;
        sscanf(cmdbuffer, "fib %i %li", &i, &id);
        if (i == 0) {
          mystore(id, 0); 
        } else if (i == 1) {
          mystore(id, 1);
        } else {
          adlb_datum_id f1, f2;
          spawnfib2(&f1, &f2, i - 1, i - 2);
          spawnadd(id, f1, f2);
        }
      } else if (strncmp(cmdbuffer, "add ", 4) == 0) {
        adlb_datum_id dst, src1, src2;
        sscanf(cmdbuffer, "add %li %li %li", &dst, &src1, &src2);
        long val1 = getnum(src1);
        long val2 = getnum(src2);

        spin(sleep);
        mystore(dst, val1 + val2);
      } else if (strncmp(cmdbuffer, "fin ", 4) == 0) {
        adlb_datum_id id;
        sscanf(cmdbuffer, "fin %li", &id);
        printf("Fib(%i) = %ld\n", N, getnum(id));
      } else {
        printf("Unknown buffer %s\n", cmdbuffer);
        exit(1);
      }
      ndone++;
      if (ndone % 50000 == 0) {
        FIB_DEBUG("trace: rank %i finished %i\n", my_app_rank, ndone);
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
