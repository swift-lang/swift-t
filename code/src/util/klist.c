
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/util/klist.h"

extern struct klist*
klist_create()
{
  // NOTE_F;
  struct klist* new_klist = malloc(sizeof(struct klist));
  if (! new_klist)
    return NULL;
  new_klist->head = NULL;
  new_klist->tail = NULL;
  new_klist->size = 0;
  return new_klist;
}

struct klist_item*
klist_add(struct klist* target, char* key, void* data)
{
  // NOTE_FS(key);

  struct klist_item* new_item = malloc(sizeof(struct klist_item));
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
   Frees keys and values.
*/
void
klist_destroy(struct klist* target)
{
  struct klist_item* item = target->head;
  while (item)
  {
    struct klist_item* next = item->next;
    free(item->key);
    free(item->data);
    free(item);
    item = next;
  }
  free(target);
}

/**
   @param format specifies the output format for the data items
 */
void
klist_dump(char* format, struct klist* target)
{
  struct klist_item* item;

  printf("[");
  for (item = target->head; item; item = item->next)
  {
    printf("(%s,", item->key);
    if (strcmp(format, "%s") == 0)
      printf(format, item->data);
    else if (strcmp(format, "%i") == 0)
      printf(format, *((int*) (item->data)));
    printf(")");
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

void
klist_dumpkeys(struct klist* target)
{
  struct klist_item* item;

  printf("[");
  for (item = target->head; item; item = item->next)
  {
    printf("(%s)", item->key);
    if (item->next)
      printf(",");
  }
  printf("]\n");
}

void klist_free(struct klist* target)
{
  struct klist_item* item;
  // NOTE_F;

  // klist_dumpkeys(target);

  item = target->head;
  while (item)
  {
    struct klist_item* next = item->next;
    free(item);
    item = next;
  }

  free(target);
}

static char*
append_pair(char* ptr, struct klist_item* item,
            char* format, void* data)
{
  ptr += sprintf(ptr, "(%s,", item->key);
  ptr += sprintf(ptr, "%s)", (char*) data);

  if (item->next)
    ptr += sprintf(ptr, ",");
  return ptr;
}

/** Dump klist to string a la snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    returns int greater than size if size limits are exceeded
            indicating result is garbage
 */
int klist_tostring(char* str, size_t size,
                   char* format, struct klist* target)
{
  int               error = size+1;
  char*             ptr   = str;
  struct klist_item* item;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  for (item = target->head; item && ptr-str < size;
       item = item->next)
  {
    ptr = append_pair(ptr, item, format, item->data);
  }
  sprintf(ptr, "]");

  return (ptr-str);
}

#ifdef DEBUG_KLIST

int
main()
{
  struct klist* L = klist_create();

  klist_add(L, "key", "value");

  klist_dump("%s", L);


}

#endif
