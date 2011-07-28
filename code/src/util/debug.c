
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/util/debug.h"

static bool initialized = false;
static bool enabled = true;

void
turbine_debug_init()
{
  initialized = true;
  char* s = getenv("DEBUG");
  if (s == NULL)
    return;
  if (strcmp(s, "0") == 0)
    enabled = false;
}

/**
   All turbine_debug messages may be disabled by setting
   DEBUG=0 (number 0) in the environment.
*/
void
turbine_debug(char* token, char* format, ...)
{
  if (!enabled)
    return;

  printf("%s: ", token);
  va_list va;
  va_start(va,format);
  vprintf(format, va);
  va_end(va);
  fflush(stdout);
}
