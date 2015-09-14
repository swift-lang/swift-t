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
 * Basic regression test for node targeting.
 *
 *  Created on: Mar 20, 2015
 *      Author: Tim Armstrong
 */
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "common/qtests.h"
#include "common/timers.h"

#include "common.h"
#include "checks.h"
#include "layout.h"
#include "requestqueue.h"
#include "workqueue.h"

/** Payload size for work units */
size_t payload_size = 0;

static adlb_code run();

int main(int argc, char **argv)
{

  adlb_code ac = run();

  if (ac != ADLB_SUCCESS) {
    fprintf(stderr, "FAILED!: %i\n", ac);
    return 1;
  }

  return 0;
}

#define COMM_SIZE 5

static adlb_code run()
{
  adlb_code ac;
 
  int type = 0;
  int my_rank = COMM_SIZE - 1;
  int nservers = 1;

  fprintf(stderr, "Initializing...\n");

  const char *fake_hosts[COMM_SIZE] =
      {"a", "a", "b", "b", "c"};
  
  int target_rank = 0;
  int should_match_rank = 1;
  int shouldnt_match_rank = 2;

  ac = qs_init(COMM_SIZE, my_rank, nservers, fake_hosts, 1);
  ADLB_CHECK(ac);

  ac = xlb_requestqueue_add(target_rank, type, 1, true);
  ADLB_CHECK(ac);

  // Check non-exact match
  int matched_rank;
  // Different node
  matched_rank = xlb_requestqueue_matches_target(shouldnt_match_rank,
                                            type, ADLB_TGT_ACCRY_NODE);
  CHECK_MSG(matched_rank == ADLB_RANK_NULL, "shouldn't match");

  // Same node but different rank
  matched_rank = xlb_requestqueue_matches_target(should_match_rank,
                                          type, ADLB_TGT_ACCRY_NODE);
  CHECK_MSG(matched_rank == target_rank, "should match");

  xlb_work_unit *wu = work_unit_alloc(payload_size);
  ADLB_ASSERT_MALLOC(wu);

  adlb_put_opts opts = ADLB_DEFAULT_PUT_OPTS;
  opts.accuracy = ADLB_TGT_ACCRY_NODE;
  xlb_work_unit_init(wu, type, 0, 0, target_rank, (int)payload_size, opts);

  ac = xlb_workq_add(wu);
  ADLB_CHECK(ac);

  xlb_work_unit *matched_wu;

  matched_wu = xlb_workq_get(shouldnt_match_rank, type);
  CHECK_MSG(matched_wu == NULL, "Shouldn't match");
  
  matched_wu = xlb_workq_get(should_match_rank, type);
  CHECK_MSG(matched_wu == wu, "Should match");

  fprintf(stderr, "Finalizing...\n");

  ac = qs_finalize();
  ADLB_CHECK(ac);

  fprintf(stderr, "Done.\n");

  return ADLB_SUCCESS;
}
