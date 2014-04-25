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
#include "refcount.h"
#include "requestqueue.h"
#include "server.h"
#include "steal.h"
#include "sync.h"
#include "turbine.h"
#include "workqueue.h"

// Check for sync requests this often so that can be handled in preference
#define XLB_SERVER_SYNC_CHECK_FREQ 16

/** Number of workers associated with this server */
int xlb_my_workers;

/** Track time of last action */
double xlb_time_last_action;

/** 
   Assign serial numbers to idle check attempts.  This solves a corner
   case with the idle check logic.  Suppose three servers: A, B, C.  A
   is the master.
   1. A is idle, initiates idle check. B has no tasks, C has tasks.
   2. A checks B -> is idle
   3. B steals tasks from C -> is no longer idle
   4. C finishes work, goes idle.
   5. A checks C -> is idle
   6. Shutdown even though B is not idle

   This has more probability of happening with many servers and
   frequent stealing.
   Master server: this is the attempt # for the last round of idle checks
   Other servers: this is the last attempt # seen, either from the master
            serve idle check request, or another server we sent tasks to.

 */
int64_t xlb_idle_check_attempt;

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
bool xlb_server_shutting_down;

/** Was this server failed? */
static bool failed = false;

/** If we failed, this contains the positive exit code */
static bool fail_code = -1;

/** Ready task queue for server */
turbine_work_array xlb_server_ready_work;

static adlb_code setup_idle_time(void);

static inline int xlb_server_number(int rank);

__attribute__((always_inline))
static inline adlb_code xlb_poll(int source, bool prefer_sync, MPI_Status *req_status);

// Service request from queue
__attribute__((always_inline))
static inline adlb_code
xlb_handle_pending(MPI_Status *status);

// Handle pending sync requests, etc
__attribute__((always_inline))
static inline adlb_code
xlb_handle_pending_syncs(void);

static inline adlb_code
xlb_handle_ready_work(void);

/**
   Serve a single request then return
   @param source MPI rank of allowable client: usually MPI_ANY_SOURCE unless syncing
 */
static inline adlb_code xlb_serve_one(int source);

adlb_code
xlb_server_init()
{
  TRACE_START;
  adlb_code code;

  xlb_server_shutting_down = false;
  
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

  list_i_init(&workers_shutdown);
  code = xlb_requestqueue_init(xlb_my_workers);
  ADLB_CHECK(code);
  code = xlb_workq_init(xlb_types_size, xlb_my_workers);
  ADLB_CHECK(code);
  xlb_data_init(xlb_servers, xlb_server_number(xlb_comm_rank));
  code = setup_idle_time();
  ADLB_CHECK(code);
  // Set a default value for now:
  mm_set_max(mm_default, 10*MB);
  xlb_handlers_init();
  xlb_time_last_action = MPI_Wtime();
  
  code = xlb_sync_init();
  ADLB_CHECK(code);
  
  code = xlb_steal_init();
  ADLB_CHECK(code);

  turbine_engine_code tc = turbine_engine_init(xlb_comm_rank);
  CHECK_MSG(tc == TURBINE_SUCCESS, "Error initializing engine");

  xlb_server_ready_work.work = NULL;
  xlb_server_ready_work.size = 0;
  xlb_server_ready_work.count = 0;

  TRACE_END
  return ADLB_SUCCESS;
}

// return the number of the server (0 is first server)
static inline int
xlb_server_number(int rank)
{
  return rank - (xlb_comm_size - xlb_servers);
}

__attribute__((always_inline))
static inline adlb_code serve_several(void);
static inline bool master_server(void);
static inline bool check_idle(void);
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

  DEBUG("ADLB_Server(): %i entering server loop", xlb_comm_rank);

  update_cached_time(); // Initial timestamp
  while (true)
  {
    if (xlb_server_shutting_down)
      break;
    if (master_server() && check_idle())
      break;

    update_cached_time(); // Periodically refresh timestamp

    adlb_code code;
    code = serve_several();
    ADLB_CHECK(code);

    update_cached_time(); // Periodically refresh timestamp

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
static inline adlb_code
serve_several()
{
  int exit_points = 0;
  int reqs = 0;
  bool other_servers = (xlb_servers > 1);
  while (exit_points < xlb_loop_threshold)
  {
    MPI_Status req_status;
    adlb_code code;
    // Prioritize server-to-server syncs to avoid blocking other servers
    bool prefer_sync = other_servers &&
          (reqs % XLB_SERVER_SYNC_CHECK_FREQ == 0);
    code = xlb_poll(MPI_ANY_SOURCE, prefer_sync, &req_status);
    if (code == ADLB_SUCCESS)
    {
      code = xlb_handle_pending(&req_status);
      ADLB_CHECK(code);

      // Previous request may have resulted in pending sync requests
      code = xlb_handle_pending_syncs();
      ADLB_CHECK(code);

      // Previous request may have resulted in pending work
      code = xlb_handle_ready_work();
      ADLB_CHECK(code);

      // Back off less on each successful request
      curr_server_backoff /= 2;

      exit_points += xlb_loop_request_points;
      reqs++;
    }
    else if (code == ADLB_NOTHING)
    {
      // Check for shutdown
      if (xlb_server_shutting_down)
      {
        return ADLB_SUCCESS;
      }
      // Backoff
      bool slept;
      bool again = xlb_backoff_server(curr_server_backoff, &slept);
      // If we reach max backoff, exit
      if (!again)
        break;
      
      if (slept)
        exit_points += xlb_loop_sleep_points;
      else
        exit_points += xlb_loop_poll_points;
      // Back off more
      curr_server_backoff++;
    }
    else
    {
      ADLB_CHECK(code);
    }
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
xlb_handle_pending(MPI_Status* status)
{
  // Call appropriate RPC handler:
  adlb_code rc = xlb_handle(status->MPI_TAG, status->MPI_SOURCE);

  // Track for idle time
  update_time_last_action(status->MPI_TAG);
  ADLB_CHECK(rc);
  return rc;
}

static inline adlb_code
xlb_handle_pending_syncs(void)
{
  int rc;
  adlb_data_code dc;

  xlb_pending_kind kind;
  int rank;
  struct packed_sync *hdr;
  void *extra_data;

  // Handle outstanding sync requests
  while ((rc = xlb_dequeue_pending(&kind, &rank, &hdr, &extra_data))
            == ADLB_SUCCESS)
  {
    DEBUG("server_sync: [%d] handling deferred sync from %d",
          xlb_comm_rank, rank);
    switch (kind)
    {
      case DEFERRED_SYNC:
        rc = xlb_accept_sync(rank, hdr, false);
        ADLB_CHECK(rc);
        break;
      case ACCEPTED_RC:
        dc = xlb_incr_rc_local(hdr->incr.id, hdr->incr.change, true);
        CHECK_MSG(dc == ADLB_DATA_SUCCESS, "unexpected error in refcount");
        break;
      case DEFERRED_NOTIFY:
        rc = xlb_handle_notify_sync(rank, &hdr->subscribe, hdr->sync_data,
                                    extra_data);
        ADLB_CHECK(rc);
        break;
      case UNSENT_NOTIFY:
        rc = xlb_send_unsent_notify(rank, hdr, extra_data);
        ADLB_CHECK(rc);
        break;
      default:
        ERR_PRINTF("Unexpected pending sync kind %i\n", kind);
        return ADLB_ERROR;
    }

    // Clean up memory
    free(hdr);
  }
  ADLB_CHECK(rc); // Check that not error instead of ADLB_NOTHING
  
  return ADLB_SUCCESS;
}

/*
 * Handle all ready work
 */
static inline adlb_code
xlb_handle_ready_work(void)
{
  adlb_code rc;
  if (xlb_server_ready_work.count == 0)
  {
    return ADLB_SUCCESS;
  }

  for (int i = 0; i < xlb_server_ready_work.count; i++)
  {
    rc = xlb_put_work_unit(xlb_server_ready_work.work[i]);
    ADLB_CHECK(rc);
  }

  if (xlb_server_ready_work.size > 1024)
  {
    // Prevent from growing too large
    free(xlb_server_ready_work.work);
    xlb_server_ready_work.work = NULL;
    xlb_server_ready_work.size = 0;
  }
  xlb_server_ready_work.count = 0;
  return ADLB_SUCCESS;
}

static inline adlb_code
xlb_serve_one(int source)
{
  TRACE_START;
  if (source > 0)
    TRACE("\t source: %i", source);
  MPI_Status status;
  adlb_code code = xlb_poll(source, false, &status);
  ADLB_CHECK(code);

  if (code == ADLB_NOTHING)
    return ADLB_NOTHING;

  int rc = xlb_handle_pending(&status);

  TRACE_END;

  return rc;
}

adlb_code
xlb_serve_server(int source)
{
  TRACE_START;
  DEBUG("\t serve_server: [%i] serving %i", xlb_comm_rank, source);
  int rc = ADLB_NOTHING;
  while (true)
  {
    rc = xlb_serve_one(source);
    ADLB_CHECK(rc);
    if (rc != ADLB_NOTHING) break;
    // Don't backoff - want to unblock other server ASAP
  }
  DEBUG("\t serve_server: [%i] served %i", xlb_comm_rank, source);
  TRACE_END;
  return rc;
}

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
  adlb_code rc = xlb_steal_match();
  TRACE_END;
  return rc;
}

/*
  Carry out everyything required for a steal attempt, including matching
  of newly acquired tasks if neededd
 */
adlb_code xlb_steal_match(void)
{
  DEBUG("Attempting steal");
  bool stole_single, stole_par;
  int rc = xlb_steal(&stole_single, &stole_par);
  ADLB_CHECK(rc);
  DEBUG("Completed steal stole_single: %i stole_par: %i",
          (int)stole_single, (int)stole_par);

  // xlb_steal may have added pending syncs
  rc = xlb_handle_pending_syncs();
  ADLB_CHECK(rc);

  // Try to match stolen tasks
  if (stole_single)
  {
    TRACE("After steal rechecking single-worker...");
    rc = xlb_recheck_queues();
    ADLB_CHECK(rc);
  }
  if (stole_par)
  {
    TRACE("After steal rechecking parallel...");
    rc = xlb_recheck_parallel_queues();
    ADLB_CHECK(rc);
  }

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
   xlb_idle_check_attempt = 0;
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
static inline bool 
check_idle()
{
  assert(master_server());

  if (! xlb_server_check_idle_local(true, 0))
    // This server is not idle long enough...
    return false;

  DEBUG("check_idle(): checking other servers...");

  // Issue idle check RPCs...
  if (! servers_idle())
    // Some server is still not idle...
    return false;


  shutdown_all_servers();
  return true;
}

bool
xlb_server_check_idle_local(bool master, int64_t check_attempt)
{
  if (!master)
  {
    DEBUG("Idle check: attempt #%"PRId64" last seen attempt #%"PRId64,
        check_attempt, xlb_idle_check_attempt);
    // These numbers are generated by master, so shouldn't be ahead
    assert(check_attempt >= xlb_idle_check_attempt);

    if (check_attempt == xlb_idle_check_attempt)
    {
      // We sent work to another server since the master started checking
      // for idleness
      return false;
    }

    // Update last check attempt
    xlb_idle_check_attempt = check_attempt;
  }

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
  // New serial number for round of checks 
  xlb_idle_check_attempt++;
  DEBUG("Master server initiating idle check attempt #%"PRId64,
        xlb_idle_check_attempt);

  // Arrays containing request and work counts from all servers
  // The counts from each server are stored contiguously
  int *request_counts = malloc(sizeof(int) *
                              (size_t)(xlb_types_size * xlb_servers));
  int *work_counts = malloc(sizeof(int) *
                              (size_t)(xlb_types_size * xlb_servers));
  // First fill in counts from this server
  requestqueue_type_counts(request_counts, xlb_types_size);
  xlb_workq_type_counts(work_counts, xlb_types_size);
  
  int rc;
  bool all_idle = true;
  for (int rank = xlb_master_server_rank+1; rank < xlb_comm_size;
       rank++)
  {
    int server_num = rank - xlb_master_server_rank;
    bool idle;
    rc = xlb_sync(rank);
    ASSERT(rc == ADLB_SUCCESS);
    int *req_subarray = &request_counts[xlb_types_size * server_num];
    int *work_subarray = &work_counts[xlb_types_size * server_num];
    rc = ADLB_Server_idle(rank, xlb_idle_check_attempt, &idle,
                          req_subarray, work_subarray);
    ASSERT(rc == ADLB_SUCCESS);
    if (! idle)
    {
      all_idle = false;
      // Break so we can cleanup allocations
      break;
    }
  }

  // Check to see if work stealing could match work to requests
  if (all_idle)
  {
    for (int t = 0; t < xlb_types_size; t++)
    {
      int has_requests = -1;
      int has_work = -1;
      for (int server = 0; server < xlb_servers; server++)
      {
        if (request_counts[t + server * xlb_types_size] > 0)
        {
          has_requests = server;
        }
        if (work_counts[t + server * xlb_types_size] > 0)
        {
          has_work = server;
        }
      }
      if (has_requests != -1 && has_work != -1)
      {
        // We have requests and work that could be matched up -
        // not actually idle
        DEBUG("Unmatched work of type %i. Work is on server %i. "
              "Requests are on server %i", t, has_work, has_requests);
        all_idle = false;
        break;
      }
    }
  }

  free(request_counts);
  free(work_counts);
  return all_idle;
}

static void
shutdown_all_servers()
{
  TRACE_START;
  MPE_LOG(xlb_mpe_dmn_shutdown_start)
  DEBUG("Initiating server shutdown");
  xlb_server_shutting_down = true;
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
  xlb_server_shutting_down = true;
  return ADLB_SUCCESS;
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
  xlb_sync_finalize();

  turbine_engine_finalize();
  return ADLB_SUCCESS;
}

/* Print out any final statistics, if enabled */
static inline void print_final_stats()
{
  bool print_time;
  getenv_boolean("ADLB_PRINT_TIME", false, &print_time);
  if (print_time)
  {
    double xlb_end_time = MPI_Wtime();
    double xlb_elapsed_time = xlb_end_time - xlb_start_time;
    printf("ADLB Total Elapsed Time: %.3lf\n", xlb_elapsed_time);
  }

  // Print other performance counters
  xlb_print_handler_counters();
  xlb_print_workq_perf_counters();
  xlb_print_sync_counters();
  turbine_engine_print_counters();
}
