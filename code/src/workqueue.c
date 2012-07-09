
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
#include <tree.h>

#include "adlb-defs.h"
#include "common.h"
#include "debug.h"
#include "workqueue.h"

/** Uniquify work units on this server */
long unique = 1;

/**
   Map from target rank to type array of priority heap -
   heap sorted by negative priority.
   Only contains targeted work
*/
struct table_ip targeted_work;

/**
   Array of trees: one for each work type
   Does not contain targeted work
 */
struct tree* typed_work;

void
workqueue_init(int work_types)
{
  DEBUG("workqueue_init(work_types=%i)", work_types);
  // heap_init(&prioritized_work, 128);
  table_ip_init(&targeted_work, 128);
  typed_work = malloc(sizeof(struct tree) * work_types);
  for (int i = 0; i < work_types; i++)
    tree_init(&typed_work[i]);
}

void
workqueue_add(int type, int putter, int priority, int answer,
              int target_rank, int length, void* item)
{
  DEBUG("workqueue_add");
  work_unit* wu = malloc(sizeof(work_unit));
  wu->id = unique++;
  wu->type = type;
  wu->putter = putter;
  wu->priority = priority;
  wu->answer = answer;
  wu->target = target_rank;
  wu->length = length;
  wu->item = malloc(length);
  memcpy(wu->item, item, length);

  if (target_rank < 0)
  {
    struct tree* T = &typed_work[type];
    tree_add(T, -priority, wu);
  }
  else
  {
    heap* A = table_ip_search(&targeted_work, target_rank);
    if (A == NULL)
    {
      A = malloc(types_size * sizeof(heap*));
      table_ip_add(&targeted_work, target_rank, A);
      for (int i = 0; i < types_size; i++)
      {
        heap* H = &A[i];
        heap_init(H, 8);
      }
    }
    heap* H = &A[type];
    heap_add(H, -priority, wu);
  }
}

//static work_unit*
//select_untargeted(void)
//{
//  long highest_key = LONG_MIN;
//  int  highest_type = -1;
//  struct tree_node* highest_node = NULL;
//  for (int i = 0; i < num_types; i++)
//  {
//    struct tree* T = &typed_work[i];
//    if (T->size == 0)
//      continue;
//    long k = tree_leftmost_key(T);
//    if (k > highest_key)
//    {
//      highest_key = k;
//      highest_type = i;
//    }
//  }
//  if (highest_node == NULL)
//    return NULL;
//
//  struct tree* T = &typed_work[highest_type];
//  tree_remove_node(T, highest_node);
//  return highest_node->data;
//}

work_unit*
workqueue_get(int target, int type)
{
  DEBUG("workqueue_get(target=%i, type=%i)", target, type);

  work_unit* wu = NULL;

  heap* A = table_ip_search(&targeted_work, target);
  if (A != NULL)
  {
    // Targeted work was found
    heap* H = &A[type];
    if (heap_size(H) != 0)
    {
      wu = heap_root_val(H);
      heap_del_root(H);
      return wu;
    }
  }

  // Select untargeted work
  struct tree* T = &typed_work[type];
  struct tree_node* node = tree_leftmost(T);
  if (node == NULL)
    // We found nothing
    return NULL;
  tree_remove_node(T, node);
  wu = node->data;
  free(node);
  return wu;
}

void
work_unit_free(work_unit* wu)
{
  free(wu->item);
  free(wu);
}
