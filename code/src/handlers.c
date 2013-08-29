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

static void register_handler(adlb_tag tag, handler h);

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
static adlb_code handle_insert_atomic(int caller);
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

static adlb_code find_req_bytes(int *bytes, int caller, adlb_tag tag);

static inline adlb_code send_work_unit(int worker, xlb_work_unit* wu);

static inline adlb_code send_work(int worker, xlb_work_unit_id wuid, int type,
                                  int answer,
                                  const void* payload, int length,
                                  int parallelism);

static inline adlb_code send_no_work(int worker);

static inline bool check_workqueue(int caller, int type);

static adlb_code
notify_helper(adlb_datum_id id, adlb_ranks *notifications);

static adlb_code
refcount_decr_helper(adlb_datum_id id, adlb_refcounts decr);

/** Is this process currently stealing work? */
static bool stealing = false;
static int deferred_gets = 0;


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
  register_handler(ADLB_TAG_INSERT_ATOMIC, handle_insert_atomic);
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
register_handler(adlb_tag tag, handler h)
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
  RECV(hdr, (int)PACKED_SYNC_SIZE, MPI_BYTE, caller, ADLB_TAG_SYNC_REQUEST);

  adlb_code rc = xlb_handle_accepted_sync(caller, hdr, NULL);
  free(hdr);
  ADLB_CHECK(rc);
  MPE_LOG(xlb_mpe_svr_sync_end);
  return rc;
}



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

  while (true)
  {
    rc = xlb_check_parallel_tasks(p.type);
    ADLB_CHECK(rc);
    if (rc == ADLB_NOTHING)
      break;
  }

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
  assert(length >= 0);
  if (parallelism == 1)
  {
    // Attempt to redirect work unit to another worker
    if (target >= 0)
    {
      CHECK_MSG(target < xlb_comm_size, "Invalid target: %i", target);
      worker = xlb_requestqueue_matches_target(target, type);
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
      worker = xlb_requestqueue_matches_type(type);
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

  xlb_work_unit *work = work_unit_alloc((size_t)length);
  RECV(work->payload, length, MPI_BYTE, putter, ADLB_TAG_WORK);
  DEBUG("work unit: x%i %s ", parallelism, xfer);
  xlb_workq_add(type, putter, priority, answer, target,
                length, parallelism, work);

  return ADLB_SUCCESS;
}

adlb_code xlb_put_targeted_local(int type, int putter, int priority,
      int answer, int target, const void* payload, int length)
{
  assert(xlb_map_to_server(target) == xlb_comm_rank);
  assert(target >= 0);
  int worker;
  int rc;

  DEBUG("xlb_put_targeted_local: to: %i payload: %s", target, (char*) payload);
  assert(length >= 0);

  // Work unit is for this server
  // Is the target already waiting?
  worker = xlb_requestqueue_matches_target(target, type);
  if (worker != ADLB_RANK_NULL)
  {
    xlb_work_unit_id wuid = xlb_workq_unique();
    rc = send_work(target, wuid, type, answer, payload, length, 1);
    ADLB_CHECK(rc);
  }
  else
  {
    xlb_work_unit *work = work_unit_alloc((size_t)length);
    memcpy(work->payload, payload, (size_t)length);
    DEBUG("xlb_put_targeted_local(): server storing work...");
    xlb_workq_add(type, putter, priority, answer, target,
                  length, 1, work);
  }

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

  if (!stealing && xlb_steal_allowed())
  {
    stealing = true;
    int rc = xlb_steal(&stole);
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
    xlb_requestqueue_add(caller, type);
  if (stole)
  {
    DEBUG("handle_get(): steal worked: rechecking...");
    xlb_recheck_queues();
  }

  while (true)
  {
    adlb_code rc = xlb_check_parallel_tasks(type);
    ADLB_CHECK(rc);
    if (rc == ADLB_NOTHING)
      break;
  }

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
  xlb_work_unit* wu = xlb_workq_get(caller, type);
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
xlb_recheck_queues()
{
  TRACE_START;

  int N = xlb_requestqueue_size();
  xlb_request_pair* r = malloc((size_t)N*sizeof(xlb_request_pair));
  N = xlb_requestqueue_get(r, N);

  for (int i = 0; i < N; i++)
    if (check_workqueue(r[i].rank, r[i].type))
      xlb_requestqueue_remove(r[i].rank);

  free(r);
  TRACE_END;
}

/**
   Try to release a parallel task of type
 */
adlb_code
xlb_check_parallel_tasks(int type)
{
  TRACE_START;
  xlb_work_unit* wu;
  int* ranks = NULL;
  adlb_code result = ADLB_SUCCESS;

  TRACE("\t tasks: %"PRId64"\n", xlb_workq_parallel_tasks());

  // Fast path for no parallel task case
  if (xlb_workq_parallel_tasks() == 0)
  {
    result = ADLB_NOTHING;
    goto end;
  }

  bool found = xlb_workq_pop_parallel(&wu, &ranks, type);
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

  end:
  TRACE_END;
  return result;
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
send_work(int worker, xlb_work_unit_id wuid, int type, int answer,
          const void* payload, int length, int parallelism)
{
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


  // DEBUG("CREATE: <%"PRId64"> %s\n", id, (char*) work_buf);
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

  assert(req_bytes % (int)sizeof(ADLB_create_spec) == 0);
  int count = req_bytes / (int)sizeof(ADLB_create_spec);
  ADLB_create_spec *specs = malloc((size_t)req_bytes);
  RECV(specs, req_bytes, MPI_BYTE, caller, ADLB_TAG_MULTICREATE);

  adlb_datum_id new_ids[count];

  adlb_data_code dc = ADLB_DATA_SUCCESS;
  for (int i = 0; i < count; i++) {
    if (specs[i].id != ADLB_DATA_ID_NULL) {
      dc = ADLB_DATA_ERROR_INVALID;
      DEBUG("non-null data id: %"PRId64"", specs[i].id);
      break;
    }
    adlb_datum_id new_id;
    dc = xlb_data_unique(&new_id);
    new_ids[i] = new_id;
    if (dc != ADLB_DATA_SUCCESS)
      break;
    dc = xlb_data_create(new_id, specs[i].type, &specs[i].type_extra,
                     &specs[i].props);
    if (dc != ADLB_DATA_SUCCESS)
      break;
  }


  RSEND(new_ids, (int)sizeof(new_ids), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

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

  RECV(xfer, XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_EXISTS);
  
  adlb_datum_id id;
  const char *subscript;
  int sub_strlen;
  char *xfer_pos = xfer;
  xfer_pos += xlb_unpack_id_sub(xfer_pos, &id, &subscript, &sub_strlen);

  adlb_refcounts decr;
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

  assert(hdr.subscript_len >= 0);
  char subscript_buf[hdr.subscript_len];
  const char *subscript = NULL;
  if (hdr.subscript_len > 0)
  {
    RECV(subscript_buf, hdr.subscript_len, MPI_BYTE, caller,
         ADLB_TAG_STORE_SUBSCRIPT);
    subscript = subscript_buf;
    DEBUG("Store: <%"PRId64">[\"%s\"]", hdr.id, subscript);
  }
  else
  {
    DEBUG("Store: <%"PRId64">", hdr.id);
  }

  // #if HAVE_MALLOC_H
  // struct mallinfo s = mallinfo();
  // DEBUG("Store: heap size: %i", s.uordblks);
  // #endif


  RECV(xfer, XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_STORE_PAYLOAD);

  int length;
  MPI_Get_count(&status, MPI_BYTE, &length);

  adlb_notif_t notifs = ADLB_NO_NOTIFS;

  int dc = xlb_data_store(hdr.id, subscript, xfer, length, hdr.type,
                      hdr.refcount_decr, &notifs);

  struct packed_store_resp resp = {
    .success = (dc == ADLB_DATA_SUCCESS),
    .notifs.notify_closed_count = 0,
    .notifs.notify_insert_count = 0,
    .notifs.reference_count = 0};

  if (ADLB_CLIENT_NOTIFIES)
  {
    // process and remove any local notifications for this server
    xlb_process_local_notif(hdr.id, NULL, &notifs.close_notify);
    xlb_process_local_notif(hdr.id, subscript, &notifs.insert_notify);

    // TODO: process reference setting locally if possible.  This is slightly
    // more complex as we might want to pass the notification work for the
    // set references back to the client

    // will send remaining to client
    resp.notifs.notify_closed_count = notifs.close_notify.count;
    resp.notifs.notify_insert_count = notifs.insert_notify.count;
    resp.notifs.reference_count = notifs.references.count;
  }

  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  // Can handle notifications on client or on server
  if (ADLB_CLIENT_NOTIFIES)
  {
    if (notifs.close_notify.count > 0)
    {
      SEND(notifs.close_notify.ranks, notifs.close_notify.count,
           MPI_INT, caller, ADLB_TAG_RESPONSE);
    }
    if (notifs.insert_notify.count > 0)
    {
      SEND(notifs.insert_notify.ranks, notifs.insert_notify.count,
           MPI_INT, caller, ADLB_TAG_RESPONSE);
    }
    if (notifs.references.count > 0)
    {
      SEND(notifs.references.ids, (int)sizeof(adlb_datum_id) 
          * notifs.references.count, MPI_BYTE, caller, ADLB_TAG_RESPONSE);
    }
  }
  else
  {
    // send notifications by self
    adlb_code rc = xlb_notify_all(&notifs, hdr.id, subscript, xfer, length,
                              hdr.type);
    ADLB_CHECK(rc);
  }

  xlb_free_notif(&notifs);

  TRACE("STORE DONE");
  MPE_LOG(xlb_mpe_svr_store_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_retrieve(int caller)
{
  // TRACE("ADLB_TAG_RETRIEVE");
  MPE_LOG(xlb_mpe_svr_retrieve_start);

  MPI_Status status;

  RECV(xfer, XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_RETRIEVE);

  // Interpret xfer buffer as struct
  struct packed_retrieve_hdr *hdr = (struct packed_retrieve_hdr*)xfer;
  const char *subscript = (hdr->subscript_len <= 0) ? NULL : hdr->subscript;
  adlb_refcounts decr_self = hdr->refcounts.decr_self;
  adlb_refcounts incr_referand = hdr->refcounts.incr_referand;

  TRACE("Retrieve: <%"PRId64">[%s] decr_self r:%i w:%i incr_referand r:%i w:%i",
          hdr->id, subscript, decr_self.read_refcount, decr_self.write_refcount,
          incr_referand.read_refcount, incr_referand.write_refcount);
      

  adlb_binary_data result;
  adlb_data_type type;
  int dc = xlb_data_retrieve(hdr->id, subscript, &type, NULL, &result);
  assert(dc != ADLB_DATA_SUCCESS || result.length >= 0);

  if (dc == ADLB_DATA_SUCCESS && !ADLB_RC_IS_NULL(decr_self)) {
    // Need to copy result if don't own memory
    dc = ADLB_Own_data(NULL, &result);
   
    if (dc == ADLB_DATA_SUCCESS)
    {
      adlb_ranks notify;
      dc = xlb_incr_rc_scav(hdr->id, subscript,
                               result.data, result.length,
                               type, decr_self, incr_referand, &notify);
      if (dc == ADLB_DATA_SUCCESS)
      {
        adlb_code rc = notify_helper(hdr->id, &notify);
        ADLB_CHECK(rc);
      }
    }
  }
  else if (dc == ADLB_DATA_SUCCESS && !ADLB_RC_IS_NULL(incr_referand))
  {
    assert(ADLB_RC_NONNEGATIVE(incr_referand));
    dc = xlb_data_referand_refcount(result.data, result.length, type, hdr->id,
                                incr_referand);
  }

  struct retrieve_response_hdr resp_hdr;
  resp_hdr.code = dc;
  resp_hdr.type = type;
  resp_hdr.length = result.length;
  RSEND(&resp_hdr, sizeof(resp_hdr), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  if (dc == ADLB_DATA_SUCCESS)
  {
    SEND(result.data, result.length, MPI_BYTE, caller, ADLB_TAG_RESPONSE);
    ADLB_Free_binary_data(&result);
    DEBUG("Retrieve: <%"PRId64">", hdr->id);
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

  adlb_buffer data = { .data = NULL, .length = 0 };
  struct packed_enumerate_result res;
  adlb_data_code dc = xlb_data_enumerate(opts.id, opts.count, opts.offset,
                           opts.request_subscripts, opts.request_members,
                           &xfer_buf, &data, &res.records,
                           &res.key_type, &res.val_type);
  bool free_data = (dc == ADLB_DATA_SUCCESS && xfer_buf.data != data.data);
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
      SEND(data.data, data.length, MPI_BYTE, caller, ADLB_TAG_RESPONSE);
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
  RECV(xfer, XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_SUBSCRIBE);
  
  adlb_datum_id id;
  const char *subscript;
  int sub_strlen;
  xlb_unpack_id_sub(xfer, &id, &subscript, &sub_strlen);

  DEBUG("subscribe: <%"PRId64">[%s]", id, subscript);
  struct pack_sub_resp resp;
  int result;
  resp.dc = xlb_data_subscribe(id, subscript, caller, &result);
  if (resp.dc == ADLB_DATA_SUCCESS)
    resp.subscribed = result != 0;
  else
    resp.subscribed = false;
  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  TRACE("ADLB_TAG_SUBSCRIBE done\n");
  MPE_LOG(xlb_mpe_svr_subscribe_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_refcount_incr(int caller)
{
  adlb_code rc;
  MPI_Status status;
  struct packed_incr msg;
  RECV(&msg, sizeof(msg), MPI_BYTE, caller, ADLB_TAG_REFCOUNT_INCR);

  DEBUG("Refcount_incr: <%"PRId64"> READ %i WRITE %i", msg.id,
        msg.change.read_refcount, msg.change.write_refcount);

  adlb_ranks notify_ranks = ADLB_NO_RANKS;
  adlb_data_code dc = xlb_data_reference_count(msg.id, msg.change, NO_SCAVENGE,
                                           NULL, NULL, &notify_ranks);

  DEBUG("data_reference_count => %i", dc);


  struct packed_refcount_resp resp = {
      .success = (dc == ADLB_DATA_SUCCESS),
      .notifs.notify_closed_count = 0,
      .notifs.notify_insert_count = 0,
      .notifs.reference_count = 0};

  if (dc == ADLB_DATA_SUCCESS && ADLB_CLIENT_NOTIFIES)
  {
    // Remove any notifications that can be handled locally
    xlb_process_local_notif(msg.id, NULL, &notify_ranks);
    resp.notifs.notify_closed_count = notify_ranks.count;
  }

  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  if (dc == ADLB_DATA_SUCCESS && notify_ranks.count > 0)
  {
    if (ADLB_CLIENT_NOTIFIES)
    {
      SEND(notify_ranks.ranks, notify_ranks.count,
             MPI_INT, caller, ADLB_TAG_RESPONSE);
    }
    else
    {
      rc = xlb_close_notify(msg.id, NULL, notify_ranks.ranks,
                        notify_ranks.count);
      ADLB_CHECK(rc);
    }
  }
  
  xlb_free_ranks(&notify_ranks);
  return ADLB_SUCCESS;
}

static adlb_code
handle_insert_atomic(int caller)
{
  MPI_Status status;

  RECV(xfer, XFER_SIZE, MPI_CHAR, caller, ADLB_TAG_INSERT_ATOMIC);

  const char *subscript;
  int sub_strlen;
  adlb_datum_id id;
  char *xfer_pos = xfer;
  xfer_pos += xlb_unpack_id_sub(xfer_pos, &id, &subscript, &sub_strlen);
  bool return_value;
  MSG_UNPACK_BIN(xfer_pos, &return_value);

  struct packed_insert_atomic_resp resp;
  resp.value_len = -1; // Default: no data returned

  bool value_present;
  resp.dc = xlb_data_insert_atomic(id, subscript, &resp.created,
                                   &value_present);

  // Only return value if it was already present
  return_value = return_value && !resp.created;

  adlb_binary_data value;
  if (return_value && resp.dc == ADLB_DATA_SUCCESS && value_present)
  {
    // Retrieve, optionally using xfer for storage
    resp.dc = xlb_data_retrieve(id, subscript, &resp.value_type,
                                &xfer_buf, &value);
    resp.value_len = value.length;
  }
  
  DEBUG("Insert_atomic: <%"PRId64">[%s] => %i", id, subscript, resp.created);

  // Send response header
  RSEND(&resp, sizeof(resp), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  
  if (return_value && value_present && resp.dc == ADLB_DATA_SUCCESS)
  {
    // Send response value
    SEND(value.data, value.length, MPI_BYTE, caller, ADLB_TAG_RESPONSE);
    ADLB_Free_binary_data(&value);
  }
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
  DEBUG("Unique: <%"PRId64">", id);
  // MPE_LOG_EVENT(mpe_svr_unique_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_typeof(int caller)
{
  adlb_datum_id id;
  MPI_Status status;
  RECV(&id, 1, MPI_ADLB_ID, caller, ADLB_TAG_TYPEOF);

  adlb_data_type type;
  adlb_data_code dc = xlb_data_typeof(id, &type);
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
  RECV(&id, 1, MPI_ADLB_ID, caller, ADLB_TAG_CONTAINER_TYPEOF);

  adlb_data_type types[2];
  adlb_data_code dc = xlb_data_container_typeof(id, &types[0], &types[1]);
  if (dc != ADLB_DATA_SUCCESS) {
   types[0] = -1;
   types[1] = -1;
  }

  RSEND(types, 2, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_container_reference(int caller)
{
  MPI_Status status;
  RECV(xfer, XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_CONTAINER_REFERENCE);

  adlb_datum_id container_id;
  const char *subscript;
  int sub_strlen;
  adlb_datum_id reference;
  adlb_data_type ref_type;

  // Unpack data from buffer
  void *xfer_read = xfer;
  MSG_UNPACK_BIN(xfer_read, &ref_type);
  MSG_UNPACK_BIN(xfer_read, &reference);

  xlb_unpack_id_sub(xfer_read, &container_id, &subscript, &sub_strlen);

  DEBUG("Container_reference: <%"PRId64">[%s] => <%"PRId64"> (%i)",
        container_id, subscript, reference, ref_type);
  
  adlb_binary_data member;
  adlb_data_code dc = xlb_data_container_reference(container_id,
                        subscript, reference, ref_type, NULL,
                        &member);
  if (dc == ADLB_DATA_SUCCESS)
  {
    // Data was set if the member is present
    if (member.data != NULL)
    {
      // Make sure we own array member data in case freed
      dc = ADLB_Own_data(NULL, &member);
      assert(dc == ADLB_DATA_SUCCESS);

      adlb_ranks notify;
      adlb_refcounts self_decr = ADLB_READ_RC;
      adlb_refcounts referand_incr = ADLB_READ_RC;
      // container_reference must consume a read reference, and we need
      // to increment read refcount of referenced variable
      dc = xlb_incr_rc_scav(container_id, subscript,
                        member.data, member.length,
                        ref_type, self_decr, referand_incr, &notify);

      if (dc == ADLB_DATA_SUCCESS)
      {
        // TODO: offload to client?
        adlb_code rc = xlb_set_ref_and_notify(reference, member.data,
                                      member.length, ref_type);
        ADLB_CHECK(rc);
      }
      if (dc == ADLB_DATA_SUCCESS)
      {
        adlb_code rc = notify_helper(container_id, &notify);
        ADLB_CHECK(rc);
      }
      ADLB_Free_binary_data(&member);
    }
  }
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_container_size(int caller)
{
  int rc;
  MPI_Status status;
  struct packed_size_req req;
  RECV(&req, sizeof(req), MPI_BYTE, caller, ADLB_TAG_CONTAINER_SIZE);

  int size;
  adlb_data_code dc = xlb_data_container_size(req.id, &size);
  DEBUG("CONTAINER_SIZE: <%"PRId64"> => <%i>", req.id, size);

  if (dc == ADLB_DATA_SUCCESS)
  {
    rc = refcount_decr_helper(req.id, req.decr);
    ADLB_CHECK(rc);
  }

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
  adlb_datum_id id;
  MPI_Status status;
  RECV(&id, 1, MPI_ADLB_ID, caller, ADLB_TAG_LOCK);

  DEBUG("Lock: <%"PRId64"> by rank: %i", id, caller);

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
  RECV(&id, 1, MPI_ADLB_ID, caller, ADLB_TAG_UNLOCK);

  DEBUG("Unlock: <%"PRId64"> by rank: %i ", id, caller);

  adlb_data_code dc = xlb_data_unlock(id);

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

/*
  Handle notifications server-side and free memory
  TODO: option to offload to client
 */
static adlb_code
notify_helper(adlb_datum_id id, adlb_ranks *notifications)
{
  if (notifications->count > 0)
  {
    adlb_code rc;
    rc = xlb_close_notify(id, NULL, notifications->ranks,
                          notifications->count);
    ADLB_CHECK(rc);
    xlb_free_ranks(notifications);
  }
  return ADLB_SUCCESS;
}

/*
  Simple helper function to modify reference counts
  TODO: option to offload to client
 */
static adlb_code
refcount_decr_helper(adlb_datum_id id, adlb_refcounts decr)
{
  if (!ADLB_RC_IS_NULL(decr))
  {
    adlb_ranks notify;
    adlb_data_code dc;
    dc = xlb_data_reference_count(id, adlb_rc_negate(decr), NO_SCAVENGE,
                              NULL, NULL, &notify);
    if (dc == ADLB_DATA_SUCCESS)
    {
      adlb_code rc = notify_helper(id, &notify);
      ADLB_CHECK(rc);
    }
  }
  return ADLB_SUCCESS;
}
