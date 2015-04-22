import location;
import assert;
import io;

@dispatch=WORKER
(int rank) f(int i) "turbine" "0.0.1" [
  "set <<rank>> [ adlb::rank ]; after 5"
];


main() {
  int N = 500;
  int target_rank = randomWorkerRank();
  location target = locationFromRank(target_rank);

  int actual_ranks[];

  foreach i in [1:N] {
    int actual_rank;
    actual_rank = @soft_location=target f(0);
    actual_ranks[i] = actual_rank;
  }

  // See if any executed on different rank to target
  boolean different_rank;
  for (int i = 1, different_rank = false; i <= N;
       i = i + 1, different_rank = different_rank || differs) {
    boolean differs = target_rank != actual_ranks[i];
  }
  printf("target: %s actual ranks: %s workers %d",
         target_rank, repr(actual_ranks), turbine_workers());

  assert(turbine_workers() == 1 || different_rank,
        "Expected soft targeted to go to idle work");
}
