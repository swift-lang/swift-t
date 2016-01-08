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

#include "ptr_array.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>

static inline void
ptr_array_reset(struct ptr_array *pa)
{
  pa->arr = NULL;
  pa->free = NULL;
  pa->capacity = pa->free_count = 0;
}

bool ptr_array_init(struct ptr_array *pa, uint32_t init_capacity)
{
  ptr_array_reset(pa);
  return ptr_array_expand(pa, init_capacity);
}

void ptr_array_clear(struct ptr_array *pa)
{
  free(pa->arr);
  free(pa->free);

  ptr_array_reset(pa);
}

bool ptr_array_expand(struct ptr_array *pa, uint32_t new_capacity)
{
  assert(pa->free_count == 0);

  void **new_arr = realloc(pa->arr,
              new_capacity * sizeof(pa->arr[0]));
  if (new_arr == NULL)
  {
    return false;
  }
  pa->arr = new_arr;

  uint32_t *new_free = realloc(pa->free,
              new_capacity * sizeof(pa->free[0]));
  if (new_free == NULL)
  {
    return false;
  }
  pa->free = new_free;

  uint32_t old_capacity = pa->capacity;

  pa->capacity = new_capacity;
  memset(&pa->arr[old_capacity], 0, (new_capacity - old_capacity)
                                     * sizeof(pa->arr[0]));

  // Add new unused to free list
  pa->free_count = new_capacity - old_capacity;
  pa->free = new_free;
  for (uint32_t i = 0; i < pa->free_count; i++)
  {
    uint32_t unused_idx = old_capacity + i;
    pa->free[i] = unused_idx;
    assert(pa->arr[unused_idx] == NULL);
  }

  return true;
}
