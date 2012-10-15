
/*
 * requestqueue.h
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 *
 *  Queue containing worker ranks requesting work
 *  These workers are waiting for another worker to Put() work
 *  that they may run in the future, or for stolen work
 */

#ifndef REQUESTQUEUE_H
#define REQUESTQUEUE_H

typedef struct
{
  int rank;
  int type;
} xlb_request_pair;

void requestqueue_init(int work_types);

void requestqueue_add(int rank, int type);

int requestqueue_matches_target(int target_rank, int type);

int requestqueue_matches_type(int type);

int requestqueue_size(void);

/**
   @param r Where to write output request_pairs.
            Must be preallocated to max*sizeof(request_pair)
   @param max Maximal number of request_pairs to return
   @return Actual number of request_pairs returned
 */
int requestqueue_get(xlb_request_pair* r, int max);

void requestqueue_remove(int worker_rank);

void requestqueue_shutdown(void);

#endif
