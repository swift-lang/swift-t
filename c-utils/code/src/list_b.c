/*
 * Copyright 2014 University of Chicago and Argonne National Laboratory
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "list_b.h"

void
list_b_init(struct list_b* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct list_b*
list_b_create()
{
  struct list_b* new_list_b = malloc(sizeof(struct list_b));
  if (! new_list_b)
    return NULL;
  list_b_init(new_list_b);
  return new_list_b;
}


/** Alloc list node with space for data */
static struct list_b_item*
list_b_alloc_item(size_t data_len)
{
  return malloc(sizeof(struct list_b_item) + data_len);
}

// Create a new data item initialized with data and a NULL next pointer
static struct list_b_item*
list_b_new_item(const void *data, size_t data_len)
{
  struct list_b_item *item = list_b_alloc_item(data_len);
  if (item == NULL)
    return NULL;
  
  memcpy(item->data, data, data_len);
  item->data_len = data_len;
  item->next = NULL;
 
  return item;
}

static bool
list_b_match(struct list_b_item *item, const void *data, size_t data_len)
{
  return item->data_len == data_len &&
    memcmp(item->data, data, data_len) == 0;
}

static bool
list_b_cmp(const void *data1, size_t data_len1, 
          const void *data2, size_t data_len2)
{
  size_t min_len = data_len1 <= data_len2 ? data_len1 : data_len2; 

  int res = memcmp(data1, data2, min_len);
  if (res != 0)
  {
    return res;
  }
  if (data_len1 == data_len2)
  {
    return 0;
  } 
  else if (data_len1 < data_len2)
  {
    return -1; // Shorter goes first
  }
  else 
  {
    return 1;
  }
}

/**
   @return The new list_b_item.
*/
struct list_b_item*
list_b_add(struct list_b* target, const void *data, size_t data_len)
{
  struct list_b_item* new_item = list_b_new_item(data, data_len);
  if (! new_item)
    return NULL;

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

/**
   This is expensive: singly linked list_b.
   @return NULL if the list is empty.
*/
struct list_b_item *
list_b_poll(struct list_b* target)
{
  struct list_b_item *result;

  if (target->size == 0)
    return NULL;
  if (target->size == 1)
  {
    result = target->head;
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return result;
  }

  struct list_b_item* item;
  for (item = target->head; item->next->next;
       item = item->next);
  result = item->next;
  item->next = NULL;
  target->tail = item;
  target->size--;
  return result;
}

const void *
list_b_peek(struct list_b* target, size_t *data_len)
{
  if (target->size == 0)
    return NULL;

  *data_len = target->head->data_len;
  return target->head->data;
}

struct list_b_item *
list_b_pop(struct list_b* target)
{
  struct list_b_item *result;
  if (target->size == 0)
    return NULL;
  if (target->size == 1)
  {
    result = target->head;
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return result;
  }

  result = target->head;
  target->head = target->head->next;
  target->size--;
  return result;
}

struct list_b_item*
list_b_ordered_insert(struct list_b* target, const void *data, size_t data_len)
{
  struct list_b_item* new_item = list_b_new_item(data, data_len);
  if (! new_item)
    return NULL;

  if (target->size == 0)
  {
    target->head = new_item;
    target->tail = new_item;
  }
  else
  {
    struct list_b_item* item = target->head;
    // Are we the new head?
    if (list_b_cmp(data, data_len, item->data, item->data_len) < 0)
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
          if (list_b_cmp(data, data_len, item->data, item->data_len) < 0)
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

struct list_b_item*
list_b_unique_insert(struct list_b* target, const void *data, size_t data_len)
{
  struct list_b_item* new_item = list_b_new_item(data, data_len);
  if (! new_item)
    return NULL;

  if (target->size == 0)
  {
    target->head = new_item;
    target->tail = new_item;
  }
  else
  {
    struct list_b_item* item = target->head;
    // Are we the new head?
    if (list_b_cmp(data, data_len, item->data, item->data_len) < 0)
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
          if (list_b_cmp(data, data_len, item->data, item->data_len) < 0)
          {
            new_item->next = item->next;
            item->next = new_item;
            break;
          }
          if (data == item->next->data)
            return NULL;
        }
      } while ((item = item->next));
    }
  }
  target->size++;
  return new_item;
}

/**
 */
bool
list_b_contains(struct list_b* target, const void *data, size_t data_len)
{
  struct list_b_item* item;
  for (item = target->head;
       item; item = item->next)
    if (list_b_match(item, data, data_len))
      return true;
  return false;
}

/**
   @return True iff the data was matched
            and the item was freed.
*/
bool
list_b_remove(struct list_b* target, const void *data, size_t data_len)
{
  struct list_b_item* item = target->head;
  // Are we removing the head?
  if (list_b_match(item, data, data_len))
  {
    struct list_b_item* next = item->next;
    free(item);
    target->head = next;
    if (target->tail == next)
      target->tail = NULL;
    target->size--;
    return true;
  }
  do
  {
    // Are we removing the item after this item?
    if (list_b_match(item, data, data_len))
    {
      struct list_b_item* nextnext = item->next->next;
      if (target->tail == item->next)
        target->tail = nextnext;
      free(item->next);
      item->next = nextnext;
      target->size--;
      return true;
    }
  } while ((item = item->next));
  return false;
}

/**
   Free this list.
 */
void
list_b_free(struct list_b* target)
{
  struct list_b_item* item = target->head;
  struct list_b_item* next_item;
  while (item)
  {
    next_item = item->next;
    free(item);
    item = next_item;
  }
  free(target);
}

void
list_b_clear(struct list_b* target)
{
  struct list_b_item* item = target->head;
  struct list_b_item* next_item;
  while (item)
  {
    next_item = item->next;
    free(item);
    item = next_item;
  }

  target->head = target->tail = NULL;
  target->size = 0;
}
