import sys;
import assert;


// Regression test for loop hoisting bug
// where loop assignments only partially hoisted out of loop
// Failure mode is that we get a double assignment

main {
  foreach i in [1:toint(argv("n"))] {
    int A[];
    foreach j in [0:2] {
      A[j] = j;
    }
    
    trace(A[0], A[1], A[2]);
    assertEqual(size(A), 3, "size");
    assertEqual(A[0], 0, "A[0]");
    assertEqual(A[1], 1, "A[1]");
    assertEqual(A[2], 2, "A[2]");
  }
}
