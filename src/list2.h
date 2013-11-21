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

void list2_add_item(struct list2* target, struct list2_item* new_item);

void* list2_pop(struct list2* target);

struct list2_item* list2_pop_item(struct list2* target);

void list2_remove_item(struct list2* target, struct list2_item* item);

#define list2_size(L) (L->size)

#endif
