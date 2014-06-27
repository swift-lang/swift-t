/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

/*
 * tcl-r.c
 *
 *  Created on: May 22, 2013
 *      Author: wozniak
 *
 *  Tcl extension calling into Python interpreter
 */

#include "config.h"

#define _GNU_SOURCE // for asprintf()
#include <stdio.h>

#include <tcl.h>

#if HAVE_R==1
#include <Rinternals.h>
#include <Rembedded.h>
#include <R_ext/Parse.h>
#include <R_ext/Print.h>
#endif

#include <list.h>

#include "src/tcl/util.h"

#include "tcl-r.h"

#if HAVE_R==1

static int
handle_r_error(const char* msg)
{
  printf("R ERROR: %s\n", msg);
  return TCL_ERROR;
}

static bool r_initialized = false;

/**
   Note that we are not allowed to re-initialize R
   @return R return code: 1=success, 0=failure
 */
static int
init_r(void)
{
  int rc = 1;
  if (! r_initialized)
  {
    // setenv("R_HOME", "/usr/lib/R", 1);
    char* Rargs[] = { "R", "--no-save", "--silent" };
    rc = Rf_initEmbeddedR(3, Rargs);
    r_initialized = true;
  }
  return rc;
}

static int
eval_r_line(Tcl_Interp* interp, Tcl_Obj* const objv[],
            const char* cmd, SEXP* result)
{
  ParseStatus status;
  SEXP expr = Rf_mkString(cmd);
  TCL_CONDITION(expr != NULL, "Bad R code[type 1]: %s", cmd);
  SEXP code = R_ParseVector(expr, 1, &status, R_NilValue);
  TCL_CONDITION(code != NULL, "Bad R code[type 2]: %s", cmd);
  TCL_CONDITION(status == PARSE_OK, "Bad R code [type 3]: %s", cmd);
  if (TYPEOF(code) == EXPRSXP)
  {
    *result = Rf_eval(VECTOR_ELT(code, 0), R_GlobalEnv);
    // printf("value: ");
    // Rf_PrintValue(*result);
  }
  else
    TCL_RETURN_ERROR("Bad R code [type 4]: %s", cmd);
  return TCL_OK;
}

/**
   Currently cannot finalize R because we cannot re-initialize R
 */
static void
finalize_r(void)
{
  // Rf_endEmbeddedR(0);
}

/**
   See note in R's printutils.c
*/
const char *Rf_EncodeElement(SEXP x, int indx, int quote, char dec);

/**
   @param result: Store result pointer here
   @return Tcl error code
 */
static int
r_eval(Tcl_Interp* interp, Tcl_Obj* const objv[],
       const char* code, Tcl_Obj** result)
{
  SEXP x;
  int rc;

  rc = init_r();
  if (!rc)
    return handle_r_error("Could not initialize R!");

  struct list* lines = list_split_lines(code);
  char* expression = NULL;
  for (struct list_item* item = lines->head; item; item = item->next)
  {
    if (item->next == NULL)
    {
      // This is the expression that returns the string
      expression = item->data;
      break;
    }
    char* command = item->data;
    rc = eval_r_line(interp, objv, command, &x);
    TCL_CHECK(rc);
  }

  // The string from R:
  const char* s;
  rc = eval_r_line(interp, objv, expression, &x);
  TCL_CHECK(rc);
  // This line gives a warning because it is not in any R header:
  s = Rf_EncodeElement(x, 0, 0, '.');
  // printf("s: %s\n", s);

  *result = Tcl_NewStringObj(s, -1);

  finalize_r();
  list_destroy(lines);

  return TCL_OK;
}

static int
R_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(2);
  char* code = Tcl_GetString(objv[1]);
  Tcl_Obj* result;
  int rc = r_eval(interp, objv, code, &result);
  TCL_CHECK(rc);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#else // R disabled

static int
R_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  tcl_condition_failed(interp, objv[0],
                       "Turbine not compiled with R support");
  return TCL_ERROR;
}

#endif

/**
   Shorten object creation lines.  r:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "r::" tcl_function, c_function, \
                         NULL, NULL);
/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclr_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "r", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  return TCL_OK;
}

void
tcl_r_init(Tcl_Interp* interp)
{
  COMMAND("eval", R_Eval_Cmd);
}
