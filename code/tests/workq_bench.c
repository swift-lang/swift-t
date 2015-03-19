#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "adlb.h"
#include "checks.h"
#include "common.h"
#include "layout.h"
#include "requestqueue.h"
#include "workqueue.h"

typedef enum {
  EQUAL,
  UNIFORM_RANDOM,
} prio_mix;

static const char *prio_mix_str(prio_mix prio);

typedef enum {
  UNTARGETED,
  RANK_TARGETED,
  NODE_TARGETED,
  EQUAL_MIX, // 50% untargeted, 50% rank targeted
  // TODO: mix with node targeted?
  RANK_SOFT_TARGETED, // soft targeted to 50% of ranks
  NODE_SOFT_TARGETED, // soft targeted to 50% of nodes
} tgt_mix;

static const char *tgt_mix_str(tgt_mix tgt);

/** Random seed to use for each experiment */
// TODO: make configurable
int random_seed = 123456;

/** Payload size for work units */
// TODO: make configurable
int payload_size = 256;

/** Number of distinct work units to use in benchmarks */
// TODO: make configurable
int num_distinct_wus = 1024 * 512;

/** Number of operations in benchmark run */
int benchmark_nops = 1 * 1000 * 1000;

/** Length of random sequences to use */
int rand_seq_len = 4096;

/** Maximum initial queue size to run experiments with */
int max_init_qlen = 16 * 1024;

typedef struct {
  struct timespec begin, end;
} expt_timers;

static adlb_code run(bool run_benchmarks);
static adlb_code init(void);
static adlb_code finalize(void);
static adlb_code warmup(void);
static adlb_code warmup_wq_iter(void);
static adlb_code drain_wq(int nexpected, bool free_wus);
static adlb_code warmup_rq_iter(void);
static adlb_code drain_rq(void);
static adlb_code expt_rq(tgt_mix tgts, bool report);
static adlb_code expt_wq(prio_mix prios, tgt_mix tgts, int init_qlen,
                         bool report);
static adlb_code expt_rwq(prio_mix prios, tgt_mix tgts, int init_qlen,
                         bool report);

static void report_hdr(void);
static void report_expt(const char *expt, prio_mix prios, tgt_mix tgts,
                   int init_qlen, int nops, expt_timers timers);

static adlb_code make_wu(prio_mix prios, tgt_mix tgts,
                    size_t payload_len, xlb_work_unit **wu_result);
static adlb_code make_wus(prio_mix prios, tgt_mix tgts,
            size_t payload_len, int nwus, xlb_work_unit ***wu_result);
static void free_wus(int nwus, xlb_work_unit **wus);

static int select_wu_target(tgt_mix tgts);
static bool is_soft_target(tgt_mix tgts);
static adlb_target_accuracy get_target_accuracy(tgt_mix tgts);
static bool same_host(int rank1, int rank2);

static void make_fake_hosts(const char **fake_hosts, int comm_size);
static adlb_code check_hostnames(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size);
static adlb_code setup_hostmap(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size);

static void time_begin(expt_timers *timers);
static void time_end(expt_timers *timers);

int main(int argc, char **argv)
{
  // TODO: command-line options for different modes

  bool run_benchmarks = false;

  int c;

  while ((c = getopt(argc, argv, "bn:w:Q:")) != -1)
  {
    switch (c) {
      case 'b':
        run_benchmarks = true;
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
    fprintf(stderr, "FAILED!: %i", ac);
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
  adlb_code ac;

  // Workaround: disable debug logging to avoid calls to MPI_WTime
  xlb_debug_enabled = false;

  // Reduce overhead slightly
  xlb_s.perfc_enabled = false;

  xlb_s.types_size = 1;
  int comm_size = 64;
  int my_rank = comm_size - 1;
  int nservers = 1;

  const char *fake_hosts[comm_size];
  make_fake_hosts(fake_hosts, comm_size);

  struct xlb_hostnames hostnames;
  ac = xlb_hostnames_fill(&hostnames, fake_hosts, comm_size, my_rank);
  ADLB_CHECK(ac);

  ac = check_hostnames(&hostnames, fake_hosts, comm_size);
  ADLB_CHECK(ac);

  ac = xlb_layout_init(comm_size, my_rank, nservers,
                       &hostnames, &xlb_s.layout);
  ADLB_CHECK(ac);

  ac = setup_hostmap(&hostnames, fake_hosts, comm_size);
  ADLB_CHECK(ac);

  ac = xlb_workq_init(xlb_s.types_size, &xlb_s.layout);
  ADLB_CHECK(ac);

  ac = xlb_requestqueue_init(xlb_s.types_size, &xlb_s.layout);
  ADLB_CHECK(ac);

  xlb_hostnames_free(&hostnames);

  return ADLB_SUCCESS;
}

/*
  Cleanup modules to free memory, etc
 */
static adlb_code finalize(void)
{
  xlb_workq_finalize();
  xlb_requestqueue_shutdown();
  xlb_layout_finalize(&xlb_s.layout);
  xlb_hostmap_free(xlb_s.hostmap);

  return ADLB_SUCCESS;
}

/*
  Do some warming up and testing.
 */
static adlb_code warmup(void) {
  adlb_code ac;

  srand(random_seed);

  int warmup_iters = 2;

  for (int iter = 0; iter < warmup_iters; iter++)
  {
    ac = warmup_wq_iter();
    ADLB_CHECK(ac);

    ac = warmup_rq_iter();
    ADLB_CHECK(ac);
  }

  return ADLB_SUCCESS;
}

static adlb_code warmup_wq_iter(void)
{
  adlb_code ac;

  for (int rank = 0; rank < xlb_s.layout.workers; rank++)
  {
    CHECK_MSG(xlb_workq_get(rank, 0) == NULL,
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
  CHECK_MSG(workq_type_counts[0] == untargeted_tasks, "full queue");

  ac = drain_wq(total_tasks, true);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

/*
  Drain work queue.  If nexpected is non-negative, check that
  expected number of tasks were present.
 */
static adlb_code drain_wq(int nexpected, bool free_wus)
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
        CHECK_MSG(wu->type == 0, "type");
        CHECK_MSG(wu->length == payload_size, "size");
        CHECK_MSG(wu->target == ADLB_RANK_ANY ||
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

    CHECK_MSG(removed_this_iter > 0, "Removed %i/%i before running out",
              nremoved, nexpected);

    nremoved += removed_this_iter;
  }

  int workq_type_counts[1];
  xlb_workq_type_counts(workq_type_counts, 1);
  CHECK_MSG(workq_type_counts[0] == 0, "empty queue");

  return ADLB_SUCCESS;
}

static adlb_code warmup_rq_iter(void)
{
  srand(random_seed);
  adlb_code ac;

  for (int rank = 0; rank < xlb_s.layout.workers; rank++)
  {
    int match = xlb_requestqueue_matches_target(rank, 0,
                                ADLB_TGT_ACCRY_RANK);
    CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    match = xlb_requestqueue_matches_target(rank, 0,
                                ADLB_TGT_ACCRY_NODE);
    CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");
  }

  for (int rank = 0; rank < xlb_s.layout.workers; rank++)
  {
    int match;
    int type = 0;

    match = xlb_requestqueue_matches_target(rank, type,
                                ADLB_TGT_ACCRY_RANK);
    CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    match = xlb_requestqueue_matches_type(type);
    CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    ac = xlb_requestqueue_add(rank, type, 2, true);
    ADLB_CHECK(ac);

    CHECK_MSG(xlb_requestqueue_nblocked() == 1, "Check nblocked");

    match = xlb_requestqueue_matches_target(rank, type,
                                ADLB_TGT_ACCRY_RANK);
    CHECK_MSG(match == rank, "Expected match");

    CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");

    match = xlb_requestqueue_matches_type(type);
    CHECK_MSG(match == rank, "Expected match");

    CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");

    // Test node accuracy - select other rank from same node
    int host_idx = host_idx_from_rank(&xlb_s.layout, rank);
    struct dyn_array_i *host_workers;
    host_workers = &xlb_s.layout.my_host2workers[host_idx];
    int other_worker_idx = host_workers->arr[rand() % host_workers->size];
    int other_rank = xlb_rank_from_my_worker_idx(&xlb_s.layout,
                                            other_worker_idx);
    assert(other_rank >= 0 && other_rank < xlb_s.layout.workers);

    ac = xlb_requestqueue_add(rank, type, 1, true);
    ADLB_CHECK(ac);

    match = xlb_requestqueue_matches_target(other_rank, type,
                                ADLB_TGT_ACCRY_NODE);
    CHECK_MSG(match == rank, "Expected match");

    CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");
  }

  CHECK_MSG(xlb_requestqueue_size() == 0, "empty queue");

  return ADLB_SUCCESS;
}

static adlb_code drain_rq(void)
{
  int type = 0;

  while (xlb_requestqueue_matches_type(type) != ADLB_RANK_NULL);

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

  for (int op = 0; op < benchmark_nops; op++)
  {
    struct expt_wq_op *curr_op = &rand_ops[op % rand_seq_len];

    if (curr_op->add)
    {
      ac = xlb_workq_add(wus[op % num_distinct_wus]);
      ADLB_CHECK(ac);
    }
    else
    {
      // Get work unit - do nothing with it
      xlb_workq_get(curr_op->rank, 0);
    }
  }

  time_end(&timers);

  ac = drain_wq(-1, false);
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
    int wu_ix;
    bool new_work; // Alt is new request
  };

  int wu_count = 0;

  // Precompute random sequence to avoid calling rand() in loop
  struct expt_rwq_op rand_ops[rand_seq_len];
  for (int i = 0; i < rand_seq_len; i++)
  {
    rand_ops[i].new_work = (rand() >> 16) % 2;
    if (rand_ops[i].new_work)
    {
      rand_ops[i].wu_ix = wu_count++ % num_distinct_wus;
    }
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

  for (int op = 0; op < benchmark_nops; op++)
  {
    struct expt_rwq_op *curr_op = &rand_ops[op % rand_seq_len];

    if (curr_op->new_work)
    {
      xlb_work_unit *wu = wus[curr_op->wu_ix % num_distinct_wus];
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

  ac = drain_wq(-1, false);
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
  long long nsec =
    ((long long)(timers.end.tv_sec - timers.begin.tv_sec)) * 1000000000 +
    timers.end.tv_nsec - timers.begin.tv_nsec;

  printf("%s,%s,%s,%i,%i,%lli,%lf,%lf,%.0lf\n",
    expt,
    prio_mix_str(prios), tgt_mix_str(tgts),
    init_qlen, nops,
    nsec, nsec / (double)1e9,
    nsec / (double)nops,
    nops / (nsec / (double)1e9));
}


static adlb_code make_wu(prio_mix prios, tgt_mix tgts,
                    size_t payload_len, xlb_work_unit **wu_result)
{
  xlb_work_unit *wu = work_unit_alloc(payload_len);
  ADLB_MALLOC_CHECK(wu);

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

static bool is_soft_target(tgt_mix tgts)
{
  return tgts == RANK_SOFT_TARGETED ||
         tgts == NODE_SOFT_TARGETED;
}

static adlb_target_accuracy get_target_accuracy(tgt_mix tgts)
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

static bool same_host(int rank1, int rank2)
{
  return host_idx_from_rank(&xlb_s.layout, rank1) ==
         host_idx_from_rank(&xlb_s.layout, rank2);
}

static int select_wu_target(tgt_mix tgts)
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
    // Target to half of hosts
    int host_idx = (rand() >> 8) % xlb_s.layout.my_worker_hosts;
    host_idx -= host_idx % 2;

    struct dyn_array_i *workers = &xlb_s.layout.my_host2workers[host_idx];

    // Return random worker on host
    int my_worker_idx = workers->arr[(rand() >> 8) % workers->size];
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

/*
  Create an array of work units
 */
static adlb_code make_wus(prio_mix prios, tgt_mix tgts,
            size_t payload_len, int nwus, xlb_work_unit ***wu_result)
{
  adlb_code ac;

  xlb_work_unit **wus = malloc(sizeof(wus[0]) * (size_t)nwus);
  ADLB_MALLOC_CHECK(wus);

  for (int i = 0; i < nwus; i++)
  {
    ac = make_wu(prios, tgts, payload_len, &wus[i]);
    ADLB_CHECK(ac);
  }

  *wu_result = wus;
  return ADLB_SUCCESS;
}

static void free_wus(int nwus, xlb_work_unit **wus)
{
  for (int i = 0; i < nwus; i++)
  {
    xlb_work_unit_free(wus[i]);
  }
  free(wus);
}

static void make_fake_hosts(const char **fake_hosts, int comm_size)
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
    CHECK_MSG(strcmp(expect, actual) == 0,
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
    CHECK_MSG(matches == 1, "expected %i in ranks for host %s",
              rank, hostname);
  }

  return ADLB_SUCCESS;
}

static void time_begin(expt_timers *timers)
{
  int rc = clock_gettime(CLOCK_THREAD_CPUTIME_ID, &timers->begin);
  assert(rc == 0);
}

static void time_end(expt_timers *timers)
{
  int rc = clock_gettime(CLOCK_THREAD_CPUTIME_ID, &timers->end);
  assert(rc == 0);
}

static const char *prio_mix_str(prio_mix prio)
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

static const char *tgt_mix_str(tgt_mix tgt)
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
}
