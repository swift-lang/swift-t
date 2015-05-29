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

/**
  Utilities for working with binary keys
 */

#ifndef __CUTILS_BINKEYS
#define __CUTILS_BINKEYS

#include <stdbool.h>
#include <string.h>
#include "jenkins-hash.h"

/*
  Check if two binary keys are equal.
  Inline in header for performance
 */
static inline bool
bin_key_eq(const void *key1, size_t key1_len, const void *key2, size_t key2_len)
{
  return key1_len == key2_len && memcmp(key1, key2, key1_len) == 0;
}

/*
  Check if first binary key is less than or equal to the second in
  lexical order.
  Inline in header for performance
 */
static inline bool
bin_key_leq(const void *key1, size_t key1_len, const void *key2, size_t key2_len)
{
  size_t min_len = (key1_len < key2_len) ? key1_len : key2_len;
  int prefix_cmp = memcmp(key1, key2, min_len);
  if (prefix_cmp == 0)
  {
    // If same length, equal
    // If different length, shorter key is lesser
    return key1_len <= key2_len;
  }
  else
  {
    // Only true if less than
    return prefix_cmp < 0;
  }
}

/*
  Calculate hash for binary key
  Inline in header for performance
 */
static inline int
binkey_hash(const void* data, size_t length, int table_size)
{
  uint32_t p = bj_hashlittle(data, length, 0u);

  int ix = (int) (p % (uint32_t)table_size);
  return ix;
}

/*
  Print key in hex format a la printf.  Not very efficient, for debugging
 */
int binkey_printf(const void *key, size_t key_len);

/*
  Print key in hex format a la sprintf.  Not very efficient, for debugging
 */
int binkey_sprintf(char *str, const void *key, size_t key_len);

#endif // __CUTILS_BINKEYS
