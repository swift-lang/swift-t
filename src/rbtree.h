
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

/**
   rbtree iterator callback
   Returns true when iteration should stop (something was found)
 */
typedef bool (*rbtree_callback)(struct rbtree_node* node,
                                void* user_data);

void rbtree_init(struct rbtree* target);

/**
   @return false iff failed to allocate memory
 */
bool rbtree_add(struct rbtree* target, long key, void* data);

void rbtree_add_node(struct rbtree* target, struct rbtree_node* node);

struct rbtree_node* rbtree_search_node(struct rbtree* target,
                                       long key);

/**
   Remove key from tree
   @param data If non-NULL, store data here
 */
bool rbtree_remove(struct rbtree* target, long key, void** data);

/**
   Removes the node from the tree.  Does not free the node
 */
void rbtree_remove_node(struct rbtree* target,
                        struct rbtree_node* node);

bool rbtree_pop(struct rbtree* target, long* key, void** data);

struct rbtree_node* rbtree_leftmost(struct rbtree* target);

long rbtree_leftmost_key(struct rbtree* target);

/**
   Call callback on each rbtree node until the callback returns false
   Proceeds in-order
   Returns true iff a callback returned true
 */
bool rbtree_iterator(struct rbtree* target, rbtree_callback cb,
                     void* user_data);

bool rbtree_move(struct rbtree* target, long key_old, long key_new);

struct rbtree_node* rbtree_random(struct rbtree* target);

void rbtree_print(struct rbtree* target);

void rbtree_clear(struct rbtree* target);

void rbtree_free(struct rbtree* target);

#define rbtree_size(T) (T->size)

#endif
