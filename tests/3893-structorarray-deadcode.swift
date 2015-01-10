/* Regression test for dead code elimination bug not understanding recursive
   stores to structs */

import assert;

type st {
  int A[];
}

// Force STC to use recursive build for output
(st o) build () "turbine" "0" [
  "set <<o>> [ dict create A [ dict create 0 1 ] ]"
];

main () {
  st x = build();

  assertEqual(x.A[0], 1, "x.A[0]");
}
