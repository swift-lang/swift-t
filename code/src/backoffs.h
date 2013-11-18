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

#ifndef BACKOFFS_H
#define BACKOFFS_H

#include <stdbool.h>

/**
   Time after which to exit because idle
   Default: May be overridden by ADLB_EXHAUST_TIME
            in setup_idle_time()
 */
extern double xlb_max_idle;

/**
   How long should we wait between rounds of steal attempts? In seconds
 */
extern double xlb_steal_backoff;

/**
  Smallest gap between successive steal attempts.
 */
extern double xlb_steal_rate_limit;

// Maximum requests to serve before yielding to main server loop
extern int xlb_loop_max_requests;

// Maximum polls before yielding to main server loop
extern int xlb_loop_max_polls;

// Maximum sleeps before yielding to main server loop
extern int xlb_loop_max_sleeps;

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

/**
   Backoff during sync() spin loop after rejection
 */
void xlb_backoff_sync_rejected(void);

#endif
