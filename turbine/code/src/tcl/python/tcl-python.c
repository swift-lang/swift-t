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
// This file includes the Python header
// It is auto-generated at configure time
#include "src/tcl/python/turbine-python-version.h"
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
handle_python_exception(bool exceptions_are_errors)
{
  printf("\n");
  printf("PYTHON EXCEPTION:\n");

  #if PY_MAJOR_VERSION >= 3

  PyObject *exc,*val,*tb;
  PyErr_Fetch(&exc,&val,&tb);
  PyObject_Print(exc, stdout, Py_PRINT_RAW);
  printf("\n");
  PyObject_Print(val, stdout, Py_PRINT_RAW);
  printf("\n");

  #else // Python 2

  PyErr_Print();

  #endif

  if (exceptions_are_errors)
    return TCL_ERROR;
  return TCL_OK;
}

static int
handle_python_non_string(PyObject* o)
{
  printf("python: expression did not return a string!\n");
  fflush(stdout);
  printf("python: expression evaluated to: ");
  PyObject_Print(o, stdout, 0);
  return TCL_ERROR;
}

static PyObject* main_module = NULL;
static PyObject* main_dict   = NULL;
static PyObject* local_dict  = NULL;

static bool initialized = false;

static int python_init(void)
{
  if (initialized) return TCL_OK;
  DEBUG_TCL_TURBINE("python: initializing...");
  Py_InitializeEx(1);
  main_module  = PyImport_AddModule("__main__");
  if (main_module == NULL) return handle_python_exception(true);
  main_dict = PyModule_GetDict(main_module);
  if (main_dict == NULL) return handle_python_exception(true);
  local_dict = PyDict_New();
  if (local_dict == NULL) return handle_python_exception(true);
  initialized = true;
  return TCL_OK;
}

static void python_finalize(void);

static char* python_result_default   = "__NOTHING__";
static char* python_result_exception = "__EXCEPTION__";

#define EXCEPTION(ee)                                            \
  {                                                               \
    *output = Tcl_NewStringObj(python_result_exception, -1);      \
    return handle_python_exception(ee);                           \
  }

/**
   @param persist: If true, retain the Python interpreter,
                   else finalize it
   @param code: The multiline string of Python code.
                The last line is evaluated to the returned result
   @param output: Store result pointer here
   @return Tcl error code
 */
static int
python_eval(bool persist, bool exceptions_are_errors,
            const char* code, const char* expression,
            Tcl_Obj** output)
{
  int rc;
  char* result = python_result_default;

  // Initialize:
  rc = python_init();
  TCL_CHECK(rc);

  // Execute code:
  DEBUG_TCL_TURBINE("python: code: %s", code);

  PyRun_String(code, Py_file_input, main_dict, local_dict);
  if (PyErr_Occurred())
    EXCEPTION(exceptions_are_errors);

  // Evaluate expression:
  DEBUG_TCL_TURBINE("python: expression: %s", expression);
  PyObject* o = PyRun_String(expression, Py_eval_input, main_dict, local_dict);
  if (o == NULL)
    EXCEPTION(exceptions_are_errors);

  // Convert Python result to C string, then to Tcl string:
  rc = PyArg_Parse(o, "s", &result);
  if (rc != 1) return handle_python_non_string(o);
  DEBUG_TCL_TURBINE("python: result: %s\n", result);
  *output = Tcl_NewStringObj(result, -1);

  // Clean up and return:
  Py_DECREF(o);
  if (!persist) python_finalize();
  return TCL_OK;
}

static void
python_finalize(void)
{
  Py_Finalize();
  initialized = false;
}

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);
  int rc;
  int persist;
  int exceptions_are_errors;
  rc = Tcl_GetBooleanFromObj(interp, objv[1], &persist);
  rc = Tcl_GetBooleanFromObj(interp, objv[2], &exceptions_are_errors);
  TCL_CHECK_MSG(rc, "first arg should be integer!");
  char* code = Tcl_GetString(objv[3]);
  char* expression = Tcl_GetString(objv[4]);
  Tcl_Obj* result = NULL;
  rc = python_eval(persist, exceptions_are_errors,
                   code, expression, &result);
  TCL_CHECK(rc);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#else // Python disabled

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);
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
