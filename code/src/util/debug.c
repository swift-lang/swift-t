
/**
 * debug.c
 *
 * Debugging reports for Turbine
 * */

#include <assert.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/util/debug.h"

static bool  initialized = false;
static bool  enabled = true;
static char* buffer = NULL;
static const int buffer_size = 2*1024;

void
turbine_debug_init()
{
  if (initialized)
  {
    printf("turbine_debug: already initialized\n");
    return;
  }
  initialized = true;
  char* s = getenv("DEBUG");
  if (s != NULL)
    if (strcmp(s, "0") == 0)
    {
      enabled = false;
      return;
    }
  buffer = malloc(buffer_size);
}

/**
   All turbine_debug messages may be disabled by setting
   DEBUG=0 (number 0) in the environment.
   We have to put everything into one string before we print it,
   otherwise mpiexec -l does not print the rank things [0]
   correctly.
*/
__attribute__ ((format (printf, 2, 3)))
void
turbine_debug(const char* token, const char* format, ...)
{
  if (!enabled)
    return;
  assert(initialized);

  va_list va;
  va_start(va, format);
  int count = 0;
  count += sprintf(buffer, "%s: ", token);
  count += vsnprintf(buffer+count, buffer_size-count, format, va);
  if (count >= buffer_size)
    printf("turbine_debug: message exceeded buffer_size (%i)\n",
           buffer_size);
  printf("%s", buffer);
  fflush(stdout);
  va_end(va);
}

void
turbine_debug_finalize()
{
  if (buffer)
    free(buffer);
}
