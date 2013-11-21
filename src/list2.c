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
  list2_add_item(target, new_item);
  return new_item;
}

void*
list2_pop(struct list2* target)
{
  if (target->size == 0)
    return NULL;
  struct list2_item* item = list2_pop_item(target);
  void* result = item->data;
  free(item);
  return result;
}

void
list2_remove_item(struct list2* target, struct list2_item* item)
{
  assert(target->size > 0);
  
  if (target->head == item)
  {
    // head case - no prev
    target->head = item->next;
  }
  else
  {
    // non-head case - has prev
    item->prev->next = item->next;
  }

  if (target->tail == item)
  {
    // tail case - no next
    target->tail = item->prev;
  }
  else
  {
    // non-tail case - has next
    item->next->prev = item->prev;
  }
  target->size--;
}

