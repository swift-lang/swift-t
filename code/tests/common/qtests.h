/*
 * Copyright 2015 University of Chicago and Argonne National Laboratory
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
 * qtests.h
 *
 * Common functions for queue tests
 *
 *  Created on: Mar 19, 2015
 *      Author: Tim Armstrong
 */

#ifndef __QTESTS_H
#define __QTESTS_H

#include "adlb-defs.h"
#include "common.h"
#include "workqueue.h"

typedef enum {
  EQUAL,
  UNIFORM_RANDOM,
} prio_mix;

typedef enum {
  UNTARGETED,
  RANK_TARGETED,
  NODE_TARGETED,
  EQUAL_MIX, // 50% untargeted, 50% rank targeted
  // TODO: mix with node targeted?
  RANK_SOFT_TARGETED, // soft targeted to 50% of ranks
  NODE_SOFT_TARGETED, // soft targeted to 50% of nodes
} tgt_mix;

const char *prio_mix_str(prio_mix prio);
const char *tgt_mix_str(tgt_mix tgt);

/*
  Setup test environment for work and request queues.
  comm_size: communicator size
  my_rank: rank in communicator
  nservers: number of servers
  hosts: array of comm_size host names
  ntypes: number of work types
 */
adlb_code qs_init(int comm_size, int my_rank, int nservers,
                  const char **hosts, int ntypes);

adlb_code qs_finalize(void);

/*
  Make a set of fake host names with ranks split between hosts.
  Fills in fake_hosts with comm_size host names.
 */
void make_fake_hosts(const char **fake_hosts, int comm_size);

/*
  Drain request queue
 */
adlb_code drain_rq(void);

/*
  Drain work queue.  If nexpected is non-negative, check that
  expected number of tasks were present.
  if expected_payload_size is non-negative, check that too
 */
adlb_code drain_wq(int expected_payload_size, int nexpected,
                   bool free_wus);

/*
  Make a work unit according to parameters
 */
adlb_code make_wu(prio_mix prios, tgt_mix tgts,
                    size_t payload_len, xlb_work_unit **wu_result);

/*
  Create a array of work units according to parameters
 */
adlb_code make_wus(prio_mix prios, tgt_mix tgts,
            size_t payload_len, int nwus, xlb_work_unit ***wu_result);

/*
  Free array of work units
 */
void free_wus(int nwus, xlb_work_unit **wus);

int select_wu_target(tgt_mix tgts);

bool is_soft_target(tgt_mix tgts);

adlb_target_accuracy get_target_accuracy(tgt_mix tgts);

/*
 * Warm up and do some sanity checks for work queue.
 */
adlb_code warmup_wq(size_t payload_size);

/*
 * Warm up and do some sanity checks for request queue.
 */
adlb_code warmup_rq(void);

/*
  Knuth shuffle of pointer array.  Calls rand() for randomness
 */
void knuth_shuffle(void **A, int n);

/*===============================*
  Inline function implementations
 *===============================*/
static inline bool same_host(int rank1, int rank2)
{
  return host_idx_from_rank(&xlb_s.layout, rank1) ==
         host_idx_from_rank(&xlb_s.layout, rank2);
}

#endif // __QTESTS_H
