
// Test that toint(empty string) results in error
// THIS-TEST-SHOULD-NOT-RUN

import io;
import sys;

main {
  int N = toint("");
  int V = N-1;
  printf("V: %i", V);
  trace(V);
}
