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
  } while (*result == xlb_comm_rank);
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

static inline adlb_code steal_sync(int target, int max_memory);

static inline adlb_code steal_payloads(int target, int count);

adlb_code
steal(bool* result)
{
  adlb_code rc;
  int target;
  *result = false;

  TRACE_START;
  MPE_LOG(xlb_mpe_dmn_steal_start);

  get_target_server(&target);

  DEBUG("[%i] stealing from %i", xlb_comm_rank, target);


  struct packed_steal_resp hdr = {
        .count = 0, .last = false };
  MPI_Request request;
  MPI_Status status;

  IRECV(&hdr, sizeof(hdr), MPI_BYTE, target,
        ADLB_TAG_RESPONSE_STEAL_COUNT);

  int max_memory = 1;
  int total = 0;
  rc = steal_sync(target, max_memory);
  if (rc == ADLB_SHUTDOWN)
    goto end;

  ADLB_CHECK(rc);

  // Sender will stream work in groups, each with
  //  header.
  while (true) {
    WAIT(&request, &status);
    if (hdr.count > 0) {
      rc = steal_payloads(target, hdr.count);
      ADLB_CHECK(rc);
      total += hdr.count;
    }
    if (hdr.last)
      break;
    
    IRECV(&hdr, sizeof(hdr), MPI_BYTE, target,
          ADLB_TAG_RESPONSE_STEAL_COUNT);
  }
  
  DEBUG("[%i] stole %i tasks from %i", xlb_comm_rank, total, target);
  // MPE_INFO(xlb_mpe_svr_info, "STOLE: %i FROM: %i", total, target);
  *result = total > 0;

  // Record the time of this steal attempt
  xlb_steal_last = MPI_Wtime();

  end:
  TRACE_END;
  MPE_LOG(xlb_mpe_dmn_steal_end);
  return ADLB_SUCCESS;
}

/*
  Send steal request and sync with server
 */
static inline adlb_code
steal_sync(int target, int max_memory)
{
  // Need to give server information about which work types we have:
  // we only want to steal work types where the other server has more
  // of them than us.
  struct packed_sync *req = malloc(PACKED_SYNC_SIZE);
  req->mode = ADLB_SYNC_STEAL;
  req->steal.max_memory = max_memory;
  workqueue_type_counts(req->steal.work_type_counts, xlb_types_size);

  adlb_code code = xlb_sync2(target, req);
  free(req);
  DEBUG("[%i] synced with %i, receiving steal response", xlb_comm_rank, target);
  return code; 
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
    xlb_work_unit *work = work_unit_alloc(wus[i].length);
    RECV(work->payload, wus[i].length, MPI_BYTE, target,
         ADLB_TAG_RESPONSE_STEAL);
    workqueue_add(wus[i].type, wus[i].putter, wus[i].priority,
                  wus[i].answer, wus[i].target, wus[i].length,
                  wus[i].parallelism, work);
  }
  free(wus);
  DEBUG("[%i] received batch size %i", xlb_comm_rank, count);
  return ADLB_SUCCESS;
}

typedef struct {
  int stealer_rank;
  xlb_work_unit **work_units;
  int size;
  int max_size;
  int stole_count;
} steal_cb_state;


/*
   Send a batch of work until to a stealer
   batch: info about the batch.  This function will free memory
   finish: true if we should notify target that this is last to send
 */
static adlb_code
send_steal_batch(steal_cb_state *batch, bool finish)
{
  int count = batch->size;
  struct packed_steal_resp hdr = { .count = count, .last = finish };
  RSEND(&hdr, sizeof(hdr), MPI_BYTE, batch->stealer_rank,
       ADLB_TAG_RESPONSE_STEAL_COUNT);

  if (count == 0)
    return ADLB_SUCCESS;

  struct packed_put puts[count];
  for (int i = 0; i < count; i++)
  {
    xlb_pack_work_unit(&(puts[i]), batch->work_units[i]);
  }
  
  DEBUG("[%i] sending batch size %i", xlb_comm_rank, batch->size);
  SEND(puts, sizeof(puts[0]) * count, MPI_BYTE,
       batch->stealer_rank, ADLB_TAG_RESPONSE_STEAL);

  for (int i = 0; i < count; i++)
  {
    DEBUG("stolen payload: %s", (char*) batch->work_units[i]->payload);
    xlb_work_unit *unit = batch->work_units[i];
    SEND(unit->payload, unit->length, MPI_BYTE,
         batch->stealer_rank, ADLB_TAG_RESPONSE_STEAL);
    work_unit_free(unit);
  }
  batch->size = 0;
  return ADLB_SUCCESS;
}

/*
   Sends 
 */
static adlb_code
handle_steal_callback(void *cb_data, xlb_work_unit *work)
{
  steal_cb_state *state = (steal_cb_state*)cb_data;
  assert(state->size < state->max_size);
  state->work_units[state->size] = work;
  state->size++;
  state->stole_count++;

  if (state->size == state->max_size) {
    adlb_code code = send_steal_batch(state, false);
    ADLB_CHECK(code);
  }
  return ADLB_SUCCESS;
}

adlb_code
handle_steal(int caller, const struct packed_steal *req)
{
  TRACE_START;
  MPE_LOG(xlb_mpe_svr_steal_start);
  DEBUG("\t caller: %i", caller);

  adlb_code code;

  /* setup callback */
  steal_cb_state state;
  state.stealer_rank = caller;
  state.max_size = XLB_STEAL_CHUNK_SIZE;
  state.work_units = malloc(sizeof(*state.work_units) * state.max_size);
  state.size = 0;
  state.stole_count = 0;
  workqueue_steal_callback cb;
  cb.f = handle_steal_callback;
  cb.data = &state;

  // Maximum amount of memory to return- currently unused
  // Call steal.  This function will call back to send messages
  code = workqueue_steal(req->max_memory, req->work_type_counts, cb);
  ADLB_CHECK(code);
 
  // send any remaining.  If nothing left (or nothing was stolen)
  //    this will notify stealer we're done
  code = send_steal_batch(&state, true);
  ADLB_CHECK(code);

  free(state.work_units);

  STATS("LOST: %i", state.stole_count);
  // MPE_INFO(xlb_mpe_svr_info, "LOST: %i TO: %i", state.stole_count, caller);

  MPE_LOG(xlb_mpe_svr_steal_end);
  TRACE_END;
  return ADLB_SUCCESS;
}

