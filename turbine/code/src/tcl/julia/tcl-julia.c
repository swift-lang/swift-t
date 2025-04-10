
/*
 * tcl-julia.c
 *
 *  Created on: Jun 2, 2014
 *      Author: wozniak
 */

#include "config.h"

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

#include <julia/julia.h>

static bool initialized = false;

static void inline
julia_initialize(void)
{
  jl_init();
  // JL_SET_STACK_BASE;
  initialized = true;
}

static int
julia_eval(const char* code, Tcl_Obj** result)
{
  if (!initialized) julia_initialize();
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
  int rc = julia_eval(code, &result);
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
