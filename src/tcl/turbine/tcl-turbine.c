
/**
 * TCL extension for Turbine
 *
 * @file tcl-turbine.c
 * @author wozniak
 * */

#include <assert.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>

#include <tcl.h>

#include "src/turbine/turbine.h"

#include "src/tcl/util.h"
#include "src/tcl/turbine/tcl-turbine.h"

/**
   @see TURBINE_CHECK
*/
static void
turbine_check_failed(Tcl_Interp* interp, turbine_code code,
                     char* format, ...)
{
    char buffer[1024];
    char* p = &buffer[0];
    va_list ap;
    va_start(ap, format);
    p += vsprintf(buffer, format, ap);
    va_end(ap);
    p += sprintf(p, "\n%s", "turbine error: ");
    turbine_code_tostring(p, code);
    printf("%s\n", buffer);
    Tcl_AddErrorInfo(interp, buffer);
}

/**
   If code is not SUCCESS, return a TCL error that includes the
   string representation of code
   @note Assumes @code Tcl_Interp* interp @endcode is in scope
   @param code A turbine_code
   @param format A printf-style format string for a error message
   @param args A printf-style vargs list
*/
#define TURBINE_CHECK(code, format, args...)                    \
  if (code != TURBINE_SUCCESS) {                                \
    turbine_check_failed(interp, code, format, ## args);        \
    return TCL_ERROR;                                           \
  }

static int
Turbine_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("TURBINE_SUCCESS", -1), NULL,
                 Tcl_NewIntObj(TURBINE_SUCCESS), TCL_GLOBAL_ONLY);

  Tcl_SetObjResult(interp, Tcl_NewIntObj(TURBINE_SUCCESS));

  turbine_code code = turbine_init();
  if (code != TURBINE_SUCCESS)
  {
    Tcl_AddErrorInfo(interp, " Could not initialize Turbine!\n");
    return TCL_ERROR;
  }

  return TCL_OK;
}

static int
Turbine_Typeof_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);

  char output[64];
  int length;
  turbine_code code = turbine_typeof(lid, output, &length);
  TURBINE_CHECK(code, "could not get type of: %li", lid);
  Tcl_Obj* result = Tcl_NewStringObj(output, length);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_File_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);
  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  char* filename = Tcl_GetStringFromObj(objv[2], NULL);
  turbine_datum_id id = (turbine_datum_id) lid;
  turbine_datum_file_create(id, filename);
  return TCL_OK;
}

/**
   usage: turbine_container <id> <type=field|key>
*/
static int
Turbine_Container_Cmd(ClientData cdata, Tcl_Interp *interp,
                      int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  long lid;
  int error = Tcl_GetLongFromObj(interp, objv[1], &lid);
  TCL_CHECK(error);
  turbine_datum_id id = (turbine_datum_id) lid;
  char* type_string = Tcl_GetString(objv[2]);
  turbine_entry_type type;
  if (strcmp(type_string, "field") == 0)
    type = TURBINE_ENTRY_FIELD;
  else if (strcmp(type_string, "key") == 0)
    type = TURBINE_ENTRY_KEY;
  else
    TCL_RETURN_ERROR("unknown entry type: %s\n", type_string);

  turbine_code code = turbine_datum_container_create(id, type);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not create container: %li", lid);
  return TCL_OK;
}

static int
Turbine_Close_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;

  turbine_code code = turbine_close(id);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not close: %li", lid);
  return TCL_OK;
}

static int
Turbine_Integer_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;

  turbine_code code = turbine_datum_integer_create(id);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not create integer: %li", lid);
  return TCL_OK;
}

static int
Turbine_Integer_Set_Cmd(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;

  printf("set: %li\n", id);

  long value;
  Tcl_GetLongFromObj(interp, objv[2], &value);

  turbine_code code = turbine_datum_integer_set(id, value);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not set integer: %li", lid);
  return TCL_OK;
}

static int
Turbine_Integer_Get_Cmd(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;

  long value;
  turbine_code code = turbine_datum_integer_get(id, &value);
  TURBINE_CHECK(code,
                "could not get integer: %li", lid);

  Tcl_Obj* result = Tcl_NewLongObj(value);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_String_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;

  turbine_code code = turbine_datum_string_create(id);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not create string: %li", lid);
  return TCL_OK;
}

static int
Turbine_String_Set_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;

  int length;
  char* value = Tcl_GetStringFromObj(objv[2], &length);
  turbine_code code = turbine_datum_string_set(id, value, length);
  //TCL_CONDITION(code == TURBINE_SUCCESS,
  //              "could not set string: %li", lid);
  TURBINE_CHECK(code, "could not set string: %li", lid);

  return TCL_OK;
}

static int
Turbine_String_Get_Cmd(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;

  int length;
  turbine_code code = turbine_datum_string_length(id, &length);
  TURBINE_CHECK(code, "could not get string: %li", lid);

  char* tmp = malloc(length+1);
  code = turbine_datum_string_get(id, tmp);
  TURBINE_CHECK(code, "could not get string: %li", lid);

  Tcl_Obj* result = Tcl_NewStringObj(tmp, length+1);
  Tcl_SetObjResult(interp, result);
  free(tmp);
  return TCL_OK;
}

static int
Turbine_Filename_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  Tcl_GetLongFromObj(interp, objv[1], &lid);
  turbine_datum_id id = (turbine_datum_id) lid;
  char filename[TCL_TURBINE_MAX_FILENAME];
  turbine_code code = turbine_filename(id, filename);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not get filename for datum: %li", lid);

  Tcl_Obj* result = Tcl_NewStringObj(filename, -1);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

#define SET_ENTRY(entry, type, subscript)                       \
  if (strcmp(type, "field"))                                    \
    entry.type = TURBINE_ENTRY_FIELD;                           \
  else if (strcmp(type, "key"))                                 \
    entry.type = TURBINE_ENTRY_KEY;                             \
  else                                                          \
    TCL_RETURN_ERROR("unknown turbine entry type: %s", type);   \
  strcpy(entry.name, subscript);

/**
   usage: turbine_insert <container id> <type> <subscript> <entry id>
*/
static int
Turbine_Insert_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);
  int error;
  long container_lid;
  error = Tcl_GetLongFromObj(interp, objv[1], &container_lid);
  TCL_CHECK(error);
  turbine_datum_id container_id = (turbine_datum_id) container_lid;
  char* type = Tcl_GetString(objv[2]);
  char* subscript = Tcl_GetString(objv[3]);
  long entry_lid;
  error = Tcl_GetLongFromObj(interp, objv[4], &entry_lid);
  TCL_CHECK(error);
  turbine_datum_id entry_id = (turbine_datum_id) entry_lid;

  turbine_code code =
    turbine_insert(container_id, subscript, entry_id);
  TURBINE_CHECK(code, "could not insert: %li:%s[%s]",
                container_id, type, subscript);
  return TCL_OK;
}

/**
   usage: turbine_lookup <container_id> <type> <entry_id>
*/
static int
Turbine_Lookup_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);

  long lid;
  int error = Tcl_GetLongFromObj(interp, objv[1], &lid);
  TCL_CHECK(error);
  char* type = Tcl_GetString(objv[2]);
  char* subscript = Tcl_GetString(objv[3]);

  turbine_datum_id member;
  turbine_datum_id id = (turbine_datum_id) lid;

  turbine_code code = turbine_lookup(id, subscript, &member);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not lookup: %li:%s[%s]", lid, type, subscript);

  Tcl_Obj* result = Tcl_NewLongObj(member);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_Container_Get_Cmd(ClientData cdata, Tcl_Interp *interp,
                          int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  long lid;
  int error = Tcl_GetLongFromObj(interp, objv[1], &lid);
  TCL_CHECK(error);
  turbine_datum_id container_id = (turbine_datum_id) lid;

  int count = 1024;
  char* keys[count];
  turbine_code code =
    turbine_container_get(container_id, keys, &count);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not read container: %li", lid);

  Tcl_Obj* result = Tcl_NewListObj(0, NULL);
  for (int i = 0; i < count; i++)
    Tcl_ListObjAppendElement(interp, result,
                             Tcl_NewStringObj(keys[i], -1));

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_New_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  long id;

  turbine_code code = turbine_new(&id);
  assert(code == TURBINE_SUCCESS);

  Tcl_Obj* result = Tcl_NewLongObj(id);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}


static int
Turbine_Rule_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(6);

  turbine_transform_id id;
  int inputs;
  turbine_datum_id input[TCL_TURBINE_MAX_INPUTS];
  int outputs;
  turbine_datum_id output[TCL_TURBINE_MAX_INPUTS];

  int code;
  code = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(code);

  char* name = Tcl_GetStringFromObj(objv[2], NULL);
  assert(name);

  code = turbine_tcl_long_array(interp, objv[3],
                                TCL_TURBINE_MAX_INPUTS,
                                input, &inputs);
  TURBINE_CHECK(code, "could not parse list as long integers: {%s}",
                Tcl_GetString(objv[3]));

  code = turbine_tcl_long_array(interp, objv[4],
                                TCL_TURBINE_MAX_OUTPUTS,
                                output, &outputs);
  TURBINE_CHECK(code, "could not parse list as long integers: {%s}",
                Tcl_GetString(objv[4]));

  char* executor = Tcl_GetStringFromObj(objv[5], NULL);
  assert(executor);

  turbine_transform transform =
  {
    .name = name,
    .executor = executor,
    .inputs = inputs,
    .input = input,
    .outputs = outputs,
    .output = output
  };

  turbine_rule_add(id, &transform);

  return TCL_OK;
}

static int
Turbine_Push_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  turbine_rules_push();
  return TCL_OK;
}

static int
Turbine_Ready_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  turbine_transform_id transforms[TCL_TURBINE_READY_COUNT];
  int actual;
  turbine_ready(TCL_TURBINE_READY_COUNT, transforms, &actual);

  Tcl_Obj* result = Tcl_NewListObj(0, NULL);
  assert(result);

  for (int i = 0; i < actual; i++)
  {
    Tcl_Obj* sid = Tcl_NewLongObj(transforms[i]);
    Tcl_ListObjAppendElement(interp, result, sid);
  }

  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
Turbine_Executor_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_transform_id id;
  int error = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(error);
  char executor[64];
  turbine_code code = turbine_executor(id, executor);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not find transform id: %li", id);

  Tcl_Obj* result = Tcl_NewStringObj(executor, -1);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
Turbine_Complete_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  turbine_transform_id id;
  int error = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(error);

  turbine_code code = turbine_complete(id);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not complete transform id: %li", id);

  return TCL_OK;
}

static int
Turbine_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  turbine_finalize();
  return TCL_OK;
}

#define ADD_COMMAND(tcl_function, c_function) \
  Tcl_CreateObjCommand(interp, tcl_function, c_function, NULL, NULL);

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclturbine_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL) {
    return TCL_ERROR;
  }

  if (Tcl_PkgProvide(interp, "turbine", "0.1") == TCL_ERROR) {
    return TCL_ERROR;
  }
  ADD_COMMAND("turbine_init",          Turbine_Init_Cmd);
  ADD_COMMAND("turbine_file",          Turbine_File_Cmd);
  ADD_COMMAND("turbine_container",     Turbine_Container_Cmd);
  ADD_COMMAND("turbine_close",         Turbine_Close_Cmd);
  ADD_COMMAND("turbine_integer",       Turbine_Integer_Cmd);
  ADD_COMMAND("turbine_integer_set",   Turbine_Integer_Set_Cmd);
  ADD_COMMAND("turbine_integer_get",   Turbine_Integer_Get_Cmd);
  ADD_COMMAND("turbine_string",        Turbine_String_Cmd);
  ADD_COMMAND("turbine_string_set",    Turbine_String_Set_Cmd);
  ADD_COMMAND("turbine_string_get",    Turbine_String_Get_Cmd);
  ADD_COMMAND("turbine_typeof",        Turbine_Typeof_Cmd);
  ADD_COMMAND("turbine_insert",        Turbine_Insert_Cmd);
  ADD_COMMAND("turbine_lookup",        Turbine_Lookup_Cmd);
  ADD_COMMAND("turbine_container_get", Turbine_Container_Get_Cmd);
  ADD_COMMAND("turbine_filename",      Turbine_Filename_Cmd);
  ADD_COMMAND("turbine_rule",          Turbine_Rule_Cmd);
  ADD_COMMAND("turbine_new",           Turbine_New_Cmd);
  ADD_COMMAND("turbine_push",          Turbine_Push_Cmd);
  ADD_COMMAND("turbine_ready",         Turbine_Ready_Cmd);
  ADD_COMMAND("turbine_executor",      Turbine_Executor_Cmd);
  ADD_COMMAND("turbine_complete",      Turbine_Complete_Cmd);
  ADD_COMMAND("turbine_finalize",      Turbine_Finalize_Cmd);
  return TCL_OK;
}
