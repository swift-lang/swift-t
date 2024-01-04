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


/*
 * sync.c
 *
 *  Created on: Aug 20, 2012
 *      Authors: wozniak, armstrong
 */

#include <assert.h>

#include <mpi.h>

#include "backoffs.h"
#include "common.h"
#include "debug.h"
#include "messaging.h"
#include "mpe-tools.h"
#include "refcount.h"
#include "server.h"
#include "steal.h"
#include "sync.h"

// Enable debugging of very long syncs
#ifndef XLB_DEBUG_SYNC_DELAY
#define XLB_DEBUG_SYNC_DELAY 0
#endif

// Print messages if this time in seconds exceeded
#define XLB_DEBUG_SYNC_DELAY_LIMIT 0.05

struct sync_delay {
  int attempts;
  double start_time;
  double last_check_time;
};

static adlb_code xlb_sync2(int target, const struct packed_sync *hdr,
                           int *response);
static xlb_sync_recv *xlb_next_sync_msg(void);
static adlb_code xlb_sync_msg_done(void);

static inline adlb_code msg_from_target(int target, int response);
static adlb_code msg_from_other_server(int other_server,
                                       bool *shutting_down);
static inline adlb_code cancel_sync(adlb_sync_mode mode, int sync_target);

static adlb_code xlb_handle_subscribe_sync(int rank,
        const struct packed_sync *hdr, bool defer_svr_ops);

static adlb_code enqueue_deferred_notify(int rank,
      const struct packed_sync *hdr);

static adlb_code enqueue_pending(xlb_pending_kind kind, int rank,
                         const struct packed_sync *hdr, void *extra_data);

static void free_pending_sync(xlb_pending *pending);

static inline bool sync_accept_required(adlb_sync_mode mode);

static inline void delay_check_init(struct sync_delay *state);
static inline void delay_check(struct sync_delay *state,
              int target, const struct packed_sync *hdr);

typedef struct {
  int64_t sent;     /** Sent to other servers */
  int64_t accepted; /** Accepted from other servers */
} xlb_sync_mode_counter;

static xlb_sync_mode_counter xlb_sync_perf_counters[ADLB_SYNC_ENUM_COUNT];
static const char *xlb_sync_mode_name[ADLB_SYNC_ENUM_COUNT];

#define xlb_add_sync_type_name(name) \
            xlb_sync_mode_name[name] = #name;

/*
  Ring buffer of MPI_Request objects and buffers used to receive incoming
  sync requests, bypassing the MPI unexpected message queue.
  Active MPI_IRecv requests exist for all buffers in this queue.

  Size can be controlled by ADLB_SYNC_RECVS.
 */
xlb_sync_recv *xlb_sync_recvs = NULL;
int xlb_sync_recv_head;
int xlb_sync_recv_size = 0;

// Default cap on number of buffers
// This affects performance:
// Too high, and we have many posted receives to match messages against
// Too small, and messages go into the unexpected message queue
#define XLB_SYNC_RECV_DEFAULT_MAX 16
static adlb_code xlb_sync_recv_init_size(int servers, int *size);

/*
  Pending sync requests that we deferred
  Implemented with FIFO ring buffer since we need FIFO in case refcount
  incr is followed by decr: processing in LIFO order could result in
  premature free.
 */
xlb_pending *xlb_pending_syncs = NULL;
int xlb_pending_sync_count = 0;
int xlb_pending_sync_head = 0;
int xlb_pending_sync_size = 0; // Malloced size
int xlb_pending_notif_count = 0; // Number that are notifs

adlb_code
xlb_sync_init(void)
{
  adlb_code rc;
  long tmp;

  /*
    Setup sync recv buffers
   */
  rc = xlb_sync_recv_init_size(xlb_s.layout.servers, &xlb_sync_recv_size);
  ADLB_CHECK(rc);
  xlb_sync_recv_head = 0;
  if (xlb_sync_recv_size > 0)
  {
    xlb_sync_recvs = malloc((size_t)xlb_sync_recv_size *
                            sizeof(xlb_sync_recvs[0]));
    ADLB_CHECK_MALLOC(xlb_sync_recvs);
  }
  else
  {
    xlb_sync_recvs = NULL;
  }

  DEBUG("Allocate %i sync recv buffers\n", xlb_sync_recv_size);

  for (int i = 0; i < xlb_sync_recv_size; i++)
  {
    xlb_sync_recvs[i].buf = malloc(PACKED_SYNC_SIZE);
    ADLB_CHECK_MALLOC(xlb_sync_recvs[i].buf);
    // Initiate requests for all in queue
    IRECV2(xlb_sync_recvs[i].buf, (int)PACKED_SYNC_SIZE, MPI_BYTE,
           MPI_ANY_SOURCE, ADLB_TAG_SYNC_REQUEST, &xlb_sync_recvs[i].req);
  }

  /*
    Setup pending sync buffer
   */
  xlb_pending_sync_size = PENDING_SYNC_INIT_SIZE;

  // Optionally have different min size - otherwise we won't cover the
  // resizing cases in testing
  rc = xlb_env_long("ADLB_DEBUG_SYNC_BUFFER_SIZE", &tmp);
  ADLB_CHECK(rc);

  if (rc != ADLB_NOTHING)
  {
    assert(tmp > 0 && tmp <= INT_MAX);
    xlb_pending_sync_size = (int)tmp;
  }

  xlb_pending_sync_count = 0;
  xlb_pending_sync_head = 0;
  xlb_pending_syncs = malloc(sizeof(xlb_pending_syncs[0]) *
                                (size_t)xlb_pending_sync_size);
  ADLB_CHECK_MALLOC(xlb_pending_syncs);
  xlb_pending_notif_count = 0;

  /*
    Setup perf counters
   */
  if (xlb_s.perfc_enabled)
  {
    for (int i = 0; i < ADLB_SYNC_ENUM_COUNT; i++)
    {
      xlb_sync_perf_counters[i].sent = 0;
      xlb_sync_perf_counters[i].accepted = 0;
    }

    // Register human-readable names
    xlb_add_sync_type_name(ADLB_SYNC_REQUEST);
    xlb_add_sync_type_name(ADLB_SYNC_STEAL_PROBE);
    xlb_add_sync_type_name(ADLB_SYNC_STEAL_PROBE_RESP);
    xlb_add_sync_type_name(ADLB_SYNC_STEAL);
    xlb_add_sync_type_name(ADLB_SYNC_REFCOUNT);
    xlb_add_sync_type_name(ADLB_SYNC_SUBSCRIBE);
    xlb_add_sync_type_name(ADLB_SYNC_NOTIFY);
    xlb_add_sync_type_name(ADLB_SYNC_SHUTDOWN);
  }
  return ADLB_SUCCESS;
}

void xlb_sync_finalize(void)
{
  /*
    Free sync request buffers, cancel requests
   */
  for (int i = 0; i < xlb_sync_recv_size; i++)
  {
    MPI_Cancel(&xlb_sync_recvs[i].req);
    free(xlb_sync_recvs[i].buf);
  }
  free(xlb_sync_recvs);
  xlb_sync_recvs = NULL;
  xlb_sync_recv_size = 0;

  DEBUG("[%i] pending syncs at finalize: %i", xlb_s.layout.rank,
       xlb_pending_sync_count);

  /*
    Free pending syncs
   */
  for (int i = 0; i < xlb_pending_sync_count; i++)
  {
    int ix = (xlb_pending_sync_head + i) % xlb_pending_sync_size;
    free_pending_sync(&xlb_pending_syncs[ix]);
  }
  free(xlb_pending_syncs);
  xlb_pending_sync_count = 0;
  xlb_pending_sync_size = 0;
  xlb_pending_notif_count = 0;
}

void xlb_print_sync_counters(void)
{
  if (!xlb_s.perfc_enabled)
  {
    return;
  }

  for (int i = 0; i < ADLB_SYNC_ENUM_COUNT; i++)
  {
    PRINT_COUNTER("SYNC_SENT_%s=%"PRId64"\n", xlb_sync_mode_name[i],
                  xlb_sync_perf_counters[i].sent);
    PRINT_COUNTER("SYNC_ACCEPTED_%s=%"PRId64"\n", xlb_sync_mode_name[i],
                  xlb_sync_perf_counters[i].accepted);
  }
}

/*
  Decide number of sync receive buffers to use.
 */
static adlb_code xlb_sync_recv_init_size(int servers, int *size)
{
  long tmp;
  adlb_code rc = xlb_env_long("ADLB_SYNC_RECVS", &tmp);
  ADLB_CHECK(rc);

  if (rc != ADLB_NOTHING)
  {
    assert(tmp > 0 && tmp <= INT_MAX);
    *size = (int)tmp;
    return ADLB_SUCCESS;
  }

  // Base size on number of other servers
  // Can be zero.
  *size = (servers - 1) * 4;
  if (*size >= XLB_SYNC_RECV_DEFAULT_MAX)
  {
    *size = XLB_SYNC_RECV_DEFAULT_MAX;
  }

  return ADLB_SUCCESS;
}

adlb_code
xlb_sync(int target)
{
  char hdr_storage[PACKED_SYNC_SIZE];
  struct packed_sync* hdr = (struct packed_sync*) hdr_storage;
#ifndef NDEBUG
  // Avoid send uninitialized bytes for memory checking tools
  memset(hdr, 0, PACKED_SYNC_SIZE);
#endif
  hdr->mode = ADLB_SYNC_REQUEST;
  return xlb_sync2(target, hdr, NULL);
}

/*
  Core function for sending a sync message.  Sends a pre-assembled message.
  response: response code from target process, meaningful to some sync
            types.  Only set if that sync type must be accepted by target.
            Can be NULL to ignore.

   While attempting a sync, one of three things may happen:
   1) The target responds.  It either accepts or rejects the sync
      request.  If it rejects, this process retries
   2) Another server interrupts this process with a sync request.
      This process either accepts and serves the request; stores the
      request in xlb_pending_syncs to process later, or rejects it
   3) The master server tells this process to shut down
 */
static adlb_code
xlb_sync2(int target, const struct packed_sync* hdr, int* response)
{
  TRACE_START;
  DEBUG("[%i] xlb_sync() target: %i sync_mode: %s", xlb_s.layout.rank,
        target, xlb_sync_mode_name[hdr->mode]);
  adlb_code rc = ADLB_SUCCESS;

  // Track sent sync message and response
  MPI_Request isend_request, accept_request;
  // Response from target if needed
  int accept_response;
  bool accept_required = sync_accept_required(hdr->mode);

  int flag1 = 0, flag2 = 0;

  assert(!xlb_server_sync_in_progress);
  xlb_server_sync_in_progress = true;

  // Flag completion of sync, either successfully or aborting if
  // shutting down
  bool done = false;
  // If one of the requests is still pending
  bool requests_pending = false;

  if (!xlb_server_shutting_down ||
      hdr->mode == ADLB_SYNC_SHUTDOWN)
  {

    if (xlb_s.perfc_enabled)
    {
      assert(hdr->mode >= 0 && hdr->mode < ADLB_SYNC_ENUM_COUNT);
      xlb_sync_perf_counters[hdr->mode].sent++;
    }

    if (accept_required)
    {
      IRECV2(&accept_response, 1, MPI_INT, target, ADLB_TAG_SYNC_RESPONSE,
             &accept_request);
    }

    /*
     * Send initial request.
     *
     * Use non-blocking send to eliminate chance of blocking here if
     * receiver's buffers are full, so that we can serve other sync
     * requests no matter what.
     *
     * TODO: get other callers to pass in ownership of sync header buffer,
     *       so we can return even if receiver's buffers are full, e.g. if
     *       sync target is extremely congested
     */
    ISEND(hdr, (int)PACKED_SYNC_SIZE, MPI_BYTE, target,
          ADLB_TAG_SYNC_REQUEST, &isend_request);
    requests_pending = true;

    DEBUG("server_sync: [%d] waiting for sync response from %d",
                          xlb_s.layout.rank, target);
  }
  else
  {
    // Check that we're not due to shut down because of a previously
    // received shutdown message before going into sync loop
    done = true;
    rc = ADLB_SHUTDOWN;
    DEBUG("server_sync: [%d] shutting down before sync to %d",
                          xlb_s.layout.rank, target);
  }

  struct sync_delay delay;

  if (XLB_DEBUG_SYNC_DELAY) {
    delay_check_init(&delay);
  }

  /*
   * Must loop until Isend completes at a minimum.
   * We don't just block on it because we want to service any incoming
   * requests.
   * If we need an accept response, must also loop until we receive
   * it from the target.
   */
  while (!done)
  {
    if (XLB_DEBUG_SYNC_DELAY) {
      delay_check(&delay, target, hdr);
    }

    TRACE("xlb_sync: loop");

    if (accept_required)
    {
      // Check for response from target
      MPI_TEST(&accept_request, &flag1);

      if (flag1)
      {
        int tmp_flag;
        MPI_TEST(&isend_request, &tmp_flag);

        rc = msg_from_target(target, accept_response);
        ADLB_CHECK(rc);

        if (response != NULL)
          *response = accept_response;
        requests_pending = false; // ISend must have completed too
        done = true;
        break;
      }
    }
    else
    {
      // just check that send went through
      MPI_TEST(&isend_request, &flag1);

      if (flag1)
      {
        requests_pending = false;
        done = true;
        break;
      }
    }

    int other_rank = -1;
    rc = xlb_check_sync_msgs(&other_rank);
    ADLB_CHECK(rc);
    if (rc == ADLB_SUCCESS)
    {
      assert(other_rank != -1);
      bool shutting_down;
      rc = msg_from_other_server(other_rank, &shutting_down);
      ADLB_CHECK(rc);

      if (shutting_down)
      {
        // This server needs to exit sync loop even if it hasn't
        // completed to avoid getting blocked on another server that
        // has already shut down
        DEBUG("server_sync: [%d] cancelled by shutdown!", xlb_s.layout.rank);

        rc = cancel_sync(hdr->mode, target);
        ADLB_CHECK(rc);

        done = true;
        rc = ADLB_SHUTDOWN;
      }

      flag2 = true;
    }

    if (!flag1 && !flag2)
    {
      // TODO: generally we don't want to wait longer than needed for
      //  a response, and we should get a response pretty quickly.
      // Maybe just go to no backoffs...
      // xlb_backoff_sync();
    }
  }

  if (requests_pending)
  {
    CANCEL(&isend_request);
    if (accept_required)
    {
      CANCEL(&accept_request);
    }
  }

  DEBUG("server_sync: [%d] sync with %d successful", xlb_s.layout.rank, target);
  xlb_server_sync_in_progress = false;
  TRACE_END;

  return rc;
}

/*
  Tell target to shut down
 */
adlb_code xlb_sync_shutdown(int target)
{
  char hdr_storage[PACKED_SYNC_SIZE]; // Temporary stack storage for struct
  struct packed_sync *hdr = (struct packed_sync *)hdr_storage;
#ifndef NDEBUG
  // Avoid send uninitialized bytes for memory checking tools
  memset(hdr, 0, PACKED_SYNC_SIZE);
#endif
  hdr->mode = ADLB_SYNC_SHUTDOWN;

  adlb_code rc = xlb_sync2(target, hdr, NULL);
  ADLB_CHECK(rc);

  return ADLB_SUCCESS;
}

static adlb_code
send_subscribe_sync(adlb_sync_mode mode,
      int target, adlb_datum_id id, adlb_subscript sub)
{
  char req_storage[PACKED_SYNC_SIZE]; // Temporary stack storage for struct
  struct packed_sync *req = (struct packed_sync *)req_storage;
  req->mode = mode;
  req->subscribe.id = id;
  req->subscribe.subscript_len = sub.length;

  bool inlined_subscript;
  if (sub.length <= SYNC_DATA_SIZE)
  {
    if (sub.length > 0)
    {
      memcpy(req->sync_data, sub.key, sub.length);
    }
    inlined_subscript = true;
  }
  else
  {
    inlined_subscript = false;
  }

  // Send sync message without waiting for response
  adlb_code rc = xlb_sync2(target, req, NULL);
  ADLB_CHECK(rc);

  if (!inlined_subscript)
  {
    // send subscript separately with special tag
    // note: could block here, although large subscripts
    // are generally quite rare.
    SEND(sub.key, (int)sub.length, MPI_BYTE, target, ADLB_TAG_SYNC_SUB);
  }

  return ADLB_SUCCESS;
}

adlb_code
xlb_sync_subscribe(int target, adlb_datum_id id, adlb_subscript sub,
                   bool *subscribed)
{
  adlb_code ac = send_subscribe_sync(ADLB_SYNC_SUBSCRIBE, target, id, sub);
  ADLB_CHECK(ac);

  // We will get notification later
  *subscribed = true;
  return ADLB_SUCCESS;
}

adlb_code
xlb_sync_notify(int target, adlb_datum_id id, adlb_subscript sub)
{
  // Send notification
  adlb_code ac = send_subscribe_sync(ADLB_SYNC_NOTIFY, target, id, sub);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

adlb_code xlb_sync_steal_probe(int target)
{
  char hdr_storage[PACKED_SYNC_SIZE];
  struct packed_sync *hdr = (struct packed_sync *)hdr_storage;
#ifndef NDEBUG
  // Avoid send uninitialized bytes for memory checking tools
  memset(hdr, 0, PACKED_SYNC_SIZE);
#endif
  hdr->mode = ADLB_SYNC_STEAL_PROBE;

  return xlb_sync2(target, hdr, NULL);
}

adlb_code
xlb_sync_steal_probe_resp(int target, const int *work_counts,
                          int size)
{
  char hdr_storage[PACKED_SYNC_SIZE];
  struct packed_sync *hdr = (struct packed_sync *)hdr_storage;
#ifndef NDEBUG
  // Avoid send uninitialized bytes for memory checking tools
  memset(hdr, 0, PACKED_SYNC_SIZE);
#endif
  hdr->mode = ADLB_SYNC_STEAL_PROBE_RESP;

  // Fill counts
  memcpy(hdr->sync_data, work_counts,
         sizeof(work_counts[0]) * (size_t)size);

  return xlb_sync2(target, hdr, NULL);
}

adlb_code
xlb_sync_steal(int target, const int *work_counts, int size,
               int max_memory, int *response)
{
  char req_storage[PACKED_SYNC_SIZE]; // Temporary stack storage for struct
  struct packed_sync* req = (struct packed_sync *)req_storage;
  req->mode = ADLB_SYNC_STEAL;
  req->steal.max_memory = max_memory;
  req->steal.idle_check_attempt = xlb_idle_check_attempt;

  // Include work types in sync data field
  memcpy(req->sync_data, work_counts,
         sizeof(work_counts[0] * (size_t)xlb_s.types_size));

  return xlb_sync2(target, req, response);
}

adlb_code
xlb_sync_refcount(int target, adlb_datum_id id,
                  adlb_refc change, bool wait)
{
  char hdr_storage[PACKED_SYNC_SIZE];
  struct packed_sync *hdr = (struct packed_sync *)hdr_storage;
#ifndef NDEBUG
  // Avoid send uninitialized bytes for memory checking tools
  memset(hdr, 0, PACKED_SYNC_SIZE);
#endif
  hdr->mode = wait ? ADLB_SYNC_REFCOUNT_WAIT : ADLB_SYNC_REFCOUNT;
  hdr->incr.id = id;
  hdr->incr.change = change;
  return xlb_sync2(target, hdr, NULL);
}

/**
   @return adlb_code
 */
static inline adlb_code
msg_from_target(int target, int response)
{
  TRACE_START;
  // Accepted
  DEBUG("server_sync: [%d] sync response from %d: %d",
         xlb_s.layout.rank, target, response);
  TRACE_END
  return ADLB_SUCCESS;
}

/*
  Return true if we need to wait for the sync to be accepted
  before returning to the caller.
 */
static inline bool sync_accept_required(adlb_sync_mode mode)
{
  if (mode == ADLB_SYNC_REQUEST ||
      mode == ADLB_SYNC_STEAL ||
      mode == ADLB_SYNC_REFCOUNT_WAIT)
  {
    return true;
  }
  else
  {
    return false;
  }
}

static adlb_code msg_from_other_server(int other_server, bool *shutting_down)
{
  TRACE_START;
  adlb_code code;

  *shutting_down = false;

  xlb_sync_recv *sync_msg = xlb_next_sync_msg();
  struct packed_sync *other_hdr = sync_msg->buf;

  /* Serve another server
   * We need to avoid the case of circular deadlock, e.g. where A is waiting
   * to serve B, which is waiting to serve C, which is waiting to serve A,
   * so don't serve higher ranked servers until we've finished our
   * sync request. We choose this ordering because the master server is
   * somewhat more likely to be busy and should be unblocked. */
  if (other_server < xlb_s.layout.rank)
  {
    // accept incoming sync
    DEBUG("server_sync: [%d] interrupted by incoming sync request from %d",
                        xlb_s.layout.rank, other_server);

    code = xlb_accept_sync(other_server, other_hdr, true);
    ADLB_CHECK(code);

    if (code == ADLB_SHUTDOWN)
    {
      *shutting_down = true;
    }
  }
  else
  {
    // Don't handle right away, defer it
    code = enqueue_pending(DEFERRED_SYNC, other_server, other_hdr, NULL);
    ADLB_CHECK(code);
  }

  code = xlb_sync_msg_done();
  ADLB_CHECK(code);
  TRACE_END;
  return ADLB_SUCCESS;
}

static xlb_sync_recv *xlb_next_sync_msg(void)
{
  xlb_sync_recv *head = &xlb_sync_recvs[xlb_sync_recv_head];
  return head;
}

/*
 * After done processing msg from xlb_next_sync_msg(), mark it
 * as done and reuse sync buffer it up to accept another sync.
 */
static adlb_code xlb_sync_msg_done(void)
{
  xlb_sync_recv *head = &xlb_sync_recvs[xlb_sync_recv_head];

  IRECV2(head->buf, (int)PACKED_SYNC_SIZE, MPI_BYTE,
           MPI_ANY_SOURCE, ADLB_TAG_SYNC_REQUEST, &head->req);

  xlb_sync_recv_head = (xlb_sync_recv_head + 1) % xlb_sync_recv_size;
  return ADLB_SUCCESS;
}

/*
  Handle sync in the case where this server isn't in a sync loop
 */
adlb_code xlb_handle_next_sync_msg(int caller)
{
  MPE_LOG(xlb_mpe_svr_sync_start);

  xlb_sync_recv *sync_msg = xlb_next_sync_msg();

  // Copy header so we can release before handling
  char hdr_storage[PACKED_SYNC_SIZE];
  struct packed_sync *hdr = (struct packed_sync *)hdr_storage;
  memcpy(hdr, sync_msg->buf, PACKED_SYNC_SIZE);

  adlb_code rc = xlb_sync_msg_done();
  ADLB_CHECK(rc);

  rc = xlb_accept_sync(caller, hdr, false);
  MPE_LOG(xlb_mpe_svr_sync_end);
  return rc;
}

/*
  One we are ready to accept sync, do whatever processing needed to service
  hdr: header data.  Must copy to take ownership
  defer_svr_ops: true if we should defer any potential server->server ops
 */
adlb_code xlb_accept_sync(int rank, const struct packed_sync *hdr,
                          bool defer_svr_ops)
{
  adlb_sync_mode mode = hdr->mode;
  adlb_code code = ADLB_ERROR;

  DEBUG("[%i] xlb_accept_sync() from: %i sync_mode: %s", xlb_s.layout.rank,
        rank, xlb_sync_mode_name[mode]);

  if (xlb_s.perfc_enabled)
  {
    assert(mode >= 0 && mode < ADLB_SYNC_ENUM_COUNT);
    xlb_sync_perf_counters[mode].accepted++;
  }

  if (sync_accept_required(mode))
  {
    // Notify the waiting caller
    const int accepted_response = 1;
    // This shouldn't block, since sender should have posted buffer
    SEND(&accepted_response, 1, MPI_INT, rank, ADLB_TAG_SYNC_RESPONSE);
  }

  switch (mode)
  {
    case ADLB_SYNC_REQUEST:
      code = xlb_serve_server(rank);
      break;

    case ADLB_SYNC_STEAL_PROBE:
      if (defer_svr_ops)
      {
        code = enqueue_pending(DEFERRED_STEAL_PROBE, rank, NULL, NULL);
      }
      else
      {
        code = xlb_handle_steal_probe(rank);
      }
      break;

    case ADLB_SYNC_STEAL_PROBE_RESP:
      if (defer_svr_ops)
      {
        code = enqueue_pending(DEFERRED_STEAL_PROBE_RESP, rank, hdr, NULL);
      }
      else
      {
        // Steal from other rank if appropriate
        code = xlb_handle_steal_probe_resp(rank, hdr);
      }
      break;

    case ADLB_SYNC_STEAL:
      // Respond to steal
      code = xlb_handle_steal(rank, &hdr->steal, (int*)hdr->sync_data);
      break;

    case ADLB_SYNC_REFCOUNT:
    case ADLB_SYNC_REFCOUNT_WAIT:
      /*
        We defer handling of server->server refcounts to avoid potential
        deadlocks if the refcount decrement triggers a cycle of reference
        count decrements between servers and a deadlock.  Deferring
        processing also has the benefit of giving the fastest possible
        response to the other servers.  One downside is that we can't pass
        errors all the way back to the caller - we will simply report them
        and continue.

        Rules about safety of deferring refcounts:
         -> refcount increments - need to apply increment before
             processing any operation that could decrement refcount
         -> read refcount decrements - safe to defer indefinitely,
              but delays freeing memory
         -> write refcount decrements - safe to defer indefinitely,
              but will delay notifications
       */

      if (defer_svr_ops)
      {
        DEBUG("Defer refcount for <%"PRId64">", hdr->incr.id);
        code = enqueue_pending(ACCEPTED_REFC, rank, hdr, NULL);
        ADLB_CHECK(code);
      }
      else
      {
        DEBUG("Update refcount now for <%"PRId64"> r: %i w: %i",
              hdr->incr.id, hdr->incr.change.read_refcount,
              hdr->incr.change.write_refcount);
        adlb_data_code dc = xlb_incr_refc_local(hdr->incr.id,
                                    hdr->incr.change, true);
        ADLB_CHECK_MSG(dc == ADLB_DATA_SUCCESS, "Unexpected error in refcount");
        code = ADLB_SUCCESS;
      }
      // Then we're done - already sent sync response to caller
      break;

    case ADLB_SYNC_SUBSCRIBE:
      code = xlb_handle_subscribe_sync(rank, hdr, defer_svr_ops);
      break;

    case ADLB_SYNC_NOTIFY:
      if (defer_svr_ops)
      {
        DEBUG("Defer notification for <%"PRId64">", hdr->subscribe.id);
        code = enqueue_deferred_notify(rank, hdr);
      }
      else
      {
        DEBUG("Handle notification now for <%"PRId64">", hdr->subscribe.id);
        code = xlb_handle_notify_sync(rank, &hdr->subscribe, hdr->sync_data,
                                      NULL);
      }
      break;

    case ADLB_SYNC_SHUTDOWN:
      DEBUG("[%d] received shutdown!", xlb_s.layout.rank);

      xlb_server_shutting_down = true;

      code = ADLB_SHUTDOWN;
      break;

    default:
      ERR_PRINTF("Invalid sync mode: %d\n", mode);
      code = ADLB_ERROR;
      break;
  }
  return code;
}

adlb_code xlb_handle_pending_sync(xlb_pending_kind kind,
      int rank, struct packed_sync *hdr, void *extra_data)
{
  adlb_code rc;
  adlb_data_code dc;
  DEBUG("server_sync: [%d] handling deferred sync from %d",
        xlb_s.layout.rank, rank);
  switch (kind)
  {
    case DEFERRED_SYNC:
      rc = xlb_accept_sync(rank, hdr, false);
      ADLB_CHECK(rc);
      break;
    case ACCEPTED_REFC:
      dc = xlb_incr_refc_local(hdr->incr.id, hdr->incr.change, true);
      ADLB_CHECK_MSG(dc == ADLB_DATA_SUCCESS, "unexpected error in refcount");
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
    case DEFERRED_STEAL_PROBE:
      rc = xlb_handle_steal_probe(rank);
      ADLB_CHECK(rc);
      break;
    case DEFERRED_STEAL_PROBE_RESP:
      rc = xlb_handle_steal_probe_resp(rank, hdr);
      ADLB_CHECK(rc);
      break;
    default:
      ERR_PRINTF("Unexpected pending sync kind %i\n", kind);
      return ADLB_ERROR;
  }

  // Clean up memory
  if (hdr != NULL)
    free(hdr);

  return ADLB_SUCCESS;
}

static adlb_code xlb_handle_subscribe_sync(int rank,
        const struct packed_sync *hdr, bool defer_svr_ops)
{
  adlb_data_code dc;
  adlb_code ac;

  MPI_Status status;

  const struct packed_subscribe_sync *sub_hdr = &hdr->subscribe;
  const void *sync_data = hdr->sync_data;

  void *malloced_subscript = NULL;
  adlb_subscript sub;
  sub.length = sub_hdr->subscript_len;

  if (sub_hdr->subscript_len == 0)
  {
    sub.key = NULL;
  }
  else if (sub_hdr->subscript_len <= SYNC_DATA_SIZE)
  {
    // subscript small enough to store inline
    sub.key = sync_data;
  }
  else
  {
    assert(sub_hdr->subscript_len <= ADLB_DATA_SUBSCRIPT_MAX);
    malloced_subscript = malloc(sub_hdr->subscript_len);
    ADLB_CHECK_MALLOC(malloced_subscript);

    // receive subscript as separate message with special tag
    RECV(malloced_subscript, (int)sub_hdr->subscript_len, MPI_BYTE,
         rank, ADLB_TAG_SYNC_SUB);
    sub.key = malloced_subscript;
  }

  // call data module to subscribe
  bool subscribed;
  dc = xlb_data_subscribe(sub_hdr->id, sub, rank, 0, &subscribed);
  ADLB_DATA_CHECK(dc);

  if (!subscribed)
  {
    // Is ready, need to get notification back to caller
    if (defer_svr_ops)
    {
      // Enqueue it for later sending
      ac = enqueue_pending(UNSENT_NOTIFY, rank, hdr,
                             malloced_subscript);
      ADLB_CHECK(ac);
      malloced_subscript = NULL; // Gave ownership to pending list
    } else {
      // Notify right away.
      // We avoid deadlock since caller doesn't wait for accept
      ac = xlb_sync_notify(rank, sub_hdr->id, sub);
      ADLB_CHECK(ac);
    }
  }

  if (malloced_subscript != NULL)
  {
    free(malloced_subscript);
  }
  return ADLB_SUCCESS;
}

/*
 * req_hdr: header from the subscribe request
 * malloc_subscript: memory for longer subscripts
 */
adlb_code xlb_send_unsent_notify(int rank,
        const struct packed_sync *req_hdr, void *malloced_subscript)
{
  adlb_subscript sub;
  sub.length = req_hdr->subscribe.subscript_len;

  if (sub.length == 0)
  {
    sub.key = NULL;
  }
  else if (sub.length <= SYNC_DATA_SIZE)
  {
    // subscript was stored inline
    sub.key = req_hdr->sync_data;
  }
  else
  {
    assert(sub.length <= ADLB_DATA_SUBSCRIPT_MAX);
    assert(malloced_subscript != NULL);
    sub.key = malloced_subscript;
  }

  adlb_code ac = xlb_sync_notify(rank, req_hdr->subscribe.id, sub);
  ADLB_CHECK(ac);

  if (malloced_subscript != NULL)
  {
    free(malloced_subscript);
  }

  return ADLB_SUCCESS;
}

/*
 * Enqueue a notification for later processing.
 * This will receive any additional messages sent by the caller.
 */
static adlb_code enqueue_deferred_notify(int rank,
      const struct packed_sync *hdr)
{
  MPI_Status status;

  void *malloced_subscript = NULL;
  size_t sub_length = hdr->subscribe.subscript_len;

  // Get subscript now to avoid having unreceived message sitting around
  if (sub_length > SYNC_DATA_SIZE)
  {
    assert(sub_length <= ADLB_DATA_SUBSCRIPT_MAX);
    malloced_subscript = malloc(sub_length);
    ADLB_CHECK_MALLOC(malloced_subscript);

    // receive subscript as separate message with special tag
    RECV(malloced_subscript, (int)sub_length, MPI_BYTE,
         rank, ADLB_TAG_SYNC_SUB);
  }

  adlb_code rc = enqueue_pending(DEFERRED_NOTIFY, rank, hdr,
                                  malloced_subscript);
  ADLB_CHECK(rc);

  return ADLB_SUCCESS;
}

/*
 * Handle a notification, either a deferred one or one with a waiting
 * caller.
 * extra_data: if not NULL and extra data is needed, assume we should
 *            receive it from caller
 */
adlb_code xlb_handle_notify_sync(int rank,
        const struct packed_subscribe_sync *hdr, const void *sync_data,
        void *extra_data)
{
  MPI_Status status;

  void *malloced_subscript = NULL;
  adlb_subscript sub;
  sub.length = hdr->subscript_len;

  if (hdr->subscript_len == 0)
  {
    sub.key = NULL;
  }
  else if (hdr->subscript_len <= SYNC_DATA_SIZE)
  {
    // subscript small enough to store inline
    sub.key = sync_data;
  }
  else if (extra_data != NULL)
  {
    sub.key = malloced_subscript = extra_data;
  }
  else
  {
    assert(hdr->subscript_len <= ADLB_DATA_SUBSCRIPT_MAX);
    malloced_subscript = malloc(hdr->subscript_len);
    ADLB_CHECK_MALLOC(malloced_subscript);

    // receive subscript as separate message with special tag
    RECV(malloced_subscript, (int)hdr->subscript_len, MPI_BYTE,
         rank, ADLB_TAG_SYNC_SUB);
    sub.key = malloced_subscript;
  }

  xlb_engine_code tc;
  // process notification
  if (adlb_has_sub(sub))
  {
    tc = xlb_engine_sub_close(hdr->id, sub, true, &xlb_server_ready_work);
    ADLB_ENGINE_CHECK(tc);
  }
  else
  {
    tc = xlb_engine_close(hdr->id, true, &xlb_server_ready_work);
    ADLB_ENGINE_CHECK(tc);
  }

  if (malloced_subscript != NULL)
  {
    free(malloced_subscript);
  }
  return ADLB_SUCCESS;

}

/*
  Add pending sync
  hdr: sync header.  This function will make a copy of it
  returns: ADLB_SUCCESS, or ADLB_ERROR on unexpected error
 */
static adlb_code enqueue_pending(xlb_pending_kind kind, int rank,
                     const struct packed_sync *hdr, void *extra_data)
{
  assert(xlb_pending_sync_count <= xlb_pending_sync_size);
  if (xlb_pending_sync_count == xlb_pending_sync_size)
  {
    xlb_pending_sync_size *= 2;
    DEBUG("Resizing to accommodate %i pending", xlb_pending_sync_size);
    xlb_pending_syncs = realloc(xlb_pending_syncs,
                      sizeof(xlb_pending_syncs[0]) * (size_t)xlb_pending_sync_size);
    ADLB_CHECK_MALLOC(xlb_pending_syncs);
    /* Entries are in: [head..count) ++ [0..head)
     * Copy [0..head) to [count..count+head) to account for new size
     * End result is all entries in [head..head+count] */
    if (xlb_pending_sync_head != 0)
    {
      memcpy(&xlb_pending_syncs[xlb_pending_sync_count], xlb_pending_syncs,
            sizeof(xlb_pending_syncs[0]) * (size_t)xlb_pending_sync_head);
    }
  }

  int tail = (xlb_pending_sync_head + xlb_pending_sync_count)
             % xlb_pending_sync_size;
  xlb_pending *entry = &xlb_pending_syncs[tail];
  entry->kind = kind;
  entry->rank = rank;
  entry->extra_data = extra_data;
  if (hdr == NULL)
  {
    entry->hdr = NULL;
  }
  else
  {
    entry->hdr = malloc(PACKED_SYNC_SIZE);
    ADLB_CHECK_MALLOC(entry->hdr);
    memcpy(entry->hdr, hdr, PACKED_SYNC_SIZE);
  }
  xlb_pending_sync_count++;

  if (xlb_is_pending_notif(kind, hdr))
  {
    xlb_pending_notif_count++;
  }
  return ADLB_SUCCESS;
}

/*
  Free memory for a pending sync (not including actual pending struct)
 */
static void free_pending_sync(xlb_pending *pending)
{
  // Assume that extra_data is a malloced pointer
  if (pending->extra_data)
    free(pending->extra_data);

  if (pending->hdr != NULL)
    free(pending->hdr);
}


// Shrink to half of previous size
adlb_code xlb_pending_shrink(void)
{
  // Short names for readability
  const int new_size = xlb_pending_sync_size / 2;
  const int old_size = xlb_pending_sync_size;
  const int count = xlb_pending_sync_count;
  const int head = xlb_pending_sync_head;
  assert(head <= old_size);
  assert(count <= new_size);
  /*
    Need to pack into smaller new array
    Entries are in [head..head+count).
  */
  if (head + count > new_size)
  {
    if (head + count > old_size)
    {
      /*
        If wrapped around, we have [head..old_size) ++ [0..nwrapped)

        Move [0..nwrapped) to [count-nwrapped..count) and
             [head..old_size) to [0..count-nwrapped)
        Destination will be unused bc. nwrapped <= head bc. in this case
        we're using less than half of the array.
      */
      int nwrapped = (head + count) % old_size;
      // Use memmove since might overlap
      memmove(&xlb_pending_syncs[count-nwrapped], xlb_pending_syncs,
              sizeof(xlb_pending_syncs[0]) * (size_t)nwrapped);
      memcpy(xlb_pending_syncs, &xlb_pending_syncs[head],
              sizeof(xlb_pending_syncs[0]) * (size_t)(count - nwrapped));
      xlb_pending_sync_head = 0;
    }
    else
    {
      // No wrapping, but past end of new array.
      // Just move to front with memmove since might overlap
      memmove(xlb_pending_syncs, &xlb_pending_syncs[head],
              sizeof(xlb_pending_syncs[0]) * (size_t)count);
      xlb_pending_sync_head = 0;
    }
  }

  xlb_pending_sync_size = new_size;
  xlb_pending_syncs = realloc(xlb_pending_syncs,
    sizeof(xlb_pending_syncs[0]) * (size_t)xlb_pending_sync_size);

  // realloc shouldn't really fail when shrinking
  assert(xlb_pending_syncs != NULL);

  return ADLB_SUCCESS;
}

static inline adlb_code
cancel_sync(adlb_sync_mode mode, int sync_target)
{
  TRACE_START;
  DEBUG("server_sync: [%d] cancelled by shutdown!", xlb_s.layout.rank);

  if (mode == ADLB_SYNC_REQUEST)
  {
    /* We're not going to follow up the sync request with an actual
     * request.  To avoid the target getting stuck waiting for
     * something, we send them a dummy piece of work. */
    SEND_TAG(sync_target, ADLB_TAG_DO_NOTHING);
  }
  else if (mode == ADLB_SYNC_STEAL_PROBE ||
           mode == ADLB_SYNC_STEAL_PROBE_RESP ||
           mode == ADLB_SYNC_STEAL ||
           mode == ADLB_SYNC_REFCOUNT ||
           mode == ADLB_SYNC_SUBSCRIBE ||
           mode == ADLB_SYNC_NOTIFY ||
           mode == ADLB_SYNC_SHUTDOWN)
  {
    // Don't do anything, the sync initiator doesn't block on any
    // follow-up response from this server after it's accepted
  }
  else
  {
    ERR_PRINTF("WARNING: Unexpected sync mode %i\n", (int)mode);
    return ADLB_SUCCESS;
  }

  TRACE_END;
  return ADLB_SUCCESS;
}

static inline void delay_check_init(struct sync_delay *state) {
  state->attempts = 0;
  state->start_time = xlb_approx_time();
  state->last_check_time = state->start_time;
}

static inline void delay_check(struct sync_delay *state,
              int target, const struct packed_sync *hdr) {
  state->attempts++;

  if (state->attempts % 1000 == 0 )
  {
    double now = MPI_Wtime();
    if (now - state->last_check_time > XLB_DEBUG_SYNC_DELAY_LIMIT)
    {
      fprintf(stderr, "[%d] has been waiting %.2lf for %i\
                sync mode %s\n",
              xlb_s.layout.rank, now - state->start_time, target,
              xlb_sync_mode_name[hdr->mode]);
      state->last_check_time = now;
    }
  }
}
