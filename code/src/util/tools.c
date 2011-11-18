
/**
 *  tools.c
 *
 *   Created on: May 4, 2011
 *       Author: wozniak
 * */

#include <stdarg.h>
#include <stdio.h>

#include "src/util/tools.h"

int array_length(void** array)
{
  int result = 0;
  while (*array)
  {
    array++;
    result++;
  }
  return result;
}

static const int buffer_size = 2*1024;

void check_msg_impl(const char* format, ...)
{
  char buffer[buffer_size];
  int count = 0;
  char* p = &buffer[0];
  va_list ap;
  va_start(ap, format);
  count += sprintf(p, "%s", "error: ");
  count += vsnprintf(buffer+count, buffer_size-count, format, ap);
  va_end(ap);
  printf("%s\n", buffer);
  fflush(NULL);
}
