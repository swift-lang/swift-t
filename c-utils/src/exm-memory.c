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
 * memory.c
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#include "exm-memory.h"

long KB;
long MB;
long GB;

mm_context mm_default = 0;
mm_context mm_null = -1;

#ifdef ENABLE_MM

static bool initialized = false;

#include <stdio.h>
#include <stdlib.h>
typedef struct
{
  char* name;
  long max;
  long available;
} context;

context contexts[MM_CONTEXTS_MAX];

/** Count of created contexts */
static int count = 1;

/**
   Ensure the operation is called on a valid context
 */
#define CHECK_CONTEXT(c) \
  if (c == mm_null || c >= count) { \
    printf("warning: given context: %i not created!\n", c); \
    return false; \
  }

void
mm_init()
{
  if (initialized)
    return;
  initialized = true;

  mm_init_units();

  contexts[mm_default].name = strdup("DEFAULT");
  contexts[mm_default].max = GB;
  contexts[mm_default].available = contexts[mm_default].max;
}

mm_context
mm_create(long max, char* name)
{
  mm_context result = count;

  if (count >= MM_CONTEXTS_MAX)
  {
    printf("mm_create(): error: "
           "exceeded MAX_CONTEXTS=%i\n", MM_CONTEXTS_MAX);
    return mm_null;
  }

  if (strnlen(name, MM_CONTEXT_NAME_MAX) == MM_CONTEXT_NAME_MAX)
  {
    printf("mm_create(): error: "
           "context name length exceeds MM_CONTEXT_NAME_MAX=%i\n",
           MM_CONTEXT_NAME_MAX);
    return mm_null;
  }

  contexts[result].name      = strdup(name);
  contexts[result].max       = max;
  contexts[result].available = contexts[result].max;

  count++;
  return result;
}

bool
mm_set_max(mm_context ctx, long max)
{
  CHECK_CONTEXT(ctx);
  contexts[ctx].max = max;
  return true;
}

bool
mm_try(mm_context ctx, long bytes)
{
  CHECK_CONTEXT(ctx);
  if (contexts[ctx].available - bytes >= 0)
    return true;
  return false;
}

bool
mm_reserve(mm_context ctx, long bytes)
{
  CHECK_CONTEXT(ctx);
  if (contexts[ctx].available - bytes >= 0)
  {
    contexts[ctx].available -= bytes;
    return true;
  }
  return false;
}

bool
mm_release(mm_context ctx, long bytes)
{
  CHECK_CONTEXT(ctx);
  contexts[ctx].available -= bytes;
  if (contexts[ctx].available > contexts[ctx].max)
  {
    printf("mm_release(): warning: available exceeds max: "
           "context: %i\n", ctx);
    return false;
  }
  return true;
}

int
mm_context_list(mm_context* output)
{
  for (int i = 0; i < count; i++)
    output[i] = i;
  return count;
}

bool
mm_context_info(mm_context ctx, mm_info* info)
{
  for (int i = 0; i < count; i++)
  {
    info[i].available = contexts[i].available;
    info[i].max = contexts[i].max;
  }
  return true;
}

bool
mm_context_name(mm_context ctx, char* name)
{
  CHECK_CONTEXT(ctx);
  strcpy(name, contexts[ctx].name);
  return true;
}

#else
// Disable this module

void
mm_init(void)
{
  mm_init_units();
}

mm_context
mm_create(long max, char* name)
{
  return -1;
}

bool
mm_set_max(mm_context context, long max)
{
  return true;
}

bool
mm_try(mm_context ctx, long bytes)
{
  return true;
}

bool
mm_take(mm_context ctx, long bytes)
{
  return true;
}

bool
mm_release(mm_context ctx, long bytes)
{
  return true;
}

#endif
