
#include <builtins.swift>
#include <swift/stdio.swift>
#include <swift/unistd.swift>
#include "../util/bench.swift"

main {
  argv_accept("NX", "NY", "delay");
  int NX    = toint(argv("NX"));
  int NY    = toint(argv("NY"));
  int delay = toint(argv("delay"));

  int A[][];
  int B[];
  foreach x in [0:NX-1]
  {
    foreach y in [0:NY-1]
    {
      A[x][y] = set1_integer(delay);
    }
    B[x] = sum_integer(A[x]);
    printf("B[%i]=%i", x, B[x]);
  }
}
