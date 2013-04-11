
// THIS-TEST-SHOULD-NOT-RUN
#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

main {
  // Invalid trailing character
  int N = toint("1232b");
  trace(N);
}
