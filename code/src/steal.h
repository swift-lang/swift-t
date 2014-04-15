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
 *      Author: wozniak
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

adlb_code xlb_steal_init(void);

/**
   Are there any other servers?
   Are we allowed to steal yet?
 */
static inline bool
xlb_steal_allowed(void)
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

/**
   Issue sync() and steal.

   Note that this may add sync requests to the xlb_pending_syncs list,
   which must be handled by the caller.
   @param stole_single true if stole single-worker task, else false
   @param stole_par true if stole parallel task, else false
 */
adlb_code xlb_steal(bool* stole_single, bool *stole_par);

/**
   Handle an accepted steal request
  */
adlb_code xlb_handle_steal(int caller, const struct packed_steal *req);

#endif
