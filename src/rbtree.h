
/*
 * rbtree.h
 *
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 */

#ifndef RBTREE_H
#define RBTREE_H

typedef enum
{
  RED, BLACK
} rbtree_color;

struct rbtree_node
{
  struct rbtree_node* parent;
  struct rbtree_node* left;
  struct rbtree_node* right;
  long key;
  void* data;
  rbtree_color color;
};

struct rbtree
{
  int size;
  struct rbtree_node* root;
};

void rbtree_init(struct rbtree* target);

bool rbtree_add(struct rbtree* target, long key, void* data);

struct rbtree_node* rbtree_search_node(struct rbtree* target,
                                       long key);

bool rbtree_remove(struct rbtree* target, long key, void** data);

void rbtree_remove_node(struct rbtree* target,
                        struct rbtree_node* node);

bool rbtree_pop(struct rbtree* target, long* key, void** data);

struct rbtree_node* rbtree_leftmost(struct rbtree* target);

long rbtree_leftmost_key(struct rbtree* target);

void rbtree_print(struct rbtree* target);

#endif
