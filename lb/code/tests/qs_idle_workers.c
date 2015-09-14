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
 * Regression test for number of idle workers and non-blocking requests
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

  ac = qs_init(COMM_SIZE, my_rank, nservers, fake_hosts, 1);
  ADLB_CHECK(ac);

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 0, "initial nblocked");

  ac = xlb_requestqueue_incr_blocked();
  ADLB_CHECK(ac);

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 1, "nblocked");

  ac = xlb_requestqueue_incr_blocked();
  ADLB_CHECK(ac);
  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 2, "nblocked");

  // Non-blocking request
  ac = xlb_requestqueue_add(target_rank, type, 5, false);
  ADLB_CHECK(ac);

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 2, "nblocked");

  // Last one blocks
  ac = xlb_requestqueue_add(target_rank, type, 5, true);
  ADLB_CHECK(ac);

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 3, "nblocked");

  // Remove by type
  int rank = xlb_requestqueue_matches_type(type);
  ADLB_CHECK_MSG(rank == target_rank, "rank");

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 2, "nblocked");

  ac = xlb_requestqueue_add(target_rank, type, 1, true);
  ADLB_CHECK(ac);

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 3, "nblocked");

  // Remove by target
  rank = xlb_requestqueue_matches_target(target_rank, type,
                                      ADLB_TGT_ACCRY_RANK);
  ADLB_CHECK_MSG(rank == target_rank, "rank");

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 2, "nblocked");

  ac = xlb_requestqueue_decr_blocked();
  ADLB_CHECK(ac);
  ac = xlb_requestqueue_decr_blocked();
  ADLB_CHECK(ac);

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 0, "nblocked");

  // Remove remaining non-blocking tasks
  for (int i = 0; i < 9; i++)
  {
    rank = xlb_requestqueue_matches_type(0);
    ADLB_CHECK_MSG(rank == target_rank, "rank");
  }

  ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 0, "nblocked");

  ac = qs_finalize();
  ADLB_CHECK(ac);

  fprintf(stderr, "Done.\n");

  return ADLB_SUCCESS;
}
