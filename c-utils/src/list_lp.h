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
 * list_lp.h
 *
 * List with wider integer keys
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef LIST_LP_H
#define LIST_LP_H

#include <stdbool.h>
#include <stddef.h>

#include "c-utils-types.h"

struct list_lp_item
{
  int64_t key;
  void* data;
  struct list_lp_item* next;
};

struct list_lp
{
  struct list_lp_item* head;
  struct list_lp_item* tail;
  int size;
};

void list_lp_init(struct list_lp* target);

struct list_lp* list_lp_create(void);

struct list_lp_item* list_lp_add(struct list_lp* target,
                                 int64_t key, void* data);
#define list_lp_push(target, key, data) list_lp_add(target, key, data)

void list_lp_add_item(struct list_lp* target,
                      struct list_lp_item* item);

struct list_lp_item* list_lp_ordered_insert(struct list_lp* target,
                                            int64_t key, void* data);

struct list_lp_item* list_lp_ordered_insertdata(struct list_lp* target,
                                                int64_t key, void* data,
                                                bool (*cmp)(void*,void*));

void* list_lp_pop(struct list_lp* target);

void* list_lp_poll(struct list_lp* target);

void* list_lp_get(struct list_lp* target, int i);

void* list_lp_search(struct list_lp* target, int64_t key);

void list_lp_free(struct list_lp* target);

void* list_lp_remove(struct list_lp* target, int64_t key);

struct list_lp_item* list_lp_remove_item(struct list_lp* target,
                                         int64_t key);

void list_lp_destroy(struct list_lp* target);

void list_lp_free_callback(struct list_lp* target,
                           void (*callback)(int64_t, void*));

void list_lp_clear(struct list_lp* target);

void list_lp_clear_callback(struct list_lp* target,
                            void (*callback)(int64_t, void*));

void list_lp_delete(struct list_lp* target);

//// Output methods...
void list_lp_dump(char* format, struct list_lp* target);
void list_lp_dumpkeys(struct list_lp* target);
void list_lp_xdumpkeys(struct list_lp* target);
void list_lp_output(char* (*f)(void*), struct list_lp* target);
size_t list_lp_tostring(char* str, size_t size,
                      char* format, struct list_lp* target);

#endif
