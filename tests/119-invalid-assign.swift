

#include <builtins.swift>
// THIS-TEST-SHOULD-NOT-RUN
// SKIP-THIS-TEST
main {
  int x;
  foreach i in [1:10] {
    x = i;
  }
  trace(x);
}
