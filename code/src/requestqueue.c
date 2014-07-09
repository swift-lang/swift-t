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
 * requestqueue.c
 *
 *  Created on: Jun 28, 2012
 *      Author: wozniak
 */

#include <assert.h>
#include <mpi.h>

#include <list2.h>
#include <tools.h>

#include "adlb-defs.h"
#include "checks.h"
#include "common.h"
#include "debug.h"
#include "messaging.h"
#include "requestqueue.h"
#include "server.h"

typedef struct
{
  /** Item in type_requests */
  struct list2_item* item;
  int rank; // Rank request was from
  int type; // ADLB Work Type
  int count; // Number of work units
  /** Whether the worker is blocking on the first request */
  bool blocking;
} request;


/** Total number of requests in queue */
static int request_queue_size;

/** Total number of workers blocked on requests */
static int nblocked;

/** Type-indexed array of list of request object */
static struct list2* type_requests;

/** Cache list nodes to avoid malloc/free calls on critical path */
static struct {
  struct list2_item **free_array;
  int free_array_size; // Size of free array
  int nfree; // Number of items in free array
} list2_node_pool;

/** Table of ranks requesting work so we can match targeted work to them. 
    We only store ranks belonging to this server, since targeted work for
    other ranks won't arrive here.  We store pointers to the respective
    list entries in type_requests, NULL if there is no request.
 */
static request* targets;

/** Helper functions for manipulating data structures */
static int pop_rank_from_types(struct list2 *type_list);
static void request_match_update(request *R, bool in_targets);
static inline void invalidate_request(request *R);
static inline bool in_targets_array(request *R);

/** Node pool functions */
static inline struct list2_item *alloc_list2_node(void);
static inline void free_list2_node(struct list2_item *node);
static inline void free_list2_node_pool(void);

adlb_code
xlb_requestqueue_init(int my_workers)
{
  assert(my_workers >= 0);
  targets = malloc(sizeof(targets[0]) * (size_t)my_workers);
  ADLB_MALLOC_CHECK(targets);

  for (int i = 0; i < my_workers; i++)
  {
    targets[i].item = NULL;
  }

  type_requests = malloc(sizeof(struct list2) * (size_t)xlb_types_size);
  ADLB_MALLOC_CHECK(type_requests);
  for (int i = 0; i < xlb_types_size; i++)
    list2_init(&type_requests[i]);

  // Assume one request per node - otherwise fallback to malloc/free
  // Allocate ahead of time to get non-fragmented memory
  list2_node_pool.free_array =
          malloc(sizeof(list2_node_pool.free_array[0]) * (size_t)my_workers);
  list2_node_pool.free_array_size = my_workers;
  list2_node_pool.nfree = my_workers;
  for (int i = 0; i < list2_node_pool.free_array_size; i++)
  {
    struct list2_item *item = malloc(sizeof(struct list2_item));
    ADLB_MALLOC_CHECK(item);
    list2_node_pool.free_array[i] = item;
  }

  request_queue_size = 0;
  nblocked = 0;
  return ADLB_SUCCESS;
}

adlb_code
xlb_requestqueue_add(int rank, int type, int count, bool blocking)
{
  DEBUG("requestqueue_add(rank=%i,type=%i,count=%i,blocking=%s)", rank,
         type, count, blocking ? "true" : "false");
  assert(count >= 1);
  request* R;

  // Store in targets if it is one of our workers
  if (xlb_map_to_server(rank) == xlb_comm_rank)
  {
    int targets_ix = xlb_my_worker_ix(rank);
    // Assert rank was not already entered
    // TODO: will need to change this to support aget
    //       -> linked list?
    // TODO: compress entries if multiple matching
    valgrind_assert_msg(targets[targets_ix].item == NULL,
          "requestqueue: double add: rank: %i", rank);
     R = &targets[targets_ix];
  }
  else
  {
    // Otherwise store on heap 
    R = malloc(sizeof(*R));
    ADLB_MALLOC_CHECK(R);
  }

  struct list2* L = &type_requests[type];

  struct list2_item* item = alloc_list2_node();
  ADLB_MALLOC_CHECK(item);

  R->rank = rank;
  R->type = type;
  R->count = count;
  R->blocking = blocking;
  R->item = item;
  item->data = R;

  list2_add_item(L, item);
  request_queue_size++;

  if (blocking)
  {
    nblocked++;
  }
  return ADLB_SUCCESS;
}

/*
  Internal helper.
  Update data structures for a single match to the request object.
  in_targets: if true, is target array entry
 */
static void request_match_update(request *R, bool in_targets)
{
  if (R->blocking)
  {
    nblocked--;
  }

  assert(R->count >= 1);
  if (R->count == 1)
  {
    struct list2* L = &type_requests[R->type];
    struct list2_item *item = R->item;
    list2_remove_item(L, item);
    free_list2_node(item);
    request_queue_size--;
    assert(request_queue_size >= 0);

    invalidate_request(R);
    if (!in_targets)
    {
      free(R);
    }
  }
  else
  {
    R->count--;
    R->blocking = false;
  }
  assert(nblocked >= 0);
}

/* Mark request as empty */
static inline void invalidate_request(request *R)
{
  R->item = NULL; // Clear item if needed for targets array
}

/*
  Return true if request is stored directly in targets array
 */
static inline bool in_targets_array(request *R)
{
  int targets_ix = xlb_my_worker_ix(R->rank);
  return R == &targets[targets_ix];
}

/*
  Internal helper.
  Pop a rank from the type list.  Return ADLB_RANK_NULL if not present
 */
static int pop_rank_from_types(struct list2 *type_list)
{
  struct list2_item *item = type_list->head;
  if (item == NULL)
  {
    return ADLB_RANK_NULL;
  }
  request* R = (request*)item->data;
  int rank = R->rank;
  request_match_update(R, false);
  return rank;
}

/**
   If target_rank is in the request_queue and requests work of
   given type, pop and return that rank.  Else return ADLB_RANK_NULL.
 */
int
xlb_requestqueue_matches_target(int target_rank, int type)
{
  DEBUG("requestqueue_matches_target(rank=%i, type=%i)",
        target_rank, type);

  int targets_ix = xlb_my_worker_ix(target_rank);
  request* R = &targets[targets_ix];
  if (R->item != NULL && R->type == type && R->rank == target_rank)
  {
    request_match_update(R, true);
    return target_rank;
  }
  return ADLB_RANK_NULL;
}

int
xlb_requestqueue_matches_type(int type)
{
  DEBUG("requestqueue_matches_type(%i)...", type);
  struct list2* L = &type_requests[type];
  return pop_rank_from_types(L);
}

bool
xlb_requestqueue_parallel_workers(int type, int parallelism, int* ranks)
{
  bool result = false;
  struct list2* L = &type_requests[type];
  int count = list2_size(L);

  TRACE("xlb_requestqueue_parallel_workers(type=%i x%i) count=%i ...",
        type, parallelism, count);

  if (count >= parallelism)
  {
    TRACE("\t found: count: %i needed: %i", count, parallelism);
    result = true;
    for (int i = 0; i < parallelism; i++)
    {
      ranks[i] = pop_rank_from_types(L);
      assert(ranks[i] != ADLB_RANK_NULL);
    }
  }
  TRACE_END;
  return result;
}

void
xlb_requestqueue_remove(xlb_request_entry *e)
{
  TRACE("pequestqueue_remove(%i)", e->rank);
  // Recover request from stored pointer
  request *r = (request*)e->_internal;
  assert(r != NULL);
  e->_internal = NULL; // Invalidate in case of reuse

  request_match_update(r, in_targets_array(r));
}

int
xlb_requestqueue_size()
{
  return request_queue_size;
}

int
xlb_requestqueue_nblocked(void)
{
  return nblocked;
}

adlb_code xlb_requestqueue_incr_blocked(void)
{
  nblocked++;
  assert(nblocked <= xlb_my_workers);
  return ADLB_SUCCESS;
}

adlb_code xlb_requestqueue_decr_blocked(void)
{
  nblocked--;
  assert(nblocked >= 0);
  return ADLB_SUCCESS;
}

void xlb_requestqueue_type_counts(int* types, int size) {
  assert(size >= xlb_types_size);
  int total = 0;
  for (int t = 0; t < xlb_types_size; t++) {
    struct list2* L = &type_requests[t];
    types[t] = L->size;
    total += L->size;
  }

  // Internal consistency check
  assert(total == request_queue_size);
}

int
xlb_requestqueue_get(xlb_request_entry* r, int max)
{
  int ix = 0;
  for (int t = 0; t < xlb_types_size; t++)
  {
    struct list2* L = &type_requests[t];
    assert(L != NULL);
    for (struct list2_item* item = L->head; item; item = item->next)
    {
      request* rq = (request*) item->data;
      r[ix].rank = rq->rank;
      r[ix].type = rq->type;
      r[ix]._internal = rq; // Store for later reference
      ix++;
      if (ix == max)
        return max;
    }
  }
  return ix;
}

// void requestqueue_send_work(int worker);

static adlb_code shutdown_rank(int rank);

/**
   The server is shutting down
   Notify all workers in the request queue
 */
void
xlb_requestqueue_shutdown()
{
  TRACE_START;
  for (int i = 0; i < xlb_types_size; i++)
  {
    while (true)
    {
      int rank = pop_rank_from_types(&type_requests[i]);
      if (rank == ADLB_RANK_NULL)
        break;
      adlb_code rc = shutdown_rank(rank);
      valgrind_assert_msg(rc == ADLB_SUCCESS, "requestqueue: "
                          "worker did not shutdown: ", rank);
    }
  }
  // Free now-empty structures
  free(type_requests);
  free(targets);

  free_list2_node_pool();
}

static adlb_code
shutdown_rank(int rank)
{
  DEBUG("shutdown_rank(%i)", rank);
  struct packed_get_response g;
  g.code = ADLB_SHUTDOWN;
  // The rest of the fields are not used:
  g.answer_rank = ADLB_RANK_NULL;
  g.length = -1;
  g.type = ADLB_TYPE_NULL;
  g.payload_source = ADLB_RANK_NULL;
  g.parallelism = 0;
  SEND(&g, sizeof(g), MPI_BYTE, rank, ADLB_TAG_RESPONSE_GET);
  return ADLB_SUCCESS;
}

static inline struct list2_item *alloc_list2_node(void)
{
  if (list2_node_pool.nfree > 0)
  {
    list2_node_pool.nfree--;
    return list2_node_pool.free_array[list2_node_pool.nfree];
  }
  else
  {
    return malloc(sizeof(struct list2_item));
  }
}

static inline void free_list2_node(struct list2_item *node)
{
  if (list2_node_pool.nfree == list2_node_pool.free_array_size)
  {
    free(node);
  }
  else
  {
    list2_node_pool.free_array[list2_node_pool.nfree++] = node;
  }
}

static inline void free_list2_node_pool(void)
{
  for (int i = 0; i < list2_node_pool.nfree; i++)
  {
    free(list2_node_pool.free_array[i]);
  }

  free(list2_node_pool.free_array);
  list2_node_pool.free_array = NULL;
  list2_node_pool.free_array_size = 0;
  list2_node_pool.nfree = 0;
}

