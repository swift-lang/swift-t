
/*
  TABLE_IP : Map from Integers to Pointers
*/

#ifndef TABLE_IP_H
#define TABLE_IP_H

#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>

#include "list_ip.h"

struct table_ip
{
  struct list_ip* array;
  int capacity;
  int size;
};

int hash_int(int key, int N);

bool table_ip_init(struct table_ip* target, int capacity);

struct table_ip* table_ip_create(int capacity);

int table_ip_size(struct table_ip* target);

bool  table_ip_add(struct table_ip* target, int key, void* data);

void* table_ip_search(const struct table_ip* target, int key);

void* table_ip_remove(struct table_ip* target, int key);

void  table_ip_free(struct table_ip* target);

void table_ip_destroy(struct table_ip* target);

void  table_ip_dump(const char* format, const struct table_ip* target);

int table_ip_tostring(char* str, size_t size,
                      const char* format,
                      const struct table_ip* target);

#endif

