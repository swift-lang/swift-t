
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

void
workqueue_init(int work_types)
{
  DEBUG("workqueue_init(work_types=%i)", work_types);
  bool b = table_ip_init(&targeted_work, 128);
  valgrind_assert(b);
  typed_work = malloc(sizeof(struct rbtree) * work_types);
  valgrind_assert(typed_work != NULL);
  parallel_work = malloc(sizeof(struct rbtree) * work_types);
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
              void* payload)
{
  xlb_work_unit* wu = malloc(sizeof(xlb_work_unit));
  wu->id = workqueue_unique();
  wu->type = type;
  wu->putter = putter;
  wu->priority = priority;
  wu->answer = answer;
  wu->target = target_rank;
  wu->length = length;
  wu->parallelism = parallelism;
  wu->payload = malloc(length);
  memcpy(wu->payload, payload, length);

  DEBUG("workqueue_add(): %li: x%i %s",
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
    TRACE("workqueue_add(): parallel task");
    struct rbtree* T = &parallel_work[type];
    rbtree_add(T, -priority, wu);
  }
  else
  {
    // Targeted task
    heap* A = table_ip_search(&targeted_work, target_rank);
    if (A == NULL)
    {
      A = malloc(xlb_types_size * sizeof(heap));
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
  DEBUG("workqueue_get(): targeted: %li", result->id);
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
  DEBUG("workqueue_get(): untargeted: %li", wu->id);
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
workqueue_pop_parallel(xlb_work_unit** wu, int** ranks)
{
  TRACE_START;
  bool result = false;
  struct pop_parallel_data data = { -1, NULL, NULL, NULL };
  for (int type = 0; type < xlb_types_size; type++)
  {
    data.type = type;
    struct rbtree* T = &parallel_work[type];
    TRACE("type: %i size: %i", type, rbtree_size(T));
    bool found = rbtree_iterator(T, pop_parallel_cb, &data);
    if (found)
    {
      *wu = data.wu;
      *ranks = data.ranks;
      result = true;
      // Release memory:
      rbtree_remove_node(T, data.node);
      free(data.node);
      goto end;
    }
  }
  end:
  TRACE_END;
  return result;
}

static bool
pop_parallel_cb(struct rbtree_node* node, void* user_data)
{
  xlb_work_unit* wu = node->data;
  struct pop_parallel_data* data = user_data;

  TRACE("pop_parallel_cb(): wu: %li x%i", wu->id, wu->parallelism);

  int ranks[wu->parallelism];
  bool found =
      requestqueue_parallel_workers(data->type, wu->parallelism,
                                    ranks);
  if (found)
  {
    data->wu = wu;
    data->node = node;
    data->ranks = malloc(wu->parallelism * sizeof(int));
    valgrind_assert(data->ranks != NULL);
    memcpy(data->ranks, ranks, wu->parallelism * sizeof(int));
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

/**
   If count is not 0, caller must free results
 */
adlb_code
workqueue_steal(int max_memory, int nsteal_types, const int *steal_types,
                int* count, xlb_work_unit*** result)
{
  assert(nsteal_types >= 0 && nsteal_types <= xlb_types_size);
  // fractions: probability weighting for each work type
  float fractions_parallel[nsteal_types];
  float fractions_single[nsteal_types];
  int total_parallel = 0;
  int total_single = 0;
  for (int i = 0; i < nsteal_types; i++)
  {
    int t = steal_types[i];
    assert(t >= 0 && t < xlb_types_size);
    total_parallel += (&parallel_work[t])->size;
    total_single   += (&typed_work[t])->size;
  }

  DEBUG("workqueue_steal(): total=%i", total_single);

  if (total_single + total_parallel == 0)
  {
    *count = 0;
    return ADLB_SUCCESS;
  }

  if (total_parallel > 0) {
    for (int i = 0; i < nsteal_types; i++) {
      int t = steal_types[i];
      fractions_parallel[t] =
          (&parallel_work[t])->size / (float) total_parallel;
    }
  }
  if (total_single > 0) {
    for (int i = 0; i < nsteal_types; i++) {
      int t = steal_types[i];
      fractions_single[t] =
          (&typed_work[t])->size / (float) total_single;
    }
  }
  // Number of work units we are willing to share (half of all work)
  int share = (total_single + total_parallel) / 2 + 1;
  // Number of work units we actually share
  int actual = 0;

  // First, steal parallel task work units
  xlb_work_unit** stolen = malloc(share * sizeof(xlb_work_unit*));
  if (total_parallel > 0)
    for (int i = 0; i < share; i++)
    {
      assert(nsteal_types > 0);
      int type_ix = rand_choose(fractions_parallel, nsteal_types);
      assert(type_ix >= 0 && type_ix < nsteal_types);
      int type = steal_types[type_ix];
      assert(type >= 0 && type < xlb_types_size);
      struct rbtree* T = &parallel_work[type];
      struct rbtree_node* node = rbtree_random(T);
      if (node == NULL)
        continue;
      rbtree_remove_node(T, node);
      stolen[actual] = (xlb_work_unit*) node->data;
      actual++;
      free(node);
    }
  // Second, steal single-process work units
  if (total_single > 0)
    for (int i = actual; i < share; i++)
    {
      assert(nsteal_types > 0);
      int type_ix = rand_choose(fractions_single, nsteal_types);
      assert(type_ix >= 0 && type_ix < nsteal_types);
      int type = steal_types[type_ix];
      assert(type >= 0 && type < xlb_types_size);
      struct rbtree* T = &typed_work[type];
      struct rbtree_node* node = rbtree_random(T);
      if (node == NULL)
        continue;
      rbtree_remove_node(T, node);
      stolen[actual] = (xlb_work_unit*) node->data;
      actual++;
      free(node);
    }

  *count = actual;
  *result = stolen;
  return ADLB_SUCCESS;
}

void
work_unit_free(xlb_work_unit* wu)
{
  free(wu->payload);
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
  TRACE_END;
}
