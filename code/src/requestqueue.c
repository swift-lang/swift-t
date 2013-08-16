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
#include <table_ip.h>
#include <tools.h>

#include "adlb-defs.h"
#include "checks.h"
#include "common.h"
#include "debug.h"
#include "messaging.h"
#include "requestqueue.h"

typedef struct
{
  int rank;
  int type;
  /** Item in type_requests */
  struct list2_item* item;
} request;

/** Type-indexed array of list of request object */
static struct list2* type_requests;

/** Table of all ranks requesting work
    Map from int rank to request object
 */
static struct table_ip targets;

void
xlb_requestqueue_init()
{
  table_ip_init(&targets, 128);

  type_requests = malloc(sizeof(struct list2) * (size_t)xlb_types_size);
  for (int i = 0; i < xlb_types_size; i++)
    list2_init(&type_requests[i]);
}

void
xlb_requestqueue_add(int rank, int type)
{
  DEBUG("requestqueue_add(rank=%i,type=%i)", rank, type);
  request* R = malloc(sizeof(request));

  struct list2* L = &type_requests[type];
  struct list2_item* item = list2_add(L, R);

  R->rank = rank;
  R->type = type;
  R->item = item;
  bool b = table_ip_add(&targets, rank, R);
  // Assert rank was not already entered
  valgrind_assert_msg(b, "requestqueue: double add: rank: %i", rank);
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

  request* R = table_ip_search(&targets, target_rank);
  if (R != NULL)
  {
    if (R->type != type)
      return ADLB_RANK_NULL;
    struct list2* L = &type_requests[type];
    struct list2_item* item = R->item;
    list2_remove_item(L, item);
    int result = R->rank;
    // printf("R: %p\n", R);
    // printf("result: %i\n", result);
    free(R);
    free(item);
    table_ip_remove(&targets, target_rank);
    return result;
  }
  return ADLB_RANK_NULL;
}

int
xlb_requestqueue_matches_type(int type)
{
  DEBUG("requestqueue_matches_type(%i)...", type);
  struct list2* L = &type_requests[type];
  request* R = list2_pop(L);
  if (R == NULL)
    return ADLB_RANK_NULL;

  int result = R->rank;
  table_ip_remove(&targets, result);
  free(R);
  return result;
}

bool
requestqueue_parallel_workers(int type, int parallelism, int* ranks)
{
  bool result = false;
  struct list2* L = &type_requests[type];
  int count = list2_size(L);

  TRACE("requestqueue_parallel_workers(type=%i x%i) count=%i ...",
        type, parallelism, count);

  if (count >= parallelism)
  {
    TRACE("\t found: count: %i needed: %i", count, parallelism);
    result = true;
    for (int i = 0; i < parallelism; i++)
    {
      request* R = list2_pop(L);
      ranks[i] = R->rank;
      // Release memory:
      request* entry = (request*) table_ip_remove(&targets, R->rank);
      valgrind_assert(entry);
      free(R);
    }
  }
  TRACE_END;
  return result;
}

void
xlb_requestqueue_remove(int worker_rank)
{
  TRACE("requestqueue_remove(%i)", worker_rank);
  request* r = (request*) table_ip_remove(&targets, worker_rank);
  valgrind_assert(r);
  int type = r->type;
  struct list2* L = &type_requests[type];
  list2_remove_item(L, r->item);
  free(r->item);
  free(r);
}

int
xlb_requestqueue_size()
{
  return table_ip_size(&targets);
}

/*
void requestqueue_type_counts(int* types, int size) {
  assert(size >= xlb_types_size);
  for (int t = 0; t < xlb_types_size; t++) {
    struct list2* L = &type_requests[t];
    types[t] = L->size;
  }
}
*/

int
xlb_requestqueue_get(xlb_request_pair* r, int max)
{
  int index = 0;
  for (int t = 0; t < xlb_types_size; t++)
  {
    struct list2* L = &type_requests[t];
    assert(L != NULL);
    for (struct list2_item* item = L->head; item; item = item->next)
    {
      request* rq = (request*) item->data;
      r[index].rank = rq->rank;
      r[index].type = rq->type;
      index++;
      if (index == max)
        return max;
    }
  }
  return index;
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
      request* r = (request*) list2_pop(&type_requests[i]);
      if (r == NULL)
        break;
      int rank = r->rank;
      adlb_code rc = shutdown_rank(rank);
      valgrind_assert_msg(rc == ADLB_SUCCESS, "requestqueue: "
                          "worker did not shutdown: ", rank);
      table_ip_remove(&targets, rank);
      free(r);
    }
  }
  // Free now-empty structures
  free(type_requests);

  assert(table_ip_size(&targets) == 0);
  table_ip_free_callback(&targets, false, NULL);
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

/**
   Release memory
 */
void
requestqueue_finalize()
{}
