
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

#ifdef ENABLE_MPE

#include <stdio.h>

#include <mpe.h>

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
extern int xlb_mpe_svr_shutdown_start, xlb_mpe_svr_shutdown_end;

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

// Info event:
extern int xlb_mpe_svr_info;

/**
   Automate MPE_Log_get_state_eventIDs calls
 */
#define make_pair(token) \
  MPE_Log_get_state_eventIDs(&xlb_mpe_##token##_start,\
                             &xlb_mpe_##token##_end);

#define make_solo(token) \
    MPE_Log_get_solo_eventID(&xlb_mpe_##token);

/**
  Automate MPE_Describe_state calls
 */
#define describe_pair(class,token) \
  MPE_Describe_state(xlb_mpe_##token##_start, xlb_mpe_##token##_end, \
                     #class "_" #token, "MPE_CHOOSE_COLOR")

#define describe_solo(class, token) \
   MPE_Describe_event(xlb_mpe_##token, #class "_" #token, \
                          "MPE_CHOOSE_COLOR");
//  MPE_Describe_info_event(xlb_mpe_##token, #class "_" #token, \
//                          "MPE_CHOOSE_COLOR", NULL);

void xlb_mpe_setup(void);

/** Do x only if ENABLE_MPE is set */
#define MPE(x) x;

#define MPE_INFO(e,fmt,args...) { \
  char t[32];                \
  snprintf(t, 32, fmt, ## args);      \
  printf("INFO: %s\n", t);\
  MPE_Log_event(e,0,t);}

#else

/** MPE is not enabled - noop */
#define MPE(x)
/** MPE is not enabled - noop */
#define MPE_INFO(e,msg...)

#endif

#define MPE_LOG(e) MPE(MPE_Log_bare_event(e));

#endif
