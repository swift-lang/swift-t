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
 * rbtree-template.h
 *  Created on: May 29, 2015
 *      Author: Tim Armstrong
 *
 * Based on rbtree.h
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 *
 * Red-black tree templated with key and value.
 */

#include "c-utils-types.h"
#include "rbtree-defs.h"

/*
  Construct specific names for this special heap.
  Preprocessor voodoo with three layers of macros was needed to
  paste tokens together correctly.
 */
#define RBTREE_NAME__(prefix, name) prefix ## name
#define RBTREE_NAME_(prefix, name) RBTREE_NAME__(prefix, name)
#define RBTREE_NAME(name) RBTREE_NAME_(RBTREE_PFX, name)

#define RBTREE_NODE RBTREE_NAME(node)
struct RBTREE_NODE
{
  struct RBTREE_NODE* parent;
  struct RBTREE_NODE* left;
  struct RBTREE_NODE* right;
  RBTREE_KEY_T key;
  RBTREE_VAL_T data;
  rbtree_color color;
};

struct RBTREE_TYPENAME
{
  int size;
  struct RBTREE_NODE* root;
};

/**
   rbtree iterator callback
   Returns true when iteration should stop (something was found)
 */
#define RBTREE_CALLBACK RBTREE_NAME(callback)
typedef bool (*RBTREE_CALLBACK)(struct RBTREE_NODE* node,
                                RBTREE_VAL_T user_data);

#define RBTREE_INIT RBTREE_NAME(init)
void RBTREE_INIT(struct RBTREE_TYPENAME* target);

/**
   @return false iff failed to allocate memory
 */
#define RBTREE_ADD RBTREE_NAME(add)
bool RBTREE_ADD(struct RBTREE_TYPENAME* target, RBTREE_KEY_T key, RBTREE_VAL_T data);

/**
   Create a node for use with RBTREE_ADD_NODE(), etc.
 */
#define RBTREE_NODE_CREATE RBTREE_NAME(node_create)
struct RBTREE_NODE* RBTREE_NODE_CREATE(RBTREE_KEY_T key, RBTREE_VAL_T data);

/**
   Add a node.  Key and value must be initialized
 */
#define RBTREE_ADD_NODE RBTREE_NAME(add_node)
void RBTREE_ADD_NODE(struct RBTREE_TYPENAME* target, struct RBTREE_NODE* node);

#define RBTREE_SEARCH_NODE RBTREE_NAME(search_node)
struct RBTREE_NODE* RBTREE_SEARCH_NODE(struct RBTREE_TYPENAME* target,
                                       RBTREE_KEY_T key);
/**
   Remove key from tree
   @param data If non-NULL, store data here
 */
#define RBTREE_REMOVE RBTREE_NAME(remove)
bool RBTREE_REMOVE(struct RBTREE_TYPENAME* target, RBTREE_KEY_T key, RBTREE_VAL_T* data);

/**
   Removes the node from the tree.  Does not free the node
 */
#define RBTREE_REMOVE_NODE RBTREE_NAME(remove_node)
void RBTREE_REMOVE_NODE(struct RBTREE_TYPENAME* target,
                        struct RBTREE_NODE* node);

/**
   Frees the node and the key
 */
#define RBTREE_FREE_NODE RBTREE_NAME(free_node)
void RBTREE_FREE_NODE(struct RBTREE_NODE* node);

#define RBTREE_POP RBTREE_NAME(pop)
bool RBTREE_POP(struct RBTREE_TYPENAME* target, RBTREE_KEY_T* key, RBTREE_VAL_T* data);

#define RBTREE_LEFTMOST RBTREE_NAME(leftmost)
struct RBTREE_NODE* RBTREE_LEFTMOST(struct RBTREE_TYPENAME* target);

#define RBTREE_LEFTMOST_KEY RBTREE_NAME(leftmost_key)
RBTREE_KEY_T RBTREE_LEFTMOST_KEY(struct RBTREE_TYPENAME* target);

#define RBTREE_RIGHTMOST RBTREE_NAME(rightmost)
struct RBTREE_NODE* RBTREE_RIGHTMOST(struct RBTREE_TYPENAME* target);

#define RBTREE_RIGHTMOST_KEY RBTREE_NAME(rightmost_key)
RBTREE_KEY_T RBTREE_RIGHTMOST_KEY(struct RBTREE_TYPENAME* target);

#define RBTREE_SEARCH_RANGE RBTREE_NAME(search_range)
struct RBTREE_NODE*
RBTREE_SEARCH_RANGE(struct RBTREE_TYPENAME* target,
                    RBTREE_KEY_T key);
/**
  Return next node in key order, or NULL if no such node.
 */
#define RBTREE_NEXT_NODE RBTREE_NAME(next_node)
struct RBTREE_NODE *
RBTREE_NEXT_NODE(struct RBTREE_NODE* node);

/**
  Return previous node in key order, or NULL if no such node.
 */
#define RBTREE_PREV_NODE RBTREE_NAME(prev_node)
struct RBTREE_NODE *
RBTREE_PREV_NODE(struct RBTREE_NODE* node);


/**
   Call callback on each rbtree node until the callback returns false
   Proceeds in-order
   Returns true iff a callback returned true
 */
#define RBTREE_ITERATOR RBTREE_NAME(iterator)
bool RBTREE_ITERATOR(struct RBTREE_TYPENAME* target, RBTREE_CALLBACK cb,
                     RBTREE_VAL_T user_data);

#define RBTREE_MOVE RBTREE_NAME(move)
bool RBTREE_MOVE(struct RBTREE_TYPENAME* target, RBTREE_KEY_T key_old,
                 RBTREE_KEY_T key_new);

#define RBTREE_RANDOM RBTREE_NAME(random)
struct RBTREE_NODE* RBTREE_RANDOM(struct RBTREE_TYPENAME* target);

#define RBTREE_PRINT RBTREE_NAME(print)
void RBTREE_PRINT(struct RBTREE_TYPENAME* target);

#define RBTREE_CLEAR RBTREE_NAME(clear)
void RBTREE_CLEAR(struct RBTREE_TYPENAME* target);

/*
  Clear all entires, calling callback function once per element.
  Return value of callback is ignored.
 */
#define RBTREE_CLEAR_CALLBACK RBTREE_NAME(clear_callback)
void RBTREE_CLEAR_CALLBACK(struct RBTREE_TYPENAME* target, RBTREE_CALLBACK cb);

#define RBTREE_FREE RBTREE_NAME(free)
void RBTREE_FREE(struct RBTREE_TYPENAME* target);

#define rbtree_size(T) (T->size)

#ifndef RBTREE_KEEP_DEFNS
#undef RBTREE_NODE
#undef RBTREE_CALLBACK
#undef RBTREE_INIT
#undef RBTREE_ADD
#undef RBTREE_NODE_CREATE
#undef RBTREE_ADD_NODE
#undef RBTREE_SEARCH_NODE
#undef RBTREE_REMOVE
#undef RBTREE_REMOVE_NODE
#undef RBTREE_POP
#undef RBTREE_LEFTMOST
#undef RBTREE_LEFTMOST_KEY
#undef RBTREE_ITERATOR
#undef RBTREE_MOVE
#undef RBTREE_RANDOM
#undef RBTREE_PRINT
#undef RBTREE_CLEAR
#undef RBTREE_CLEAR_CALLBACK
#undef RBTREE_FREE
#undef RBTREE_NAME__
#undef RBTREE_NAME_
#undef RBTREE_NAME

#undef RBTREE_KEY_T
#undef RBTREE_VAL_T
#undef RBTREE_TYPENAME
#undef RBTREE_PFX
#endif // RBTREE_KEEP_DEFNS
