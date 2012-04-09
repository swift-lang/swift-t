
#include <builtins.swift>

// THIS-TEST-DOES-NOT-WORK (#199)

main
{
  int n = 3;
  int d = 2;

  int T = pow_integer(n,d);

  foreach i in [0:T-1]
  {
    trace(i);
  }
}
