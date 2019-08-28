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
#include <stdlib.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "list_ip.h"

void
list_ip_init(struct list_ip* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct list_ip*
list_ip_create()
{
  struct list_ip* result = malloc(sizeof(struct list_ip));
  if (! result)
    return NULL;
  list_ip_init(result);
  return result;
}

void
list_ip_append(struct list_ip* target, int key, void* data)
{
  struct list_ip_item* new_item = malloc(sizeof(struct list_ip_item));
  assert(new_item);

  new_item->key  = key;
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
}

/**
   Set data
 */
bool
list_ip_set(struct list_ip* target, int key, void* data)
{
  struct list_ip_item* item;
  for (item = target->head; item; item = item->next)
    if (item->key == key)
    {
      item->data = data;
      return true;
    }
  return false;
}

/**
   Add key/data pair to table.
   If key exists, do nothing and return false
*/
bool
list_ip_add(struct list_ip* target, int key, void* data)
{
  if (list_ip_contains(target, key))
    return false;
  list_ip_append(target, key, data);
  return true;
}

bool
list_ip_contains(const struct list_ip* target, int key)
{
  for (struct list_ip_item* item = target->head; item;
       item = item->next)
    if (item->key == key)
      return true;
  return false;
}

bool
list_ip_matches(const struct list_ip* target,
                int (*cmp)(void*,void*), void* data)
{
  for (struct list_ip_item* item = target->head; item;
       item = item->next)
    if (cmp(item->data, data) == 0)
      return true;
  return false;
}

/**
   Insert into list so that keys are in order from smallest at head
   to largest at tail.
*/
struct list_ip_item*
list_ip_ordered_insert(struct list_ip* target, int key, void* data)
{
  struct list_ip_item* new_item = malloc(sizeof(struct list_ip_item));
  if (! new_item)
    return NULL;
  new_item->key  = key;
  new_item->data = data;
  new_item->next = NULL;

  if (target->size == 0)
  {
    target->head = new_item;
    target->tail = new_item;
  }
  else
  {
    struct list_ip_item* item = target->head;
    // Are we the new head?
    if (key < target->head->key)
    {
      new_item->next = target->head;
      target->head   = new_item;
    }
    else
    {
      do
      {
        // Are we inserting after this item?
        if (item->next == NULL)
        {
          item->next   = new_item;
          target->tail = new_item;
          break;
        }
        else
        {
          if (key < item->next->key)
          {
            new_item->next = item->next;
            item->next = new_item;
            break;
          }
        }
      } while ((item = item->next));
    }
  }
  target->size++;
  return new_item;
}

/**
   Does nothing if the key/data pair are in the list.
   Ordered smallest to largest.
   @return NULL iff the key/data pair are in the list.
   Could optimize to only malloc if insertion point is found.
*/
struct list_ip_item*
list_ip_ordered_insert_unique(struct list_ip* target,
                            int (*cmp)(void*,void*),
                            int key, void* data)
{
  struct list_ip_item* new_item = malloc(sizeof(struct list_ip_item));
  if (! new_item)
    return NULL;
  new_item->key  = key;
  new_item->data = data;
  new_item->next = NULL;

  if (target->size == 0)
  {
    target->head = new_item;
    target->tail = new_item;
  }
  else
  {
    struct list_ip_item* item = target->head;
    // Are we the new head?
    if (key < target->head->key)
    {
      new_item->next = target->head;
      target->head   = new_item;
    }
    // Are we equal to the head?
    else if (key == target->head->key &&
             cmp(data, target->head->data) == 0)
    {
      free(new_item);
      return NULL;
    }
    else
    {
      do
      {
        // Are we inserting after this item?
        if (item->next == NULL)
        {
          item->next   = new_item;
          target->tail = new_item;
          break;
        }
        else
        {
          if (key == item->next->key &&
              cmp(data, item->next->data) == 0)
          {
            free(new_item);
            return NULL;
          }
          if (key < item->next->key)
          {
            new_item->next = item->next;
            item->next = new_item;
            break;
          }
        }
      } while ((item = item->next));
    }
  }
  target->size++;
  return new_item;
}

/**
   Remove and return tail data of list
   This is expensive: singly linked list.
*/
void*
list_ip_poll(struct list_ip* target)
{
  // NOTE_F;

  void* data;
  if (target->size == 0)
    return NULL;
  if (target->size == 1)
  {
    data = target->head->data;
    free(target->head);
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return data;
  }

  struct list_ip_item* item;
  for (item = target->head; item->next->next;
       item = item->next);
  data = item->next->data;
  free(item->next);
  item->next = NULL;
  target->tail = item;
  target->size--;
  return data;
}

/**
   Remove and return head of list.
 */
void*
list_ip_pop(struct list_ip* target)
{
  void* data;
  if (target->size == 0)
    return NULL;
  if (target->size == 1)
  {
    data = target->head->data;
    free(target->head);
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return data;
  }

  struct list_ip_item* delendum = target->head;
  data = target->head->data;
  target->head = target->head->next;
  free(delendum);
  target->size--;
  return data;
}

/**
   Return data item i or NULL if i is out of bounds.
*/
void*
list_ip_get(struct list_ip* target, int i)
{
  struct list_ip_item* item;
  int j = 0;
  for (item = target->head; item; item = item->next, j++)
    if (i == j)
      return (item->data);
  return NULL;
}

/**
  @return The data or NULL if not found.
*/
void*
list_ip_search(struct list_ip* target, int key)
{
 struct list_ip_item* item;
  for (item = target->head; item; item = item->next)
    if (key == item->key)
      return (item->data);
  return NULL;
}

/**
   Removes the list_ip_item from the list.
   frees the list_ip_item.
   @return The data item or NULL if not found.
*/
void*
list_ip_remove(struct list_ip* target, int key)
{
  struct list_ip_item* item;
  void* data;
  if (target->size == 0)
    return false;
  if (target->head->key == key)
  {
    item = target->head;
    target->head = item->next;
    data = item->data;
    free(item);
    target->size--;
    return data;
  }
  for (item = target->head; item->next; item = item->next)
    if (item->next->key == key)
    {
      struct list_ip_item* delendum = item->next;
      data = delendum->data;
      if (item->next == target->tail)
        target->tail = item;
      item->next = item->next->next;
      free(delendum);
      target->size--;
      return data;
    }
  return NULL;
}

/**
   Free this list but not its data.
*/
void
list_ip_free(struct list_ip* target)
{
  list_ip_free_callback(target, true, NULL);
}

void list_ip_free_callback(struct list_ip* target, bool free_root,
                           void (*callback)(int, void*))
{
  struct list_ip_item* item = target->head;
  while (item)
  {
    struct list_ip_item* next = item->next;
    if (callback != NULL)
      callback(item->key, item->data);
    free(item);
    item = next;
  }
  if (free_root)
    free(target);
  else
    list_ip_init(target); // Reinitialize to consistent state
}

/**
   Free this list and its data.
*/
void
list_ip_destroy(struct list_ip* target)
{
  // NOTE_F;
  struct list_ip_item* item = target->head;
  while (item)
  {
    struct list_ip_item* next = item->next;
    free(item->data);
    free(item);
    item = next;
  }
  free(target);
  // DONE;
}

/**
   @param format Specifies the output format for the data items.
 */
void
list_ip_printf(char* format, struct list_ip* target)
{
  struct list_ip_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%i,", item->key);
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

/**
   @param format Specifies the output format for the data items.
 */
void
list_ip_fprintf(FILE* file, char* format, struct list_ip* target)
{
  struct list_ip_item* item;
  fprintf(file, "[");
  for (item = target->head;
       item; item = item->next)
  {
    fprintf(file, "(%i,", item->key);
    if (strcmp(format, "%s") == 0)
      fprintf(file, format, item->data);
    else if (strcmp(format, "%i") == 0)
      fprintf(file, format, *((int*) (item->data)));
    fprintf(file, ")");
    if (item->next)
      fprintf(file, ",");
  }
  fprintf(file, "]\n");
}

/**
   @param f Specifies the output format for the data items.
 */
void
list_ip_fdump(FILE* file, char* (f)(void*), struct list_ip* target)
{
  struct list_ip_item* item;
  fprintf(file, "[");
  for (item = target->head;
       item; item = item->next)
  {
    fprintf(file, "(%i,", item->key);
    fprintf(file, "%s", f(item->data));
    fprintf(file, ")");
    if (item->next)
      fprintf(file, ",");
  }
  fprintf(file, "]\n");
}

/**
   Just dump the int keys.
 */
void
list_ip_dumpkeys(struct list_ip* target)
{
  struct list_ip_item* item;
  printf("KEYS: [");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%i)", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/**
   Just dump the int keys.
 */
void
list_ip_fdumpkeys(FILE* file, struct list_ip* target)
{
  struct list_ip_item* item;
  fprintf(file, "KEYS: [");
  for (item = target->head;
       item; item = item->next)
  {
    fprintf(file, "(%i)", item->key);
    if (item->next)
      fprintf(file, ",");
  }
  fprintf(file, "]\n");
}

/**
   Dump the int keys in hex.
 */
void
list_ip_xdumpkeys(struct list_ip* target)
{
  struct list_ip_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%x)", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/**
   Just dump the data pointers.
   @return Allocated memory: 10 * target->size.
 */
char*
list_ip_serialize_ptrs(struct list_ip* target)
{
  char* result = malloc(30*(size_t)target->size);
  struct list_ip_item* item;
  char* p = result;
  p += sprintf(p, "PTRS: [");
  for (item = target->head;
       item; item = item->next)
  {
    p += sprintf(p, "%p", item->data);
    if (item->next)
      p += sprintf(p, " ");
  }
  sprintf(p, "]\n");
  return result;
}

static char*
append_pair(char* ptr, struct list_ip_item* item, char* s)
{
  ptr += sprintf(ptr, "(%i,", item->key);
  ptr += sprintf(ptr, "%s)", s);

  if (item->next)
    ptr += sprintf(ptr, ",");
  return ptr;
}

/**
   @param f The output function for the data.
*/
void
list_ip_dump(char* (*f)(void*), struct list_ip* target)
{
  struct list_ip_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%i,", item->key);
    printf("%s", f(item->data));
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("] \n");
}

/** Dump list_ip to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage
 */
size_t list_ip_snprintf(char* str, size_t size,
                     const char* format, const struct list_ip* target)
{
  size_t            error = size+1;
  char*             ptr   = str;
  struct list_ip_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  char* s = (char*) malloc(sizeof(char)*LIST_IP_MAX_DATUM);

  for (item = target->head; item && ptr-str < size;
       item = item->next)

  {
    int   r = snprintf(s, LIST_IP_MAX_DATUM, format, item->data);
    if (r > LIST_IP_MAX_DATUM)
      return size+1;
    if ((ptr-str) + 10 + r + 4 < size)
      ptr = append_pair(ptr, item, s);
    else
      return error;
  }
  ptr += sprintf(ptr, "]");

  free(s);
  return (size_t)(ptr-str);
}

/** Dump list_ip to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage
 */
/*
 WARNING: This has a couple bugs...
size_t list_ip_marshal(char* str, size_t size,
                  char* (f)(void*), struct list_ip* target)
{
  size_t            error = size+1;
  char*             ptr   = str;
  struct list_ip_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  for (item = target->head; item && ptr-str < size;
       item = item->next)
  {
    char* s = f(item->data);
    int   r = sprintf(s, "%s", s);
    if (r > LIST_IP_MAX_DATUM)
      return size+1;
    if ((ptr-str) + 10 + r + 4 < size)
      ptr = append_pair(ptr, item, s);
    else
      return error;
  }
  ptr += sprintf(ptr, "]");

  return (size_t)(ptr-str);
}
*/
