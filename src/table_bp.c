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

#include "binkeys.h"

#include "table_bp.h"
#include "c-utils-types.h"

/**
   Warning: If this function fails, it may have leaked memory.
*/
bool
table_bp_init(struct table_bp* target, int capacity)
{
  assert(capacity >= 1);
  target->size     = 0;
  target->capacity = capacity;

  target->array = malloc(sizeof(struct list_bp*) * (size_t)capacity);
  if (!target->array)
  {
    free(target);
    return false;
  }

  for (int i = 0; i < capacity; i++)
  {
    struct list_bp* new_list_bp = list_bp_create();
    if (! new_list_bp)
      return false;
    target->array[i] = new_list_bp;
  }
  return true;
}

struct table_bp*
table_bp_create(int capacity)
{
  struct table_bp* new_table =  malloc(sizeof(const struct table_bp));
  if (! new_table)
    return NULL;

  bool result = table_bp_init(new_table, capacity);
  if (!result)
    return NULL;

  return new_table;
}

void
table_bp_free(struct table_bp* target)
{
  table_bp_free_callback(target, true, NULL);
}

void table_bp_free_callback(struct table_bp* target, bool free_root,
                         void (*callback)(void*, size_t, void*))
{
  for (int i = 0; i < target->capacity; i++)
    list_bp_free_callback(target->array[i], callback);

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
table_bp_destroy(struct table_bp* target)
{
  for (int i = 0; i < target->capacity; i++)
    list_bp_destroy(target->array[i]);

  free(target->array);
  free(target);
}

void
table_bp_release(struct table_bp* target)
{
  free(target->array);
}

/**
   Note: duplicates internal copy of key (in list_bp_add())
 */
bool
table_bp_add(struct table_bp *target, const void* key, size_t key_len,
             void* data)
{
  int index = hash_bin(key, key_len, target->capacity);

  struct list_bp_item* new_item =
    list_bp_add(target->array[index], key, key_len, data);

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
table_bp_set(struct table_bp* target, const void* key, size_t key_len,
          void* value, void** old_value)
{
  int index = hash_bin(key, key_len, target->capacity);

  bool result = list_bp_set(target->array[index], key, key_len, 
                            value, old_value);

  return result;
}

/**
   @param value: this is used to return the value if found
   @return true if found, false if not 
   
   if value is NULL and return is true, this means that the key exists
   but the value is NULL
*/
bool
table_bp_search(const struct table_bp* table, const void* key,
                size_t key_len, void **value)
{
  int index = hash_bin(key, key_len, table->capacity);

  for (struct list_bp_item* item = table->array[index]->head; item;
       item = item->next)
    if (key_match(key, key_len, item) == 0) {
      *value = (void*) item->data;
      return true;
    }

  *value = NULL;
  return false;
}

bool
table_bp_contains(const struct table_bp* table, const void* key,
                  size_t key_len)
{
  void* tmp = NULL;
  return table_bp_search(table, key, key_len, &tmp);
}

bool
table_bp_remove(struct table_bp* table, const void* key, size_t key_len,
                void** data)
{
  int index = hash_bin(key, key_len, table->capacity);
  struct list_bp* list = table->array[index];
  assert(list != NULL);
  bool result = list_bp_remove(list, key, key_len, data);
  if (result)
    table->size--;
  return result;
}

/** format specifies the output format for the data items
 */
void
table_bp_dump(const char* format, const struct table_bp* target)
{
  char s[200];
  int i;
  printf("{\n");
  for (i = 0; i < target->capacity; i++)
    if (target->array[i]->size > 0)
    {
      list_bp_tostring(s, 200, "%s", target->array[i]);
      printf("%i: %s \n", i, s);
    }
  printf("}\n");
}

void
table_bp_dumpkeys(const struct table_bp* target)
{
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
    if (target->array[i]->size)
    {
      printf("%i:", i);
      list_bp_dumpkeys(target->array[i]);
    }
  printf("}\n");
}

size_t
table_bp_keys_string_length(const struct table_bp* target)
{
  size_t result = 0;
  for (int i = 0; i < target->capacity; i++)
    result += list_bp_keys_string_length(target->array[i]);
  return result;
}

size_t
table_bp_keys_string(char** result, const struct table_bp* target)
{
  size_t length = table_bp_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
  // Update length with actual usage
  length = table_bp_keys_tostring(*result, target);
  return length;
}

size_t
table_bp_keys_string_slice(char** result,
                        const struct table_bp* target,
                        int count, int offset)
{
  size_t length = table_bp_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
  // Update length with actual usage
  length = table_bp_keys_tostring_slice(*result, target, count, offset);
  return length;
}

size_t
table_bp_keys_tostring(char* result, const struct table_bp* target)
{
  char* p = result;
  for (int i = 0; i < target->capacity; i++)
    p += list_bp_keys_tostring(p, target->array[i]);
  return (size_t)(p-result);
}

size_t
table_bp_keys_tostring_slice(char* result, const struct table_bp* target,
                          int count, int offset)
{
  // Count of how many items we have covered
  int c = 0;
  char* p = result;
  p[0] = '\0';
  for (int i = 0; i < target->capacity; i++)
  {
    struct list_bp* L = target->array[i];
    for (struct list_bp_item* item = L->head; item; item = item->next)
    {
      if (c < offset) {
        c++;
        continue;
      }
      if (c >= offset+count && count != -1)
        break;
      p += sprintf_key(p, item->key, item->key_len);
      *(p++) = ' ';
      c++;
    }
  }
  return (size_t)(p-result);
}

/** Dump list_bp to string as in snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    internally allocates O(size) memory
    returns int greater than size if size limits are exceeded
            indicating result is garbage
*/
size_t
table_bp_tostring(char* output, size_t size,
               char* format, const struct table_bp* target)
{
  size_t   error = size+1;
  char* ptr   = output;
  int i;
  ptr += sprintf(output, "{\n");

  char* s = malloc(sizeof(char) * size);

  for (i = 0; i < target->capacity; i++)
  {
    size_t r = list_bp_tostring(s, size, format, target->array[i]);
    if (((size_t)(ptr-output)) + r + 2 < size)
      ptr += sprintf(ptr, "%s\n", s);
    else
      return error;
  }
  sprintf(ptr, "}\n");

  free(s);
  return (size_t)(ptr-output);
}
