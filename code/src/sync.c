
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
#include "server.h"
#include "sync.h"

static inline adlb_code msg_from_target(int target, bool* done);
static inline adlb_code msg_from_other_server(int other_server);
static inline adlb_code msg_shutdown(bool* done);

/*
   While attempting a sync, one of three things may happen:
   1) The target responds.  It either accepts or rejects the sync
      request.  If it rejects, this process retries
   2) Another server interrupts this process with a sync request.
      This process either accepts and serves the request or rejects it
   3) The master server tells this process to shut down
   These numbers correspond to the variables in the function
 */
adlb_code
xlb_sync(int target)
{
  TRACE_START;
  DEBUG("\t xlb_sync() target: %i", target);
  int rc = ADLB_SUCCESS;

  MPI_Status status1, status2, status3;
  MPI_Request request1, request2;
  int flag1 = 0, flag2 = 0, flag3 = 0;

  assert(!xlb_server_sync_in_progress);
  xlb_server_sync_in_progress = true;

  // When true, break the loop
  bool done = false;
  // Response from other server: 0=reject, 1=accept
  int response;

  // Send initial request:
  SEND_TAG(target, ADLB_TAG_SYNC_REQUEST);

  while (!done)
  {
    TRACE("xlb_sync: loop");

    IPROBE(target, ADLB_TAG_SYNC_RESPONSE, &flag1, &status1);
    if (flag1)
    {
      msg_from_target(target, &done);
      if (done) break;
    }

    IPROBE(MPI_ANY_SOURCE, ADLB_TAG_SYNC_REQUEST, &flag2, &status2);
    if (flag2)
      msg_from_other_server(status2.MPI_SOURCE);

    IPROBE(MPI_ANY_SOURCE, ADLB_TAG_SHUTDOWN_SERVER, &flag3,
           &status3);
    if (flag3)
    {
      msg_shutdown(&done);
      rc = ADLB_SHUTDOWN;
    }

    xlb_backoff_sync();
  }

  xlb_server_sync_in_progress = false;
  TRACE_END;
  return rc;
}

/**
   @return ADLBcode
 */
static inline adlb_code
msg_from_target(int target, bool* done)
{
  int rc;
  MPI_Status status;
  TRACE_START;
  int response;
  RECV(&response, 1, MPI_INT, target, ADLB_TAG_SYNC_RESPONSE);
  if (response)
  {
    // Accepted
    DEBUG("sync accepted.");
    rc = ADLB_SUCCESS;
    *done = true;
  }
  else
  {
    // Rejected
    DEBUG("sync rejected.  retrying...");
    SEND_TAG(target, ADLB_TAG_SYNC_REQUEST);
  }
  TRACE_END
  return ADLB_SUCCESS;
}

static inline adlb_code
msg_from_other_server(int other_server)
{
  TRACE_START;
  MPI_Status status;
  RECV_TAG(other_server, ADLB_TAG_SYNC_REQUEST);

  // Break tie
  if (other_server > xlb_world_rank)
  {
    // accept incoming sync
    DEBUG("server_sync: interrupted by incoming sync request");
    server_sync_retry = false;
    xlb_serve_server(other_server);
    if (server_sync_retry)
    {
      // In this case, the interrupting server is our sync target
      // It detected the collision and rejected this process
      // Try again
      DEBUG("server_sync: retrying...");
      xlb_backoff_sync_rejected();
      SEND_TAG(other_server, ADLB_TAG_SYNC_REQUEST);
    }
  }
  else
  {
    // Reject incoming sync request
    int response = 0;
    DEBUG("server_sync: rejecting incoming sync request");
    SEND(&response, 1, MPI_INT, other_server,
         ADLB_TAG_SYNC_RESPONSE);
  }
  TRACE_END;
  return ADLB_SUCCESS;
}

static inline adlb_code
msg_shutdown(bool* done)
{
  TRACE_START;
  DEBUG("server_sync: cancelled by shutdown!");
  *done = true;
  TRACE_END;
  return ADLB_SUCCESS;
}

