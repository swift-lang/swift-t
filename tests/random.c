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

#include <stdio.h>

#include "src/tools.h"

int
main()
{
  int N = 10;
  for (int i = 0; i < N; i++)
  {
    double l = i;
    double h = i+0.1;
    double d = random_between_double(l, h);
    printf("random [%0.3f,%0.3f) : %0.5f\n", l, h, d);
  }
  return 0;
}
