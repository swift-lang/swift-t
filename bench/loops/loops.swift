
#include <builtins.swift>
#include <swift/stdio.swift>
#include "../util/bench.swift"

main {
  // int x = 3;
  // int y = 4;
  // int z = 5;
  // int r;
  // r = add3(x, y, z);

  int N = 3;

  foreach i in [0:N-1] {
    foreach j in [0:N-1] {
      foreach k in [0:N-1] {
        int r = add3(i, j, k);
        // printf("%i %i %i", i, j, k);e
      }}}
}
