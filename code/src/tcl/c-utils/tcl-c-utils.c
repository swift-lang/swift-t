
/*
 * tcl-c-utils.c
 *
 *  Created on: Oct 2, 2012
 *      Author: wozniak
 *
 *  Tcl extension for miscellaneous C functions
 */

#include <malloc.h>

#include <tcl.h>

#include "src/tcl/util.h"

#include "tcl-c-utils.h"

static int
c_utils_heapsize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  struct mallinfo s = mallinfo();

  Tcl_Obj* result = Tcl_NewLongObj(s.uordblks);

  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   Shorten object creation lines.  mpe:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "c_utils::" tcl_function, c_function, \
                         NULL, NULL);
/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclcutils_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "c_utils", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  return TCL_OK;
}

void
tcl_c_utils_init(Tcl_Interp* interp)
{
  COMMAND("heapsize", c_utils_heapsize_Cmd);
}
