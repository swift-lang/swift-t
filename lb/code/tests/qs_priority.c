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
 * Basic regression test for priority ordering.
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

#include "../src/adlb-checks.h"
#include "common/qtests.h"
#include "common/timers.h"

#include "common.h"
#include "layout.h"
#include "requestqueue.h"
#include "workqueue.h"

/** Payload size for work units */
size_t payload_size = 0;

static adlb_code run(void);
static adlb_code run2(bool targeted);

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

  int my_rank = COMM_SIZE - 1;
  int nservers = 1;

  fprintf(stderr, "Initializing...\n");

  const char *fake_hosts[COMM_SIZE] =
      {"a", "a", "b", "b", "c"};

  ac = qs_init(COMM_SIZE, my_rank, nservers, fake_hosts, 1);
  ADLB_CHECK(ac);

  fprintf(stderr, "Testing with targeted...\n");
  ac = run2(true);
  ADLB_CHECK(ac);

  fprintf(stderr, "Testing with untargeted...\n");
  ac = run2(false);
  ADLB_CHECK(ac);

  fprintf(stderr, "Finalizing...\n");

  ac = qs_finalize();
  ADLB_CHECK(ac);

  fprintf(stderr, "Done.\n");

  return ADLB_SUCCESS;
}

static adlb_code run2(bool targeted)
{
  adlb_code ac;
  int type = 0;
  int nwus = 100000;
  xlb_work_unit *wus[nwus];

  int target_rank = targeted ? 0 : ADLB_RANK_ANY;

  for (int i = 0; i < nwus; i++)
  {
    xlb_work_unit *wu = work_unit_alloc(payload_size);
    ADLB_CHECK_MALLOC(wu);

    adlb_put_opts opts = ADLB_DEFAULT_PUT_OPTS;
    // Assign distinct priority to each element
    opts.priority = i;
    xlb_work_unit_init(wu, type, 0, 0, target_rank, (int)payload_size, opts);

    wus[i] = wu;
  }

  // Insert in random order to better exercise queue
  srand(12345);
  shuffle_ptrs((void**)wus, nwus);

  for (int i = 0; i < nwus; i++)
  {
    ac = xlb_workq_add(wus[i]);
    ADLB_CHECK(ac);
  }

  for (int i = 0; i < nwus; i++)
  {
    int expected_priority = nwus - i - 1;


    int worker;
    if (targeted)
    {
      worker = target_rank;
    }
    else
    {
      worker = rand() % xlb_s.layout.workers;
    }

    xlb_work_unit *wu = xlb_workq_get(worker, type);
    ADLB_CHECK_MSG(wu != NULL, "expected wu");

    ADLB_CHECK_MSG(expected_priority == wu->opts.priority,
            "wrong priority: expected %i vs actual %i",
            expected_priority, wu->opts.priority);

    xlb_work_unit_free(wu);
  }

  return ADLB_SUCCESS;
}
