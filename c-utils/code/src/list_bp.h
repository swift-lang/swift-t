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
   Extremely simple singly-linked of keyed data items
 */

#ifndef __LIST_BP_H
#define __LIST_BP_H

#include <stdbool.h>
#include <stddef.h>

#include "binkeys.h"

struct list_bp_item
{
  void* key;
  size_t key_len;
  void* data;
  struct list_bp_item* next;
};

struct list_bp
{
  struct list_bp_item* head;
  struct list_bp_item* tail;
  int size;
};

struct list_bp* list_bp_create(void);

struct list_bp_item* list_bp_add(struct list_bp* target,
             const void* key, size_t key_len, void* data);

bool list_bp_set(struct list_bp* target, const void* key,
                 size_t key_len, void* value, void** old_value);

/**
   Return and remove the head data item
   Caller is now responsible to free key, data
   @return True if key/data are set, else false (empty)
 */
bool list_bp_pop(struct list_bp* target, void** key, size_t *key_len,
                 void** data);

/**
   If found, caller is responsible for old_value -
          it was provided by the user
   @return True if found
 */
bool list_bp_remove(struct list_bp* target, const void* key,
                    size_t key_len, void** data);

void list_bp_free(struct list_bp* target);

/*
  Free data structure, and callback function with key, key length and
  value
 */
void list_bp_free_callback(struct list_bp* target,
                           void (*callback)(void*, size_t, void*));

void list_bp_destroy(struct list_bp* target);

void list_bp_dump(const char* format, const struct list_bp* target);

void list_bp_dumpkeys(const struct list_bp* target);

size_t list_bp_keys_string_length(const struct list_bp* target);

size_t list_bp_keys_tostring(char* result, const struct list_bp* target);

size_t list_bp_tostring(char* str, size_t size,
                     const char* format,
                     const struct list_bp* target);

char*
bp_append_pair(char* ptr, const void *key, size_t key_len,
            const char* format, const void* data, bool next);

/*
  Check if key matches item key. Inline for performance
 */
static inline bool
list_bp_key_match(const void *key, size_t key_len, struct list_bp_item *item)
{
  return binkey_eq(key, key_len, item->key, item->key_len);
}
#endif //__LIST_BP_H
