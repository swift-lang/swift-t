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
   Each work unit is indexed by typed_work
   If the target is ANY, it is indexed by prioritized_work
   If the target is not ANY, it is indexed by targeted_work
      If soft_target is set, it is also indexed in prioritized work
      with the minimum priority
 */

#include <assert.h>
#include <limits.h>

#include <heap.h>
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

#define XLB_SOFT_TARGET_PRIORITY_PENALTY -65536


static void
xlb_workq_remove_soft_targeted(int type, int target, xlb_work_unit *wu);

/** Uniquify work units on this server */
static xlb_work_unit_id unique = 1;

/**
   Heaps for target rank/type combinations, heap sorted by negative priority.
   This only include worker ranks that belong to this server.
   You can obtain the heap for a rank with targeted_work_ix(rank, type)
   Only contains targeted work
*/
static heap_t *targeted_work;
static int targeted_work_size;

static int targeted_work_entries(int work_types, int my_workers)
{
  TRACE("work_types: %i my_workers: %i", work_types, my_workers);
  return work_types * my_workers;
}

__attribute__((always_inline))
static inline int targeted_work_ix(int rank, int type)
{
  int ix = xlb_my_worker_ix(rank) * xlb_types_size + (int)type;
  assert(ix >= 0 && ix < targeted_work_size);
  return ix;
}

// Calculate index for one of my workers

/**
   typed_work
   Array of trees: one for each work type
   Does not contain targeted work
   The tree contains work_unit*
   Ordered by work unit priority
 */
static struct rbtree* typed_work;

/**
   parallel_work
   Array of trees: one for each work type
   Does not contain targeted work
   The tree contains work_unit*
   Ordered by work unit priority
 */
static struct rbtree* parallel_work;

// Track number of parallel tasks
int64_t xlb_workq_parallel_task_count;

work_type_counters *xlb_task_counters;

adlb_code
xlb_workq_init(int work_types, int my_workers)
{
  assert(work_types >= 1);
  DEBUG("xlb_workq_init(work_types=%i)", work_types);

  int targeted_entries = targeted_work_entries(work_types, my_workers);
  targeted_work = malloc(sizeof(heap_t) * (size_t)targeted_entries);
  targeted_work_size = targeted_entries;
  valgrind_assert(targeted_work != NULL);
  for (int i = 0; i < targeted_entries; i++)
  {
    bool ok = heap_init_empty(&targeted_work[i]);
    CHECK_MSG(ok, "Could not allocate memory for heap");
  }

  typed_work = malloc(sizeof(struct rbtree) * (size_t)work_types);
  valgrind_assert(typed_work != NULL);
  parallel_work = malloc(sizeof(struct rbtree) * (size_t)work_types);
  xlb_workq_parallel_task_count = 0;
  valgrind_assert(parallel_work != NULL);
  for (int i = 0; i < work_types; i++)
  {
    rbtree_init(&typed_work[i]);
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

xlb_work_unit_id
xlb_workq_unique()
{
  return unique++;
}

adlb_code
xlb_workq_add(xlb_work_unit* wu)
{
  DEBUG("xlb_workq_add(): %"PRId64": x%i %s",
        wu->id, wu->parallelism, (char*) wu->payload);

  if (wu->target < 0 && wu->parallelism == 1)
  {
    // Untargeted single-process task
    TRACE("xlb_workq_add(): single-process");
    struct rbtree* T = &typed_work[wu->type];
    rbtree_add(T, -wu->priority, wu);
    if (xlb_perf_counters_enabled)
    {
      xlb_task_counters[wu->type].single_enqueued++;
    }
  }
  else if (wu->parallelism > 1)
  {
    // Untargeted parallel task
    TRACE("xlb_workq_add(): parallel task: %p", wu);
    struct rbtree* T = &parallel_work[wu->type];
    TRACE("rbtree_add: wu: %p key: %i\n", wu, -wu->priority);
    rbtree_add(T, -wu->priority, wu);
    xlb_workq_parallel_task_count++;
    if (xlb_perf_counters_enabled)
    {
      xlb_task_counters[wu->type].parallel_enqueued++;
    }
  }
  else
  {
    // Targeted task
    heap_t* H = &targeted_work[targeted_work_ix(wu->target, wu->type)];
    bool b = heap_add(H, -wu->priority, wu);
    CHECK_MSG(b, "out of memory expanding heap");

    if (wu->flags.soft_target)
    {
      // Soft-targeted work has reduced priority compared with non-targeted work
      int base_priority = wu->priority;
      int modified_priority;
      if (base_priority < INT_MIN - XLB_SOFT_TARGET_PRIORITY_PENALTY)
      {
        // Avoid underflow
        modified_priority = INT_MIN;
      }
      else
      {
        modified_priority = base_priority - XLB_SOFT_TARGET_PRIORITY_PENALTY;
      }

      // Add duplicate entry
      TRACE("rbtree_add for soft targeted: wu: %p key: %i\n", wu, -modified_priority);

      struct rbtree* T = &typed_work[wu->type];
      struct rbtree_node *N = malloc(sizeof(struct rbtree_node));
      ADLB_MALLOC_CHECK(N);

      N->key = -modified_priority;
      N->data = wu;
      wu->__internal = N; // Store entry to node to allow deletion later
      
      rbtree_add_node(T, N);
    }

    if (xlb_perf_counters_enabled)
    {
      xlb_task_counters[wu->type].targeted_enqueued++;
    }
  }
  return ADLB_SUCCESS;
}

/**
   If we have no more targeted work for this rank, clean up data
   structures
 */
static inline xlb_work_unit*
pop_targeted(heap_t* H, int target)
{
  xlb_work_unit* result = heap_root_val(H);
  DEBUG("xlb_workq_get(): targeted: %"PRId64"", result->id);
  heap_del_root(H);
  if (heap_size(H) == 0)
  {
    // Free storage for empty heaps
    heap_clear(H);
  }

  if (result->flags.soft_target)
  {
    // Matching entry was added in typed for soft targeted
    struct rbtree_node *typed_node = result->__internal;
    assert(typed_node != NULL);

    rbtree_remove_node(&typed_work[result->type], typed_node);
    free(typed_node);
    result->__internal = NULL;
  }

  return result;
}

xlb_work_unit*
xlb_workq_get(int target, int type)
{
  DEBUG("xlb_workq_get(target=%i, type=%i)", target, type);

  xlb_work_unit* wu = NULL;

  // Targeted work was found
  heap_t* H = &targeted_work[targeted_work_ix(target, type)];
  if (heap_size(H) != 0)
  {
    wu = pop_targeted(H, target);
    return wu;
  }

  // Select untargeted work
  struct rbtree* T = &typed_work[type];
  struct rbtree_node* node = rbtree_leftmost(T);
  if (node == NULL)
    // We found nothing
    return NULL;
  rbtree_remove_node(T, node);
  wu = node->data;
  DEBUG("xlb_workq_get(): untargeted: %"PRId64"", wu->id);
  free(node);

  if (wu->target >= 0)
  {
    xlb_workq_remove_soft_targeted(type, target, wu);
  }

  return wu;
}

/*
 * Remove entry from targeted queue for soft targeted task.
 * type: type of work
 * target: target of work
 * wu: the work unit pointer
 */
static void
xlb_workq_remove_soft_targeted(int type, int target, xlb_work_unit *wu)
{
  assert(target >= 0);
  assert(wu != NULL);
  assert(wu->flags.soft_target);
  /*
   * Remove entry from targeted heap.
   * We do a linear search, which is potentially expensive, but will
   * will only require checking many entries if the rank has many
   * hard-targeted tasks enqueued */
  heap_t* H = &targeted_work[targeted_work_ix(target, type)];
  for (uint32_t i = 0; i < H->size; i++)
  {
    heap_entry_t *entry = &H->array[i];
    if (entry->val == wu)
    {
      heap_del_entry(H, i);
      return;
    }
  }
  // Should have been found
  assert(false);
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

  TRACE("pop_parallel_cb(): wu: %p %"PRId64" x%i",
        wu, wu->id, wu->parallelism);
  assert(wu->parallelism > 0);

  int ranks[wu->parallelism];
  bool found =
      xlb_requestqueue_parallel_workers(data->type, wu->parallelism,
                                        ranks);
  if (found)
  {
    data->wu = wu;
    data->node = node;
    data->ranks = malloc((size_t)wu->parallelism * sizeof(int));
    valgrind_assert(data->ranks != NULL);
    memcpy(data->ranks, ranks, (size_t)wu->parallelism * sizeof(int));
    return true;
  }
  return false;
}

/*
 * Steal work of a given type.
 * Note: we allow soft-targeted tasks to be stolen.
 */
static adlb_code
xlb_workq_steal_type(struct rbtree *q, int num,
                      xlb_workq_steal_callback cb)
{
  assert(q->size >= num);
  for (int i = 0; i < num; i++)
  {
    struct rbtree_node* node = rbtree_random(q);
    assert(node != NULL);
    rbtree_remove_node(q, node);
    xlb_work_unit *wu = (xlb_work_unit*) node->data;
    free(node);

    if (wu->target >= 0) {
      xlb_workq_remove_soft_targeted(wu->type, wu->target, wu);
    }

    adlb_code code = cb.f(cb.data, wu);

    ADLB_CHECK(code);
  }
  return ADLB_SUCCESS;
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
    int single_count = typed_work[t].size;
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
        int single_to_send = (int)(single_pc * to_send);
        int par_to_send = to_send - single_to_send;
        TRACE("xlb_workq_steal(): stealing type=%i single=%i/%i par=%i/%i"
              " This server count: %i versus %i",
                        t, single_to_send, single_count, par_to_send, par_count,
                        tot_count, stealer_count);
        adlb_code code;
        code = xlb_workq_steal_type(&(typed_work[t]), single_to_send, cb);
        ADLB_CHECK(code);
        code = xlb_workq_steal_type(&(parallel_work[t]), par_to_send, cb);
        xlb_workq_parallel_task_count -= par_to_send;
        ADLB_CHECK(code);

        if (xlb_perf_counters_enabled)
        {
          xlb_task_counters[t].single_stolen += single_to_send;
          xlb_task_counters[t].parallel_stolen += par_to_send;
        }
      }
    }
  }
  return ADLB_SUCCESS;
}

void xlb_workq_type_counts(int *types, int size)
{
  assert(size >= xlb_types_size);
  for (int t = 0; t < xlb_types_size; t++)
  {
    assert(typed_work[t].size >= 0);
    assert(parallel_work[t].size >= 0);
    types[t] = typed_work[t].size + parallel_work[t].size;
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

static void
wu_heap_clear_callback(heap_key_t k, heap_val_t v)
{
  xlb_work_unit *wu = (xlb_work_unit*)v;
  // Free soft targeted via typed work
  if (!wu->flags.soft_target)
  {
    xlb_work_unit_free(wu);
  }
}

static void
targeted_heap_clear(heap_t *H)
{
  if (H->size > 0)
  {
    printf("WARNING: server contains targeted work that was never "
           "received by target!\n");
    for (int j = 0; j < H->size; j++)
    {
      xlb_work_unit *wu = H->array[j].val;
      printf("  Targeted work: type: %i target rank: %i\n",
                  wu->type, wu->target);
    }
  }

  // free the work unit
  heap_clear_callback(H, wu_heap_clear_callback);
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

  // Clear up targeted_work
  int targeted_entries = targeted_work_entries(xlb_types_size, xlb_my_workers);
  for (int i = 0; i < targeted_entries; i++)
  {
    // TODO: report unmatched targeted work
    targeted_heap_clear(&targeted_work[i]);
  }
  free(targeted_work);
  targeted_work = NULL;

  // Clear up typed_work
  for (int i = 0; i < xlb_types_size; i++)
  {
    // TODO: pass waiting tasks to higher-level handling code
    int count = (&typed_work[i])->size;
    if (count > 0)
      printf("WARNING: server contains %i work units of type: %i\n",
             count, i);
    rbtree_clear_callback(&typed_work[i], wu_rbtree_clear_callback);
  }
  free(typed_work);
  typed_work = NULL;

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
