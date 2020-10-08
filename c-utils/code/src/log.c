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
 * log.c
 *
 *  Created on: Aug 16, 2011
 *      Author: wozniak
 */

#include <stdlib.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/time.h>

#include "src/log.h"
#include "tools.h"

static FILE* output;
static char* filename = NULL;
static double log_start = 0;

bool log_enabled;

/** If non-NULL, this is our prefix */
static char* prefix;

/** If true, flush during every log_printf() */
static bool log_flush_auto = true;

void
log_init()
{
  log_enabled = true;
  output = stdout;
  prefix = NULL;
}

void
log_enable(bool b)
{
  log_enabled = b;
}

void
log_flush_auto_enable(bool b)
{
  log_flush_auto = b;
}

static void log_cleanup(void);

bool
log_file_set(const char* f)
{
  log_cleanup();

  filename = strdup(f);
  output = fopen(filename, "w");
  if (output == NULL)
  {
    output = stdout;
    return false;
  }
  return true;
}

void
log_prefix_set(const char* p)
{
  nullp(&prefix);
  if (p == NULL)
    return;

  prefix = strdup(p);
}

double
log_time_absolute()
{
  struct timeval tv;
  gettimeofday(&tv, NULL);
  double result = (double) tv.tv_sec;
  result += (double) tv.tv_usec * 0.000001;
  return result;
}

void
log_normalize()
{
  log_start = log_time_absolute();
}

double
log_time()
{
  struct timeval tv;
  gettimeofday(&tv, NULL);
  double result = (double)tv.tv_sec;
  result += (double)tv.tv_usec * 0.000001;
  result -= log_start;
  return result;
}

/**
   Resulting line is limited to 1024 characters
 */
void
log_printf(char* format, ...)
{
  if (!log_enabled)
    return;

  double t = log_time();
  va_list ap;
  va_start(ap, format);
  static char line[1024];
  vsnprintf(line, 1024, format, ap);
  int precision = t > 10000 ? 15 : 9;
  if (prefix == NULL)
    fprintf(output, "%*.3f %s\n", precision, t, line);
  else
    fprintf(output, "%s %*.3f %s\n", prefix, precision, t, line);
  if (log_flush_auto)
    fflush(output);
  va_end(ap);
}

/**
   Resulting line is limited to 1024 characters
 */
void
log_printf_force(char* format, ...)
{
  double t = log_time();
  va_list ap;
  va_start(ap, format);
  static char line[1024];
  vsnprintf(line, 1024, format, ap);
  int precision = t > 10000 ? 15 : 9;
  if (prefix == NULL)
    fprintf(output, "%*.3f %s\n", precision, t, line);
  else
    fprintf(output, "%s %*.3f %s\n", prefix, precision, t, line);
  if (log_flush_auto)
    fflush(output);
  va_end(ap);
}

void
log_flush()
{
  fflush(output);
}

static void
log_cleanup()
{
  nullp(&prefix);
  nullp(&filename);
  if (output != stdout)
    fclose(output);
}

void
log_finalize()
{
  log_cleanup();
}
