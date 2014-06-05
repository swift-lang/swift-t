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
 * queue.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#ifndef WORKQUEUE_H
#define WORKQUEUE_H

#include <stdbool.h>

#include "adlb-defs.h"

#include "debug.h"

typedef int64_t xlb_work_unit_id;

#define XLB_WORK_UNIT_ID_NULL (-1)

typedef struct
{
  /** Unique ID wrt this server */
  xlb_work_unit_id id;
  /** Time at which this was enqueued 
      NOTE: unused */
  // double timestamp;
  /** Work type */
  int type;
  /** Rank that put this work unit */
  int putter;
  /** Priority */
  int priority;
  /** Answer rank: application-specific field */
  int answer;
  /** If set, worker rank that should receive this */
  int target;
  /** Length of item */
  int length;
  /** Number of processes required to run this task */
  int parallelism;
  /** Bulk work unit data 
      Payload kept contiguous with data to save memory allocation */
  unsigned char payload[]; 
} xlb_work_unit;


adlb_code xlb_workq_init(int work_types, int my_workers);

xlb_work_unit_id xlb_workq_unique(void);

/** Allocate work unit with space for payload */
static inline xlb_work_unit *work_unit_alloc(size_t payload_length)
{
  // Allocate header struct plus following array
  return malloc(sizeof(xlb_work_unit) + payload_length);
}

/** Initialize work unit fields, aside from payload */
static inline void xlb_work_unit_init(xlb_work_unit *wu, int type,
      int putter, int priority, int answer, int target_rank, int length,
      int parallelism)
{
  wu->id = xlb_workq_unique();
  wu->type = type;
  wu->putter = putter;
  wu->priority = priority;
  wu->answer = answer;
  wu->target = target_rank;
  wu->length = length;
  wu->parallelism = parallelism;
}

/*
 * Add work unit to queue.  All fields of work unit must be init.
 */
adlb_code xlb_workq_add(xlb_work_unit *wu);

/**
   Return work unit for rank target and given type.
   Caller must xlb_work_unit_free() the result if
   Returns NULL if nothing found
 */
xlb_work_unit* xlb_workq_get(int target, int type);

/**
   Are we able to release a parallel task of type?
   If so, return true, put the work unit in wu, and the ranks in
   ranks.  Caller must free ranks
 */
bool xlb_workq_pop_parallel(xlb_work_unit** wu, int** ranks, int work_type);

extern int64_t xlb_workq_parallel_task_count;

static inline int64_t xlb_workq_parallel_tasks()
{
  TRACE("xlb_workq_parallel_tasks: %"PRId64"",
        xlb_workq_parallel_task_count);
  return xlb_workq_parallel_task_count;
}


typedef struct {
  adlb_code (*f)(void*, xlb_work_unit*);
  void *data;
} xlb_workq_steal_callback;

/*
 * steal_type_counts: counts that stealer has of each type
 * callback: called for every stolen unit.  The callback
            function is responsible for freeing work unit
 */
adlb_code xlb_workq_steal(int max_memory, const int *steal_type_counts,
                      xlb_workq_steal_callback cb);

/* present should be an array of size >= number of request types
 * it is filled in with the counts of types
 * Does not include targeted work.
 * types: array to be filled in
 * size: size of the array (greater than num of work types)
 * ntypes: returns number of elements filled in
 */
void xlb_workq_type_counts(int *types, int size);

void xlb_work_unit_free(xlb_work_unit* wu);

void xlb_print_workq_perf_counters(void);

void xlb_workq_finalize(void);


typedef struct {

  /** Number of targeted tasks added to work queue */
  int64_t targeted_enqueued;

  /** Number of targeted tasks bypassing work queue */
  int64_t targeted_bypass;
  
  /** Untargeted serial added to work queue */
  int64_t single_enqueued;
  
  /** Number of untargeted serial tasks bypassing work queue */
  int64_t single_bypass;

  /** Number stolen (will be counted as added to other server's queue) */
  int64_t single_stolen;

  /** Parallel tasks added to work queue */
  int64_t parallel_enqueued;
  
  /** Number of parallel tasks bypassing work queue */
  int64_t parallel_bypass;

  /** Parallel tasks stolen */
  int64_t parallel_stolen;

  /*
   * Data-dependent task counters:
   */ 

  /** Number of targeted tasks that must wait for input */
  int64_t targeted_data_wait;

  /** Number of targeted tasks that were ready immediately */
  int64_t targeted_data_no_wait;
  
  /** Number of single tasks that must wait for input */
  int64_t single_data_wait;

  /** Number of single tasks that were ready immediately */
  int64_t single_data_no_wait;
  
  /** Number of parallel tasks that must wait for input */
  int64_t parallel_data_wait;

  /** Number of parallel tasks that were ready immediately */
  int64_t parallel_data_no_wait;
} work_type_counters;

extern work_type_counters *xlb_task_counters;

/*
 * Mark that a task bypassed the work queue for performance counters.
 * Inline because it is very small and called frequently
 */
static inline void xlb_task_bypass_count(int type, bool targeted,
                                    bool parallel)
{
  if (xlb_perf_counters_enabled)
  {
    work_type_counters *tc = &xlb_task_counters[type];
    if (targeted)
    {
      tc->targeted_bypass++;
    }
    else if (parallel)
    {
      tc->parallel_bypass++;
    }
    else
    {
      tc->single_bypass++;
    }
  }
}

/*
 * Mark that a rule task was created
 * wait: if it has to wait for data
 */
static inline void xlb_task_data_count(int type, bool targeted,
                                      bool parallel, bool wait)
{
  if (xlb_perf_counters_enabled)
  {
    work_type_counters *tc = &xlb_task_counters[type];
    if (targeted)
    {
      if (wait)
        tc->targeted_data_wait++;
      else
        tc->targeted_data_no_wait++;
    }
    else if (parallel)
    {
      if (wait)
        tc->parallel_data_wait++;
      else
        tc->parallel_data_no_wait++;
    }
    else
    {
      if (wait)
        tc->single_data_wait++;
      else
        tc->single_data_no_wait++;
    }
  }
}

#endif
