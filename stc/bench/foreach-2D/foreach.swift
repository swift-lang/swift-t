
#include <builtins.swift>
#include <sys.swift>
#include "../util/bench.swift"

main {
  argv_accept("NX", "NY", "delay");
  int   NX     = toint(argv("NX"));
  int   NY     = toint(argv("NY"));
  float delay  = tofloat(argv("delay"));

  float A[][];
  foreach x in [0:NX-1]
  {
    foreach y in [0:NY-1]
    {
      A[x][y] = set1_float(delay);
    }
  }
}
