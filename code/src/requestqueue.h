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
  void *_internal; /* Internal pointer, caller should not touch */
} xlb_request_entry;

adlb_code xlb_requestqueue_init(int my_workers);

adlb_code xlb_requestqueue_add(int rank, int type);

int xlb_requestqueue_matches_target(int target_rank, int type);

int xlb_requestqueue_matches_type(int type);

int xlb_requestqueue_size(void);

/**
   @return Count of each types
   @param types array to be filled in with result
   @param size size of the array (greater than xlb_types_size)
 */
void xlb_requestqueue_type_counts(int *types, int size);

/**
   Get number of workers (in result) equal to parallelism
   @return True iff enough workers were found
 */
bool xlb_requestqueue_parallel_workers(int type, int parallelism,
                                   int* result);

/**
   @param r Where to write output request_entrys.
            Must be preallocated to max*sizeof(request_entry)
   @param max Maximal number of request_pairs to return
   @return Actual number of request_pairs returned
 */
int xlb_requestqueue_get(xlb_request_entry* r, int max);

/**
  Remove an entry from the request queue
  This should not be called twice for the same entry.
 */
void xlb_requestqueue_remove(xlb_request_entry *e);

void xlb_requestqueue_shutdown(void);

#endif
