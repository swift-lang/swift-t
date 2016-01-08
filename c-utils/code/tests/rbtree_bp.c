/*
 * Copyright 2015 University of Chicago and Argonne National Laboratory
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
 * rbtree_bp.c
 *
 *  Created on: June 01, 2015
 *      Author: Tim Armstrong
 *
 * Based on:
 * rbtree.c
 *
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 */

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "src/c-utils-tests.h"
#include "src/tools.h"
#include "src/rbtree_bp.h"

static void
pop_all(struct rbtree_bp* T)
{
  binkey_packed_t k;
  void* v;
  int size = T->size;
  int pops = 0;
  while (true)
  {
    bool b = rbtree_bp_pop(T, &k, &v);
    if (!b) { printf("POPPED NULL\n"); break; }
    printf("popped: %s=%s\n\n", (char*)binkey_packed_get(&k), (char*) v);
    printf("STABLE:\n");
    rbtree_bp_print(T);
    pops++;
  }
  ASSERT_TRUE(pops == size);
}

static bool
test_cb(struct rbtree_bp_node* node, void* user_data)
{
  printf("node: %s %s\n", (char*)binkey_packed_get(&node->key),
                          (char*) node->data);
  return false;
}

static bool
empty_cb(struct rbtree_bp_node* node, void* user_data)
{
  printf("node: %s %s\n", (char*)binkey_packed_get(&node->key),
                           (char*) node->data);
  return true;
}

static bool
test_empty_iterator()
{

  struct rbtree_bp T;
  rbtree_bp_init(&T);
  bool found = rbtree_bp_iterator(&T, empty_cb, NULL);
  if (found)
  {
    fprintf(stderr, "Found something in empty tree\n");
    exit(1);
  }
  fprintf(stderr, "Empty iterator did nothing\n");
  return true;
}

#define BUF_SIZE 512

/*
  Make key with numeric representation plus padding to get a
  range of key sizes.
  Add numeric padding so that for positive numbers up to 9999,
  lexical order matches numeric order.
 */
static binkey_packed_t make_key(char buf[BUF_SIZE], int val)
{
  int pos = 0;
  pos += sprintf(buf, "%04i", val);
  while (pos < BUF_SIZE - 1 && pos < val / 3)
  {
    buf[pos++] = '_';
  }

  buf[pos++] = '\0';

  binkey_packed_t packed;
  bool ok = binkey_packed_set(&packed, buf, (size_t)pos);
  ASSERT_TRUE(ok);

  return packed;
}

int
main()
{
  struct rbtree_bp T;
  rbtree_bp_init(&T);

  // TEST 1:

  char buf[BUF_SIZE], buf2[BUF_SIZE];

  rbtree_bp_add(&T, make_key(buf, 12), "hello");
  rbtree_bp_add(&T, make_key(buf, 8),  "hello");
  rbtree_bp_add(&T, make_key(buf, 9),  "hello");
  rbtree_bp_add(&T, make_key(buf, 10), "hello");
  rbtree_bp_add(&T, make_key(buf, 7),  "hello");
  rbtree_bp_add(&T, make_key(buf, 15), "hello");
  rbtree_bp_add(&T, make_key(buf, 14), "hello");
  rbtree_bp_add(&T, make_key(buf, 13), "hello");

  printf("\nITERATOR...\n");
  rbtree_bp_iterator(&T, test_cb, NULL);

  printf("\nREMOVING...\n");

  void* data;
  rbtree_bp_remove(&T, make_key(buf, 12), &data);
  printf("remove ok.\n");
  rbtree_bp_print(&T);

  pop_all(&T);

  printf("\n--\n\n");

 // TEST 2: in-order insertion

  for (long i = 1; i <= 20; i++)
  {
    rbtree_bp_add(&T, make_key(buf, i), "hello");
    rbtree_bp_print(&T);
  }

  pop_all(&T);

  // TEST 3: random insertion / in-order deletion

  int n = 100;
  long A[n];
  for (int i = 0; i < n; i++)
    A[i] = i;
  shuffle(A, n);
  for (int i = 0; i < n; i++)
    rbtree_bp_add(&T, make_key(buf, A[i]), NULL);

  printf("COMPLETE TREE:\n");
  rbtree_bp_print(&T);
  printf("\n");

  pop_all(&T);

  // TEST 4: random insertion / random deletion

  printf("\nRANDOM INSERTION - RANDOM DELETION\n\n");
  shuffle(A, n);
  print_longs(A, n);
  printf("\n");
  for (int i = 0; i < n; i++)
    rbtree_bp_add(&T, make_key(buf, A[i]), NULL);
  shuffle(A, n);
  for (int i = 0; i < n; i++)
  {
    binkey_packed_t key = make_key(buf, A[i]);
    printf("removing: %li (%s)\n", A[i], buf);
    bool b = rbtree_bp_remove(&T, key, NULL);
    ASSERT_TRUE(b);
    rbtree_bp_print(&T);
  }

  // TEST 5: moves

  int m = 8;
  int moves = 2;
  ASSERT_TRUE(moves < m/2);

  long B[m];
  for (int i = 0; i < m; i++)
    B[i] = i;

  long tmp[m];
  memcpy(tmp, B, m*sizeof(long));
  shuffle(tmp, m);

  // sources
  long S[moves];
  // dests
  long D[moves];
  for (int i = 0; i < moves; i++)
  {
    S[i] = tmp[i];
    D[i] = tmp[m-i-1];
  }

  printf("B:\n");
  print_longs(B, m);
  printf("\n");
  printf("S:\n");
  print_longs(S, moves);
  printf("\n");
  printf("D:\n");
  print_longs(D, moves);
  printf("\n");

  // add all data
  printf("ADDING...\n");
  for (int i = 0; i < m; i++)
    rbtree_bp_add(&T, make_key(buf, B[i]), NULL);
  rbtree_bp_print(&T);

  // remove all dests (make space for moves)
  printf("REMOVING DESTS...\n");
  for (int i = 0; i < moves; i++)
    rbtree_bp_remove(&T, make_key(buf, D[i]), NULL);
  rbtree_bp_print(&T);

  printf("MOVING...\n");
  // do each move
  for (int i = 0; i < moves; i++)
  {
    printf("moving: %li to %li\n", S[i], D[i]);
    rbtree_bp_move(&T, make_key(buf, S[i]), make_key(buf2, D[i]));
    printf("move done.\n");
    rbtree_bp_print(&T);
  }

  rbtree_bp_clear(&T);

  printf("SIZE: %i\n", T.size);

  test_empty_iterator();

  // TEST - range search
  printf("RANGE SEARCH\n");

  int MAX_RANGE = 100;
  // Add odds only so there are gaps
  for (int i = 1; i < MAX_RANGE; i += 2)
  {
    rbtree_bp_add(&T, make_key(buf, i), (void*)(long)i);

    for (int j = 0; j < MAX_RANGE; j++)
    {
      struct rbtree_bp_node *n = rbtree_bp_search_range(&T, make_key(buf, j));
      if (j <= i)
      {
        // Should return exact match or one after
        int exp_key = j + (1 - j % 2);
        binkey_packed_t exp_key2 = make_key(buf, exp_key);
        ASSERT_TRUE_MSG(binkey_packed_eq(&n->key, &exp_key2), "Expected lookup of %i to be"
                                  "%i after adding %i", j, exp_key, i);
        for (int k = exp_key + 2; k <= i; k += 2)
        {
          struct rbtree_bp_node *nn = rbtree_bp_next_node(n);

          binkey_packed_t k2 = make_key(buf, k);
          ASSERT_TRUE_MSG(binkey_packed_eq(&nn->key, &k2),
                    "Expected %s == %s", (char*)binkey_packed_get(&nn->key),
                    (char*)binkey_packed_get(&k2));
          ASSERT_TRUE_MSG((long)nn->data == k, "Expected %li == %i", (long)nn->data, k);

          // Check prev node works
          ASSERT_TRUE(rbtree_bp_prev_node(nn) == n);

          n = nn;
        }

        // Should be at last valid node
        ASSERT_TRUE(rbtree_bp_next_node(n) == NULL);
      }
      else
      {
        ASSERT_TRUE_MSG(rbtree_bp_search_range(&T, make_key(buf, j)) == NULL,
                    "Expected lookup of %i to be NULL", j);
      }
    }

    // Check that searching past last always returns NULL
    ASSERT_TRUE_MSG(rbtree_bp_search_range(&T, make_key(buf, i + 1)) == NULL,
                    "Expected lookup of %i to be NULL", i + 1);
  }

  printf("DONE\n");
  return 0;
}
