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
 *  tools.c
 *
 *   Created on: May 4, 2011
 *       Author: wozniak
 * */

#include <ctype.h>
#ifdef HAVE_ERROR
// GNU extension
#include <error.h>
#endif
#include <errno.h>
#include <libgen.h>
#include <limits.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h> // For alloca() on Mac
#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 200809L
#endif
#include <time.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

#include "src/tools.h"

/* The following block produces a false autoscan report about
   AC FUNC ERROR AT LINE
*/
#ifndef HAVE_ERROR
void
error(int status, int errnum, const char* format, ...)
{
  // GNU doc says that error() should flush stdout first:
  fflush(stdout);
  printf("error(): %s\n", strerror(errnum));
  fflush(stdout);
  va_list ap;
  va_start(ap, format);
  vprintf(format, ap);
  va_end(ap);
  exit(status);
}
#endif

int
array_length(const void** array)
{
  int result = 0;
  while (*array)
  {
    array++;
    result++;
  }
  return result;
}

int
array_max_integer(const int* array, int size)
{
  valgrind_assert_msg(size > 0,
                      "array_max_integer(): array size is 0!");
  int index = 0;
  int max   = array[index];
  for (int i = 1; i < size; i++)
    if (array[i] > max)
      index = i;
  return index;
}

static const int buffer_size = 2*1024;

void
crash(const char* format, ...)
{
  va_list ap;
  va_start(ap, format);
  vprintf(format, ap);
  va_end(ap);
  exit(EXIT_FAILURE);
}

/**
   We bundle everything into one big printf for MPI
 */
void
assert_msg_impl(const char* format, ...)
{
  char buffer[buffer_size];
  int count = 0;
  char* p = &buffer[0];
  va_list ap;
  va_start(ap, format);
  count += sprintf(p, "error: ");
  count += vsnprintf(buffer+count, (size_t)(buffer_size-count), format, ap);
  va_end(ap);
  printf("%s\n", buffer);
  fflush(NULL);
  exit(1);
}

/**
   Is there another way to detect we are under valgrind?
 */
static bool
using_valgrind(void)
{
  // User must set VALGRIND to get this to work
  char* s = getenv("VALGRIND");
  if (s != NULL && strlen(s) > 0)
    return true;
  return false;
}

void
valgrind_assert_failed(const char* file, int line)
{
  printf("valgrind_assert(): failed: %s:%d\n", file, line);
  if (using_valgrind())
  {
    printf("valgrind_assert(): inducing memory fault...\n");
    // This will give us more information from valgrind
    puts((char*) 1);
  }
  abort();
}

void
valgrind_assert_failed_msg(const char* file, int line,
                           const char* format, ...)
{
  printf("valgrind_assert(): failed: %s:%d\n", file, line);
  char buffer[buffer_size];
  int count = 0;
  char* p = &buffer[0];
  va_list ap;
  va_start(ap, format);
  count += sprintf(p, "valgrind_assert(): ");
  count += vsnprintf(buffer+count, (size_t)(buffer_size-count), format, ap);
  va_end(ap);
  printf("%s\n", buffer);
  fflush(NULL);
  if (using_valgrind())
  {
    printf("valgrind_assert(): inducing memory fault...\n");
    // This will give us more information from valgrind
    // This will trigger a known warning in GCC 11+
    puts((char*) 1);
  }
  abort();
}

/**
   This is a separate function so we can set a function breakpoint
 */
static void
gdb_sleep(int* t, int i)
{
  sleep(5);
  printf("gdb_check: %i %i\n", *t, i);
}

void
gdb_spin(int target)
{
  // User must set GDB_SPIN to get this to work
  char* s = getenv("GDB_SPIN");
  int gdb_spin_number;
  if (s != NULL && strlen(s) > 0)
  {
    int c = sscanf(s, "%i", &gdb_spin_number);
    if (c != 1)
    {
      printf("Invalid GDB_SPIN: %s\n", s);
      exit(1);
    }
    if (gdb_spin_number == target)
    {
      pid_t pid = getpid();
      printf("Waiting for gdb: target: %i pid: %i\n", target, pid);
      int t = 0;
      int i = 0;
      while (!t)
        gdb_sleep(&t, i++);
    }
  }
}

double
time_micros()
{
  struct timeval tv;
  gettimeofday(&tv, NULL);
  double result = (double)tv.tv_sec + 0.000001 * (double)tv.tv_usec;
  return result;
}

/** Constant for 10^9 */
#define POW_10_9 1000000000

void
time_delay(double delay)
{
  struct timespec ts;
  time_t i = (time_t)floor(delay);
  double d = delay - (double)i;
  ts.tv_sec = i;
  ts.tv_nsec = (long) (d / POW_10_9);
  nanosleep(&ts, NULL);
}

bool
getenv_integer(const char* name, int dflt, int* result)
{
  char* v = getenv(name);
  if (v == NULL || strlen(v) == 0)
  {
    *result = dflt;
    return true;
  }
  int n = sscanf(v, "%i", result);
  if (n != 1)
    return false;
  return true;
}

bool
getenv_ulong(const char* name, unsigned long dflt,
             unsigned long* result)
{
  char* v = getenv(name);
  if (v == NULL || strlen(v) == 0)
  {
    *result = dflt;
    return true;
  }
  int n = sscanf(v, "%lu", result);
  if (n != 1)
    return false;
  return true;
}

bool
getenv_boolean(const char* name, bool dflt, bool* result)
{
  char* s = getenv(name);
  if (s == NULL || strlen(s) == 0)
  {
    // Undefined or empty: return default
    *result = dflt;
    return true;
  }

  // Try to parse as number
  char* end = NULL;
  long num_val = strtol(s, &end, 10);
  if (end != NULL && end != s && *end == '\0')
  {
    // Whole string was number
    *result = (num_val != 0);
    return true;
  }

  // Try to parse as true/false
  size_t length = strlen(s);
  // should not be longer than 5 characters "false"
  const int max_length = 5;
  if (length > max_length)
    goto error;

  // Convert to lower case
  char lower_s[8];
  for (int i = 0; i < length; i++)
    lower_s[i] = (char) tolower(s[i]);

  lower_s[length] = '\0';

  if (strcmp(lower_s, "true") == 0)
    *result = true;
  else if (strcmp(lower_s, "false") == 0)
    *result = false;
  else
    goto error;

  // Successful return:
  return true;

  error:
  printf("Invalid boolean environment variable value: %s=\"%s\"\n",
         name, s);
  return false;
}

bool
getenv_double(const char* name, double dflt, double* result)
{
  char* v = getenv(name);
  if (v == NULL || strlen(v) == 0)
  {
    *result = dflt;
    return true;
  }
  int n = sscanf(v, "%lf", result);
  if (n != 1)
    return false;
  return true;
}

void
getenv_string(const char* name, char* dflt, char** result)
{
  char* v = getenv(name);
  if (v == NULL || strlen(v) == 0)
    *result = dflt;
  else
    *result = v;
}

/**

 */
void
shuffle(long* A, int count)
{
  assert(count >= 0);
  // Shuffled working space: initially empty
  long buffer[count];
  // Index into buffer
  int index = 0;
  // Number of remaining inputs
  int inputs = count;

  while (inputs > 0)
  {
    int r = random_between(0, inputs);
    buffer[index++] = A[r];
    // Shift remaining inputs into bottom of A (no gaps)
    memmove(&A[r], &A[r+1], (size_t)(inputs-r-1)*sizeof(long));
    inputs--;
  }

  // Store result
  memcpy(A, buffer, (size_t)count*sizeof(long));
}

void
print_longs(long* A, int count)
{
  int i;
  for (i = 0; i < count-1; i++)
    printf("%li,", A[i]);
  printf("%li", A[i]);
}

char*
slurp(const char* filename)
{
  FILE* file = fopen(filename, "r");
  if (file == NULL)
  {
    printf("slurp(): could not read from: %s\n", filename);
    return NULL;
  }

  struct stat s;
  int rc = stat(filename, &s);
  valgrind_assert(rc == 0);

  off_t length = s.st_size;
  char* result = malloc((size_t)length+1);
  if (result == NULL)
  {
    printf("slurp(): could not allocate memory for: %s\n", filename);
    return NULL;
  }

  char* p = result;
  int actual = (int)fread(p, sizeof(char), (size_t)length, file);
  if (actual != length)
  {
    printf("could not read all %li bytes from file: %s\n",
           (long) length, filename);
    free(result);
    return NULL;
  }
  result[length] = '\0';

  fclose(file);
  return result;
}

void
print_ints(const int* A, int n)
{
  void* t = alloca(n*16*sizeof(char));
  char* p = t;
  append(p, "[");
  for (int i = 0; i < n; i++)
  {
    append(p, "%i", A[i]);
    if (i < n-1) append(p, ",");
  }
  append(p, "]\n");
  printf("%s", (char*) t);
}

static inline void
swap_ints(int* a, int* b)
{
  int t;
  t  = *a;
  *a = *b;
  *b = t;
}

/** From: http://www.cquestions.com/2008/01/c-program-for-quick-sort.html */
void
quicksort_ints(int* A, int first, int last)
{
  if (first < last)
  {
    int pivot = first;
    int i = first;
    int j = last;

    while (i < j)
    {
      while (A[i] <= A[pivot] && i<last)
        i++;
      while (A[j] > A[pivot])
        j--;
      // printf("i: %i j: %i\n", i, j);
      if (i < j)
        swap_ints(&A[i], &A[j]);
    }
    swap_ints(&A[pivot], &A[j]);

    quicksort_ints(A,first,j-1);
    quicksort_ints(A,j+1,last);
  }
}

bool
make_parents(const char* filename)
{
  bool b;
  int rc;
  struct stat s;
  char f[PATH_MAX];
  // printf("make_parents(): '%s'\n", filename);
  if (strcmp(filename, ".") == 0)
    return true;
  strcpy(f, filename);
  char* d = dirname(f);
  // printf("parent: '%s'\n", d);
  if (strcmp(d, ".") == 0)
    return true;
  errno = 0;
  rc = stat(d, &s);
  if (errno == ENOENT)
  {
    b = make_parents(d);
    if (!b) return false;
    // printf("mkdir: '%s' ...\n", d); fflush(stdout);
    rc = mkdir(d, S_IRWXU|S_IRWXG|S_IRWXO);
    // printf("mkdir: '%s' -> %i\n", d, rc); fflush(stdout);
    if (rc != 0)
    {
      printf("could not mkdir: '%s'\n", d);
      fflush(stdout);
      error(1, errno, "error");
      return false;
    }
  }
  else if (rc != 0)
  {
    printf("could not stat: '%s'\n", d);
    fflush(stdout);
    error(1, errno, "error");
    return false;
  }

  return true;
}
