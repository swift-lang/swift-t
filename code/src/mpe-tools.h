
/*
 * mpe-tools.h
 *
 *  Created on: Aug 28, 2012
 *      Author: wozniak
 *
 *  Tools to simplify use of MPE
 */

#ifndef MPE_TOOLS_H
#define MPE_TOOLS_H

#include <stdio.h>

#include <mpe.h>

#ifdef ENABLE_MPE

// Event pairs
// Note: these names must be conventional for use with our macros
// The convention is: xlb_mpe_[svr|wkr]?_<OP>_[start|end]

// Events for servers and workers:
extern int xlb_mpe_init_start, xlb_mpe_init_end;
extern int xlb_mpe_finalize_start, xlb_mpe_finalize_end;

// Server handler events:
extern int xlb_mpe_svr_put_start, xlb_mpe_svr_put_end;
extern int xlb_mpe_svr_get_start, xlb_mpe_svr_get_end;
extern int xlb_mpe_svr_steal_start, xlb_mpe_svr_steal_end;

// Server daemon events (steal, shutdown):
extern int xlb_mpe_dmn_steal_start, xlb_mpe_dmn_steal_end;
extern int xlb_mpe_dmn_shutdown_start, xlb_mpe_dmn_shutdown_end;

// Client calls:
// Task operations:
extern int xlb_mpe_wkr_put_start, xlb_mpe_wkr_put_end;
extern int xlb_mpe_wkr_get_start, xlb_mpe_wkr_get_end;
// Data module:
extern int xlb_mpe_wkr_create_start, xlb_mpe_wkr_create_end;
extern int xlb_mpe_wkr_store_start, xlb_mpe_wkr_store_end;
extern int xlb_mpe_wkr_retrieve_start, xlb_mpe_wkr_retrieve_end;
extern int xlb_mpe_wkr_subscribe_start, xlb_mpe_wkr_subscribe_end;
extern int xlb_mpe_wkr_close_start, xlb_mpe_wkr_close_end;
extern int xlb_mpe_wkr_unique_start, xlb_mpe_wkr_unique_end;

/**
   Automate MPE_Log_get_state_eventIDs calls
 */
#define make_pair(token) \
  MPE_Log_get_state_eventIDs(&xlb_mpe_##token##_start,\
                             &xlb_mpe_##token##_end);

/**
  Automate MPE_Describe_state calls
 */
#define describe_pair(class,token) \
  MPE_Describe_state(xlb_mpe_##token##_start, xlb_mpe_##token##_end, \
                     #class "_" #token, "MPE_CHOOSE_COLOR")

void xlb_mpe_setup(void);

/**
   Log an empty event
 */
static inline void
mpe_log(int event)
{
  printf("mpe_log: %i\n", event);
  MPE_Log_event(event, 0, NULL);
}

/** Do x only if ENABLE_MPE is set */
#define MPE(x) x;

#else

/** MPE is not enabled - noop */
#define MPE(x)

#endif

#define MPE_LOG(e) MPE(mpe_log(e));

#endif
