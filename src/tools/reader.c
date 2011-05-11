/*
 * reader.c
 *
 *  Created on: May 9, 2011
 *      Author: wozniak
 */

#include <sys/stat.h>

#include <ltable.h>

#include "src/tools/reader.h"

static struct ltable table;

static int unique = 0;

struct entry
{
  int id;
  int size;
  char* data;
  char* current;
};

int
reader_init()
{
  ltable_init(&ltable, 128);
}

int
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
    int actual = fread((*data)+total, 1, chunk, file);
    total += actual;
  }

  struct entry* e = malloc(sizeof(struct entry));
  e->id = unique++;
  e->size = total;
  e->data = data;
  e->current = data;

  ltable_add(&table, e->id, e);

  return e->id;
}

/** @return true iff successfully removed */
bool reader_free(int id);
{
  struct entry* e = (struct entry*) ltable_remove(&table, id);
  if (!e)
    return false;

  free(e->data);
  free(e);

  return true;
}
