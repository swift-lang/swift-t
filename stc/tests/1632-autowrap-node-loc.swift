
// SKIP-THIS-TEST - Too many false failures.

import location;
import assert;
import io;

@dispatch=WORKER
(int rank, string host) f(int i) "turbine" "0.0.1" [
  "set <<rank>> [ adlb::rank ]; set <<host>> [ c_utils::hostname ] ; set after 5"
];


main() {
  int N = 500;
  string target_host = hostmapList()[0];
  int target_rank = hostmapOneWorkerRank(target_host);

  location target = location(target_rank, HARD, NODE);

  int actual_ranks[];
  string actual_hosts[];

  foreach i in [1:N] {
    actual_ranks[i], actual_hosts[i]= @location=target f(0);
  }

  // Roundabout way to implement per-rank counters
  bag<void> counters[];

  // See if any executed on different rank to target
  boolean different_host;
  for (int i = 1, different_host = false; i <= N;
       i = i + 1, different_host = different_host || differs) {
    boolean differs = target_host != actual_hosts[i];
  }
  printf("target: %s actual ranks: %s actual hosts: %s workers %d",
         target_rank, repr(actual_ranks), repr(actual_hosts),
         turbine_workers());

  foreach r in actual_ranks {
    counters[r] += makeVoid();
  }

  foreach b, r in counters {
    printf("rank %i count %i", r, bagSize(b));
  }

  assert(!different_host, "Expected hard targeted to go to same node");
  assertLT(1, size(counters), "Expected more than one rank to receive tasks");
}
