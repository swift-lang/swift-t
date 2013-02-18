
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

typedef long xlb_work_unit_id;

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
  /** Bulk work unit data */
  void* payload;
} xlb_work_unit;

void workqueue_init(int work_types);

xlb_work_unit_id workqueue_unique(void);

void workqueue_add(int type, int putter, int priority, int answer,
                   int target, int length, int parallelism,
                   void* payload);

/**
   Return work unit for rank target and given type.
   Caller must work_unit_free() the result if
   Returns NULL if nothing found
 */
xlb_work_unit* workqueue_get(int target, int type);

/**
   Are we able to release a parallel task?
   If so, return true, put the work unit in wu, and the ranks in
   ranks.  Caller must free ranks
 */
bool workqueue_pop_parallel(xlb_work_unit** wu, int** ranks);

/*
 *
 * count: counts of returned results (array of size nsteal_types)
 * stolen: array of stolen tasks (array of size nsteal_types)
 */
adlb_code workqueue_steal(int max_memory, int nsteal_types,
                          const int *steal_types,
                          int* count, xlb_work_unit*** stolen);

void work_unit_free(xlb_work_unit* wu);

void workqueue_finalize(void);

#endif
