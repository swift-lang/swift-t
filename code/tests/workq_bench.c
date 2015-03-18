#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "adlb.h"
#include "checks.h"
#include "common.h"
#include "layout.h"
#include "requestqueue.h"
#include "workqueue.h"

static adlb_code run(void);
static adlb_code init(void);
static adlb_code warmup(void);
static adlb_code finalize(void);

static void make_fake_hosts(const char **fake_hosts, int comm_size);
static adlb_code check_hostnames(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size);
static adlb_code setup_hostmap(struct xlb_hostnames *hostnames,
                              const char **hosts, int comm_size);

int main(int argc, char **argv)
{
  // TODO: fill in other things in xlb_s

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

  ac = finalize();
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

/*
  Do some warming up and testing.
 */
static adlb_code warmup(void) {
  adlb_code ac;

  int warmup_iters = 1000;

  for (int i = 0; i < warmup_iters; i++)
  {

    for (int rank = 0; rank < xlb_s.layout.workers; rank++)
    {
      CHECK_MSG(xlb_workq_get(rank, 0) == NULL,
                "workq_get failed for rank %i", rank);
    }

    for (int rank = 0; rank < xlb_s.layout.workers; rank++)
    {
      int match = xlb_requestqueue_matches_target(rank, 0,
                                  ADLB_TGT_ACCRY_RANK);
      CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");

      match = xlb_requestqueue_matches_target(rank, 0,
                                  ADLB_TGT_ACCRY_NODE);
      CHECK_MSG(match == ADLB_RANK_NULL, "Unexpected match");
    }

    // TODO: test work queue

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
  }

  return ADLB_SUCCESS;
}

static adlb_code init(void)
{
  adlb_code ac;

  // Workaround: disable debug logging to avoid calls to MPI_WTime
  xlb_debug_enabled = false;

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
