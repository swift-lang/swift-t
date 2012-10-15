
/*
 * tree.h
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 *
 *  Binary tree, indexed by long key
 *  Smaller entry on left
 *  Primary use will be popping leftmost node (priority queue)
 */

#ifndef TREE_H
#define TREE_H

#include <stdbool.h>

struct tree_node
{
  struct tree_node* parent;
  struct tree_node* left;
  struct tree_node* right;
  long key;
  void* data;
};

struct tree
{
  int size;
  struct tree_node* root;
};

void tree_init(struct tree* target);

void tree_add(struct tree* target, long key, void* data);

void tree_add_node(struct tree* target, struct tree_node* node);

bool tree_pop(struct tree* target, long* key, void** data);

struct tree_node* tree_leftmost(struct tree* target);

long tree_leftmost_key(struct tree* target);

bool tree_move(struct tree* target, long key_old, long key_new);

struct tree_node* tree_random(struct tree* target);

void tree_remove_node(struct tree* target, struct tree_node* node);

int tree_size(struct tree* target);

void tree_clear(struct tree* target);

void tree_free(struct tree* target);

void tree_print(struct tree* target);

#endif
