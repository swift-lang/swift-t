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
 * qtests.c
 *
 * Common functions for queue tests
 *
 *  Created on: Mar 19, 2015
 *      Author: Tim Armstrong
 */

#include "qtests.h"

#include <stdlib.h>

#include "adlb.h"
#include "checks.h"
#include "layout.h"
#include "requestqueue.h"
#include "workqueue.h"

static adlb_code check_hostnames(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size);
static adlb_code setup_hostmap(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size);

const char* prio_mix_str(prio_mix prio)
{
  if (prio == EQUAL)
  {
    return "EQUAL";
  }
  else
  {
    assert(prio == UNIFORM_RANDOM);
    return "UNIFORM_RANDOM";
  }
}

const char* tgt_mix_str(tgt_mix tgt)
{
  switch (tgt)
  {
    case UNTARGETED: return "UNTARGETED";
    case RANK_TARGETED: return "RANK_TARGETED";
    case RANK_SOFT_TARGETED: return "RANK_SOFT_TARGETED";
    case EQUAL_MIX: return "EQUAL_MIX";
    case NODE_TARGETED: return "NODE_TARGETED";
    case NODE_SOFT_TARGETED: return "NODE_SOFT_TARGETED";
    default: assert(false);
  }
  // Unreachable:
  assert(false);
  return NULL;
}

adlb_code qs_init(int comm_size, int my_rank, int nservers,
                  const char **hosts, int ntypes)
{
  adlb_code ac;

  xlb_s.types_size = ntypes;

  // Workaround: disable debug logging to avoid calls to MPI_WTime
  xlb_debug_enabled = false;

  // Reduce overhead slightly
  xlb_s.perfc_enabled = false;

  struct xlb_hostnames hostnames;
  ac = xlb_hostnames_fill(&hostnames, hosts, comm_size, my_rank);
  ADLB_CHECK(ac);

  ac = check_hostnames(&hostnames, hosts, comm_size);
  ADLB_CHECK(ac);

  ac = xlb_layout_init(comm_size, my_rank, nservers,
                       &hostnames, &xlb_s.layout);
  ADLB_CHECK(ac);

  ac = setup_hostmap(&hostnames, hosts, comm_size);
  ADLB_CHECK(ac);

  ac = xlb_workq_init(xlb_s.types_size, &xlb_s.layout);
  ADLB_CHECK(ac);

  ac = xlb_requestqueue_init(xlb_s.types_size, &xlb_s.layout);
  ADLB_CHECK(ac);

  xlb_hostnames_free(&hostnames);

  return ADLB_SUCCESS;
}

adlb_code qs_finalize(void)
{
  xlb_workq_finalize();
  xlb_requestqueue_shutdown();
  xlb_layout_finalize(&xlb_s.layout);
  xlb_hostmap_free(xlb_s.hostmap);

  return ADLB_SUCCESS;
}

/*
  Verify that hostnames filled correctly
 */
static adlb_code check_hostnames(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size)
{
  for (int rank = 0; rank < comm_size; rank++)
  {
    const char *expect = hosts[rank];
    const char *actual = xlb_hostnames_lookup(hostnames, rank);
    ADLB_CHECK_MSG(strcmp(expect, actual) == 0,
          "Expected \"%s\" Actual \"%s\"", expect, actual);
  }

  return ADLB_SUCCESS;
}

/*
  Test hostmap building.
 */
static adlb_code setup_hostmap(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size)
{
  adlb_code ac;

  xlb_s.hostmap_mode = HOSTMAP_ENABLED;

  ac = xlb_hostmap_init(&xlb_s.layout, hostnames, &xlb_s.hostmap);
  ADLB_CHECK(ac);

  for (int rank = 0; rank < comm_size; rank++)
  {
    const char *hostname = hosts[rank];

    int nranks;
    int ranks[xlb_s.layout.size];
    ac = ADLB_Hostmap_lookup(hostname, xlb_s.layout.size, ranks, &nranks);
    ADLB_CHECK(ac);

    int matches = 0;
    for (int i = 0; i < nranks; i++)
    {
      if (ranks[i] == rank)
      {
        matches++;
      }
    }
    ADLB_CHECK_MSG(matches == 1, "expected %i in ranks for host %s",
              rank, hostname);
  }

  return ADLB_SUCCESS;
}

void make_fake_hosts(const char **fake_hosts, int comm_size)
{
  for (int i = 0; i < comm_size; i++) {
    switch (i % 8)
    {
      case 0:
        fake_hosts[i] = "ZERO";
        break;
      case 1:
        fake_hosts[i] = "ONE";
        break;
      case 2:
        fake_hosts[i] = "TWO";
        break;
      case 3:
        fake_hosts[i] = "THREE";
        break;
      case 4:
        fake_hosts[i] = "FOUR";
        break;
      case 5:
        fake_hosts[i] = "FIVE";
        break;
      case 6:
        fake_hosts[i] = "SIX";
        break;
      case 7:
        fake_hosts[i] = "SEVEN";
        break;
    }
  }
}

adlb_code drain_rq(void)
{
  int type = 0;

  while (xlb_requestqueue_matches_type(type) != ADLB_RANK_NULL);

  return ADLB_SUCCESS;
}

adlb_code drain_wq(int expected_payload_size, int nexpected,
                   bool free_wus)
{
  int nremoved = 0;

  // Remove all in round-robin way
  while (nexpected < 0 || nremoved < nexpected)
  {
    int removed_this_iter = 0;
    for (int w = 0; w < xlb_s.layout.workers; w++)
    {
      xlb_work_unit *wu = xlb_workq_get(w, 0);

      if (wu != NULL)
      {
        ADLB_CHECK_MSG(wu->type == 0, "type");
        ADLB_CHECK_MSG(expected_payload_size < 0 ||
                  wu->length == expected_payload_size, "size");
        ADLB_CHECK_MSG(wu->target == ADLB_RANK_ANY ||
                  wu->target == w ||
                  wu->opts.strictness == ADLB_TGT_STRICT_SOFT ||
                  (wu->opts.accuracy == ADLB_TGT_ACCRY_NODE &&
                    same_host(wu->target, w)),
                  "target");
        removed_this_iter++;

        if (free_wus)
        {
          xlb_work_unit_free(wu);
        }
      }
    }

    if (nexpected < 0 && removed_this_iter == 0)
    {
      // Queue is empty
      break;
    }

    ADLB_CHECK_MSG(removed_this_iter > 0, "Removed %i/%i before running out",
              nremoved, nexpected);

    nremoved += removed_this_iter;
  }

  int workq_type_counts[1];
  xlb_workq_type_counts(workq_type_counts, 1);
  ADLB_CHECK_MSG(workq_type_counts[0] == 0, "empty queue");

  return ADLB_SUCCESS;
}

adlb_code make_wu(prio_mix prios, tgt_mix tgts,
                    size_t payload_len, xlb_work_unit **wu_result)
{
  xlb_work_unit *wu = work_unit_alloc(payload_len);
  ADLB_CHECK_MALLOC(wu);

  adlb_put_opts opts = ADLB_DEFAULT_PUT_OPTS;
  if (prios == EQUAL) {
    // Do nothing
  } else {
    assert(prios == UNIFORM_RANDOM);
    opts.priority = rand();
  }

  int target = select_wu_target(tgts);

  if (is_soft_target(tgts))
  {
    opts.strictness = ADLB_TGT_STRICT_SOFT;
  }

  opts.accuracy = get_target_accuracy(tgts);

  xlb_work_unit_init(wu, 0, 0, 0, target, (int)payload_len, opts);

  // Empty payload
  memset(wu->payload, 0, payload_len);

  *wu_result = wu;
  return ADLB_SUCCESS;
}

/*
  Create an array of work units
 */
adlb_code make_wus(prio_mix prios, tgt_mix tgts,
            size_t payload_len, int nwus, xlb_work_unit ***wu_result)
{
  adlb_code ac;

  xlb_work_unit **wus = malloc(sizeof(wus[0]) * (size_t)nwus);
  ADLB_CHECK_MALLOC(wus);

  for (int i = 0; i < nwus; i++)
  {
    ac = make_wu(prios, tgts, payload_len, &wus[i]);
    ADLB_CHECK(ac);
  }

  *wu_result = wus;
  return ADLB_SUCCESS;
}

void free_wus(int nwus, xlb_work_unit **wus)
{
  for (int i = 0; i < nwus; i++)
  {
    xlb_work_unit_free(wus[i]);
  }
  free(wus);
}

int select_wu_target(tgt_mix tgts)
{
  if (tgts == UNTARGETED)
  {
    return ADLB_RANK_ANY;
  }
  else if (tgts == RANK_SOFT_TARGETED)
  {
    // Target to half of workers
    int rank = (rand() >> 8) % xlb_s.layout.workers;

    return rank - (rank % 2);
  }
  else if (tgts == NODE_SOFT_TARGETED)
  {
    // TODO: doesn't work on worker

    // Target to half of hosts
    int host_idx = (rand() >> 8) % xlb_s.layout.my_worker_hosts;
    host_idx -= host_idx % 2;

    struct dyn_array_i *workers = &xlb_s.layout.my_host2workers[host_idx];

    // Return random worker on host
    int my_worker_idx = workers->arr[(rand() >> 8) % (int)workers->size];
    int rank = xlb_rank_from_my_worker_idx(&xlb_s.layout, my_worker_idx);
    assert(rank >= 0 && rank < xlb_s.layout.workers);

    return rank;
  }
  else if (tgts == EQUAL_MIX)
  {
    return (rand() % 2) ? select_wu_target(RANK_TARGETED)
                        : select_wu_target(UNTARGETED);
  }
  else
  {
    assert(tgts == RANK_TARGETED || tgts == NODE_TARGETED);
    // Random worker
    return (rand() >> 8) % xlb_s.layout.workers;
  }
}

bool is_soft_target(tgt_mix tgts)
{
  return tgts == RANK_SOFT_TARGETED ||
         tgts == NODE_SOFT_TARGETED;
}

adlb_target_accuracy get_target_accuracy(tgt_mix tgts)
{
  switch (tgts)
  {
    case UNTARGETED:
    case RANK_TARGETED:
    case RANK_SOFT_TARGETED:
    case EQUAL_MIX:
      return ADLB_TGT_ACCRY_RANK;

    case NODE_TARGETED:
    case NODE_SOFT_TARGETED:
      return ADLB_TGT_ACCRY_NODE;


    default:
      assert(false);
      return ADLB_TGT_ACCRY_RANK;
  }
}

adlb_code warmup_wq(size_t payload_size)
{
  adlb_code ac;

  for (int rank = 0; rank < xlb_s.layout.workers; rank++)
  {
    ADLB_CHECK_MSG(xlb_workq_get(rank, 0) == NULL,
              "workq_get failed for rank %i", rank);
  }
  // test work queue
  int ntasks = 1000000;
  for (int i = 0; i < ntasks; i++)
  {
    xlb_work_unit *wu;
    ac = make_wu(UNIFORM_RANDOM, UNTARGETED, payload_size, &wu);
    ADLB_CHECK(ac);

    ac = xlb_workq_add(wu);
    ADLB_CHECK(ac);

    ac = make_wu(UNIFORM_RANDOM, RANK_TARGETED, payload_size, &wu);
    ADLB_CHECK(ac);

    ac = xlb_workq_add(wu);
    ADLB_CHECK(ac);

    ac = make_wu(UNIFORM_RANDOM, NODE_TARGETED, payload_size, &wu);
    ADLB_CHECK(ac);

    ac = xlb_workq_add(wu);
    ADLB_CHECK(ac);

    ac = make_wu(UNIFORM_RANDOM, RANK_SOFT_TARGETED, payload_size, &wu);
    ADLB_CHECK(ac);

    ac = xlb_workq_add(wu);
    ADLB_CHECK(ac);

    ac = make_wu(UNIFORM_RANDOM, NODE_SOFT_TARGETED, payload_size, &wu);
    ADLB_CHECK(ac);

    ac = xlb_workq_add(wu);
    ADLB_CHECK(ac);
  }

  // Including soft targeted
  int untargeted_tasks = ntasks * 3;
  int total_tasks = untargeted_tasks + ntasks * 2;

  // Check correct number of untargeted tasks
  int workq_type_counts[1];
  xlb_workq_type_counts(workq_type_counts, 1);
  ADLB_CHECK_MSG(workq_type_counts[0] == untargeted_tasks, "full queue");

  ac = drain_wq((int)payload_size, total_tasks, true);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

adlb_code warmup_rq(void)
{
  adlb_code ac;

  for (int rank = 0; rank < xlb_s.layout.workers; rank++)
  {
    int match = xlb_requestqueue_matches_target(rank, 0,
                                ADLB_TGT_ACCRY_RANK);
    ADLB_CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    match = xlb_requestqueue_matches_target(rank, 0,
                                ADLB_TGT_ACCRY_NODE);
    ADLB_CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");
  }

  for (int rank = 0; rank < xlb_s.layout.workers; rank++)
  {
    int match;
    int type = 0;

    match = xlb_requestqueue_matches_target(rank, type,
                                ADLB_TGT_ACCRY_RANK);
    ADLB_CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    match = xlb_requestqueue_matches_type(type);
    ADLB_CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    ac = xlb_requestqueue_add(rank, type, 2, true);
    ADLB_CHECK(ac);

    ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 1, "Check nblocked");

    match = xlb_requestqueue_matches_target(rank, type,
                                ADLB_TGT_ACCRY_RANK);
    ADLB_CHECK_MSG(match == rank, "Expected match");

    ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");

    match = xlb_requestqueue_matches_type(type);
    ADLB_CHECK_MSG(match == rank, "Expected match");

    ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");

    // Test node accuracy - select other rank from same node
    int host_idx = host_idx_from_rank(&xlb_s.layout, rank);
    struct dyn_array_i *host_workers;
    host_workers = &xlb_s.layout.my_host2workers[host_idx];
    int other_worker_idx = host_workers->arr[rand() % (int)host_workers->size];
    int other_rank = xlb_rank_from_my_worker_idx(&xlb_s.layout,
                                            other_worker_idx);
    assert(other_rank >= 0 && other_rank < xlb_s.layout.workers);

    ac = xlb_requestqueue_add(rank, type, 1, true);
    ADLB_CHECK(ac);

    match = xlb_requestqueue_matches_target(other_rank, type,
                                ADLB_TGT_ACCRY_NODE);
    ADLB_CHECK_MSG(match == rank, "Expected match");

    ADLB_CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");
  }

  ADLB_CHECK_MSG(xlb_requestqueue_size() == 0, "empty queue");

  return ADLB_SUCCESS;
}

void shuffle_ptrs(void **A, int n)
{
  // Knuth shuffle
  for (int i = n - 1; i >= 0; i--)
  {
    // Number 0 <= j <= i
    int j = rand() % (i + 1);

    void *tmp = A[i];
    A[i] = A[j];
    A[j] = tmp;
  }
}
