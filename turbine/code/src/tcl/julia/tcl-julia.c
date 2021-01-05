
/*
 * tcl-julia.c
 *
 *  Created on: Jun 2, 2014
 *      Author: wozniak
 */

#include "config.h"

#include <dlfcn.h>
#include <stdio.h>

#include <tcl.h>
#ifdef INLINE
// Julia also defines INLINE
#undef INLINE
#endif

#include <exm-string.h>

#include "src/util/debug.h"
#include "src/tcl/util.h"

#include "src/tcl/julia/tcl-julia.h"

#if HAVE_JULIA == 1

#include <julia.h>

static bool initialized = false;

static inline int
julia_initialize(Tcl_Interp* interp, Tcl_Obj* const objv[])
{
  if (initialized) return TCL_OK;
  DEBUG_TCL_TURBINE("julia initialize...");
  void* rc = dlopen("libjulia.so", RTLD_NOW | RTLD_GLOBAL);
  DEBUG_TCL_TURBINE("dlopen(libjulia.so): OK");
  TCL_CONDITION(rc != NULL, "Turbine-Julia: "
                "could not dlopen(libjulia.so)!");
  jl_init();
  initialized = true;
  DEBUG_TCL_TURBINE("julia initialized.");
  return TCL_OK;
}

static int
julia_eval(Tcl_Interp* interp, Tcl_Obj* const objv[],
           const char* code, Tcl_Obj** result)
{
  int rc = julia_initialize(interp, objv);
  TCL_CONDITION(rc == TCL_OK, "Turbine-Julia: "
                "could not initialize!");
  int length = strlen(code);
  char assignment[length + 32];
  DEBUG_TCL_TURBINE("julia evaluation:\n%s", code);
  sprintf(assignment, "_t = %s", code);
  // sprintf(assignment, "_t = sqrt(2.0)");
  jl_eval_string(assignment);
  jl_value_t* value = jl_eval_string("\"$_t\n\"");
  char* s = jl_string_data(value);
  chomp(s);
  DEBUG_TCL_TURBINE("julia result: %s", s);
  *result = Tcl_NewStringObj(s, -1);
  return TCL_OK;
}

static int
Julia_Eval_Cmd(ClientData cdata, Tcl_Interp* interp,
                int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(2);
  char* code = Tcl_GetString(objv[1]);
  Tcl_Obj* result = NULL;
  int rc = julia_eval(interp, objv, code, &result);
  TCL_CHECK(rc);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#else // Julia disabled

static int
Julia_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  // TODO: Throw TURBINE ERROR for cleaner handling (#601)
  turbine_tcl_condition_failed(interp, objv[0],
                       "Turbine not compiled with Julia support");
  return TCL_ERROR;
}

#endif

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tcljulia_Init(Tcl_Interp* interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "python", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  return TCL_OK;
}

#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "julia::" tcl_function, c_function, \
                         NULL, NULL);

void
tcl_julia_init(Tcl_Interp* interp)
{
  COMMAND("eval", Julia_Eval_Cmd);
}
