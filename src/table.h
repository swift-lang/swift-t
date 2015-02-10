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

#ifndef HASHTABLE_H
#define HASHTABLE_H

#include <stdbool.h>
#include <stddef.h>
#include <string.h>

typedef struct table_entry table_entry;

struct table
{
  struct table_entry* array;
  int capacity;
  int size;
  float load_factor;
  int resize_threshold; // Resize if > this size
};

struct table_entry
{
  char *key; 
  void* data; // NULL is valid data
  table_entry *next;
};

#define TABLE_DEFAULT_LOAD_FACTOR 0.75

/*
  Macro for iterating over table entries.  This handles the simple case
  of iterating over all valid table entries with no modifications.  
 */
#define TABLE_FOREACH(T, item) \
  for (int __i = 0; __i < (T)->capacity; __i++) \
    if (table_entry_valid(&((T)->array[__i]))) \
      for (table_entry *item = &((T)->array[__i]); item != NULL; \
           item = item->next)

bool table_init(struct table* target, int capacity);

bool table_init_custom(struct table* target, int capacity,
                       float load_factor);

struct table* table_create(int capacity);

struct table* table_create_custom(int capacity, float load_factor);

bool table_add(struct table *target, const char* key, void *value);

bool table_set(struct table* target, const char* key,
               void* value, void** old_value);

bool table_search(const struct table* target, const char* key,
                  void **value);

/**
   Return pointer to key or NULL if not found.
   Could be used to save memory if key is referenced elsewhere.
 */
char* table_locate_key(const struct table* T, const char* key);

bool table_contains(const struct table* table, const char* key);

bool table_remove(struct table* table, const char* key, void** data);

/*
  Free data structure, and callback function with key and value
 */
void table_free_callback(struct table* target, bool free_root,
                         void (*callback)(const char*, void*));

void table_free(struct table* target);

void table_destroy(struct table* target);

void table_release(struct table* target);

void table_dump(const char* format, const struct table* target);

size_t
table_tostring(char* output, size_t size,
               char* format, const struct table* target);

size_t table_keys_string(char** result, const struct table* target);

size_t table_keys_string_slice(char** result,
                            const struct table* target,
                            int count, int offset);

size_t table_keys_tostring(char* result, const struct table* target);

size_t table_keys_tostring_slice(char* result,
                              const struct table* target,
                              int count, int offset);

void table_dumpkeys(const struct table* target);

static inline void table_clear_entry(table_entry *entry)
{
  entry->key = NULL;
  entry->data = NULL;
  entry->next = NULL;
}

/*
  If the entry contains data
 */
static inline bool
table_entry_valid(const table_entry *e)
{
  return e->key != NULL;
}

/*
  Check if key matches item key. Inline for performance
  Entry must be valid entry
 */
static inline bool
table_key_match(const char *key, const table_entry *e)
{
  return strcmp(key, e->key) == 0;
}
#endif
