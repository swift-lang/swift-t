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
 * workq_bench.c
 *
 * Benchmarking utility for queues
 *
 *  Created on: Mar 19, 2015
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

/** Random seed to use for each experiment */
// TODO: make configurable
unsigned int random_seed = 123456;

/** Payload size for work units */
// TODO: make configurable
size_t payload_size = 256;

/** Number of distinct work units to use in benchmarks */
// TODO: make configurable
int num_distinct_wus = 1024 * 512;

/** Number of operations in benchmark run */
int benchmark_nops = 1 * 1000 * 1000;

/** Length of random sequences to use */
int rand_seq_len = 1024 * 128;

/** Maximum initial queue size to run experiments with */
int max_init_qlen = 16 * 1024;

/** Number of warmup iterations to run */
int warmup_iters = 2;

static adlb_code run(bool run_benchmarks);
static adlb_code init(void);
static adlb_code finalize(void);
static adlb_code warmup(void);
static adlb_code expt_rq(tgt_mix tgts, bool report);
static adlb_code expt_wq(prio_mix prios, tgt_mix tgts, int init_qlen,
                         bool report);
static adlb_code expt_rwq(prio_mix prios, tgt_mix tgts, int init_qlen,
                         bool report);

static void report_hdr(void);
static void report_expt(const char *expt, prio_mix prios, tgt_mix tgts,
                   int init_qlen, int nops, expt_timers timers);

int main(int argc, char **argv)
{
  // TODO: command-line options for different modes

  bool run_benchmarks = false;

  int c;

  while ((c = getopt(argc, argv, "bn:r:Q:w:")) != -1)
  {
    switch (c) {
      case 'b':
        run_benchmarks = true;
        warmup_iters = 5; // Extra warmup
        break;
      case 'n':
        benchmark_nops = atoi(optarg);
        if (benchmark_nops == 0)
        {
          fprintf(stderr, "Invalid number of ops: %s\n", optarg);
          return 1;
        }

        fprintf(stderr, "Number of ops: %i\n", benchmark_nops);
        break;
      case 'r':
        rand_seq_len = atoi(optarg);
        if (rand_seq_len == 0)
        {
          fprintf(stderr, "Invalid random sequence length: %s\n",
                          optarg);
          return 1;
        }

        fprintf(stderr, "Random sequence length: %i\n", rand_seq_len);
        break;
      case 'Q':
        max_init_qlen = atoi(optarg);
        if (max_init_qlen == 0)
        {
          fprintf(stderr, "Invalid max init qlen: %s\n", optarg);
          return 1;
        }

        fprintf(stderr, "Max initial queue length: %i\n", max_init_qlen);
        break;
      case 'w':
        num_distinct_wus = atoi(optarg);
        if (num_distinct_wus == 0)
        {
          fprintf(stderr, "Invalid number of distinct work units: %s\n",
                          optarg);
          return 1;
        }

        fprintf(stderr, "Number of distinct work units: %i\n",
                num_distinct_wus);
        break;
      case '?':
        fprintf(stderr, "Unknown option %c\n", (char)(c));
        return 1;
    }
  }

  adlb_code ac = run(run_benchmarks);

  if (ac != ADLB_SUCCESS) {
    fprintf(stderr, "FAILED!: %i\n", ac);
    return 1;
  }

  return 0;
}


static adlb_code run(bool run_benchmarks)
{
  adlb_code ac;

  fprintf(stderr, "Initializing...\n");
  ac = init();
  ADLB_CHECK(ac);

  fprintf(stderr, "Running warmup tests...\n");
  ac = warmup();
  ADLB_CHECK(ac);

  if (run_benchmarks)
  {
    fprintf(stderr, "Running benchmarks...\n");
    report_hdr();

    tgt_mix tgts[] = {UNTARGETED, RANK_TARGETED, NODE_TARGETED,
            EQUAL_MIX, RANK_SOFT_TARGETED, NODE_SOFT_TARGETED};
    int ntgts = sizeof(tgts)/sizeof(tgts[0]);
    prio_mix prios[] = {EQUAL, UNIFORM_RANDOM};
    int nprios = sizeof(prios)/sizeof(prios[0]);

    for (int exp_iter = 0; exp_iter < 5; exp_iter++)
    {
      bool report = exp_iter > 0;

      for (int tgt_idx = 0; tgt_idx < ntgts; tgt_idx++)
      {
        ac = expt_rq(tgts[tgt_idx], report);
        ADLB_CHECK(ac);

        for (int prio_idx = 0; prio_idx < nprios; prio_idx++)
        {
          for (int init_qlen = 0; init_qlen <= max_init_qlen;
                  init_qlen = init_qlen == 0 ? 1 : init_qlen * 2)
          {
            ac = expt_wq(prios[prio_idx], tgts[tgt_idx], init_qlen, report);
            ADLB_CHECK(ac);

            ac = expt_rwq(prios[prio_idx], tgts[tgt_idx], init_qlen, report);
            ADLB_CHECK(ac);
          }
        }
      }
    }
  }

  fprintf(stderr, "Finalizing...\n");

  ac = finalize();
  ADLB_CHECK(ac);

  fprintf(stderr, "Done.\n");

  return ADLB_SUCCESS;
}

static adlb_code init(void)
{
  int comm_size = 64;
  int my_rank = comm_size - 1;
  int nservers = 1;

  const char *fake_hosts[comm_size];
  make_fake_hosts(fake_hosts, comm_size);

  adlb_code ac;

  ac = qs_init(comm_size, my_rank, nservers, fake_hosts, 1);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

/*
  Cleanup modules to free memory, etc
 */
static adlb_code finalize(void)
{
  adlb_code ac;

  ac = qs_finalize();
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

/*
  Do some warming up and testing.
 */
static adlb_code warmup(void) {
  adlb_code ac;

  srand(random_seed);

  for (int iter = 0; iter < warmup_iters; iter++)
  {
    ac = warmup_wq(payload_size);
    ADLB_CHECK(ac);

    ac = warmup_rq();
    ADLB_CHECK(ac);
  }

  return ADLB_SUCCESS;
}

/*
  Run experiment on request queue in isolation
 */
static adlb_code expt_rq(tgt_mix tgts, bool report)
{
  // Reseed before experiment
  srand(random_seed);

  adlb_code ac;

  struct expt_rq_op {
    int target;
    bool add;
  };

  // Precompute random sequence to avoid calling rand() in loop
  struct expt_rq_op rand_ops[rand_seq_len];
  for (int i = 0; i < rand_seq_len; i++)
  {
    rand_ops[i].add = (rand() >> 16) % 2;
    if (rand_ops[i].add)
    {
      // Target for incoming request
      rand_ops[i].target = (rand() >> 8) % xlb_s.layout.workers;
    }
    else
    {
      // Target for incoming work unit
      rand_ops[i].target = select_wu_target(tgts);
    }
  }

  bool soft_targeted = is_soft_target(tgts);
  adlb_target_accuracy accry = get_target_accuracy(tgts);

  // Start off with empty queues

  expt_timers timers;
  time_begin(&timers);

  for (int op = 0; op < benchmark_nops; op++)
  {
    struct expt_rq_op *curr_op = &rand_ops[op % rand_seq_len];
    int target = curr_op->target;
    int type = 0;

    if (curr_op->add)
    {
      ac = xlb_requestqueue_add(target, 0, 1, false);
      ADLB_CHECK(ac);
    }
    else
    {
      int rank;
      if (curr_op->target >= 0)
      {
        rank = xlb_requestqueue_matches_target(target, type, accry);

        if (soft_targeted && rank == ADLB_RANK_NULL)
        {
          rank = xlb_requestqueue_matches_type(type);
        }
      }
      else
      {
        rank = xlb_requestqueue_matches_type(type);
      }
    }
  }

  time_end(&timers);

  ac = drain_rq();
  ADLB_CHECK(ac);

  if (report)
  {
    report_expt("rq", EQUAL, tgts, 0, benchmark_nops, timers);
  }

  return ADLB_SUCCESS;
}

/*
  Run experiment on work queue in isolation
 */
static adlb_code expt_wq(prio_mix prios, tgt_mix tgts, int init_qlen,
                         bool report)
{
  // Reseed before experiment
  srand(random_seed);

  adlb_code ac;

  struct expt_wq_op {
    int rank;
    bool add;
  };

  // Precompute random sequence to avoid calling rand() in loop
  struct expt_wq_op rand_ops[rand_seq_len];
  for (int i = 0; i < rand_seq_len; i++)
  {
    rand_ops[i].add = (rand() >> 16) % 2;
    // Target for incoming request
    rand_ops[i].rank = (rand() >> 8) % xlb_s.layout.workers;
  }

  xlb_work_unit **wus;

  ac = make_wus(prios, tgts, payload_size, num_distinct_wus, &wus);
  ADLB_CHECK(ac);

  // Prepopulate queue
  for (int i = 0; i < init_qlen; i++)
  {
    // Fill with different ones from initial work units
    ac = xlb_workq_add(wus[num_distinct_wus - 1 - (i % num_distinct_wus)]);
    ADLB_CHECK(ac);
  }

  expt_timers timers;
  time_begin(&timers);

  int wu_idx = 0;

  for (int op = 0; op < benchmark_nops; op++)
  {
    struct expt_wq_op *curr_op = &rand_ops[op % rand_seq_len];

    if (curr_op->add)
    {
      ac = xlb_workq_add(wus[wu_idx++ % num_distinct_wus]);
      ADLB_CHECK(ac);
    }
    else
    {
      // Get work unit - do nothing with it
      xlb_workq_get(curr_op->rank, 0);
    }
  }

  time_end(&timers);

  ac = drain_wq((int)payload_size, -1, false);
  ADLB_CHECK(ac);

  if (report)
  {
    report_expt("wq", prios, tgts, init_qlen, benchmark_nops, timers);
  }

  free_wus(num_distinct_wus, wus);

  return ADLB_SUCCESS;
}

/*
  Run experiment on request queue + work queue flow
 */
static adlb_code expt_rwq(prio_mix prios, tgt_mix tgts, int init_qlen,
                          bool report)
{
  // Reseed before experiment
  srand(random_seed);

  adlb_code ac;

  struct expt_rwq_op {
    int rank;
    bool new_work; // Alt is new request
  };

  // Precompute random sequence to avoid calling rand() in loop
  struct expt_rwq_op rand_ops[rand_seq_len];
  for (int i = 0; i < rand_seq_len; i++)
  {
    rand_ops[i].new_work = (rand() >> 16) % 2;
    rand_ops[i].rank = (rand() >> 8) % xlb_s.layout.workers;
  }

  xlb_work_unit **wus;

  ac = make_wus(prios, tgts, payload_size, num_distinct_wus, &wus);
  ADLB_CHECK(ac);

  // Prepopulate queue
  for (int i = 0; i < init_qlen; i++)
  {
    // Fill with different ones from initial work units
    ac = xlb_workq_add(wus[num_distinct_wus - 1 - (i % num_distinct_wus)]);
    ADLB_CHECK(ac);
  }

  expt_timers timers;
  time_begin(&timers);

  int wu_idx = 0;

  for (int op = 0; op < benchmark_nops; op++)
  {
    struct expt_rwq_op *curr_op = &rand_ops[op % rand_seq_len];

    if (curr_op->new_work)
    {
      xlb_work_unit *wu = wus[wu_idx++ % num_distinct_wus];

      int rank;
      if (wu->target >= 0)
      {
        rank = xlb_requestqueue_matches_target(wu->target, wu->type,
                                               wu->opts.accuracy);

        if (wu->opts.strictness == ADLB_TGT_STRICT_SOFT && rank == ADLB_RANK_NULL)
        {
          rank = xlb_requestqueue_matches_type(wu->type);
        }
      }
      else
      {
        rank = xlb_requestqueue_matches_type(wu->type);
      }

      //fprintf(stderr, "Wu to rank (target %i): %p -> %i\n", wu->target, wu, rank);
      if (rank == ADLB_RANK_NULL)
      {
        ac = xlb_workq_add(wu);
        ADLB_CHECK(ac);
      }
    }
    else
    {
      // New request
      int rank = curr_op->rank;
      int type = 0;

      xlb_work_unit *wu = xlb_workq_get(rank, type);
      //fprintf(stderr, "Rank to wu: %i -> %p\n", rank, wu);

      if (wu == NULL)
      {
        ac = xlb_requestqueue_add(rank, type, 1, false);
        ADLB_CHECK(ac);
      }
    }
  }

  time_end(&timers);

  ac = drain_wq((int)payload_size, -1, false);
  ADLB_CHECK(ac);

  ac = drain_rq();
  ADLB_CHECK(ac);

  if (report)
  {
    report_expt("rwq", prios, tgts, init_qlen, benchmark_nops, timers);
  }

  free_wus(num_distinct_wus, wus);

  return ADLB_SUCCESS;
}

static void report_hdr(void)
{
  printf("experiment,priorities,targets,init_qlen,nops,nsec,sec,nsec_op,"
         "op_sec\n");
}

static void report_expt(const char *expt, prio_mix prios, tgt_mix tgts,
                   int init_qlen, int nops, expt_timers timers)
{
  long long nsec = duration_nsec(timers);

  printf("%s,%s,%s,%i,%i,%lli,%lf,%lf,%.0lf\n",
    expt,
    prio_mix_str(prios), tgt_mix_str(tgts),
    init_qlen, nops,
    nsec, (double)nsec / (double)1e9,
    (double)nsec / (double)nops,
    nops / ((double)nsec / (double)1e9));
  // Make progress visible
  fflush(stdout);
}

