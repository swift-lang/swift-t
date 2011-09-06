
/**
   Extremely simple singly-linked list of ints.
   Everything is IN the list, no external pointers.
 */

#ifndef INLIST_H
#define INLIST_H

#include <stdbool.h>
#include <stdlib.h>

struct inlist_item
{
  int data;
  struct inlist_item* next;
};

struct inlist
{
  struct inlist_item* head;
  struct inlist_item* tail;
  int size;
};

void inlist_init(struct inlist* target);

struct inlist* inlist_create(void);

struct inlist_item* inlist_add(struct inlist* target, int data);
#define inlist_push(target, data) inlist_add(target, data)

struct inlist* inlist_parse(char* s);

int inlist_search(struct inlist* target, int data);

int inlist_random(struct inlist* target);

bool inlist_remove(struct inlist* target, int data);

int inlist_pop(struct inlist* target);

int inlist_peek(struct inlist* target);

int inlist_poll(struct inlist* target);

struct inlist_item* inlist_ordered_insert(struct inlist* target,
                                          int data);

struct inlist_item* inlist_unique_insert(struct inlist* target,
                                         int data);

bool inlist_contains(struct inlist* target, int data);

void inlist_printf(struct inlist* target);

/*
int inlist_tostring(char* str, size_t size,
struct inlist* target); */

char* inlist_serialize(struct inlist* target);

void inlist_free(struct inlist* target);

bool inlist_toints(struct inlist* target, int** result, int* count);

#endif
