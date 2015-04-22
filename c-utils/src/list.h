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
 * list.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef LIST_H
#define LIST_H

#include <stdbool.h>
#include <stddef.h>

/**
   Maximum size of a list datum (only used for debug printing)
*/
#define LIST_MAX_DATUM 1024

struct list_item
{
  void* data;
  struct list_item* next;
};

struct list
{
  struct list_item* head;
  struct list_item* tail;
  int size;
};

void list_init(struct list* target);

struct list* list_create(void);

#define list_size(target) (target->size)

struct list_item* list_add(struct list* target, void* data);
#define list_push(target, data) list_add(target, data)

struct list* list_split_words(char* s);

struct list* list_split_lines(const char* s);

/**
   Add this data if list_inspect does not find it.
*/
struct list_item* list_add_bytes(struct list* target,
                                 void* data, int n);

struct list_item* list_add_unique(struct list* target,
                                  int(*cmp)(void*,void*),
                                  void* data);

/**
   Compare data pointer addresses for match.
   @return An equal data pointer or NULL if not found.
*/
void* list_search(struct list* target, void* data);

void* list_inspect(struct list* target, void* data, size_t n);

bool list_matches(struct list* target, int (*cmp)(void*,void*),
                  void* arg);

bool list_remove(struct list* target, void* data);

bool list_erase(struct list* target, void* data, size_t n);

struct list* list_select(struct list* target,
                         int (*cmp)(void*,void*), void* arg);

void* list_select_one(struct list* target, int (*cmp)(void*,void*),
                      void* arg);

bool list_remove_where(struct list* target,
                       int (*cmp)(void*,void*), void* arg);

struct list* list_pop_where(struct list* target,
                            int (*cmp)(void*,void*), void* arg);

void list_transplant(struct list* target, struct list* segment);

/*
  Clear list data structure, and call free() on all values in list.
  Equivalent to list_clear(target, free);
 */
void list_clear(struct list* target);

/*
  Clear list data structure, and call callback, but not free() on all
  values in list.
 */
void list_clear_callback(struct list* target, void (*callback)(void*));

void* list_pop(struct list* target);

void* list_head(struct list* target);

/*
  Remove one element from head of list and return.
  returns NULL if list is empty.
 */
void* list_poll(struct list* target);

void* list_random(struct list* target);

struct list_item* list_ordered_insert(struct list* target,
                                      int (*cmp)(void*,void*), void* data);

bool list_contains(struct list* target,
                   int (*cmp)(void*,void*), void* data);

void list_output(char* (*f)(void*), struct list* target);

void list_printf(char* format, struct list* target);

int list_tostring(char* str, size_t size,
                  char* format, struct list* target);

/**
   Free this list but not its data.
   Equivalent to list_free_callback(target, NULL);
*/
void list_free(struct list* target);

/*
  Free this list and call callback on all data
 */
void list_free_callback(struct list* target, void (*callback)(void*));

/**
   Free this list and its data.
   Equivalent to list_free_callback(target, free);
*/
void list_destroy(struct list* target);

#endif
