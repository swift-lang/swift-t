
/*
 * handlers.c
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()
#include <assert.h>
#include <alloca.h>
#include <stdio.h>

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
#include "tools.h"
#include "workqueue.h"

#define MAX_HANDLERS 128

typedef adlb_code (*handler)(int caller);

/** Map from incoming message tag to handler function */
static handler handlers[MAX_HANDLERS];

static int handler_count = 0;

/** Local MPI rank */
static int mpi_rank;

static void create_handler(adlb_tag tag, handler h);

static adlb_code handle_sync(int caller);
static adlb_code handle_put(int caller);
static adlb_code handle_get(int caller);
static adlb_code handle_steal(int caller);
static adlb_code handle_create(int caller);
static adlb_code handle_exists(int caller);
static adlb_code handle_store(int caller);
static adlb_code handle_retrieve(int caller);
static adlb_code handle_enumerate(int caller);
static adlb_code handle_close(int caller);
static adlb_code handle_subscribe(int caller);
static adlb_code handle_slot_create(int caller);
static adlb_code handle_slot_drop(int caller);
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
static adlb_code handle_abort(int caller);

static adlb_code slot_notification(long id);
static int close_notification(long id, int* ranks, int count);
static int set_reference_and_notify(long id, long value);

static adlb_code put_targeted(int type, int putter, int priority,
                              int answer, int target,
                              void* payload, int length);

void
handlers_init(void)
{
  MPI_Comm_rank(adlb_all_comm, &mpi_rank);

  memset(handlers, '\0', MAX_HANDLERS*sizeof(handler));

  create_handler(ADLB_TAG_SYNC_REQUEST, handle_sync);
  create_handler(ADLB_TAG_PUT, handle_put);
  create_handler(ADLB_TAG_GET, handle_get);
  create_handler(ADLB_TAG_STEAL, handle_steal);
  create_handler(ADLB_TAG_CREATE_HEADER, handle_create);
  create_handler(ADLB_TAG_EXISTS, handle_exists);
  create_handler(ADLB_TAG_STORE_HEADER, handle_store);
  create_handler(ADLB_TAG_RETRIEVE, handle_retrieve);
  create_handler(ADLB_TAG_ENUMERATE, handle_enumerate);
  create_handler(ADLB_TAG_CLOSE, handle_close);
  create_handler(ADLB_TAG_SUBSCRIBE, handle_subscribe);
  create_handler(ADLB_TAG_SLOT_CREATE, handle_slot_create);
  create_handler(ADLB_TAG_SLOT_DROP, handle_slot_drop);
  create_handler(ADLB_TAG_INSERT_HEADER, handle_insert);
  create_handler(ADLB_TAG_INSERT_ATOMIC, handle_insert_atomic);
  create_handler(ADLB_TAG_LOOKUP, handle_lookup);
  create_handler(ADLB_TAG_UNIQUE, handle_unique);
  create_handler(ADLB_TAG_TYPEOF, handle_typeof);
  create_handler(ADLB_TAG_CONTAINER_TYPEOF, handle_container_typeof);
  create_handler(ADLB_TAG_CONTAINER_REFERENCE, handle_container_reference);
  create_handler(ADLB_TAG_CONTAINER_SIZE, handle_container_size);
  create_handler(ADLB_TAG_LOCK, handle_lock);
  create_handler(ADLB_TAG_UNLOCK, handle_unlock);
  create_handler(ADLB_TAG_CHECK_IDLE, handle_check_idle);
  create_handler(ADLB_TAG_SHUTDOWN_WORKER, handle_shutdown_worker);
  create_handler(ADLB_TAG_SHUTDOWN_SERVER, handle_shutdown_server);
  create_handler(ADLB_TAG_ABORT, handle_abort);
}

static void
create_handler(adlb_tag tag, handler h)
{
  handlers[tag] = h;
  handler_count++;
}

bool
handler_valid(adlb_tag tag)
{
  if (tag >= 0)
    return true;
  return false;
}

adlb_code
handle(adlb_tag tag, int caller)
{
  CHECK_MSG(handler_valid(tag), "handle(): invalid tag: %i\n", tag);
  CHECK_MSG(handlers[tag] != NULL, "handle(): invalid tag: %i", tag);
  DEBUG("handle: caller=%i %s", caller, xlb_get_tag_name(tag));

  // Call handler:
  adlb_code result = handlers[tag](caller);

  // Update timestamp:
  if (tag != ADLB_TAG_CHECK_IDLE &&
      tag != ADLB_TAG_SYNC_REQUEST &&
      tag != ADLB_TAG_STEAL)
    xlb_time_last_action = MPI_Wtime();

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
  MPI_Status status;

  RECV_TAG(caller, ADLB_TAG_SYNC_REQUEST);
  int rc = xlb_serve_server(caller);
  ADLB_CHECK(rc);
  return rc;
}

static adlb_code put(int type, int putter, int priority, int answer,
                     int target, int length);

static adlb_code
handle_put(int caller)
{
  struct packed_put p;
  MPI_Status status;
  int rc;

  MPE_LOG(xlb_mpe_svr_put_start);

  RECV(&p, sizeof(p), MPI_BYTE, caller, ADLB_TAG_PUT);

  mpi_recv_sanity(&status, MPI_BYTE, sizeof(p));

  put(p.type, p.putter, p.priority, p.answer, p.target, p.length);

  MPE_LOG(xlb_mpe_svr_put_end);
  STATS("PUT");

  return ADLB_SUCCESS;
}

static inline adlb_code redirect_work(int type, int putter,
                                      int priority, int answer,
                                      int target,
                                      int length, int worker);

static adlb_code
put(int type, int putter, int priority, int answer, int target,
    int length)
{
  int rc;
  MPI_Status status;
  int next_worker = 0;
  int worker;
  if (target >= 0)
  {
    worker = requestqueue_matches_target(target, type);
    if (worker != ADLB_RANK_NULL)
    {
      redirect_work(type, putter, priority, answer, target,
                    length, worker);
      return ADLB_SUCCESS;
    }
  }
  worker = requestqueue_matches_type(type);
  if (worker != ADLB_RANK_NULL)
  {
    redirect_work(type, putter, priority, answer, target,
                  length, worker);
    return ADLB_SUCCESS;
  }

  DEBUG("server storing work...");

  SEND(&mpi_rank, 1, MPI_INT, putter, ADLB_TAG_RESPONSE_PUT);
  RECV(xfer, length, MPI_BYTE, putter, ADLB_TAG_WORK);

  DEBUG("work unit: %s", xfer);

  // Enqueue this
  workqueue_add(type, putter, priority, answer, target,
                length, xfer);

  return ADLB_SUCCESS;
}

/**
   Set up direct transfer from putter to worker
 */
static inline adlb_code
redirect_work(int type, int putter, int priority, int answer,
              int target, int length, int worker)
{
  int rc;
  DEBUG("redirect: %i->%i", putter, worker);
  struct packed_get_response g;
  g.answer_rank = answer;
  g.code = ADLB_SUCCESS;
  g.length = length;
  g.type = type;
  g.payload_source = putter;
  DEBUG("redirect: worker");
  SEND(&g, sizeof(g), MPI_BYTE, worker, ADLB_TAG_RESPONSE_GET);
  DEBUG("redirect: putter");
  SEND(&worker, 1, MPI_INT, putter, ADLB_TAG_RESPONSE_PUT);
  MPI_CHECK(rc);

  return ADLB_SUCCESS;
}

static inline adlb_code send_work_unit(int worker, xlb_work_unit* wu);

static inline adlb_code send_work(int worker, long wuid, int type,
                                  int answer,
                                  void* payload, int length);

static inline bool check_workqueue(int caller, int type);

/** Is this process currently stealing work? */
static bool stealing = false;
static int deferred_gets = 0;

static adlb_code
handle_get(int caller)
{
  struct packed_get p;
  MPI_Status status;
  int rc;

  MPE_LOG(xlb_mpe_svr_get_start);

  RECV(&p, sizeof(p), MPI_BYTE, caller, ADLB_TAG_GET);

  bool found_work = false;
  bool stole = false;
  bool b = check_workqueue(caller, p.type);
  if (b) goto end;

  if (!stealing)
  {
    stealing = true;
    rc = steal(&stole);
    ADLB_CHECK(rc);
    stealing = false;
    if (stole)
      found_work = check_workqueue(caller, p.type);
  }
  else
  {
    deferred_gets++;
    DEBUG("deferred_gets: %i", deferred_gets);
  }

  DEBUG("stole?: %i", stole);

  if (!found_work)
    requestqueue_add(caller, p.type);
  if (stole)
  {
    DEBUG("rechecking...");
    requestqueue_recheck();
  }

  end:
  MPE_LOG(xlb_mpe_svr_get_end);

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
requestqueue_recheck()
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
   Simple wrapper function
 */
static inline adlb_code
send_work_unit(int worker, xlb_work_unit* wu)
{
  int rc = send_work(worker,
                     wu->id, wu->type, wu->answer,
                     wu->payload, wu->length);
  return rc;
}

/**
   Send the work unit to a worker
   Workers are blocked on the recv for this
 */
static inline adlb_code
send_work(int worker, long wuid, int type, int answer,
          void* payload, int length)
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

  int rc;
  SEND(&g, sizeof(g), MPI_BYTE, worker, ADLB_TAG_RESPONSE_GET);
  SEND(payload, length, MPI_BYTE, worker, ADLB_TAG_WORK);

  STATS("SEND_WORK");

  return ADLB_SUCCESS;
}

static adlb_code
handle_steal(int caller)
{
  TRACE_START;
  MPE_LOG(xlb_mpe_svr_steal_start);
  DEBUG("\t caller: %i", caller);

  MPI_Status status;

  int rc;
  int count;
  xlb_work_unit** stolen;
  // Maximum amount of memory to return- currently unused
  int max_memory;
  RECV(&max_memory, 1, MPI_INT, caller, ADLB_TAG_STEAL);

  workqueue_steal(max_memory, &count, &stolen);
  STATS("LOST: %i", count);
  // MPE_INFO(xlb_mpe_svr_info, "LOST: %i TO: %i", count, caller);

  RSEND(&count, 1, MPI_INT, caller, ADLB_TAG_RESPONSE_STEAL_COUNT);

  if (count == 0)
    goto end;

  int p_length = count*sizeof(struct packed_put);
  struct packed_put* p = alloca(count*sizeof(struct packed_put));
  for (int i = 0; i < count; i++)
    xlb_pack_work_unit(&p[i], stolen[i]);
  SEND(p, p_length, MPI_BYTE, caller, ADLB_TAG_RESPONSE_STEAL);

  for (int i = 0; i < count; i++)
  {
    DEBUG("stolen payload: %s", (char*) stolen[i]->payload);
    SEND(stolen[i]->payload, stolen[i]->length, MPI_BYTE, caller,
         ADLB_TAG_RESPONSE_STEAL);
  }

  for (int i = 0; i < count; i++)
  {
    free(stolen[i]->payload);
    free(stolen[i]);
  }
  free(stolen);

  end:
  MPE_LOG(xlb_mpe_svr_steal_end);
  TRACE_END;
  return ADLB_SUCCESS;
}

static adlb_code
handle_create(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_create_start);
  TRACE("ADLB_TAG_CREATE\n");
  struct packed_id_type data;
  int rc;
  MPI_Status status;

  RECV(&data, sizeof(struct packed_id_type), MPI_BYTE, caller,
       ADLB_TAG_CREATE_HEADER);

  long id = data.id;
  adlb_data_type type = data.type;

  adlb_data_code dc = data_create(id, type);

  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  // Types file and container need additional information
  if (type == ADLB_DATA_TYPE_FILE)
  {
    RECV(xfer, XFER_SIZE, MPI_CHAR, caller, ADLB_TAG_CREATE_PAYLOAD);
    data_create_filename(id, xfer);
  }
  else if (type == ADLB_DATA_TYPE_CONTAINER)
  {
    adlb_data_type container_type;
    RECV(&container_type, 1, MPI_INT, caller,
         ADLB_TAG_CREATE_PAYLOAD);
    data_create_container(id, container_type);
  }

  // DEBUG("CREATE: <%li> %s\n", id, (char*) work_buf);
  TRACE("ADLB_TAG_CREATE done\n");
  // MPE_LOG_EVENT(mpe_svr_create_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_exists(int caller)
{
  bool result;
  adlb_datum_id id;
  int rc;
  MPI_Status status;

  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_EXISTS);
  data_exists(id, &result);
  RSEND(&result, sizeof(bool), MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_store(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_store_start);
  long id;
  int rc;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_STORE_HEADER);

  DEBUG("Store: <%li>", id);

  RECV(xfer, XFER_SIZE, MPI_BYTE, caller, ADLB_TAG_STORE_PAYLOAD);

  int length;
  MPI_Get_count(&status, MPI_BYTE, &length);

  int dc = data_store(id, xfer, length);

  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  TRACE("STORE DONE");
  // MPE_LOG_EVENT(mpe_svr_store_end);

  return ADLB_SUCCESS;
}

static adlb_code
handle_retrieve(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_retrieve_start);
  // TRACE("ADLB_TAG_RETRIEVE");
  long id;
  int rc;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_RETRIEVE);

  void* result = NULL;
  int length;
  adlb_data_type type;
  int dc = data_retrieve(id, &type, &result, &length);
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  if (dc == ADLB_DATA_SUCCESS)
  {
    SEND(&type, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
    SEND(result, length, MPI_BYTE, caller, ADLB_TAG_RESPONSE);
    if (type == ADLB_DATA_TYPE_CONTAINER)
      free(result);
    DEBUG("Retrieve: <%li>", id);
  }
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
                  ADLB_TAG_RESPONSE, adlb_all_comm);
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
handle_close(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_close_start);
  long id;
  int rc;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_CLOSE);

  DEBUG("Close: <%li>", id);

  int* ranks;
  int count;
  adlb_data_code dc = data_close(id, &ranks, &count);
  if (dc != ADLB_DATA_SUCCESS)
    count = -1;
  RSEND(&count, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  if (count > 0)
  {
    SEND(ranks, count, MPI_INT, caller, ADLB_TAG_RESPONSE);
    free(ranks);
  }

  //  MPE_LOG_EVENT(mpe_svr_close_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_subscribe(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_subscribe_start);
  TRACE("ADLB_TAG_SUBSCRIBE\n");

  long id;
  int rc;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_SUBSCRIBE);

  int result;
  adlb_data_code dc = data_subscribe(id, caller, &result);
  if (dc != ADLB_DATA_SUCCESS)
    result = -1;
  RSEND(&result, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  TRACE("ADLB_TAG_SUBSCRIBE done\n");
  // MPE_LOG_EVENT(mpe_svr_subscribe_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_slot_create(int caller)
{
  long id;
  int rc;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_SLOT_CREATE);

  adlb_data_code dc = data_slot_create(id);
  DEBUG("Slot_create: <%li>", id);

  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  return ADLB_SUCCESS;
}

static adlb_code
handle_slot_drop(int caller)
{
  long id;
  int rc = ADLB_SUCCESS;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_SLOT_DROP);

  int slots;
  DEBUG("Slot_drop: <%li>", id);
  adlb_data_code dc = data_slot_drop(id, &slots);

  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  if (slots == 0)
    rc = slot_notification(id);
  return rc;
}

static adlb_code
handle_insert(int caller)
{
  int rc;
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
  assert(n == 4);

  member = malloc((member_length+1) * sizeof(char));

  long* references;
  int count, slots;

  RECV(member, ADLB_DATA_MEMBER_MAX, MPI_CHAR, caller,
       ADLB_TAG_INSERT_PAYLOAD);

  DEBUG("Insert: <%li>[\"%s\"]=\"%s\"",
        id, subscript, member);

  adlb_data_code dc = data_insert(id, subscript, member, drops,
                                  &references, &count, &slots);
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);

  if (dc == ADLB_DATA_SUCCESS)
  {
    if (count > 0)
    {
      long m;
      n = sscanf(member, "%li", &m);
      assert(n == 1);
      for (int i = 0; i < count; i++)
        set_reference_and_notify(references[i], m);
      free(references);
    }
    if (slots == 0)
      slot_notification(id);
  }
  TRACE("INSERT DONE");
  return ADLB_SUCCESS;
}

static adlb_code
handle_insert_atomic(int caller)
{
  int rc;
  MPI_Status status;

  RECV(xfer, ADLB_DATA_SUBSCRIPT_MAX+128, MPI_CHAR, caller,
       ADLB_TAG_INSERT_ATOMIC);

  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  long id;
  int n = sscanf(xfer, "%li %s", &id, subscript);
  // This can only fail on an internal error:
  assert(n == 2);

  bool result;
  adlb_data_code dc = data_insert_atomic(id, subscript, &result);
  DEBUG("Insert_atomic: <%li>[\"%s\"] => %i",
        id, subscript, result);
  RSEND(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE);
  RSEND(&result, sizeof(bool), MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  return ADLB_SUCCESS;
}

static adlb_code
handle_lookup(int caller)
{
  int rc;
  MPI_Status status;
  TRACE("ADLB_TAG_LOOKUP\n");
  char msg[ADLB_DATA_SUBSCRIPT_MAX+32];
  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  char* member;
  RECV(msg, ADLB_DATA_SUBSCRIPT_MAX+32, MPI_BYTE, caller,
       ADLB_TAG_LOOKUP);

  long id;
  int n = sscanf(msg, "%li %s", &id, subscript);
  assert(n == 2);

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
  return ADLB_SUCCESS;
}

static adlb_code
handle_unique(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_unique_start);
  int msg;
  int rc;
  MPI_Status status;
  RECV(&msg, 1, MPI_INT, caller, ADLB_TAG_UNIQUE);

  long id;
  adlb_data_code dc = data_unique(&id);

  RSEND(&id, 1, MPI_LONG, caller, ADLB_TAG_RESPONSE);
  // DEBUG("UNIQUE: <%li>\n", id);
  // MPE_LOG_EVENT(mpe_svr_unique_end);
  return ADLB_SUCCESS;
}

static adlb_code
handle_typeof(int caller)
{
  adlb_datum_id id;
  int rc;
  MPI_Status status;
  RECV(&id, 1, MPI_LONG, caller, ADLB_TAG_TYPEOF);
  MPI_CHECK(rc);

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
  int rc;
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
  int rc;
  MPI_Status status;
  RECV(xfer, XFER_SIZE, MPI_BYTE, caller,
       ADLB_TAG_CONTAINER_REFERENCE);

  long container_id;
  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  long reference;
  int n = sscanf(xfer, "%li %li %s",
                 &reference, &container_id, subscript);
  assert(n == 3);

  DEBUG("Container_reference: <%li>[\"%s\"] => <%li>",
        container_id, subscript, reference);

  long member;
  adlb_data_code dc =
      data_container_reference(container_id, subscript,
                               reference, &member);
  if (dc == ADLB_DATA_SUCCESS)
    if (member != 0)
      set_reference_and_notify(reference, member);

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
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

static adlb_code
handle_lock(int caller)
{
  long id;
  int rc;
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
  int rc;
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
handle_abort(int caller)
{
  TRACE("");
  MPI_Status status;
  int code;
  RECV(&code, 1, MPI_INT, caller, ADLB_TAG_ABORT);
  // MPE_INFO("ABORT: caller: %i code: ", caller);
  xlb_server_abort(code);
  return ADLB_SUCCESS;
}


static adlb_code
slot_notification(long id)
{
  int rc;
  int* waiters;
  int count;
  TRACE("slot_notification: %li", id);
  rc = data_close(id, &waiters, &count);
  if (count > 0)
  {
    close_notification(id, waiters, count);
    free(waiters);
  }
  return ADLB_SUCCESS;
}

static adlb_code
put_targeted(int type, int putter, int priority, int answer,
             int target, void* payload, int length);

static int
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

static int
set_reference_and_notify(long id, long value)
{
  DEBUG("set_reference: <%li>=%li", id, value);
  int rc;
  rc = ADLB_Store(id, &value, sizeof(long));
  ADLB_CHECK(rc);
  int* ranks;
  int count;
  rc = ADLB_Close(id, &ranks, &count);
  ADLB_CHECK(rc);
  if (count > 0)
  {
    rc = close_notification(id, ranks, count);
    ADLB_CHECK(rc);
    free(ranks);
  }
  TRACE("SET_REFERENCE DONE");
  return ADLB_SUCCESS;
}

static adlb_code
put_targeted(int type, int putter, int priority, int answer,
             int target, void* payload, int length)
{
  int next_worker = 0;
  int worker;

  DEBUG("put_targeted: to: %i payload: %s", target, (char*) payload);

  if (target >= 0)
  {
    int server = xlb_map_to_server(target);
    if (server == xlb_world_rank)
    {
      worker = requestqueue_matches_target(target, type);
      if (worker != ADLB_RANK_NULL)
      {
        int wuid = workqueue_unique();
        send_work(target, wuid, type, answer, payload, length);
        return ADLB_SUCCESS;
      }
    }
    else
    {
      xlb_sync(server);
      int rc = ADLB_Put(payload, length, target, answer,
                        type, priority);
      ADLB_CHECK(rc);
    }
  }

  DEBUG("put_targeted(): server storing work...");

  // Enqueue this
  workqueue_add(type, putter, priority, answer, target,
                length, payload);

  return ADLB_SUCCESS;
}
