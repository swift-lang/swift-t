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
#ifdef HAVE_SYS_PARAM_H
// Python will try to redefine this:
#undef HAVE_SYS_PARAM_H
#endif

#if HAVE_PYTHON==1
#include "Python.h"
#endif

#include <dlfcn.h>

#include <stdio.h>

#include <tcl.h>

#include "src/util/debug.h"
#include "src/tcl/util.h"

#include "tcl-python.h"

#if HAVE_PYTHON==1

/** @return A Tcl return code */
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

static int
python_init(void)
{
/* Loading python library symbols so that dynamic extensions don't throw symbol not found error.
           Ref Link: http://stackoverflow.com/questions/29880931/importerror-and-pyexc-systemerror-while-embedding-python-script-within-c-for-pam
        */
  char str_python_lib[17];
#ifdef _WIN32
  sprintf(str_python_lib, "libpython%d.%d.dll", PY_MAJOR_VERSION, PY_MINOR_VERSION);
#elif defined __unix__
  sprintf(str_python_lib, "libpython%d.%d.so", PY_MAJOR_VERSION, PY_MINOR_VERSION);
#elif defined __APPLE__
  sprintf(str_python_lib, "libpython%d.%d.dylib", PY_MAJOR_VERSION, PY_MINOR_VERSION);
#endif
  dlopen(str_python_lib, RTLD_NOW | RTLD_GLOBAL);

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

#define EXCEPTION(ee)                           \
  {                                             \
    *result = python_result_exception;          \
    return handle_python_exception(ee);         \
  }

/**
   @param persist: If true, retain the Python interpreter,
                   else finalize it
   @param exceptions_are_errors: If true, abort on Python exception
   @param code: The multiline string of Python code.
   @param expr: A Python expression to be evaluated to the returned result
   @param result: Store result pointer here
   @return Tcl return code
 */
static int
python_eval(bool persist, bool exceptions_are_errors,
            const char* code, const char* expr, char** result)
{
  int rc;
  char* s = python_result_default;

  // Initialize:
  rc = python_init();
  TCL_CHECK(rc);

  // Execute code:
  DEBUG_TCL_TURBINE("python: code: %s", code);
  PyRun_String(code, Py_file_input, main_dict, local_dict);
  if (PyErr_Occurred()) EXCEPTION(exceptions_are_errors);

  // Evaluate expression:
  DEBUG_TCL_TURBINE("python: expr: %s", expr);
  PyObject* o = PyRun_String(expr, Py_eval_input,
                             main_dict, local_dict);
  if (o == NULL) EXCEPTION(exceptions_are_errors);

  // Convert Python result to C string
  rc = PyArg_Parse(o, "s", &s);
  if (rc != 1) return handle_python_non_string(o);
  // DEBUG_TCL_TURBINE
  printf("python: result: %s\n", s);
  *result = strdup(s);

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
  TCL_CHECK_MSG(rc, "python: argument persist should be integer!");
  rc = Tcl_GetBooleanFromObj(interp, objv[2], &exceptions_are_errors);
  TCL_CHECK_MSG(rc,
                "python: argument exceptions_are_errors should be integer!");
  char* code = Tcl_GetString(objv[3]);
  char* expr = Tcl_GetString(objv[4]);
  char* output = NULL;
  rc = python_eval(persist, exceptions_are_errors,
                   code, expr, &output);
  TCL_CHECK(rc);
  printf("python: output: %s\n", output);
  Tcl_Obj* result = Tcl_NewStringObj(output, -1);
  Tcl_SetObjResult(interp, result);
  free(output);
  return TCL_OK;
}

char*
python_parallel_persist(MPI_Comm comm, char* code, char* expr)
{
  int task_rank, task_size;
  MPI_Comm_rank(comm, &task_rank);
  MPI_Comm_size(comm, &task_size);
  printf("In ppp(): rank: %i/%i\n", task_rank, task_size);
  printf("code: %s\n", code);
  printf("expr: %s\n", expr);


  int rc;
  rc = python_init();
  assert(rc == TCL_OK);

  PyRun_String("print(\"warmup\")", Py_file_input, main_dict, local_dict);
  if (PyErr_Occurred()) { printf("ERROR\n"); }
  printf("warmup ok.\n");   fflush(stdout);

  long long int task_comm_int = (long long int) comm;
  PyObject* globals = PyEval_GetGlobals();
  PyObject* key = PyBytes_FromString("task_comm");
  PyObject* task_comm = PyLong_FromLong(task_comm_int);
  PyObject_SetItem(globals, key, task_comm);

  printf("comm ok.\n");   fflush(stdout);

  // PyObject* globals = PyEval_GetGlobals();
  char* output;
  rc = python_eval(true, true, code, expr, &output);
  if (rc != TCL_OK)
  {
    printf("python parallel task failed!\n");
    exit(EXIT_FAILURE);
  }

  printf("eval ok.\n");   fflush(stdout);

  // Py_DECREF(task_comm);
  // Py_DECREF(globals);
  MPI_Comm_free(&comm);
  if (task_rank == 0)
    // Return a real value
    return output;
  // Return a placeholder
  free(output);
  return NULL;
}


#else // Python disabled

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp
                int objc, Tcl_Obj *const objv[])
{
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
