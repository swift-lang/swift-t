
#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "exm-string.h"

bool
string_copy(char* dest, int d_space, const char* src)
{
  int length = strlen(src);
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
  int length = q-src;
  char* result = malloc((length+1)*sizeof(char));
  memcpy(result, src, length);
  result[length] = '\0';
  return result;
}

void
chomp(char* s)
{
  int length = strlen(s) - 1;
  if (length >= 0 && s[length] == '\n')
    s[length] = '\0';
}
