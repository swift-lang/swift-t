
/*
 * backoffs.c
 *
 *  Created on: Aug 22, 2012
 *      Author: wozniak
 */

#include <unistd.h>

#include "backoffs.h"
#include "tools.h"

// Allow debugging user to toggle these values:
// #define SLOW
#ifdef SLOW
       double xlb_max_idle          = 10;
       double xlb_steal_backoff     = 8;
static double backoff_server_max    = 2;
static double backoff_sync          = 1;
static double backoff_sync_rejected = 1;
#else // FAST
       double xlb_max_idle          = 1;
       double xlb_steal_backoff     = 0.001;
static double backoff_server_max    = 0.000001;
static double backoff_sync          = 0.00001;
static double backoff_sync_rejected = 0.0001;
#endif

void
xlb_backoff_server()
{
  // DEBUG("backoff()");
  int delay = random_between_double(0,backoff_server_max);
  time_delay(delay);
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
