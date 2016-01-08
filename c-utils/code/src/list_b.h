/*
 * Copyright 2014 University of Chicago and Argonne National Laboratory
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
   Extremely simple singly-linked list of binary data stored inline in
   list nodes.
   Everything is IN the list, no external pointers.
 */

#ifndef LIST_B_H
#define LIST_B_H

#include <stdbool.h>

struct list_b_item
{
  struct list_b_item* next;
  size_t data_len;
  char data[];
};

struct list_b
{
  struct list_b_item* head;
  struct list_b_item* tail;
  int size;
};

void list_b_init(struct list_b* target);
struct list_b* list_b_create(void);

/**
   Add to the tail of the list_b.
*/
struct list_b_item* list_b_add(struct list_b* target, const void *data,
                               size_t data_len);

bool list_b_remove(struct list_b* target, const void *data,
                   size_t data_len);

/**
   Remove and return the head data item.
   Caller must free result
*/
struct list_b_item *list_b_pop(struct list_b* target);

/**
   Return the head data item, NULL if the list is empty.
*/
const void *list_b_peek(struct list_b* target, size_t *data_len);

/**
   Remove and return the tail data item.
   Caller must free result
   NOTE: expensive, may traverse entire list
*/
struct list_b_item *list_b_poll(struct list_b* target);

struct list_b_item *
list_b_ordered_insert(struct list_b* target, const void *data,
                      size_t data_len);

struct list_b_item* list_b_unique_insert(struct list_b* target,
                             const void *data, size_t data_len);

bool list_b_contains(struct list_b* target, const void *data,
                      size_t data_len);

void list_b_dump(const char *format, struct list_b* target);

int list_b_tostring(char* str, size_t size,
                    struct list_b* target);

void list_b_free(struct list_b* target);

void list_b_clear(struct list_b* target);

#endif

