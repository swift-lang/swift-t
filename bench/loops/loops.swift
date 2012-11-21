
#include <builtins.swift>
#include <io.swift>
#include <sys.swift>
#include "../util/bench.swift"

main {

  argv_accept("V");
  int V = toint(argv("V"));

  @async
  foreach i in [0:V-1] {
    @async
    foreach j in [0:V-1] {
      @async
      foreach k in [0:V-1] {
        foreach m in [0:V-1] {
          int r = add4(i, j, k, m);
          // printf("%i+%i+%i+%i=%i", i, j, k, m, r);
        }}}}
}
