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

/**
 * Tcl extension for Turbine
 *
 * @author wozniak
 * */

#include "config.h"

#include <assert.h>
#include <ctype.h>
#include <errno.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdlib.h>
// strnlen() is a GNU extension: Need _GNU_SOURCE
#define _GNU_SOURCE
#if ENABLE_BGP == 1
// Also need __USE_GNU on the BG/P and on older GCC (4.1, 4.3)
#define __USE_GNU
#endif
#include <string.h>

#include <tcl.h>

#include <adlb.h>

#include <log.h>
#include <tools.h>

#include "src/util/debug.h"
#include "src/turbine/turbine-version.h"
#include "src/turbine/turbine.h"
#include "src/turbine/cache.h"

#include "src/tcl/util.h"
#include "src/tcl/turbine/tcl-turbine.h"

#include "src/tcl/c-utils/tcl-c-utils.h"
#include "src/tcl/adlb/tcl-adlb.h"
#include "src/tcl/mpe/tcl-mpe.h"
#include "src/tcl/python/tcl-python.h"
#include "src/tcl/r/tcl-r.h"

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
   If code is not SUCCESS, return a Tcl error that includes the
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

static Tcl_Obj *SPAWN_RULE_CMD;

static int
Turbine_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);
  int amserver, rank, size;

  int rc;
  rc = Tcl_GetIntFromObj(interp, objv[1], &amserver);
  TCL_CHECK(rc);
  rc = Tcl_GetIntFromObj(interp, objv[2], &rank);
  TCL_CHECK(rc);
  rc = Tcl_GetIntFromObj(interp, objv[3], &size);
  TCL_CHECK(rc);

  turbine_code code = turbine_init(amserver, rank, size);
  if (code != TURBINE_SUCCESS)
  {
    Tcl_AddErrorInfo(interp, " Could not initialize Turbine!\n");
    return TCL_ERROR;
  }

  set_namespace_constants(interp);

  // Name of Tcl command
  SPAWN_RULE_CMD = Tcl_NewStringObj("::turbine::spawn_rule", -1);

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

static inline void rule_set_name_default(char* name, int size,
                                         const char* action);

struct rule_opts
{
  char* name;
  turbine_action_type type;
  int target;
  int parallelism;
};

static inline void rule_set_opts_default(struct rule_opts* opts,
                                         const char* action,
                                         char* buffer, int buffer_size);

static inline int
rule_opts_from_list(Tcl_Interp* interp, Tcl_Obj *const objv[],
                    struct rule_opts* opts,
                    Tcl_Obj *const objs[], int count,
                    char *name_buffer, int name_buffer_size,
                    const char *action);

/**
   usage:
   OLD rule name [ list inputs ] action_type target parallelism action => id
   NEW rule [ list inputs ] action [ name ... ] [ action_type ... ]
                                   [ target ... ] [ parallelism ... ]
             keyword args are optional
   DEFAULTS: name=<first token of action plus output list>
             type=TURBINE_ACTION_WORK
             target=TURBINE_RANK_ANY
             parallelism=1
   The name is just for debugging
 */
static int
Turbine_Rule_Cmd(ClientData cdata, Tcl_Interp* interp,
                 int objc, Tcl_Obj *const objv[])
{
  const int BASIC_ARGS = 3;
  TCL_CONDITION(objc >= BASIC_ARGS,
                "turbine::c::rule requires at least %i args!",
                BASIC_ARGS);

  /* Intercept calls to rule not on engine and send to engine.
     Need to call back into turbine::spawn_rule with same arguments,
     which is defined in Tcl
   */
  if (!turbine_is_engine())
  {
    Tcl_Obj *newObjv[objc];
    memcpy(newObjv, objv, sizeof(newObjv));
    newObjv[0] = SPAWN_RULE_CMD;
    return Tcl_EvalObjv(interp, objc, newObjv, 0);
  }

  int rc;
  turbine_transform_id id;
  int inputs;
  turbine_datum_id input_list[TCL_TURBINE_MAX_INPUTS];
  char name_buffer[TURBINE_NAME_MAX];

  // Get the action string
  char* action = Tcl_GetStringFromObj(objv[2], NULL);
  assert(action);

  struct rule_opts opts = {NULL, 0, 0, 0};

  if (objc > BASIC_ARGS)
  {
    // User gave us a list of optional args
    rc = rule_opts_from_list(interp, objv, &opts, objv + BASIC_ARGS,
                             objc - BASIC_ARGS,
                             name_buffer, TURBINE_NAME_MAX, action);
    TCL_CHECK(rc);
  }
  else
  {
    rule_set_opts_default(&opts, action, name_buffer,
                          TURBINE_NAME_MAX);
  }

  // Get the input list - done last so we can report name on error
  rc = turbine_tcl_long_array(interp, objv[1],
                              TCL_TURBINE_MAX_INPUTS,
                              input_list, &inputs);
  TCL_CHECK_MSG(rc, "could not parse inputs list as integers:\n"
                "in rule: <%lli> %s inputs: \"%s\"",
                lli(id), opts.name, Tcl_GetString(objv[1]));

  turbine_code code =
      turbine_rule(opts.name, inputs, input_list, opts.type, action,
                   ADLB_curr_priority, opts.target, opts.parallelism, &id);
  TURBINE_CHECK(code, "could not add rule: %lli", id);
  return TCL_OK;
}

static inline void
rule_set_opts_default(struct rule_opts* opts,
                      const char* action, char* buffer,
                      int buffer_size)
{
  opts->name = buffer;
  if (action != NULL) {
    assert(opts->name != NULL);
    rule_set_name_default(opts->name, buffer_size, action);
  }
  opts->type = TURBINE_ACTION_LOCAL;
  opts->target = TURBINE_RANK_ANY;
  opts->parallelism = 1;
}

static inline void
rule_set_name_default(char* name, int size, const char* action)
{
  char* q = strchr(action, ' ');
  long n = q-action+1;
  strncpy(name, action, (size_t)n);
  name[n] = '\0';
}

static int
Turbine_RuleOpts_Cmd(ClientData cdata, Tcl_Interp* interp,
                     int objc, Tcl_Obj *const objv[])
{
  int name_buf_size = 128;
  char t[name_buf_size];
  struct rule_opts opts;
  opts.name = NULL;

  const char *action = "dummy action";
  // User gave us a list of optional args
  rule_opts_from_list(interp, objv, &opts, objv + 1, objc - 1,
                      t, name_buf_size, action);
  return TCL_OK;
}

static inline int
rule_opt_from_kv(Tcl_Interp* interp, Tcl_Obj *const objv[],
            struct rule_opts* opts, Tcl_Obj* key, Tcl_Obj* val);

/**
  Fill in struct from list
  
  Note that strings may be filled in with pointers to
  other Tcl data structures.  If these pointers are going to
  escape from the caller into arbitrary Tcl code, the caller
  must make a copy.
   @param interp : just here for error cases
   @param objv : just here for error messages
   @return Tcl error code
 */
static inline int
rule_opts_from_list(Tcl_Interp* interp, Tcl_Obj *const objv[],
                    struct rule_opts* opts,
                    Tcl_Obj *const objs[], int count,
                    char *name_buffer, int name_buffer_size,
                    const char *action)
{
  TCL_CONDITION(count % 2 == 0,
                "Must have matching key-value args, but "
                "found odd number: %i", count);

  rule_set_opts_default(opts, NULL, NULL, 0);
  
  for (int keypos = 0; keypos < count; keypos+=2)
  {
    int valpos = keypos + 1; 
    int rc = rule_opt_from_kv(interp, objv, opts,
                              objs[keypos], objs[valpos]);
    TCL_CHECK(rc);
  }
  if (opts->name == NULL) {
    rule_set_name_default(name_buffer, name_buffer_size, action);
    opts->name = name_buffer;
  }
  return TCL_OK;
}

/**
   Translate one key value pair into an opts entry.
   Note that caller is responsible for copying any strings.
 */
static inline int
rule_opt_from_kv(Tcl_Interp* interp, Tcl_Obj *const objv[],
                 struct rule_opts* opts, Tcl_Obj* key, Tcl_Obj* val)
{
  char* k = Tcl_GetString(key);
  int rc;

  switch (k[0])
  {
    case 'n':
      if (strcmp(k, "name") == 0)
      {
        opts->name = Tcl_GetString(val);
        return TCL_OK;
        // printf("name: %s\n", opts->name);
      }
      break;
    case 'p':
      if (strcmp(k, "parallelism") == 0)
      {
        int t;
        rc = Tcl_GetIntFromObj(interp, val, &t);
        TCL_CHECK_MSG(rc, "parallelism argument must be integer");
        opts->parallelism = t;
        return TCL_OK;
      }
      break;
    case 't':
      if (strcmp(k, "target") == 0)
      {
        int t;
        rc = Tcl_GetIntFromObj(interp, val, &t);
        TCL_CHECK_MSG(rc, "target argument must be integer");
        opts->target = t;
        return TCL_OK;
      }
      else if (strcmp(k, "type") == 0)
      {
        int t;
        rc = Tcl_GetIntFromObj(interp, val, &t);
        TCL_CHECK_MSG(rc, "type argument must be integer");
        opts->type = t;
        return TCL_OK;
      }
      break;
  }

  TCL_RETURN_ERROR("rule options: unknown key: %s", k);
  return TCL_ERROR; // unreachable
}

static int
Turbine_Push_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  turbine_code code = turbine_rules_push();
  TURBINE_CHECK(code, "failure while pushing rules");
  return TCL_OK;
}

/**
   usage: ready => [ list ids ]
   Note that this may not return all ready TRs
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
    Tcl_Obj* sid = Tcl_NewWideIntObj(transforms[i]);
    Tcl_ListObjAppendElement(interp, result, sid);
  }

  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
Turbine_Pop_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(7); // ID, plus type, action, priority, target, par vars to set

  turbine_transform_id id;
  int error = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK(error);

  turbine_action_type type;
  char *action;
  int priority;
  int target;
  int parallelism;

  turbine_code code = turbine_pop(id, &type, &action,
                                  &priority, &target, &parallelism);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                 "could not pop transform id: %lli", id);

  Tcl_ObjSetVar2(interp, objv[2], NULL, Tcl_NewIntObj(type),
                 EMPTY_FLAG);
  Tcl_ObjSetVar2(interp, objv[3], NULL, Tcl_NewStringObj(action, -1),
                 EMPTY_FLAG);
  free(action);
  Tcl_ObjSetVar2(interp, objv[4], NULL, Tcl_NewIntObj(priority), EMPTY_FLAG);
  Tcl_ObjSetVar2(interp, objv[5], NULL, Tcl_NewIntObj(target), EMPTY_FLAG);
  Tcl_ObjSetVar2(interp, objv[6], NULL, Tcl_NewIntObj(parallelism), EMPTY_FLAG);
  return TCL_OK;
}

static int
Turbine_Close_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  turbine_datum_id id;
  int error = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK(error);

  turbine_code code = turbine_close(id);
  TCL_CONDITION(code == TURBINE_SUCCESS,
                "could not close datum id: %lli", id);

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
  int error = Tcl_GetADLB_ID(interp, objv[1], &td);
  TCL_CHECK(error);

  bool found = turbine_cache_check(td);

  Tcl_Obj* result = Tcl_NewBooleanObj(found);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static inline int retrieve_object(Tcl_Interp *interp,
                                  Tcl_Obj *const objv[], adlb_datum_id td,
                                  turbine_type type,
                                  void* data, int length,
                                  Tcl_Obj** result);

static int
cache_retrieve_cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS_SUB(retrieve, 2);
  turbine_datum_id td;
  int error = Tcl_GetADLB_ID(interp, objv[1], &td);
  TCL_CHECK(error);

  turbine_type type;
  void* data;
  int length;
  turbine_code rc = turbine_cache_retrieve(td, &type, &data, &length);
  TURBINE_CHECK(rc, "cache retrieve failed: %lli", td);

  Tcl_Obj* result = NULL;
  retrieve_object(interp, objv, td, type, data, length, &result);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   interp, objv, id, and length: just for error checking and messages
 */
static inline int
retrieve_object(Tcl_Interp *interp, Tcl_Obj *const objv[], adlb_datum_id id,
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
      string_length = (int)strnlen(data, (size_t)length);
      TCL_CONDITION(string_length < length,
                    "adlb::retrieve: unterminated blob: <%lli>", id);
      *result = Tcl_NewStringObj(data, string_length);
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

static int extract_object(Tcl_Interp* interp, Tcl_Obj *const objv[],
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
  error = Tcl_GetADLB_ID(interp, objv[1], &td);
  TCL_CHECK(error);
  int t;
  error = Tcl_GetIntFromObj(interp, objv[2], &t);
  type = t;
  TCL_CHECK(error);
  error = extract_object(interp, objv, td, type, objv[3],
                         &data, &length);
  TCL_CHECK_MSG(error, "object extraction failed: <%lli>", td);

  turbine_code rc = turbine_cache_store(td, type, data, length);
  TURBINE_CHECK(rc, "cache store failed: %lli", td);

  return TCL_OK;
}

static int
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
      TCL_CHECK_MSG(rc, "cache store failed: <%lli>", td);
      *length = (int)sizeof(long);
      data = malloc((size_t)*length);
      memcpy(data, &tmp_long, (size_t)*length);
      break;
    case TURBINE_TYPE_FLOAT:
      rc = Tcl_GetDoubleFromObj(interp, obj, &tmp_double);
      TCL_CHECK_MSG(rc, "cache store failed: <%lli>", td);
      *length = (int)sizeof(double);
      data = malloc((size_t)*length);
      memcpy(data, &tmp_double, (size_t)*length);
      break;
    case TURBINE_TYPE_STRING:
      data = Tcl_GetStringFromObj(objv[3], length);
      TCL_CONDITION(data != NULL,
                    "cache store failed: <%lli>", td);
      *length = (int)strlen(data)+1;
      TCL_CONDITION(*length < ADLB_DATA_MAX,
                    "cache store: string too long: <%lli>", td);
      break;
    case TURBINE_TYPE_BLOB:
      TCL_RETURN_ERROR("cannot cache a blob!");
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

#define MAX_WORK_STRING 10240
static char work_string[MAX_WORK_STRING];

/* usage: worker_loop <work type>
   Repeatedly run units of work from ADLB of provided type
 */
static int
Turbine_Worker_Loop_Cmd(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  
  int work_type;
  int rc;
  rc = Tcl_GetIntFromObj(interp, objv[1], &work_type);
  TCL_CHECK(rc);

  adlb_code code;
  int buffer_size;
  char *buffer = tcl_adlb_xfer_buffer(&buffer_size);
  while (true) {
    MPI_Comm task_comm;
    int work_len, answer_rank, type_recved;
    code = ADLB_Get(work_type, buffer, &work_len,
                    &answer_rank, &type_recved, &task_comm);
    if (code == ADLB_SHUTDOWN)
      break;
    turbine_task_comm = task_comm;
    TCL_CONDITION(code == ADLB_SUCCESS, "Get failed with code %i\n", code);
    assert(work_len <= buffer_size);
    assert(type_recved == work_type);

    // Copy work string out of buffer: work unit may overwrite buffer
    assert(strnlen(buffer, MAX_WORK_STRING) < MAX_WORK_STRING);
    strcpy(work_string, buffer);

    // Work unit is prepended with rule ID, followed by space.
    char *rule_id_end = strchr(work_string, ' ');

    assert(rule_id_end != NULL);
    char *work = rule_id_end + 1; // start of Tcl work unit
    
    DEBUG_TURBINE("rule_id: %lli", lli(atol(work_string)));
    DEBUG_TURBINE("eval: %s", work);

    // Work out length | null byte | prefix
    int cmd_len = work_len - 1 - (int)(work - buffer);
    rc = Tcl_EvalEx(interp, work, cmd_len, 0);
    if (rc != TCL_OK) {
      TCL_CONDITION(rc == TCL_ERROR, "Unexpected return code from evaled "
                    "command: %d", rc);
      // Pass error to calling script
      const char *prefix = "\nWorker executing task: ";
      char *msg = malloc(sizeof(char) * (strlen(prefix) + (size_t)work_len));
      sprintf(msg, "%s%s", prefix, work);
      Tcl_AddErrorInfo(interp, msg);
      free(msg);
      return rc;
    }
  }
  return TCL_OK;
}

static int
Turbine_TaskComm_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  Tcl_Obj* result = Tcl_NewLongObj(turbine_task_comm);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_TaskRank_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  int rank;
  MPI_Comm_rank(turbine_task_comm, &rank);
  Tcl_Obj* result = Tcl_NewIntObj(rank);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_TaskSize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  int size;
  MPI_Comm_size(turbine_task_comm, &size);
  Tcl_Obj* result = Tcl_NewIntObj(size);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  turbine_finalize();
  return TCL_OK;
}

static int
Turbine_Debug_On_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  bool enabled;
#ifdef ENABLE_DEBUG_TCL_TURBINE
  enabled = 1;
#else
  enabled = 0;
#endif
  Tcl_SetObjResult(interp, Tcl_NewIntObj(enabled));
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

/*
  Convert decimal string to int
 */
static int
Turbine_StrInt_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int len;
  const char *str = Tcl_GetStringFromObj(objv[1], &len);
  
  errno = 0; // Reset so we can detect errors
  char *end_str;

  Tcl_WideInt val;

#ifdef TCL_WIDE_INT_IS_LONG
  val = strtol(str, &end_str, 10);
#else
  val = strtoll(str, &end_str, 10);
#endif

  // Check for errors
  if (errno != 0)
  {
    int my_errno = errno;
    errno = 0; // reset errno
    if (my_errno == ERANGE)
    {
      TCL_RETURN_ERROR("Integer representation of '%s' is out of range of "
          "%zi bit integers", str, sizeof(Tcl_WideInt) * 8);
    }
    else if (my_errno == EINVAL)
    {
      TCL_RETURN_ERROR("'%s' cannot be interpreted as an integer ", str);
    }
    else
    {
      TCL_RETURN_ERROR("Internal error: unexpected my_errno %d when "
                       "converting '%s' to integer", my_errno, str);
    }
  }
  long consumed = end_str - str;
  if (consumed == 0)
  {
    // Handle case where no input consumed
    TCL_RETURN_ERROR("'%s' cannot be interpreted as an integer ", str);
  }

  if (consumed < len)
  {
    // Didn't consume all string.  Make sure only whitespace at end
    for (long i = consumed; i < len; i++)
    {
      if (!isspace(str[i]))
      {
        TCL_RETURN_ERROR("Invalid trailing characters in '%s'", str);
      }
    }
  }

  Tcl_SetObjResult(interp, Tcl_NewWideIntObj(val));
  return TCL_OK;
}

/**
   Shorten command creation lines.
   The "turbine::c::" namespace is prepended
 */
#define COMMAND(tcl_function, c_function)                           \
  Tcl_CreateObjCommand(interp,                                      \
                       "turbine::c::" tcl_function, c_function,     \
                       NULL, NULL);

// We assume SWIG correctly generates this function
// See the tcl/blob module
int Blob_Init(Tcl_Interp* interp);

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclturbine_Init(Tcl_Interp* interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "turbine", TURBINE_VERSION) == TCL_ERROR)
    return TCL_ERROR;

  tcl_c_utils_init(interp);
  tcl_adlb_init(interp);
  tcl_mpe_init(interp);
  tcl_python_init(interp);
  tcl_r_init(interp);
  Blob_Init(interp);

  COMMAND("init",        Turbine_Init_Cmd);
  COMMAND("engine_init", Turbine_Engine_Init_Cmd);
  COMMAND("version",     Turbine_Version_Cmd);
  COMMAND("rule",        Turbine_Rule_Cmd);
  COMMAND("ruleopts",    Turbine_RuleOpts_Cmd);
  COMMAND("push",        Turbine_Push_Cmd);
  COMMAND("ready",       Turbine_Ready_Cmd);
  COMMAND("pop",         Turbine_Pop_Cmd);
  COMMAND("close",       Turbine_Close_Cmd);
  COMMAND("log",         Turbine_Log_Cmd);
  COMMAND("normalize",   Turbine_Normalize_Cmd);
  COMMAND("worker_loop", Turbine_Worker_Loop_Cmd);
  COMMAND("cache",       Turbine_Cache_Cmd);
  COMMAND("task_comm",   Turbine_TaskComm_Cmd);
  COMMAND("task_rank",   Turbine_TaskRank_Cmd);
  COMMAND("task_size",   Turbine_TaskSize_Cmd);
  COMMAND("finalize",    Turbine_Finalize_Cmd);
  COMMAND("debug_on",    Turbine_Debug_On_Cmd);
  COMMAND("debug",       Turbine_Debug_Cmd);
  COMMAND("check_str_int", Turbine_StrInt_Cmd);

  Tcl_Namespace* turbine =
    Tcl_FindNamespace(interp, "::turbine::c", NULL, 0);
  Tcl_Export(interp, turbine, "*", 0);

  return TCL_OK;
}
