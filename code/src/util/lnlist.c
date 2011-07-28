
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/util/lnlist.h"

void
lnlist_init(struct lnlist* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct lnlist*
lnlist_create()
{
  struct lnlist* new_lnlist = malloc(sizeof(struct lnlist));
  if (! new_lnlist)
    return NULL;
  lnlist_init(new_lnlist);
  return new_lnlist;
}

/**
   @return The new lnlist_item.
*/
struct lnlist_item*
lnlist_add(struct lnlist* target, long data)
{
  struct lnlist_item* new_item = malloc(sizeof(struct lnlist_item));
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
   This is expensive: singly linked lnlist.
   @return -1 if the list is empty.
*/
long
lnlist_pop(struct lnlist* target)
{
  long data;
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

  struct lnlist_item* item;
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
   @return -1 if the list is empty.
*/
long
lnlist_peek(struct lnlist* target)
{
  if (target->size == 0)
    return -1;
  return target->head->data;
}

/**
   Return and remove the head.
   @return The data or -1 if list is empty.
 */
long
lnlist_poll(struct lnlist* target)
{
  long data;
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

  struct lnlist_item* delendum = target->head;
  data = target->head->data;
  target->head = target->head->next;
  free(delendum);
  target->size--;
  return data;
}

struct lnlist_item*
lnlist_ordered_insert(struct lnlist* target, long data)
{
  struct lnlist_item* new_item = malloc(sizeof(struct lnlist_item));
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
    struct lnlist_item* item = target->head;
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

/**
   Untested.
 */
struct lnlist_item*
lnlist_ordered_insertdata(struct lnlist* target, long data)
{
  struct lnlist_item* new_item = malloc(sizeof(struct lnlist_item));
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
    struct lnlist_item* item = target->head;
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
lnlist_contains(struct lnlist* target, long data)
{
  struct lnlist_item* item;
  for (item = target->head;
       item; item = item->next)
    if (item->data == data)
      return true;
  return false;
}

/**
   @return An equal data int or -1 if not found.
*/
long
lnlist_search(struct lnlist* target, long data)
{
  struct lnlist_item* item;
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
lnlist_remove(struct lnlist* target, long data)
{
  struct lnlist_item* item = target->head;
  // Are we removing the head?
  if (data == item->data)
  {
    struct lnlist_item* next = item->next;
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
      struct lnlist_item* nextnext = item->next->next;
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
   @param format specifies the output format for the data items
 */
void
lnlist_dump(struct lnlist* target)
{
  struct lnlist_item* item;

  printf("[");
  for (item = target->head; item; item = item->next)
  {
    printf("%li", item->data);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

/**
   Free this list.
 */
void
lnlist_free(struct lnlist* target)
{
  struct lnlist_item* item = target->head;
  struct lnlist_item* next_item;
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
  struct lnlist* L = lnlist_create();

  lnlist_ordered_insert(L, 2);
  lnlist_ordered_insert(L, 4);
  lnlist_ordered_insert(L, 3);
  lnlist_ordered_insert(L, 0);
  lnlist_ordered_insert(L, 5);
  lnlist_ordered_insert(L, 1);

  lnlist_push(L, 8);

  lnlist_dump(L);

  /*
  lnlist_dump("%i", L);
  lnlist_poll(L);
  lnlist_dump("%i", L);
  lnlist_pop(L);
  lnlist_dump("%i", L);
  lnlist_add(L, &seven);
  lnlist_add(L, &six);
  lnlist_dump("%i", L);
  */

}

#endif

/*
 *
** Dump lnlist to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage

int lnlist_tostring(char* str, size_t size,
                  char* format, struct lnlist* target)
{
  int               error = size+1;
  char*             ptr   = str;
  struct lnlist_item* item;

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
