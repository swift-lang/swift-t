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

#include "dyn_array_i.h"

#include <stdlib.h>

bool dyn_array_i_init(struct dyn_array_i *da, size_t init_capacity)
{
  da->arr = malloc(sizeof(da->arr[0]) * init_capacity);
  if (da->arr == NULL)
  {
    return false;
  }

  da->size = 0;
  da->capacity = init_capacity;

  return true;
}

bool dyn_array_i_expand(struct dyn_array_i *da)
{
  /* Use growth factor of 1.5, ensuring we actually grow */
  size_t new_capacity;
  if (da->capacity >= 2)
  {
    new_capacity = da->capacity + da->capacity / 2;
  }
  else
  {
    new_capacity = 2;
  }

  int *new_arr = realloc(da->arr, new_capacity * sizeof(da->arr[0]));
  if (new_arr == NULL)
  {
    return false;
  }

  da->arr = new_arr;
  da->capacity = new_capacity;

  return true;
}

void dyn_array_i_release(struct dyn_array_i *da)
{
  free(da->arr);
  da->arr = NULL;
  da->size = da->capacity = 0;
}
