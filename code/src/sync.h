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


// Allow special varieties of sync
adlb_code xlb_sync2(int target, const struct packed_sync *hdr);

typedef struct {
  int rank;
  struct packed_sync *hdr;
} xlb_pending_sync;

// Info about pending sync requests: where sync request has been received
// but we haven't responded yet
extern xlb_pending_sync xlb_pending_syncs[];
extern int xlb_pending_sync_count;

adlb_code handle_accepted_sync(int rank, const struct packed_sync *hdr,
                               bool *server_sync_rejected);

#endif
