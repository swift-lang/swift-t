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
 * backoffs.h
 *
 *  Created on: Aug 22, 2012
 *      Author: wozniak
 */

#pragma once

#include <stdbool.h>

// Progress speeds:
#define BACKOFF_SLOW   0 // for fine-tuned debugging
#define BACKOFF_MEDIUM 1 // good for use with valgrind
#define BACKOFF_FAST   2 // normal - do not use with valgrind (thrashes)
#define BACKOFF_SPEED  BACKOFF_FAST


/**
   Time after which to exit because idle
   Default: May be overridden by ADLB_EXHAUST_TIME
            in setup_idle_time()
 */
extern double xlb_max_idle;

/**
   Time interval between server checking other workers for idleness,
   once xlb_max_idle has expired for that server.  Expressed as
   a multiplier for xlb_max_idle.  Should be set to a value that
   gives a reasonable probability for the other server that didn't
   have their idle timer expire previously to expire between checks.
 */
static const double xlb_servers_idle_frac = 0.25;


/**
   How long should we wait between rounds of steal attempts? In seconds
 */
extern double xlb_steal_backoff;

/**
  How many concurrent steal probes can be outstanding at once?
 */
extern double xlb_steal_concurrency_limit;

/**
  Smallest gap between successive steal attempts.
 */
extern double xlb_steal_rate_limit;

#if BACKOFF_SPEED == BACKOFF_SLOW
// Threshold for main server request loop yield to main loop
static const int xlb_loop_threshold = 1;

// How much request counts towards threshold
static const int xlb_loop_request_points = 1;

// How much an unsuccessful poll counts towards threshold
static const int xlb_loop_poll_points = 1;

// How much a sleep counts towards threshold
static const int xlb_loop_sleep_points = 1;
#elif BACKOFF_SPEED == BACKOFF_MEDIUM
static const int xlb_loop_threshold = 16;
static const int xlb_loop_request_points = 1;
static const int xlb_loop_poll_points = 1;
static const int xlb_loop_sleep_points = 1;
#elif BACKOFF_SPEED == BACKOFF_FAST
static const int xlb_loop_threshold = 10000;
static const int xlb_loop_request_points = 100;
static const int xlb_loop_poll_points = 1;
static const int xlb_loop_sleep_points = 1000;
#endif 

/**
   Backoff while in server loop
   @param attempt: what level we should go to
   @param slept: true if this function slept
   returns true if should retry again later with higher level
 */
bool xlb_backoff_server(int attempt, bool *slept);

/**
   Backoff during sync() spin loop
 */
void xlb_backoff_sync(void);
