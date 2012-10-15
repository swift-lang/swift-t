
/*
 * exm_string.h
 *
 *  Created on: Mar 20, 2012
 *      Author: wozniak
 */

#ifndef EXM_STRING_H
#define EXM_STRING_H

#include <c-utils-config.h>
#include <stdbool.h>

/**
   Copy the string
   @param dest The destination memory
   @param d_space The size of the destination memory
   @param src The source string

   @return True iff there was enough space and the copy succeeded
 */
bool string_copy(char* dest, int d_space, const char* src);

char* string_dup_word(char* src);

void chomp(char* s);

#ifndef HAVE_STRNLEN

/**
   Provide strnlen on systems that do not have it (e.g., Mac)
*/
static inline size_t
strnlen(char* text, size_t maxlen)
{
  const char* last = memchr(text, '\0', maxlen);
  return last ? (size_t) (last - text) : maxlen;
}

#endif

#endif
