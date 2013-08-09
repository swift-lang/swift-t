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
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 */

#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>

#include "tools.h"

#include "tree-common.h"
#include "tree.h"

void
tree_init(struct tree* target)
{
  target->size = 0;
  target->root = NULL;
}

void
tree_add(struct tree* target, tree_key_t key, void* data)
{
  struct tree_node* node = malloc(sizeof(struct tree_node));
  assert(node);
  node->key = key;
  node->data = data;
  node->left = NULL;
  node->right = NULL;

  tree_add_node(target, node);
}

static inline void tree_add_loop(struct tree_node* node,
                                 struct tree_node* p);

void
tree_add_node(struct tree* target, struct tree_node* node)
{
  struct tree_node* p = target->root;
  if (p == NULL)
  {
    target->root = node;
    node->parent = NULL;
    assert(target->size == 0);
    target->size = 1;
    return;
  }

  tree_add_loop(node, p);
  target->size++;
}

static inline void
tree_add_loop(struct tree_node* node, struct tree_node* p)
{
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
        break;
      }
      p = p->right;
    }
  }
}

bool
tree_pop(struct tree* target, tree_key_t* key, void** data)
{
  struct tree_node* node = tree_leftmost(target);
  if (node == NULL)
    return false;
  *key = node->key;
  *data = node->data;
  tree_remove_node(target, node);
  free(node);
  return true;
}

static inline struct tree_node*
tree_leftmost_loop(struct tree_node* p);

struct tree_node*
tree_leftmost(struct tree* target)
{
  if (target->size == 0)
    return NULL;

  struct tree_node* result = tree_leftmost_loop(target->root);
  return result;
}

tree_key_t
tree_leftmost_key(struct tree* target)
{
  if (target->size == 0)
    return 0;

  struct tree_node* node = tree_leftmost_loop(target->root);
  tree_key_t result = node->key;
  return result;
}

static inline struct tree_node*
tree_leftmost_loop(struct tree_node* p)
{
  struct tree_node* result = NULL;
  while (true)
  {
    if (p->left == NULL)
    {
      result = p;
      break;
    }
    p = p->left;
  }
  return result;
}

struct tree_node* tree_search_node_loop(struct tree_node* p,
                                        tree_key_t key);

struct tree_node*
tree_search_node(struct tree* target, tree_key_t key)
{
  if (target->size == 0)
    return NULL;

  return tree_search_node_loop(target->root, key);
}

struct tree_node*
tree_search_node_loop(struct tree_node* p, tree_key_t key)
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

bool
tree_move(struct tree* target, tree_key_t key_old, tree_key_t key_new)
{
  struct tree_node* p = tree_search_node(target, key_old);
  if (p == NULL)
    return false;
  tree_remove_node(target, p);
  p->key = key_new;
  p->left = NULL;
  p->right = NULL;
  tree_add_node(target, p);

  return true;
}

static inline struct tree_node* tree_random_loop(struct tree_node* p);

struct tree_node*
tree_random(struct tree* target)
{
  if (target->size == 0)
    return NULL;

  struct tree_node* result = tree_random_loop(target->root);
  return result;
}

static inline struct tree_node*
tree_random_loop(struct tree_node* p)
{
  if (p->left == NULL && p->right == NULL)
    // Leaf
    return p;

  if (p->left == NULL)
    return tree_random_loop(p->right);
  else if (p->right == NULL)
    return tree_random_loop(p->left);

  bool b = random_bool();
  if (b)
    return tree_random_loop(p->right);
  return tree_random_loop(p->left);
}

static inline tree_side
which_side(struct tree_node* parent, struct tree_node* child)
{
  if (parent == NULL)
    return ROOT;
  else if (parent->left == child)
    return LEFT;
  else if (parent->right == child)
    return RIGHT;
  return NEITHER;
}

void
tree_remove_node(struct tree* target, struct tree_node* node)
{
  valgrind_assert(target->size != 0);

  struct tree_node* replacement = NULL;

  if (node->left != NULL && node->right != NULL)
  {
    replacement = node->left;
    struct tree_node* branch = node->right;
    tree_add_loop(branch, replacement->right);
  }
  else if (node->left == NULL)
  {
    replacement = node->right;
  }
  else if (node->right == NULL)
  {
    replacement = node->left;
  }

  if (node == target->root)
    target->root = replacement;

  tree_side s = which_side(node->parent, node);

  assert(s != NEITHER);
  if (s == LEFT)
    node->parent->left = replacement;
  else if (s == RIGHT)
    node->parent->right = replacement;
  if (replacement != NULL)
    replacement->parent = node->parent;

  // Do nothing if TREE_ROOT

  target->size--;

  return;
}

int
tree_size(struct tree* target)
{
  return target->size;
}

static void tree_free_subtree(struct tree_node* node);

void
tree_clear(struct tree* target)
{
  if (target->size != 0)
    tree_free_subtree(target->root);
  tree_init(target);
}

static void
tree_free_subtree(struct tree_node* node)
{
  if (node->left != NULL)
    tree_free_subtree(node->left);
  if (node->right != NULL)
    tree_free_subtree(node->right);
  free(node);
}

void
tree_free(struct tree* target)
{
  tree_clear(target);
  free(target);
}

static void
tree_print_loop(struct tree_node* node, int level);

void
tree_print(struct tree* target)
{
  if (target->size == 0)
    printf("TREE EMPTY\n");
  else
    tree_print_loop(target->root, 0);
}

static void
tree_print_loop(struct tree_node* node, int level)
{
  char buffer[level];
  int i;
  for (i = 0; i < level; i++)
    buffer[i] = ' ';
  sprintf(&buffer[i], "%"PRId64, node->key);
  printf("%s\n", buffer); // fflush(stdout);

  if (node->left == NULL && node->right == NULL)
    return;

  if (node->left != NULL)
    tree_print_loop(node->left, level+1);
  else
    print_x(level+1);
  if (node->right != NULL)
    tree_print_loop(node->right, level+1);
  else
    print_x(level+1);
}
