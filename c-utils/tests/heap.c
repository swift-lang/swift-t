
#include <stdio.h>

// Cf. heap.h
#define HEAP_SKIP_ASSERTS
#include "heap.h"

int main() {
  heap h;

  heap_init(&h, 128);

  heap_add(&h, 1, 1);
  heap_add(&h, 234, 234);
  heap_add(&h, 3, 3);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_decrease_key(&h, 0, h.array[0].key - 1);
  heap_add(&h, 453, 453);
  heap_add(&h, 2, 2);
  heap_add(&h, -1, -1);
  heap_add(&h, 3, 3);
  heap_decrease_key(&h, 4, h.array[4].key - 1);
  heap_increase_key(&h, 2, h.array[2].key + 2);
  heap_increase_key(&h, 0, h.array[0].key + 21);
  heap_add(&h, 234, 234);
  heap_add(&h, 453, 453);
  heap_decrease_key(&h, 4, h.array[4].key - 1);
  heap_decrease_key(&h, 4, h.array[4].key - 1);
  heap_decrease_key(&h, 7, h.array[7].key - 12333);
  heap_add(&h, 2, 2);
  heap_add(&h, -1, -1);
  heap_add(&h, 234, 234);
  heap_add(&h, 54, 54);
  heap_add(&h, 254, 253);

  heap_check(&h);

  // Heap sort
  while (heap_size(&h) > 0) {
    printf("(%li, %li)\n", heap_root_key(&h),
                           heap_root_val(&h));
    heap_check(&h);
    heap_del_root(&h);
  }
  printf("DONE\n");
}
