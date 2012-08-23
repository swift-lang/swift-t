
/*
 * backoffs.h
 *
 *  Created on: Aug 22, 2012
 *      Author: wozniak
 */

#ifndef BACKOFFS_H
#define BACKOFFS_H

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

/**
   Backoff while in server loop
 */
void xlb_backoff_server(void);

/**
   Backoff during sync() spin loop
 */
void xlb_backoff_sync(void);

/**
   Backoff during sync() spin loop after rejection
 */
void xlb_backoff_sync_rejected(void);

#endif
