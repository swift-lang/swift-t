
/**
 * TCL extension for ADLB
 *
 * @author wozniak
 * */

#include <assert.h>

#include <tcl.h>
#include <adlb.h>

#include "src/tcl/util.h"
#include "src/util/debug.h"

enum
{ CMDLINE };

static int adlb_rank;
/** Number of workers */
static int workers;
/** Number of servers */
static int servers;

static int am_server, am_debug_server;

/** Max command-line length */
#define ADLBTCL_CMD_MAX 1024

// ADLB uses -1 to mean "any" in ADLB_Put() and ADLB_Reserve()
#define ADLB_ANY -1

char retrieved[ADLB_DHT_MAX];

/**
   Simplified use of ADLB_Init type_vect: just give adlb_init
   a number ntypes, and the valid types will be: [0..ntypes-1]
 */
static int
ADLB_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int ntypes;
  int error = Tcl_GetIntFromObj(interp, objv[1], &ntypes);
  TCL_CHECK(error);

  servers = 1;

  int argc = 0;
  char** argv = NULL;

  printf("ntypes: %i\n", ntypes);
  int type_vect[ntypes];
  for (int i = 0; i < ntypes; i++)
    type_vect[i] = i;

  MPI_Comm app_comm;

  int code;
  code = MPI_Init(&argc, &argv);
  assert(code == MPI_SUCCESS);

  int size;
  MPI_Comm_size(MPI_COMM_WORLD, &size);
  workers = size - servers;

  if (workers <= 0)
    puts("WARNING: No workers");

  // ADLB_Init(int num_servers, int use_debug_server,
  //           int aprintf_flag, int num_types, int *types,
  //           int *am_server, int *am_debug_server, MPI_Comm *app_comm)
  code = ADLB_Init(servers, 0, 0, ntypes, type_vect,
                   &am_server, &am_debug_server, &app_comm);
  assert(code == ADLB_SUCCESS);

  // printf("am_server: %i\n", am_server);

  if (! am_server)
    MPI_Comm_rank(app_comm, &adlb_rank);

  // code = MPI_Barrier(app_comm);
  // assert(code == MPI_SUCCESS);

  if ( am_server )
  {
    puts("ADLB server starting");
    ADLB_Server( 3000000, 0.0 );
    puts("ADLB server done");
  }

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::SUCCESS", -1), NULL,
                 Tcl_NewIntObj(ADLB_SUCCESS), 0);

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("::adlb::ANY", -1), NULL,
                 Tcl_NewIntObj(ADLB_ANY), 0);

  Tcl_SetObjResult(interp, Tcl_NewIntObj(ADLB_SUCCESS));

  return TCL_OK;
}

/**
   usage: no args, returns ADLB rank
*/
static int
ADLB_Rank_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(adlb_rank));
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
   usage: adlb_put <reserve_rank> <work type> <work unit>
*/
static int
ADLB_Put_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);

  int reserve_rank;
  int work_type;
  Tcl_GetIntFromObj(interp, objv[1], &reserve_rank);
  Tcl_GetIntFromObj(interp, objv[2], &work_type);
  char* cmd = Tcl_GetString(objv[3]);

  DEBUG_ADLB("adlb_put: rr: %i wt: %i %s\n",
             reserve_rank, work_type, cmd);

  // int ADLB_Put(void *work_buf, int work_len, int reserve_rank,
  //              int answer_rank, int work_type, int work_prio)
  int rc = ADLB_Put(cmd, strlen(cmd)+1, reserve_rank, adlb_rank,
                    work_type, 1);

  assert(rc == ADLB_SUCCESS);
  return TCL_OK;
}

/**
   usage: adlb_get <req_type> <answer_rank>
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

  DEBUG_ADLB("adlb_get: req_type=%i\n", req_type);

  char result[ADLBTCL_CMD_MAX];
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
    puts("ADLB_DONE_BY_EXHAUSTION!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_MORE_WORK ) {
    puts("ADLB_NO_MORE_WORK!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_CURRENT_WORK) {
    puts("ADLB_NO_CURRENT_WORK");
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
    DEBUG_ADLB("adlb_get: %i %s\n", answer_rank, result);

  // Store answer_rank in caller's stack frame
  Tcl_Obj* tcl_answer_rank = Tcl_NewIntObj(answer_rank);
  Tcl_ObjSetVar2(interp, tcl_answer_rank_name, NULL, tcl_answer_rank,
                 EMPTY_FLAG);

  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));
  return TCL_OK;
}

/**
   usage: adlb_create <id> <data>
*/
static int
ADLB_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  long id;
  int length;
  Tcl_GetLongFromObj(interp, objv[1], &id);
  char* s = Tcl_GetStringFromObj(objv[2], &length);
  char data[length+1];
  strncpy(data, s, length);
  data[length] = '\0';
  DEBUG_ADLB("adlb_create: <%li> %s\n", id, data);
  int rc = ADLB_Create(id, data, length+1);
  assert(rc == ADLB_SUCCESS);
  return TCL_OK;
}

/**
   usage: adlb_store <id> <data>
*/
static int
ADLB_Store_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  long id;
  int length;
  Tcl_GetLongFromObj(interp, objv[1], &id);
  char* s = Tcl_GetStringFromObj(objv[2], &length);
  char data[length+1];
  strncpy(data, s, length);
  data[length] = '\0';
  DEBUG_ADLB("adlb_store: length: %i\n", length);
  DEBUG_ADLB("adlb_store: <%li> %s\n", id, data);
  int rc = ADLB_Store(id, data, length+1);

  assert(rc == ADLB_SUCCESS);
  return TCL_OK;
}

/**
   usage: adlb_retrieve <id>
*/
static int
ADLB_Retrieve_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long id;
  Tcl_GetLongFromObj(interp, objv[1], &id);

  int length;
  DEBUG_ADLB("adlb_retrieve: <%li>\n", id);
  int rc = ADLB_Retrieve(id, retrieved, &length);
  assert(rc == ADLB_SUCCESS);

  DEBUG_ADLB("retrieved: %s\n", retrieved);

  Tcl_Obj* result = Tcl_NewStringObj(retrieved, length-1);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
ADLB_Subscribe_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  int subscribed;
  long id;

  Tcl_GetLongFromObj(interp, objv[1], &id);
  int rc = ADLB_Subscribe(id, &subscribed);
  assert(rc == ADLB_SUCCESS);

  Tcl_Obj* result = Tcl_NewIntObj(subscribed);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb_close <id>
   returns list of int ranks that must be notified
*/
static int
ADLB_Close_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long id;
  Tcl_GetLongFromObj(interp, objv[1], &id);

  DEBUG_ADLB("adlb_close: <%li>\n", id);
  int* ranks;
  int count;
  int rc = ADLB_Close(id, &ranks, &count);
  assert(rc == ADLB_SUCCESS);

  Tcl_Obj* result = Tcl_NewListObj(0, NULL);
  for (int i = 0; i < count; i++)
  {
    printf("adding rank: %i\n", ranks[i]);
    Tcl_Obj* o = Tcl_NewIntObj(ranks[i]);
    Tcl_ListObjAppendElement(interp, result, o);
  }
  free(ranks);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
ADLB_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  // sleep(1);
  puts("ADLB finalizing...");
  ADLB_Finalize();
  MPI_Finalize();
  return TCL_OK;
}

/**
   Shorten object creation lines.  "turbine::" namespace is prepended
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
  COMMAND("rank",      ADLB_Rank_Cmd);
  COMMAND("amserver",  ADLB_AmServer_Cmd);
  COMMAND("servers",   ADLB_Servers_Cmd);
  COMMAND("workers",   ADLB_Workers_Cmd);
  COMMAND("put",       ADLB_Put_Cmd);
  COMMAND("get",       ADLB_Get_Cmd);
  COMMAND("create",    ADLB_Create_Cmd);
  COMMAND("store",     ADLB_Store_Cmd);
  COMMAND("retrieve",  ADLB_Retrieve_Cmd);
  COMMAND("subscribe", ADLB_Subscribe_Cmd);
  COMMAND("close",     ADLB_Close_Cmd);
  COMMAND("finalize",  ADLB_Finalize_Cmd);

  return TCL_OK;
}
