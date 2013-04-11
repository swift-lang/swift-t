
// Test that toint(empty string) results in error
// THIS-TEST-SHOULD-NOT-RUN

#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

main {
  int N = toint("");
  int V = N-1;
  printf("V: %i", V);
  trace(V);
}
