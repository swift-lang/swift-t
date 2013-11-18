/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


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
#include "sync.h"
#include "workqueue.h"

// Check for sync requests this often so that can be handled in preference
#define XLB_SERVER_SYNC_CHECK_FREQ 16

/** Number of workers associated with this server */
static int xlb_my_workers;

/** Track time of last action */
double xlb_time_last_action;

/** Cached recent timestamp */
static double xlb_time_approx_now;

double xlb_approx_time(void)
{
  return xlb_time_approx_now;
}

static inline void update_time_last_action(adlb_tag tag)
{
  // Update timestamp:
  if (tag != ADLB_TAG_CHECK_IDLE &&
      tag != ADLB_TAG_SYNC_REQUEST)
    xlb_time_last_action = xlb_time_approx_now;
}

static inline void update_cached_time()
{
  xlb_time_approx_now = MPI_Wtime();
}

/** Workers that have called ADLB_Shutdown() */
struct list_i workers_shutdown;

bool xlb_server_sync_in_progress = false;

/** Is this server shutting down? */
static bool shutting_down;

/** Was this server failed? */
static bool failed = false;

/** If we failed, this contains the positive exit code */
static bool fail_code = -1;

static adlb_code setup_idle_time(void);

static inline int xlb_server_number(int rank);

static inline adlb_code xlb_poll(int source, bool prefer_sync, MPI_Status *req_status);

// Service request from queue
static inline adlb_code
xlb_handle_pending(MPI_Status *status, bool *sync_rejected);

// Handle pending sync requests
static inline adlb_code
xlb_handle_pending_syncs();

/**
   Serve a single request then return
   @param source MPI rank of allowable client: usually MPI_ANY_SOURCE unless syncing
   @param sync_rejected: set to true if we got a sync rejected message
 */
static inline adlb_code xlb_serve_one(int source, bool *sync_rejected);

adlb_code
xlb_server_init()
{
  TRACE_START;

  shutting_down = false;

  list_i_init(&workers_shutdown);
  xlb_requestqueue_init();
  xlb_workq_init(xlb_types_size);
  xlb_data_init(xlb_servers, xlb_server_number(xlb_comm_rank));
  adlb_code code = setup_idle_time();
  ADLB_CHECK(code);
  // Set a default value for now:
  mm_set_max(mm_default, 10*MB);
  xlb_handlers_init();
  xlb_time_last_action = MPI_Wtime();

  // Add up xlb_my_workers:
  // printf("SERVER for ranks: ");
  xlb_my_workers = 0;
  for (int i = 0; i < xlb_workers; i++)
  {
    if (xlb_map_to_server(i) == xlb_comm_rank)
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
  if (rank > xlb_comm_size - xlb_servers)
    return true;
  return false;
}

// return the number of the server (0 is first server)
static inline int
xlb_server_number(int rank)
{
  return rank - (xlb_comm_size - xlb_servers);
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
  valgrind_assert(rank < xlb_comm_size);
  int w = rank % xlb_servers;
  return w + xlb_workers;
}

static adlb_code serve_several(void);
static inline bool master_server(void);
static inline void check_idle(void);
static adlb_code server_shutdown(void);
static inline adlb_code check_steal(void);
static inline void print_final_stats();

adlb_code
ADLB_Server(long max_memory)
{
  TRACE_START;

  if (!xlb_am_server)
  {
    printf("ADLB_Server invoked for non-server\n");
    return ADLB_ERROR;
  }

  mm_set_max(mm_default, max_memory);

  update_cached_time(); // Initial timestamp
  while (true)
  {
    if (shutting_down)
      break;
    if (master_server())
      check_idle();
    if (shutting_down)
      break;

    update_cached_time(); // Periodically refresh timestamp

    adlb_code code;
    code = serve_several();
    ADLB_CHECK(code);

    update_cached_time(); // Periodically refresh timestamp

    code = xlb_check_parallel_tasks(0);
    ADLB_CHECK(code);
    // code = check_parallel_tasks(1);
    // ADLB_CHECK(code);

    check_steal();
  }

  // Print stats, then cleanup all modules
  print_final_stats();
  server_shutdown();

  TRACE_END;
  return ADLB_SUCCESS;
}

// Track current backoff amount for adaptive backoff
static int curr_server_backoff = 0;

/**
   Serve several requests before returning.
   If there are pending requests in the queue, we try to serve them
   as quickly as possible.  If there are no requests we busy-wait
   then back off several times with sleeps so as to avoid using
   excessive CPU.  We use an adaptive algorithm that backs off
   more if the queue has been empty recently.
 */
static adlb_code
serve_several()
{
  int reqs = 0; // count of requests served
  int total_polls = 0; // total polls
  int sleeps = 0;
  while (reqs < xlb_loop_max_requests &&
         total_polls < xlb_loop_max_polls &&
         sleeps < xlb_loop_max_sleeps)
  {
    MPI_Status req_status;
    adlb_code code;
    // Prioritize server-to-server syncs to avoid blocking other servers
    bool prefer_sync = (reqs % XLB_SERVER_SYNC_CHECK_FREQ == 0);
    code = xlb_poll(MPI_ANY_SOURCE, prefer_sync, &req_status);
    ADLB_CHECK(code);
    if (code == ADLB_SUCCESS)
    {
      code = xlb_handle_pending(&req_status, NULL);
      ADLB_CHECK(code);
      reqs++;

      // Previous request may have resulted in pending sync requests
      code = xlb_handle_pending_syncs();
      ADLB_CHECK(code);

      // Back off less on each successful request
      curr_server_backoff /= 2;
    }
    else
    {
      // Backoff
      bool slept;
      bool again = xlb_backoff_server(curr_server_backoff, &slept);
      // If we reach max backoff, exit
      if (!again)
        break;
      if (slept)
        sleeps++;
      // Back off more
      curr_server_backoff++;
    }
    total_polls++;
  }

  return reqs > 0 ? ADLB_SUCCESS : ADLB_NOTHING;
}

/**
   Poll msg queue for requests

   prefer_sync: if true, check for server-server syncs first
 */
static inline adlb_code
xlb_poll(int source, bool prefer_sync, MPI_Status *req_status)
{
  int new_message;
  if (prefer_sync)
  {
    IPROBE(source, ADLB_TAG_SYNC_REQUEST, &new_message, req_status);
    if (new_message)
      return ADLB_SUCCESS;
  }
  IPROBE(source, MPI_ANY_TAG, &new_message, req_status);
  return new_message ? ADLB_SUCCESS : ADLB_NOTHING;
}

static inline adlb_code
xlb_handle_pending(MPI_Status* status, bool *sync_rejected)
{
  if (status->MPI_TAG == ADLB_TAG_SYNC_RESPONSE)
  {
    // Corner case: this process is trying to sync with source
    // Source is rejecting the sync request
    int response;
    RECV_STATUS(&response, 1, MPI_INT, status->MPI_SOURCE,
                ADLB_TAG_SYNC_RESPONSE, status);
    assert(response == 0);
    if (sync_rejected != NULL)
      *sync_rejected = true;
    DEBUG("server_sync: [%d] sync rejected", xlb_comm_rank);
    TRACE_END;
    return ADLB_NOTHING;
  }

  // Call appropriate RPC handler:
  adlb_code rc = xlb_handle(status->MPI_TAG, status->MPI_SOURCE);

  // Track for idle time
  update_time_last_action(status->MPI_TAG);
  ADLB_CHECK(rc);
  return rc;
}

static inline adlb_code
xlb_handle_pending_syncs()
{
  if (xlb_pending_sync_count > 0)
  {
    // Handle outstanding sync requests
    for (int i = 0; i < xlb_pending_sync_count; i++)
    {
      int rank = xlb_pending_syncs[i].rank;
      DEBUG("server_sync: [%d] handling deferred sync %d from %d",
          xlb_comm_rank, i, rank);
      adlb_code code = xlb_handle_accepted_sync(rank,
                     xlb_pending_syncs[i].hdr, NULL);
      if (code == ADLB_SUCCESS)
      {
        free(xlb_pending_syncs[i].hdr);
      }
      else
      {
        // Update pending syncs to avoid corrupted state
        if (i > 0)
          for (int j = 0; j < xlb_pending_sync_count - i; j++) {
            xlb_pending_syncs[j] = xlb_pending_syncs[j + i];
          }
        xlb_pending_sync_count -= i;
        return code;
      }
    }
    xlb_pending_sync_count = 0;
  }
  return ADLB_SUCCESS;
}

static inline adlb_code
xlb_serve_one(int source, bool *sync_rejected)
{
  TRACE_START;
  if (source > 0)
    TRACE("\t source: %i", source);
  MPI_Status status;
  adlb_code code = xlb_poll(source, false, &status);
  ADLB_CHECK(code);

  if (code == ADLB_NOTHING)
    return ADLB_NOTHING;

  int rc = xlb_handle_pending(&status, sync_rejected);

  TRACE_END;

  return rc;
}

adlb_code
xlb_serve_server(int source, bool *server_sync_retry)
{
  TRACE_START;
  DEBUG("\t serve_server: [%i] serving %i", xlb_comm_rank, source);
  int rc = ADLB_NOTHING;
  while (true)
  {
    rc = xlb_serve_one(source, server_sync_retry);
    ADLB_CHECK(rc);
    if (rc != ADLB_NOTHING) break;
    // Don't backoff - want to unblock other server ASAP
  }
  DEBUG("\t serve_server: [%i] served %i", xlb_comm_rank, source);
  TRACE_END;
  return rc;
}

double xlb_steal_last = 0.0;

/**
   Steal work
   Operates at intervals defined by xlb_steal_backoff
 */
static inline adlb_code
check_steal(void)
{
  if (! xlb_steal_allowed())
    // Too soon to try again
    return ADLB_SUCCESS;
  if (xlb_requestqueue_size() == 0)
    // Our workers are busy
    return ADLB_SUCCESS;

  // Steal...
  TRACE_START;
  bool b;
  int rc = xlb_steal(&b);
  ADLB_CHECK(rc);

  // xlb_steal may have added pending syncs
  rc = xlb_handle_pending_syncs();
  ADLB_CHECK(rc);

  if (b)
  {
    TRACE("check_steal(): rechecking...");
    xlb_recheck_queues();
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
  return (xlb_comm_rank == xlb_workers);
}

static inline bool
workers_idle(void)
{
  int queued = xlb_requestqueue_size();
  int shutdown = list_i_size(&workers_shutdown);

  assert(queued+shutdown <= xlb_my_workers);

  // TRACE("workers_idle(): workers queued:   %i\n", queued);
  // TRACE("workers_idle(): workers shutdown: %i\n", shutdown);

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
  if (! workers_idle())
    // A worker is busy...
    return false;

  // Current time
  double t = MPI_Wtime();

  // Time idle
  double idle = t - xlb_time_last_action;
  if (idle < xlb_max_idle)
    // Not idle long enough...
    return false;

  return true;
}

static bool
servers_idle()
{
  int rc;
  for (int rank = xlb_master_server_rank+1; rank < xlb_comm_size;
       rank++)
  {
    bool idle;
    rc = xlb_sync(rank);
    ASSERT(rc == ADLB_SUCCESS);
    rc = ADLB_Server_idle(rank, &idle);
    ASSERT(rc == ADLB_SUCCESS);
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
  for (int rank = xlb_master_server_rank+1; rank < xlb_comm_size;
       rank++)
  {
    int rc = ADLB_Server_shutdown(rank);
    ASSERT(rc == ADLB_SUCCESS);
  }
  TRACE_END;

  MPE_LOG(xlb_mpe_dmn_shutdown_end);
}

adlb_code
xlb_server_fail(int code)
{
  valgrind_assert(xlb_comm_rank == xlb_master_server_rank);
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
  xlb_requestqueue_shutdown();
  xlb_workq_finalize();
  return ADLB_SUCCESS;
}

/* Print out any final statistics, if enabled */
static inline void print_final_stats()
{
  bool print_time = false;
  xlb_env_boolean("ADLB_PRINT_TIME", &print_time);
  if (print_time)
  {
    double xlb_end_time = MPI_Wtime();
    double xlb_elapsed_time = xlb_end_time - xlb_start_time;
    printf("ADLB Total Elapsed Time: %.3lf\n", xlb_elapsed_time);
  }

  // Print other performance counters
  xlb_print_handler_counters();
  xlb_print_workq_perf_counters();
}

