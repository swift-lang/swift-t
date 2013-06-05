

// THIS-TEST-SHOULD-NOT-RUN
main {
  int x;
  foreach i in [1:10] {
    x = i;
  }
  trace(x);
}
