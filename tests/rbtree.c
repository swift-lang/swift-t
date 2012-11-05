
/*
 * rbtree.c
 *
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 */

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "src/rbtree.h"

int
main()
{
  struct rbtree T;
  rbtree_init(&T);
  rbtree_add(&T, 12, "hello");
  rbtree_add(&T, 8,  "hello");
  rbtree_add(&T, 9,  "hello");
  rbtree_add(&T, 10, "hello");
  rbtree_add(&T, 7,  "hello");
  rbtree_add(&T, 15, "hello");
  rbtree_add(&T, 14, "hello");
  rbtree_add(&T, 13, "hello");
  rbtree_print(&T);

  printf("\nREMOVING...\n");

  void* data;
  rbtree_remove(&T, 12, &data);
  printf("remove ok.\n");
  rbtree_print(&T);

  long k;
  void* v;
  // works up to i<4
  for (int i = 0; i < 8; i++)
  {
    printf("\nPOP:\n");
    bool b = rbtree_pop(&T, &k, &v);
    if (!b) { printf("POPPED NULL\n"); break; }
    printf("popped: %li=%s\n\n", k, (char*) v);
    printf("STABLE:\n");
    rbtree_print(&T);
  }

  printf("\n--\n\n");

  // for (long i = 0; i <



//
//  printf("move 13 -> 1\n");
//  rbtree_move(&T, 13, 1);
//  rbtree_print(&T);
//  rbtree_pop(&T, &k, &v);
//  printf("popped: %li=%s\n\n", k, (char*) v);
//
//  printf("move 9 -> 20\n");
//  rbtree_move(&T, 9, 20);
//  rbtree_print(&T);
//  printf("\n");
//
//  printf("move 12 -> 9\n");
//  rbtree_move(&T, 12, 9);
//  rbtree_print(&T);
//  printf("\n");
//
//  rbtree_clear(&T);

  printf("DONE\n");
  return 0;
}
