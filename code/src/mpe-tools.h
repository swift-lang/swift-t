
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

#include <mpe.h>

#ifdef ENABLE_MPE

/** Automate MPE_Log_get_state_eventIDs calls */
#define make_pair(token) \
  MPE_Log_get_state_eventIDs(&mpe_##token##_start,\
                             &mpe_##token##_end);

/** Automate MPE_Describe_state calls */
#define describe_pair(class,token) \
  MPE_Describe_state(mpe_##token##_start, mpe_##token##_end, \
                     #class "_" #token, "MPE_CHOOSE_COLOR")

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
