/*
 * llist.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/util/llist.h"

void
llist_init(struct llist* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct llist*
llist_create()
{
  struct llist* new_llist = malloc(sizeof(struct llist));
  if (! new_llist)
    return NULL;
  llist_init(new_llist);
  return new_llist;
}

struct llist_item*
llist_add(struct llist* target, long key, void* data)
{
  struct llist_item* new_item = malloc(sizeof(struct llist_item));
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
    target->tail->next = new_item;
  }
  target->tail = new_item;
  target->size++;
  return new_item;
}

/**
   Insert into list so that keys are in order from smallest at head
   to largest at tail.
*/
struct llist_item*
llist_ordered_insert(struct llist* target, long key, void* data)
{
  struct llist_item* new_item = malloc(sizeof(struct llist_item));
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
    struct llist_item* item = target->head;
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
struct llist_item*
llist_ordered_insertdata(struct llist* target,
                         long key, void* data,
                         bool (*cmp)(void*,void*))
{
  struct llist_item* new_item = malloc(sizeof(struct llist_item));
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
    struct llist_item* item = target->head;
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
llist_pop(struct llist* target)
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

  struct llist_item* item;
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
llist_poll(struct llist* target)
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

  struct llist_item* delendum = target->head;
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
llist_get(struct llist* target, int i)
{
  struct llist_item* item;
  int j = 0;
  for (item = target->head; item; item = item->next, j++)
    if (i == j)
      return (item->data);
  return NULL;
}

void*
llist_search(struct llist* target, long key)
{
 struct llist_item* item;
  for (item = target->head; item; item = item->next)
    if (key == item->key)
      return (item->data);
  return NULL;
}

/**
   Removes the llist_item from the list.
   frees the llist_item and the data pointer.
   @return The removed item or NULL if not found.
*/
void*
llist_remove(struct llist* target, long key)
{
  struct llist_item* item;
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
      struct llist_item* delendum = item->next;
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
llist_free(struct llist* target)
{
  struct llist_item* item = target->head;
  while (item)
  {
    struct llist_item* next = item->next;
    free(item);
    item = next;
  }
  free(target);
}

/**
   Free this list and its data.
*/
void
llist_destroy(struct llist* target)
{
  struct llist_item* item = target->head;
  while (item)
  {
    struct llist_item* next = item->next;
    free(item->data);
    free(item);
    item = next;
  }
  free(target);
}

/** format specifies the output format for the data items
 */
void
llist_dump(char* format, struct llist* target)
{
  struct llist_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%li,", item->key);
    if (strcmp(format, "%s") == 0)
      printf(format, item->data);
    else if (strcmp(format, "%li") == 0)
      printf(format, *((long*) (item->data)));
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/** Just dump the long keys.
 */
void
llist_dumpkeys(struct llist* target)
{
  struct llist_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%li)", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/** Dump the long keys in hex.
 */
void
llist_xdumpkeys(struct llist* target)
{
  struct llist_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%lx)", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}


static char*
append_pair(char* ptr, struct llist_item* item, char* s)
{
  ptr += sprintf(ptr, "(%li,", item->key);
  ptr += sprintf(ptr, "%s)", s);

  if (item->next)
    ptr += sprintf(ptr, ",");
  return ptr;
}

/**
   @param f The output function for the data.
*/
void llist_output(char* (*f)(void*), struct llist* target)
{
  struct llist_item* item;
  printf("[");
  for (item = target->head;
       item; item = item->next)
  {
    printf("(%li,", item->key);
    printf("%s", f(item->data));
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("] \n");
}

/** Dump llist to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage
 */
int llist_tostring(char* str, size_t size,
                   char* format, struct llist* target)
{
  int               error = size+1;
  char*             ptr   = str;
  struct llist_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  char* s = (char*) malloc(sizeof(char)*LLIST_MAX_DATUM);

  for (item = target->head; item && ptr-str < size;
       item = item->next)

  {
    int   r = snprintf(s, LLIST_MAX_DATUM, format, item->data);
    if (r > LLIST_MAX_DATUM)
      return size+1;
    if ((ptr-str) + 10 + r + 4 < size)
      ptr = append_pair(ptr, item, s);
    else
      return error;
  }
  ptr += sprintf(ptr, "]");

  free(s);
  return (ptr-str);
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

  struct llist* list = llist_create();

  llist_ordered_insert(list, 30, d2);
  llist_ordered_insert(list, 12, d1);
  llist_remove(list, 30);
  i = llist_tostring(s, 200, "%s", list);
  printf("1: %s \n", s);

  llist_ordered_insert(list, 31, "okey-dokey31");
  //
  llist_ordered_insert(list, 32, "okey-dokey32");

  i = llist_tostring(s, 200, "%s", list);
  printf("2: %s \n", s);

  llist_remove(list, 12);
  llist_ordered_insert(list, 33, "okey-dokey33");
  // llist_remove(list, 30);

  llist_ordered_insert(list, 20, "okey-dokey20");

  i = llist_tostring(s, 200, "%s", list);
  printf("%s \n", s);
}

#endif
