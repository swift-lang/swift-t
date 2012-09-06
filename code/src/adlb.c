
#define _GNU_SOURCE
#include <assert.h>
#include <inttypes.h>
#include <stddef.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>

#include <mpi.h>

#include <c-utils.h>
#include <tools.h>

#include "adlb.h"
#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "messaging.h"
#include "mpi-tools.h"
#include "server.h"

adlb_code next_server;

static void print_proc_self_status(void);

void adlb_exit_handler(void);

/** True after a Get() receives a shutdown code */
static bool got_shutdown = false;

static void
check_versions()
{
  version av, cuv, rcuv;
  // required c-utils version (rcuv):
  ADLB_Version(&av);
  version_parse(&rcuv, "0.0.1");
  c_utils_version(&cuv);
  version_require("ADLB", &av, "c-utils", &cuv, &rcuv);
}

adlb_code
ADLBP_Init(int nservers, int ntypes, int type_vect[],
           int *am_server, MPI_Comm *worker_comm)
{
  TRACE_START;
  int initialized, j, rc;

  check_versions();
  rc = MPI_Initialized(&initialized);
  CHECK_MSG(initialized, "ADLB: MPI is not initialized!\n");

  xlb_start_time = MPI_Wtime();

  debug_check_environment();

  xlb_msg_init();

  rc = MPI_Comm_size(MPI_COMM_WORLD, &xlb_world_size);
  rc = MPI_Comm_rank(MPI_COMM_WORLD, &xlb_world_rank);

  gdb_spin(xlb_world_rank);

  xlb_types_size = ntypes;
  xlb_types = malloc(xlb_types_size * sizeof(int));
  for (int i = 0; i < xlb_types_size; i++)
    xlb_types[i] = type_vect[i];
  xlb_servers = nservers;
  xlb_workers = xlb_world_size - xlb_servers;
  xlb_master_server_rank = xlb_world_size - xlb_servers;

  rc = MPI_Comm_dup(MPI_COMM_WORLD, &adlb_all_comm);
  assert(rc == MPI_SUCCESS);

  if (xlb_world_rank < xlb_workers)
  {
    *am_server = 0;
    MPI_Comm_split(MPI_COMM_WORLD, 0, xlb_world_rank, worker_comm);
    xlb_my_server = xlb_workers + (xlb_world_rank % xlb_servers);
    DEBUG("my_server_rank: %i\n", xlb_my_server);
    next_server = xlb_my_server;
  }
  else
  {
    *am_server = 1;
    // Don't have a server: I am one
    xlb_my_server = ADLB_RANK_NULL;
    MPI_Comm_split(MPI_COMM_WORLD,1, xlb_world_rank-xlb_workers,
                   &adlb_server_comm);
    xlb_server_init();
  }

  srandom(xlb_world_rank+1);
  TRACE_END;
  return ADLB_SUCCESS;
}

adlb_code
ADLB_Version(version* output)
{
  version_parse(output, "0.0.2");
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Put(void* payload, int length, int target, int answer,
          int type, int priority)
{
  adlb_code code;
  MPI_Status status;
  MPI_Request request;
  /** In a redirect, we send the payload to a worker */
  int payload_dest;

  DEBUG("ADLB_Put: target=%i %s", target, (char*) payload);

  CHECK_MSG(type >= 0 && xlb_type_index(type) >= 0,
            "ADLB_Put(): invalid work type: %d\n", type);

  /** Server to contact */
  int to_server;
  if (target != ADLB_RANK_ANY)
    to_server = xlb_workers + (target % xlb_servers);
  else
    to_server = xlb_my_server;

  struct packed_put p;
  p.type = type;
  p.priority = priority;
  p.putter = xlb_world_rank;
  p.answer = answer;
  p.target = target;
  p.length = length;

  IRECV(&payload_dest, 1, MPI_INT, to_server, ADLB_TAG_RESPONSE_PUT);
  SEND(&p, sizeof(p), MPI_BYTE, to_server, ADLB_TAG_PUT);

  WAIT(&request, &status);
  if (payload_dest == ADLB_REJECTED)
  {
    printf("ADLB_Put(): REJECTED\n");
//    to_server_rank = next_server++;
//    if (next_server >= (master_server_rank+num_servers))
//      next_server = master_server_rank;
    return payload_dest;
  }

  DEBUG("ADLB_Put: payload to: %i", payload_dest);
  if (payload_dest == ADLB_RANK_NULL)
    return ADLB_ERROR;
  SSEND(payload, length, MPI_BYTE, payload_dest, ADLB_TAG_WORK);
  TRACE("ADLB_Put: DONE");

  return ADLB_SUCCESS;
}


adlb_code
ADLBP_Get(int type_requested, void* payload, int* length,
          int* answer, int* type_recvd)
{
  int rc;
  adlb_code code;
  MPI_Status status;
  MPI_Request request;

  CHECK_MSG(xlb_type_index(type_requested) != -1,
                "ADLB_Get(): Bad work type: %i\n", type_requested);

  struct packed_get_response g;

  IRECV(&g, sizeof(g), MPI_BYTE, xlb_my_server, ADLB_TAG_RESPONSE_GET);

  SEND(&type_requested, 1, MPI_INT, xlb_my_server, ADLB_TAG_GET);

  WAIT(&request, &status);

  mpi_recv_sanity(&status, MPI_BYTE, sizeof(g));

  if (g.code == ADLB_SHUTDOWN)
  {
    DEBUG("ADLB_Get(): SHUTDOWN");
    got_shutdown = true;
    return ADLB_SHUTDOWN;
  }

  DEBUG("ADLB_Get: payload source: %i", g.payload_source);
  RECV(payload, g.length, MPI_BYTE, g.payload_source, ADLB_TAG_WORK);

  mpi_recv_sanity(&status, MPI_BYTE, g.length);
  TRACE("ADLB_Get: got: %s", (char*) payload);

  STATS("GOT_WORK");

  *length = g.length;
  *answer = g.answer_rank;
  *type_recvd = g.type;

  return ADLB_SUCCESS;
}

static inline int
locate(long id)
{
  int offset = id % xlb_servers;
  int rank = xlb_world_size - xlb_servers + offset;
  // DEBUG("locate(%li) => %i\n", id, rank);
  return rank;
}

/**
   Reusable internal data creation function
   Applications should use the ADLB_Create_type macros in adlb.h
   @param filename Only used for file-type data
   @param subscript_type Only used for container-type data
 */
adlb_code ADLBP_Create(adlb_datum_id id, adlb_data_type type,
                       const char* filename,
                       adlb_data_type subscript_type)
{
  int rc, to_server_rank;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  to_server_rank = locate(id);
  struct packed_id_type data = { id, type };

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                 adlb_all_comm, &request);
  MPI_CHECK(rc);

  rc = MPI_Send(&data, sizeof(struct packed_id_type), MPI_BYTE,
                to_server_rank, ADLB_TAG_CREATE_HEADER, adlb_all_comm);
  MPI_CHECK(rc);

  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);
  ADLB_DATA_CHECK(dc);

  if (type == ADLB_DATA_TYPE_FILE)
  {
    int length = strlen(filename);
    // Remove const qualifier- MPI cannot accept it
    char* fn = (char*) filename;
    rc = MPI_Send(fn, length+1, MPI_CHAR, to_server_rank,
                  ADLB_TAG_CREATE_PAYLOAD, adlb_all_comm);
    MPI_CHECK(rc);
  }
  else if (type == ADLB_DATA_TYPE_CONTAINER)
  {
    TRACE("ADLB_Create(type=container, subscript_type=%i)",
          subscript_type);
    rc = MPI_Send(&subscript_type, 1, MPI_INT, to_server_rank,
                  ADLB_TAG_CREATE_PAYLOAD, adlb_all_comm);
    MPI_CHECK(rc);
  }
  return ADLB_SUCCESS;
}

adlb_code ADLB_Create_integer(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_INTEGER, NULL, ADLB_DATA_TYPE_NULL);
}

adlb_code ADLB_Create_float(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_FLOAT, NULL, ADLB_DATA_TYPE_NULL);
}

adlb_code ADLB_Create_string(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_STRING, NULL, ADLB_DATA_TYPE_NULL);
}

adlb_code ADLB_Create_blob(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_BLOB, NULL, ADLB_DATA_TYPE_NULL);
}

adlb_code ADLB_Create_file(adlb_datum_id id, const char* filename)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_FILE, filename, ADLB_DATA_TYPE_NULL);
}

adlb_code ADLB_Create_container(adlb_datum_id id, adlb_data_type subscript_type)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_CONTAINER, NULL, subscript_type);
}

adlb_code ADLBP_Exists(adlb_datum_id id, bool* result)
{
  int to_server_rank = locate(id);

  int rc;
  MPI_Status status;
  MPI_Request request;

  TRACE("ADLB_Exists: <%li>\n", id);

  rc = MPI_Irecv(result, sizeof(bool), MPI_BYTE, to_server_rank,
                 ADLB_TAG_RESPONSE, adlb_all_comm, &request);

  MPI_CHECK(rc);

  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_EXISTS,
                adlb_all_comm);
  MPI_CHECK(rc);

  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);

  return ADLB_SUCCESS;
}

adlb_code ADLBP_Store(adlb_datum_id id, void *data, int length)
{
  int to_server_rank;
  int rc;
  adlb_data_code dc;

  MPI_Status status;
  MPI_Request request;

  to_server_rank = locate(id);

  if (to_server_rank == xlb_world_rank)
  {
    // This is a server-to-server operation on myself
    TRACE("Store SELF");
    dc = data_store(id, data, length);
    ADLB_DATA_CHECK(dc);
    return ADLB_SUCCESS;
  }

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                  adlb_all_comm,&request);
  MPI_CHECK(rc);

  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_STORE_HEADER,
                 adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Send(data, length, MPI_BYTE, to_server_rank,
		ADLB_TAG_STORE_PAYLOAD, adlb_all_comm);
  MPI_CHECK(rc);

  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

/**
   Obtain the next server index
   Currently implemented as a round-robin loop through the ranks
 */
static inline int
get_next_server()
{
  static int next_server_index = 0;
  int offset = next_server_index % xlb_servers;
  int rank = xlb_world_size - xlb_servers + offset;
  // DEBUG("random_server => %i\n", rank);
  next_server_index = (next_server_index + 1) % xlb_servers;
  return rank;
}

adlb_code ADLBP_Slot_create(long id)
{
    adlb_data_code dc;
    int rc;

    MPI_Status status;
    MPI_Request request;

    DEBUG("ADLB_Slot_create: <%li>", id);
    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                    adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                   ADLB_TAG_SLOT_CREATE, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    if (dc != ADLB_DATA_SUCCESS)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

adlb_code ADLBP_Slot_drop(long id)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    DEBUG("ADLB_Slot_drop: <%li>", id);
    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                    adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                   ADLB_TAG_SLOT_DROP, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    if (dc != ADLB_DATA_SUCCESS)
        return ADLB_ERROR;
    return ADLB_SUCCESS;
}

adlb_code
ADLBP_Insert(adlb_datum_id id,
             const char* subscript, const char* member,
             int member_length, int drops)
{
  int rc;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  CHECK_MSG(member_length < ADLB_DATA_MEMBER_MAX,
            "ADLB_Insert(): member too long: <%li>[\"%s\"]\n",
            id, subscript);

  DEBUG("ADLB_Insert: <%li>[\"%s\"]=\"%s\"", id, subscript, member);
  int length = sprintf(xfer, "%li %s %i %i",
                       id, subscript, member_length, drops);
  int to_server_rank = locate(id);

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                 adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(xfer, length+1, MPI_INT, to_server_rank,
                ADLB_TAG_INSERT_HEADER, adlb_all_comm);
  MPI_CHECK(rc);

  rc = MPI_Send((char*) member, member_length+1, MPI_BYTE,
                to_server_rank, ADLB_TAG_INSERT_PAYLOAD, adlb_all_comm);
  MPI_CHECK(rc);

  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);
  if (dc != ADLB_DATA_SUCCESS)
    return ADLB_ERROR;

  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Insert_atomic(adlb_datum_id id, const char* subscript,
                    bool* result)
{
  int rc;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request1, request2;

  DEBUG("ADLB_Insert_atomic: <%li>[\"%s\"]", id, subscript);
  int length = sprintf(xfer, "%li %s", id, subscript);
  int to_server_rank = locate(id);

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                 adlb_all_comm, &request1);
  MPI_CHECK(rc);
  rc = MPI_Irecv(result, sizeof(bool), MPI_BYTE, to_server_rank,
                 ADLB_TAG_RESPONSE, adlb_all_comm, &request2);
  MPI_CHECK(rc);
  rc = MPI_Send(xfer, length+1, MPI_INT, to_server_rank,
                ADLB_TAG_INSERT_ATOMIC, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request1, &status);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request2, &status);
  MPI_CHECK(rc);

  if (dc != ADLB_DATA_SUCCESS)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Retrieve(adlb_datum_id id, adlb_data_type* type,
               void *data, int *length)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank,
                   ADLB_TAG_RESPONSE, adlb_all_comm, &request);

    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  ADLB_TAG_RETRIEVE, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);

    if (dc == ADLB_DATA_SUCCESS)
    {
      rc = MPI_Recv(type, 1, MPI_INT, to_server_rank,
		    ADLB_TAG_RESPONSE, adlb_all_comm, &status);
      MPI_CHECK(rc);
      rc = MPI_Recv(data, ADLB_PAYLOAD_MAX, MPI_BYTE, to_server_rank,
		    ADLB_TAG_RESPONSE, adlb_all_comm, &status);
      MPI_CHECK(rc);
    }
    else
      return ADLB_ERROR;

    // Set length output parameter
    MPI_Get_count(&status, MPI_BYTE, length);
    // DEBUG("RETRIEVE: <%li>=%s\n", hashcode, (char*) data);
    return ADLB_SUCCESS;
}

/**
   Allocates fresh memory in subscripts and members
   Caller must free this when done
 */
adlb_code
ADLBP_Enumerate(adlb_datum_id container_id,
                int count, int offset,
                char** subscripts, int* subscripts_length,
                char** members, int* members_length,
                int* restrict records)
{
  int rc;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(container_id);

  struct packed_enumerate opts;
  opts.id = container_id;
  // Are we requesting subscripts?
  opts.request_subscripts = *subscripts ? 1 : 0;
  // Are we requesting members?
  opts.request_members = *members ? 1 : 0;
  opts.count = count;
  opts.offset = offset;

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank,
                 ADLB_TAG_RESPONSE, adlb_all_comm, &request);
  MPI_CHECK(rc);

  rc = MPI_Send(&opts, sizeof(struct packed_enumerate), MPI_BYTE,
                to_server_rank, ADLB_TAG_ENUMERATE, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request,&status);
  MPI_CHECK(rc);

  if (dc == ADLB_DATA_SUCCESS)
  {
    rc = MPI_Recv(records, 1, MPI_INT, to_server_rank,
                  ADLB_TAG_RESPONSE, adlb_all_comm, &status);
    MPI_CHECK(rc);

    if (*subscripts)
    {
      rc = MPI_Recv(xfer, ADLB_PAYLOAD_MAX, MPI_BYTE, to_server_rank,
                    ADLB_TAG_RESPONSE, adlb_all_comm, &status);
      MPI_CHECK(rc);
      *subscripts = strdup(xfer);
      // Set length output parameter
      MPI_Get_count(&status, MPI_BYTE, subscripts_length);
    }
    if (*members)
    {
      rc = MPI_Recv(xfer, ADLB_PAYLOAD_MAX, MPI_BYTE, to_server_rank,
                    ADLB_TAG_RESPONSE, adlb_all_comm, &status);
      MPI_CHECK(rc);
      int c;
      MPI_Get_count(&status, MPI_BYTE, &c);
      char* A = malloc(c);
      assert(A);
      memcpy(A, xfer, c);
      *members = A;
      *members_length = c;
    }
    MPI_CHECK(rc);
  }
  else
    return ADLB_ERROR;

  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Unique(long* result)
{
    int rc;
    MPI_Status status;
    MPI_Request request;

    TRACE("ADLBP_Unique()...");

    // This is just something to send, it is ignored by the server
    static int msg = 0;
    int to_server_rank = get_next_server();
    rc = MPI_Irecv(result, 1, MPI_LONG, to_server_rank,
                   ADLB_TAG_RESPONSE, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&msg, 1, MPI_INT, to_server_rank,
                  ADLB_TAG_UNIQUE, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);

    if (result == ADLB_DATA_ID_NULL)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

adlb_code
ADLBP_Typeof(adlb_datum_id id, adlb_data_type* type)
{
    int rc;
    MPI_Status status;
    MPI_Request request;

    int to_server_rank = locate(id);
    rc = MPI_Irecv(type, 1, MPI_INT, to_server_rank,
                   ADLB_TAG_RESPONSE, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  ADLB_TAG_TYPEOF, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);

    DEBUG("ADLB_Typeof <%li>=>%i", id, *type);

    if (*type == -1)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

adlb_code
ADLBP_Container_typeof(adlb_datum_id id, adlb_data_type* type)
{
    int rc;
    MPI_Status status;
    MPI_Request request;

    // DEBUG("ADLB_Container_typeof: %li", id);

    int to_server_rank = locate(id);
    rc = MPI_Irecv(type, 1, MPI_INT, to_server_rank,
                   ADLB_TAG_RESPONSE, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  ADLB_TAG_CONTAINER_TYPEOF, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);

    DEBUG("ADLB_Container_typeof <%li>=>%i", id, *type);

    if (*type == -1)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

/**
   Look in given container for subscript
   Store result in output member
   On error, output member is ADLB_DATA_ID_NULL
   @param member Must be pre-allocated to ADLB_DATA_MEMBER_MAX
 */
adlb_code ADLBP_Lookup(adlb_datum_id id,
                 const char* subscript, char* member, int* found)
{
    int rc, to_server_rank;
    MPI_Status status;
    MPI_Request request;

    // DEBUG("lookup: %li\n", hashcode);

    to_server_rank = locate(id);

    char msg[ADLB_DATA_SUBSCRIPT_MAX+32];
    sprintf(msg, "%li %s", id, subscript);
    int msg_length = strlen(msg)+1;

    struct packed_code_length p;
    rc = MPI_Irecv(&p, sizeof(p), MPI_BYTE, to_server_rank,
                   ADLB_TAG_RESPONSE, adlb_all_comm,&request);
    MPI_CHECK(rc);
    rc = MPI_Send(msg, msg_length, MPI_CHAR, to_server_rank,
                  ADLB_TAG_LOOKUP, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);

    if (p.code != ADLB_DATA_SUCCESS)
      return ADLB_ERROR;

    if (p.length == 1)
    {
      rc = MPI_Recv(member, ADLB_DATA_MEMBER_MAX, MPI_BYTE, to_server_rank,
                    ADLB_TAG_RESPONSE, adlb_all_comm, &status);
      MPI_CHECK(rc);
      *found = 1;
    }
    else
      *found = -1;

    return ADLB_SUCCESS;
}

/**
   @param subscribed output: false if data is already closed
                             or ADLB_ERROR on error
 */
adlb_code ADLBP_Subscribe(long id, int* subscribed)
{
    int rc, to_server_rank;
    MPI_Status status;
    MPI_Request request;

    to_server_rank = locate(id);

    rc = MPI_Irecv(subscribed, 1, MPI_INT, to_server_rank,
                   ADLB_TAG_RESPONSE, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  ADLB_TAG_SUBSCRIBE, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);
    DEBUG("ADLB_Subscribe: <%li> => %i", id, *subscribed);

    if (*subscribed == -1)
      return ADLB_ERROR;

    return ADLB_SUCCESS;
}

/**
   @return false in subscribed if data is already closed
 */
adlb_code
ADLBP_Container_reference(adlb_datum_id id, const char *subscript,
                          adlb_datum_id reference)
{
  int rc;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int length = sprintf(xfer, "%li %li %s",
                       reference, id, subscript);

  int to_server_rank = locate(id);

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                 adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(xfer, length+1, MPI_CHAR, to_server_rank,
                ADLB_TAG_CONTAINER_REFERENCE, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);
  DEBUG("ADLB_Container_reference: <%li>[\"%s\"] => <%li>",
        id, subscript, reference);

  if (dc != ADLB_DATA_SUCCESS)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

/** Return ADLB_ERROR and size=-1 if container is not closed */
adlb_code
ADLBP_Container_size(adlb_datum_id container_id, int* size)
{
  int rc;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(container_id);

  rc = MPI_Irecv(size, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                 adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(&container_id, 1, MPI_LONG, to_server_rank,
                ADLB_TAG_CONTAINER_SIZE, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);
  DEBUG("ADLB_Container_size: <%li> => %i",
        container_id, *size);

  if (*size < 0)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

/**
   Allocates fresh storage in ranks iff count > 0
 */
adlb_code
ADLBP_Close(adlb_datum_id id, int** ranks, int *count)
{
  int rc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(id);

  if (to_server_rank == xlb_world_rank)
  {
    TRACE("CLOSE SELF: <%li>\n", id);
    adlb_data_code dc = data_close(id, ranks, count);
    ADLB_DATA_CHECK(dc);
    return ADLB_SUCCESS;
  }

  rc = MPI_Irecv(count, 1, MPI_INT, to_server_rank,
                 ADLB_TAG_RESPONSE, adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                ADLB_TAG_CLOSE, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request,&status);
  MPI_CHECK(rc);

  if (*count == -1)
    return ADLB_ERROR;

  if (*count > 0)
  {
    *ranks = malloc(*count*sizeof(int));
    rc = MPI_Recv(*ranks, *count, MPI_INT, to_server_rank,
                  ADLB_TAG_RESPONSE, adlb_all_comm, &status);
    MPI_CHECK(rc);
  }

  return ADLB_SUCCESS;
}

/**
   @return result 0->try again, 1->locked
 */
adlb_code ADLBP_Lock(adlb_datum_id id, bool* result)
{
  int rc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(id);

  // c 0->try again, 1->locked, x->failed
  char c;
  rc = MPI_Irecv(&c, 1, MPI_CHAR, to_server_rank,
                 ADLB_TAG_RESPONSE, adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                ADLB_TAG_LOCK, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request,&status);
  MPI_CHECK(rc);

  if (c == 'x')
    return ADLB_ERROR;

  if (c == '0')
    *result = false;
  else if (c == '1')
    *result = true;
  else
    assert(false);

  return ADLB_SUCCESS;
}

/**
   @return result 0->try again, 1->locked
 */
adlb_code
ADLBP_Unlock(adlb_datum_id id)
{
  int rc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(id);

  // c: 1->success, x->failed
  char c;
  rc = MPI_Irecv(&c, 1, MPI_CHAR, to_server_rank,
                 ADLB_TAG_RESPONSE, adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                ADLB_TAG_UNLOCK, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);

  if (c == 'x')
    return ADLB_ERROR;

  if (c != '1')
    assert(false);

  return ADLB_SUCCESS;
}

/**
   Is the server at rank idle?
 */
adlb_code
ADLB_Server_idle(int rank, bool* result)
{
  MPI_Request request;
  MPI_Status status;
  IRECV(result, sizeof(result), MPI_BYTE, rank, ADLB_TAG_RESPONSE);
  SEND_TAG(rank, ADLB_TAG_CHECK_IDLE);
  WAIT(&request, &status);
  return ADLB_SUCCESS;
}

/**
   Tell the server at rank to shutdown
 */
adlb_code
ADLB_Server_shutdown(int rank)
{
  TRACE_START;
  SEND_TAG(rank, ADLB_TAG_SHUTDOWN_SERVER);
  TRACE_END;
  return ADLB_SUCCESS;
}

/**
   Tell the server that this worker is shutting down
 */
static inline adlb_code
ADLB_Shutdown(void)
{
  TRACE_START;
  SEND_TAG(xlb_my_server, ADLB_TAG_SHUTDOWN_WORKER);
  TRACE_END;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Finalize()
{
  TRACE_START;

  int flag;
  MPI_Finalized(&flag);
  CHECK_MSG(flag,
            "ERROR: MPI_Finalize() called before ADLB_Finalize()\n");
  data_finalize();
  if (xlb_world_rank >= xlb_master_server_rank)
  {
    // Server:
    ; // print_final_stats();
  }
  else
  {
    // Worker:
    if (!got_shutdown)
    {
      int rc = ADLB_Shutdown();
      ADLB_CHECK(rc);
    }
  }
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Abort(int code)
{
  printf("ADLB_Abort(%i)\n", code);

  MPI_Send(&code, 1, MPI_INT, xlb_my_server, ADLB_TAG_ABORT, adlb_all_comm);

  // give servers a chance to shut down
  sleep(1);

  MPI_Abort(MPI_COMM_WORLD,code);  /* only after servers have all reacted */
  // Should not get here due to Abort above
  assert(false);
  return -1;
}

static void
print_proc_self_status()
{
  int val;
  char input_line[1024], key[100], mag[100];
  FILE *statsfile;

  statsfile = fopen("/proc/self/status","r");
  if (statsfile)
  {
    printf("values from: /proc/self/status:\n");
    while (fgets(input_line,100,statsfile) != NULL)
    {
      if (strncmp(input_line,"VmRSS:",6)  == 0  ||
          strncmp(input_line,"VmHWM:",6)  == 0  ||
          strncmp(input_line,"VmPeak:",7) == 0  ||
          strncmp(input_line,"VmSize:",7) == 0)
      {
        sscanf(input_line,"%s %d %s",key,&val,mag);
        printf("    %s %d %s\n",key,val,mag);
      }
    }
  }
}



//static int get_server_idx(int);
//static int get_server_rank(int);

/*
static int
get_server_idx(int server_rank)
{
  return server_rank - master_server_rank;
}
*/

/*
static int
get_server_rank(int server_idx)
{
  return master_server_rank + server_idx;
}
*/

void
adlb_exit_handler()
{
    printf("adlb_exit_handler:\n");
    print_proc_self_status();
}


