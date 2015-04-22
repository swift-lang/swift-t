// THIS-TEST-SHOULD-NOT-COMPILE

// Check can't define conflicting function
(int x) trace (int y) {
  x = y;
}

main {
  trace(1);
}
