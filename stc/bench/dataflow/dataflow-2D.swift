
#include <builtins.swift>
#include <random.swift>
#include "../util/bench.swift"

main {

  int M = 3;
  int N = 3;

  // metadata("hello");

  foreach i in [0:M-1]  {
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
}
