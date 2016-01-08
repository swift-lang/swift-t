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
 * rbtree.h
 *
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 *
 * Red-black tree with 64-bit integer keys and pointer values
 */

#ifndef RBTREE_H
#define RBTREE_H

#define RBTREE_KEY_T int64_t
#define RBTREE_VAL_T void*
#define RBTREE_TYPENAME rbtree
#define RBTREE_PFX rbtree_

#include "rbtree-template.h"

#endif

