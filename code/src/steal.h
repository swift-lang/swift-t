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
 * steal.h
 *
 *  Created on: Aug 20, 2012
 *      Authors: wozniak, armstrong
 *
 * Module that implements server to server work-stealing.
 * Other modules must call functions in this module to
 * initiating steals, and to update steal state when work-stealing
 * messages are received
 */

#ifndef STEAL_H
#define STEAL_H

#include <stdbool.h>

#include "backoffs.h"

// The number of work units to send at a time
#define XLB_STEAL_CHUNK_SIZE 16

/**
   When was the last time we tried to steal?  In seconds.
   Updated by steal()
 */
extern double xlb_steal_last;

extern int xlb_failed_steals_since_backoff;

/**
  Initialize steal internal state before use
 */
adlb_code xlb_steal_init(void);

/**
  Finalize steal internal state before use
 */
void xlb_steal_finalize(void);

/**
  Check if this server is allowed to initiate a steal request
  according to the steal throttling/backoff algorithms.

  Implemented as inline function to allow rapid return in common cases.
 */
static inline bool xlb_steal_allowed(void);

/**
  Send a steal probe to check for work on a random caller 
 */
adlb_code xlb_random_steal_probe(void);

/**
  Handle a steal probe received from a caller
 */
adlb_code xlb_handle_steal_probe(int caller);

/**
   Handle steal probe response, and carry out the steal if needed.

   Note that this may add sync requests to the xlb_pending_syncs list,
   which must be handled by the caller.
 */
adlb_code xlb_handle_steal_probe_resp(int caller,
               const struct packed_sync *msg);

/**
   Handle an accepted steal request
   work_type_counts: array of size xlb_types_size
  */
adlb_code xlb_handle_steal(int caller, const struct packed_steal *req,
                           const int *work_type_counts);

// Inline functions:
static inline bool xlb_steal_allowed(void)
{
  if (xlb_servers == 1)
    // No other servers
    return false;
  double t = xlb_approx_time();

  // Somewhat adaptive backoff approach where we do bursts of polling
  double interval;

  bool backoff = (xlb_failed_steals_since_backoff == xlb_servers);
  if (backoff)
  {
    interval = xlb_steal_backoff;
  }
  else
  {
    interval = xlb_steal_rate_limit;
  }
  if (t - xlb_steal_last < interval)
    // Too soon to try again
    return false;

  if (backoff)
  {
    // Backoff expired, reset
    xlb_failed_steals_since_backoff = 0;
  }
  return true;
}
#endif
