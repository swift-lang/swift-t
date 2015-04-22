import math;
import sys;

// COMPILE-ONLY-TEST
// Regression test for substituting uninit var

main {
  float A[];
  // Forward reference to array values causes uninit value to be substituted
  float sum = A[1] + A[2] + A[3] + A[4] + A[4] + A[5] + A[6] + A[7] + A[8];

  // Loop should be expanded by opt
  foreach i in [1:8] {
    // Use argc so that this can't be constant folded
    A[i] = exp(itof(argc() + i));
  }

  trace(sum);
}
