
/*
 * tools.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef TOOLS_H
#define TOOLS_H

#include <assert.h>
#include <stdbool.h>
#include <stdlib.h>

int array_length(void** array);

#define append(string,  args...) \
  string += sprintf(string, ## args)
#define vappend(string, args...) \
  string += vsprintf(string, format, ap)

static inline int min_integer(int i1, int i2)
{
  if (i1 < i2)
    return i1;
  return i2;
}

static inline int max_integer(int i1, int i2)
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
  return (low + random() / (RAND_MAX / (high - low)));
}

/**
   Random float in set [low, high)
 */
static inline double
random_between_double(double low, double high)
{
  double r = random();
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
  int index = -1;
  do
  {
    index++;
    weight += p[index];
  } while (weight < target);
  return index;
}

/** Called when the check_msg() condition fails */
void check_msg_impl(const char* format, ...);

/** Nice vargs error check and message */
#define check_msg(condition, format, args...)  \
    { if (!(condition))                          \
       check_msg_impl(format, ## args);        \
    }

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
  valgrind_assert_failed_msg(__FILE__, __LINE__, ## msg);

void gdb_spin(int target);

/**
   Get time since the Unix Epoch in microseconds
 */
double time_micros(void);

/**
   Sleepfor the given number of seconds (using nanosleep)
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
   Shuffle array A in-place
 */
void shuffle(long* A, int count);

/**
   Simply print comma-separated array of longs
 */
void print_longs(long* A, int count);

#endif
