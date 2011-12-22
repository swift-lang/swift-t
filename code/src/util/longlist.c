
#include <stdio.h>
#include <string.h>

#include "longlist.h"

void
longlist_init(struct longlist* target)
{
  target->head = NULL;
  target->tail = NULL;
  target->size = 0;
}

struct longlist*
longlist_create()
{
  struct longlist* new_longlist = malloc(sizeof(struct longlist));
  if (! new_longlist)
    return NULL;
  longlist_init(new_longlist);
  return new_longlist;
}

/**
   Add to the tail of the longlist.
   @return The new longlist_item.
*/
struct longlist_item*
longlist_add(struct longlist* target, int data)
{
  struct longlist_item* new_item = malloc(sizeof(struct longlist_item));
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
   Parse a string with integers separated by spaces.
   Add all integers found to the returned new longlist.
   Leading or trailing spaces are ok.
*/
struct longlist*
longlist_parse(char* s)
{
  struct longlist* result = longlist_create();
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
      longlist_add(result, tmp);
    if (*p == ' ')
      p++;
    else
      good = false;
  }
  return result;
}

/**
   Remove and return the tail data item.
   This is expensive: singly linked longlist.
   @return -1 if the list is empty.
*/
int
longlist_pop(struct longlist* target)
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

  struct longlist_item* item;
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
longlist_peek(struct longlist* target)
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
longlist_poll(struct longlist* target)
{
  // NOTE_F;

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

  struct longlist_item* delendum = target->head;
  data = target->head->data;
  target->head = target->head->next;
  free(delendum);
  target->size--;
  return data;
}

struct longlist_item*
longlist_ordered_insert(struct longlist* target, int data)
{
  struct longlist_item* new_item = malloc(sizeof(struct longlist_item));
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
    struct longlist_item* item = target->head;
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
   Inserts into ordered list if data does not already exist
   @return The new item or NULL if data already existed
 */
struct longlist_item*
longlist_unique_insert(struct longlist* target, int data)
{
  struct longlist_item* new_item = malloc(sizeof(struct longlist_item));
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
    struct longlist_item* item = target->head;
    // Are we the new head?
    if (data < item->data)
    {
      new_item->next = target->head;
      target->head   = new_item;
    }
    else if (data == item->data)
    {
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
          if (data < item->next->data)
          {
            new_item->next = item->next;
            item->next = new_item;
            break;
          }
          else if (data == item->next->data)
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
struct longlist_item*
longlist_ordered_insertdata(struct longlist* target, int data)
{
  struct longlist_item* new_item = malloc(sizeof(struct longlist_item));
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
    struct longlist_item* item = target->head;
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
longlist_random(struct longlist* target)
{
  int i;
  // printf("%s %i \n", "list size: ", target->size);

  if (target->size == 0)
    return -1;

  int p = rand() % target->size;
  struct longlist_item* item = target->head;
  for (i = 0; i < p; i++)
    item = item->next;

  return item->data;
}

/**
 */
bool
longlist_contains(struct longlist* target, int data)
{
  struct longlist_item* item;
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
longlist_search(struct longlist* target, int data)
{
  for (struct longlist_item* item = target->head;
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
longlist_remove(struct longlist* target, int data)
{
  bool result = false;

  // NOTE_FI(data);

  struct longlist_item* item = target->head;
  // Are we removing the head?
  if (data == item->data)
  {
    struct longlist_item* next = item->next;
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
        struct longlist_item* nextnext = item->next->next;
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
longlist_printf(struct longlist* target)
{
  struct longlist_item* item;

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
   Allocate and return string containing ints in this longlist
   Returns pointer to allocated output location, 12*size.
 */
char*
longlist_serialize(struct longlist* target)
{
  char* result = malloc(12 * (target->size) * sizeof(char));
  char* p = result;
  struct longlist_item* item;

  for (item = target->head; item; item = item->next)
  {
    p += sprintf(p, "%li", item->data);
    if (item->next)
      p += sprintf(p, " ");
  }
  return result;
}

/**
   Free this list.
 */
void
longlist_free(struct longlist* target)
{
  // NOTE_F;
  struct longlist_item* item = target->head;
  struct longlist_item* next_item;
  while (item)
  {
    next_item = item->next;
    free(item);
    item = next_item;
  }
  free(target);
}

/**
   @return true unless we could not allocate memory
 */
bool
longlist_toints(struct longlist* target, int** result, int* count)
{
  *result = malloc(target->size * sizeof(int));
  if (!*result)
    return false;

  int i = 0;
  for (struct longlist_item* item = target->head; item;
       item = item->next)
    (*result)[i++] = item->data;

  *count = target->size;
  return true;
}

/*
 *
** Dump longlist to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage

int longlist_tostring(char* str, size_t size,
                  char* format, struct longlist* target)
{
  int               error = size+1;
  char*             ptr   = str;
  struct longlist_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  char* s = (char*) malloc(sizeof(char)*LONGLIST_MAX_DATUM);

  for (item = target->head; item; item = item->next,
         item && ptr-str < size)
  {
    int   r = snprintf(s, LONGLIST_MAX_DATUM, format, item->data);
    if (r > LONGLIST_MAX_DATUM)
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
