
/*
 * handlers.c
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()
#include <assert.h>
#include <stdio.h>

#include <mpi.h>

#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "handlers.h"
#include "messaging.h"
#include "server.h"
#include "workqueue.h"

#define MAX_HANDLERS 128

static handler handlers[MAX_HANDLERS];

static int handler_count = 0;

static void create(adlb_tag tag, handler h);

adlb_code handle_put(int caller);
adlb_code handle_get(int caller);
adlb_code handle_create(int caller);
adlb_code handle_exists(int caller);
adlb_code handle_store(int caller);
adlb_code handle_retrieve(int caller);
adlb_code handle_enumerate(int caller);
adlb_code handle_close(int caller);
adlb_code handle_subscribe(int caller);
adlb_code handle_slot_create(int caller);
adlb_code handle_slot_drop(int caller);
adlb_code handle_insert(int caller);
adlb_code handle_insert_atomic(int caller);
adlb_code handle_lookup(int caller);
adlb_code handle_unique(int caller);
adlb_code handle_typeof(int caller);
adlb_code handle_container_typeof(int caller);
adlb_code handle_container_reference(int caller);
adlb_code handle_container_size(int caller);
adlb_code handle_lock(int caller);
adlb_code handle_unlock(int caller);

static int slot_notification(long id);
static int close_notification(long id, int* ranks, int count);
static int set_reference_and_notify(long id, long value);

static adlb_code put_targeted(int type, int priority, int answer,
                              int target, void* payload, int length);

void
handlers_init(void)
{
  create(ADLB_TAG_PUT_HEADER, handle_put);
  create(ADLB_TAG_GET, handle_get);
  create(ADLB_TAG_CREATE_HEADER, handle_create);
  create(ADLB_TAG_EXISTS, handle_exists);
  create(ADLB_TAG_STORE_HEADER, handle_store);
  create(ADLB_TAG_RETRIEVE, handle_retrieve);
  create(ADLB_TAG_ENUMERATE, handle_enumerate);
  create(ADLB_TAG_CLOSE, handle_close);
  create(ADLB_TAG_SUBSCRIBE, handle_subscribe);
  create(ADLB_TAG_SLOT_CREATE, handle_slot_create);
  create(ADLB_TAG_SLOT_DROP, handle_slot_drop);
  create(ADLB_TAG_INSERT_HEADER, handle_insert);
  create(ADLB_TAG_INSERT_ATOMIC, handle_insert_atomic);
  create(ADLB_TAG_LOOKUP, handle_lookup);
  create(ADLB_TAG_UNIQUE, handle_unique);
  create(ADLB_TAG_TYPEOF, handle_typeof);
  create(ADLB_TAG_CONTAINER_TYPEOF, handle_container_typeof);
  create(ADLB_TAG_CONTAINER_REFERENCE, handle_container_reference);
  create(ADLB_TAG_CONTAINER_SIZE, handle_container_size);
  create(ADLB_TAG_LOCK, handle_lock);
  create(ADLB_TAG_UNLOCK, handle_unlock);
}

static void
create(adlb_tag tag, handler h)
{
  handlers[tag] = h;
  handler_count++;
}

bool
handler_valid(adlb_tag tag)
{
  if (tag >= 0 && tag < handler_count)
    return true;
  return false;
}

adlb_code
handle(adlb_tag tag, int caller)
{
  CHECK_MSG(handler_valid(tag), "Invalid tag: %i\n", tag);
  adlb_code result = handlers[tag](caller);
  time_last_action = MPI_Wtime();
  return result;
}

//// Individual handlers follow...

/** Reusable MPI return code */
int rc;

/** Reusable status location */
MPI_Status status;

static void put(struct packed_put* p);

adlb_code
handle_put(int caller)
{
  struct packed_put p;
  rc = MPI_Recv(&p, sizeof(p), MPI_BYTE, caller,
                ADLB_TAG_PUT_HEADER, adlb_all_comm, &status);
  MPI_CHECK(rc);

  put(&p);

  adlb_code code = ADLB_SUCCESS;
  rc = MPI_Send(&code, sizeof(code), MPI_BYTE, caller,
                ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

static void
put(struct packed_put* p)
{
  int next_worker = 0;
  int worker;
  worker = requestqueue_matches_target(p->target);
  if (worker != ADLB_RANK_NULL)
  {
    send_work(p, worker);
    return;
  }

  worker = requestqueue_next();
  send_work(worker);
}

adlb_code
handle_get(int caller)
{
  struct packed_get p;
  rc = MPI_Recv(&p, sizoeof(p), MPI_BYTE, caller, ADLB_TAG_GET,
                adlb_all_comm, &status);
  MPI_CHECK(rc);

  struct work_unit* wu = workqueue_get(p);
  if (wu == NULL)
  {
    requestqueue_add(caller);
    return ADLB_SUCCESS;
  }

  send_work(worker, wu);

  return ADLB_SUCCESS;
}

adlb_code
handle_create(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_create_start);
  TRACE("ADLB_TAG_CREATE\n");
  struct packed_id_type data;

  rc = MPI_Recv(&data, sizeof(struct packed_id_type),
                 MPI_BYTE, caller, ADLB_TAG_CREATE_HEADER,
                 adlb_all_comm, &status);
  MPI_CHECK(rc);

  long id = data.id;
  adlb_data_type type = data.type;

  adlb_data_code dc = data_create(id, type);

  rc = MPI_Rsend(&dc, 1, MPI_INT, caller,
                  ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);

  // Types file and container need additional information
  if (type == ADLB_DATA_TYPE_FILE)
  {
    MPI_Recv(xfer, XFER_SIZE, MPI_CHAR, caller,
             ADLB_TAG_CREATE_PAYLOAD, adlb_all_comm, &status);
    data_create_filename(id, xfer);
  }
  else if (type == ADLB_DATA_TYPE_CONTAINER)
  {
    adlb_data_type container_type;
    MPI_Recv(&container_type, 1, MPI_INT, caller,
             ADLB_TAG_CREATE_PAYLOAD, adlb_all_comm, &status);
    data_create_container(id, container_type);
  }

  // DEBUG("CREATE: <%li> %s\n", id, (char*) work_buf);
  TRACE("ADLB_TAG_CREATE done\n");
  // MPE_LOG_EVENT(mpe_svr_create_end);

  return ADLB_SUCCESS;
}

adlb_code
handle_exists(int caller)
{
  bool result;
  adlb_datum_id id;

  rc = MPI_Recv(&id, 1, MPI_LONG, caller, ADLB_TAG_EXISTS,
                adlb_all_comm, &status);
  MPI_CHECK(rc);

  data_exists(id, &result);

  rc = MPI_Rsend(&result, sizeof(bool), MPI_BYTE, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

adlb_code
handle_store(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_store_start);
  long id;
  rc = MPI_Recv(&id, 1, MPI_LONG, caller, ADLB_TAG_STORE_HEADER,
                adlb_all_comm, &status);
  MPI_CHECK(rc);

  DEBUG("Store: <%li>", id);

  rc = MPI_Recv(xfer, XFER_SIZE, MPI_BYTE, caller,
                ADLB_TAG_STORE_PAYLOAD, adlb_all_comm, &status);
  MPI_CHECK(rc);

  int length;
  MPI_Get_count(&status, MPI_BYTE, &length);

  return ADLB_SUCCESS;
}

adlb_code
handle_retrieve(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_retrieve_start);
  TRACE("ADLB_TAG_RETRIEVE\n");
  long id;
  rc = MPI_Recv(&id, 1, MPI_LONG, caller, ADLB_TAG_RETRIEVE,
                adlb_all_comm, &status);
  MPI_CHECK(rc);

  void* result = NULL;
  int length;
  adlb_data_type type;
  int dc = data_retrieve(id, &type, &result, &length);
  MPI_Rsend(&dc, 1, MPI_INT, caller,
            ADLB_TAG_RESPONSE, adlb_all_comm);
  if (dc == ADLB_DATA_SUCCESS)
  {
    MPI_Send(&type, 1, MPI_INT, caller,
             ADLB_TAG_RESPONSE, adlb_all_comm);
    MPI_Send(result, length, MPI_BYTE, caller,
             ADLB_TAG_RESPONSE, adlb_all_comm);
    if (type == ADLB_DATA_TYPE_CONTAINER)
      free(result);
    DEBUG("Retrieve: <%li>", id);
  }
  return ADLB_SUCCESS;
}

adlb_code
handle_enumerate(int caller)
{
  TRACE("ENUMERATE\n");
  struct packed_enumerate opts;
  rc = MPI_Recv(&opts, sizeof(struct packed_enumerate),
                MPI_BYTE, caller, ADLB_TAG_ENUMERATE,
                adlb_all_comm, &status);
  MPI_CHECK(rc);

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
  rc = MPI_Rsend(&dc, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  if (dc == ADLB_DATA_SUCCESS)
  {
    rc = MPI_Send(&actual, 1, MPI_INT, caller,
                  ADLB_TAG_RESPONSE, adlb_all_comm);
    MPI_CHECK(rc);
    if (opts.request_subscripts)
    {
      rc = MPI_Send(subscripts, subscripts_length+1,
                    MPI_BYTE, caller,
                    ADLB_TAG_RESPONSE, adlb_all_comm);
      MPI_CHECK(rc);
      free(subscripts);
    }
    if (opts.request_members)
    {
      rc = MPI_Send(members, members_length,
                    MPI_BYTE, caller,
                    ADLB_TAG_RESPONSE, adlb_all_comm);
      MPI_CHECK(rc);
      free(members);
    }
  }
  return ADLB_SUCCESS;
}

adlb_code
handle_close(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_close_start);
  long id;
  MPI_Recv(&id, 1, MPI_LONG, caller, ADLB_TAG_CLOSE,
           adlb_all_comm, &status);

  DEBUG("Close: <%li>", id);

  int* ranks;
  int count;
  adlb_data_code dc = data_close(id, &ranks, &count);
  if (dc != ADLB_DATA_SUCCESS)
    count = -1;
  rc = MPI_Rsend(&count, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);

  if (count > 0)
  {
    rc = MPI_Send(ranks, count, MPI_INT, caller,
                  ADLB_TAG_RESPONSE, adlb_all_comm);
    MPI_CHECK(rc);
    free(ranks);
  }

  //  MPE_LOG_EVENT(mpe_svr_close_end);
  return ADLB_SUCCESS;
}

adlb_code
handle_subscribe(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_subscribe_start);
  TRACE("ADLB_TAG_SUBSCRIBE\n");

  long id;
  rc = MPI_Recv(&id, 1, MPI_LONG, caller,
                ADLB_TAG_SUBSCRIBE, adlb_all_comm, &status);
  MPI_CHECK(rc);

  int result;
  adlb_data_code dc = data_subscribe(id, caller, &result);
  if (dc != ADLB_DATA_SUCCESS)
    result = -1;
  rc = MPI_Rsend(&result, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);

  TRACE("ADLB_TAG_SUBSCRIBE done\n");
  // MPE_LOG_EVENT(mpe_svr_subscribe_end);
  return ADLB_SUCCESS;
}

adlb_code
handle_slot_create(int caller)
{
  long id;
  rc = MPI_Recv(&id, 1, MPI_LONG, caller,
                ADLB_TAG_SLOT_CREATE, adlb_all_comm, &status);
  MPI_CHECK(rc);

  adlb_data_code dc = data_slot_create(id);
  DEBUG("Slot_create: <%li>", id);

  rc = MPI_Rsend(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE,
                 adlb_all_comm);
  return ADLB_SUCCESS;
}

adlb_code
handle_slot_drop(int caller)
{
  long id;
  rc = MPI_Recv(&id, 1, MPI_LONG, caller,
                ADLB_TAG_SLOT_DROP, adlb_all_comm, &status);
  MPI_CHECK(rc);

  int slots;
  DEBUG("Slot_drop: <%li>", id);
  adlb_data_code dc = data_slot_drop(id, &slots);

  rc = MPI_Rsend(&dc, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);

  if (slots == 0)
    slot_notification(id);
  return ADLB_SUCCESS;
}

adlb_code
handle_insert(int caller)
{
  rc = MPI_Recv(xfer, ADLB_DATA_SUBSCRIPT_MAX+128, MPI_CHAR,
                caller, ADLB_TAG_INSERT_HEADER, adlb_all_comm,
                &status);
  MPI_CHECK(rc);
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

  rc = MPI_Recv(member, ADLB_DATA_MEMBER_MAX, MPI_CHAR,
                caller, ADLB_TAG_INSERT_PAYLOAD, adlb_all_comm,
                &status);
  MPI_CHECK(rc);

  DEBUG("Insert: <%li>[\"%s\"]=\"%s\"",
        id, subscript, member);

  adlb_data_code dc = data_insert(id, subscript, member, drops,
                   &references, &count, &slots);
  rc = MPI_Rsend(&dc, 1, MPI_INT, caller, ADLB_TAG_RESPONSE,
                 adlb_all_comm);
  MPI_CHECK(rc);

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

adlb_code handle_insert_atomic(int caller)
{
  return ADLB_SUCCESS;
}

adlb_code
handle_lookup(int caller)
{
  TRACE("ADLB_TAG_LOOKUP\n");
  char msg[ADLB_DATA_SUBSCRIPT_MAX+32];
  char subscript[ADLB_DATA_SUBSCRIPT_MAX];
  char* member;
  rc = MPI_Recv(msg, ADLB_DATA_SUBSCRIPT_MAX+32, MPI_BYTE,
                caller, ADLB_TAG_LOOKUP,adlb_all_comm,&status);
  MPI_CHECK(rc);

  long id;
  int n = sscanf(msg, "%li %s", &id, subscript);
  assert(n == 2);

  adlb_data_code dc = data_lookup(id, subscript, &member);

  struct packed_code_length p;
  p.code = dc;
  // Set this field to 1 if we found the entry, else -1
  bool found = (member != ADLB_DATA_ID_NULL);
  p.length = found ? 1 : -1;

  rc = MPI_Rsend(&p, sizeof(p), MPI_BYTE, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);

  if (dc == ADLB_DATA_SUCCESS && found)
  {
    rc = MPI_Send(member, strlen(member)+1, MPI_CHAR, caller,
                  ADLB_TAG_RESPONSE, adlb_all_comm);
    MPI_CHECK(rc);
  }
  // DEBUG("LOOKUP: <%li>[\"%s\"] => <%li>\n",
  //       id, subscript, member);
  TRACE("ADLB_TAG_LOOKUP done\n");
  return ADLB_SUCCESS;
}

adlb_code handle_unique(int caller)
{
  // MPE_LOG_EVENT(mpe_svr_unique_start);
  int msg;
  rc = MPI_Recv(&msg, 1, MPI_INT, caller, ADLB_TAG_UNIQUE,
                adlb_all_comm, &status);
  MPI_CHECK(rc);

  long id;
  adlb_data_code dc = data_unique(&id);

  rc = MPI_Rsend(&id, 1, MPI_LONG, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  // DEBUG("UNIQUE: <%li>\n", id);
  // MPE_LOG_EVENT(mpe_svr_unique_end);
  return ADLB_SUCCESS;
}

adlb_code
handle_typeof(int caller)
{
  adlb_datum_id id;
  rc = MPI_Recv(&id, 1, MPI_LONG, caller,
                ADLB_TAG_TYPEOF, adlb_all_comm,
                &status);
  MPI_CHECK(rc);

  adlb_data_type type;
  adlb_data_code dc = data_typeof(id, &type);
  if (dc != ADLB_DATA_SUCCESS)
    type = -1;

  rc = MPI_Rsend(&type, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

adlb_code handle_container_typeof(int caller)
{
  adlb_datum_id id;
  rc = MPI_Recv(&id, 1, MPI_LONG, caller,
                ADLB_TAG_CONTAINER_TYPEOF, adlb_all_comm,
                &status);
  MPI_CHECK(rc);

  adlb_data_type type;
  adlb_data_code dc = data_container_typeof(id, &type);
  if (dc != ADLB_DATA_SUCCESS)
    type = -1;

  rc = MPI_Rsend(&type, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

adlb_code
handle_container_reference(int caller)
{
  rc = MPI_Recv(xfer, XFER_SIZE, MPI_BYTE, caller,
                ADLB_TAG_CONTAINER_REFERENCE, adlb_all_comm,
                &status);
  MPI_CHECK(rc);

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

  rc = MPI_Rsend(&dc, 1, MPI_INT, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

adlb_code
handle_container_size(int caller)
{
  long container_id;
  rc = MPI_Recv(&container_id, 1, MPI_LONG, caller,
                ADLB_TAG_CONTAINER_SIZE, adlb_all_comm,
                &status);
  MPI_CHECK(rc);

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

adlb_code
handle_lock(int caller)
{
  long id;
  MPI_Recv(&id, 1, MPI_LONG, caller, ADLB_TAG_LOCK,
           adlb_all_comm, &status);

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
  rc = MPI_Rsend(&c, 1, MPI_CHAR, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  return ADLB_SUCCESS;
}

adlb_code
handle_unlock(int caller)
{
  long id;
  MPI_Recv(&id, 1, MPI_LONG, caller, ADLB_TAG_UNLOCK,
           adlb_all_comm, &status);

  DEBUG("Unlock: <%li> by rank: %i ", id, caller);

  adlb_data_code dc = data_unlock(id);

  char c = (dc == ADLB_DATA_SUCCESS) ? '1' : 'x';
  rc = MPI_Rsend(&c, 1, MPI_CHAR, caller,
                 ADLB_TAG_RESPONSE, adlb_all_comm);
  return ADLB_SUCCESS;
}

static int
slot_notification(long id)
{
  int rc;
  int* waiters;
  int count;
  rc = data_close(id, &waiters, &count);
  if (count > 0)
  {
    close_notification(id, waiters, count);
    free(waiters);
  }
  return ADLB_SUCCESS;
}

static int
close_notification(long id, int* ranks, int count)
{
  for (int i = 0; i < count; i++)
  {
    char* t;
    int length = asprintf(&t, "close %li", id);
    put_targeted(1,        // work_type CONTROL
                 1,        // work_prio
                 -1,       // answer_rank
                 ranks[i], // target_rank
                 t,         // work_buf
                 length+1 // work_len
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
  rc = close_notification(id, ranks, count);
  ADLB_CHECK(rc);
  TRACE("SET_REFERENCE DONE");
  return ADLB_SUCCESS;
}

adlb_code
put_targeted(int type, int priority, int answer, int target,
             void* payload, int length)
{
  return ADLB_SUCCESS;
}
