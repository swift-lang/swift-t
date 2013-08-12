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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "table.h"
#include "c-utils-types.h"
#include "jenkins-hash.h"

int
hash_string(const char* data, int table_size)
{
  uint32_t p = 0;
  uint32_t q = 0;
  size_t length = strlen(data);
  bj_hashlittle2(data, length, &p, &q);

  int index = (int) (p % (uint32_t)table_size);
  return index;
}

int64_t
hash_string_long(const char* data)
{
  uint32_t p, q;
  size_t length = strlen(data);
  bj_hashlittle2(data, length, &p, &q);

  int64_t result = 0;

  result += p;

  // printf("hash_string %s -> %i \n", data, index);

  return result;
}

/**
   Warning: If this function fails, it may have leaked memory.
*/
bool
table_init(struct table* target, int capacity)
{
  assert(capacity >= 1);
  target->size     = 0;
  target->capacity = capacity;

  target->array = malloc(sizeof(struct list_sp*) * (size_t)capacity);
  if (!target->array)
  {
    free(target);
    return false;
  }

  for (int i = 0; i < capacity; i++)
  {
    struct list_sp* new_list_sp = list_sp_create();
    if (! new_list_sp)
      return false;
    target->array[i] = new_list_sp;
  }
  return true;
}

struct table*
table_create(int capacity)
{
  struct table* new_table =  malloc(sizeof(const struct table));
  if (! new_table)
    return NULL;

  bool result = table_init(new_table, capacity);
  if (!result)
    return NULL;

  return new_table;
}

void
table_free(struct table* target)
{
  table_free_callback(target, true, NULL);
}

void table_free_callback(struct table* target, bool free_root,
                         void (*callback)(char*, void*))
{
  for (int i = 0; i < target->capacity; i++)
    list_sp_free_callback(target->array[i], callback);

  free(target->array);

  if (free_root)
  {
    free(target);
  }
  else
  {
    target->array = NULL;
    target->capacity = target->size = 0;
  }
}

void
table_destroy(struct table* target)
{
  for (int i = 0; i < target->capacity; i++)
    list_sp_destroy(target->array[i]);

  free(target->array);
  free(target);
}

void
table_release(struct table* target)
{
  free(target->array);
}

/**
   Note: duplicates internal copy of key (in list_sp_add())
 */
bool
table_add(struct table *target, const char* key, void* data)
{
  int index = hash_string(key, target->capacity);

  struct list_sp_item* new_item =
    list_sp_add(target->array[index], key, data);

  if (! new_item)
    return false;

  target->size++;

  return true;
}

/**
   If found, caller is responsible for old_value -
          it was provided by the user
   @return True if found
 */
bool
table_set(struct table* target, const char* key,
          void* value, void** old_value)
{
  int index = hash_string(key, target->capacity);

  bool result =
      list_sp_set(target->array[index], key, value, old_value);

  return result;
}

/**
   @param value: this is used to return the value if found
   @return true if found, false if not 
   
   if value is NULL and return is true, this means that the key exists
   but the value is NULL
*/
bool
table_search(const struct table* table, const char* key,
             void **value)
{
  int index = hash_string(key, table->capacity);

  for (struct list_sp_item* item = table->array[index]->head; item;
       item = item->next)
    if (strcmp(key, item->key) == 0) {
      *value = (void*) item->data;
      return true;
    }

  *value = NULL;
  return false;
}

bool
table_contains(const struct table* table, const char* key)
{
  void* tmp = NULL;
  return table_search(table, key, &tmp);
}

bool
table_remove(struct table* table, const char* key, void** data)
{
  int index = hash_string(key, table->capacity);
  struct list_sp* list = table->array[index];
  assert(list != NULL);
  bool result = list_sp_remove(list, key, data);
  if (result)
    table->size--;
  return result;
}

/** format specifies the output format for the data items
 */
void
table_dump(const char* format, const struct table* target)
{
  char s[200];
  int i;
  printf("{\n");
  for (i = 0; i < target->capacity; i++)
    if (target->array[i]->size > 0)
    {
      list_sp_tostring(s, 200, "%s", target->array[i]);
      printf("%i: %s \n", i, s);
    }
  printf("}\n");
}

void
table_dumpkeys(const struct table* target)
{
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
    if (target->array[i]->size)
    {
      printf("%i:", i);
      list_sp_dumpkeys(target->array[i]);
    }
  printf("}\n");
}

size_t
table_keys_string_length(const struct table* target)
{
  size_t result = 0;
  for (int i = 0; i < target->capacity; i++)
    result += list_sp_keys_string_length(target->array[i]);
  return result;
}

size_t
table_keys_string(char** result, const struct table* target)
{
  size_t length = table_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
  // Update length with actual usage
  length = table_keys_tostring(*result, target);
  return length;
}

size_t
table_keys_string_slice(char** result,
                        const struct table* target,
                        int count, int offset)
{
  size_t length = table_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
  // Update length with actual usage
  length = table_keys_tostring_slice(*result, target, count, offset);
  return length;
}

size_t
table_keys_tostring(char* result, const struct table* target)
{
  char* p = result;
  for (int i = 0; i < target->capacity; i++)
    p += list_sp_keys_tostring(p, target->array[i]);
  return (size_t)(p-result);
}

size_t
table_keys_tostring_slice(char* result, const struct table* target,
                          int count, int offset)
{
  // Count of how many items we have covered
  int c = 0;
  char* p = result;
  p[0] = '\0';
  for (int i = 0; i < target->capacity; i++)
  {
    struct list_sp* L = target->array[i];
    for (struct list_sp_item* item = L->head; item; item = item->next)
    {
      if (c < offset) {
        c++;
        continue;
      }
      if (c >= offset+count && count != -1)
        break;
      p += sprintf(p, "%s ", (char*) item->key);
      c++;
    }
  }
  return (size_t)(p-result);
}

/** Dump list_sp to string as in snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    internally allocates O(size) memory
    returns int greater than size if size limits are exceeded
            indicating result is garbage
*/
size_t
table_tostring(char* output, size_t size,
               char* format, const struct table* target)
{
  size_t   error = size+1;
  char* ptr   = output;
  int i;
  ptr += sprintf(output, "{\n");

  char* s = malloc(sizeof(char) * size);

  for (i = 0; i < target->capacity; i++)
  {
    size_t r = list_sp_tostring(s, size, format, target->array[i]);
    if (((size_t)(ptr-output)) + r + 2 < size)
      ptr += sprintf(ptr, "%s\n", s);
    else
      return error;
  }
  sprintf(ptr, "}\n");

  free(s);
  return (size_t)(ptr-output);
}
