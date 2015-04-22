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

#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "exm-string.h"

bool
string_copy(char* dest, int d_space, const char* src)
{
  size_t length = strlen(src);
  if (d_space <= length)
    return false;
  memcpy(dest, src, length+1);
  return true;
}

char*
string_dup_word(char* src)
{
  char* q = strchr(src, ' ');
  if (!q) return NULL;
  size_t length = (size_t)(q-src);
  char* result = malloc((length+1)*sizeof(char));
  memcpy(result, src, length);
  result[length] = '\0';
  return result;
}

void
chomp(char* s)
{
  size_t length = strlen(s);
  if (length > 0 && s[length - 1] == '\n')
    s[length - 1] = '\0';
}
