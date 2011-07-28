/*
 * ltable.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef LTABLE_H
#define LTABLE_H

#include <stdbool.h>

#include "llist.h"

struct ltable
{
  struct llist** array;
  int capacity;
  int size;
};

struct ltable* ltable_init(struct ltable *table, int capacity);

struct ltable* ltable_create(int capacity);

bool  ltable_add(struct ltable *table, long key, void* data);

void* ltable_search(struct ltable* table, long key);

bool ltable_contains(struct ltable* table, long key);

void* ltable_remove(struct ltable* table, long key);

void  ltable_dump(char* format, struct ltable* target);

int ltable_tostring(char* str, size_t size,
                    char* format, struct ltable* target);

void ltable_dumpkeys(struct ltable* target);

#endif
