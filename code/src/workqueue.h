
/*
 * queue.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#ifndef QUEUE_H
#define WORKQUEUE_H

#include <stdbool.h>

typedef struct
{
  double timestamp;
  int type;
  int priority;
  int answer;
  int target;
  int length;
  void* item;
} work_unit;

void workqueue_init(void);

void workqueue_add(int type, int priority, int answer, int target,
                   int length, void* work_unit, int* exhausted_flag);

/**
   Return item with highest priority
 */
adlb_code workqueue_pop(work_unit* w);

/**

 */
adlb_code workqueue_select_type(int type, work_unit* w);

int requestqueue_matches_target(int target_rank);

bool requestqueue_remove(int worker_rank);

#endif
