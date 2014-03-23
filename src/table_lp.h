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

typedef struct table_lp_entry table_lp_entry;
typedef struct table_lp_bucket table_lp_bucket;

typedef struct table_lp
{
  table_lp_bucket* array;
  int capacity;
  int size;
  float load_factor;
  int resize_threshold; // Resize if > this size
} table_lp;

struct table_lp_entry
{
  int64_t key; 
  void* data; // NULL is valid data
  table_lp_entry *next;
};

struct table_lp_bucket
{
  table_lp_entry head;
  bool valid; // Put after bigger struct to help with alignment
};

#define TABLE_LP_DEFAULT_LOAD_FACTOR 0.75

/*
  Macro for iterating over table entries.  This handles the simple case
  of iterating over all valid table entries with no modifications.  
 */
#define TABLE_LP_FOREACH(T, item) \
  for (int __i = 0; __i < (T)->capacity; __i++) \
    if (table_lp_bucket_valid(&((T)->array[__i]))) \
      for (table_lp_entry *item = &((T)->array[__i].head); item != NULL; \
           item = item->next)

/**
   @param capacity: Number of entries.  Must not be 0
 */
bool table_lp_init(table_lp *table, int capacity);

bool table_lp_init_custom(table_lp *table, int capacity,
                          float load_factor);

table_lp* table_lp_create(int capacity);

table_lp* table_lp_create_custom(int capacity, float load_factor);

bool table_lp_add(table_lp* table, int64_t key, void* data);

bool table_lp_set(table_lp* table, int64_t key,
               void* value, void** old_value);

bool table_lp_search(table_lp* table, int64_t key, void **value);

bool table_lp_contains(table_lp* table, int64_t key);

bool table_lp_move(table_lp* table,
                   int64_t key_old, int64_t key_new);

bool table_lp_remove(table_lp* table, int64_t key, void **value);

void table_lp_destroy(table_lp* target);

void table_lp_free_callback(table_lp* target, bool free_root,
                            void (*callback)(int64_t, void*));

void table_lp_clear(table_lp* target);

void table_lp_delete(table_lp* target);

void table_lp_release(table_lp* target);

void table_lp_dump(const char* format, const table_lp* target);

size_t table_lp_tostring(char* str, size_t size,
                    char* format, table_lp* target);

void table_lp_dumpkeys(const table_lp* target);

/*
  If the entry contains data
 */
static inline bool
table_lp_bucket_valid(const table_lp_bucket *b)
{
  return b->valid;
}

#endif
