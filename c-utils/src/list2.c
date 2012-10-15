
/*
 * list2.c
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 */

#include <assert.h>
#include <stdlib.h>

#include "list2.h"

void
list2_init(struct list2* target)
{
  assert(target);
  target->size = 0;
  target->head = NULL;
  target->tail = NULL;
}

struct list2*
list2_create()
{
  struct list2* new_list = malloc(sizeof(struct list2));
  if (! new_list)
    return NULL;
  list2_init(new_list);
  return new_list;
}

/**
   @return The new list2_item.
*/
struct list2_item*
list2_add(struct list2* target, void* data)
{
  struct list2_item* new_item = malloc(sizeof(struct list2_item));
  if (! new_item)
    return NULL;

  new_item->data = data;
  new_item->next = NULL;

  if (target->size == 0)
  {
    target->head = new_item;
    target->tail = new_item;
    new_item->prev = NULL;
  }
  else
  {
    new_item->prev = target->tail;
    target->tail->next = new_item;
  }

  target->tail = new_item;
  target->size++;

  return new_item;
}

void*
list2_pop(struct list2* target)
{
  if (target->size == 0)
    return NULL;
  struct list2_item* item = target->head;
  void* result = item->data;
  list2_remove_item(target, item);
  free(item);
  return result;
}

void
list2_remove_item(struct list2* target, struct list2_item* item)
{
  assert(target->size > 0);
  if (target->size == 1)
  {
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return;
  }
  if (target->head == item)
    target->head = item->next;
  if (target->tail == item)
    target->tail = item->prev;
  if (item->prev != NULL)
    item->prev->next = item->next;
  if (item->next != NULL)
    item->next->prev = item->prev;
  target->size--;
}

int
list2_size(struct list2* target)
{
  return target->size;
}
