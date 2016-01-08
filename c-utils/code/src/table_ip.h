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
 * table_ip.h
 *
 * Table mapping int to void pointer
 */

#ifndef TABLE_IP_H
#define TABLE_IP_H

#include <stdbool.h>
#include <stddef.h>

#include "c-utils-types.h"

typedef struct table_ip_entry table_ip_entry;
typedef struct table_ip_bucket table_ip_bucket;

typedef struct table_ip
{
  table_ip_bucket* array;
  int capacity;
  int size;
  float load_factor;
  int resize_threshold; // Resize if > this size
} table_ip;

struct table_ip_entry
{
  int key; 
  void* data; // NULL is valid data
  table_ip_entry *next;
};

struct table_ip_bucket
{
  table_ip_entry head;
  bool valid; // Put after bigger struct to help with alignment
};

#define TABLE_IP_DEFAULT_LOAD_FACTOR 0.75

/*
  Macro for iterating over table entries.  This handles the simple case
  of iterating over all valid table entries with no modifications.  
 */
#define TABLE_IP_FOREACH(T, item) \
  for (int __i = 0; __i < (T)->capacity; __i++) \
    if (table_ip_bucket_valid(&((T)->array[__i]))) \
      for (table_ip_entry *item = &((T)->array[__i].head); item != NULL; \
           item = item->next)

/**
   @param capacity: Number of entries.  Must not be 0
 */
bool table_ip_init(table_ip *table, int capacity);

bool table_ip_init_custom(table_ip *table, int capacity,
                          float load_factor);

table_ip* table_ip_create(int capacity);

table_ip* table_ip_create_custom(int capacity, float load_factor);

bool table_ip_add(table_ip* table, int key, void* data);

bool table_ip_set(table_ip* target, int key,
               void* value, void** old_value);

bool table_ip_search(table_ip* table, int key, void **value);

bool table_ip_contains(table_ip* table, int key);

bool table_ip_move(table_ip* table,
                   int key_old, int key_new);

bool table_ip_remove(table_ip* table, int key, void **value);

void table_ip_destroy(table_ip* target);

void table_ip_free_callback(table_ip* target, bool free_root,
                            void (*callback)(int, void*));

void table_ip_clear(table_ip* target);

void table_ip_delete(table_ip* target);

void table_ip_release(table_ip* target);

void table_ip_dump(const char* format, const table_ip* target);

size_t table_ip_tostring(char* str, size_t size,
                    char* format, table_ip* target);

void table_ip_dumpkeys(const table_ip* target);

/*
  If the entry contains data
 */
static inline bool
table_ip_bucket_valid(const table_ip_bucket *b)
{
  return b->valid;
}

#endif
