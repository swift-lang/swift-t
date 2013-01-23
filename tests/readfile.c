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
 * readfile.c
 *
 *  Created on: May 11, 2011
 *      Author: wozniak
 */

#include <stdio.h>

#include "src/reader.h"

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

  reader_free(id);
  reader_finalize();
}
