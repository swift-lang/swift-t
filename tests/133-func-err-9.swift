// THIS-TEST-SHOULD-NOT-COMPILE

(int x) trace2 (int y) {
  x = y;
}

main {
  // Check can't define conflicting variable
  int trace2 = 3;
  trace(trace2);
}
