// THIS-TEST-SHOULD-NOT-COMPILE

(int x) trace2 (int y) {
  x = y;
}


// Check can't define conflicting variable
global const int trace2 = 3;
main {
  trace(trace2);
}
