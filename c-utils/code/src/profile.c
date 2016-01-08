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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "profile.h"

#ifdef ENABLE_PROFILE

/**
   Message strings must be strictly less than this many characters
 */
#define PROFILE_MSG_SIZE (64)

typedef struct
{
  double timestamp;
  char message[PROFILE_MSG_SIZE];
} entry;

static entry* entries = NULL;
static int size = -1;
static int count = -1;

/**
   @param s Maximal number of entries
 */
void
profile_init(int s)
{
  assert(s >= 0);
  count = 0;
  size = s;
  entries = malloc((size_t)size*sizeof(entry));
  for (int i = 0; i < size; i++)
    entries[i].timestamp = -1;
}

/**
   Not currently thread-safe
   Does not copy message
   message is freed by profile_finalize
*/
void profile_entry(double timestamp, const char* message)
{
  entries[count].timestamp = timestamp;
  assert(strlen(message) < PROFILE_MSG_SIZE);
  strncpy(entries[count].message, message, PROFILE_MSG_SIZE);
  count++;
}

void profile_write(int rank, FILE* file)
{
  for (int i = 0; i < count; i++)
    fprintf(file, "[%i] %0.4f: %s\n",
            rank, entries[i].timestamp, entries[i].message);
}

void profile_finalize()
{
  for (int i = 0; i < count; i++)
    free(entries[i].message);
  free(entries);
}

#endif
