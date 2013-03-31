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
 * turbine.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 *
 * TD means Turbine Datum, which is a variable id stored in ADLB
 * TR means TRansform, the in-memory record from a rule
 * */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>

#include <adlb.h>

#include <c-utils.h>
#include <list.h>
#include <log.h>
#include <table.h>
#include <table_lp.h>
#include <tools.h>

#include "src/util/debug.h"

#include "turbine-version.h"
#include "cache.h"
#include "turbine.h"

typedef enum
{
  /** Waiting for inputs */
  TRANSFORM_WAITING,
  /** Inputs ready */
  TRANSFORM_READY,
  /** Application level has received this TR as ready */
  TRANSFORM_RETURNED
} transform_status;

/**
   In-memory structure resulting from Turbine rule statement
 */
typedef struct
{
  turbine_transform_id id;
  /** Name for human debugging */
  char* name;
  /** Tcl string to evaluate when inputs are ready */
  char* action;
  /** Number of inputs */
  int inputs;
  /** Array of input TDs */
  turbine_datum_id* input_list;
  turbine_action_type action_type;
  /** ADLB priority for this action */
  int priority;
  /** ADLB target rank for this action */
  int target;
  /** Closed inputs - bit vector */
  unsigned char *closed_inputs;
  /** Index of next subscribed input (starts at 0) */
  int blocker;
  transform_status status;
} transform;

static int bitfield_size(int inputs);

// Check if input closed
static inline bool input_closed(transform *T, int i);

static inline void mark_input_closed(transform *T, int i);

/**
   Has turbine_init() been called successfully?
*/
static bool initialized = false;

/**
   Waiting transforms
   Map from transform id to transform
 */
struct table_lp transforms_waiting;

/**
   Ready transforms
 */
struct list transforms_ready;

/**
   Transforms whose IDs have been returned to application
   We retain these until the application pops them
   Map from transform ID to transform
 */
struct table_lp transforms_returned;

/**
   TD inputs blocking their transforms
   Map from TD ID to list of transforms
 */
struct table_lp td_blockers;

/**
  TDs to which this engine has subscribed, used to avoid
  subscribing multiple times
 */
struct table_lp td_subscribed;

#define turbine_check(code) if (code != TURBINE_SUCCESS) return code;

#define turbine_check_verbose(code) \
    turbine_check_verbose_impl(code, __FILE__, __LINE__)

#define turbine_check_verbose_impl(code, file, line)    \
  { if (code != TURBINE_SUCCESS)                        \
    {                                                   \
      char output[64];                                  \
      turbine_code_tostring(output, code);              \
      printf("turbine error: %s\n", output);            \
      printf("\t at: %s:%i\n", file, line);             \
      return code;                                      \
    }                                                   \
  }

/**
   Globally unique transform ID for new rules
   Starts at mpi_rank, incremented by mpi_size, thus unique
 */
static long transform_unique_id = -1;

static int mpi_size = -1;
static int mpi_rank = -1;

#define turbine_condition(condition, code, format, args...) \
  { if (! (condition))                                      \
    {                                                       \
       printf(format, ## args);                             \
       return code;                                         \
    }}

static void
check_versions()
{
  version tv, av, rav, cuv, rcuv;
  turbine_version(&tv);
  ADLB_Version(&av);
  // Required ADLB version:
  version_parse(&rav, ADLB_REQUIRED_VERSION);
  c_utils_version(&cuv);
  // Required c-utils version:
  version_parse(&rcuv, C_UTILS_REQUIRED_VERSION);
  version_require("Turbine", &tv, "c-utils", &cuv, &rcuv);
  version_require("Turbine", &tv, "ADLB",    &av,  &rav);
}

/**
   This is a separate function so we can set a function breakpoint
 */
static void
gdb_sleep(int* t, int i)
{
  sleep(1);
  DEBUG_TURBINE("gdb_check: %i %i\n", *t, i);
}

/**
   Allows user to launch Turbine in a loop until a debugger attaches
 */
static void
gdb_check(int rank)
{
  int gdb_rank;
  char* s = getenv("GDB_RANK");
  if (s != NULL &&
      strlen(s) > 0)
  {
    int c = sscanf(s, "%i", &gdb_rank);
    if (c != 1)
    {
      printf("Invalid GDB_RANK: %s\n", s);
      exit(1);
    }
    if (gdb_rank == rank)
    {
      pid_t pid = getpid();
      printf("Waiting for gdb: rank: %i pid: %i\n", rank, pid);
      int t = 0;
      int i = 0;
      while (!t)
        gdb_sleep(&t, i++);
    }
  }
}

static bool setup_cache(void);

turbine_code
turbine_init(int amserver, int rank, int size)
{
  check_versions();

  gdb_check(rank);

  if (amserver)
    return TURBINE_SUCCESS;

  mpi_size = size;
  mpi_rank = rank;
  transform_unique_id = rank+mpi_size;
  initialized = true;

  bool b = setup_cache();
  if (!b) return TURBINE_ERROR_NUMBER_FORMAT;

  return TURBINE_SUCCESS;
}

static bool
setup_cache()
{
  int size;
  unsigned long max_memory;
  bool b;

  b = getenv_integer("TURBINE_CACHE_SIZE", 1024, &size);
  if (!b)
  {
    printf("malformed integer in environment: TURBINE_CACHE_SIZE\n");
    return false;
  }
  if (mpi_rank == 0)
    DEBUG_TURBINE("TURBINE_CACHE_SIZE: %i", size);
  b = getenv_ulong("TURBINE_CACHE_MAX", 10*1024*1024, &max_memory);
  if (!b)
  {
    printf("malformed integer in environment: TURBINE_CACHE_MAX\n");
    return false;
  }
  if (mpi_rank == 0)
    DEBUG_TURBINE("TURBINE_CACHE_MAX: %lu", max_memory);

  turbine_cache_init(size, max_memory);

  return true;
}

turbine_code
turbine_engine_init()
{
  bool result;
  result = table_lp_init(&transforms_waiting, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;

  list_init(&transforms_ready);
  result = table_lp_init(&transforms_returned, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  result = table_lp_init(&td_blockers, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  
  result = table_lp_init(&td_subscribed, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;

  return TURBINE_SUCCESS;
}

void
turbine_version(version* output)
{
  version_parse(output, TURBINE_VERSION);
}

static inline long
make_unique_id()
{
  long result = transform_unique_id;
  transform_unique_id += mpi_size;
  return result;
}

static inline turbine_code
transform_create(const char* name,
                 int inputs, const turbine_datum_id* input_list,
                 turbine_action_type action_type,
                 const char* action, int priority, int target,
                 transform** result)
{
  assert(name);
  assert(action);

  if (strlen(action) > TURBINE_ACTION_MAX)
  {
    printf("error: turbine rule action string storage exceeds %i\n",
           TURBINE_ACTION_MAX);
    *result = NULL;
    return TURBINE_ERROR_INVALID;
  }

  transform* T = malloc(sizeof(transform));

  T->id = make_unique_id();
  T->name = strdup(name);
  T->action_type = action_type;
  T->action = strdup(action);
  T->priority = priority;
  T->target = target;
  T->blocker = 0;

  if (inputs > 0)
  {
    T->input_list = malloc(inputs*sizeof(turbine_datum_id));
    T->closed_inputs = malloc(bitfield_size(inputs)*sizeof(unsigned char));
    if (! T->input_list || ! T->closed_inputs)
      return TURBINE_ERROR_OOM;
  }
  else {
    T->input_list = NULL;
    T->closed_inputs = NULL;
  }

  T->inputs = inputs;
  for (int i = 0; i < inputs; i++) {
    T->input_list[i] = input_list[i];
  }
  for (int i =0; i < bitfield_size(inputs); i++) {
    T->closed_inputs[i] = 0;
  }

  T->status = TRANSFORM_WAITING;

  *result = T;
  return TURBINE_SUCCESS;
}

static inline void
transform_free(transform* T)
{
  free(T->name);
  if (T->action)
    free(T->action);
  if (T->input_list)
    free(T->input_list);
  free(T);
}

/**
 * Return true if subscribed, false if data already set
 */
static inline turbine_code
subscribe(turbine_transform_id id, bool *result)
{
  if (table_lp_search(&td_subscribed, id) != NULL) {
    // Already subscribed
    *result = true;
    return TURBINE_SUCCESS;
  }
  int subscribed;
  adlb_code rc = ADLB_Subscribe(id, &subscribed);

  if (rc != ADLB_SUCCESS) {
    log_printf("ADLB_Subscribe on <%ld> failed with code: %d\n", id, rc);
    return rc; // Turbine codes are same as ADLB data codes
  }

  if (subscribed != 0) {
    // Record it was subscribed
    table_lp_add(&td_subscribed, id, (void*)1);
  }
  *result = subscribed != 0;
  return TURBINE_SUCCESS;
}

static int transform_tostring(char* output,
                              transform* transform);

#ifdef ENABLE_DEBUG_TURBINE
#define DEBUG_TURBINE_RULE(transform, id) {         \
    char tmp[1024];                                     \
    transform_tostring(tmp, transform);                 \
    DEBUG_TURBINE("rule: %s {%li}", tmp, id);     \
  }
#else
#define DEBUG_TURBINE_RULE(transform, id)
#endif

static inline turbine_code progress(transform* T, bool* subscribed);
static inline void rule_inputs(transform* T);

turbine_code
turbine_rule(const char* name,
             int inputs, const turbine_datum_id* input_list,
             turbine_action_type action_type,
             const char* action,
             int priority,
             int target,
             turbine_transform_id* id)
{
  transform* T = NULL;
  turbine_code code = transform_create(name, inputs, input_list,
                                       action_type, action,
                                       priority, target, &T);

  *id = T->id;
  turbine_check(code);

  rule_inputs(T);

  bool subscribed;
  turbine_code tc = progress(T, &subscribed);
  if (tc != TURBINE_SUCCESS) {
    DEBUG_TURBINE("turbine_rule failed\n");
    DEBUG_TURBINE_RULE(T, *id);
    return tc;
  }
  
  DEBUG_TURBINE_RULE(T, *id);

  if (subscribed)
  {
    table_lp_add(&transforms_waiting, *id, T);
  }
  else
  {
    list_add(&transforms_ready, T);
  }

  return TURBINE_SUCCESS;
}

static inline turbine_code declare_datum(turbine_datum_id id,
                                         struct list_l** result);

/**
   Record that this transform is blocked by its inputs
*/
static inline void
rule_inputs(transform* T)
{
  for (int i = 0; i < T->inputs; i++)
  {
    turbine_datum_id id = T->input_list[i];
    struct list_l* L = table_lp_search(&td_blockers, id);
    if (L == NULL)
      declare_datum(id, &L);
    list_l_add(L, T->id);
  }
}

/**
   Remove the transforms from waiting and add to ready list
   Empties given list along the way
 */
static void
add_to_ready(struct list* tmp)
{
  transform* T;
  while ((T = list_poll(tmp)))
  {
    void* c = table_lp_remove(&transforms_waiting, T->id);
    ASSERT(c != NULL);
    list_add(&transforms_ready, T);
  }
}

/**
   Push transforms that are ready into trs_ready
   @return
*/
turbine_code
turbine_rules_push()
{
  // Temporary holding list for transforms moving into ready list
  struct list tmp;
  list_init(&tmp);

  for (int i = 0; i < transforms_waiting.capacity; i++)
    for (struct list_lp_item* item = transforms_waiting.array[i].head;
         item; item = item->next)
    {
      transform* T = item->data;
      assert(T);
      bool subscribed;
      turbine_code tc = progress(T, &subscribed);
      if (tc != TURBINE_SUCCESS) {
        return tc;
      }

      if (!subscribed)
      {
        DEBUG_TURBINE("not subscribed on: %li\n", T->id);
        list_add(&tmp, T);
      }
    }

  add_to_ready(&tmp);

  return TURBINE_SUCCESS;
}

/**
   Declare a new data id
   @param result If non-NULL, return the new blocked list here
 */
static inline turbine_code
declare_datum(turbine_datum_id id, struct list_l** result)
{
  assert(initialized);
  // DEBUG_TURBINE("declare: %li\n", id);
  struct list_l* blocked = list_l_create();
  if (table_lp_contains(&td_blockers, id))
    return TURBINE_ERROR_DOUBLE_DECLARE;
  table_lp_add(&td_blockers, id, blocked);
  if (result != NULL)
    *result = blocked;
  return TURBINE_SUCCESS;
}

turbine_code
turbine_ready(int count, turbine_transform_id* output,
              int* result)
{
  int i = 0;
  void* v;
  DEBUG_TURBINE("ready:");
  while (i < count && (v = list_poll(&transforms_ready)))
  {
    transform* T = (transform*) v;
    table_lp_add(&transforms_returned, T->id, T);
    output[i] = T->id;
    DEBUG_TURBINE("\t %li", output[i]);
    i++;
  }
  *result = i;
  return TURBINE_SUCCESS;
}

turbine_code
turbine_pop(turbine_transform_id id, turbine_action_type* action_type,
            char** action, int* priority, int* target)
{
  // Check inputs
  if (id == TURBINE_ID_NULL)
     return TURBINE_ERROR_NULL;
  transform* T = table_lp_remove(&transforms_returned, id);
  if (!T)
    return TURBINE_ERROR_NOT_FOUND;

  // Debugging
  DEBUG_TURBINE("pop: transform: {%li}", id);
  DEBUG_TURBINE("\t action:   {%li} %s: %s", id, T->name, T->action);
  DEBUG_TURBINE("\t priority: {%li} => %i\n", id, T->priority);
  DEBUG_TURBINE("\t target:   {%li} => %i\n", id, T->target);

  // Copy outputs
  *action_type = T->action_type;
  *action = T->action;
  T->action = NULL;
  *priority = T->priority;
  *target = T->target;

  // Clean up
  transform_free(T);

  return TURBINE_SUCCESS;
}

/**
   TODO: This does not remove tds from td_blockers!
 */
turbine_code
turbine_close(turbine_datum_id id)
{
  // Record no longer subscribed
  table_lp_remove(&td_subscribed, id);

  // Look up transforms that this td was blocking
  struct list_l* L = table_lp_search(&td_blockers, id);
  if (L == NULL)
    // We don't have any rules that block on this td
    return TURBINE_SUCCESS;

  // Temporary holding spot for transforms moving into ready list
  struct list tmp;
  list_init(&tmp);

  // Try to make progress on those transforms
  for (struct list_l_item* item = L->head; item; item = item->next)
  {
    turbine_transform_id transform_id = item->data;
    transform* T = table_lp_search(&transforms_waiting, transform_id);
    if (!T)
      continue;

    // update closed vector
    for (int i = T->blocker; i < T->inputs; i++) {
      if (T->input_list[i] == id) {
        mark_input_closed(T, i);
      }
    }

    bool subscribed;
    turbine_code tc = progress(T, &subscribed);
    if (tc != TURBINE_SUCCESS)
      return tc;

    if (!subscribed)
    {
      DEBUG_TURBINE("ready: {%li}", transform_id);
      list_add(&tmp, T);
    }
  }

  add_to_ready(&tmp);

  return TURBINE_SUCCESS;
}

/**
 * Make progress on transform. Provided is a list of
 * ids that are closed.  We contact server to check
 * status of any IDs not in list.
 */
static inline turbine_code
progress(transform* T, bool* subscribed)
{
  *subscribed = false;
  for (; T->blocker < T->inputs; T->blocker++)
  {
    if (!input_closed(T, T->blocker))
    {
      // Contact server to check if available
      turbine_datum_id td = T->input_list[T->blocker];
      turbine_code tc = subscribe(td, subscribed);
      if (tc != TURBINE_SUCCESS) {
        return tc;
      }
      if (*subscribed) {
        // Need to block on this id
        return TURBINE_SUCCESS;
      }
    }
  }
  // Ready to run
  *subscribed = false;
  return TURBINE_SUCCESS;
}

/**
   @param output Should point to good storage for output,
   at least 64 chars
   @return Number of characters written
*/
int
turbine_code_tostring(char* output, turbine_code code)
{
  int result = -1;
  switch (code)
  {
    case TURBINE_SUCCESS:
      result = sprintf(output, "TURBINE_SUCCESS");
      break;
    case TURBINE_ERROR_OOM:
      result = sprintf(output, "TURBINE_ERROR_OOM");
      break;
    case TURBINE_ERROR_DOUBLE_DECLARE:
      result = sprintf(output, "TURBINE_ERROR_DOUBLE_DECLARE");
      break;
    case TURBINE_ERROR_DOUBLE_WRITE:
      result = sprintf(output, "TURBINE_ERROR_DOUBLE_WRITE");
      break;
    case TURBINE_ERROR_UNSET:
      result = sprintf(output, "TURBINE_ERROR_UNSET");
      break;
    case TURBINE_ERROR_NOT_FOUND:
      result = sprintf(output, "TURBINE_ERROR_NOT_FOUND");
      break;
    case TURBINE_ERROR_NUMBER_FORMAT:
      result = sprintf(output, "TURBINE_ERROR_NUMBER_FORMAT");
      break;
    case TURBINE_ERROR_INVALID:
      result = sprintf(output, "TURBINE_ERROR_INVALID");
      break;
    case TURBINE_ERROR_NULL:
      result = sprintf(output, "TURBINE_ERROR_NULL");
      break;
    case TURBINE_ERROR_UNKNOWN:
      result = sprintf(output, "TURBINE_ERROR_UNKNOWN");
      break;
    case TURBINE_ERROR_TYPE:
      result = sprintf(output, "TURBINE_ERROR_TYPE");
      break;
    default:
      sprintf(output, "<could not convert code to string>");
      break;
  }
  return result;
}

static void action_type_tostring(turbine_action_type action_type,
                                 char* output);

static int
transform_tostring(char* output, transform* t)
{
  int result = 0;
  char* p = output;

  char action_type_string[16];
  action_type_tostring(t->action_type, action_type_string);

  append(p, "%s ", t->name);
  append(p, "%s ", action_type_string);
  append(p, "(");
  for (int i = 0; i < t->inputs; i++)
  {
    // Highlight the blocking variable
    if (i == t->blocker)
      append(p, "/%li/", t->input_list[i]);
    else
      append(p, "%li", t->input_list[i]);
    if (i < t->inputs-1)
      append(p, " ");
  }
  append(p, ")");

  result = p - output;
  return result;
}

static inline bool
input_closed(transform *T, int i)
{
  unsigned char field = T->closed_inputs[i / 8];
  return (field >> (i % 8)) & 0x1;
}

// Extract bit from closed_inputs
static inline void
mark_input_closed(transform *T, int i)
{
  unsigned char mask = 0x1 << (i % 8);
  T->closed_inputs[i / 8] |= mask;
}

static int
bitfield_size(int inputs) {
  if (inputs <= 0)
    return 0;
  // Round up to nearest multiple of 8
  return (inputs - 1) / 8 + 1;
}

/**
   Convert given action_type to string representation
*/
static void
action_type_tostring(turbine_action_type action_type, char* output)
{
  char* s = NULL;
  switch (action_type)
  {
    case TURBINE_ACTION_LOCAL:
      s = "LOCAL";
      break;
    case TURBINE_ACTION_CONTROL:
      s = "CONTROL";
      break;
    case TURBINE_ACTION_WORK:
      s = "WORK";
      break;
  }
  if (s == NULL)
  {
    printf("action_type_tostring(): unknown: %i\n", action_type);
    exit(1);
  }
  strcpy(output, s);
}

static void
info_waiting()
{
  printf("WAITING TRANSFORMS: %i\n", transforms_waiting.size);
  char buffer[1024];
  for (int i = 0; i < transforms_waiting.capacity; i++)
    for (struct list_lp_item* item = transforms_waiting.array[i].head;
         item; item = item->next)
    {
      transform* t = item->data;
      char id_string[24];
      sprintf(id_string, "{%li}", t->id);
      int c = sprintf(buffer, "%10s ", id_string);
      transform_tostring(buffer+c, t);
      printf("TRANSFORM: %s\n", buffer);
    }
}

void
turbine_finalize()
{
  turbine_cache_finalize();
  if (transforms_waiting.size != 0)
    info_waiting();
}
