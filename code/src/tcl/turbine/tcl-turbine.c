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

#include <stdint.h>
#include <inttypes.h>

#include <tcl.h>

#include <adlb.h>
#include <adlb_types.h>

#include <log.h>
#include <tools.h>

#include "src/util/debug.h"
#include "src/turbine/turbine-version.h"
#include "src/turbine/turbine.h"
#include "src/turbine/cache.h"
#include "src/turbine/worker.h"

#include "src/tcl/util.h"
#include "src/tcl/turbine/tcl-turbine.h"

#include "src/tcl/c-utils/tcl-c-utils.h"
#include "src/tcl/adlb/tcl-adlb.h"
#include "src/tcl/mpe/tcl-mpe.h"
#include "src/tcl/python/tcl-python.h"
#include "src/tcl/r/tcl-r.h"

#define TURBINE_ADLB_WORK_TYPE_WORK 0
#define TURBINE_ADLB_WORK_TYPE_CONTROL 1

static int
turbine_extract_ids(Tcl_Interp* interp, Tcl_Obj *const objv[],
            Tcl_Obj* list, int max,
            turbine_datum_id* ids, int* id_count,
            td_sub_pair* id_subs, int* id_sub_count);

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
  log_normalize();

  // Did the user disable logging?
  char* s = getenv("TURBINE_LOG");
  if (s != NULL && strcmp(s, "0") == 0)
    log_enabled(false);

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
  int inputs = 0, input_pairs = 0;
  turbine_datum_id input_list[TCL_TURBINE_MAX_INPUTS];
  td_sub_pair input_pair_list[TCL_TURBINE_MAX_INPUTS];
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
  // TODO: also support id/subscript pairs
  rc = turbine_extract_ids(interp, objv, objv[1], TCL_TURBINE_MAX_INPUTS,
              input_list, &inputs, input_pair_list, &input_pairs);
  TCL_CHECK_MSG(rc, "could not parse inputs list as ids or id/subscript "
                "pairs:\n in rule: <%"PRId64"> %s inputs: \"%s\"",
                id, opts.name, Tcl_GetString(objv[1]));

  turbine_code code =
      turbine_rule(opts.name, inputs, input_list, input_pairs, input_pair_list,
                   opts.type, action,
                   ADLB_curr_priority, opts.target, opts.parallelism, &id);
  TURBINE_CHECK(code, "could not process rule!");
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

/*
  Pop a ready transform. All arguments are output arguments
  turbine::pop_or_break <transform id> <type> <action> <priority> <target>
  If no ready transform, will return TCL_BREAK to signal this
  condition.
 */
static int
Turbine_Pop_Or_Break_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(7);

  turbine_transform_id transform_id;
  turbine_action_type type;
  char *action;
  int priority;
  int target;
  int parallelism;

  turbine_code code = turbine_pop(&type, &transform_id, &action,
                                  &priority, &target, &parallelism);
  TCL_CONDITION(code == TURBINE_SUCCESS, "could not pop transform id");
  if (type == TURBINE_ACTION_NULL)
  {
    DEBUG_TURBINE("No ready transforms, sending TCL_BREAK signal");
    return TCL_BREAK;
  }

  Tcl_ObjSetVar2(interp, objv[1], NULL, Tcl_NewWideIntObj(transform_id),
                 EMPTY_FLAG);
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

/**
  turbine::close <id> [<subscript>]
  Handle close notification message
 */
static int
Turbine_Close_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 2 || objc == 3,
                "turbine::close requires 1 or 2 args!");

  turbine_datum_id id;
  int error = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK(error);

  if (objc == 2)
  {
    turbine_code code = turbine_close(id);
    TCL_CONDITION(code == TURBINE_SUCCESS,
                  "could not close datum id: %"PRId64"", id);
  }
  else
  {
    int sub_strlen;
    const char *sub = Tcl_GetStringFromObj(objv[2], &sub_strlen);
    size_t sub_len = (size_t) sub_strlen + 1; // Account for null byte
    turbine_code code = turbine_sub_close(id, sub, sub_len);
    TCL_CONDITION(code == TURBINE_SUCCESS,
                  "could not close %"PRId64"[\"%s\"]", id, sub);
  }

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
Turbine_Cache_Check_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_datum_id td;
  int error = Tcl_GetADLB_ID(interp, objv[1], &td);
  TCL_CHECK(error);

  bool found = turbine_cache_check(td);

  Tcl_Obj* result = Tcl_NewBooleanObj(found);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_Cache_Retrieve_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  turbine_datum_id td;
  int error = Tcl_GetADLB_ID(interp, objv[1], &td);
  TCL_CHECK(error);

  turbine_type type;
  void* data;
  int length;
  turbine_code rc = turbine_cache_retrieve(td, &type, &data, &length);
  TURBINE_CHECK(rc, "cache retrieve failed: %"PRId64"", td);

  Tcl_Obj* result = NULL;
  int tcl_code = adlb_data_to_tcl_obj(interp, objv, td, type, NULL,
                                      data, length, &result);
  TCL_CHECK(tcl_code);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
tcl_obj_to_binary(Tcl_Interp* interp, Tcl_Obj *const objv[],
               turbine_datum_id td, turbine_type type,
               const adlb_type_extra *extra,
               Tcl_Obj* obj, void** result, int* length);

/**
   usage turbine::cache_store $td $type [ extra type info ] $value
 */
static int
Turbine_Cache_Store_Cmd(ClientData cdata, Tcl_Interp* interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 4, "requires >= 4 args");

  turbine_datum_id td;
  void* data = NULL;
  int length = 0;

  int argpos = 1;

  int error;
  error = Tcl_GetADLB_ID(interp, objv[argpos++], &td);
  TCL_CHECK(error);

  adlb_data_type type;
  bool has_extra;
  adlb_type_extra extra;
  error = type_from_obj_extra(interp, objv, objv[argpos++], &type,
                              &has_extra, &extra);
  TCL_CHECK(error);

  TCL_CONDITION(argpos < objc, "not enough arguments");
  error = tcl_obj_to_binary(interp, objv, td, type, has_extra ? &extra : NULL,
                         objv[argpos++], &data, &length);
  TCL_CHECK_MSG(error, "object extraction failed: <%"PRId64">", td);

  TCL_CONDITION(argpos == objc, "extra trailing arguments from %i", argpos);

  turbine_code rc = turbine_cache_store(td, type, data, length);
  TURBINE_CHECK(rc, "cache store failed: %"PRId64"", td);

  return TCL_OK;
}

// Allocate a binary buffer and serialize a tcl object into it
//  for the specified ADLB type
static int
tcl_obj_to_binary(Tcl_Interp* interp, Tcl_Obj *const objv[],
               turbine_datum_id td, turbine_type type,
               const adlb_type_extra *extra,
               Tcl_Obj* obj, void** result, int* length)
{
  adlb_binary_data data;

  int rc = tcl_obj_to_bin(interp, objv, type, extra, obj, NULL, &data);
  TCL_CHECK_MSG(rc, "failed serializing tcl object to ADLB <%"PRId64">: \"%s\"",
                    td, Tcl_GetString(obj));

  // Ensure we have ownership of a malloced buffer with the data
  adlb_data_code dc  = ADLB_Own_data(NULL, &data);

  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "allocating binary buffer for <%"PRId64"> "
                "failed: %s", td, Tcl_GetString(obj));

  assert(data.caller_data != NULL);
  *result = data.caller_data;
  *length = data.length;
  return TCL_OK;
}

/* usage: worker_loop <work type>
   Repeatedly run units of work from ADLB of provided type
 */
static int
Turbine_Worker_Loop_Cmd(ClientData cdata, Tcl_Interp* interp,
                        int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(2);

  int work_type;
  int rc = TCL_OK;
  rc = Tcl_GetIntFromObj(interp, objv[1], &work_type);
  TCL_CHECK(rc);

  // Maintain separate buffer from xfer, since xfer may be
  // used in code that we call.
  size_t buffer_size = ADLB_DATA_MAX;
  void* buffer = malloc(buffer_size);

  turbine_code code = turbine_worker_loop(interp, buffer, buffer_size,
                                          work_type);
  if (code == TURBINE_ERROR_EXTERNAL)
    // turbine_worker_loop() has added the error info
    rc = TCL_ERROR;
  else
    TCL_CONDITION(code == TURBINE_SUCCESS, "Unknown worker error!");

  free(buffer);
  return rc;
}

static int
create_autoclose_rule(Tcl_Interp *interp, Tcl_Obj *const objv[],
                      adlb_datum_id wait_on, adlb_datum_id to_close);

static int
Turbine_Create_Nested_Impl(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[], adlb_data_type type)
{
  TCL_CONDITION(type == ADLB_DATA_TYPE_CONTAINER ||
                type == ADLB_DATA_TYPE_MULTISET,
                "Must create nested container or multiset, not %s",
                ADLB_Data_type_tostring(type));

  int min_args;
  if (type == ADLB_DATA_TYPE_CONTAINER) {
    min_args = 4;
  } else {
    // Multiset
    min_args = 3;
  }
  TCL_CONDITION(objc >= min_args, "Requires at least %d args", min_args);
  adlb_datum_id id;
  adlb_subscript subscript;

  int rc;
  int argpos = 1;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &id);
  TCL_CHECK(rc);

  rc = Tcl_GetADLB_Subscript(objv[argpos++], &subscript);
  TCL_CHECK_MSG(rc, "Invalid subscript argument");

  adlb_type_extra type_extra;
  adlb_data_type tmp;
  if (type == ADLB_DATA_TYPE_CONTAINER) {
    rc = type_from_obj(interp, objv, objv[argpos++], &tmp);
    TCL_CHECK(rc);
    type_extra.CONTAINER.key_type = tmp;

    rc = type_from_obj(interp, objv, objv[argpos++], &tmp);
    TCL_CHECK(rc);
    type_extra.CONTAINER.val_type = tmp;
  } else {
    assert(type == ADLB_DATA_TYPE_MULTISET);
    rc = type_from_obj(interp, objv, objv[argpos++], &tmp);
    TCL_CHECK(rc);
    type_extra.MULTISET.val_type = tmp;
  }

  // Increments for inner container (default no extras)
  adlb_refcounts nested_incr = ADLB_NO_RC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                                   &nested_incr.read_refcount);
    TCL_CHECK(rc);
  }
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                                   &nested_incr.write_refcount);
    TCL_CHECK(rc);
  }

  // Decrements for outer container
  adlb_refcounts decr = ADLB_NO_RC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.write_refcount);
    TCL_CHECK(rc);
  }
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.read_refcount);
    TCL_CHECK(rc);
  }
  TCL_CONDITION(argpos == objc, "Trailing args starting at %i", argpos);

  if (type == ADLB_DATA_TYPE_CONTAINER) {
    log_printf("creating nested container <%"PRId64">[%.*s] (%s->%s)", id,
      (int)subscript.length, subscript.key,
      ADLB_Data_type_tostring(type_extra.CONTAINER.key_type),
      ADLB_Data_type_tostring(type_extra.CONTAINER.val_type));
  } else {
    log_printf("creating nested multiset <%"PRId64">[%.*s] (%s)", id,
      (int)subscript.length, subscript.key,
      ADLB_Data_type_tostring(type_extra.MULTISET.val_type));
  }

  uint64_t xfer_size;
  char *xfer = tcl_adlb_xfer_buffer(&xfer_size);

  bool created;
  int value_len;
  adlb_data_type outer_value_type;
  adlb_code code = ADLB_Insert_atomic(id, subscript, &created,
                              xfer, &value_len, &outer_value_type);
  TCL_CONDITION(code == ADLB_SUCCESS, "error in Insert_atomic!");

  if (created)
  {
    // Need to create container and insert
    adlb_datum_id new_id;
    adlb_create_props props = DEFAULT_CREATE_PROPS;
    // Initial read refcount - 1 for container, plus more
    // Initial write refcount - 1 for container, plus more
    props.read_refcount = 1 + nested_incr.read_refcount;
    props.write_refcount = 1 + nested_incr.write_refcount;
    code = ADLB_Create(ADLB_DATA_ID_NULL, type, type_extra, props, &new_id);
    TCL_CONDITION(code == ADLB_SUCCESS, "Error while creating nested");

    code = ADLB_Store(id, subscript, ADLB_DATA_TYPE_REF, &new_id,
                    (int)sizeof(new_id), decr);
    TCL_CONDITION(code == ADLB_SUCCESS, "Error while inserting nested");

    // Set up rule to close container
    rc = create_autoclose_rule(interp, objv, id, new_id);
    TCL_CHECK(rc);

    // Return the ID of the new container
    Tcl_SetObjResult(interp, Tcl_NewADLB_ID(new_id));
    return TCL_OK;
  }
  else
  {
    // Wasn't able to create.  Entry may or may not already have value.
    bool must_do_rc = !ADLB_RC_IS_NULL(decr);
    while (value_len < 0)
    {
      // Need to poll until value exists
      // Try to decrement reference counts with this operation
      adlb_retrieve_rc ret_decr = ADLB_RETRIEVE_NO_RC;
      if (must_do_rc)
        ret_decr.decr_self = decr;
      code = ADLB_Retrieve(id, subscript, ret_decr, &outer_value_type,
                         xfer, &value_len);
      TCL_CONDITION(code == ADLB_SUCCESS,
          "unexpected error while polling for container value");

      must_do_rc = false;
    }
    TCL_CONDITION(outer_value_type == ADLB_DATA_TYPE_REF,
            "only works on containers with values of type ref");

    if (!ADLB_RC_IS_NULL(nested_incr))
    {
      // Do any necessary changes to refcounts
      adlb_datum_id nested_id;
      adlb_data_code dc = ADLB_Unpack_ref(&nested_id, xfer, value_len);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "unexpected error unpacked reference data");
      code = ADLB_Refcount_incr(nested_id, nested_incr);
      TCL_CONDITION(code == ADLB_SUCCESS,
            "unexpected error incrementing nested reference counts");
    }

    if (must_do_rc)
    {
      // do decrement as separate operation
      code = ADLB_Refcount_incr(id, adlb_rc_negate(decr));
      TCL_CONDITION(code == ADLB_SUCCESS,
          "unexpected error when update reference count");
    }

    Tcl_Obj* result = NULL;
    adlb_data_to_tcl_obj(interp, objv, id, ADLB_DATA_TYPE_REF, NULL,
                         xfer, value_len, &result);
    Tcl_SetObjResult(interp, result);
    return TCL_OK;
  }
}

/*
  turbine::create_nested <container> <subscript> <key_type> <val_type>
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   caller * refs: how many reference counts to give back to caller
 */
static int
Turbine_Create_Nested_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return Turbine_Create_Nested_Impl(cdata, interp, objc, objv,
                                  ADLB_DATA_TYPE_CONTAINER);
}

/*
  turbine::create_nested_bag <container> <subscript> <val_type>
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   caller * refs: how many reference counts to give back to caller
 */
static int
Turbine_Create_Nested_Bag_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return Turbine_Create_Nested_Impl(cdata, interp, objc, objv,
                                  ADLB_DATA_TYPE_MULTISET);
}

static int
create_autoclose_rule(Tcl_Interp *interp, Tcl_Obj *const objv[],
                      adlb_datum_id wait_on, adlb_datum_id to_close)
{
  const int i64_len = 21; // Upper bound on int64_t string length

  int tmp_len;
  const int name_len = 10 + i64_len; // enough for text+id
  char name[name_len];
  tmp_len = sprintf(name, "autoclose-%"PRId64"", to_close);
  assert(tmp_len > 0 && tmp_len < name_len);

  const int action_len = 30 + i64_len; // enough for function name+id
  char action[action_len];
  tmp_len = sprintf(action, "adlb::write_refcount_decr %"PRId64"", to_close);
  assert(tmp_len > 0 && tmp_len < action_len);

  if (turbine_is_engine())
  {
    turbine_transform_id transform_id;
    turbine_code tc = turbine_rule(name, 1, &wait_on, 0, NULL,
                  TURBINE_ACTION_LOCAL, action, ADLB_curr_priority,
                  TURBINE_RANK_ANY, 1, &transform_id);
    TCL_CONDITION(tc == TURBINE_SUCCESS, "Failed creating autoclose rule");
  }
  else
  {
    // We're not on an engine, need to send this to an engine to process
    const int rule_cmd_len = name_len + action_len + 20 + i64_len;
    char rule_cmd[rule_cmd_len];
    tmp_len = sprintf(rule_cmd, "rule %"PRId64" \"%s\" name \"%s\"",
                      wait_on, action, name);
    assert(tmp_len > 0 && tmp_len < rule_cmd_len);

    adlb_code code = ADLB_Put(rule_cmd, tmp_len + 1, ADLB_RANK_ANY, adlb_comm_rank,
                        TURBINE_ADLB_WORK_TYPE_CONTROL, ADLB_curr_priority, 1);
    TCL_CONDITION(code == ADLB_SUCCESS, "Error in ADLB put");
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
  // Free allocated object
  Tcl_DecrRefCount(SPAWN_RULE_CMD);

  turbine_finalize();
  return TCL_OK;
}

static int
Turbine_Debug_On_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  bool enabled = turbine_debug_enabled;
  Tcl_SetObjResult(interp, Tcl_NewIntObj(enabled));
  return TCL_OK;
}

static int
Turbine_Debug_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  // Only print if debug enabled.  Note that compiler
  // will be able to eliminate dead code if debugging is disabled
  // at compile time
  if (turbine_debug_enabled)
  {
    char* msg = Tcl_GetString(objv[1]);
    DEBUG_TCL_TURBINE("%s", msg);
    return TCL_OK;
  }
  else
  {
    return TCL_OK;
  }
}

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

/*
  Extract IDs and ID/Sub pairs
 */
static int
turbine_extract_ids(Tcl_Interp* interp, Tcl_Obj *const objv[],
            Tcl_Obj* list, int max,
            turbine_datum_id* ids, int* id_count,
            td_sub_pair* id_subs, int* id_sub_count)
{
  Tcl_Obj** entry;
  int n;
  int code = Tcl_ListObjGetElements(interp, list, &n, &entry);
  assert(code == TCL_OK);
  TCL_CONDITION(n < max, "Rule IDs exceed supported max: %i > %i",
                n, max);
  assert(sizeof(Tcl_WideInt) == sizeof(turbine_datum_id));
  for (int i = 0; i < n; i++)
  {
    Tcl_Obj *obj = entry[i];
    // First try to interpret as ID
    code = Tcl_GetWideIntFromObj(interp, obj,
                                (Tcl_WideInt*)&ids[*id_count]);
    if (code == TCL_OK)
    {
      (*id_count)++;
    }
    else
    {
      // Try to interpret as id/sub pair
      Tcl_Obj** id_pair_list;
      int id_pair_llen;
      code = Tcl_ListObjGetElements(interp, obj, &id_pair_llen, &id_pair_list);
      TCL_CONDITION(code == TCL_OK && id_pair_llen == 2, "Could not "
              "interpret %s as id or id/subscript pair", Tcl_GetString(obj));
      turbine_datum_id id;
      char *subscript;
      int subscript_strlen;
      code = Tcl_GetWideIntFromObj(interp, id_pair_list[0],
                                  (Tcl_WideInt*)&id);
      TCL_CONDITION(code == TCL_OK, "Could not interpret %s as "
            "id/subscript pair", Tcl_GetString(obj));
      subscript = Tcl_GetStringFromObj(id_pair_list[1], &subscript_strlen);
      size_t subscript_len = (size_t)subscript_strlen + 1;
      td_sub_pair *pair = &id_subs[(*id_sub_count)++];
      pair->td = id;
      pair->subscript.key = malloc(subscript_len);
      TCL_CONDITION(pair->subscript.key != NULL,
                    "Could not allocate memory");
      memcpy(pair->subscript.key, subscript, subscript_len);
      pair->subscript.length = subscript_len;
    }
  }
  return TURBINE_SUCCESS;
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
  COMMAND("pop_or_break",Turbine_Pop_Or_Break_Cmd);
  COMMAND("close",       Turbine_Close_Cmd);
  COMMAND("log",         Turbine_Log_Cmd);
  COMMAND("normalize",   Turbine_Normalize_Cmd);
  COMMAND("worker_loop", Turbine_Worker_Loop_Cmd);
  COMMAND("create_nested", Turbine_Create_Nested_Cmd);
  COMMAND("create_nested_bag", Turbine_Create_Nested_Bag_Cmd);
  COMMAND("cache_check", Turbine_Cache_Check_Cmd);
  COMMAND("cache_retrieve", Turbine_Cache_Retrieve_Cmd);
  COMMAND("cache_store", Turbine_Cache_Store_Cmd);
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
