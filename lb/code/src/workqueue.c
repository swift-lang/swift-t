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


/*
 * workqueue.c
 *
 *  Created on: Jun 28, 2012
 *      Author: wozniak
 */

#include <assert.h>
#include <limits.h>
#include <stdlib.h>

#include <heap_iu32.h>
#include <list.h>
#include <ptr_array.h>
#include <table_ip.h>
#include <tools.h>
#include <rbtree.h>

#include "adlb-defs.h"
#include "common.h"
#include "debug.h"
#include "layout.h"
#include "messaging.h"
#include "requestqueue.h"
#include "workqueue.h"

// minimum percentage imbalance to trigger steal if stealers queue not empty
#define XLB_STEAL_IMBALANCE 0.1

#define XLB_SOFT_TARGET_PRIORITY_PENALTY 65536

static adlb_code init_work_heaps(heap_iu32_t** heap_array, int count);
static adlb_code xlb_workq_add_parallel(xlb_work_unit* wu);
static adlb_code xlb_workq_add_serial(xlb_work_unit* wu);
static adlb_code add_untargeted(xlb_work_unit* wu, uint32_t wu_idx);
static adlb_code add_targeted(xlb_work_unit* wu, uint32_t wu_idx);

static int targeted_work_entries(int work_types, int my_workers);
static inline heap_iu32_t *targeted_work_heap(int rank, int type);
static inline heap_iu32_t *host_targeted_work_heap(int host_idx, int type);
static inline int host_idx_from_rank2(int rank);

static bool wu_array_finalize(void);
static inline xlb_work_unit *
wu_array_try_remove_untargeted(uint32_t wu_idx, int type, int priority);
static inline xlb_work_unit *
wu_array_try_remove_targeted(uint32_t wu_idx, int type, int target, int priority);
static inline xlb_work_unit *
wu_array_try_remove_host_targeted(uint32_t wu_idx, int type, int host_idx,
                                  int priority);

static xlb_work_unit* pop_untargeted(int type);
static xlb_work_unit* pop_targeted(int type, int target);
static xlb_work_unit* pop_host_targeted(int type, int host_idx);

static adlb_code
heap_steal_type(heap_iu32_t *q, int type, double p, int *stolen,
                xlb_workq_steal_callback cb);
static adlb_code
rbtree_steal_type(struct rbtree *q, int num, xlb_workq_steal_callback cb);

static int soft_target_priority(int base_priority);

/** Uniquify work units on this server */
xlb_work_unit_id xlb_workq_next_id = 1;

/**
  Array of pointers to all non-parallel work units in workqueue.
  Integer index in array is used to identify the work unit within this
  module - the work unit is never relocated within array.  An index into
  this array can be validated by checking that the type/target/priority
  match as expected.

  Other structures provide indexes for quick lookup of work units.

  Note: this may be unfriendly for large arrays - need large contiguous
        allocation and need to copy on resize.
 */
static struct ptr_array wu_array = PTR_ARRAY_EMPTY;

#define WU_ARRAY_INIT_SIZE (1024 * 64)

/**
  untargeted_work and targeted_work provide indexes to lookup wu_array
  by on (type) and (target, type) and return the maximum priority entry.
  host_targeted_work is similar to targeted_work, but is index by a
  host identifier rather than rank.

  Work units can be in one or both indices, depending on what lookups
  need to be supported for them.

  untargeted work goes in untargeted_work only
  hard targeted work goes in targeted_work only
  soft targeted work goes in both untargeted_work and targeted_work
  hard host targeted work goes in host_targeted_work only
  soft host targeted work goes in both untargeted_work and host_targeted_work

  These indices are allowed to get out of sync with wu_array - stale
  entries are allowed in the indices.  This is ok so long as we check
  that the type and priority of the work unit match before returning.
 */

static heap_iu32_t* untargeted_work;

static heap_iu32_t *targeted_work;
static int targeted_work_size;  // Number of individual heaps

static heap_iu32_t *host_targeted_work;
static int host_targeted_work_size; // Number of heaps (hosts * types)

/** We should free heaps that are empty but more than this number of
 * unused entries. */
#define HEAP_FREE_THRESHOLD_TARGETED 64
#define HEAP_FREE_THRESHOLD_UNTARGETED 8192

/**
   parallel_work

   Storage for parallel work.
   Array of trees: one for each work type
   Does not contain targeted work
   The tree contains work_unit*
   Ordered by work unit priority
 */
static struct rbtree* parallel_work;

/*
  Track number of parallel tasks so we can skip the moderately expensive
  parallel task matching logic when there are no parallel tasks
 */
int64_t xlb_workq_parallel_task_count;

work_type_counters *xlb_task_counters;

adlb_code
xlb_workq_init(int work_types, const xlb_layout *layout)
{
  assert(work_types >= 1);
  DEBUG("xlb_workq_init(work_types=%i)", work_types);

  adlb_code ac;

  bool ok = ptr_array_init(&wu_array, WU_ARRAY_INIT_SIZE);
  ADLB_CHECK_MSG(ok, "wu_array initialisation failed");

  targeted_work_size = targeted_work_entries(work_types,
                                    layout->my_workers);
  ac = init_work_heaps(&targeted_work, targeted_work_size);
  ADLB_CHECK(ac);

  host_targeted_work_size = targeted_work_entries(work_types,
                                          layout->my_worker_hosts);
  ac = init_work_heaps(&host_targeted_work, host_targeted_work_size);
  ADLB_CHECK(ac);

  ac = init_work_heaps(&untargeted_work, work_types);
  ADLB_CHECK(ac);

  parallel_work = malloc(sizeof(parallel_work[0]) * (size_t)work_types);
  xlb_workq_parallel_task_count = 0;
  valgrind_assert(parallel_work != NULL);
  for (int i = 0; i < work_types; i++)
  {
    rbtree_init(&parallel_work[i]);
  }

  if (xlb_s.perfc_enabled)
  {
    DEBUG("PERF COUNTERS ENABLED");
    xlb_task_counters = malloc(sizeof(*xlb_task_counters) *
                               (size_t)work_types);
    valgrind_assert(xlb_task_counters != NULL);
    for (int i = 0; i < work_types; i++)
    {
      xlb_task_counters[i].targeted_enqueued = 0;
      xlb_task_counters[i].targeted_bypass = 0;
      xlb_task_counters[i].single_enqueued = 0;
      xlb_task_counters[i].single_bypass = 0;
      xlb_task_counters[i].single_stolen = 0;
      xlb_task_counters[i].parallel_enqueued = 0;
      xlb_task_counters[i].parallel_bypass = 0;
      xlb_task_counters[i].parallel_stolen = 0;

      xlb_task_counters[i].targeted_data_wait = 0;
      xlb_task_counters[i].targeted_data_no_wait = 0;
      xlb_task_counters[i].single_data_wait = 0;
      xlb_task_counters[i].single_data_no_wait = 0;
      xlb_task_counters[i].parallel_data_wait = 0;
      xlb_task_counters[i].parallel_data_no_wait = 0;
    }
  }
  else
  {
    xlb_task_counters = NULL;
  }

  return ADLB_SUCCESS;
}

static adlb_code init_work_heaps(heap_iu32_t** heap_array, int count)
{
  *heap_array = malloc(sizeof((*heap_array)[0]) * (size_t)count);
  ADLB_CHECK_MALLOC(*heap_array);

  for (int i = 0; i < count; i++)
  {
    bool ok = heap_iu32_init_empty(&(*heap_array)[i]);
    ADLB_CHECK_MSG(ok, "Could not allocate memory for heap");
  }

  return ADLB_SUCCESS;
}

static int targeted_work_entries(int work_types, int my_workers)
{
  TRACE("work_types: %i my_workers: %i", work_types, my_workers);
  return work_types * my_workers;
}

/*
 * Return targeted work index, or -1 if not targeted to current server.
 */
__attribute__((always_inline))
static inline heap_iu32_t *targeted_work_heap(int rank, int type)
{
  int idx = xlb_my_worker_idx(&xlb_s.layout, rank) * xlb_s.types_size
            + (int)type;
  assert(idx >= 0 && idx < targeted_work_size);
  return &targeted_work[idx];
}

__attribute__((always_inline))
static inline heap_iu32_t *host_targeted_work_heap(int host_idx, int type)
{
  int idx = host_idx * xlb_s.types_size + (int)type;
  assert(idx >= 0 && idx < targeted_work_size);
  return &host_targeted_work[idx];
}

adlb_code
xlb_workq_add(xlb_work_unit* wu)
{
  DEBUG("xlb_workq_add(): %"PRId64": x%i %s",
        wu->id, wu->opts.parallelism, (char*) wu->payload);

  if (wu->opts.parallelism > 1)
  {
    return xlb_workq_add_parallel(wu);
  } else {
    return xlb_workq_add_serial(wu);
  }
}

static adlb_code xlb_workq_add_parallel(xlb_work_unit* wu)
{
  // Untargeted parallel task
  TRACE("xlb_workq_add_parallel(): %p", wu);
  struct rbtree* T = &parallel_work[wu->type];
  TRACE("rbtree_add: wu: %p key: %i\n", wu, -wu->opts.priority);
  rbtree_add(T, -wu->opts.priority, wu);
  xlb_workq_parallel_task_count++;
  if (xlb_s.perfc_enabled)
  {
    xlb_task_counters[wu->type].parallel_enqueued++;
  }

  return ADLB_SUCCESS;
}

static adlb_code xlb_workq_add_serial(xlb_work_unit* wu)
{
  TRACE("xlb_workq_add_serial()");
  uint32_t wu_idx;

  bool ok = ptr_array_add(&wu_array, wu, &wu_idx);
  ADLB_CHECK_MSG(ok, "Could not add work unit");

  if (wu->target >= 0)
  {
    return add_targeted(wu, wu_idx);
  }
  else
  {
    return add_untargeted(wu, wu_idx);
  }
}

static adlb_code add_untargeted(xlb_work_unit* wu, uint32_t wu_idx)
{
  // Untargeted single-process task
  heap_iu32_t* H = &untargeted_work[wu->type];
  bool b = heap_iu32_add(H, -wu->opts.priority, wu_idx);
  ADLB_CHECK_MSG(b, "out of memory expanding heap");

  if (xlb_s.perfc_enabled)
  {
    xlb_task_counters[wu->type].single_enqueued++;
  }

  return ADLB_SUCCESS;
}

static adlb_code add_targeted(xlb_work_unit* wu, uint32_t wu_idx)
{
  // Targeted task
  if (xlb_worker_maps_to_server(&xlb_s.layout, wu->target,
                                xlb_s.layout.rank))
  {
    heap_iu32_t* H;
    if (wu->opts.accuracy == ADLB_TGT_ACCRY_RANK)
    {
      H = targeted_work_heap(wu->target, wu->type);
    }
    else
    {
      assert(wu->opts.accuracy == ADLB_TGT_ACCRY_NODE);
      int host_idx = host_idx_from_rank2(wu->target);
      H = host_targeted_work_heap(host_idx, wu->type);
    }
    bool b = heap_iu32_add(H, -wu->opts.priority, wu_idx);
    ADLB_CHECK_MSG(b, "out of memory expanding heap");
  }
  else
  {
    // All hard-targeted tasks should only be on matching server
    assert(wu->opts.strictness != ADLB_TGT_STRICT_HARD);
  }

  if (wu->opts.strictness != ADLB_TGT_STRICT_HARD)
  {
    int modified_priority = soft_target_priority(wu->opts.priority);

    // Also add entry to untargeted work
    DEBUG("Add to soft targeted: wu: %p key: %i\n", wu, -modified_priority);

    heap_iu32_t* H = &untargeted_work[wu->type];
    bool b = heap_iu32_add(H, -modified_priority, wu_idx);
    ADLB_CHECK_MSG(b, "out of memory expanding heap");
  }

  if (xlb_s.perfc_enabled)
  {
    xlb_task_counters[wu->type].targeted_enqueued++;
  }
  return ADLB_SUCCESS;
}

static bool wu_array_finalize(void)
{
  bool success = true;
  bool unmatched_serial = false;
  for (int i = 0; i < wu_array.capacity; i++)
  {
    xlb_work_unit *wu = wu_array.arr[i];
    if (wu != NULL)
    {
      // TODO: pass waiting tasks to higher-level handling code
      if (!unmatched_serial)
      {
        printf("ERROR: server contains work that was never received!\n");
        unmatched_serial = true;
        success = false;
      }
      if (wu->target < 0)
      {
        printf("  Untargeted work: type: %i\n", wu->type);
      }
      else
      {
        printf("  Targeted work: type: %i target rank: %i\n",
                    wu->type, wu->target);
      }
    }
  }

  ptr_array_clear(&wu_array);
  return success;
}

/*
  Try to remove and return untargeted entry.
   type: expected type
   priority: expected priority (from index)
   Return NULL if no longer present.
 */
__attribute__((always_inline))
static inline xlb_work_unit *
wu_array_try_remove_untargeted(uint32_t wu_idx, int type, int priority)
{
  xlb_work_unit* wu = ptr_array_get(&wu_array, wu_idx);
  /*
    Check not stale.  There is a corner case here where all of these
    match but it was a different work unit to the one the targeted
    entry was created for.  In that case it doesn't matter which
    was returned.

    We need to modify priority check for adjusted priority for soft
    targeted tasks.
   */
  if (wu != NULL && wu->type == type &&
      (wu->target < 0 || wu->opts.strictness != ADLB_TGT_STRICT_HARD)) {
    int modified_priority = wu->opts.priority;
    if (wu->opts.strictness != ADLB_TGT_STRICT_HARD)
    {
      modified_priority = soft_target_priority(modified_priority);
    }

    if (modified_priority == priority) {
      ptr_array_remove(&wu_array, wu_idx);
      return wu;
    }
  }

  return NULL;
}

/* Try to remove and return targeted entry.
   Return NULL if no longer present. */
__attribute__((always_inline))
static inline xlb_work_unit *
wu_array_try_remove_targeted(uint32_t wu_idx, int type, int target, int priority)
{
  xlb_work_unit* wu = ptr_array_get(&wu_array, wu_idx);
  /*
    Check not stale.  There is a corner case here where all of these
    match but it was a different work unit to the one the targeted
    entry was created for.  In that case it doesn't matter which
    was returned.
   */
  if (wu != NULL && wu->type == type && wu->target == target &&
      wu->opts.priority == priority) {
    ptr_array_remove(&wu_array, wu_idx);
    return wu;
  }

  return NULL;
}

/* Try to remove and return host targeted entry.
   Return NULL if no longer present. */
__attribute__((always_inline))
static inline xlb_work_unit *
wu_array_try_remove_host_targeted(uint32_t wu_idx, int type, int host_idx,
                                  int priority)
{
  xlb_work_unit* wu = ptr_array_get(&wu_array, wu_idx);
  /*
    Check not stale.  There is a corner case here where all of these
    match but it was a different work unit to the one the targeted
    entry was created for.  In that case it doesn't matter which
    was returned.
   */
  if (wu != NULL && wu->type == type &&
      host_idx_from_rank2(wu->target) == host_idx &&
      wu->opts.priority == priority) {
    ptr_array_remove(&wu_array, wu_idx);
    return wu;
  }

  return NULL;
}

// Soft-targeted work has reduced priority compared with non-targeted work
static int soft_target_priority(int base_priority)
{
  if (base_priority < INT_MIN + XLB_SOFT_TARGET_PRIORITY_PENALTY)
  {
    // Avoid underflow
    return INT_MIN;
  }
  else
  {
    return base_priority - XLB_SOFT_TARGET_PRIORITY_PENALTY;
  }
}

xlb_work_unit*
xlb_workq_get(int target, int type)
{
  DEBUG("xlb_workq_get(target=%i, type=%i)", target, type);

  xlb_work_unit* wu;

  // Targeted work was found
  wu = pop_targeted(type, target);
  if (wu != NULL)
  {
    return wu;
  }

  // Targeted work was found
  wu = pop_host_targeted(type, host_idx_from_rank2(target));
  if (wu != NULL)
  {
    return wu;
  }

  // Select untargeted work
  wu = pop_untargeted(type);
  if (wu != NULL)
  {
    return wu;
  }

  return NULL;
}

/**
  Pop an entry from a targeted queue, return NULL if none left.

  Implementation notes:
   Does not remove entry in untargeted_work if soft targeted
   Frees per target/type queue if empty
 */
static xlb_work_unit* pop_targeted(int type, int target)
{
  heap_iu32_t* H = targeted_work_heap(target, type);
  while (H->size > 0)
  {
    heap_iu32_entry_t root = heap_iu32_root(H);

    int priority = -root.key;
    uint32_t wu_idx = root.val;

    heap_iu32_del_root(H);

    xlb_work_unit* wu = wu_array_try_remove_targeted(wu_idx, type,
                                                target, priority);
    if (wu != NULL)
    {

      DEBUG("xlb_workq_get(): targeted: %"PRId64"", wu->id);
      return wu;
    }
  }

  // Clear empty heaps
  if (H->malloced_size > HEAP_FREE_THRESHOLD_TARGETED)
  {
    heap_iu32_clear(H);
  }
  return NULL;
}

/**
  Pop an entry from a host_targeted queue, return NULL if none left.

  Implementation notes:
   Does not remove entry in untargeted_work if soft targeted
   Frees per target/type queue if empty
 */
static xlb_work_unit* pop_host_targeted(int type, int host_idx)
{
  heap_iu32_t* H = host_targeted_work_heap(host_idx, type);
  while (H->size > 0)
  {
    heap_iu32_entry_t root = heap_iu32_root(H);

    int priority = -root.key;
    uint32_t wu_idx = root.val;

    heap_iu32_del_root(H);

    xlb_work_unit* wu = wu_array_try_remove_host_targeted(wu_idx, type,
                                                    host_idx, priority);
    if (wu != NULL)
    {

      DEBUG("xlb_workq_get(): host targeted: %"PRId64"", wu->id);
      return wu;
    }
  }

  // Clear empty heaps
  if (H->malloced_size > HEAP_FREE_THRESHOLD_TARGETED)
  {
    heap_iu32_clear(H);
  }
  return NULL;
}

static xlb_work_unit* pop_untargeted(int type)
{
  heap_iu32_t *H = &untargeted_work[type];
  while (H->size > 0)
  {
    heap_iu32_entry_t root = heap_iu32_root(H);

    int priority = -root.key;
    uint32_t wu_idx = root.val;

    heap_iu32_del_root(H);

    xlb_work_unit* wu = wu_array_try_remove_untargeted(wu_idx, type,
                                                       priority);
    if (wu != NULL) {
      DEBUG("xlb_workq_get(): untargeted: %"PRId64"", wu->id);
      return wu;
    }
  }

  // Clear empty heaps
  if (H->malloced_size > HEAP_FREE_THRESHOLD_UNTARGETED)
  {
    heap_iu32_clear(H);
  }
  return NULL;
}

/** Struct for user data during rbtree iterator search */
struct pop_parallel_data
{
  /** Input: ADLB task type */
  int type;
  /** Output: work unit that can be run */
  xlb_work_unit* wu;
  /** Output: ranks on which to run work unit */
  int* ranks;
  /** Output: node in rbtree to remove */
  struct rbtree_node* node;
  /** Smallest task seen so far */
  int smallest;
};

static bool pop_parallel_cb(struct rbtree_node* node,
                            void* user_data);

bool
xlb_workq_pop_parallel(xlb_work_unit** wu, int** ranks, int work_type)
{
  // TODO: cache the minimum size of parallel task of each type
  TRACE_START;
  bool result = false;
  struct rbtree* T = &parallel_work[work_type];
  DEBUG("xlb_workq_pop_parallel(): "
        "type: %i tree_size: %i", work_type, rbtree_size(T));
  // Common case is empty: want to exit ASAP:
  if (rbtree_size(T) == 0)
    goto end;

  struct pop_parallel_data data = { -1, NULL, NULL, NULL, INT_MAX };
  data.type = work_type;
  TRACE("iterator...");
  bool found = rbtree_iterator(T, pop_parallel_cb, &data);
  if (!found)
  {
    DEBUG("xlb_workq_pop_parallel(): nothing");
    goto end;
  }
  DEBUG("xlb_workq_pop_parallel(): found: wuid=%"PRId64,
        data.wu->id);
  *wu = data.wu;
  *ranks = data.ranks;
  result = true;
  // Release memory:
  rbtree_remove_node(T, data.node);
  TRACE("rbtree_removed: wu: %p node: %p...", wu, data.node);
  free(data.node);
  xlb_workq_parallel_task_count--;
  end:
  TRACE_END;
  return result;
}

static bool
pop_parallel_cb(struct rbtree_node* node, void* user_data)
{
  xlb_work_unit* wu = node->data;
  struct pop_parallel_data* data = user_data;
  int parallelism = wu->opts.parallelism;
  if (parallelism >= data->smallest)
    return false;

  TRACE("pop_parallel_cb(): wu: %p %"PRID64" x%i",
        wu, wu->id, parallelism);
  assert(parallelism > 0);

  int ranks[parallelism];
  bool found =
    xlb_requestqueue_parallel_workers(data->type, parallelism, ranks);
  if (! found)
  {
    if (parallelism < data->smallest)
      data->smallest = parallelism;
    return false;
  }

  data->wu = wu;
  data->node = node;
  data->ranks = malloc((size_t)parallelism * sizeof(int));
  valgrind_assert(data->ranks != NULL);
  memcpy(data->ranks, ranks, (size_t)parallelism * sizeof(int));
  return true;
}

adlb_code
xlb_workq_steal(int max_memory, const int *steal_type_counts,
                          xlb_workq_steal_callback cb)
{
  // for each type:
  //    select # to send to stealer
  //    randomly choose until meet quota
  for (int t = 0; t < xlb_s.types_size; t++)
  {
    int stealer_count = steal_type_counts[t];
    int single_count = (int)untargeted_work[t].size;
    int par_count = parallel_work[t].size;
    int tot_count = single_count + par_count;
    // TODO: handle ser and par separately?
    //  What if server A has single idle workers and parallel work,
    //    while server B has single work?

    // Only send if stealer has significantly fewer
    if (tot_count > 0) {
      bool send = false;
      if (stealer_count == 0) {
        send = true;
      }
      else
      {
        double imbalance = (tot_count - stealer_count) / (double) stealer_count;
        send = imbalance > XLB_STEAL_IMBALANCE;
      }
      if (send) {
        // Fraction of our tasks to send
        double send_pc = (tot_count - stealer_count) / (2.0 * tot_count);
        int par_to_send = (int)(send_pc * par_count);
        if (par_count > 0 && par_to_send == 0)
        {
          par_to_send = 1;
        }

        TRACE("xlb_workq_steal(): stealing type=%i single=%lf of %i par=%i/%i"
              " This server count: %i versus %i",
                        t, send_pc, single_count, par_to_send, par_count,
                        tot_count, stealer_count);
        adlb_code code;
        int single_sent;
        code = heap_steal_type(&(untargeted_work[t]), t, send_pc,
                               &single_sent, cb);
        ADLB_CHECK(code);
        code = rbtree_steal_type(&(parallel_work[t]), par_to_send, cb);
        xlb_workq_parallel_task_count -= par_to_send;
        ADLB_CHECK(code);

        if (xlb_s.perfc_enabled)
        {
          xlb_task_counters[t].single_stolen += single_sent;
          xlb_task_counters[t].parallel_stolen += par_to_send;
        }
      }
    }
  }
  return ADLB_SUCCESS;
}

/*
 * Steal work of a given type.
 * p: probability of stealing a given task
 * Note: we allow soft-targeted tasks to be stolen.
 */
static adlb_code
heap_steal_type(heap_iu32_t *q, int type, double p, int *stolen,
                xlb_workq_steal_callback cb)
{
  int p_threshold = (int)(p * RAND_MAX);
  *stolen = 0;

  /*
    Iterate backwards because removing entries sifts them down -
    best to remove from bottom first
   */
  for (long i = heap_iu32_size(q) - 1; i >= 0; i--)
  {
    if (rand() < p_threshold)
    {
      int priority = -q->array[i].key;
      uint32_t wu_idx = q->array[i].val;
      heap_iu32_del_entry(q, (heap_idx_t)i);

      xlb_work_unit* wu;
      wu = wu_array_try_remove_untargeted(wu_idx, type, priority);
      if (wu != NULL)
      {
        adlb_code code = cb.f(cb.data, wu);
        ADLB_CHECK(code);
        (*stolen)++;
      }
    }
  }
  return ADLB_SUCCESS;
}

/*
  Convenience function
 */
static inline int host_idx_from_rank2(int rank)
{
  return host_idx_from_rank(&xlb_s.layout, rank);
}


static adlb_code
rbtree_steal_type(struct rbtree *q, int num, xlb_workq_steal_callback cb)
{
  assert(q->size >= num);
  for (int i = 0; i < num; i++)
  {
    struct rbtree_node* node = rbtree_random(q);
    assert(node != NULL);
    rbtree_remove_node(q, node);
    xlb_work_unit *wu = (xlb_work_unit*) node->data;
    free(node);

    adlb_code code = cb.f(cb.data, wu);

    ADLB_CHECK(code);
  }
  return ADLB_SUCCESS;
}

void xlb_workq_type_counts(int *types, int size)
{
  assert(size >= xlb_s.types_size);
  for (int t = 0; t < xlb_s.types_size; t++)
  {
    assert(parallel_work[t].size >= 0);
    // TODO: it's possible that this will miscount because of removed soft-targeted work
    types[t] = (int)untargeted_work[t].size + parallel_work[t].size;
  }
}

static bool
wu_rbtree_clear_callback(struct rbtree_node *node, void *data)
{
  // Just free the work unit
  xlb_work_unit_free((xlb_work_unit*)data);
  return true;
}

void xlb_print_workq_perf_counters(void)
{
  if (!xlb_s.perfc_enabled)
  {
    return;
  }

  for (int t = 0; t < xlb_s.types_size; t++)
  {
    work_type_counters *c = &xlb_task_counters[t];
    /*
     * Print collected and derived stats:
     * total: total tasks passing through this server
     * net: total tasks, minus those passed to other servers,
     *      so that summing net gives actual tasks in whole system
     */

    PRINT_COUNTER("worktype_%i_targeted_total=%"PRId64"\n",
            t, c->targeted_enqueued + c->targeted_bypass);
    PRINT_COUNTER("worktype_%i_targeted_enqueued=%"PRId64"\n",
            t, c->targeted_enqueued);
    PRINT_COUNTER("worktype_%i_targeted_bypass=%"PRId64"\n",
            t, c->targeted_bypass);
    PRINT_COUNTER("worktype_%i_targeted_data_wait=%"PRId64"\n",
            t, c->targeted_data_wait);
    PRINT_COUNTER("worktype_%i_targeted_data_no_wait=%"PRId64"\n",
            t, c->targeted_data_no_wait);
    PRINT_COUNTER("worktype_%i_single_total=%"PRId64"\n",
            t, c->single_enqueued + c->single_bypass);
    PRINT_COUNTER("worktype_%i_single_net=%"PRId64"\n",
            t, c->single_enqueued + c->single_bypass - c->single_stolen);
    PRINT_COUNTER("worktype_%i_single_enqueued=%"PRId64"\n",
            t, c->single_enqueued);
    PRINT_COUNTER("worktype_%i_single_bypass=%"PRId64"\n",
            t, c->single_bypass);
    PRINT_COUNTER("worktype_%i_single_stolen=%"PRId64"\n",
            t, c->single_stolen);
    PRINT_COUNTER("worktype_%i_single_data_wait=%"PRId64"\n",
            t, c->single_data_wait);
    PRINT_COUNTER("worktype_%i_single_data_no_wait=%"PRId64"\n",
            t, c->single_data_no_wait);
    PRINT_COUNTER("worktype_%i_parallel_total=%"PRId64"\n",
            t, c->parallel_enqueued + c->parallel_bypass);
    PRINT_COUNTER("worktype_%i_parallel_net=%"PRId64"\n",
            t, c->parallel_enqueued + c->parallel_bypass - c->parallel_stolen);
    PRINT_COUNTER("worktype_%i_parallel_enqueued=%"PRId64"\n",
            t, c->parallel_enqueued);
    PRINT_COUNTER("worktype_%i_parallel_bypass=%"PRId64"\n",
            t, c->parallel_bypass);
    PRINT_COUNTER("worktype_%i_parallel_stolen=%"PRId64"\n",
            t, c->parallel_stolen);
    PRINT_COUNTER("worktype_%i_parallel_data_wait=%"PRId64"\n",
            t, c->parallel_data_wait);
    PRINT_COUNTER("worktype_%i_parallel_data_no_wait=%"PRId64"\n",
            t, c->parallel_data_no_wait);
  }
}

bool
xlb_workq_finalize()
{
  bool success = true;
  TRACE_START;

  // Remove unmatched serial work
  // Clear array and report unmatched serial work
  success = wu_array_finalize();

  // Clear up targeted_work heaps
  for (int i = 0; i < targeted_work_size; i++)
  {
    heap_iu32_clear_callback(&targeted_work[i], NULL);
  }
  free(targeted_work);
  targeted_work = NULL;

  for (int i = 0; i < host_targeted_work_size; i++)
  {
    heap_iu32_clear_callback(&host_targeted_work[i], NULL);
  }
  free(host_targeted_work);
  host_targeted_work = NULL;

  // Clear up untargeted_work heaps
  for (int i = 0; i < xlb_s.types_size; i++)
  {
    heap_iu32_clear_callback(&untargeted_work[i], NULL);
  }
  free(untargeted_work);
  untargeted_work = NULL;

  // Clear up parallel_work
  for (int i = 0; i < xlb_s.types_size; i++)
  {
    // TODO: pass waiting tasks to higher-level handling code
    if (parallel_work[i].size > 0)
    {
      printf("ERROR: server contains %i "
             "parallel work units of type %i:\n",
             parallel_work[i].size, i);
      success = false;
    }
    rbtree_clear_callback(&parallel_work[i], wu_rbtree_clear_callback);
  }
  free(parallel_work);
  parallel_work = NULL;

  if (xlb_s.perfc_enabled)
  {
    free(xlb_task_counters);
    xlb_task_counters = NULL;
  }
  TRACE_END;
  return success;
}
