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

#include "list_sp.h"

struct table
{
  struct list_sp** array;
  int capacity;
  int size;
};

// inline int quickhash_string_hash(char* key, int capacity);
int hash_string(const char* key, int capacity);

/**
   Compress SHA-1 down to a smaller address space,
   namely, 4 bytes.
*/
int SHA1_mod(char* data);

bool table_init(struct table* target, int capacity);

struct table* table_create(int capacity);

bool table_add(struct table *target,
               const char* key, void* data);

bool table_set(struct table* target, const char* key,
               void* value, void** old_value);

bool table_search(const struct table* target, const char* key,
                  void **value);

bool table_contains(const struct table* table, const char* key);

bool table_remove(struct table* table, const char* key, void** data);

void table_dump(const char* format, const struct table* target);

void table_free(struct table* target);

void table_destroy(struct table* target);

void table_release(struct table* target);

int table_keys_string(char** result, const struct table* target);

int table_keys_string_slice(char** result,
                            const struct table* target,
                            int count, int offset);

int table_keys_tostring(char* result, const struct table* target);

int table_keys_tostring_slice(char* result,
                              const struct table* target,
                              int count, int offset);

void  table_dumpkeys(const struct table* target);

#endif
