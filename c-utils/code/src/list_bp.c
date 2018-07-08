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

#include "binkeys.h"
#include "list_bp.h"

extern struct list_bp*
list_bp_create()
{
  struct list_bp* new_list_bp = malloc(sizeof(struct list_bp));
  if (! new_list_bp)
    return NULL;
  new_list_bp->head = NULL;
  new_list_bp->tail = NULL;
  new_list_bp->size = 0;
  return new_list_bp;
}

/**
   Note: duplicates internal copy of key
   returns null on error.
   @param key Must be non-NULL
 */
struct list_bp_item*
list_bp_add(struct list_bp* target, const void* key, size_t key_len,
            void* data)
{
  assert(key);

  struct list_bp_item* new_item = malloc(sizeof(struct list_bp_item));
  if (! new_item)
    return NULL;

  new_item->key = malloc(key_len);
  if (!new_item->key)
    return NULL;
  memcpy(new_item->key, key, key_len);
  new_item->key_len = key_len;

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
list_bp_set(struct list_bp* target, const void* key, size_t key_len,
            void* value, void** old_value)
{
  for (struct list_bp_item* item = target->head; item;
       item = item->next)
  {
    if (list_bp_key_match(key, key_len, item))
    {
      *old_value = (char*) item->data;
      item->data = value;
      return true;
    }
  }
  return false;
}

bool
list_bp_pop(struct list_bp* target, void** key, size_t *key_len,
            void** data)
{
  if (target->size == 0)
    return false;

  *key  = target->head->key;
  *key_len = target->head->key_len;
  *data = target->head->data;

  if (target->size == 1)
  {
    free(target->head);
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return data;
  }

  struct list_bp_item* delendum = target->head;
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
list_bp_remove(struct list_bp* target, const void* key, size_t key_len,
               void** data)
{
  if (target->size == 0)
    return false;

  // Special handling if we match on the first item:
  if (list_bp_key_match(key, key_len, target->head))
  {
    struct list_bp_item* old_head = target->head;
    target->head = old_head->next;
    if (target->tail == old_head)
      target->tail = NULL;
    // De-const these- list_bp is no longer responsible for them
    *data = (char*) old_head->data;
    free((char*) old_head->key);
    free(old_head);
    target->size--;
    return true;
  }

  for (struct list_bp_item* item = target->head; item->next;
       item = item->next)
  {
    if (list_bp_key_match(key, key_len, item->next))
    {
      struct list_bp_item* old_item = item->next;
      if (target->tail == old_item)
        target->tail = item;
      item->next = old_item->next;
      // De-const these- list_bp is no longer responsible for them
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
list_bp_destroy(struct list_bp* target)
{
  struct list_bp_item* item = target->head;
  while (item)
  {
    struct list_bp_item* next = item->next;
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
list_bp_dump(const char* format, const struct list_bp* target)
{
  printf("[");
  for (struct list_bp_item* item = target->head; item;
      item = item->next)
  {
    printf("(");
    binkey_printf(item->key, item->key_len);
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
list_bp_dumpkeys(const struct list_bp* target)
{
  printf("[");
  for (struct list_bp_item* item = target->head; item;
       item = item->next)
  {
    printf("(");
    binkey_printf(item->key, item->key_len);
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

size_t
list_bp_keys_string_length(const struct list_bp* target)
{
  size_t result = 0;
  for (struct list_bp_item* item = target->head; item;
       item = item->next)
  {
    // Each byte is two hex digits in string repr.
    result += item->key_len * 2 + 1;
  }
  return result;
}

size_t
list_bp_keys_tostring(char* result,
                      const struct list_bp* target)
{
  char* p = result;
  for (struct list_bp_item* item = target->head; item;
       item = item->next)
  {
    p += binkey_sprintf(p, item->key, item->key_len);
    p[0] = ' ';
    p++;
  }
  return (size_t)(p - result);
}

void list_bp_free(struct list_bp* target)
{
  list_bp_free_callback(target, NULL);
}

void list_bp_free_callback(struct list_bp* target,
                           void (*callback)(void*, size_t, void*))
{
  struct list_bp_item* item = target->head;
  while (item)
  {
    if (callback != NULL)
      callback(item->key, item->key_len, item->data);

    struct list_bp_item* next = item->next;
    free(item);
    item = next;
  }
  free(target);
}

char*
bp_append_pair(char* ptr, const void *key, size_t key_len,
            const char* format, const void* data, bool next)
{
  ptr += sprintf(ptr, "(");
  ptr += binkey_sprintf(ptr, key, key_len);
  ptr += sprintf(ptr, ",");
  ptr += sprintf(ptr, "%s)", (char*) data);

  if (next)
    ptr += sprintf(ptr, ",");
  return ptr;
}

/** Dump list_bp to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage
 */
size_t list_bp_tostring(char* str, size_t size,
                     const char* format, const struct list_bp* target)
{
  size_t error = size+1;
  char* ptr = str;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  for (struct list_bp_item* item = target->head;
       item && ptr-str < size;
       item = item->next)
    ptr = bp_append_pair(ptr, item->key, item->key_len, format,
                          item->data, item->next != NULL);
  sprintf(ptr, "]");

  return (size_t)(ptr-str);
}
