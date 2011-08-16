
/**
 * log.c
 *
 *  Created on: Aug 16, 2011
 *      Author: wozniak
 */

#include <stdarg.h>
#include <stdio.h>
#include <sys/time.h>

#include "src/util/log.h"

FILE* output;
double log_start = 0;

void
log_init()
{
  output = stdout;
}

/**
   Reset the original time to now
 */
void
log_normalize()
{
  log_start = log_time();
}

double
log_time()
{
  struct timeval tv;
  gettimeofday(&tv, NULL);
  double result = tv.tv_sec;
  result += tv.tv_usec * 0.000001;
  result -= log_start;
  return result;
}

/**
   Resulting line is limited to 1024 characters
 */
void
log_printf(char* format, ...)
{
  double t = log_time();

  static char line[1024];
  va_list ap;
  va_start(ap, format);
  vsnprintf(line, 1024, format, ap);
  va_end(ap);

  int precision = t > 10000 ? 15 : 8;
  fprintf(output, "%*.3f %s\n", precision, t, line);
}

void
log_finalize()
{}
