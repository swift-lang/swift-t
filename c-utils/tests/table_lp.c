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
#include <stdlib.h>

#include <table_lp.h>
#include <assert.h>

static void null_cb(int64_t k, void *v);

int main() {

  table_lp T;
  bool ok;

  ok = table_lp_init(&T, 4);
  assert(ok);

  // force expand several times
  int N = 64;
  for (int i = 0; i < N; i++)
  {
    int64_t key = i;

    void *val = (void*)(long)i;
    ok = table_lp_add(&T, key, val);
    assert(ok);
    
    table_lp_dump(NULL, &T);

    // Check iteration works;
    int count = 0;
    TABLE_LP_FOREACH(&T, item)
    {
      count++;
    }
    printf("i=%i count=%i\n", i, count);
    assert(count == i + 1);
  }
  assert(T.size == N);

  for (int i = 0; i < N; i++)
  {
    // Lookup in different order
    int64_t key = ((i*29) + 30) % N;
    void *val;
    bool found = table_lp_search(&T, key, &val);
    assert(found);
    printf("Search: %"PRId64"=%li\n", key, (long)val);
    assert(((long)val) == key);
  }
  assert(T.size == N);
  
  for (int i = 0; i < N; i++)
  {
    // Remove in different order
    int64_t key = ((i*29) + 5) % N;
    void *val;
    bool found = table_lp_remove(&T, key, &val);
    assert(found);
    printf("Remove: %"PRId64"=%li\n", key, (long)val);
    assert(((long)val) == key);
  }
  assert(T.size == 0);

  // Free
  table_lp_free_callback(&T, false, NULL);
  
  // Rebuild
  N = 512;
  // Force many hash collisions
  table_lp_init_custom(&T, 2, 32.0);
  for (int i = 0; i < N; i++)
  {
    int64_t key = ((i*9) + 12) % 16;

    void *val = (void*)((long)key);
    ok = table_lp_add(&T, key, val);
    assert(ok);
    
    void *search_val;
    ok = table_lp_search(&T, key, &search_val);
    assert(ok);
    assert(val == search_val);
  }
  assert(T.size == N);

  // Try printing
  printf("\n\ntable_lp_dump:\n");
  table_lp_dump("%p", &T);
  printf("\n\ntable_lp_dump_keys:\n");
  table_lp_dumpkeys(&T);
  
  // This should free all memory
  table_lp_free_callback(&T, false, null_cb);

  printf("DONE\n");
}

static void null_cb(int64_t k, void *v)
{
  // Do nothing
}
