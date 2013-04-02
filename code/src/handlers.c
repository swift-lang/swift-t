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

#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "handlers.h"
#include "messaging.h"
#include "mpe-tools.h"
#include "mpi-tools.h"
#include "requestqueue.h"
#include "server.h"
#include "steal.h"
#include "sync.h"
#include "tools.h"
#include "workqueue.h"

/** Type definition of all handler functions */
typedef adlb_code (*handler)(int caller);

/** Maximal number of handlers that may be registered */
#define MAX_HANDLERS XLB_MAX_TAGS

/** Map from incoming message tag to handler function */
static handler handlers[MAX_HANDLERS];

/** Count how many handlers have been registered */
static int handler_count = 0;

/** Copy of this processes' MPI rank */
static int mpi_rank;

static void register_handler_function(adlb_tag tag, handler h);

static adlb_code handle_sync(int caller);
static adlb_code handle_put(int caller);
static adlb_code handle_get(int caller);
static adlb_code handle_iget(int caller);
static adlb_code handle_create(int caller);
static adlb_code handle_multicreate(int caller);
static adlb_code handle_exists(int caller);
static adlb_code handle_store(int caller);
static adlb_code handle_retrieve(int caller);
static adlb_code handle_enumerate(int caller);
static adlb_code handle_subscribe(int caller);
static adlb_code handle_refcount_incr(int caller);
static adlb_code handle_insert(int caller);
static adlb_code handle_insert_atomic(int caller);
static adlb_code handle_lookup(int caller);
static adlb_code handle_unique(int caller);
static adlb_code handle_typeof(int caller);
static adlb_code handle_container_typeof(int caller);
static adlb_code handle_container_reference(int caller);
static adlb_code handle_container_size(int caller);
static adlb_code handle_lock(int caller);
static adlb_code handle_unlock(int caller);
static adlb_code handle_check_idle(int caller);
static adlb_code handle_shutdown_worker(int caller);
static adlb_code handle_shutdown_server(int caller);
static adlb_code handle_fail(int caller);

static adlb_code close_notification(long id, int* ranks, int count);
static adlb_code set_int_reference_and_notify(long id, long value);
static adlb_code set_str_reference_and_notify(long id, char* value);

static adlb_code put_targeted(int type, int putter, int priority,
                              int answer, int target,
                              void* payload, int length);

static adlb_code find_req_bytes(int *bytes, int caller, adlb_tag tag);

#define register_handler(tag, handler) \
{ register_handler_function(tag, handler); \
  xlb_add_tag_name(tag, #tag); }

void
xlb_handlers_init(void)
{
  MPI_Comm_rank(adlb_comm, &mpi_rank);

  memset(handlers, '\0', MAX_HANDLERS*sizeof(handler));

  register_handler(ADLB_TAG_SYNC_REQUEST, handle_sync);
  register_handler(ADLB_TAG_PUT, handle_put);
  register_handler(ADLB_TAG_GET, handle_get);
  register_handler(ADLB_TAG_IGET, handle_iget);
  register_handler(ADLB_TAG_CREATE_HEADER, handle_create);
  register_handler(ADLB_TAG_MULTICREATE, handle_multicreate);
  register_handler(ADLB_TAG_EXISTS, handle_exists);
  register_handler(ADLB_TAG_STORE_HEADER, handle_store);
  register_handler(ADLB_TAG_RETRIEVE, handle_retrieve);
  register_handler(ADLB_TAG_ENUMERATE, handle_enumerate);
  register_handler(ADLB_TAG_SUBSCRIBE, handle_subscribe);
  register_handler(ADLB_TAG_REFCOUNT_INCR, handle_refcount_incr);
  register_handler(ADLB_TAG_INSERT_HEADER, handle_insert);
  register_handler(ADLB_TAG_INSERT_ATOMIC, handle_insert_atomic);
  register_handler(ADLB_TAG_LOOKUP, handle_lookup);
  register_handler(ADLB_TAG_UNIQUE, handle_unique);
  register_handler(ADLB_TAG_TYPEOF, handle_typeof);
  register_handler(ADLB_TAG_CONTAINER_TYPEOF, handle_container_typeof);
  register_handler(ADLB_TAG_CONTAINER_REFERENCE, handle_container_reference);
  register_handler(ADLB_TAG_CONTAINER_SIZE, handle_container_size);
  register_handler(ADLB_TAG_LOCK, handle_lock);
  register_handler(ADLB_TAG_UNLOCK, handle_unlock);
  register_handler(ADLB_TAG_CHECK_IDLE, handle_check_idle);
  register_handler(ADLB_TAG_SHUTDOWN_WORKER, handle_shutdown_worker);
  register_handler(ADLB_TAG_SHUTDOWN_SERVER, handle_shutdown_server);
  register_handler(ADLB_TAG_FAIL, handle_fail);
}

static void
register_handler_function(adlb_tag tag, handler h)
{
  handlers[tag] = h;
  handler_count++;
  valgrind_assert(handler_count < MAX_HANDLERS);
}

bool
xlb_handler_valid(adlb_tag tag)
{
  if (tag >= 0)
    return true;
  return false;
}

adlb_code
xlb_handle(adlb_tag tag, int caller)
{
  CHECK_MSG(xlb_handler_valid(tag), "handle(): invalid tag: %i", tag);
  CHECK_MSG(handlers[tag] != NULL, "handle(): invalid tag: %i", tag);
  DEBUG("handle: caller=%i %s", caller, xlb_get_tag_name(tag));

  MPE_LOG(xlb_mpe_svr_busy_start);

  // Call handler:
  adlb_code result = handlers[tag](caller);

  MPE_LOG(xlb_mpe_svr_busy_end);

  return result;
}

//// Individual handlers follow...

/**
   Incoming sync request: no collision detection necessary
   because this process is not attempting a sync
 */
static adlb_code
handle_sync(int caller)
{
  MPE_LOG(xlb_mpe_svr_sync_start);
  MPI_Status status;
  struct packed_sync *hdr = malloc(PACKED_SYNC_SIZE);
  RECV(hdr, PACKED_SYNC_SIZE, MPI_BYTE, caller, ADLB_TAG_SYNC_REQUEST);

  adlb_code rc = handle_accepted_sync(caller, hdr, NULL);
  free(hdr);
  ADLB_CHECK(rc);
  MPE_LOG(xlb_mpe_svr_sync_end);
  return rc;
}

static inline adlb_code check_parallel_tasks(int work_type);

static adlb_code put(int type, int putter, int priority, int answer,
                     int target, int length, int parallelism);

static adlb_code
handle_put(int caller)
{
  struct packed_put p;
  MPI_Status status;

  MPE_LOG(xlb_mpe_svr_put_start);

  RECV(&p, sizeof(p), MPI_BYTE, caller, ADLB_TAG_PUT);
  mpi_recv_sanity(&status, MPI_BYTE, sizeof(p));

  adlb_code rc;
  rc = put(p.type, p.putter, p.priority, p.answer, p.target,
               p.length, p.parallelism);
  ADLB_CHECK(rc);

  rc = check_parallel_tasks(p.type);
  ADLB_CHECK(rc);

  MPE_LOG(xlb_mpe_svr_put_end);

  return ADLB_SUCCESS;
}

static inline adlb_code redirect_work(int type, int putter,
                                      int priority, int answer,
                                      int target,
                                      int length, int worker);

static adlb_code
put(int type, int putter, int priority, int answer, int target,
    int length, int parallelism)
{
  MPI_Status status;
  int worker;
  if (parallelism == 1)
  {
    // Attempt to redirect work unit to another worker
    if (target >= 0)
    {
      worker = requestqueue_matches_target(target, type);
      if (worker != ADLB_RANK_NULL)
      {
        assert(worker == target);
        redirect_work(type, putter, priority, answer, target,
                      length, worker);
        return ADLB_SUCCESS;
      }
    }
    else
    {
      worker = requestqueue_matches_type(type);
      if (worker != ADLB_RANK_NULL)
      {
        redirect_work(type, putter, priority, answer, target,
                      length, worker);
        return ADLB_SUCCESS;
      }
    }
  }

  // Store this work unit on this server
  DEBUG("server storing work...");
  SEND(&mpi_rank, 1, MPI_INT, putter, ADLB_TAG_RESPONSE_PUT);
  RECV(xfer, length, MPI_BYTE, putter, ADLB_TAG_WORK);
  DEBUG("work unit: x%i %s ", parallelism, xfer);
  workqueue_add(type, putter, priority, answer, target,
                length, parallelism, xfer);

  return ADLB_SUCCESS;
}

/**
   Set up direct transfer from putter to worker
 */
static inline adlb_code
redirect_work(int type, int putter, int priority, int answer,
              int target, int length, int worker)
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

static inline adlb_code send_work_unit(int worker, xlb_work_unit* wu);

static inline adlb_code send_work(int worker, long wuid, int type,
                                  int answer,
                                  void* payload, int length,
                                  int parallelism);

static inline adlb_code send_no_work(int worker);

static inline bool check_workqueue(int caller, int type);

/** Is this process currently stealing work? */
static bool stealing = false;
static int deferred_gets = 0;

static adlb_code
handle_get(int caller)
{
  int type;
  MPI_Status status;

  MPE_LOG(xlb_mpe_svr_get_start);

  RECV(&type, 1, MPI_INT, caller, ADLB_TAG_GET);

  bool found_work = false;
  bool stole = false;
  bool b = check_workqueue(caller, type);
  if (b) goto end;

  if (!stealing && steal_allowed())
  {
    stealing = true;
    int rc = steal(&stole);
    ADLB_CHECK(rc);
    stealing = false;
    if (stole)
      found_work = check_workqueue(caller, type);
  }
  else
  {
    deferred_gets++;
    DEBUG("deferred_gets: %i", deferred_gets);
  }

  DEBUG("stole?: %i", stole);

  if (!found_work)
    requestqueue_add(caller, type);
  if (stole)
  {
    DEBUG("handle_get(): steal worked: rechecking...");
    xlb_requestqueue_recheck();
  }

  adlb_code rc = check_parallel_tasks(type);
  ADLB_CHECK(rc);

  end:
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
  bool b = check_workqueue(caller, type);

  if (!b)
    send_no_work(caller);

  // MPE_LOG(xlb_mpe_svr_iget_end);

  return ADLB_SUCCESS;
}

/**
   Find work and send it!
   @return True if work was found and sent, else false.
 */
static inline bool
check_workqueue(int caller, int type)
{
  TRACE_START;
  xlb_work_unit* wu = workqueue_get(caller, type);
  bool result = false;
  if (wu != NULL)
  {
    send_work_unit(caller, wu);
    work_unit_free(wu);
    result = true;
  }
  TRACE_END;
  return result;
}

/**
   Called after a steal
 */
void
xlb_requestqueue_recheck()
{
  TRACE_START;

  int N = requestqueue_size();
  xlb_request_pair* r = malloc(N*sizeof(xlb_request_pair));
  N = requestqueue_get(r, N);

  for (int i = 0; i < N; i++)
    if (check_workqueue(r[i].rank, r[i].type))
      requestqueue_remove(r[i].rank);

  free(r);
  TRACE_END;
}

/**
   Try to release a parallel task of type
 */
static inline adlb_code
check_parallel_tasks(int type)
{
  TRACE_START;
  xlb_work_unit* wu;
  int* ranks = NULL;

  // Fast path for no parallel task case
  if (workqueue_parallel_tasks() == 0)
    return ADLB_NOTHING;

  bool found = workqueue_pop_parallel(&wu, &ranks, type);
  if (! found)
    return ADLB_NOTHING;
  for (int i = 0; i < wu->parallelism; i++)
  {
    int rc = send_work_unit(ranks[i], wu);
    ADLB_CHECK(rc);
    SEND(ranks, wu->parallelism, MPI_INT, ranks[i],
         ADLB_TAG_RESPONSE_GET);
  }
  free(ranks);
  work_unit_free(wu);
  TRACE_END;
  return ADLB_SUCCESS;
}

/**
   Simple wrapper function
 */
static inline adlb_code
send_work_unit(int worker, xlb_work_unit* wu)
{
  int rc = send_work(worker,
                     wu->id, wu->type, wu->answer,
                     wu->payload, wu->length, wu->parallelism);
  return rc;
}

/**
   Send the work unit to a worker
   Workers are blocked on the recv for this
 */
static inline adlb_code
send_work(int worker, long wuid, int type, int answer,
          void* payload, int length, int parallelism)
{
  DEBUG("send_work() to: %i wuid: %li...", worker, wuid);
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
  struct packed_create_request data;
  MPI_Status status;

  RECV(&data, sizeof(data), MPI_BYTE, caller, ADLB_TAG_CREATE_HEADER);

  adlb_datum_id id = data.id;
  adlb_data_type type = data.type;
  adlb_create_props props =  data.props;
 
  adlb_data_code dc = ADLB_DATA_SUCCESS;
  if (id == ADLB_DATA_ID_NULL)
    // Allocate a new id
    dc = data_unique(&id);

  if (dc == ADLB_DATA_SUCCESS)
    dc = data_create(id, type, &props);
  
  struct packed_create_response resp = { .dc = dc, .id = id };
  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  if (dc == ADLB_DATA_SUCCESS)
  {
    // Containers need additional information
    if (type == ADLB_DATA_TYPE_CONTAINER)
    {
      adlb_data_type container_type;
      RECV(&container_type, 1, MPI_INT, caller,
           ADLB_TAG_CREATE_PAYLOAD);
      data_create_container(id, container_type, &props);
    }
  }

  // DEBUG("CREATE: <%li> %s\n", id, (char*) work_buf);
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
  adlb_code rc = find_req_bytes(&req_bytes, caller, ADLB_TAG_MULTICREATE);
  ADLB_CHECK(rc);

  assert(req_bytes % sizeof(ADLB_create_spec) == 0);
  int count = req_bytes / sizeof(ADLB_create_spec);
  ADLB_create_spec *specs = malloc(req_bytes);
  RECV(specs, req_bytes, MPI_BYTE, caller, ADLB_TAG_MULTICREATE);

  adlb_datum_id new_ids[count];

  adlb_data_code dc = ADLB_DATA_SUCCESS;
  for (int i = 0; i < count; i++) {
    if (specs[i].id != ADLB_DATA_ID_NULL) {
      dc = ADLB_DATA_ERROR_INVALID;
      DEBUG("non-null data id: %li", specs[i].id);
      break;
    }
    adlb_datum_id new_id;
    dc = data_unique(&new_id);
    new_ids[i] = new_id;
    if (dc != ADLB_DATA_SUCCESS)
      break;
    dc = data_create(new_id, specs[i].type, &(specs[i].props));
    if (dc != ADLB_DATA_SUCCESS)
      break;
    if (specs[i].type == ADLB_DATA_TYPE_CONTAINER) {
      dc = data_create_container(new_id, specs[i].subscript_type,
                                &(specs[i].props));
    }
    if (dc != ADLB_DATA_SUCCESS)
      break;
  }
  
  
  RSEND(new_ids, sizeof(new_ids), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  free(specs);
  ADLB_DATA_CHECK(dc);
  
  TRACE("ADLB_TAG_MULTICREATE done\n");
  // MPE_LOG(xlb_mpe_svr_multicreate_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_exists(int caller)
{
  bool result;
  adlb_datum_id id;
  MPI_Status status;

  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_EXISTS);
  data_exists(id, &result);
  RSEND(&result, sizeof(bool), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
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

  // #if HAVE_MALLOC_H
  // struct mallinfo s = mallinfo();
  // DEBUG("Store: heap size: %i", s.uordblks);
  // #endif
  DEBUG("Store: <%li>", hdr.id);

  RECV(xfer, XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_STORE_PAYLOAD);

  int length;
  MPI_Get_count(&status, MPI_BYTE, &length);

  int* ranks;
  int count;
  int dc = data_store(hdr.id, xfer, length, hdr.decr_write_refcount,
                      &ranks, &count);

  if (dc != ADLB_DATA_SUCCESS)
    count = -1;
  RSEND(&count, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  if (count > 0)
  {
    SEND(ranks, count, MPI_INT, caller, ADLB_TAG_RESPONSE);
    free(ranks);
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

  struct packed_retrieve_hdr hdr;
  MPI_Status status;

  RECV(&hdr, sizeof(hdr), MPI_BYTE, caller, ADLB_TAG_RETRIEVE);

  void* result = NULL;
  int length;
  adlb_data_type type;
  int dc = data_retrieve(hdr.id, &type, &result, &length);
  
  bool malloced_result = (type == ADLB_DATA_TYPE_CONTAINER);
  if (dc == ADLB_DATA_SUCCESS && hdr.decr_read_refcount > 0) {
    int notify_count;
    int *notify_ranks;

    // Need to copy result if don't own memory
    if (!malloced_result) {
      void* result_copy = malloc(length);
      malloced_result = true;
      memcpy(result_copy, result, length);
      result = result_copy;
    }

    dc = data_reference_count(hdr.id, ADLB_READ_REFCOUNT,
                              -1 * hdr.decr_read_refcount,
                              &notify_ranks, &notify_count);
    assert(notify_count == 0);  // Shouldn't close anything
  }

  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  if (dc == ADLB_DATA_SUCCESS)
  {
    SEND(&type, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
    SEND(result, length, MPI_BYTE, caller, ADLB_TAG_RESPONSE);
    if (malloced_result)
      free(result);
    DEBUG("Retrieve: <%li>", hdr.id);
  }

  MPE_LOG(xlb_mpe_svr_retrieve_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_enumerate(int caller)
{
  TRACE("ENUMERATE\n");
  struct packed_enumerate opts;
  int rc;
  MPI_Status status;
  RECV(&opts, sizeof(struct packed_enumerate), MPI_BYTE, caller,
       ADLB_TAG_ENUMERATE);

  char* subscripts =
      (void*) (opts.request_subscripts ? NULL+1 : NULL);
  char* members =
      (void*) (opts.request_members ? NULL+1 : NULL);
  int subscripts_length;
  int members_length;
  int actual;
  adlb_data_code dc = data_enumerate(opts.id, opts.count, opts.offset,
                                     &subscripts, &subscripts_length,
                                     &members, &members_length,
                                     &actual);
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  if (dc == ADLB_DATA_SUCCESS)
  {
    rc = MPI_Send(&actual, 1, MPI_INT, caller,
                  ADLB_TAG_RESPONSE, adlb_comm);
    MPI_CHECK(rc);
    if (opts.request_subscripts)
    {
      SEND(subscripts, subscripts_length+1, MPI_BYTE, caller,
           ADLB_TAG_RESPONSE);
      free(subscripts);
    }
    if (opts.request_members)
    {
      SEND(members, members_length, MPI_BYTE, caller,
           ADLB_TAG_RESPONSE);
      free(members);
    }
  }
  return ADLB_SUCCESS;
}

static adlb_code
handle_subscribe(int caller)
{
  TRACE("ADLB_TAG_SUBSCRIBE\n");
  MPE_LOG(xlb_mpe_svr_subscribe_start);

  long id;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_SUBSCRIBE);

  int result;
  adlb_data_code dc = data_subscribe(id, caller, &result);
  if (dc != ADLB_DATA_SUCCESS)
    result = -1;
  RSEND(&result, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  TRACE("ADLB_TAG_SUBSCRIBE done\n");
  MPE_LOG(xlb_mpe_svr_subscribe_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_refcount_incr(int caller)
{
  MPI_Status status;
  struct packed_incr msg;
  RECV(&msg, sizeof(msg), MPI_BYTE, caller, ADLB_TAG_REFCOUNT_INCR);

  if (msg.type == ADLB_READ_REFCOUNT) {
    DEBUG("Refcount_incr: <%li> READ_REFCOUNT %i", msg.id, msg.incr);
  } else if (msg.type == ADLB_WRITE_REFCOUNT) {
    DEBUG("Refcount_incr: <%li> WRITE_REFCOUNT %i", msg.id, msg.incr);
  } else {
    assert(msg.type == ADLB_READWRITE_REFCOUNT);
    DEBUG("Refcount_incr: <%li> READWRITE_REFCOUNT %i", msg.id, msg.incr);
  }

  int notify_count;
  int *notify_ranks;
  adlb_data_code dc = data_reference_count(msg.id, msg.type,
                     msg.incr, &notify_ranks, &notify_count);
  if (dc != ADLB_DATA_SUCCESS) {
    return ADLB_ERROR;
  }

  if (notify_count > 0) {
    close_notification(msg.id, notify_ranks, notify_count);
    free(notify_ranks);
  }

  DEBUG("data_reference_count => %i", dc);
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_insert(int caller)
{
  MPE_LOG(xlb_mpe_svr_insert_start);
  MPI_Status status;
  RECV(xfer, ADLB_DATA_SUBSCRIPT_MAX+128, MPI_CHAR, caller,
       ADLB_TAG_INSERT_HEADER);

  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  long id;
  char* member;
  int  member_length;
  int  drops;
  int n;
  n = sscanf(xfer, "%li %s %i %i",
             &id, subscript, &member_length, &drops);
  // This can only fail on an internal error:
  ASSERT(n == 4);

  member = malloc((member_length+1) * sizeof(char));

  int references_count, notify_count;
  long* references;
  int *notify_ranks;

  RECV(member, ADLB_DATA_MEMBER_MAX, MPI_CHAR, caller,
       ADLB_TAG_INSERT_PAYLOAD);

  DEBUG("Insert: <%li>[%s]=%s",
        id, subscript, member);

  adlb_data_code dc = data_insert(id, subscript, member, drops,
                                  &references, &references_count,
                                  &notify_ranks, &notify_count);
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  if (dc == ADLB_DATA_SUCCESS)
  {
    if (references_count > 0)
    {
      long m = -1;
      bool parsed = false;
      for (int i = 0; i < references_count; i++)
      {
        TRACE("Notifying reference %li\n", references[i]);
        // Negative used to indicate string
        if (references[i] >= 0)
        {
          if (!parsed) {
            parsed = true;
            n = sscanf(member, "%li", &m);
            assert(n == 1);
          }
          set_int_reference_and_notify(references[i], m);
        }
        else
        {
          set_str_reference_and_notify(references[i] * -1, member);
        }
      }
      free(references);
    }
    // Notify of entire container closed
    if (notify_count > 0) {
      close_notification(id, notify_ranks, notify_count);
    }
  }
  TRACE("INSERT DONE");
  MPE_LOG(xlb_mpe_svr_insert_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_insert_atomic(int caller)
{
  MPI_Status status;

  RECV(xfer, ADLB_DATA_SUBSCRIPT_MAX+128, MPI_CHAR, caller,
       ADLB_TAG_INSERT_ATOMIC);

  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  long id;
  int n = sscanf(xfer, "%li %s", &id, subscript);
  // This can only fail on an internal error:
  ASSERT(n == 2);

  bool result;
  adlb_data_code dc = data_insert_atomic(id, subscript, &result);
  DEBUG("Insert_atomic: <%li>[%s] => %i",
        id, subscript, result);
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  RSEND(&result, sizeof(bool), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  return ADLB_SUCCESS;
}

static adlb_code
handle_lookup(int caller)
{
  MPE_LOG(xlb_mpe_svr_lookup_start);
  MPI_Status status;
  TRACE("ADLB_TAG_LOOKUP\n");
  char msg[ADLB_DATA_SUBSCRIPT_MAX+32];
  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  char* member;
  RECV(msg, ADLB_DATA_SUBSCRIPT_MAX+32, MPI_BYTE, caller,
       ADLB_TAG_LOOKUP);

  long id;
  int n = sscanf(msg, "%li %s", &id, subscript);
  ASSERT(n == 2);

  adlb_data_code dc = data_lookup(id, subscript, &member);

  struct packed_code_length p;
  p.code = dc;
  // Set this field to 1 if we found the entry, else -1
  bool found = (member != ADLB_DATA_ID_NULL);
  p.length = found ? 1 : -1;

  RSEND(&p, sizeof(p), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  if (dc == ADLB_DATA_SUCCESS && found)
  {
    SEND(member, strlen(member)+1, MPI_CHAR, caller,
         ADLB_TAG_RESPONSE);
  }
  // DEBUG("LOOKUP: <%li>[\"%s\"] => <%li>\n",
  //       id, subscript, member);
  TRACE("ADLB_TAG_LOOKUP done\n");
  MPE_LOG(xlb_mpe_svr_lookup_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_unique(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_unique_start);
  int msg;
  MPI_Status status;
  RECV(&msg, 1, MPI_INT, caller, ADLB_TAG_UNIQUE);

  long id;
  data_unique(&id);

  RSEND(&id, 1, MPI_LONG, caller, ADLB_TAG_RESPONSE);
  DEBUG("Unique: <%li>", id);
  // MPE_LOG_EVENT(mpe_svr_unique_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_typeof(int caller)
{
  adlb_datum_id id;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_TYPEOF);

  adlb_data_type type;
  adlb_data_code dc = data_typeof(id, &type);
  if (dc != ADLB_DATA_SUCCESS)
    type = -1;

  RSEND(&type, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_container_typeof(int caller)
{
  adlb_datum_id id;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_CONTAINER_TYPEOF);

  adlb_data_type type;
  adlb_data_code dc = data_container_typeof(id, &type);
  if (dc != ADLB_DATA_SUCCESS)
    type = -1;

  RSEND(&type, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_container_reference(int caller)
{
  MPI_Status status;
  RECV(xfer, XFER_SIZE, MPI_BYTE, caller,
       ADLB_TAG_CONTAINER_REFERENCE);

  long container_id;
  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  long reference;
  adlb_data_type ref_type;
  int n = sscanf(xfer, "%li %li %s %i",
            &reference, &container_id, subscript,
            (int*)&ref_type);
  ASSERT(n == 4);

  DEBUG("Container_reference: <%li>[%s] => <%li> (%i)",
        container_id, subscript, reference, ref_type);

  char *member;
  adlb_data_code dc = data_container_reference_str(container_id,
                        subscript, reference, ref_type, &member);
  if (dc == ADLB_DATA_SUCCESS)
    if (member != 0)
            {
              if (ref_type == ADLB_DATA_TYPE_INTEGER)
              {
                long m;
                int n = sscanf(member, "%li", &m);
                ASSERT(n == 1);
                set_int_reference_and_notify(reference, m);
              }
              else if (ref_type == ADLB_DATA_TYPE_STRING)
                set_str_reference_and_notify(reference, member);
              else
                assert(false);
            }

  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_container_size(int caller)
{
  long container_id;
  int rc;
  MPI_Status status;
  RECV(&container_id, 1, MPI_LONG, caller, ADLB_TAG_CONTAINER_SIZE);

  int size;
  adlb_data_code dc = data_container_size(container_id, &size);
  DEBUG("CONTAINER_SIZE: <%li> => <%i>",
        container_id, size);

  if (dc != ADLB_DATA_SUCCESS)
    size = -1;
  rc = MPI_Rsend(&size, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

static adlb_code
handle_lock(int caller)
{
  long id;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_LOCK);

  DEBUG("Lock: <%li> by rank: %i", id, caller);

  bool result;
  adlb_data_code dc = data_lock(id, caller, &result);
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
  long id;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_UNLOCK);

  DEBUG("Unlock: <%li> by rank: %i ", id, caller);

  adlb_data_code dc = data_unlock(id);

  char c = (dc == ADLB_DATA_SUCCESS) ? '1' : 'x';
  RSEND(&c, 1, MPI_CHAR, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_check_idle(int caller)
{
  MPI_Status status;
  RECV_TAG(caller, ADLB_TAG_CHECK_IDLE);
  bool idle = xlb_server_check_idle_local();
  DEBUG("handle_check_idle: %s", bool2string(idle));
  SEND(&idle, sizeof(idle), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
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
handle_shutdown_server(int caller)
{
  MPE_LOG(xlb_mpe_svr_shutdown_start);
  // caller is a server
  xlb_server_shutdown();
  MPE_LOG(xlb_mpe_svr_shutdown_end);
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

static adlb_code
put_targeted(int type, int putter, int priority, int answer,
             int target, void* payload, int length);

static adlb_code
close_notification(long id, int* ranks, int count)
{
  for (int i = 0; i < count; i++)
  {
    char t[32];
    int length = sprintf(t, "close %li", id);

    put_targeted(1,        // work_type CONTROL
                 mpi_rank, // putter
                 1,        // work_prio
                 -1,       // answer_rank
                 ranks[i], // target_rank
                 t,        // work_buf
                 length+1  // work_len
    );
  }
  return ADLB_SUCCESS;
}

static adlb_code
set_int_reference_and_notify(long id, long value)
{
  DEBUG("set_reference: <%li>=%li", id, value);
  DEBUG("set_int_reference: <%li>=%li", id, value);
  int* ranks;
  int count;

  adlb_code rc = ADLB_SUCCESS;
  int server = ADLB_Locate(id);
  if (server != xlb_comm_rank)
    rc = xlb_sync(server);
  ADLB_CHECK(rc);
  rc = ADLB_Store(id, &value, sizeof(long), true, &ranks, &count);
  ADLB_CHECK(rc);
  if (count > 0)
  {
    rc = close_notification(id, ranks, count);
    ADLB_CHECK(rc);
    free(ranks);
  }
  TRACE("SET_INT_REFERENCE DONE");
  return ADLB_SUCCESS;
}

static adlb_code
set_str_reference_and_notify(long id, char *value)
{
  DEBUG("set_str_reference: <%li>=%s", id, value);
  int* ranks;
  int count;

  int rc = ADLB_SUCCESS;
  int server = ADLB_Locate(id);
  if (server != xlb_comm_rank)
    rc = xlb_sync(server);
  ADLB_CHECK(rc);
  rc = ADLB_Store(id, value, (strlen(value)+1) * sizeof(char), true,
                  &ranks, &count);
  ADLB_CHECK(rc);
  rc = close_notification(id, ranks, count);
  ADLB_CHECK(rc);
  TRACE("SET_STR_REFERENCE DONE");
  return ADLB_SUCCESS;
}

static adlb_code
put_targeted(int type, int putter, int priority, int answer,
             int target, void* payload, int length)
{
  int worker;
  int rc;

  DEBUG("put_targeted: to: %i payload: %s", target, (char*) payload);

  if (target >= 0)
  {
    int server = xlb_map_to_server(target);
    if (server == xlb_comm_rank)
    {
      // Work unit is for this server
      // Is the target already waiting?
      worker = requestqueue_matches_target(target, type);
      if (worker != ADLB_RANK_NULL)
      {
        int wuid = workqueue_unique();
        rc = send_work(target, wuid, type, answer, payload, length, 1);
        ADLB_CHECK(rc);
        return ADLB_SUCCESS;
      }
      else
      {
        xlb_work_unit *work = work_unit_alloc(length);
        memcpy(work->payload, payload, length);
        DEBUG("put_targeted(): server storing work...");
        workqueue_add(type, putter, priority, answer, target,
                      length, 1, work);
      }
    }
    else
    {
      // Work unit is for another server
      rc = xlb_sync(server);
      ADLB_CHECK(rc);
      rc = ADLB_Put(payload, length, target, answer,
                        type, priority, 1);
      ADLB_CHECK(rc);
    }
  }


  return ADLB_SUCCESS;
}

// Find the size of a pending request (that has already been detected
// by Iprobe).
static adlb_code find_req_bytes(int *bytes, int caller, adlb_tag tag) {
  MPI_Status req_status;
  int new_msg;
  int mpi_rc = MPI_Iprobe(caller, tag, adlb_comm, &new_msg,
                          &req_status);
  MPI_CHECK(mpi_rc);
  assert(new_msg); // should be message
  MPI_Get_count(&req_status, MPI_BYTE, bytes);
  return ADLB_SUCCESS;
}
