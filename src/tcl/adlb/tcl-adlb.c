
/**
 * TCL extension for ADLB
 *
 * @author wozniak
 * */

#include <assert.h>

#include <tcl.h>
#include <adlb.h>

enum
{ CMDLINE };

static int adlb_rank;

static int am_server, am_debug_server;

/** Max command-line length */
#define ADLBTCL_CMD_MAX 1024

static int
ADLB_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  int argc = 0;
  char** argv = NULL;
  int type_vect[2] = {CMDLINE};

  MPI_Comm app_comm;

  int code;

  code = MPI_Init(&argc, &argv);
  assert(code == MPI_SUCCESS);

  // ADLB_Init(int num_servers, int use_debug_server,
  //           int aprintf_flag, int num_types, int *types,
  //           int *am_server, int *am_debug_server, MPI_Comm *app_comm)
  code = ADLB_Init(1, 0, 0, 1, type_vect,
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

  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("ADLB_SUCCESS", -1), NULL,
                 Tcl_NewIntObj(ADLB_SUCCESS), TCL_GLOBAL_ONLY);

  Tcl_SetObjResult(interp, Tcl_NewIntObj(ADLB_SUCCESS));

  return TCL_OK;
}

static int
ADLB_Rank_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  char result[64];
  sprintf(result, "%i", adlb_rank);
  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));

  return TCL_OK;
}

static int
ADLB_AmServer_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  char result[64];
  sprintf(result, "%i", am_server);
  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));

  return TCL_OK;
}


static int
ADLB_Put_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  assert(objc == 2);

  char* cmd = Tcl_GetString(objv[1]);

  // printf("adlb_put: %s\n", cmd);

  int rc = ADLB_Put(cmd, strlen(cmd)+1, -1, -1,
                    CMDLINE, 1);

  assert(rc == ADLB_SUCCESS);

  return TCL_OK;
}

static int
ADLB_Get_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  char result[ADLBTCL_CMD_MAX];
  int work_prio, work_type, work_handle[ADLB_HANDLE_SIZE], work_len,
    answer_rank;
  int req_types[4];

  req_types[0] = -1;
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
  else if (work_type != CMDLINE) {
    puts("unknown work type!");
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
  }

  printf("adlb_get: %s\n", result);
  fflush(NULL);

  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));
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
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tcladlb_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "ADLB", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  Tcl_CreateObjCommand(interp, "adlb_init", ADLB_Init_Cmd, NULL, NULL);
  Tcl_CreateObjCommand(interp, "adlb_rank", ADLB_Rank_Cmd, NULL, NULL);
  Tcl_CreateObjCommand(interp, "adlb_amserver", ADLB_AmServer_Cmd, NULL, NULL);
  Tcl_CreateObjCommand(interp, "adlb_put", ADLB_Put_Cmd, NULL, NULL);
  Tcl_CreateObjCommand(interp, "adlb_get", ADLB_Get_Cmd, NULL, NULL);
  Tcl_CreateObjCommand(interp, "adlb_finalize", ADLB_Finalize_Cmd, NULL, NULL);
  return TCL_OK;
}
