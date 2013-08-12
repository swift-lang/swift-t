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
   Extremely simple singly-linked list of longs.
   Everything is IN the list, no external pointers.
 */

#ifndef LIST_L_H
#define LIST_L_H

#include <stdbool.h>
#include "c-utils-types.h"

struct list_l_item
{
  int64_t data;
  struct list_l_item* next;
};

struct list_l
{
  struct list_l_item* head;
  struct list_l_item* tail;
  int size;
};

void list_l_init(struct list_l* target);
struct list_l* list_l_create(void);

/**
   Add to the tail of the list_l.
*/
struct list_l_item* list_l_add(struct list_l* target, int64_t data);

int64_t list_l_search(struct list_l* target, int64_t data);

bool list_l_remove(struct list_l* target, int64_t data);
bool list_l_erase(struct list_l* target, int64_t data, size_t n);

/**
   Remove and return the head data item.
*/
int64_t list_l_pop(struct list_l* target);

/**
   Return the head data item, -1 if the list is empty.
*/
int64_t list_l_peek(struct list_l* target);

/**
   Remove and return the tail data item.
*/
int64_t list_l_poll(struct list_l* target);

struct list_l_item *
list_l_ordered_insert(struct list_l* target, int64_t data);

struct list_l_item* list_l_unique_insert(struct list_l* target,
                                         int64_t data);

bool list_l_contains(struct list_l* target, int64_t data);

bool list_l_tolongs(const struct list_l* target,
                    int64_t** result, int* count);

void list_l_dump(struct list_l* target);

int list_l_tostring(char* str, size_t size,
                    struct list_l* target);

void list_l_free(struct list_l* target);

#endif

