
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

#include <log.h>
#include <tools.h>

#include "src/util/debug.h"
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
  append(p, "\n");
  p += vsprintf(p, format, ap);
  va_end(ap);
  append(p, "\n%s", "turbine error: ");
  turbine_code_tostring(p, code);
  printf("turbine_check_failed: %s\n", buffer);
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
  TCL_ARGS(4);
  int amserver, rank, size;
  int rc;
  rc = Tcl_GetIntFromObj(interp, objv[1], &amserver);
  assert(rc == TCL_OK);
  rc = Tcl_GetIntFromObj(interp, objv[2], &rank);
  assert(rc == TCL_OK);
  rc = Tcl_GetIntFromObj(interp, objv[3], &size);
  assert(rc == TCL_OK);

  turbine_code code = turbine_init(amserver, rank, size);
  if (code != TURBINE_SUCCESS)
  {
    Tcl_AddErrorInfo(interp, " Could not initialize Turbine!\n");
    return TCL_ERROR;
  }

  log_init();
  log_normalize();

  return TCL_OK;
}

#define string_tomode(mode, mode_string)                        \
  if (strcmp(mode_string, "field") == 0)                        \
    mode = TURBINE_ENTRY_FIELD;                                 \
  else if (strcmp(mode_string, "key") == 0)                     \
    mode = TURBINE_ENTRY_KEY;                                   \
  else                                                          \
    TCL_RETURN_ERROR("unknown entry mode: %s\n", mode_string);

#define SET_ENTRY(entry, type, subscript)                       \
  if (strcmp(type, "field"))                                    \
    entry.type = TURBINE_ENTRY_FIELD;                           \
  else if (strcmp(type, "key"))                                 \
    entry.type = TURBINE_ENTRY_KEY;                             \
  else                                                          \
    TCL_RETURN_ERROR("unknown turbine entry type: %s", type);   \
  strcpy(entry.name, subscript);

static int
Turbine_Rule_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(6);

  int inputs;
  turbine_datum_id input_list[TCL_TURBINE_MAX_INPUTS];
  int outputs;
  turbine_datum_id output_list[TCL_TURBINE_MAX_INPUTS];

  int error;
  turbine_transform_id id;
  error = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(error);

  char* name = Tcl_GetStringFromObj(objv[2], NULL);
  assert(name);

  error = turbine_tcl_long_array(interp, objv[3],
                                TCL_TURBINE_MAX_INPUTS,
                                input_list, &inputs);
  TCL_CHECK_MSG(error, "could not parse inputs list as integers:\n"
                "in rule: <%li> %s inputs: \"%s\"",
                id, name, Tcl_GetString(objv[3]));

  error = turbine_tcl_long_array(interp, objv[4],
                                TCL_TURBINE_MAX_OUTPUTS,
                                 output_list, &outputs);
  TCL_CHECK_MSG(error, "could not parse outputs list as integers:\n"
                 "in rule: <%li> %s outputs: \"%s\"",
                 id, name, Tcl_GetString(objv[4]));

  char* action = Tcl_GetStringFromObj(objv[5], NULL);
  assert(action);

  turbine_transform transform =
  {
    .name = name,
    .action = action,
    .inputs = inputs,
    .input_list = input_list,
    .outputs = outputs,
    .output_list = output_list
  };

  turbine_code code = turbine_rule_add(id, &transform);
  TURBINE_CHECK(code, "could not add rule: %li", id);

  return TCL_OK;
}

static int
Turbine_RuleNew_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  turbine_transform_id id;
  turbine_rule_new(&id);

  Tcl_Obj* result = Tcl_NewLongObj(id);
  Tcl_SetObjResult(interp, result);
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
  char action[64];
  turbine_code code = turbine_action(id, action);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not find transform id: %li", id);

  Tcl_Obj* result = Tcl_NewStringObj(action, -1);
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
Turbine_Close_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  turbine_datum_id id;
  int error = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(error);

  turbine_code code = turbine_close(id);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not close datum id: %li", id);

  return TCL_OK;
}

static int
Turbine_Declare_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  turbine_transform_id id;
  int error = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(error);

  turbine_code code = turbine_declare(id, NULL);

  if (code == TURBINE_ERROR_DOUBLE_DECLARE)
    printf("error: trying to declare twice: %li", id);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not declare data id: %li", id);

  return TCL_OK;
}

static int
Turbine_Log_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  log_printf("%s", Tcl_GetString(objv[1]));

  return TCL_OK;
}

static int
Turbine_Normalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                      int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  log_normalize();
  return TCL_OK;
}

static int
Turbine_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  turbine_finalize();
  return TCL_OK;
}

#ifdef ENABLE_DEBUG_TCL_TURBINE
static int
Turbine_Debug_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  char* msg = Tcl_GetString(objv[1]);
  DEBUG_TCL_TURBINE("%s\n", msg);
  return TCL_OK;
}
#else // Debug output is disabled
static int
Turbine_Debug_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  // This is a noop
  return TCL_OK;
}
#endif

/**
   Shorten command creation lines.
   The "turbine::c::" namespace is prepended
 */
#define COMMAND(tcl_function, c_function)                           \
  Tcl_CreateObjCommand(interp, "turbine::c::" tcl_function, c_function, \
                       NULL, NULL);

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclturbine_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "turbine", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  COMMAND("init",      Turbine_Init_Cmd);
  COMMAND("declare",   Turbine_Declare_Cmd);
  COMMAND("rule",      Turbine_Rule_Cmd);
  COMMAND("rule_new",  Turbine_RuleNew_Cmd);
  COMMAND("push",      Turbine_Push_Cmd);
  COMMAND("ready",     Turbine_Ready_Cmd);
  COMMAND("action",  Turbine_Executor_Cmd);
  COMMAND("complete",  Turbine_Complete_Cmd);
  COMMAND("close",     Turbine_Close_Cmd);
  COMMAND("log",       Turbine_Log_Cmd);
  COMMAND("normalize", Turbine_Normalize_Cmd);
  COMMAND("finalize",  Turbine_Finalize_Cmd);
  COMMAND("debug",     Turbine_Debug_Cmd);

  Tcl_Namespace* turbine =
    Tcl_FindNamespace(interp, "turbine::c", NULL, 0);
  Tcl_Export(interp, turbine, "*", 0);

  return TCL_OK;
}
