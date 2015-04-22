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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "list_l.h"

void
list_l_init(struct list_l* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct list_l*
list_l_create()
{
  struct list_l* new_list_l = malloc(sizeof(struct list_l));
  if (! new_list_l)
    return NULL;
  list_l_init(new_list_l);
  return new_list_l;
}

/**
   @return The new list_l_item.
*/
struct list_l_item*
list_l_add(struct list_l* target, int64_t data)
{
  struct list_l_item* new_item = malloc(sizeof(struct list_l_item));
  if (! new_item)
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

/**
   This is expensive: singly linked list_l.
   @return -1 if the list is empty.
*/
int64_t
list_l_poll(struct list_l* target)
{
  int64_t data;
  if (target->size == 0)
    return -1;
  if (target->size == 1)
  {
    data = target->head->data;
    free(target->head);
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return data;
  }

  struct list_l_item* item;
  for (item = target->head; item->next->next;
       item = item->next);
  data = item->next->data;
  free(item->next);
  item->next = NULL;
  target->tail = item;
  target->size--;
  return data;
}

int64_t
list_l_peek(struct list_l* target)
{
  if (target->size == 0)
    return -1;
  return target->head->data;
}

int64_t
list_l_pop(struct list_l* target)
{
  int64_t data;
  if (target->size == 0)
    return -1;
  if (target->size == 1)
  {
    data = target->head->data;
    free(target->head);
    target->head = NULL;
    target->tail = NULL;
    target->size = 0;
    return data;
  }

  struct list_l_item* delendum = target->head;
  data = target->head->data;
  target->head = target->head->next;
  free(delendum);
  target->size--;
  return data;
}

struct list_l_item*
list_l_ordered_insert(struct list_l* target, int64_t data)
{
  struct list_l_item* new_item = malloc(sizeof(struct list_l_item));
  if (! new_item)
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
    struct list_l_item* item = target->head;
    // Are we the new head?
    if (data < item->data)
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
          if (data < item->next->data)
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

struct list_l_item*
list_l_unique_insert(struct list_l* target, int64_t data)
{
  struct list_l_item* new_item = malloc(sizeof(struct list_l_item));
  if (! new_item)
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
    struct list_l_item* item = target->head;
    // Are we the new head?
    if (data < item->data)
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
          if (data < item->next->data)
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
   Untested.
 */
struct list_l_item*
list_l_ordered_insertdata(struct list_l* target, int64_t data)
{
  struct list_l_item* new_item = malloc(sizeof(struct list_l_item));
  if (! new_item)
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
    struct list_l_item* item = target->head;
    // Are we the new head?
    if (data < item->data)
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
          if (data == item->next->data)
          {
            free(new_item);
            return NULL;
          }
          if (data < item->next->data)
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
 */
bool
list_l_contains(struct list_l* target, int64_t data)
{
  struct list_l_item* item;
  for (item = target->head;
       item; item = item->next)
    if (item->data == data)
      return true;
  return false;
}

/**
   @return An equal data int or -1 if not found.
*/
int64_t
list_l_search(struct list_l* target, int64_t data)
{
  struct list_l_item* item;
  for (item = target->head;
       item; item = item->next)
    if (item->data == data)
      return data;
  return -1;
}

/**
   @return True iff the data was matched
            and the item was freed.
*/
bool
list_l_remove(struct list_l* target, int64_t data)
{
  struct list_l_item* item = target->head;
  // Are we removing the head?
  if (data == item->data)
  {
    struct list_l_item* next = item->next;
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
    if (data == item->next->data)
    {
      struct list_l_item* nextnext = item->next->next;
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
   Memory is allocated in result unless count==0
   @return true unless we could not allocate memory
 */
bool
list_l_tolongs(const struct list_l* target, int64_t** result, int* count)
{
  if (target->size == 0)
  {
    *count = 0;
    return true;
  }

  *result = malloc((size_t)target->size * sizeof(int64_t));
  if (!*result)
    return false;

  int i = 0;
  for (struct list_l_item* item = target->head; item;
       item = item->next)
    (*result)[i++] = item->data;

  *count = target->size;
  return true;
}


/**
   @param format specifies the output format for the data items
 */
void
list_l_dump(struct list_l* target)
{
  struct list_l_item* item;

  printf("[");
  for (item = target->head; item; item = item->next)
  {
    printf("%"PRId64"", item->data);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/**
   Free this list.
 */
void
list_l_free(struct list_l* target)
{
  struct list_l_item* item = target->head;
  struct list_l_item* next_item;
  while (item)
  {
    next_item = item->next;
    free(item);
    item = next_item;
  }
  free(target);
}


#ifdef DEBUG_LNLIST

int
main()
{
  struct list_l* L = list_l_create();

  list_l_ordered_insert(L, 2);
  list_l_ordered_insert(L, 4);
  list_l_ordered_insert(L, 3);
  list_l_ordered_insert(L, 0);
  list_l_ordered_insert(L, 5);
  list_l_ordered_insert(L, 1);

  list_l_push(L, 8);

  list_l_dump(L);

  /*
  list_l_dump("%i", L);
  list_l_poll(L);
  list_l_dump("%i", L);
  list_l_pop(L);
  list_l_dump("%i", L);
  list_l_add(L, &seven);
  list_l_add(L, &six);
  list_l_dump("%i", L);
  */

}

#endif

/*
 *
** Dump list_l to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage

int list_l_tostring(char* str, size_t size,
                  char* format, struct list_l* target)
{
  int               error = size+1;
  char*             ptr   = str;
  struct list_l_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  char* s = (char*) malloc(sizeof(char)*LNLIST_MAX_DATUM);

  for (item = target->head; item; item = item->next,
         item && ptr-str < size)
  {
    int   r = snprintf(s, LNLIST_MAX_DATUM, format, item->data);
    if (r > LNLIST_MAX_DATUM)
      return size+1;
    if ((ptr-str) + strlen(item->data) + r + 4 < size)
      ptr = append_pair(ptr, item, s);
    else
      return error;
  }
  sprintf(ptr, "]");

  free(s);
  return (ptr-str);
}

*/
