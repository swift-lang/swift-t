// SKIP-THIS-TEST #759
// THIS-TEST-SHOULD-NOT-COMPILE

type x {
  int a;
  int b;
}

main {
  // Check can't define conflicting variable with type
  int x = 3;
}
