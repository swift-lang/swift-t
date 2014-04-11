
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

#define uint unsigned int

#include <adlb.h>

#include "bench_util.h"

#include "uts-src/uts_inline.h"

// Work unit type
#define WORKT 0

typedef struct {
  Node node;
  // Number of ancestor tasks, used to implement same algo as Swift version
  int task_depth; 
} uts_task;

void spawn_uts(const uts_task *task) {
  int rc = ADLB_Put(task, sizeof(*task), ADLB_RANK_ANY, -1, WORKT, 1, 1);
  assert(rc == ADLB_SUCCESS);
}

static void process_node(uts_task *init_node, uts_params params,
    int max_nodes, int max_steps)
{
  int task_depth = init_node->task_depth;
  int count = 0, head = 0, tail = 0;
  // switch between bfs and dfs in same way as Swift
  bool bfs = task_depth <= 1;
  if (bfs)
  {
    int steps = 256; // Get work out quick
    bool ok = uts_step_bfs(&init_node->node, params, max_nodes, steps,
                           &head, &tail, &count);
    assert(ok);
  }
  else
  {
    bool ok = uts_step_dfs(&init_node->node, params, max_nodes, max_steps,
                           &count);
    assert(ok);
  }

  for (int i = 0; i < count; i++)
  {
    struct node_t *result_node;
    if (bfs)
    {
      result_node = &nodes[(head + i) % NODE_ARRAY_SIZE];
    }
    else
    {
      result_node = &nodes[i];
    }
    
    uts_task task;
    task.node = *result_node;
    task.task_depth = task_depth + 1;
    spawn_uts(&task);
  }
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
    UTS_INFO("[%i] I AM SERVER!\n", my_world_rank);
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
    // NOTE: some params cannot be changed for now
    uts_params params = {
      .tree_type = GEO,
      .geoshape = LINEAR,
      .b_0 = 4.0,
      .gen_mx = 6,
      .shift_depth = 0.5,
    };
    int max_nodes = 128, max_steps = 1000000;
    int root_id = 0;
   
    const char *help = "\n\
    --b_0 <float initial branch factor>\n\
    --gen_mx <int max depth>\n\
    --shift_depth <float shift depth>\n\
    --max_nodes <max node list size per task>\n\
    --max_steps <max steps per task>\n\
    --root_id <integer root id>\n";
      
    int argi = 1;
    while (argi < argc)
    {
      const char *flag = argv[argi++];
      if (argi == argc)
      { 
        printf("Stray flag: %s\n", flag); 
        puts(help);
        ADLB_Fail(-1);
      }
      const char *val = argv[argi++];
      if (strcmp(flag, "--b_0") == 0) {
        params.b_0 = atof(val);
        assert(params.b_0 > 0.0);
      } else if (strcmp(flag, "--gen_mx") == 0) {
        params.gen_mx = atoi(val);
        assert(params.gen_mx > 0);
      } else if (strcmp(flag, "--shift_depth") == 0) {
        params.shift_depth = atof(val);
        assert(params.shift_depth > 0.0);
      } else if (strcmp(flag, "--max_nodes") == 0) {
        max_nodes = atoi(val);
        assert(max_nodes > 0);
      } else if (strcmp(flag, "--max_steps") == 0) {
        max_steps = atoi(val);
        assert(max_steps > 0);
      } else if (strcmp(flag, "--root_id") == 0) {
        root_id = atoi(val);
        assert(root_id > 0);
      } else {
        printf("Unknown flag: %s\n", flag); 
        puts(help);
        ADLB_Fail(-1);
      }

    }

    if ( my_app_rank == 0 ) {
      uts_task root;
      root.task_depth = 0;
      uts_init_root(&root.node, params.tree_type, root_id);
      process_node(&root, params, max_nodes, max_steps);
    }
  
    done = 0;
    while (!done)
    {
      //printf("Getting a command\n");
      MPI_Comm task_comm;
     
      int before_nodes_processed = total_nodes_processed;

      uts_task task;
      int work_len, answer_rank, work_type;
      rc = ADLB_Get(WORKT, &task, &work_len, &answer_rank, &work_type,
                    &task_comm);
      if ( rc == ADLB_SHUTDOWN )
      {
	UTS_INFO("trace: All jobs done\n");
	break;
      }
      
      assert(work_len == sizeof(task));
      process_node(&task, params, max_nodes, max_steps);

      if (total_nodes_processed / NODE_REPORT_INTERVAL >
          before_nodes_processed / NODE_REPORT_INTERVAL )
      {
        UTS_INFO("Processed %ld nodes\n", total_nodes_processed);
      }
    }

    if (my_app_rank == 0)
    {
      end_time = MPI_Wtime();
      printf("TOOK: %.3f\n", end_time-start_time);
    }
  }

  ADLB_Finalize();

  if (!am_server)
  {
    // Print nodes so that we can actually work out throughput
    UTS_INFO("[%i] total_nodes_processed: %ld\n", my_world_rank,
           total_nodes_processed);
  }

  // Do MPI reduction to be able to print sum concisely
  int reduce_root = 0;
  long global_total_nodes_processed;
  rc = MPI_Reduce(&total_nodes_processed, &global_total_nodes_processed,
      1, MPI_LONG, MPI_SUM, reduce_root, MPI_COMM_WORLD);
  assert(rc == MPI_SUCCESS);
  if (my_world_rank == reduce_root)
  {
    fprintf(stderr, "global_total_nodes_processed: %ld\n",
                    global_total_nodes_processed);
  }

  MPI_Finalize();

  return(0);
}
