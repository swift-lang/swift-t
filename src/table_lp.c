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
 * table_lp.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "table_lp.h"

static int
hash_long(cutil_long key, int N)
{
  return (int)(key % N);
}

bool
table_lp_init(struct table_lp* table, int capacity)
{
  assert(capacity > 0);
  table->size     = 0;
  table->capacity = capacity;

  table->array = malloc(sizeof(struct list_lp) * (size_t)capacity);
  if (!table->array)
  {
    free(table);
    return false;
  }

  for (int i = 0; i < capacity; i++)
    list_lp_init(&table->array[i]);
  return true;
}

struct table_lp*
table_lp_create(int capacity)
{
  struct table_lp *new_table = malloc(sizeof(struct table_lp));
  if (! new_table)
    return NULL;

  bool result = table_lp_init(new_table, capacity);
  if (!result)
  {
    free(new_table);
    return NULL;
  }

  return new_table;
}

void
table_lp_clear(struct table_lp* target)
{
  for (int i = 0; i < target->capacity; i++)
    list_lp_clear(&target->array[i]);
}

void
table_lp_delete(struct table_lp* target)
{
  for (int i = 0; i < target->capacity; i++)
    list_lp_delete(&target->array[i]);
}

void
table_lp_destroy(struct table_lp* target)
{
  table_lp_clear(target);
  free(target->array);
  free(target);
}

void
table_lp_release(struct table_lp* target)
{
  free(target->array);
}

/**
   @return true on success, false on failure (memory)
 */
bool
table_lp_add(struct table_lp *table, cutil_long key, void* data)
{
  int index = hash_long(key, table->capacity);

  struct list_lp_item* new_item =
      list_lp_add(&table->array[index], key, data);

  if (! new_item)
    return false;

  table->size++;

  return true;
}

void*
table_lp_search(struct table_lp* table, cutil_long key)
{
  int index = hash_long(key, table->capacity);
  return list_lp_search(&table->array[index], key);
}

bool
table_lp_contains(struct table_lp* table, cutil_long key)
{
  void* tmp = table_lp_search(table, key);
  return (tmp != NULL);
}

bool
table_lp_move(struct table_lp* table, cutil_long key_old, cutil_long key_new)
{
  int index_old = hash_long(key_old, table->capacity);
  int index_new = hash_long(key_new, table->capacity);
  struct list_lp_item* item =
      list_lp_remove_item(&table->array[index_old], key_old);
  if (item == NULL)
    return false;
  item->key = key_new;
  list_lp_add_item(&table->array[index_new], item);
  return true;
}

void*
table_lp_remove(struct table_lp* table, cutil_long key)
{
  int index = hash_long(key, table->capacity);
  void* result = list_lp_remove(&table->array[index], key);
  table->size--;
  return result;
}

/** format specifies the output format for the data items
 */
void
table_lp_dump(char* format, struct table_lp* target)
{
  int i;
  char s[200];
  printf("{\n");
  for (i = 0; i < target->capacity; i++)
  {
    if (target->array[i].size > 0)
    {
      list_lp_tostring(s, 200, "%s", &target->array[i]);
      printf("%i: %s \n", i, s);
    }
  }
  printf("}\n");
}

void
table_lp_dumpkeys(struct table_lp* target)
{
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
  {
    if (target->array[i].size > 0)
    {
      printf("%i: \n\t", i);
      list_lp_dumpkeys(&target->array[i]);
    }
  }
  printf("}\n");
}

/** Dump list_lp to string a la snprintf()
        size must be greater than 2.
        format specifies the output format for the data items
        internally allocates O(size) memory
        returns int greater than size if size limits are exceeded
                indicating result is garbage
 */
size_t table_lp_tostring(char* str, size_t size,
                    char* format, struct table_lp* target)
{
  size_t error = size+1;
  char* ptr   = str;
  int i;
  ptr += sprintf(str, "{\n");

  char* s = (char*) malloc(sizeof(char) * size);

  for (i = 0; i < target->size; i++)
  {
    size_t r = list_lp_tostring(s, size, format, &target->array[i]);
    if ((size_t)(ptr-str) + r + 2 < size)
    {
      int len = sprintf(ptr, "%s\n", s);
      assert(len > 0);
      ptr += (size_t)len;
    }
    else
    {
      return error;
    }
  }
  sprintf(ptr, "}\n");

  free(s);
  return (size_t)(ptr-str);
}

#ifdef DEBUG_LTABLE

int
main()
{
  char s[200];
  struct table_lp* table = table_lp_create(30);

  table_lp_add(table, 30, "hello30");
  table_lp_add(table, 22, "hello22");
  table_lp_add(table, 21, "hello21");
  table_lp_add(table, 51, "hello51");

  // table_lp_tostring(s, 200, "%s", table);
  table_lp_dump("%s", table);

  table_lp_remove(table, 22);

  table_lp_dump("%s", table);
}

#endif
