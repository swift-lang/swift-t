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
 * server-prof.h
 *
 * Accumulate the wall-clock time an ADLB server spends in each of a few
 * broad categories, and report it at shutdown.
 *
 * Doubly gated: compiled out unless configured with
 * --enable-server-profile, and inert at run time unless the environment
 * variable ADLB_SERVER_PROFILE is set.  When compiled out, the macros
 * below expand to nothing, so the server loop is unchanged.
 *
 * Accounting is exclusive: time is charged to the innermost active
 * region only.  This matters because regions nest, e.g.
 * process_get_request() may start a steal while handling a GET, and we
 * want that charged to stealing rather than to task handling.
 *
 * NOTE: unrelated to adlb_prof.c, which despite its name is the public
 * ADLB_* -> ADLBP_* API shim.
 */

#pragma once

#include <stdbool.h>

#include "config.h"

#include "adlb-defs.h"
#include "messaging.h"

/**
   Categories of server time.  IDLE must be 0: it is the base of the
   region stack, and therefore accumulates all time not attributed to
   any other category.  That makes the server's polling and backoff
   loop idle time for free, with no instrumentation in the hot path.
 */
typedef enum
{
  XLB_PROF_IDLE = 0,
  XLB_PROF_STEAL_THIEF,
  XLB_PROF_STEAL_TARGET,
  XLB_PROF_TASK,
  XLB_PROF_DATA,
  XLB_PROF_OTHER,
  XLB_PROF_CATEGORIES
} xlb_prof_cat;

/**
   Set up the profile state.  Reads ADLB_SERVER_PROFILE.
   Server-only: call from xlb_server_init().
 */
adlb_code xlb_prof_init(void);

/**
   Open the base (idle) region and take the first timestamp.
   Call when the server loop is about to start, so that start-up time
   is not charged to the server.
 */
void xlb_prof_start(void);

/** Report the accumulated times.  Call from print_final_stats(). */
void xlb_prof_print(void);

#if XLB_ENABLE_SERVER_PROFILE

/**
   Maximal region nesting.  Deep nesting is normal here: a sync accepted
   while serving a request may itself serve a request, which may start a
   steal, which may spin the sync loop again.
 */
#define XLB_PROF_STACK_MAX 64

/** Whether profiling was requested via the environment */
extern bool xlb_prof_enabled;

/** Accumulated seconds per category */
extern double xlb_prof_total[XLB_PROF_CATEGORIES];

/** Category to charge each tag to, indexed by adlb_tag */
extern unsigned char xlb_prof_tag_cat[XLB_MAX_TAGS];

void xlb_prof_enter_impl(xlb_prof_cat cat);
void xlb_prof_exit_impl(void);
void xlb_prof_unwind_impl(void);

/** Charge time to cat until the matching XLB_PROF_EXIT() */
#define XLB_PROF_ENTER(cat) \
  do { if (xlb_prof_enabled) xlb_prof_enter_impl(cat); } while (0)

/** Close the region opened by XLB_PROF_ENTER() */
#define XLB_PROF_EXIT() \
  do { if (xlb_prof_enabled) xlb_prof_exit_impl(); } while (0)

/** As XLB_PROF_ENTER(), choosing the category from a message tag */
#define XLB_PROF_ENTER_TAG(tag) \
  do { if (xlb_prof_enabled) \
         xlb_prof_enter_impl((xlb_prof_cat) xlb_prof_tag_cat[tag]); \
     } while (0)

/**
   Drop back to the base region.  Called once per server loop iteration
   so that a region left open by an error path costs at most one
   iteration of misattribution instead of unbalancing the stack.
 */
#define XLB_PROF_UNWIND() \
  do { if (xlb_prof_enabled) xlb_prof_unwind_impl(); } while (0)

#else // not XLB_ENABLE_SERVER_PROFILE

#define XLB_PROF_ENTER(cat)     ((void) 0)
#define XLB_PROF_EXIT()         ((void) 0)
#define XLB_PROF_ENTER_TAG(tag) ((void) 0)
#define XLB_PROF_UNWIND()       ((void) 0)

#endif
