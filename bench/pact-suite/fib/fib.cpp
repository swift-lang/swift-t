
// For usleep
#define _BSD_SOURCE

#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define _Bool bool

#undef __cplusplus
extern "C" { 
#include <adlb.h>
}
#define __cplusplus
#include <unistd.h>

#include <map>
#include <utility>
using namespace std;

// Work unit type
#define WORKT 0
#define CONTROL 1

typedef struct {
  adlb_datum_id fn;
  adlb_datum_id fn1;
  adlb_datum_id fn2;
  bool got1, got2;
} fib_blocked;


void mystore(adlb_datum_id id, long val) {
    int *ranks;
    int count;
    //printf("<%ld> = %ld\n", id, val);
    ADLB_Store(id, ADLB_NO_SUB, ADLB_DATA_TYPE_INTEGER,
               &val, sizeof(long), ADLB_WRITE_RC);
    if (count > 0) {
      char buf[128];
      int len = sprintf(buf, "close %ld", id);
      for (int r = 0; r < count; r++) {
        int rank = ranks[r];
        //printf("Notify %i about %ld\n", rank, id);
        ADLB_Put(buf, len+1, rank, -1, CONTROL, 1, 1);
      }
    }
}

adlb_datum_id spawnfib(int N) {
      char buf[1024];
      adlb_datum_id id;
      ADLB_Create_integer(ADLB_DATA_ID_NULL, DEFAULT_CREATE_PROPS,
                          &id);
      int len = sprintf(buf, "fib %i %li\n", N, id);
      ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, CONTROL, 1, 1);
      return id;
}

void spawnfib2(adlb_datum_id *f1, adlb_datum_id *f2, int N1, int N2) {
      char buf[1024];
      adlb_datum_id ids[2];
      ADLB_create_spec specs[2];
      for (int i = 0; i < 2; i++) {
        specs[i].id = ADLB_DATA_ID_NULL;
        specs[i].type = ADLB_DATA_TYPE_INTEGER;
        specs[i].props = DEFAULT_CREATE_PROPS;
      }
      ADLB_Multicreate(specs, 2);
      int len = sprintf(buf, "fib %i %li\n", N1, specs[0].id);
      ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, CONTROL, 1, 1);
      len = sprintf(buf, "fib %i %li\n", N2, specs[1].id);
      ADLB_Put(buf, len+1, ADLB_RANK_ANY, -1, CONTROL, 1, 1);
      *f1 = specs[0].id;
      *f2 = specs[1].id;
}

// True if already present
bool subscribe(adlb_datum_id id) {
  int subscribed;
  adlb_code code = ADLB_Subscribe(id, ADLB_NO_SUB, &subscribed);
  assert(code == ADLB_SUCCESS);
  return subscribed == 0;
}

long getnum(adlb_datum_id id) {
          long result_val;
          adlb_data_type t;
          int l;
          adlb_code code = ADLB_Retrieve(id, ADLB_NO_SUB, ADLB_RETRIEVE_READ_RC,
                                         &t, &result_val, &l);
          assert(code == ADLB_SUCCESS);
          //printf("Got <%ld> = %ld\n", id, result_val);
          return result_val;
}

int main(int argc, char *argv[])
{
  FILE *fp;
  int rc, i, done;
  char c, cmdbuffer[1024];

  int am_server, am_debug_server;
  int num_servers, use_debug_server, aprintf_flag;
  MPI_Comm app_comm;
  int my_world_rank, my_app_rank;

  int num_types = 2;
  int type_vect[2] = {WORKT, CONTROL};

  int quiet = 1;

  double start_time, end_time;

  //printf("HELLO!\n");
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
  rc = ADLB_Init(num_servers, num_types, type_vect, &am_server, MPI_COMM_WORLD, &app_comm);
  if ( !am_server ) /* application process */
  {
    printf("[%i] I AM SERVER!\n", my_world_rank);
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
    map<long, fib_blocked*> waitmap;
    adlb_datum_id result = ADLB_DATA_ID_NULL;
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
      result = spawnfib(N);
      if (subscribe(result)) {
        printf("Finished strangely early...\n");
        exit(1);
      }
    }
  
    done = 0;
    int ndone = 0;
    while (!done)
    {
      //printf("Getting a command\n");
      MPI_Comm task_comm;
      char cmdbuffer[1024];
      int work_len, answer_rank, work_type;
      rc = ADLB_Get(CONTROL,
                    cmdbuffer, &work_len, &answer_rank, &work_type,
                    &task_comm);
      if ( rc == ADLB_SHUTDOWN )
      {
	printf("trace: All jobs done\n");
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
          long f = i;
        } else {
          adlb_datum_id f1, f2;
          spawnfib2(&f1, &f2, i - 1, i - 2);
          fib_blocked *entry = (fib_blocked*)malloc(sizeof(fib_blocked));
          entry->fn = id;
          entry->fn1 = f1;
          entry->fn2 = f2;
          entry->got1 = subscribe(f1);
          entry->got2 = subscribe(f2);
          
          if (entry->got1 && entry->got2) {
            long val1 = getnum(entry->fn1);
            long val2 = getnum(entry->fn2);
            if (sleep > 0.0) {
                usleep((long)(sleep * 1000000));
            }
            mystore(entry->fn, val1 + val2);
            //printf("Subscribed right away: %ld + %ld = %ld\n", val1, val2, val1 + val2);
            free(entry);
          } else {
            waitmap[f1] = entry;
            waitmap[f2] = entry;
          }
        }
      } else if (strncmp(cmdbuffer, "close ", 5) == 0) {
        adlb_datum_id id;
        sscanf(cmdbuffer, "close %li", &id);
        if (id == result) {
          printf("Fib(%i) = %ld\n", N, getnum(id));
        } else {
          fib_blocked *entry = waitmap[id];
          if (entry == NULL) {
            printf("Rank %i Unknown entry %ld\n", my_app_rank, id);
            exit(1);
          }
          if (entry->fn1 == id) {
            entry->got1 = true;
          }
          if (entry->fn2 == id) {
            entry->got2 = true;
          }
          if (entry->got1 && entry->got2) {
            long val1 = getnum(entry->fn1);
            long val2 = getnum(entry->fn2);
            if (sleep > 0.0) {
                usleep((long)(sleep * 1000000));
            }
            mystore(entry->fn, val1 + val2);
            //printf("Later: %ld + %ld = %ld\n", val1, val2, val1 + val2);
            waitmap.erase(id);
            free(entry);
          }
        }
      } else {
        printf("Unknown buffer %s\n", cmdbuffer);
        exit(1);
      }
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
