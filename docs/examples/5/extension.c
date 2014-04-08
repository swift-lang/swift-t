
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include <tcl.h>

#include "swift-main.h"

static int
swift_main_extension(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj*const objv[])
{
  // Create argc/argv from Tcl objects:
  int argc = objc;
  char** argv = malloc(argc * sizeof(argv));
  for (int i = 0; i < argc; i++)
    argv[i] = Tcl_GetString(objv[i]);

  // Call the user function:
  int rc = swift_main(argc, argv);

  // Return the exit code as the Tcl return code:
  Tcl_Obj* result = Tcl_NewIntObj(rc);
  Tcl_SetObjResult(interp, result);

  // Clean up:
  free(argv);
}

int
Swift_main_Init(Tcl_Interp* interp)
{
  int rc;

  Tcl_PkgProvide(interp, "swift_main", "0.0");

  Tcl_CreateObjCommand(interp,
                       "swift_main_extension", swift_main_extension,
                       NULL, NULL);
  return TCL_OK;
}

