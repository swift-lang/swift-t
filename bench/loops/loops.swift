
#include <builtins.swift>
#include <swift/stdio.swift>
#include <swift/unistd.swift>
#include "../util/bench.swift"

main {
  // int x = 3;
  // int y = 4;
  // int z = 5;
  // int r;
  // r = add3(x, y, z);

  argv_accept("V", "delay");
  int V = toint(argv("V"));

  foreach i in [0:V-1] {
    foreach j in [0:V-1] {
      foreach k in [0:V-1] {
        foreach m in [0:V-1] {
        int r = add3(j, k, m);
        // printf("%i %i %i", i, j, k);e
        }}}}
}
