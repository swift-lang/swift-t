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
#include "requestqueue.h"
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

bool
steal_allowed()
{
  if (xlb_servers == 1)
    // No other servers
    return false;
  double t = MPI_Wtime();
  if (t - xlb_steal_last < xlb_steal_backoff)
    // Too soon to try again
    return false;
  return true;
}

static inline adlb_code steal_handshake(int target, int max_memory,
                                        int* count);

static inline adlb_code steal_payloads(int target, int count);

adlb_code
steal(bool* result)
{
  adlb_code rc;
  int target;
  *result = false;

  TRACE_START;
  MPE_LOG(xlb_mpe_dmn_steal_start);

  // Record the time of this steal attempt
  xlb_steal_last = MPI_Wtime();

  get_target_server(&target);

  rc = xlb_sync(target);
  ADLB_CHECK(rc);
  if (rc == ADLB_SHUTDOWN)
    goto end;

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

static inline adlb_code
steal_handshake(int target, int max_memory, int* count)
{
  MPI_Request request;
  MPI_Status status;

  IRECV(count, 1, MPI_INT, target, ADLB_TAG_RESPONSE_STEAL_COUNT);

  // Only try to steal work types for which we have outstanding requests.
  // If we steal other work types, we may not be able to do anything with
  // them, and in some cases they can be stolen right back by another 
  // server in the same situation (e.g. imagine the situation where this
  // server has requests for type A, and a surplus of type B.  We don't
  // want to steal more of type B from another server in the same situation)
  struct packed_steal *req = malloc(PACKED_STEAL_SIZE(xlb_types_size));
  req->max_memory = max_memory;
  requestqueue_types(req->work_types, xlb_types_size, &(req->type_count));
  SEND(req, PACKED_STEAL_SIZE(req->type_count), MPI_BYTE, target,
        ADLB_TAG_STEAL);
  free(req);

  WAIT(&request, &status);
  DEBUG("STOLE: %i", *count);
  // MPE_INFO(xlb_mpe_svr_info, "STOLE: %i FROM: %i", *count, target);
  return ADLB_SUCCESS;
}

static inline adlb_code
steal_payloads(int target, int count)
{
  MPI_Status status;
  int length = count * sizeof(struct packed_put);
  struct packed_put* wus = malloc(length);
  valgrind_assert(wus);
  RECV(wus, length, MPI_BYTE, target, ADLB_TAG_RESPONSE_STEAL);

  for (int i = 0; i < count; i++)
  {
    RECV(xfer, wus[i].length, MPI_BYTE, target,
         ADLB_TAG_RESPONSE_STEAL);
    workqueue_add(wus[i].type, wus[i].putter, wus[i].priority,
                  wus[i].answer, wus[i].target, wus[i].length,
                  wus[i].parallelism, xfer);
  }
  free(wus);
  return ADLB_SUCCESS;
}
