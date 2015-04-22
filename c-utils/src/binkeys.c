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

#include "binkeys.h"
#include <stdio.h>

int binkey_printf(const void *key, size_t key_len)
{
  for (size_t i = 0; i < key_len; i++)
  {
    unsigned char b = ((unsigned char*)key)[i];
    int rc = printf("%hhX", b);
    if (rc < 0)
      return rc;
  }
  return (int)key_len;
}

int binkey_sprintf(char *str, const void *key, size_t key_len)
{
  int pos = 0;
  for (size_t i = 0; i < key_len; i++)
  {
    unsigned char b = ((unsigned char*)key)[i];
    int rc = sprintf(&str[pos], "%hhX", b);
    if (rc < 0)
      return rc;
    pos += rc; 
  }
  return pos;
}

