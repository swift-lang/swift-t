#include "builtins.swift"
//THIS-TEST-SHOULD-NOT-COMPILE
// because we're mixing use of an array ref with an allocated array


(int x[]) f () {
    x[0] = 1;
}

main {
    int x = 0;
    int A[];
    int B[][];
    B[0] = f();

    // on one branch, A must be locally allocated,
    // on the other A must be a reference to an array allocated elsewhere
    if (x) {
        A = B[0]; // Make sure A is forced to be a ref to array
    } else {
        A[0] = 1;
    }
    trace(A[0]);
}
