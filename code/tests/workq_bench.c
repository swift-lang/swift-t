#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "adlb.h"
#include "checks.h"
#include "common.h"
#include "layout.h"
#include "requestqueue.h"
#include "workqueue.h"

#define RANDOM_SEED 123456

typedef enum {
  EQUAL,
  UNIFORM_RANDOM,
} priority_mix;

typedef enum {
  UNTARGETED,
  TARGETED,
  EQUAL_MIX,
} targeted_mix;

#define PAYLOAD_SIZE 256

typedef struct {
  struct timespec begin, end;
} expt_timers;

static adlb_code run(void);
static adlb_code init(void);
static adlb_code finalize(void);
static adlb_code warmup(void);
static adlb_code warmup_wq_iter(void);
static adlb_code warmup_rq_iter(void);
static adlb_code expt_rq(targeted_mix targets);
static adlb_code expt_wq(priority_mix prios, targeted_mix targets);
static adlb_code expt_rwq(priority_mix prios, targeted_mix targets);

static adlb_code make_wu(priority_mix prios, targeted_mix targets,
                    size_t payload_len, xlb_work_unit **wu_result);
static int select_target(targeted_mix targets);
static adlb_code make_wus(priority_mix prios, targeted_mix targets,
            size_t payload_len, int nwus, xlb_work_unit ***wu_result);
static void free_wus(int nwus, xlb_work_unit **wus);

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

  adlb_code ac = run();

  if (ac != ADLB_SUCCESS) {
    fprintf(stderr, "FAILED!: %i", ac);
    return 1;
  }

  fprintf(stderr, "DONE!\n");
  return 0;
}


static adlb_code run(void)
{
  adlb_code ac;

  ac = init();
  ADLB_CHECK(ac);

  ac = warmup();
  ADLB_CHECK(ac);

  targeted_mix targets[] = {UNTARGETED, TARGETED, EQUAL_MIX};
  int ntargets = sizeof(targets)/sizeof(targets[0]);
  priority_mix prios[] = {EQUAL, UNIFORM_RANDOM};
  int nprios = sizeof(prios)/sizeof(prios[0]);

  for (int tgt_idx = 0; tgt_idx < ntargets; tgt_idx++)
  {
    ac = expt_rq(targets[tgt_idx]);
    ADLB_CHECK(ac);

    for (int prio_idx = 0; prio_idx < nprios; prio_idx++)
    {
      ac = expt_wq(prios[prio_idx], targets[tgt_idx]);
      ADLB_CHECK(ac);

      ac = expt_rwq(prios[prio_idx], targets[tgt_idx]);
      ADLB_CHECK(ac);
    }
  }

  ac = finalize();
  ADLB_CHECK(ac);

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
    ac = make_wu(UNIFORM_RANDOM, UNTARGETED, PAYLOAD_SIZE, &wu);
    ADLB_CHECK(ac);

    ac = xlb_workq_add(wu);
    ADLB_CHECK(ac);

    ac = make_wu(UNIFORM_RANDOM, TARGETED, PAYLOAD_SIZE, &wu);
    ADLB_CHECK(ac);

    ac = xlb_workq_add(wu);
    ADLB_CHECK(ac);
  }

  // Check correct number of untargeted tasks
  int workq_type_counts[1];
  xlb_workq_type_counts(workq_type_counts, 1);
  CHECK_MSG(workq_type_counts[0] == ntasks, "full queue");

  int nremoved = 0;

  // Remove all in round-robin way
  while (nremoved < ntasks * 2)
  {
    int removed_this_iter = 0;
    for (int w = 0; w < xlb_s.layout.workers; w++)
    {
      xlb_work_unit *wu = xlb_workq_get(w, 0);

      if (wu != NULL)
      {
        CHECK_MSG(wu->type == 0, "type");
        CHECK_MSG(wu->length == PAYLOAD_SIZE, "size");
        CHECK_MSG(wu->target == ADLB_RANK_ANY ||
                  wu->target == w, "target");
        removed_this_iter++;
        xlb_work_unit_free(wu);
      }
    }

    CHECK_MSG(removed_this_iter > 0, "Removed %i/%i before running out",
              nremoved, ntasks * 2);

    nremoved += removed_this_iter;
  }

  xlb_workq_type_counts(workq_type_counts, 1);
  CHECK_MSG(workq_type_counts[0] == 0, "empty queue");

  return ADLB_SUCCESS;
}

static adlb_code warmup_rq_iter(void)
{
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

    match = xlb_requestqueue_matches_target(rank, 0,
                                ADLB_TGT_ACCRY_RANK);
    CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    match = xlb_requestqueue_matches_type(0);
    CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

    ac = xlb_requestqueue_add(rank, 0, 2, true);
    ADLB_CHECK(ac);

    CHECK_MSG(xlb_requestqueue_nblocked() == 1, "Check nblocked");

    match = xlb_requestqueue_matches_target(rank, 0,
                                ADLB_TGT_ACCRY_RANK);
    CHECK_MSG(match == rank, "Expected match");

    CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");

    match = xlb_requestqueue_matches_type(0);
    CHECK_MSG(match == rank, "Expected match");

    CHECK_MSG(xlb_requestqueue_nblocked() == 0, "Check nblocked");
  }

  CHECK_MSG(xlb_requestqueue_size() == 0, "empty queue");

  return ADLB_SUCCESS;
}

/*
  Run experiment on request queue in isolation
 */
static adlb_code expt_rq(targeted_mix targets)
{
  // Reseed before experiment
  srand(RANDOM_SEED);

  // TODO:
  return ADLB_SUCCESS;
}

/*
  Run experiment on work queue in isolation
 */
static adlb_code expt_wq(priority_mix prios, targeted_mix targets)
{
  // Reseed before experiment
  srand(RANDOM_SEED);

  adlb_code ac;

  int nwus = 1000; // TODO

  xlb_work_unit **wus;
  ac = make_wus(prios, targets, PAYLOAD_SIZE, nwus, &wus);
  ADLB_CHECK(ac);

  expt_timers timers;
  time_begin(&timers);

  // TODO: experiment

  time_end(&timers);

  // TODO: report
  printf("%llis %lins\n",
      (long long)(timers.end.tv_sec - timers.begin.tv_sec),
      timers.end.tv_nsec - timers.begin.tv_nsec);

  free_wus(nwus, wus);
  return ADLB_SUCCESS;
}

/*
  Run experiment on request queue + work queue flow
 */
static adlb_code expt_rwq(priority_mix prios, targeted_mix targets)
{
  // Reseed before experiment
  srand(RANDOM_SEED);

  // TODO:
  return ADLB_SUCCESS;
}


static adlb_code make_wu(priority_mix prios, targeted_mix targets,
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

  int target;
  if (targets == EQUAL_MIX)
  {
    target = (rand() % 2 == 0) ? select_target(TARGETED)
                               : select_target(UNTARGETED);
  }
  else
  {
    target = select_target(targets);
  }

  xlb_work_unit_init(wu, 0, 0, 0, target, (int)payload_len, opts);

  // Empty payload
  memset(wu->payload, 0, payload_len);

  *wu_result = wu;
  return ADLB_SUCCESS;
}

static int select_target(targeted_mix targets)
{
  if (targets == UNTARGETED)
  {
    return ADLB_RANK_ANY;
  }
  else {
    assert(targets == TARGETED);
    // Random worker
    return rand() % xlb_s.layout.workers;
  }
}

/*
  Create an array of work units
 */
static adlb_code make_wus(priority_mix prios, targeted_mix targets,
            size_t payload_len, int nwus, xlb_work_unit ***wu_result)
{
  adlb_code ac;

  xlb_work_unit **wus = malloc(sizeof(wus[0]) * (size_t)nwus);
  ADLB_MALLOC_CHECK(wus);

  for (int i = 0; i < nwus; i++)
  {
    ac = make_wu(prios, targets, payload_len, &wus[i]);
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
