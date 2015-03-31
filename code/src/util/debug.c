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

#include <exm-memory.h>
#include <tools.h>

#include "src/util/debug.h"

static bool  initialized = false;
static char* buffer = NULL;
static size_t buffer_size;

#ifndef NDEBUG
/**
   Use variable to switch debugging on/off at runtime to avoid
   use of preprocessor.
   Note that compiler will be able to eliminate dead code if
   debugging is disabled at compile time.
 */
bool  turbine_debug_enabled = true;
#endif

void
turbine_debug_init()
{
  if (initialized)
  {
    DEBUG_TURBINE("turbine_debug: already initialized\n");
    return;
  }
  initialized = true;

  mm_init();

  char* s = getenv("TURBINE_DEBUG");
  if (s != NULL)
    if (strcmp(s, "0") == 0)
    {
      #ifndef NDEBUG
      turbine_debug_enabled = false;
      #endif
      return;
    }
  buffer_size = (size_t)(32*KB);
  buffer = malloc(buffer_size);
}

/**
   Used only for snprintf checks in turbine_debug
*/
#define BUFFER_SIZE_CHECK(count)                     \
  if (count >= buffer_size) {                        \
    printf("turbine_debug: "                         \
           "message exceeded buffer_size (%zi/%zi)\n", \
           count, buffer_size);               \
    buffer[buffer_size-1] = '\0';                    \
    printf("buffer: %s\n", buffer);  \
}

/**
   All turbine_debug messages may be disabled by setting
   TURBINE_DEBUG=0 (number 0) in the environment.
   We have to put everything into one string before we print it,
   otherwise mpiexec -l does not print the rank things [0]
   correctly.
*/
__attribute__ ((format (printf, 2, 3)))
void
turbine_debug(const char* token, const char* format, ...)
{
  if (!turbine_debug_enabled)
    return;
  assert(initialized);

  va_list va;
  va_start(va, format);
  size_t count = 0;
  count += (size_t)sprintf(buffer, "%s: ", token);
  count += (size_t)vsnprintf(buffer+count, buffer_size-count, format, va);
  BUFFER_SIZE_CHECK(count);
  count += (size_t)snprintf(buffer+count, buffer_size-count, "\n");
  BUFFER_SIZE_CHECK(count);
  printf("%s", buffer);
  fflush(stdout);
  va_end(va);
}

void
turbine_debug_finalize()
{
  if (!initialized)
    return;
  if (buffer)
    free(buffer);
  initialized = false;
}
