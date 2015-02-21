#include <stdio.h>

#include <assert.h>

#include <dyn_array_i.h>

int main()
{
  struct dyn_array_i a = DYN_ARRAY_I_EMPTY;

  bool ok = dyn_array_i_init(&a, 64);
  assert(ok);
  assert(a.capacity == 64);
  assert(a.arr != NULL);
  assert(a.size == 0);


  for (int i = 0; i < 64; i++)
  {
    ok = dyn_array_i_add(&a, i);
    assert(ok);
  }
  assert(a.capacity == 64);
  assert(a.size == 64);
  assert(a.arr[30] == 30);

  ok = dyn_array_i_add(&a, 64);
  assert(ok);
  assert(a.arr[64] == 64);
  assert(a.size == 65);
  assert(a.capacity > 64);

  for (int i = 0; i < 3; i++)
  {
    dyn_array_i_remove(&a);
  }
  assert(a.size == 62);
  for (int i = 0; i < 62; i++)
  {
    dyn_array_i_remove(&a);
  }
  assert(a.size == 0);

  // Try to remove more
  dyn_array_i_remove(&a);
  assert(a.size == 0);

  dyn_array_i_release(&a);
  assert(a.capacity == 0);
  assert(a.size == 0);
  assert(a.arr == NULL);

  ok = dyn_array_i_init(&a, 64);
  assert(ok);

  ok = dyn_array_i_add(&a, 10);
  assert(ok);

  // Check that clear clears but does not free
  dyn_array_i_clear(&a);
  assert(a.size == 0);
  assert(a.capacity > 0);
  assert(a.arr != NULL);

  printf("DONE");
  return 0;
}
