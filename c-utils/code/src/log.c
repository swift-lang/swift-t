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

/** If true, prepend an MPI-like rank identifier */
static bool rank_enabled;
/** If rank_enabled, this is our rank */
static int  rank;

/** If true, flush during every log_printf() */
static bool log_flush = true;

void
log_init()
{
  log_enabled = true;
  output = stdout;
  rank_enabled = false;

  getenv_boolean("LOG_FLUSH", true, &log_flush);
}

void
log_enable(bool b)
{
  log_enabled = b;
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
log_rank_set(int r)
{
  rank_enabled = true;
  rank = r;
}

double
log_time_absolute()
{
  struct timeval tv;
  gettimeofday(&tv, NULL);
  double result = (double)tv.tv_sec;
  result += (double)tv.tv_usec * 0.000001;
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
  int precision = t > 10000 ? 15 : 8;
  if (! rank_enabled)
    fprintf(output, "%*.3f %s\n", precision, t, line);
  else
    fprintf(output, "[%i] %*.3f %s\n", rank, precision, t, line);
  if (log_flush)
    fflush(output);
  va_end(ap);
}

static void
log_cleanup()
{
  if (filename != NULL)
    free(filename);
  if (output != stdout)
    fclose(output);
}

void
log_finalize()
{
  log_cleanup();
}
