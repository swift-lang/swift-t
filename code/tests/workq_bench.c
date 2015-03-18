#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "checks.h"
#include "common.h"
#include "layout.h"
#include "requestqueue.h"
#include "workqueue.h"

static void make_fake_hosts(const char **fake_hosts, int comm_size);
static adlb_code run(void);

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

  ac = xlb_layout_init(comm_size, my_rank, nservers,
                       &hostnames, &xlb_s.layout);
  ADLB_CHECK(ac);

  ac = xlb_workq_init(xlb_s.types_size, &xlb_s.layout);
  ADLB_CHECK(ac);

  ac = xlb_requestqueue_init(xlb_s.types_size, &xlb_s.layout);
  ADLB_CHECK(ac);

  for (int rank = 0; rank < xlb_s.layout.workers; rank++)
  {
    CHECK_MSG(xlb_workq_get(rank, 0) == NULL,
              "workq_get failed for rank %i", rank);
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
