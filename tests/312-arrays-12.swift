// THIS-TEST-SHOULD-NOT-RUN

// CHeck that double writes are caught


// Known bug: at O3 double write isn't caught
// See issue #475
// SKIP-O3-TEST

main {
    int A[];
    A[id(1)] = 1;
    A[id(1)] = 2;
    // Prevent A getting optimized out
    trace(A[1]);
}

(int o) id (int i) {
    o = i;
}
