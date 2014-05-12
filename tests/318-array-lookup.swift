// THIS-TEST-SHOULD-NOT-RUN

// Test failing array looking

main {
  A[0] = 1;

  f(A);
}

f (int A[]) {
  trace(A[1]);
}
