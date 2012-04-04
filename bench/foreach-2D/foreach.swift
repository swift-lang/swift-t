
#include <builtins.swift>
#include <swift/unistd.swift>
#include "../util/bench.swift"

main {
  argv_accept("N", "delay");
  int   N     = toint(argv("N"));
  float delay = tofloat(argv("delay"));

  float A[];
  foreach i in [0:N-1]
  {
    A[i] = set1_float(delay);
  }
}
