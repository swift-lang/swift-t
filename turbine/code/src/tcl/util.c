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

#define _GNU_SOURCE // for vasprintf
#include <assert.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>

#include <tools.h>

#include "src/tcl/util.h"

int
turbine_user_error(Tcl_Interp* interp, int nargs, Tcl_Obj *args[])
{
  assert(nargs >= 0);

  Tcl_Obj *call_args[nargs + 1];
  call_args[0] = Tcl_NewStringObj("::turbine::turbine_error", -1);

  for (int i = 0; i < nargs; i++)
  {
    call_args[i + 1] = args[i];
  }

  return Tcl_EvalObjv(interp, nargs + 1, call_args, 0);
}

int
turbine_user_errorv(Tcl_Interp* interp, const char* fmt, ...)
{
  va_list ap;
  va_start(ap, fmt);

  if (strcmp(TCL_VERSION, "8.5") == 0)
  {
    // Our nice error handling does not work in Tcl 8.5
    // (EvalObjv segvs)
    vprintf(fmt, ap);
    printf("\n");
    return TCL_ERROR;
  }
  else
  {
    // Tcl 8.6+
    char* msg;
    int length = vasprintf(&msg, fmt, ap);
    valgrind_assert(length >= 0);

    Tcl_Obj* cmd[2];
    cmd[0] = Tcl_NewStringObj("::turbine::turbine_error", -1);
    cmd[1] = Tcl_NewStringObj(msg, length);
    free(msg);

    Tcl_EvalObjv(interp, 2, cmd, EMPTY_FLAG);
  }
  va_end(ap);

  return TCL_ERROR;
}

turbine_code
turbine_tcl_long_array(Tcl_Interp* interp, Tcl_Obj* list, int max,
                      int64_t* output, int* count)
{
  Tcl_Obj** entry;
  int code = Tcl_ListObjGetElements(interp, list, count, &entry);
  assert(code == TCL_OK);
  assert(*count < max);
  assert(sizeof(Tcl_WideInt) == sizeof(int64_t));
  for (int i = 0; i < *count; i++)
  {
    code = Tcl_GetWideIntFromObj(interp, entry[i], (Tcl_WideInt*)&output[i]);
    if (code != TCL_OK)
      return TURBINE_ERROR_NUMBER_FORMAT;
  }
  return TURBINE_SUCCESS;
}

turbine_code
turbine_tcl_string_array(Tcl_Interp* interp, Tcl_Obj* list, int max,
                         char** output, int* count)
{
  Tcl_Obj** entry;
  int code = Tcl_ListObjGetElements(interp, list, count, &entry);
  assert(code == TCL_OK);
  assert(*count < max);
  for (int i = 0; i < *count; i++)
  {
    char* t = Tcl_GetStringFromObj(entry[i], NULL);
    if (code != TCL_OK)
          return TURBINE_ERROR_UNKNOWN;
    output[i] = t;
  }
  return TURBINE_SUCCESS;
}

#define TCL_CONDITION_MSG_MAX 1024

void turbine_tcl_condition_failed(Tcl_Interp* interp, Tcl_Obj* command,
                                  const char* format, ...)
{
  va_list va;
  va_start(va,format);
  char buffer[TCL_CONDITION_MSG_MAX];
  char* commandname = Tcl_GetStringFromObj(command, NULL);
  char* p = &buffer[0];
  p += sprintf(p, "\n");
  p += sprintf(p, "error: ");
  p += sprintf(p, "%s: ", commandname);
  p += vsprintf(p, format, va);
  p += sprintf(p, "\n\n");
  va_end(va);
  Tcl_AddErrorInfo(interp, buffer);
}

void
turbine_tcl_set_string(Tcl_Interp* interp,
                       const char* name, const char* value)
{
  Tcl_Obj* p = Tcl_ObjSetVar2(interp, Tcl_NewStringObj(name, -1),
                              NULL, Tcl_NewStringObj(value, -1), 0);
  valgrind_assert(p != NULL);
}

void
turbine_tcl_set_integer(Tcl_Interp* interp,
                        const char* name, int value)
{
  Tcl_Obj* p = Tcl_ObjSetVar2(interp, Tcl_NewStringObj(name, -1),
                              NULL, Tcl_NewIntObj(value), 0);
  valgrind_assert(p != NULL);
}

void
turbine_tcl_get_integer(Tcl_Interp* interp,
                        const char* name, int* value)
{
  int result;
  Tcl_Obj name_obj;
  Tcl_SetStringObj(&name_obj, name, -1);
  Tcl_Obj* p = Tcl_ObjGetVar2(interp, &name_obj, NULL, EMPTY_FLAG);
  valgrind_assert(p != NULL);
  int rc = Tcl_GetIntFromObj(interp, p, &result);
  valgrind_assert(rc == TCL_OK);
  *value = result;
}

void
turbine_tcl_set_long(Tcl_Interp* interp,
                     const char* name, long value)
{
  Tcl_Obj* p = Tcl_ObjSetVar2(interp, Tcl_NewStringObj(name, -1),
                              NULL, Tcl_NewLongObj(value), 0);
  valgrind_assert(p != NULL);
}

void
turbine_tcl_set_wideint(Tcl_Interp* interp,
                        const char* name, int64_t value)
{
  Tcl_Obj* p = Tcl_ObjSetVar2(interp, Tcl_NewStringObj(name, -1),
                              NULL, Tcl_NewWideIntObj(value), 0);
  valgrind_assert(p != NULL);
}


void
turbine_tcl_dict_put(Tcl_Interp* interp, Tcl_Obj* dict,
                     const char* key, Tcl_Obj* value)
{
  Tcl_Obj* k = Tcl_NewStringObj(key, -1);
  int rc = Tcl_DictObjPut(interp, dict, k, value);
  valgrind_assert(rc == TCL_OK);
}

void
turbine_tcl_dict_get(Tcl_Interp* interp, Tcl_Obj* dict,
                     const char* key, Tcl_Obj** value)
{
  Tcl_Obj* k = Tcl_NewStringObj(key, -1);
  int rc = Tcl_DictObjGet(interp, dict, k, value);
  valgrind_assert(rc == TCL_OK);
}

Tcl_Obj*
turbine_tcl_list_new(int count, const char** strings)
{
  Tcl_Obj* objs[count];
  for (int i = 0; i < count; i++)
    objs[i] = Tcl_NewStringObj(strings[i], -1);

  Tcl_Obj* result = Tcl_NewListObj(count, objs);
  return result;
}

Tcl_Obj*
turbine_tcl_list_from_array_ints(Tcl_Interp *interp, const int* vals, int count)
{
  Tcl_Obj* result = Tcl_NewListObj(0, NULL);
  for (int i = 0; i < count; i++)
  {
    Tcl_Obj* o = Tcl_NewIntObj(vals[i]);
    Tcl_ListObjAppendElement(interp, result, o);
  }
  return result;
}
