
/**
   Extremely simple singly-linked list of longs.
   Everything is IN the list, no external pointers.
 */

#ifndef LIST_L_H
#define LIST_L_H

#include <stdbool.h>

struct list_l_item
{
  long data;
  struct list_l_item* next;
};

struct list_l
{
  struct list_l_item* head;
  struct list_l_item* tail;
  int size;
};

void list_l_init(struct list_l* target);
struct list_l* list_l_create(void);

/**
   Add to the tail of the list_l.
*/
struct list_l_item* list_l_add(struct list_l* target, long data);
#define list_l_push(target, data) list_l_add(target, data)

long list_l_search(struct list_l* target, long data);

bool list_l_remove(struct list_l* target, long data);
bool list_l_erase(struct list_l* target, long data, size_t n);

/**
   Remove and return the tail data item.
*/
long list_l_pop(struct list_l* target);

/**
   Return the head data item.
*/
long list_l_peek(struct list_l* target);

/**
   Remove and return the head data item.
*/
long list_l_poll(struct list_l* target);

struct list_l_item *
list_l_ordered_insert(struct list_l* target, long data);

struct list_l_item* list_l_unique_insert(struct list_l* target,
                                         long data);

bool list_l_contains(struct list_l* target, long data);

bool list_l_tolongs(const struct list_l* target,
                    long** result, int* count);

void list_l_dump(struct list_l* target);

int list_l_tostring(char* str, size_t size,
                    struct list_l* target);

void list_l_free(struct list_l* target);

#endif

