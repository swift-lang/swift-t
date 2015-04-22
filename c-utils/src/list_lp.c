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
 * list_lp.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/list_lp.h"

void
list_lp_init(struct list_lp* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct list_lp*
list_lp_create()
{
  struct list_lp* new_list_lp = malloc(sizeof(struct list_lp));
  if (! new_list_lp)
    return NULL;
  list_lp_init(new_list_lp);
  return new_list_lp;
}

struct list_lp_item*
list_lp_add(struct list_lp* target, int64_t key, void* data)
{
  struct list_lp_item* item = malloc(sizeof(struct list_lp_item));
  if (! item)
    return NULL;
  item->key  = key;
  item->data = data;
  item->next = NULL;
  list_lp_add_item(target, item);
  return item;
}

void
list_lp_add_item(struct list_lp* target, struct list_lp_item* item)
{
  if (target->size == 0)
  {
    target->head = item;
    target->tail = item;
  }
  else
  {
    target->tail->next = item;
  }
  target->tail = item;
  target->size++;
}

/**
   Insert into list so that keys are in order from smallest at head
   to largest at tail.
*/
struct list_lp_item*
list_lp_ordered_insert(struct list_lp* target, int64_t key, void* data)
{
  struct list_lp_item* new_item = malloc(sizeof(struct list_lp_item));
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
    struct list_lp_item* item = target->head;
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
   @return NULL iff the key/data pair are in the list.
   Could optimize to only malloc if insertion point is found.
*/
struct list_lp_item*
list_lp_ordered_insertdata(struct list_lp* target,
                         int64_t key, void* data,
                         bool (*cmp)(void*,void*))
{
  struct list_lp_item* new_item = malloc(sizeof(struct list_lp_item));
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
    struct list_lp_item* item = target->head;
    // Are we the new head?
    if (key < target->head->key)
    {
      new_item->next = target->head;
      target->head   = new_item;
    }
    // Are we equal to the head?
    else if (key == target->head->key &&
             cmp(data, target->head->data))
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
              cmp(data, item->next->data))
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
   This is expensive: singly linked list.
*/
void*
list_lp_pop(struct list_lp* target)
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

  struct list_lp_item* item;
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
list_lp_poll(struct list_lp* target)
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

  struct list_lp_item* delendum = target->head;
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
list_lp_get(struct list_lp* target, int i)
{
  struct list_lp_item* item;
  int j = 0;
  for (item = target->head; item; item = item->next, j++)
    if (i == j)
      return (item->data);
  return NULL;
}

void*
list_lp_search(struct list_lp* target, int64_t key)
{
  for (struct list_lp_item* item = target->head; item;
       item = item->next)
    if (key == item->key)
      return (item->data);
  return NULL;
}

/**
   Removes the list_lp_item from the list.
   frees the list_lp_item
   @return The removed item or NULL if not found.
*/
void*
list_lp_remove(struct list_lp* target, int64_t key)
{
  struct list_lp_item* item = list_lp_remove_item(target, key);
  if (item == NULL)
    return NULL;
  void* result = item->data;
  free(item);
  return result;
}

struct list_lp_item*
list_lp_remove_item(struct list_lp* target, int64_t key)
{
  struct list_lp_item* result;
  if (target->size == 0)
    return NULL;
  if (target->head->key == key)
  {
    result = target->head;
    target->head = result->next;
    target->size--;
    return result;
  }
  for (result = target->head; result->next; result = result->next)
    if (result->next->key == key)
    {
      struct list_lp_item* delendum = result->next;
      if (result->next == target->tail)
        target->tail = result;
      result->next = result->next->next;
      target->size--;
      return delendum;
    }
  return NULL;
}

/**
   Free this list but not its data.
*/
void
list_lp_free(struct list_lp* target)
{
  struct list_lp_item* item = target->head;
  while (item)
  {
    struct list_lp_item* next = item->next;
    free(item);
    item = next;
  }
  free(target);
}

/**
   Free this list and its data.
*/
void
list_lp_destroy(struct list_lp* target)
{
  list_lp_delete(target);
  free(target);
}

void list_lp_free_callback(struct list_lp* target,
                           void (*callback)(int64_t, void*))
{
  list_lp_clear_callback(target, callback);
  free(target);
}

void
list_lp_clear(struct list_lp* target)
{
  list_lp_clear_callback(target, NULL);
}

void
list_lp_clear_callback(struct list_lp* target,
                       void (*callback)(int64_t, void*))
{
  struct list_lp_item* item = target->head;
  while (item)
  {
    struct list_lp_item* next = item->next;
    if (callback != NULL)
      callback(item->key, item->data);
    free(item);
    item = next;
  }

  // Reset to original state
  list_lp_init(target);
}

void
list_lp_delete(struct list_lp* target)
{
  struct list_lp_item* item = target->head;
  while (item)
  {
    struct list_lp_item* next = item->next;
    free(item->data);
    free(item);
    item = next;
  }
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

/** format specifies the output format for the data items
 */
void
list_lp_dump(char* format, struct list_lp* target)
{
  struct list_lp_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%"PRId64",", item->key);
    if (strcmp(format, "%s") == 0)
      printf(format, item->data);
    else if (strcmp(format, "%"PRId64"") == 0)
      printf(format, *((long*) (item->data)));
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/** Just dump the keys.
 */
void
list_lp_dumpkeys(struct list_lp* target)
{
  struct list_lp_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%"PRId64")", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/** Dump the keys in hex.
 */
void
list_lp_xdumpkeys(struct list_lp* target)
{
  struct list_lp_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%"PRIx64")", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}


static char*
append_pair(char* ptr, struct list_lp_item* item, char* s)
{
  ptr += sprintf(ptr, "(%"PRId64",", item->key);
  ptr += sprintf(ptr, "%s)", s);

  if (item->next)
    ptr += sprintf(ptr, ",");
  return ptr;
}

/**
   @param f The output function for the data.
*/
void list_lp_output(char* (*f)(void*), struct list_lp* target)
{
  struct list_lp_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%"PRId64",", item->key);
    printf("%s", f(item->data));
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("] \n");
}

/** Dump list_lp to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage
 */
size_t list_lp_tostring(char* str, size_t size,
                     char* format, struct list_lp* target)
{
  size_t error = size+1;
  char* ptr   = str;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  for (struct list_lp_item* item = target->head; item && ptr-str < size;
       item = item->next)
  {
    char* s;
    int r = asprintf(&s, format, item->data);
    if ((ptr-str) + 10 + r + 4 < size)
      ptr = append_pair(ptr, item, s);
    else
      return error;
    free(s);
  }
  ptr += sprintf(ptr, "]");

  return (size_t)(ptr-str);
}

#ifdef DEBUG_LLIST

int
main()
{
  int i;
  char s[200];
  char* d1 = malloc(50*sizeof(char));
  strcpy(d1, "okey-dokey");
  char* d2 = malloc(50*sizeof(char));
  strcpy(d2, "okey-dokey30");

  struct list_lp* list = list_lp_create();

  list_lp_ordered_insert(list, 30, d2);
  list_lp_ordered_insert(list, 12, d1);
  list_lp_remove(list, 30);
  i = list_lp_tostring(s, 200, "%s", list);
  printf("1: %s \n", s);

  list_lp_ordered_insert(list, 31, "okey-dokey31");
  //
  list_lp_ordered_insert(list, 32, "okey-dokey32");

  i = list_lp_tostring(s, 200, "%s", list);
  printf("2: %s \n", s);

  list_lp_remove(list, 12);
  list_lp_ordered_insert(list, 33, "okey-dokey33");
  // list_lp_remove(list, 30);

  list_lp_ordered_insert(list, 20, "okey-dokey20");

  i = list_lp_tostring(s, 200, "%s", list);
  printf("%s \n", s);
}

#endif
