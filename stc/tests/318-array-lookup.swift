// THIS-TEST-SHOULD-NOT-RUN

// Test failing array lookup

main {
  A[0] = 1;

  f(A);
}

f (int A[]) {
  trace(A[1]);
}
