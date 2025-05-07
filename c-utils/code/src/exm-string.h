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

/*
 * exm_string.h
 *
 *  Created on: Mar 20, 2012
 *      Author: wozniak
 */

#pragma once

#include <stdbool.h>

// We need this for configure-time symbols (e.g. SUBST_HAVE_STRNLEN)
#include <c-utils.h>

/**
   Copy the string
   @param dest The destination memory
   @param d_space The size of the destination memory
   @param src The source string

   @return True iff there was enough space and the copy succeeded
 */
bool string_copy(char* dest, int d_space, const char* src);

char* string_dup_word(char* src);

/**
   Remove the newline from the end of string s.
   Like Perl's chomp.
 */
void chomp(char* s);

#if ! SUBST_HAVE_STRNLEN

/**
   Provide strnlen on systems that do not have it (e.g., Mac)
   This produces a false autoscan warning
*/
static inline size_t
strnlen(char* text, size_t maxlen)
{
  const char* last = memchr(text, '\0', maxlen);
  return last ? (size_t) (last - text) : maxlen;
}

#endif
