
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

/** Type-indexed array of requests */
static struct list2* type_requests;

/** Table of all ranks requesting work
    Map from int rank to request object
 */
static struct table_ip targets;

/** Local copy of work_types */
static int rq_work_types;

void
requestqueue_init(int work_types)
{
  rq_work_types = work_types;

  table_ip_init(&targets, 8);

  type_requests = malloc(sizeof(struct list2) * work_types);
  for (int i = 0; i < rq_work_types; i++)
    list2_init(&type_requests[i]);
}

void
requestqueue_add(int rank, int type)
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
  assert(b);
}

/**
   If target_rank is in the request_queue and requests work of
   given type, pop and return that rank.  Else return ADLB_RANK_NULL.
 */
int
requestqueue_matches_target(int target_rank, int type)
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
    printf("R: %p\n", R);
    printf("result: %i\n", result);
    free(R);
    free(item);
    table_ip_remove(&targets, target_rank);
    return result;
  }
  return ADLB_RANK_NULL;
}

int
requestqueue_matches_type(int type)
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

void
requestqueue_remove(int worker_rank)
{
  request* r = (request*) table_ip_remove(&targets, worker_rank);
  valgrind_assert(r);
  int type = r->type;
  struct list2* L = &type_requests[type];
  list2_remove_item(L, r->item);
  free(r);
}

int
requestqueue_size()
{
  return table_ip_size(&targets);
}

int
requestqueue_get(xlb_request_pair* r, int max)
{
  int index = 0;
  for (int t = 0; t < rq_work_types; t++)
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
requestqueue_shutdown()
{
  TRACE_START;
  for (int i = 0; i < rq_work_types; i++)
    while (true)
    {
      request* r = (request*) list2_pop(&type_requests[i]);
      if (r == NULL)
        break;
      int rank = r->rank;
      adlb_code rc = shutdown_rank(rank);
      valgrind_assert_msg(rc == ADLB_SUCCESS, "requestqueue: "
                          "worker did not shutdown: ", rank);
    }
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
  SEND(&g, sizeof(g), MPI_BYTE, rank, ADLB_TAG_RESPONSE_GET);
  return ADLB_SUCCESS;
}

/**
   Release memory
 */
void
requestqueue_finalize()
{

}
