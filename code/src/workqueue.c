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
   If the target is not ANY, it is indexed by targeted_work
   If the target is ANY, it is indexed by prioritized_work
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
#include "workqueue.h"


// minimum percentage imbalance to trigger steal if stealers queue not empty
#define XLB_STEAL_IMBALANCE 0.1


/** Uniquify work units on this server */
static xlb_work_unit_id unique = 1;

/**
   Map from target rank to type array of priority heap -
   heap sorted by negative priority.
   Only contains targeted work
*/
static struct table_ip targeted_work;

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
int64_t workqueue_parallel_task_count;

void
workqueue_init(int work_types)
{
  assert(work_types >= 1);
  DEBUG("workqueue_init(work_types=%i)", work_types);
  bool b = table_ip_init(&targeted_work, 128);
  valgrind_assert(b);
  typed_work = malloc(sizeof(struct rbtree) * (size_t)work_types);
  valgrind_assert(typed_work != NULL);
  parallel_work = malloc(sizeof(struct rbtree) * (size_t)work_types);
  workqueue_parallel_task_count = 0;
  valgrind_assert(parallel_work != NULL);
  for (int i = 0; i < work_types; i++)
  {
    rbtree_init(&typed_work[i]);
    rbtree_init(&parallel_work[i]);
  }
}

xlb_work_unit_id
workqueue_unique()
{
  return unique++;
}

void
workqueue_add(int type, int putter, int priority, int answer,
              int target_rank, int length, int parallelism,
              xlb_work_unit* wu)
{
  wu->id = workqueue_unique();
  wu->type = type;
  wu->putter = putter;
  wu->priority = priority;
  wu->answer = answer;
  wu->target = target_rank;
  wu->length = length;
  wu->parallelism = parallelism;

  DEBUG("workqueue_add(): %lli: x%i %s",
        wu->id, wu->parallelism, (char*) wu->payload);

  if (target_rank < 0 && parallelism == 1)
  {
    // Untargeted single-process task
    TRACE("workqueue_add(): single-process");
    struct rbtree* T = &typed_work[type];
    rbtree_add(T, -priority, wu);
  }
  else if (parallelism > 1)
  {
    // Untargeted parallel task
    TRACE("workqueue_add(): parallel task: %p", wu);
    struct rbtree* T = &parallel_work[type];
    TRACE("rbtree_add: wu: %p key: %i\n", wu, -priority);
    rbtree_add(T, -priority, wu);
    workqueue_parallel_task_count++;
  }
  else
  {
    // Targeted task
    heap* A = table_ip_search(&targeted_work, target_rank);
    if (A == NULL)
    {
      A = malloc((size_t)xlb_types_size * sizeof(heap));
      table_ip_add(&targeted_work, target_rank, A);
      for (int i = 0; i < xlb_types_size; i++)
      {
        heap* H = &A[i];
        heap_init(H, 8);
      }
    }
    heap* H = &A[type];
    heap_add(H, -priority, wu);
  }
}

/**
   If we have no more targeted work for this rank, clean up data
   structures
 */
static inline xlb_work_unit*
pop_targeted(heap* H, int target)
{
  xlb_work_unit* result = heap_root_val(H);
  DEBUG("workqueue_get(): targeted: %lli", result->id);
  heap_del_root(H);
  return result;
}

xlb_work_unit*
workqueue_get(int target, int type)
{
  DEBUG("workqueue_get(target=%i, type=%i)", target, type);

  xlb_work_unit* wu = NULL;

  heap* A = table_ip_search(&targeted_work, target);
  if (A != NULL)
  {
    // Targeted work was found
    heap* H = &A[type];
    if (heap_size(H) != 0)
    {
      wu = pop_targeted(H, target);
      return wu;
    }
  }

  // Select untargeted work
  struct rbtree* T = &typed_work[type];
  struct rbtree_node* node = rbtree_leftmost(T);
  if (node == NULL)
    // We found nothing
    return NULL;
  rbtree_remove_node(T, node);
  wu = node->data;
  DEBUG("workqueue_get(): untargeted: %lli", wu->id);
  free(node);
  return wu;
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
workqueue_pop_parallel(xlb_work_unit** wu, int** ranks, int work_type)
{
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
      workqueue_parallel_task_count--;
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

  TRACE("pop_parallel_cb(): wu: %p %lli x%i",
        wu, wu->id, wu->parallelism);
  assert(wu->parallelism > 0);

  int ranks[wu->parallelism];
  bool found =
      requestqueue_parallel_workers(data->type, wu->parallelism,
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

static inline int
rand_choose(float *weights, int length) {
  if (length == 1) {
    return 0;
  } else {
    return random_draw(weights, length);
  }
}

static inline adlb_code
workqueue_steal_type(struct rbtree *q, int num,
                      workqueue_steal_callback cb)
{
  assert(q->size >= num);
  for (int i = 0; i < num; i++)
  {
    struct rbtree_node* node = rbtree_random(q);
    assert(node != NULL);
    rbtree_remove_node(q, node);
    adlb_code code = cb.f(cb.data, (xlb_work_unit*) node->data);
    free(node);
    ADLB_CHECK(code);
  }
  return ADLB_SUCCESS;
}


adlb_code
workqueue_steal(int max_memory, const int *steal_type_counts,
                          workqueue_steal_callback cb)
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
        TRACE("workqueue_steal(): stealing type=%i single=%i/%i par=%i/%i"
              " This server count: %i versus %i",
                        t, single_to_send, single_count, par_to_send, par_count,
                        tot_count, stealer_count);
        adlb_code code;
        code = workqueue_steal_type(&(typed_work[t]), single_to_send, cb);
        ADLB_CHECK(code);
        code = workqueue_steal_type(&(parallel_work[t]), par_to_send, cb);
        workqueue_parallel_task_count -= par_to_send;
        ADLB_CHECK(code);
      }
    }
  }
  return ADLB_SUCCESS;
}

void workqueue_type_counts(int *types, int size)
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
work_unit_free(xlb_work_unit* wu)
{
  free(wu);
}

void
workqueue_finalize()
{
  TRACE_START;
  for (int i = 0; i < targeted_work.capacity; i++)
  {
    for (struct list_ip_item* item = targeted_work.array->head;
        item; item = item->next)
    {
      heap* A = item->data;
      for (int i = 0; i < xlb_types_size; i++)
      {
        heap* H = &A[i];
        if (H->size > 0)
          printf("WARNING: server contains targeted work!\n");
      }
    }
  }
  for (int i = 0; i < xlb_types_size; i++)
  {
    int count = (&typed_work[i])->size;
    if (count > 0)
      printf("WARNING: server contains %i work units of type: %i\n",
             count, i);
  }
  for (int i = 0; i < xlb_types_size; i++)
  {
    if (parallel_work[i].size > 0)
      printf("WARNING: server contains %i "
             "parallel work units of type %i:\n",
             parallel_work[i].size, i);
  }

  TRACE_END;
}
