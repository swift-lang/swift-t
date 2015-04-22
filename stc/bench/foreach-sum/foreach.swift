
#include <builtins.swift>
#include <io.swift>
#include <sys.swift>
#include "../util/bench.swift"

main {
  argv_accept("NX", "NY", "delay");
  int NX    = toint(argv("NX"));
  int NY    = toint(argv("NY"));
  int delay = toint(argv("delay"));
  int delay_2 = delay %/ 2;

  int A[][];
  int B[][];
  int C[];
  foreach x in [0:NX-1]
  {
    foreach y in [0:NY-1]
    {
      // f()
      A[x][y] = set1rA_integer(delay);
      // g()
      B[x][y] = set1rB_integer(delay_2*A[x][y]);
    }
    C[x] = bench_sum_integer(B[x]);
    // C[x] = sum_integer(B[x]);
    printf("C[%i]=%i", x, C[x]);
  }
}
