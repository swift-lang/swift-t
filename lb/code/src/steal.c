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
 *      Authors: wozniak, armstrong
 */

#include <mpi.h>

#include <table_ip.h>
#include <tools.h>

#include "backoffs.h"
#include "common.h"
#include "debug.h"
#include "handlers.h"
#include "messaging.h"
#include "mpe-tools.h"
#include "requestqueue.h"
#include "server.h"
#include "sync.h"
#include "steal.h"

double xlb_steal_last = 0.0;
int xlb_failed_steals_since_backoff = 0;

/*
  Table to track ranks that we have sent steal probes to but not
  received a response from.
 */
static struct table_ip sent_steal_probes;

/**
   Target: another server
 */
static inline void
get_target_server(int* result)
{
  do
  {
    *result = xlb_random_server();
  } while (*result == xlb_s.layout.rank);
}

static bool xlb_can_steal(const int* work_type_counts);
static adlb_code xlb_steal(int target, bool* stole_single, bool* stole_par);
static adlb_code steal_sync(int target, int max_memory, int* response);
static adlb_code steal_payloads(int target, int count,
               int *single_count, int* par_count, bool discard);

/**
 * Initialize internal state for steal module
 */
adlb_code
xlb_steal_init(void)
{
  bool ok = table_ip_init(&sent_steal_probes, 128);
  ADLB_CHECK_MSG(ok, "Error initing table_ip");

  return ADLB_SUCCESS;
}

void
xlb_steal_finalize(void)
{
  table_ip_free_callback(&sent_steal_probes, false, NULL);
}

adlb_code
xlb_random_steal_probe(void)
{
  if (sent_steal_probes.size >= xlb_steal_concurrency_limit)
  {
    // Already have too many steals
    return ADLB_NOTHING;
  }

  int target;
  get_target_server(&target);

  if (table_ip_contains(&sent_steal_probes, target))
  {
    // do nothing - already sent probe
    return ADLB_NOTHING;
  }

  adlb_code rc = xlb_sync_steal_probe(target);
  ADLB_CHECK(rc);

  // Mark as sent to avoid duplicates
  bool ok = table_ip_add(&sent_steal_probes, target, (void*)0x1);
  ADLB_CHECK_MSG(ok, "error adding to table");

  return ADLB_SUCCESS;
}

adlb_code xlb_handle_steal_probe(int caller)
{
  int work_counts[xlb_s.types_size];

  // Fill counts
  xlb_workq_type_counts(work_counts, xlb_s.types_size);

  adlb_code rc = xlb_sync_steal_probe_resp(caller, work_counts,
                                           xlb_s.types_size);
  ADLB_CHECK(rc);

  return ADLB_SUCCESS;
}

/*
 * Called when steal probe received.
 *
 * Should not be called when within sync loop: may initiate more syncs.
 */
adlb_code
xlb_handle_steal_probe_resp(int caller,
  const struct packed_sync *hdr)
{
  adlb_code rc;

  // Mark probe as received
  void *tmp;
  bool found = table_ip_remove(&sent_steal_probes, caller, &tmp);
  ADLB_CHECK_MSG(found, "probe not found");

  const int *caller_type_counts = (int*)hdr->sync_data;
  if (xlb_can_steal(caller_type_counts))
  {
    bool stole_single, stole_par;
    rc = xlb_steal(caller, &stole_single, &stole_par);
    ADLB_CHECK(rc);

    DEBUG("[%i] Completed steal from %i stole_single: %i stole_par: %i",
          xlb_s.layout.rank, caller, (int)stole_single, (int)stole_par);
    // Try to match stolen tasks
    rc = xlb_recheck_queues(stole_single, stole_par);
    ADLB_CHECK(rc);
  }
  else
  {
    DEBUG("[%i] No matching work to steal from %i",
          xlb_s.layout.rank, caller);
  }

  return ADLB_SUCCESS;
}

/*
 * Check if there is anything worth stealing from a target
 *
 * work_type_counts: Work type counts for steal target
 */
static bool xlb_can_steal(const int *work_type_counts)
{
  int request_q_sizes[xlb_s.types_size];
  xlb_requestqueue_type_counts(request_q_sizes, xlb_s.types_size);
  for (int i = 0; i < xlb_s.types_size; i++)
  {
    if (request_q_sizes[i] > 0 &&
        work_type_counts[i] > 0)
    {
      // Matching request here and work on other server
      return true;
    }
  }
  return false;
}

/**
   Issue sync() and steal.

   Note that this may add sync requests to the xlb_pending_syncs list,
   which must be handled by the caller.
   @param stole_single true if stole single-worker task, else false
   @param stole_par true if stole parallel task, else false
 */
static adlb_code
xlb_steal(int target, bool *stole_single, bool *stole_par)
{
  adlb_code rc;
  *stole_single = false;
  *stole_par = false;
  MPI_Request request;
  MPI_Status status;

  TRACE_START;
  MPE_LOG(xlb_mpe_dmn_steal_start);

  DEBUG("[%i] stealing from %i", xlb_s.layout.rank, target);

  struct packed_steal_resp hdr;

  IRECV2(&hdr, sizeof(hdr), MPI_BYTE, target,
        ADLB_TAG_RESPONSE_STEAL_COUNT, &request);

  int max_memory = 1;
  int total_single = 0, total_par = 0;
  int response;
  rc = steal_sync(target, max_memory, &response);
  if (!response || rc == ADLB_SHUTDOWN)
  {
    CANCEL(&request);
    *stole_single = *stole_par = false;
    goto end;
  }

  ADLB_CHECK(rc);

  // Sender will stream work in groups, each with
  //  header.
  while (true) {
    WAIT(&request, &status);
    if (hdr.count > 0) {
      int single, par;
      rc = steal_payloads(target, hdr.count, &single, &par, false);
      ADLB_CHECK(rc);
      total_single += single;
      total_par += par;
    }
    if (hdr.last)
      break;

    IRECV2(&hdr, sizeof(hdr), MPI_BYTE, target,
           ADLB_TAG_RESPONSE_STEAL_COUNT, &request);
  }

  DEBUG("[%i] steal result: stole %i tasks from %i", xlb_s.layout.rank,
        total_single + total_par, target);
  // MPE_INFO(xlb_mpe_svr_info, "STOLE: %i FROM: %i", hdr->count, target);
  *stole_single = (total_single > 0);
  *stole_par = (total_par > 0);

  // Record the time of this steal attempt
  xlb_steal_last = MPI_Wtime();

  // Update failed steals
  if (hdr.count > 0)
  {
    xlb_failed_steals_since_backoff = 0;
  }
  else
  {
    xlb_failed_steals_since_backoff++;
  }

  end:
  TRACE_END;
  MPE_LOG(xlb_mpe_dmn_steal_end);
  return ADLB_SUCCESS;
}

/*
  Send steal request and sync with server.
  Note that this can add requests to the pending_sync list that
  will need to be handled.
  accepted: if true, steal response will be sent to us
 */
static adlb_code
steal_sync(int target, int max_memory, int* response)
{
  // Need to give server information about which work types we have:
  // we only want to steal work types where the other server has more
  // of them than us.
  int work_counts[xlb_s.types_size];

  // Fill counts
  xlb_workq_type_counts(work_counts, xlb_s.types_size);

  adlb_code code = xlb_sync_steal(target, work_counts, xlb_s.types_size,
                                  max_memory, response);
  if (code == ADLB_SUCCESS)
  {
    if (*response)
    {
      DEBUG("[%i] synced with %i, receiving steal response",
           xlb_s.layout.rank, target);
    }
    else
    {
      DEBUG("[%i] synced with %i, no steal response",
           xlb_s.layout.rank, target);
    }
  }
  else if (code == ADLB_SHUTDOWN)
  {
    DEBUG("[%i] tried to sync with %i, received shutdown",
         xlb_s.layout.rank, target);
  }
  else
  {
    DEBUG("[%i] tried to sync with %i, error!",
         xlb_s.layout.rank, target);
  }
  return code;
}

/*
 * discard: if true, discard the payloads.  If false, enqueue the work
 */
static adlb_code
steal_payloads(int target, int count,
               int* single_count, int* par_count,
               bool discard)
{
  assert(count > 0);
  MPI_Status status;
  int length = count * (int)sizeof(struct packed_steal_work);
  struct packed_steal_work* wus = malloc((size_t)length);
  valgrind_assert(wus);
  RECV(wus, length, MPI_BYTE, target, ADLB_TAG_RESPONSE_STEAL);
  int single = 0, par = 0;
  for (int i = 0; i < count; i++)
  {
    assert(wus[i].length > 0);
    xlb_work_unit *work = work_unit_alloc((size_t)wus[i].length);
    RECV(work->payload, wus[i].length, MPI_BYTE, target,
         ADLB_TAG_RESPONSE_STEAL);
    if (!discard) {
      xlb_work_unit_init(work, wus[i].type, wus[i].putter,
                    wus[i].answer, wus[i].target, wus[i].length,
                    wus[i].opts);
      xlb_workq_add(work);
    } else {
      xlb_work_unit_free(work);
    }
    if (wus[i].opts.parallelism > 1)
    {
      par++;
    }
    else
    {
      single++;
    }
  }
  free(wus);
  DEBUG("[%i] received batch size %i", xlb_s.layout.rank, count);

  *single_count = single;
  *par_count = par;
  return ADLB_SUCCESS;
}

typedef struct {
  int stealer_rank;
  xlb_work_unit **work_units;
  size_t size;
  size_t max_size;
  int stole_count; /* Total number stolen */
} steal_cb_state;


/*
   Send a batch of work until to a stealer
   batch: info about the batch.  This function will free memory
   finish: true if we should notify target that this is last to send
 */
static adlb_code
send_steal_batch(steal_cb_state *batch, bool finish)
{
  int count = (int)batch->size;
  struct packed_steal_resp hdr = { .count = count, .last = finish };
  SEND(&hdr, sizeof(hdr), MPI_BYTE, batch->stealer_rank,
       ADLB_TAG_RESPONSE_STEAL_COUNT);

  if (count == 0)
    return ADLB_SUCCESS;

  struct packed_steal_work packed[count];
  for (int i = 0; i < count; i++)
  {
    xlb_pack_steal_work(&(packed[i]), batch->work_units[i]);
  }

  // Store requests for wait

  MPI_Request reqs[count + 1];

  DEBUG("[%i] sending batch size %zu", xlb_s.layout.rank, batch->size);
  ISEND(packed, (int)sizeof(packed[0]) * count, MPI_BYTE,
       batch->stealer_rank, ADLB_TAG_RESPONSE_STEAL, &reqs[0]);

  for (int i = 0; i < count; i++)
  {
    DEBUG("stolen payload: %s", (char*) batch->work_units[i]->payload);
    xlb_work_unit *unit = batch->work_units[i];
    ISEND(unit->payload, unit->length, MPI_BYTE,
         batch->stealer_rank, ADLB_TAG_RESPONSE_STEAL, &reqs[i+1]);
  }

  // Wait until MPI confirms sends have completed
  int rc = MPI_Waitall(count + 1, reqs, MPI_STATUSES_IGNORE);
  MPI_CHECK(rc);

  for (int i = 0; i < count; i++)
  {
    xlb_work_unit_free(batch->work_units[i]);
  }

  batch->size = 0;
  return ADLB_SUCCESS;
}

/*
   Sends
 */
static adlb_code
handle_steal_callback(void* cb_data, xlb_work_unit* work)
{
  steal_cb_state* state = (steal_cb_state*) cb_data;
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
xlb_handle_steal(int caller, const struct packed_steal *req,
                 const int *work_type_counts)
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
  xlb_workq_steal_callback cb;
  cb.f = handle_steal_callback;
  cb.data = &state;

  // Maximum amount of memory to return- currently unused
  // Call steal.  This function will call back to send messages
  code = xlb_workq_steal(req->max_memory, work_type_counts, cb);
  ADLB_CHECK(code);

  // send any remaining.  If nothing left (or nothing was stolen)
  //    this will notify stealer we're done
  code = send_steal_batch(&state, true);
  ADLB_CHECK(code);

  free(state.work_units);

  if (state.stole_count > 0)
  {
    // Update idle check attempt if needed to account for work being
    // moved around.
    int64_t thief_idle_check_attempt = req->idle_check_attempt;
    if (thief_idle_check_attempt > xlb_idle_check_attempt)
    {
      DEBUG("Update idle check attempt from thief: %"PRId64,
            thief_idle_check_attempt);
      xlb_idle_check_attempt = thief_idle_check_attempt;
    }
  }
  DEBUG("[%i] steal result: sent %i tasks to %i", xlb_s.layout.rank,
        state.stole_count, caller);
  STATS("LOST: %i", state.stole_count);
  // MPE_INFO(xlb_mpe_svr_info, "LOST: %i TO: %i", state.stole_count, caller);

  MPE_LOG(xlb_mpe_svr_steal_end);
  TRACE_END;
  return ADLB_SUCCESS;
}
