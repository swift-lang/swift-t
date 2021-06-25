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
 * sync.h
 *
 *  Created on: Aug 20, 2012
 *      Authors: wozniak, armstrong
 *
 * The sync module manages server-to-server synchronization and
 * communication.  When servers are communicating with each other,
 * it is important to a) avoid deadlocks where a cycle of servers is
 * waiting on each other for a response, b) avoid starvation, where
 * servers are unable to progress waiting for other processes,
 * particularly if we get chains of servers waiting for each other.
 *
 * Several techniques are implemented in this module.
 *
 * - Tie-breaking algorithms to avoid deadlocks if servers must perform
 *   blocking synchronous RPCs on other servers
 * - Asynchronous RPCs, where key server-to-server operations such
 *   as work-stealing probes are sent immediately, with the respond
 *   handled later.
 * - Use of small, fixed-size sync messages to allow quick sending and
 *   receiving into fixed-size buffers in the caller.
 * - Asynchronous request handling where servers will continue to serve
 *   requests from other servers, to avoid starvation propagating.
 * - Request buffering, where we accumulate pending requests to be
 *   processed later.
 */

#pragma once

#include "adlb-defs.h"
#include "messaging.h"

adlb_code xlb_sync_init(void);
void xlb_sync_finalize(void);

void xlb_print_sync_counters(void);

/**
   Avoids server-to-server deadlocks by synchronizing with target
   server rank.  An MPI deadlock may be caused by two processes
   sending an RPC to each other simultaneously.  The sync
   functionality avoids this by doing special MPI probes and
   breaking ties to allow the higher rank process to always win

   This is used for server-to-server RPCs that do not have special
   support support in the sync module.

   After returning from this, this calling process may issue one RPC
   on the target process

   This function may add pending sync requests from other servers to
   the xlb_pending_syncs buffer, which will need to be serviced.
 */
adlb_code xlb_sync(int target);

/*
  Tell target to shut down
 */
adlb_code xlb_sync_shutdown(int target);

/*
  Subscribe to a datum on another server
 */
adlb_code xlb_sync_subscribe(int target, adlb_datum_id id,
                             adlb_subscript sub, bool *subscribed);

adlb_code xlb_sync_notify(int target, adlb_datum_id id,
                          adlb_subscript sub);

adlb_code xlb_send_unsent_notify(int rank,
                                 const struct packed_sync *req_hdr,
                                 void *malloced_subscript);

/*
  Send a steal probe
 */
adlb_code xlb_sync_steal_probe(int target);

/*
  Send a steal probe response
  work_counts: counts of request types
  size: number of entries in work_counts array
 */
adlb_code xlb_sync_steal_probe_resp(int target,
                                    const int *work_counts,
                                    int size);

/*
  Send a request to initiate a steal, to be followed up by actual steal
  communication once accepted.
  work_counts: counts of request types
  size: number of entries in work_counts array
  max_memory: max additional memory to accept
  response: logical, true if we will receive work
 */
adlb_code xlb_sync_steal(int target, const int *work_counts,
                         int size, int max_memory, int *response);

/*
  Send a refcount operation to another server.

  If wait is true, wait for response.
  Otherwise, return as soon as it is sent.
 */
adlb_code
xlb_sync_refcount(int target, adlb_datum_id id, adlb_refc change,
                  bool wait);

typedef struct {
  MPI_Request req;
  struct packed_sync *buf;
} xlb_sync_recv;

typedef enum {
  DEFERRED_SYNC, // Have not yet accepted
  ACCEPTED_REFC,   // Have accepted but need to do refcount
  DEFERRED_NOTIFY, // Have accepted but need to process notify
  UNSENT_NOTIFY, // Need to notify other server
  DEFERRED_STEAL_PROBE, // Have accepted but need to respond
  DEFERRED_STEAL_PROBE_RESP, // Have response, but have not acted
} xlb_pending_kind;

typedef struct {
  xlb_pending_kind kind;
  int rank;
  struct packed_sync *hdr; // Header to be freed later
  void *extra_data; // Extra data if needed for header type
} xlb_pending;

static inline adlb_code xlb_dequeue_pending(xlb_pending_kind* kind,
                                            int* rank,
                                            struct packed_sync** hdr,
                                            void** extra_data);

/*
  Handle a dequeued pending sync.  Frees all memory associated
  with it, so data pointed to will become invalid for caller.

  This should not be called if already within a sync loop.
 */
adlb_code xlb_handle_pending_sync(xlb_pending_kind kind,
                                  int rank,
                                  struct packed_sync* hdr,
                                  void* extra_data);

/*
 * return: true if we have pending notification work that could result
 *         in tasks being released
 */
static inline bool xlb_have_pending_notifs(void);

static inline bool xlb_is_pending_notif(xlb_pending_kind kind,
                                        const struct packed_sync* hdr);

/*
 * Check if there incoming sync request messages to process.
 * return: ADLB_SUCCESS if message present, ADLB_NOTHING if no message,
 *         ADLB_ERROR on error
 */
static inline adlb_code xlb_check_sync_msgs(int *caller);

/*
 * Handle sync message if check returns true
 */
adlb_code xlb_handle_next_sync_msg(int caller);

/*
 * Accept and handle sync.
 * return: ADLB_ERROR on error, ADLB_SHUTDOWN if got shutdown command,
 *         ADLB_SUCCESS otherwise
 */
adlb_code xlb_accept_sync(int rank, const struct packed_sync *hdr,
                          bool defer_svr_ops);

adlb_code xlb_handle_notify_sync(int rank,
                                 const struct packed_subscribe_sync *hdr,
                                 const void* sync_data,
                                 void* extra_data);

// Inline functions to make it quick to check for pending sync requests

extern xlb_sync_recv* xlb_sync_recvs;
extern int xlb_sync_recv_head;
extern int xlb_sync_recv_size;

static inline adlb_code xlb_check_sync_msgs(int *caller)
{
  int flag = 0;
  MPI_Status status;
  if (xlb_sync_recv_size <= 0)
  {
    return ADLB_NOTHING;
  }

  xlb_sync_recv* head = &xlb_sync_recvs[xlb_sync_recv_head];
  MPI_TEST2(&head->req, &flag, &status);

  if (flag)
  {
    *caller = status.MPI_SOURCE;
    return ADLB_SUCCESS;
  }
  else
  {
    return ADLB_NOTHING;
  }
}

// Info about pending sync requests: where sync request has been received
// but we haven't responded yet.
extern xlb_pending* xlb_pending_syncs; // Array for ring buffer
extern int xlb_pending_sync_head; // Head of ring buffer
extern int xlb_pending_sync_count; // Valid entries in array
extern int xlb_pending_sync_size; // Malloced size
extern int xlb_pending_notif_count; // Entries that are notifications

// Initial size of pending sync array
#define PENDING_SYNC_INIT_SIZE 1024

adlb_code xlb_pending_shrink(void);

/*
  Remove the pending sync from the list (FIFO order)
  returns ADLB_NOTHING if not found, otherwise sets arguments
  Caller is responsible for freeing hdr
 */
__attribute__((always_inline))
static inline adlb_code
xlb_dequeue_pending(xlb_pending_kind* kind,
                    int* rank,
                    struct packed_sync** hdr,
                    void** extra_data)
{
  if (xlb_pending_sync_count == 0)
    return ADLB_NOTHING;

  xlb_pending* pending = &xlb_pending_syncs[xlb_pending_sync_head];

  *kind = pending->kind;
  *rank = pending->rank;
  *hdr = pending->hdr;
  *extra_data = pending->extra_data;

  xlb_pending_sync_count--;
  xlb_pending_sync_head = (xlb_pending_sync_head + 1) %
                           xlb_pending_sync_size;

  if (xlb_is_pending_notif(pending->kind, pending->hdr))
  {
    xlb_pending_notif_count--;
  }

  TRACE("POP PENDING: %d left", xlb_pending_sync_count);

  if (xlb_pending_sync_size > PENDING_SYNC_INIT_SIZE &&
      xlb_pending_sync_count < (xlb_pending_sync_size / 4))
  {
    adlb_code code = xlb_pending_shrink();
    ADLB_CHECK(code);
  }

  return ADLB_SUCCESS;
}

static inline bool
xlb_have_pending_notifs(void)
{
  return xlb_pending_notif_count > 0;
}

__attribute__((always_inline))
static inline bool
xlb_is_pending_notif_mode(adlb_sync_mode mode)
{
  switch (mode) {
    case ADLB_SYNC_NOTIFY:
    case ADLB_SYNC_SUBSCRIBE:
    case ADLB_SYNC_REFCOUNT:
      // Notification or may result in notification
      return true;
    default:
      return false;
  }
}

__attribute__((always_inline))
static inline bool
xlb_is_pending_notif(xlb_pending_kind kind,
                     const struct packed_sync* hdr)
{
  switch (kind)
  {
    case DEFERRED_NOTIFY:
    case UNSENT_NOTIFY:
    case ACCEPTED_REFC:
      return true;
    case DEFERRED_SYNC:
      assert(hdr != NULL);
      return xlb_is_pending_notif_mode(hdr->mode);
    default:
      return false;
  }
}
