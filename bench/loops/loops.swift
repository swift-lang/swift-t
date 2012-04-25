
#include <builtins.swift>
#include <swift/stdio.swift>
#include <swift/unistd.swift>
#include "../util/bench.swift"

main {

  argv_accept("V");
  int V = toint(argv("V"));

  foreach i in [0:V-1] {
    foreach j in [0:V-1] {
      foreach k in [0:V-1] {
        foreach m in [0:V-1] {
        int r = add3(j, k, m);
        // printf("%i %i %i", i, j, k);e
        }}}}
}
