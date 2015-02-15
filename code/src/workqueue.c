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

/*
   Each work unit is indexed by untargeted_work
   If the target is ANY, it is indexed by prioritized_work
   If the target is not ANY, it is indexed by targeted_work
      If soft_target is set, it is also indexed in prioritized work
      with a reduced priority
 */

#include <assert.h>
#include <limits.h>
#include <stdlib.h>

#include <heap_ii.h>
#include <list.h>
#include <table_ip.h>
#include <tools.h>
#include <rbtree.h>

#include "adlb-defs.h"
#include "common.h"
#include "debug.h"
#include "messaging.h"
#include "requestqueue.h"
#include "server.h"
#include "workqueue.h"


// minimum percentage imbalance to trigger steal if stealers queue not empty
#define XLB_STEAL_IMBALANCE 0.1

#define XLB_SOFT_TARGET_PRIORITY_PENALTY 65536

static adlb_code init_work_heaps(heap_ii_t** heap_array, int count);
static adlb_code xlb_workq_add_parallel(xlb_work_unit* wu);
static adlb_code xlb_workq_add_serial(xlb_work_unit* wu);
static adlb_code add_untargeted(xlb_work_unit* wu, int wu_id);
static adlb_code add_targeted(xlb_work_unit* wu, int wu_id);

static adlb_code wu_array_init(void);
static void wu_array_clear(void);
static void wu_array_finalize(void);
static adlb_code wu_array_expand(int new_size);
static adlb_code wu_array_add(xlb_work_unit* wu, int *wu_id);
static inline xlb_work_unit *wu_array_get(int wu_id);
static inline xlb_work_unit *
wu_array_try_remove_untargeted(int wu_id, int type, int priority);
static inline xlb_work_unit *
wu_array_try_remove_targeted(int wu_id, int type, int target, int priority);
static void wu_array_remove(int wu_id);

static xlb_work_unit* pop_untargeted(int type);
static xlb_work_unit* pop_targeted(int type, int target);

static adlb_code
heap_steal_type(heap_ii_t *q, int type, double p, int *stolen,
                xlb_workq_steal_callback cb);
static adlb_code
rbtree_steal_type(struct rbtree *q, int num, xlb_workq_steal_callback cb);

static int soft_target_priority(int base_priority);

/** Uniquify work units on this server */
static xlb_work_unit_id unique = 1;

/**
  Storage for non-parallel work units.  Integer index in array identifies
  work unit within this module - work unit isn't relocated within array.

  Other structures provide indexes for quick lookup of work units.

  Note: this may be unfriendly for large arrays - need large contiguous
        allocation and need to copy on resize.
 */
static struct {
  xlb_work_unit **arr;
  int *free; // Gaps in work array, same size as arr

  int size;
  int free_count;
} wu_array = { NULL, NULL, 0, 0 };

#define WU_ARRAY_INIT_SIZE (1024 * 64)

/**
  untargeted_work and targeted_work provide indexes to lookup wu_array
  by on (type) and (target, type) and return the maximum priority entry.

  Work units can be in one or both indices, depending on what lookups
  need to be supported for them.

  untargeted work goes in untargeted_work only
  hard targeted work goes in targeted_work only
  soft targeted work goes in both untargeted_work and targeted_work

  These indices are allowed to get out of sync with wu_array - stale
  entries are allowed in the indices.  This is ok so long as we check
  that the type and priority of the work unit match before returning.
 */

static heap_ii_t* untargeted_work;

static heap_ii_t *targeted_work;
static int targeted_work_size;  // Number of individual heaps

static int targeted_work_entries(int work_types, int my_workers)
{
  TRACE("work_types: %i my_workers: %i", work_types, my_workers);
  return work_types * my_workers;
}

/*
 * Return targeted work index, or -1 if not targeted to current server.
 */
__attribute__((always_inline))
static inline int targeted_work_ix(int rank, int type)
{
  int ix = xlb_my_worker_ix(rank) * xlb_types_size + (int)type;
  assert(ix >= 0 && ix < targeted_work_size);
  return ix;
}

/**
   parallel_work
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
xlb_workq_init(int work_types, int my_workers)
{
  assert(work_types >= 1);
  DEBUG("xlb_workq_init(work_types=%i)", work_types);

  adlb_code ac = wu_array_init();
  ADLB_CHECK(ac);

  targeted_work_size = targeted_work_entries(work_types, my_workers);
  ac = init_work_heaps(&targeted_work, targeted_work_size);
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

  if (xlb_perf_counters_enabled)
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

static adlb_code init_work_heaps(heap_ii_t** heap_array, int count)
{
  *heap_array = malloc(sizeof((*heap_array)[0]) * (size_t)count);
  ADLB_MALLOC_CHECK(*heap_array);

  for (int i = 0; i < count; i++)
  {
    bool ok = heap_ii_init_empty(&(*heap_array)[i]);
    CHECK_MSG(ok, "Could not allocate memory for heap");
  }

  return ADLB_SUCCESS;
}

xlb_work_unit_id
xlb_workq_unique()
{
  return unique++;
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
  if (xlb_perf_counters_enabled)
  {
    xlb_task_counters[wu->type].parallel_enqueued++;
  }

  return ADLB_SUCCESS;
}

static adlb_code xlb_workq_add_serial(xlb_work_unit* wu)
{
  adlb_code ac;
  TRACE("xlb_workq_add_serial()");
  int wu_id;

  ac = wu_array_add(wu, &wu_id);
  ADLB_CHECK(ac);

  if (wu->target >= 0)
  {
    return add_targeted(wu, wu_id);
  }
  else
  {
    return add_untargeted(wu, wu_id);
  }
}

static adlb_code add_untargeted(xlb_work_unit* wu, int wu_id)
{
  // Untargeted single-process task
  heap_ii_t* H = &untargeted_work[wu->type];
  bool b = heap_ii_add(H, -wu->opts.priority, wu_id);
  CHECK_MSG(b, "out of memory expanding heap");

  if (xlb_perf_counters_enabled)
  {
    xlb_task_counters[wu->type].single_enqueued++;
  }

  return ADLB_SUCCESS;
}

static adlb_code add_targeted(xlb_work_unit* wu, int wu_id)
{
  // Targeted task
  if (xlb_worker_maps_to_server(wu->target, xlb_comm_rank))
  {
    heap_ii_t* H = &targeted_work[targeted_work_ix(wu->target, wu->type)];
    bool b = heap_ii_add(H, -wu->opts.priority, wu_id);
    CHECK_MSG(b, "out of memory expanding heap");
  }
  else
  {
    // All hard-targeted tasks should only be on matching server
    assert(wu->opts.soft_target);
  }

  if (wu->opts.soft_target)
  {
    int modified_priority = soft_target_priority(wu->opts.priority);

    // Also add entry to untargeted work
    TRACE("add for soft targeted: wu: %p key: %i\n", wu, -modified_priority);

    heap_ii_t* H = &untargeted_work[wu->type];
    bool b = heap_ii_add(H, -modified_priority, wu_id);
    CHECK_MSG(b, "out of memory expanding heap");
  }

  if (xlb_perf_counters_enabled)
  {
    xlb_task_counters[wu->type].targeted_enqueued++;
  }
  return ADLB_SUCCESS;
}

static adlb_code wu_array_init(void)
{
  wu_array_clear();
  return wu_array_expand(WU_ARRAY_INIT_SIZE);
}

static void wu_array_clear(void)
{
  free(wu_array.arr);
  free(wu_array.free);

  wu_array.arr = NULL;
  wu_array.free = NULL;
  wu_array.size = wu_array.free_count = 0;
}

static void wu_array_finalize(void)
{
  bool unmatched_serial = false;
  for (int i = 0; i < wu_array.size; i++)
  {
    xlb_work_unit *wu = wu_array.arr[i];
    if (wu != NULL)
    {
      // TODO: pass waiting tasks to higher-level handling code
      if (!unmatched_serial)
      {
        printf("WARNING: server contains work that was never received!\n");
        unmatched_serial = true;
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

  wu_array_clear();
}

static adlb_code wu_array_expand(int new_size)
{
  assert(wu_array.free_count == 0);

  xlb_work_unit **new_arr = realloc(wu_array.arr,
              (size_t)new_size * sizeof(wu_array.arr[0]));
  ADLB_MALLOC_CHECK(new_arr);

  int *new_free = realloc(wu_array.free,
              (size_t)new_size * sizeof(wu_array.free[0]));
  ADLB_MALLOC_CHECK(new_free);

  int old_size = wu_array.size;

  wu_array.arr = new_arr;
  wu_array.size = new_size;
  memset(&wu_array.arr[old_size], 0, (size_t)(new_size - old_size)
                                     * sizeof(wu_array.arr[0]));

  // Add new unused to free list
  wu_array.free_count = new_size - old_size;
  wu_array.free = new_free;
  for (int i = 0; i < wu_array.free_count; i++)
  {
    int unused_wu_id = old_size + i;
    wu_array.free[i] = unused_wu_id;
    assert(wu_array.arr[unused_wu_id] == NULL);
  }

  return ADLB_SUCCESS;
}

static adlb_code wu_array_add(xlb_work_unit* wu, int *wu_id)
{
  if (wu_array.free_count == 0)
  {
    // No free case - resize work array and free list
    int new_size = wu_array.size * 2;

    adlb_code ac = wu_array_expand(new_size);
    ADLB_CHECK(ac);
  }

  *wu_id = wu_array.free[wu_array.free_count - 1];
  wu_array.free_count--;

  wu_array.arr[*wu_id] = wu;
  return ADLB_SUCCESS;
}

__attribute__((always_inline))
static inline xlb_work_unit *wu_array_get(int wu_id)
{
  assert(wu_id >= 0); // Negative numbers shouldn't be generated internally
  return wu_id < wu_array.size ? wu_array.arr[wu_id] : NULL;
}

/*
  Try to remove and return untargeted entry.
   type: expected type
   priority: expected priority (from index)
   Return NULL if no longer present.
 */
__attribute__((always_inline))
static inline xlb_work_unit *
wu_array_try_remove_untargeted(int wu_id, int type, int priority)
{
  xlb_work_unit* wu = wu_array_get(wu_id);
  /*
    Check not stale.  There is a corner case here where all of these
    match but it was a different work unit to the one the targeted
    entry was created for.  In that case it doesn't matter which
    was returned.

    We need to modify priority check for adjusted priority for soft
    targeted tasks.
   */
  if (wu != NULL && wu->type == type &&
      (wu->target < 0 || wu->opts.soft_target)) {
    int exp_priority = priority;
    if (wu->opts.soft_target)
    {
      exp_priority = soft_target_priority(priority);
    }

    if (wu->opts.priority == exp_priority) {
      wu_array_remove(wu_id);
      return wu;
    }
  }

  return NULL;
}

/* Try to remove and return untargeted entry.
   Return NULL if no longer present. */
__attribute__((always_inline))
static inline xlb_work_unit *
wu_array_try_remove_targeted(int wu_id, int type, int target, int priority)
{
  xlb_work_unit* wu = wu_array_get(wu_id);
  /*
    Check not stale.  There is a corner case here where all of these
    match but it was a different work unit to the one the targeted
    entry was created for.  In that case it doesn't matter which
    was returned.
   */
  if (wu != NULL && wu->type == type && wu->target == target &&
      wu->opts.priority == -priority) {
    wu_array_remove(wu_id);
    return wu;
  }

  return NULL;
}

static void wu_array_remove(int wu_id)
{
  wu_array.arr[wu_id] = NULL;
  wu_array.free[wu_array.free_count++] = wu_id;
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
  heap_ii_t* H = &targeted_work[targeted_work_ix(target, type)];
  while (H->size > 0)
  {
    heap_ii_entry_t root = heap_ii_root(H);

    int priority = root.key;
    int wu_id = root.val;

    heap_ii_del_root(H);

    xlb_work_unit* wu = wu_array_try_remove_targeted(wu_id, type,
                                                target, priority);
    if (wu != NULL)
    {

      DEBUG("xlb_workq_get(): targeted: %"PRId64"", wu->id);
      return wu;
    }
  }

  // Clear empty heaps
  heap_ii_clear(H);
  return NULL;
}

static xlb_work_unit* pop_untargeted(int type)
{
  heap_ii_t *H = &untargeted_work[type];
  while (H->size > 0)
  {
    heap_ii_entry_t root = heap_ii_root(H);

    int priority = root.key;
    int wu_id = root.val;

    heap_ii_del_root(H);

    xlb_work_unit* wu = wu_array_try_remove_untargeted(wu_id, type,
                                                       priority);
    if (wu != NULL) {
      DEBUG("xlb_workq_get(): untargeted: %"PRId64"", wu->id);
      return wu;
    }
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
};

static bool pop_parallel_cb(struct rbtree_node* node,
                            void* user_data);

bool
xlb_workq_pop_parallel(xlb_work_unit** wu, int** ranks, int work_type)
{
  //TODO: cache the minimum size of parallel task of each type
  TRACE_START;
  bool result = false;
  struct rbtree* T = &parallel_work[work_type];
  TRACE("type: %i tree_size: %i", work_type, rbtree_size(T));
  // Common case is empty: want to exit asap
  if (rbtree_size(T) != 0)
  {
    struct pop_parallel_data data = { -1, NULL, NULL, NULL };
    data.type = work_type;
    TRACE("iterator...");
    bool found = rbtree_iterator(T, pop_parallel_cb, &data);
    if (found)
    {
      TRACE("found...");
      *wu = data.wu;
      *ranks = data.ranks;
      result = true;
      // Release memory:
      rbtree_remove_node(T, data.node);
      TRACE("rbtree_removed: wu: %p node: %p...", wu, data.node);
      free(data.node);
      xlb_workq_parallel_task_count--;
    }
  }
  TRACE_END;
  return result;
}

static bool
pop_parallel_cb(struct rbtree_node* node, void* user_data)
{
  xlb_work_unit* wu = node->data;
  struct pop_parallel_data* data = user_data;
  int parallelism = wu->opts.parallelism;

  TRACE("pop_parallel_cb(): wu: %p %"PRId64" x%i",
        wu, wu->id, parallelism);
  assert(parallelism > 0);

  int ranks[parallelism];
  bool found = xlb_requestqueue_parallel_workers(data->type, parallelism,
                                        ranks);
  if (found)
  {
    data->wu = wu;
    data->node = node;
    data->ranks = malloc((size_t)parallelism * sizeof(int));
    valgrind_assert(data->ranks != NULL);
    memcpy(data->ranks, ranks, (size_t)parallelism * sizeof(int));
    return true;
  }
  return false;
}

adlb_code
xlb_workq_steal(int max_memory, const int *steal_type_counts,
                          xlb_workq_steal_callback cb)
{
  // for each type:
  //    select # to send to stealer
  //    randomly choose until meet quota
  for (int t = 0; t < xlb_types_size; t++)
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
        int to_send = (tot_count - stealer_count) / 2;
        if (to_send == 0)
          to_send = 1;
        double single_pc = single_count / (double) tot_count;
        double par_pc = par_count / (double) tot_count;
        int par_to_send = (int)(par_pc * to_send);
        TRACE("xlb_workq_steal(): stealing type=%i single=%i/%i par=%i/%i"
              " This server count: %i versus %i",
                        t, single_to_send, single_count, par_to_send, par_count,
                        tot_count, stealer_count);
        adlb_code code;
        int single_sent;
        code = heap_steal_type(&(untargeted_work[t]), t, single_pc,
                               &single_sent, cb);
        ADLB_CHECK(code);
        code = rbtree_steal_type(&(parallel_work[t]), par_to_send, cb);
        xlb_workq_parallel_task_count -= par_to_send;
        ADLB_CHECK(code);

        if (xlb_perf_counters_enabled)
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
heap_steal_type(heap_ii_t *q, int type, double p, int *stolen,
                xlb_workq_steal_callback cb)
{
  int p_threshold = (int)(p * RAND_MAX);
  *stolen = 0;

  /*
    Iterate backwards because removing entries sifts them down -
    best to remove from bottom first
   */
  for (int i = (int)heap_ii_size(q) - 1; i >= 0; i--)
  {
    if (rand() > p_threshold)
    {
      int priority = q->array[i].key;
      int wu_id = q->array[i].val;
      heap_ii_del_entry(q, (heap_ix_t)i);

      xlb_work_unit* wu;
      wu = wu_array_try_remove_untargeted(wu_id, type, priority);
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
  assert(size >= xlb_types_size);
  for (int t = 0; t < xlb_types_size; t++)
  {
    assert(untargeted_work[t].size >= 0);
    assert(parallel_work[t].size >= 0);
    types[t] = (int)untargeted_work[t].size + parallel_work[t].size;
  }
}

void
xlb_work_unit_free(xlb_work_unit* wu)
{
  free(wu);
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
  if (!xlb_perf_counters_enabled)
  {
    return;
  }

  for (int t = 0; t < xlb_types_size; t++)
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

void
xlb_workq_finalize()
{
  TRACE_START;

  // Remove unmatched serial work
  // Clear array and report unmatched serial work
  wu_array_finalize();

  // Clear up targeted_work heaps
  for (int i = 0; i < targeted_work_size; i++)
  {
    heap_ii_clear_callback(&targeted_work[i], NULL);
  }
  free(targeted_work);
  targeted_work = NULL;

  // Clear up untargeted_work heaps
  for (int i = 0; i < xlb_types_size; i++)
  {
    heap_ii_clear_callback(&untargeted_work[i], NULL);
  }
  free(untargeted_work);
  untargeted_work = NULL;

  // Clear up parallel_work
  for (int i = 0; i < xlb_types_size; i++)
  {
    // TODO: pass waiting tasks to higher-level handling code
    if (parallel_work[i].size > 0)
      printf("WARNING: server contains %i "
             "parallel work units of type %i:\n",
             parallel_work[i].size, i);
    rbtree_clear_callback(&parallel_work[i], wu_rbtree_clear_callback);
  }
  free(parallel_work);
  parallel_work = NULL;

  if (xlb_perf_counters_enabled)
  {
    free(xlb_task_counters);
    xlb_task_counters = NULL;
  }
  TRACE_END;
}
