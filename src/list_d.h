
/**
   Extremely simple singly-linked list of ints.
   Everything is IN the list, no external pointers.
 */

#ifndef INLIST_H
#define INLIST_H

#include <stdbool.h>
#include <stdlib.h>

struct list_d_item
{
  double data;
  struct list_d_item* next;
};

struct list_d
{
  struct list_d_item* head;
  struct list_d_item* tail;
  int size;
};

void list_d_init(struct list_d* target);

struct list_d* list_d_create(void);

int list_d_size(struct list_d* target);

struct list_d_item* list_d_add(struct list_d* target, double data);

/**
   Return true unless malloc fails
 */
bool list_d_push(struct list_d* target, double data);

struct list_d* list_d_parse(char* s);

double list_d_search(struct list_d* target, double data);

double list_d_random(struct list_d* target);

bool list_d_remove(struct list_d* target, double data);

double list_d_pop(struct list_d* target);

double list_d_peek(struct list_d* target);

double list_d_poll(struct list_d* target);

struct list_d_item* list_d_ordered_insert(struct list_d* target,
                                          double data);

struct list_d_item* list_d_unique_insert(struct list_d* target,
                                         double data);

bool list_d_contains(struct list_d* target, double data);

void list_d_printf(struct list_d* target);

/*
int list_d_tostring(char* str, size_t size,
struct list_d* target); */

char* list_d_serialize(struct list_d* target);

void list_d_clear(struct list_d* target);

void list_d_free(struct list_d* target);

bool list_d_todoubles(struct list_d* target,
                      double** result, int* count);

#endif
