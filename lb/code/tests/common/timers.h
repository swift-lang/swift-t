/*
 * Copyright 2015 University of Chicago and Argonne National Laboratory
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
 * timers.h
 *
 * Common timing functions for tests
 *
 *  Created on: Mar 19, 2015
 *      Author: Tim Armstrong
 */

#ifndef __TIMERS_H
#define __TIMERS_H

#include <time.h>

typedef struct {
  struct timespec begin, end;
} expt_timers;


static inline void time_begin(expt_timers *timers)
{
  int rc = clock_gettime(CLOCK_THREAD_CPUTIME_ID, &timers->begin);
  assert(rc == 0);
}

static inline void time_end(expt_timers *timers)
{
  int rc = clock_gettime(CLOCK_THREAD_CPUTIME_ID, &timers->end);
  assert(rc == 0);
}

/*
  Duration in nanoseconds.
  Note: difference must fit in long long!
 */
static inline long long duration_nsec(expt_timers timers)
{
  return ((long long)(timers.end.tv_sec - timers.begin.tv_sec)) * 1000000000 +
          timers.end.tv_nsec - timers.begin.tv_nsec;
}

#endif // __TIMERS_H
