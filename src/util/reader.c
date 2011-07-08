/*
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

#include "src/util/ltable.h"

#include "src/util/reader.h"

/**
   Map from entry ids to entries
*/
static struct ltable table;

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
  void* result = ltable_init(&table, 128);
  return (result) ? true : false;
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
  int length = stats.st_size;

  char* data = malloc(length);

  int total = 0;

  FILE* file = fopen(path, "r");
  if (!file)
    return -1;

  while (total < length)
  {
    int chunk  = length-total;
    int actual = fread(data+total, 1, chunk, file);
    total += actual;
  }

  struct entry* e = malloc(sizeof(struct entry));
  e->id = unique++;

  e->size = total;
  e->data = data;
  e->position = 0;
  e->number = 1;
  ltable_add(&table, e->id, e);

  return e->id;
}

/**
     @return Pointer to next string or NULL on end of data
*/
reader_line
reader_next(long id)
{
  reader_line result = {0};

  struct entry* e = ltable_search(&table, id);
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
    memcpy(e->line, e->data+p, q-p-1);
    e->line[q-p-1] = '\0';

    // Cut off everything after a comment character
    char* comment = strchr(e->line, '#');
    if (comment)
      *comment = '\0';

    // Delete trailing whitespace
    int length = strlen(e->line);
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
  struct entry* e = ltable_remove(&table, id);
  if (!e)
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
