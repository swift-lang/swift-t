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

/**
 * Tcl extension for ADLB
 *
 * @author wozniak
 * */

// This file should do some user logging using the c-utils
// logging library - this is because the ADLB C layer cannot
// do that effectively, and these functions are called
// directly as Tcl extension functions

// This file should not do DEBUG logging for data operations
// except for during development of this file - the Turbine and ADLB
// messages are more useful.  This file only packs and unpacks
// calls to the ADLB C layer

#include "config.h"

#include <assert.h>

// strnlen() is a GNU extension: Need _GNU_SOURCE
#define _GNU_SOURCE
#if ENABLE_BGP == 1
// Also need __USE_GNU on the BG/P and on older GCC (4.1, 4.3)
#define __USE_GNU
#endif
#include <string.h>
#include <exm-string.h>

#include <tcl.h>
#include <mpi.h>
#include <adlb.h>

#include <log.h>

#include <memory.h>
#include <table_lp.h>
#include <tools.h>

#include "src/tcl/util.h"
#include "src/util/debug.h"

#include "tcl-adlb.h"

// Auto-detect: Old ADLB or new XLB
#ifdef XLB
#define USE_XLB
#else
#define USE_ADLB
#endif

static int adlb_rank;
/** Number of workers */
static int workers;
/** Number of servers */
static int servers;

static int am_server;

#ifdef USE_ADLB
static int am_debug_server;
#endif

/** If false, intercept and disable all read refcount operations */
static bool read_refcount_enabled = false;

/** Size of MPI_COMM_WORLD */
static int mpi_size = -1;

/** Rank in MPI_COMM_WORLD */
static int mpi_rank = -1;

/** Communicator for ADLB workers */
static MPI_Comm worker_comm;

/** If the controlling code passed us a communicator, it is here */
long adlb_comm_ptr = 0;

static char xfer[ADLB_DATA_MAX];

/**
   Map from TD to local blob pointers.
   This is not an LRU cache: the user must use blob_free to
   free memory
 */
static struct table_lp blob_cache;

static void set_namespace_constants(Tcl_Interp* interp);

static Tcl_Obj* TclListFromArray(Tcl_Interp *interp, int *vals, int count);

static int
ADLB_Retrieve_Impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[], bool decr);
/**
   usage: adlb::init <servers> <types> [<comm>]?
   Simplified use of ADLB_Init type_vect: just give adlb_init
   a number ntypes, and the valid types will be: [0..ntypes-1]
   If comm is given, run ADLB in that communicator
   Else, run ADLB in a dup of MPI_COMM_WORLD
 */
static int
ADLB_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 3 || objc == 4,
                "adlb::init requires 2 or 3 arguments!");

  mm_init();
  turbine_debug_init();

  int rc;

  int servers;
  rc = Tcl_GetIntFromObj(interp, objv[1], &servers);
  TCL_CHECK(rc);

  int ntypes;
  rc = Tcl_GetIntFromObj(interp, objv[2], &ntypes);
  TCL_CHECK(rc);

  int type_vect[ntypes];
  for (int i = 0; i < ntypes; i++)
    type_vect[i] = i;

  table_lp_init(&blob_cache, 16);

  MPI_Comm adlb_comm;

  if (objc == 3)
  {
    // Start with MPI_Init() and MPI_COMM_WORLD
    int argc = 0;
    char** argv = NULL;
    rc = MPI_Init(&argc, &argv);
    assert(rc == MPI_SUCCESS);
    MPI_Comm_dup(MPI_COMM_WORLD, &adlb_comm);
  }
  else if (objc == 4)
  {
    rc = Tcl_GetLongFromObj(interp, objv[3], &adlb_comm_ptr);
    TCL_CHECK(rc);
    memcpy(&adlb_comm, (void*) adlb_comm_ptr, sizeof(MPI_Comm));
  }
  else
    assert(false);

  MPI_Comm_size(adlb_comm, &mpi_size);
  workers = mpi_size - servers;
  MPI_Comm_rank(adlb_comm, &mpi_rank);

  if (mpi_rank == 0)
  {
    if (workers <= 0)
      puts("WARNING: No workers");
    // Other configuration information will go here...
  }

  // ADLB_Init(int num_servers, int use_debug_server,
  //           int aprintf_flag, int num_types, int *types,
  //           int *am_server, int *am_debug_server, MPI_Comm *app_comm)
#ifdef USE_ADLB
  rc = ADLB_Init(servers, 0, 0, ntypes, type_vect,
                   &am_server, &am_debug_server, &worker_comm);
#endif
#ifdef USE_XLB
  rc = ADLB_Init(servers, ntypes, type_vect,
                 &am_server, adlb_comm, &worker_comm);
#endif
  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;

  if (! am_server)
    MPI_Comm_rank(worker_comm, &adlb_rank);

  set_namespace_constants(interp);

  Tcl_SetObjResult(interp, Tcl_NewIntObj(ADLB_SUCCESS));
  return TCL_OK;
}

static void
set_namespace_constants(Tcl_Interp* interp)
{
  tcl_set_integer(interp, "::adlb::SUCCESS",   ADLB_SUCCESS);
  tcl_set_integer(interp, "::adlb::RANK_ANY",  ADLB_RANK_ANY);
  tcl_set_integer(interp, "::adlb::INTEGER",   ADLB_DATA_TYPE_INTEGER);
  tcl_set_integer(interp, "::adlb::FLOAT",     ADLB_DATA_TYPE_FLOAT);
  tcl_set_integer(interp, "::adlb::STRING",    ADLB_DATA_TYPE_STRING);
  tcl_set_integer(interp, "::adlb::BLOB",      ADLB_DATA_TYPE_BLOB);
  tcl_set_long(interp,    "::adlb::NULL_ID",   ADLB_DATA_ID_NULL);
  tcl_set_integer(interp, "::adlb::CONTAINER", ADLB_DATA_TYPE_CONTAINER);
  tcl_set_integer(interp, "::adlb::READ_REFCOUNT", ADLB_READ_REFCOUNT);
  tcl_set_integer(interp, "::adlb::WRITE_REFCOUNT", ADLB_WRITE_REFCOUNT);
  tcl_set_integer(interp, "::adlb::READWRITE_REFCOUNT", ADLB_READWRITE_REFCOUNT);
}

/**
   Enter server
 */
static int
ADLB_Server_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  if (!am_server)
  {
    printf("adlb::server: This process is not a server!\n");
    return TCL_ERROR;
  }

  DEBUG_ADLB("ADLB SERVER...");
  // Limit ADLB to 100MB
  int max_memory = 100*1024*1024;
#ifdef USE_ADLB
  double logging = 0.0;
  int rc = ADLB_Server(max_memory, logging);
#endif
#ifdef USE_XLB
  int rc = ADLB_Server(max_memory);
#endif

  TCL_CONDITION(rc == ADLB_SUCCESS, "SERVER FAILED");

  return TCL_OK;
}

/**
   usage: no args, returns MPI rank
*/
static int
ADLB_Rank_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(mpi_rank));
  return TCL_OK;
}

/**
   usage: no args, returns true if a server, else false
*/
static int
ADLB_AmServer_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewBooleanObj(am_server));
  return TCL_OK;
}

/**
   usage: no args, returns number of MPI world ranks
*/
static int
ADLB_Size_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(mpi_size));
  return TCL_OK;
}

/**
   usage: no args, returns number of servers
*/
static int
ADLB_Servers_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(servers));
  return TCL_OK;
}

/**
   usage: no args, returns number of servers
*/
static int
ADLB_Workers_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(workers));
  return TCL_OK;
}

/**
   usage: no args, barrier for workers
*/
static int
ADLB_Barrier_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  int rc = MPI_Barrier(MPI_COMM_WORLD);
  ASSERT(rc == MPI_SUCCESS);
  return TCL_OK;
}

static int
ADLB_Hostmap_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  // This is limited only by the number of ranks a user could
  // conceivably put on a node- getting bigger
  int count = 512;
  int ranks[count];
  int actual;

  char* name = Tcl_GetString(objv[1]);

  printf("ADLB_Hostmap_Cmd: %s\n", name);

  adlb_code rc = ADLB_Hostmap(name, count, ranks, &actual);
  printf("rc: %i\n", rc);
  TCL_CONDITION(rc == ADLB_SUCCESS || rc == ADLB_NOTHING,
                "error in hostmap!");

  printf("actual: %i\n", actual);

  Tcl_Obj* items[actual];
  for (int i = 0; i < actual; i++)
    items[i] = Tcl_NewIntObj(ranks[i]);

  Tcl_Obj* result = Tcl_NewListObj(actual, items);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::put <reserve_rank> <work type> <work unit>
*/
static int
ADLB_Put_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);

  int reserve_rank;
  int work_type;
  int priority;
  Tcl_GetIntFromObj(interp, objv[1], &reserve_rank);
  Tcl_GetIntFromObj(interp, objv[2], &work_type);
  char* cmd = Tcl_GetString(objv[3]);
  Tcl_GetIntFromObj(interp, objv[4], &priority);

  DEBUG_ADLB("adlb::put: reserve_rank: %i type: %i \"%s\" %i",
             reserve_rank, work_type, cmd, priority);

  // int ADLB_Put(void *work_buf, int work_len, int reserve_rank,
  //              int answer_rank, int work_type, int work_prio)
  int rc = ADLB_Put(cmd, strlen(cmd)+1, reserve_rank, adlb_rank,
                    work_type, priority, 1);

  ASSERT(rc == ADLB_SUCCESS);
  return TCL_OK;
}

/**
   usage: adlb::get <req_type> <answer_rank>
   Returns the next work unit of req_type or empty string when
   ADLB is done
   Stores answer_rank in given output variable
 */
static int
ADLB_Get_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  int req_type;
  int error = Tcl_GetIntFromObj(interp, objv[1], &req_type);
  TCL_CHECK(error);
  Tcl_Obj* tcl_answer_rank_name = objv[2];

  DEBUG_ADLB("adlb::get: type=%i", req_type);

  int work_type;

  char* result = &xfer[0];
#ifdef USE_ADLB
  int work_handle[ADLB_HANDLE_SIZE];
#endif
  int work_len;
  int answer_rank;
  bool found_work = false;
  int rc;

#ifdef USE_ADLB

  int req_types[4];
  int work_prio;

  req_types[0] = req_type;
  req_types[1] = req_types[2] = req_types[3] = -1;

  DEBUG_ADLB("enter reserve: type=%i", req_types[0]);
  rc = ADLB_Reserve(req_types, &work_type, &work_prio,
                    work_handle, &work_len, &answer_rank);
  DEBUG_ADLB("exit reserve");
  if (rc == ADLB_DONE_BY_EXHAUSTION)
  {
    DEBUG_ADLB("ADLB_DONE_BY_EXHAUSTION!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_MORE_WORK ) {
    DEBUG_ADLB("ADLB_NO_MORE_WORK!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_CURRENT_WORK) {
    DEBUG_ADLB("ADLB_NO_CURRENT_WORK");
    result[0] = '\0';
  }
  else if (rc < 0) {
    DEBUG_ADLB("rc < 0");
    result[0] = '\0';
  }
  else
  {
    DEBUG_ADLB("work is reserved.");
    rc = ADLB_Get_reserved(result, work_handle);
    if (rc == ADLB_NO_MORE_WORK)
    {
      puts("No more work on Get_reserved()!");
      result[0] = '\0';
    }
    else
      found_work = true;
  }
  if (result[0] == '\0')
    answer_rank = -1;
#endif

#ifdef USE_XLB
  MPI_Comm task_comm;
  rc = ADLB_Get(req_type, result, &work_len,
                &answer_rank, &work_type, &task_comm);
  if (rc == ADLB_SHUTDOWN)
  {
    result[0] = '\0';
    answer_rank = ADLB_RANK_NULL;
  }
#endif

  if (found_work)
    DEBUG_ADLB("adlb::get: %s", (char*) result);

  // Store answer_rank in caller's stack frame
  Tcl_Obj* tcl_answer_rank = Tcl_NewIntObj(answer_rank);
  Tcl_ObjSetVar2(interp, tcl_answer_rank_name, NULL, tcl_answer_rank,
                 EMPTY_FLAG);

  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));
  return TCL_OK;
}

/**
   usage: adlb::iget <req_type> <answer_rank>
   Returns the next work unit of req_type or
        "ADLB_SHUTDOWN" or "ADLB_NOTHING"
   Stores answer_rank in given output variable
 */
static int
ADLB_Iget_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  int req_type;
  int error = Tcl_GetIntFromObj(interp, objv[1], &req_type);
  TCL_CHECK(error);
  Tcl_Obj* tcl_answer_rank_name = objv[2];

  DEBUG_ADLB("adlb::get: type=%i", req_type);

  int work_type;

  char* result = &xfer[0];
  int work_len;
  int answer_rank;

  adlb_code rc = ADLB_Iget(req_type, result, &work_len,
                           &answer_rank, &work_type);
  if (rc == ADLB_SHUTDOWN)
  {
    strcpy(result, "ADLB_SHUTDOWN");
    answer_rank = ADLB_RANK_NULL;
  }
  else if (rc == ADLB_NOTHING)
  {
    strcpy(result, "ADLB_NOTHING");
    answer_rank = ADLB_RANK_NULL;
  }

  DEBUG_ADLB("adlb::iget: %s", result);

  // Store answer_rank in caller's stack frame
  Tcl_Obj* tcl_answer_rank = Tcl_NewIntObj(answer_rank);
  Tcl_ObjSetVar2(interp, tcl_answer_rank_name, NULL, tcl_answer_rank,
                 EMPTY_FLAG);

  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));
  return TCL_OK;
}

/**
   Convert type string to adlb_data_type
 */
static inline
adlb_data_type type_from_string(const char* type_string)
{
  adlb_data_type result;
  if (strcmp(type_string, "integer") == 0)
    result = ADLB_DATA_TYPE_INTEGER;
  else if (strcmp(type_string, "float") == 0)
    result = ADLB_DATA_TYPE_FLOAT;
  else if (strcmp(type_string, "string") == 0)
    result = ADLB_DATA_TYPE_STRING;
  else if (strcmp(type_string, "blob") == 0)
    result = ADLB_DATA_TYPE_BLOB;
  else if (strcmp(type_string, "container") == 0)
    result = ADLB_DATA_TYPE_CONTAINER;
  else
    result = ADLB_DATA_TYPE_NULL;
  return result;
}

/**
   usage: adlb::create <id> <type> [<extra for type>]
          [ <read_refcount> [ <write_refcount> [ <permanent> ] ] ]
   if <id> is adlb::NULL_ID, returns a newly created id
   @param extra is only used for files and containers
*/
static int
ADLB_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 3, "adlb::create requires >= 2 args!");
  int rc;
  long id;
  int argpos = 1;
  rc = Tcl_GetLongFromObj(interp, objv[argpos++], &id);
  TCL_CHECK_MSG(rc, "adlb::create could not get data id");

  int type;
  rc = Tcl_GetIntFromObj(interp, objv[argpos++], &type);
  TCL_CHECK_MSG(rc, "adlb::create could not get data type");
 
 // Process type-specific params
  char* subscript_type_string = NULL;
  switch (type)
  {
    case ADLB_DATA_TYPE_CONTAINER:
      TCL_CONDITION(objc > argpos,
                    "adlb::create type=container requires "
                    "subscript type!");
      subscript_type_string = Tcl_GetString(objv[argpos++]);
      break;
  }

  // Process create props if present
  adlb_create_props props = DEFAULT_CREATE_PROPS;
 
  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &(props.read_refcount));
    TCL_CHECK_MSG(rc, "adlb::create could not get read_refcount argument");
  }

  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &(props.write_refcount));
    TCL_CHECK_MSG(rc, "adlb::create could not get write_refcount argument");
  }
  
  if (argpos < objc) {
    int permanent;
    rc = Tcl_GetBooleanFromObj(interp, objv[argpos++], &permanent);
    TCL_CHECK_MSG(rc, "adlb::create could not get permanent argument");
    props.permanent = permanent != 0;
  }


  long new_id = ADLB_DATA_ID_NULL;

  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      rc = ADLB_Create_integer(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      rc = ADLB_Create_float(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_STRING:
      rc = ADLB_Create_string(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_BLOB:
      rc = ADLB_Create_blob(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_CONTAINER: {
      adlb_data_type subscript_type = type_from_string(subscript_type_string);
      rc = ADLB_Create_container(id, subscript_type, props, &new_id);
      break;
    }
    case ADLB_DATA_TYPE_NULL:
      Tcl_AddErrorInfo(interp,
                       "adlb::create: unknown type!");
      return TCL_ERROR;
      break;
  }

  if (id == ADLB_DATA_ID_NULL) {
    // need to return new ID
    Tcl_Obj* result = Tcl_NewLongObj(new_id);
    Tcl_SetObjResult(interp, result);
  }

  TCL_CONDITION(rc == ADLB_SUCCESS, "adlb::create <%li> failed!", id);
  return TCL_OK;
}

/**
   usage: adlb::exists <id>
 */
static int
ADLB_Exists_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long id;
  bool b;
  int rc;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "adlb::exists requires a data ID");
  rc = ADLB_Exists(id, &b);
  TCL_CONDITION(rc == ADLB_SUCCESS, "adlb::exists <%li> failed!", id);
  Tcl_Obj* result = Tcl_NewBooleanObj(b);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
 * Create a tcl list from an array.
 * Free vals if count > 0
 */
static Tcl_Obj* TclListFromArray(Tcl_Interp *interp, int *vals, int count) {
  Tcl_Obj* result = Tcl_NewListObj(0, NULL);
  if (count > 0)
  {
    for (int i = 0; i < count; i++)
    {
      Tcl_Obj* o = Tcl_NewIntObj(vals[i]);
      Tcl_ListObjAppendElement(interp, result, o);
    }
    free(vals);
  }
  return result;
}

/**
   usage: adlb::store <id> <type> <value> [ <decrement> ]
   @param value Ignored for types file, container
   If given a blob, the value must be a string
   This allows users to store a string in a blob
   @param decrement Optional  If true, decrement the writers reference
                count by 1.  Default if not provided is true
   returns list of int ranks that must be notified
*/
static int
ADLB_Store_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 4 || objc == 5,
                "adlb::store requires 4 or 5 args!");

  long id;
  int length = 0;
  Tcl_GetLongFromObj(interp, objv[1], &id);
  int type;
  Tcl_GetIntFromObj(interp, objv[2], &type);
  int rc;

  double tmp_double;

  void* data = &xfer[0];
  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      rc = Tcl_GetLongFromObj(interp, objv[3], (long*) xfer);
      TCL_CHECK_MSG(rc, "adlb::store long <%li> failed!", id);
      length = sizeof(long);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      rc = Tcl_GetDoubleFromObj(interp, objv[3], &tmp_double);
      TCL_CHECK_MSG(rc, "adlb::store double <%li> failed!", id);
      memcpy(xfer, &tmp_double, sizeof(double));
      length = sizeof(double);
      break;
    case ADLB_DATA_TYPE_STRING:
      data = Tcl_GetStringFromObj(objv[3], &length);
      TCL_CONDITION(data != NULL,
                    "adlb::store string <%li> failed!", id);
      length = strlen(data)+1;
      TCL_CONDITION(length < ADLB_DATA_MAX,
          "adlb::store: string too long: <%li>", id);
      break;
    case ADLB_DATA_TYPE_BLOB:
      // User is storing a Tcl string in a blob
      data = Tcl_GetStringFromObj(objv[3], &length);
      TCL_CONDITION(data != NULL,
                    "adlb::store blob <%li> failed!", id);
      length = strlen(data)+1;
      TCL_CONDITION(length < ADLB_DATA_MAX,
                    "adlb::store: string too long: <%li>", id);
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      // Ignore objv[3]
      break;
    default:
      printf("adlb::store unknown type!\n");
      rc = TCL_ERROR;
      break;
  }

  // Handle optional decr_write_refcount
  bool decr = true;
  if (objc == 5) {
    int decr_int;
    rc = Tcl_GetIntFromObj(interp, objv[4], &decr_int);
    TCL_CHECK_MSG(rc, "adlb::store decrement arg must be int!");
    decr = decr_int != 0;
  }

  // DEBUG_ADLB("adlb::store: <%li>=%s", id, data);
  int *notify_ranks;
  int notify_count;
  rc = ADLB_Store(id, data, length, decr, &notify_ranks, &notify_count);

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::store <%li> failed!", id);

  Tcl_Obj* result = TclListFromArray(interp, notify_ranks, notify_count);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static inline void report_type_mismatch(adlb_data_type expected,
                                        adlb_data_type actual);

static inline int retrieve_object(Tcl_Interp *interp,
                                  Tcl_Obj *const objv[],
                                  long id, adlb_data_type type,
                                  int length, Tcl_Obj** result);

/**
   usage: adlb::retrieve <id> [<type>]
   If id is a blob, we try to return it as a string.
   This is an error if the blob is not NULL-terminated
*/
static int
ADLB_Retrieve_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Impl(cdata, interp, objc, objv, false);
}

/**
   usage: adlb::retrieve_decr <id> <decr> [<type>]
   same as retrieve, but also decrement read reference count by <decr>
*/
static int
ADLB_Retrieve_Decr_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Impl(cdata, interp, objc, objv, true);
}

static int
ADLB_Retrieve_Impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[], bool decr)
{
  if (decr) {
    TCL_CONDITION((objc == 3 || objc == 4),
                  "requires 2 or 3 args!");
  } else {
    TCL_CONDITION((objc == 2 || objc == 3),
                  "requires 1 or 2 args!");

  }

  int rc;
  long id;
  int argpos = 1;
  rc = Tcl_GetLongFromObj(interp, objv[argpos++], &id);
  TCL_CHECK_MSG(rc, "requires id!");
 
  
  int decr_amount = 0;
  if (decr) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr_amount);
    TCL_CHECK_MSG(rc, "requires decr amount!");
    /* Only decrement if refcounting enabled */
    if (!read_refcount_enabled) {
      // disable if needed
      decr_amount = 0;
    }
  }

  adlb_data_type given_type = ADLB_DATA_TYPE_NULL;
  if (argpos < objc)
  {
    int tmp;
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &tmp);
    TCL_CHECK_MSG(rc, "arg %i must be adlb:: type!", argpos);
    given_type = tmp;
  }

  // Retrieve the data, actual type, and length from server
  adlb_data_type type;
  int length;
  rc = ADLB_Retrieve(id, &type, decr_amount, xfer, &length);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%li> failed!", id);

  // Type check
  if ((given_type != ADLB_DATA_TYPE_NULL &&
       given_type != type))
  {
    report_type_mismatch(given_type, type);
    return TCL_ERROR;
  }

  // Unpack from xfer to Tcl object
  Tcl_Obj* result;
  rc = retrieve_object(interp, objv, id, type, length, &result);
  TCL_CHECK(rc);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}


/**
   interp, objv, id, and length: just for error checking and messages
 */
static inline int
retrieve_object(Tcl_Interp *interp, Tcl_Obj *const objv[], long id,
                adlb_data_type type, int length, Tcl_Obj** result)
{
  long tmp_long;
  double tmp_double;
  int string_length;

  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      memcpy(&tmp_long, xfer, sizeof(long));
      *result = Tcl_NewLongObj(tmp_long);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      memcpy(&tmp_double, xfer, sizeof(double));
      *result = Tcl_NewDoubleObj(tmp_double);
      break;
    case ADLB_DATA_TYPE_STRING:
      *result = Tcl_NewStringObj(xfer, length-1);
      break;
    case ADLB_DATA_TYPE_BLOB:
      string_length = strnlen(xfer, length);
      TCL_CONDITION(string_length < length,
                    "adlb::retrieve: unterminated blob: <%li>", id);
      *result = Tcl_NewStringObj(xfer, string_length);
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      *result = Tcl_NewStringObj(xfer, length-1);
      break;
    default:
      *result = NULL;
      return TCL_ERROR;
  }
  return TCL_OK;
}

static inline void
report_type_mismatch(adlb_data_type expected,
                     adlb_data_type actual)
{
  char e_string[16];
  char a_string[16];
  ADLB_Data_type_tostring(e_string, expected);
  ADLB_Data_type_tostring(a_string, actual);
  printf("type mismatch: expected: %s actual: %s\n",
         e_string, a_string);
}

static inline int set_enumerate_pointers(Tcl_Interp *interp,
                                         Tcl_Obj *const objv[],
                                         const char* token,
                                         char** subscripts,
                                         char** members);

static inline void enumerate_object(Tcl_Interp *interp,
                                    const char* token,
                                    char* subscripts,
                                    int subscripts_length,
                                    char* members,
                                    int members_length,
                                    int records,
                                    Tcl_Obj** result);

/**
   usage:
   adlb::enumerate <id> subscripts|members|dict|count
                   <count>|all <offset>

   subscripts: return list of subscript strings
   members: return list of member TDs
   dict: return dict mapping subscripts to TDs
   count: return integer count of container elements
 */
static int
ADLB_Enumerate_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);
  int rc;
  long container_id;
  int count;
  int offset;
  rc = Tcl_GetLongFromObj(interp, objv[1], &container_id);
  TCL_CHECK_MSG(rc, "requires container id!");
  char* token = Tcl_GetStringFromObj(objv[2], NULL);
  TCL_CONDITION(token, "requires token!");
  // This argument is either the integer count or "all", all == -1
  char* tmp = Tcl_GetStringFromObj(objv[3], NULL);
  if (strcmp(tmp, "all"))
  {
    rc = Tcl_GetIntFromObj(interp, objv[3], &count);
    TCL_CHECK_MSG(rc, "requires count!");
  }
  else
    count = -1;
  rc = Tcl_GetIntFromObj(interp, objv[4], &offset);
  TCL_CHECK_MSG(rc, "requires offset!");

  // Set up call
  char* subscripts;
  int subscripts_length;
  char* members;
  int members_length;
  int records;
  rc = set_enumerate_pointers(interp, objv, token,
                              &subscripts, &members);
  TCL_CHECK_MSG(rc, "unknown token!");

  // Call ADLB
  rc = ADLB_Enumerate(container_id, count, offset,
                      &subscripts, &subscripts_length,
                      &members, &members_length, &records);

  // Return results to Tcl
  Tcl_Obj* result;
  enumerate_object(interp, token,
                   subscripts, subscripts_length,
                   members, members_length, records, &result);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   Encode results requested in pointers
   interp, objv provided for error handling
 */
static inline int
set_enumerate_pointers(Tcl_Interp *interp, Tcl_Obj *const objv[],
                       const char* token,
                       char** subscripts, char** members)
{
  if (!strcmp(token, "subscripts"))
  {
    *subscripts = NULL+1;
    *members = NULL;
  }
  else if (!strcmp(token, "members"))
  {
    *subscripts = NULL;
    *members = NULL+1;
  }
  else if (!strcmp(token, "dict"))
  {
    *subscripts = NULL+1;
    *members = NULL+1;
  }
  else if (!strcmp(token, "count"))
  {
    *subscripts = NULL;
    *members = NULL;
  }
  else
  {
    return TCL_ERROR;
  }
  return TCL_OK;
}

/**
   Simple string struct for indices of strings
   Note: s may not be NULL-terminated: user must refer to length
 */
struct record_entry
{
  char* s;
  int length;
};

static void record_index(char* s, int x, int n,
                         struct record_entry* entries);

/**
   Pack ADLB_Enumerate results into Tcl object
 */
void
enumerate_object(Tcl_Interp *interp, const char* token,
                 char* subscripts, int subscripts_length,
                 char* members, int members_length,
                 int records, Tcl_Obj** result)
{
  if (!strcmp(token, "subscripts"))
  {
    *result = Tcl_NewStringObj(subscripts, subscripts_length-1);
  }
  else if (!strcmp(token, "members"))
  {
    // Scan the members buffer as string records
    struct record_entry entries[records];
    record_index(members, records, members_length, entries);
    Tcl_Obj* objv[records];
    for (int i = 0; i < records; i++)
      objv[i] = Tcl_NewStringObj(entries[i].s, entries[i].length);
    Tcl_Obj* L = Tcl_NewListObj(records, objv);
    *result = L;
  }
  else if (!strcmp(token, "dict"))
  {
    // Use Tcl to convert subscripts string to list
    Tcl_Obj* s = Tcl_NewStringObj(subscripts, subscripts_length);

    // Scan the members buffer as string records
    struct record_entry entries[records];
    record_index(members, records, members_length, entries);

    // Insert each key/value pair into result dict
    Tcl_Obj* dict = Tcl_NewDictObj();
    for (int i = 0; i < records; i++)
    {
      Tcl_Obj* k;
      Tcl_ListObjIndex(interp, s, i, &k);
      Tcl_Obj* v = Tcl_NewStringObj(entries[i].s, entries[i].length);
      Tcl_DictObjPut(interp, dict, k, v);
    }
    *result = dict;
  }
  else if (!strcmp(token, "count"))
  {
    *result = Tcl_NewLongObj(records);
  }
  else
    // Cannot get here
    assert(false);

  if (subscripts != NULL)
    free(subscripts);
  if (members != NULL)
    free(members);
}

/**
   Scan the buffer (of length n) for max RS-separated strings
   RS defined in adlb-defs.h
   @param s The buffer to scan
   @param x The expected number of strings to be found
   @param n The length of the buffer
   @return Records
 */
void
record_index(char* s, int x, int n, struct record_entry* entries)
{
  // Current pointer into buffer
  char* p = s;
  // Current output index
  for (int i = 0; i < x; i++)
  {
    assert(p < p+n);
    char* r = strchr(p, RS);
    int length = r-p;
    entries[i].s = p;
    entries[i].length = length;
    p = r+1;
  }
}

static inline int
ADLB_Retrieve_Blob_Impl(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[], bool decr);

/**
   Copy a blob from the distributed store into a local blob
   in the memory of this process
   Must be freed with adlb::blob_free
   usage: adlb::retrieve_blob <id> => [ list <pointer> <length> ]
 */
static int
ADLB_Retrieve_Blob_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Blob_Impl(cdata, interp, objc, objv, false);
}

static int
ADLB_Retrieve_Blob_Decr_Cmd(ClientData cdata, Tcl_Interp *interp,
                            int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Blob_Impl(cdata, interp, objc, objv, true);
}

static inline int
ADLB_Retrieve_Blob_Impl(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[], bool decr)
{
  if (decr) {
    TCL_ARGS(3);
  } else {
    TCL_ARGS(2);
  }

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  int decr_amount = 0;
  /* Only decrement if refcounting enabled */
  if  (read_refcount_enabled && decr) {
    decr_amount = 0;
    rc = Tcl_GetLongFromObj(interp, objv[2], &id);
    TCL_CHECK_MSG(rc, "requires id!");
  }

  // Retrieve the blob data
  adlb_data_type type;
  int length;
  rc = ADLB_Retrieve(id, &type, decr_amount, xfer, &length);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%li> failed!", id);
  TCL_CONDITION(type == ADLB_DATA_TYPE_BLOB,
                "type mismatch: expected: %i actual: %i",
                ADLB_DATA_TYPE_BLOB, type);

  // Allocate the local blob
  void* blob = malloc(length);
  assert(blob);

  // Copy the blob data
  memcpy(blob, xfer, length);

  // Link the blob into the cache
  bool b = table_lp_add(&blob_cache, id, blob);
  ASSERT(b);

  // printf("retrieved blob: [ %p %i ]\n", blob, length);

  // Pack and return the blob pointer, length as Tcl list
  Tcl_Obj* list[2];
  long pointer = (long) blob;
  list[0] = Tcl_NewLongObj(pointer);
  list[1] = Tcl_NewIntObj(length);
  Tcl_Obj* result = Tcl_NewListObj(2, list);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   Free a local blob cached with adlb::blob_cache
   usage: adlb::blob_free <id>
 */
static int
ADLB_Blob_Free_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  void* blob = table_lp_remove(&blob_cache, id);
  TCL_CONDITION(blob != NULL, "blob not cached: <%li>", id);
  free(blob);
  return TCL_OK;
}

/**
   Free a local blob with provided pointer
   usage: adlb::local_blob_free <ptr>
 */
static int
ADLB_Local_Blob_Free_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;

  long ptrVal;
  void *ptr;
  rc = Tcl_GetLongFromObj(interp, objv[1], &ptrVal);
  TCL_CHECK_MSG(rc, "requires ptr!");

  ptr = (void *)ptrVal;
  free(ptr);
  return TCL_OK;
}

/**
   adlb::store_blob <id> <pointer> <length> [<decr>]
 */
static int
ADLB_Store_Blob_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 4 || objc == 5,
                "adlb::store_blob requires 4 or 5 args!");

  int rc;
  long id;
  long p;
  void* pointer;
  int length;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");
  rc = Tcl_GetLongFromObj(interp, objv[2], &p);
  TCL_CHECK_MSG(rc, "requires pointer!");
  pointer = (void*) p;
  rc = Tcl_GetIntFromObj(interp, objv[3], &length);
  TCL_CHECK_MSG(rc, "requires length!");

  bool decr = true;
  if (objc == 5) {
    int decr_int;
    rc = Tcl_GetIntFromObj(interp, objv[4], &decr_int);
    TCL_CHECK_MSG(rc, "decr must be int!");
    decr = decr_int != 0;
  }

  int *notify_ranks;
  int notify_count;
  rc = ADLB_Store(id, pointer, length, decr, &notify_ranks, &notify_count);
  TCL_CONDITION(rc == ADLB_SUCCESS, "failed!");

  Tcl_Obj* result = TclListFromArray(interp, notify_ranks, notify_count);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   adlb::store_blob_floats <id> [ list doubles ]
 */
static int
ADLB_Blob_store_floats_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);
  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  int length;
  Tcl_Obj** objs;
  rc = Tcl_ListObjGetElements(interp, objv[2], &length, &objs);
  TCL_CHECK_MSG(rc, "requires list!");

  TCL_CONDITION(length*sizeof(double) <= ADLB_DATA_MAX,
                "list too long!");

  for (int i = 0; i < length; i++)
  {
    double v;
    rc = Tcl_GetDoubleFromObj(interp, objs[i], &v);
    memcpy(xfer+i*sizeof(double), &v, sizeof(double));
  }

  int *notify_ranks;
  int notify_count;
  rc = ADLB_Store(id, xfer, length*sizeof(double), true,
                  &notify_ranks, &notify_count);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::store <%li> failed!", id);

  Tcl_Obj* result = TclListFromArray(interp, notify_ranks, notify_count);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   adlb::blob_from_string <string value>
 */
static int
ADLB_Blob_From_String_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int length;
  char *data = Tcl_GetStringFromObj(objv[1], &length);

  TCL_CONDITION(data != NULL,
                "adlb::blob_from_string failed!");
  int length2 = strlen(data)+1; // TODO: remote
  printf("length1: %i length2: %i\n", length, length2);

  void *blob = malloc(length2 * sizeof(char));
  memcpy(blob, data, length2);

  Tcl_Obj* list[2];
  list[0] = Tcl_NewLongObj((long)blob);
  list[1] = Tcl_NewIntObj(length2);
  Tcl_Obj* result = Tcl_NewListObj(2, list);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
  return TCL_OK;
}

/**
   usage: adlb::insert <id> <subscript> <member> [<drops>]
*/
static int
ADLB_Insert_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 4 || objc == 5),
                "requires 3 or 4 args!");

  int rc;
  long id;
  Tcl_GetLongFromObj(interp, objv[1], &id);
  char* subscript = Tcl_GetString(objv[2]);
  int member_length;
  char* member = Tcl_GetStringFromObj(objv[3], &member_length);
  assert(member);
  int drops = 0;
  if (objc == 5)
  {
    rc = Tcl_GetIntFromObj(interp, objv[4], &drops);
    TCL_CHECK(rc);
  }

  rc = ADLB_Insert(id, subscript, member, member_length, drops);

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "failed: <%li>[\"%s\"]\n",
                id, subscript);
  return TCL_OK;
}

/**
   usage: adlb::insert_atomic <id> <subscript>
   returns: 1 if the id[subscript] already existed, else 0
*/
static int
ADLB_Insert_Atomic_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  bool b;
  long id;
  Tcl_GetLongFromObj(interp, objv[1], &id);
  char* subscript = Tcl_GetString(objv[2]);

  DEBUG_ADLB("adlb::insert_atomic: <%li>[\"%s\"]",
             id, subscript);
  int rc = ADLB_Insert_atomic(id, subscript, &b);

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::insert_atomic: failed: <%li>[%s]",
                id, subscript);

  Tcl_Obj* result = Tcl_NewBooleanObj(b);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::lookup <id> <subscript>
   returns the member TD or 0 if not found
*/
static int
ADLB_Lookup_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  long id;
  int rc;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "adlb::lookup could not parse given id!");
  char* subscript = Tcl_GetString(objv[2]);

  char member[ADLB_DATA_MEMBER_MAX];
  int found;
  rc = ADLB_Lookup(id, subscript, member, &found);
  TCL_CONDITION(rc == ADLB_SUCCESS, "lookup failed for: <%li>[%s]",
                id, subscript);

  if (found == -1)
    sprintf(member, "0");

  DEBUG_ADLB("adlb::lookup <%li>[\"%s\"]=<%s>",
             id, subscript, member);

  Tcl_Obj* result = Tcl_NewStringObj(member, -1);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::lock <id> => false (try again) or
                             true (locked by caller)
*/
static int
ADLB_Lock_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "argument must be a long integer!");

  bool locked;
  rc = ADLB_Lock(id, &locked);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%li> failed!", id);

  Tcl_Obj* result = Tcl_NewBooleanObj(locked);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::unlock <id>
*/
static int
ADLB_Unlock_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "argument must be a long integer!");

  rc = ADLB_Unlock(id);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%li> failed!", id);

  return TCL_OK;
}

/**
   usage: adlb::unique => id
*/
static int
ADLB_Unique_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  long id;
  int rc = ADLB_Unique(&id);
  ASSERT(rc == ADLB_SUCCESS);

  // DEBUG_ADLB("adlb::unique: <%li>", id);

  Tcl_Obj* result = Tcl_NewLongObj(id);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::container_typeof <id>
*/
static int
ADLB_Typeof_Cmd(ClientData cdata, Tcl_Interp *interp,
		int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long id;
  Tcl_GetLongFromObj(interp, objv[1], &id);

  adlb_data_type type;
  int rc = ADLB_Typeof(id, &type);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_typeof <%li> failed!", id);

  // DEBUG_ADLB("adlb::container_typeof: <%li> is: %i\n", id, type);

  char type_string[32];
  ADLB_Data_type_tostring(type_string, type);

  // DEBUG_ADLB("adlb::container_typeof: <%li> is: %s",
  //            id, type_string);

  Tcl_Obj* result = Tcl_NewStringObj(type_string, -1);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::container_typeof <id>
*/
static int
ADLB_Container_Typeof_Cmd(ClientData cdata, Tcl_Interp *interp,
                          int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long id;
  Tcl_GetLongFromObj(interp, objv[1], &id);

  adlb_data_type type;
  int rc = ADLB_Container_typeof(id, &type);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_typeof <%li> failed!", id);

  char type_string[32];
  ADLB_Data_type_tostring(type_string, type);

  Tcl_Obj* result = Tcl_NewStringObj(type_string, -1);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::container_reference
      <container_id> <subscript> <reference> <reference_type>

      reference_type is type used internally to represent
      the reference e.g. integer for plain turbine IDs, or
      string if represented as a more complex datatype
*/
static int
ADLB_Container_Reference_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);

  long container_id;
  int rc;
  rc = Tcl_GetLongFromObj(interp, objv[1], &container_id);
  TCL_CHECK_MSG(rc, "adlb::container_reference: "
                "argument 1 is not a long integer!");
  char* subscript = Tcl_GetString(objv[2]);
  long reference;
  rc = Tcl_GetLongFromObj(interp, objv[3], &reference);
  TCL_CHECK_MSG(rc, "adlb::container_reference: "
                "argument 3 is not a long integer!");

  const char *ref_type_name = Tcl_GetString(objv[4]);
  TCL_CONDITION(ref_type_name != NULL,
                "adlb::container_reference: "
                "argument 4 not valid!");
  int ref_type = type_from_string(ref_type_name);

  switch (ref_type)
  {
    case ADLB_DATA_TYPE_INTEGER:
    case ADLB_DATA_TYPE_STRING:
        break;

    default:
        Tcl_AddErrorInfo(interp,
                "adlb::container_reference: invalid type for "
                "container_reference call.");
        return TCL_ERROR;
  }

  // DEBUG_ADLB("adlb::container_reference: <%li>[%s] => <%li>\n",
  //            container_id, subscript, reference);
  rc = ADLB_Container_reference(container_id, subscript, reference,
                                ref_type);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_reference: <%li> failed!",
                container_id);
  return TCL_OK;
}

/**
   usage: adlb::container_size <container_id>
*/
static int
ADLB_Container_Size_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long container_id;
  int rc;
  rc = Tcl_GetLongFromObj(interp, objv[1], &container_id);
  TCL_CHECK_MSG(rc, "adlb::container_size: "
                "argument is not a long integer!");

  int size;
  // DEBUG_ADLB("adlb::container_size: <%li>",
  //            container_id, size);
  rc = ADLB_Container_size(container_id, &size);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_size: <%li> failed!",
                container_id);
  Tcl_Obj* result = Tcl_NewIntObj(size);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::slot_create <container_id> [ increment ]
*/
static int
ADLB_Slot_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 2 || objc == 3),
                "requires 1 or 2 args!");

  long container_id;
  Tcl_GetLongFromObj(interp, objv[1], &container_id);

  int incr = 1;
  if (objc == 3)
    Tcl_GetIntFromObj(interp, objv[2], &incr);

  // DEBUG_ADLB("adlb::slot_create: <%li>", container_id);
  int rc = ADLB_Refcount_incr(container_id, ADLB_WRITE_REFCOUNT,
                              incr);

  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;
  return TCL_OK;
}

/**
   usage: adlb::slot_drop <container_id>
*/
static int
ADLB_Slot_Drop_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 2 || objc == 3),
                "requires 1 or 2 args!");

  long container_id;
  Tcl_GetLongFromObj(interp, objv[1], &container_id);

  int decr = 1;
  if (objc == 3)
    Tcl_GetIntFromObj(interp, objv[2], &decr);

  // DEBUG_ADLB("adlb::slot_drop: <%li>", container_id);
  int rc = ADLB_Refcount_incr(container_id, ADLB_WRITE_REFCOUNT,
                              -1 * decr);

  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;
  return TCL_OK;
}

/**
   usage: adlb::refcount_incr <container_id> <refcount_type> <change>
   refcount_type in { $adlb::READ_REFCOUNT , $adlb::WRITE_REFCOUNT ,
          $adlb::READWRITE_REFCOUNT }
*/
static int
ADLB_Refcount_Incr_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 4), "requires 4 args!");

  long container_id;
  Tcl_GetLongFromObj(interp, objv[1], &container_id);

  adlb_refcount_type type;
  int t;
  Tcl_GetIntFromObj(interp, objv[2], &t);
  type = t;

  int change = 1;
  Tcl_GetIntFromObj(interp, objv[3], &change);

  if (!read_refcount_enabled) {
    // Intercept any read refcount operations
    if (type == ADLB_READ_REFCOUNT)
    {
      return TCL_OK;
    }
    else if (type == ADLB_READWRITE_REFCOUNT)
    {
      type = ADLB_WRITE_REFCOUNT;
    }
  }

  // DEBUG_ADLB("adlb::refcount_incr: <%li>", container_id);
  int rc = ADLB_Refcount_incr(container_id, type, change);

  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;
  return TCL_OK;
}


/**
   usage: adlb::read_refcount_enableding
   If not set, all read reference count operations are ignored
 **/
static int
ADLB_Enable_Read_Refcount_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  read_refcount_enabled = true;
  return TCL_OK;
}

/**
   usage: adlb::fail
 */
static int
ADLB_Fail_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  ADLB_Fail(1);
  return TCL_OK;
}

/**
   usage: adlb::abort
 */
static int
ADLB_Abort_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  ADLB_Abort(1);
  return TCL_OK;
}


/**
   usage: adlb::finalize <b>
   If b, finalize MPI
 */
static int
ADLB_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  int rc = ADLB_Finalize();
  if (rc != ADLB_SUCCESS)
    printf("WARNING: ADLB_Finalize() failed!\n");
  TCL_ARGS(2);
  int b;
  Tcl_GetBooleanFromObj(interp, objv[1], &b);
  if (b)
    MPI_Finalize();
  turbine_debug_finalize();
  return TCL_OK;
}

/**
   Shorten object creation lines.  "adlb::" namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "adlb::" tcl_function, c_function, \
                         NULL, NULL);

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tcladlb_Init(Tcl_Interp* interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "ADLB", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  tcl_adlb_init(interp);

  return TCL_OK;
}

void
tcl_adlb_init(Tcl_Interp* interp)
{
  COMMAND("init",      ADLB_Init_Cmd);
  COMMAND("server",    ADLB_Server_Cmd);
  COMMAND("rank",      ADLB_Rank_Cmd);
  COMMAND("amserver",  ADLB_AmServer_Cmd);
  COMMAND("size",      ADLB_Size_Cmd);
  COMMAND("servers",   ADLB_Servers_Cmd);
  COMMAND("workers",   ADLB_Workers_Cmd);
  COMMAND("barrier",   ADLB_Barrier_Cmd);
  COMMAND("hostmap",   ADLB_Hostmap_Cmd);
  COMMAND("put",       ADLB_Put_Cmd);
  COMMAND("get",       ADLB_Get_Cmd);
  COMMAND("iget",      ADLB_Iget_Cmd);
  COMMAND("create",    ADLB_Create_Cmd);
  COMMAND("exists",    ADLB_Exists_Cmd);
  COMMAND("store",     ADLB_Store_Cmd);
  COMMAND("retrieve",  ADLB_Retrieve_Cmd);
  COMMAND("retrieve_decr",  ADLB_Retrieve_Decr_Cmd);
  COMMAND("enumerate", ADLB_Enumerate_Cmd);
  COMMAND("retrieve_blob", ADLB_Retrieve_Blob_Cmd);
  COMMAND("retrieve_decr_blob", ADLB_Retrieve_Blob_Decr_Cmd);
  COMMAND("blob_free",  ADLB_Blob_Free_Cmd);
  COMMAND("local_blob_free",  ADLB_Local_Blob_Free_Cmd);
  COMMAND("store_blob", ADLB_Store_Blob_Cmd);
  COMMAND("store_blob_floats", ADLB_Blob_store_floats_Cmd);
  COMMAND("blob_from_string", ADLB_Blob_From_String_Cmd);
  COMMAND("slot_create", ADLB_Slot_Create_Cmd);
  COMMAND("slot_drop", ADLB_Slot_Drop_Cmd);
  COMMAND("enable_read_refcount",  ADLB_Enable_Read_Refcount_Cmd);
  COMMAND("refcount_incr", ADLB_Refcount_Incr_Cmd);
  COMMAND("insert",    ADLB_Insert_Cmd);
  COMMAND("insert_atomic", ADLB_Insert_Atomic_Cmd);
  COMMAND("lookup",    ADLB_Lookup_Cmd);
  COMMAND("lock",      ADLB_Lock_Cmd);
  COMMAND("unlock",    ADLB_Unlock_Cmd);
  COMMAND("unique",    ADLB_Unique_Cmd);
  COMMAND("typeof",    ADLB_Typeof_Cmd);
  COMMAND("container_typeof",    ADLB_Container_Typeof_Cmd);
  COMMAND("container_reference", ADLB_Container_Reference_Cmd);
  COMMAND("container_size",      ADLB_Container_Size_Cmd);
  COMMAND("fail",      ADLB_Fail_Cmd);
  COMMAND("abort",     ADLB_Abort_Cmd);
  COMMAND("finalize",  ADLB_Finalize_Cmd);
}
