
/*
 * requestqueue.c
 *
 *  Created on: Jun 28, 2012
 *      Author: wozniak
 */

#include <table_ip.h>
#include <list2.h>

#include "adlb-defs.h"
#include "requestqueue.h"

typedef struct
{
  int rank;
  int type;
  /** Item in type_requests */
  struct list2_item* item;
} request;

/** Type-indexed array of requests */
struct list2* type_requests;

struct table_ip targets;

void
requestqueue_init()
{}

void
requestqueue_add(int rank, int type)
{
  request* R = malloc(sizeof(request));
  R->rank = rank;
  R->type = type;
  struct list2* L = &type_requests[type];
  struct list2_item* item = list2_add(L, R);

  table_ip_add(&targets, rank, item);
}

int
requestqueue_matches_target(int target_rank, int type)
{
  request* R = table_ip_remove(&targets, target_rank);
  if (R != NULL)
  {
    if (R->type != type)
      return ADLB_RANK_NULL;
    struct list2_item* item = R->item;
    struct list2* L = &type_requests[type];
    list2_remove_item(L, item);
    free(R);
    free(item);
    return R->rank;
  }
  return ADLB_RANK_NULL;
}

int
requestqueue_matches_type(int type)
{
  struct list2* L = &type_requests[type];
  request* R = list2_pop(L);
  if (R == NULL)
    return ADLB_RANK_NULL;
  int result = R->rank;
  free(R);
  return result;
}

bool
requestqueue_remove(int worker_rank)
{
  return ADLB_RANK_NULL;
}

// void requestqueue_send_work(int worker);
