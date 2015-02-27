
/**
 * ptr-array.c
 *
 * Test ptr-array functionality
 *
 *  Created on: Feb 17, 2015
 *      Author: wozniak
 *      Author: armstrong
 */

#include <assert.h>
#include <stdio.h>

#include <ptr_array.h>

int
main()
{
  bool ok;
  struct ptr_array pa;
  ptr_array_init(&pa, 8);
  assert(pa.arr != NULL);
  assert(pa.capacity == 8);
  assert(pa.free_count == 8);

  int n = 17;
  uint32_t idxs[n];
  for (size_t i = 0; i < n; i++)
  {
    ok = ptr_array_add(&pa, (void*)i, &idxs[i]);
    assert(ok);

    assert(idxs[i] >= 0);
    assert(idxs[i] < pa.capacity);
    assert(pa.arr[idxs[i]] == (void*)i);
  }
  assert(pa.capacity >= n);
  assert(pa.free_count == pa.capacity - n);

  // Now, remove a subset of values
  int skip = 3;
  int removed = 0;
  for (size_t i = 0; i < n; i += skip)
  {
    void *val = ptr_array_remove(&pa, idxs[i]);
    assert((size_t)val == i);
    removed++;
  }
  assert(pa.capacity >= n - removed);
  assert(pa.free_count == pa.capacity - n + removed);

  for (size_t i = 0; i < n; i++)
  {
    void *val = ptr_array_get(&pa, idxs[i]);
    printf("val = %zu\n", (size_t)val);
    if (i % skip == 0)
    {
      // Should have been removed
      assert(val == NULL);
    }
    else
    {
      assert(val == (void*)i);
    }
  }

  uint32_t prev_capacity = pa.capacity;
  uint32_t to_readd = n / skip;
  // Finally, add back in some values
  for (size_t i = 0; i < to_readd; i++)
  {
    uint32_t idx;
    ok = ptr_array_add(&pa, (void*)i, &idx);
    assert(ok);
    assert(ptr_array_get(&pa, idx) == (void*)i);
  }

  assert(pa.free_count == pa.capacity - (n - removed + to_readd));
  // Should not have expanded - was enough space
  assert(pa.capacity == prev_capacity);

  ptr_array_clear(&pa);
  assert(pa.arr == NULL);
  assert(pa.capacity == 0);
  assert(pa.free_count == 0);
  printf("DONE\n");
  return 0;
}
