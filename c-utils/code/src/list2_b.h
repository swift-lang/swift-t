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
 * list2_b.h
 *
 *  Created on: May 2, 2014
 *      Author: wozniak, armstrong
 *
 *  Doubly-linked list with inline data
 */

#ifndef LIST2_B_H
#define LIST2_B_H

#include <stdbool.h>
#include <stddef.h> // For NULL
#include <stdlib.h> // For malloc

struct list2_b_item
{
  struct list2_b_item* prev;
  struct list2_b_item* next;
  char data[]; // Inline data
};

struct list2_b
{
  struct list2_b_item* head;
  struct list2_b_item* tail;
  int size;
};


void list2_b_init(struct list2_b* target);

void list2_b_clear(struct list2_b* target);

struct list2_b* list2_b_create(void);

static inline struct list2_b_item* list2_b_item_alloc(size_t data_len);

struct list2_b_item* list2_b_add(struct list2_b* target, const void *data,
                                 size_t data_len);

static inline struct list2_b_item* list2_b_pop_item(struct list2_b* target);

void list2_b_remove_item(struct list2_b* target, struct list2_b_item* item);

/**
  Alternative interface allowing caller to provide list node
 */
void list2_b_add_item(struct list2_b* target, struct list2_b_item* new_item);

/*
  Functions for inlining follow.  These are fairly simple functions that
  are often called frequently, so inlining is often useful.
*/

static inline int list2_b_size(struct list2_b *L)
{
  return L->size;
}

static inline struct list2_b_item* list2_b_item_alloc(size_t data_len)
{
  return malloc(sizeof(struct list2_b_item) + data_len);
}

static inline struct list2_b_item*
list2_b_pop_item(struct list2_b* target)
{
  struct list2_b_item* item = target->head;
  if (item == NULL)
    return NULL;

  // Code for special case of unlinking head
  target->head = item->next;
  if (target->tail == item)
  {
    // Tail case - no next - must be only entry in list
    target->tail = NULL;
  }
  else
  {
    // Non-tail case - must have next. Next is now head
    item->next->prev = NULL;
  }

  target->size--;
  return item;
}

#endif
