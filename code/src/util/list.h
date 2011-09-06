/*
 * list.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef LIST_H
#define LIST_H

#include <stdbool.h>

/**
   Maximum size of a list datum (only used for debug printing)
*/
#define LIST_MAX_DATUM 1024

struct list_item
{
  void* data;
  struct list_item* next;
};

struct list
{
  struct list_item* head;
  struct list_item* tail;
  int size;
};

void list_init(struct list* target);

struct list* list_create(void);

/**
   Add to the tail of the list.
*/
struct list_item* list_add(struct list* target, void* data);
#define list_push(target, data) list_add(target, data)

struct list* list_parse(char* s);

/**
   Add this data if list_inspect does not find it.
*/
struct list_item* list_add_bytes(struct list* target,
                                 void* data, int n);

struct list_item* list_add_unique(struct list* target,
                                  int(*cmp)(void*,void*),
                                  void* data);

/**
   Compare data pointer addresses for match.
   @return An equal data pointer or NULL if not found.
*/
void* list_search(struct list* target, void* data);

/**
   Compare data contents for match.
   @return A pointer to an equivalent object or NULL if not found.
 */
void* list_inspect(struct list* target, void* data, size_t n);

/**
   True if the comparator finds a match for arg
*/
bool list_matches(struct list* target, int (*cmp)(void*,void*),
                  void* arg);

/**
   Removes only one item that points to given data.
   Does not free the item data.
   @return True iff the data pointer was matched
            and the item was freed.
*/
bool list_remove(struct list* target, void* data);

/**
   Does not free the item data.
   @return True iff the data content was matched by memcmp
            and the item was freed.
*/
bool list_erase(struct list* target, void* data, size_t n);

/**
   Return all elements from the list where f(data,arg).
*/
struct list* list_select(struct list* target,
                         int (*cmp)(void*,void*), void* arg);

/**
   Return the first data element from the list where f(data,arg).
*/
void* list_select_one(struct list* target, int (*cmp)(void*,void*),
                      void* arg);

/**
   Remove the element from the list where f(data,arg).
*/
bool list_remove_where(struct list* target,
                       int (*cmp)(void*,void*), void* arg);

/**
   Remove and return all elements from the list where
   cmp(data,arg) == 0.
   @return true if one or more items were deleted.
*/
struct list* list_pop_where(struct list* target,
                            int (*cmp)(void*,void*), void* arg);

/**
   Moves all items from tail into target structure.
*/
void list_transplant(struct list* target, struct list* segment);

void list_clear(struct list* target);

void list_clobber(struct list* target);

/**
   Remove and return the tail data item.
*/
void* list_pop(struct list* target);

/**
   Return the head data item.
*/
void* list_head(struct list* target);

/**
   Remove and return the head data item.
*/
void* list_poll(struct list* target);

/**
   Return a random data item.
*/
void* list_random(struct list* target);

struct list_item* list_ordered_insert(struct list* target,
                                      int (*cmp)(void*,void*), void* data);

bool list_contains(struct list* target,
                   int (*cmp)(void*,void*), void* data);

void list_output(char* (*f)(void*), struct list* target);

void list_printf(char* format, struct list* target);

int list_tostring(char* str, size_t size,
                  char* format, struct list* target);

/**
   Free this list but not its data.
*/
void list_free(struct list* target);

/**
   Free this list and its data.
*/
void list_destroy(struct list* target);

#endif
