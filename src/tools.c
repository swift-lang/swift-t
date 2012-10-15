
/**
 *  tools.c
 *
 *   Created on: May 4, 2011
 *       Author: wozniak
 * */

#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 200809L
#endif
#include <time.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

#include "src/tools.h"

/**
   Determine the length of an array of pointers
 */
int
array_length(void** array)
{
  int result = 0;
  while (*array)
  {
    array++;
    result++;
  }
  return result;
}

static const int buffer_size = 2*1024;

/**
   We bundle everything into one big printf for MPI
 */
void
check_msg_impl(const char* format, ...)
{
  char buffer[buffer_size];
  int count = 0;
  char* p = &buffer[0];
  va_list ap;
  va_start(ap, format);
  count += sprintf(p, "error: ");
  count += vsnprintf(buffer+count, buffer_size-count, format, ap);
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
  count += vsnprintf(buffer+count, buffer_size-count, format, ap);
  va_end(ap);
  printf("%s\n", buffer);
  fflush(NULL);
  if (using_valgrind())
  {
    printf("valgrind_assert(): inducing memory fault...\n");
    // This will give us more information from valgrind
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
  double result = tv.tv_sec + 0.000001 * tv.tv_usec;
  return result;
}

/** Constant for 10^9 */
#define POW_10_9 1000000000

void
time_delay(double delay)
{
  struct timespec ts;
  time_t i = floor(delay);
  double d = delay - i;
  ts.tv_sec = i;
  ts.tv_nsec = d / POW_10_9;
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

