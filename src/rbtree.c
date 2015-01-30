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

#include "rbtree.h"
#include "tree-common.h"

#if 0
#define DEBUG_RBTREE(args...) {printf(args); fflush(stdout);}
#else
#define DEBUG_RBTREE(args...)
#endif

static inline tree_side
which_side(struct rbtree_node* parent, struct rbtree_node* child)
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
replace(struct rbtree_node* P,
        struct rbtree_node* N,
        struct rbtree_node* R)
{
  tree_side s = which_side(P, N);
  if (s == LEFT)
    P->left = R;
  else if (s == RIGHT)
    P->right = R;
  else
    valgrind_fail("replace: P-!>N %"PRId64"->%"PRId64"\n", P->key, N->key);
}

static inline struct rbtree_node*
grandparent(struct rbtree_node* node)
{
  if ((node != NULL) && (node->parent != NULL))
    return node->parent->parent;
  else
    return NULL;
}

static inline struct rbtree_node*
uncle(struct rbtree_node* entry)
{
  struct rbtree_node* g = grandparent(entry);
  if (g == NULL)
    return NULL; // No grandparent means no uncle
  if (entry->parent == g->left)
    return g->right;
  else
    return g->left;
}

void
rbtree_init(struct rbtree* target)
{
  target->size = 0;
  target->root = NULL;
}

static inline struct rbtree_node*
create_node(rbtree_key_t key, void* data)
{
  struct rbtree_node* node = malloc(sizeof(struct rbtree_node));
  if (node == NULL) return NULL;
  node->parent = NULL;
  node->right = NULL;
  node->left = NULL;
  node->key = key;
  node->data = data;
  node->color = RED;
  return node;
}

static inline void rbtree_add_loop(struct rbtree* target,
                                   struct rbtree_node* node,
                                   struct rbtree_node* p);


/*
      P    -\       P
     N     -/   C
   C  B       Y   N
  Y X            X B
 */
static inline void
rotate_right(struct rbtree* target, struct rbtree_node* N)
{
  struct rbtree_node* P = N->parent;
  struct rbtree_node* C = N->left;
  struct rbtree_node* X = C->right;

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
rotate_left(struct rbtree* target, struct rbtree_node* N)
{
  struct rbtree_node* P = N->parent;
  struct rbtree_node* C = N->right;
  struct rbtree_node* X = C->left;

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

static inline void insert_case1(struct rbtree* target,
                                struct rbtree_node* node);
static inline void insert_case2(struct rbtree* target,
                                struct rbtree_node* node);
static        void insert_case3(struct rbtree* target,
                                struct rbtree_node* node);
static inline void insert_case4(struct rbtree* target,
                                struct rbtree_node* node);
static inline void insert_case5(struct rbtree* target,
                                struct rbtree_node* node);

static inline void
insert_case1(struct rbtree* target, struct rbtree_node* node)
{
  if (node->parent == NULL)
    node->color = BLACK;
  else
    insert_case2(target, node);
}

static inline void
insert_case2(struct rbtree* target, struct rbtree_node* node)
{
  DEBUG_RBTREE("insert_case2\n");
  if (node->parent->color == BLACK)
    return;
  else
    insert_case3(target, node);
  DEBUG_RBTREE("insert_case2 done.\n");
}

static void
insert_case3(struct rbtree* target, struct rbtree_node* node)
{
  struct rbtree_node* U = uncle(node);
  struct rbtree_node* G;

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
insert_case4(struct rbtree* target, struct rbtree_node* node)
{
  DEBUG_RBTREE("insert_case4\n");
  struct rbtree_node* g = grandparent(node);

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
insert_case5(struct rbtree* target, struct rbtree_node* node)
{
  struct rbtree_node* g = grandparent(node);

  node->parent->color = BLACK;
  g->color = RED;
  if (node == node->parent->left)
    rotate_right(target, g);
  else
    rotate_left(target, g);
}

static inline void rbtree_add_node_impl(struct rbtree* target,
                                        struct rbtree_node* N);

bool
rbtree_add(struct rbtree* target, rbtree_key_t key, void* data)
{
  struct rbtree_node* node = create_node(key, data);
  if (node == NULL) return false;
  DEBUG_RBTREE("rbtree_add: node: %p\n", node);

  rbtree_add_node_impl(target, node);

  return true;
}

struct rbtree_node*
rbtree_node_create(rbtree_key_t key, void* data)
{
  return create_node(key, data);
}

void
rbtree_add_node(struct rbtree* target,
                struct rbtree_node* node)
{
  node->parent = node->left = node->right = NULL;
  rbtree_add_node_impl(target, node);
}

static inline void
rbtree_add_node_impl(struct rbtree* target,
                     struct rbtree_node* N)
{
  DEBUG_RBTREE("rbtree_add_node_impl: %"PRId64"\n", N->key);
  DEBUG_RBTREE("before:\n");
  // rbtree_print(target);
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
    struct rbtree_node* root = target->root;
    rbtree_add_loop(target, N, root);
    insert_case2(target, N);
  }
  DEBUG_RBTREE("after:\n");
  // rbtree_print(target);
}

static inline void
rbtree_add_loop(struct rbtree* target,
                struct rbtree_node* node,
                struct rbtree_node* p)
{
  DEBUG_RBTREE("rbtree_add_loop\n");
  target->size++;
  while (true)
  {
    if (node->key <= p->key)
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

static inline struct rbtree_node*
search_node_loop(struct rbtree_node* p, rbtree_key_t key);

struct rbtree_node*
rbtree_search_node(struct rbtree* target, rbtree_key_t key)
{
  if (target->size == 0)
    return NULL;

  return search_node_loop(target->root, key);
}

static inline struct rbtree_node*
search_node_loop(struct rbtree_node* p, rbtree_key_t key)
{
  while (key != p->key)
  {
    if (key < p->key)
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

static inline void delete_one_child(struct rbtree* target,
                                    struct rbtree_node* N);

bool
rbtree_remove(struct rbtree* target, rbtree_key_t key, void** data)
{
  struct rbtree_node* N = search_node_loop(target->root, key);
  if (N == NULL)
    return false;

  if (data != NULL)
    *data = N->data;

  rbtree_remove_node(target, N);

  free(N);
  return true;
}

static inline struct rbtree_node*
rbtree_leftmost_loop(struct rbtree_node* N);

/**
   Get color as character
 */
static char
color(struct rbtree_node* N)
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
  else DEBUG_RBTREE("%s: %c%"PRId64"\n", #t, color(t), t->key); \
}

static inline void swap_nodes(struct rbtree* target,
                              struct rbtree_node* N,
                              struct rbtree_node* R);
void
rbtree_remove_node(struct rbtree* target, struct rbtree_node* N)
{
  DEBUG_RBTREE("rbtree_remove_node: %"PRId64"\n", N->key);
  valgrind_assert(target->size >= 1);

  DEBUG_RBTREE("before:\n");
  // rbtree_print(target);

  if (target->size == 1)
  {
    valgrind_assert(target->root == N);
    target->root = 0;
    goto end;
  }

  // If N has two children, we use replacement R: in-order successor
  struct rbtree_node* R = NULL;
  if (N->right != NULL && N->left != NULL)
    R = rbtree_leftmost_loop(N->right);

  if (R != NULL)
    swap_nodes(target, N, R);

  delete_one_child(target, N);

  DEBUG_RBTREE("after:\n");
  // rbtree_print(target);

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
swap_nodes(struct rbtree* target,
           struct rbtree_node* N, struct rbtree_node* R)
{
  struct rbtree_node* P = N->parent;
  struct rbtree_node* B = N->left;
  struct rbtree_node* C = N->right;
  struct rbtree_node* X = R->left;
  struct rbtree_node* Y = R->right;

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
rbtree_pop(struct rbtree* target, rbtree_key_t* key, void** data)
{
  if (target->size == 0)
    return false;

  struct rbtree_node* node = rbtree_leftmost(target);
  *key  = node->key;
  *data = node->data;

  rbtree_remove_node(target, node);
  free(node);

  return true;
}

struct rbtree_node*
rbtree_leftmost(struct rbtree* target)
{
  if (target->size == 0)
    return NULL;

  struct rbtree_node* result = rbtree_leftmost_loop(target->root);
  DEBUG_RBTREE("rbtree_leftmost: %"PRId64"\n", result->key);
  return result;
}

rbtree_key_t
rbtree_leftmost_key(struct rbtree* target)
{
  if (target->size == 0)
    return 0;

  struct rbtree_node* node = rbtree_leftmost_loop(target->root);
  rbtree_key_t result = node->key;
  return result;
}

static inline struct rbtree_node*
rbtree_leftmost_loop(struct rbtree_node* N)
{
  if (N == NULL)
    return NULL;

  struct rbtree_node* result = NULL;
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
static inline void delete_case1(struct rbtree* target, struct rbtree_node* P,
                         struct rbtree_node* N);
static inline void delete_case2(struct rbtree* target,
                                struct rbtree_node* P,
                                struct rbtree_node* N);
static        void delete_case3(struct rbtree* target,
                                struct rbtree_node* N);
static inline void delete_case4(struct rbtree* target,
                                struct rbtree_node* P,
                                struct rbtree_node* N,
                                struct rbtree_node* S);
static inline void delete_case5(struct rbtree* target,
                                struct rbtree_node* P,
                                struct rbtree_node* N,
                                struct rbtree_node* S);
static inline void delete_case6(struct rbtree* target,
                                struct rbtree_node* N);

/**
   Preconditions:
   * N has at most one non-null child
   * Tree size is 2 or more
 */
static inline void
delete_one_child(struct rbtree* target, struct rbtree_node* N)
{
  valgrind_assert(target->size >= 2);
  valgrind_assert(N->right == NULL || N->left == NULL);

  struct rbtree_node* P = N->parent;
  struct rbtree_node* C =
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
    struct rbtree_node* t = N;
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
delete_case1(struct rbtree* target,
             struct rbtree_node* P,
             struct rbtree_node* N)
{
  if (P != NULL)
    delete_case2(target, P, N);
}

/**
   Find sibling of N with common parent P
   Note: N may be NULL
 */
static inline struct rbtree_node*
sibling(struct rbtree_node* P, struct rbtree_node* N)
{
  if (N == P->left)
    return P->right;
  else
    return P->left;
}

static inline void
delete_case2(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N)
{
  struct rbtree_node* S = sibling(P, N);

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
delete_case3(struct rbtree* target,
             struct rbtree_node* N)
{
  struct rbtree_node* P = N->parent;
  struct rbtree_node* S = sibling(P, N);

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
delete_case4(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N, struct rbtree_node* S)
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
delete_case5(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N, struct rbtree_node* S)
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
delete_case6(struct rbtree* target,
             struct rbtree_node* N)
{
  struct rbtree_node* P = N->parent;
  struct rbtree_node* S = sibling(P, N);
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

static bool iterator_loop(struct rbtree_node* node,
                          rbtree_callback cb,
                          void* user_data);

bool
rbtree_iterator(struct rbtree* target, rbtree_callback cb,
                void* user_data)
{
  struct rbtree_node* root = target->root;
  if (root == NULL)
    return false;
  DEBUG_RBTREE("rbtree_iterator: %i\n", target->size);
  // rbtree_print(target);
  bool b = iterator_loop(root, cb, user_data);
  return b;
}

static bool
iterator_loop(struct rbtree_node* node, rbtree_callback cb,
              void* user_data)
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
rbtree_move(struct rbtree* target, rbtree_key_t key_old,
            rbtree_key_t key_new)
{
  DEBUG_RBTREE("rbtree_move: %"PRId64" -> %"PRId64"\n", key_old, key_new);
  struct rbtree_node* p = rbtree_search_node(target, key_old);
  if (p == NULL)
    return false;

  DEBUG_RBTREE("before:\n");
  // rbtree_print(target);

  rbtree_remove_node(target, p);

  p->key   = key_new;
  p->color = RED; // new nodes are always RED
  p->left  = NULL;
  p->right = NULL;
  rbtree_add_node_impl(target, p);

  DEBUG_RBTREE("after:\n");
  // rbtree_print(target);

  return true;
}

static inline struct rbtree_node*
rbtree_random_loop(struct rbtree_node* p);

struct rbtree_node*
rbtree_random(struct rbtree* target)
{
  if (target->size == 0)
    return NULL;

  struct rbtree_node* result = rbtree_random_loop(target->root);
  return result;
}

#define INT_BITS (sizeof(int) * 8)

static inline struct rbtree_node*
rbtree_random_loop(struct rbtree_node* p)
{
  int random_bits_left = 0;
  int randval = 0;
  while (true) {
    if (p->left != NULL)
    {
      if (p->right != NULL)
      {
        // Both not null
        if (random_bits_left == 0) {
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
}

static void rbtree_print_loop(struct rbtree_node* node, int level);

void
rbtree_print(struct rbtree* target)
{
  if (target->size == 0)
    printf("TREE EMPTY\n");
  else
  {
    // printf("rbtree_print: %p\n", target);
    rbtree_print_loop(target->root, 0);
  }
}

static void
rbtree_print_loop(struct rbtree_node* node, int level)
{
  valgrind_assert(node != NULL);
  char buffer[level+16];
  char* p = &buffer[0];
  append(p, "+ ");
  for (int i = 0; i < level; i++)
    append(p, " ");
  append(p, "%c%"PRId64"", color(node), node->key);
  printf("%s\n", buffer);

  if (node->left == NULL && node->right == NULL)
    return;

  // recurse left
  if (node->left != NULL)
  {
    valgrind_assert_msg(node->left->parent == node,
                        "node: %c%"PRId64"->left is not linked back",
                        color(node), node->key);
    rbtree_print_loop(node->left, level+1);
  }
  else
    print_x(level+1);

  // recurse right
  if (node->right != NULL)
  {
    valgrind_assert_msg(node->right->parent == node,
                        "node: %c%"PRId64"->right is not linked back",
                        color(node), node->key);
    rbtree_print_loop(node->right, level+1);
  }
  else
    print_x(level+1);
}

static void rbtree_free_subtree(struct rbtree_node* node, rbtree_callback cb);

void
rbtree_clear(struct rbtree* target)
{
  rbtree_clear_callback(target, NULL);
}

void
rbtree_clear_callback(struct rbtree* target, rbtree_callback cb)
{
  if (target->size != 0)
  {
    rbtree_free_subtree(target->root, cb);
    // Reset to original state
    rbtree_init(target);
  }
}

static void
rbtree_free_subtree(struct rbtree_node* node, rbtree_callback cb)
{
  if (node->left != NULL)
    rbtree_free_subtree(node->left, cb);
  if (node->right != NULL)
    rbtree_free_subtree(node->right, cb);
  if (cb != NULL)
    cb(node, node->data);
  free(node);
}

void
rbtree_free(struct rbtree* target)
{
  rbtree_clear(target);
  free(target);
}
