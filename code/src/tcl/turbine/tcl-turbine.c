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
// Out-of-range work type to signal that it should be sent back to this
// rank with type WORK
#define TURBINE_ADLB_WORK_TYPE_LOCAL -1

static int
turbine_extract_ids(Tcl_Interp* interp, Tcl_Obj *const objv[],
            Tcl_Obj* list, int max,
            adlb_datum_id* ids, int* id_count,
            adlb_datum_id_sub* id_subs, int* id_sub_count);

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

static int log_setup(int rank);

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

  log_setup(rank);

  return TCL_OK;
}

/**
   @return Tcl error code
*/
static int
log_setup(int rank)
{
  log_init();
  log_normalize();

  // Did the user disable logging?
  int enabled;
  getenv_integer("TURBINE_LOG", 1, &enabled);
  if (enabled)
  {
    // Should we use a specific log file?
    char* filename = getenv("TURBINE_LOG_FILE");
    if (filename != NULL && strlen(filename) > 0)
    {
      bool b = log_file_set(filename);
      if (!b)
      {
        printf("Could not set log file: %s", filename);
        return TCL_ERROR;
      }
    }
    // Should we prepend the MPI rank (emulate "mpiexec -l")?
    int log_rank_enabled;
    getenv_integer("TURBINE_LOG_RANKS", 0, &log_rank_enabled);
    if (log_rank_enabled)
      log_rank_set(rank);
  }
  else
    log_enabled(false);

  return TCL_OK;
}

static void
set_namespace_constants(Tcl_Interp* interp)
{
  tcl_set_integer(interp, "::turbine::WORK", TURBINE_ADLB_WORK_TYPE_WORK);
  // Map control to work for backwards compatibility with Tcl code
  // that distinguishes between the two
  tcl_set_integer(interp, "::turbine::CONTROL",
        TURBINE_ADLB_WORK_TYPE_WORK);
  tcl_set_integer(interp, "::turbine::LOCAL",
        TURBINE_ADLB_WORK_TYPE_LOCAL);
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
  int work_type;
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
   rule [ list inputs ] action [ name ... ] [ work_type ... ]
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
  int rc;
  int inputs = 0, input_pairs = 0;
  adlb_datum_id input_list[TCL_TURBINE_MAX_INPUTS];
  adlb_datum_id_sub input_pair_list[TCL_TURBINE_MAX_INPUTS];
  char name_buffer[TURBINE_NAME_MAX];

  // Get the action string
  int action_len;
  char* action = Tcl_GetStringFromObj(objv[2], &action_len);
  assert(action);
  action_len++; // Include null terminator

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
  rc = turbine_extract_ids(interp, objv, objv[1], TCL_TURBINE_MAX_INPUTS,
              input_list, &inputs, input_pair_list, &input_pairs);
  TCL_CHECK_MSG(rc, "could not parse inputs list as ids or id/subscript "
                "pairs:\n in rule: %s inputs: \"%s\"",
                opts.name, Tcl_GetString(objv[1]));


  adlb_code ac = ADLB_Put_rule(action, action_len, opts.target,
        adlb_comm_rank, opts.work_type, ADLB_curr_priority, opts.parallelism,
        opts.name, input_list, inputs, input_pair_list, input_pairs);
  TCL_CONDITION(ac == ADLB_SUCCESS, "could not process rule!");
  
  // Free subscripts that were allocated
  for (int i = 0; i < input_pairs; i++)
  {
    free((void*)input_pair_list[i].subscript.key);
  }
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
  opts->work_type = TURBINE_ADLB_WORK_TYPE_WORK;
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
        if (t == TURBINE_ADLB_WORK_TYPE_LOCAL)
        {
          // Ensure sent back here
          opts->work_type = TURBINE_ADLB_WORK_TYPE_WORK;
          opts->target = adlb_comm_rank;
        }
        else
        {
          opts->work_type = t;
        }
        return TCL_OK;
      }
      break;
  }

  TCL_RETURN_ERROR("rule options: unknown key: %s", k);
  return TCL_ERROR; // unreachable
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
  const char *subscript;
  int subscript_len;
  int error = ADLB_EXTRACT_HANDLE(objv[1], &td, &subscript,
                                  &subscript_len);
  TCL_CHECK(error);

  bool found;
  if (subscript_len == 0)
  {
    found = turbine_cache_check(td);
  }
  else
  {
    // TODO: handle caching subscripts - currently just ignore
    found = false;
  }

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
  const char *subscript;
  int subscript_len;
  int error = ADLB_EXTRACT_HANDLE(objv[1], &td, &subscript,
                                  &subscript_len);
  TCL_CHECK(error);

  // TODO: handle caching subscripts
  TCL_CONDITION(subscript_len == 0, "Don't handle caching subscripts");

  turbine_type type;
  void* data;
  int length;
  turbine_code rc = turbine_cache_retrieve(td, &type, &data, &length);
  TURBINE_CHECK(rc, "cache retrieve failed: %"PRId64"", td);

  Tcl_Obj* result = NULL;
  int tcl_code = adlb_data_to_tcl_obj(interp, objv, td, type,
                      ADLB_TYPE_EXTRA_NULL, data, length, &result);
  TCL_CHECK(tcl_code);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
tcl_obj_to_binary(Tcl_Interp* interp, Tcl_Obj *const objv[],
               turbine_datum_id td, turbine_type type,
               adlb_type_extra extra, Tcl_Obj* obj,
               bool canonicalize, void** result, int* length);

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

  const char *subscript;
  int subscript_len;
  error = ADLB_EXTRACT_HANDLE(objv[argpos++], &td, &subscript,
                                  &subscript_len);
  TCL_CHECK(error);

  if (subscript_len != 0)
  {
    // TODO: handle caching subscripts
    return TCL_OK;
  }

  adlb_data_type type;
  adlb_type_extra extra;
  error = type_from_obj_extra(interp, objv, objv[argpos++], &type,
                              &extra);
  TCL_CHECK(error);

  TCL_CONDITION(argpos < objc, "not enough arguments");
  error = tcl_obj_to_binary(interp, objv, td, type, extra,
                         objv[argpos++], false, &data, &length);
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
               adlb_type_extra extra, Tcl_Obj* obj,
               bool canonicalize, void** result, int* length)
{
  adlb_binary_data data;

  int rc = tcl_obj_to_bin(interp, objv, type, extra, obj, canonicalize,
                          NULL, &data);
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
  type_extra.valid = true;


  // Increments/decrements for outer and inner containers
  // (default no extras)
  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;

  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                    &refcounts.incr_referand.read_refcount);
    TCL_CHECK(rc);
  }
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                    &refcounts.incr_referand.write_refcount);
    TCL_CHECK(rc);
  }

  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                           &refcounts.decr_self.write_refcount);
    TCL_CHECK(rc);
  }
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                           &refcounts.decr_self.read_refcount);
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

  // Initial trial at inserting.
  // Refcounts are only applied here if we got back the data
  adlb_code code = ADLB_Insert_atomic(id, subscript, refcounts,
                        &created, xfer, &value_len, &outer_value_type);
  TCL_CONDITION(code == ADLB_SUCCESS, "error in Insert_atomic!");

  if (created)
  {
    // Need to create container and insert

    /*
     * Initial refcounts for container passed to caller
     * We set to a fairly high number since this lets us give refcounts
     * from outer container to callers without also touching inner
     * datum.  Remainder will be freed all at once when outer container
     * is closed/garbage collected.
     */
    int init_count = (2 << 24);
    adlb_refc init_refs = { .read_refcount = init_count,
                                 .write_refcount = init_count };
    adlb_create_props props = DEFAULT_CREATE_PROPS;

    props.release_write_refs = turbine_release_write_rc_policy(type);

    props.read_refcount = init_refs.read_refcount +
                          refcounts.incr_referand.read_refcount;
    props.write_refcount = init_refs.write_refcount +
                           refcounts.incr_referand.write_refcount;
    
    adlb_datum_id new_id;
    code = ADLB_Create(ADLB_DATA_ID_NULL, type, type_extra, props, &new_id);
    TCL_CONDITION(code == ADLB_SUCCESS, "Error while creating nested");

    // ID is only relevant data, so init refcounts to any value
    adlb_ref new_ref = { .id = new_id, .read_refs = 0, .write_refs = 0 };
   
    // Pack using standard api.  Checks should be mostly optimized out
    adlb_binary_data packed;
    adlb_data_code dc = ADLB_Pack_ref(&new_ref, &packed);
    TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error packing ref");


    // Store and apply remaining refcounts
    code = ADLB_Store(id, subscript, ADLB_DATA_TYPE_REF, packed.data,
                      packed.length, refcounts.decr_self, init_refs);
    TCL_CONDITION(code == ADLB_SUCCESS, "Error while inserting nested");

    ADLB_Free_binary_data(&packed);

    // Return the ID of the new container
    Tcl_SetObjResult(interp, Tcl_NewADLB_ID(new_id));
    return TCL_OK;
  }
  else
  {
    // Wasn't able to create.  Entry may or may not already have value.
    while (value_len < 0)
    {
      // Need to poll until value exists
      // This will decrement reference counts if it succeeds
      code = ADLB_Retrieve(id, subscript, refcounts, &outer_value_type,
                         xfer, &value_len);
      TCL_CONDITION(code == ADLB_SUCCESS,
          "unexpected error while polling for container value");

    }
    TCL_CONDITION(outer_value_type == ADLB_DATA_TYPE_REF,
            "only works on containers with values of type ref");

    Tcl_Obj* result = NULL;
    adlb_data_to_tcl_obj(interp, objv, id, ADLB_DATA_TYPE_REF,
            ADLB_TYPE_EXTRA_NULL, xfer, value_len, &result);
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

  if (turbine_debug_enabled)
  {
    unused char* msg = Tcl_GetString(objv[1]);
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

  Ownership of subscript memory in id_subs is passed to caller.
 */
static int
turbine_extract_ids(Tcl_Interp* interp, Tcl_Obj *const objv[],
            Tcl_Obj* list, int max,
            adlb_datum_id* ids, int* id_count,
            adlb_datum_id_sub* id_subs, int* id_sub_count)
{
  Tcl_Obj** entry;
  int n;
  int code = Tcl_ListObjGetElements(interp, list, &n, &entry);
  assert(code == TCL_OK);
  TCL_CONDITION(n < max, "Rule IDs exceed supported max: %i > %i",
                n, max);
  for (int i = 0; i < n; i++)
  {
    Tcl_Obj *obj = entry[i];

    // Parse, allocating memory for subscripts
    tcl_adlb_handle handle;
    code = ADLB_PARSE_HANDLE(obj, &handle, false);
    TCL_CHECK_MSG(code, "Error parsing handle %s", Tcl_GetString(obj));
    if (handle.sub.val.key == NULL)
    {
      ids[(*id_count)++] = handle.id;
    }
    else
    {
      adlb_datum_id_sub *pair = &id_subs[(*id_sub_count)++];

      pair->id = handle.id;
      pair->subscript.length = handle.sub.val.length;

      // check if key memory was allocated and owned by us
      if (handle.sub.buf.data == handle.sub.val.key)
      {
        pair->subscript.key = handle.sub.buf.data;
      }
      else
      {
        // Don't own data, alloc and copy
        // TODO: avoid malloc somehow?
        char *tmp_key = malloc(handle.sub.val.length);
        TCL_MALLOC_CHECK(tmp_key);
        memcpy(tmp_key, handle.sub.val.key, handle.sub.val.length);
        pair->subscript.key = tmp_key;
      }
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
  COMMAND("version",     Turbine_Version_Cmd);
  COMMAND("rule",        Turbine_Rule_Cmd);
  COMMAND("ruleopts",    Turbine_RuleOpts_Cmd);
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
