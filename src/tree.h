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
 * tree.h
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 *
 *  Binary tree, indexed by 64-bit signed int key
 *  Smaller entry on left
 *  Primary use will be popping leftmost node (priority queue)
 */

#ifndef TREE_H
#define TREE_H

#include <stdbool.h>
#include "c-utils-types.h"

typedef int64_t tree_key_t;

struct tree_node
{
  struct tree_node* parent;
  struct tree_node* left;
  struct tree_node* right;
  tree_key_t key;
  void* data;
};

struct tree
{
  int size;
  struct tree_node* root;
};

void tree_init(struct tree* target);

void tree_add(struct tree* target, tree_key_t key, void* data);

void tree_add_node(struct tree* target, struct tree_node* node);

bool tree_pop(struct tree* target, tree_key_t* key, void** data);

struct tree_node* tree_leftmost(struct tree* target);

tree_key_t tree_leftmost_key(struct tree* target);

bool tree_move(struct tree* target, tree_key_t key_old, tree_key_t key_new);

struct tree_node* tree_random(struct tree* target);

void tree_remove_node(struct tree* target, struct tree_node* node);

int tree_size(struct tree* target);

void tree_clear(struct tree* target);

void tree_free(struct tree* target);

void tree_print(struct tree* target);

#endif
