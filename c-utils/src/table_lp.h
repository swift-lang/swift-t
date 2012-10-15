
/*
 * table_lp.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 *
 * Table mapping long to void pointer
 */

#ifndef TABLE_LP_H
#define TABLE_LP_H

#include <stdbool.h>

#include "list_lp.h"

struct table_lp
{
  struct list_lp* array;
  int capacity;
  int size;
};

bool table_lp_init(struct table_lp *table, int capacity);

struct table_lp* table_lp_create(int capacity);

bool table_lp_add(struct table_lp *table, long key, void* data);

void* table_lp_search(struct table_lp* table, long key);

bool table_lp_contains(struct table_lp* table, long key);

bool table_lp_move(struct table_lp* table,
                   long key_old, long key_new);

void* table_lp_remove(struct table_lp* table, long key);

void table_lp_destroy(struct table_lp* target);

void table_lp_clear(struct table_lp* target);

void table_lp_delete(struct table_lp* target);

void table_lp_release(struct table_lp* target);

void table_lp_dump(char* format, struct table_lp* target);

int table_lp_tostring(char* str, size_t size,
                    char* format, struct table_lp* target);

void table_lp_dumpkeys(struct table_lp* target);

#endif
