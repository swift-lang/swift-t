import assert;
import io;
import random;

main() {
  actual_ranks = [1];

  // Roundabout way to implement per-rank counters
  bag<void> counters[];
  foreach r in actual_ranks {
    counters[r] += makeVoid();
  }

  foreach b, r in counters {
    printf("rank %i count %i", r, bagSize(b));
  }

  printf("Distinct values: %i", size(counters));
}
