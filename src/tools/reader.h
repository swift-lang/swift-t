/*
 * reader.h
 *
 *  Created on: May 9, 2011
 *      Author: wozniak
 */

#ifndef READER_H
#define READER_H

#include <stdbool.h>

typedef struct
{
  int number;
  char* line;
} reader_line;

bool reader_init();

long reader_read(char* file);

reader_line reader_next(long id);

bool reader_free(long id);

void reader_finalize();

#endif
