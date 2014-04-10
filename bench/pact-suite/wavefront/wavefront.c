
// For usleep
#define _BSD_SOURCE


#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Quick workaround
#define uint unsigned int

#include <adlb.h>
#include <table_lp.h>

// Work unit type
#define WAVEFRONT_WORK 0
#define CONTROL_WORK 1

//#define DEBUG(fmt, args...) fprintf(stderr, "DEBUG" fmt, ##args)
#define DEBUG(fmt, args...) 

char buffer[ADLB_PAYLOAD_MAX];

#define IX(row, col, N) ((row) * (N) + (col))

void launch_task(adlb_datum_id *ids, int N, int row, int col) {
  DEBUG("Launching task for [%i][%i]\n", row, col);
  // spawn task to do computation
  int tasklen = sprintf(buffer, 
    "wave %"PRId64" %"PRId64" %"PRId64 " => %" PRId64,
          ids[IX(row-1, col-1, N)], 
          ids[IX(row-1, col, N)], 
          ids[IX(row, col-1, N)], 
          ids[IX(row, col, N)]);
  int rc = ADLB_Put(buffer, tasklen+1, ADLB_RANK_ANY, 0,
                    WAVEFRONT_WORK, 0, 1);
  assert(rc == ADLB_SUCCESS);
}

void decr_waiting(adlb_datum_id *ids, int *waiting, int row, int col, int N) {
  int *rem = &waiting[IX(row, col, N)];
  (*rem)--;
  assert(*rem >= 0);
  if (*rem == 0)
  {
    launch_task(ids, N, row, col);
  }
}

int main(int argc, char *argv[])
{
  int rc;
  int am_server;
  int num_servers;
  MPI_Comm app_comm;
  int my_world_rank, my_app_rank;

  int num_types = 2;
  int type_vect[2] = {WAVEFRONT_WORK, CONTROL_WORK};

  int work_type,  work_len,
    answer_rank;

  double start_time, end_time;

  printf("HELLO!\n");
  fflush(NULL);

  rc = MPI_Init( &argc, &argv );
  assert(rc == MPI_SUCCESS);

  MPI_Comm_rank( MPI_COMM_WORLD, &my_world_rank );

  num_servers = 1;		/* one server should be enough */
  rc = ADLB_Init(num_servers, num_types, type_vect, &am_server, MPI_COMM_WORLD, &app_comm);
  if ( !am_server ) /* application process */
  {
    MPI_Comm_rank( app_comm, &my_app_rank );
  }

  rc = ADLB_Read_refcount_enable();
  assert(rc == ADLB_SUCCESS);

  rc = MPI_Barrier( MPI_COMM_WORLD );
  start_time = MPI_Wtime();

  if ( am_server )
  {
    ADLB_Server(3000000);
  }
  else
  {                                 
    if (argc != 2 && argc != 3) {
      printf("usage: %s <n> <sleep>\n", argv[0]);
      ADLB_Fail(-1);
    }
    // N == number of rows and columns
    int N = atoi(argv[1]);
    double sleep = 0.0;
    if (argc == 3) {
        sleep=atof(argv[2]);
    }
    if ( my_app_rank == 0 ) {  /* if master app, put cmds */

      // Map id to board position (binary integer pair)
      struct table_lp id_map;
      table_lp_init(&id_map, N*N);

      adlb_datum_id *ids = malloc(sizeof(adlb_datum_id) * N * N);
      assert(ids != NULL);

      ADLB_create_spec *to_create = malloc(sizeof(ADLB_create_spec) * N * N);
      assert(to_create != NULL);
      for (int row = 0; row < N; row++) {
        for (int col = 0; col < N; col++) {
          int i = IX(row, col, N);
          to_create[i].id = ADLB_DATA_ID_NULL;
          to_create[i].type = ADLB_DATA_TYPE_FLOAT;
          to_create[i].props = DEFAULT_CREATE_PROPS;
          if (row == 0 && col == 0) {
            to_create[i].props.read_refcount = 1;
          } else if (row == N-1 || col == N-1) {
            to_create[i].props.read_refcount = 1;
          } else if (row == 0 || col == 0) {
            to_create[i].props.read_refcount = 2;
          } else{
            assert(row < N-1 && col < N-1);
            to_create[i].props.read_refcount = 3;
          }
        }
      }
      rc = ADLB_Multicreate(to_create, N * N); 
      assert(rc == ADLB_SUCCESS);

      for (int row = 0; row < N; row++) {
        for (int col = 0; col < N; col++) {
          int i = IX(row, col, N);
          ids[i] = to_create[i].id;

          DEBUG("[%d][%d] = <%"PRId64">\n", row, col, ids[i]);
          
          // Track which ID goes where
          int *val = malloc(sizeof(int)*2);
          val[0] = row;
          val[1] = col;
          table_lp_add(&id_map, ids[i], val);
        }
      }
      free(to_create);

      // Initialize edges
      for (int i = 0; i < N; i++) {
        double val = i;
        rc = ADLB_Store(ids[IX(i, 0, N)], ADLB_NO_SUB, ADLB_DATA_TYPE_FLOAT,
                          &val, sizeof(val), ADLB_WRITE_RC, ADLB_NO_RC);
        assert(rc == ADLB_SUCCESS);
        
        // Don't double-assign [0][0]
        if (i != 0) {
          rc = ADLB_Store(ids[IX(0, i, N)], ADLB_NO_SUB, ADLB_DATA_TYPE_FLOAT,
                            &val, sizeof(val), ADLB_WRITE_RC, ADLB_NO_RC);
          assert(rc == ADLB_SUCCESS);
        }
      }

      // Track number of neighbours we're waiting for
      int *waiting = malloc(sizeof(int) * N * N);
      assert(waiting != NULL);
      for (int row = 0; row < N; row++) {
        for (int col = 0; col < N; col++) {
          int count;
          if (row == 0 || col == 0) {
            count = 0;
          } else if (row == 1 && col == 1) {
            count = 0;
          } else if (row == 1 || col == 1) {
            count = 1;
          } else {
            // Three neighbours
            count = 3;
          }
          waiting[IX(row, col, N)] = count;

          if (row != 0 && col != 0) {
            // Subscribe to get notification
            int subscribed;
            ADLB_Subscribe(ids[IX(row, col, N)], ADLB_NO_SUB, &subscribed);
            assert(subscribed);

          }
        }
      }

      // launch initial task
      launch_task(ids, N, 1, 1);

      // loop to receive notifications
      while (true) {
        int tasklen;
        int answer;
        int type_recvd;
        MPI_Comm tmp;
        rc = ADLB_Get(CONTROL_WORK, buffer, &tasklen, &answer, &type_recvd, &tmp);
        if (rc == ADLB_SHUTDOWN) {
          break;
        }
        assert(rc == ADLB_SUCCESS);
        
        DEBUG("Received not: %s\n", buffer);
        if (strncmp(buffer, "close ", 5) == 0) {
          adlb_datum_id id;
          int c = sscanf(buffer, "close %li", &id);
          assert(c == 1);
          int *rowcol;
          
          bool found = table_lp_search(&id_map, id, (void**)&rowcol);
          assert(found);
          int row = rowcol[0];
          int col = rowcol[1];
          DEBUG("[%d][%d] notification\n", row, col);
          
          // up to three neighbours
          if (row < N - 1) {
            decr_waiting(ids, waiting, row + 1, col, N);
          }
          if (col < N - 1) {
            decr_waiting(ids, waiting, row, col + 1, N);
          }
          if (row < N - 1 && col < N - 1) {

            decr_waiting(ids, waiting, row + 1, col + 1, N);
          }
          if (row == N - 1 && col == N - 1) {
            double brval;
            adlb_data_type t;
            int l;
            rc = ADLB_Retrieve(ids[IX(N-1, N-1, N)], ADLB_NO_SUB,
                                ADLB_RETRIEVE_READ_RC,
                                &t, &brval, &l);
            assert(rc == ADLB_SUCCESS);
            assert(l == sizeof(double));
            fprintf(stderr, "Bottom right was set: %0.2lf\n", brval);
          }
        } else {
          fprintf(stderr, "Unexpected task %s", buffer);
          exit(1);
        }
      }
    } else {
      // Loop for worker processes
      while (true) {
        MPI_Comm task_comm;
        rc = ADLB_Get(WAVEFRONT_WORK,
                      buffer, &work_len, &answer_rank, &work_type,
                      &task_comm);

        if ( rc == ADLB_SHUTDOWN )
        {
          printf("All jobs done\n");
          break;
        }
        DEBUG("Got task %s\n", buffer);

        assert(strncmp(buffer, "wave ", 5) == 0);
        
        adlb_datum_id preds[3];
        adlb_datum_id result_id;
        int c = sscanf(buffer, "wave %"PRId64" %"PRId64" %"PRId64 " => %" PRId64 ,
                      &preds[0], &preds[1], &preds[2], &result_id);
        assert(c == 4);

        double pred_vals[3];
        for (int v = 0; v < 3; v++) {
          adlb_data_type t;
          int l;
          rc = ADLB_Retrieve(preds[v], ADLB_NO_SUB, ADLB_RETRIEVE_READ_RC,
                                         &t, &pred_vals[v], &l);
          assert(rc == ADLB_SUCCESS);
          assert(l == sizeof(double));
        }

        // do simulated work
        if (sleep > 0.0) {
            usleep((long)(sleep * 1000000));
        }

        double new_val = pred_vals[0] + pred_vals[1] + pred_vals[2];
        rc = ADLB_Store(result_id, ADLB_NO_SUB, ADLB_DATA_TYPE_FLOAT,
                      &new_val, sizeof(new_val), ADLB_WRITE_RC, ADLB_NO_RC);   
        assert(rc == ADLB_SUCCESS);
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
