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
 * server_task_bench.c
 *
 * Benchmarking utility for server work queue
 *
 *  Created on: April 10, 2015
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

#include "adlb.h"
#include "common.h"
#include "checks.h"

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

static adlb_code run(void);
static adlb_code expt(prio_mix prios, tgt_mix tgts, int init_qlen,
                         bool report);

static void report_hdr(void);
static void report_expt(const char *expt, prio_mix prios, tgt_mix tgts,
                   int init_qlen, int nops, expt_timers timers);

int main(int argc, char **argv)
{
  int c;

  while ((c = getopt(argc, argv, "n:r:Q:w:")) != -1)
  {
    switch (c) {
      case 'n':
        benchmark_nops = atoi(optarg);
        if (benchmark_nops == 0)
        {
          fprintf(stderr, "Invalid number of ops: %s\n", optarg);
          return 1;
        }

        fprintf(stderr, "Number of ops: %i\n",
                        benchmark_nops);
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

  adlb_code ac = run();

  if (ac != ADLB_SUCCESS) {
    fprintf(stderr, "FAILED!: %i\n", ac);
    return 1;
  }

  return 0;
}


static adlb_code run()
{
  adlb_code ac;

  fprintf(stderr, "Initializing...\n");
  int mpi_argc = 0;
  char** mpi_argv = NULL;
  int rc = MPI_Init(&mpi_argc, &mpi_argv);
  CHECK_MSG(rc == MPI_SUCCESS, "error setting up MPI");

  fprintf(stderr, "Running benchmarks...\n");
  report_hdr();

  // TODO: Omitting NODE_SOFT_TARGETED, too tricky to generate right ranks
  tgt_mix tgts[] = {UNTARGETED, RANK_TARGETED, NODE_TARGETED,
          EQUAL_MIX, RANK_SOFT_TARGETED, /*NODE_SOFT_TARGETED*/};
  int ntgts = sizeof(tgts)/sizeof(tgts[0]);
  prio_mix prios[] = {EQUAL, UNIFORM_RANDOM};
  int nprios = sizeof(prios)/sizeof(prios[0]);

  for (int exp_iter = 0; exp_iter < 5; exp_iter++)
  {
    bool report = exp_iter > 0;

    for (int tgt_idx = 0; tgt_idx < ntgts; tgt_idx++)
    {
      for (int prio_idx = 0; prio_idx < nprios; prio_idx++)
      {
        for (int init_qlen = 128; init_qlen <= max_init_qlen;
                init_qlen = init_qlen == 0 ? 1 : init_qlen * 2)
        {
          ac = expt(prios[prio_idx], tgts[tgt_idx], init_qlen, report);
          ADLB_CHECK(ac);
        }
      }
    }
  }

  fprintf(stderr, "Finalizing...\n");

  MPI_Finalize();

  fprintf(stderr, "Done.\n");

  return ADLB_SUCCESS;
}

/*
  Run experiment on request queue + work queue flow
 */
static adlb_code expt(prio_mix prios, tgt_mix tgts, int init_qlen,
                          bool report)
{
  int my_rank;
  MPI_Comm_rank(MPI_COMM_WORLD, &my_rank);

  int comm_size;
  MPI_Comm_size(MPI_COMM_WORLD, &comm_size);
  int nservers = 1;
  int nworkers = comm_size - nservers;
  int ntypes = 1;
  int types[1] = { 0 };

  CHECK_MSG(init_qlen * 2 <= benchmark_nops, "init queue length %i must be "
           "less than half number of operations %i", init_qlen,
           benchmark_nops);
  CHECK_MSG(benchmark_nops % 2 == 0, "Benchmark op count %i must be even",
           benchmark_nops);
  // 1 put and 1 get per task
  int benchmark_ntasks = benchmark_nops / 2;

  // Reseed before experiment
  srand((uint32_t)my_rank * 151 + random_seed);

  adlb_code ac;

  // Create copy to isolate any interference.
  MPI_Comm comm;
  int rc = MPI_Comm_dup(MPI_COMM_WORLD, &comm);
  CHECK_MSG(rc == MPI_SUCCESS, "comm dup");


  int am_server;
  MPI_Comm worker_comm;
  ac = ADLB_Init(nservers, ntypes, types, &am_server, comm, &worker_comm);
  ADLB_CHECK(ac);

  // Number of ops executed by this worker
  int my_ops = 0;

  if (am_server)
  {
    fprintf(stderr, "Server, q length: %i\n", init_qlen);
    // Wait for workers to setup before timing
    MPI_Barrier(comm);

    // Do timing on server
    expt_timers timers;
    time_begin(&timers);

    ac = ADLB_Server(1);
    ADLB_CHECK(ac);

    time_end(&timers);

    if (report)
    {
      report_expt("server_task_bench", prios, tgts, init_qlen, benchmark_nops, timers);
    }
  }
  else
  {
    // Am worker
    xlb_work_unit **wus;
    ac = make_wus(prios, tgts, payload_size, num_distinct_wus, &wus);
    ADLB_CHECK(ac);

    // Wait for work unit generation to finish
    MPI_Barrier(comm);

    // Prepopulate queues from workers
    int init_qlen_mine = init_qlen / nworkers + (my_rank < init_qlen % nworkers);
    // fprintf(stderr, "init_qlen_min = %i\n", init_qlen_mine);
    for (int i = 0; i < init_qlen_mine; i++)
    {
      // Work out number of work units that need to be run per work unit
      // Two ops per work unit
      int payload_val = benchmark_ntasks / init_qlen +
                (i * nworkers + my_rank < benchmark_ntasks % init_qlen);

      /*fprintf(stderr, "payload_val = %i = %i + %i\n", payload_val,
                benchmark_nops / (2 * init_qlen),
                i * nworkers + my_rank < benchmark_nops % (2 * init_qlen));*/

      xlb_work_unit *wu = wus[num_distinct_wus - 1 - (i % num_distinct_wus)];
      memcpy(wu->payload, &payload_val, sizeof(payload_val));

      ac = ADLB_Put(wu->payload, wu->length, wu->target, -1, 0, wu->opts);
      ADLB_CHECK(ac);

      my_ops++;
    }

    int wu_idx = 0;
    int answer;
    int len;
    int type;
    MPI_Comm tmp_comm;

    while ((ac = ADLB_Get(0, wus[wu_idx]->payload, &len, &answer, &type, &tmp_comm))
            == ADLB_SUCCESS)
    {
      int counter;
      xlb_work_unit *wu = wus[wu_idx];
      memcpy(&counter, wu->payload, sizeof(counter));
      counter--;
      if (counter > 0)
      {
        // Add another work unit with decremented counter
        memcpy(wu->payload, &counter, sizeof(counter));

        ac = ADLB_Put(wu->payload, wu->length, wu->target, -1, 0, wu->opts);
        ADLB_CHECK(ac);

        my_ops++;
      }

      my_ops++;
      wu_idx = (wu_idx + 1 % num_distinct_wus);
    }

    CHECK_MSG(ac == ADLB_SHUTDOWN, "Expected shutdown, got adlb_code %i", ac);

    free_wus(num_distinct_wus, wus);
  }

  ac = ADLB_Finalize();
  ADLB_CHECK(ac);

  // Verify that we did the correct number of operations
  int sum_ops;
  rc = MPI_Allreduce(&my_ops, &sum_ops, 1, MPI_INT, MPI_SUM, comm);
  CHECK_MSG(sum_ops == benchmark_nops, "Wrong number of ops executed "
        "expected %i actual %i", benchmark_nops, sum_ops);

  MPI_Comm_free(&comm);

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

