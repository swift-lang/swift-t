
// Regression test for compiler bug

// Test should fail from double write
// THIS-TEST-SHOULD-NOT-RUN
main {
    int A[];
    foreach i in [1:10] {
        // Assign array multiple times - should not be hoistedout of loop
        A[0] = 0;
    }
    trace(A[0]);
}
