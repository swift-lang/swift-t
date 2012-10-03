#include <builtins.swift>
// SKIP-THIS-TEST
// THIS-TEST-SHOULD-NOT-RUN
// (but it should compile)

<T>
(int x) f (T y) "package" "0.0.0" "f";

main {
  int x = f(1);
  trace(x);
}
