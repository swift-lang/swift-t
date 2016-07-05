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
 * tcl-groovy.c
 *
 *  Created on: July 7, 2016
 *      Author: spagnuolo
 *
 *  Tcl extension calling into Groovy interpreter from a JVM instance
 */

#include "config.h"

#if HAVE_JVM_SCRIPT==1
// This file includes the Python header
// It is auto-generated at configure time
#include "src/tcl/swift-lang-swift-t-jvm-engine-master/swift-jvm.h"
#endif

// #define _GNU_SOURCE // for asprintf()
#include <stdio.h>

#include <tcl.h>

#include <list.h>
#include "src/util/debug.h"
#include "src/tcl/util.h"

#include "tcl-groovy.h"

#if HAVE_JVM_SCRIPT==1

static int
Groovy_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(3);
  // A chunk of Groovy code that does not return anything:
  char* code = Tcl_GetString(objv[1]);
  // A chunk of Groovy code that returns a string to Swift:
  char* return_expression = Tcl_GetString(objv[2]);

  // The string result from Groovy: Default is empty string
  char* s = "";
  int   length = 0;
  bool  empty = true;

  s = groovy(code);

  Tcl_Obj* result = Tcl_NewStringObj(s, length);
  if (!empty)
    free(s);
 
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#else // JVM SCRIPT disabled

static int
Groovy_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_tcl_condition_failed(interp, objv[0],
                       "Turbine not compiled with JVM scripting support");
  return TCL_ERROR;
}

#endif

/**
   Shorten object creation lines.  r:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "groovy::" tcl_function, c_function, \
                         NULL, NULL);
/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclgroovy_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "groovy", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  return TCL_OK;
}

void
tcl_groovy_init(Tcl_Interp* interp)
{
  COMMAND("eval", Groovy_Eval_Cmd);
}