
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

#include "src/tools.h"

#include "src/rbtree.h"

static void
pop_all(struct rbtree* T)
{
  long k;
  void* v;
  while (true)
  {
    bool b = rbtree_pop(T, &k, &v);
    if (!b) { printf("POPPED NULL\n"); break; }
    printf("popped: %li=%s\n\n", k, (char*) v);
    printf("STABLE:\n");
    rbtree_print(T);
  }
}

int
main()
{
  struct rbtree T;
  rbtree_init(&T);

  // TEST 1: special case

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

  pop_all(&T);

  printf("\n--\n\n");

 // TEST 2: in-order insertion

  for (long i = 1; i <= 20; i++)
  {
    rbtree_add(&T, i, "hello");
    rbtree_print(&T);
  }

  pop_all(&T);

  // TEST 3: random insertion / in-order deletion

  int n = 20;
  long A[n];
  for (int i = 0; i < n; i++)
    A[i] = i;
  shuffle(A, n);
  for (int i = 0; i < n; i++)
    rbtree_add(&T, A[i], NULL);

  printf("COMPLETE TREE:\n");
  rbtree_print(&T);
  printf("\n");

  pop_all(&T);

  // TEST 4: random insertion / random deletion

  shuffle(A, n);
  for (int i = 0; i < n; i++)
    rbtree_add(&T, A[i], NULL);
  shuffle(A, n);
  for (int i = 0; i < n; i++)
  {
    printf("removing: %li\n", A[i]);
    rbtree_remove(&T, A[i], NULL);
  }


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

  printf("SIZE: %i\n", T.size);
  printf("DONE\n");
  return 0;
}
