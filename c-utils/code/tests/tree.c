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
 * tree.c
 *
 *  Created on: Sep 6, 2012
 *      Author: wozniak
 */

#include <stdio.h>

#include "src/tree.h"

int
main()
{
  struct tree T;
  tree_init(&T);
  tree_add(&T, 12, "hello");
  tree_add(&T, 8,  "hello");
  tree_add(&T, 9,  "hello");
  tree_add(&T, 10, "hello");
  tree_add(&T, 7,  "hello");
  tree_add(&T, 15, "hello");
  tree_add(&T, 14, "hello");
  tree_add(&T, 13, "hello");

  tree_print(&T);

  int64_t k;
  void* v;
  while (true)
  {
    bool b = tree_pop(&T, &k, &v);
    if (!b) break;
    printf("popped: %" PRId64 "=%s\n\n", k, (char*) v);
    tree_print(&T);
  }

  printf("\n--\n\n");
  tree_add(&T,  12, "hello");
  tree_add(&T, 8,  "hello");
  tree_add(&T, 9,  "hello-9");
  tree_add(&T, 10, "hello");
  tree_add(&T, 7,  "hello");
  tree_add(&T, 15, "hello");
  tree_add(&T, 14, "hello");
  tree_add(&T, 13, "hello-13");
  tree_print(&T);

  printf("move 13 -> 1\n");
  tree_move(&T, 13, 1);
  tree_print(&T);
  tree_pop(&T, &k, &v);
  printf("popped: %" PRId64 "=%s\n\n", k, (char*) v);

  printf("move 9 -> 20\n");
  tree_move(&T, 9, 20);
  tree_print(&T);
  printf("\n");

  printf("move 12 -> 9\n");
  tree_move(&T, 12, 9);
  tree_print(&T);
  printf("\n");

  tree_clear(&T);

  printf("DONE\n");
  return 0;
}
