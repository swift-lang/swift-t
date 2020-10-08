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
 * backoffs.c
 *
 *  Created on: Aug 22, 2012
 *      Author: wozniak
 */

#include <unistd.h>
#include <math.h>

#include "backoffs.h"
#include "tools.h"

// All backoffs in seconds
#if BACKOFF_SPEED == BACKOFF_SLOW
       double xlb_max_idle          = 10;
       double xlb_steal_rate_limit  = 8;
       double xlb_steal_backoff     = 8;
       double xlb_steal_concurrency_limit = 1;
static double backoff_server_max    = 2;
static int    backoff_server_no_delay_attempts  = 0;
static int    backoff_server_min_delay_attempts = 1;
static int    backoff_server_exp_delay_attempts = 0;
       int    xlb_loop_threshold      = 1;
       int    xlb_loop_request_points = 1;
       int    xlb_loop_poll_points    = 1;
       int    xlb_loop_sleep_points   = 1;
static double backoff_sync          = 1;
#elif BACKOFF_SPEED == BACKOFF_MEDIUM
       double xlb_max_idle          = 4;
       double xlb_steal_rate_limit  = 0.5;
       double xlb_steal_backoff     = 0.5;
       double xlb_steal_concurrency_limit = 1;
static double backoff_server_max    = 0.001;
static int    backoff_server_no_delay_attempts  = 0;
static int    backoff_server_min_delay_attempts = 1;
static int    backoff_server_exp_delay_attempts = 0;
static double backoff_sync          = 0.01;
#elif BACKOFF_SPEED == BACKOFF_FAST
       double xlb_max_idle          = 0.1;
/*
   Rate-limit steals.  The rate needs to be slow enough so that we don't
   overwhelm other servers that could be doing more useful work.
   xlb_steal_rate_limit: absolute maximum rate.  If we can serve 200k
            requests per sec per server, 500us would mean that at most
            1/100 requests were work-stealing requests.
   xlb_steal_backoff: take a break from stealing after trying #servers times
 */
       double xlb_steal_rate_limit  = 0.05;
       double xlb_steal_backoff     = 0.1;
       double xlb_steal_concurrency_limit = 2;
static double backoff_server_max    = 0.000001;
static int    backoff_server_no_delay_attempts  = 1024;
static int    backoff_server_min_delay_attempts = 4;
static int    backoff_server_exp_delay_attempts = 4;
static double backoff_sync          = 0.00001;
#endif

#define BACKOFF_SERVER_TOTAL_ATTEMPTS \
    (backoff_server_no_delay_attempts + backoff_server_min_delay_attempts \
    + backoff_server_exp_delay_attempts)

bool
xlb_backoff_server(int attempt, bool* slept)
{
  // DEBUG("backoff()");
  if (attempt < backoff_server_no_delay_attempts)
  {
    *slept = false;
    return true;
  }
  else
  {
    double delay;
    if (attempt < backoff_server_no_delay_attempts
                + backoff_server_min_delay_attempts)
    {
      // Try yielding for min time
      delay = backoff_server_max;
    }
    else
    {
      int exponent = attempt - backoff_server_no_delay_attempts
                - backoff_server_min_delay_attempts + 1;
      if (exponent > backoff_server_exp_delay_attempts) {
        exponent = backoff_server_exp_delay_attempts;
      }
      delay = pow(2, exponent) * backoff_server_max;
    }
    time_delay(delay);
    *slept = true;
    return attempt < BACKOFF_SERVER_TOTAL_ATTEMPTS - 1;
  }
}

void
xlb_backoff_sync()
{
  time_delay(backoff_sync);
}
