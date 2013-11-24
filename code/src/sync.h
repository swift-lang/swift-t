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

typedef enum {
  PENDING_SYNC, // Have not yet accepted
  PENDING_RC,   // Have accepted but need to do refcount
} xlb_pending_kind;

// TODO: modify to also store deferred reference counts
typedef struct {
  xlb_pending_kind kind; 
  int rank;
  struct packed_sync hdr;
} xlb_pending;

adlb_code xlb_handle_accepted_sync(int rank, const struct packed_sync *hdr,
                                   bool defer_svr_ops);

// Inline functions to make it quick to check for pending sync requests

// Info about pending sync requests: where sync request has been received
// but we haven't responded yet
extern xlb_pending *xlb_pending_syncs; // Array
extern int xlb_pending_sync_count; // Valid entries in array
extern int xlb_pending_sync_size; // Malloced sized

// Initial size of pending sync array
#define PENDING_SYNC_INIT_SIZE 1024

/*
  returns ADLB_NOTHING if not found, otherwise sets arguments
 */
static inline adlb_code xlb_peek_pending(xlb_pending **pending)
{
  if (xlb_pending_sync_count == 0)
    return ADLB_NOTHING;
 
  *pending = &xlb_pending_syncs[xlb_pending_sync_count - 1];

  return ADLB_SUCCESS;
}

__attribute__((always_inline))
static inline adlb_code xlb_pop_pending(void)
{
  if (xlb_pending_sync_count == 0)
    return ADLB_NOTHING;
  
  xlb_pending_sync_count--;
  DEBUG("POP: %d left", xlb_pending_sync_count);

  if (xlb_pending_sync_size > PENDING_SYNC_INIT_SIZE &&
      xlb_pending_sync_count < (xlb_pending_sync_size / 4))
  {
    // Shrink
    xlb_pending_sync_size = xlb_pending_sync_size / 2;
    xlb_pending_syncs = realloc(xlb_pending_syncs,
      sizeof(xlb_pending_syncs[0]) * (size_t)xlb_pending_sync_size);
    // realloc shouldn't really fail when shrinking
    assert(xlb_pending_syncs != NULL);
  }

  return ADLB_SUCCESS;
}

#endif
