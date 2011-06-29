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
    reader_line line = reader_next(id);
    if (!line.line)
      break;
    printf("line[%i]: [%s]\n", i++, line.line);
  }

  bool b = reader_free(id);

  printf("freed: %i\n", b);

  reader_finalize();
}
