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
  Table which has binary key as key
 */
#ifndef __TABLE_BP_H
#define __TABLE_BP_H

#include <stdbool.h>
#include <stddef.h>

#include "binkeys.h"

typedef struct table_bp_entry table_bp_entry;

#define TABLE_BP_INVALID_KEY BINKEY_PACKED_INVALID

struct table_bp_entry
{
  /* We sometimes store key inline in pointer.  Use table_bp_get_key to
   * access the value of the key correctly */
  binkey_packed_t key;
  void* data; // NULL is valid data
  table_bp_entry *next;
};

typedef struct table_bp
{
  table_bp_entry *array;
  int capacity;
  int size;
  float load_factor;
  int resize_threshold; // Resize if > this size
} table_bp;

#define TABLE_BP_DEFAULT_LOAD_FACTOR 0.75

/*
  Macro for iterating over table entries.  This handles the simple case
  of iterating over all valid table entries with no modifications.  
 */
#define TABLE_BP_FOREACH(T, item) \
  for (int __i = 0; __i < (T)->capacity; __i++) \
    if (table_bp_entry_valid(&((T)->array[__i]))) \
      for (table_bp_entry *item = &((T)->array[__i]); item != NULL; \
           item = item->next)

bool table_bp_init(table_bp* target, int capacity);

bool table_bp_init_custom(table_bp* target, int capacity, float load_factor);

table_bp* table_bp_create(int capacity);

table_bp*
table_bp_create_custom(int capacity, float load_factor);

bool table_bp_add(table_bp *target, const void* key,
                  size_t key_len, void* data);

bool table_bp_set(table_bp* target, const void* key,
                  size_t key_len, void* value, void** old_value);

bool table_bp_search(const table_bp* target, const void* key,
                  size_t key_len, void **value);

bool table_bp_contains(const table_bp* table, const void* key,
                  size_t key_len);

bool table_bp_remove(table_bp* table, const void* key,
                  size_t key_len, void** data);

void table_bp_dump(const char* format, const table_bp* target);

/*
  Free data structure, and callback function with key and value
 */
void table_bp_free_callback(table_bp* target, bool free_root,
                            void (*callback)(const void*, size_t, void*));

void table_bp_free(table_bp* target);

void table_bp_destroy(table_bp* target);

void table_bp_release(table_bp* target);

size_t table_bp_keys_string(char** result,
                            const table_bp* target);

size_t table_bp_keys_string_slice(char** result,
                            const table_bp* target,
                            int count, int offset);

size_t table_bp_keys_tostring(char* result,
                              const table_bp* target);

size_t table_bp_keys_tostring_slice(char* result,
                              const table_bp* target,
                              int count, int offset);

void  table_bp_dumpkeys(const table_bp* target);

/*
  As an optimisation, we store the key in the pointer field if it's
  small enough to fit
  returns: whether we store key inline in pointer
 */
static inline bool table_bp_inline_key(size_t key_len)
{
  return binkey_packed_inline(key_len);
}

/*
   Get key from a valid entry
 */
static inline const void *table_bp_get_key(const table_bp_entry *e)
{
  return binkey_packed_get(&e->key);
}

static inline size_t table_bp_get_key_len(const table_bp_entry *e)
{
  return binkey_packed_len(&e->key);
}

static inline void table_bp_clear_entry(table_bp_entry *entry)
{
  binkey_packed_clear(&entry->key);
  entry->data = NULL;
  entry->next = NULL;
}

/*
  If the entry contains data
 */
static inline bool
table_bp_entry_valid(const table_bp_entry *e)
{
  return binkey_packed_valid(&e->key);
}

/*
  Check if key matches item key. Inline for performance
  Entry must be valid entry
 */
static inline bool
table_bp_key_match(const void *key, size_t key_len, const table_bp_entry *e)
{
  return binkey_eq(key, key_len, table_bp_get_key(e), e->key.key_len);
}
#endif // __TABLE_BP_H
