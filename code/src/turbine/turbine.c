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

#include <stdint.h>
#include <inttypes.h>

#include <adlb.h>

#include <c-utils.h>
#include <list.h>
#include <log.h>
#include <table.h>
#include <table_bp.h>
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

  /** Number of input tds */
  int input_tds;
  /** Array of input TDs */
  turbine_datum_id* input_td_list;

  /** Number of input TD/subscript pairs */
  int input_td_subs;
  /** Array of input TD/subscript pairs */
  td_sub_pair* input_td_sub_list;

  turbine_action_type action_type;
  /** ADLB priority for this action */
  int priority;
  /** ADLB target rank for this action */
  int target;
  /** ADLB task parallelism */
  int parallelism;
  /** Closed inputs - bit vector for both tds and td/sub pairs */
  unsigned char *closed_inputs;
  /** Index of next subscribed input (starts at 0 in input_td list,
      continues into input_td_sub_list) */
  int blocker;
  transform_status status;
} transform;

MPI_Comm turbine_task_comm = MPI_COMM_NULL;

static size_t bitfield_size(int inputs);

// Check if input closed
static inline bool input_td_closed(transform *T, int i);
static inline void mark_input_td_closed(transform *T, int i);
static inline bool input_td_sub_closed(transform *T, int i);
static inline void mark_input_td_sub_closed(transform *T, int i);

// Update transforms after close
static turbine_code
turbine_close_update(struct list_l *blocked, turbine_datum_id id,
                     const void *subscript, size_t subscript_len);

// Finalize engine
static void turbine_engine_finalize(void);

/**
   Has turbine_init() been called successfully?
*/
static bool initialized = false;

/** Has turbine_engine_init() been called? */
bool turbine_engine_initialized = false;

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
   TD inputs blocking their transforms
   Map from TD ID to list of transforms
 */
struct table_lp td_blockers;

/**
   ID/subscript pairs blocking transforms
   Map from ID/subscript pair to list of transforms
 */
struct table_bp td_sub_blockers;

/**
  TDs to which this engine has subscribed, used to avoid
  subscribing multiple times
 */
struct table_lp td_subscribed;

/**
  TD/subscript pairs to which engine is subscribed.  Key is created using
   write_id_sub_key function
 */
struct table_bp td_sub_subscribed;

// Maximum length of buffer required for key
#define ID_SUB_KEY_MAX (ADLB_DATA_SUBSCRIPT_MAX + 30)

// Return size of buffer to use for id/subscript pair.
// Zero-length buffer if no subscript
static inline size_t
id_sub_key_buflen(const void *subscript, size_t length)
{
  if (subscript == NULL)
    return 0;
  size_t res = (sizeof(turbine_datum_id) + length);
  assert(res <= ID_SUB_KEY_MAX);
  return res;
}

static inline size_t
write_id_sub_key(char *buf, turbine_datum_id id,
                 const void *subscript, size_t length)
{
  assert(subscript != NULL);
  memcpy(buf, &id, sizeof(id));
  memcpy(&buf[sizeof(id)], subscript, length);
  return id_sub_key_buflen(subscript, length);
}

static inline adlb_subscript sub_convert(turbine_subscript sub)
{
  adlb_subscript asub = { .key = sub.key, .length = sub.length };
  return asub;
}

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
  if (!initialized)
    return TURBINE_ERROR_UNINITIALIZED;

  bool result;
  result = table_lp_init(&transforms_waiting, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;

  result = table_lp_init(&td_blockers, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  
  result = table_bp_init(&td_sub_blockers, 1024); // Will expand
  if (!result)
    return TURBINE_ERROR_OOM;

  result = table_lp_init(&td_subscribed, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;

  result = table_bp_init(&td_sub_subscribed, 1024); // Will expand
  if (!result)
    return TURBINE_ERROR_OOM;

  turbine_engine_initialized = true;
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
             int input_tds, const turbine_datum_id* input_td_list,
             int input_td_subs, const td_sub_pair* input_td_sub_list,
             turbine_action_type action_type,
             const char* action,
             int priority, int target, int parallelism,
             transform** result)
{
  assert(name);
  assert(action);
  assert(input_tds >= 0);
  assert(input_tds == 0 || input_td_list != NULL);
  assert(input_td_subs >= 0);
  assert(input_td_subs == 0 || input_td_sub_list != NULL);

  if (strlen(action) > TURBINE_ACTION_MAX)
  {
    printf("error: turbine rule action string storage exceeds %i\n",
           TURBINE_ACTION_MAX);
    *result = NULL;
    return TURBINE_ERROR_INVALID;
  }
  if (parallelism <= 0)
  {
    printf("error: turbine rule parallelism must be >0 \n");
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
  T->parallelism = parallelism;
  T->blocker = 0;
  T->input_tds = input_tds;
  T->input_td_subs = input_td_subs;

  if (input_tds > 0)
  {
    size_t sz = (size_t)input_tds*sizeof(turbine_datum_id);
    T->input_td_list = malloc(sz);

    if (! T->input_td_list)
      return TURBINE_ERROR_OOM;

    memcpy(T->input_td_list, input_td_list, sz);
  }
  else
  {
    T->input_td_list = NULL;
  }

  if (input_td_subs > 0)
  {
    size_t sz = (size_t)input_td_subs* sizeof(td_sub_pair);
    T->input_td_sub_list = malloc(sz);

    if (! T->input_td_sub_list)
      return TURBINE_ERROR_OOM;

    memcpy(T->input_td_sub_list, input_td_sub_list, sz);
  }
  else
  {
    T->input_td_sub_list = NULL;
  }

  int total_inputs = input_tds + input_td_subs;
  if (total_inputs > 0)
  {
    size_t sz = bitfield_size(total_inputs)* sizeof(unsigned char);
    T->closed_inputs = malloc(sz);

    if (! T->closed_inputs)
      return TURBINE_ERROR_OOM;

    memset(T->closed_inputs, 0, sz);
  }
  else
  {
    T->closed_inputs = NULL;
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
  if (T->input_td_list)
    free(T->input_td_list);
  if (T->input_td_sub_list)
  {
    for (int i = 0; i < T->input_td_subs; i++)
    {
      // free subscript strings
      free(T->input_td_sub_list[i].subscript.key);
    }
    free(T->input_td_sub_list);
  }
  if (T->closed_inputs)
    free(T->closed_inputs);
  free(T);
}


/**
 * Return true if subscribed, false if data already set
 */
static inline turbine_code
subscribe(adlb_datum_id id, turbine_subscript subscript, bool *result)
{
  // if subscript provided, use key
  size_t id_sub_keylen = id_sub_key_buflen(subscript.key, subscript.length);
  char id_sub_key[id_sub_keylen];
  if (subscript.key != NULL)
  {
    write_id_sub_key(id_sub_key, id, subscript.key, subscript.length);
    void *tmp;
    if (table_bp_search(&td_sub_subscribed, id_sub_key, id_sub_keylen,
                        &tmp))
    {
      // TODO: support binary subscript
      DEBUG_TURBINE("Already subscribed: <%"PRId64">[\"%.*s\"]",
                      id, (int)subscript.length, subscript.key);
      *result = true;
      return TURBINE_SUCCESS;
    }
  }
  else
  {
    if (table_lp_search(&td_subscribed, id) != NULL) {
      // Already subscribed
      *result = true;
      return TURBINE_SUCCESS;
    }
  }
  int subscribed;
  adlb_code rc = ADLB_Subscribe(id, sub_convert(subscript), &subscribed);
  
  if (rc == (int)ADLB_DATA_ERROR_NOT_FOUND) {
    // Handle case where read_refcount == 0 and write_refcount == 0
    //      => datum was freed and we're good to go
    subscribed = 0;
  } else if (rc != ADLB_SUCCESS) {
    if (subscript.key != NULL)
    {
      log_printf("ADLB_Subscribe on <%ld>[\"%s\"] failed with code: %d\n",
                 id, subscript, rc);
    }
    else
    {
      log_printf("ADLB_Subscribe on <%ld> failed with code: %d\n", id, rc);
    }
    return rc; // Turbine codes are same as ADLB data codes
  }

  DEBUG_TURBINE("ADLB_Subscribe: %i", subscribed);

  if (subscribed != 0) {
    // Record it was subscribed
    if (subscript.key != NULL)
      table_bp_add(&td_sub_subscribed, id_sub_key, id_sub_keylen, (void*)1);
    else
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
    DEBUG_TURBINE("rule: %s {%"PRId64"}", tmp, id);     \
  }
#else
#define DEBUG_TURBINE_RULE(transform, id)
#endif

static inline turbine_code progress(transform* T, bool* subscribed);
static inline turbine_code rule_inputs(transform* T);

turbine_code
turbine_rule(const char* name,
             int input_tds, const turbine_datum_id* input_td_list,
             int input_td_subs, const td_sub_pair* input_td_sub_list,
             turbine_action_type action_type,
             const char* action,
             int priority,
             int target,
             int parallelism,
             turbine_transform_id* id)
{
  turbine_code tc;

  if (!turbine_engine_initialized)
    return TURBINE_ERROR_UNINITIALIZED;
  transform* T = NULL;
  tc = transform_create(name, input_tds, input_td_list,
                                       input_td_subs, input_td_sub_list,
                                       action_type, action,
                                       priority, target, parallelism,
                                       &T);

  turbine_check(tc);
  *id = T->id;

  tc = rule_inputs(T);
  turbine_check(tc);

  bool subscribed;
  tc = progress(T, &subscribed);
  if (tc != TURBINE_SUCCESS)
  {
    DEBUG_TURBINE("turbine_rule failed:\n");
    DEBUG_TURBINE_RULE(T, *id);
    return tc;
  }

  DEBUG_TURBINE_RULE(T, *id);

  if (subscribed)
  {
    DEBUG_TURBINE("waiting: {%"PRId64"}", *id);
    assert(T != NULL);
    table_lp_add(&transforms_waiting, *id, T);
  }
  else
  {
    DEBUG_TURBINE("ready: {%"PRId64"}", *id);
    list_add(&transforms_ready, T);
  }

  return TURBINE_SUCCESS;
}

static inline turbine_code add_rule_blocker(turbine_datum_id id,
                                         turbine_transform_id transform);

static inline turbine_code add_rule_blocker_sub(void *id_sub_key,
        size_t id_sub_keylen, turbine_transform_id transform);
/**
   Record that this transform is blocked by its inputs.  Do not yet
   subscribe to any inputs
*/
static inline turbine_code
rule_inputs(transform* T)
{
  for (int i = 0; i < T->input_tds; i++)
  {
    turbine_datum_id id = T->input_td_list[i];
    // TODO: we might add duplicate list entries if id appears multiple
    //       times. This is currently handled upon removal from list
    turbine_code code = add_rule_blocker(id, T->id);
    turbine_check_verbose(code);
  }

  for (int i = 0; i < T->input_td_subs; i++)
  {
    td_sub_pair *td_sub = &T->input_td_sub_list[i];
    size_t id_sub_keylen = id_sub_key_buflen(td_sub->subscript.key,
                                             td_sub->subscript.length);
    char id_sub_key[id_sub_keylen];
    assert(td_sub->subscript.key != NULL);
    write_id_sub_key(id_sub_key, td_sub->td, td_sub->subscript.key,
                     td_sub->subscript.length);
    // TODO: we might add duplicate list entries if id appears multiple
    //      times. This is currently handled upon removal from list
    turbine_code code = add_rule_blocker_sub(id_sub_key, id_sub_keylen,
                                             T->id);
    turbine_check_verbose(code);
  }
  return TURBINE_SUCCESS;
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
    if (c != NULL)
    {
      // TODO: c can be null if there were two entries in the blockers
      //      list for that transform.  Handle here for now
      list_add(&transforms_ready, T);
    }
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
        DEBUG_TURBINE("not subscribed on: %"PRId64"\n", T->id);
        list_add(&tmp, T);
      }
    }

  add_to_ready(&tmp);

  return TURBINE_SUCCESS;
}

/**
   Declare a new data id
   @param result return the new blocked list here
 */
static inline turbine_code
add_rule_blocker(turbine_datum_id id, turbine_transform_id transform)
{
  assert(initialized);
  DEBUG_TURBINE("add_rule_blocker for {%"PRId64"}: <%"PRId64">",
                transform, id);
  struct list_l* blocked = table_lp_search(&td_blockers, id);
  if (blocked == NULL)
  {
    blocked = list_l_create();
    table_lp_add(&td_blockers, id, blocked);
  }
  list_l_add(blocked, transform);
  return TURBINE_SUCCESS;
}

/*
  Same as add_rule_blocker, but with subscript.
 */
static inline turbine_code add_rule_blocker_sub(void *id_sub_key,
        size_t id_sub_keylen, turbine_transform_id transform)
{
  assert(initialized);
  DEBUG_TURBINE("add_rule_blocker_sub for {%"PRId64"}", transform);
  struct list_l* blocked;
  bool found = table_bp_search(&td_sub_blockers, id_sub_key,
                         id_sub_keylen, (void**)&blocked);
  if (!found)
  {
    blocked = list_l_create();
    table_bp_add(&td_sub_blockers, id_sub_key, id_sub_keylen, blocked);
  }
  list_l_add(blocked, transform);
  return TURBINE_SUCCESS;
}

turbine_code turbine_pop(turbine_action_type* action_type,
                         turbine_transform_id *id,
                         char** action, int* priority, int* target,
                         int* parallelism)
{
  transform *T = (transform*) list_poll(&transforms_ready);

  if (T == NULL)
  {
    // Signal none ready
    DEBUG_TURBINE("pop: no transforms ready");
    *action_type = TURBINE_ACTION_NULL;
    return TURBINE_SUCCESS;
  }

  // Debugging
  DEBUG_TURBINE("pop: transform:   {%"PRId64"}", T->id);
  DEBUG_TURBINE("     action:      {%"PRId64"} %s: %s", T->id, T->name,
                                                            T->action);
  DEBUG_TURBINE("     priority:    {%"PRId64"} => %i",  T->id, T->priority);
  DEBUG_TURBINE("     target:      {%"PRId64"} => %i",  T->id, T->target);
  DEBUG_TURBINE("     parallelism: {%"PRId64"} => %i",  T->id, T->parallelism);

  // Copy outputs
  *action_type = T->action_type;
  *id = T->id;
  *action = T->action;
  T->action = NULL; // Avoid freeing action that we're going to return
  *priority = T->priority;
  *target = T->target;
  *parallelism = T->parallelism;

  // Clean up
  transform_free(T);

  return TURBINE_SUCCESS;
}

turbine_code
turbine_close(turbine_datum_id id)
{
  DEBUG_TURBINE("turbine_close(<%"PRId64">)", id);
  // Record no longer subscribed
  table_lp_remove(&td_subscribed, id);

  // Remove from table transforms that this td was blocking
  // Will need to free list later
  struct list_l* L = table_lp_remove(&td_blockers, id);
  if (L == NULL)
    // We don't have any rules that block on this td
    return TURBINE_SUCCESS;

  DEBUG_TURBINE("%i blocked", L->size);
  return turbine_close_update(L, id, NULL, 0);
}

turbine_code turbine_sub_close(turbine_datum_id id, const void *subscript,
                               size_t subscript_len)
{
  DEBUG_TURBINE("turbine_sub_close(<%"PRId64">[\"%.*s\"])", id,
                (int)subscript_len, (const char*)subscript);
  size_t key_len = id_sub_key_buflen(subscript, subscript_len);
  char key[key_len];
  write_id_sub_key(key, id, subscript, subscript_len);
  
  struct list_l* L;
  
  bool found = table_bp_remove(&td_sub_blockers, key, key_len, (void**)&L);
  if (!found)
    // We don't have any rules that block on this td
    return TURBINE_SUCCESS;

  // TODO: support binary subscript
  return turbine_close_update(L, id, subscript, subscript_len);
}

/*
  Update transforms after having one of blockers removed.
  blocked: list of transforms with blocker remoed
  id: id of data
  subscript: optional subscript
 */
static turbine_code
turbine_close_update(struct list_l *blocked, turbine_datum_id id,
                     const void *subscript, size_t subscript_len)
{
  // Temporary holding spot for transforms moving into ready list
  struct list tmp;
  list_init(&tmp);

  // Try to make progress on those transforms
  for (struct list_l_item* item = blocked->head; item; item = item->next)
  {
    turbine_transform_id transform_id = item->data;
    transform* T = table_lp_search(&transforms_waiting, transform_id);
    if (!T)
      continue;
 
    // update closed vector
    if (subscript == NULL)
    {
      DEBUG_TURBINE("Update {%"PRId64"} for close: <%"PRId64">", T->id, id);
      for (int i = T->blocker; i < T->input_tds; i++) {
        if (T->input_td_list[i] == id) {
          mark_input_td_closed(T, i);
        }
      }
    }
    else
    {
      DEBUG_TURBINE("Update {%"PRId64"} for subscript close: <%"PRId64">",
                    T->id, id);
      if (T->blocker >= T->input_tds)
      {
        for (int i = T->blocker - T->input_tds; i < T->input_td_subs; i++)
        {
          td_sub_pair *tdsub = &T->input_td_sub_list[i];
          turbine_subscript *sub = &tdsub->subscript;
          if (tdsub->td == id && sub->length == subscript_len
              && memcmp(sub->key, subscript, subscript_len) == 0)
          {
            mark_input_td_sub_closed(T, i);
          }
        }
      }  
    }

    bool subscribed;
    turbine_code tc = progress(T, &subscribed);
    if (tc != TURBINE_SUCCESS)
      return tc;

    if (!subscribed)
    {
      DEBUG_TURBINE("ready: {%"PRId64"}", transform_id);
      list_add(&tmp, T);
    }
  }


  list_l_free(blocked); // No longer need list

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

  // first check TDs to see if all are ready
  for (; T->blocker < T->input_tds; T->blocker++)
  {
    if (!input_td_closed(T, T->blocker))
    {
      // Contact server to check if available
      turbine_datum_id td = T->input_td_list[T->blocker];
      turbine_code tc = subscribe(td, TURBINE_NO_SUB, subscribed);
      if (tc != TURBINE_SUCCESS) {
        return tc;
      }
      if (*subscribed) {
        // Need to block on this id
        return TURBINE_SUCCESS;
      }
    }
  }

  // now, make progress on any ID/subscript pairs
  int total_inputs = T->input_tds  + T->input_td_subs;
  for (; T->blocker < total_inputs; T->blocker++)
  {
    int td_sub_ix = T->blocker - T->input_tds;
    if (!input_td_sub_closed(T, td_sub_ix))
    {
      // Contact server to check if available
      td_sub_pair ts = T->input_td_sub_list[td_sub_ix];
      turbine_code tc = subscribe(ts.td, ts.subscript, subscribed);
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
    case TURBINE_ERROR_STORAGE:
      result = sprintf(output, "TURBINE_ERROR_STORAGE");
      break;
    case TURBINE_ERROR_UNINITIALIZED:
      result = sprintf(output, "TURBINE_ERROR_UNINITIALIZED");
      break;
    default:
      sprintf(output, "<could not convert code %d to string>", code);
      break;
  }
  return result;
}

static const char *action_type_tostring(turbine_action_type action_type);

static int
transform_tostring(char* output, transform* t)
{
  int result = 0;
  char* p = output;

  const char *action_type_string = action_type_tostring(t->action_type);

  append(p, "%s ", t->name);
  append(p, "%s ", action_type_string);
  append(p, "(");
  bool first = true;
  for (int i = 0; i < t->input_tds; i++)
  {
    if (first)
    {
      first = false;
    }
    else
    {
      append(p, " ");
    }
    // Highlight the blocking variable
    bool blocking = (i == t->blocker);
    if (blocking)
      append(p, "/");
    append(p, "%"PRId64"", t->input_td_list[i]);
    if (blocking)
      append(p, "/");
  }
  for (int i = 0; i < t->input_td_subs; i++)
  {
    if (first)
      first = false;
    else
      append(p, " ");

    // Highlight the blocking variable
    bool blocking = (i + t->input_tds == t->blocker);
    td_sub_pair ts = t->input_td_sub_list[i];
    if (blocking)
      append(p, "/");
    // TODO: support binary subscript
    append(p, "%"PRId64"[\"%.*s\"]", ts.td, (int)ts.subscript.length,
           ts.subscript.key);
    if (blocking)
      append(p, "/");
  }
  append(p, ")");

  result = (int)(p - output);
  return result;
}

static inline bool
input_td_closed(transform *T, int i)
{
  assert(i >= 0);
  unsigned char field = T->closed_inputs[(unsigned int)i / 8];
  return (field >> ((unsigned int)i % 8)) & 0x1;
}

static inline bool
input_td_sub_closed(transform *T, int i)
{
  // closed_inputs had pairs come after tds
  return input_td_closed(T, i + T->input_tds);
}

// Extract bit from closed_inputs
static inline void
mark_input_td_closed(transform *T, int i)
{
  assert(i >= 0);
  unsigned char mask = (unsigned char) (0x1 << ((unsigned int)i % 8));
  T->closed_inputs[i / 8] |= mask;
}

static inline void
mark_input_td_sub_closed(transform *T, int i)
{
  mark_input_td_closed(T, i + T->input_tds);
}

static size_t
bitfield_size(int inputs) {
  if (inputs <= 0)
    return 0;
  // Round up to nearest multiple of 8
  return (size_t)(inputs - 1) / 8 + 1;
}

/**
   Convert given action_type to string representation
*/
static const char*
action_type_tostring(turbine_action_type action_type)
{
  const char* s = NULL;
  switch (action_type)
  {
    case TURBINE_ACTION_NULL:
      s = "NULL";
      break;
    case TURBINE_ACTION_LOCAL:
      s = "LOCAL";
      break;
    case TURBINE_ACTION_CONTROL:
      s = "CONTROL";
      break;
    case TURBINE_ACTION_WORK:
      s = "WORK";
      break;
    default:
      printf("action_type_tostring(): unknown: %i\n", action_type);
      exit(1);
  }
  return s;
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
      sprintf(id_string, "{%"PRId64"}", t->id);
      int c = sprintf(buffer, "%10s ", id_string);
      transform_tostring(buffer+c, t);
      printf("TRANSFORM: %s\n", buffer);
    }
}

// Callbacks to free data
static void tbl_free_transform_cb(turbine_transform_id key, void *T);
static void tbl_free_blockers_cb(turbine_datum_id key, void *L);
static void tbl_free_sub_blockers_cb(const void *key, size_t key_len, void *L);
static void list_free_transform_cb(void *T);

void
turbine_finalize(void)
{
  turbine_cache_finalize();
  turbine_engine_finalize();
}


static void turbine_engine_finalize(void)
{
  if (!turbine_engine_initialized)
    return;

  // First report any problems we find
  if (transforms_waiting.size != 0)
    info_waiting();

  // Now we're done reporting, free everything
  table_lp_free_callback(&transforms_waiting, false, tbl_free_transform_cb);
  list_clear_callback(&transforms_ready, list_free_transform_cb);
  table_lp_free_callback(&td_blockers, false, tbl_free_blockers_cb);
  table_bp_free_callback(&td_sub_blockers, false, tbl_free_sub_blockers_cb);

  // Entries in td_subscribed and td_sub_subscribed are not pointers and don't
  // need to be freed
  table_lp_free_callback(&td_subscribed, false, NULL);
  table_bp_free_callback(&td_sub_subscribed, false, NULL);

}

static void tbl_free_transform_cb(turbine_transform_id key, void *T)
{
  transform_free((transform*)T);
}

static void list_free_transform_cb(void *T)
{
  transform_free((transform*)T);
}

static void tbl_free_blockers_cb(turbine_datum_id key, void *L)
{
  list_l_free((struct list_l*)L);
}

static void tbl_free_sub_blockers_cb(const void *key, size_t key_len, void *L)
{
  list_l_free((struct list_l*)L);
}
