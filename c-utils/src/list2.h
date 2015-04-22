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
 * list2.h
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 *
 *  Doubly-linked list
 */

#ifndef LIST2_H
#define LIST2_H

#include <stdbool.h>
#include <stddef.h> // For NULL

struct list2_item
{
  void* data;
  struct list2_item* prev;
  struct list2_item* next;
};

struct list2
{
  struct list2_item* head;
  struct list2_item* tail;
  int size;
};


void list2_init(struct list2* target);

struct list2* list2_create(void);

struct list2_item* list2_add(struct list2* target, void* data);

static inline 
void list2_add_item(struct list2* target, struct list2_item* new_item);

void* list2_pop(struct list2* target);

static inline 
struct list2_item* list2_pop_item(struct list2* target);

void list2_remove_item(struct list2* target, struct list2_item* item);

/**
  Functions for inlining follow.  These are fairly simple functions that
  are often called frequently, so inlining is often useful.
 */

static inline int list2_size(struct list2 *L)
{
  return L->size;
}

/**
  Alternative interface allowing caller to provide list node
 */
static inline void
list2_add_item(struct list2* target, struct list2_item* new_item)
{
  assert(target != NULL);
  new_item->next = NULL;
  new_item->prev = target->tail;

  // Already loaded target->tail, so check that for emptiness
  if (target->tail == NULL)
  {
    // Empty list case
    target->head = new_item;
  }
  else
  {
    // Non-empty list case
    target->tail->next = new_item;
  }

  target->tail = new_item;
  target->size++;
}

static inline struct list2_item*
list2_pop_item(struct list2* target)
{
  struct list2_item* item = target->head;
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
