
/**
 * ADLB SERVER
 *
 **/

#include <assert.h>
#include <stddef.h>

#include <mpi.h>

#include <list_i.h>
#include <memory.h>
#include <tools.h>

#include "adlb.h"
#include "adlb-mpe.h"
#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "handlers.h"
#include "messaging.h"
#include "requestqueue.h"
#include "server.h"
#include "workqueue.h"

/** Number of workers linked to this server */
int my_workers;

/** Time of last activity: used to determine shutdown */
double time_last_action;

/** Time after which to shutdown because idle */
double time_max_idle;

/** Workers that have called ADLB_Shutdown() */
struct list_i workers_shutdown;

adlb_code
adlb_server_init()
{
  printf("adlb_server_init()...\n");
  my_workers = 0;

  list_i_init(&workers_shutdown);
  requestqueue_init(types_size);
  workqueue_init(types_size);

  printf("SERVER for ranks: ");
  for (int i = 0; i < workers; i++)
  {
    if (adlb_map_to_server(i) == world_rank)
    {
      my_workers++;
      printf("%i ", i);
    }
  }
  printf("\n");
  return ADLB_SUCCESS;
}

static inline bool
xlb_is_server(int rank)
{
  if (rank > world_size - servers)
    return true;
  return false;
}

/**
   @param rank of worker
   @return rank of server for this worker rank
 */
int
adlb_map_to_server(int rank)
{
  if (xlb_is_server(rank))
    return rank;
  valgrind_assert(rank >= 0);
  valgrind_assert(rank < world_size);
  int w = rank % servers;
  return w + workers;
}

static adlb_code setup_idle_time(void);

static bool check_idle(void);

static void shutdown_server(void);

adlb_code
ADLBP_Server(long max_memory)
{
  bool done = false;
  int from_rank, tag;

  /** Returns from ADLB */
  adlb_code code;

  MPI_Request *temp_req;

  data_init(servers, world_rank);
  code = setup_idle_time();
  ADLB_CHECK(code)
  mm_set_max(mm_default, max_memory);
  handlers_init();

  // Default: May be overridden below:
  time_max_idle = 1.0;

  code = setup_idle_time();
  assert(code == ADLB_SUCCESS);

  done = 0;
  while (! done)
  {
    if (check_idle())
      break;

    int new_message;
    MPI_Status status;
    // May want to switch to PMPI call for speed
    int rc = MPI_Iprobe(MPI_ANY_SOURCE, MPI_ANY_TAG, adlb_all_comm,
                        &new_message, &status);
    MPI_CHECK(rc);
    if (!new_message)
      continue;

    from_rank = status.MPI_SOURCE;
    tag = status.MPI_TAG;

    // Call appropriate RPC handler:
    code = handle(tag, from_rank);
    ADLB_CHECK(code);
  }
  shutdown_server();

  return ADLB_SUCCESS;
}

/**
    Allow user to override ADLB exhaustion interval
 */
adlb_code
setup_idle_time()
{
   char *s = getenv("ADLB_EXHAUST_TIME");
   if (s != NULL &&
       strlen(s) > 0)
   {
     int c = sscanf(s, "%lf", &time_max_idle);
     if (c != 1 || time_max_idle <= 0)
     {
       printf("Illegal value of ADLB_EXHAUST_TIME!\n");
       return ADLB_ERROR;
     }
   }
   time_last_action = MPI_Wtime();
   return ADLB_SUCCESS;
}

/**
   Am I the master server? (server with lowest rank)
 */
static bool
master_server()
{
  return (world_rank == workers);
}

adlb_code
shutdown_worker(int worker)
{
  DEBUG("shutdown_worker(): %i", worker);
  list_i_add(&workers_shutdown, worker);
  return ADLB_SUCCESS;
}

static bool
workers_idle(void)
{
  int queued = requestqueue_size();
  int shutdown = list_i_size(&workers_shutdown);

  assert(queued+shutdown <= my_workers);

  if (queued+shutdown == my_workers)
    return true;

  return false;
}

/**
   @return true when idle
 */
bool
check_idle()
{
  if (! master_server())
    return false;

  // Current time
  double t = MPI_Wtime();
  // Time idle
  double idle = t - time_last_action;
  if (idle < time_max_idle)
    // Not idle
    return false;

  if (! workers_idle())
    return false;

  // Issue idle check RPCs...

  return true;
}

static void
shutdown_server()
{
  DEBUG("ADLB_Server(): Shutdown...\n");
  requestqueue_shutdown();
}
