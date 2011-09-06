
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/util/hashtable.h"
#include "src/util/jenkins-hash.h"

int
hash_string(char* data, int table_size)
{
  uint32_t p = 0;
  uint32_t q = 0;
  int length = strlen(data);
  bj_hashlittle2(data, length, &p, &q);

  int index = p % table_size;
  return index;
}

long
hash_string_long(char* data)
{
  uint32_t p, q;
  int length = strlen(data);
  bj_hashlittle2(data, length, &p, &q);

  long result = 0;

  result += p;

  // printf("hash_string %s -> %i \n", data, index);

  return result;
}

/**
   Warning: If this function fails, it may have leaked memory.
*/
bool
hashtable_init(struct hashtable* target, int capacity)
{
  target->size     = 0;
  target->capacity = capacity;

  target->array =
    (struct klist**) malloc(sizeof(struct klist*) * capacity);
  if (!target->array)
  {
    free(target);
    return false;
  }

  for (int i = 0; i < capacity; i++)
  {
    struct klist* new_klist = klist_create();
    if (! new_klist)
      return false;
    target->array[i] = new_klist;
  }
  return true;
}

struct hashtable*
hashtable_create(int capacity)
{
  struct hashtable* new_table =  malloc(sizeof(struct hashtable));
  if (! new_table)
    return NULL;

  bool result = hashtable_init(new_table, capacity);
  if (!result)
    return NULL;

  return new_table;
}

void
hashtable_free(struct hashtable* target)
{
  // NOTE_F;

  for (int i = 0; i < target->capacity; i++)
    klist_free(target->array[i]);

  free(target->array);
  free(target);

  return;
}

void
hashtable_destroy(struct hashtable* target)
{
  for (int i = 0; i < target->capacity; i++)
    klist_destroy(target->array[i]);

  free(target->array);
  free(target);
}

bool
hashtable_add(struct hashtable *table, char* key, void* data)
{
  int index = hash_string(key, table->capacity);

  struct klist_item* new_item =
    klist_add(table->array[index], key, data);

  if (! new_item)
    return false;

  table->size++;

  return true;
}

/**
   @return A pointer to the matching data or NULL if not found.
*/
void*
hashtable_search(struct hashtable* table, char* key)
{
  int index = hash_string(key, table->capacity);

  for (struct klist_item* item = table->array[index]->head; item;
       item = item->next)
    if (strcmp(key, item->key) == 0)
      return item->data;

  return NULL;
}

/** format specifies the output format for the data items
 */
void
hashtable_dump(char* format, struct hashtable* target)
{
  char s[200];
  int i;
  printf("{\n");
  for (i = 0; i < target->capacity; i++)
    if (target->array[i]->size > 0)
    {
      klist_tostring(s, 200, "%s", target->array[i]);
      printf("%i: %s \n", i, s);
    }
  printf("}\n");
}

void
hashtable_dumpkeys(struct hashtable* target)
{
  int i;
  printf("{\n");
  for (i = 0; i < target->capacity; i++)
    if (target->array[i]->size)
    {
      printf("%i:", i);
      klist_dumpkeys(target->array[i]);
    }
  printf("}\n");
}

/** Dump klist to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    internally allocates O(size) memory
    returns int greater than size if size limits are exceeded
            indicating result is garbage
*/
int hashtable_tostring(char* str, size_t size,
                       char* format, struct hashtable* target)
{
  int   error = size+1;
  char* ptr   = str;
  int i;
  ptr += sprintf(str, "{\n");

  char* s = (char*) malloc(sizeof(char) * size);

  for (i = 0; i < target->capacity; i++)
  {
    int r = klist_tostring(s, size, format, target->array[i]);
    if ((ptr-str) + r + 2 < size)
      ptr += sprintf(ptr, "%s\n", s);
    else
      return error;
  }
  sprintf(ptr, "}\n");

  free(s);
  return (ptr-str);
}

/**
   Debugging only.
*/

#ifdef DEBUG_HASHTABLE
int
main()
{
  struct hashtable* table = hashtable_create(11);

  hashtable_add(table, "key1", "val1");

  hashtable_dump("%s", table);

}
#endif
