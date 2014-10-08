
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
 * rbtree.h
 *
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 *
 * Red-black tree
 */

#ifndef RBTREE_H
#define RBTREE_H

#include "c-utils-types.h"

typedef enum
{
  RED, BLACK
} rbtree_color;

typedef int64_t rbtree_key_t;

struct rbtree_node
{
  struct rbtree_node* parent;
  struct rbtree_node* left;
  struct rbtree_node* right;
  rbtree_key_t key;
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
bool rbtree_add(struct rbtree* target, rbtree_key_t key, void* data);

/**
   Add a node.  Key and value must be initialized
 */
void rbtree_add_node(struct rbtree* target, struct rbtree_node* node);

struct rbtree_node* rbtree_search_node(struct rbtree* target,
                                       rbtree_key_t key);

/**
   Remove key from tree
   @param data If non-NULL, store data here
 */
bool rbtree_remove(struct rbtree* target, rbtree_key_t key, void** data);

/**
   Removes the node from the tree.  Does not free the node
 */
void rbtree_remove_node(struct rbtree* target,
                        struct rbtree_node* node);

bool rbtree_pop(struct rbtree* target, rbtree_key_t* key, void** data);

struct rbtree_node* rbtree_leftmost(struct rbtree* target);

rbtree_key_t rbtree_leftmost_key(struct rbtree* target);

/**
   Call callback on each rbtree node until the callback returns false
   Proceeds in-order
   Returns true iff a callback returned true
 */
bool rbtree_iterator(struct rbtree* target, rbtree_callback cb,
                     void* user_data);

bool rbtree_move(struct rbtree* target, rbtree_key_t key_old,
                 rbtree_key_t key_new);

struct rbtree_node* rbtree_random(struct rbtree* target);

void rbtree_print(struct rbtree* target);

void rbtree_clear(struct rbtree* target);

/*
  Clear all entires, calling callback function once per element.
  Return value of callback is ignored.
 */
void rbtree_clear_callback(struct rbtree* target, rbtree_callback cb);

void rbtree_free(struct rbtree* target);

#define rbtree_size(T) (T->size)

#endif
