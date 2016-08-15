/*
 * Copyright 2016 University of Chicago and Argonne National Laboratory
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
 * tcl-jvm.c
 *
 *  Created on: July 7, 2016
 *      Author: spagnuolo
 *
 *  Tcl extension calling into JVM-based language interpreters
 */

#include "config.h"

#if HAVE_JVM_SCRIPT==1
// This file includes the JVM script header
// It is auto-generated at configure time
// TODO: Make this directory location configurable
#include "swift-t-jvm/src/swift-jvm.h"
#endif

// #define _GNU_SOURCE // for asprintf()
#include <stdio.h>
#include <tcl.h>
#include <string.h>
#include <list.h>
#include "src/util/debug.h"
#include "src/tcl/util.h"
#include "tcl-jvm.h"

#if HAVE_JVM_SCRIPT==1

static int
Clojure_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(3);
  // A chunk of Clojure code that does not return anything:
  char* code = Tcl_GetString(objv[1]);
    // A chunk of Clojure code that returns a string:
  char* expr = Tcl_GetString(objv[2]);

  clojure(code);

  // The string result from Clojure: Default is empty string

  char* s = clojure(expr);
  TCL_CONDITION(s != NULL, "clojure code failed: %s", code);

  Tcl_Obj* result = Tcl_NewStringObj(s, strlen(s));
  if (strlen(s)>0)
    free(s);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Groovy_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(3);
  // A chunk of Groovy code that does not return anything:
  char* code = Tcl_GetString(objv[1]);
    // A chunk of Groovy code that returns a string:
  char* expr = Tcl_GetString(objv[2]);

  printf("calling groovy...\n");
  groovy(code);

  // The string result from Groovy: Default is empty string

  char* s = groovy(expr);
  TCL_CONDITION(s != NULL, "groovy code failed: %s", code);

  Tcl_Obj* result = Tcl_NewStringObj(s, strlen(s));
  if (strlen(s)>0)
    free(s);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
JavaScript_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(3);
  // A chunk of JavaScript code that does not return anything:
  char* code = Tcl_GetString(objv[1]);
    // A chunk of JavaScript code that returns a string:
  char* expr = Tcl_GetString(objv[2]);

  javascript(code);

  // The string result from JavaScript: Default is empty string

  char* s = javascript(expr);
  TCL_CONDITION(s != NULL, "javascript code failed: %s", code);

  Tcl_Obj* result = Tcl_NewStringObj(s, strlen(s));
  if (strlen(s)>0)
    free(s);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Scala_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(3);
  // A chunk of Scala code that does not return anything:
  char* code = Tcl_GetString(objv[1]);
    // A chunk of Scala code that returns a string:
  char* expr = Tcl_GetString(objv[2]);

  scala(code);

  // The string result from Scala: Default is empty string

  char* s = scala(expr);
  TCL_CONDITION(s != NULL, "scala code failed: %s", code);

  Tcl_Obj* result = Tcl_NewStringObj(s, strlen(s));
  if (strlen(s)>0)
    free(s);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#else // JVM SCRIPT disabled

static int
Clojure_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_tcl_condition_failed(interp, objv[0],
                       "Turbine not compiled with JVM scripting support");
  return TCL_ERROR;
}

static int
Groovy_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_tcl_condition_failed(interp, objv[0],
                       "Turbine not compiled with JVM scripting support");
  return TCL_ERROR;
}

static int
JavaScript_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_tcl_condition_failed(interp, objv[0],
                       "Turbine not compiled with JVM scripting support");
  return TCL_ERROR;
}

static int
Scala_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_tcl_condition_failed(interp, objv[0],
                       "Turbine not compiled with JVM scripting support");
  return TCL_ERROR;
}

#endif

/**
   Shorten object creation lines.  jvm:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "jvm::" tcl_function, c_function, \
                         NULL, NULL);
/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tcljvm_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "jvm", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  return TCL_OK;
}

void
tcl_jvm_init(Tcl_Interp* interp)
{
  COMMAND("clojure",    Clojure_Eval_Cmd);
  COMMAND("groovy",     Groovy_Eval_Cmd);
  COMMAND("javascript", JavaScript_Eval_Cmd);
  COMMAND("scala",      Scala_Eval_Cmd);
}
