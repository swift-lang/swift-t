/*
 * readfile.c
 *
 *  Created on: May 11, 2011
 *      Author: wozniak
 */

#include <stdio.h>

#include "src/tools/reader.h"

int
main()
{
  reader_init();

  long id = reader_read("/dev/stdin");

  printf("id: %li\n", id);

  int i = 0;
  while (true)
  {
    char* line = reader_next(id);
    printf("line[%i]: [%s]\n", i++, line);
    if (!line)
      break;
  }

  bool b = reader_free(id);

  printf("freed: %i\n", b);
}
