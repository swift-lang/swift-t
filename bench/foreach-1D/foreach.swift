
#include <builtins.swift>
#include <string.swift>
#include <sys.swift>
#include "../util/bench.swift"

/*
  delay in milliseconds
*/
main {
  argv_accept("N", "delay");
  int   N     = toint(argv("N"));
  float delay = tofloat(argv("delay"));

  metadata(sprintf("N:%i",N));

  float A[];

  // @sync
  // @splitdegree // STC test 587
  // Make
  // No actual container

  foreach i in [0:N-1]
  {
    A[i] = set1_float(delay);
  }
}
