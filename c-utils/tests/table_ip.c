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

#include <table_ip.h>

static void null_cb(int k, void *v);

int main() {

  table_ip T;
  bool ok;

  ok = table_ip_init(&T, 4);
  ASSERT_TRUE(ok);

  // force expand several times
  int N = 64;
  for (int i = 0; i < N; i++)
  {
    int key = i;

    void *val = (void*)(long)i;
    ok = table_ip_add(&T, key, val);
    ASSERT_TRUE(ok);

    // Check iteration works;
    int count = 0;
    TABLE_IP_FOREACH(&T, item)
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
    int key = ((i*29) + 30) % N;
    void *val;
    bool found = table_ip_search(&T, key, &val);
    ASSERT_TRUE(found);
    printf("Search: %i=%li\n", key, (long)val);
    ASSERT_TRUE(((long)val) == key);
  }
  ASSERT_TRUE(T.size == N);
  
  for (int i = 0; i < N; i++)
  {
    // Remove in different order
    int key = ((i*29) + 5) % N;
    void *val;
    bool found = table_ip_remove(&T, key, &val);
    ASSERT_TRUE(found);
    printf("Remove: %i=%li\n", key, (long)val);
    ASSERT_TRUE(((long)val) == key);
  }
  ASSERT_TRUE(T.size == 0);

  // Free
  table_ip_free_callback(&T, false, NULL);
  
  // Rebuild
  N = 128;
  // Force many hash collisions
  table_ip_init_custom(&T, 2, 1000.0);
  for (int i = 0; i < N; i++)
  {
    int key = ((i*9) + 12) % 16;

    void *val = (void*)((long)key);
    ok = table_ip_add(&T, key, val);
    ASSERT_TRUE(ok);
    
    void *search_val;
    ok = table_ip_search(&T, key, &search_val);
    ASSERT_TRUE(ok);
    ASSERT_TRUE(val == search_val);
  }
  ASSERT_TRUE(T.size == N);

  // Try printing
  printf("\n\ntable_ip_dump:\n");
  table_ip_dump("%p", &T);
  printf("\n\ntable_ip_dump_keys:\n");
  table_ip_dumpkeys(&T);
  
  // This should free all memory
  table_ip_free_callback(&T, false, null_cb);

  printf("DONE\n");
}

static void null_cb(int k, void *v)
{
  // Do nothing
}
