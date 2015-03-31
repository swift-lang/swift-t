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
 * tcl-python.c
 *
 *  Created on: May 22, 2013
 *      Author: wozniak
 *
 *  Tcl extension calling into Python interpreter
 */

#include "config.h"

#if HAVE_PYTHON==1
#include <python2.7/Python.h>
#endif

// #define _GNU_SOURCE // for asprintf()
#include <stdio.h>

#include <tcl.h>

#include <list.h>
#include "src/util/debug.h"
#include "src/tcl/util.h"

#include "tcl-python.h"

#if HAVE_PYTHON==1

static int
handle_python_exception()
{
  printf("\n");
  printf("PYTHON EXCEPTION:\n");
  PyErr_Print();
  return TCL_ERROR;
}

/**
   @param result: Store result pointer here
   @return Tcl error code
 */
static int
python_eval(const char* code, Tcl_Obj** result)
{
  char* s = "NOTHING";

  Py_InitializeEx(1);
  int rc;

  PyObject* main_module  = PyImport_AddModule("__main__");
  if (main_module == NULL) return handle_python_exception();
  PyObject* main_dict  = PyModule_GetDict(main_module);
  if (main_dict == NULL) return handle_python_exception();

  struct list* lines = list_split_lines(code);

  // Handle setup lines:
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
    DEBUG_TCL_TURBINE("python: command: %s", command);
    rc = PyRun_SimpleString(command);
    if (rc != 0)
      return handle_python_exception();
    DEBUG_TCL_TURBINE("python: command done.");
  }

  // Handle value expression:
  if (expression != NULL)
  {
    DEBUG_TCL_TURBINE("python: expression: %s", expression);
    PyObject* o = PyRun_String(expression, Py_eval_input,
                               main_dict, main_dict);
    if (o == NULL) return handle_python_exception();
    rc = PyArg_Parse(o, "s", &s);
    assert(s != NULL);
    DEBUG_TCL_TURBINE("python: result: %s\n", s);
    *result = Tcl_NewStringObj(s, -1);
  }

  list_destroy(lines);

  return TCL_OK;
}

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  char* code = Tcl_GetString(objv[1]);
  Tcl_Obj* result = NULL;
  int rc = python_eval(code, &result);
  TCL_CHECK(rc);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#else // Python disabled

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  return turbine_user_errorv(interp,
                   "Turbine not compiled with Python support");
}

#endif


/**
   Called when Tcl loads this extension
 */
int
Tclpython_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "python", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  return TCL_OK;
}

/**
   Shorten object creation lines.  python:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "python::" tcl_function, c_function, \
                         NULL, NULL);

void
tcl_python_init(Tcl_Interp* interp)
{
  COMMAND("eval", Python_Eval_Cmd);
}
