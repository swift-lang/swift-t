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

#ifndef __CUTILS_STRKEYS
#define __CUTILS_STRKEYS

#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include "jenkins-hash.h"

static inline int
strkey_hash(const char* data, int n);

static inline int
strkey_hash2(const char* data, int n, size_t *data_strlen);

static inline int64_t
strkey_hash_long(const char* data);

static inline int
strkey_hash(const char* data, int n)
{
  size_t l;
  return strkey_hash2(data, n, &l);
}

/**
  Same as strkey_hash, but return string length to avoid recalculating.
 */
static int
strkey_hash2(const char* data, int table_size,
                    size_t *data_strlen)
{
  size_t l = strlen(data);
  uint32_t p = bj_hashlittle(data, l, 0u);

  int index = (int) (p % (uint32_t)table_size);
  *data_strlen = l;
  return index;
}

static inline int64_t
strkey_hash_long(const char* data)
{
  uint32_t p, q;
  size_t length = strlen(data);
  bj_hashlittle2(data, length, &p, &q);

  int64_t result = 0;

  result += p;

  return result;
}

/*
  str: string to append to
  format: printf format string for data
  has_next: if true, adds comma to precede next item

  returns: str pointer advanced past last char appended

  NOTE: this function is generally unsafe since there are no protections
  against appending past end of buffer
 */
char*
strkey_append_pair(char* str, char *key,
            const char* format, const void* data, bool has_next);

#endif // __CUTILS_STRKEYS
