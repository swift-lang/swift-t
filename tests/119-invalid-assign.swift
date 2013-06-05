

// THIS-TEST-SHOULD-NOT-RUN
// SKIP-O2-TEST
// SKIP-O3-TEST
main {
  int x;
  foreach i in [1:10] {
    x = i;
  }
  trace(x);
}
