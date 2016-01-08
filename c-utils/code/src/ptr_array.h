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

#ifndef __PTR_ARRAY_H
#define __PTR_ARRAY_H
/*
   ptr_array is a specialised data type that stores an expandable array
   of pointers with gaps and allows quick lookup of free cells in the
   array.  It is useful for storing an unordered collection of items
   in a memory-efficient way with with O(1) insert and O(1) delete.
 */

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

struct ptr_array {
  void **arr;
  uint32_t *free; // Unused cells in array, same size as arr

  uint32_t capacity; // allocated size of arr
  uint32_t free_count; // Number of free spots in array
};

#define PTR_ARRAY_EMPTY { NULL, NULL, 0, 0 }

bool ptr_array_init(struct ptr_array *pa, uint32_t init_capacity);
void ptr_array_clear(struct ptr_array *pa);
bool ptr_array_expand(struct ptr_array *pa, uint32_t new_capacity);

/*
  Get item from array, validating index.  Returns NULL if index out
  of range.
 */
static inline void *
ptr_array_get(struct ptr_array *pa, uint32_t idx);

/*
  Add data to array, expanding if necessary.
  idx: set to index of data (must be non-null)

  returns false if expanding of array fails
 */
static inline bool
ptr_array_add(struct ptr_array *pa, void *data, uint32_t *idx);

/*
  Remove data from array.
  Returns previous value.
  Note: should only be called if valid data actually present at idx,
      i.e. if ptr_array_add returned idx and it has not been removed
 */
static inline void *
ptr_array_remove(struct ptr_array *pa, uint32_t idx);


/*===================================
  Implementations of inline functions 
  ===================================*/

static inline void *
ptr_array_get(struct ptr_array *pa, uint32_t idx)
{
  return idx < pa->capacity ? pa->arr[idx] : NULL;
}

static inline bool
ptr_array_add(struct ptr_array *pa, void *data, uint32_t *idx)
{
  if (pa->free_count == 0)
  {
    // No free case - resize work array and free list
    uint32_t new_capacity = pa->capacity * 2;

    bool ok = ptr_array_expand(pa, new_capacity);
    if (!ok)
    {
      return false;
    }
  }

  *idx = pa->free[pa->free_count - 1];
  pa->free_count--;

  pa->arr[*idx] = data;

  return true;
}

static inline void *
ptr_array_remove(struct ptr_array *pa, uint32_t idx)
{
  void *result = pa->arr[idx];
  pa->arr[idx] = NULL;
  pa->free[pa->free_count++] = idx;

  return result;
}

#endif // __PTR_ARRAY_H
