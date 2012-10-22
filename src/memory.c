
/*
 * memory.c
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#include "memory.h"

long KB;
long MB;
long GB;

mm_context mm_default = 0;
mm_context mm_null = -1;

#ifndef DISABLE_MM

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

#endif
