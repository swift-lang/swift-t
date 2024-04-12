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
 * debug.h
 *
 *  Created on: Jun 11, 2012
 *      Author: wozniak
 *
 *  Debugging macros
 *  These may be enabled at configure time with --enable-log-*
 *  These all may be disabled at compile time with NDEBUG
 *  or at run time by setting environment variable XLB_DEBUG=0
 */

#pragma once

#include <stdbool.h>
#define _GNU_SOURCE
#include <stdio.h>

#include <tools.h>

#include "config.h"
#include "adlb-time.h"
#include "adlb-defs.h"

/** Is ADLB_DEBUG enabled? */
extern bool xlb_debug_enabled;
/** Is ADLB_TRACE enabled? */
extern bool xlb_trace_enabled;
/** Is ADLB_DEBUG_GC enabled? */
extern bool xlb_debug_gc_enabled;

/**
   Check environment to see if user disabled ADLB_DEBUG, ADLB_TRACE
 */
adlb_code xlb_debug_check_environment(void);

/**
   Most warnings will result in fatal errors at some point,
   but the user may turn these messages off
 */
#define ENABLE_WARN
#ifdef ENABLE_WARN
#define WARN(format, args...)              \
  {    printf("WARNING: ADLB: " format, ## args); \
       fflush(stdout);                    \
  }
#else
#define WARN(format, args...)
#endif

/*
   Debugging may be enabled at configure time
   Debugging may be disabled at compile-time via NDEBUG or
   ENABLE_DEBUG or at run-time by setting environment variable
   ADLB_DEBUG=0
 */
#if ENABLE_LOG_DEBUG && !defined(NDEBUG)
#define INFO_ENABLED 1
#define INFO(format, args...)              \
  {      printf("%8.4f ADLB: " format "\n", xlb_wtime(), ## args);  \
         fflush(stdout);                    \
       }
#else
#define INFO_ENABLED 0
#define INFO(format, args...) // noop
#endif

#if ENABLE_LOG_DEBUG && !defined(NDEBUG)
#define DEBUG_ENABLED 1
#define DEBUG(format, args...)              \
  { if (xlb_debug_enabled) {                            \
         printf("%8.4f ADLB:  " format "\n", xlb_wtime(), ## args); \
         fflush(stdout);                    \
       } }
#else
#define DEBUG_ENABLED 0
#define DEBUG(format, args...) // noop
#endif

#if ENABLE_LOG_TRACE && !defined(NDEBUG)
#define TRACE(format, args...)                    \
  { if (xlb_trace_enabled && xlb_debug_enabled) { \
      printf("%8.4f TRACE: " format "\n", xlb_wtime(), ## args);  \
  fflush(stdout);                                 \
  } }
#else
#define TRACE(format, args...) // noop
#endif

#if ENABLE_LOG_TRACE_MPI && !defined(NDEBUG)
#define TRACE_MPI(format, args...)             \
  { if (xlb_debug_enabled) {                           \
  printf("%8.4f MPI:   " format "\n", xlb_wtime(), ## args);  \
  fflush(stdout);                          \
  } }
#else
#define TRACE_MPI(format, args...) // noop
#endif

#ifndef NDEBUG
// #define ENABLE_STATS
#endif
#ifdef ENABLE_STATS
#define STATS(format, args...)             \
  { if (xlb_debug_enabled) {               \
  printf("STATS: " format "\n", ## args);  \
  fflush(stdout);                          \
  } }
#else
#define STATS(format, args...) // noop
#endif

/** Print that we are entering a function */
#define TRACE_START TRACE("%s() ...",   __func__)
/** Print that we are exiting a function */
#define TRACE_END   TRACE("%s() done.", __func__)

#define PRINT_COUNTER(format, args...)                    \
  { if (xlb_s.perfc_enabled) {                      \
      printf("[%d] COUNTER: " format "\n", xlb_s.layout.rank, \
            ## args);                                     \
  } }
