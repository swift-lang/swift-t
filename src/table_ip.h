/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

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

