
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

#include <assert.h>

// strnlen() is a GNU extension
#define _GNU_SOURCE
#define __USE_GNU
#include <string.h>

#include <tcl.h>
#include <adlb.h>

#include <log.h>

#include <table_lp.h>

#include "src/tcl/util.h"
#include "src/util/debug.h"

static int adlb_rank;
/** Number of workers */
static int workers;
/** Number of servers */
static int servers;

static int am_server, am_debug_server;

/** Size of MPI_COMM_WORLD */
static int mpi_size = -1;

/** Rank in MPI_COMM_WORLD */
static int mpi_rank = -1;

/** Communicator for ADLB workers */
static MPI_Comm worker_comm;

/** ADLB uses -1 to mean "any" in ADLB_Put() and ADLB_Reserve() */
#define ADLB_ANY -1

static char xfer[ADLB_MSG_MAX];

/** Map from TD to local blob pointers */
static struct table_lp blob_cache;

/**
   usage: adlb::init <servers> <types>
   Simplified use of ADLB_Init type_vect: just give adlb_init
   a number ntypes, and the valid types will be: [0..ntypes-1]
 */
static int
ADLB_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

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

  int argc = 0;
  char** argv = NULL;
  rc = MPI_Init(&argc, &argv);
  assert(rc == MPI_SUCCESS);

  MPI_Comm_size(MPI_COMM_WORLD, &mpi_size);
  workers = mpi_size - servers;

  MPI_Comm_rank(MPI_COMM_WORLD, &mpi_rank);

  if (mpi_rank == 0)
  {
    if (workers <= 0)
      puts("WARNING: No workers");
    // Other configuration information will go here...
  }

  // ADLB_Init(int num_servers, int use_debug_server,
  //           int aprintf_flag, int num_types, int *types,
  //           int *am_server, int *am_debug_server, MPI_Comm *app_comm)
  rc = ADLB_Init(servers, 0, 0, ntypes, type_vect,
                   &am_server, &am_debug_server, &worker_comm);
  assert(rc == ADLB_SUCCESS);

  if (! am_server)
    MPI_Comm_rank(worker_comm, &adlb_rank);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::SUCCESS", -1), NULL,
                 Tcl_NewIntObj(ADLB_SUCCESS), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::ANY", -1), NULL,
                 Tcl_NewIntObj(ADLB_ANY), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::INTEGER", -1), NULL,
                 Tcl_NewIntObj(ADLB_DATA_TYPE_INTEGER), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::FLOAT", -1), NULL,
                 Tcl_NewIntObj(ADLB_DATA_TYPE_FLOAT), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::STRING", -1), NULL,
                 Tcl_NewIntObj(ADLB_DATA_TYPE_STRING), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::FILE", -1), NULL,
                 Tcl_NewIntObj(ADLB_DATA_TYPE_FILE), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::BLOB", -1), NULL,
                 Tcl_NewIntObj(ADLB_DATA_TYPE_BLOB), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::CONTAINER", -1), NULL,
                   Tcl_NewIntObj(ADLB_DATA_TYPE_CONTAINER), 0);

  Tcl_SetObjResult(interp, Tcl_NewIntObj(ADLB_SUCCESS));
  return TCL_OK;
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
  double logging = 0.0;
  int rc = ADLB_Server(max_memory, logging);
  Tcl_SetObjResult(interp, Tcl_NewIntObj(rc));
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
  assert(rc == MPI_SUCCESS);
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
                    work_type, priority);

  assert(rc == ADLB_SUCCESS);
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

  char result[ADLB_MSG_MAX];
  int work_type;
  int work_prio;
  int work_handle[ADLB_HANDLE_SIZE];
  int work_len;
  int answer_rank;
  int req_types[4];
  bool found_work = false;

  req_types[0] = req_type;
  req_types[1] = req_types[2] = req_types[3] = -1;

  // puts("enter reserve");
  int rc = ADLB_Reserve(req_types, &work_type, &work_prio,
                        work_handle, &work_len, &answer_rank);
  // puts("exit reserve");
  if (rc == ADLB_DONE_BY_EXHAUSTION)
  {
    // puts("ADLB_DONE_BY_EXHAUSTION!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_MORE_WORK ) {
    // puts("ADLB_NO_MORE_WORK!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_CURRENT_WORK) {
    // puts("ADLB_NO_CURRENT_WORK");
    result[0] = '\0';
  }
  else if (rc < 0) {
    puts("rc < 0");
    result[0] = '\0';
  }
  else
  {
    rc = ADLB_Get_reserved(result, work_handle);
    if (rc == ADLB_NO_MORE_WORK)
    {
      puts("No more work on Get_reserved()!");
      result[0] = '\0';
    }
    else
      found_work = true;
  }

  if (found_work)
    DEBUG_ADLB("adlb::get: %s", result);

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
static inline adlb_data_type type_from_string(char* type_string)
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
  else if (strcmp(type_string, "file") == 0)
    result = ADLB_DATA_TYPE_FILE;
  else if (strcmp(type_string, "container") == 0)
    result = ADLB_DATA_TYPE_CONTAINER;
  else
    result = ADLB_DATA_TYPE_NULL;
  return result;
}

/**
   usage: adlb::create <id> <type> [<extra>]
   @param extra is only used for files and containers
*/
static int
ADLB_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 3, "adlb::create requires >= 3 args!");

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CONDITION(rc == TCL_OK, "adlb:create could not get data id");

  int type;
  Tcl_GetIntFromObj(interp, objv[2], &type);

  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      rc = ADLB_Create_integer(id);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      rc = ADLB_Create_float(id);
      break;
    case ADLB_DATA_TYPE_STRING:
      rc = ADLB_Create_string(id);
      break;
    case ADLB_DATA_TYPE_BLOB:
      rc = ADLB_Create_blob(id);
      break;
    case ADLB_DATA_TYPE_FILE:
      TCL_CONDITION(objc >= 4,
                    "adlb::create type=file requires file name!");
      char* filename = Tcl_GetString(objv[3]);
      rc = ADLB_Create_file(id, filename);
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      TCL_CONDITION(objc >= 4,
                    "adlb::create type=container requires "
                    "subscript type!");
      char* subscript_type_string = Tcl_GetString(objv[3]);
      adlb_data_type subscript_type =
          type_from_string(subscript_type_string);
      rc = ADLB_Create_container(id, subscript_type);
      break;
    case ADLB_DATA_TYPE_NULL:
      Tcl_AddErrorInfo(interp,
                       "adlb::create: unknown type!");
      return TCL_ERROR;
      break;
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
   usage: adlb::store <id> <type> <value>
   @param value Ignored for types file, container
   If given a blob, the value must be a string
   This allows users to store a string in a blob
*/
static int
ADLB_Store_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 4, "adlb::create requires >= 4 args!");

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
      TCL_CONDITION(objc == 4, "incorrect arguments!");
      rc = Tcl_GetLongFromObj(interp, objv[3], (long*) xfer);
      TCL_CHECK_MSG(rc, "adlb::store long <%li> failed!", id);
      length = sizeof(long);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      TCL_CONDITION(objc == 4, "incorrect arguments!");
      rc = Tcl_GetDoubleFromObj(interp, objv[3], &tmp_double);
      TCL_CHECK_MSG(rc, "adlb::store double <%li> failed!", id);
      memcpy(xfer, &tmp_double, sizeof(double));
      length = sizeof(double);
      break;
    case ADLB_DATA_TYPE_STRING:
      TCL_CONDITION(objc == 4, "incorrect arguments!");
      data = Tcl_GetStringFromObj(objv[3], &length);
      TCL_CONDITION(data != NULL,
                    "adlb::store string <%li> failed!", id);
      length = strlen(data)+1;
      TCL_CONDITION(length < ADLB_MSG_MAX,
          "adlb::store: string too long: <%li>", id);
      break;
    case ADLB_DATA_TYPE_BLOB:
      if (objc == 4)
      {
        // User is storing a Tcl string in a blob
        TCL_CONDITION(objc == 4, "incorrect arguments!");
        data = Tcl_GetStringFromObj(objv[3], &length);
        TCL_CONDITION(data != NULL,
                      "adlb::store blob <%li> failed!", id);
        length = strlen(data)+1;
        TCL_CONDITION(length < ADLB_MSG_MAX,
                      "adlb::store: string too long: <%li>", id);
        break;
      }
      else if (objc == 5)
      {
        // User is providing a pointer+length
        TCL_CONDITION(objc == 5, "incorrect arguments!");
        int p;
        rc = Tcl_GetIntFromObj(interp, objv[3], &p);
        TCL_CHECK_MSG(rc, "required pointer!");
        data = (void*) p;
        rc = Tcl_GetIntFromObj(interp, objv[4], &length);
        TCL_CHECK_MSG(rc, "required length!");
      }
      else
        TCL_RETURN_ERROR("incorrect arguments!");
      break;
    case ADLB_DATA_TYPE_FILE:
      // Ignore objv[3]
      break;

    case ADLB_DATA_TYPE_CONTAINER:
      // Ignore objv[3]
      break;
    default:
      printf("adlb::store unknown type!\n");
      rc = TCL_ERROR;
      break;
  }

  // DEBUG_ADLB("adlb::store: <%li>=%s", id, data);
  rc = ADLB_Store(id, data, length);

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::store <%li> failed!", id);
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
  TCL_CONDITION((objc == 2 || objc == 3),
                "requires 1 or 2 args!");

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  adlb_data_type given_type = ADLB_DATA_TYPE_NULL;
  if (objc == 3)
  {
    int tmp;
    rc = Tcl_GetIntFromObj(interp, objv[2], &tmp);
    TCL_CHECK_MSG(rc, "2nd arg must be adlb:: type!");
    given_type = tmp;
  }

  // Retrieve the data, actual type, and length from server
  adlb_data_type type;
  int length;
  rc = ADLB_Retrieve(id, &type, xfer, &length);
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
    case ADLB_DATA_TYPE_FILE:
      *result = Tcl_NewStringObj(xfer, length-1);
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      *result = Tcl_NewStringObj(xfer, length-1);
      break;
    default:
      result = NULL;
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

/**
   usage:
   adlb::enumerate <id> subscripts|members|dict|count <count>|all <offset>

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
  char* token;
  char* tmp;
  int count;
  int offset;
  rc = Tcl_GetLongFromObj(interp, objv[1], &container_id);
  TCL_CHECK_MSG(rc, "requires container id!");
  token = Tcl_GetStringFromObj(objv[2], NULL);
  TCL_CONDITION(token, "requires token!");
  // This argument is either the integer count or "all", all == -1
  tmp = Tcl_GetStringFromObj(objv[3], NULL);
  if (strcmp(tmp, "all"))
  {
    rc = Tcl_GetIntFromObj(interp, objv[3], &count);
    TCL_CHECK_MSG(rc, "requires count!");
  }
  else
    count = -1;
  rc = Tcl_GetIntFromObj(interp, objv[4], &offset);
  TCL_CHECK_MSG(rc, "requires offset!");

  char* subscripts;
  int length;
  adlb_datum_id* member_ids;
  int actual;

  if (!strcmp(token, "subscripts"))
  {
    subscripts = NULL+1;
    member_ids = NULL;
  }
  else if (!strcmp(token, "members"))
  {
    subscripts = NULL;
    member_ids = NULL+1;
  }
  else if (!strcmp(token, "dict"))
  {
    subscripts = NULL+1;
    member_ids = NULL+1;
  }
  else if (!strcmp(token, "count"))
  {
    subscripts = NULL;
    member_ids = NULL;
  }
  else 
  {
    tcl_condition_failed(interp, objv[0], "unknown token!");
    return TCL_ERROR;
  }

  rc = ADLB_Enumerate(container_id, count, offset,
                      &subscripts, &length, &member_ids, &actual);

  Tcl_Obj* result;
  if (!strcmp(token, "subscripts"))
  {
    result = Tcl_NewStringObj(subscripts, length-1);
    free(subscripts);
  }
  else if (!strcmp(token, "members"))
  {
    Tcl_Obj* objv[actual];
    for (int i = 0; i < actual; i++)
    {
      objv[i] = Tcl_NewLongObj(member_ids[i]);
    }
    free(member_ids);
    result = Tcl_NewListObj(actual, objv);
  }
  else if (!strcmp(token, "dict"))
  {
    // Use Tcl to convert string to list
    Tcl_Obj* s = Tcl_NewStringObj(subscripts, length);

    // Member TDs
    Tcl_Obj* m[actual];
    for (int i = 0; i < actual; i++)
      m[i] = Tcl_NewLongObj(member_ids[i]);
    result = Tcl_NewDictObj();
    for (int i = 0; i < actual; i++)
    {
      Tcl_Obj* p;
      Tcl_ListObjIndex(interp, s, i, &p);
      Tcl_DictObjPut(interp, result, p, m[i]);
    }
  }
  else if (!strcmp(token, "count"))
  {
    result = Tcl_NewLongObj(actual);
  }
  else
    // Cannot get here
    return TCL_ERROR;

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   Copy a blob from the distributed store into a local blob
   in the memory of this process
   Must be freed with adlb::blob_free
   usage: adlb::blob_cache_retrieve <id> => [ list <pointer> <length> ]
 */
static int
ADLB_Blob_Cache_Retrieve_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  // Retrieve the blob data
  adlb_data_type type;
  int length;
  rc = ADLB_Retrieve(id, &type, xfer, &length);
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
  assert(b);

  // printf("blob_cache: %p\n", blob);

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
   adlb::store_blob_floats <id> [ list doubles ]
 */
static int
ADLB_Blob_store_floats_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  int length;
  Tcl_Obj** objs;
  rc = Tcl_ListObjGetElements(interp, objv[2], &length, &objs);
  TCL_CHECK_MSG(rc, "requires list!");

  TCL_CONDITION(length*sizeof(double) <= ADLB_MSG_MAX,
                "list too long!");

  for (int i = 0; i < length; i++)
  {
    double v;
    rc = Tcl_GetDoubleFromObj(interp, objs[i], &v);
    memcpy(xfer+i*sizeof(double), &v, sizeof(double));
  }

  rc = ADLB_Store(id, xfer, length*sizeof(double));

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::store <%li> failed!", id);
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
  long member;
  rc = Tcl_GetLongFromObj(interp, objv[3], &member);
  assert(rc == TCL_OK);
  int drops = 0;
  if (objc == 5)
  {
    rc = Tcl_GetIntFromObj(interp, objv[4], &drops);
    TCL_CHECK(rc);
  }

  rc = ADLB_Insert(id, subscript, member, drops);

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "failed: <%li>[%s]=<%li>\n",
                id, subscript, member);
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

  long member;
  rc = ADLB_Lookup(id, subscript, &member);
  TCL_CONDITION(rc == ADLB_SUCCESS, "lookup failed for: <%li>[%s]",
                id, subscript);

  DEBUG_ADLB("adlb::lookup <%li>[\"%s\"]=<%li>",
             id, subscript, member);

  Tcl_Obj* result = Tcl_NewLongObj(member);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::close <id>
   returns list of int ranks that must be notified
*/
static int
ADLB_Close_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  long id;
  rc = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "argument must be a long integer!");

  // DEBUG_ADLB("adlb::close: <%li>\n", id);
  int* ranks;
  int count;
  rc = ADLB_Close(id, &ranks, &count);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%li> failed!", id);

  Tcl_Obj* result = Tcl_NewListObj(0, NULL);
  if (count > 0)
  {
    for (int i = 0; i < count; i++)
    {
      Tcl_Obj* o = Tcl_NewIntObj(ranks[i]);
      Tcl_ListObjAppendElement(interp, result, o);
    }
    free(ranks);
  }
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
  assert(rc == ADLB_SUCCESS);

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
                  <container_id> <subscript> <reference>
*/
static int
ADLB_Container_Reference_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);

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

  // DEBUG_ADLB("adlb::container_reference: <%li>[%s] => <%li>\n",
  //            container_id, subscript, reference);
  rc = ADLB_Container_reference(container_id, subscript, reference);
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
   usage: adlb::slot_create <container_id>
*/
static int
ADLB_Slot_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long container_id;
  Tcl_GetLongFromObj(interp, objv[1], &container_id);

  // DEBUG_ADLB("adlb::slot_create: <%li>", container_id);
  int rc = ADLB_Slot_create(container_id);

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
  TCL_ARGS(2);

  long container_id;
  Tcl_GetLongFromObj(interp, objv[1], &container_id);

  // DEBUG_ADLB("adlb::slot_drop: <%li>", container_id);
  int rc = ADLB_Slot_drop(container_id);

  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;
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
  // ADLB_Abort does not return
  return TCL_OK;
}

/**
   usage: adlb::finalize
 */
static int
ADLB_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  ADLB_Finalize();
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
Tcladlb_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "ADLB", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  COMMAND("init",      ADLB_Init_Cmd);
  COMMAND("server",    ADLB_Server_Cmd);
  COMMAND("rank",      ADLB_Rank_Cmd);
  COMMAND("amserver",  ADLB_AmServer_Cmd);
  COMMAND("size",      ADLB_Size_Cmd);
  COMMAND("servers",   ADLB_Servers_Cmd);
  COMMAND("workers",   ADLB_Workers_Cmd);
  COMMAND("barrier",   ADLB_Barrier_Cmd);
  COMMAND("put",       ADLB_Put_Cmd);
  COMMAND("get",       ADLB_Get_Cmd);
  COMMAND("create",    ADLB_Create_Cmd);
  COMMAND("exists",    ADLB_Exists_Cmd);
  COMMAND("store",     ADLB_Store_Cmd);
  COMMAND("retrieve",  ADLB_Retrieve_Cmd);
  COMMAND("enumerate", ADLB_Enumerate_Cmd);
  COMMAND("blob_cache_retrieve", ADLB_Blob_Cache_Retrieve_Cmd);
  COMMAND("blob_free",  ADLB_Blob_Free_Cmd);
  COMMAND("store_blob_floats", ADLB_Blob_store_floats_Cmd);
  COMMAND("slot_create", ADLB_Slot_Create_Cmd);
  COMMAND("slot_drop", ADLB_Slot_Drop_Cmd);
  COMMAND("insert",    ADLB_Insert_Cmd);
  COMMAND("insert_atomic", ADLB_Insert_Atomic_Cmd);
  COMMAND("lookup",    ADLB_Lookup_Cmd);
  COMMAND("lock",      ADLB_Lock_Cmd);
  COMMAND("unlock",    ADLB_Unlock_Cmd);
  COMMAND("close",     ADLB_Close_Cmd);
  COMMAND("unique",    ADLB_Unique_Cmd);
  COMMAND("typeof",    ADLB_Typeof_Cmd);
  COMMAND("container_typeof",    ADLB_Container_Typeof_Cmd);
  COMMAND("container_reference", ADLB_Container_Reference_Cmd);
  COMMAND("container_size",      ADLB_Container_Size_Cmd);
  COMMAND("abort",     ADLB_Abort_Cmd);
  COMMAND("finalize",  ADLB_Finalize_Cmd);

  return TCL_OK;
}
