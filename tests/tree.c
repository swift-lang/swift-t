
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

  long k;
  void* v;
  while (true)
  {
    bool b = tree_pop(&T, &k, &v);
    if (!b) break;
    printf("popped: %li=%s\n\n", k, (char*) v);
    tree_print(&T);
  }

  printf("\n--\n\n");

  tree_add(&T, 12, "hello");
  tree_add(&T, 8, "hello");
  tree_add(&T, 9, "hello-9");
  tree_add(&T, 10, "hello");
  tree_add(&T, 7, "hello");
  tree_add(&T, 15, "hello");
  tree_add(&T, 14, "hello");
  tree_add(&T, 13, "hello-13");
  tree_print(&T);

  printf("move 13 -> 1\n");
  tree_move(&T, 13, 1);
  tree_print(&T);
  tree_pop(&T, &k, &v);
  printf("popped: %li=%s\n\n", k, (char*) v);

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
