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
#include <fcntl.h>
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
#include <sys/wait.h>
#include <unistd.h>

#include <stdint.h>
#include <inttypes.h>

#include <tcl.h>

#include <adlb.h>
#include <adlb-defs.h>
#include <adlb_types.h>

#include <log.h>
#include <tools.h>

#include "src/util/debug.h"
#include "src/turbine/turbine-version.h"
#include "src/turbine/turbine.h"
#include "src/turbine/cache.h"
#include "src/turbine/worker.h"
#include "src/turbine/io.h"
#include "src/turbine/sync_exec.h"

#include "src/turbine/async_exec.h"
#include "src/turbine/executors/noop_executor.h"

#if HAVE_COASTER == 1
#include <coaster.h>
#include "src/turbine/executors/coaster_executor.h"
#endif

#include "src/tcl/util.h"
#include "src/tcl/turbine/tcl-turbine.h"

#include "src/tcl/c-utils/tcl-c-utils.h"
#include "src/tcl/adlb/tcl-adlb.h"
#include "src/tcl/jvm/tcl-jvm.h"
#include "src/tcl/mpe/tcl-mpe.h"
#include "src/tcl/julia/tcl-julia.h"
#include "src/tcl/python/tcl-python.h"
#include "src/tcl/r/tcl-r.h"

#define TURBINE_ADLB_WORK_TYPE_WORK 0
// Out-of-range work type to signal that it should be sent back to this
// rank with type WORK
#define TURBINE_ADLB_WORK_TYPE_LOCAL -1

static double tcl_version;

static int
turbine_extract_ids(Tcl_Interp* interp, Tcl_Obj *const objv[],
            Tcl_Obj* list, int max,
            adlb_datum_id* ids, int* id_count,
            adlb_datum_id_sub* id_subs, int* id_sub_count);

static int
Turbine_ParseInt_Impl(ClientData cdata, Tcl_Interp *interp,
                  Tcl_Obj *const objv[], Tcl_Obj *obj, int base);

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

int turbine_tcllist_to_strings(Tcl_Interp *interp, Tcl_Obj *const objv[],
      Tcl_Obj *list, int *count, const char ***strs, size_t **str_lens);

static int
worker_keyword_args(Tcl_Interp *interp, Tcl_Obj *const objv[],
                    Tcl_Obj *dict, int *buffer_count, int *buffer_size);

#if HAVE_COASTER == 1
struct staging_mode_entry {
  const char *name;
  coaster_staging_mode mode;
};

/*
 * Convert strings to enum values
 */
static struct staging_mode_entry staging_modes[] = {
  { "always", COASTER_STAGE_ALWAYS },
  { "if_present", COASTER_STAGE_IF_PRESENT },
  { "on_error", COASTER_STAGE_ON_ERROR },
  { "on_success", COASTER_STAGE_ON_SUCCESS },
};

static int num_staging_modes = (int)(sizeof(staging_modes) /
                                     sizeof(staging_modes[0]));

static int parse_coaster_stages(Tcl_Interp *interp, Tcl_Obj *const objv[],
      Tcl_Obj *list, coaster_staging_mode staging_mode,
      int *count, coaster_stage_entry **stages);
static int parse_coaster_opts(Tcl_Interp *interp, Tcl_Obj *const objv[],
      Tcl_Obj *dict, const char **stdin_s, size_t *stdin_slen,
      const char **stdout_s, size_t *stdout_slen,
      const char **stderr_s, size_t *stderr_slen,
      const char **job_manager, size_t *job_manager_len,
      coaster_staging_mode *staging_mode);
#endif

static void get_tcl_version(void);

static int
Turbine_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);
  int amserver, rank, size;

  get_tcl_version();

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

  return TCL_OK;
}

static void
get_tcl_version()
{
  int major;
  int minor;
  Tcl_GetVersion(&major, &minor, NULL, NULL);

  tcl_version = major + 0.1 * minor;
}

/*
  Initialises Turbine debug logging.
  Tcl name is turbine::c::init_debug
 */
static int
Turbine_Init_Debug_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  turbine_debug_init();

  return TCL_OK;
}

static void
set_namespace_constants(Tcl_Interp* interp)
{
  turbine_tcl_set_integer(interp, "::turbine::WORK",
                          TURBINE_ADLB_WORK_TYPE_WORK);
  // Map control to work for backwards compatibility with Tcl code
  // that distinguishes between the two
  turbine_tcl_set_integer(interp, "::turbine::CONTROL",
        TURBINE_ADLB_WORK_TYPE_WORK);
  turbine_tcl_set_integer(interp, "::turbine::LOCAL",
        TURBINE_ADLB_WORK_TYPE_LOCAL);

  turbine_tcl_set_string(interp, "::turbine::NOOP_EXEC_NAME",
                 NOOP_EXECUTOR_NAME);

#if HAVE_COASTER == 1
  turbine_tcl_set_string(interp, "::turbine::COASTER_EXEC_NAME",
                 COASTER_EXECUTOR_NAME);
#endif
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

static inline void rule_set_name_default(char* name, size_t size,
                                         const char* action);

struct rule_opts
{
  char* name;
  int work_type;
  int target;
  adlb_put_opts opts;
};

static inline void rule_log(int inputs,
                            const adlb_datum_id input_list[],
                            const char* action);

static inline void rule_set_opts_default(struct rule_opts* opts,
                                         const char* action,
                                         char* buffer, int buffer_size);

static inline int rule_opts_from_list(Tcl_Interp* interp,
                                      Tcl_Obj *const objv[],
                                      struct rule_opts* opts,
                                      Tcl_Obj *const objs[],
                                      int count,
                                      char *name_buffer,
                                      int name_buffer_size,
                                      const char *action);

/**
   usage:
   rule [ list inputs ] action [ name ... ] [ work_type ... ]
                             [ target ... ] [ parallelism ... ]
                             [ soft_target ... ]
             keyword args are optional
   DEFAULTS: name=<first token of action plus output list>
             type=TURBINE_ACTION_WORK
             target=TURBINE_RANK_ANY
             parallelism=1
   The name is just for debugging
   soft_target will enable soft targeting mode and specify the target rank
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

  struct rule_opts opts = {NULL, 0, 0, ADLB_DEFAULT_PUT_OPTS};

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

  opts.opts.priority = ADLB_curr_priority;

  rule_log(inputs, input_list, action);

  adlb_code ac = ADLB_Dput(action, action_len, opts.target,
        adlb_comm_rank, opts.work_type, opts.opts, opts.name,
        input_list, inputs, input_pair_list, input_pairs);
  TCL_CONDITION(ac == ADLB_SUCCESS, "could not process rule!");

  // Free subscripts that were allocated
  for (int i = 0; i < input_pairs; i++)
  {
    free((void*)input_pair_list[i].subscript.key);
  }
  return TCL_OK;
}

static inline void
rule_log(int inputs, const adlb_datum_id input_list[],
         const char* action)
{
  char log_string[1024];
  if (log_is_enabled())
  {
    char* p = &log_string[0];
    append(p, "rule: ");
    for (int i = 0; i < inputs; i++)
      append(p, "<%i> ", (int) input_list[i]);
    log_printf("%s=> %s", log_string, action);
  }
}

static inline void
rule_set_opts_default(struct rule_opts* opts,
                      const char* action, char* buffer,
                      int buffer_size)
{
  opts->name = buffer;
  if (action != NULL)
  {
    assert(opts->name != NULL);
    rule_set_name_default(opts->name, buffer_size, action);
  }
  opts->work_type = TURBINE_ADLB_WORK_TYPE_WORK;
  opts->target = TURBINE_RANK_ANY;
  opts->opts.parallelism = 1;
}

static inline void
rule_set_name_default(char* name, size_t size, const char* action)
{
  char* q = strchr(action, ' ');
  if (q == NULL)
  {
    strncpy(name, action, size-1);
  }
  else
  {
    size_t n = q-action+1;
    // Do this instead of str[n]cpy to avoid GCC warnings:
    memcpy(name, action, n);
    name[n] = '\0';
  }
}

static int
Turbine_RuleOpts_Cmd(ClientData cdata, Tcl_Interp* interp,
                     int objc, Tcl_Obj *const objv[])
{
  int name_buf_size = 128;
  char t[name_buf_size];
  struct rule_opts opts;
  opts.name = NULL;

  const char* action = "dummy action";
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

  for (int keypos = 0; keypos < count; keypos += 2)
  {
    int valpos = keypos + 1;
    int rc = rule_opt_from_kv(interp, objv, opts,
                              objs[keypos], objs[valpos]);
    TCL_CHECK(rc);
  }
  if (opts->name == NULL)
  {
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
    case 'a':
      if (strcmp(k, "accuracy") == 0)
      {
        return adlb_parse_accuracy(interp, val, &opts->opts.accuracy);
      }
      break;
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
        opts->opts.parallelism = t;
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
          opts->opts.strictness = ADLB_TGT_STRICT_HARD;
        }
        else
        {
          opts->work_type = t;
        }
        return TCL_OK;
      }
      break;
    case 's':
      if (strcmp(k, "strictness") == 0)
      {
        return adlb_parse_strictness(interp, val, &opts->opts.strictness);
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
Turbine_LogTime_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  double t = log_time();
  Tcl_Obj* result = Tcl_NewDoubleObj(t);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/* UNUSED
static int
Turbine_LogTimeAbs_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  double t = log_time_absolute();
  Tcl_Obj* result = Tcl_NewDoubleObj(t);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}
*/

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
  size_t subscript_len;
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
  size_t subscript_len;
  int error = ADLB_EXTRACT_HANDLE(objv[1], &td, &subscript,
                                  &subscript_len);
  TCL_CHECK(error);

  // TODO: handle caching subscripts
  TCL_CONDITION(subscript_len == 0, "Don't handle caching subscripts");

  turbine_type type;
  void* data;
  size_t length;
  turbine_code rc = turbine_cache_retrieve(td, &type, &data, &length);
  TURBINE_CHECK(rc, "cache retrieve failed: %"PRId64"", td);

  adlb_data_type adlb_type = (adlb_data_type) type;
  Tcl_Obj* result = NULL;
  int tcl_code = adlb_datum2tclobj(interp, objv, td, adlb_type,
                      ADLB_TYPE_EXTRA_NULL, data, length, &result);
  TCL_CHECK(tcl_code);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
turbine_tclobj2bin(Tcl_Interp* interp, Tcl_Obj *const objv[],
               turbine_datum_id td, turbine_type type,
               adlb_type_extra extra, Tcl_Obj* obj,
               bool canonicalize, void** result, size_t* length);

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
  size_t length = 0;

  int argpos = 1;
  int error;

  const char *subscript;
  size_t subscript_len;
  error = ADLB_EXTRACT_HANDLE(objv[argpos++], &td, &subscript,
                                  &subscript_len);
  TCL_CHECK(error);

  if (subscript_len != 0)
  {
    // TODO: handle caching subscripts
    return TCL_OK;
  }

  adlb_data_type adlb_type;
  adlb_type_extra extra;
  error = adlb_type_from_obj_extra(interp, objv, objv[argpos++], &adlb_type,
                              &extra);
  TCL_CHECK(error);

  turbine_type type = (turbine_type) adlb_type;
  TCL_CONDITION(argpos < objc, "not enough arguments");
  error = turbine_tclobj2bin(interp, objv, td, type, extra,
                         objv[argpos++], false, &data, &length);
  TCL_CHECK_MSG(error, "object extraction failed: <%"PRId64">", td);

  TCL_CONDITION(argpos == objc, "extra trailing arguments from %i",
                argpos);

  turbine_code rc = turbine_cache_store(td, type, data, length);
  TURBINE_CHECK(rc, "cache store failed: %"PRId64"", td);

  return TCL_OK;
}

// Allocate a binary buffer and serialize a tcl object into it
//  for the specified ADLB type
static int
turbine_tclobj2bin(Tcl_Interp* interp, Tcl_Obj *const objv[],
               turbine_datum_id td, turbine_type type,
               adlb_type_extra extra, Tcl_Obj* obj,
               bool canonicalize, void** result, size_t* length)
{
  adlb_binary_data data;

  adlb_data_type adlb_type = (adlb_data_type) type;
  int rc = adlb_tclobj2bin(interp, objv, adlb_type, extra, obj, canonicalize,
                          NULL, &data);
  TCL_CHECK_MSG(rc, "failed serializing tcl object to ADLB <%"PRId64">: "
                "\"%s\"", td, Tcl_GetString(obj));

  // Ensure we have ownership of a malloced buffer with the data
  adlb_data_code dc  = ADLB_Own_data(NULL, &data);

  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "allocating binary buffer for "
        "<%"PRId64"> failed: %s", td, Tcl_GetString(obj));

  assert(data.caller_data != NULL);
  *result = data.caller_data;
  *length = data.length;
  return TCL_OK;
}

/* usage: worker_loop <work type> [<keyword arg dict>]
   Repeatedly run units of work from ADLB of provided type
  Optional key-value arguments:
    buffer_size: size of payload buffer in bytes (must be large enough
                                                   for work units)
 */
static int
Turbine_Worker_Loop_Cmd(ClientData cdata, Tcl_Interp* interp,
                        int objc, Tcl_Obj* const objv[])
{
  TCL_CONDITION(objc == 2 || objc == 3, "Need 1 or 2 arguments");

  int work_type;
  int rc = TCL_OK;
  rc = Tcl_GetIntFromObj(interp, objv[1], &work_type);
  TCL_CHECK(rc);

  // Note that ADLB_Get() can give us a bigger buffer
  int buffer_size = TURBINE_ASYNC_EXEC_DEFAULT_BUFFER_SIZE;

  if (objc >= 3)
  {
    int buffer_count = 1; // Deliberately ignored
    rc = worker_keyword_args(interp, objv, objv[2], &buffer_count,
                             &buffer_size);
    TCL_CHECK(rc);
  }

  // Maintain separate buffer from xfer, since xfer may be
  // used in code that we call.
  void* buffer = malloc((size_t)buffer_size);
  TCL_CONDITION(buffer != NULL, "Out of memory");

  turbine_code code =
      turbine_worker_loop(interp, buffer, buffer_size, work_type);

  if (code == TURBINE_ERROR_EXTERNAL)
    // turbine_worker_loop() has added the error info
    rc = TCL_ERROR;
  else
    TCL_CONDITION(code == TURBINE_SUCCESS, "Unknown worker error!");
  free(buffer);
  return rc;
}

/*
 * Process worker keyword arguments.
 * Only modifies arguments if the keyword arg was encountered, i.e.
 * caller should initialise the arguments to their default values.
 *
 * This will validate any values received.
 */
static int
worker_keyword_args(Tcl_Interp *interp, Tcl_Obj *const objv[],
                  Tcl_Obj *dict, int *buffer_count, int *buffer_size) {
  int rc;
  Tcl_DictSearch search;
  Tcl_Obj *key_obj, *val_obj;
  int done;

  rc = Tcl_DictObjFirst(interp, dict, &search, &key_obj, &val_obj,
                        &done);
  TCL_CHECK_MSG(rc, "Error iterating over dict: %s",
                Tcl_GetString(dict));

  for (; !done; Tcl_DictObjNext(&search, &key_obj, &val_obj, &done))
  {
    const char *key = Tcl_GetString(key_obj);
    if (strcmp(key, "buffer_count") == 0)
    {
      rc = Tcl_GetIntFromObj(interp, val_obj, buffer_count);
      TCL_CHECK_MSG(rc, "Expected integer value for buffer_count");

      TCL_CONDITION(*buffer_count >= 0, "Positive value for "
            "buffer_count expected, but got %i", *buffer_count);
    }
    else if (strcmp(key, "buffer_size") == 0)
    {
      rc = Tcl_GetIntFromObj(interp, val_obj, buffer_size);
      TCL_CHECK_MSG(rc, "Expected integer value for buffer_size");

      TCL_CONDITION(*buffer_size >= 0, "Positive value for buffer_size "
                                  "expected, but got %i", *buffer_size);
    }
    else
    {
      TCL_RETURN_ERROR("Invalid key for key-value argument: %s\n", key);
      return TCL_ERROR;
    }
  }

  Tcl_DictObjDone(&search);

  return TCL_OK;
}

int
Turbine_TaskCommInt_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  Tcl_Obj* result =
    Tcl_NewWideIntObj((long long int) turbine_task_comm);
  //printf("TaskCommInt_Cmd(): turbine_task_comm: %lli\n",
  //       (long long int) turbine_task_comm);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Turbine_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  turbine_finalize(interp);
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
  }
  return TCL_OK;
}

/*
  turbine::toint_impl <string>
  Convert decimal string to wide integer
 */
static int
Turbine_ToIntImpl_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  return Turbine_ParseInt_Impl(cdata, interp, objv, objv[1], 10);
}

/*
  turbine::parse_int_impl <string> <base>
  Convert string in any base to wide integer
 */
static int
Turbine_ParseIntImpl_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  int base;
  int rc = Tcl_GetIntFromObj(interp, objv[2], &base);
  TCL_CHECK(rc);

  TCL_CONDITION(base >= 1, "Base must be positive: %i", base);
  return Turbine_ParseInt_Impl(cdata, interp, objv, objv[1], base);
}


static int
Turbine_ParseInt_Impl(ClientData cdata, Tcl_Interp *interp,
                  Tcl_Obj *const objv[], Tcl_Obj *obj, int base)
{
  int len;
  const char *str = Tcl_GetStringFromObj(obj, &len);

  errno = 0; // Reset so we can detect errors
  char *end_str;

  Tcl_WideInt val;

#ifdef TCL_WIDE_INT_IS_LONG
  val = strtol(str, &end_str, base);
#else
  val = strtoll(str, &end_str, base);
#endif

  // Check for errors
  if (errno != 0)
  {
    int my_errno = errno;
    errno = 0; // reset errno
    Tcl_Obj *msg = NULL;
    if (my_errno == ERANGE)
    {
      msg = Tcl_ObjPrintf("toint: Integer representation of '%s' "
              "base %i is out of range of %zi bit integers", str,
              base, sizeof(Tcl_WideInt) * 8);
    }
    else if (my_errno == EINVAL)
    {
      msg = Tcl_ObjPrintf("toint: '%s' cannot be interpreted as an "
                            "base %i integer ", str, base);
    }
    else
    {
      msg = Tcl_ObjPrintf("toint: Internal error: unexpected errno "
                  "%d when converting '%s' to base %i integer",
                  my_errno, str, base);
    }
    Tcl_Obj *msgs[1] = { msg };
    return turbine_user_error(interp, 1, msgs);
  }
  long consumed = end_str - str;
  if (consumed == 0)
  {
    // Handle case where no input consumed
    Tcl_Obj *msgs[1] = { Tcl_ObjPrintf("toint: '%s' cannot be "
             "interpreted as a base %i integer ", str, base) };
    return turbine_user_error(interp, 1, msgs);
  }

  if (consumed < len)
  {
    // Didn't consume all string.  Make sure only whitespace at end
    for (long i = consumed; i < len; i++)
    {
      if (!isspace(str[i]))
      {
        Tcl_Obj *msgs[1] = { Tcl_ObjPrintf("toint: Invalid trailing "
                                           "characters in '%s'", str) };
        return turbine_user_error(interp, 1, msgs);
      }
    }
  }

  Tcl_SetObjResult(interp, Tcl_NewWideIntObj(val));
  return TCL_OK;
}

static void
redirect_error_exit(const char *file, const char *purpose)
{
  fprintf(stderr, "error opening %s for %s: %s\n", file, purpose,
          strerror(errno));
  exit(1);
}

static void
dup2_error_exit(const char *purpose)
{
  fprintf(stderr, "error duplicating file for %s: %s\n", purpose,
          strerror(errno));
  exit(1);
}

static void
close_error_exit(const char *purpose)
{
  fprintf(stderr, "error closing file for %s: %s\n", purpose,
          strerror(errno));
  exit(1);
}

static int pid_status(Tcl_Interp* interp, pid_t child);

static int
Sync_Exec_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  int rc;
  TCL_CONDITION(objc >= 5, "Requires at least 4 arguments");

  const char *stdin_file = Tcl_GetString(objv[1]);
  const char *stdout_file = Tcl_GetString(objv[2]);
  const char *stderr_file = Tcl_GetString(objv[3]);

  char *cmd = Tcl_GetString(objv[4]);

  int cmd_offset = 4;
  int cmd_argc = objc - cmd_offset;
  char *cmd_argv[cmd_argc + 1];
  cmd_argv[0] = cmd;
  for (int i = 1; i < cmd_argc; i++)
    cmd_argv[i] = Tcl_GetString(objv[i + cmd_offset]);
  cmd_argv[cmd_argc] = NULL; // Need to NULL-terminate for execvp()

  pid_t child = fork();
  TCL_CONDITION(child >= 0, "Error forking: %s", strerror(errno));
  if (child == 0)
  {
    // Setup redirects
    if (stdin_file[0] != '\0')
    {
      int in_fd = open(stdin_file, O_RDONLY);
      if (in_fd == -1) redirect_error_exit(stdin_file, "input redirection");

      rc = dup2(in_fd, 0);
      if (rc == -1) dup2_error_exit("input redirection");

      rc = close(in_fd);
      if (rc == -1) close_error_exit("input redirection");
    }

    if (stdout_file[0] != '\0')
    {
      int out_fd = open(stdout_file, O_WRONLY | O_TRUNC | O_CREAT, 0666);
      if (out_fd == -1) redirect_error_exit(stdin_file, "output redirection");

      rc = dup2(out_fd, 1);
      if (rc == -1) dup2_error_exit("output redirection");

      rc = close(out_fd);
      if (rc == -1) close_error_exit("output redirection");
    }

    if (stderr_file[0] != '\0')
    {
      int err_fd = open(stderr_file, O_WRONLY | O_TRUNC | O_CREAT, 0666);
      if (err_fd == -1) redirect_error_exit(stdin_file, "output redirection");

      rc = dup2(err_fd, 2);
      if (rc == -1) dup2_error_exit("output redirection");

      rc = close(err_fd);
      if (rc == -1) close_error_exit("output redirection");
    }

    rc = execvp(cmd, cmd_argv);
    TCL_CONDITION(rc != -1, "Error exec()ing command %s: %s", cmd,
                  strerror(errno));
  }

  return pid_status(interp, child);
}

static int child_error(Tcl_Interp* interp, const char* message);

static int pid_status(Tcl_Interp* interp, pid_t child)
{
  int rc;
  int status;
  char message[1024];
  rc = waitpid(child, &status, 0);
  assert(rc > 0);
  if (WIFEXITED(status))
  {
    int exitcode = WEXITSTATUS(status);
    // printf("exitcode: %i\n", exitcode);

    if (exitcode != 0)
    {
      sprintf(message,
              "Child exited with code: %i", exitcode);
      return child_error(interp, message);
    }
  }
  else if (WIFSIGNALED(status))
  {
    int sgnl = WTERMSIG(status);
    sprintf(message, "Child killed by signal: %i", sgnl);
    return child_error(interp, message);
  }
  else
  {
    printf("TURBINE: UNKNOWN ERROR in pid_status()\n");
    exit(1);
  }

  return TCL_OK;
}

static int child_error(Tcl_Interp* interp, const char* message)
{
  if (tcl_version > 8.5)
  {
    // printf("child_error: \n");
    Tcl_Obj *msgs[1] = { Tcl_ObjPrintf("%s", message) };
    return turbine_user_error(interp, 1, msgs);
  }
  else // Tcl 8.5
  {
    Tcl_AddErrorInfo(interp, message);
    return TCL_ERROR;
  }
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

// We assume SWIG correctly generates these functions
// See the tcl/blob module
int Blob_Init(Tcl_Interp* interp);
// See the tcl/launch module
int Launch_Init(Tcl_Interp* interp);
// See the tcl/python module
int Python_Init(Tcl_Interp* interp);

/*
  turbine::noop_exec_register
 */
static int
Noop_Exec_Register_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  turbine_code tc;
  tc = noop_executor_register();
  TCL_CONDITION(tc == TURBINE_SUCCESS,
                "Could not register noop executor");

  return TCL_OK;
}

/*
  turbine::coaster_register

  Register coaster executor if enabled
 */
static int
Coaster_Register_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

#if HAVE_COASTER == 1
  turbine_code tc;
  tc = coaster_executor_register();
  TCL_CONDITION(tc == TURBINE_SUCCESS,
                "Could not register Coaster executor");

  coaster_log_level threshold = COASTER_LOG_WARN;

  // Turn on debugging based on debug tokens.
  if (turbine_debug_enabled) {
#ifdef ENABLE_DEBUG_COASTER
    // Only enable detailed debugging if coaster debugging on
    threshold = COASTER_LOG_DEBUG;
#else
    threshold = COASTER_LOG_INFO;
#endif
  }

  coaster_rc crc = coaster_set_log_threshold(threshold);
  TCL_CONDITION(crc == COASTER_SUCCESS, "Could not set log threshold");
#endif
  return TCL_OK;
}

/* usage: async_exec_names
   Return list of names of registered async executors
 */
static int
Async_Exec_Names_Cmd(ClientData cdata, Tcl_Interp* interp,
                        int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(1);

  turbine_code tc;

  const int names_size = TURBINE_ASYNC_EXEC_LIMIT;
  const char *names[names_size];
  int n;
  tc = turbine_async_exec_names(names, names_size, &n);
  TCL_CONDITION(tc == TURBINE_SUCCESS, "Error enumerating executors");

  assert(n >= 0 && n <= names_size);

  Tcl_Obj * name_objs[n];

  for (int i = 0; i < n; i++)
  {
    const char *exec_name = names[i];
    assert(exec_name != NULL);

    name_objs[i] = Tcl_NewStringObj(exec_name, -1);
    TCL_CONDITION(name_objs[i] != NULL, "Error allocating string");
  }

  Tcl_SetObjResult(interp, Tcl_NewListObj(n, name_objs));
  return TCL_OK;
}

/* usage: async_exec_configure <executor name> <config string>
   Configure registered executor.
 */
static int
Async_Exec_Configure_Cmd(ClientData cdata, Tcl_Interp* interp,
                        int objc, Tcl_Obj* const objv[])
{
  TCL_ARGS(3);

  turbine_code tc;

  const char *exec_name = Tcl_GetString(objv[1]);
  int config_len;
  const char *config = Tcl_GetStringFromObj(objv[2], &config_len);

  turbine_executor *exec = turbine_get_async_exec(exec_name, NULL);
  TCL_CONDITION(exec != NULL, "Executor %s not registered", exec_name);

  tc = turbine_configure_exec(interp, exec, config, (size_t)config_len);
  TCL_CONDITION(tc == TURBINE_SUCCESS,
      "Could not configure executor %s", exec_name);

  return TCL_OK;
}

/*
  turbine::async_exec_worker_loop <executor name> <adlb work type>
                                  [<keyword arg dict>]
  Optional key-value arguments:
    buffer_count: number of payload buffers to allocate
    buffer_size: size of payload buffers in bytes (must be large enough
                                                   for work units)
 */
static int
Async_Exec_Worker_Loop_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 3 || objc == 4, "Need 2 or 3 arguments");

  int rc;
  turbine_code tc;

  adlb_payload_buf *bufs = NULL;

  const char *exec_name = Tcl_GetString(objv[1]);

  turbine_executor *exec = turbine_get_async_exec(exec_name, NULL);
  TCL_CONDITION(exec != NULL, "Executor %s not registered", exec_name);

  int adlb_work_type;
  rc = Tcl_GetIntFromObj(interp, objv[2], &adlb_work_type);
  TCL_CHECK(rc);

  int buffer_count = TURBINE_ASYNC_EXEC_DEFAULT_BUFFER_COUNT;
  int buffer_size = TURBINE_ASYNC_EXEC_DEFAULT_BUFFER_SIZE;

  if (objc >= 4)
  {
    DEBUG_TURBINE("Keyword args for %s: %s", exec_name,
                  Tcl_GetString(objv[3]));
    rc = worker_keyword_args(interp, objv, objv[3], &buffer_count,
                             &buffer_size);
    TCL_CHECK(rc);
  }

  DEBUG_TURBINE("Allocating %i buffers of %i bytes each for %s",
                buffer_count, buffer_size, exec_name);

  int max_slots;
  tc = turbine_async_exec_max_slots(interp, exec, &max_slots);
  TCL_CONDITION(tc == TURBINE_SUCCESS, "Executor error in %s getting "
                                       "max slots!", exec_name);

  // Only allocate as many buffers as can be used
  if (max_slots >= 1 && max_slots < buffer_count) {
    buffer_count = max_slots;
  }

  bufs = malloc(sizeof(adlb_payload_buf) *
                                  (size_t)buffer_count);
  TCL_MALLOC_CHECK(bufs);

  // Initialize to allow cleanup
  for (int i = 0; i < buffer_count; i++)
  {
    bufs[i].payload = NULL;
  }

  for (int i = 0; i < buffer_count; i++)
  {
    // Maintain separate buffers from xfer, since xfer may be
    // used in code that we call.

    bufs[i].payload = malloc((size_t)buffer_size);
    TCL_MALLOC_CHECK_GOTO(bufs[i].payload, cleanup);
    bufs[i].size = buffer_size;
  }

  tc = turbine_async_worker_loop(interp, exec, adlb_work_type,
                                  bufs, buffer_count);

  if (tc == TURBINE_ERROR_EXTERNAL)
  {
    // turbine_async_worker_loop() has added the error info
   rc = TCL_ERROR;
   goto cleanup;
  }
  else
  {
    TCL_CONDITION_GOTO(tc == TURBINE_SUCCESS, cleanup,
                       "Unknown worker error!");
  }

  rc = TCL_OK;
cleanup:
  if (bufs != NULL)
  {
    for (int i = 0; i < buffer_count; i++)
    {
      free(bufs[i].payload);
    }
    free(bufs);
  }

  return rc;
}

/*
  turbine::noop_exec_run <work string> [<success callback>]
                         [<failure callback>]
 */
static int
Noop_Exec_Run_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 2 && objc <= 4, "Wrong # args");
  turbine_code tc;

  bool started;
  const turbine_executor *noop_exec;
  noop_exec = turbine_get_async_exec(NOOP_EXECUTOR_NAME, &started);
  TCL_CONDITION(noop_exec != NULL, "Noop executor not registered");
  TCL_CONDITION(started, "Noop executor not started");

  char *str;
  int len;
  str = Tcl_GetStringFromObj(objv[1], &len);

  turbine_task_callbacks callbacks;
  callbacks.success.code = NULL;
  callbacks.failure.code = NULL;

  if (objc >= 3)
  {
    callbacks.success.code = objv[2];
  }

  if (objc >= 4)
  {
    callbacks.failure.code = objv[3];
  }

  tc = noop_execute(interp, noop_exec, str, len, callbacks);
  TCL_CONDITION(tc == TURBINE_SUCCESS, "Error executing noop task");

  return TCL_OK;
}

/*
  turbine::coaster_run <executable> <argument list> <infiles>
              <outfiles> <options dict>
              <success callback> <failure callback>
  options dict: optional arguments. Valid keys are:
    stdin/stdout/stderr: redirect output
    job_manager: coaster job manager to use,
                  e.g. "local:slurm" or "local:local"
    staging_mode: staging mode to use
              ("always", "if_present", "on_error", "on_success")
 */
static int
Coaster_Run_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
#if HAVE_COASTER == 0
  TCL_CONDITION(false, "Coaster extension not enabled");
  return TCL_ERROR;
#else
  TCL_CONDITION(objc == 8, "Wrong # args: %i", objc - 1);
  turbine_code tc;
  int rc;

  const turbine_executor *coaster_exec;
  bool started;
  coaster_exec = turbine_get_async_exec(COASTER_EXECUTOR_NAME, &started);
  TCL_CONDITION(coaster_exec != NULL, "Coaster executor not registered");
  TCL_CONDITION(started, "Coaster executor not started");
  const char *executable;
  int executable_len;
  executable = Tcl_GetStringFromObj(objv[1], &executable_len);

  int argc, stageinc, stageoutc;
  const char **argv;
  size_t *arg_lens;
  coaster_stage_entry *stageins, *stageouts;

  rc = turbine_tcllist_to_strings(interp, objv, objv[2], &argc, &argv, &arg_lens);
  TCL_CHECK(rc);

  const char *stdin_s = NULL, *stdout_s = NULL, *stderr_s = NULL;
  size_t stdin_slen = 0, stdout_slen = 0, stderr_slen = 0;

  const char *job_manager;
  size_t job_manager_len;
  tc = coaster_default_job_manager(coaster_exec, &job_manager,
                                   &job_manager_len);
  TCL_CONDITION(tc == TURBINE_SUCCESS, "Error getting coaster default "
                                       "job manager");

  coaster_staging_mode staging_mode = COASTER_DEFAULT_STAGING_MODE;

  rc = parse_coaster_opts(interp, objv, objv[5], &stdin_s, &stdin_slen,
            &stdout_s, &stdout_slen, &stderr_s, &stderr_slen,
            &job_manager, &job_manager_len, &staging_mode);
  TCL_CHECK(rc);

  // Parse stages after we know staging mode
  rc = parse_coaster_stages(interp, objv, objv[3], staging_mode,
                    &stageinc, &stageins);
  TCL_CHECK(rc);

  rc = parse_coaster_stages(interp, objv, objv[4], staging_mode,
                    &stageoutc, &stageouts);
  TCL_CHECK(rc);

  turbine_task_callbacks callbacks;
  callbacks.success.code = objv[6];
  callbacks.failure.code = objv[7];

  coaster_job *job;
  coaster_rc crc;

  DEBUG_COASTER("Coaster jobManager: %.*s", (int)job_manager_len,
                 job_manager);
  crc = coaster_job_create(executable, (size_t)executable_len, argc,
                argv, arg_lens, job_manager, job_manager_len, &job);
  TCL_CONDITION(crc == COASTER_SUCCESS, "Error constructing coaster job: "
                "%s", coaster_last_err_info());

  if (stageinc > 0 || stageoutc > 0)
  {
    crc = coaster_job_add_stages(job, stageinc, stageins,
                                stageoutc, stageouts);
    TCL_CONDITION(crc == COASTER_SUCCESS, "Error adding coaster stages: "
                "%s", coaster_last_err_info());
  }

  if (stdin_s != NULL || stdout_s != NULL || stderr_s != NULL)
  {
    crc = coaster_job_set_redirects(job, stdin_s, stdin_slen,
                stdout_s, stdout_slen, stderr_s, stderr_slen);
    TCL_CONDITION(crc == COASTER_SUCCESS, "Error adding coaster stages: "
                "%s", coaster_last_err_info())

  }

  // Cleanup memory before execution
  void *alloced[] = {argv, arg_lens, stageins, stageouts};
  int alloced_count = (int)(sizeof(alloced) / sizeof(alloced[0]));

  for (int i = 0; i < alloced_count; i++)
  {
    if (alloced[i] != NULL)
    {
      free(alloced[i]);
    }
  }

  tc = coaster_execute(interp, coaster_exec, job, callbacks);
  TCL_CONDITION(tc == TURBINE_SUCCESS, "Error executing coaster task");

  return TCL_OK;
#endif
}

int turbine_tcllist_to_strings(Tcl_Interp *interp, Tcl_Obj *const objv[],
      Tcl_Obj *list, int *count, const char ***strs, size_t **str_lens)
{
  int rc;

  Tcl_Obj **objs;
  rc = Tcl_ListObjGetElements(interp, list, count, &objs);
  TCL_CHECK(rc);

  if (*count > 0)
  {
    *strs = malloc(sizeof((*strs)[0]) * (size_t)*count);
    TCL_MALLOC_CHECK(*strs);
    *str_lens = malloc(sizeof((*str_lens)[0]) * (size_t)*count);
    TCL_MALLOC_CHECK(*str_lens);

    for (int i = 0; i < *count; i++)
    {
      int tmp_len;
      (*strs)[i] = Tcl_GetStringFromObj(objs[i], &tmp_len);
      (*str_lens)[i] = (size_t)tmp_len;
    }
  }
  else
  {
    *strs = NULL;
    *str_lens = NULL;
  }
  return TCL_OK;
}

#if HAVE_COASTER == 1
static int parse_coaster_stages(Tcl_Interp *interp, Tcl_Obj *const objv[],
      Tcl_Obj *list, coaster_staging_mode staging_mode,
      int *count, coaster_stage_entry **stages)
{
  int rc;

  Tcl_Obj **objs;
  rc = Tcl_ListObjGetElements(interp, list, count, &objs);
  TCL_CHECK(rc);

  if (*count > 0)
  {
    *stages = malloc(sizeof((*stages)[0]) * (size_t)*count);
    TCL_MALLOC_CHECK(*stages);

    for (int i = 0; i < *count; i++)
    {
      coaster_stage_entry *e = &(*stages)[i];
      int tmp_len;
      e->src = Tcl_GetStringFromObj(objs[i], &tmp_len);
      e->src_len = (size_t)tmp_len;
      e->dst = e->src;
      e->dst_len = e->src_len;
      e->mode = staging_mode;
    }
  }
  else
  {
    *stages = NULL;
  }
  return TCL_OK;
}

/*
  Parse options dictionary.  Modifies arguments if keys found,
  otherwise leaves them unmodified
 */
static int parse_coaster_opts(Tcl_Interp *interp, Tcl_Obj *const objv[],
      Tcl_Obj *dict, const char **stdin_s, size_t *stdin_slen,
      const char **stdout_s, size_t *stdout_slen,
      const char **stderr_s, size_t *stderr_slen,
      const char **job_manager, size_t *job_manager_len,
      coaster_staging_mode *staging_mode)
{
  int rc;

  Tcl_DictSearch search;
  Tcl_Obj *key, *value;
  int done;

  rc = Tcl_DictObjFirst(interp, dict, &search, &key, &value, &done);
  TCL_CHECK(rc);

  for (; !done ; Tcl_DictObjNext(&search, &key, &value, &done)) {
    const char *key_s;
    int key_len;
    int tmp_len;

    key_s = Tcl_GetStringFromObj(key, &key_len);

    if (key_len == 5 && memcmp(key_s, "stdin", 5) == 0)
    {
      *stdin_s = Tcl_GetStringFromObj(value, &tmp_len);
      *stdin_slen = (size_t)tmp_len;
      continue;
    }
    else if (key_len == 6)
    {
      if (memcmp(key_s, "stdout", 6) == 0)
      {
        *stdout_s = Tcl_GetStringFromObj(value, &tmp_len);
        *stdout_slen = (size_t)tmp_len;
        continue;
      }
      else if (memcmp(key_s, "stderr", 6) == 0)
      {
        *stderr_s = Tcl_GetStringFromObj(value, &tmp_len);
        *stderr_slen = (size_t)tmp_len;
        continue;
      }
    }
    else if (key_len == 11 && memcmp(key_s, "job_manager", 11) == 0)
    {
      *job_manager = Tcl_GetStringFromObj(value, &tmp_len);
      *job_manager_len = (size_t)tmp_len;
      continue;
    }
    else if (key_len == 12 && memcmp(key_s, "staging_mode", 12) == 0)
    {
      const char *staging_mode_s = Tcl_GetString(value);
      bool valid_staging_mode = false;
      for (int i = 0; i < num_staging_modes; i++) {
        if (strcmp(staging_mode_s, staging_modes[i].name) == 0) {
          *staging_mode = staging_modes[i].mode;
          valid_staging_mode = true;
          break;
        }
      }
      TCL_CONDITION(valid_staging_mode, "Unknown Coaster staging mode: "
                    "%s", staging_mode_s);
      continue;
    }
    TCL_CONDITION(false, "Unknown coaster key: %.*s", key_len, key_s);
  }
  Tcl_DictObjDone(&search);
  return TCL_OK;
}
#endif

static int
Turbine_CopyTo_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);
  Tcl_WideInt comm_int;
  int rc = Tcl_GetWideIntFromObj(interp, objv[1], &comm_int);
  TCL_CHECK_MSG(rc, "Not an integer: %s", Tcl_GetString(objv[1]));
  const char* name_in  = Tcl_GetString(objv[2]);
  const char* name_out = Tcl_GetString(objv[3]);

  MPI_Comm comm = (MPI_Comm) comm_int;
  bool result = turbine_io_copy_to(comm, name_in, name_out);
  TCL_CONDITION(result, "Could not copy: %s to %s",
                name_in, name_out);
  return TCL_OK;
}

static int
Turbine_Bcast_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  // Unpack
  // ARGS: comm root variable_name
  TCL_ARGS(4);
  int rc;
  Tcl_WideInt comm_int;
  rc = Tcl_GetWideIntFromObj(interp, objv[1], &comm_int);
  TCL_CHECK_MSG(rc, "Not an integer: %s", Tcl_GetString(objv[1]));
  int root;
  rc = Tcl_GetIntFromObj(interp, objv[2], &root);
  TCL_CHECK_MSG(rc, "Not an integer: %s", Tcl_GetString(objv[2]));
  MPI_Comm comm = (MPI_Comm) comm_int;
  char* name  = Tcl_GetString(objv[3]);

  // Switch on bcast root
  char* s;
  int rank;
  MPI_Comm_rank(comm, &rank);
  if (rank == root)
    s = (char*) Tcl_GetVar(interp, name, EMPTY_FLAG);

  // Execute
  int length;
  bool b = turbine_io_bcast(comm, &s, &length);
  TCL_CONDITION(b, "Broadcast failed!")

  // Return
  if (rank != root)
    Tcl_SetVar(interp, name, s, EMPTY_FLAG);
  return TCL_OK;
}

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
  tcl_jvm_init(interp);
  tcl_mpe_init(interp);
  tcl_julia_init(interp);
  tcl_python_init(interp);
  tcl_r_init(interp);
  Blob_Init(interp);
  Launch_Init(interp);
  Python_Init(interp);

  COMMAND("init",        Turbine_Init_Cmd);
  COMMAND("init_debug",  Turbine_Init_Debug_Cmd);
  COMMAND("version",     Turbine_Version_Cmd);
  COMMAND("rule",        Turbine_Rule_Cmd);
  COMMAND("ruleopts",    Turbine_RuleOpts_Cmd);
  COMMAND("log",         Turbine_Log_Cmd);
  COMMAND("log_time",    Turbine_LogTime_Cmd);
  COMMAND("normalize",   Turbine_Normalize_Cmd);
  COMMAND("worker_loop", Turbine_Worker_Loop_Cmd);
  COMMAND("cache_check", Turbine_Cache_Check_Cmd);
  COMMAND("cache_retrieve", Turbine_Cache_Retrieve_Cmd);
  COMMAND("cache_store", Turbine_Cache_Store_Cmd);
  COMMAND("task_comm_int", Turbine_TaskCommInt_Cmd);
  COMMAND("finalize",    Turbine_Finalize_Cmd);
  COMMAND("debug_on",    Turbine_Debug_On_Cmd);
  COMMAND("debug",       Turbine_Debug_Cmd);
  COMMAND("toint_impl", Turbine_ToIntImpl_Cmd);
  COMMAND("parse_int_impl", Turbine_ParseIntImpl_Cmd);

  COMMAND("sync_exec", Sync_Exec_Cmd);

  COMMAND("async_exec_names", Async_Exec_Names_Cmd);
  COMMAND("async_exec_configure", Async_Exec_Configure_Cmd);
  COMMAND("async_exec_worker_loop", Async_Exec_Worker_Loop_Cmd);

  COMMAND("noop_exec_register", Noop_Exec_Register_Cmd);
  COMMAND("noop_exec_run", Noop_Exec_Run_Cmd);

  COMMAND("coaster_register", Coaster_Register_Cmd);
  COMMAND("coaster_run", Coaster_Run_Cmd);

  COMMAND("bcast",   Turbine_Bcast_Cmd);
  COMMAND("copy_to", Turbine_CopyTo_Cmd);

  Tcl_Namespace* turbine =
    Tcl_FindNamespace(interp, "::turbine::c", NULL, 0);
  Tcl_Export(interp, turbine, "*", 0);

  set_namespace_constants(interp);

  return TCL_OK;
}
