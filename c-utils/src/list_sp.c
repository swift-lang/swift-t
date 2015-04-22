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

#include "list_sp.h"

#include "strkeys.h"

extern struct list_sp*
list_sp_create()
{
  struct list_sp* new_list_sp = malloc(sizeof(struct list_sp));
  if (! new_list_sp)
    return NULL;
  new_list_sp->head = NULL;
  new_list_sp->tail = NULL;
  new_list_sp->size = 0;
  return new_list_sp;
}

/**
   Note: duplicates internal copy of key
   @param key Must be non-NULL
 */
struct list_sp_item*
list_sp_add(struct list_sp* target, const char* key, void* data)
{
  assert(key);

  struct list_sp_item* new_item = malloc(sizeof(struct list_sp_item));
  if (! new_item)
    return NULL;

  new_item->key  = strdup(key);
  if (!new_item->key)
    return NULL;

  new_item->data = data;
  new_item->next = NULL;
  if (target->size == 0)
  {
    target->head = new_item;
    target->tail = new_item;
  }
  else
  {
    target->tail->next = new_item;
  }
  target->tail = new_item;
  target->size++;
  return new_item;
}

bool
list_sp_set(struct list_sp* target, const char* key,
            void* value, void** old_value)
{
  for (struct list_sp_item* item = target->head; item;
       item = item->next)
  {
    if (strcmp(key, item->key) == 0)
    {
      *old_value = (char*) item->data;
      item->data = value;
      return true;
    }
  }
  return false;
}

bool
list_sp_pop(struct list_sp* target, char** key, void** data)
{
  if (target->size == 0)
    return false;

  *key  = target->head->key;
  *data = target->head->data;

  if (target->size == 1)
  {
    free(target->head);
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return data;
  }

  struct list_sp_item* delendum = target->head;
  target->head = target->head->next;
  free(delendum);
  target->size--;
  return data;
}

/**
   Caller is responsible for data as well -
          it was provided by the user
 */
bool
list_sp_remove(struct list_sp* target, const char* key, void** data)
{
  if (target->size == 0)
    return false;

  // Special handling if we match on the first item:
  if (strcmp(key, target->head->key) == 0)
  {
    struct list_sp_item* old_head = target->head;
    target->head = old_head->next;
    if (target->tail == old_head)
      target->tail = NULL;
    // De-const these- list_sp is no longer responsible for them
    *data = (char*) old_head->data;
    free((char*) old_head->key);
    free(old_head);
    target->size--;
    return true;
  }

  for (struct list_sp_item* item = target->head; item->next;
       item = item->next)
  {
    if (strcmp(key, item->next->key) == 0)
    {
      struct list_sp_item* old_item = item->next;
      if (target->tail == old_item)
        target->tail = item;
      item->next = old_item->next;
      // De-const these- list_sp is no longer responsible for them
      free((char*) old_item->key);
      *data = (char*) old_item->data;
      free(old_item);
      target->size--;
      return true;
    }
  }
  return false;
}

/**
   Frees keys and values.
*/
void
list_sp_destroy(struct list_sp* target)
{
  struct list_sp_item* item = target->head;
  while (item)
  {
    struct list_sp_item* next = item->next;
    free((char*) item->key);
    free((char*) item->data);
    free(item);
    item = next;
  }
  free(target);
}

/**
   @param format specifies the output format for the data items
 */
void
list_sp_dump(const char* format, const struct list_sp* target)
{
  printf("[");
  for (struct list_sp_item* item = target->head; item;
      item = item->next)
  {
    printf("(%s,", item->key);
    if (strcmp(format, "%s") == 0)
      printf(format, item->data);
    else if (strcmp(format, "%i") == 0)
      printf(format, *((int*) (item->data)));
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

void
list_sp_dumpkeys(const struct list_sp* target)
{
  printf("[");
  for (struct list_sp_item* item = target->head; item;
       item = item->next)
  {
    printf("(%s)", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

size_t
list_sp_keys_string_length(const struct list_sp* target)
{
  size_t result = 0;
  for (struct list_sp_item* item = target->head; item;
       item = item->next)
    result += strlen(item->key);
  return result;
}

size_t
list_sp_keys_tostring(char* result,
                      const struct list_sp* target)
{
  char* p = result;
  for (struct list_sp_item* item = target->head; item;
       item = item->next)
    p += sprintf(p, "%s ", item->key);
  return (size_t)(p - result);
}

void list_sp_free(struct list_sp* target)
{
  list_sp_free_callback(target, NULL);
}

void list_sp_free_callback(struct list_sp* target,
                           void (*callback)(char*, void*))
{
  struct list_sp_item* item = target->head;
  while (item)
  {
    if (callback != NULL)
      callback(item->key, item->data);

    struct list_sp_item* next = item->next;
    free(item);
    item = next;
  }
  free(target);
}


/** Dump list_sp to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage
 */
size_t list_sp_tostring(char* str, size_t size,
                     const char* format, const struct list_sp* target)
{
  size_t error = size+1;
  char* ptr = str;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  for (struct list_sp_item* item = target->head;
       item && ptr-str < size;
       item = item->next)
    ptr = strkey_append_pair(ptr, item->key, format, item->data,
                             item->next != NULL);
  sprintf(ptr, "]");

  return (size_t)(ptr-str);
}
