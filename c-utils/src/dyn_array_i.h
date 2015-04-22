/*
 * Copyright 2015 University of Chicago and Argonne National Laboratory
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

#ifndef __DYN_ARRAY_I_H
#define __DYN_ARRAY_I_H

#include <stdbool.h>
#include <stddef.h>

struct dyn_array_i {
  int *arr;
  size_t size;
  size_t capacity;
};

static const struct dyn_array_i DYN_ARRAY_I_EMPTY = { NULL, 0, 0 };

bool dyn_array_i_init(struct dyn_array_i *da, size_t init_capacity);

/*
 * Add a value to end of array, expanding if needed.
 */
static inline bool dyn_array_i_add(struct dyn_array_i *da, int val);

/*
 * Expands the array to next size.
 */
bool dyn_array_i_expand(struct dyn_array_i *da);

/** Remove last element, fail silently if empty */
static inline void dyn_array_i_remove(struct dyn_array_i *da);

/** Remove all data */
static inline void dyn_array_i_clear(struct dyn_array_i *da);

/** Remove all data and free */
void dyn_array_i_release(struct dyn_array_i *da);

/*=============================*
 | Inline function definitions |
 *=============================*/

static inline bool dyn_array_i_add(struct dyn_array_i *da, int val)
{
  if (da->size >= da->capacity)
  {
    bool ok = dyn_array_i_expand(da);
    if (!ok)
    {
      return false;
    }
  }

  da->arr[da->size++] = val;
  return true;
}

static inline void dyn_array_i_remove(struct dyn_array_i *da)
{
  // Just remove, don't try to shrink
  if (da->size > 0)
  {
    da->size--;
  }
}

static inline void dyn_array_i_clear(struct dyn_array_i *da)
{
  // Just remove, don't try to shrink
  da->size = 0;
}

#endif // __DYN_ARRAY_I_H
