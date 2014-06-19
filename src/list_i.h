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

/**
   Extremely simple singly-linked list of ints.
   Everything is IN the list, no external pointers.
 */

#ifndef INLIST_H
#define INLIST_H

#include <stdbool.h>
#include <stdlib.h>

struct list_i_item
{
  int data;
  struct list_i_item* next;
};

struct list_i
{
  struct list_i_item* head;
  struct list_i_item* tail;
  int size;
};

void list_i_init(struct list_i* target);

struct list_i* list_i_create(void);

int list_i_size(struct list_i* target);

struct list_i_item* list_i_add(struct list_i* target, int data);

struct list_i* list_i_parse(char* s);

int list_i_search(struct list_i* target, int data);

int list_i_random(struct list_i* target);

bool list_i_remove(struct list_i* target, int data);

int list_i_pop(struct list_i* target);

int list_i_peek(struct list_i* target);

int list_i_poll(struct list_i* target);

struct list_i_item* list_i_ordered_insert(struct list_i* target,
                                          int data);

struct list_i_item* list_i_unique_insert(struct list_i* target,
                                         int data);

bool list_i_contains(struct list_i* target, int data);

void list_i_printf(struct list_i* target);

/*
int list_i_tostring(char* str, size_t size,
struct list_i* target); */

char* list_i_serialize(struct list_i* target);

void list_i_clear(struct list_i* target);

void list_i_free(struct list_i* target);

bool list_i_toints(struct list_i* target, int** result, int* count);

/*
  Efficient item adding for inlining when client library wants to manage
  its own list nodes.
 */
static inline void list_i_add_item(struct list_i* target,
                                   struct list_i_item* item)
{
  item->next = NULL;

  if (target->size == 0)
  {
    target->head = item;
    target->tail = item;
  }
  else
  {
    target->tail->next = item;
  }

  target->tail = item;
  target->size++;
}

static inline struct list_i_item*
list_i_pop_item(struct list_i* target)
{
  if (target->size == 0)
  {
    return NULL;
  }
  
  struct list_i_item* head = target->head;
  target->head = head->next;
  target->size--;

  if (target->size == 1)
  {
    target->tail = NULL;
  }
  return head;
}

#endif
