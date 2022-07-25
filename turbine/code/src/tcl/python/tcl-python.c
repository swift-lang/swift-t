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
#include <stdlib.h>

#include <mpi.h>
#include <tcl.h>

#include <tools.h>

#include "src/util/debug.h"
#include "src/tcl/util.h"

#include "tcl-python.h"

#if HAVE_PYTHON==1

static int   handle_python_exception(bool exceptions_are_errors);
static char* handle_python_exception_parallel(void);
static int   handle_python_non_string(PyObject* o);

static PyObject* main_module = NULL;
static PyObject* main_dict   = NULL;
static PyObject* local_dict  = NULL;

static bool initialized = false;

static void python_finalize(void);

static char* python_result_default   = "__NOTHING__";
static char* python_result_exception = "__EXCEPTION__";

/** 1 on error, 0 on success */
static int python_parallel_error_code = 0;
static char python_parallel_error_string[4096];

#if   HAVE_MPI_IMPL_MPICH
static char* mpi_impl = "MPICH";
#elif HAVE_MPI_IMPL_OPENMPI
static char* mpi_impl = "OpenMPI";
#else
#error "Must specify MPI_IMPL!"
#endif

#define EXCEPTION(ee)                           \
  {                                             \
    *result = strdup(python_result_exception);  \
    return handle_python_exception(ee);         \
  }

static int
python_init(void)
{
  if (initialized) return TCL_OK;

  /* Load Python library symbols so that dynamic extensions
     don't throw symbol not found error.
     Cf. http://stackoverflow.com/questions/29880931/importerror-and-pyexc-systemerror-while-embedding-python-script-within-c-for-pam
   */
  char python_lib_name[32];
#ifdef _WIN32
  sprintf(python_lib_name, "lib%s.dll", PYTHON_NAME);
#elif defined __unix__
  sprintf(python_lib_name, "lib%s.so", PYTHON_NAME);
#elif defined __APPLE__
  sprintf(python_lib_name, "lib%s.dylib", PYTHON_NAME);
#endif
  dlopen(python_lib_name, RTLD_NOW | RTLD_GLOBAL);

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
  DEBUG_TCL_TURBINE("python: result: %s\n", s);
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
  //  printf("In ppp(): rank: %i/%i\n", task_rank, task_size);
  //  printf("code: %s\n", code);
  //  printf("expr: %s\n", expr);

  int rc;

  // Initialize Python (if needed):
  rc = python_init();
  valgrind_assert_msg(rc == TCL_OK, "Could not initialize Python!");

  // Convert the MPI_Comm to a big integer (MPICH or OpenMPI)
  long long int task_comm_int = (long long int) comm;

  // Run some Python code to setup the mpi4py communicator:
  char setup[256];
  memset(setup, '\0', 256);
  sprintf(setup, "import turbine_helpers as TH");
  PyRun_String(setup, Py_file_input, main_dict, local_dict);
  if (PyErr_Occurred()) return handle_python_exception_parallel();
  sprintf(setup, "TH.task_comm = %lli", task_comm_int);
  // printf("setup: %s\n", setup); fflush(stdout);
  PyRun_String(setup, Py_file_input, main_dict, local_dict);
  sprintf(setup, "TH.mpi_impl = \"%s\"", mpi_impl);
  PyRun_String(setup, Py_file_input, main_dict, local_dict);

  // Run the user Python code:
  char* output;
  rc = python_eval(true, true, code, expr, &output);
  // Set the error markers for later access:
  if (rc == TCL_OK)
  {
    python_parallel_error_code = 0;
    strcpy(python_parallel_error_string, "OK");
  }
  else
  {
    printf("python parallel task failed!\n");
    python_parallel_error_code = 1;
    strcpy(python_parallel_error_string, output);
  }

  // Free the new MPI_Comm
  MPI_Comm_free(&comm);

  // Return the result to SWIG:
  if (task_rank == 0)
    return output;

  // We are not rank 0: free and return
  free(output);
  return NULL;
}

/** @return A Tcl return code */
static int
handle_python_non_string(PyObject* o)
{
  printf("python: expression did not return a string!\n");
  fflush(stdout);
  printf("python: expression evaluated to: ");
  PyObject_Print(o, stdout, 0);
  return TCL_ERROR;
}

/** @return A Tcl return code */
static int
handle_python_exception(bool exceptions_are_errors)
{
  printf("\n");
  printf("PYTHON EXCEPTION:\n");
  fflush(stdout);

  #if PY_MAJOR_VERSION >= 3
  int size = 4096;
  char buffer1[size];
  char buffer2[size];
  memset(buffer1, 0, size);
  FILE* errs = fmemopen(buffer1, size, "r+");

  PyObject *exc,*val,*tb;
  PyErr_Fetch(&exc, &val, &tb);
  PyObject_Print(exc, errs, Py_PRINT_RAW);
  fprintf(errs, "\n");
  PyObject_Print(val, errs, Py_PRINT_RAW);
  fprintf(errs, "\n");

  rewind(errs);
  while (fgets(buffer2, size, errs) != NULL)
  {
    int n = strlen(buffer2);
    if (n == 0) continue;
    printf("EXCEPTION: ");
    fwrite(buffer2, 1, n, stdout);
    fflush(stdout);
  }
  fclose(errs);

  #else // Python 2

  PyErr_Print();

  #endif

  if (exceptions_are_errors)
    return TCL_ERROR;
  return TCL_OK;
}

static char*
handle_python_exception_parallel()
{
  printf("\n");
  printf("PYTHON PARALLEL EXCEPTION:\n");
  fflush(stdout);

  python_parallel_error_code = 1;
  strcpy(python_parallel_error_string, "EXCEPTION!");

  #if PY_MAJOR_VERSION >= 3
  int size = 4096;
  char buffer1[size];
  char buffer2[size];
  memset(buffer1, 0, size);
  FILE* errs = fmemopen(buffer1, size, "r+");

  PyObject *exc,*val,*tb;
  PyErr_Fetch(&exc, &val, &tb);
  PyObject_Print(exc, errs, Py_PRINT_RAW);
  fprintf(errs, "\n");
  PyObject_Print(val, errs, Py_PRINT_RAW);
  fprintf(errs, "\n");

  rewind(errs);
  while (fgets(buffer2, size, errs) != NULL)
  {
    int n = strlen(buffer2);
    if (n == 0) continue;
    printf("EXCEPTION: ");
    fwrite(buffer2, 1, n, stdout);
    fflush(stdout);
  }
  fclose(errs);

  #else // Python 2

  PyErr_Print();

  #endif

  // Return this to SWIG:
  return strdup("PYTHON PARALLEL EXCEPTION (see above)");
}

/** For SWIG- Check error status */
int
python_parallel_error_status()
{
  return python_parallel_error_code;
}

/** For SWIG- Obtain human-readable error message */
char*
python_parallel_error_message()
{
  printf("error_message(): %s\n", python_parallel_error_string);
  return strdup(python_parallel_error_string);
}

#else // HAVE_PYTHON==0 // Python disabled

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return turbine_user_errorv(interp,
                     "Turbine not compiled with Python support");
}

char*
python_parallel_persist(MPI_Comm comm, char* code, char* expr)
{
  int task_rank, task_size;
  MPI_Comm_rank(comm, &task_rank);
  MPI_Comm_size(comm, &task_size);
  printf("python_parallel_persist: "
         "Turbine not compiled with Python support");
  if (task_rank == 0)
    return strdup("__ERROR__");
  return NULL;
}

int
python_parallel_error_status()
{
  return 1;
}

char*
python_parallel_error_message()
{
  return strdup("__DISABLED__");
}

#endif // HAVE_PYTHON


/** Called when Tcl loads this extension */
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
