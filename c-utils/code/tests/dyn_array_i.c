#include <stdio.h>

#include "src/c-utils-tests.h"

#include <dyn_array_i.h>

int main()
{
  struct dyn_array_i a = DYN_ARRAY_I_EMPTY;

  bool ok = dyn_array_i_init(&a, 64);
  ASSERT_TRUE(ok);
  ASSERT_TRUE(a.capacity == 64);
  ASSERT_TRUE(a.arr != NULL);
  ASSERT_TRUE(a.size == 0);


  for (int i = 0; i < 64; i++)
  {
    ok = dyn_array_i_add(&a, i);
    ASSERT_TRUE(ok);
  }
  ASSERT_TRUE(a.capacity == 64);
  ASSERT_TRUE(a.size == 64);
  ASSERT_TRUE(a.arr[30] == 30);

  ok = dyn_array_i_add(&a, 64);
  ASSERT_TRUE(ok);
  ASSERT_TRUE(a.arr[64] == 64);
  ASSERT_TRUE(a.size == 65);
  ASSERT_TRUE(a.capacity > 64);

  for (int i = 0; i < 3; i++)
  {
    dyn_array_i_remove(&a);
  }
  ASSERT_TRUE(a.size == 62);
  for (int i = 0; i < 62; i++)
  {
    dyn_array_i_remove(&a);
  }
  ASSERT_TRUE(a.size == 0);

  // Try to remove more
  dyn_array_i_remove(&a);
  ASSERT_TRUE(a.size == 0);

  dyn_array_i_release(&a);
  ASSERT_TRUE(a.capacity == 0);
  ASSERT_TRUE(a.size == 0);
  ASSERT_TRUE(a.arr == NULL);

  ok = dyn_array_i_init(&a, 64);
  ASSERT_TRUE(ok);

  ok = dyn_array_i_add(&a, 10);
  ASSERT_TRUE(ok);

  // Check that clear clears but does not free
  dyn_array_i_clear(&a);
  ASSERT_TRUE(a.size == 0);
  ASSERT_TRUE(a.capacity > 0);
  ASSERT_TRUE(a.arr != NULL);

  printf("DONE");
  return 0;
}
