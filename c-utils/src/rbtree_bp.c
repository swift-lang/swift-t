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
 * rbtree-template.c
 *  Created on: May 29, 2015
 *      Author: Tim Armstrong
 *
 * Based on rbtree.c
 *
 *  Created on: Oct 26, 2012
 *      Author: wozniak
 *
 */

#define RBTREE_KEEP_DEFNS
#include "rbtree_bp.h"
#include "binkeys.h"

#include <stdlib.h>

static const binkey_packed_t rbtree_key_invalid = { NULL, 0 };

#define RBTREE_KEY_INVALID rbtree_key_invalid;
#define RBTREE_KEY_LEQ(a, b) binkey_packed_leq(&(a), &(b))
#define RBTREE_KEY_EQ(a, b) binkey_packed_eq(&(a), &(b))
#define RBTREE_KEY_COPY(a, b) binkey_packed_copy(&(a), &(b))
#define RBTREE_KEY_FREE(k) binkey_packed_free(&(k))

#define RBTREE_KEY_PRNF "%.*s (%zu)"
#define RBTREE_KEY_PRNA(k) (int)binkey_packed_len(&(k)), \
     (const char*)binkey_packed_get(&(k)), binkey_packed_len(&(k))

#include "rbtree-template.c"

