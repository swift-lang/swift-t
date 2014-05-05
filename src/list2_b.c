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
 * list2_b.c
 *
 *  Created on: May 2, 2014
 *      Author: wozniak, armstrong
 */

#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "list2_b.h"

void
list2_b_init(struct list2_b* target)
{
  assert(target);
  target->size = 0;
  target->head = NULL;
  target->tail = NULL;
}

void list2_b_clear(struct list2_b* target)
{
  struct list2_b_item *curr, *next;
  curr = target->head;
  while (curr)
  {
    next = curr->next;
    free(curr); // Free list node and inline data
    curr = next;
  }

  target->size = 0;
  target->head = NULL;
  target->tail = NULL;
}

struct list2_b*
list2_b_create()
{
  struct list2_b* new_list = malloc(sizeof(struct list2_b));
  if (! new_list)
    return NULL;
  list2_b_init(new_list);
  return new_list;
}

/**
   @return The new list2_b_item.
*/
struct list2_b_item*
list2_b_add(struct list2_b* target, const void* data, size_t data_len)
{
  struct list2_b_item* new_item = list2_b_item_alloc(data_len);
  if (! new_item)
    return NULL;

  memcpy(new_item->data, data, data_len);
  list2_b_add_item(target, new_item);
  return new_item;
}

void
list2_b_remove_item(struct list2_b* target, struct list2_b_item* item)
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

