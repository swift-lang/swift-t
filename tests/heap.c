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

#include <stdio.h>

// Cf. heap.h
#define HEAP_SKIP_ASSERTS
#include "heap.h"

int main() {
  heap h;

  heap_init(&h, 128);

  heap_add(&h, 1, (void *)1);
  heap_add(&h, 234, (void *)234);
  heap_add(&h, 3, (void *)3);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_add(&h, 453, (void *)453);
  heap_add(&h, 2, (void *)2);
  heap_add(&h, -1, (void *)-1);
  heap_add(&h, 3, (void *)3);
  heap_decrease_key(&h, 4, h.array[4].key - 1);
  heap_increase_key(&h, 2, h.array[2].key + 2);
  heap_increase_key(&h, 0, h.array[0].key + 21);
  heap_add(&h, 234, (void *)234);
  heap_add(&h, 453, (void *)453);
  heap_decrease_key(&h, 4, h.array[4].key - 1);
  heap_decrease_key(&h, 4, h.array[4].key - 1);
  heap_decrease_key(&h, 7, h.array[7].key - 12333);
  heap_add(&h, 2, (void *)2);
  heap_add(&h, -1, (void *)-1);
  heap_add(&h, 234, (void *)234);
  heap_add(&h, 54, (void *)54);
  heap_add(&h, 254, (void *)253);

  heap_check(&h);

  // Heap sort
  while (heap_size(&h) > 0) {
    printf("(%i, %li)\n", heap_root_key(&h),
                           (long)heap_root_val(&h));
    heap_check(&h);
    heap_del_root(&h);
  }
  printf("DONE\n");
}
