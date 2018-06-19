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
 * log.h
 *
 *  Created on: Aug 16, 2011
 *      Author: wozniak
 */

#ifndef LOG_H
#define LOG_H

#include <stdbool.h>

extern bool log_enabled;

void log_init(void);

void log_enable(bool b);

static inline bool
log_is_enabled(void)
{
  return log_enabled;
}

/**
   Set log output file
   @return True on success, else false
 */
bool log_file_set(const char* f);

/**
   Prepend this number to each output line (emulating mpiexec -l).
   For systems that do not support this themselves.
   (This is unnecessary for normal MPICH, OpenMPI systems)
*/
void log_rank_set(int rank);

/**
   Reset the original time to now
 */
void log_normalize(void);

/**
    Time in seconds since last log_normalize().
    From gettimeofday().
 */
double log_time(void);

double log_time_absolute(void);

void   log_printf(char* format, ...);

void   log_finalize(void);

#endif
