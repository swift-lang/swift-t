
/**
 * Tcl extension for Turbine
 *
 * @author wozniak
 * */

#include <assert.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdlib.h>
#define _GNU_SOURCE // for strnlen()
#define __USE_GNU   // for strnlen() (needed on BG/P)
#include <string.h>

#include <tcl.h>

#include <log.h>
#include <tools.h>

#include "src/util/debug.h"
#include "src/turbine/turbine.h"
#include "src/turbine/cache.h"

#include "src/tcl/util.h"
#include "src/tcl/turbine/tcl-turbine.h"

#include "src/tcl/c-utils/tcl-c-utils.h"
#include "src/tcl/adlb/tcl-adlb.h"
#include "src/tcl/mpe/tcl-mpe.h"

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

static void set_namespace_constants(Tcl_Interp* interp);

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

  set_namespace_constants(interp);

  log_init();

  // Did the user disable logging?
  char* s = getenv("TURBINE_LOG");
  if (s != NULL && strcmp(s, "0") == 0)
    log_enabled(false);
  else
    log_normalize();

  return TCL_OK;
}

static void
set_namespace_constants(Tcl_Interp* interp)
{
  tcl_set_integer(interp, "::turbine::LOCAL",   TURBINE_ACTION_LOCAL);
  tcl_set_integer(interp, "::turbine::CONTROL", TURBINE_ACTION_CONTROL);
  tcl_set_integer(interp, "::turbine::WORK",    TURBINE_ACTION_WORK);
}

static int
Turbine_Engine_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[])
{
  turbine_engine_init();
  return TCL_OK;
}

static int
Turbine_Version_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  version v;
  turbine_version(&v);
  char vs[8];
  version_to_string(vs, &v);
  Tcl_Obj* result = Tcl_NewStringObj(vs, -1);
  assert(result);
  Tcl_SetObjResult(interp, result);

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

/**
   usage: rule name [ list inputs ] action_type action => id
   The name is just for debugging
 */
static int
Turbine_Rule_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);

  int inputs;
  turbine_datum_id input_list[TCL_TURBINE_MAX_INPUTS];

  int error;
  turbine_transform_id id;

  // Get the debugging name
  char* name = Tcl_GetStringFromObj(objv[1], NULL);
  assert(name);

  // Get the input list
  error = turbine_tcl_long_array(interp, objv[2],
                                TCL_TURBINE_MAX_INPUTS,
                                input_list, &inputs);
  TCL_CHECK_MSG(error, "could not parse inputs list as integers:\n"
                "in rule: <%li> %s inputs: \"%s\"",
                id, name, Tcl_GetString(objv[2]));

  // Get the action type
  turbine_action_type action_type;
  int tmp;
  error = Tcl_GetIntFromObj(interp, objv[3], &tmp);
  TCL_CHECK_MSG(error, "could not parse as integer!");
  action_type = tmp;

  // Get the action string
  char* action = Tcl_GetStringFromObj(objv[4], NULL);
  assert(action);

  // Lookup current priority
  int priority = 0;
  Tcl_Obj* p = Tcl_GetVar2Ex(interp, "turbine::priority", NULL, 0);
  TCL_CONDITION(p != NULL, "could not access turbine::priority");
  error = Tcl_GetIntFromObj(interp, p, &priority);
  TCL_CHECK_MSG(error, "turbine::priority is not an integer!");

  // Issue the rule
  turbine_code code =
      turbine_rule(name, inputs, input_list, action_type, action,
                   priority, &id);
  TURBINE_CHECK(code, "could not add rule: %li", id);
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

/**
   usage: ready => [ list ids ]
 */
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

/**
   usage: turbine::action id => [ list action_type action ]
 */
static int
Turbine_Action_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_transform_id id;
  int error = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(error);

  // Pointer into Turbine memory
  char* action;
  turbine_action_type action_type;
  turbine_code code = turbine_action(id, &action_type, &action);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not find transform id: %li", id);

  Tcl_Obj* items[2];
  items[0] = Tcl_NewIntObj(action_type);
  items[1] = Tcl_NewStringObj(action, -1);

  Tcl_Obj* result = Tcl_NewListObj(2, items);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: turbine::priority id => priority
 */
static int
Turbine_Priority_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_transform_id id;
  int error = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(error);


  int priority;
  turbine_code code = turbine_priority(id, &priority);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not find transform id: %li", id);

  Tcl_Obj* result = Tcl_NewIntObj(priority);
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

/*
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
*/

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

static int cache_check_cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[]);

static int cache_retrieve_cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[]);

static int cache_store_cmd(ClientData cdata, Tcl_Interp* interp,
                           int objc, Tcl_Obj *const objv[]);
static int
Turbine_Cache_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  int rc = TCL_OK;
  char* subcommand = Tcl_GetString(objv[1]);
  if (subcommand == NULL)
    subcommand = "";

  if (strcmp("check", subcommand) == 0)
    rc = cache_check_cmd(cdata, interp, objc-1, objv+1);
  else if (strcmp("retrieve", subcommand) == 0)
    rc = cache_retrieve_cmd(cdata, interp, objc-1, objv+1);
  else if (strcmp("store", subcommand) == 0)
    rc = cache_store_cmd(cdata, interp, objc-1, objv+1);
  else
  {
    printf("turbine::cache received bad subcommand: '%s'\n",
           subcommand);
    return TCL_ERROR;
  }
  return rc;
}

static int
cache_check_cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS_SUB(cache, 2);
  turbine_datum_id td;
  int error = Tcl_GetLongFromObj(interp, objv[1], &td);
  TCL_CHECK(error);

  bool found = turbine_cache_check(td);

  Tcl_Obj* result = Tcl_NewBooleanObj(found);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static inline int retrieve_object(Tcl_Interp *interp,
                                  Tcl_Obj *const objv[], long td,
                                  turbine_type type,
                                  void* data, int length,
                                  Tcl_Obj** result);

static int
cache_retrieve_cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS_SUB(retrieve, 2);
  turbine_datum_id td;
  int error = Tcl_GetLongFromObj(interp, objv[1], &td);
  TCL_CHECK(error);

  turbine_type type;
  void* data;
  int length;
  turbine_code rc = turbine_cache_retrieve(td, &type, &data, &length);
  TURBINE_CHECK(rc, "cache retrieve failed: %li", td);

  Tcl_Obj* result = NULL;
  retrieve_object(interp, objv, td, type, data, length, &result);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   interp, objv, id, and length: just for error checking and messages
 */
static inline int
retrieve_object(Tcl_Interp *interp, Tcl_Obj *const objv[], long id,
                turbine_type type, void* data, int length,
                Tcl_Obj** result)
{
  long tmp_long;
  double tmp_double;
  int string_length;

  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      memcpy(&tmp_long, data, sizeof(long));
      *result = Tcl_NewLongObj(tmp_long);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      memcpy(&tmp_double, data, sizeof(double));
      *result = Tcl_NewDoubleObj(tmp_double);
      break;
    case ADLB_DATA_TYPE_STRING:
      *result = Tcl_NewStringObj(data, length-1);
      break;
    case ADLB_DATA_TYPE_BLOB:
      string_length = strnlen(data, length);
      TCL_CONDITION(string_length < length,
                    "adlb::retrieve: unterminated blob: <%li>", id);
      *result = Tcl_NewStringObj(data, string_length);
      break;
    case ADLB_DATA_TYPE_FILE:
      *result = Tcl_NewStringObj(data, length-1);
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      *result = Tcl_NewStringObj(data, length-1);
      break;
    default:
      *result = NULL;
      return TCL_ERROR;
  }
  return TCL_OK;
}

int extract_object(Tcl_Interp* interp, Tcl_Obj *const objv[],
                   turbine_datum_id td, turbine_type type,
                   Tcl_Obj* obj, void** result, int* length);

/**
   usage turbine::cache store $td $type $value
 */
static int
cache_store_cmd(ClientData cdata, Tcl_Interp* interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS_SUB(store, 4);

  turbine_datum_id td;
  turbine_type type;
  void* data;
  int length;

  int error;
  error = Tcl_GetLongFromObj(interp, objv[1], &td);
  TCL_CHECK(error);
  int t;
  error = Tcl_GetIntFromObj(interp, objv[2], &t);
  type = t;
  TCL_CHECK(error);
  error = extract_object(interp, objv, td, type, objv[3],
                         &data, &length);
  TCL_CHECK_MSG(error, "object extraction failed: <%li>", td);

  turbine_code rc = turbine_cache_store(td, type, data, length);
  TURBINE_CHECK(rc, "cache store failed: %li", td);

  return TCL_OK;
}

int
extract_object(Tcl_Interp* interp, Tcl_Obj *const objv[],
               turbine_datum_id td,
               turbine_type type,
               Tcl_Obj* obj, void** result, int* length)
{
  int rc;
  long tmp_long;
  double tmp_double;

  void* data = NULL;

  switch (type)
  {
    case TURBINE_TYPE_INTEGER:
      rc = Tcl_GetLongFromObj(interp, obj, &tmp_long);
      TCL_CHECK_MSG(rc, "cache store failed: <%li>", td);
      *length = sizeof(long);
      data = malloc(*length);
      memcpy(data, &tmp_long, *length);
      break;
    case TURBINE_TYPE_FLOAT:
      rc = Tcl_GetDoubleFromObj(interp, obj, &tmp_double);
      TCL_CHECK_MSG(rc, "cache store failed: <%li>", td);
      *length = sizeof(double);
      data = malloc(*length);
      memcpy(data, &tmp_double, *length);
      break;
    case TURBINE_TYPE_STRING:
      data = Tcl_GetStringFromObj(objv[3], length);
      TCL_CONDITION(data != NULL,
                    "cache store failed: <%li>", td);
      *length = strlen(data)+1;
      TCL_CONDITION(*length < ADLB_DATA_MAX,
                    "cache store: string too long: <%li>", td);
      break;
    case TURBINE_TYPE_BLOB:
      TCL_RETURN_ERROR("cannot cache a blob!");
      break;
    case TURBINE_TYPE_FILE:
      TCL_RETURN_ERROR("cannot cache a file!");
      break;
    case TURBINE_TYPE_CONTAINER:
      TCL_RETURN_ERROR("cannot cache a container!");
      break;
    case TURBINE_TYPE_NULL:
      TCL_RETURN_ERROR("cache store: given TURBINE_TYPE_NULL!");
      break;
  }

  *result = data;
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
  DEBUG_TCL_TURBINE("%s", msg);
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
  Tcl_CreateObjCommand(interp,                                      \
                       "turbine::c::" tcl_function, c_function,     \
                       NULL, NULL);

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclturbine_Init(Tcl_Interp* interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

#ifndef TURBINE_VERSION
#error TURBINE_VERSION must be set by the build system!
#endif

  if (Tcl_PkgProvide(interp, "turbine", TURBINE_VERSION) == TCL_ERROR)
    return TCL_ERROR;

  tcl_c_utils_init(interp);
  tcl_adlb_init(interp);
  tcl_mpe_init(interp);

  COMMAND("init",        Turbine_Init_Cmd);
  COMMAND("engine_init", Turbine_Engine_Init_Cmd);
  COMMAND("version",     Turbine_Version_Cmd);
  COMMAND("rule",        Turbine_Rule_Cmd);
  COMMAND("push",        Turbine_Push_Cmd);
  COMMAND("ready",       Turbine_Ready_Cmd);
  COMMAND("action",      Turbine_Action_Cmd);
  COMMAND("priority",    Turbine_Priority_Cmd);
  COMMAND("complete",    Turbine_Complete_Cmd);
  COMMAND("close",       Turbine_Close_Cmd);
  COMMAND("log",         Turbine_Log_Cmd);
  COMMAND("normalize",   Turbine_Normalize_Cmd);
  COMMAND("cache",       Turbine_Cache_Cmd);
  COMMAND("finalize",    Turbine_Finalize_Cmd);
  COMMAND("debug",       Turbine_Debug_Cmd);

  Tcl_Namespace* turbine =
    Tcl_FindNamespace(interp, "turbine::c", NULL, 0);
  Tcl_Export(interp, turbine, "*", 0);

  return TCL_OK;
}
