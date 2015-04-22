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
#include <string.h>

#include "list_i.h"

void
list_i_init(struct list_i* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct list_i*
list_i_create()
{
  struct list_i* new_list_i = malloc(sizeof(struct list_i));
  if (! new_list_i)
    return NULL;
  list_i_init(new_list_i);
  return new_list_i;
}

int
list_i_size(struct list_i* target)
{
  return target->size;
}

/**
   Add to the tail of the list_i.
   @return The new list_i_item.
*/
struct list_i_item*
list_i_add(struct list_i* target, int data)
{
  struct list_i_item* new_item = malloc(sizeof(struct list_i_item));
  if (! new_item)
    return NULL;
  
  new_item->data = data;
  list_i_add_item(target, new_item);
  return new_item;
}

/**
   Parse a string with integers separated by spaces.
   Add all integers found to the returned new list_i.
   Leading or trailing spaces are ok.
*/
struct list_i*
list_i_parse(char* s)
{
  struct list_i* result = list_i_create();
  char* p = s;

  int   n;
  int   tmp;
  bool  good = true;

  int   r;
  while (good)
  {
    r = sscanf(p, "%i%n", &tmp, &n);
    p += n;
    if (r == 1)
      list_i_add(result, tmp);
    if (*p == ' ')
      p++;
    else
      good = false;
  }
  return result;
}

/**
   Remove and return the tail data item.
   This is expensive: singly linked list_i.
   @return -1 if the list is empty.
*/
int
list_i_poll(struct list_i* target)
{
  int data;
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

  struct list_i_item* item;
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
   Return the head data item.
   @return -1 if the list is empty.
*/
int
list_i_peek(struct list_i* target)
{
  if (target->size == 0)
    return -1;
  return target->head->data;
}

/**
   Return and remove the head data item.
   @return The data or -1 if list is empty.
 */
int
list_i_pop(struct list_i* target)
{ 
  struct list_i_item* delendum = list_i_pop_item(target);
  int data = delendum->data;
  free(delendum);
  return data;
}

struct list_i_item*
list_i_ordered_insert(struct list_i* target, int data)
{
  struct list_i_item* new_item = malloc(sizeof(struct list_i_item));
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
    struct list_i_item* item = target->head;
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

static inline struct list_i_item*
create_item(int data)
{
  struct list_i_item* item = malloc(sizeof(struct list_i_item));
   if (! item)
     return NULL;
   item->data = data;
   item->next = NULL;
   return item;
}

/**
   Inserts into ordered list if data does not already exist
   @return The new item or NULL if data already existed
 */
struct list_i_item*
list_i_unique_insert(struct list_i* target, int data)
{
  struct list_i_item* new_item = NULL;
  if (target->size == 0)
  {
    new_item = create_item(data);
    target->head = new_item;
    target->tail = new_item;
  }
  else
  {
    struct list_i_item* item = target->head;
    // Are we the new head?
    if (data < item->data)
    {
      new_item = create_item(data);
      new_item->next = target->head;
      target->head   = new_item;
    }
    else if (data == item->data)
    {
      // The head is a duplicate of data
      return NULL;
    }
    else
    {
      do
      {
        // Are we inserting at the end of the list?
        if (item->next == NULL)
        {
          new_item = create_item(data);
          item->next   = new_item;
          target->tail = new_item;
          break;
        }
        else
        {
          // Insert just before the next item
          if (data < item->next->data)
          {
            new_item = create_item(data);
            new_item->next = item->next;
            item->next = new_item;
            break;
          }
          else if (data == item->next->data)
            // Found a duplicate- do nothing
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
struct list_i_item*
list_i_ordered_insertdata(struct list_i* target, int data)
{
  struct list_i_item* new_item = malloc(sizeof(struct list_i_item));
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
    struct list_i_item* item = target->head;
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

int
list_i_random(struct list_i* target)
{
  int i;
  // printf("%s %i \n", "list size: ", target->size);

  if (target->size == 0)
    return -1;

  int p = rand() % target->size;
  struct list_i_item* item = target->head;
  for (i = 0; i < p; i++)
    item = item->next;

  return item->data;
}

/**
 */
bool
list_i_contains(struct list_i* target, int data)
{
  struct list_i_item* item;
  for (item = target->head;
       item; item = item->next)
    if (item->data == data)
      return true;
  return false;
}

/**
   @return An equal data int or -1 if not found.
*/
int
list_i_search(struct list_i* target, int data)
{
  for (struct list_i_item* item = target->head;
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
list_i_remove(struct list_i* target, int data)
{
  bool result = false;

  // NOTE_FI(data);

  struct list_i_item* item = target->head;
  // Are we removing the head?
  if (data == item->data)
  {
    struct list_i_item* next = item->next;
    free(item);
    target->head = next;
    if (target->tail == next)
      target->tail = NULL;
    target->size--;
    result = true;
  }
  else
  {
    // Are we removing the item after this item?
    while (item->next)
    {
      if (data == item->next->data)
      {
        struct list_i_item* nextnext = item->next->next;
        if (target->tail == item->next)
          target->tail = nextnext;
        free(item->next);
        item->next = nextnext;
        target->size--;
        result = true;
        break;
      }
      item = item->next;
    }
  }
  // DONE;
  return result;
}

/**
   Print all ints using printf.
 */
void
list_i_printf(struct list_i* target)
{
  struct list_i_item* item;

  printf("[");
  for (item = target->head; item; item = item->next)
  {
    printf("%i", item->data);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/**
   Allocate and return string containing ints in this list_i
   Returns pointer to allocated output location, 12*size.
 */
char*
list_i_serialize(struct list_i* target)
{
  char* result = malloc(12 * (size_t)(target->size) * sizeof(char));
  char* p = result;
  struct list_i_item* item;

  for (item = target->head; item; item = item->next)
  {
    p += sprintf(p, "%i", item->data);
    if (item->next)
      p += sprintf(p, " ");
  }
  return result;
}

/**
   Empty this list
 */
void
list_i_clear(struct list_i* target)
{
  struct list_i_item* item = target->head;
  struct list_i_item* next_item;
  while (item)
  {
    next_item = item->next;
    free(item);
    item = next_item;
  }

  // Reinitialize list
  list_i_init(target);
}

/**
   Free this list.
 */
void
list_i_free(struct list_i* target)
{
  list_i_clear(target);
  free(target);
}

/**
   Memory is allocated in result unless count==0
   @return true unless we could not allocate memory
 */
bool
list_i_toints(struct list_i* target, int** result, int* count)
{
  assert(target != NULL);

  if (target->size == 0)
  {
    *count = 0;
    return true;
  }

  *result = malloc((size_t)target->size * sizeof(int));
  if (!*result)
    return false;

  int i = 0;
  for (struct list_i_item* item = target->head; item;
       item = item->next)
    (*result)[i++] = item->data;

  *count = target->size;
  return true;
}

/*
 *
** Dump list_i to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage

int list_i_tostring(char* str, size_t size,
                  char* format, struct list_i* target)
{
  int               error = size+1;
  char*             ptr   = str;
  struct list_i_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  char* s = (char*) malloc(sizeof(char)*INLIST_MAX_DATUM);

  for (item = target->head; item; item = item->next,
         item && ptr-str < size)
  {
    int   r = snprintf(s, INLIST_MAX_DATUM, format, item->data);
    if (r > INLIST_MAX_DATUM)
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
