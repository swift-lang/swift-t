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
 * test/version.c
 *
 *  Created on: Dec 31, 2011
 *      Author: wozniak
 * */

#include <stdio.h>

#include "src/version.h"

int
main()
{
  version v1, v2, v3;
  char c[64];

  version_init(&v1, 1, 2, 6);
  version_parse(&v2, "2.1.6");
  version_init(&v3, 1, 2, 5);

  int count = version_to_string(&c[0], &v1);
  printf("version: %s\n", c);
  printf("count: %i\n", count);
  int b;
  b = version_cmp(&v1, &v2);
  printf("b1: %i\n", b);
  b = version_cmp(&v1, &v3);
  printf("b2: %i\n", b);
  b = version_cmp(&v2, &v3);
  printf("b3: %i\n", b);
}
