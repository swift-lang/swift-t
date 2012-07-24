
/**
 * ADLB SERVER
 *
 **/

#include <assert.h>
#include <stddef.h>
#include <unistd.h>

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

/** Is this server shutting down? */
static bool shutting_down;

static adlb_code setup_idle_time(void);


adlb_code
xlb_server_init()
{
  shutting_down = false;
  printf("adlb_server_init()...\n");
  my_workers = 0;

  list_i_init(&workers_shutdown);
  requestqueue_init(types_size);
  workqueue_init(types_size);
  data_init(servers, world_rank);
  adlb_code code = setup_idle_time();
  ADLB_CHECK(code);
  // Set a default value for now:
  mm_set_max(mm_default, 10*MB);
  handlers_init();
  time_last_action = MPI_Wtime();

  // Default: May be overridden below:
  time_max_idle = 5.0;

  printf("SERVER for ranks: ");
  for (int i = 0; i < workers; i++)
  {
    if (xlb_map_to_server(i) == world_rank)
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
xlb_map_to_server(int rank)
{
  if (xlb_is_server(rank))
    return rank;
  valgrind_assert(rank >= 0);
  valgrind_assert(rank < world_size);
  int w = rank % servers;
  return w + workers;
}

static inline bool master_server();
static inline void check_idle(void);
static adlb_code server_shutdown(void);

adlb_code
ADLB_Server(long max_memory)
{
  mm_set_max(mm_default, max_memory);
  while (true)
  {
    if (master_server())
      check_idle();
    if (shutting_down)
      break;

    adlb_code code = xlb_serve_one();
    ADLB_CHECK(code);
  }
  server_shutdown();

  return ADLB_SUCCESS;
}

static inline void backoff(void);

adlb_code
xlb_serve_one()
{
  DEBUG_START;
  int new_message;
  MPI_Status status;
  // May want to switch to PMPI call for speed
  int rc = MPI_Iprobe(MPI_ANY_SOURCE, MPI_ANY_TAG, adlb_all_comm,
                      &new_message, &status);
  MPI_CHECK(rc);

  if (!new_message)
  {
    backoff();
    return ADLB_SUCCESS;
  }

  // This is a special case: this server has requested a steal and is
  // now getting a response - do nothing
  if (status.MPI_TAG == ADLB_TAG_RESPONSE_STEAL)
    return ADLB_SUCCESS;

  // Call appropriate RPC handler:
  adlb_code code = handle(status.MPI_TAG, status.MPI_SOURCE);
  DEBUG_END;
  return code;
}

static inline void
backoff()
{
  sleep(1);
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

adlb_code
xlb_shutdown_worker(int worker)
{
  DEBUG("shutdown_worker(): %i", worker);
  list_i_add(&workers_shutdown, worker);
  return ADLB_SUCCESS;
}

/**
   Am I the master server? (server with lowest rank)
 */
static inline bool
master_server()
{
  return (world_rank == workers);
}

static inline bool
workers_idle(void)
{
  int queued = requestqueue_size();
  int shutdown = list_i_size(&workers_shutdown);

  assert(queued+shutdown <= my_workers);

  if (queued+shutdown == my_workers)
    return true;

  return false;
}

static bool servers_idle(void);
static void shutdown_all_servers(void);

/**
   Master server uses this to check for shutdown condition
   @return true when idle
 */
static inline void
check_idle()
{
  if (! xlb_server_check_idle_local())
    // This server is not idle long enough...
    return;

  // Issue idle check RPCs...
  if (! servers_idle())
    // Some server is still not idle...
    return;

  shutdown_all_servers();
}

bool
xlb_server_check_idle_local()
{
  // Current time
  double t = MPI_Wtime();

  // Time idle
  double idle = t - time_last_action;
  if (idle < time_max_idle)
    // Not idle long enough...
    return false;

  if (! workers_idle())
    // A worker is busy...
    return false;

  return true;
}

static bool
servers_idle()
{
  for (int rank = master_server_rank+1; rank < world_size; rank++)
  {
    bool idle;
    int rc = ADLB_Server_idle(rank, &idle);
    assert(rc == ADLB_SUCCESS);
    if (! idle)
      return false;
  }
  return true;
}

static void
shutdown_all_servers()
{
  shutting_down = true;
  for (int rank = master_server_rank+1; rank < world_size; rank++)
  {
    int rc = ADLB_Server_shutdown(rank);
    assert(rc == ADLB_SUCCESS);
  }
}

/**
   The master server has told this server to shut down
 */
adlb_code
xlb_server_shutdown()
{
  DEBUG_START;
  shutting_down = true;
  return ADLB_SUCCESS;
}

bool
xlb_server_shutting_down()
{
  return shutting_down;
}

/**
   Actually shut down
 */
static adlb_code
server_shutdown()
{
  DEBUG_START;
  requestqueue_shutdown();
  return ADLB_SUCCESS;
}

