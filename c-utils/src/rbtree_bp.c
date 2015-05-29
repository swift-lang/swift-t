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

static const rbtree_bp_key_t rbtree_key_invalid = { NULL, 0 };

static bool
rbtree_bp_key_set(rbtree_bp_key_t *dst, rbtree_bp_key_t src)
{
  dst->key = malloc(src.length);
  if (dst->key == NULL)
  {
    return false;
  }
  memcpy(dst->key, src.key, src.length);
  dst->length = src.length;

  return true;
}

#define RBTREE_KEY_INVALID rbtree_key_invalid;
#define RBTREE_KEY_LEQ(a, b) bin_key_leq((a).key, (a).length, \
                                         (b).key, (b).length)
#define RBTREE_KEY_EQ(a, b) bin_key_eq((a).key, (a).length, \
                                       (b).key, (b).length)
#define RBTREE_KEY_COPY(a, b) rbtree_bp_key_set(&(a), (b))
#define RBTREE_KEY_FREE(k) free((k).key)

#define RBTREE_KEY_PRNF "%p (%zu)"
#define RBTREE_KEY_PRNA(k) (k).key, (k).length

#include "rbtree-template.c"

