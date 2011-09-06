
/**
   Extremely simple singly-linked list of longs.
   Everything is IN the list, no external pointers.
 */

#ifndef LNLIST_H
#define LNLIST_H

#include <stdbool.h>

struct lnlist_item
{
  long data;
  struct lnlist_item* next;
};

struct lnlist
{
  struct lnlist_item* head;
  struct lnlist_item* tail;
  int size;
};

void lnlist_init(struct lnlist* target);
struct lnlist* lnlist_create(void);

/**
   Add to the tail of the lnlist.
*/
struct lnlist_item* lnlist_add(struct lnlist* target, long data);
#define lnlist_push(target, data) lnlist_add(target, data)

long lnlist_search(struct lnlist* target, long data);

bool lnlist_remove(struct lnlist* target, long data);
bool lnlist_erase(struct lnlist* target, long data, size_t n);

/**
   Remove and return the tail data item.
*/
long lnlist_pop(struct lnlist* target);

/**
   Return the head data item.
*/
long lnlist_peek(struct lnlist* target);

/**
   Remove and return the head data item.
*/
long lnlist_poll(struct lnlist* target);

struct lnlist_item *
lnlist_ordered_insert(struct lnlist* target, long data);

bool lnlist_contains(struct lnlist* target, long data);

void lnlist_dump(struct lnlist* target);

int lnlist_tostring(char* str, size_t size,
                    struct lnlist* target);

void lnlist_free(struct lnlist* target);

#endif
