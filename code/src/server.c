
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
#include "backoffs.h"
#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "handlers.h"
#include "messaging.h"
#include "mpe-tools.h"
#include "requestqueue.h"
#include "server.h"
#include "steal.h"
#include "workqueue.h"

/** Number of workers associated with this server */
static int xlb_my_workers;

double xlb_time_last_action;

/** Workers that have called ADLB_Shutdown() */
struct list_i workers_shutdown;

bool xlb_server_sync_in_progress = false;

bool server_sync_retry = false;

/** Is this server shutting down? */
static bool shutting_down;

/** Was this server failed? */
static bool failed = false;

/** If we failed, this contains the positive exit code */
static bool fail_code = -1;

static adlb_code setup_idle_time(void);

adlb_code
xlb_server_init()
{
  TRACE_START;

  shutting_down = false;

  list_i_init(&workers_shutdown);
  requestqueue_init(xlb_types_size);
  workqueue_init(xlb_types_size);
  data_init(xlb_servers, xlb_world_rank);
  adlb_code code = setup_idle_time();
  ADLB_CHECK(code);
  // Set a default value for now:
  mm_set_max(mm_default, 10*MB);
  handlers_init();
  xlb_time_last_action = MPI_Wtime();

  // Add up xlb_my_workers:
  // printf("SERVER for ranks: ");
  xlb_my_workers = 0;
  for (int i = 0; i < xlb_workers; i++)
  {
    if (xlb_map_to_server(i) == xlb_world_rank)
    {
      xlb_my_workers++;
      // printf("%i ", i);
    }
  }
  // printf("\n");
  TRACE_END
  return ADLB_SUCCESS;
}

static inline bool
xlb_is_server(int rank)
{
  if (rank > xlb_world_size - xlb_servers)
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
  valgrind_assert(rank < xlb_world_size);
  int w = rank % xlb_servers;
  return w + xlb_workers;
}

static inline bool master_server(void);
static inline void check_idle(void);
static adlb_code server_shutdown(void);
static inline adlb_code check_steal(void);

adlb_code
ADLB_Server(long max_memory)
{
  mm_set_max(mm_default, max_memory);
  while (true)
  {
    if (shutting_down)
      break;
    if (master_server())
      check_idle();
    if (shutting_down)
      break;

    adlb_code code = xlb_serve_one(MPI_ANY_SOURCE);
    ADLB_CHECK(code);

    check_steal();
  }
  server_shutdown();

  return ADLB_SUCCESS;
}

adlb_code
xlb_serve_one(int source)
{
  TRACE_START;
  if (source > 0)
    TRACE("\t source: %i", source);
  int new_message = 0;
  MPI_Status status;
  int rc;

  int attempt = 0;
  bool repeat = true;
  // May want to switch to PMPI call for speed
  while (!new_message)
  {
    rc = MPI_Iprobe(source, MPI_ANY_TAG, adlb_all_comm,
                    &new_message, &status);
    MPI_CHECK(rc);
    if (!new_message)
    {
      if (!repeat)
      {
        TRACE_END;
        return ADLB_NOTHING;
      }
      repeat = xlb_backoff_server(attempt);
      attempt++;
    }
  }


  if (status.MPI_TAG == ADLB_TAG_SYNC_RESPONSE)
  {
    // Corner case: this process is trying to sync with source
    // Source is rejecting the sync request
    int response;
    RECV(&response, 1, MPI_INT, status.MPI_SOURCE,
         ADLB_TAG_SYNC_RESPONSE);
    server_sync_retry = true;
    assert(response == 0);
    TRACE_END;
    return ADLB_NOTHING;
  }

  // Call appropriate RPC handler:
  rc = handle(status.MPI_TAG, status.MPI_SOURCE);
  ADLB_CHECK(rc);
  TRACE_END;
  return rc;
}

/**
   This process has accepted a sync from a calling server
   Handle the actual RPC here
 */
adlb_code
xlb_serve_server(int source)
{
  TRACE_START;
  DEBUG("\t serve_server: %i", source);
  MPI_Status status;
  static int response = 1;
  SEND(&response, 1, MPI_INT, source, ADLB_TAG_SYNC_RESPONSE);
  int rc = ADLB_NOTHING;
  while (rc == ADLB_NOTHING)
  {
    rc = xlb_serve_one(source);
    ADLB_CHECK(rc);
  }
  TRACE_END;
  return rc;
}

double xlb_steal_last = 0.0;

/**
   Steal work
   Operates at intervals defined by steal_backoff
 */
static inline adlb_code
check_steal(void)
{
  if (! steal_allowed())
    // Too soon to try again
    return ADLB_SUCCESS;
  if (requestqueue_size() == 0)
    // Our workers are busy
    return ADLB_SUCCESS;

  // Steal...
  TRACE_START;
  bool b;
  int rc = steal(&b);
  ADLB_CHECK(rc);
  if (b)
  {
    TRACE("check_steal(): rechecking...");
    requestqueue_recheck();
  }
  TRACE_END;
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
     int c = sscanf(s, "%lf", &xlb_max_idle);
     if (c != 1 || xlb_max_idle <= 0)
     {
       printf("Illegal value of ADLB_EXHAUST_TIME!\n");
       return ADLB_ERROR;
     }
   }
   xlb_time_last_action = MPI_Wtime();
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
  return (xlb_world_rank == xlb_workers);
}

static inline bool
workers_idle(void)
{
  int queued = requestqueue_size();
  int shutdown = list_i_size(&workers_shutdown);

  assert(queued+shutdown <= xlb_my_workers);

  if (queued+shutdown == xlb_my_workers)
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

  DEBUG("check_idle(): checking other servers...");

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
  double idle = t - xlb_time_last_action;
  if (idle < xlb_max_idle)
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
  int rc;
  for (int rank = xlb_master_server_rank+1; rank < xlb_world_size;
       rank++)
  {
    bool idle;
    rc = xlb_sync(rank);
    assert(rc == ADLB_SUCCESS);
    rc = ADLB_Server_idle(rank, &idle);
    assert(rc == ADLB_SUCCESS);
    if (! idle)
      return false;
  }
  return true;
}

static void
shutdown_all_servers()
{
  TRACE_START;
  MPE_LOG(xlb_mpe_dmn_shutdown_start)
  shutting_down = true;
  for (int rank = xlb_master_server_rank+1; rank < xlb_world_size;
       rank++)
  {
    int rc = ADLB_Server_shutdown(rank);
    assert(rc == ADLB_SUCCESS);
  }
  TRACE_END;

  MPE_LOG(xlb_mpe_dmn_shutdown_end);
}

adlb_code
xlb_server_fail(int code)
{
  valgrind_assert(xlb_world_rank == xlb_master_server_rank);
  xlb_server_shutdown();
  failed = true;
  fail_code = code;
  return ADLB_SUCCESS;
}

adlb_code
xlb_server_failed(bool* f, int* code)
{
  *f = failed;
  if (code != NULL)
    *code = fail_code;
  return ADLB_SUCCESS;
}

/**
   The master server has told this server to shut down
 */
adlb_code
xlb_server_shutdown()
{
  TRACE_START;
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
  DEBUG("server down.");
  requestqueue_shutdown();
  workqueue_finalize();
  return ADLB_SUCCESS;
}

