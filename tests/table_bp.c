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

#include <table_bp.h>
#include <assert.h>

int main() {

  table_bp T;
  bool ok;

  ok = table_bp_init(&T, 4);
  assert(ok);

  // force expand
  for (int i = 0; i < 16; i++)
  {
    char key[128];
    size_t key_len = (size_t) sprintf(key, "key%i", i);
    
    // remove null terminator, since it shouldn't depend on it
    key[key_len] = ' ';

    ok = table_bp_add(&T, key, key_len, NULL);
    assert(ok);

    // Check iteration works;
    int count = 0;
    TABLE_BP_FOREACH(&T, item)
    {
      count++;
    }
    printf("i=%i count=%i\n", i, count);
    assert(count == i + 1);
  }


  printf("DONE\n");
}
