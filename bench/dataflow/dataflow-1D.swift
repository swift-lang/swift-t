
#include <builtins.swift>
#include <stdlib.swift>
#include <unistd.swift>
#include "../util/bench.swift"

main {

  int N = 3;

  metadata(sprintf("N:%i", N));
  metadata(sprintf("JOB:%s", getenv("PWD")));

  int A[];
  foreach j in [0:N-1] {
    int d;
    if (j == 0) {
      d = 0;
    } else {
      int r = randint(0, j);
      d = A[r];
    }
    A[j] = set1_integer(d);
  }
}
