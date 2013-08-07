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
#include "tools.h"

#include "table_ip.h"

int
hash_int(int key, int N)
{
  valgrind_assert_msg(N != 0, "hash_int(): N==0");
  return (key % N);
}

bool
table_ip_init(struct table_ip* target, int capacity)
{
  assert(capacity >= 0);
  if (! target)
    return false;

  target->size     = 0;
  target->capacity = capacity;

  target->array = malloc(sizeof(struct list_ip) * (size_t)capacity);
  if (!target->array)
    return false;

  for (int i = 0; i < capacity; i++)
    list_ip_init(&target->array[i]);

  return true;
}

struct table_ip*
table_ip_create(int capacity)
{
  struct table_ip *new_table = NULL;

  new_table = (struct table_ip*) malloc(sizeof(struct table_ip));

  table_ip_init(new_table, capacity);

  return new_table;
}

int
table_ip_size(struct table_ip* target)
{
  return target->size;
}

/**
   Add key/data pair to table.
   If key exists, do nothing and return false
*/
bool
table_ip_add(struct table_ip* target, int key, void* data)
{
  int index = hash_int(key, target->capacity);

  bool result = list_ip_add(&target->array[index], key, data);

  if (result)
    target->size++;

  return result;
}

/**
  @return The data or NULL if not found.
*/
void*
table_ip_search(const struct table_ip* target, int key)
{
  valgrind_assert_msg(key >= 0, "negative key: %i", key);
  int index = hash_int(key, target->capacity);
  return list_ip_search(&target->array[index], key);
}

void*
table_ip_remove(struct table_ip* target, int key)
{
  int index = hash_int(key, target->capacity);
  void* result = list_ip_remove(&target->array[index], key);
  if (result)
    target->size--;
  return result;
}

void
table_ip_free(struct table_ip* target)
{
  table_ip_free_callback(target, true, NULL);
}

void table_ip_free_callback(struct table_ip* target, bool free_root,
                    void (*callback)(int, void*))
{
  for (int i = 0; i < target->capacity; i++)
    list_ip_free_callback(&target->array[i], false, callback);
  free(target->array);
  if (free_root)
  {
    free(target);
  }
  else
  {
    target->array = NULL;
    target->size = 0;
  }
}

void
table_ip_destroy(struct table_ip* target)
{
  for (int i = 0; i < target->capacity; i++)
    list_ip_destroy(&target->array[i]);

  free(target->array);
  free(target);
}

/**
   @param format specifies the output format for the data items
 */
void
table_ip_dump(const char* format, const struct table_ip* target)
{
  char s[200];
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
  {
    if (target->array[i].size > 0)
    {
      list_ip_snprintf(s, 200, "%s", &target->array[i]);
      printf("%i: %s \n", i, s);
    }
  }
  printf("}\n");
}

/** Dump list_ip to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    internally allocates O(size) memory
    returns int greater than size if size limits are exceeded
            indicating result is garbage
*/
size_t table_ip_tostring(char* str, size_t size,
                      const char* format,
                      const struct table_ip* target)
{
  size_t error = size + 1;
  char* ptr = str;
  ptr += sprintf(str, "{\n");

  char* s = (char*) malloc(sizeof(char) * size);

  for (int i = 0; i < target->size; i++)
  {
    size_t r = list_ip_snprintf(s, size, format, &target->array[i]);
    if ((size_t)(ptr-str) + r + 2 < size)
      ptr += sprintf(ptr, "%s\n", s);
    else
      return error;
  }
  sprintf(ptr, "}\n");

  free(s);
  return (size_t)(ptr-str);
}

#ifdef DEBUG_TABLE_IP

int
main()
{
  char s[200];
  struct table_ip* table = table_ip_create(30);

  table_ip_add(table, 30, "hello30");
  table_ip_add(table, 22, "hello22");
  table_ip_add(table, 21, "hello21");
  table_ip_add(table, 51, "hello51");

  // table_ip_tostring(s, 200, "%s", table);
  table_ip_dump("%s", table);

  table_ip_remove(table, 22);

  table_ip_dump("%s", table);
}

#endif
