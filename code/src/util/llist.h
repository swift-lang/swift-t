/*
 * llist.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef LLIST_H
#define LLIST_H

#include <stdbool.h>

// Maximum size of a llist datum
#define LLIST_MAX_DATUM 100

struct llist_item
{
  long  key;
  void* data;
  struct llist_item* next;
};

struct llist
{
  struct llist_item* head;
  struct llist_item* tail;
  int size;
};

void llist_init(struct llist* target);

struct llist* llist_create(void);

struct llist_item* llist_add(struct llist* target,
                             long key, void* data);
#define llist_push(target, key, data) llist_add(target, key, data)

struct llist_item* llist_ordered_insert(struct llist* target,
                                        long key, void* data);
struct llist_item* llist_ordered_insertdata(struct llist* target,
                                            long key, void* data,
                                            bool (*cmp)(void*,void*));

void* llist_pop(struct llist* target);

void* llist_poll(struct llist* target);

void* llist_get(struct llist* target, int i);

void* llist_search(struct llist* target, long key);

/**
   Free this list but not its data.
*/
void llist_free(struct llist* target);

/**
   Removes the llist_item from the list.
   frees the llist_item.
   @return The removed item or NULL if not found.
*/
void* llist_remove(struct llist* target, long key);

//// Output methods...
void llist_dump(char* format, struct llist* target);
void llist_dumpkeys(struct llist* target);
void llist_xdumpkeys(struct llist* target);
void llist_output(char* (*f)(void*), struct llist* target);
int  llist_tostring(char* str, size_t size,
                  char* format, struct llist* target);

#endif
