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
 *      Author: wozniak
 */

#ifndef SYNC_H
#define SYNC_H

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
   breaking ties by allowing the higher rank process to always win

   After returning from this, this calling process may issue one RPC
   on the target process

   This function may add pending sync requests from other servers to
   the xlb_pending_syncs buffer, which will need to be serviced.

   This is used for all server-to-server RPCs, including Put, Store,
   Close, Steal, and Shutdown
 */
adlb_code xlb_sync(int target);

/* 
  More flexible version of xlb_sync.  See xlb_sync and packed_sync
  data header for details.
 */
adlb_code xlb_sync2(int target, const struct packed_sync *hdr);

/*
  Subscribe to a datum on another server
 */
adlb_code
xlb_sync_subscribe(int target, adlb_datum_id id, adlb_subscript sub,
                   bool *subscribed);

adlb_code
xlb_sync_notify(int target, adlb_datum_id id, adlb_subscript sub);

adlb_code
xlb_send_unsent_notify(int rank, const struct packed_sync *req_hdr,
        void *malloced_subscript);

typedef enum {
  DEFERRED_SYNC, // Have not yet accepted
  ACCEPTED_RC,   // Have accepted but need to do refcount
  DEFERRED_NOTIFY, // Have accepted but need to process notify
  UNSENT_NOTIFY, // Need to notify other server
} xlb_pending_kind;

typedef struct {
  xlb_pending_kind kind; 
  int rank;
  struct packed_sync *hdr; // Header to be freed later
  void *extra_data; // Extra data if needed for header type
} xlb_pending;

adlb_code xlb_accept_sync(int rank, const struct packed_sync *hdr,
                          bool defer_svr_ops);

adlb_code xlb_handle_notify_sync(int rank,
        const struct packed_subscribe_sync *hdr, const void *sync_data,
        void *extra_data);

// Inline functions to make it quick to check for pending sync requests

// Info about pending sync requests: where sync request has been received
// but we haven't responded yet.
extern xlb_pending *xlb_pending_syncs; // Array for ring buffer
extern int xlb_pending_sync_head; // Head of ring buffer
extern int xlb_pending_sync_count; // Valid entries in array
extern int xlb_pending_sync_size; // Malloced size

// Initial size of pending sync array
#define PENDING_SYNC_INIT_SIZE 1024

adlb_code xlb_pending_shrink(void);

/*
  Remove the pending sync from the list (FIFO order)
  returns ADLB_NOTHING if not found, otherwise sets arguments
  Caller is responsible for freeing hdr
 */
__attribute__((always_inline))
static inline adlb_code xlb_dequeue_pending(xlb_pending_kind *kind,
            int *rank, struct packed_sync **hdr, void **extra_data)
{
  if (xlb_pending_sync_count == 0)
    return ADLB_NOTHING;
  
  xlb_pending *pending = &xlb_pending_syncs[xlb_pending_sync_head];

  *kind = pending->kind;
  *rank = pending->rank;
  *hdr = pending->hdr;
  *extra_data = pending->extra_data;

  xlb_pending_sync_count--;
  xlb_pending_sync_head = (xlb_pending_sync_head + 1) %
                           xlb_pending_sync_size;
  DEBUG("POP: %d left", xlb_pending_sync_count);

  // TODO: validate shrink code and reenable
  /*
  if (xlb_pending_sync_size > PENDING_SYNC_INIT_SIZE &&
      xlb_pending_sync_count < (xlb_pending_sync_size / 4))
  {
    adlb_code code = xlb_pending_shrink();
    ADLB_CHECK(code);
  }*/

  return ADLB_SUCCESS;
}

#endif
