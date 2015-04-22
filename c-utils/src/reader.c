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

/**
 * reader.c
 *
 *  Created on: May 9, 2011
 *      Author: wozniak
 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#include "src/table_lp.h"
#include "src/reader.h"

/**
   Map from entry ids to entries
*/
static struct table_lp table;

static int unique = 0;

#define LINE_MAX 1024

struct entry
{
  int id;
  int size;
  char* data;
  int position;
  /** current line number */
  int number;
  /** copy of current line */
  char line[LINE_MAX];
};

/**
   @return true unless memory could not be allocated
 */
bool
reader_init()
{
  bool result = table_lp_init(&table, 128);
  return result;
}

/**
   Read the file into memory
   @return The non-negative id or -1 on error
 */
long
reader_read(char* path)
{
  struct stat stats;
  int error = stat(path, &stats);
  if (error)
    return -1;
  int length = (int)stats.st_size;

  char* data = malloc((size_t)length);

  int total = 0;

  FILE* file = fopen(path, "r");
  if (!file)
    return -1;

  while (total < length)
  {
    int chunk  = length-total;
    int actual = (int)fread(data+total, 1, (size_t)chunk, file);
    total += actual;
  }

  struct entry* e = malloc(sizeof(struct entry));
  e->id = unique++;

  e->size = total;
  e->data = data;
  e->position = 0;
  e->number = 1;
  table_lp_add(&table, e->id, e);

  return e->id;
}

/**
     @return Pointer to next string or NULL on end of data
*/
reader_line
reader_next(long id)
{
  reader_line result = {0};

  struct entry* e;
  struct entry** ee = &e;
  void** v = (void**) ee;
  table_lp_search(&table, id, v);
  result.number = e->number;
  if (!e)
    return result;

  // Internal error:
  assert(e->id == id);

  // Loop until we have a non-empty line
  while (true)
  {
    // Check for end of data:
    if (e->position >= e->size)
      return result;

    // Copy next line, without leading spaces, into returned line
    int p = e->position;
    while (e->data[p] == ' ')
      p++;
    int q = p;
    while (e->data[q] != '\n' && q < e->size)
      q++;
    q++;
    e->position = q;
    memcpy(e->line, e->data+p, (size_t)(q-p-1));
    e->line[q-p-1] = '\0';

    // Cut off everything after a comment character
    char* comment = strchr(e->line, '#');
    if (comment)
      *comment = '\0';

    // Delete trailing whitespace
    int length = (int)strlen(e->line);
    for (int i = length-1; i >= 0; i--)
      if (e->line[i] == ' ')
        e->line[i] = '\0';
      else
        break;

    if (strlen(e->line) > 0)
      break;
    e->number++;
  }

  result.number = e->number;
  result.line= &(e->line[0]);
  e->number++;
  return result;
}

/** @return true iff successfully removed */
bool
reader_free(long id)
{
  struct entry* e;
  struct entry** ee = &e;
  void** v = (void**) ee;
  if (!table_lp_remove(&table, id, v))
    return false;

  free(e->data);
  free(e);

  return true;
}

void reader_finalize()
{
  if (table.size != 0)
    puts("reader: table not empty!");
}
