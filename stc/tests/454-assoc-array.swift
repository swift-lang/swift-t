// Test for associative array literals

main {
  int A[string];

  A = { "test":1, "testing":2, "tested":3 };

  foreach k, i in { 1: "test", 2: "testing", 3: "tested" } {
    trace(i, k, A[k], contains(A, k));
  }
}
