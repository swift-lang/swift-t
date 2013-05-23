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

#include <tcl.h>

#if HAVE_PYTHON==1
#include <python2.7/Python.h>
#endif

#include "src/tcl/util.h"

#include "tcl-python.h"

#if HAVE_PYTHON==1
static Tcl_Obj*
python_eval(char* code)
{
  char* s;
  Py_Initialize();
  PyObject *globals = Py_BuildValue("{}");
  PyObject *locals = Py_BuildValue("{}");
  PyObject* o = PyRun_String(code, Py_eval_input, globals, locals);
  PyArg_Parse(o, "s", &s);
  Tcl_Obj* result = Tcl_NewStringObj(s, -1);
  return result;
}

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  char* code = Tcl_GetString(objv[1]);
  Tcl_Obj* result = python_eval(code);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#else

static int
Python_Eval_Cmd(ClientData cdata, Tcl_Interp *interp,
           int objc, Tcl_Obj *const objv[])
{
  TCL_RETURN_ERROR("Turbine not compiled with Python support");
}
#endif
/**
   Shorten object creation lines.  python:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "python::" tcl_function, c_function, \
                         NULL, NULL);
/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclpython_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "python", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  return TCL_OK;
}

void
tcl_python_init(Tcl_Interp* interp)
{
  COMMAND("python", Python_Eval_Cmd);
}
