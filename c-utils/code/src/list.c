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
 * list.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "src/list.h"

void
list_init(struct list* target)
{
  assert(target);
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct list*
list_create()
{
  struct list* new_list = malloc(sizeof(struct list));
  if (! new_list)
    return NULL;
  list_init(new_list);
  return new_list;
}

/**
   @return The new list_item.
*/
struct list_item*
list_add(struct list* target, void* data)
{
  struct list_item* new_item = malloc(sizeof(struct list_item));
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
   Add this data if list_inspect does not find it.
*/
struct list_item*
list_add_one(struct list* target, void* data, size_t n)
{
  if (! list_inspect(target, data, n))
    return list_add(target, data);
  return NULL;
}

/**
   Add this pre-formed list_item to target.
   Convenience: sets item->next to NULL.
   @return The added item.
*/
struct list_item*
list_append(struct list* target, struct list_item* item)
{
  if (target->size == 0)
    target->head = item;
  else
    target->tail->next = item;
  target->tail       = item;
  item->next         = NULL;
  target->size++;
  return item;
}

struct list*
list_split_words(char* s)
{
  struct list* result = list_create();
  char* p = s;
  char* q;
  while (*p)
  {
    // Set p to start of word, q to end of word...
    while (*p == ' ' || *p == '\t')
      p++;
    if (!*p)
      break;
    q = p+1;
    while (! (*q == ' ' || *q == '\t' || *q == '\0'))
      q++;

    // Insert word into list...
    char* data = malloc((size_t)(q-p+2));
    strncpy(data, p, (size_t)(q-p));
    data[q-p] = '\0';
    list_add(result, data);

    // Step forward:
    p = q;
  }

  return result;
}

struct list*
list_split_lines(const char* s)
{
  struct list* result = list_create();
  const char* p = s;
  const char* q;
  while (*p)
  {
    // Set p to start of word, q to end of word...
    while (*p == '\n')
      p++;
    if (!*p)
      break;
    q = p+1;
    while (! (*q == '\n' || *q == '\0'))
      q++;

    // Insert line into list...
    char* data = malloc((size_t)(q-p+2));
    strncpy(data, p, (size_t)(q-p));
    data[q-p] = '\0';
    list_add(result, data);

    // Step forward:
    p = q;
  }

  return result;
}

/**
    Remove and return the tail data item.
   This is expensive: singly linked list.
*/
void*
list_pop(struct list* target)
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

  struct list_item* item;
  for (item = target->head; item->next->next;
       item = item->next);
  data = item->next->data;
  free(item->next);
  item->next = NULL;
  target->tail = item;
  target->size--;
  return data;
}

void*
list_head(struct list* target)
{
  if (target->size == 0)
    return NULL;
  return target->head->data;
}

/**
 */
void*
list_poll(struct list* target)
{
  // NOTE_FI(target->size);
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

  struct list_item* delendum = target->head;
  data = target->head->data;
  target->head = target->head->next;
  free(delendum);
  target->size--;
  return data;
}

void*
list_random(struct list* target)
{
  if (target->size == 0)
    return NULL;

  int p = rand() % target->size;
  struct list_item* item = target->head;
  for (int i = 0; i < p; i++)
    item = item->next;

  return item->data;
}

struct list_item*
list_ordered_insert(struct list* target,
                    int (*cmp)(void*,void*), void* data)
{
  // NOTE_F;
  struct list_item* new_item = malloc(sizeof(struct list_item));
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
    struct list_item* item = target->head;
    // Are we the new head?
    if (cmp(data, item->data) == -1)
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
          if (cmp(data, item->next->data) == -1)
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
   Untested.
 */
struct list_item*
list_ordered_insert_unique(struct list* target, int (*cmp)(void*,void*),
                           void* data)
{
  struct list_item* new_item = malloc(sizeof(struct list_item));
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
    struct list_item* item = target->head;
    // Are we the new head?
    if (cmp(data, item->data) == -1)
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
          int c = cmp(data, item->next->data);
          if (c == 0)
          {
            free(new_item);
            return NULL;
          }
          if (c == -1)
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

struct list_item*
list_add_unique(struct list* target,
                int (*cmp)(void*,void*), void* data)
{
  if (! list_contains(target, cmp, data))
    return list_add(target, data);
  return NULL;
}

/**
 */
bool
list_contains(struct list* target,
              int (*cmp)(void*,void*), void* data)
{
  struct list_item* item;
  for (item = target->head;
       item; item = item->next)
    if (cmp(item->data, data) == 0)
      return true;
  return false;
}

/**
   Compare data pointer addresses for match.
   @return An equal data pointer or NULL if not found.
*/
void*
list_search(struct list* target, void* data)
{
  struct list_item* item;
  for (item = target->head;
       item; item = item->next)
    if (item->data == data)
      return data;
  return NULL;
}

/**
   Compare data contents with memcmp for match.
   @return A pointer to an equivalent object or NULL if not found.
 */
void*
list_inspect(struct list* target, void* data, size_t n)
{
  struct list_item* item;
  for (item = target->head;
       item; item = item->next)
    if (memcmp(item->data, data, n) == 0)
      return item->data;
  return NULL;
}

bool
list_matches(struct list* target, int (*cmp)(void*,void*), void* arg)
{
  assert(target != NULL);

  for (struct list_item* item = target->head; item;
       item = item->next)
    if (cmp(item->data, arg) == 0)
      return true;

  return false;
}

/**
   Empty the list and free the data.
*/
void
list_clear(struct list* target)
{
  list_clear_callback(target, free);
}


void list_clear_callback(struct list* target, void (*callback)(void*))
{
  struct list_item* item = target->head;

  while (item)
  {
    struct list_item* next = item->next;
    if (callback != NULL)
      callback(item->data);
    free(item);
    item = next;
  }
  // Reset everything
  list_init(target);
}


/**
   Removes only one item that points to given data.
   Does not free the item data.
   @return True iff the data pointer was matched
            and the item was freed.
*/
bool
list_remove(struct list* target, void* data)
{
  if (target->size == 0)
    return false;

  struct list_item* item = target->head;

  if (data == item->data)
  {
    struct list_item* next = item->next;
    free(item);
    target->head = next;
    target->size--;
    if (target->size == 0)
      target->tail = NULL;
    return true;
  }

  while (item->next)
  {
    // Are we removing the item after this item?
    if (data == item->next->data)
    {
      struct list_item* nextnext = item->next->next;
      if (target->tail == item->next)
        target->tail = nextnext;
      free(item->next);
      item->next = nextnext;
      target->size--;
      return true;
    }
    item = item->next;
  }
  return false;
}

/**
   Return all elements from the list where cmp(data,arg) == 0.
*/
struct list*
list_select(struct list* target,
            int (*cmp)(void*,void*), void* arg)
{
  struct list* result = list_create();
  struct list_item* item;
  assert(target != NULL);


  for (item = target->head;
       item; item = item->next)
  {
    if (cmp(item->data, arg) == 0)
      list_add(result, item->data);
  }

  return result;
}

/**
   Return the first data element from the list where f(data,arg).
*/
void*
list_select_one(struct list* target,
                int (*cmp)(void*,void*), void* arg)
{
  assert(target != NULL);

  for (struct list_item* item = target->head; item;
       item = item->next)
    if (cmp(item->data, arg) == 0)
      return item->data;

  return NULL;
}

/**
   Remove the elements from the list where cmp(data,arg) == 0.
   @return true if one or more items were deleted.
*/
bool
list_remove_where(struct list* target,
                  int (*cmp)(void*,void*), void* arg)
{
  bool result = false;
  struct list_item* item;

  if (target->size == 0)
    return false;

  int old_size = target->size;

  // Establish next good item in list...
  struct list_item* good = NULL;
  for (item = target->head;
       item; item = item->next)
  {
    if (cmp(item->data, arg) != 0)
    {
      good = item;
      break;
    }
  }

  if (! good)
    // List should be empty
  {
    if (target->size > 0)
      result = true;
    list_clear(target);
    return result;
  }

  // Establish correct head...
  struct list_item* head = target->head;
  while (head && head != good)
  {
    struct list_item* next = head->next;
    free(head);
    target->size--;
    head = next;
  }
  target->head = good;

  // Now current points to the first valid item in the list.
  struct list_item* current = target->head;
  while (good != NULL)
  {
    // Move to a good item or NULL...
    struct list_item* item = good->next;
    good = NULL;
    while (item)
    {
      if (cmp(item->data, arg) != 0)
      {
        good = item;
        break;
      }
      item = item->next;
    }

    if (good == NULL)
      // No more good items were found
    {
      target->tail = current;
    }

    // Free items between current and good:

    struct list_item* link = current;
    current = current->next;
    while (current != good)
    {
      struct list_item* next = current->next;
      free(current);
      target->size--;
      current = next;
    }
    link->next = good;
  }

  if (target->size != old_size)
    return true;
  return false;
}


/**
   Remove and return all elements from the list where
   cmp(data,arg) == 0.
*/
struct list*
list_pop_where(struct list* target,
                  int (*cmp)(void*,void*), void* arg)
{
  struct list* result = list_create();
  struct list_item* item;

  if (target->size == 0)
    return result;

  // Establish next good item in list...
  struct list_item* good = NULL;
  for (item = target->head;
       item; item = item->next)
  {
    if (cmp(item->data, arg) != 0)
    {
      good = item;
      break;
    }
  }

  if (! good)
    // All elements should be moved
  {
    list_transplant(result, target);
    return result;
  }

  // Establish correct head...
  struct list_item* head = target->head;
  while (head && head != good)
  {
    struct list_item* next = head->next;
    list_append(result, head);
    target->size--;
    head = next;
  }
  target->head = good;

  // Now current points to the first valid item in the list.
  struct list_item* current = target->head;
  while (good != NULL)
  {
    // Move to a good item or NULL...
    struct list_item* item = good->next;
    good = NULL;
    while (item)
    {
      if (cmp(item->data, arg) != 0)
      {
        good = item;
        break;
      }
      item = item->next;
    }

    if (good == NULL)
      // No more good items were found
      target->tail = current;

    // if (good != NULL)
    //  printf("good: %i \n", *(int*) good->data);

    // Free items between current and good:
    struct list_item* link = current;
    current = current->next;
    while (current != good)
    {
      struct list_item* next = current->next;
      list_append(result, current);
      target->size--;
      current = next;
    }
    link->next = good;
  }

  return result;
}

/**
   Moves all items from segment into target structure.
*/
void
list_transplant(struct list* target, struct list* segment)
{
  if (target->size == 0)
  {
    target->head = segment->head;
    target->tail = segment->tail;
  }
  else
  {
    target->tail->next = segment->head;
    target->tail = segment->tail;
  }
  target->size += segment->size;

  segment->head = NULL;
  segment->tail = NULL;
  segment->size = 0;
}

/**
   Does not free the item data.
   @return True iff the data content was matched by memcmp
            and the item was freed.
*/
bool
list_erase(struct list* target, void* data, size_t n)
{
  struct list_item* item = target->head;
  // Are we removing the head?
  if (memcmp(data, item->data, n) == 0)
  {
    struct list_item* next = item->next;
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
    if (memcmp(data, item->next->data, n) == 0)
    {
      struct list_item* nextnext = item->next->next;
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
   Function specifies the output format for the data items
   Does not free return of f.
 */
void
list_output(char* (*f)(void*), struct list* target)
{
  struct list_item* item;

  printf("[");
  for (item = target->head; item; item = item->next)
  {
    printf("%s", f(item->data));
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/**
   format specifies the output format for the data items
 */
void
list_printf(char* format, struct list* target)
{
  printf("[");
  for (struct list_item* item = target->head; item;
       item = item->next)
  {
    if (strcmp(format, "%s") == 0)
      printf(format, item->data);
    else if (strcmp(format, "%i") == 0)
      printf(format, *((int*) (item->data)));
    else if (strcmp(format, "%li") == 0)
      printf(format, *((long*) (item->data)));
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/**
   Free this list but not its data.
*/
void
list_free(struct list* target)
{
  list_free_callback(target, NULL);
}

void list_free_callback(struct list* target, void (*callback)(void*))
{
  struct list_item* item = target->head;
  while (item)
  {
    struct list_item* next = item->next;
    if (callback != NULL)
      callback(item->data);
    free(item);
    item = next;
  }
  free(target);
}

/**
   Free this list and its data.
*/
void
list_destroy(struct list* target)
{
  struct list_item* item = target->head;
  while (item)
  {
    struct list_item* next = item->next;
    free(item->data);
    free(item);
    item = next;
  }
  free(target);
}

int
int_cmp(void* i1, void* i2)
{
  int j1 = *(int*) i1;
  int j2 = *(int*) i2;

  if (j1 > j2)
    return 1;
  else if (j1 < j2)
    return -1;
  else
    return 0;
}

/**
   Returns 0 iff i1 is divisible by i2.
*/
int
divides_cmp(void* i1, void* i2)
{
  int j1 = *(int*) i1;
  int j2 = *(int*) i2;

  return (j1 % j2);
}

#ifdef DEBUG_LIST



int
main()
{
  struct list* L = list_create();

  int zero  = 0;
  int one   = 1;
  int two   = 2;
  int three = 3;
  int four  = 4;
  int four2 = 4;
  int five  = 5;
  int six   = 6;
  int seven = 7;
  int eight = 8;

  list_ordered_insert(L, &two,   int_cmp);
  list_ordered_insert(L, &four,  int_cmp);
  list_ordered_insert(L, &three, int_cmp);
  list_ordered_insert(L, &three, int_cmp);
  list_ordered_insert(L, &three, int_cmp);
  list_ordered_insert(L, &three, int_cmp);
  list_ordered_insert(L, &zero,  int_cmp);
  list_ordered_insert(L, &four2, int_cmp);
  list_ordered_insert(L, &four2, int_cmp);
  list_ordered_insert(L, &five,  int_cmp);
  list_ordered_insert(L, &one,   int_cmp);

  list_push(L, &eight);

  list_dump("%i", L);
  printf("size: %i \n", L->size);

  // struct list* matches = list_select(L, int_cmp, &four);
  // list_remove_where(L, divides_cmp, &two);

  struct list* K = list_pop_where(L, divides_cmp, &two);

  list_dump("%i", L);
  printf("size: %i \n", L->size);

  list_dump("%i", K);
  printf("size: %i \n", L->size);

  /*
  list_dump("%i", L);
  list_poll(L);
  list_dump("%i", L);
  list_pop(L);
  list_dump("%i", L);
  list_add(L, &seven);
  list_add(L, &six);
  list_dump("%i", L);
  */

  // list_clobber(L);
  list_clear(L);
  printf("size(L): %i \n", L->size);

  list_clear(K);
  printf("size(K): %i \n", K->size);

  list_dump("%i", L);
}

#endif

/*

char* append_pair(char* ptr, struct list_item* item, char* s)
{
  ptr += sprintf(ptr, "(%s,", item->data);
  ptr += sprintf(ptr, "%s)", s);

  if (item->next)
    ptr += sprintf(ptr, ",");
  return ptr;
}

** Dump list to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage

int list_tostring(char* str, size_t size,
                  char* format, struct list* target)
{
  int               error = size+1;
  char*             ptr   = str;
  struct list_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  char* s = (char*) malloc(sizeof(char)*LIST_MAX_DATUM);

  for (item = target->head; item; item = item->next,
         item && ptr-str < size)
  {
    int   r = snprintf(s, LIST_MAX_DATUM, format, item->data);
    if (r > LIST_MAX_DATUM)
      return size+1;
    if ((ptr-str) + strlen(item->data) + r + 4 < size)
      ptr = append_pair(ptr, item, s);
    else
      return error;
  }
  sprintf(ptr, "]");

  // free(s);
  return (ptr-str);
}

*/
