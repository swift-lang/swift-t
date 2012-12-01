
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
   How long should we wait between steal attempts? In seconds
   This must be much bigger than xlb_backoff_server() to allow the
   master server to shut other servers down
 */
extern double xlb_steal_backoff;

// Maximum requests to serve before yielding to main server loop
extern int xlb_loop_max_requests;

// Maximum polls before yielding to main server loop
extern int xlb_loop_max_polls;

/**
   Backoff while in server loop
   @param attempt: what level we should go to
   returns true if should retry again later with higher level
 */
bool xlb_backoff_server(int attempt);

/**
   Backoff during sync() spin loop
 */
void xlb_backoff_sync(void);

/**
   Backoff during sync() spin loop after rejection
 */
void xlb_backoff_sync_rejected(void);

#endif
