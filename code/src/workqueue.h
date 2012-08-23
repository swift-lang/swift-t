
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
  /** Bulk work unit data */
  void* payload;
} xlb_work_unit;

void workqueue_init(int work_types);

xlb_work_unit_id workqueue_unique(void);

void workqueue_add(int type, int putter, int priority, int answer,
                   int target, int length, void* work_unit);

/**
   Return work unit for rank target and given type.
   Caller must work_unit_free() the result if
   Returns NULL if nothing found
 */
xlb_work_unit* workqueue_get(int target, int type);

/**
   Return item with highest priority that is not targeted
 */
adlb_code workqueue_pop(xlb_work_unit* w);

/**

 */
adlb_code workqueue_select_type(int type, xlb_work_unit* w);

adlb_code workqueue_steal(int max_memory, int* count,
                          xlb_work_unit*** stolen);

void work_unit_free(xlb_work_unit* wu);

void workqueue_finalize(void);

#endif
