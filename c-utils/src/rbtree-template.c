/*
 * Copyright 2013-2015 University of Chicago and Argonne National Laboratory
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
 * rbtree.c
 *
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 *
 * Implementation based on:
 * http://en.wikipedia.org/wiki/Red%E2%80%93black_tree
 */

#include <assert.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>

#include "tools.h"

#include "tree-common.h"

#if 0
#define DEBUG_RBTREE(args...) {printf(args); fflush(stdout);}
#else
#define DEBUG_RBTREE(args...)
#endif

static inline tree_side
which_side(struct RBTREE_NODE* parent, struct RBTREE_NODE* child)
{
  if (parent == NULL)
    return ROOT;
  else if (parent->left == child)
    return LEFT;
  else if (parent->right == child)
    return RIGHT;
  return NEITHER;
}

/**
   With respect to parent P, replace N with R
   @param P is N->parent
   @param N is the node we are removing
   @param R is the replacement
 */
static inline void
replace(struct RBTREE_NODE* P,
        struct RBTREE_NODE* N,
        struct RBTREE_NODE* R)
{
  tree_side s = which_side(P, N);
  if (s == LEFT)
    P->left = R;
  else if (s == RIGHT)
    P->right = R;
  else
    valgrind_fail("replace: P-!>N "RBTREE_KEY_PRNF"->"RBTREE_KEY_PRNF
              "\n", RBTREE_KEY_PRNA(P->key), RBTREE_KEY_PRNA(N->key));
}

static inline struct RBTREE_NODE*
grandparent(struct RBTREE_NODE* node)
{
  if ((node != NULL) && (node->parent != NULL))
    return node->parent->parent;
  else
    return NULL;
}

static inline struct RBTREE_NODE*
uncle(struct RBTREE_NODE* entry)
{
  struct RBTREE_NODE* g = grandparent(entry);
  if (g == NULL)
    return NULL; // No grandparent means no uncle
  if (entry->parent == g->left)
    return g->right;
  else
    return g->left;
}

void
RBTREE_INIT(struct RBTREE_TYPENAME* target)
{
  target->size = 0;
  target->root = NULL;
}

static inline struct RBTREE_NODE*
create_node(RBTREE_KEY_T key, RBTREE_VAL_T data)
{
  struct RBTREE_NODE* node = malloc(sizeof(struct RBTREE_NODE));
  if (node == NULL) return NULL;
  node->parent = NULL;
  node->right = NULL;
  node->left = NULL;
  bool ok = RBTREE_KEY_COPY(node->key, key);
  if (!ok)
  {
    free(node);
    return NULL;
  }

  node->data = data;
  node->color = RED;
  return node;
}

static inline void rbtree_add_loop(struct RBTREE_TYPENAME* target,
                                   struct RBTREE_NODE* node,
                                   struct RBTREE_NODE* p);


/*
      P    -\       P
     N     -/   C
   C  B       Y   N
  Y X            X B
 */
static inline void
rotate_right(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* N)
{
  struct RBTREE_NODE* P = N->parent;
  struct RBTREE_NODE* C = N->left;
  struct RBTREE_NODE* X = C->right;

  valgrind_assert(C != NULL);

  C->right  = N;
  N->parent = C;
  if (P == NULL)
    target->root = C;
  else
    replace(P, N, C);

  C->parent = P;
  N->left   = X;
  if (X != NULL)
    X->parent = N;
}

/*
     P     -\   P
      N    -/         C
     B  C           N   Y
       X Y        B  X
        */
static inline void
rotate_left(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* N)
{
  struct RBTREE_NODE* P = N->parent;
  struct RBTREE_NODE* C = N->right;
  struct RBTREE_NODE* X = C->left;

  valgrind_assert(C != NULL);


  C->left = N;
  N->parent = C;

  if (P == NULL)
    target->root = C;
  else
    replace(P, N, C);
  C->parent = P;

  N->right = X;
  if (X != NULL)
    X->parent = N;
}

// All of these are inlined except insert_case3(), which makes a
// recursive call

static inline void insert_case1(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* node);
static inline void insert_case2(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* node);
static        void insert_case3(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* node);
static inline void insert_case4(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* node);
static inline void insert_case5(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* node);

static inline void
insert_case1(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* node)
{
  if (node->parent == NULL)
    node->color = BLACK;
  else
    insert_case2(target, node);
}

static inline void
insert_case2(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* node)
{
  DEBUG_RBTREE("insert_case2\n");
  if (node->parent->color == BLACK)
    return;
  else
    insert_case3(target, node);
  DEBUG_RBTREE("insert_case2 done.\n");
}

static void
insert_case3(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* node)
{
  struct RBTREE_NODE* U = uncle(node);
  struct RBTREE_NODE* G;

  DEBUG_RBTREE("insert_case3\n");
  if (U != NULL && U->color == RED)
  {
    node->parent->color = BLACK;
    U->color = BLACK;
    G = grandparent(node);
    G->color = RED;
    insert_case1(target, G);
  }
  else
  {
    insert_case4(target, node);
  }
}

static void
insert_case4(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* node)
{
  DEBUG_RBTREE("insert_case4\n");
  struct RBTREE_NODE* g = grandparent(node);

  if ((node == node->parent->right) && (node->parent == g->left))
  {
    rotate_left(target, node->parent);
    node = node->left;
  }
  else if ((node == node->parent->left) && (node->parent == g->right))
  {
    rotate_right(target, node->parent);
    node = node->right;
  }
  insert_case5(target, node);
}

static void
insert_case5(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* node)
{
  struct RBTREE_NODE* g = grandparent(node);

  node->parent->color = BLACK;
  g->color = RED;
  if (node == node->parent->left)
    rotate_right(target, g);
  else
    rotate_left(target, g);
}

static inline void rbtree_add_node_impl(struct RBTREE_TYPENAME* target,
                                        struct RBTREE_NODE* N);

bool
RBTREE_ADD(struct RBTREE_TYPENAME* target, RBTREE_KEY_T key, RBTREE_VAL_T data)
{
  struct RBTREE_NODE* node = create_node(key, data);
  if (node == NULL) return false;
  DEBUG_RBTREE("rbtree_add: node: %p\n", node);

  rbtree_add_node_impl(target, node);

  return true;
}

struct RBTREE_NODE*
RBTREE_NODE_CREATE(RBTREE_KEY_T key, RBTREE_VAL_T data)
{
  return create_node(key, data);
}

void
RBTREE_ADD_NODE(struct RBTREE_TYPENAME* target,
                struct RBTREE_NODE* node)
{
  node->parent = node->left = node->right = NULL;
  rbtree_add_node_impl(target, node);
}

static inline void
rbtree_add_node_impl(struct RBTREE_TYPENAME* target,
                     struct RBTREE_NODE* N)
{
  DEBUG_RBTREE("rbtree_add_node_impl: "RBTREE_KEY_PRNF"\n",
               RBTREE_KEY_PRN(N->key));
  DEBUG_RBTREE("before:\n");
  // RBTREE_PRINT(target);
  N->color = RED;
  if (target->size == 0)
  {
    target->root = N;
    target->size = 1;
    insert_case1(target, N);
  }
  else
  {
    // Normal tree insertion
    struct RBTREE_NODE* root = target->root;
    rbtree_add_loop(target, N, root);
    insert_case2(target, N);
  }
  DEBUG_RBTREE("after:\n");
  // RBTREE_PRINT(target);
}

static inline void
rbtree_add_loop(struct RBTREE_TYPENAME* target,
                struct RBTREE_NODE* node,
                struct RBTREE_NODE* p)
{
  DEBUG_RBTREE("rbtree_add_loop\n");
  target->size++;
  while (true)
  {
    if (RBTREE_KEY_LEQ(node->key, p->key))
    {
      if (p->left == NULL)
      {
        p->left = node;
        node->parent = p;
        break;
      }
      p = p->left;
    }
    else
    {
      if (p->right == NULL)
      {
        p->right = node;
        node->parent = p;
        return;
      }
      p = p->right;
    }
  }
}

static inline struct RBTREE_NODE*
search_node_loop(struct RBTREE_NODE* p, RBTREE_KEY_T key);

struct RBTREE_NODE*
RBTREE_SEARCH_NODE(struct RBTREE_TYPENAME* target, RBTREE_KEY_T key)
{
  if (target->size == 0)
    return NULL;

  return search_node_loop(target->root, key);
}

static inline struct RBTREE_NODE*
search_node_loop(struct RBTREE_NODE* p, RBTREE_KEY_T key)
{
  while (!RBTREE_KEY_EQ(key, p->key))
  {
    if (RBTREE_KEY_LEQ(key, p->key))
      if (p->left == NULL)
        return NULL;
      else
        p = p->left;
    else
      if (p->right == NULL)
        return NULL;
      else
        p = p->right;
  }
  return p;
}

static inline void delete_one_child(struct RBTREE_TYPENAME* target,
                                    struct RBTREE_NODE* N);

bool
RBTREE_REMOVE(struct RBTREE_TYPENAME* target, RBTREE_KEY_T key, RBTREE_VAL_T* data)
{
  struct RBTREE_NODE* N = search_node_loop(target->root, key);
  if (N == NULL)
    return false;

  if (data != NULL)
    *data = N->data;

  RBTREE_REMOVE_NODE(target, N);

  free(N);
  return true;
}

static inline struct RBTREE_NODE*
rbtree_leftmost_loop(struct RBTREE_NODE* N);

/**
   Get color as character
 */
static char
color(struct RBTREE_NODE* N)
{
  if (N->color == RED)
    return 'R';
  else if (N->color == BLACK)
    return 'B';
  return '?';
}

/**
   Debugging routine: pretty-print node with color and key
 */
#define show_node(t) { \
  if (t == NULL) DEBUG_RBTREE("%s: NULL\n", #t); \
  else DEBUG_RBTREE("%s: %c"RBTREE_KEY_PRNF"\n", #t, color(t), \
                    RBTREE_KEY_PRNA(t->key)); \
}

static inline void swap_nodes(struct RBTREE_TYPENAME* target,
                              struct RBTREE_NODE* N,
                              struct RBTREE_NODE* R);
void
RBTREE_REMOVE_NODE(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* N)
{
  DEBUG_RBTREE("rbtree_remove_node: "RBTREE_KEY_PRNF"\n",
               RBTREE_KEY_PRNA(N->key));
  valgrind_assert(target->size >= 1);

  DEBUG_RBTREE("before:\n");
  // RBTREE_PRINT(target);

  if (target->size == 1)
  {
    valgrind_assert(target->root == N);
    target->root = 0;
    goto end;
  }

  // If N has two children, we use replacement R: in-order successor
  struct RBTREE_NODE* R = NULL;
  if (N->right != NULL && N->left != NULL)
    R = rbtree_leftmost_loop(N->right);

  if (R != NULL)
    swap_nodes(target, N, R);

  delete_one_child(target, N);

  DEBUG_RBTREE("after:\n");
  // RBTREE_PRINT(target);

  end:
  target->size--;
}

/**
   P                     P
      N                     R
     B C        -\         B C
       ...      -/          ...
        R                     N
       X Y                   X Y
 */
static inline void
swap_nodes(struct RBTREE_TYPENAME* target,
           struct RBTREE_NODE* N, struct RBTREE_NODE* R)
{
  struct RBTREE_NODE* P = N->parent;
  struct RBTREE_NODE* B = N->left;
  struct RBTREE_NODE* C = N->right;
  struct RBTREE_NODE* X = R->left;
  struct RBTREE_NODE* Y = R->right;

  // Possibilities
  // N, R unrelated
  // R->parent == N

  // Link P to R
  if (P == NULL)
  {
    assert(target->root == N);
    target->root = R;
  }
  else
  {
    replace(P, N, R);
  }

  // Link N to its new parent
  if (R->parent != N)
  {
    N->parent = R->parent;
    show_node(N->parent);
    replace(R->parent, R, N);
  }
  else
  {
    tree_side s = which_side(N, R);
    N->parent = R;
    if (s == LEFT)
      R->left = N;
    else
      R->right = N;
  }

  R->parent = P;

  // Set N's children
  N->left   = X;
  if (X != NULL)
    X->parent = N;
  N->right  = Y;
  if (Y != NULL)
    Y->parent = N;

  // Set R's children
  if (B != R) // otherwise set above
  {
    R->left = B;
    if (B != NULL)
      B->parent = R;
  }
  if (C != R) // otherwise set above
  {
    R->right = C;
    if (C != NULL)
      C->parent = R;
  }

  // Restore colors
  rbtree_color tmp = R->color;
  R->color = N->color;
  N->color = tmp;
}

bool
RBTREE_POP(struct RBTREE_TYPENAME* target, RBTREE_KEY_T* key, RBTREE_VAL_T* data)
{
  if (target->size == 0)
    return false;

  struct RBTREE_NODE* node = RBTREE_LEFTMOST(target);
  *key  = node->key;
  *data = node->data;

  RBTREE_REMOVE_NODE(target, node);
  free(node);

  return true;
}

struct RBTREE_NODE*
RBTREE_LEFTMOST(struct RBTREE_TYPENAME* target)
{
  if (target->size == 0)
    return NULL;

  struct RBTREE_NODE* result = rbtree_leftmost_loop(target->root);
  DEBUG_RBTREE("rbtree_leftmost: "RBTREE_KEY_PRNF"\n",
               RBTREE_KEY_PRNA(result->key));
  return result;
}

RBTREE_KEY_T
RBTREE_LEFTMOST_KEY(struct RBTREE_TYPENAME* target)
{
  if (target->size == 0)
    return RBTREE_KEY_INVALID;

  struct RBTREE_NODE* node = rbtree_leftmost_loop(target->root);
  RBTREE_KEY_T result = node->key;
  return result;
}

static inline struct RBTREE_NODE*
rbtree_leftmost_loop(struct RBTREE_NODE* N)
{
  if (N == NULL)
    return NULL;

  struct RBTREE_NODE* result = NULL;
  while (true)
  {
    if (N->left == NULL)
    {
      result = N;
      break;
    }
    N = N->left;
  }
  return result;
}

// All of these are inlined except delete_case3(), which makes a
// recursive call
static inline void delete_case1(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* P,
                         struct RBTREE_NODE* N);
static inline void delete_case2(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* P,
                                struct RBTREE_NODE* N);
static        void delete_case3(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* N);
static inline void delete_case4(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* P,
                                struct RBTREE_NODE* N,
                                struct RBTREE_NODE* S);
static inline void delete_case5(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* P,
                                struct RBTREE_NODE* N,
                                struct RBTREE_NODE* S);
static inline void delete_case6(struct RBTREE_TYPENAME* target,
                                struct RBTREE_NODE* N);

/**
   Preconditions:
   * N has at most one non-null child
   * Tree size is 2 or more
 */
static inline void
delete_one_child(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* N)
{
  valgrind_assert(target->size >= 2);
  valgrind_assert(N->right == NULL || N->left == NULL);

  struct RBTREE_NODE* P = N->parent;
  struct RBTREE_NODE* C =
      (N->right == NULL) ? N->left : N->right;

  if (C == NULL)
  {
    if (N->color == RED)
    {
      replace(P, N, NULL);
      return;
    }
  }

  if (C != NULL)
  {
    swap_nodes(target, N, C);
    struct RBTREE_NODE* t = N;
    N = C;
    C = t;
  }

  if (N->color == BLACK)
  {
    if (C != NULL && C->color == RED)
    {
      // unlink child
      replace(N, C, NULL);
    }
    else
    {
      delete_case1(target, P, N);
      replace(P, N, C);
    }
  }
  else // N RED
  {
    replace(N, C, NULL);
  }
}

static inline void
delete_case1(struct RBTREE_TYPENAME* target,
             struct RBTREE_NODE* P,
             struct RBTREE_NODE* N)
{
  if (P != NULL)
    delete_case2(target, P, N);
}

/**
   Find sibling of N with common parent P
   Note: N may be NULL
 */
static inline struct RBTREE_NODE*
sibling(struct RBTREE_NODE* P, struct RBTREE_NODE* N)
{
  if (N == P->left)
    return P->right;
  else
    return P->left;
}

static inline void
delete_case2(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* P,
             struct RBTREE_NODE* N)
{
  struct RBTREE_NODE* S = sibling(P, N);

  if (S->color == RED)
  {
    P->color = RED;
    S->color = BLACK;
    if (N == P->left)
      rotate_left(target, P);
    else
      rotate_right(target, P);
  }
  delete_case3(target, N);
}

static void
delete_case3(struct RBTREE_TYPENAME* target,
             struct RBTREE_NODE* N)
{
  struct RBTREE_NODE* P = N->parent;
  struct RBTREE_NODE* S = sibling(P, N);

  if ((P->color == BLACK) &&
      (S->color == BLACK) &&
      (S->left  == NULL || S->left->color  == BLACK) &&
      (S->right == NULL || S->right->color == BLACK))
  {
    S->color = RED;
    delete_case1(target, P->parent, P);
  }
  else
    delete_case4(target, P, N, S);
}

static inline void
delete_case4(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* P,
             struct RBTREE_NODE* N, struct RBTREE_NODE* S)
{
  if ((P->color == RED) &&
      (S->color == BLACK) &&
      (S->left  == NULL || S->left->color  == BLACK) &&
      (S->right == NULL || S->right->color == BLACK))
  {
    S->color = RED;
    P->color = BLACK;
  }
  else
    delete_case5(target, P, N, S);
}

static inline void
delete_case5(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* P,
             struct RBTREE_NODE* N, struct RBTREE_NODE* S)
{
  valgrind_assert(S->color == BLACK);
  if ((N == P->left) &&
      (S->right == NULL || S->right->color == BLACK))
  {
    valgrind_assert(S->left->color == RED);
    S->color = RED;
    S->left->color = BLACK;
    rotate_right(target, S);
  }
  else if ((N == P->right) &&
           (S->left == NULL || S->left->color == BLACK))
  {
    valgrind_assert(S->right->color == RED);
    S->color = RED;
    S->right->color = BLACK;
    rotate_left(target, S);
  }
  delete_case6(target, N);
}

static inline void
delete_case6(struct RBTREE_TYPENAME* target,
             struct RBTREE_NODE* N)
{
  struct RBTREE_NODE* P = N->parent;
  struct RBTREE_NODE* S = sibling(P, N);
  S->color = P->color;
  P->color = BLACK;

  tree_side s = which_side(P, N);
  if (s == LEFT)
  {
    S->right->color = BLACK;
    rotate_left(target, P);
  }
  else if (s == RIGHT)
  {
    S->left->color = BLACK;
    rotate_right(target, P);
  }
  else
    valgrind_fail("X");
}

static bool iterator_loop(struct RBTREE_NODE* node,
                          RBTREE_CALLBACK cb,
                          RBTREE_VAL_T user_data);

bool
RBTREE_ITERATOR(struct RBTREE_TYPENAME* target, RBTREE_CALLBACK cb,
                RBTREE_VAL_T user_data)
{
  struct RBTREE_NODE* root = target->root;
  if (root == NULL)
    return false;
  DEBUG_RBTREE("rbtree_iterator: %i\n", target->size);
  // RBTREE_PRINT(target);
  bool b = iterator_loop(root, cb, user_data);
  return b;
}

static bool
iterator_loop(struct RBTREE_NODE* node, RBTREE_CALLBACK cb,
              RBTREE_VAL_T user_data)
{
  DEBUG_RBTREE("rbtree_iterator loop...\n");
  if (node->left != NULL)
  {
    DEBUG_RBTREE("rbtree_iterator loop left...\n");
    bool b = iterator_loop(node->left, cb, user_data);
    if (b) return true;
  }
  DEBUG_RBTREE("rbtree_iterator loop cb()...\n");
  bool b = cb(node, user_data);
  if (b) return true;
  if (node->right != NULL)
  {
    DEBUG_RBTREE("rbtree_iterator loop right...\n");
    bool b = iterator_loop(node->right, cb, user_data);
    if (b) return true;
  }
  return false;
}

bool
RBTREE_MOVE(struct RBTREE_TYPENAME* target, RBTREE_KEY_T key_old,
            RBTREE_KEY_T key_new)
{
  DEBUG_RBTREE("rbtree_move: "RBTREE_KEY_PRNF" -> "RBTREE_KEY_PRNF"\n",
              RBTREE_KEY_PRNA(key_old), RBTREE_KEY_PRNA(key_new));
  struct RBTREE_NODE* p = RBTREE_SEARCH_NODE(target, key_old);
  if (p == NULL)
    return false;

  DEBUG_RBTREE("before:\n");
  // RBTREE_PRINT(target);

  RBTREE_REMOVE_NODE(target, p);

  p->key   = key_new;
  p->color = RED; // new nodes are always RED
  p->left  = NULL;
  p->right = NULL;
  rbtree_add_node_impl(target, p);

  DEBUG_RBTREE("after:\n");
  // RBTREE_PRINT(target);

  return true;
}

static inline struct RBTREE_NODE*
rbtree_random_loop(struct RBTREE_NODE* p);

struct RBTREE_NODE*
RBTREE_RANDOM(struct RBTREE_TYPENAME* target)
{
  if (target->size == 0)
    return NULL;

  struct RBTREE_NODE* result = rbtree_random_loop(target->root);
  return result;
}

#define INT_BITS (sizeof(int) * 8)

static inline struct RBTREE_NODE*
rbtree_random_loop(struct RBTREE_NODE* p)
{
  int random_bits_left = 0;
  int randval = 0;
  while (true)
  {
    if (p->left != NULL)
    {
      if (p->right != NULL)
      {
        // Both not null
        if (random_bits_left == 0)
        {
          randval = rand();
          random_bits_left = INT_BITS;
        }
        // Consume a random bit
        bool choice = randval & 0x1;
        randval = randval >> 1;
        random_bits_left--;
        if (choice)
          p = p->right;
        else
          p = p->left;
      }
      else
      {
        p = p->left;
      }
    }
    else
    {
      // p->left NULL
      if (p->right != NULL)
      {
        p = p->right;
      }
      else
      {
        // Leaf
        return p;
      }
    }
  }
  // This is unreachable: squelch no-return warning
  return NULL;
}

static void rbtree_print_loop(struct RBTREE_NODE* node, int level);

void
RBTREE_PRINT(struct RBTREE_TYPENAME* target)
{
  if (target->size == 0)
    printf("TREE EMPTY\n");
  else
  {
    // printf("RBTREE_PRINT: %p\n", target);
    rbtree_print_loop(target->root, 0);
  }
}

static void
rbtree_print_loop(struct RBTREE_NODE* node, int level)
{
  valgrind_assert(node != NULL);
  char buffer[level+16];
  char* p = &buffer[0];
  append(p, "+ ");
  for (int i = 0; i < level; i++)
    append(p, " ");
  append(p, "%c"RBTREE_KEY_PRNF"", color(node),
         RBTREE_KEY_PRNA(node->key));
  printf("%s\n", buffer);

  if (node->left == NULL && node->right == NULL)
    return;

  // recurse left
  if (node->left != NULL)
  {
    valgrind_assert_msg(node->left->parent == node,
            "node: %c"RBTREE_KEY_PRNF"->left is not linked back",
            color(node), RBTREE_KEY_PRNA(node->key));
    rbtree_print_loop(node->left, level+1);
  }
  else
    print_x(level+1);

  // recurse right
  if (node->right != NULL)
  {
    valgrind_assert_msg(node->right->parent == node,
                "node: %c"RBTREE_KEY_PRNF"->right is not linked back",
                color(node), RBTREE_KEY_PRNA(node->key));
    rbtree_print_loop(node->right, level+1);
  }
  else
    print_x(level+1);
}

static void rbtree_free_subtree(struct RBTREE_NODE* node, RBTREE_CALLBACK cb);

void
RBTREE_CLEAR(struct RBTREE_TYPENAME* target)
{
  RBTREE_CLEAR_CALLBACK(target, NULL);
}

void
RBTREE_CLEAR_CALLBACK(struct RBTREE_TYPENAME* target, RBTREE_CALLBACK cb)
{
  if (target->size != 0)
  {
    rbtree_free_subtree(target->root, cb);
    // Reset to original state
    RBTREE_INIT(target);
  }
}

static void
rbtree_free_subtree(struct RBTREE_NODE* node, RBTREE_CALLBACK cb)
{
  if (node->left != NULL)
    rbtree_free_subtree(node->left, cb);
  if (node->right != NULL)
    rbtree_free_subtree(node->right, cb);
  if (cb != NULL)
    cb(node, node->data);
  RBTREE_KEY_FREE(node->key);
  free(node);
}

void
RBTREE_FREE(struct RBTREE_TYPENAME* target)
{
  RBTREE_CLEAR(target);
  free(target);
}
