

main {
  int A[];
  int x;
  if (test(1) == 1) {
    A[0] = 1;
    x = test(1);
  } else {
    A[0] = 2;
    x = test(2);
  }

  // Use wait to force ordering such that branch optimization of A[0]
  // will always be able to happen
  wait (x) {
    trace("RESULT:" + fromint(A[0]));
  }
}


// Opaque function that can't be inlined
(int o) test (int i) "turbine" "0.0.1" [
  "set <<o>> <<i>>"
];
