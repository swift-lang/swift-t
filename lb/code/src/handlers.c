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
 * handlers.c
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()

#include "config.h"

#include <assert.h>
#include <alloca.h>
#include <stdio.h>

#if HAVE_MALLOC_H
#include <malloc.h>
#endif

#include <mpi.h>

#include "adlb-defs.h"
#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "handlers.h"
#include "messaging.h"
#include "mpe-tools.h"
#include "mpi-tools.h"
#include "notifications.h"
#include "requestqueue.h"
#include "refcount.h"
#include "server.h"
#include "steal.h"
#include "sync.h"
#include "tools.h"
#include "workqueue.h"

/** Map from incoming message tag to handler function */
xlb_handler xlb_handlers[XLB_MAX_HANDLERS];

/** Count how many handlers have been registered */
static int xlb_handler_count = 0;

/** Count how many calls to each handler */
int64_t xlb_handler_counters[XLB_MAX_HANDLERS];

/** Copy of this processes' MPI rank */
static int mpi_rank;

/** Additional scratch space */
#define XLB_SCRATCH_SIZE (1024 * 64)
char xlb_scratch[XLB_SCRATCH_SIZE];
static const adlb_buffer xlb_scratch_buf =
          { .data = xlb_scratch, .length = XLB_SCRATCH_SIZE };

static void register_handler(adlb_tag tag, xlb_handler h);

static adlb_code handle_sync_response(int caller);
static adlb_code handle_steal_response(int caller);
static adlb_code handle_do_nothing(int caller);
static adlb_code handle_put(int caller);
static adlb_code handle_dput(int caller);
static adlb_code handle_get(int caller);
static adlb_code handle_iget(int caller);
static adlb_code handle_amget(int caller);
static adlb_code handle_create(int caller);
static adlb_code handle_multicreate(int caller);
static adlb_code handle_exists(int caller);
static adlb_code handle_store(int caller);
static adlb_code handle_retrieve(int caller);
static adlb_code handle_enumerate(int caller);
static adlb_code handle_subscribe(int caller);
static adlb_code handle_notify(int caller);
static adlb_code handle_get_refcounts(int caller);
static adlb_code handle_refcount_incr(int caller);
static adlb_code handle_insert_atomic(int caller);
static adlb_code handle_unique(int caller);
static adlb_code handle_typeof(int caller);
static adlb_code handle_container_typeof(int caller);
static adlb_code handle_container_reference(int caller);
static adlb_code handle_container_size(int caller);
static adlb_code handle_lock(int caller);
static adlb_code handle_unlock(int caller);
static adlb_code handle_check_idle(int caller);
static adlb_code handle_block_worker(int caller);
static adlb_code handle_shutdown_worker(int caller);
static adlb_code handle_fail(int caller);

static adlb_code find_req_bytes(int *bytes, int caller, adlb_tag tag);

static inline adlb_code send_work_unit(int worker, xlb_work_unit* wu);

static adlb_code send_work(int worker, xlb_work_unit_id wuid, int type,
                                  int answer,
                                  const void* payload, int length,
                                  int parallelism);

static adlb_code
send_parallel_work_unit(int *workers, xlb_work_unit *wu);

static inline adlb_code send_parallel_work(int *workers,
    xlb_work_unit_id wuid, int type, int answer,
    const void* payload, int length, int parallelism);

static inline adlb_code send_no_work(int worker);

static adlb_code put(int type, int putter, int answer, int target,
      int length, adlb_put_opts opts, const void *data);

static inline adlb_code attempt_match_work(int type, int putter,
      int answer, int target, adlb_put_opts opts,
      int length, const void *inline_data);

static adlb_code attempt_match_par_work(int type,
      int answer, const void *payload, int length, int parallelism);

static inline adlb_code send_matched_work(int type, int putter,
      int answer, bool targeted,
      int worker, int length, const void *inline_data);

static adlb_code
process_get_request(int caller, int type, int count, bool blocking);

static adlb_code xlb_recheck_single_queues(void);

static adlb_code xlb_recheck_parallel_queues(void);

static inline adlb_code xlb_check_parallel_queue(int work_type);

static inline adlb_code redirect_work(int type, int putter, int answer,
                                      int worker, int length);


static inline int check_workqueue(int caller, int type, int count);

static adlb_code
notify_helper(adlb_notif_t *notifs);

static adlb_code
refcount_decr_helper(adlb_datum_id id, adlb_refc decr);

/** Is this process currently stealing work? */
static bool stealing = false;

void
xlb_handlers_init(void)
{
  MPI_Comm_rank(xlb_s.comm, &mpi_rank);

  xlb_handler_count = 0;
  memset(xlb_handlers, '\0', XLB_MAX_HANDLERS*sizeof(xlb_handler));

  register_handler(ADLB_TAG_SYNC_RESPONSE, handle_sync_response);
  register_handler(ADLB_TAG_RESPONSE_STEAL_COUNT, handle_steal_response);
  register_handler(ADLB_TAG_DO_NOTHING, handle_do_nothing);
  register_handler(ADLB_TAG_PUT, handle_put);
  register_handler(ADLB_TAG_DPUT, handle_dput);
  register_handler(ADLB_TAG_GET, handle_get);
  register_handler(ADLB_TAG_IGET, handle_iget);
  register_handler(ADLB_TAG_AMGET, handle_amget);
  register_handler(ADLB_TAG_CREATE_HEADER, handle_create);
  register_handler(ADLB_TAG_MULTICREATE, handle_multicreate);
  register_handler(ADLB_TAG_EXISTS, handle_exists);
  register_handler(ADLB_TAG_STORE_HEADER, handle_store);
  register_handler(ADLB_TAG_RETRIEVE, handle_retrieve);
  register_handler(ADLB_TAG_ENUMERATE, handle_enumerate);
  register_handler(ADLB_TAG_SUBSCRIBE, handle_subscribe);
  register_handler(ADLB_TAG_NOTIFY, handle_notify);
  register_handler(ADLB_TAG_GET_REFCOUNTS, handle_get_refcounts);
  register_handler(ADLB_TAG_REFCOUNT_INCR, handle_refcount_incr);
  register_handler(ADLB_TAG_INSERT_ATOMIC, handle_insert_atomic);
  register_handler(ADLB_TAG_UNIQUE, handle_unique);
  register_handler(ADLB_TAG_TYPEOF, handle_typeof);
  register_handler(ADLB_TAG_CONTAINER_TYPEOF, handle_container_typeof);
  register_handler(ADLB_TAG_CONTAINER_REFERENCE,
                                           handle_container_reference);
  register_handler(ADLB_TAG_CONTAINER_SIZE, handle_container_size);
  register_handler(ADLB_TAG_LOCK, handle_lock);
  register_handler(ADLB_TAG_UNLOCK, handle_unlock);
  register_handler(ADLB_TAG_CHECK_IDLE, handle_check_idle);
  register_handler(ADLB_TAG_BLOCK_WORKER, handle_block_worker);
  register_handler(ADLB_TAG_SHUTDOWN_WORKER, handle_shutdown_worker);
  register_handler(ADLB_TAG_FAIL, handle_fail);
}

static void
register_handler(adlb_tag tag, xlb_handler h)
{
  xlb_handlers[tag] = h;
  xlb_handler_count++;
  valgrind_assert(xlb_handler_count < XLB_MAX_HANDLERS);
  xlb_handler_counters[tag] = 0;
}

void xlb_print_handler_counters(void)
{
  if (!xlb_s.perfc_enabled)
  {
    return;
  }

  for (int tag = 0; tag < XLB_MAX_HANDLERS; tag++)
  {
    if (xlb_handlers[tag] != NULL)
    {
      PRINT_COUNTER("%s=%"PRId64"\n",
                    xlb_get_tag_name(tag),
                    xlb_handler_counters[tag]);
    }
  }
}

//// Individual handlers follow...

/**
   Incoming sync response was received but not in sync mode.  This can
   occur if we aborted a sync attempt due to receiving a shutdown signal.
   Ignore the sync response in this case.
 */
static adlb_code handle_sync_response(int caller)
{
  MPI_Status status;
  int response;
  RECV(&response, 1, MPI_INT, caller, ADLB_TAG_SYNC_RESPONSE);
  return ADLB_SUCCESS;
}

/**
  A similar situation can occur with stolen task counts to sync responses
 */
static adlb_code handle_steal_response(int caller)
{
  MPI_Status status;
  struct packed_steal_resp hdr;
  RECV(&hdr, sizeof(hdr), MPI_BYTE, caller,
        ADLB_TAG_RESPONSE_STEAL_COUNT);

  assert(hdr.count == 0); // Shouldn't be receiving work after shutdown
  return ADLB_SUCCESS;
}


/**
  Placeholder request: do nothing.
 */
static adlb_code
handle_do_nothing(int caller)
{
  MPI_Status status;
  RECV_TAG(caller, ADLB_TAG_DO_NOTHING);
  return ADLB_SUCCESS;
}

static adlb_code
handle_put(int caller)
{
  MPI_Status status;

  MPE_LOG(xlb_mpe_svr_put_start);

  char req_buf[PACKED_PUT_MAX];
  RECV(req_buf, PACKED_PUT_MAX, MPI_BYTE, caller, ADLB_TAG_PUT);
  struct packed_put *p = (struct packed_put*)req_buf;

#ifndef NDEBUG
  // Sanity check size
  if (p->has_inline_data)
  {
    int msg_size;
    int mc = MPI_Get_count(&status, MPI_BYTE, &msg_size);
    assert(mc == MPI_SUCCESS);
    int inline_data_recvd = msg_size - (int)PACKED_PUT_SIZE(0);
    assert(inline_data_recvd == p->length);
  }
#endif
  const void *inline_data = p->has_inline_data ? p->inline_data : NULL;
  adlb_code rc;
  rc = put(p->type, p->putter, p->answer, p->target,
           p->length, p->opts, inline_data);
  ADLB_CHECK(rc);

  MPE_LOG(xlb_mpe_svr_put_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_dput(int caller)
{
  MPI_Status status;

  MPE_LOG(xlb_mpe_svr_put_start);

  RECV(xlb_xfer, ADLB_XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_DPUT);
  const struct packed_dput *p = (struct packed_dput*)xlb_xfer;

  // Put arrays first to avoid alignment issues
  const adlb_datum_id *wait_ids = p->inline_data;

  // Remainder of data is packed into array in binary form
  const char *p_pos = (const char*)p->inline_data;
  p_pos += (int)sizeof(wait_ids[0]) * p->id_count;

  adlb_datum_id_sub wait_id_subs[p->id_sub_count];
  for (int i = 0; i < p->id_sub_count; i++)
  {
    p_pos += xlb_unpack_id_sub(p_pos, &wait_id_subs[i].id,
                               &wait_id_subs[i].subscript);
  }

  const char *name = NULL;
  int name_strlen = 0;
  #ifndef NDEBUG
  // Don't pack name for optimized build
  name_strlen = p->name_strlen;
  name = p_pos;
  p_pos += name_strlen;
  #endif

  const void *inline_data = NULL;
  if (p->has_inline_data)
  {
    inline_data = p_pos;
    p_pos += p->length;
  }
  #ifndef NDEBUG
  // Sanity check size
  int msg_size;
  int mc = MPI_Get_count(&status, MPI_BYTE, &msg_size);
  assert(mc == MPI_SUCCESS);
  // Make sure we don't get garbage data
  assert(((const char*)p_pos) - ((const char*) p) <= msg_size);
  #endif

  MPI_Request request;
  xlb_work_unit *work = work_unit_alloc((size_t)p->length);
  ADLB_CHECK_MALLOC(work);

  xlb_work_unit_init(work, p->type, caller, p->answer,
                     p->target, p->length, p->opts);

  if (inline_data == NULL)
  {
    // Set up receive for payload into work unit
    IRECV(work->payload, p->length, MPI_BYTE, caller, ADLB_TAG_WORK);

    // send initial response to prompt send
    int send_work_response = ADLB_SUCCESS;
    SEND(&send_work_response, 1, MPI_INT, caller, ADLB_TAG_RESPONSE_PUT);
  }
  else
  {
    memcpy(work->payload, inline_data, (size_t)p->length);
  }

  if (inline_data == NULL)
  {
    // Wait to receive data
    WAIT(&request, &status);
  }

  // We have all info from caller now - caller can proceed.
  // Any errors will occur in server now.
  int response = ADLB_SUCCESS;
  SEND(&response, 1, MPI_INT, caller, ADLB_TAG_RESPONSE_PUT);

  bool ready;
  xlb_engine_code tc = xlb_engine_put(name, name_strlen,
        p->id_count, wait_ids, p->id_sub_count, wait_id_subs,
        work, &ready);
  ADLB_CHECK_MSG(tc == XLB_ENGINE_SUCCESS, "Error adding data-dependent work");

  if (ready)
  {
    // Didn't put into engine, need to move to work queue
    adlb_code ac = xlb_put_work_unit(work);
    ADLB_CHECK(ac);
  }

  // Update performance counters
  xlb_task_data_count(p->type, p->target >= 0, p->opts.parallelism > 1,
                      !ready);

  MPE_LOG(xlb_mpe_svr_dput_end);
  return ADLB_SUCCESS;
}

/*
  Handle a put
  inline_data: if task data already available here, otherwise NULL
 */
static adlb_code
put(int type, int putter, int answer, int target, int length,
    adlb_put_opts opts, const void *inline_data)
{
  adlb_code code;
  MPI_Status status;
  assert(length >= 0);

  if (opts.parallelism <= 1)
  {
    // Try to match to a worker immediately for single-worker task
    adlb_code matched = attempt_match_work(type, putter,
        answer, target, opts, length, inline_data);
    if (matched == ADLB_SUCCESS)
      // Redirected ok
      return ADLB_SUCCESS;
    ADLB_CHECK(matched);
  }

  // Store this work unit on this server
  DEBUG("server storing work...");

  MPI_Request req;

  xlb_work_unit *work = NULL;
  if (inline_data == NULL)
  {
    // Set up receive for payload into work unit
    work = work_unit_alloc((size_t)length);
    IRECV2(work->payload, length, MPI_BYTE, putter, ADLB_TAG_WORK, &req);
    SEND(&mpi_rank, 1, MPI_INT, putter, ADLB_TAG_RESPONSE_PUT);
    // Wait to receive data
    WAIT(&req, &status);
  }
  else
  {
    int resp = ADLB_SUCCESS;
    ISEND(&resp, 1, MPI_INT, putter, ADLB_TAG_RESPONSE_PUT, &req);
    // Copy data while waiting for message
    work = work_unit_alloc((size_t)length);
    memcpy(work->payload, inline_data, (size_t)length);
    WAIT(&req, &status);
  }

  DEBUG("recvd work unit: x%i %s ", opts.parallelism, work->payload);

  if (opts.parallelism > 1)
  {
    code = attempt_match_par_work(type, answer, work->payload, length,
                                  opts.parallelism);
    if (code == ADLB_SUCCESS)
    {
      // Successfully sent out task
      xlb_work_unit_free(work);
      return ADLB_SUCCESS;
    }
    ADLB_CHECK(code);
  }

  xlb_work_unit_init(work, type, putter, answer, target, length, opts);
  code = xlb_workq_add(work);
  ADLB_CHECK(code);

  return ADLB_SUCCESS;
}

/*
  Put already-allocated work unit.
  This takes ownership of entire work unit.
  */
adlb_code
xlb_put_work_unit(xlb_work_unit *work)
{
  adlb_code code;
  assert(work->length >= 0);

  int target = work->target;
  int type = work->type;
  if (work->opts.parallelism <= 1)
  {
    // Try to match to a worker
    bool targeted = (target >= 0);
    int worker;
    // Attempt to redirect work unit to another worker
    if (targeted)
    {
      ADLB_CHECK_MSG(target < xlb_s.layout.size, "Invalid target: %i", target);
      worker = xlb_requestqueue_matches_target(target, type,
                                               work->opts.accuracy);
    }
    else
    {
      worker = xlb_requestqueue_matches_type(type);
    }

    if (worker != ADLB_RANK_NULL)
    {
      code = send_work(worker, work->id, type, work->answer,
                       work->payload, work->length, 1);
      ADLB_CHECK(code);
      xlb_work_unit_free(work);

      if (xlb_s.perfc_enabled)
      {
        xlb_task_bypass_count(type, targeted, false);
      }
      return ADLB_SUCCESS;
    }
  }
  else
  {
    code = attempt_match_par_work(type, work->answer,
            work->payload, work->length, work->opts.parallelism);
    if (code == ADLB_SUCCESS)
    {
      // Successfully sent out task
      xlb_work_unit_free(work);

      if (xlb_s.perfc_enabled)
      {
        xlb_task_bypass_count(type, false, true);
      }
      return ADLB_SUCCESS;
    }
    ADLB_CHECK(code);
  }

  // Store this work unit on this server
  DEBUG("server storing work...");
  DEBUG("work unit: x%i %s ", work->opts.parallelism, work->payload);

  code = xlb_workq_add(work);
  ADLB_CHECK(code);

  return ADLB_SUCCESS;
}

adlb_code xlb_put_targeted_local(int type, int putter,
      int answer, int target, adlb_put_opts opts,
      const void* payload, int length)
{
  assert(xlb_map_to_server(&xlb_s.layout, target) == xlb_s.layout.rank);
  valgrind_assert(target >= 0 && target < xlb_s.layout.workers);
  assert(target >= 0 && target < xlb_s.layout.workers);
  int worker;
  adlb_code rc;

  DEBUG("xlb_put_targeted_local: to: %i payload: %s", target,
        (char*) payload);
  assert(length >= 0);

  // Work unit is for this server
  // Is the target already waiting?
  worker = xlb_requestqueue_matches_target(target, type,
                                           opts.accuracy);
  if (worker != ADLB_RANK_NULL)
  {
    xlb_work_unit_id wuid = xlb_workq_unique();
    rc = send_work(target, wuid, type, answer, payload, length,
                   opts.parallelism);
    ADLB_CHECK(rc);
  }
  else
  {
    xlb_work_unit *work = work_unit_alloc((size_t)length);
    memcpy(work->payload, payload, (size_t)length);

    xlb_work_unit_init(work, type, putter, answer, target,
                       length, opts);
    DEBUG("xlb_put_targeted_local(): server storing work...");
    xlb_workq_add(work);
  }

  return ADLB_SUCCESS;
}


/*
  Attempt to match work.  Return ADLB_NOTHING if couldn't redirect,
  ADLB_SUCCESS on successful redirect, ADLB_ERROR on error.

  inline_data: non-null if we already have task body
 */
static adlb_code attempt_match_work(int type, int putter,
      int answer, int target, adlb_put_opts opts,
      int length, const void *inline_data)
{
  if (opts.parallelism > 1)
  {
    // Don't try to redirect parallel work
    return ADLB_NOTHING;
  }

  bool targeted = (target >= 0);
  int worker;
  // Attempt to redirect work unit to another worker
  if (targeted)
  {
    ADLB_CHECK_MSG(target < xlb_s.layout.size, "Invalid target: %i", target);
    worker = xlb_requestqueue_matches_target(target, type,
                                             opts.accuracy);
    if (worker == ADLB_RANK_NULL &&
        opts.strictness != ADLB_TGT_STRICT_HARD)
    {
      // Try to send to alternate target
      worker = xlb_requestqueue_matches_type(type);
    }
    if (worker == ADLB_RANK_NULL)
    {
      return ADLB_NOTHING;
    }
    assert(opts.strictness != ADLB_TGT_STRICT_HARD ||
           opts.accuracy   == ADLB_TGT_ACCRY_NODE  ||
           worker == target);
  }
  else
  {
    worker = xlb_requestqueue_matches_type(type);
    if (worker == ADLB_RANK_NULL)
    {
      return ADLB_NOTHING;
    }
  }

  return send_matched_work(type, putter, answer, targeted,
          worker, length, inline_data);
}

/*
  Attempt to match parallel work.  Return ADLB_NOTHING if couldn't
  redirect, ADLB_SUCCESS on successful redirect, ADLB_ERROR on error.

  inline_data: non-null if we already have task body
 */
static adlb_code attempt_match_par_work(int type,
      int answer, const void *payload, int length, int parallelism)
{
  ADLB_CHECK_MSG(parallelism <= xlb_s.layout.my_workers,
            "Task with parallelism %i can never execute: "
            "server has %i workers!\n",
            parallelism, xlb_s.layout.my_workers);
  adlb_code code;

  // Try to match parallel task to multiple workers after receiving
  int parallel_workers[parallelism];
  if (xlb_requestqueue_parallel_workers(type, parallelism,
                                         parallel_workers))
  {
    code = send_parallel_work(parallel_workers, XLB_WORK_UNIT_ID_NULL,
                              type, answer, payload, length, parallelism);
    ADLB_CHECK(code);
    if (xlb_s.perfc_enabled)
    {
      xlb_task_bypass_count(type, false, true);
    }
    return ADLB_SUCCESS;
  }

  return ADLB_NOTHING;
}


static inline adlb_code send_matched_work(int type, int putter,
      int answer, bool targeted,
      int worker, int length, const void *inline_data)
{
  adlb_code code;
  if (xlb_s.perfc_enabled)
  {
    // TODO: track soft targeted separately?
    xlb_task_bypass_count(type, targeted, false);
  }

  if (inline_data == NULL)
  {
    code = redirect_work(type, putter, answer, worker, length);
    ADLB_CHECK(code);
  }
  else
  {
    MPI_Request req;
    MPI_Status status;
    int response = ADLB_SUCCESS;
    // Let putter know we've got it from here
    IRSEND(&response, 1, MPI_INT, putter, ADLB_TAG_RESPONSE_PUT, &req);

    // Sent to matched
    code = send_work(worker, XLB_WORK_UNIT_ID_NULL, type, answer,
                     inline_data, length, 1);
    ADLB_CHECK(code);

    WAIT(&req, &status);
  }

  return ADLB_SUCCESS;
}

/**
   Set up direct transfer from putter to worker
 */
static inline adlb_code
redirect_work(int type, int putter, int answer,
              int worker, int length)
{
  DEBUG("redirect: %i->%i", putter, worker);
  struct packed_get_response g;
  g.answer_rank = answer;
  g.code = ADLB_SUCCESS;
  g.length = length;
  g.type = type;
  g.payload_source = putter;
  g.parallelism = 1;
  SEND(&g, sizeof(g), MPI_BYTE, worker, ADLB_TAG_RESPONSE_GET);
  SEND(&worker, 1, MPI_INT, putter, ADLB_TAG_RESPONSE_PUT);

  return ADLB_SUCCESS;
}

static adlb_code
handle_get(int caller)
{
  adlb_code code;
  int type;
  MPI_Status status;

  MPE_LOG(xlb_mpe_svr_get_start);

  RECV(&type, 1, MPI_INT, caller, ADLB_TAG_GET);

  code = process_get_request(caller, type, 1, true);
  ADLB_CHECK(code);

  MPE_LOG(xlb_mpe_svr_get_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_iget(int caller)
{
  int type;
  MPI_Status status;

  // MPE_LOG(xlb_mpe_svr_iget_start);

  RECV(&type, 1, MPI_INT, caller, ADLB_TAG_IGET);
  int matched = check_workqueue(caller, type, 1);

  if (matched == 0)
    send_no_work(caller);

  // MPE_LOG(xlb_mpe_svr_iget_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_amget(int caller)
{
  MPI_Status status;
  adlb_code code;
  struct packed_mget_request req;
  MPE_LOG(xlb_mpe_svr_amget_start);

  RECV(&req, sizeof(req), MPI_BYTE, caller, ADLB_TAG_AMGET);

  code = process_get_request(caller, req.type, req.count, req.blocking);
  ADLB_CHECK(code);

  MPE_LOG(xlb_mpe_svr_amget_end);

  return ADLB_SUCCESS;
}

static adlb_code
process_get_request(int caller, int type, int count, bool blocking)
{
  adlb_code code;

  int matched = check_workqueue(caller, type, count);
  if (matched > 0)
  {
    if (matched == count) return ADLB_SUCCESS;
    blocking = false; // Match should have unblocked
  }

  code = xlb_requestqueue_add(caller, type, count - matched, blocking);
  ADLB_CHECK(code);

  // New request might allow us to release a parallel task
  if (xlb_workq_parallel_tasks() > 0)
  {
    // TODO: for count > 0 this early exit may leave unmatched work
    // without initiating a steal
    code = xlb_check_parallel_queue(type);
    if (code == ADLB_SUCCESS)
      return ADLB_SUCCESS;
    else if (code != ADLB_NOTHING)
      ADLB_CHECK(code);
  }

  if (!stealing && xlb_steal_allowed())
  {
    // Try to initiate a steal to see if we can get work to the worker
    // immediately
    stealing = true;
    adlb_code rc = xlb_try_steal();
    ADLB_CHECK(rc);
    stealing = false;
  }

  return ADLB_SUCCESS;
}

/**
   Find work and send it!
   @return number of matched requests
 */
static inline int
check_workqueue(int caller, int type, int count)
{
  TRACE_START;
  TRACE("check_workqueue: caller=%i count=%i\n", caller, count);
  int matched = 0;
  while (matched < count)
  {
    xlb_work_unit* wu = xlb_workq_get(caller, type);
    if (wu == NULL)
    {
      break;
    }

    send_work_unit(caller, wu);
    xlb_work_unit_free(wu);
    matched++;
  }
  TRACE_END;
  return matched;
}

/**
  Check to see if anything in request queue can be matched to work
  queue for single-worker tasks.  E.g. after a steal.
 */
adlb_code xlb_recheck_queues(bool single, bool parallel)
{
  adlb_code code;
  if (single)
  {
    code = xlb_recheck_single_queues();
    ADLB_CHECK(code);
  }

  if (parallel)
  {
    code = xlb_recheck_parallel_queues();
    ADLB_CHECK(code);
  }

  return ADLB_SUCCESS;
}

static adlb_code
xlb_recheck_single_queues(void)
{
  TRACE_START;

  int N = xlb_requestqueue_size();
  xlb_request_entry* r = malloc((size_t)N*sizeof(xlb_request_entry));
  N = xlb_requestqueue_get(r, N);

  for (int i = 0; i < N; i++)
  {
    int matched = check_workqueue(r[i].rank, r[i].type, r[i].count);
    if (matched > 0)
    {
     xlb_requestqueue_remove(&r[i], matched);
    }
  }

  free(r);
  TRACE_END;
  return ADLB_SUCCESS;
}

/**
  Try to match parallel tasks between work queue and request queue
  return ADLB_SUCCESS if any matches, ADLB_NOTHING if no matches
 */
static adlb_code
xlb_check_parallel_queue(int type)
{
  TRACE_START;
  xlb_work_unit* wu;
  int* ranks = NULL;
  adlb_code result = ADLB_SUCCESS;

  DEBUG("xlb_check_parallel_queue(): size=%"PRId64"",
        xlb_workq_parallel_tasks());

  bool found = xlb_workq_pop_parallel(&wu, &ranks, type);
  if (! found)
  {
    result = ADLB_NOTHING;
    goto end;
  }

  result = send_parallel_work_unit(ranks, wu);
  ADLB_CHECK(result);

  free(ranks);
  xlb_work_unit_free(wu);

  result = ADLB_SUCCESS;
  end:
  TRACE_END;
  return result;
}

static adlb_code
xlb_recheck_parallel_queues(void)
{
  TRACE("check_steal(): rechecking parallel...");

  // Fast path for no parallel task case
  if (xlb_workq_parallel_tasks() == 0)
  {
    return ADLB_SUCCESS;
  }

  for (int t = 0; t < xlb_s.types_size; t++)
  {
    adlb_code rc = xlb_check_parallel_queue(t);
    if (rc != ADLB_SUCCESS && rc != ADLB_NOTHING)
      ADLB_CHECK(rc);
  }
  return ADLB_SUCCESS;
}

static adlb_code
send_parallel_work_unit(int *workers, xlb_work_unit *wu)
{
  return send_parallel_work(workers, wu->id, wu->type, wu->answer,
        wu->payload, wu->length, wu->opts.parallelism);
}

static inline adlb_code send_parallel_work(int *workers,
    xlb_work_unit_id wuid, int type, int answer,
    const void* payload, int length, int parallelism)
{
  INFO("[%i] send_parallel_work: worker=%i",
       xlb_s.layout.rank, workers[0]);
  for (int i = 0; i < parallelism; i++)
  {
    adlb_code rc = send_work(workers[i], wuid, type, answer,
                             payload, length, parallelism);
    ADLB_CHECK(rc);
    SEND(workers, parallelism, MPI_INT, workers[i],
         ADLB_TAG_RESPONSE_GET);
  }
  INFO("[%i] send_parallel_work: OK", xlb_s.layout.rank);
  return ADLB_SUCCESS;
}

/**
   Simple wrapper function
 */
static inline adlb_code
send_work_unit(int worker, xlb_work_unit* wu)
{
  return send_work(worker, wu->id, wu->type, wu->answer,
                   wu->payload, wu->length, wu->opts.parallelism);
}

/**
   Send the work unit to a worker
   Workers are blocked on the recv for this
 */
static adlb_code
send_work(int worker, xlb_work_unit_id wuid, int type, int answer,
          const void* payload, int length, int parallelism)
{
  assert(!xlb_server_shutting_down); // Shouldn't shutdown if have work

  DEBUG("send_work() to: %i wuid: %"PRId64"...", worker, wuid);
  TRACE("work_unit: %s\n", (char*) payload);
  struct packed_get_response g;
  g.answer_rank = answer;
  g.code = ADLB_SUCCESS;
  g.length = length;
  g.payload_source = mpi_rank;
  TRACE("payload_source: %i", g.payload_source);
  g.type = type;
  g.parallelism = parallelism;

  SEND(&g, sizeof(g), MPI_BYTE, worker, ADLB_TAG_RESPONSE_GET);
  SEND(payload, length, MPI_BYTE, worker, ADLB_TAG_WORK);

  return ADLB_SUCCESS;
}

/**
   Send no work unit to a worker
   This is used when worker does an Iget and gets nothing
 */
static inline adlb_code
send_no_work(int worker)
{
  DEBUG("send_no_work() to: %i ...", worker);
  struct packed_get_response g;
  g.code = ADLB_NOTHING;
  g.answer_rank = -1;
  g.length = 0;
  g.payload_source = mpi_rank;
  g.type = -1;

  SEND(&g, sizeof(g), MPI_BYTE, worker, ADLB_TAG_RESPONSE_GET);

  return ADLB_SUCCESS;
}

static adlb_code
handle_create(int caller)
{
  MPE_LOG(xlb_mpe_svr_create_start);
  TRACE("ADLB_TAG_CREATE\n");
  ADLB_create_spec data;
  MPI_Status status;

  RECV(&data, sizeof(data), MPI_BYTE, caller, ADLB_TAG_CREATE_HEADER);

  adlb_data_code dc = ADLB_DATA_SUCCESS;

  adlb_datum_id id = data.id;
  if (id == ADLB_DATA_ID_NULL)
    // Allocate a new id
    dc = xlb_data_unique(&id);

  if (dc == ADLB_DATA_SUCCESS)
    dc = xlb_data_create(id, data.type, &data.type_extra, &data.props);

  struct packed_create_response resp = { .dc = dc, .id = id };
  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  TRACE("ADLB_TAG_CREATE done\n");
  MPE_LOG(xlb_mpe_svr_create_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_multicreate(int caller)
{
  // MPE_LOG(xlb_mpe_svr_multicreate_start);
  TRACE("ADLB_TAG_MULTICREATE\n");

  MPI_Status status;

  int req_bytes;
  adlb_code rc;
  rc = find_req_bytes(&req_bytes, caller, ADLB_TAG_MULTICREATE);
  ADLB_CHECK(rc);

  assert(req_bytes % (int)sizeof(xlb_create_spec) == 0);
  int count = req_bytes / (int)sizeof(xlb_create_spec);
  xlb_create_spec *specs = malloc((size_t)req_bytes);
  RECV(specs, req_bytes, MPI_BYTE, caller, ADLB_TAG_MULTICREATE);

  adlb_datum_id new_ids[count];

  for (int i = 0; i < count; i++)
  {
    new_ids[i] = ADLB_DATA_ID_NULL;
  }

  adlb_data_code dc = xlb_data_multicreate(specs, count, new_ids);

  RSEND(new_ids, (int)sizeof(new_ids), MPI_BYTE, caller,
        ADLB_TAG_RESPONSE);

  free(specs);
  ADLB_DATA_CHECK(dc);

  TRACE("ADLB_TAG_MULTICREATE done\n");
  // MPE_LOG(xlb_mpe_svr_multicreate_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_exists(int caller)
{
  struct packed_bool_resp resp;
  MPI_Status status;

  RECV(xlb_xfer, ADLB_XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_EXISTS);

  adlb_datum_id id;
  adlb_subscript subscript;
  char *xfer_pos = xlb_xfer;
  xfer_pos += xlb_unpack_id_sub(xfer_pos, &id, &subscript);

  adlb_refc decr;
  MSG_UNPACK_BIN(xfer_pos, &decr);

  resp.dc = xlb_data_exists(id, subscript, &resp.result);

  if (resp.dc == ADLB_DATA_SUCCESS)
  {
    adlb_code rc = refcount_decr_helper(id, decr);
    ADLB_CHECK(rc);
  }
  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_store(int caller)
{
  MPE_LOG(xlb_mpe_svr_store_start);
  struct packed_store_hdr hdr;
  MPI_Status status;

  RECV(&hdr, sizeof(struct packed_store_hdr), MPI_BYTE, caller,
       ADLB_TAG_STORE_HEADER);

  char subscript_buf[hdr.subscript_len];
  adlb_subscript subscript = { .key = NULL,
        .length = hdr.subscript_len };
  if (hdr.subscript_len > 0)
  {
    RECV(subscript_buf, (int)hdr.subscript_len, MPI_BYTE, caller,
         ADLB_TAG_STORE_SUBSCRIPT);
    subscript.key = subscript_buf;
    DEBUG("Store: "ADLB_PRIDSUB, ADLB_PRIDSUB_ARGS(hdr.id,
                                        ADLB_DSYM_NULL, subscript));
  }
  else
  {
    DEBUG("Store: "ADLB_PRID,
        ADLB_PRID_ARGS(hdr.id, ADLB_DSYM_NULL));
  }

  void* xfer;
  // Normally, we copy out of the same recv buffer:
  bool xfer_alloced = false;
  if (hdr.length > ADLB_XFER_SIZE)
  {
    xfer_alloced = true;
    xfer = malloc(hdr.length);
    ADLB_CHECK_MALLOC(xfer);
  }
  else
  {
    xfer = xlb_xfer;
  }

  mpi_recv_big(xfer, hdr.length, caller, ADLB_TAG_STORE_PAYLOAD);

  adlb_notif_t notifs = ADLB_NO_NOTIFS;

  bool lost_xfer_ownership;
  adlb_data_code dc =
      xlb_data_store(hdr.id, subscript, xfer, hdr.length, !xfer_alloced,
          &lost_xfer_ownership,
          hdr.type, hdr.refcount_decr, hdr.store_refcounts, &notifs);

  struct packed_store_resp resp = { .dc = dc };
  // Can handle notifications on client or on server
  if (dc != ADLB_DATA_SUCCESS)
  {
    // Send failure return code
    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  }
  else
  {
    adlb_code rc;
    size_t tmp_len = 8192;
    char tmp[tmp_len];
    adlb_buffer tmp_buf = { .data = tmp, .length= tmp_len };
    xlb_prepared_notifs prep;
    bool send_notifs;

    rc = xlb_prepare_notif_work(&notifs, &tmp_buf, &resp.notifs,
                                &prep, &send_notifs);
    ADLB_CHECK(rc);
    DEBUG("handle_store(): sending store response");

    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

    if (send_notifs)
    {
      rc = xlb_send_notif_work(caller, &notifs, &resp.notifs, &prep);
      ADLB_CHECK(rc)
    }
  }

  xlb_free_notif(&notifs);

  if (xfer_alloced && !lost_xfer_ownership) {
    free(xfer);
  }

  TRACE("STORE DONE");
  MPE_LOG(xlb_mpe_svr_store_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_retrieve(int caller)
{
  // TRACE("ADLB_TAG_RETRIEVE");
  MPE_LOG(xlb_mpe_svr_retrieve_start);

  unused double t0 = MPI_Wtime();

  MPI_Status status;

  RECV(xlb_xfer, ADLB_XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_RETRIEVE);

  // Interpret xlb_xfer buffer as struct
  struct packed_retrieve_hdr *hdr =
        (struct packed_retrieve_hdr*) xlb_xfer;
  adlb_subscript subscript = ADLB_NO_SUB;
  if (hdr->subscript_len > 0)
  {
   subscript.key = hdr->subscript;
   subscript.length = hdr->subscript_len;
  }

  adlb_refc decr_self = hdr->refcounts.decr_self;
  adlb_refc incr_referand = hdr->refcounts.incr_referand;

  TRACE("Retrieve: "ADLB_PRIDSUB" decr_self r:%i w:%i "
        "incr_referand r:%i w:%i",
          ADLB_PRIDSUB_ARGS(hdr->id, ADLB_DSYM_NULL, subscript),
          decr_self.read_refcount, decr_self.write_refcount,
          incr_referand.read_refcount, incr_referand.write_refcount);


  adlb_data_code dc;
  adlb_data_type type;
  adlb_binary_data result;
  adlb_notif_t notifs = ADLB_NO_NOTIFS;
  dc = xlb_data_retrieve(hdr->id, subscript, decr_self, incr_referand,
                          &type, &xlb_scratch_buf, &result, &notifs);

  struct retrieve_response_hdr resp_hdr;
  resp_hdr.code = dc;
  resp_hdr.type = type;
  resp_hdr.length = result.length;
  if (dc == ADLB_DATA_SUCCESS)
  {
    adlb_code rc;
    size_t tmp_len = 8192;
    char tmp[tmp_len];
    adlb_buffer tmp_buf = { .data = tmp, .length= tmp_len };
    xlb_prepared_notifs prep;
    bool send_notifs;

    rc = xlb_prepare_notif_work(&notifs, &tmp_buf, &resp_hdr.notifs,
                                &prep, &send_notifs);
    ADLB_CHECK(rc);

    RSEND(&resp_hdr, sizeof(resp_hdr), MPI_BYTE, caller,
          ADLB_TAG_RESPONSE);

    // Send data then notifs
    mpi_send_big(result.data, result.length, caller, ADLB_TAG_RESPONSE);
    DEBUG("Retrieve: "ADLB_PRID,
          ADLB_PRID_ARGS(hdr->id, ADLB_DSYM_NULL));

    if (send_notifs)
    {
      rc = xlb_send_notif_work(caller, &notifs, &resp_hdr.notifs, &prep);
      ADLB_CHECK(rc)
    }
  }
  else
  {
    // Send header only
    RSEND(&resp_hdr, sizeof(resp_hdr), MPI_BYTE, caller,
          ADLB_TAG_RESPONSE);
  }

  xlb_free_notif(&notifs);

  ADLB_Free_binary_data2(&result, xlb_scratch);

  unused double t1 = MPI_Wtime();
  DEBUG("handle_retrieve: rank=%i %8.5f", xlb_s.layout.rank, t1-t0);

  MPE_LOG(xlb_mpe_svr_retrieve_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_enumerate(int caller)
{
  TRACE("ENUMERATE\n");
  struct packed_enumerate opts;
  adlb_code rc;
  MPI_Status status;
  RECV(&opts, sizeof(struct packed_enumerate), MPI_BYTE, caller,
       ADLB_TAG_ENUMERATE);

  adlb_buffer data = { .data = NULL, .length = 0 };
  struct packed_enumerate_result res;
  adlb_data_code dc;
  dc = xlb_data_enumerate(opts.id, opts.count, opts.offset,
                           opts.request_subscripts, opts.request_members,
                           &xlb_xfer_buf, &data, &res.records,
                           &res.key_type, &res.val_type);
  bool free_data = (dc == ADLB_DATA_SUCCESS &&
                    xlb_xfer_buf.data != data.data);
  if (dc == ADLB_DATA_SUCCESS)
  {
    rc = refcount_decr_helper(opts.id, opts.decr);
    ADLB_CHECK(rc);
  }

  res.dc = dc;
  res.length = data.length;


  RSEND(&res, sizeof(res), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  if (dc == ADLB_DATA_SUCCESS)
  {
    if (opts.request_subscripts || opts.request_members)
    {
      rc = mpi_send_big(data.data, data.length,
                        caller, ADLB_TAG_RESPONSE);
      ADLB_CHECK(rc);
    }
  }

  if (free_data)
    free(data.data);
  return ADLB_SUCCESS;
}

static adlb_code
handle_subscribe(int caller)
{
  TRACE("ADLB_TAG_SUBSCRIBE\n");
  MPE_LOG(xlb_mpe_svr_subscribe_start);

  MPI_Status status;
  RECV(xlb_xfer, ADLB_XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_SUBSCRIBE);

  adlb_datum_id id;
  adlb_subscript subscript;
  int work_type;
  const char *xfer_pos = xlb_xfer;
  MSG_UNPACK_BIN(xfer_pos, &work_type);
  xlb_unpack_id_sub(xfer_pos, &id, &subscript);


  if (adlb_has_sub(subscript))
  {
    DEBUG("subscribe: "ADLB_PRIDSUB,
          ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript));
  }
  else
  {
    DEBUG("subscribe: "ADLB_PRID, ADLB_PRID_ARGS(id, ADLB_DSYM_NULL));
  }
  struct pack_sub_resp resp;
  resp.dc = xlb_data_subscribe(id, subscript, caller, work_type,
                              &resp.subscribed);
  if (resp.dc != ADLB_DATA_SUCCESS)
    resp.subscribed = false;
  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  TRACE("ADLB_TAG_SUBSCRIBE done\n");
  MPE_LOG(xlb_mpe_svr_subscribe_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_notify(int caller)
{
  TRACE("ADLB_TAG_NOTIFY\n");

  MPI_Status status;
  RECV(xlb_xfer, ADLB_XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_NOTIFY);
  struct packed_notify_hdr *hdr = (struct packed_notify_hdr *)xlb_xfer;

  xlb_engine_code tc;

  if (hdr->subscript_len > 0)
  {
    adlb_subscript sub = { .key = hdr->subscript,
                           .length = hdr->subscript_len };

    DEBUG("notification received: "ADLB_PRIDSUB, ADLB_PRIDSUB_ARGS(
          hdr->id, ADLB_DSYM_NULL, sub));

    tc = xlb_engine_sub_close(hdr->id, sub, true,
                              &xlb_server_ready_work);
  }
  else
  {
    DEBUG("notification received: "ADLB_PRID, ADLB_PRID_ARGS(hdr->id,
           ADLB_DSYM_NULL));
    tc = xlb_engine_close(hdr->id, true, &xlb_server_ready_work);
  }

  int resp = (tc == XLB_ENGINE_SUCCESS) ? ADLB_SUCCESS : ADLB_ERROR;
  RSEND(&resp, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_get_refcounts(int caller)
{
  adlb_code rc;
  MPI_Status status;
  struct packed_refcounts_req req;
  RECV(&req, sizeof(req), MPI_BYTE, caller, ADLB_TAG_GET_REFCOUNTS);

  DEBUG("Refcount_get: "ADLB_PRID" decr r: %i w: %i",
        ADLB_PRID_ARGS(req.id, ADLB_DSYM_NULL),
        req.decr.read_refcount, req.decr.write_refcount);

  struct packed_refcounts_resp resp;
  resp.dc = xlb_data_get_reference_count(req.id, &resp.refcounts);

  if (resp.dc == ADLB_DATA_SUCCESS)
  {
    rc = refcount_decr_helper(req.id, req.decr);
    ADLB_CHECK(rc);
  }

  // Compensate for decr
  resp.refcounts.read_refcount -= req.decr.read_refcount;
  resp.refcounts.write_refcount -= req.decr.write_refcount;
  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_refcount_incr(int caller)
{
  adlb_code rc;
  MPI_Status status;
  struct packed_incr msg;
  RECV(&msg, sizeof(msg), MPI_BYTE, caller, ADLB_TAG_REFCOUNT_INCR);

  DEBUG("Refcount_incr: "ADLB_PRID" READ %i WRITE %i",
        ADLB_PRID_ARGS(msg.id, ADLB_DSYM_NULL),
        msg.change.read_refcount, msg.change.write_refcount);

  adlb_notif_t notifs = ADLB_NO_NOTIFS;
  adlb_data_code dc = xlb_data_reference_count(msg.id, msg.change,
                                    XLB_NO_ACQUIRE, NULL, &notifs);

  DEBUG("data_reference_count => %i", dc);

  struct packed_incr_resp resp = {
      .success = (dc == ADLB_DATA_SUCCESS)};

  if (dc != ADLB_DATA_SUCCESS)
  {
    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  }
  else
  {
    xlb_prepared_notifs prep;
    bool send_notifs;

    rc = xlb_prepare_notif_work(&notifs, &xlb_xfer_buf, &resp.notifs,
                                &prep, &send_notifs);
    ADLB_CHECK(rc);

    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

    if (send_notifs)
    {
      rc = xlb_send_notif_work(caller, &notifs, &resp.notifs, &prep);
      ADLB_CHECK(rc)
    }
  }

  xlb_free_notif(&notifs);

  return ADLB_SUCCESS;
}

static adlb_code
handle_insert_atomic(int caller)
{
  MPI_Status status;

  RECV(xlb_xfer, ADLB_XFER_SIZE, MPI_CHAR, caller,
       ADLB_TAG_INSERT_ATOMIC);

  adlb_subscript subscript;
  adlb_datum_id id;
  char *xfer_pos = xlb_xfer;
  xfer_pos += xlb_unpack_id_sub(xfer_pos, &id, &subscript);
  bool return_value;
  MSG_UNPACK_BIN(xfer_pos, &return_value);
  adlb_retrieve_refc refcounts;
  MSG_UNPACK_BIN(xfer_pos, &refcounts);
  struct packed_insert_atomic_resp resp;
  resp.value_len = 0; // Default: no data returned

  resp.dc = xlb_data_insert_atomic(id, subscript, &resp.created,
                                   &resp.value_present);

  DEBUG("Insert_atomic: "ADLB_PRIDSUB" => %i",
        ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript),
        resp.created);

  if (resp.dc != ADLB_DATA_SUCCESS)
  {
    // Failed: send response header
    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
    return ADLB_SUCCESS;
  }

  adlb_notif_t notifs = ADLB_NO_NOTIFS;
  adlb_binary_data value;
  bool send_data = return_value && resp.value_present;

  if (resp.value_present)
  {
    // In these cases, need to apply refcount operation
    if (return_value)
    {
      // Retrieve and update references
      // Optionally use xlb_scratch_buf for storage
      resp.dc = xlb_data_retrieve(id, subscript,
            refcounts.decr_self, refcounts.incr_referand,
            &resp.value_type, &xlb_scratch_buf, &value, &notifs);
      resp.value_len = value.length;
    }
    else
    {
      xlb_refc_acquire acq = { .refcounts = refcounts.incr_referand,
          .subscript = subscript };
      // Just update reference counts
      resp.dc = xlb_data_reference_count(id,
            adlb_refc_negate(refcounts.decr_self), acq , NULL, &notifs);
    }
  }

  if (resp.dc != ADLB_DATA_SUCCESS)
  {
    // Failed: send response header
    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
    return ADLB_SUCCESS;
  }

  adlb_code rc;
  size_t tmp_len = 8192;
  char tmp[tmp_len];
  adlb_buffer tmp_buf = { .data = tmp, .length= tmp_len };
  xlb_prepared_notifs prep;
  bool send_notifs;

  rc = xlb_prepare_notif_work(&notifs, &tmp_buf, &resp.notifs,
                              &prep, &send_notifs);
  ADLB_CHECK(rc);

  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  // Send data before notifs
  if (send_data)
  {
    // Send response value
    rc = mpi_send_big(value.data, value.length,
                      caller, ADLB_TAG_RESPONSE);
    ADLB_CHECK(rc);
    ADLB_Free_binary_data2(&value, xlb_scratch);
  }

  if (send_notifs)
  {
    rc = xlb_send_notif_work(caller, &notifs, &resp.notifs, &prep);
    ADLB_CHECK(rc);
  }

  xlb_free_notif(&notifs);
  return ADLB_SUCCESS;
}

static adlb_code
handle_unique(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_unique_start);
  int msg;
  MPI_Status status;
  RECV(&msg, 1, MPI_INT, caller, ADLB_TAG_UNIQUE);

  adlb_datum_id id;
  xlb_data_unique(&id);

  RSEND(&id, 1, MPI_ADLB_ID, caller, ADLB_TAG_RESPONSE);
  DEBUG("Unique: "ADLB_PRID, ADLB_PRID_ARGS(id, ADLB_DSYM_NULL));
  // MPE_LOG_EVENT(mpe_svr_unique_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_typeof(int caller)
{
  adlb_datum_id id;
  MPI_Status status;
  long long i;
  RECV(&i, 1, MPI_ADLB_ID, caller, ADLB_TAG_TYPEOF);
  id = i;

  adlb_data_type type;
  int t;
  adlb_data_code dc = xlb_data_typeof(id, &type);
  if (dc == ADLB_DATA_SUCCESS)
  {
    t = (int) type;
  }
  else
  {
    t = -1;
  }

  RSEND(&t, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_container_typeof(int caller)
{
  adlb_datum_id id;
  MPI_Status status;
  long long i;
  RECV(&i, 1, MPI_ADLB_ID, caller, ADLB_TAG_CONTAINER_TYPEOF);
  id = i;

  adlb_data_type types[2];
  int t[2];
  adlb_data_code dc =
    xlb_data_container_typeof(id, &types[0], &types[1]);
  if (dc == ADLB_DATA_SUCCESS) {
    t[0] = (int) types[0];
    t[1] = (int) types[1];
  }
  else
  {
    t[0] = -1;
    t[1] = -1;
  }

  RSEND(t, 2, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_container_reference(int caller)
{
  MPI_Status status;
  RECV(xlb_xfer, ADLB_XFER_SIZE, MPI_BYTE, caller,
       ADLB_TAG_CONTAINER_REFERENCE);

  adlb_datum_id id, ref_id;
  adlb_subscript subscript, ref_subscript;
  adlb_data_type ref_type;
  adlb_refc transfer_refs; // Refcounts to transfer to dest
  int ref_write_decr; // Decrement for ref_id
  // TODO: support custom decrement of id

  // Unpack data from buffer
  const char *xfer_read = (const char*)xlb_xfer;
  MSG_UNPACK_BIN(xfer_read, &ref_type);

  xfer_read += xlb_unpack_id_sub(xfer_read, &id, &subscript);
  xfer_read += xlb_unpack_id_sub(xfer_read, &ref_id, &ref_subscript);

  MSG_UNPACK_BIN(xfer_read, &transfer_refs);
  MSG_UNPACK_BIN(xfer_read, &ref_write_decr);

  DEBUG("Container_reference: "ADLB_PRIDSUB" => "ADLB_PRIDSUB" "
        "(%s) r: %i w: %i",
        ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript),
        ADLB_PRIDSUB_ARGS(ref_id, ADLB_DSYM_NULL, ref_subscript),
        ADLB_Data_type_tostring(ref_type), transfer_refs.read_refcount,
        transfer_refs.write_refcount);

  adlb_notif_t notifs = ADLB_NO_NOTIFS;
  adlb_binary_data member;

  adlb_data_code dc = xlb_data_container_reference(id,
                        subscript, ref_id, ref_subscript, false,
                        ref_type, transfer_refs, ref_write_decr,
                        &xlb_scratch_buf, &member, &notifs);

  struct packed_cont_ref_resp resp = { .dc = dc };
  if (dc != ADLB_DATA_SUCCESS)
  {
    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  }
  else
  {
    adlb_code rc;
    size_t tmp_len = 8192;
    char tmp[tmp_len];
    adlb_buffer tmp_buf = { .data=tmp, .length=tmp_len };
    xlb_prepared_notifs prep;
    bool send_notifs;

    rc = xlb_prepare_notif_work(&notifs, &tmp_buf, &resp.notifs,
                                &prep, &send_notifs);
    ADLB_CHECK(rc);

    RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

    if (send_notifs)
    {
      rc = xlb_send_notif_work(caller, &notifs, &resp.notifs, &prep);
      ADLB_CHECK(rc)
    }
  }

  xlb_free_notif(&notifs);

  return ADLB_SUCCESS;
}

static adlb_code
handle_container_size(int caller)
{
  adlb_code ac;
  int rc;
  MPI_Status status;
  struct packed_size_req req;
  RECV(&req, sizeof(req), MPI_BYTE, caller, ADLB_TAG_CONTAINER_SIZE);

  int size;
  adlb_data_code dc = xlb_data_container_size(req.id, &size);
  DEBUG("CONTAINER_SIZE: "ADLB_PRID" => %i",
         ADLB_PRID_ARGS(req.id, ADLB_DSYM_NULL), size);

  if (dc == ADLB_DATA_SUCCESS)
  {
    ac = refcount_decr_helper(req.id, req.decr);
    ADLB_CHECK(ac);
  }

  if (dc != ADLB_DATA_SUCCESS)
    size = -1;
  rc = MPI_Rsend(&size, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, xlb_s.comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

static adlb_code
handle_lock(int caller)
{
  adlb_datum_id id;
  MPI_Status status;
  long long i;
  RECV(&i, 1, MPI_ADLB_ID, caller, ADLB_TAG_LOCK);
  id = i;

  DEBUG("Lock: "ADLB_PRID" by rank: %i",
        ADLB_PRID_ARGS(id, ADLB_DSYM_NULL), caller);

  bool result;
  adlb_data_code dc = xlb_data_lock(id, caller, &result);
  char c;
  if (dc == ADLB_DATA_SUCCESS)
  {
    if (result)
      c = '1';
    else
      c = '0';
  }
  else
    c = 'x';
  RSEND(&c, 1, MPI_CHAR, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_unlock(int caller)
{
  adlb_datum_id id;
  MPI_Status status;
  long long i;
  RECV(&i, 1, MPI_ADLB_ID, caller, ADLB_TAG_UNLOCK);
  id = i;

  DEBUG("Unlock: "ADLB_PRID" by rank: %i ",
        ADLB_PRID_ARGS(id, ADLB_DSYM_NULL), caller);

  adlb_data_code dc = xlb_data_unlock(id);

  char c = (dc == ADLB_DATA_SUCCESS) ? '1' : 'x';
  RSEND(&c, 1, MPI_CHAR, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_check_idle(int caller)
{
  MPI_Status status;
  int64_t new_check_attempt;
  RECV(&new_check_attempt, sizeof(new_check_attempt), MPI_BYTE,
       caller, ADLB_TAG_CHECK_IDLE);
  bool idle = xlb_server_check_idle_local(false, new_check_attempt);
  DEBUG("handle_check_idle: %s", bool2string(idle));
  SEND(&idle, sizeof(idle), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  if (idle)
  {
    int request_counts[xlb_s.types_size];
    xlb_requestqueue_type_counts(request_counts, xlb_s.types_size);
    SEND(request_counts, xlb_s.types_size, MPI_INT, caller,
         ADLB_TAG_RESPONSE);

    int untargeted_work_counts[xlb_s.types_size];
    xlb_workq_type_counts(untargeted_work_counts, xlb_s.types_size);
    SEND(untargeted_work_counts, xlb_s.types_size, MPI_INT, caller,
         ADLB_TAG_RESPONSE);
  }
  return ADLB_SUCCESS;
}

/**
  The calling worker rank is blocking on a non-blocking request,
  or unblocked itself
 */
static adlb_code
handle_block_worker(int caller)
{
  MPI_Status status;
  int positive;
  RECV(&positive, 1, MPI_INT, caller, ADLB_TAG_BLOCK_WORKER);

  adlb_code ac;
  if (positive)
  {
     ac = xlb_requestqueue_incr_blocked();
     ADLB_CHECK(ac);
  }
  else
  {
     ac = xlb_requestqueue_decr_blocked();
     ADLB_CHECK(ac);
  }

  return ADLB_SUCCESS;
}

/**
   The calling worker rank is shutting down
 */
static adlb_code
handle_shutdown_worker(int caller)
{
  MPI_Status status;
  RECV(&caller, 0, MPI_INT, caller, ADLB_TAG_SHUTDOWN_WORKER);

  adlb_code code = xlb_shutdown_worker(caller);
  ADLB_CHECK(code);

  return ADLB_SUCCESS;
}

static adlb_code
handle_fail(int caller)
{
  TRACE("");
  MPI_Status status;
  int code;
  RECV(&code, 1, MPI_INT, caller, ADLB_TAG_FAIL);
  // MPE_INFO("ABORT: caller: %i code: ", caller);
  xlb_server_fail(code);
  return ADLB_SUCCESS;
}

// Find the size of a pending request (that has already been detected
// by Iprobe).
static adlb_code find_req_bytes(int *bytes, int caller, adlb_tag tag) {
  MPI_Status req_status;
  int new_msg;
  int mpi_rc = MPI_Iprobe(caller, tag, xlb_s.comm, &new_msg,
                          &req_status);
  MPI_CHECK(mpi_rc);
  assert(new_msg); // should be message
  MPI_Get_count(&req_status, MPI_BYTE, bytes);
  return ADLB_SUCCESS;
}

/*
  Handle notifications server-side and free memory
 */
static adlb_code
notify_helper(adlb_notif_t *notifs)
{
  if (!xlb_notif_empty(notifs))
  {
    adlb_code rc;
    rc = xlb_notify_all(notifs);
    ADLB_CHECK(rc);
    xlb_free_notif(notifs);
  }
  return ADLB_SUCCESS;
}

/*
  Simple helper function to modify reference counts
  TODO: eliminate this, have option of sending all refcounts
  back to client.
 */
static adlb_code
refcount_decr_helper(adlb_datum_id id, adlb_refc decr)
{
  if (!ADLB_REFC_IS_NULL(decr))
  {
    adlb_notif_t notifs = ADLB_NO_NOTIFS;
    adlb_data_code dc;
    dc = xlb_data_reference_count(id, adlb_refc_negate(decr),
                                XLB_NO_ACQUIRE,
                                  NULL, &notifs);
    if (dc == ADLB_DATA_SUCCESS)
    {
      adlb_code rc = notify_helper(&notifs);
      ADLB_CHECK(rc);
    }
  }
  return ADLB_SUCCESS;
}
