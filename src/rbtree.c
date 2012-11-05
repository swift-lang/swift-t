
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
    valgrind_fail("replace: P-!>N %li->%li\n", P->key, N->key);
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
  target->root = 0;
}

static inline struct rbtree_node*
create_node(long key, void* data)
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
  printf("rotate right: %li\n", N->key);
  struct rbtree_node* P = N->parent;
  if (P == NULL)
  {
    printf("root!\n");

  }
  struct rbtree_node* C = N->left;
  valgrind_assert(C != NULL);
  struct rbtree_node* X = C->right;

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
  printf("rotate right: result:\n");
  rbtree_print(target);
  printf("rotate right: ok.\n");
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
  printf("rotate left: %li\n", N->key);
  struct rbtree_node* P = N->parent;

  if (P == NULL)
  {
    printf("root!\n");
  }

  // struct rbtree_node* B = N->left;
  struct rbtree_node* C = N->right;
  valgrind_assert(C != NULL);
  struct rbtree_node* X = C->left;

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
  printf("rotate left: result:\n");
  rbtree_print(target);
  printf("rotate left: ok.\n");
}

static inline void insert_case1(struct rbtree* target,
                                struct rbtree_node* node);
static inline void insert_case2(struct rbtree* target,
                                struct rbtree_node* node);
static void insert_case3(struct rbtree* target,
                         struct rbtree_node* node);
static void insert_case4(struct rbtree* target,
                         struct rbtree_node* node);
static void insert_case5(struct rbtree* target,
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
  if (node->parent->color == BLACK)
    return;
  else
    insert_case3(target, node);
}

static void
insert_case3(struct rbtree* target, struct rbtree_node* node)
{
  struct rbtree_node* u = uncle(node);
  struct rbtree_node* g;

  if (u != NULL && u->color == RED)
  {
    node->parent->color = BLACK;
    u->color = BLACK;
    g = grandparent(node);
    g->color = RED;
    insert_case1(target, g);
  }
  else
  {
    insert_case4(target, node);
  }
}

static void
insert_case4(struct rbtree* target, struct rbtree_node* node)
{
  struct rbtree_node* g = grandparent(node);

  if ((node == node->parent->right) && (node->parent == g->left))
  {
    rotate_left(target, node->parent);
    node = node->left;
    rbtree_print(target);
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

bool
rbtree_add(struct rbtree* target, long key, void* data)
{
  printf("adding: %li %s\n", key, (char*) data);
  struct rbtree_node* node = create_node(key, data);
  if (node == NULL) return false;

  if (target->size == 0)
  {
    target->root = node;
    target->size = 1;
    insert_case1(target, node);
  }
  else
  {
    // Normal tree insertion
    struct rbtree_node* root = target->root;
    rbtree_add_loop(target, node, root);
    printf("added:\n");
    // rbtree_print(target);
    insert_case2(target, node);
  }

  printf("add(): ok.\n");
  return true;
}

static inline void
rbtree_add_loop(struct rbtree* target,
                struct rbtree_node* node,
                struct rbtree_node* p)
{
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
search_node_loop(struct rbtree_node* p, long key);

struct rbtree_node*
rbtree_search_node(struct rbtree* target, long key)
{
  if (target->size == 0)
    return NULL;

  return search_node_loop(target->root, key);
}

static inline struct rbtree_node*
search_node_loop(struct rbtree_node* p, long key)
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
rbtree_remove(struct rbtree* target, long key, void** data)
{
  printf("rbtree_remove(%li)\n", key);
  struct rbtree_node* N = search_node_loop(target->root, key);
  if (N == NULL)
    return false;

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

#define show_node(t) { \
  if (t == NULL) printf("%s: NULL\n", #t); \
  else printf("%s: %c%li\n", #t, color(t), t->key); \
}

static inline void swap_nodes(struct rbtree* target,
                              struct rbtree_node* N,
                              struct rbtree_node* R);
void
rbtree_remove_node(struct rbtree* target, struct rbtree_node* N)
{
  valgrind_assert(target->size != 0);

  printf("remove_node:\n");
  show_node(N);

  valgrind_assert(target->size >= 1);

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
  {
    swap_nodes(target, N, R);
  }
  else
    printf("R==NULL\n");

  delete_one_child(target, N);

  end:
  target->size--;
}

/** == replace_node - replaces keys and nodes (so pointers to keys
    still work but keeps colors intact */
static inline void
swap_nodes(struct rbtree* target,
           struct rbtree_node* N, struct rbtree_node* R)
{
  struct rbtree_node* P = N->parent;
  struct rbtree_node* B = N->left;
  struct rbtree_node* C = N->right;
  struct rbtree_node* X = R->left;
  struct rbtree_node* Y = R->right;

  printf("swap_nodes:\n");

  show_node(P);
  show_node(N);
  show_node(R);
  show_node(B);
  show_node(C);

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
  rbtree_print(target);

  printf("swap_nodes: ok.\n");
}

bool
rbtree_pop(struct rbtree* target, long* key, void** data)
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
  return result;
}

long
rbtree_leftmost_key(struct rbtree* target)
{
  if (target->size == 0)
    return 0;

  struct rbtree_node* node = rbtree_leftmost_loop(target->root);
  long result = node->key;
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

static void delete_case1(struct rbtree* target, struct rbtree_node* P,
                         struct rbtree_node* N);
static void delete_case2(struct rbtree* target, struct rbtree_node* P,
                         struct rbtree_node* N);
static void delete_case3(struct rbtree* target, struct rbtree_node* P,
                         struct rbtree_node* N);
static void delete_case4(struct rbtree* target, struct rbtree_node* P,
                         struct rbtree_node* N, struct rbtree_node* S);
static void delete_case5(struct rbtree* target, struct rbtree_node* P,
                         struct rbtree_node* N, struct rbtree_node* S);
static void delete_case6(struct rbtree* target, struct rbtree_node* P,
                         struct rbtree_node* N, struct rbtree_node* S);

/**
   Preconditions:
   * N has at most one non-null child
   * Tree size is 2 or more
 */
static inline void
delete_one_child(struct rbtree* target, struct rbtree_node* N)
{
  printf("delete_one_child:\n");
  show_node(N);

  valgrind_assert(target->size >= 2);
  valgrind_assert(N->right == NULL || N->left == NULL);

  rbtree_print(target);
  struct rbtree_node* C =
      (N->right == NULL) ? N->left : N->right;
  struct rbtree_node* P = N->parent;
  show_node(P);

  if (C == NULL)
  {
    printf("C: NULL\n");
    printf("NO CHILDREN\n");
    if (N->color == RED)
    {
      replace(P, N, NULL);
      return;
    }
  }

  if (C != NULL)
  {
    printf("C: %li\n", C->key);
    swap_nodes(target, N, C);
    struct rbtree_node* t = N;
    N = C;
    C = t;
  }

  show_node(P);
  show_node(N);
  show_node(C);
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

static void
delete_case1(struct rbtree* target,
             struct rbtree_node* P,
             struct rbtree_node* N)
{
  printf("delete_case1:\n");
  show_node(P);
  show_node(N);
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

static void
delete_case2(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N)
{
  printf("delete_case2: %li->%li\n", P->key, N->key);
  struct rbtree_node* S = sibling(P, N);
  show_node(S);

  if (S->color == RED)
  {
    P->color = RED;
    S->color = BLACK;
    if (N == P->left)
      rotate_left(target, P);
    else
      rotate_right(target, P);
  }
  delete_case3(target, P, N);
}

static void
delete_case3(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N)
{
  printf("delete_case3: %li->%li\n", P->key, N->key);
  struct rbtree_node* S = sibling(P, N);
  show_node(S);
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

static void
delete_case4(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N, struct rbtree_node* S)
{
  printf("delete_case4: %li->%li\n", P->key, N->key);
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

static void
delete_case5(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N, struct rbtree_node* S)
{
  printf("delete_case5: %li->%li\n", P->key, N->key);
  if  (S->color == BLACK) { /* this if statement is trivial,
due to case 2 (even though case 2 changed the sibling to a sibling's child,
the sibling's child can't be red, since no red parent can have a red child). */
    /* the following statements just force the red to be on the left of the left of the parent,
   or right of the right, so case six will rotate correctly. */
    if ((N == P->left) &&
        (S->right->color == BLACK) &&
        (S->left->color == RED))
    {
      /* this last test is trivial too due to cases 2-4. */
      S->color = RED;
      S->left->color = BLACK;
      rotate_right(target, S);
    }
    else if ((N == P->right) &&
               (S->left->color == BLACK) &&
               (S->right->color == RED))
    {
      /* this last test is trivial too due to cases 2-4. */
      S->color = RED;
      S->right->color = BLACK;
      rotate_left(target, S);
    }
  }
  delete_case6(target, P, N, S);
}

static void
delete_case6(struct rbtree* target, struct rbtree_node* P,
             struct rbtree_node* N, struct rbtree_node* S)
{
  printf("delete_case6: %li->%li\n", P->key, N->key);
  S->color = P->color;
  P->color = BLACK;

  if (N == P->left)
  {
    S->right->color = BLACK;
    rotate_left(target, P);
  }
  else
  {
    S->left->color = BLACK;
    rotate_right(target, P);
  }
}

static void
rbtree_print_loop(struct rbtree_node* node, int level);

void
rbtree_print(struct rbtree* target)
{
  if (target->size == 0)
    printf("TREE EMPTY\n");
  else
    rbtree_print_loop(target->root, 0);
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
  append(p, "%c%li", color(node), node->key);
  printf("%s\n", buffer);

  if (node->left == NULL && node->right == NULL)
    return;

  // recurse left
  if (node->left != NULL)
  {
    valgrind_assert_msg(node->left->parent == node,
                        "node: %c%li->left is not linked back",
                        color(node), node->key);
    rbtree_print_loop(node->left, level+1);
  }
  else
    print_x(level+1);

  // recurse right
  if (node->right != NULL)
  {
    valgrind_assert_msg(node->right->parent == node,
                        "node: %c%li->right is not linked back",
                        color(node), node->key);
    rbtree_print_loop(node->right, level+1);
  }
  else
    print_x(level+1);
}
