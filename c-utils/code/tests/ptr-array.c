
/**
 * ptr-array.c
 *
 * Test ptr-array functionality
 *
 *  Created on: Feb 17, 2015
 *      Author: wozniak
 *      Author: armstrong
 */

#include <stdio.h>

#include "src/c-utils-tests.h"

#include <ptr_array.h>

int
main()
{
  bool ok;
  struct ptr_array pa;
  ptr_array_init(&pa, 8);
  ASSERT_TRUE(pa.arr != NULL);
  ASSERT_TRUE(pa.capacity == 8);
  ASSERT_TRUE(pa.free_count == 8);

  int n = 17;
  uint32_t idxs[n];
  for (size_t i = 0; i < n; i++)
  {
    ok = ptr_array_add(&pa, (void*)i, &idxs[i]);
    ASSERT_TRUE(ok);

    ASSERT_TRUE(idxs[i] >= 0);
    ASSERT_TRUE(idxs[i] < pa.capacity);
    ASSERT_TRUE(pa.arr[idxs[i]] == (void*)i);
  }
  ASSERT_TRUE(pa.capacity >= n);
  ASSERT_TRUE(pa.free_count == pa.capacity - n);

  // Now, remove a subset of values
  int skip = 3;
  int removed = 0;
  for (size_t i = 0; i < n; i += skip)
  {
    void *val = ptr_array_remove(&pa, idxs[i]);
    ASSERT_TRUE((size_t)val == i);
    removed++;
  }
  ASSERT_TRUE(pa.capacity >= n - removed);
  ASSERT_TRUE(pa.free_count == pa.capacity - n + removed);

  for (size_t i = 0; i < n; i++)
  {
    void *val = ptr_array_get(&pa, idxs[i]);
    printf("val = %zu\n", (size_t)val);
    if (i % skip == 0)
    {
      // Should have been removed
      ASSERT_TRUE(val == NULL);
    }
    else
    {
      ASSERT_TRUE(val == (void*)i);
    }
  }

  uint32_t prev_capacity = pa.capacity;
  uint32_t to_readd = n / skip;
  // Finally, add back in some values
  for (size_t i = 0; i < to_readd; i++)
  {
    uint32_t idx;
    ok = ptr_array_add(&pa, (void*)i, &idx);
    ASSERT_TRUE(ok);
    ASSERT_TRUE(ptr_array_get(&pa, idx) == (void*)i);
  }

  ASSERT_TRUE(pa.free_count == pa.capacity - (n - removed + to_readd));
  // Should not have expanded - was enough space
  ASSERT_TRUE(pa.capacity == prev_capacity);

  ptr_array_clear(&pa);
  ASSERT_TRUE(pa.arr == NULL);
  ASSERT_TRUE(pa.capacity == 0);
  ASSERT_TRUE(pa.free_count == 0);
  printf("DONE\n");
  return 0;
}
