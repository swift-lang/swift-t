
/*
 * ltable.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/util/ltable.h"

static int
hash_long(long key, int table_size)
{
  return (key % table_size);
}

struct ltable*
ltable_init(struct ltable *table, int capacity)
{
  table->size     = 0;
  table->capacity = capacity;

  table->array =
      (struct llist**) malloc(sizeof(struct llist*) * capacity);
  if (!table->array)
  {
    free(table);
    return NULL;
  }

  for (int i = 0; i < capacity; i++)
  {
    struct llist* new_llist = llist_create();
    if (! new_llist)
    {
      for (i--; i >= 0; i--)
        free(table->array[i]);
      free(table);
      return NULL;
    }
    table->array[i] = new_llist;
  }
  return table;
}

struct ltable*
ltable_create(int capacity)
{
  struct ltable *new_table = NULL;

  new_table =
      (struct ltable*) malloc(sizeof(struct ltable));
  if (! new_table)
    return (NULL);

  ltable_init(new_table, capacity);

  return new_table;
}

void
ltable_finalize(struct ltable *old_table)
{
  // DOES NOT free LLISTS!
  free(old_table->array);
  free(old_table);
  return;
}

/**
   @return true on success, false on failure (memory)
 */
bool
ltable_add(struct ltable *table, long key, void* data)
{
  int index = 0;

  index = hash_long(key, table->capacity);

  struct llist_item* new_item =
      llist_add(table->array[index], key, data);

  if (! new_item)
    return false;

  table->size++;

  return true;
}

void*
ltable_search(struct ltable* table, long key)
{
  int index = hash_long(key, table->capacity);
  return llist_search(table->array[index], key);
}

bool
ltable_contains(struct ltable* table, long key)
{
  void* tmp = ltable_search(table, key);
  return (tmp != NULL);
}

void*
ltable_remove(struct ltable* table, long key)
{
  int index = hash_long(key, table->capacity);
  void* result = llist_remove(table->array[index], key);
  table->size--;
  return result;
}

/** format specifies the output format for the data items
 */
void
ltable_dump(char* format, struct ltable* target)
{
  int i;
  char s[200];
  printf("{\n");
  for (i = 0; i < target->capacity; i++)
  {
    if (target->array[i]->size > 0)
    {
      llist_tostring(s, 200, "%s", target->array[i]);
      printf("%i: %s \n", i, s);
    }
  }
  printf("}\n");
}

/** Dump llist to string a la snprintf()
        size must be greater than 2.
        format specifies the output format for the data items
        internally allocates O(size) memory
        returns int greater than size if size limits are exceeded
                indicating result is garbage
 */
int ltable_tostring(char* str, size_t size,
                    char* format, struct ltable* target)
{
  int   error = size+1;
  char* ptr   = str;
  int i;
  ptr += sprintf(str, "{\n");

  char* s = (char*) malloc(sizeof(char) * size);

  for (i = 0; i < target->size; i++)
  {
    int r = llist_tostring(s, size, format, target->array[i]);
    if ((ptr-str) + r + 2 < size)
      ptr += sprintf(ptr, "%s\n", s);
    else
      return error;
  }
  sprintf(ptr, "}\n");

  free(s);
  return (ptr-str);
}

#ifdef DEBUG_LTABLE

int
main()
{
  char s[200];
  struct ltable* table = ltable_create(30);

  ltable_add(table, 30, "hello30");
  ltable_add(table, 22, "hello22");
  ltable_add(table, 21, "hello21");
  ltable_add(table, 51, "hello51");

  // ltable_tostring(s, 200, "%s", table);
  ltable_dump("%s", table);

  ltable_remove(table, 22);

  ltable_dump("%s", table);
}

#endif
