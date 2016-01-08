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

#include "src/c-utils-tests.h"

#include <table_bp.h>

static size_t make_key(char *out, int n);
static void null_cb(const void *k, size_t kl, void *v);

int main() {

  table_bp T;
  bool ok;

  ok = table_bp_init(&T, 4);
  ASSERT_TRUE(ok);

  // force expand several times
  int N = 64;
  for (int i = 0; i < N; i++)
  {
    char key[128];
    size_t key_len = make_key(key, i);

    void *val = (void*)(long)i;
    ok = table_bp_add(&T, key, key_len, val);
    ASSERT_TRUE(ok);

    // Check iteration works;
    int count = 0;
    TABLE_BP_FOREACH(&T, item)
    {
      count++;
    }
    printf("i=%i count=%i\n", i, count);
    ASSERT_TRUE(count == i + 1);
  }
  ASSERT_TRUE(T.size == N);

  for (int i = 0; i < N; i++)
  {
    // Lookup in different order
    int j = ((i*29) + 30) % N;
    char key[128];
    size_t key_len = make_key(key, j);
    void *val;
    bool found = table_bp_search(&T, key, key_len, &val);
    ASSERT_TRUE(found);
    printf("Search: %.*s=%li\n", (int)key_len, key, (long)val);
    ASSERT_TRUE(((long)val) == j);
  }
  ASSERT_TRUE(T.size == N);
  
  for (int i = 0; i < N; i++)
  {
    // Remove in different order
    int j = ((i*29) + 5) % N;
    char key[128];
    size_t key_len = make_key(key, j);
    void *val;
    bool found = table_bp_remove(&T, key, key_len, &val);
    ASSERT_TRUE(found);
    printf("Remove: %.*s=%li\n", (int)key_len, key, (long)val);
    ASSERT_TRUE(((long)val) == j);
  }
  ASSERT_TRUE(T.size == 0);

  // Free
  table_bp_free_callback(&T, false, NULL);
  
  // Rebuild
  N = 128;
  // Force many hash collisions
  table_bp_init_custom(&T, 2, 1000.0);
  for (int i = 0; i < N; i++)
  {
    int j = ((i*9) + 12) % 16;
    char key[128];
    size_t key_len = make_key(key, j);

    void *val = (void*)((long)j);
    ok = table_bp_add(&T, key, key_len, val);
    ASSERT_TRUE(ok);
    
    void *search_val;
    ok = table_bp_search(&T, key, key_len, &search_val);
    ASSERT_TRUE(ok);
    ASSERT_TRUE(val == search_val);
  }
  ASSERT_TRUE(T.size == N);
  
  // Try printing
  printf("\n\ntable_bp_dump:\n");
  table_bp_dump("%p", &T);
  printf("\n\ntable_bp_dump_keys:\n");
  table_bp_dumpkeys(&T);
  char *str;
  table_bp_keys_string(&str, &T);
  printf("\n\ntable_bp_keys_string:\n%s\n", str);
  free(str);

  // This should free all memory
  table_bp_free_callback(&T, false, null_cb);

  printf("DONE\n");
}

static size_t make_key(char *out, int n)
{
  size_t key_len = (size_t) sprintf(out, "key%i", n);
  // remove null terminator, since it shouldn't depend on it
  out[key_len] = ' ';
  return key_len;
}


static void null_cb(const void *k, size_t kl, void *v)
{
  // Do nothing
}
