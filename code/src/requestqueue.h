
/*
 * requestqueue.h
 *
 *  Created on: Jun 29, 2012
 *      Author: wozniak
 */

#ifndef REQUESTQUEUE_H
#define REQUESTQUEUE_H

void requestqueue_init(void);

void requestqueue_add(int rank, int type);

int requestqueue_matches_target(int target_rank, int type);

int requestqueue_matches_type(int type);

#endif
