
/*
 * steal.c
 *
 *  Created on: Aug 20, 2012
 *      Author: wozniak
 */

#include <mpi.h>

#include <tools.h>

#include "backoffs.h"
#include "common.h"
#include "debug.h"
#include "messaging.h"
#include "mpe-tools.h"
#include "server.h"
#include "sync.h"
#include "steal.h"

/**
   Target: another server
 */
static inline void
get_target_server(int* result)
{
  do
  {
    *result = random_server();
  } while (*result == xlb_world_rank);
}

static inline adlb_code
steal_handshake(int target, int max_memory, int* count)
{
  MPI_Request request;
  MPI_Status status;

  IRECV(count, 1, MPI_INT, target, ADLB_TAG_RESPONSE_STEAL_COUNT);
  SEND(&max_memory, 1, MPI_INT, target, ADLB_TAG_STEAL);

  WAIT(&request, &status);
  STATS("STOLE: %i", *count);
  // MPE_INFO(xlb_mpe_svr_info, "STOLE: %i FROM: %i", *count, target);
  return ADLB_SUCCESS;
}

static inline adlb_code
steal_payloads(int target, int count )
{
  MPI_Status status;
  int length = count * sizeof(struct packed_put);
  struct packed_put* wus = malloc(length);
  RECV(wus, length, MPI_BYTE, target, ADLB_TAG_RESPONSE_STEAL);

  for (int i = 0; i < count; i++)
  {
    RECV(xfer, wus[i].length, MPI_BYTE, target,
         ADLB_TAG_RESPONSE_STEAL);
    workqueue_add(wus[i].type, wus[i].putter, wus[i].priority,
                  wus[i].answer, wus[i].target, wus[i].length, xfer);
  }
  free(wus);
  return ADLB_SUCCESS;
}

/**
   Are we allowed to steal yet?
 */
bool
steal_allowed()
{
  double t = MPI_Wtime();
  if (t - xlb_steal_last < xlb_steal_backoff)
    // Too soon to try again
    return false;
  return true;
}

adlb_code
steal(bool* result)
{
  MPI_Status status;
  adlb_code rc;
  int target;
  *result = false;

  TRACE_START;
  MPE_LOG(xlb_mpe_dmn_steal_start);

  // Record the time of this steal attempt
  xlb_steal_last = MPI_Wtime();

  if (xlb_servers == 1)
    goto end;

  get_target_server(&target);

  rc = xlb_sync(target);
  if (rc == ADLB_SHUTDOWN)
    goto end;
  ADLB_CHECK(rc);

  int count = 0;
  int max_memory = 1;
  rc = steal_handshake(target, max_memory, &count);
  ADLB_CHECK(rc);
  if (count == 0)
    goto end;

  rc = steal_payloads(target, count);
  ADLB_CHECK(rc);
  *result = true;

  end:
  TRACE_END;
  MPE_LOG(xlb_mpe_dmn_steal_end);
  return ADLB_SUCCESS;
}
