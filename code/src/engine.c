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
 * engine.c
 *
 *  Created on: May 4, 2011
 *  Moved to ADLB codebase: Apr 2014
 *      Author: wozniak, armstrong
 *
 * TR means TRansform, the in-memory record of data-dependent work
 * */

#include "engine.h"

#include <assert.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <unistd.h>

#include <adlb.h>

#include <c-utils.h>
#include <list.h>
#include <list2.h>
#include <list2_b.h>
#include <log.h>
#include <table.h>
#include <table_bp.h>
#include <table_lp.h>
#include <tools.h>

#include "data_internal.h"
#include "debug.h"
#include "sync.h"

/*
  Track different subscribe methods used
 */
static struct {
  // Counters for IDs only
  int64_t id_subscribed; /* Combine with existing subscribe */
  int64_t id_subscribe_local; /* Subscribe to local data */
  int64_t id_subscribe_remote; /* Subscribe to remote data */
  int64_t id_subscribe_cached; /* Cached subscribe to remote data */
  int64_t id_ready; /* Already closed upon subscribe */
  
  // Counters for ID/subscript combo
  int64_t id_sub_subscribed; /* Combine with existing subscribe */
  int64_t id_sub_subscribe_local; /* Subscribe to local data */
  int64_t id_sub_subscribe_remote; /* Subscribe to remote data */
  int64_t id_sub_subscribe_cached; /* Cached subscribe to remote data */
  int64_t id_sub_ready; /* Already closed upon subscribe */
} xlb_engine_counters;

#define INCR_COUNTER(name) \
  if (xlb_s.perfc_enabled) { \
    xlb_engine_counters.name++;    \
  }

// Subscript with modifiable key
typedef struct {
  char *key;
  size_t length;
} engine_sub;

typedef struct {
  adlb_datum_id td;
  engine_sub subscript;
} id_sub_pair;

typedef enum
{
  /** Waiting for inputs */
  TRANSFORM_WAITING,
  /** Inputs ready */
  TRANSFORM_READY,
} transform_status;

/**
   In-memory structure for data-dependent task
 */
typedef struct
{
  /** Name for human debugging */
  char* name;

  /** Task to release when inputs are ready */
  xlb_work_unit *work;

  /** Entry in transforms_waiting */
  struct list2_item *list_entry;

  /** Number of input tds */
  int input_tds;
  /** Array of input TDs */
  adlb_datum_id* input_id_list;

  /** Number of input TD/subscript pairs */
  int input_id_subs;
  /** Array of input TD/subscript pairs */
  id_sub_pair* input_id_sub_list;

  /** Closed inputs - bit vector for both tds and td/sub pairs */
  unsigned char *closed_inputs;
  /** Index of next subscribed input (starts at 0 in input_td list,
      continues into input_id_sub_list) */
  int blocker; // Next input we're waiting for
  transform_status status;
} transform;

static size_t bitfield_size(int inputs);

// Check if input closed
static inline bool input_id_closed(transform *T, int i);
static inline void mark_input_id_closed(transform *T, int i);
static inline bool input_id_sub_closed(transform *T, int i);
static inline void mark_input_id_sub_closed(transform *T, int i);

// Update transforms after close
static xlb_engine_code
xlb_engine_close_update(struct list *blocked, adlb_datum_id id,
     adlb_subscript sub, xlb_engine_work_array *ready);

static inline xlb_engine_code
move_to_ready(xlb_engine_work_array *ready, transform *T);

static xlb_engine_code
subscribe_td(adlb_datum_id id, bool *subscribed);
static xlb_engine_code
subscribe_id_sub(adlb_datum_id id, engine_sub subscript,
     const void *id_sub_key, size_t id_sub_key_len, bool *subscribed);

static xlb_engine_code init_closed_caches(void);
static void finalize_closed_caches(void);

static xlb_engine_code id_closed_cache_add(adlb_datum_id id);
static bool id_closed_cache_check(adlb_datum_id id);

static xlb_engine_code
id_sub_closed_cache_add(const void *key, size_t key_len);
static bool id_sub_closed_cache_check(const void *key, size_t key_len);

#define DEBUG_ENGINE(s, args...) DEBUG("ENGINE:" s, ## args)

/** Has xlb_engine_init() been called? */
bool xlb_engine_initialized = false;

/**
   Waiting transforms.
   List of transforms
 */
static struct list2 transforms_waiting;

/**
   TD inputs blocking their transforms
   Map from TD ID to list of pointers to transforms.

   There may be duplicate entries of the same transform for an ID
   in id_blockers: this must be handled when the notification is
   received.
 */
static struct table_lp id_blockers;

/**
   ID/subscript pairs blocking transforms
   Map from ID/subscript pair to list of pointers to transforms
   
   There may be duplicate entries of the same transform for an ID
   in id_sub_blockers: this must be handled when the notification is
   received.
 */
static struct table_bp id_sub_blockers;

/**
  TDs to which this engine has subscribed, used to avoid
  subscribing multiple times
 */
static struct table_lp id_subscribed;

/**
  TD/subscript pairs to which engine is subscribed.  Key is created using
   xlb_write_id_sub function
 */
static struct table_bp id_sub_subscribed;

/**
  LRU caches for TDs or TD/sub pairs known to be closed implemented with
  doubly-linked list and hash table.  Hash table entries point to linked
  list node.  Linked list node contains subscribe cache entry.  The head
  of the LRU is next in line for eviction.  We only cache remote
  subscribes, not local ones.
 */
typedef struct {
  adlb_datum_id id;
} id_closed_cache_entry;

// Key created with xlb_write_id_sub
typedef struct {
  size_t key_len;
  char key[];
} id_sub_closed_cache_entry;

#define DEFAULT_CLOSED_CACHE_SIZE 4096

static int id_closed_cache_size; // Number of entries

static int id_sub_closed_cache_size; // Number of entries

static struct table_lp id_closed_cache;

static struct table_bp id_sub_closed_cache;

static struct list2_b id_closed_cache_lru;

static struct list2_b id_sub_closed_cache_lru;

// Maximum length of buffer required for key
#define ID_SUB_KEY_MAX (ADLB_DATA_SUBSCRIPT_MAX + 30)

static inline adlb_subscript sub_convert(engine_sub sub)
{
  adlb_subscript asub = { .key = sub.key, .length = sub.length };
  return asub;
}

#define ENGINE_CONDITION(condition, code, format, args...) \
  { if (! (condition))                                      \
    {                                                       \
       printf(format, ## args);                             \
       return code;                                         \
    }}

#if ENABLE_LOG_DEBUG
// Include traceback
#define ENGINE_CHECK(rc) \
  { xlb_engine_code _rc = (rc);                             \
    if (_rc != XLB_ENGINE_SUCCESS) {                       \
      printf("ADLB ENGINE CHECK FAILED: %s:%s:%i\n",        \
         __FUNCTION__, __FILE__, __LINE__);                 \
      return _rc;                                           \
  }}

#define ENGINE_CHECK_DATA(rc, err_rc) \
  { adlb_data_code _rc = (rc);                              \
    if (_rc != ADLB_DATA_SUCCESS) {                         \
      printf("ADLB ENGINE CHECK FAILED: %s:%s:%i\n",        \
         __FUNCTION__, __FILE__, __LINE__);                 \
      return (err_rc);                                      \
  }}

#define ENGINE_CHECK_ADLB(rc, err_rc) \
  { adlb_code _rc = (rc);                                   \
    if (_rc == ADLB_ERROR) {                                \
      printf("ADLB ENGINE CHECK FAILED: %s:%s:%i\n",        \
         __FUNCTION__, __FILE__, __LINE__);                 \
      return (err_rc);                                      \
  }}

#else
// Just return
#define ENGINE_CHECK(rc) \
  { xlb_engine_code _rc = (rc);                             \
    if (_rc != XLB_ENGINE_SUCCESS) {                        \
      return _rc;                                           \
  }}

#define ENGINE_CHECK_DATA(rc, err_rc) \
  { adlb_data_code _rc = (rc);                              \
    if (_rc != ADLB_DATA_SUCCESS) {                         \
      return (err_rc);                                      \
  }}

#define ENGINE_CHECK_ADLB(rc, err_rc) \
  { adlb_code _rc = (rc);                                   \
    if (_rc == ADLB_ERROR) {                                \
      return (err_rc);                                      \
  }}

#endif

/**
   This is a separate function so we can set a function breakpoint
 */
static void
gdb_sleep(int* t, int i)
{
  sleep(1);
  DEBUG_ENGINE("gdb_check: %i %i\n", *t, i);
}

/**
   Allows user to launch in a loop until a debugger attaches
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

xlb_engine_code
xlb_engine_init(int rank)
{
  gdb_check(rank);

  // Initialize tables to size that will probably not need to be
  // expanded, but is not excessively large
  const int table_init_capacity = 65536;

  bool result;

  list2_init(&transforms_waiting); 

  result = table_lp_init(&id_blockers, table_init_capacity); 
  if (!result)
    return XLB_ENGINE_ERROR_OOM;
  
  result = table_bp_init(&id_sub_blockers, table_init_capacity); 
  if (!result)
    return XLB_ENGINE_ERROR_OOM;

  result = table_lp_init(&id_subscribed, table_init_capacity); 
  if (!result)
    return XLB_ENGINE_ERROR_OOM;

  result = table_bp_init(&id_sub_subscribed, table_init_capacity); 
  if (!result)
    return XLB_ENGINE_ERROR_OOM;

  if (xlb_s.perfc_enabled)
  {
    xlb_engine_counters.id_subscribed = 0;
    xlb_engine_counters.id_subscribe_local = 0;
    xlb_engine_counters.id_subscribe_remote = 0;
    xlb_engine_counters.id_subscribe_cached = 0;
    xlb_engine_counters.id_ready = 0;
    
    xlb_engine_counters.id_sub_subscribed = 0;
    xlb_engine_counters.id_sub_subscribe_local = 0;
    xlb_engine_counters.id_sub_subscribe_remote = 0;
    xlb_engine_counters.id_sub_subscribe_cached = 0;
    xlb_engine_counters.id_sub_ready = 0;
  }

  xlb_engine_code tc = init_closed_caches();
  if (tc != XLB_ENGINE_SUCCESS)
    return tc;
  
  xlb_engine_initialized = true;
  return XLB_ENGINE_SUCCESS;
}

void
xlb_engine_print_counters(void)
{
  if (!xlb_s.perfc_enabled)
    return;
  PRINT_COUNTER("engine_subscribed=%"PRId64,
        xlb_engine_counters.id_subscribed +
        xlb_engine_counters.id_sub_subscribed);
  PRINT_COUNTER("engine_subscribe_local=%"PRId64,
        xlb_engine_counters.id_subscribe_local + 
        xlb_engine_counters.id_sub_subscribe_local);
  PRINT_COUNTER("engine_subscribe_remote=%"PRId64, 
        xlb_engine_counters.id_subscribe_remote +
        xlb_engine_counters.id_sub_subscribe_remote);
  PRINT_COUNTER("engine_subscribe_cached=%"PRId64, 
        xlb_engine_counters.id_subscribe_cached +
        xlb_engine_counters.id_sub_subscribe_cached);
  PRINT_COUNTER("engine_ready=%"PRId64, xlb_engine_counters.id_ready +
                                xlb_engine_counters.id_sub_ready);

  PRINT_COUNTER("engine_id_subscribed=%"PRId64,
        xlb_engine_counters.id_subscribed);
  PRINT_COUNTER("engine_id_subscribe_local=%"PRId64,
        xlb_engine_counters.id_subscribe_local);
  PRINT_COUNTER("engine_id_subscribe_remote=%"PRId64,
        xlb_engine_counters.id_subscribe_remote);
  PRINT_COUNTER("engine_id_subscribe_cached=%"PRId64,
        xlb_engine_counters.id_subscribe_cached);
  PRINT_COUNTER("engine_id_ready=%"PRId64,
        xlb_engine_counters.id_ready);
  
  PRINT_COUNTER("engine_id_sub_subscribed=%"PRId64,
        xlb_engine_counters.id_sub_subscribed);
  PRINT_COUNTER("engine_id_sub_subscribe_local=%"PRId64,
        xlb_engine_counters.id_sub_subscribe_local);
  PRINT_COUNTER("engine_id_sub_subscribe_remote=%"PRId64,
        xlb_engine_counters.id_sub_subscribe_remote);
  PRINT_COUNTER("engine_id_sub_subscribe_cached=%"PRId64,
        xlb_engine_counters.id_sub_subscribe_cached);
  PRINT_COUNTER("engine_id_sub_ready=%"PRId64,
        xlb_engine_counters.id_sub_ready);
}

static inline xlb_engine_code
transform_create(const char* name, int name_strlen,
           int input_tds, const adlb_datum_id* input_id_list,
           int input_id_subs, const adlb_datum_id_sub* input_id_sub_list,
           xlb_work_unit *work, transform** result)
{
  assert(work);
  assert(input_tds >= 0);
  assert(input_tds == 0 || input_id_list != NULL);
  assert(input_id_subs >= 0);
  assert(input_id_subs == 0 || input_id_sub_list != NULL);

  transform* T = malloc(sizeof(transform));
  if (! T)
    return XLB_ENGINE_ERROR_OOM;

  if (name != NULL)
  {
    T->name = malloc((size_t) name_strlen + 1);
    memcpy(T->name, name, (size_t)name_strlen);
    T->name[name_strlen] = '\0';
  }
  else
  {
    T->name = NULL;
  }

  T->work = work;
  T->blocker = 0;
  T->input_tds = input_tds;
  T->input_id_subs = input_id_subs;

  if (input_tds > 0)
  {
    size_t sz = (size_t)input_tds*sizeof(adlb_datum_id);
    T->input_id_list = malloc(sz);

    if (! T->input_id_list)
      return XLB_ENGINE_ERROR_OOM;

    memcpy(T->input_id_list, input_id_list, sz);
  }
  else
  {
    T->input_id_list = NULL;
  }

  if (input_id_subs > 0)
  {
    size_t sz = (size_t)input_id_subs* sizeof(id_sub_pair);
    T->input_id_sub_list = malloc(sz);

    if (! T->input_id_sub_list)
      return XLB_ENGINE_ERROR_OOM;
    // Copy across all subscripts
    for (int i = 0; i < input_id_subs; i++)
    {
      const adlb_datum_id_sub *src;
      id_sub_pair *dst;
      src = &input_id_sub_list[i];
      dst = &T->input_id_sub_list[i];
      dst->td = src->id;
      dst->subscript.length = src->subscript.length;
      dst->subscript.key = malloc(src->subscript.length);
      if (!dst->subscript.key)
        return XLB_ENGINE_ERROR_OOM;
      memcpy(dst->subscript.key, src->subscript.key,
             src->subscript.length);
    }
  }
  else
  {
    T->input_id_sub_list = NULL;
  }

  int total_inputs = input_tds + input_id_subs;
  if (total_inputs > 0)
  {
    size_t sz = bitfield_size(total_inputs)* sizeof(unsigned char);
    T->closed_inputs = malloc(sz);

    if (! T->closed_inputs)
      return XLB_ENGINE_ERROR_OOM;

    memset(T->closed_inputs, 0, sz);
  }
  else
  {
    T->closed_inputs = NULL;
  }


  T->status = TRANSFORM_WAITING;

  *result = T;
  return XLB_ENGINE_SUCCESS;
}

static inline void
transform_free(transform* T)
{
  if (T->name != NULL)
    free(T->name);
  if (T->work)
    xlb_work_unit_free(T->work);
  if (T->input_id_list)
    free(T->input_id_list);
  if (T->input_id_sub_list)
  {
    for (int i = 0; i < T->input_id_subs; i++)
    {
      // free subscript strings
      free(T->input_id_sub_list[i].subscript.key);
    }
    free(T->input_id_sub_list);
  }
  if (T->closed_inputs)
    free(T->closed_inputs);
  free(T);
}

/**
 * subscribed: set to true if subscribed, false if data already set
 */
static xlb_engine_code
subscribe_td(adlb_datum_id id, bool *subscribed)
{
  ENGINE_CONDITION(id != ADLB_DATA_ID_NULL, XLB_ENGINE_ERROR_INVALID,
                    "Null ID provided to data-dependent task");
  int server = ADLB_Locate(id);

  DEBUG_ENGINE("Engine subscribe to <%"PRId64">", id);
  if (table_lp_contains(&id_subscribed, id)) {
    TRACE("already subscribed: <%"PRId64">", id);
    // Already subscribed
    *subscribed = true;
    INCR_COUNTER(id_subscribed);
  }
  else
  {
    if (server == xlb_s.layout.rank)
    {
      adlb_data_code dc = xlb_data_subscribe(id, ADLB_NO_SUB,
                               xlb_s.layout.rank, 0, subscribed);
      TRACE("xlb_data_subscribe => %i %i", (int)dc, (int)*subscribed);
      if (dc == ADLB_DATA_ERROR_NOT_FOUND)
      {
        // Handle case where read_refcount == 0 and write_refcount == 0
        //      => datum was freed and we're good to go
        *subscribed = false;
      }
      ENGINE_CHECK_DATA(dc, XLB_ENGINE_ERROR_UNKNOWN);
      INCR_COUNTER(id_subscribe_local);
    }
    else
    {
      // Only cache closed data on other servers
      if (id_closed_cache_check(id))
      {
        *subscribed = false;
        INCR_COUNTER(id_subscribe_cached);
      }
      else
      {
        adlb_code ac = xlb_sync_subscribe(server, id, ADLB_NO_SUB,
                                          subscribed);
        ENGINE_CHECK_ADLB(ac,  XLB_ENGINE_ERROR_UNKNOWN);
        INCR_COUNTER(id_subscribe_remote);
      }
    }
  
    if (*subscribed)
    {
      bool ok = table_lp_add(&id_subscribed, id, (void*)1);
      if (!ok)
        return XLB_ENGINE_ERROR_OOM;
    }
    else
    {
      INCR_COUNTER(id_ready);
    }
  }
  
  return XLB_ENGINE_SUCCESS;
}

/**
 * Subscribe to td/subscribe pair
 *
 * id_sub_key/id_sub_key_len: key used for data structures,
                              to avoid recomputing
 */
static xlb_engine_code
subscribe_id_sub(adlb_datum_id id, engine_sub subscript,
     const void *id_sub_key, size_t id_sub_key_len, bool *subscribed)
{
  ENGINE_CONDITION(id != ADLB_DATA_ID_NULL, XLB_ENGINE_ERROR_INVALID,
                    "Null ID provided to to data-dependent task");
  
  int server = ADLB_Locate(id);

  assert(subscript.key != NULL);
  DEBUG_ENGINE("Engine subscribe to <%"PRId64">[%.*s]", id,
               (int)subscript.length, (const char*)subscript.key);

  // Avoid multiple subscriptions for same data
  void *tmp;
  if (table_bp_search(&id_sub_subscribed, id_sub_key, id_sub_key_len,
                      &tmp))
  {
    DEBUG_ENGINE("Already subscribed: <%"PRId64">[\"%.*s\"]",
                    id, (int)subscript.length, subscript.key);
    *subscribed = true;

    INCR_COUNTER(id_sub_subscribed);
  }
  else
  {
    if (server == xlb_s.layout.rank)
    {
      adlb_data_code dc = xlb_data_subscribe(id, sub_convert(subscript),
                                            xlb_s.layout.rank, 0, subscribed);
      TRACE("xlb_data_subscribe => %i %i", (int)dc, (int)*subscribed);
      if (dc == ADLB_DATA_ERROR_NOT_FOUND)
      {
        // Handle case where read_refcount == 0 and write_refcount == 0
        //      => datum was freed and we're good to go
        *subscribed = false;
      }
      ENGINE_CHECK_DATA(dc, XLB_ENGINE_ERROR_UNKNOWN);
    
      INCR_COUNTER(id_sub_subscribe_local);
    }
    else
    {
      // Only cache closed data on other servers
      if (id_sub_closed_cache_check(id_sub_key, id_sub_key_len))
      {
        *subscribed = false;
        INCR_COUNTER(id_sub_subscribe_cached);
      }
      else
      {
        adlb_code ac = xlb_sync_subscribe(server, id,
                            sub_convert(subscript), subscribed);
        ENGINE_CHECK_ADLB(ac,  XLB_ENGINE_ERROR_UNKNOWN);
        
        INCR_COUNTER(id_sub_subscribe_remote);
      }
    }

    if (*subscribed)
    {
      // Record it was subscribed
      bool ok = table_bp_add(&id_sub_subscribed, id_sub_key,
                            id_sub_key_len, (void*)1);

      if (!ok)
        return XLB_ENGINE_ERROR_OOM;
    }
    else
    {
      INCR_COUNTER(id_sub_ready);
    }
  }

  return XLB_ENGINE_SUCCESS;
}

static int transform_tostring(char* output,
                              transform* transform);

#ifdef ENABLE_DEBUG_ENGINE
#define DEBUG_XLB_ENGINE_TRANSFORM(transform, id) {         \
    char tmp[1024];                                     \
    transform_tostring(tmp, transform);                 \
    DEBUG_ENGINE("transform: %s {%"PRId64"}", tmp, id);     \
  }
#else
#define DEBUG_XLB_ENGINE_TRANSFORM(transform, id)
#endif

static xlb_engine_code progress(transform* T, bool* subscribed);
static xlb_engine_code init_inputs(transform* T);

xlb_engine_code
xlb_engine_put(const char* name, int name_strlen,
              int input_tds,
              const adlb_datum_id* input_id_list,
              int input_id_subs,
              const adlb_datum_id_sub* input_id_sub_list,
              xlb_work_unit *work, bool *ready)
{
  xlb_engine_code tc;

  if (!xlb_engine_initialized)
    return XLB_ENGINE_ERROR_UNINITIALIZED;
  transform* T = NULL;
  tc = transform_create(name, name_strlen, input_tds, input_id_list,
                       input_id_subs, input_id_sub_list, work, &T);

  ENGINE_CHECK(tc);

  tc = init_inputs(T);
  ENGINE_CHECK(tc);

  bool subscribed;
  tc = progress(T, &subscribed);
  if (tc != XLB_ENGINE_SUCCESS)
  {
    DEBUG_ENGINE("xlb_engine_put failed:\n");
    DEBUG_XLB_ENGINE_TRANSFORM(T, work->id);
    return tc;
  }

  DEBUG_XLB_ENGINE_TRANSFORM(T, work->id);

  if (subscribed)
  {
    DEBUG_ENGINE("waiting: {%"PRId64"}", work->id);
    assert(T != NULL);
    struct list2_item *list_entry = list2_add(&transforms_waiting, T);
    if (list_entry == NULL)
      return XLB_ENGINE_ERROR_OOM;
    T->list_entry = list_entry;
    *ready = false;
  }
  else
  {
    DEBUG_ENGINE("ready: {%"PRId64"}", work->id);
    *ready = true;

    // Free transform except for work unit
    T->work = NULL;
    transform_free(T);
  }

  return XLB_ENGINE_SUCCESS;
}

static inline xlb_engine_code add_blocker(adlb_datum_id id,
                                      transform *T);

static inline xlb_engine_code add_blocker_sub(void *id_sub_key,
        size_t id_sub_keylen, transform *T);
/**
  Do initial setup of subscribes so that notifications will update
  the inputs of this transform

  Currently this is implemented by subscribing to all inputs,
  marking those that are already closed, and adding the remainder
  to the blockers table.
*/
static xlb_engine_code
init_inputs(transform* T)
{
  /*
    We might add duplicate list entries if input appears multiple
    times. This is currently handled upon removal from list.
   */
  xlb_engine_code tc;

  for (int i = 0; i < T->input_tds; i++)
  {
    adlb_datum_id id = T->input_id_list[i];
    bool subscribed;
    tc = subscribe_td(id, &subscribed);
    ENGINE_CHECK(tc);

    if (subscribed)
    {
      // We might add duplicate list entries if id appears multiple
      //      times. This is currently handled upon removal from list
      tc = add_blocker(id, T);
      ENGINE_CHECK(tc);
    }
    else
    {
      mark_input_id_closed(T, i);
    }
  }

  for (int i = 0; i < T->input_id_subs; i++)
  {
    id_sub_pair *id_sub = &T->input_id_sub_list[i];
    size_t id_sub_keylen = xlb_id_sub_buflen(sub_convert(id_sub->subscript));
    char id_sub_key[id_sub_keylen];
    assert(id_sub->subscript.key != NULL);
    xlb_write_id_sub(id_sub_key, id_sub->td, sub_convert(id_sub->subscript));

    bool subscribed;
    tc = subscribe_id_sub(id_sub->td, id_sub->subscript,
                     id_sub_key, id_sub_keylen, &subscribed);
    ENGINE_CHECK(tc);

    if (subscribed)
    {
      // We might add duplicate list entries if id appears multiple
      //      times. This is currently handled upon removal from list
      tc = add_blocker_sub(id_sub_key, id_sub_keylen, T);
      ENGINE_CHECK(tc);
    }
    else
    {
      mark_input_id_sub_closed(T, i);
    }
  }
  return XLB_ENGINE_SUCCESS;
}


/**
   Declare a new data id
   @param result return the new blocked list here
 */
static inline xlb_engine_code
add_blocker(adlb_datum_id id, transform *T)
{
  assert(xlb_engine_initialized);
  DEBUG_ENGINE("add_blocker for {%"PRId64"}: <%"PRId64">",
                T->work->id, id);
  struct list* blocked;
  table_lp_search(&id_blockers, id, (void**)&blocked);
  if (blocked == NULL)
  {
    blocked = list_create();
    bool ok = table_lp_add(&id_blockers, id, blocked);
    if (!ok)
      return XLB_ENGINE_ERROR_OOM;
  }
  list_add(blocked, T);
  return XLB_ENGINE_SUCCESS;
}

/*
  Same as add_blocker, but with subscript.
 */
static inline xlb_engine_code add_blocker_sub(void *id_sub_key,
        size_t id_sub_keylen, transform *T)
{
  assert(xlb_engine_initialized);
  DEBUG_ENGINE("add_blocker_sub for {%"PRId64"}", T->work->id);
  struct list* blocked;
  bool found = table_bp_search(&id_sub_blockers, id_sub_key,
                         id_sub_keylen, (void**)&blocked);
  if (!found)
  {
    blocked = list_create();
    bool ok = table_bp_add(&id_sub_blockers, id_sub_key, id_sub_keylen,
                           blocked);
    if (!ok)
      return XLB_ENGINE_ERROR_OOM;
  }
  list_add(blocked, T);
  return XLB_ENGINE_SUCCESS;
}

xlb_engine_code
xlb_engine_close(adlb_datum_id id, bool remote,
              xlb_engine_work_array *ready)
{
  DEBUG_ENGINE("xlb_engine_close(<%"PRId64">)", id);
  // Record no longer subscribed
  void *tmp;
  bool was_subscribed = table_lp_remove(&id_subscribed, id, &tmp);
  assert(was_subscribed);

  if (remote)
  {
    // Cache remote subscribes
    xlb_engine_code tc = id_closed_cache_add(id);
    ENGINE_CHECK(tc);
  }

  // Remove from table transforms that this td was blocking
  // Will need to free list later
  struct list* L;
  bool found = table_lp_remove(&id_blockers, id, (void**)&L);
  if (!found)
    // We don't have any transforms that block on this td
    return XLB_ENGINE_SUCCESS;

  DEBUG_ENGINE("%i blocked", L->size);
  return xlb_engine_close_update(L, id, ADLB_NO_SUB, ready);
}

xlb_engine_code xlb_engine_sub_close(adlb_datum_id id, adlb_subscript sub,
                               bool remote, xlb_engine_work_array *ready)
{
  DEBUG_ENGINE("xlb_engine_sub_close(<%"PRId64">[\"%.*s\"])", id,
                (int)sub.length, (const char*)sub.key);
  size_t key_len = xlb_id_sub_buflen(sub);
  char key[key_len];
  xlb_write_id_sub(key, id, sub);
  
  // Record no longer subscribed
  void *tmp;
  bool was_subscribed = table_bp_remove(&id_sub_subscribed, key,
                                        key_len, &tmp);
  assert(was_subscribed);
  
  if (remote)
  {
    // Cache remote subscribes
    xlb_engine_code tc = id_sub_closed_cache_add(key, key_len);
    ENGINE_CHECK(tc);
  }

  struct list* L;
  
  bool found = table_bp_remove(&id_sub_blockers, key, key_len, (void**)&L);
  if (!found)
    // We don't have any transforms that block on this td
    return XLB_ENGINE_SUCCESS;

  return xlb_engine_close_update(L, id, sub, ready);
}

/*
  Update transforms after having one of blockers removed.
  blocked: list of transforms with blocker remoed
  id: id of data
  sub: optional subscript
  ready/ready_count: list of any work units made ready by this change,
      with ownership passed to caller
 */
static xlb_engine_code
xlb_engine_close_update(struct list *blocked, adlb_datum_id id,
         adlb_subscript sub, xlb_engine_work_array *ready)
{
  transform* T_prev = NULL;
  
  // Try to make progress on those transforms
  for (struct list_item* item = blocked->head; item; item = item->next)
  {
    transform* T = item->data;

    /*
     * Avoid processing transform multiple times if the input appeared
     * multiple times in the transform inputs.  We can assume that the
     * duplicates of a transform will appear consecutively in the list
     * because they would have been added at the same time.
     */
    if (T == T_prev)
      continue;

    // update closed vector
    if (!adlb_has_sub(sub))
    {
      DEBUG_ENGINE("Update {%"PRId64"} for close: <%"PRId64">", T->work->id, id);
      for (int i = T->blocker; i < T->input_tds; i++) {
        if (T->input_id_list[i] == id) {
          mark_input_id_closed(T, i);
        }
      }
    }
    else
    {
      DEBUG_ENGINE("Update {%"PRId64"} for subscript close: <%"PRId64">",
                    T->work->id, id);
      // Check to see which ones remain to be checked
      int first_id_sub;
      if (T->blocker >= T->input_tds)
        first_id_sub = T->blocker - T->input_tds;
      else
        first_id_sub = 0;

      for (int i = first_id_sub; i < T->input_id_subs; i++)
      {
        id_sub_pair *input_tdsub = &T->input_id_sub_list[i];
        engine_sub *input_sub = &input_tdsub->subscript;
        if (input_tdsub->td == id && input_sub->length == sub.length 
            && memcmp(input_sub->key, sub.key, sub.length) == 0)
        {
          mark_input_id_sub_closed(T, i);
        }
      }
    }

    bool subscribed;
    xlb_engine_code tc = progress(T, &subscribed);
    if (tc != XLB_ENGINE_SUCCESS)
      return tc;

    T_prev = T;
    if (!subscribed)
    {
      DEBUG_ENGINE("Ready {%"PRId64"}", T->work->id);
      tc = move_to_ready(ready, T);
      ENGINE_CHECK(tc);
    }
  }

  list_free(blocked); // No longer need list

  return XLB_ENGINE_SUCCESS;
}

/*
 * Add transform to ready array and remove from waiting table
 */
static inline xlb_engine_code
move_to_ready(xlb_engine_work_array *ready, transform *T)
{
  DEBUG_ENGINE("ready: {%"PRId64"}", T->work->id);
  if (ready->size <= ready->count)
  {
    if (ready->size == 0)
    {
      ready->size = 16;
    } else {
      ready->size *= 2;
    }
    ready->work = realloc(ready->work, sizeof(ready->work[0]) *
                          (size_t) ready->size);
    if (!ready->work)
      return XLB_ENGINE_ERROR_OOM;
  }
  ready->work[ready->count++] = T->work;

  list2_remove_item(&transforms_waiting, T->list_entry);
  free(T->list_entry);
  T->list_entry = NULL;
  
  T->work = NULL; // Don't free work
  transform_free(T);

  return XLB_ENGINE_SUCCESS;
}

/**
 * Check if a transform is done, and initiate progress if needed.
 *
 * We initiated all subscribes on entering work into engine, so we just
 * check to see if we're still waiting on anything.
 *
 * subscribed: set to true if still subscribed to data
 */
static xlb_engine_code
progress(transform* T, bool* subscribed)
{
  // first check TDs to see if all are ready
  for (; T->blocker < T->input_tds; T->blocker++)
  {
    if (!input_id_closed(T, T->blocker))
    {
      // TRACE("{%"PRId64"} blocked on <%"PRId64">", T->work->id, T->blocker);
      // Not yet done
      *subscribed = true;
      return XLB_ENGINE_SUCCESS;
    }
  }

  // now, make progress on any ID/subscript pairs
  int total_inputs = T->input_tds  + T->input_id_subs;
  for (; T->blocker < total_inputs; T->blocker++)
  {
    int id_sub_ix = T->blocker - T->input_tds;
    if (!input_id_sub_closed(T, id_sub_ix))
    {
      // Not yet done
      *subscribed = true;
      return XLB_ENGINE_SUCCESS;
    }
  }

  // Ready to run
  TRACE("{%"PRId64"} ready to run", T->work->id);
  *subscribed = false;
  return XLB_ENGINE_SUCCESS;
}

/**
   @return constant struct with name of code
*/
const char *
xlb_engine_code_tostring(xlb_engine_code code)
{
  switch (code)
  {
    case XLB_ENGINE_SUCCESS:
      return "XLB_ENGINE_SUCCESS";
    case XLB_ENGINE_ERROR_OOM:
      return "XLB_ENGINE_ERROR_OOM";
    case XLB_ENGINE_ERROR_INVALID:
      return "XLB_ENGINE_ERROR_INVALID";
    case XLB_ENGINE_ERROR_UNKNOWN:
      return "XLB_ENGINE_ERROR_UNKNOWN";
    case XLB_ENGINE_ERROR_UNINITIALIZED:
      return "XLB_ENGINE_ERROR_UNINITIALIZED";
    default:
      return "<INVALID_ERROR_CODE>";
  }
}

static int
transform_tostring(char* output, transform* t)
{
  int result = 0;
  char* p = output;
 
  if (t->name != NULL)
  {
    append(p, "%s ", t->name);
  }
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
    bool blocking = !input_id_closed(t, i);
    if (blocking)
      append(p, "/");
    // TODO: debug symbol?
    adlb_datum_id td = t->input_id_list[i];
    append(p, ADLB_PRID, ADLB_PRID_ARGS(td,
                                 xlb_get_dsym(td)));
    if (blocking)
      append(p, "/");
  }
  for (int i = 0; i < t->input_id_subs; i++)
  {
    if (first)
      first = false;
    else
      append(p, " ");

    // Highlight the blocking variable
    bool blocking = !input_id_sub_closed(t, i);
    id_sub_pair ts = t->input_id_sub_list[i];
    if (blocking)
      append(p, "/");
    // TODO: debug symbol?
    append(p, ADLB_PRIDSUB, ADLB_PRIDSUB_ARGS(
            ts.td, xlb_get_dsym(ts.td), sub_convert(ts.subscript)));
    if (blocking)
      append(p, "/");
  }
  append(p, ")");

  result = (int)(p - output);
  return result;
}

__attribute__((always_inline))
static inline bool
input_id_closed(transform *T, int i)
{
  assert(i >= 0);
  unsigned char field = T->closed_inputs[(unsigned int)i / 8];
  return (field >> ((unsigned int)i % 8)) & 0x1;
}

__attribute__((always_inline))
static inline bool
input_id_sub_closed(transform *T, int i)
{
  // closed_inputs had pairs come after tds
  return input_id_closed(T, i + T->input_tds);
}

__attribute__((always_inline))
// Extract bit from closed_inputs
static inline void
mark_input_id_closed(transform *T, int i)
{
  assert(i >= 0);
  unsigned char mask = (unsigned char) (0x1 << ((unsigned int)i % 8));
  T->closed_inputs[i / 8] |= mask;
}

__attribute__((always_inline))
static inline void
mark_input_id_sub_closed(transform *T, int i)
{
  mark_input_id_closed(T, i + T->input_tds);
}

static size_t
bitfield_size(int inputs) {
  if (inputs <= 0)
    return 0;
  // Round up to nearest multiple of 8
  return (size_t)(inputs - 1) / 8 + 1;
}

static xlb_engine_code init_closed_caches(void)
{
  id_closed_cache_size = DEFAULT_CLOSED_CACHE_SIZE;

  long tmp;
  adlb_code rc = xlb_env_long("ADLB_CLOSED_CACHE_SIZE", &tmp);
  ENGINE_CHECK_ADLB(rc, XLB_ENGINE_ERROR_INVALID);
  if (rc == ADLB_SUCCESS)
  {
    ENGINE_CONDITION(tmp >= 0 && tmp < INT_MAX,
                              XLB_ENGINE_ERROR_INVALID,
          "Invalid ADLB_CLOSED_CACHE_SIZE %li", tmp);
    id_closed_cache_size = (int)tmp;
  }

  // Same cache sizes for now
  id_sub_closed_cache_size = id_closed_cache_size;

  // Initialize to size large enough for all entries
  table_lp_init_custom(&id_closed_cache, id_closed_cache_size, 1.0);
  table_bp_init_custom(&id_sub_closed_cache, id_sub_closed_cache_size,
                       1.0);
  
  list2_b_init(&id_closed_cache_lru);
  list2_b_init(&id_sub_closed_cache_lru);
  return XLB_ENGINE_SUCCESS;
}

static void finalize_closed_caches(void)
{
  // Free tables.  Values are pointers to list nodes, which we free next
  table_lp_free_callback(&id_closed_cache, false, NULL);
  table_bp_free_callback(&id_sub_closed_cache, false, NULL);

  // Free LRU list
  list2_b_clear(&id_closed_cache_lru);
  list2_b_clear(&id_sub_closed_cache_lru);
}

// Move to top of lru list
static void id_closed_update_lru(struct list2_b_item *entry)
{
  if (id_closed_cache_lru.tail != entry)
  {
    // Remove and add back at tail
    list2_b_remove_item(&id_closed_cache_lru, entry);
    list2_b_add_item(&id_closed_cache_lru, entry);
  }
}

static void id_sub_closed_update_lru(struct list2_b_item *entry)
{
  if (id_sub_closed_cache_lru.tail != entry)
  {
    // Remove and add back at tail
    list2_b_remove_item(&id_sub_closed_cache_lru, entry);
    list2_b_add_item(&id_sub_closed_cache_lru, entry);
  }
}

// Return true if closed
static bool id_closed_cache_check(adlb_datum_id id)
{

  struct list2_b_item *entry;
  if (table_lp_search(&id_closed_cache, id, (void**)&entry))
  {
    // Was closed, just need to update LRU
    id_closed_update_lru(entry);
    return true;
  }
  else
  {
    return false;
  }
}

// Return true if closed
static bool id_sub_closed_cache_check(const void *key, size_t key_len)
{
  struct list2_b_item *entry;

  if (table_bp_search(&id_sub_closed_cache, key, key_len, (void**)&entry))
  {
    // Was closed, just need to update LRU
    id_sub_closed_update_lru(entry);
    return true;
  }
  else
  {
    return false;
  }
}

// Add that it was closed to cache
static xlb_engine_code id_closed_cache_add(adlb_datum_id id)
{
  if (id_closed_cache.size >= id_closed_cache_size)
  {
    // Evict an entry
    struct list2_b_item *victim = list2_b_pop_item(&id_closed_cache_lru);
    id_closed_cache_entry *victim_entry = (id_closed_cache_entry*)
                                          victim->data;
    void *tmp;
    bool removed = table_lp_remove(&id_closed_cache, victim_entry->id,
                                   &tmp);
    assert(removed && tmp == victim); // Should have had entry
    free(victim);
  }

  id_closed_cache_entry *entry;
  struct list2_b_item *node = list2_b_item_alloc(sizeof(*entry));
  entry = (id_closed_cache_entry*)&node->data;
  entry->id = id;
  
  list2_b_add_item(&id_closed_cache_lru, node);

  bool ok = table_lp_add(&id_closed_cache, id, node);
  if (!ok)
    return XLB_ENGINE_ERROR_OOM;

  return XLB_ENGINE_SUCCESS;
}

static xlb_engine_code
id_sub_closed_cache_add(const void *key, size_t key_len)
{
  if (id_sub_closed_cache.size >= id_sub_closed_cache_size)
  {
    // Evict an entry
    struct list2_b_item *victim =
                      list2_b_pop_item(&id_sub_closed_cache_lru);
    id_sub_closed_cache_entry *victim_entry =
                        (id_sub_closed_cache_entry*)victim->data;

    void *tmp;
    bool removed = table_bp_remove(&id_sub_closed_cache,
        victim_entry->key, victim_entry->key_len, &tmp);
    assert(removed && tmp == victim); // Should have had entry
    free(victim);
  }

  id_sub_closed_cache_entry *entry;
  size_t entry_size = sizeof(*entry) + key_len;
  struct list2_b_item *node = list2_b_item_alloc(entry_size);
  entry = (id_sub_closed_cache_entry*)&node->data;
  memcpy(entry->key, key, key_len);
  entry->key_len = key_len;
  
  list2_b_add_item(&id_sub_closed_cache_lru, node);

  bool ok = table_bp_add(&id_sub_closed_cache, key, key_len, node);
  if (!ok)
    return XLB_ENGINE_ERROR_OOM;

  return XLB_ENGINE_SUCCESS;
}

static void
info_waiting()
{
  // TODO: pass waiting tasks to higher-level handling code
  printf("WAITING WORK: %i\n", transforms_waiting.size);
  char buffer[1024];
  struct list2_item *item = transforms_waiting.head;
  while (item != NULL)
  {
    transform* t = item->data;
    assert(t != NULL);
    char id_string[24];
    assert(t->work != NULL);
    sprintf(id_string, "{%"PRId64"}", t->work->id);
    int c = sprintf(buffer, "%10s ", id_string);
    transform_tostring(buffer+c, t);
    printf("\t%s\n", buffer);

    item = item->next;
  }
}

// Callbacks to free data
static void free_transforms_waiting(void);
static void tbl_free_blockers_cb(adlb_datum_id key, void *L);
static void tbl_free_sub_blockers_cb(const void *key, size_t key_len, void *L);

void xlb_engine_finalize(void)
{
  if (!xlb_engine_initialized)
    return;

  // First report any problems we find
  if (transforms_waiting.size != 0)
    info_waiting();

  // Now we're done reporting, free everything
  free_transforms_waiting();
  table_lp_free_callback(&id_blockers, false, tbl_free_blockers_cb);
  table_bp_free_callback(&id_sub_blockers, false, tbl_free_sub_blockers_cb);

  // Entries in id_subscribed and id_sub_subscribed are not pointers and don't
  // need to be freed
  table_lp_free_callback(&id_subscribed, false, NULL);
  table_bp_free_callback(&id_sub_subscribed, false, NULL);

  finalize_closed_caches();
}

static void free_transforms_waiting(void)
{
  struct list2_item *item = transforms_waiting.head;
  struct list2_item *next;
  while (item != NULL)
  {
    transform *T = item->data; 
    next = item->next;
    transform_free(T);
    free(item);
    item = next;
  }
}

static void tbl_free_blockers_cb(adlb_datum_id key, void *L)
{
  list_free((struct list*)L);
}

static void tbl_free_sub_blockers_cb(const void *key, size_t key_len, void *L)
{
  list_free((struct list*)L);
}
