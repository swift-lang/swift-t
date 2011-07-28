
/**
   Extremely simple singly-linked list of longs.
   Everything is IN the list, no external pointers.
 */

#ifndef LONGLIST_H
#define LONGLIST_H

#include <stdbool.h>
#include <stdlib.h>

struct longlist_item
{
  long data;
  struct longlist_item* next;
};

struct longlist
{
  struct longlist_item* head;
  struct longlist_item* tail;
  int size;
};

void longlist_init(struct longlist* target);

struct longlist* longlist_create(void);

struct longlist_item* longlist_add(struct longlist* target, int data);
#define longlist_push(target, data) longlist_add(target, data)

struct longlist* longlist_parse(char* s);

int longlist_search(struct longlist* target, int data);

int longlist_random(struct longlist* target);

bool longlist_remove(struct longlist* target, int data);

int longlist_pop(struct longlist* target);

int longlist_peek(struct longlist* target);

int longlist_poll(struct longlist* target);

struct longlist_item* longlist_ordered_insert(struct longlist* target,
                                          int data);

struct longlist_item* longlist_unique_insert(struct longlist* target,
                                         int data);

bool longlist_contains(struct longlist* target, int data);

void longlist_printf(struct longlist* target);

/*
int longlist_tostring(char* str, size_t size,
struct longlist* target); */

char* longlist_serialize(struct longlist* target);

void longlist_free(struct longlist* target);

bool longlist_toints(struct longlist* target, int** result, int* count);

#endif
