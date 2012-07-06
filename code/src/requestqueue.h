
/*
 * requestqueue.h
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 *
 *  Queue containing worker ranks requesting work
 *  These workers are waiting for another worker to Put() work
 *  that they may run in the future
 */

#ifndef REQUESTQUEUE_H
#define REQUESTQUEUE_H

void requestqueue_init(int work_types);

void requestqueue_add(int rank, int type);

int requestqueue_matches_target(int target_rank, int type);

int requestqueue_matches_type(int type);

int requestqueue_size(void);

void requestqueue_shutdown(void);

#endif
