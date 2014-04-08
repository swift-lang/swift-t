#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <tcl.h>

#include "ex2.h"

static int leaf_main_extension(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj*const objv[]) {
  // Create argc/argv from Tcl objects:
  int argc = objc;
  char** argv = (char**) malloc(argc * sizeof(argv));
  for (int i = 0; i < argc; i++)
    argv[i] = Tcl_GetString(objv[i]);

  // Call the user function:
  int rc = leaf_main(argc, argv);

  // Return the exit code as the Tcl return code:
  Tcl_Obj* result = Tcl_NewIntObj(rc);
  Tcl_SetObjResult(interp, result);

  // Clean up:
  free(argv);
}

int Userlib_Init(Tcl_Interp* interp) {
  int rc;

  Tcl_PkgProvide(interp, "leaf_main", "0.0");

  Tcl_CreateObjCommand(interp,
                       "leaf_main_extension", leaf_main_extension,
                       NULL, NULL);
  return TCL_OK;
}

