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

#include "list_bp.h"

struct table_bp
{
  struct list_bp** array;
  int capacity;
  int size;
};

bool table_bp_init(struct table_bp* target, int capacity);

struct table_bp* table_bp_create(int capacity);

bool table_bp_add(struct table_bp *target, const void* key,
                  size_t key_len, void* data);

bool table_bp_set(struct table_bp* target, const void* key,
                  size_t key_len, void* value, void** old_value);

bool table_bp_search(const struct table_bp* target, const void* key,
                  size_t key_len, void **value);

bool table_bp_contains(const struct table_bp* table, const void* key,
                  size_t key_len);

bool table_bp_remove(struct table_bp* table, const void* key,
                  size_t key_len, void** data);

void table_bp_dump(const char* format, const struct table_bp* target);

/*
  Free data structure, and callback function with key and value
 */
void table_bp_free_callback(struct table_bp* target, bool free_root,
                            void (*callback)(void*, size_t, void*));

void table_bp_free(struct table_bp* target);

void table_bp_destroy(struct table_bp* target);

void table_bp_release(struct table_bp* target);

size_t table_bp_keys_string(char** result,
                            const struct table_bp* target);

size_t table_bp_keys_string_slice(char** result,
                            const struct table_bp* target,
                            int count, int offset);

size_t table_bp_keys_tostring(char* result,
                              const struct table_bp* target);

size_t table_bp_keys_tostring_slice(char* result,
                              const struct table_bp* target,
                              int count, int offset);

void  table_bp_dumpkeys(const struct table_bp* target);

#endif // __TABLE_BP_H
