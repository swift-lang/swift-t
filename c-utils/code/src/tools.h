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
 * tools.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#pragma once

#include <assert.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

// This only works in GCC:
#ifdef __GNUC__
/** Ease suppression of unused variable warnings */
#define unused __attribute__ ((unused))
#else
#define unused
#endif

/**
   Free and reset this pointer
   NOTE: Pass in the address of the pointer you want to modify
         (Thus actually a pointer-pointer.  We
          do this because of C auto-casting limits.)

*/
static inline void
null(void* p)
{
  void** pp = (void**) p;
  free(*pp);
  *pp = NULL;
}

/**
   null-predicated: Free and reset this pointer if not already NULL
   Return true if the pointer was non-NULL and is now NULL,
   else return false
   NOTE: Pass in the address of the pointer you want to modify
         (Thus actually a pointer-pointer.  We
          do this because of C auto-casting limits.)
*/
static inline bool
nullp(void* p)
{
  void** pp = (void**) p;
  if (*pp == NULL)
    return false;
  // TODO: use null(p)
  free(*pp);
  *pp = NULL;
  return true;
}

/**
   Used on non-GNU systems.
   See: https://www.gnu.org/software/autoconf/manual/autoconf-2.60/html_node/Particular-Functions.html
*/
static inline void*
rpl_malloc(size_t size)
{
  if (size == 0)
    size = 1;
  return malloc(size);
}

/**
   Used on non-GNU systems.
   See: https://www.gnu.org/software/autoconf/manual/autoconf-2.60/html_node/Particular-Functions.html
*/
static inline void*
rpl_realloc(void* ptr, size_t size)
{
  if (size == 0)
    size = 1;
  return realloc(ptr, size);
}

/**
   Determine the length of an array of pointers ending in NULL
 */
int array_length(const void** array);

/**
   More readable memset(0)
 */
static inline void zero_ints(int* array, int count)
{
  memset(array, 0, ((size_t) count) * sizeof(int));
}

/**
   Find the index with the maximal integer in array of given size
 */
int array_max_integer(const int* array, int size);

#define append(string,  args...) \
  string += sprintf(string, ## args)
#define vappend(string, args...) \
  string += vsprintf(string, format, ap)

static inline int
min_integer(int i1, int i2)
{
  if (i1 < i2)
    return i1;
  return i2;
}

static inline int
max_integer(int i1, int i2)
{
  if (i1 > i2)
    return i1;
  return i2;
}

static inline int64_t
min_int64(int64_t i1, int64_t i2)
{
  if (i1 < i2)
    return i1;
  return i2;
}

static inline int64_t
max_int64(int64_t i1, int64_t i2)
{
  if (i1 > i2)
    return i1;
  return i2;
}

static inline uint64_t
min_uint64(uint64_t i1, uint64_t i2)
{
  if (i1 < i2)
    return i1;
  return i2;
}

static inline uint64_t
max_uint64(uint64_t i1, uint64_t i2)
{
  if (i1 > i2)
    return i1;
  return i2;
}

#define bool2string(b) (b ? "true" : "false" )

/**
   Random integer in set [low, high)
 */
static inline int
random_between(int low, int high)
{
  return (low + rand() / ((RAND_MAX / (high - low)) + 1));
}

/**
   Random float in set [low, high)
 */
static inline double
random_between_double(double low, double high)
{
  double r = (double) random();
  double p = r / (double) RAND_MAX;
  double d = low + p * (high - low);
  return d;
}

static inline bool
random_bool(void)
{
  return (bool) random_between(0,2);
}

/**
   input: probability weights for each index - must sum to 1
   output: random index
 */
static inline int
random_draw(float* p, int length)
{
  double weight = 0;
  double r = (double) random();
  double max = (double) RAND_MAX;
  double target = r/max;
  int ix = -1;
  do
  {
    ix++;
    weight += p[ix];
  } while (weight < target && ix < length - 1);
  return ix;
}

void crash(const char* format, ...);

// #define UNUSED __attribute__((unused))

/** Called when the assert_msg() condition fails */
void assert_msg_impl(const char* format, ...);

/** Nice vargs assert with message */
#define assert_msg(condition, format, args...)  \
    { if (!(condition))                          \
       check_msg_impl(format, ## args);        \
    }

/** Nice vargs error check and message */
#define check_msg(condition, format, args...) \
    { if (!(condition)) {                     \
       printf(format, ## args);               \
       fflush(NULL);                          \
       return false;                          \
    } }

/**
   Substitute for assert(): handles unused variables under NDEBUG
 */
#define ASSERT(condition) { assert(condition); (void)(condition); }

/** Called when the valgrind_assert() condition fails */
void valgrind_assert_failed(const char *file, int line);

/** Called when the valgrind_assert_msg() condition fails */
void valgrind_assert_failed_msg(const char *file, int line,
                                const char* format, ...);

/**
   VALGRIND_ASSERT
   Substitute for assert(): provide stack trace via valgrind
   If not running under valgrind, works like assert()
 */
#ifdef NDEBUG
#define valgrind_assert(condition)            (void) (condition);
#define valgrind_assert_msg(condition,msg...) (void) (condition);
#else
#define valgrind_assert(condition) \
    if (!(condition)) \
    { valgrind_assert_failed(__FILE__, __LINE__); }
#define valgrind_assert_msg(condition,msg...) \
    if (!(condition)) \
    { valgrind_assert_failed_msg(__FILE__, __LINE__, ## msg); }
#endif

/**
   Cause valgrind assertion error behavior w/o condition
 */
#define valgrind_fail(msg...) \
  valgrind_assert_failed_msg(__FILE__, __LINE__, ## msg)

/**
   Allows for GDB/Eclipse debugging of MPI applications
   From shell, set environment:
     GDB_SPIN=<rank to which you want to attach>
   Then have each process call gdb_spin(rank) with its rank
   gdb_spin() will report the PID to which to attach
   Attach to that PID, then set variable t=1 to continue stepping
 */
void gdb_spin(int target);

/**
   Get time since the Unix Epoch in microseconds
 */
double time_micros(void);

/**
   Sleep for the given number of seconds (using nanosleep)
 */
void time_delay(double delay);

/**
   Convert environment variable value to integer
   If not found, return default value
   @param dflt The default value
   @return True, false if string could not be converted to integer
 */
bool getenv_integer(const char* name, int dflt, int* result);

/**
   Convert environment variable value to integer
   If not found, return default value
   @param dflt The default value
   @return True, false if string could not be converted to integer
 */
bool getenv_ulong(const char* name, unsigned long dflt,
                  unsigned long* result);

/**
   Receive a true/false setting by env var, which is
   false if "0", or false (case-insensitive),
   and true for a non-zero number or true (case-insensitive)
   If not found, return default value
   @return True, false if string could not be converted to boolean
 */
bool getenv_boolean(const char* name, bool dflt, bool* result);

/**
   Convert environment variable value to double
   If not found, return default value
   @param dflt The default value
   @return True, false if string could not be converted to double
 */
bool getenv_double(const char* name, double dflt, double* result);

/**
   Lookup environment variable name, store it in result
   if found and non-empty.  Else store the default value.
   Set dflt to NULL if you want to simply test for a
   found and non-empty variable.
   @param dflt The default value
*/
void getenv_string(const char* name, char* dflt, char** result);

/**
   Shuffle array A in-place
 */
void shuffle(long* A, int count);

/**
   Simply print comma-separated array of longs
 */
void print_longs(long* A, int count);

/**
   Read a whole file into a newly allocated string
 */
char* slurp(const char* filename);

/**
   Read all output/errors from process
*/
int slurp_process(const char** argv);

void print_ints(const int* A, int n);

void quicksort_ints(int* A, int first, int last);

/**
   Make all parent directories for this file
   @return True on success, else false.
*/
bool make_parents(const char* filename);
