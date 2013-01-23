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
