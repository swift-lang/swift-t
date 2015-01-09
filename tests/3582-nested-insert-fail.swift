// THIS-TEST-SHOULD-NOT-RUN
/*
 * Test behaviour when we make conflicting assignments to nested arrays
 */

import sys;

(int o) f(int i) "turbine" "0" [
  "set <<o>> <<i>>"
];

main {

  int A[][];

  A[f(0)] = [ 1, 2, 3 ];

  sleep(0.1) =>
    A[f(0)][0] = 4;


  // Output to prevent optimisation
  trace(A[0][0]);
}
