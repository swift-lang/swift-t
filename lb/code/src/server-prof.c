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
 * server-prof.c
 *
 * See server-prof.h
 */

#include <mpi.h>

#include <tools.h>

#include "common.h"
#include "server-prof.h"

#if XLB_ENABLE_SERVER_PROFILE

bool xlb_prof_enabled = false;

double xlb_prof_total[XLB_PROF_CATEGORIES];

unsigned char xlb_prof_tag_cat[XLB_MAX_TAGS];

/** Stack of open regions.  Entry 0 is the base (idle) region. */
static xlb_prof_cat stack[XLB_PROF_STACK_MAX];

/** Index of the innermost open region */
static int depth;

/** Time of the most recent region transition */
static double last;

/** Pushes refused because the stack was full */
static int64_t overflows;

/** Regions left open at the end of a server loop iteration */
static int64_t leaks;

static const char *const names[XLB_PROF_CATEGORIES] = {
  "idle", "steal_thief", "steal_target", "task", "data", "other",
};

/**
   Charge the time since the previous transition to the innermost
   region, and start a new interval.
 */
static inline void
charge(void)
{
  double now = MPI_Wtime();
  xlb_prof_total[stack[depth]] += now - last;
  last = now;
}

static void
classify_tags(void)
{
  // Anything we do not name below, including tags with no handler,
  // falls into "other" rather than going unaccounted
  for (int tag = 0; tag < XLB_MAX_TAGS; tag++)
    xlb_prof_tag_cat[tag] = XLB_PROF_OTHER;

  const adlb_tag task[] = {
    ADLB_TAG_PUT, ADLB_TAG_DPUT, ADLB_TAG_GET, ADLB_TAG_IGET,
    ADLB_TAG_AMGET,
  };
  const adlb_tag data[] = {
    ADLB_TAG_CREATE_HEADER, ADLB_TAG_MULTICREATE, ADLB_TAG_EXISTS,
    ADLB_TAG_STORE_HEADER, ADLB_TAG_RETRIEVE, ADLB_TAG_ENUMERATE,
    ADLB_TAG_SUBSCRIBE, ADLB_TAG_NOTIFY, ADLB_TAG_GET_REFCOUNTS,
    ADLB_TAG_REFCOUNT_INCR, ADLB_TAG_INSERT_ATOMIC, ADLB_TAG_UNIQUE,
    ADLB_TAG_TYPEOF, ADLB_TAG_CONTAINER_TYPEOF,
    ADLB_TAG_CONTAINER_REFERENCE, ADLB_TAG_CONTAINER_SIZE,
    ADLB_TAG_LOCK, ADLB_TAG_UNLOCK,
  };

  for (size_t i = 0; i < sizeof(task)/sizeof(task[0]); i++)
    xlb_prof_tag_cat[task[i]] = XLB_PROF_TASK;
  for (size_t i = 0; i < sizeof(data)/sizeof(data[0]); i++)
    xlb_prof_tag_cat[data[i]] = XLB_PROF_DATA;

  // A steal response arriving late, after the steal itself gave up
  xlb_prof_tag_cat[ADLB_TAG_RESPONSE_STEAL_COUNT] = XLB_PROF_STEAL_THIEF;
}

adlb_code
xlb_prof_init(void)
{
  for (int i = 0; i < XLB_PROF_CATEGORIES; i++)
    xlb_prof_total[i] = 0.0;

  stack[0] = XLB_PROF_IDLE;
  depth = 0;
  last = 0.0;
  overflows = 0;
  leaks = 0;

  classify_tags();

  getenv_boolean("ADLB_SERVER_PROFILE", false, &xlb_prof_enabled);

  return ADLB_SUCCESS;
}

void
xlb_prof_start(void)
{
  if (!xlb_prof_enabled)
    return;
  // Start the clock here rather than in xlb_prof_init() so that
  // start-up is not charged to the server as idle time
  last = MPI_Wtime();
}

void
xlb_prof_enter_impl(xlb_prof_cat cat)
{
  charge();
  if (depth + 1 >= XLB_PROF_STACK_MAX)
  {
    // Keep accounting to the current region rather than overrunning
    overflows++;
    return;
  }
  stack[++depth] = cat;
}

void
xlb_prof_exit_impl(void)
{
  charge();
  if (depth > 0)
    depth--;
  // An unmatched exit would otherwise pop the base region away
}

void
xlb_prof_unwind_impl(void)
{
  charge();
  if (depth > 0)
  {
    // Some region was left open, most likely by an error path that
    // returned between enter and exit.  Report rather than hide it.
    leaks++;
    depth = 0;
  }
}

void
xlb_prof_print(void)
{
  if (!xlb_prof_enabled)
    return;

  // Close the region still open so its final interval is counted
  xlb_prof_unwind_impl();

  double total = 0.0;
  for (int i = 0; i < XLB_PROF_CATEGORIES; i++)
    total += xlb_prof_total[i];

  printf("ADLB Server Profile[%i]:", xlb_s.layout.rank);
  for (int i = 0; i < XLB_PROF_CATEGORIES; i++)
    printf(" %s=%.6lf", names[i], xlb_prof_total[i]);
  printf(" total=%.6lf", total);
  if (leaks > 0)
    printf(" leaks=%"PRId64, leaks);
  if (overflows > 0)
    printf(" overflows=%"PRId64, overflows);
  printf("\n");
}

#else // not XLB_ENABLE_SERVER_PROFILE

adlb_code xlb_prof_init(void) { return ADLB_SUCCESS; }
void      xlb_prof_start(void) {}
void      xlb_prof_print(void) {}

#endif
