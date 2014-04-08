#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include <tcl.h>

#include "thf-ribo.h"

static int
thfribo_main_extension(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj*const objv[])
{
  // Create argc/argv from Tcl objects:
  int argc = objc;
  char** argv = (char**) malloc(argc * sizeof(argv));
  for (int i = 0; i < argc; i++)
    argv[i] = Tcl_GetString(objv[i]);

  // Call the user function:
  int rc = thfribo_main(argc, argv);

  // Return the exit code as the Tcl return code:
  Tcl_Obj* result = Tcl_NewIntObj(rc);
  Tcl_SetObjResult(interp, result);

  // Clean up:
  free(argv);
}

extern "C"
int
Thfribo_main_Init(Tcl_Interp* interp)
{
  int rc;

  Tcl_PkgProvide(interp, "thfribo_main", "0.0");

  Tcl_CreateObjCommand(interp,
                       "thfribo_main_extension", thfribo_main_extension,
                       NULL, NULL);
  return TCL_OK;
}

