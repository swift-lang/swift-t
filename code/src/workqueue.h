
/*
 * queue.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#ifndef QUEUE_H
#define WORKQUEUE_H

#include <stdbool.h>

#include "adlb-defs.h"

typedef struct
{
  /** Unique ID wrt this server */
  long id;
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
  void* item;
} work_unit;

void workqueue_init(int work_types);

void workqueue_add(int type, int putter, int priority, int answer,
                   int target, int length, void* work_unit);

work_unit* workqueue_get(int type, int target);

/**
   Return item with highest priority that is not targeted
 */
adlb_code workqueue_pop(work_unit* w);

/**

 */
adlb_code workqueue_select_type(int type, work_unit* w);

#endif
