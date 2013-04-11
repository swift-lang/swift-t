#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

// THIS-TEST-SHOULD-NOT-RUN
main {
  // Should only accept decimals
  int N = toint("0x1232");
  trace(N);
}
