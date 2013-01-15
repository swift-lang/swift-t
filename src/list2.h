
/*
 * list2.h
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 *
 *  Doubly-linked list
 */

#ifndef LIST2_H
#define LIST2_H

#include <stdbool.h>

struct list2_item
{
  void* data;
  struct list2_item* prev;
  struct list2_item* next;
};

struct list2
{
  struct list2_item* head;
  struct list2_item* tail;
  int size;
};


void list2_init(struct list2* target);

struct list2* list2_create(void);

struct list2_item* list2_add(struct list2* target, void* data);

void* list2_pop(struct list2* target);

void list2_remove_item(struct list2* target, struct list2_item* item);

#define list2_size(L) (L->size)

#endif
