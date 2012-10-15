
/*
 * list_lp.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef LIST_LP_H
#define LIST_LP_H

#include <stdbool.h>
#include <stddef.h>

struct list_lp_item
{
  long  key;
  void* data;
  struct list_lp_item* next;
};

struct list_lp
{
  struct list_lp_item* head;
  struct list_lp_item* tail;
  int size;
};

void list_lp_init(struct list_lp* target);

struct list_lp* list_lp_create(void);

struct list_lp_item* list_lp_add(struct list_lp* target,
                                 long key, void* data);
#define list_lp_push(target, key, data) list_lp_add(target, key, data)

void list_lp_add_item(struct list_lp* target,
                      struct list_lp_item* item);

struct list_lp_item* list_lp_ordered_insert(struct list_lp* target,
                                            long key, void* data);

struct list_lp_item* list_lp_ordered_insertdata(struct list_lp* target,
                                                long key, void* data,
                                                bool (*cmp)(void*,void*));

void* list_lp_pop(struct list_lp* target);

void* list_lp_poll(struct list_lp* target);

void* list_lp_get(struct list_lp* target, int i);

void* list_lp_search(struct list_lp* target, long key);

void list_lp_free(struct list_lp* target);

void* list_lp_remove(struct list_lp* target, long key);

struct list_lp_item* list_lp_remove_item(struct list_lp* target,
                                         long key);

void list_lp_destroy(struct list_lp* target);

void list_lp_clear(struct list_lp* target);

void list_lp_delete(struct list_lp* target);

//// Output methods...
void list_lp_dump(char* format, struct list_lp* target);
void list_lp_dumpkeys(struct list_lp* target);
void list_lp_xdumpkeys(struct list_lp* target);
void list_lp_output(char* (*f)(void*), struct list_lp* target);
int  list_lp_tostring(char* str, size_t size,
                      char* format, struct list_lp* target);

#endif
