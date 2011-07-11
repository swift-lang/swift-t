
#include <stdarg.h>
#include <stdio.h>

#include "src/util/debug.h"

void
turbine_debug(char* token, char* format, ...)
{
  printf("%s: ", token);
  va_list va;
  va_start(va,format);
  vprintf(format, va);
  va_end(va);
  fflush(stdout);
}
