
/*
 * backoffs.c
 *
 *  Created on: Aug 22, 2012
 *      Author: wozniak
 */

#include <unistd.h>

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
static double backoff_sync          = 1;
static double backoff_sync_rejected = 1;
#elif SPEED == MEDIUM
       double xlb_max_idle          = 4;
       double xlb_steal_backoff     = 0.5;
static double backoff_server_max    = 0.001;
static double backoff_sync          = 0.01;
static double backoff_sync_rejected = 0.01;
#elif SPEED == FAST
       double xlb_max_idle          = 1;
       double xlb_steal_backoff     = 0.01;
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
