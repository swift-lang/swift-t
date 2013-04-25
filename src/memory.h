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

// Must be first include:
#ifndef _GNU_SOURCE
#define _GNU_SOURCE // for strnlen()
#endif
#include <string.h>
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
  long max;
  long available;
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

#endif
