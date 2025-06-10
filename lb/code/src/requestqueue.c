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

typedef struct
{
  /** Item in type_requests.  NULL indicates invalid request. */
  struct list2_item* item;

  /** Rank request was from */
  int rank;

  /** ADLB work type requested */
  int type;

  /** Number of work units requested */
  int count;

  /** Whether the worker is blocking on the first request */
  bool blocking;
} request;

/** Total number of entries in queue.
    Entries with count > 1 only contribute 1 to total. */
static int request_queue_size;

/** Total number of workers blocked on requests */
static int nblocked;

/** Array of lists of request object.  Index is work type. */
static struct list2* type_requests;

/** Cache list nodes to avoid malloc/free calls on critical path */
static struct
{
  struct list2_item** free_array;
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
static inline adlb_code
merge_request(request *R, int rank, int type, int count, bool blocking);
static int pop_rank_from_types(struct list2 *type_list);
static void request_match_update(request *R, bool in_targets, int count);
static inline void invalidate_request(request *R);
static bool in_targets_array(request *R);

/** Node pool functions */
static inline adlb_code list2_node_pool_init(int size);
static inline void list2_node_pool_finalize(void);
static inline struct list2_item *alloc_list2_node(void);
static inline void free_list2_node(struct list2_item *node);

adlb_code
xlb_requestqueue_init(int ntypes, const xlb_layout *layout)
{
  assert(layout->my_workers >= 0);
  assert(ntypes >= 1);

  // INFO("requestqueue_init");

  targets = malloc(sizeof(targets[0]) * (size_t)layout->my_workers);
  ADLB_CHECK_MALLOC(targets);

  for (int i = 0; i < layout->my_workers; i++)
    targets[i].item = NULL;

  type_requests = malloc(sizeof(struct list2) * (size_t) ntypes);
  ADLB_CHECK_MALLOC(type_requests);
  for (int i = 0; i < ntypes; i++)
    list2_init(&type_requests[i]);

  // Allocate one list node per worker -
  //          otherwise will fallback to malloc/free
  adlb_code ac = list2_node_pool_init(layout->my_workers);
  ADLB_CHECK(ac);

  request_queue_size = 0;
  nblocked = 0;
  return ADLB_SUCCESS;
}

adlb_code
xlb_requestqueue_add(int rank, int type, int count, bool blocking)
{
  /* if (rank % 100 == 0) */
  /*   INFO("requestqueue_add(server=%i, rank=%i,type=%i,count=%i,blocking=%s)\n", */
  /*          xlb_s.layout.rank, rank, type, count, blocking ? "true" : "false"); */
  assert(count >= 1);
  request* R;

  // Whether we need to merge requests
  // Store in targets if it is one of our workers
  if (xlb_worker_maps_to_server(&xlb_s.layout, rank, xlb_s.layout.rank))
  {
    int targets_ix = xlb_worker_idx(&xlb_s.layout, rank);
    R = &targets[targets_ix];
    if (R->item != NULL) {
      /*
         Assuming that types match avoids complications with responding to
         requests out of order, and with more complicated data structures.
         We leave it to the client code to avoid doing this for now.
      */
      ADLB_CHECK_MSG(R->type == type,
                     "Do not yet support simultaneous requests"
                     " for different work types from same rank."
                     " Rank: %i Types: %i, %i", rank, R->type, type);
      return merge_request(R, rank, type, count, blocking);
    }
  }
  else
  {
    // Otherwise store on heap
    // When would this happen? - Justin 2020-05-13
    R = malloc(sizeof(*R));
    ADLB_CHECK_MALLOC(R);
  }

  struct list2* L = &type_requests[type];
  struct list2_item* item = alloc_list2_node();
  ADLB_CHECK_MALLOC(item);

  R->rank = rank;
  R->type = type;
  R->count = count;
  R->blocking = blocking;
  R->item = item;
  item->data = R;

  list2_add_item(L, item);
  request_queue_size++;

  DEBUG("request_queue_add(): size=%i", request_queue_size);

  if (blocking)
  {
    nblocked++;
  }

  return ADLB_SUCCESS;
}

/*
 * Merge new request into existing request.
 * Caller is responsible for ensuring types and ranks match.
 */
static inline adlb_code
merge_request(request* R, int rank, int type, int count, bool blocking)
{
  valgrind_assert_msg(R->rank == rank,
		      "attempt to merge request rank %i "
		      "into slot rank %i",
		      R->rank, rank);
  valgrind_assert(R->type == type);
  valgrind_assert(R->item != NULL);

  R->count += count;
  if (blocking)
  {
    assert(!R->blocking); // Shouldn't already be blocked
    R->blocking = true;
    nblocked++;
  }

  return ADLB_SUCCESS;
}

/*
  Internal helper.
  Update data structures for a single match to the request object.
  in_targets: if true, is target array entry
  count: number to remove, must be >= 1
 */
static void
request_match_update(request* R, bool in_targets, int count)
{
  assert(count >= 1);

  if (R->blocking)
  {
    nblocked--;
  }

  assert(R->count >= count);
  if (R->count == count)
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
    R->count -= count;
    R->blocking = false;
  }
  assert(nblocked >= 0);
}

/* Mark request as empty */
static inline void invalidate_request(request* R)
{
  R->item = NULL; // Clear item if needed for targets array
}

/*
  Return true if request is stored directly in targets array
  GCC says not to inline this. -Justin 2015/01/06
 */
static bool in_targets_array(request *R)
{
  int target_server = xlb_map_to_server(&xlb_s.layout, R->rank);
  if (target_server != xlb_s.layout.rank) {
    return false;
  }
  int targets_ix = xlb_worker_idx(&xlb_s.layout, R->rank);
  return (R == &targets[targets_ix]);
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
  request_match_update(R, in_targets_array(R), 1);
  return rank;
}

static inline int requestq_matches_tgt_node(int task_tgt_idx, int task_type);

/**
   If target_rank is in the request_queue and requests work of
   given type, pop and return that rank.  Else return ADLB_RANK_NULL.

   Assumes that the rank is one of our workers.
 */
int
xlb_requestqueue_matches_target(int task_target_rank, int task_type,
                                adlb_target_accuracy accuracy)
{
  DEBUG("requestqueue_matches_target(rank=%i, type=%i)",
        task_target_rank, task_type);

  int task_tgt_idx = xlb_worker_idx(&xlb_s.layout, task_target_rank);
  request* R = &targets[task_tgt_idx];
  if (R->item != NULL && R->type == task_type)
  {
    assert(R->rank == task_target_rank);
    request_match_update(R, true, 1);
    return task_target_rank;
  }

  if (accuracy == ADLB_TGT_ACCRY_NODE)
    return requestq_matches_tgt_node(task_tgt_idx, task_type);

  return ADLB_RANK_NULL;
}

static inline int
requestq_matches_tgt_node(int task_tgt_idx, int task_type)
{
  TRACE_START;
  DEBUG("requestq_matches_tgt_node(task_tgt_idx=%i, task_type=%i)",
        task_tgt_idx, task_type);
  int result = ADLB_RANK_NULL;
  int task_host_idx = xlb_s.layout.my_worker2host[task_tgt_idx];
  struct dyn_array_i *host_workers;
  host_workers = &xlb_s.layout.my_host2workers[task_host_idx];

  for (int i = 0; i < host_workers->size; i++)
  {
    int worker_idx = host_workers->arr[i];
    request* R = &targets[worker_idx];
    if (R->item != NULL && R->type == task_type)
    {
      request_match_update(R, true, 1);
      result = xlb_rank_from_worker_idx(&xlb_s.layout, worker_idx);
      break;
    }
  }
  TRACE_END;
  return result;
}

int
xlb_requestqueue_matches_type(int type)
{
  DEBUG("requestqueue_matches_type(%i)...", type);
  struct list2* L = &type_requests[type];
  return pop_rank_from_types(L);
}

static inline bool get_parallel_workers_unordered(int count,
                                                  int parallelism,
                                                  struct list2* L,
                                                  int* ranks);

static inline bool get_parallel_workers_ordered(int count,
                                                int parallelism,
                                                struct list2* L,
                                                int* ranks);

bool
xlb_requestqueue_parallel_workers(int type, int parallelism, int* ranks)
{
  bool result = false;
  struct list2* L = &type_requests[type];
  int count = list2_size(L);

  /* INFO("xlb_requestqueue_parallel_workers(type=%i x%i) count=%i ...", */
  /*       type, parallelism, count); */

  if (count < parallelism)
    return false;

  if (xlb_s.par_mod == 1)
    result = get_parallel_workers_unordered(count, parallelism, L, ranks);
  else
    result = get_parallel_workers_ordered(count, parallelism, L, ranks);
  TRACE_END;
  return result;
}

static inline bool
get_parallel_workers_unordered(int count, int parallelism,
                               struct list2* L, int* ranks)
{
  TRACE("\t found: count: %i needed: %i", count, parallelism);
  for (int i = 0; i < parallelism; i++)
  {
    ranks[i] = pop_rank_from_types(L);
    assert(ranks[i] != ADLB_RANK_NULL);
  }
  return true;
}

static inline void extract_worker_ranks(struct list2* L, int* result);

static inline bool find_contig(int* A, int n, int k, int m, int* result);

#if 0
// UNUSED: 2025-06-10
static inline request* find_request(struct list2* L, int rank);
#endif

static void update_parallel_requests(struct list2* L, int* ranks, int count);

/**
   ranks: output array of ranks
   returns true if ranks were found, else false
 */
static inline bool
get_parallel_workers_ordered(int count, int parallelism,
                             struct list2* L, int* ranks)
{
  double t0 = MPI_Wtime();
  if (count < parallelism)
      return false;

  /* INFO("get_parallel_workers_ordered(server=%i count=%i parallelism=%i) ...", */
  /*       xlb_s.layout.rank, count, parallelism); */

  // Flat array representation of available ranks:
  int flat[count];
  extract_worker_ranks(L, flat);
  // Timing data: only used by logging
  unused double t1, t2, t3, t4, duration;
  t1 = MPI_Wtime();
  // INFO("qsort: %i", count);
  quicksort_ints(flat, 0, count-1);
  t2 = MPI_Wtime();

  // print_ints(t, count);

  int p; // Index of start rank in flat array
  bool result = find_contig(flat, count, parallelism, xlb_s.par_mod, &p);
  t3 = MPI_Wtime();

  if (!result)
  {
    /* INFO("get_parallel_workers_ordered(): " */
    /*       "could not satisfy ADLB_PAR_MOD=%i", xlb_s.par_mod); */
    return false;
  }

  /* for (int i = 0; i < parallelism; i++) */
  /* { */
  /*   ranks[i] = flat[p]+i; */
  /*   request* R = find_request(L, ranks[i]); */
  /*   request_match_update(R, in_targets_array(R), 1); */
  /* } */

  for (int i = 0; i < parallelism; i++)
    ranks[i] = flat[p]+i;
  update_parallel_requests(L, ranks, parallelism);

  t4 = MPI_Wtime();

  duration = t4 - t0;

  INFO("get_parallel_workers_ordered(server=%i count=%i parallelism=%i) OK "
       "%.4f extract=%.4f sort=%.4f %.4f %.4f",
        xlb_s.layout.rank, count, parallelism,
       duration, t1-t0, t2-t1, t3-t2, t4-t3);


  // print_ints(ranks, parallelism);

  return true;
}

/** Extract all worker ranks in the requestqueue */
static inline void
extract_worker_ranks(struct list2* L, int* result)
{
  int i = 0;
  for (struct list2_item *item = L->head; item != NULL;
       item = item->next)
  {
    request* R = (request*)item->data;
    result[i++] = R->rank;
  }
}

/**
   Find k contiguous entries in array A(n), starting at A[p],
   where A[p] % m == 0.
 */
static inline bool
find_contig(int* A, int n, int k, int m, int* result)
{
  int n_k = n-k; // Useful place to give up
  int p = 0;
  // INFO("find_contig(): n=%i k=%i m=%i", n, k, m);
  do
  {
    // printf("p trial1: %i\n", p);
    // Advance to next allowable PAR_MOD start point
    while (A[p] % m != 0)
    {
      // printf(" A[p]: %i\n", A[p]);
      if (++p > n_k) return false;
    }
    // printf("p trial2: %i\n", p);

    int start = A[p];
    // printf("start: %i\n", start);
    int next = start+1;
    int q = p+1;
    int z = q+k-1;
    if (z > n) break;
    for ( ; q < z; q++)
    {
      // printf("A[%i]=%i\n", q, A[q]);
      // Proceed while we have sequential ranks
      if (A[q] != next++) goto loop;
    }
    // Found it!
    *result = p;
    // INFO("find_contig(): found: p=%i", p);
    return true;

    loop: p = q; // Not sequential: try again
    // printf("loop: p=%i n_k=%i\n", p, n_k);
  } while (p < n_k);
  // INFO("find_contig(): nothing.");
  return false;
}

#if 0
// UNUSED: 2025-06-10
static inline request*
find_request(struct list2* L, int rank)
{
  // printf("find_request: %i\n", rank);
  request* result = NULL;
  for (struct list2_item *item = L->head; item != NULL;
       item = item->next)
  {
    request* R = (request*) item->data;
    if (R->rank == rank)
    {
      result = R;
      break;
    }
  }
  assert(result != NULL);
  return result;
}
#endif

static void
update_parallel_requests(struct list2* L, int* ranks, int count)
{
  int copy[count];
  memcpy(copy, ranks, count*sizeof(int));
  for (struct list2_item *item = L->head; item != NULL;
       item = item->next)
  {
    request* R = (request*) item->data;
    for (int i = 0; i < count; i++)
    {
      if (R->rank == copy[i])
      {
        request_match_update(R, in_targets_array(R), 1);
        // Move last element to present element, shrink array
        copy[i] = copy[count-1];
        count--;
      }
    }
  }
}
void
xlb_requestqueue_remove(xlb_request_entry* e, int count)
{
  TRACE("requestqueue_remove(%i)", e->rank);
  // Recover request from stored pointer
  request* r = (request*) e->_internal;
  assert(r != NULL);
  if (count >= r->count)
  {
    e->_internal = NULL; // Invalidate in case of reuse
  }

  bool in_targets = in_targets_array(r);
  request_match_update(r, in_targets, count);
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
  assert(nblocked <= xlb_s.layout.my_workers);
  return ADLB_SUCCESS;
}

adlb_code xlb_requestqueue_decr_blocked(void)
{
  nblocked--;
  assert(nblocked >= 0);
  return ADLB_SUCCESS;
}

void xlb_requestqueue_type_counts(int* types, int size)
{
  assert(size >= xlb_s.types_size);
  int total = 0;
  for (int t = 0; t < xlb_s.types_size; t++)
  {
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
  for (int t = 0; t < xlb_s.types_size; t++)
  {
    struct list2* L = &type_requests[t];
    assert(L != NULL);
    for (struct list2_item* item = L->head; item; item = item->next)
    {
      request* rq = (request*) item->data;
      r[ix].rank = rq->rank;
      r[ix].type = rq->type;
      r[ix].count = rq->count;
      r[ix]._internal = rq; // Store for later reference
      ix++;
      if (ix == max) break;
    }
    if (ix == max) break;
  }
  TRACE("xlb_requestqueue_get() => %i", ix);
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
  for (int i = 0; i < xlb_s.types_size; i++)
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

  list2_node_pool_finalize();
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

/*
  Initialize fixed-size array of list nodes.
  Allocate ahead of time to get non-fragmented memory.
 */
static inline adlb_code list2_node_pool_init(int size)
{
  assert(size >= 0);

  if (size == 0)
  {
    list2_node_pool.free_array = NULL;
  }
  else
  {
    list2_node_pool.free_array =
            malloc(sizeof(list2_node_pool.free_array[0]) * (size_t)size);
  }

  list2_node_pool.free_array_size = size;
  list2_node_pool.nfree = size;

  for (int i = 0; i < size; i++)
  {
    struct list2_item* item = malloc(sizeof(struct list2_item));
    ADLB_CHECK_MALLOC(item);
    list2_node_pool.free_array[i] = item;
  }

  return ADLB_SUCCESS;
}

static inline void list2_node_pool_finalize(void)
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

static inline struct list2_item* alloc_list2_node(void)
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

static inline void free_list2_node(struct list2_item* node)
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
