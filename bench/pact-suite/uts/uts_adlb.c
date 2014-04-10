
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

#define uint unsigned int

#include <adlb.h>

#include "bench_util.h"

// Just get all the code in here - fix later
#include "uts-src/uts_leaf.c"

// Work unit type
#define WORKT 0

void spawn_uts(const Node *node) {
  int rc = ADLB_Put(node, sizeof(*node), ADLB_RANK_ANY, -1, WORKT, 1, 1);
  assert(rc == ADLB_SUCCESS);
}

static void process_node(struct node_t *init_node, uts_params params,
    int max_nodes, int max_steps)
{
  // TODO: do stuff
  // TODO: switch between bfs and dfs?
}

int main(int argc, char *argv[])
{
  int rc, done;

  int am_server;
  int num_servers;
  MPI_Comm app_comm;
  int my_world_rank, my_app_rank;

  int num_types = 1;
  int type_vect[1] = {WORKT};

  double start_time, end_time;

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
    // TODO: lotsa args
    uts_params params;
    int max_nodes, max_steps, root_id;

    if (argc != 2 && argc != 3) {
      printf("usage: %s <n> <sleep>", argv[0]);
      ADLB_Fail(-1);
    }

    int N = atoi(argv[1]);

    if ( my_app_rank == 0 ) {
      Node root;
      uts_init_root(&root, params.tree_type, root_id);
      process_node(&root, params, max_nodes, max_steps);
    }
  
    done = 0;
    while (!done)
    {
      //printf("Getting a command\n");
      MPI_Comm task_comm;
      
      Node node;
      int work_len, answer_rank, work_type;
      rc = ADLB_Get(WORKT, &node, &work_len, &answer_rank, &work_type,
                    &task_comm);
      if ( rc == ADLB_SHUTDOWN )
      {
	printf("trace: All jobs done\n");
	break;
      }
      
      assert(work_len == sizeof(Node));
      process_node(&node, params, max_nodes, max_steps);
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
