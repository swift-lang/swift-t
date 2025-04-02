
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

#define _GNU_SOURCE
#include <assert.h>
#include <inttypes.h>
#include <limits.h>
#include <stddef.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include <mpi.h>

#include <c-utils.h>
#include <list_i.h>
#include <tools.h>

#include "adlb.h"
#include "adlb_types.h"
#include "adlb-version.h"
#include "adlb-xpt.h"
#include "checks.h"
#include "config.h"
#include "client_internal.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "debug_symbols.h"
#include "location.h"
#include "mpe-tools.h"
#include "mpi-tools.h"
#include "notifications.h"
#include "server.h"
#include "sync.h"

static int next_server;

static void print_proc_self_status(void);

void adlb_exit_handler(void);

/** Cached copy of ADLB world group */
static MPI_Group adlb_group;

static int mpi_version;

static inline int choose_data_server(adlb_placement placement);

#define XLB_GET_RESP_HDR_IX 0
#define XLB_GET_RESP_PAYLOAD_IX 1

typedef struct {
  struct packed_get_response hdr;
  MPI_Comm task_comm; // Communicator for parallel tasks
  // MPI_Request objects
  MPI_Request reqs[3];
  int ntotal; // Total number of reqs issued
  int ncomplete; // Number of reqs which completed

  bool in_use; // Whether being used for a request
} xlb_get_req_impl;

/*
  Dynamically sized array to store active get requests.
  adlb_get_req handles are indices of this table.
 */
static struct {
  xlb_get_req_impl *reqs;
  int size; // Size of array

  // Track unused entries
  struct list_i unused_reqs;
  struct list_i spare_nodes; // Save spare list nodes here
} xlb_get_reqs;

#define XLB_GET_REQS_INIT_SIZE 16


static adlb_code xlb_setup_layout(MPI_Comm comm, int nservers);

static adlb_code xlb_get_reqs_init(void);
static adlb_code xlb_get_reqs_finalize(void);
static adlb_code xlb_get_reqs_alloc(adlb_get_req* handles, int count);
static adlb_code xlb_get_req_lookup(adlb_get_req handle,
                                    xlb_get_req_impl** req);
static adlb_code xlb_get_reqs_expand(int min_size);

static adlb_code xlb_block_worker(bool blocking);

static adlb_code xlb_aget_test(adlb_get_req *req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm,
                    xlb_get_req_impl *req_impl);
static adlb_code xlb_aget_progress(adlb_get_req *req_handle,
        xlb_get_req_impl *req, bool blocking, bool free_on_shutdown);
static adlb_code xlb_get_req_cancel(adlb_get_req *req,
                             xlb_get_req_impl *impl);
static adlb_code xlb_get_req_release(adlb_get_req* req,
                    xlb_get_req_impl* impl, bool cancelled);

static adlb_code xlb_parallel_comm_setup(int parallelism, MPI_Comm* comm);

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

static inline int get_next_server();

adlb_code
ADLBP_Init(int nservers, int ntypes, int type_vect[],
           int *am_server, MPI_Comm comm, MPI_Comm *worker_comm)
{
  adlb_code code = xlb_debug_check_environment();
  ADLB_CHECK(code);

  TRACE_START;

  int initialized;
  check_versions();
  int rc;
  rc = MPI_Initialized(&initialized);
  MPI_CHECK(rc);
  ADLB_CHECK_MSG(initialized, "ADLB: MPI is not initialized!\n");

  xlb_s.status = ADLB_STATUS_RUNNING;
  xlb_s.start_time = MPI_Wtime();

  code = xlb_setup_layout(comm, nservers);
  ADLB_CHECK(code);

  xlb_msg_init();

  xlb_s.types_size = ntypes;

  for (int i = 0; i < ntypes; i++)
  {
    ADLB_CHECK_MSG(type_vect[i] == i,
        "Only support type_vect with types 0..ntypes-1: "
        "type_vect[%i] was %i", i, type_vect[i]);
  }

  // Set this correctly before initializing other modules
  xlb_s.perfc_enabled = false; // TODO: don't need this line?
  getenv_boolean("ADLB_PERF_COUNTERS", xlb_s.perfc_enabled,
                 &xlb_s.perfc_enabled);

  next_server = xlb_s.layout.my_server;

  code = xlb_dsyms_init();
  ADLB_CHECK(code);

  srandom((unsigned int)xlb_s.layout.rank+1);

  xlb_s.read_refc_enabled = false;

  adlb_data_code dc = xlb_data_types_init();
  ADLB_DATA_CHECK(dc);

  code = xlb_env_placement(&xlb_s.placement);
  ADLB_CHECK(code);

  code = xlb_get_reqs_init();
  ADLB_CHECK(code);

  rc = MPI_Comm_group(xlb_s.comm, &adlb_group);
  assert(rc == MPI_SUCCESS);

  if (xlb_s.layout.am_server)
  {
    // Need to run this now to setup data module, etc
    code = xlb_server_init(&xlb_s);
    ADLB_CHECK(code);
  }

  *am_server = xlb_s.layout.am_server;
  *worker_comm = xlb_s.worker_comm;

  TRACE_END;
  return ADLB_SUCCESS;
}

/**
 * Setup everything to do with layout of communicator we're running on
 */
static adlb_code
xlb_setup_layout(MPI_Comm comm, int nservers)
{
  int rc;
  adlb_code code;

  xlb_s.comm = comm;
  xlb_s.worker_comm = MPI_COMM_NULL;
  xlb_s.server_comm = MPI_COMM_NULL;
  xlb_s.leader_comm = MPI_COMM_NULL;

  int comm_size;
  int comm_rank;
  rc = MPI_Comm_size(comm, &comm_size);
  MPI_CHECK(rc);
  rc = MPI_Comm_rank(comm, &comm_rank);
  MPI_CHECK(rc);

  DEBUG("ADLB: RANK: %i/%i", comm_rank, comm_size);

  struct xlb_hostnames hostnames;
  code = xlb_hostnames_gather(comm, &hostnames);
  ADLB_CHECK(code);

  code = xlb_layout_init(comm_size, comm_rank, nservers, &hostnames,
                         &xlb_s.layout);
  ADLB_CHECK(code);

  DEBUG("my_server: rank=%i -> server=%i\n",
	xlb_s.layout.rank, xlb_s.layout.my_server);

  code = xlb_get_hostmap_mode(&xlb_s.hostmap_mode);
  ADLB_CHECK(code);

  xlb_s.hostmap = NULL;

  if (xlb_s.hostmap_mode != HOSTMAP_DISABLED)
  {
    struct xlb_hostmap* hostmap;

    // Need hostmap for server init
    code = xlb_hostmap_init(&xlb_s.layout, &hostnames, &hostmap);
    ADLB_CHECK(code);

    code = xlb_setup_leaders(&xlb_s.layout, hostmap,
                             comm, &xlb_s.leader_comm);
    ADLB_CHECK(code);

    if (xlb_s.hostmap_mode == HOSTMAP_ENABLED)
    {
      xlb_s.hostmap = hostmap;
    }
    else
    {
      // We created this table just to set up leaders
      xlb_hostmap_free(hostmap);
    }
  }

  if (xlb_s.layout.am_server)
  {
    MPI_Comm_split(comm, 1, xlb_s.layout.rank-xlb_s.layout.workers,
                   &xlb_s.server_comm);
  }
  else
  {
    MPI_Comm_split(comm, 0, xlb_s.layout.rank, &xlb_s.worker_comm);
  }

  // We only need hostname to rank mapping for setup
  xlb_hostnames_free(&hostnames);

  return ADLB_SUCCESS;
}

adlb_status
ADLB_Status()
{
  return xlb_s.status;
}

adlb_code
ADLB_Version(version* output)
{
  version_parse(output, ADLB_VERSION);
  return ADLB_SUCCESS;
}

MPI_Comm
ADLB_GetComm()
{
  return xlb_s.comm;
}

MPI_Comm
ADLB_GetComm_workers()
{
  return xlb_s.worker_comm;
}

MPI_Comm
ADLB_GetComm_leaders()
{
  return xlb_s.leader_comm;
}

void
ADLB_Leaders(int* leaders, int* count)
{
  xlb_get_leader_ranks(&xlb_s.layout,  xlb_s.hostmap,
                       false, leaders, count);
}

// Server to target with work
__attribute__((always_inline))
static inline adlb_code
adlb_put_target_server(int target, int *to_server)
{
  if (target == ADLB_RANK_ANY)
    *to_server = xlb_s.layout.my_server;
  else if (target < xlb_s.layout.size)
    *to_server = xlb_map_to_server(&xlb_s.layout, target);
  else
    ADLB_CHECK_MSG(target >= 0 && target < xlb_s.layout.size,
              "ADLB_Put(): invalid target rank: %i", target);
  return ADLB_SUCCESS;
}

static inline adlb_code
adlb_put_check_params(int target, int type, adlb_put_opts opts)
{
  ADLB_CHECK_MSG(target == ADLB_RANK_ANY ||
            (target >= 0 && target < xlb_s.layout.workers),
            "ADLB_Put(): invalid target: %i", target);

  ADLB_CHECK_MSG(type >= 0 && type < xlb_s.types_size,
            "ADLB_Put(): invalid work type: %d\n", type);

  ADLB_CHECK_MSG(mpi_version >= 3 || opts.parallelism == 1,
            "ADLB_Put(): "
            "parallel tasks not supported for MPI version %i",
            mpi_version);
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Put(const void* payload, int length, int target, int answer,
          int type, adlb_put_opts opts)
{
  MPI_Status status;
  MPI_Request request;
  adlb_code rc;
  int response;

  DEBUG("ADLB_Put: type=%i target=%i priority=%i strictness=%i "
        "accuracy=%i x%i length=%i \"%.*s\"", type, target, opts.priority,
        opts.strictness, opts.accuracy, opts.parallelism, length, length,
        (char*) payload);

  rc = adlb_put_check_params(target, type, opts);
  ADLB_CHECK(rc);

  /** Server to contact */
  int to_server;
  rc = adlb_put_target_server(target, &to_server);
  ADLB_CHECK(rc);

  int inline_data_len;
  if (length <= PUT_INLINE_DATA_MAX)
  {
    inline_data_len = length;
  }
  else
  {
    inline_data_len = 0;
  }

  size_t p_size = PACKED_PUT_SIZE((size_t)inline_data_len);
  assert(p_size <= ADLB_XFER_SIZE);
  struct packed_put *p = (struct packed_put*)xlb_xfer;
  p->type = type;
  p->putter = xlb_s.layout.rank;
  p->answer = answer;
  p->target = target;
  p->length = length;
  p->opts = opts;
  p->has_inline_data = inline_data_len > 0;
  if (p->has_inline_data)
  {
    memcpy(p->inline_data, payload, (size_t)inline_data_len);
  }

  IRECV(&response, 1, MPI_INT, to_server, ADLB_TAG_RESPONSE_PUT);
  SEND(p, (int)p_size, MPI_BYTE, to_server, ADLB_TAG_PUT);

  WAIT(&request, &status);
  if (response == ADLB_REJECTED)
  {
 //    to_server_rank = next_server++;
//    if (next_server >= (master_server_rank+num_servers))
//      next_server = master_server_rank;
    return (adlb_code)response;
  }

  if (p->has_inline_data)
  {
    // Successfully sent: just check response
    ADLB_CHECK((adlb_code)response);
  }
  else
  {
    int payload_dest = response;
    // Still need to send payload
    // In a redirect, we send the payload to a worker
    DEBUG("ADLB_Put: payload to: %i", payload_dest);
    if (payload_dest == ADLB_RANK_NULL)
      return ADLB_ERROR;
    SSEND(payload, length, MPI_BYTE, payload_dest, ADLB_TAG_WORK);
  }
  TRACE("ADLB_Put: DONE");

  return ADLB_SUCCESS;
}

adlb_code ADLBP_Dput(const void* payload, int length, int target,
        int answer, int type, adlb_put_opts opts, const char *name,
        const adlb_datum_id* wait_ids, int wait_id_count,
        const adlb_datum_id_sub* wait_id_subs, int wait_id_sub_count)
{
  MPI_Status status;
  MPI_Request request;
  int response;
  adlb_code rc;

  DEBUG("ADLB_Dput: target=%i x%i length=%i %.*s",
        target, opts.parallelism, length, length, (char*) payload);

  rc = adlb_put_check_params(target, type, opts);
  ADLB_CHECK(rc);

  /** Server to contact */
  int to_server;
  rc = adlb_put_target_server(target, &to_server);
  ADLB_CHECK(rc);

  int inline_data_len;
  if (length <= PUT_INLINE_DATA_MAX)
  {
    inline_data_len = length;
  }
  else
  {
    inline_data_len = 0;
  }

  struct packed_dput* p = (struct packed_dput*) xlb_xfer;
  p->type = type;
  p->putter = xlb_s.layout.rank;
  p->answer = answer;
  p->target = target;
  p->length = length;
  p->opts = opts;
  p->has_inline_data = inline_data_len > 0;
  p->id_count = wait_id_count;
  p->id_sub_count = wait_id_sub_count;

  int p_len = (int)sizeof(struct packed_dput);

  // pack in all needed data at end
  char *p_data = (char*)p->inline_data;

  size_t wait_id_len = sizeof(wait_ids[0]) * (size_t)wait_id_count;
  memcpy(p_data, wait_ids, wait_id_len);
  p_data += wait_id_len;
  p_len += (int)wait_id_len;

  for (int i = 0; i < wait_id_sub_count; i++)
  {
    int packed_len = xlb_pack_id_sub(p_data, wait_id_subs[i].id,
                                      wait_id_subs[i].subscript);
    p_data += packed_len;
    p_len += packed_len;
  }

  #ifndef NDEBUG
  // Don't pack name if NDEBUG on
  p->name_strlen = (int)strlen(name);
  memcpy(p_data, name, (size_t)p->name_strlen);
  p_data += p->name_strlen;
  p_len += p->name_strlen;
  #endif

  if (p->has_inline_data)
  {
    memcpy(p_data, payload, (size_t)inline_data_len);
    p_data += inline_data_len;
    p_len += inline_data_len;
  }

  // xlb_xfer is much larger than we need for ids/subs plus inline data
  assert(p_len < ADLB_XFER_SIZE);

  IRECV(&response, 1, MPI_INT, to_server, ADLB_TAG_RESPONSE_PUT);
  SEND(p, (int)p_len, MPI_BYTE, to_server, ADLB_TAG_DPUT);

  WAIT(&request, &status);
  if ((adlb_code)response == ADLB_REJECTED)
  {
 //    to_server_rank = next_server++;
//    if (next_server >= (master_server_rank+num_servers))
//      next_server = master_server_rank;
    return (adlb_code)response;
  }

  // Check response before sending any payload data
  ADLB_CHECK((adlb_code)response);
  if (!p->has_inline_data)
  {
    // Second response to confirm entered ok
    IRECV(&response, 1, MPI_INT, to_server, ADLB_TAG_RESPONSE_PUT);
    // Still need to send payload
    // Note: don't try to redirect work for data-dependent work
    // Use RSEND so that server can pre-allocate a buffer
    RSEND(payload, length, MPI_BYTE, to_server, ADLB_TAG_WORK);
    WAIT(&request, &status);
    ADLB_CHECK((adlb_code)response);
  }
  DEBUG("ADLB_Dput: DONE");
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Get(int type_requested, void** payload,
          int* length, int max_length,
          int* answer, int* type_recvd, MPI_Comm* comm)
{
  adlb_code rc;
  MPI_Status status;
  MPI_Request request;

  TRACE_START;

  if (xlb_s.layout.am_leader)
  {
    TRACE("Get(): post: rank=%i", xlb_s.layout.rank);
  }

  ADLB_CHECK_MSG(type_requested >= 0 && type_requested < xlb_s.types_size,
                "ADLB_Get(): Bad work type: %i\n", type_requested);

  struct packed_get_response g;
  IRECV(&g, sizeof(g), MPI_BYTE, xlb_s.layout.my_server, ADLB_TAG_RESPONSE_GET);
  SEND(&type_requested, 1, MPI_INT, xlb_s.layout.my_server, ADLB_TAG_GET);
  WAIT(&request, &status);

  if (xlb_s.layout.am_leader)
  {
    TRACE("Get(): recv rank=%i", xlb_s.layout.rank);
  }

  xlb_mpi_recv_sanity(&status, MPI_BYTE, sizeof(g));

  if (g.code == ADLB_SHUTDOWN)
  {
    DEBUG("ADLB_Get(): SHUTDOWN");
    xlb_s.status = ADLB_STATUS_SHUTDOWN;
    return ADLB_SHUTDOWN;
  }

  if (g.length > max_length)
  {
    DEBUG("ADLB_Get(): user-provided max length too small!");
    return ADLB_ERROR;
  }

  void* buffer = *payload;
  if (buffer == NULL || g.length > *length)
  {
    // User buffer is insufficient:
    buffer = malloc(g.length);
    valgrind_assert(buffer != NULL);
    *payload = buffer;
  }

  TRACE("ADLB_Get(): payload source: %i", g.payload_source);
  RECV(buffer, g.length, MPI_BYTE, g.payload_source, ADLB_TAG_WORK);
  xlb_mpi_recv_sanity(&status, MPI_BYTE, g.length);
  DEBUG("ADLB_Get(): got: %s", (char*) buffer);

  if (xlb_s.layout.am_leader)
  {
    TRACE("Get(): payload rank=%i", xlb_s.layout.rank);
  }

  if (g.parallelism > 1)
  {
    rc = xlb_parallel_comm_setup(g.parallelism, comm);
    ADLB_CHECK(rc);
  }
  else
    *comm = MPI_COMM_SELF;

  *length     = g.length;
  *answer     = g.answer_rank;
  *type_recvd = g.type;

  TRACE_END;
  return ADLB_SUCCESS;
}

/*
 * Receive info about parallel workers and setup communicator.
 */
static adlb_code
xlb_parallel_comm_setup(int parallelism, MPI_Comm* comm)
{
  if (xlb_s.layout.am_leader)
  {
    INFO("xlb_parallel_comm_setup(): parallelism=%i rank=%i",
         parallelism, xlb_s.layout.rank);
  }
  // Parallel tasks require MPI 3.  Cf. configure.ac
  ADLB_CHECK_MSG(ADLB_MPI_VERSION >= 3,
                 "Parallel tasks not supported for MPI version %i < 3",
                 ADLB_MPI_VERSION);
  #if ADLB_MPI_VERSION >= 3
  MPI_Status status;
  // Recv ranks for output comm
  int ranks[parallelism];
  RECV(ranks, parallelism, MPI_INT, xlb_s.layout.my_server,
       ADLB_TAG_RESPONSE_GET);

  if (xlb_s.layout.am_leader)
  {
    INFO("xlb_parallel_comm_setup(): ranks rank=%i",
         xlb_s.layout.rank);
  }

  MPI_Group group;
  int rc;
  rc = MPI_Group_incl(adlb_group, parallelism, ranks, &group);
  assert(rc == MPI_SUCCESS);
  // This is an MPI 3 function:
  rc = MPI_Comm_create_group(xlb_s.comm, group, 0, comm);
  valgrind_assert(rc == MPI_SUCCESS);
  MPI_Group_free(&group);
  if (xlb_s.layout.am_leader)
  {
    INFO("xlb_parallel_comm_setup(): grouped rank=%i",
         xlb_s.layout.rank);
  }

  TRACE("MPI_Comm_create_group(): comm=%llu\n",
        (long long unsigned int) *comm);
  #endif

  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Iget(int type_requested, void* payload, int* length,
           int* answer, int* type_recvd, MPI_Comm* comm)
{
  adlb_code rc;
  MPI_Status status;
  MPI_Request request;

  ADLB_CHECK_MSG(type_requested >= 0 && type_requested < xlb_s.types_size,
            "ADLB_Iget(): Bad work type: %i\n", type_requested);

  struct packed_get_response g;
  IRECV(&g, sizeof(g), MPI_BYTE, xlb_s.layout.my_server, ADLB_TAG_RESPONSE_GET);
  SEND(&type_requested, 1, MPI_INT, xlb_s.layout.my_server, ADLB_TAG_IGET);
  WAIT(&request, &status);

  xlb_mpi_recv_sanity(&status, MPI_BYTE, sizeof(g));

  if (g.code == ADLB_SHUTDOWN)
  {
    DEBUG("ADLB_Iget(): SHUTDOWN");
    xlb_s.status = ADLB_STATUS_SHUTDOWN;
    return ADLB_SHUTDOWN;
  }
  if (g.code == ADLB_NOTHING)
  {
    DEBUG("ADLB_Iget(): NOTHING");
    return ADLB_NOTHING;
  }

  DEBUG("ADLB_Iget: payload source: %i", g.payload_source);
  RECV(payload, g.length, MPI_BYTE, g.payload_source, ADLB_TAG_WORK);

  xlb_mpi_recv_sanity(&status, MPI_BYTE, g.length);
  TRACE("ADLB_Iget: got: %s", (char*) payload);

  *length = g.length;
  *answer = g.answer_rank;
  *type_recvd = g.type;

  if (g.parallelism > 1)
  {
    rc = xlb_parallel_comm_setup(g.parallelism, comm);
    ADLB_CHECK(rc);
  }
  else
    *comm = MPI_COMM_SELF;

  return ADLB_SUCCESS;
}

static adlb_code xlb_get_reqs_init(void)
{
  xlb_get_reqs.reqs = NULL;
  xlb_get_reqs.size = 0;

  list_i_init(&xlb_get_reqs.unused_reqs);
  list_i_init(&xlb_get_reqs.spare_nodes);
  return ADLB_SUCCESS;
}

static adlb_code xlb_get_reqs_finalize(void)
{
  adlb_code ac;

  // Cancel any outstanding requests
  for (int i = 0; i < xlb_get_reqs.size; i++)
  {
    xlb_get_req_impl *req = &xlb_get_reqs.reqs[i];
    if (req->in_use)
    {
      adlb_get_req tmp_handle = i;
      ac = xlb_get_req_cancel(&tmp_handle, req);
      ADLB_CHECK(ac);
    }
  }

  if (xlb_get_reqs.reqs != NULL)
  {
    free(xlb_get_reqs.reqs);
  }
  xlb_get_reqs.reqs = NULL;
  xlb_get_reqs.size = 0;
  list_i_clear(&xlb_get_reqs.unused_reqs);
  list_i_clear(&xlb_get_reqs.spare_nodes);
  return ADLB_SUCCESS;
}

/*
  Allocate handles for get requests.
 */
static adlb_code xlb_get_reqs_alloc(adlb_get_req *handles, int count)
{
  adlb_code ac;
  assert(count >= 0);

  // Check array is large enough for all requests
  if (count > xlb_get_reqs.unused_reqs.size)
  {
    int curr_used = xlb_get_reqs.size - xlb_get_reqs.unused_reqs.size;
    int required_size = curr_used + count;
    ac = xlb_get_reqs_expand(required_size);
    ADLB_CHECK(ac);
  }

  for (int i = 0; i < count; i++)
  {
    struct list_i_item *node;
    node = list_i_pop_item(&xlb_get_reqs.unused_reqs);
    assert(node != NULL);

    int req_ix = node->data;
    list_i_add_item(&xlb_get_reqs.spare_nodes, node);

    xlb_get_req_impl *req = &xlb_get_reqs.reqs[req_ix];
    assert(!req->in_use);
    req->in_use = true;

    handles[i] = req_ix;
  }

  return ADLB_SUCCESS;
}

static adlb_code xlb_get_req_lookup(adlb_get_req handle,
                                    xlb_get_req_impl **req)
{
  ADLB_CHECK_MSG(handle >= 0 && handle < xlb_get_reqs.size,
            "Invalid adlb_get_req: out of range (%i)", handle);

  xlb_get_req_impl *tmp = &xlb_get_reqs.reqs[handle];
  ADLB_CHECK_MSG(tmp->in_use, "Invalid or old adlb_get_req (%i)", handle);

  *req = tmp;
  return ADLB_SUCCESS;
}

static adlb_code xlb_get_reqs_expand(int min_size)
{
  if (xlb_get_reqs.size >= min_size)
  {
    return ADLB_SUCCESS;
  }

  int old_size = xlb_get_reqs.size;
  int new_size;
  if (old_size == 0)
  {
    new_size = XLB_GET_REQS_INIT_SIZE;
  }
  else
  {
    new_size = old_size * 2;
  }

  new_size = (new_size >= min_size) ? new_size : min_size;

  xlb_get_req_impl *new_reqs;
  new_reqs = malloc(sizeof(xlb_get_req_impl) * (size_t) new_size);
  ADLB_CHECK_MALLOC(new_reqs);

  xlb_get_reqs.reqs = new_reqs;
  xlb_get_reqs.size = new_size;

  for (int i = old_size; i < new_size; i++)
  {
    xlb_get_reqs.reqs[i].in_use = false;

    // Track unused entries
    struct list_i_item *node = malloc(sizeof(struct list_i_item));
    ADLB_CHECK_MALLOC(node);
    node->data = i;
    list_i_add_item(&xlb_get_reqs.unused_reqs, node);
  }

  return ADLB_SUCCESS;
}

adlb_code ADLBP_Aget(int type_requested, adlb_payload_buf payload,
                     adlb_get_req *req)
{
  // Special case of Amget
  return ADLBP_Amget(type_requested, 1, false, &payload, req);
}

adlb_code ADLBP_Amget(int type_requested, int nreqs, bool wait,
                      const adlb_payload_buf* payloads,
                      adlb_get_req *reqs)
{
  adlb_code ac;
  assert(nreqs >= 0);
  if (nreqs <= 0)
  {
    return ADLB_SUCCESS;
  }

  ADLB_CHECK_MSG(type_requested >= 0 && type_requested < xlb_s.types_size,
                "ADLB_Amget(): Bad work type: %i\n", type_requested);

  ac = xlb_get_reqs_alloc(reqs, nreqs);
  ADLB_CHECK(ac);

  for (int i = 0; i < nreqs; i++)
  {
    // TODO: this assumes that requests won't be matched out of the
    //  order they're initiated in.  We would need to use MPI tags
    //  to avoid this problem.
    adlb_get_req handle = reqs[i];
    xlb_get_req_impl *R = &xlb_get_reqs.reqs[handle];
    IRECV2(&R->hdr, sizeof(R->hdr), MPI_BYTE, xlb_s.layout.my_server,
          ADLB_TAG_RESPONSE_GET, &R->reqs[XLB_GET_RESP_HDR_IX]);

    const adlb_payload_buf* payload = &payloads[i];
    TRACE("ADLB_Amget(): post payload buffer %i/%i: %p %i",
          i + 1, nreqs, payload->payload, payload->size);
    assert(payload->size >= 0);

    // Initiate a receive for up to the max payload expected
    IRECV2(payload->payload, payload->size, MPI_BYTE, xlb_s.layout.my_server,
          ADLB_TAG_WORK, &R->reqs[XLB_GET_RESP_PAYLOAD_IX]);

    R->ntotal = 2;
    R->ncomplete = 0;

    // TODO: don't handle parallel task ranks
  }

  // Send request after receives initiated
  struct packed_mget_request hdr = { .type = type_requested,
                         .count = nreqs, .blocking = wait };
  SEND(&hdr, sizeof(hdr), MPI_BYTE, xlb_s.layout.my_server, ADLB_TAG_AMGET);

  if (wait)
  {
    xlb_get_req_impl *req_impl;
    ac = xlb_get_req_lookup(reqs[0], &req_impl);
    ADLB_CHECK(ac);

    // Note: ignore ADLB_SHUTDOWN, handle when testing handle
    ac = xlb_aget_progress(&reqs[0], req_impl, true, false);
    ADLB_CHECK(ac);
  }

  return ADLB_SUCCESS;
}

adlb_code ADLBP_Aget_test(adlb_get_req* req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm)
{
  adlb_code ac;
  xlb_get_req_impl *req_impl;
  ac = xlb_get_req_lookup(*req, &req_impl);
  ADLB_CHECK(ac);

  return xlb_aget_test(req, length, answer, type_recvd, comm, req_impl);
}

/*
  Test for completion.  Release request and do other cleanup
  on success.
 */
static adlb_code xlb_aget_test(adlb_get_req *req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm,
                    xlb_get_req_impl *req_impl)
{
  adlb_code ac;
  ac = xlb_aget_progress(req, req_impl, false, true);
  ADLB_CHECK(ac);

  if (ac == ADLB_NOTHING || ac == ADLB_SHUTDOWN)
  {
    return ac;
  }

  *length = req_impl->hdr.length;
  *answer = req_impl->hdr.answer_rank;
  *type_recvd = req_impl->hdr.type;
  *comm = req_impl->task_comm;

  // Release and invalidate request
  ac = xlb_get_req_release(req, req_impl, false);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

/*
  Make progress on get request.

  blocking: if true, don't return until completed
  free_on_shutdown: if true, deallocate request on shutdown

  Returns ADLB_SUCCESS if completed, ADLB_NOTHING if not complete,
    ADLB_ERROR if error encountered, ADLB_SHUTDOWN on shutdown

  Note that on errors we can't yet cleanly terminate requests.
  In this situation subsequent requests may be matched to wrong thing.
 */
static adlb_code xlb_aget_progress(adlb_get_req *req_handle,
        xlb_get_req_impl *req, bool blocking, bool free_on_shutdown)
{
  int rc;
  assert(req->in_use);

  if (req->ncomplete > XLB_GET_RESP_HDR_IX &&
      free_on_shutdown &&
      req->hdr.code == ADLB_SHUTDOWN)
  {
    // Handle previously deferred shutdown
    adlb_code ac = xlb_get_req_cancel(req_handle, req);
    ADLB_CHECK(ac);

    return ADLB_SHUTDOWN;
  }

  while (req->ncomplete < req->ntotal)
  {
    int mpireq_num = req->ncomplete;

    if (blocking)
    {
      // Wait for all outstanding requests
      rc = MPI_Wait(&req->reqs[req->ncomplete], MPI_STATUS_IGNORE);
      MPI_CHECK(rc);

      req->ncomplete++;
    }
    else
    {
      int flag;
      rc = MPI_Test(&req->reqs[req->ncomplete], &flag,
                    MPI_STATUS_IGNORE);
      MPI_CHECK(rc);

      if (!flag)
      {
        return ADLB_NOTHING;
      }
      req->ncomplete++;
    }

    // Special handling for requests
    if (mpireq_num == XLB_GET_RESP_HDR_IX &&
        req->hdr.code != ADLB_SUCCESS)
    {
      if (req->hdr.code != ADLB_SHUTDOWN ||
          free_on_shutdown)
      {
        /* E.g. shutdown or error */
        adlb_code ac = xlb_get_req_cancel(req_handle, req);
        ADLB_CHECK(ac);
      }

      return req->hdr.code;
    }
  }

  // TODO: remove when we support parallel tasks with Amget
  ADLB_CHECK_MSG(req->hdr.parallelism == 1, "Don't yet support "
            "receiving parallel tasks with ADLB_Aget or ADLB_Amget");

  // TODO: parallel task logic e.g. communicator creation

  req->task_comm = MPI_COMM_SELF;
  return ADLB_SUCCESS;
}

/*
 * Cancel a get request.
 * Note that this doesn't cleanly cancel it, since messages may end up
 * being matched to the wrong thing.
 */
static adlb_code xlb_get_req_cancel(adlb_get_req *req,
                             xlb_get_req_impl *impl)
{
  assert(impl->in_use);

  adlb_code ac;

  // Cancel outstanding MPI requests, regardless of whether they
  // completed or not.
  for (int i = impl->ncomplete; i < impl->ntotal; i++)
  {
    CANCEL(&impl->reqs[i]);
  }

  // Release the request object
  ac = xlb_get_req_release(req, impl, true);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

static adlb_code xlb_get_req_release(adlb_get_req* req,
                    xlb_get_req_impl* impl, bool cancelled)
{
  // Should be completed
  assert(impl->in_use);
  assert(cancelled || impl->ncomplete == impl->ntotal);

  impl->in_use = false;

  struct list_i_item *node = list_i_pop_item(&xlb_get_reqs.spare_nodes);
  assert(node != NULL);
  node->data = *req;
  list_i_add_item(&xlb_get_reqs.unused_reqs, node);

  *req = ADLB_GET_REQ_NULL;
  return ADLB_SUCCESS;
}

adlb_code ADLBP_Aget_wait(adlb_get_req *req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm)
{
  adlb_code ac;

  xlb_get_req_impl *req_impl;
  ac = xlb_get_req_lookup(*req, &req_impl);
  ADLB_CHECK(ac);

  ac = xlb_aget_test(req, length, answer, type_recvd, comm, req_impl);
  ADLB_CHECK(ac);
  if (ac != ADLB_NOTHING)
  {
    // Completed - may be success, shutdown, etc
    return ac;
  }

  // Get ready to block
  ac = xlb_block_worker(true);
  ADLB_CHECK(ac);

  ac = xlb_aget_progress(req, req_impl, true, true);
  ADLB_CHECK(ac);
  if (ac == ADLB_SHUTDOWN)
  {
    return ADLB_SHUTDOWN;
  }
  assert(ac == ADLB_SUCCESS); // Shouldn't be ADLB_NOTHING

  // Notify we're unblocked
  ac = xlb_block_worker(false);
  ADLB_CHECK(ac);

  *length = req_impl->hdr.length;
  *answer = req_impl->hdr.answer_rank;
  *type_recvd = req_impl->hdr.type;
  *comm = req_impl->task_comm;

  ac = xlb_get_req_release(req, req_impl, false);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

/*
  Notify server that worker is blocking or unblocking on get request.
  blocking: true if becoming blocked, false if unblocking
 */
static adlb_code xlb_block_worker(bool blocking)
{
  int msg = blocking ? 1 : 0;

  SEND(&msg, 1, MPI_INT, xlb_s.layout.my_server, ADLB_TAG_BLOCK_WORKER);

  // Don't wait for response

  return ADLB_SUCCESS;
}

int
ADLB_Locate(adlb_datum_id id)
{
  int offset = abs((int) (id % xlb_s.layout.servers));
  int rank = xlb_s.layout.size - xlb_s.layout.servers + offset;
  // DEBUG("ADLB_Locate(%li) => %i\n", id, rank);
  return rank;
}

/**
   Reusable internal data creation function
   Applications should use the ADLB_Create_type functions in adlb.h
   @param filename Only used for file-type data
   @param type_extra Additional type info
 */
static adlb_code
ADLBP_Create_impl(adlb_datum_id id, adlb_data_type type,
                  adlb_type_extra type_extra,
                  adlb_create_props props,
                  adlb_datum_id *new_id)
{
  int to_server_rank;
  MPI_Status status;
  MPI_Request request;
  adlb_data_code dc;

  if (id != ADLB_DATA_ID_NULL) {
    to_server_rank = ADLB_Locate(id);
  } else {
    to_server_rank = choose_data_server(props.placement);
  }

  if (to_server_rank == xlb_s.layout.rank)
  {
    if (id == ADLB_DATA_ID_NULL) {
      dc = xlb_data_unique(&id);
      ADLB_DATA_CHECK(dc);
    }
    dc = xlb_data_create(id, type, &type_extra, &props);
    ADLB_DATA_CHECK(dc);
    return ADLB_SUCCESS;
  }

  ADLB_create_spec data = { id, type, type_extra, props };

  struct packed_create_response resp;
  IRECV(&resp, sizeof(resp), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);
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

  return ADLB_SUCCESS;
}

/**
   Extern version of this (unused)
 */
adlb_code
ADLBP_Create(adlb_datum_id id, adlb_data_type type,
             adlb_type_extra type_extra,
             adlb_create_props props,
             adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, type, type_extra, props, new_id);
}

/*
  Comparison function for specs to sort by server in ascending order
 */
static int create_spec_cmp(const void *p1, const void *p2)
{
  const xlb_create_spec *spec1 = p1, *spec2 = p2;
  if (spec1->server < spec2->server)
  {
    return -1;
  }
  else if (spec1->server > spec2->server)
  {
    return 1;
  }
  else
  {
    return 0;
  }
}

adlb_code ADLBP_Multicreate(ADLB_create_spec *specs, int count)
{
  MPI_Request request;
  MPI_Status status;

  // Temporary specs (reorder and store allocated ids)
  xlb_create_spec tmp_specs[count];
  adlb_datum_id ids[count];
  for (int i = 0; i < count; i++) {
    tmp_specs[i].idx = i;
    tmp_specs[i].spec = specs[i];
    tmp_specs[i].server = choose_data_server(specs[i].props.placement);
  }

  // Sort so that we can send them to servers in batches
  qsort(tmp_specs, (size_t)count, sizeof(tmp_specs[0]), create_spec_cmp);

  int pos = 0;

  while (pos < count)
  {
    int batch_start = pos;
    int server = tmp_specs[pos++].server;
    while (pos < count && tmp_specs[pos].server == server)
    {
      pos++;
    }

    int batch_size = pos - batch_start;
    TRACE("Send batch of size %i/%i from %i to %i", batch_size,
          count, xlb_s.layout.rank, server);
    if (server == xlb_s.layout.rank)
    {
      adlb_data_code dc;
      dc = xlb_data_multicreate(&tmp_specs[batch_start], batch_size, ids);
      ADLB_DATA_CHECK(dc);
    }
    else
    {
      IRECV(ids, (int)sizeof(ids[0]) * batch_size, MPI_BYTE, server,
            ADLB_TAG_RESPONSE);

      SEND(&tmp_specs[batch_start], (int)sizeof(tmp_specs[0]) * batch_size,
           MPI_BYTE, server, ADLB_TAG_MULTICREATE);
      WAIT(&request, &status);
    }

    // Check success by inspecting ids
    // Copy results back to input
    for (int i = 0; i < batch_size; i++) {
      if (ids[i] == ADLB_DATA_ID_NULL) {
        return ADLB_ERROR;
      }
      specs[tmp_specs[batch_start + i].idx].id = ids[i];
    }
  }
  return ADLB_SUCCESS;
}

adlb_code
ADLB_Create_integer(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_INTEGER,
                   ADLB_TYPE_EXTRA_NULL, props, new_id);
}

adlb_code
ADLB_Create_float(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_FLOAT,
                ADLB_TYPE_EXTRA_NULL, props, new_id);
}

adlb_code
ADLB_Create_string(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_STRING,
              ADLB_TYPE_EXTRA_NULL, props, new_id);
}

adlb_code
ADLB_Create_blob(adlb_datum_id id, adlb_create_props props,
                  adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_BLOB, ADLB_TYPE_EXTRA_NULL,
                   props, new_id);
}

adlb_code ADLB_Create_ref(adlb_datum_id id, adlb_create_props props,
                              adlb_datum_id *new_id)
{
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_REF, ADLB_TYPE_EXTRA_NULL,
                   props, new_id);
}

adlb_code ADLB_Create_struct(adlb_datum_id id, adlb_create_props props,
                   adlb_struct_type struct_type, adlb_datum_id *new_id)
{
  adlb_type_extra extra;
  extra.STRUCT.struct_type = struct_type;
  extra.valid = (struct_type != ADLB_STRUCT_TYPE_NULL);
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_STRUCT, extra, props,
                           new_id);
}

adlb_code
ADLB_Create_container(adlb_datum_id id, adlb_data_type key_type,
                      adlb_data_type val_type,
                      adlb_create_props props, adlb_datum_id *new_id)
{
  adlb_type_extra extra_type;
  extra_type.valid = true;
  extra_type.CONTAINER.key_type = key_type;
  extra_type.CONTAINER.val_type = val_type;
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_CONTAINER, extra_type,
                           props, new_id);
}

adlb_code ADLB_Create_multiset(adlb_datum_id id,
                                adlb_data_type val_type,
                                adlb_create_props props,
                                adlb_datum_id *new_id)
{
  adlb_type_extra extra_type;
  extra_type.valid = true;
  extra_type.MULTISET.val_type = val_type;
  return ADLBP_Create_impl(id, ADLB_DATA_TYPE_MULTISET, extra_type,
                           props, new_id);
}

adlb_code
ADLBP_Exists(adlb_datum_id id, adlb_subscript subscript, bool* result,
             adlb_refc decr)
{
  int to_server_rank = ADLB_Locate(id);

  MPI_Status status;
  MPI_Request request;

  TRACE("ADLB_Exists: "ADLB_PRID"\n",
        ADLB_PRID_ARGS(id, ADLB_DSYM_NULL));

  char req[PACKED_SUBSCRIPT_MAX + sizeof(decr)];
  char *req_ptr = req;
  req_ptr += xlb_pack_id_sub(req_ptr, id, subscript);
  assert(req_ptr > req);

  MSG_PACK_BIN(req_ptr, decr);

  struct packed_bool_resp resp;
  IRECV(&resp, sizeof(resp), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);
  SEND(req, (int)(req_ptr - req), MPI_BYTE, to_server_rank,
       ADLB_TAG_EXISTS);
  WAIT(&request, &status);

  ADLB_DATA_CHECK(resp.dc);
  *result = resp.result;
  return ADLB_SUCCESS;
}
adlb_code
ADLBP_Refcount_get(adlb_datum_id id, adlb_refc *result,
                              adlb_refc decr)
{
  int to_server_rank = ADLB_Locate(id);

  MPI_Status status;
  MPI_Request request;

  TRACE("ADLB_Refcount_get: "ADLB_PRID,
        ADLB_PRID_ARGS(id, ADLB_DSYM_NULL));

  struct packed_refcounts_req req = { .id = id, .decr = decr };

  struct packed_refcounts_resp resp;
  IRECV(&resp, sizeof(resp), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);
  SEND(&req, sizeof(req), MPI_BYTE, to_server_rank,
       ADLB_TAG_GET_REFCOUNTS);
  WAIT(&request, &status);

  ADLB_DATA_CHECK(resp.dc);
  *result = resp.refcounts;
  return ADLB_SUCCESS;

}

adlb_code
ADLBP_Store(adlb_datum_id id, adlb_subscript subscript,
          adlb_data_type type, const void *data, size_t length,
          adlb_refc refcount_decr, adlb_refc store_refcounts)
{
  adlb_notif_t notifs = ADLB_NO_NOTIFS;
  adlb_code rc, final_rc;

  final_rc = xlb_store(id, subscript, type, data, length, refcount_decr,
                       store_refcounts, &notifs);
  ADLB_CHECK(final_rc); // Check for ADLB_ERROR, not other codes

  rc = xlb_notify_all(&notifs);
  ADLB_CHECK(rc);

  xlb_free_notif(&notifs);

  return final_rc;
}

adlb_code
xlb_store(adlb_datum_id id, adlb_subscript subscript,
          adlb_data_type type, const void *data, size_t length,
          adlb_refc refcount_decr, adlb_refc store_refcounts,
          adlb_notif_t *notifs)
{
  adlb_code code;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  ADLB_CHECK_MSG(length < ADLB_DATA_MAX,
            "ADLB_Store(): value too long: %llu max: %llu\n",
            (long long unsigned) length, ADLB_DATA_MAX);

  if (adlb_has_sub(subscript))
  {
    DEBUG("ADLB_Store: "ADLB_PRIDSUB"=%p[%zu]",
        ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript),
        data, length);
  }
  else
  {
    DEBUG("ADLB_Store: "ADLB_PRID"=%p[%zu]",
          ADLB_PRID_ARGS(id, ADLB_DSYM_NULL), data, length);
  }

  int to_server_rank = ADLB_Locate(id);
  if (to_server_rank == xlb_s.layout.rank)
  {
    // This is a server-to-server operation on myself
    // Can cast away const since we're forcing it to copy
    dc = xlb_data_store(id, subscript, (void*)data, length, true, NULL,
                    type, refcount_decr, store_refcounts, notifs);
    if (dc == ADLB_DATA_ERROR_DOUBLE_WRITE)
      return ADLB_REJECTED;
    ADLB_DATA_CHECK(dc);

    return ADLB_SUCCESS;
  }
  TRACE("Store to server %i", to_server_rank);

  if (xlb_s.layout.am_server)
  {
    code = xlb_sync(to_server_rank);
    ADLB_CHECK(code);
  }

  struct packed_store_hdr hdr = {
    .id = id,
    .type = type,
    .length = length,
    .subscript_len = adlb_has_sub(subscript) ? subscript.length : 0,
    .refcount_decr = refcount_decr,
    .store_refcounts = store_refcounts
  };
  struct packed_store_resp resp;

  IRECV(&resp, sizeof(resp), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);
  SEND(&hdr, sizeof(struct packed_store_hdr), MPI_BYTE,
       to_server_rank, ADLB_TAG_STORE_HEADER);
  if (adlb_has_sub(subscript))
  {
    SEND(subscript.key, (int)subscript.length, MPI_BYTE, to_server_rank,
         ADLB_TAG_STORE_SUBSCRIPT);
  }
  mpi_send_big(data, length, to_server_rank, ADLB_TAG_STORE_PAYLOAD);

  WAIT(&request, &status);

  if (resp.dc == ADLB_DATA_ERROR_DOUBLE_WRITE)
    return ADLB_REJECTED;
  ADLB_DATA_CHECK(resp.dc);

  code = xlb_recv_notif_work(&resp.notifs, to_server_rank, notifs);
  ADLB_CHECK(code);

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
  int offset = next_server_index % xlb_s.layout.servers;
  int rank = xlb_s.layout.size - xlb_s.layout.servers + offset;
  // DEBUG("xlb_random_server => %i\n", rank);
  next_server_index = (next_server_index + 1) % xlb_s.layout.servers;
  return rank;
}

/**
  Choose server to create data on
 */
static inline int
choose_data_server(adlb_placement placement)
{
  int server;
  if (placement == ADLB_PLACE_DEFAULT)
    placement = xlb_s.placement;

  switch (placement)
  {
    case ADLB_PLACE_LOCAL:
      server = xlb_s.layout.my_server;
      break;
    case ADLB_PLACE_DEFAULT:
    case ADLB_PLACE_RANDOM:
    default:
      server = xlb_random_server();
      break;
  }

  return server;
}

adlb_code
ADLBP_Read_refcount_enable(void)
{
  xlb_s.read_refc_enabled = true;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Refcount_incr(adlb_datum_id id, adlb_refc change)
{
  adlb_code rc;

  adlb_notif_t notifs = ADLB_NO_NOTIFS;
  rc = xlb_refcount_incr(id, change, &notifs);
  ADLB_CHECK_MSG(rc, "failed to increment: ");

  rc = xlb_notify_all(&notifs);
  ADLB_CHECK(rc);

  xlb_free_notif(&notifs);

  return ADLB_SUCCESS;
}

adlb_code
xlb_refcount_incr(adlb_datum_id id, adlb_refc change,
                    adlb_notif_t *notifs)
{
  int rc;
  adlb_code ac;
  MPI_Status status;
  MPI_Request request;

  DEBUG("ADLB_Refcount_incr: "ADLB_PRID" READ %i WRITE %i",
            ADLB_PRID_ARGS(id, ADLB_DSYM_NULL),
            change.read_refcount, change.write_refcount);

  if (!xlb_s.read_refc_enabled)
    change.read_refcount = 0;

  if (ADLB_REFC_IS_NULL(change))
    return ADLB_SUCCESS;

  int to_server_rank = ADLB_Locate(id);

  struct packed_incr_resp resp;
  rc = MPI_Irecv(&resp, sizeof(resp), MPI_BYTE, to_server_rank,
                 ADLB_TAG_RESPONSE, xlb_s.comm, &request);
  MPI_CHECK(rc);
  struct packed_incr msg = { .id = id, .change = change };
  rc = MPI_Send(&msg, sizeof(msg), MPI_BYTE, to_server_rank,
                ADLB_TAG_REFCOUNT_INCR, xlb_s.comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);

  if (!resp.success)
    return ADLB_ERROR;

  ac = xlb_recv_notif_work(&resp.notifs, to_server_rank, notifs);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Insert_atomic(adlb_datum_id id, adlb_subscript subscript,
                       adlb_retrieve_refc refcounts,
                       bool *result, bool *value_present,
                       void *data, size_t *length, adlb_data_type *type)
{
  adlb_code ac;
  MPI_Status status;
  MPI_Request request;
  struct packed_insert_atomic_resp resp;

  DEBUG("ADLB_Insert_atomic: "ADLB_PRIDSUB,
        ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript));
  char *xfer_pos = xlb_xfer;
  xfer_pos += xlb_pack_id_sub(xfer_pos, id, subscript);

  bool return_value = data != NULL;
  MSG_PACK_BIN(xfer_pos, return_value);

  MSG_PACK_BIN(xfer_pos, refcounts);

  int to_server_rank = ADLB_Locate(id);

  int rc;
  rc = MPI_Irecv(&resp, sizeof(resp), MPI_BYTE, to_server_rank,
                ADLB_TAG_RESPONSE, xlb_s.comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(xlb_xfer, (int)(xfer_pos - xlb_xfer), MPI_BYTE,
                to_server_rank, ADLB_TAG_INSERT_ATOMIC, xlb_s.comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);

  if (resp.dc != ADLB_DATA_SUCCESS)
    return ADLB_ERROR;

  // Receive data before handling notifications
  if (return_value)
  {
    *length = resp.value_len;
    if (resp.value_present)
    {
      ac = mpi_recv_big(data, resp.value_len,
                        to_server_rank, ADLB_TAG_RESPONSE);
      ADLB_CHECK(ac);
      *type = resp.value_type;
    }
  }

  ac = xlb_handle_client_notif_work(&resp.notifs,
                                    to_server_rank);
  ADLB_CHECK(ac);

  *result = resp.created;
  *value_present = resp.value_present;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Retrieve(adlb_datum_id id, adlb_subscript subscript,
               adlb_retrieve_refc refcounts, adlb_data_type* type,
               void* data, size_t* length)
{
  double unused t0 = MPI_Wtime();
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(id);

  size_t subscript_len = adlb_has_sub(subscript) ?
                          subscript.length : 0;

  // Stack allocate small buffer
  size_t hdr_len = sizeof(struct packed_retrieve_hdr) + subscript_len;
  char hdr_buffer[hdr_len];
  struct packed_retrieve_hdr *hdr;
  hdr = (struct packed_retrieve_hdr*)hdr_buffer;

  // Fill in header
  hdr->id = id;
  hdr->refcounts = refcounts;
  hdr->subscript_len = subscript_len;
  if (subscript_len > 0)
  {
    memcpy(hdr->subscript, subscript.key, subscript_len);
  }

  struct retrieve_response_hdr resp_hdr;
  IRECV(&resp_hdr, sizeof(resp_hdr), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);
  SEND(hdr, (int)hdr_len, MPI_BYTE, to_server_rank, ADLB_TAG_RETRIEVE);
  WAIT(&request,&status);

  if (resp_hdr.code == ADLB_DATA_ERROR_NOT_FOUND ||
      resp_hdr.code == ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND)
  {
    return ADLB_NOTHING;
  }
  else if (resp_hdr.code != ADLB_DATA_SUCCESS)
  {
    return ADLB_ERROR;
  }

  assert(resp_hdr.length <= ADLB_PAYLOAD_MAX);
  mpi_recv_big(data, resp_hdr.length, to_server_rank,
               ADLB_TAG_RESPONSE);
  // Set length and type output parameters
  *length = resp_hdr.length;
  *type = resp_hdr.type;

  adlb_code ac = xlb_handle_client_notif_work(&resp_hdr.notifs,
                                              to_server_rank);
  ADLB_CHECK(ac);

  double unused t1 = MPI_Wtime();
  DEBUG("ADLB_Retrieve: rank=%i svr=%i id=%"PRId64" %8.5f",
        xlb_s.layout.rank, to_server_rank, id, t1-t0);

  return ADLB_SUCCESS;
}

/**
   Allocates fresh memory in subscripts and members
   Caller must free this when done
 */
adlb_code
ADLBP_Enumerate(adlb_datum_id container_id,
                int count, int offset, adlb_refc decr,
                bool include_keys, bool include_vals,
                void** data, size_t* length, int* records,
                adlb_type_extra *kv_type)
{
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(container_id);

  struct packed_enumerate opts;
  opts.id = container_id;
  // Are we requesting subscripts?
  opts.request_subscripts = include_keys;
  // Are we requesting members?
  opts.request_members = include_vals;
  opts.count = count;
  opts.offset = offset;
  opts.decr = decr;

  struct packed_enumerate_result res;
  IRECV(&res, sizeof(res), MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&opts, sizeof(struct packed_enumerate), MPI_BYTE,
       to_server_rank, ADLB_TAG_ENUMERATE);
  WAIT(&request,&status);

  if (res.dc == ADLB_DATA_SUCCESS)
  {
    *records = res.records;
    *length = res.length;
    if (include_keys || include_vals)
    {
      *data = malloc(res.length);
      ADLB_CHECK_MALLOC(*data);

      adlb_code ac = mpi_recv_big(*data, res.length,
                                  to_server_rank, ADLB_TAG_RESPONSE);
      ADLB_CHECK(ac);
    }
    kv_type->valid = true;
    kv_type->CONTAINER.key_type = res.key_type;
    kv_type->CONTAINER.val_type = res.val_type;
    return ADLB_SUCCESS;
  }
  else
    return ADLB_ERROR;
}

adlb_code
ADLBP_Unique(adlb_datum_id* result)
{
  MPI_Status status;
  MPI_Request request;

  TRACE("ADLBP_Unique()...");

  // This is just something to send, it is ignored by the server
  static int msg = 0;
  int to_server_rank = get_next_server();
  IRECV(result, 1, MPI_ADLB_ID, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&msg, 1, MPI_INT, to_server_rank, ADLB_TAG_UNIQUE);
  WAIT(&request, &status);

  if (result == ADLB_DATA_ID_NULL)
    return ADLB_ERROR;
  return ADLB_SUCCESS;
}

adlb_code ADLBP_Alloc_global(int count, adlb_datum_id *start)
{
  DEBUG("Alloc_global: rank: %i count: %i", xlb_s.layout.rank, count);
  assert(start != NULL);

  adlb_data_code dc;

  dc = xlb_data_system_reserve(count, start);
  ADLB_DATA_CHECK(dc);

  adlb_datum_id end = *start + count - 1;

  /*
    Local check on servers to make sure not in use.
    Note: checking all IDs is inefficient for large ranges since most
          aren't on this server, but that is not a typical use-case for
          this function.
   */
  bool error = false;
  if (xlb_s.layout.am_server)
  {
    for (adlb_datum_id id = *start; id <= end; id++)
    {
      if (ADLB_Locate(id) == xlb_s.layout.rank)
      {
        bool exists;
        dc = xlb_data_exists(id, ADLB_NO_SUB, &exists);
        ADLB_DATA_CHECK(dc);

        if (exists)
        {
          ERR_PRINTF("ID " ADLB_PRID " was already allocated",
                ADLB_PRID_ARGS(id, ADLB_DSYM_NULL));
          error = true;
        }
      }
    }
  }

  // Collectively wait until all work has been finished
  // Note: we don't validate that all ranks called with the same arguments
  BARRIER();

  if (error)
  {
    return ADLB_ERROR;
  }

  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Typeof(adlb_datum_id id, adlb_data_type* type)
{
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(id);
  int t;
  IRECV(&t, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_ADLB_ID, to_server_rank, ADLB_TAG_TYPEOF);
  WAIT(&request, &status);

  DEBUG("ADLB_Typeof "ADLB_PRID" => %i",
        ADLB_PRID_ARGS(id, ADLB_DSYM_NULL), t);

  if (t == -1)
    return ADLB_ERROR;
  adlb_data_type recvd = (adlb_data_type) t;
  *type = recvd;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Container_typeof(adlb_datum_id id, adlb_data_type* key_type,
                                         adlb_data_type* val_type)
{
  MPI_Status status;
  MPI_Request request;
  // DEBUG("ADLB_Container_typeof: %li", id);

  int to_server_rank = ADLB_Locate(id);

  int t[2];
  IRECV(t, 2, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&id, 1, MPI_ADLB_ID, to_server_rank, ADLB_TAG_CONTAINER_TYPEOF);
  WAIT(&request, &status);

  DEBUG("ADLB_Container_typeof "ADLB_PRID" => (%i,%i)",
        ADLB_PRID_ARGS(id, ADLB_DSYM_NULL), t[0], t[1]);

  if (t[0] == -1 || t[1] == -1)
    return ADLB_ERROR;

  adlb_data_type recvd;
  recvd = (adlb_data_type) t[0];
  *key_type = recvd;
  recvd = (adlb_data_type) t[1];
  *val_type = recvd;
  return ADLB_SUCCESS;
}

/**
   @param work_type work type to receive notification as
   @param subscribed output: false if data is already closed
                             or ADLB_ERROR on error
 */
adlb_code
ADLBP_Subscribe(adlb_datum_id id, adlb_subscript subscript,
                int work_type, int* subscribed)
{
  int to_server_rank;
  MPI_Status status;
  MPI_Request request;

  to_server_rank = ADLB_Locate(id);

  char *xfer_pos = xlb_xfer;
  MSG_PACK_BIN(xfer_pos, work_type);
  xfer_pos += xlb_pack_id_sub(xfer_pos, id, subscript);

  int req_length = (int)(xfer_pos - xlb_xfer);
  assert(req_length > 0);

  struct pack_sub_resp result;
  IRECV(&result, sizeof(result), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);
  SEND(xlb_xfer, req_length, MPI_BYTE, to_server_rank,
        ADLB_TAG_SUBSCRIBE);
  WAIT(&request, &status);

  if (result.dc == ADLB_DATA_SUCCESS)
  {
    *subscribed = result.subscribed;
    if (!adlb_has_sub(subscript))
    {
      DEBUG("ADLB_Subscribe: "ADLB_PRID" => %i",
            ADLB_PRID_ARGS(id, ADLB_DSYM_NULL), *subscribed);
    }
    else
    {
      DEBUG("ADLB_Subscribe: "ADLB_PRIDSUB" => %i",
          ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript), *subscribed);
    }
    return ADLB_SUCCESS;
  }
  else if (result.dc == ADLB_DATA_ERROR_NOT_FOUND)
  {
    DEBUG("ADLB_Subscribe: "ADLB_PRID" not found",
           ADLB_PRID_ARGS(id, ADLB_DSYM_NULL));
    return ADLB_NOTHING;
  }
  else
  {
    if (!adlb_has_sub(subscript))
    {
      DEBUG("ADLB_Subscribe: "ADLB_PRID" => error",
            ADLB_PRID_ARGS(id, ADLB_DSYM_NULL));
    }
    else
    {
      DEBUG("ADLB_Subscribe: "ADLB_PRIDSUB" => error",
            ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript));
    }
    return ADLB_ERROR;
  }
}

/**
   This consumes a read reference count to the container
   @return false in subscribed if data is already closed
 */
adlb_code
ADLBP_Container_reference(adlb_datum_id id, adlb_subscript subscript,
            adlb_datum_id ref_id, adlb_subscript ref_subscript,
            adlb_data_type ref_type, adlb_refc transfer_refs,
            int ref_write_decr)
{
  MPI_Status status;
  MPI_Request request;

  char *xfer_pos = xlb_xfer;

  MSG_PACK_BIN(xfer_pos, ref_type);
  xfer_pos += xlb_pack_id_sub(xfer_pos, id, subscript);
  xfer_pos += xlb_pack_id_sub(xfer_pos, ref_id, ref_subscript);
  MSG_PACK_BIN(xfer_pos, transfer_refs);
  MSG_PACK_BIN(xfer_pos, ref_write_decr);

  int to_server_rank = ADLB_Locate(id);

  struct packed_cont_ref_resp resp;

  IRECV(&resp, sizeof(resp), MPI_BYTE, to_server_rank,
        ADLB_TAG_RESPONSE);

  assert(xfer_pos - xlb_xfer <= INT_MAX);
  int length = (int)(xfer_pos - xlb_xfer);

  SEND(xlb_xfer, length, MPI_CHAR, to_server_rank,
       ADLB_TAG_CONTAINER_REFERENCE);
  WAIT(&request, &status);

  // Check for error before processing notification
  ADLB_DATA_CHECK(resp.dc);

  adlb_code ac;
  ac = xlb_handle_client_notif_work(&resp.notifs, to_server_rank);
  ADLB_CHECK(ac);

  DEBUG("ADLB_Container_reference: "ADLB_PRIDSUB" => "ADLB_PRIDSUB
    " (%i)", ADLB_PRIDSUB_ARGS(id, ADLB_DSYM_NULL, subscript),
    ADLB_PRIDSUB_ARGS(ref_id, ADLB_DSYM_NULL, ref_subscript), ref_type);

  return ADLB_SUCCESS;
}

/** Return ADLB_ERROR and size=-1 if container is not closed */
adlb_code
ADLBP_Container_size(adlb_datum_id container_id, int* size,
                     adlb_refc decr)
{
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = ADLB_Locate(container_id);

  struct packed_size_req req = { .id = container_id, .decr = decr };
  IRECV(size, 1, MPI_INT, to_server_rank, ADLB_TAG_RESPONSE);
  SEND(&req, sizeof(req), MPI_BYTE, to_server_rank,
                ADLB_TAG_CONTAINER_SIZE);
  WAIT(&request, &status);

  DEBUG("ADLB_Container_size: "ADLB_PRID" => %i",
        ADLB_PRID_ARGS(container_id, ADLB_DSYM_NULL), *size);

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
  SEND(&id, 1, MPI_ADLB_ID, to_server_rank, ADLB_TAG_LOCK);
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
  SEND(&id, 1, MPI_ADLB_ID, to_server_rank, ADLB_TAG_UNLOCK);
  WAIT(&request, &status);

  if (c == 'x')
    return ADLB_ERROR;

  if (c != '1')
    assert(false);

  return ADLB_SUCCESS;
}

/**
   Is the server at rank idle?

   check_attempt: attempt number from master server of checking for idle
   result: true if idle
   request_counts: must be array large enough to hold ntypes. Filled in
        if idle with # of requests for each type
   untargeted_work_counts: must be array large enough to hold ntypes,
        Filled in if idle with # of tasks for each type
 */
adlb_code
ADLB_Server_idle(int rank, int64_t check_attempt, bool* result,
                 int *request_counts, int *untargeted_work_counts)
{
  MPI_Request request;
  MPI_Status status;
  IRECV(result, sizeof(result), MPI_BYTE, rank, ADLB_TAG_RESPONSE);
  SEND(&check_attempt, sizeof(check_attempt), MPI_BYTE, rank,
       ADLB_TAG_CHECK_IDLE);
  WAIT(&request, &status);

  if (*result)
  {
    RECV(request_counts, xlb_s.types_size, MPI_INT, rank,
         ADLB_TAG_RESPONSE);
    RECV(untargeted_work_counts, xlb_s.types_size, MPI_INT, rank,
         ADLB_TAG_RESPONSE);
  }
  return ADLB_SUCCESS;
}

/**
   Tell the server that this worker is shutting down
 */
static inline adlb_code
ADLB_Shutdown(void)
{
  if (xlb_s.status == ADLB_STATUS_SHUTDOWN)
    // Already got a SHUTDOWN message
    return ADLB_SUCCESS;

  // This worker is shutting itself down - notify its server
  TRACE_START;
  SEND_TAG(xlb_s.layout.my_server, ADLB_TAG_SHUTDOWN_WORKER);
  TRACE_END;
  return ADLB_SUCCESS;
}

adlb_code
ADLBP_Finalize()
{
  TRACE_START;
  adlb_code rc;
  int flag;
  MPI_Finalized(&flag);
  ADLB_CHECK_MSG(!flag,
                 "ERROR: MPI_Finalize() called before ADLB_Finalize()\n");

#ifdef XLB_ENABLE_XPT
  // Finalize checkpoints before shutting down data
  ADLB_Xpt_finalize();
#endif

  adlb_data_code dc = xlb_data_finalize();
  if (dc != ADLB_DATA_SUCCESS)
    xlb_server_fail(1);

  if (xlb_s.layout.rank >= xlb_s.layout.master_server_rank)
  {
    // Server:
    ; // print_final_stats();
  }
  else
  {
    // Worker:
    rc = ADLB_Shutdown();
    ADLB_CHECK(rc);
  }

  if (xlb_s.hostmap != NULL)
    xlb_hostmap_free(xlb_s.hostmap);

  xlb_dsyms_finalize();

  bool failed;
  int fail_code;
  xlb_server_failed(&failed, &fail_code);
  if (xlb_s.layout.rank == xlb_s.layout.master_server_rank && failed)
  {
    printf("FAILED: EXIT(%i)\n", fail_code);
    exit(fail_code);
  }

  rc = xlb_get_reqs_finalize();
  ADLB_CHECK(rc);

  // Get messaging module to clean up state
  xlb_msg_finalize();

  // Clean up communicators (avoid memory leaks if ADLB used within
  // another application, and avoid spurious warnings from leak
  // detectors otherwise)
  if (xlb_s.leader_comm != MPI_COMM_NULL)
    MPI_Comm_free(&xlb_s.leader_comm);
  if (xlb_s.server_comm != MPI_COMM_NULL)
    MPI_Comm_free(&xlb_s.server_comm);
  if (xlb_s.worker_comm != MPI_COMM_NULL)
    MPI_Comm_free(&xlb_s.worker_comm);
  MPI_Group_free(&adlb_group);
  free(xlb_s.my_name);

  xlb_data_types_finalize();

  xlb_layout_finalize(&xlb_s.layout);

  return ADLB_SUCCESS;
}

adlb_code
ADLB_Fail(int code)
{
  printf("ADLB_Fail(%i)\n", code);

  SEND(&code, 1, MPI_INT, xlb_s.layout.master_server_rank, ADLB_TAG_FAIL);

  // give servers a chance to shut down
  sleep(1);

  return ADLB_SUCCESS;
}

void
ADLB_Abort(int code)
{
  printf("ADLB: ADLB_Abort(%i) calling MPI_Abort(%i)\n", code, code);
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

void
adlb_exit_handler()
{
  printf("adlb_exit_handler:\n");
  print_proc_self_status();
}
