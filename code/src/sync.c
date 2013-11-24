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
 *      Author: wozniak
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

static inline adlb_code send_sync(int target, const struct packed_sync *hdr);
static inline adlb_code msg_from_target(int target,
                                  const struct packed_sync *hdr, bool* done);
static inline adlb_code msg_from_other_server(int other_server, 
                  int target, const struct packed_sync *my_hdr);
static inline adlb_code msg_shutdown(bool* done);

static adlb_code add_pending(xlb_pending_kind kind, int rank,
                             const struct packed_sync *hdr);

// IDs of servers with pending sync requests
xlb_pending *xlb_pending_syncs = NULL;
int xlb_pending_sync_count = 0;
int xlb_pending_sync_size = 0; // Malloced sized

adlb_code
xlb_sync_init(void)
{
  xlb_pending_sync_size = PENDING_SYNC_INIT_SIZE;

  // Optionally have different min size - otherwise we won't cover the
  // resizing cases in testing`
  long tmp;
  adlb_code rc = xlb_env_long("ADLB_DEBUG_SYNC_BUFFER_SIZE", &tmp);
  ADLB_CHECK(rc);

  if (rc != ADLB_NOTHING)
  {
    assert(tmp > 0 && tmp <= INT_MAX);
    xlb_pending_sync_size = (int)tmp;
  }

  xlb_pending_sync_count = 0;
  xlb_pending_syncs = malloc(sizeof(xlb_pending_syncs[0]) *
                                (size_t)xlb_pending_sync_size);
  CHECK_MSG(xlb_pending_syncs != NULL, "could not allocate memory");
  return ADLB_SUCCESS;
}

void xlb_sync_finalize(void)
{
  free(xlb_pending_syncs);
  xlb_pending_sync_count = 0;
  xlb_pending_sync_size = 0;
}


adlb_code
xlb_sync(int target)
{
  char hdr_storage[PACKED_SYNC_SIZE];
  struct packed_sync *hdr = (struct packed_sync *)hdr_storage;
#ifndef NDEBUG
  // Avoid send uninitialized bytes for memory checking tools
  memset(hdr, 0, PACKED_SYNC_SIZE);
#endif
  hdr->mode = ADLB_SYNC_REQUEST;
  return xlb_sync2(target, hdr);
}

/*
   While attempting a sync, one of three things may happen:
   1) The target responds.  It either accepts or rejects the sync
      request.  If it rejects, this process retries
   2) Another server interrupts this process with a sync request.
      This process either accepts and serves the request; stores the
      request in xlb_pending_syncs to process later, or rejects it
   3) The master server tells this process to shut down
   These numbers correspond to the variables in the function
 */
adlb_code
xlb_sync2(int target, const struct packed_sync *hdr)
{
  TRACE_START;
  DEBUG("\t xlb_sync() target: %i", target);
  int rc = ADLB_SUCCESS;

  MPE_LOG(xlb_mpe_dmn_sync_start);

  MPI_Status status1, status2, status3;
  /// MPI_Request request1, request2;
  int flag1 = 0, flag2 = 0, flag3 = 0;

  assert(!xlb_server_sync_in_progress);
  xlb_server_sync_in_progress = true;

  // When true, break the loop
  bool done = false;

  if (!xlb_server_shutting_down)
  {
    // Send initial request:
    send_sync(target, hdr);
  }
  else
  {
    // Check that we're not due to shut down because of a previously
    // received shutdown message before going into sync loop
    done = true;
    rc = ADLB_SHUTDOWN;
  }

  while (!done)
  {
    TRACE("xlb_sync: loop");

    IPROBE(target, ADLB_TAG_SYNC_RESPONSE, &flag1, &status1);
    if (flag1)
    {
      msg_from_target(target, hdr, &done);
      if (done) break;
    }

    IPROBE(MPI_ANY_SOURCE, ADLB_TAG_SYNC_REQUEST, &flag2, &status2);
    if (flag2)
      msg_from_other_server(status2.MPI_SOURCE, target, hdr);

    IPROBE(MPI_ANY_SOURCE, ADLB_TAG_SHUTDOWN_SERVER, &flag3,
           &status3);
    if (flag3)
    {
      msg_shutdown(&done);
      rc = ADLB_SHUTDOWN;
      // TODO: break here?
    }

    if (!flag1 && !flag2 && !flag3)
    {
      // Nothing happened, don't poll too aggressively
      xlb_backoff_sync();
    }
  }

  DEBUG("server_sync: [%d] sync with %d successful", xlb_comm_rank, target);
  xlb_server_sync_in_progress = false;
  TRACE_END;
  MPE_LOG(xlb_mpe_dmn_sync_end);

  return rc;
}

static inline adlb_code
send_sync(int target, const struct packed_sync *hdr)
{
  SEND(hdr, (int)PACKED_SYNC_SIZE, MPI_BYTE, target, ADLB_TAG_SYNC_REQUEST);
  return ADLB_SUCCESS;
}

/**
   @return adlb_code
 */
static inline adlb_code
msg_from_target(int target, const struct packed_sync *hdr, bool* done)
{
  MPI_Status status;
  TRACE_START;
  int response;
  RECV(&response, 1, MPI_INT, target, ADLB_TAG_SYNC_RESPONSE);
  CHECK_MSG(response, "Unexpected sync response: %i", response);
  // Accepted
  DEBUG("server_sync: [%d] sync accepted by %d.", xlb_comm_rank, target);
  *done = true;
  TRACE_END
  return ADLB_SUCCESS;
}

static inline adlb_code msg_from_other_server(int other_server, int target,
                  const struct packed_sync *my_hdr)
{
  TRACE_START;
  MPI_Status status;
  adlb_code code;

  // Store on stack - skip malloc
  char hdr_storage[PACKED_SYNC_SIZE];
  struct packed_sync *other_hdr = (struct packed_sync *)hdr_storage;

  RECV(other_hdr, (int)PACKED_SYNC_SIZE, MPI_BYTE, other_server, ADLB_TAG_SYNC_REQUEST);

  /* Serve another server
   * We need to avoid the case of circular deadlock, e.g. where A is waiting
   * to serve B, which is waiting to serve C, which is waiting to serve A, 
   * so don't serve lower ranked servers until we've finished our
   * sync request */
  if (other_server > xlb_comm_rank)
  {
    // accept incoming sync
    DEBUG("server_sync: [%d] interrupted by incoming sync request from %d",
                        xlb_comm_rank, other_server);
    
    code = xlb_handle_accepted_sync(other_server, other_hdr, true);
    ADLB_CHECK(code);
  }
  else
  {
    // Don't handle right away, defer it
    code = add_pending(PENDING_SYNC, other_server, other_hdr);
    ADLB_CHECK(code);
  }
  TRACE_END;
  return ADLB_SUCCESS;
}

/*
  One we have accepted sync, do whatever processing needed to service
  hdr: header data.  Must copy to take ownership
  defer_svr_ops: true if we should defer any potential server->server ops
 */
adlb_code xlb_handle_accepted_sync(int rank, const struct packed_sync *hdr,
            bool defer_svr_ops)
{
  const int response = 1;
  SEND(&response, 1, MPI_INT, rank, ADLB_TAG_SYNC_RESPONSE);

  int mode = hdr->mode;
  adlb_code code = ADLB_ERROR;
  if (mode == ADLB_SYNC_REQUEST)
  {
    code = xlb_serve_server(rank);
  }
  else if (mode == ADLB_SYNC_STEAL)
  {
    // Respond to steal
    code = xlb_handle_steal(rank, &hdr->steal);
  }
  else if (mode == ADLB_SYNC_REFCOUNT)
  {
    /*
      We defer handling of server->server refcounts to avoid potential
      deadlocks if the refcount decrement triggers a cycle of reference
      count decrements between servers and a deadlock.  Deferring
      processing also has the benefit of giving the fastest possible
      response to the other servers.  One downside is that we can't pass
      errors all the way back to the caller - we will simply report them
      and continue.

      Rules about safety of deferring refcounts:
       -> refcount increments - need to apply increment before processing
            any operation that could decrement refcount
       -> read refcount decrements - safe to defer indefinitely,
            but delays freeing memory
       -> write refcount decrements - safe to defer indefinitely, 
            but will delay notifications
     */
    if (defer_svr_ops)
    {
      DEBUG("Defer refcount for <%"PRId64">", hdr->incr.id);
      code = add_pending(PENDING_SYNC, rank, hdr);
      ADLB_CHECK(code);
    }
    else
    {
      DEBUG("Update refcount now for <%"PRId64">", hdr->incr.id);
      adlb_data_code dc = xlb_incr_rc_local(hdr->incr.id,
                                  hdr->incr.change, true);
      CHECK_MSG(dc == ADLB_DATA_SUCCESS, "Unexpected error in refcount");
    }
    // Then we're done - already sent sync response to caller
    return ADLB_SUCCESS;
  }
  else
  {
    printf("Invalid sync mode: %d\n", mode);
    return ADLB_ERROR;
  }
  return code;
}

/*
  Add pending sync
  hdr: sync header.  This function will make a copy of it
  returns: ADLB_SUCCESS, or ADLB_ERROR on unexpected error
 */
static adlb_code add_pending(xlb_pending_kind kind, int rank,
                             const struct packed_sync *hdr)
{
  assert(xlb_pending_sync_count <= xlb_pending_sync_size);
  if (xlb_pending_sync_count == xlb_pending_sync_size)
  {
    xlb_pending_sync_size *= 2;
    DEBUG("Resizing to accommodate %i pending", xlb_pending_sync_size);
    xlb_pending_syncs = realloc(xlb_pending_syncs,
                      sizeof(xlb_pending_syncs[0]) * (size_t)xlb_pending_sync_size);
    CHECK_MSG(xlb_pending_syncs != NULL, "could not allocate memory");
  }
  
  xlb_pending *entry = &xlb_pending_syncs[xlb_pending_sync_count++];
  entry->kind = kind;
  entry->rank = rank;
  memcpy(&entry->hdr, hdr, PACKED_SYNC_SIZE);
  return ADLB_SUCCESS;
}

static inline adlb_code
msg_shutdown(bool* done)
{
  TRACE_START;
  DEBUG("server_sync: [%d] cancelled by shutdown!", xlb_comm_rank);
  *done = true;
  TRACE_END;
  return ADLB_SUCCESS;
}

