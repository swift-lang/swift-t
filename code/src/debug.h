
/*
 * debug.h
 *
 *  Created on: Jun 11, 2012
 *      Author: wozniak
 *
 *  Debugging macros
 *  These all may be disabled at compile time with NDEBUG
 *  or at run time by setting environment variable XLB_DEBUG=0
 */

#ifndef DEBUG_H
#define DEBUG_H

#include <stdbool.h>
#include <stdio.h>

#include <tools.h>

#include "common.h"

/** Is ADLB_DEBUG enabled? */
extern bool xlb_debug_enabled;
/** Is ADLB_TRACE enabled? */
extern bool xlb_trace_enabled;

/**
   Check environment to see if user disabled ADLB_DEBUG, ADLB_TRACE
 */
void debug_check_environment(void);


#ifndef NDEBUG
#define ENABLE_DEBUG 1
#endif

#ifndef NDEBUG
// #define ENABLE_TRACE_MPI 1
#endif

#ifndef NDEBUG
#define ENABLE_TRACE 1
#endif

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
   Debugging may be disabled at compile-time via NDEBUG or
   ENABLE_DEBUG or at run-time by setting environment variable
   ADLB_DEBUG=0
 */
#ifdef ENABLE_DEBUG
#define DEBUG(format, args...)              \
  { if (xlb_debug_enabled) {                            \
         printf("%5.0f ADLB: " format "\n", xlb_wtime(), ## args); \
         fflush(stdout);                    \
       } }
#else
#define DEBUG(format, args...) // noop
#endif

#ifdef ENABLE_TRACE
#define TRACE(format, args...)                    \
  { if (xlb_trace_enabled && xlb_debug_enabled) { \
  printf("ADLB_TRACE: " format "\n", ## args);    \
  fflush(stdout);                                 \
  } }
#else
#define TRACE(format, args...) // noop
#endif

#ifdef ENABLE_TRACE_MPI
#define TRACE_MPI(format, args...)             \
  { if (xlb_debug_enabled) {                           \
  printf("%5.0f MPI:  " format "\n", xlb_wtime(), ## args);  \
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
#define TRACE_START TRACE("%s()...",    __func__)
/** Print that we are exiting a function */
#define TRACE_END   TRACE("%s() done.", __func__)

#endif
