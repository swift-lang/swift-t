
/*
 * memory.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 *
 * Memory manager: for memory usage statistics
 *
 * If DISABLE_MM is set, these all become noops, except the units
 * */

#ifndef MEMORY_H
#define MEMORY_H

#include <stdbool.h>

#ifdef NDEBUG
#define DISABLE_MM
#endif

extern long KB;
extern long MB;
extern long GB;

static inline void
mm_init_units(void)
{
  KB = 1024;
  MB = KB*1024;
  GB = MB*1024;
}

#ifndef DISABLE_MM

typedef int mm_context;

/**
   Default context
 */
extern int mm_default;

/**
   Not a context
 */
extern mm_context mm_null;

void mm_init(void);

/** Maximal number of contexts */
#define MM_CONTEXTS_MAX 16

/** Maximal length of a context name, including null byte */
#define MM_CONTEXT_NAME_MAX 64

typedef struct
{
  int max;
  int available;
} mm_info;

/**
   Create a context with given maximum memory, name
   @return Context; mm_null on error
 */
mm_context mm_create(long max, char* name);

/**
   Change context max memory setting
 */
bool mm_set_max(mm_context context, long max);

/**
   Is this memory available?
 */
bool mm_try(mm_context ctx, long bytes);

/**
   Decrement available memory
 */
bool mm_take(mm_context ctx, long bytes);

/**
   Increment available memory
   Fails only if context was not created
 */
bool mm_release(mm_context ctx, long bytes);

/**
   List defined contexts
   @param max Maximal number of contexts to list
   @param output Must point to MM_CONTEXTS_MAX mm_contexts for output
   @return Number of mm_contexts written to output
 */
int mm_context_list(mm_context* output);

bool mm_context_info(mm_context ctx, mm_info* info);

/**
   Retrieve context name.
   @param name Must point to MM_CONTEXT_NAME_MAX bytes for output
   @return false if ctx is not defined
 */
bool mm_context_name(mm_context ctx, char* name);

#else
// Disable this module

static inline void
mm_init(void)
{
  mm_init_units();
}

static inline mm_context
mm_create(long max, char* name)
{
  return mm_default;
}

static inline bool
mm_set_max(mm_context context, long max)
{
  return true;
}

static inline bool
mm_try(mm_context ctx, long bytes)
{
  return true;
}

static inline bool
mm_take(mm_context ctx, long bytes)
{
  return true;
}

static inline bool
mm_release(mm_context ctx, long bytes)
{
  return true;
}

#endif

#endif
