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

typedef int64_t xlb_work_unit_id;

typedef struct
{
  /** Unique ID wrt this server */
  xlb_work_unit_id id;
  /** Time at which this was enqueued */
  double timestamp;
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

void workqueue_init(int work_types);

xlb_work_unit_id workqueue_unique(void);

/** Allocate work unit with space for payload */
static inline xlb_work_unit *work_unit_alloc(size_t payload_length)
{
  // Allocate header struct plus following array
  return malloc(sizeof(xlb_work_unit) + payload_length);
}

/*
 * Initialize work unit fields and add to queue
 */
void workqueue_add(int type, int putter, int priority, int answer,
                   int target, int length, int parallelism,
                   xlb_work_unit *wu);

/**
   Return work unit for rank target and given type.
   Caller must work_unit_free() the result if
   Returns NULL if nothing found
 */
xlb_work_unit* workqueue_get(int target, int type);

/**
   Are we able to release a parallel task of type?
   If so, return true, put the work unit in wu, and the ranks in
   ranks.  Caller must free ranks
 */
bool workqueue_pop_parallel(xlb_work_unit** wu, int** ranks, int work_type);

extern int64_t workqueue_parallel_task_count;

static inline int64_t workqueue_parallel_tasks()
{
  TRACE("workqueue_parallel_tasks: %"PRId64"",
        workqueue_parallel_task_count);
  return workqueue_parallel_task_count;
}


typedef struct {
  adlb_code (*f)(void*, xlb_work_unit*);
  void *data;
} workqueue_steal_callback;

/*
 * steal_type_counts: counts that stealer has of each type
 * callback: called for every stolen unit.  The callback
            function is responsible for freeing work unit
 */
adlb_code workqueue_steal(int max_memory, const int *steal_type_counts,
                      workqueue_steal_callback cb);

/* present should be an array of size >= number of request types
 * it is filled in with the counts of types
 * types: array to be filled in
 * size: size of the array (greater than num of work types)
 * ntypes: returns number of elements filled in
 */
void workqueue_type_counts(int *types, int size);

void work_unit_free(xlb_work_unit* wu);

void workqueue_finalize(void);

#endif
