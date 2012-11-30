
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

// Progress speeds:
#define SLOW   0 // for fine-tuned debugging
#define MEDIUM 1 // good for use with valgrind
#define FAST   2 // normal - do not use with valgrind (thrashes)
#define SPEED  FAST
// All backoffs in seconds
#if SPEED == SLOW
       double xlb_max_idle          = 10;
       double xlb_steal_backoff     = 8;
static double backoff_server_max    = 2;
static int    backoff_server_no_delay_attempts  = 0;
static int    backoff_server_min_delay_attempts = 1;
static int    backoff_server_exp_delay_attempts = 0;
static double backoff_sync          = 1;
static double backoff_sync_rejected = 1;
#elif SPEED == MEDIUM
       double xlb_max_idle          = 4;
       double xlb_steal_backoff     = 0.5;
static double backoff_server_max    = 0.001;
static int    backoff_server_no_delay_attempts  = 0;
static int    backoff_server_min_delay_attempts = 1;
static int    backoff_server_exp_delay_attempts = 0;
static double backoff_sync          = 0.01;
static double backoff_sync_rejected = 0.01;
#elif SPEED == FAST
       double xlb_max_idle          = 1;
       double xlb_steal_backoff     = 0.01;
static double backoff_server_max    = 0.000001;
static int    backoff_server_no_delay_attempts  = 1024;
static int    backoff_server_min_delay_attempts = 8;
static int    backoff_server_exp_delay_attempts = 4;
static double backoff_sync          = 0.00001;
static double backoff_sync_rejected = 0.0001;
#endif

#define BACKOFF_SERVER_TOTAL_ATTEMPTS \
    (backoff_server_no_delay_attempts + backoff_server_min_delay_attempts \
    + backoff_server_exp_delay_attempts)

bool
xlb_backoff_server(int attempt)
{
  // DEBUG("backoff()");
  if (attempt < backoff_server_no_delay_attempts) {
    return true;
  } else  {
    double delay;
    if (attempt < backoff_server_no_delay_attempts
                + backoff_server_min_delay_attempts) {
      // Try yielding for min time
      delay = backoff_server_max;
    } else {
      int exponent = attempt - backoff_server_no_delay_attempts
                - backoff_server_min_delay_attempts + 1;
      delay = pow(2, exponent) * backoff_server_max;
    }
    time_delay(delay);
    return attempt < BACKOFF_SERVER_TOTAL_ATTEMPTS - 1;
  }
}

void
xlb_backoff_sync()
{
  time_delay(backoff_sync);
}

void
xlb_backoff_sync_rejected()
{
  time_delay(backoff_sync_rejected);
}
