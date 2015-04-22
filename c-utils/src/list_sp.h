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

#ifndef LIST_SP_H
#define LIST_SP_H

#include <stdbool.h>
#include <stddef.h>

struct list_sp_item
{
  char* key;
  void* data;
  struct list_sp_item* next;
};

struct list_sp
{
  struct list_sp_item* head;
  struct list_sp_item* tail;
  int size;
};

struct list_sp* list_sp_create(void);

struct list_sp_item* list_sp_add(struct list_sp* target,
                                 const char* key, void* data);

bool list_sp_set(struct list_sp* target, const char* key,
                 void* value, void** old_value);

/**
   Return and remove the head data item
   Caller is now responsible to free key, data
   @return True if key/data are set, else false (empty)
 */
bool list_sp_pop(struct list_sp* target, char** key, void** data);

/**
   If found, caller is responsible for old_value -
          it was provided by the user
   @return True if found
 */
bool list_sp_remove(struct list_sp* target, const char* key,
                    void** data);

void list_sp_free(struct list_sp* target);

/*
  Free data structure, and callback function with key and value
 */
void list_sp_free_callback(struct list_sp* target,
                           void (*callback)(char*, void*));

void list_sp_destroy(struct list_sp* target);

void list_sp_dump(const char* format, const struct list_sp* target);

void list_sp_dumpkeys(const struct list_sp* target);

size_t list_sp_keys_string_length(const struct list_sp* target);

size_t list_sp_keys_tostring(char* result, const struct list_sp* target);

size_t list_sp_tostring(char* str, size_t size,
                     const char* format,
                     const struct list_sp* target);

#endif
