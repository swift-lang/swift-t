
#define _GNU_SOURCE
#include <assert.h>
#include <inttypes.h>
#include <stddef.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/utsname.h>

#include <mpi.h>

#include <c-utils.h>
#include <table.h>
#include <tools.h>

#include "adlb.h"
#include "adlb-version.h"
#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "messaging.h"
#include "mpe-tools.h"
#include "mpi-tools.h"
#include "server.h"

adlb_code next_server;

static void print_proc_self_status(void);

void adlb_exit_handler(void);

/** True after a Get() receives a shutdown code */
static bool got_shutdown = false;

/** Cached copy of MPI world group */
static MPI_Group world_group;

static int mpi_version;

/**
   Maps string hostname to list of int ranks which are running on
   that host
 */
struct table hostmap;

static void
check_versions()
{
  mpi_version = ADLB_MPI_VERSION;

  version av, cuv, rcuv;
  // required c-utils version (rcuv):
  ADLB_Version(&av);
  version_parse(&rcuv, C_UTILS_REQUIRED_VERSION);
  c_utils_version(&cuv);
  version_require("ADLB", &av, "c-utils", &cuv, &rcuv);
}

#define HOSTNAME_MAX 128
static bool setup_hostmap(void);

adlb_code
ADLBP_Init(int nservers, int ntypes, int type_vect[],
           int *am_server, MPI_Comm *worker_comm)
{
  debug_check_environment();
  TRACE_START;

  int initialized;
  check_versions();
  int rc;
  rc = MPI_Initialized(&initialized);
  CHECK_MSG(initialized, "ADLB: MPI is not initialized!\n");

  xlb_start_time = MPI_Wtime();

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
  ASSERT(rc == MPI_SUCCESS);

  MPI_Comm_group(MPI_COMM_WORLD, &world_group);

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
    adlb_code code = xlb_server_init();
    ADLB_CHECK(code);
  }

  setup_hostmap();

  srandom(xlb_world_rank+1);
  TRACE_END;
  return ADLB_SUCCESS;
}

static bool
setup_hostmap()
{
  struct utsname u;
  uname(&u);

  // Length of nodenames
  int length = sizeof(u.nodename);

  // This may be too big for the stack
  char* allnames = malloc((xlb_world_size*length) * sizeof(char));

  char myname[length];
  memset(myname, 0, length);
  strcpy(myname, u.nodename);

  int rc = MPI_Allgather(myname,   length, MPI_CHAR,
                         allnames, length, MPI_CHAR, adlb_all_comm);
  MPI_CHECK(rc);
  table_init(&hostmap, 1024);

  char* p = allnames;
  for (int rank = 0; rank < xlb_world_size; rank++)
  {
    char* name = p;
    // printf("setup_hostmap(): %s -> %i\n", name, rank);

    if (!table_contains(&hostmap, name))
    {
      struct list_i* L = list_i_create();
      table_add(&hostmap, name, L);
    }

    struct list_i* L;
    table_search(&hostmap, name, (void*) &L);
    list_i_add(L, rank);

    p += length;
  }

  free(allnames);
  return true;
}

adlb_code
ADLB_Version(version* output)
{
  version_parse(output, ADLB_VERSION);
  return ADLB_SUCCESS;
}

adlb_code
ADLB_Hostmap(const char* name, int count, int* output, int* actual)
{
  struct list_i* L;
  bool b = table_search(&hostmap, name, (void*) &L);
  if (!b)
    return ADLB_NOTHING;
  int i = 0;
  for (struct list_i_item* item = L->head; item; item = item->next)
  {
    output[i++] = item->data;
    if (i == count)
      break;
  }
  *actual = i;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Put(void* payload, int length, int target, int answer,
          int type, int priority, int parallelism)
{
  MPI_Status status;
  MPI_Request request;
  /** In a redirect, we send the payload to a worker */
  int payload_dest;

  DEBUG("ADLB_Put: target=%i x%i %s",
        target, parallelism, (char*) payload);

  CHECK_MSG(type >= 0 && xlb_type_index(type) >= 0,
            "ADLB_Put(): invalid work type: %d\n", type);

  CHECK_MSG(mpi_version >= 3 || parallelism == 1,
            "ADLB_Put(): "
            "parallel tasks not supported for MPI version %i",
            mpi_version);

  /** Server to contact */
  int to_server = -1;
  if (target == ADLB_RANK_ANY)
    to_server = xlb_my_server;
  else if (target < xlb_world_size)
    to_server = xlb_map_to_server(target);
  else
    valgrind_fail("ADLB_Put(): invalid target rank: %i", target);

  struct packed_put p;
  p.type = type;
  p.priority = priority;
  p.putter = xlb_world_rank;
  p.answer = answer;
  p.target = target;
  p.length = length;
  p.parallelism = parallelism;

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
          int* answer, int* type_recvd, MPI_Comm* comm)
{
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

  DEBUG("ADLB_Get(): payload source: %i", g.payload_source);
  RECV(payload, g.length, MPI_BYTE, g.payload_source, ADLB_TAG_WORK);
  mpi_recv_sanity(&status, MPI_BYTE, g.length);
  TRACE("ADLB_Get(): got: %s", (char*) payload);

  if (g.parallelism > 1)
  {
    // Parallel tasks require MPI 3.  Cf. configure.ac
    #if ADLB_MPI_VERSION >= 3
    // Recv ranks for output comm
    int ranks[g.parallelism];
    RECV(ranks, g.parallelism, MPI_INT, xlb_my_server,
         ADLB_TAG_RESPONSE_GET);
    MPI_Group group;
    MPI_Group_incl(world_group, g.parallelism, ranks, &group);
    // This is an MPI 3 function:
    MPI_Comm_create_group(MPI_COMM_WORLD, group, 0, comm);
    #endif
  }
  else
    *comm = MPI_COMM_SELF;

  *length = g.length;
  *answer = g.answer_rank;
  *type_recvd = g.type;

  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Iget(int type_requested, void* payload, int* length,
           int* answer, int* type_recvd)
{
  MPI_Status status;
  MPI_Request request;

  CHECK_MSG(xlb_type_index(type_requested) != -1,
            "ADLB_Iget(): Bad work type: %i\n", type_requested);

  struct packed_get_response g;
  IRECV(&g, sizeof(g), MPI_BYTE, xlb_my_server, ADLB_TAG_RESPONSE_GET);
  SEND(&type_requested, 1, MPI_INT, xlb_my_server, ADLB_TAG_IGET);
  WAIT(&request, &status);

  mpi_recv_sanity(&status, MPI_BYTE, sizeof(g));

  if (g.code == ADLB_SHUTDOWN)
  {
    DEBUG("ADLB_Iget(): SHUTDOWN");
    got_shutdown = true;
    return ADLB_SHUTDOWN;
  }
  if (g.code == ADLB_NOTHING)
  {
    DEBUG("ADLB_Iget(): NOTHING");
    return ADLB_NOTHING;
  }

  DEBUG("ADLB_Iget: payload source: %i", g.payload_source);
  RECV(payload, g.length, MPI_BYTE, g.payload_source, ADLB_TAG_WORK);

  mpi_recv_sanity(&status, MPI_BYTE, g.length);
  TRACE("ADLB_Iget: got: %s", (char*) payload);

  *length = g.length;
  *answer = g.answer_rank;
  *type_recvd = g.type;

  return ADLB_SUCCESS;
}

int
ADLB_Locate(long id)
{
  int offset = id % xlb_servers;
  int rank = xlb_world_size - xlb_servers + offset;
  // DEBUG("ADLB_Locate(%li) => %i\n", id, rank);
  return rank;
}

/**
   Reusable internal data creation function
   Applications should use the ADLB_Create_type functions in adlb.h
   @param filename Only used for file-type data
   @param subscript_type Only used for container-type data
 */
static inline adlb_code
ADLBP_Create_impl(adlb_datum_id id, adlb_data_type type,
                  const char* filename,
                  adlb_data_type subscript_type, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  int to_server_rank;
  MPI_Status status;
  MPI_Request request;

  to_server_rank = ADLB_Locate(id);
  struct packed_create_request data = { id, type, props };

  struct packed_create_response resp;
  IRECV(&resp, sizeof(resp), MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&data, sizeof(data), MPI_BYTE,
       to_server_rank, ADLB_TAG_CREATE_HEADER);
  WAIT(&request, &status);

  ADLB_DATA_CHECK(resp.dc);

  // Check id makes sense
  assert(id == ADLB_DATA_ID_NULL || id == resp.id);
  if (id == ADLB_DATA_ID_NULL && new_id != NULL) {
    // Tell caller about new id
    *new_id = resp.id;
  }

  if (resp.dc != ADLB_DATA_SUCCESS) {
    return ADLB_ERROR;
  }

  if (type == ADLB_DATA_TYPE_CONTAINER)
  {
    TRACE("ADLB_Create(type=container, subscript_type=%i)",
          subscript_type);
    SEND(&subscript_type, 1, MPI_INT, to_server_rank,
         ADLB_TAG_CREATE_PAYLOAD);
  }
  return ADLB_SUCCESS;
}

/**
   Extern version of this (unused)
 */
adlb_code
ADLBP_Create(adlb_datum_id id, adlb_data_type type,
             const char* filename,
             adlb_data_type subscript_type, adlb_create_props props,
             adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, type, filename, subscript_type,
                           props, new_id);
}

adlb_code
ADLB_Create_integer(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_INTEGER, NULL,
                   ADLB_DATA_TYPE_NULL, props, new_id);
}

adlb_code
ADLB_Create_float(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_FLOAT, NULL,
                   ADLB_DATA_TYPE_NULL, props, new_id);
}

adlb_code
ADLB_Create_string(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_STRING, NULL,
                   ADLB_DATA_TYPE_NULL, props, new_id);
}

adlb_code
ADLB_Create_blob(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_BLOB, NULL,
                   ADLB_DATA_TYPE_NULL, props, new_id);
}

adlb_code
ADLB_Create_container(adlb_datum_id id, adlb_data_type subscript_type,
                      adlb_create_props props, adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_CONTAINER, NULL,
                           subscript_type, props, new_id);
}

adlb_code
ADLBP_Exists(adlb_datum_id id, bool* result)
{
  int to_server_rank = ADLB_Locate(id);

  MPI_Status status;
  MPI_Request request;

  TRACE("ADLB_Exists: <%li>\n", id);

  IRECV(result, sizeof(bool), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_EXISTS);
  WAIT(&request, &status);

  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Store(adlb_datum_id id, void *data, int length,
            bool decr_write_refcount, int** ranks, int *count)
{
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(id);

  if (to_server_rank == xlb_world_rank)
  {
    // This is a server-to-server operation on myself
    TRACE("Store SELF");
    dc = data_store(id, data, length, decr_write_refcount,
                    ranks, count);
    ADLB_DATA_CHECK(dc);
    return ADLB_SUCCESS;
  }

  struct packed_store_hdr hdr = { .id = id,
      .decr_write_refcount = decr_write_refcount };
  IRECV(count, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&hdr, sizeof(struct packed_store_hdr), MPI_BYTE,
       to_server_rank, ADLB_TAG_STORE_HEADER);
  SEND(data, length, MPI_BYTE, to_server_rank,
       ADLB_TAG_STORE_PAYLOAD);
  WAIT(&request, &status);

  // Check to see if list of notifications will be received
  if (*count == -1)
    return ADLB_ERROR;

  if (*count > 0)
  {
    *ranks = malloc(*count*sizeof(int));
    valgrind_assert(*ranks);
    RECV(*ranks, *count, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  }

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

adlb_code
ADLBP_Refcount_incr(adlb_datum_id id, adlb_refcount_type type, int change)
{
  int rc;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  if (type == ADLB_READ_REFCOUNT) {
    DEBUG("ADLB_Refcount_incr: <%li> READ_REFCOUNT %i", id, change);
  } else if (type == ADLB_WRITE_REFCOUNT) {
    DEBUG("ADLB_Refcount_incr: <%li> WRITE_REFCOUNT %i", id, change);
  } else {
    assert(type == ADLB_READWRITE_REFCOUNT);
    DEBUG("ADLB_Refcount_incr: <%li> READWRITE_REFCOUNT %i", id, change);
  }
  int to_server_rank = ADLB_Locate(id);

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE,
                 adlb_all_comm, &request);
  MPI_CHECK(rc);
  struct packed_incr msg = { .id = id, .type = type, .incr = change };
  rc = MPI_Send(&msg, sizeof(msg), MPI_BYTE, to_server_rank,
                ADLB_TAG_REFCOUNT_INCR, adlb_all_comm);
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
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  CHECK_MSG(member_length < ADLB_DATA_MEMBER_MAX,
            "ADLB_Insert(): member too long: <%li>[\"%s\"]\n",
            id, subscript);

  DEBUG("ADLB_Insert: <%li>[%s]=\"%s\"", id, subscript, member);
  int length = sprintf(xfer, "%li %s %i %i",
                       id, subscript, member_length, drops);
  int to_server_rank = ADLB_Locate(id);

  IRECV(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(xfer, length+1, MPI_INT, to_server_rank,
       ADLB_TAG_INSERT_HEADER);
  SEND((char*) member, member_length+1, MPI_BYTE, to_server_rank,
       ADLB_TAG_INSERT_PAYLOAD);
  WAIT(&request, &status);

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
  int to_server_rank = ADLB_Locate(id);

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
               bool decr_read_refcount, void *data, int *length)
{
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(id);

  struct packed_retrieve_hdr hdr =
          { .id = id, .decr_read_refcount = decr_read_refcount };
  IRECV(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&hdr, sizeof(hdr), MPI_BYTE, to_server_rank, ADLB_TAG_RETRIEVE);
  WAIT(&request,&status);

  if (dc == ADLB_DATA_SUCCESS)
  {
    RECV(type, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
    RECV(data, ADLB_PAYLOAD_MAX, MPI_BYTE, to_server_rank,
         ADLB_TAG_RESPONSE);
  }
  else
    return ADLB_ERROR;

  // Set length output parameter
  MPI_Get_count(&status, MPI_BYTE, length);
  DEBUG("RETRIEVE: <%li> (%i bytes)\n", id, *length);
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
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(container_id);

  struct packed_enumerate opts;
  opts.id = container_id;
  // Are we requesting subscripts?
  opts.request_subscripts = *subscripts ? 1 : 0;
  // Are we requesting members?
  opts.request_members = *members ? 1 : 0;
  opts.count = count;
  opts.offset = offset;

  IRECV(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&opts, sizeof(struct packed_enumerate), MPI_BYTE,
       to_server_rank, ADLB_TAG_ENUMERATE);
  WAIT(&request,&status);

  if (dc == ADLB_DATA_SUCCESS)
  {
    RECV(records, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
    if (*subscripts)
    {
      RECV(xfer, ADLB_PAYLOAD_MAX, MPI_BYTE, to_server_rank,
           ADLB_TAG_RESPONSE);
      *subscripts = strdup(xfer);
      // Set length output parameter
      MPI_Get_count(&status, MPI_BYTE, subscripts_length);
    }
    if (*members)
    {
      RECV(xfer, ADLB_PAYLOAD_MAX, MPI_BYTE, to_server_rank,
           ADLB_TAG_RESPONSE);
      int c;
      MPI_Get_count(&status, MPI_BYTE, &c);
      char* A = malloc(c);
      valgrind_assert(A);
      memcpy(A, xfer, c);
      *members = A;
      *members_length = c;
    }
  }
  else
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Unique(long* result)
{
  MPI_Status status;
  MPI_Request request;

  TRACE("ADLBP_Unique()...");

  // This is just something to send, it is ignored by the server
  static int msg = 0;
  int to_server_rank = get_next_server();
  IRECV(result, 1, MPI_LONG, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&msg, 1, MPI_INT, to_server_rank, ADLB_TAG_UNIQUE);
  WAIT(&request, &status);

  if (result == ADLB_DATA_ID_NULL)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Typeof(adlb_datum_id id, adlb_data_type* type)
{
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(id);
  IRECV(type, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_TYPEOF);
  WAIT(&request, &status);

  DEBUG("ADLB_Typeof <%li>=>%i", id, *type);

  if (*type == -1)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Container_typeof(adlb_datum_id id, adlb_data_type* type)
{
  MPI_Status status;
  MPI_Request request;
  // DEBUG("ADLB_Container_typeof: %li", id);

  int to_server_rank = ADLB_Locate(id);
  IRECV(type, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_CONTAINER_TYPEOF);
  WAIT(&request, &status);

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
adlb_code
ADLBP_Lookup(adlb_datum_id id,
             const char* subscript, char* member, int* found)
{
  int to_server_rank;
  MPI_Status status;
  MPI_Request request;

  // DEBUG("lookup: %li\n", hashcode);

  to_server_rank = ADLB_Locate(id);

  char msg[ADLB_DATA_SUBSCRIPT_MAX+32];
  sprintf(msg, "%li %s", id, subscript);
  int msg_length = strlen(msg)+1;

  struct packed_code_length p;
  IRECV(&p, sizeof(p), MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(msg, msg_length, MPI_CHAR, to_server_rank, ADLB_TAG_LOOKUP);
  WAIT(&request, &status);

  if (p.code != ADLB_DATA_SUCCESS)
    return ADLB_ERROR;

  if (p.length == 1)
  {
    RECV(member, ADLB_DATA_MEMBER_MAX, MPI_BYTE, to_server_rank,
         ADLB_TAG_RESPONSE);
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
adlb_code
ADLBP_Subscribe(long id, int* subscribed)
{
  int to_server_rank;
  MPI_Status status;
  MPI_Request request;

  to_server_rank = ADLB_Locate(id);

  IRECV(subscribed, 1, MPI_INT, to_server_rank,
        ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_SUBSCRIBE);
  WAIT(&request, &status);

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
                          adlb_datum_id reference,
                          adlb_data_type ref_type)
{
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int length = sprintf(xfer, "%li %li %s %i",
                         reference, id, subscript, ref_type);

  int to_server_rank = ADLB_Locate(id);

  IRECV(&dc, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(xfer, length+1, MPI_CHAR, to_server_rank,
       ADLB_TAG_CONTAINER_REFERENCE);
  WAIT(&request, &status);

  DEBUG("ADLB_Container_reference: <%li>[%s] => <%li> (%i)",
        id, subscript, reference, ref_type);

  if (dc != ADLB_DATA_SUCCESS)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

/** Return ADLB_ERROR and size=-1 if container is not closed */
adlb_code
ADLBP_Container_size(adlb_datum_id container_id, int* size)
{
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(container_id);

  IRECV(size, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&container_id, 1, MPI_LONG, to_server_rank,
                ADLB_TAG_CONTAINER_SIZE);
  WAIT(&request, &status);

  DEBUG("ADLB_Container_size: <%li> => %i",
        container_id, *size);

  if (*size < 0)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

/**
   @return result 0->try again, 1->locked
 */
adlb_code
ADLBP_Lock(adlb_datum_id id, bool* result)
{
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(id);

  // c 0->try again, 1->locked, x->failed
  char c;
  IRECV(&c, 1, MPI_CHAR, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_LOCK);
  WAIT(&request, &status);

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
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(id);

  // c: 1->success, x->failed
  char c;
  IRECV(&c, 1, MPI_CHAR, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_LONG, to_server_rank, ADLB_TAG_UNLOCK);
  WAIT(&request, &status);

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

static void free_hostmap(void);

adlb_code
ADLBP_Finalize()
{
  TRACE_START;

  int rc;
  int flag;
  MPI_Finalized(&flag);
  CHECK_MSG(!flag,
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
      rc = ADLB_Shutdown();
      ADLB_CHECK(rc);
    }
  }

  free_hostmap();

  bool failed;
  int fail_code;
  xlb_server_failed(&failed, &fail_code);
  if (xlb_world_rank == xlb_master_server_rank && failed)
  {
    printf("FAILED: EXIT(%i)\n", fail_code);
    exit(fail_code);
  }
  return ADLB_SUCCESS;
}

static void
free_hostmap()
{
  for (int i = 0; i < hostmap.capacity; i++)
  {
    struct list_sp* S = hostmap.array[i];
    while (true)
    {
      char* name;
      struct list_i* L;
      bool b = list_sp_pop(S, &name, (void*) &L);
      if (!b)
        break;
      free(name);
      list_i_free(L);
    }
    list_sp_free(S);
  }
  table_release(&hostmap);
}

adlb_code
ADLB_Fail(int code)
{
  printf("ADLB_Fail(%i)\n", code);

  SEND(&code, 1, MPI_INT, xlb_master_server_rank, ADLB_TAG_FAIL);

  // give servers a chance to shut down
  sleep(1);

  return ADLB_SUCCESS;
}

void
ADLB_Abort(int code)
{
  printf("ADLB_Abort(%i)\n", code);
  printf("MPI_Abort(%i)\n", code);
  MPI_Abort(MPI_COMM_WORLD, code);
}

static void
print_proc_self_status()
{
  int val;
  char input_line[1024], key[100], mag[100];
  FILE *statsfile;

  statsfile = fopen("/proc/self/status", "r");
  if (statsfile)
  {
    printf("values from: /proc/self/status:\n");
    while (fgets(input_line,100,statsfile) != NULL)
    {
      if (strncmp(input_line,"VmRSS:",6)  == 0 ||
          strncmp(input_line,"VmHWM:",6)  == 0 ||
          strncmp(input_line,"VmPeak:",7) == 0 ||
          strncmp(input_line,"VmSize:",7) == 0)
      {
        sscanf(input_line,"%s %d %s",key,&val,mag);
        printf("    %s %d %s\n",key,val,mag);
      }
    }
    fclose(statsfile);
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
