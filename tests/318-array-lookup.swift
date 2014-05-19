// THIS-TEST-SHOULD-NOT-RUN
// SKIP-THIS-TEST : Currently broken (#525)

// Test failing array lookup

main {
  A[0] = 1;

  f(A);
}

f (int A[]) {
  trace(A[1]);
}
