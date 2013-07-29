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
 * table_lp.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 *
 * Table mapping 64-bit int to void pointer
 */

#ifndef TABLE_LP_H
#define TABLE_LP_H

#include <stdbool.h>

#include "c-utils-types.h"
#include "list_lp.h"

struct table_lp
{
  struct list_lp* array;
  int capacity;
  int size;
};

/**
   @param capacity: Number of entries.  Must not be 0
 */
bool table_lp_init(struct table_lp *table, int capacity);

struct table_lp* table_lp_create(int capacity);

bool table_lp_add(struct table_lp *table, cutil_long key, void* data);

void* table_lp_search(struct table_lp* table, cutil_long key);

bool table_lp_contains(struct table_lp* table, cutil_long key);

bool table_lp_move(struct table_lp* table,
                   cutil_long key_old, cutil_long key_new);

void* table_lp_remove(struct table_lp* table, cutil_long key);

void table_lp_destroy(struct table_lp* target);

void table_lp_free_callback(struct table_lp* target, bool free_root,
                            void (*callback)(cutil_long, void*));

void table_lp_clear(struct table_lp* target);

void table_lp_delete(struct table_lp* target);

void table_lp_release(struct table_lp* target);

void table_lp_dump(char* format, struct table_lp* target);

size_t table_lp_tostring(char* str, size_t size,
                    char* format, struct table_lp* target);

void table_lp_dumpkeys(struct table_lp* target);

#endif
