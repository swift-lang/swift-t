
/**
 * Tcl extension for MPE
 *
 * @author wozniak
 * */

#include <assert.h>

#include <tcl.h>
#include <mpe.h>
#include <adlb.h>

#include "src/tcl/util.h"
#include "src/util/debug.h"

/*
static int
MPE_Get_IDs_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  int n1, n2;

  MPE_Log_get_state_eventIDs(&n1, &n2);

  Tcl_Obj* items[2];
  items[0] = Tcl_NewIntObj(n1);
  items[1] = Tcl_NewIntObj(n2);
  Tcl_Obj* result = Tcl_NewListObj(2, items);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
MPE_Describe_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);

  int n1, n2;
  char* state;
  char* color;
  int error;
  error = Tcl_GetIntFromObj(interp, objv[1], &n1);
  TCL_CHECK(error);
  error = Tcl_GetIntFromObj(interp, objv[2], &n2);
  TCL_CHECK(error);
  state = Tcl_GetStringFromObj(objv[3], NULL);
  assert(state);
  color = Tcl_GetStringFromObj(objv[4], NULL);
  assert(color);

  MPE_Describe_state(n1, n2, state, color);

  return TCL_OK;
}
*/

static const char* MPE_CHOOSE_COLOR = "MPE_CHOOSE_COLOR";

/**
  usage: mpe::create <symbol> => [ list start-ID stop-ID ]
*/
static int
MPE_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  char* state = Tcl_GetStringFromObj(objv[1], NULL);
  assert(state);

  int event1, event2;
  // This is typically the first call to MPE
  // A SEGV here probably means that ADLB was not configured with MPE
  MPE_Log_get_state_eventIDs(&event1, &event2);
  MPE_Describe_state(event1, event2, state, MPE_CHOOSE_COLOR);

  Tcl_Obj* items[2];
  items[0] = Tcl_NewIntObj(event1);
  items[1] = Tcl_NewIntObj(event2);
  Tcl_Obj* result = Tcl_NewListObj(2, items);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: mpe::log <event-ID> [<message>]
*/
static int
MPE_Log_Cmd(ClientData cdata, Tcl_Interp *interp,
        int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 2 || objc == 3),
                "requires 1 or 2 args!");

  int event;
  int error = Tcl_GetIntFromObj(interp, objv[1], &event);
  TCL_CHECK(error);

  char* bytes = NULL;
  if (objc == 3)
    bytes = Tcl_GetStringFromObj(objv[2], NULL);

  MPE_Log_event(event, 0, bytes);
  return TCL_OK;
}

/**
   Shorten object creation lines.  mpe:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "mpe::" tcl_function, c_function, \
                         NULL, NULL);
/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclmpe_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "MPE", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  //  COMMAND("get_ids", MPE_Get_IDs_Cmd);
  // COMMAND("describe", MPE_Describe_Cmd);
  COMMAND("create", MPE_Create_Cmd);
  COMMAND("log", MPE_Log_Cmd);

  return TCL_OK;
}
