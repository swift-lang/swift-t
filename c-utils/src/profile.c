
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
  count = 0;
  size = s;
  entries = malloc(size*sizeof(entry));
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
